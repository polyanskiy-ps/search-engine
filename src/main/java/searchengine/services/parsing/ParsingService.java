package searchengine.services.parsing;

import searchengine.dto.indexing.IndexingResponse;

public interface ParsingService {
    IndexingResponse startIndexing();
    IndexingResponse stopIndexing();
    IndexingResponse indexPage(String url);
}
