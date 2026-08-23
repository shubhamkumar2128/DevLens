package com.devlens.spring.autoconfigure;

import com.devlens.core.DevLensConfiguration;
import com.devlens.core.DevLensInterceptorEngine;
import com.devlens.core.masking.DefaultSensitiveDataMasker;
import com.devlens.core.masking.SensitiveDataMasker;
import com.devlens.core.serialization.ObjectSerializer;
import com.devlens.core.serialization.ToStringSerializer;
import com.devlens.spring.aspect.DevLensAspect;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass({Aspect.class, DevLensInterceptorEngine.class})
@EnableConfigurationProperties(DevLensProperties.class)
public class DevLensAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ObjectSerializer devLensObjectSerializer() {
        return new ToStringSerializer();
    }

    @Bean
    @ConditionalOnMissingBean
    public SensitiveDataMasker devLensSensitiveDataMasker(DevLensProperties properties) {
        return new DefaultSensitiveDataMasker(properties.getMasking().getFields());
    }

    @Bean
    public DevLensConfiguration devLensConfiguration(DevLensProperties props) {
        return DevLensConfiguration.builder()
                .enabled(props.isEnabled())
                .executionTimeEnabled(props.getExecutionTime().isEnabled())
                .logInputEnabled(props.getLogInput().isEnabled())
                .logOutputEnabled(props.getLogOutput().isEnabled())
                .logExceptionEnabled(props.getLogException().isEnabled())
                .threadInfoEnabled(props.getThreadInfo().isEnabled())
                .correlationIdEnabled(props.getCorrelationId().isEnabled())
                .externalCallEnabled(props.getExternalCall().isEnabled())
                .retryEnabled(props.getRetry().isEnabled())
                .memoryUsageEnabled(props.getMemoryUsage().isEnabled())
                .maxOutputLength(props.getMaxOutputLength())
                .executionTimeThreshold(props.getExecutionTime().getThreshold())
                .build();
    }

    @Bean
    public DevLensInterceptorEngine devLensInterceptorEngine(
            ObjectSerializer serializer,
            SensitiveDataMasker masker,
            DevLensConfiguration config) {
        return new DevLensInterceptorEngine(serializer, masker, config);
    }

    @Bean
    @ConditionalOnClass(Aspect.class)
    public DevLensAspect devLensAspect(DevLensInterceptorEngine engine) {
        return new DevLensAspect(engine);
    }
}
