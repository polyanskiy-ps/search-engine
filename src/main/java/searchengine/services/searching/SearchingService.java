package searchengine.services.searching;

import searchengine.dto.searching.SearchResponse;

public interface SearchingService {
    SearchResponse getSearchResults(String query, String site, Integer offset, Integer limit);

}
