package searchengine.controllers;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.dto.searching.SearchResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.parsing.ParsingService;
import searchengine.services.searching.SearchingService;
import searchengine.services.statistics.StatisticsService;

import javax.inject.Inject;

@RestController
@RequestMapping("/api")
@AllArgsConstructor
public class ApiController {
    @Inject
    StatisticsService statisticsService;
    @Inject
    ParsingService parsingService;
    @Inject
    SearchingService searchingService;

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return new ResponseEntity<>(statisticsService.getStatistics(), HttpStatus.OK);
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<IndexingResponse> startIndexing() {
        return new ResponseEntity<>(parsingService.startIndexing(), HttpStatus.OK);
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<IndexingResponse> stopIndexing() {
        return new ResponseEntity<>(parsingService.stopIndexing(), HttpStatus.OK);
    }

    @PostMapping("/indexPage")
    public ResponseEntity<IndexingResponse> parsePage(String url) {
        return new ResponseEntity<>(parsingService.indexPage(url), HttpStatus.OK);
    }

    @GetMapping("/search")
    public ResponseEntity<SearchResponse> search(@RequestParam String query,
                @RequestParam(required = false) String site,
                @RequestParam Integer offset,
                @RequestParam Integer limit) {
        return new ResponseEntity<>(searchingService.getSearchResults(query, site, offset, limit), HttpStatus.OK);
    }

}
