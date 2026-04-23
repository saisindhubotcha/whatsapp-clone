package com.example.websocketdemo.config;

import com.example.websocketdemo.sharding.ShardingRoutingDataSource;
import com.example.websocketdemo.sharding.ShardRouter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class ShardingConfig {

    @Autowired
    private ShardRouter shardRouter;

    @Value("#{'${sharding.datasource.urls}'.split(',')}")
    private List<String> datasourceUrls;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${spring.datasource.driverClassName}")
    private String driverClassName;

    @Value("${sharding.enabled:false}")
    private boolean shardingEnabled;

    @Bean(name = "shardedDataSource")
    public DataSource shardedDataSource() {
        if (!shardingEnabled) {
            return createDefaultDataSource();
        }

        Map<String, DataSource> dataSources = new HashMap<>();
        
        for (int i = 0; i < datasourceUrls.size(); i++) {
            String shardKey = "shard" + i;
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName(driverClassName);
            dataSource.setUrl(datasourceUrls.get(i));
            dataSource.setUsername(username);
            dataSource.setPassword(password);
            dataSources.put(shardKey, dataSource);
        }

        return new ShardingRoutingDataSource(dataSources);
    }

    private DataSource createDefaultDataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(driverClassName);
        dataSource.setUrl(datasourceUrls.get(0));
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return dataSource;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeSharding() {
        if (shardingEnabled) {
            shardRouter.initializeShards();
        }
    }
}
