package searchengine.services.parsing;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SearchIndex;
import searchengine.model.SiteEntity;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SearchIndexRepository;
import searchengine.repositories.SiteRepository;

import java.util.*;
import java.util.concurrent.RecursiveAction;

@Slf4j
@RequiredArgsConstructor
public class SiteParser extends RecursiveAction {
    private final String url;
    private final TreeSet<String> hrefList;
    private final SiteEntity site;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final SearchIndexRepository searchIndexRepository;
    private Document document;



    @Override
    @SneakyThrows
    protected void compute() {

        Thread.sleep(250);
        Connection connection = Jsoup.connect(url)
                .ignoreContentType(true)
                .userAgent(new UserAgent().getUserAgent())
                .referrer("https://www.google.com");

        try {
            document = connection.execute().parse();
            PageEntity page = new PageEntity();
            page.setSiteID(site);
            page.setPagePath(url);
            page.setPageContent(String.valueOf(document));
            page.setPageCode(connection.response().statusCode());
            pageRepository.save(page);

            Lemmatizer lemmatizer = new Lemmatizer();
            Map<String, Integer> lemmas = lemmatizer.getLemmas(document.text());
            List<LemmaEntity> lemmaEntityList = new ArrayList<>();
            List<SearchIndex> searchIndexList = new ArrayList<>();

            for(Map.Entry<String, Integer> lemma:lemmas.entrySet()){
                lemmaEntityList.add(createLemmaEntity(site, lemma.getKey(), lemma.getValue()));
                searchIndexList.add(createSearchIndex(page, lemmaEntityList.get(lemmaEntityList.size() - 1), lemma.getValue()));
            }
            lemmaRepository.saveAll(lemmaEntityList);
            searchIndexRepository.saveAll(searchIndexList);
        }
        catch (HttpStatusException e) {
            log.info("Не удается выполнить парсинг сайта {}", url);
        }

        List<String> links = collectLinks(url);
        List<SiteParser> tasks = new ArrayList<>();
        for (String link : links)
        {
            if (!hrefList.contains(link) && !links.isEmpty())
            {
                SiteParser siteParser = new SiteParser(
                        link,
                        hrefList,
                        site,
                        pageRepository,
                        siteRepository,
                        lemmaRepository,
                        searchIndexRepository);
                siteParser.fork();
                hrefList.add(link);
                log.info("Парсинг {}", link);
                tasks.add(siteParser);
            }
        }

        for (SiteParser task : tasks) {
            task.join();
        }
    }

    private LemmaEntity createLemmaEntity(SiteEntity site, String lemma, int frequency) {
        return new LemmaEntity(site, lemma, frequency);
    }

    private SearchIndex createSearchIndex(PageEntity page, LemmaEntity lemmaEntity, Integer searchRank) {
        return new SearchIndex(page, lemmaEntity, searchRank);

    }


    @SneakyThrows
    public  synchronized List<String> collectLinks(String url) {

        List<String> linkList = new ArrayList<>();
        linkList.add(url);

        Elements links = document.select("a[href]");
        for (Element element : links) {
            String link = element.attr("abs:href");
            if (!link.contains(url.replaceAll("(http(s)?://)?(www/.)?(/.*)?", ""))) continue;
            if (link.contains("&") ||
                    link.contains("#") ||
                    link.contains("?") ||
                    link.contains("?page=") ||
                    link.contains("?ref") ||
                    link.contains("?main_click") ||
                    link.endsWith(".shtml") ||
                    link.endsWith(".pdf") ||
                    link.endsWith(".xml") ||
                    link.endsWith(".jpg") ||
                    link.endsWith(".png") ||
                    link.endsWith(".jpeg") ||
                    link.endsWith(".jfif") ||
                    link.endsWith(".doc") ||
                    link.endsWith(".docx") ||
                    link.endsWith(".xls") ||
                    link.endsWith(".xlsx") ||
                    link.endsWith(".pptx") ||
                    link.endsWith(".rtf") ||
                    link.endsWith(".mp4") ||
                    link.endsWith(".gif")) continue;
            if (linkList.contains(link)) continue;
            linkList.add(link);
        }
        return linkList;
    }
}
