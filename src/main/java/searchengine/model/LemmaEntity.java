package searchengine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.Objects;

@Entity
@Setter
@Getter
@Table(name = "lemma", schema = "search_engine")
@NoArgsConstructor(force = true)
public class LemmaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "lemma_id")
    private int lemmaID;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn (referencedColumnName = "site_id", name = "site_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private final SiteEntity siteID;

    @Column(name = "lemma", nullable = false, columnDefinition = "varchar(255)")
    private final String lemma;

    @Column(name = "frequency", nullable = false)
    private final int frequency;

    public LemmaEntity(SiteEntity siteID, String lemma, int frequency) {
        this.siteID = siteID;
        this.lemma = lemma;
        this.frequency = frequency;
    }


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
