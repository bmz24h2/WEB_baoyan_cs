package com.baoyan.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 教师数据模型（从 BaoyanApp.Teacher 提取为独立顶层类） */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Teacher {

    private Long   id;
    private String name;
    private String university;
    private String department;
    private String profileUrl;
    private String title;          // 教授 / 副教授 / 讲师 / 研究员
    private String researchAreas;  // 从个人主页解析的研究方向
    private String  email;
    private String  googleScholar;
    private String  labName;       // ★ 所属课题组/实验室
    private Boolean recruiting;    // ★ 本年度是否在招保研
    private String  scrapedAt;
    private int     citedCount;    // ★ 被引总次数（来自 OpenAlex）
    private int     worksCount;    // ★ 论文总数
    private int     activeYear;    // ★ 最近活跃年份（counts_by_year 里最近有论文的年份）
    private String  countsByYear;  // ★ JSON: [{year,works_count,cited_by_count},...]

    public Teacher() {}

    public Teacher(String name, String university, String department, String profileUrl) {
        this.name        = name;
        this.university  = university;
        this.department  = department;
        this.profileUrl  = profileUrl;
        this.scrapedAt   = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    public Long   getId()              { return id; }
    public void   setId(Long id)       { this.id = id; }
    public String getName()            { return name; }
    public void   setName(String v)    { this.name = v; }
    public String getUniversity()      { return university; }
    public void   setUniversity(String v) { this.university = v; }
    public String getDepartment()      { return department; }
    public void   setDepartment(String v) { this.department = v; }
    public String getProfileUrl()      { return profileUrl; }
    public void   setProfileUrl(String v) { this.profileUrl = v; }
    public String getTitle()           { return title; }
    public void   setTitle(String v)   { this.title = v; }
    public String getResearchAreas()   { return researchAreas; }
    public void   setResearchAreas(String v) { this.researchAreas = v; }
    public String getEmail()           { return email; }
    public void   setEmail(String v)   { this.email = v; }
    public String getGoogleScholar()   { return googleScholar; }
    public void   setGoogleScholar(String v) { this.googleScholar = v; }
    public String  getLabName()        { return labName; }
    public void    setLabName(String v){ this.labName = v; }
    public Boolean getRecruiting()     { return recruiting; }
    public void    setRecruiting(Boolean v){ this.recruiting = v; }
    public String getScrapedAt()       { return scrapedAt; }
    public void   setScrapedAt(String v) { this.scrapedAt = v; }
    public int    getCitedCount()        { return citedCount; }
    public void   setCitedCount(int v)   { this.citedCount = v; }
    public int    getWorksCount()        { return worksCount; }
    public void   setWorksCount(int v)   { this.worksCount = v; }
    public int    getActiveYear()        { return activeYear; }
    public void   setActiveYear(int v)   { this.activeYear = v; }
    public String getCountsByYear()      { return countsByYear; }
    public void   setCountsByYear(String v){ this.countsByYear = v; }
}