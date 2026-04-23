package com.example.websocketdemo.sharding;

import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConsistentHashRing {
    private final TreeMap<Long, String> ring = new TreeMap<>();
    private final Map<String, Set<Long>> shardToHashes = new ConcurrentHashMap<>();
    private final int virtualNodes = 150;
    private final MessageDigest md5;

    public ConsistentHashRing() {
        try {
            this.md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }

    public synchronized void addShard(String shardKey) {
        for (int i = 0; i < virtualNodes; i++) {
            String virtualNodeKey = shardKey + ":" + i;
            long hash = hash(virtualNodeKey);
            ring.put(hash, shardKey);
            shardToHashes.computeIfAbsent(shardKey, k -> ConcurrentHashMap.newKeySet()).add(hash);
        }
    }

    public synchronized void removeShard(String shardKey) {
        Set<Long> hashes = shardToHashes.remove(shardKey);
        if (hashes != null) {
            hashes.forEach(ring::remove);
        }
    }

    public String getShard(String key) {
        if (ring.isEmpty()) {
            return null;
        }

        long hash = hash(key);
        Map.Entry<Long, String> entry = ring.ceilingEntry(hash);
        
        if (entry == null) {
            entry = ring.firstEntry();
        }
        
        return entry.getValue();
    }

    public Set<String> getAllShards() {
        return new HashSet<>(shardToHashes.keySet());
    }

    private long hash(String key) {
        md5.reset();
        byte[] digest = md5.digest(key.getBytes());
        
        long hash = ((long) digest[0] & 0xFF) << 24;
        hash |= ((long) digest[1] & 0xFF) << 16;
        hash |= ((long) digest[2] & 0xFF) << 8;
        hash |= (digest[3] & 0xFF);
        
        return hash & 0xffffffffL;
    }

    public synchronized void initializeShards(List<String> shardKeys) {
        ring.clear();
        shardToHashes.clear();
        shardKeys.forEach(this::addShard);
    }
}
