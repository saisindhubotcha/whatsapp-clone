package com.example.websocketdemo.sharding;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class ShardRouter {

    @Autowired
    private ConsistentHashRing hashRing;

    @Autowired
    @Qualifier("shardingRedisTemplate")
    private RedisTemplate<String, String> redisTemplate;

    @Value("${sharding.default.shards:shard0,shard1,shard2}")
    private List<String> defaultShards;

    @Value("${sharding.metadata.ttl:86400}")
    private long metadataTtlSeconds;

    private static final String SHARD_METADATA_KEY_PREFIX = "chat:shard:";

    private String getShardMetadataKey(Long chatId) {
        return SHARD_METADATA_KEY_PREFIX + chatId;
    }

    public String resolveShard(Long chatId) {
        if (chatId == null) {
            return getDefaultShard();
        }

        String metadataKey = getShardMetadataKey(chatId);
        String shardFromRedis = redisTemplate.opsForValue().get(metadataKey);

        if (shardFromRedis != null && !shardFromRedis.isEmpty()) {
            return shardFromRedis;
        }

        String shardFromHash = hashRing.getShard(chatId.toString());
        return shardFromHash != null ? shardFromHash : getDefaultShard();
    }

    public void setShardMapping(Long chatId, String shardId) {
        String metadataKey = getShardMetadataKey(chatId);
        redisTemplate.opsForValue().set(metadataKey, shardId, metadataTtlSeconds, TimeUnit.SECONDS);
    }

    public void removeShardMapping(Long chatId) {
        String metadataKey = getShardMetadataKey(chatId);
        redisTemplate.delete(metadataKey);
    }

    public String getDefaultShard() {
        return defaultShards.isEmpty() ? "shard0" : defaultShards.get(0);
    }

    public void initializeShards() {
        hashRing.initializeShards(defaultShards);
    }

    public String resolveShardWithFallback(Long chatId) {
        String primaryShard = resolveShard(chatId);
        return primaryShard != null ? primaryShard : getDefaultShard();
    }

    public boolean hasExplicitMapping(Long chatId) {
        String metadataKey = getShardMetadataKey(chatId);
        String shardFromRedis = redisTemplate.opsForValue().get(metadataKey);
        return shardFromRedis != null && !shardFromRedis.isEmpty();
    }

    public void migrateShardMapping(Long chatId, String oldShard, String newShard) {
        setShardMapping(chatId, newShard);
    }

    public void refreshMappingTtl(Long chatId) {
        String metadataKey = getShardMetadataKey(chatId);
        String existingShard = redisTemplate.opsForValue().get(metadataKey);
        if (existingShard != null) {
            redisTemplate.expire(metadataKey, metadataTtlSeconds, TimeUnit.SECONDS);
        }
    }

    public List<String> getAllShards() {
        return defaultShards;
    }
}
