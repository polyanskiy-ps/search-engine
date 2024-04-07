package searchengine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

@Entity
@Table
@Setter
@Getter
@NoArgsConstructor(force = true)
public class SearchIndex {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "search_index_id")
    private int searchIndexID;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(referencedColumnName = "page_id", name = "page_id")
    private final PageEntity pageID;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(referencedColumnName = "lemma_id", name = "lemma_id")
    private final LemmaEntity lemmaID;

    @Column(name = "search_rank", nullable = false)
    private final float searchRank;

    public SearchIndex(PageEntity pageID, LemmaEntity lemmaID, float searchRank) {
        this.pageID = pageID;
        this.lemmaID = lemmaID;
        this.searchRank = searchRank;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SearchIndex that)) return false;
        return getSearchIndexID() == that.getSearchIndexID() && Float.compare(getSearchRank(), that.getSearchRank()) == 0 && Objects.equals(getPageID(), that.getPageID()) && Objects.equals(getLemmaID(), that.getLemmaID());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getSearchIndexID(), getPageID(), getLemmaID(), getSearchRank());
    }
}
