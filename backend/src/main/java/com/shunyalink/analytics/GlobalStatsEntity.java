package com.shunyalink.analytics;

import jakarta.persistence.*;

@Entity
@Table(name = "platform_stats")
public class GlobalStatsEntity {

    @Id
    private Long id;

    @Column(name = "total_clicks", nullable = false)
    private long totalClicks;

    @Column(name = "total_links", nullable = false)
    private long totalLinks;

    @Column(name = "total_users", nullable = false)
    private long totalUsers;

    public GlobalStatsEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public long getTotalClicks() { return totalClicks; }
    public void setTotalClicks(long totalClicks) { this.totalClicks = totalClicks; }

    public long getTotalLinks() { return totalLinks; }
    public void setTotalLinks(long totalLinks) { this.totalLinks = totalLinks; }

    public long getTotalUsers() { return totalUsers; }
    public void setTotalUsers(long totalUsers) { this.totalUsers = totalUsers; }
}
