package com.baoyan;

import com.baoyan.db.DatabaseService;
import com.baoyan.model.Teacher;
import com.baoyan.model.SchoolInfo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BaoyanApp — Spring Boot 启动入口
 *
 * 职责（精简后）：
 *   - main()：启动 Spring 容器，打印初始统计
 *   - corsConfigurer Bean：配置跨域策略
 *
 * 原内部类已迁移至独立文件：
 *   Teacher        → com.baoyan.model.Teacher
 *   SchoolInfo     → com.baoyan.model.SchoolInfo
 *   DatabaseService→ com.baoyan.db.DatabaseService
 *   ApiController  → com.baoyan.api.ApiController
 *
 * 向下兼容别名（其他文件短期内仍可用 BaoyanApp.DatabaseService）：
 *   暂保留 DatabaseService 的类型别名，待全项目 import 统一后删除。
 */
@SpringBootApplication
public class BaoyanApp {

    private static final Logger log = LoggerFactory.getLogger(BaoyanApp.class);

    public static void main(String[] args) {
        ApplicationContext ctx = SpringApplication.run(BaoyanApp.class, args);
        DatabaseService db = ctx.getBean(DatabaseService.class);
        db.initSchema();
        db.initStaticData();
        log.info("✅ 985/211 CS 保研导航已启动");
        log.info("   登录页面 → http://localhost:8080/html/login.html");
        log.info("   当前数据库: {} 条教师记录", db.countTeachers());
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOriginPatterns("*")
                        .allowedMethods("GET", "POST", "OPTIONS", "DELETE")
                        .allowedHeaders("*")
                        .maxAge(3600);
            }
        };
    }

    // ── 向下兼容类型别名（过渡期保留，全项目 import 完成后删除）─────────────
    /** @deprecated 请直接使用 com.baoyan.db.DatabaseService */
    @Deprecated public static final Class<DatabaseService> DatabaseServiceClass = DatabaseService.class;
}