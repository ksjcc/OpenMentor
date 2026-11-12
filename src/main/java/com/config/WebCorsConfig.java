package com.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 开发期 CORS。
 *
 * <p>前端是独立静态站点（默认 http://localhost:5173），和后端不同源，
 * 不加这个配置浏览器会拦掉所有 /api 请求。
 *
 * <p>生产环境的两种正确做法（二选一，然后把这个类删掉）：
 * <ol>
 *   <li>把 frontend 目录复制到 {@code src/main/resources/static/app}，同源访问</li>
 *   <li>由 Nginx 同域反代 /api，前端只发相对路径</li>
 * </ol>
 * 直接把 {@code allowedOriginPatterns} 放宽成 {@code *} 不属于其中之一。
 */
@Configuration
public class WebCorsConfig implements WebMvcConfigurer {

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/api/**")
        .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        .exposedHeaders("Content-Type")
        .allowCredentials(true)
        .maxAge(3600);
  }
}
