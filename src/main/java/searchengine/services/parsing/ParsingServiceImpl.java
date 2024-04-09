package searchengine.services.parsing;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
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
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class ParsingServiceImpl implements ParsingService {

    private final SitesList sites;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final SearchIndexRepository searchIndexRepository;
    private boolean isIndexingStarted;
    private static volatile boolean isStopped;
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
        } else {
            isIndexingStarted = true;
            long startIndexing = System.currentTimeMillis();
            log.info("Индексация запущена в {}", formatter.format(startIndexing));
            new Thread(this::indexSite).start();
            return new IndexingResponse(true);
        }
    }

    @Override
    public IndexingResponse stopIndexing() {
        if (!isIndexingStarted) {
            log.info("Запрос остановки индексации в момент, когда индексация не выполняется");
            return new IndexingResponse(false, "Индексация не запущена");
        } else {
            isIndexingStarted = false;
            isStopped = true;
        }
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
                    System.out.println("Сайт был найден в репозитории. Запущена пере индексация страницы");
                    containsInRepository = true;
                    break;
                }
            }

            if (!containsInRepository) {
                System.out.println("Сайт не был найден в репозитории. Создается новый объект");
                siteEntity = new SiteEntity();
                siteEntity.setUrl(indexingSite.getUrl());
                siteEntity.setName(indexingSite.getName());
                siteEntity.setStatus(Status.INDEXING);
                siteEntity.setStatusTime(LocalDateTime.now());
                siteRepository.save(siteEntity);
            }

            PageParser pageParser = new PageParser(siteEntity, url, pageRepository, siteRepository,
                    lemmaRepository, searchIndexRepository);
            pageParser.parsePage();
            return new IndexingResponse(true);
        } else {
            return new IndexingResponse(false,
                    "Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
        }
    }

    private IndexingResponse indexSite() {
        siteRepository.deleteAll();
        pageRepository.deleteAll();
        lemmaRepository.deleteAll();
        searchIndexRepository.deleteAll();

        for (Site site : sites.getSites()) {
            SiteEntity siteEntity = new SiteEntity();
            siteEntity.setName(site.getName());
            siteEntity.setUrl(site.getUrl());
            siteEntity.setStatus(Status.INDEXING);
            siteEntity.setStatusTime(LocalDateTime.now());
            siteRepository.save(siteEntity);

            TreeSet<String> hrefList = new TreeSet<>();
            SiteParser siteParser = new SiteParser(site.getUrl(), hrefList,
                    siteEntity,
                    pageRepository, siteRepository, lemmaRepository, searchIndexRepository);

            hrefList.add(site.getUrl());
            forkJoinPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
            forkJoinPool.execute(siteParser);
            forkJoinPool.shutdown();

            try {
                forkJoinPool.awaitTermination(1, TimeUnit.HOURS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            if (isStopped) {
                isStopped = false;
                return new IndexingResponse(false, "Индексация остановлена пользователем");
            }

            siteEntity.setStatus(Status.INDEXED);
            siteEntity.setStatusTime(LocalDateTime.now());
            siteRepository.save(siteEntity);
        }

        isIndexingStarted = false;
        return new IndexingResponse(true);
    }
}
