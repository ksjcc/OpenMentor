package com.config;

import com.github.xiaoymin.knife4j.spring.configuration.Knife4jProperties;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Knife4jConfig {

    @Bean
    public static BeanFactoryPostProcessor knife4jPropertiesPrimaryPostProcessor() {
        return new BeanFactoryPostProcessor() {
            @Override
            public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
                String[] beanNames = beanFactory.getBeanNamesForType(Knife4jProperties.class, true, false);
                for (String beanName : beanNames) {
                    if ("knife4jProperties".equals(beanName) && beanFactory.containsBeanDefinition(beanName)) {
                        BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
                        beanDefinition.setPrimary(true);
                    }
                }
            }
        };
    }

    @Bean
    public OpenAPI careerMindOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("CareerMind API")
                .description("CareerMind career interview agent backend API documentation.")
                .version("0.0.1")
                .contact(new Contact()
                    .name("CareerMind")
                    .email("career-mind@example.com"))
                .license(new License()
                    .name("Apache 2.0")
                    .url("https://www.apache.org/licenses/LICENSE-2.0")))
            .externalDocs(new ExternalDocumentation()
                .description("Knife4j")
                .url("https://doc.xiaominfo.com/"));
    }
}
