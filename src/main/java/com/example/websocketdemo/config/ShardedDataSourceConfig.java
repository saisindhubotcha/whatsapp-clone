package com.example.websocketdemo.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
    basePackages = "com.example.websocketdemo.repository",
    includeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
        type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
        classes = {com.example.websocketdemo.repository.ChatRepository.class, com.example.websocketdemo.repository.MessageRepository.class}
    ),
    entityManagerFactoryRef = "shardedEntityManagerFactory",
    transactionManagerRef = "shardedTransactionManager"
)
public class ShardedDataSourceConfig {

    @Bean
    public LocalContainerEntityManagerFactoryBean shardedEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("shardedDataSource") DataSource dataSource) {
        return builder
                .dataSource(dataSource)
                .packages("com.example.websocketdemo.model")
                .persistenceUnit("sharded")
                .build();
    }

    @Bean
    public PlatformTransactionManager shardedTransactionManager(
            @Qualifier("shardedEntityManagerFactory") LocalContainerEntityManagerFactoryBean shardedEntityManagerFactory) {
        return new JpaTransactionManager(shardedEntityManagerFactory.getObject());
    }
}
