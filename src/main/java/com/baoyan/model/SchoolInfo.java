package com.baoyan.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 招生/实验室信息数据模型（从 BaoyanApp.SchoolInfo 提取） */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class SchoolInfo {

    private Long   id;
    private String university;
    private String category;
    private String title;
    private String url;
    private String source;
    private String  snippet;
    private String  extraJson;   // ★ JSON: deadline/quota/gpaReq/members/pi 等
    private String  publishedAt;
    private String scrapedAt;

    public SchoolInfo() {}

    public SchoolInfo(String university, String category, String title, String url, String source) {
        this.university = university;
        this.category   = category;
        this.title      = title;
        this.url        = url;
        this.source     = source;
        this.scrapedAt  = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    public Long   getId()              { return id; }
    public void   setId(Long id)       { this.id = id; }
    public String getUniversity()      { return university; }
    public void   setUniversity(String v) { this.university = v; }
    public String getCategory()        { return category; }
    public void   setCategory(String v) { this.category = v; }
    public String getTitle()           { return title; }
    public void   setTitle(String v)   { this.title = v; }
    public String getUrl()             { return url; }
    public void   setUrl(String v)     { this.url = v; }
    public String getSource()          { return source; }
    public void   setSource(String v)  { this.source = v; }
    public String getSnippet()          { return snippet; }
    public void   setSnippet(String v)  { this.snippet = v; }
    public String getExtraJson()        { return extraJson; }
    public void   setExtraJson(String v){ this.extraJson = v; }
    public String getPublishedAt()      { return publishedAt; }
    public void   setPublishedAt(String v) { this.publishedAt = v; }
    public String getScrapedAt()       { return scrapedAt; }
    public void   setScrapedAt(String v) { this.scrapedAt = v; }
}