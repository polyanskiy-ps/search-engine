package searchengine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Entity
@Setter
@Getter
@Table(name = "lemma", schema = "search_engine")
public class LemmaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "lemma_id")
    private int lemmaID;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn (referencedColumnName = "site_id", name = "site_id")
    private SiteEntity siteID;

    @Column(name = "lemma", nullable = false, columnDefinition = "varchar(255)")
    private String lemma;

    @Column(name = "frequency", nullable = false)
    private int frequency;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LemmaEntity that)) return false;
        return lemmaID == that.lemmaID && frequency == that.frequency && Objects.equals(siteID, that.siteID) && Objects.equals(lemma, that.lemma);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lemmaID, siteID, lemma, frequency);
    }
}
