package com.scholarship.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/** Fetch state of one registered official source (a row of dataset/source_registry.csv). */
@Entity
@Table(name = "source_snapshots")
public class SourceSnapshotEntity {

    @Id
    @Column(name = "source_id", length = 20)
    private String sourceId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String url;

    @Column(columnDefinition = "TEXT")
    private String organization;

    @Column(columnDefinition = "TEXT")
    private String portalName;

    @Column(name = "last_checked")
    private OffsetDateTime lastChecked;

    @Column(name = "last_success")
    private OffsetDateTime lastSuccess;

    @Column(name = "last_http_status")
    private Integer lastHttpStatus;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "last_change_detected")
    private OffsetDateTime lastChangeDetected;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;

    /** PORTAL (landing/listing page), SCHEME_PAGE (one scheme), BRAND_PAGE (a provider page listing several programmes). */
    @Column(length = 20)
    private String kind;

    /** HTML or B4S (Buddy4Study embedded JSON). */
    @Column(length = 20)
    private String adapter;

    /** OFFICIAL, PARTNER (application partner of the provider) or AGGREGATOR. Aggregators never auto-update dates. */
    @Column(length = 20)
    private String trust;

    /** Regex picking the programme(s) on a multi-programme page that map to the schemes this source covers. */
    @Column(name = "item_match", columnDefinition = "TEXT")
    private String itemMatch;

    /** Allow dates read from this page to update records without a human (needs a structured adapter or an explicit opt-in). */
    @Column(name = "auto_apply")
    private Boolean autoApply;

    /** Follow links / sitemaps from this page to discover scheme pages. */
    @Column(name = "discover_links")
    private Boolean discoverLinks;

    /** Regex an anchor href or text must match to be treated as a scheme page during discovery. */
    @Column(name = "link_filter", columnDefinition = "TEXT")
    private String linkFilter;

    /** True when this row was found by discovery rather than listed in source_registry.csv. */
    @Column(name = "discovered")
    private Boolean discovered;

    @Column(name = "discovered_from", length = 20)
    private String discoveredFrom;

    public SourceSnapshotEntity() {
    }

    public SourceSnapshotEntity(String sourceId, String url, String organization, String portalName) {
        this.sourceId = sourceId;
        this.url = url;
        this.organization = organization;
        this.portalName = portalName;
    }

    public String getSourceId() { return sourceId; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getOrganization() { return organization; }
    public void setOrganization(String organization) { this.organization = organization; }
    public String getPortalName() { return portalName; }
    public void setPortalName(String portalName) { this.portalName = portalName; }
    public OffsetDateTime getLastChecked() { return lastChecked; }
    public void setLastChecked(OffsetDateTime lastChecked) { this.lastChecked = lastChecked; }
    public OffsetDateTime getLastSuccess() { return lastSuccess; }
    public void setLastSuccess(OffsetDateTime lastSuccess) { this.lastSuccess = lastSuccess; }
    public Integer getLastHttpStatus() { return lastHttpStatus; }
    public void setLastHttpStatus(Integer lastHttpStatus) { this.lastHttpStatus = lastHttpStatus; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public OffsetDateTime getLastChangeDetected() { return lastChangeDetected; }
    public void setLastChangeDetected(OffsetDateTime lastChangeDetected) { this.lastChangeDetected = lastChangeDetected; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getAdapter() { return adapter == null ? "HTML" : adapter; }
    public void setAdapter(String adapter) { this.adapter = adapter; }
    public String getTrust() { return trust == null ? "OFFICIAL" : trust; }
    public void setTrust(String trust) { this.trust = trust; }
    public String getItemMatch() { return itemMatch; }
    public void setItemMatch(String itemMatch) { this.itemMatch = itemMatch; }
    public boolean isAutoApply() { return Boolean.TRUE.equals(autoApply); }
    public void setAutoApply(Boolean autoApply) { this.autoApply = autoApply; }
    public boolean isDiscoverLinks() { return Boolean.TRUE.equals(discoverLinks); }
    public void setDiscoverLinks(Boolean discoverLinks) { this.discoverLinks = discoverLinks; }
    public String getLinkFilter() { return linkFilter; }
    public void setLinkFilter(String linkFilter) { this.linkFilter = linkFilter; }
    public boolean isDiscovered() { return Boolean.TRUE.equals(discovered); }
    public void setDiscovered(Boolean discovered) { this.discovered = discovered; }
    public String getDiscoveredFrom() { return discoveredFrom; }
    public void setDiscoveredFrom(String discoveredFrom) { this.discoveredFrom = discoveredFrom; }
    public int getConsecutiveFailures() { return consecutiveFailures; }
    public void setConsecutiveFailures(int consecutiveFailures) { this.consecutiveFailures = consecutiveFailures; }
}
