package searchengine.services.searching;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.searching.SearchData;
import searchengine.dto.searching.SearchResponse;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SearchIndex;
import searchengine.model.SiteEntity;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.SearchIndexRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.parsing.Lemmatizer;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchingServiceImpl implements SearchingService {
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final SearchIndexRepository indexRepository;
    private final Lemmatizer lemmatizer;
    private final SnippetGenerator snippetGenerator;
    private final SitesList sitesList;

    @Override
    public SearchResponse getSearchResults(String query, String siteUrl, Integer offset, Integer limit) {
        long start = System.currentTimeMillis();
        if (query.isEmpty()) {
            log.info("Ошибка. Задан пустой поисковый запрос");
            return new SearchResponse(false,"Задан пустой поисковый запрос", 0, new ArrayList<>());
        }

        Set<String> lemmasFromQuery = generateLemmasFromQuery(query);

        LinkedHashMap<String, Integer> lemmasSortedByFrequency = sortLemmasByFrequency(lemmasFromQuery);

        SearchResponse searchResponse;

        LinkedHashMap<LemmaEntity, PageEntity> entitiesList = new LinkedHashMap<>();

        if (siteUrl != null) {
            SiteEntity siteEntity = getSiteEntity(siteUrl);
            entitiesList = getEntitiesList(lemmasFromQuery, siteEntity, lemmasSortedByFrequency);

            LinkedHashMap<PageEntity, Integer> pagesByRelevance = countAbsoluteRank(entitiesList);
            LinkedHashMap<PageEntity, Integer> sortedPages = sortPages(pagesByRelevance);
            List<SearchData> generatedSearchDataList = generateSearchDataList(sortedPages, lemmasFromQuery, limit, offset);
            searchResponse = response(generatedSearchDataList);
        } else {
            for (Site site : sitesList.getSites()) {
                log.info("Поиск на сайте {}", site.getName());
                SiteEntity siteEntity = getSiteEntity(site.getUrl());
                entitiesList.putAll(getEntitiesList(lemmasFromQuery, siteEntity, lemmasSortedByFrequency));
            }
            LinkedHashMap<PageEntity, Integer> pagesByRelevance = countAbsoluteRank(entitiesList);
            LinkedHashMap<PageEntity, Integer> sortedPages = sortPages(pagesByRelevance);
            List<SearchData> generatedSearchDataList = generateSearchDataList(sortedPages, lemmasFromQuery, limit, offset);
            searchResponse = response(generatedSearchDataList);
        }
        log.info("Поиск выполнен за {} мс", System.currentTimeMillis() - start);
        return searchResponse;
    }

    private LinkedHashMap<LemmaEntity, PageEntity> getEntitiesList(Set<String> lemmasFromQuery,
                                                                   SiteEntity site,
                                                                   LinkedHashMap<String, Integer> lemmasSortedByFrequency) {
        List<PageEntity> pagesListFromFirstLemma =
                getPageEntityListFromFirstLemma(lemmasSortedByFrequency, site);

        List<PageEntity> pagesFilteredByNextLemmas =
                filterPagesByOtherLemmas(lemmasSortedByFrequency, pagesListFromFirstLemma);

        return compareFinalPagesAndLemmas(
                pagesFilteredByNextLemmas, lemmasFromQuery);
    }

    private SearchResponse response(List<SearchData> searchData) {
        return SearchResponse.builder()
                .result(true)
                .count(searchData.size())
                .data(searchData)
                .build();
    }

    private SearchData generateSearchData(String site,
                                          String siteName,
                                          String uri,
                                          String title,
                                          String snippet,
                                          float relevance) {
        return SearchData.builder()
                .site(site)
                .siteName(siteName)
                .uri(uri)
                .title(title)
                .snippet(snippet)
                .relevance(relevance)
                .build();
    }

    private List<SearchData> generateSearchDataList(LinkedHashMap<PageEntity,
            Integer> sortedPages,
                                                    Set<String> lemmasFromQuery,
                                                    int limit, int offset) {
        log.info("Формирование списка объектов SearchData");
        if (offset != 0 && !sortedPages.isEmpty()) {
            sortedPages.remove(sortedPages.keySet().stream().findFirst().get());
        }

        List<SearchData> dataList = new ArrayList<>();
        int count = 0;
        for (Map.Entry<PageEntity, Integer> entry : sortedPages.entrySet()) {
            if (count < limit) {
                dataList.add(
                        generateSearchData(
                                entry.getKey().getSiteID().getUrl(),
                                entry.getKey().getSiteID().getName(),
                                shortThePath(entry.getKey(), entry.getKey().getSiteID()),
                                Jsoup.parse(entry.getKey().getPageContent()).title(),
                                getSnippet(entry.getKey(), lemmasFromQuery),
                                entry.getValue())
                );
                count++;
            }
        }
        log.info("Сформировано {} объектов", dataList.size());
        return dataList;
    }

    private String shortThePath(PageEntity page, SiteEntity site) {
        String pageURL = page.getPagePath();
        String siteURL = site.getUrl();
        return pageURL.replaceAll(siteURL, "");
    }

    private SiteEntity getSiteEntity(String siteURL) {
        return siteRepository.findSiteEntityByUrlIsIgnoreCase(siteURL);
    }

    private String getSnippet(PageEntity page, Set<String> lemmas) {
        List<String> queryList = new ArrayList<>(lemmas);
        snippetGenerator.setText(page.getPageContent());
        snippetGenerator.setQueryWords(queryList);
        return snippetGenerator.generateSnippets();
    }

    private Set<String> generateLemmasFromQuery(String query) {
        return lemmatizer.getLemmas(query).keySet();
    }

    private LinkedHashMap<String, Integer> sortLemmasByFrequency(Set<String> lemmasList) {
        log.info("Сортировка лемм по частоте встречаемости");
        LinkedHashMap<String, Integer> foundLemmas = new LinkedHashMap<>();

        for (String lemmaFromList : lemmasList) {
            AtomicInteger frequency = new AtomicInteger();
            List<LemmaEntity> lemmas = lemmaRepository.findLemmaEntitiesByLemmaEqualsIgnoreCase(lemmaFromList);

            lemmas.forEach(lemma -> log.info("лемма {} частота {}", lemma.getLemma(), lemma.getFrequency()));
            lemmas = removeMostFrequentlyLemmas(lemmas);

            lemmas.forEach(lemmaEntity -> frequency.set(frequency.get() + lemmaEntity.getFrequency()));
            foundLemmas.put(lemmaFromList, frequency.intValue());

        }

        LinkedHashMap<String, Integer> sortedMap = foundLemmas.entrySet().stream()
                .sorted(Comparator.comparingInt(Map.Entry::getValue))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> {
                            throw new AssertionError();
                        },
                        LinkedHashMap::new
                ));
        sortedMap.forEach((s, integer) -> System.out.println(s + " " + integer));
        return sortedMap;
    }

    private ArrayList<LemmaEntity> removeMostFrequentlyLemmas(List<LemmaEntity> lemmas) {
        log.info("Исключение наиболее встречающихся лемм");
        ArrayList<LemmaEntity> reList = new ArrayList<>(lemmas);
        int removableLemmasPercent = 5;
        int removeCount = Math.round((float) lemmas.size() / 100 * removableLemmasPercent);
        log.info("Количество исключаемых лемм {}", removeCount);
        LemmaEntity removable = new LemmaEntity();

        for (int i = 0; i < removeCount; i++) {
            int maxFrequency = 0;

            for (LemmaEntity lemma : lemmas) {
                if (lemma.getFrequency() > maxFrequency) {
                    maxFrequency = lemma.getFrequency();
                    removable = lemma;
                }
            }
            reList.remove(removable);
            log.info("исключена лемма: id: {} {}, частота {}",
                    removable.getLemmaID(),
                    removable.getLemma(),
                    removable.getFrequency());
        }

        return reList;
    }

    private List<PageEntity> getPageEntityListFromFirstLemma(LinkedHashMap<String, Integer> sortedLemmas, SiteEntity site) {
        log.info("Поиск страниц с самой редкой леммой из списка");
        List<PageEntity> listFromFirstLemma = new ArrayList<>();
        ArrayList<String> lemmaList = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : sortedLemmas.entrySet()) {
            lemmaList.add(entry.getKey());
        }
        String rareLemma = lemmaList.get(0);
        log.info("Самая редкая лемма: {}", rareLemma);

        ArrayList<SearchIndex> indexesFromFirstLemma =
                indexRepository.findSearchIndicesByLemmaID_LemmaAndPageID_SiteID(rareLemma, site);
        indexesFromFirstLemma.forEach(searchIndex -> listFromFirstLemma.add(searchIndex.getPageID()));

        log.info("По первой лемме найдено страниц: {}", listFromFirstLemma.size());
        listFromFirstLemma.forEach(page -> log.info(page.getPagePath()));
        return listFromFirstLemma;
    }

    private List<PageEntity> filterPagesByOtherLemmas(LinkedHashMap<String, Integer> sortedLemmas,
                                                      List<PageEntity> pagesListFromFirstLemma) {
        log.info("Исключение страниц, на которых отсутствуют остальные леммы");
        List<PageEntity> refactoredList = new ArrayList<>(pagesListFromFirstLemma);

        ArrayList<String> lemmaList = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : sortedLemmas.entrySet()) {
            lemmaList.add(entry.getKey());
        }
        if (!lemmaList.isEmpty()) {
            lemmaList.remove(0);

            for (PageEntity page : pagesListFromFirstLemma) {
                for (String lemma : lemmaList) {
                    if (indexRepository.findSearchIndicesByPageIDAndLemmaID_Lemma(page, lemma).isEmpty()) {
                        refactoredList.remove(page);
                        log.info("Исключена страница: {}", page.getPagePath());
                    }
                }
            }
        }

        log.info("Страниц после проверки списка: {}", refactoredList.size());
        refactoredList.forEach(pageEntity -> log.info(pageEntity.getPagePath()));
        return refactoredList;
    }

    private LinkedHashMap<PageEntity, Integer> countAbsoluteRank(LinkedHashMap<LemmaEntity, PageEntity> lemmaAndPageList) {
        log.info("Расчет абсолютной релевантности");

        LinkedHashMap<PageEntity, Integer> sortedList = new LinkedHashMap<>();

        for (Map.Entry<LemmaEntity, PageEntity> entry : lemmaAndPageList.entrySet()) {
            if (sortedList.containsKey(entry.getValue())) {
                int rank = sortedList.get(entry.getValue());
                sortedList.remove(entry.getValue());
                sortedList.put(entry.getValue(), (entry.getKey().getFrequency() + rank));
            } else {
                sortedList.put(entry.getValue(), entry.getKey().getFrequency());
            }
        }
        for (Map.Entry<PageEntity, Integer> entry : sortedList.entrySet()) {
            log.info(entry.getKey().getPagePath() + " " + entry.getValue());
        }
        return sortedList;
    }

    private LinkedHashMap<LemmaEntity, PageEntity> compareFinalPagesAndLemmas(List<PageEntity> pagesFilteredByNextLemmas,
                                                                              Set<String> lemmasFromQuery) {
        log.info("Группировка лемм и страниц");
        LinkedHashMap<LemmaEntity, PageEntity> finalPagesAndLemmasList = new LinkedHashMap<>();

        for (PageEntity page : pagesFilteredByNextLemmas) {
            for (String lemma : lemmasFromQuery) {
                indexRepository.findSearchIndicesByPageIDAndLemmaID_Lemma(page, lemma)
                        .forEach(searchIndex ->
                                finalPagesAndLemmasList.put(searchIndex.getLemmaID(), searchIndex.getPageID()));
            }
        }

        for (Map.Entry<LemmaEntity, PageEntity> entry : finalPagesAndLemmasList.entrySet()) {
            log.info(entry.getKey().getLemma() + " " + entry.getValue().getPagePath());
        }
        return finalPagesAndLemmasList;
    }

    private LinkedHashMap<PageEntity, Integer> sortPages(LinkedHashMap<PageEntity, Integer> finalPages) {
        log.info("Сортировка страниц к выдаче по релевантности");
        LinkedHashMap<PageEntity, Integer> sortedList;

        sortedList = finalPages.entrySet().stream()
                .sorted(Comparator.comparingInt(e -> -e.getValue()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> {
                            throw new AssertionError();
                        },
                        LinkedHashMap::new
                ));

        for (Map.Entry<PageEntity, Integer> entry : sortedList.entrySet()) {
            log.info(entry.getKey().getPagePath() + " " + entry.getValue());
        }
        return sortedList;
    }
}