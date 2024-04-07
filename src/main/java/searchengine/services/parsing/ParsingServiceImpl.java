package searchengine.services.parsing;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.model.SiteEntity;
import searchengine.model.Status;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SearchIndexRepository;
import searchengine.repositories.SiteRepository;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.TreeSet;
import java.util.concurrent.ForkJoinPool;

@Service
@Slf4j
public class ParsingServiceImpl implements ParsingService {

    @Autowired
    SitesList sites;
    @Autowired
    SiteRepository siteRepository;
    @Autowired
    PageRepository pageRepository;
    @Autowired
    LemmaRepository lemmaRepository;
    @Autowired
    SearchIndexRepository searchIndexRepository;
    private boolean isIndexingStarted;
    private boolean isContainsUrl;
    private SiteEntity siteEntity;
    private ForkJoinPool forkJoinPool;

    final SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy hh:mm:ss");

    @Override
    @SneakyThrows
    public IndexingResponse startIndexing() {
        if (isIndexingStarted) {
            log.info("Запрос запуска индексации при уже выполняющейся");
            return new IndexingResponse(false, "Индексация уже запущена");
        } else isIndexingStarted = true;
        long startIndexing = System.currentTimeMillis();
        log.info("Индексация запущена в {}", formatter.format(startIndexing));
        searchIndexRepository.deleteAll();
        lemmaRepository.deleteAll();
        pageRepository.deleteAll();
        siteRepository.deleteAll();

        for (Site site : sites.getSites()) {
            indexSite(site);
        }
        isIndexingStarted = false;
        log.info("Индексация завершена за {}", System.currentTimeMillis() - startIndexing);
        return new IndexingResponse(true);
    }

    @Override
    public IndexingResponse stopIndexing() {
        if (!isIndexingStarted) {
            log.info("Запрос остановки индексации в момент, когда индексация не выполняется");
            return new IndexingResponse(false, "Индексация не запущена");
        } else isIndexingStarted = false;
        log.info("Остановка индексации ");
        forkJoinPool.shutdownNow();
        Iterable<SiteEntity> siteList = siteRepository.findAll();
        for (SiteEntity site : siteList) {
            if (site.getStatus() == Status.INDEXING) {
                site.setStatus(Status.FAILED);
                site.setStatusTime(LocalDateTime.now());
                site.setLastError("Индексация остановлена");
                siteRepository.save(site);
            }
        }
        log.info("Индексация остановлена в {}", formatter.format(System.currentTimeMillis()));
        return new IndexingResponse(true);
    }

    @Override
    @SneakyThrows
    public IndexingResponse indexPage(String url) {
        Site indexingSite = new Site();
        for (Site site : sites.getSites()) {
            if (url.contains(site.getUrl())) {
                isContainsUrl = true;
                indexingSite = site;
                break;
            }
        }
        if (isContainsUrl) {
            boolean containsInRepository = false;
            for (SiteEntity site : siteRepository.findAll()) {
                if (site.getName().equals(indexingSite.getName())) {
                    siteEntity = site;
                    containsInRepository = true;
                    break;
                }
            }
            if (!containsInRepository) {
                siteEntity = new SiteEntity();
                siteEntity.setUrl(indexingSite.getUrl());
                siteEntity.setName(indexingSite.getName());
                siteEntity.setStatus(Status.INDEXING);
                siteEntity.setStatusTime(LocalDateTime.now());
                siteRepository.save(siteEntity);
            }

            PageParser pageParser = new PageParser(url, siteEntity, pageRepository, siteRepository, lemmaRepository, searchIndexRepository);
            pageParser.parsePage();
            return new IndexingResponse(true);
        } else {
            log.info("Сайт не находится в перечне разрешенных в конфигурационном файле");
            return new IndexingResponse(false, "Сайт не находится в перечне разрешенных в конфигурационном файле");
        }
    }

    private void indexSite(Site site) {
        SiteEntity siteEntity = new SiteEntity();
        siteEntity.setName(site.getName());
        siteEntity.setUrl(site.getUrl());
        siteEntity.setStatus(Status.INDEXING);
        siteEntity.setStatusTime(LocalDateTime.now());
        siteRepository.save(siteEntity);

        TreeSet<String> hrefList = new TreeSet<>();
        hrefList.add(site.getUrl());

        forkJoinPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
        SiteParser siteParser = new SiteParser(site.getUrl(), hrefList, siteEntity, pageRepository, siteRepository, lemmaRepository, searchIndexRepository);
        forkJoinPool.execute(siteParser);

        forkJoinPool.shutdown();

        siteEntity.setStatus(Status.INDEXED);
        siteEntity.setStatusTime(LocalDateTime.now());
        siteRepository.save(siteEntity);
    }
}
