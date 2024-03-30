package searchengine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Entity
@Table
@Setter
@Getter
public class SearchIndex {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "search_index_id")
    private int searchIndexID;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(referencedColumnName = "page_id", name = "page_id")
    private PageEntity pageID;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(referencedColumnName = "lemma_id", name = "lemma_id")
    private LemmaEntity lemmaID;

    @Column(name = "search_rank", nullable = false)
    private float searchRank;

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
