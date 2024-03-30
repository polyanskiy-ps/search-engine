package searchengine.dto.searching;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SearchData {

    private String site;
    private String siteName;
    private String uri;
    private String title;
    private String snippet;
    private float relevance;

    @Override
    public String toString() {
        return "SearchData{" +
                "site='" + site + '\'' +
                ", uri='" + uri + '\'' +
                ", title='" + title + '\'' +
                ", snippet='" + snippet + '\'' +
                ", relevance=" + relevance +
                '}';
    }
}

