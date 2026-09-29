package com.ffs.efdl.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.ffs.efdl.security.RoutePathInterceptor;
import com.ffs.efdl.security.PasswordChangeInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RoutePathInterceptor routePathInterceptor;
    private final PasswordChangeInterceptor passwordChangeInterceptor;

    public WebConfig(RoutePathInterceptor routePathInterceptor, PasswordChangeInterceptor passwordChangeInterceptor) {
        this.routePathInterceptor = routePathInterceptor;
        this.passwordChangeInterceptor = passwordChangeInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(passwordChangeInterceptor).addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**", "/api/tenant");
        registry.addInterceptor(routePathInterceptor).addPathPatterns("/api/**");
    }
}
