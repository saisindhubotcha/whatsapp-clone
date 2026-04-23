package com.example.websocketdemo.sharding;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

public class ShardingRoutingDataSource extends AbstractRoutingDataSource {

    public ShardingRoutingDataSource(Map<String, DataSource> dataSources) {
        setDefaultTargetDataSource(dataSources.values().iterator().next());
        setTargetDataSources(new HashMap<>(dataSources));
    }

    @Override
    protected Object determineCurrentLookupKey() {
        return ShardContext.getShard();
    }
}
