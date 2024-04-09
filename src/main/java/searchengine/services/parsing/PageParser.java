package searchengine.services.parsing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import searchengine.model.*;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SearchIndexRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class PageParser {
    private final SiteEntity site;
    private PageEntity page;
    private final String url;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final SearchIndexRepository searchIndexRepository;
    private boolean contains;


    public void parsePage() {

        for (PageEntity page : pageRepository.findAll()) {
            if (page.getPagePath().equalsIgnoreCase(url)) {
                this.page = page;
                contains = true;
                break;
            }
        }
        if (contains) {
            List<SearchIndex> indexList = new ArrayList<>();
            for (SearchIndex searchIndex : searchIndexRepository.findAll()) {
                if (searchIndex.getPageID().getPageID() == page.getPageID()) {
                    indexList.add(searchIndex);
                }
            }
            searchIndexRepository.deleteAll(indexList);
            pageRepository.delete(page);
        }

        Connection connection = Jsoup.connect(url)
                .ignoreContentType(true)
                .userAgent(new UserAgent().getUserAgent())
                .referrer("https://www.google.com");

        try {
            Document document = connection.execute().parse();
            page = new PageEntity();
            page.setSiteID(site);
            page.setPagePath(url);
            page.setPageContent(String.valueOf(document));
            page.setPageCode(connection.response().statusCode());
            pageRepository.save(page);
            site.setStatus(Status.INDEXED);
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);

            Lemmatizer lemmatizer = new Lemmatizer();
            Map<String, Integer> lemmas = lemmatizer.getLemmas(document.text());
            List<LemmaEntity> lemmaEntityList = new ArrayList<>();
            List<SearchIndex> searchIndexList = new ArrayList<>();

            for (Map.Entry<String, Integer> lemma : lemmas.entrySet()) {
                lemmaEntityList.add(createLemmaEntity(site, lemma.getKey(), lemma.getValue()));
                searchIndexList.add(createSearchIndex(page, lemmaEntityList.get(lemmaEntityList.size() - 1), lemma.getValue()));
            }
            lemmaRepository.saveAll(lemmaEntityList);
            searchIndexRepository.saveAll(searchIndexList);
        } catch (IOException e) {
            log.info("Не удается выполнить парсинг сайта {}", url);
        }
    }

    private LemmaEntity createLemmaEntity(SiteEntity site, String lemma, int frequency) {
        return new LemmaEntity(site, lemma, frequency);
    }

    private SearchIndex createSearchIndex(PageEntity page, LemmaEntity lemmaEntity, Integer searchRank) {
        return new SearchIndex(page, lemmaEntity, searchRank);

    }
}
