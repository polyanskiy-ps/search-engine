package searchengine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.Objects;

@Entity
@Getter
@Setter
@Table(name = "page", schema = "search_engine",
        indexes =
        @Index(name = "path_index",
                columnList = "page_path",
                unique = true))

public class PageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "page_id")
    private int pageID;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn (referencedColumnName = "site_id", name = "site_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private SiteEntity siteID;

    @Column(name = "page_path", nullable = false,
            columnDefinition = "varchar(255)", unique = true)
    private String pagePath;

    @Column(name = "page_code", nullable = false)
    private int pageCode;

    @Column(name = "page_content", nullable = false, columnDefinition = "mediumtext")
    private String pageContent;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PageEntity that)) return false;
        return getPageID() == that.getPageID() && getPageCode() == that.getPageCode() && Objects.equals(getSiteID(), that.getSiteID()) && Objects.equals(getPagePath(), that.getPagePath()) && Objects.equals(getPageContent(), that.getPageContent());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getPageID(), getSiteID(), getPagePath(), getPageCode(), getPageContent());
    }
}
