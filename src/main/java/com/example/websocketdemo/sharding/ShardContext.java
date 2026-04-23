package com.example.websocketdemo.sharding;

public class ShardContext {
    private static final ThreadLocal<String> currentShard = new ThreadLocal<>();

    public static void setShard(String shardKey) {
        currentShard.set(shardKey);
    }

    public static String getShard() {
        return currentShard.get();
    }

    public static void clear() {
        currentShard.remove();
    }
}
