package com.salon.config;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.*;
@Configuration
public class ReportStreamingConfig implements WebMvcConfigurer {
 @Bean public ThreadPoolTaskExecutor csvExportExecutor(){var executor=new ThreadPoolTaskExecutor();executor.setCorePoolSize(2);executor.setMaxPoolSize(4);executor.setQueueCapacity(20);executor.setThreadNamePrefix("csv-report-");executor.setAwaitTerminationSeconds(20);return executor;}
 @Override public void configureAsyncSupport(AsyncSupportConfigurer configurer){configurer.setTaskExecutor(csvExportExecutor()).setDefaultTimeout(120000);}
}
