package searchengine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Getter
@Setter
@Table(name = "site", schema = "search_engine")
public class SiteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "site_id")
    private int siteID;

    @Enumerated(EnumType.STRING)
    @Column(name = "site_status", nullable = false)
    private Status status;

    @Column(name = "status_time", nullable = false, columnDefinition = "datetime")
    private LocalDateTime statusTime;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "site_url", nullable = false, columnDefinition = "varchar(255)")
    private String url;

    @Column(name = "site_name", updatable = false, columnDefinition = "varchar(255)")
    private String name;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SiteEntity that)) return false;
        return getSiteID() == that.getSiteID() && Objects.equals(getStatus(), that.getStatus()) && Objects.equals(getStatusTime(), that.getStatusTime()) && Objects.equals(getLastError(), that.getLastError()) && Objects.equals(getUrl(), that.getUrl()) && Objects.equals(getName(), that.getName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getSiteID(), getStatus(), getStatusTime(), getLastError(), getUrl(), getName());
    }
}
