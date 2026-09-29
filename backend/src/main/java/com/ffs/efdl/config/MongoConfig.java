package com.ffs.efdl.config;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;

/**
 * Format des documents stockés :
 * Spring Data n'écrit pas de champ {@code _class} (le type est porté par {@code className}).
 * Les index ne sont jamais créés automatiquement (spring.data.mongodb.auto-index-creation=false).
 */
@Configuration
public class MongoConfig {

    @Bean
    static BeanPostProcessor removeTypeKeyFromDocuments() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof MappingMongoConverter converter) {
                    converter.setTypeMapper(new DefaultMongoTypeMapper(null));
                }
                return bean;
            }
        };
    }
}
