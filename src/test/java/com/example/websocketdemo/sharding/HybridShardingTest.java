package com.example.websocketdemo.sharding;

import com.example.websocketdemo.model.Chat;
import com.example.websocketdemo.model.Message;
import com.example.websocketdemo.service.ChatService;
import com.example.websocketdemo.service.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class HybridShardingTest {

    @Autowired
    private ShardRouter shardRouter;

    @Autowired
    private ConsistentHashRing hashRing;

    @Autowired
    private ChatService chatService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
        // Clear Redis metadata
        redisTemplate.getConnectionFactory().getConnection().flushAll();
        
        // Initialize shards
        shardRouter.initializeShards();
    }

    @Test
    void testConsistentHashingDistribution() {
        // Test that different chatIds are distributed across shards
        String shard1 = shardRouter.resolveShard(1L);
        String shard2 = shardRouter.resolveShard(2L);
        String shard3 = shardRouter.resolveShard(3L);

        assertNotNull(shard1);
        assertNotNull(shard2);
        assertNotNull(shard3);

        // Test consistency - same chatId should always resolve to same shard
        String shard1Again = shardRouter.resolveShard(1L);
        assertEquals(shard1, shard1Again);
    }

    @Test
    void testRedisMetadataOverride() {
        // Test that Redis metadata overrides consistent hashing
        Long chatId = 100L;
        
        // Get initial shard from consistent hashing
        String initialShard = shardRouter.resolveShard(chatId);
        
        // Set explicit mapping in Redis
        String overrideShard = "shard2";
        shardRouter.setShardMapping(chatId, overrideShard);
        
        // Verify override is used
        String resolvedShard = shardRouter.resolveShard(chatId);
        assertEquals(overrideShard, resolvedShard);
        
        // Verify explicit mapping detection
        assertTrue(shardRouter.hasExplicitMapping(chatId));
    }

    @Test
    void testShardContext() {
        // Test thread-local shard context
        assertNull(ShardContext.getShard());
        
        ShardContext.setShard("test-shard");
        assertEquals("test-shard", ShardContext.getShard());
        
        ShardContext.clear();
        assertNull(ShardContext.getShard());
    }

    @Test
    void testChatCreationWithSharding() {
        // Test chat creation uses sharding
        Map<String, Object> result = chatService.createChat("Test Chat", "user1", List.of("user2"));
        
        assertTrue((Boolean) result.get("success"));
        Long chatId = (Long) result.get("chatId");
        assertNotNull(chatId);
        
        // Verify shard mapping exists
        String resolvedShard = shardRouter.resolveShard(chatId);
        assertNotNull(resolvedShard);
    }

    @Test
    void testMessageSendingWithSharding() {
        // Create chat first
        Map<String, Object> chatResult = chatService.createChat("Test Chat", "user1", List.of("user2"));
        Long chatId = (Long) chatResult.get("chatId");
        
        // Send message
        Map<String, Object> msgResult = chatService.sendMessage(chatId, "user1", "Hello World", null);
        
        assertTrue((Boolean) msgResult.get("success"));
        Message message = (Message) msgResult.get("message");
        assertNotNull(message);
        assertEquals(chatId, message.getChatId());
    }

    @Test
    void testCrossShardQuery() {
        // Create multiple chats that should go to different shards
        Map<String, Object> chat1 = chatService.createChat("Chat 1", "user1", List.of());
        Map<String, Object> chat2 = chatService.createChat("Chat 2", "user1", List.of());
        Map<String, Object> chat3 = chatService.createChat("Chat 3", "user1", List.of());
        
        // Get user chats - should query all shards and merge results
        List<Chat> userChats = chatService.getUserChats("user1");
        
        assertEquals(3, userChats.size());
        
        // Verify chats are sorted by last message time
        // (All should have same creation time, but verify no duplicates)
        assertTrue(userChats.stream().distinct().count() == 3);
    }

    @Test
    void testLazyMigration() {
        // This test would require setting up multiple data sources
        // For now, we'll test the metadata update logic
        
        Long chatId = 200L;
        String oldShard = "shard0";
        String newShard = "shard1";
        
        // Simulate migration scenario
        shardRouter.migrateShardMapping(chatId, oldShard, newShard);
        
        // Verify new mapping
        String resolvedShard = shardRouter.resolveShard(chatId);
        assertEquals(newShard, resolvedShard);
    }

    @Test
    void testHashRingOperations() {
        // Test hash ring operations
        List<String> initialShards = List.of("shard0", "shard1", "shard2");
        hashRing.initializeShards(initialShards);
        
        assertEquals(3, hashRing.getAllShards().size());
        
        // Test shard resolution
        String shard = hashRing.getShard("test-key");
        assertNotNull(shard);
        assertTrue(initialShards.contains(shard));
        
        // Test adding new shard
        hashRing.addShard("shard3");
        assertEquals(4, hashRing.getAllShards().size());
        
        // Test removing shard
        hashRing.removeShard("shard3");
        assertEquals(3, hashRing.getAllShards().size());
    }

    @Test
    void testMetadataTTL() {
        Long chatId = 300L;
        shardRouter.setShardMapping(chatId, "shard1");
        
        // Verify mapping exists
        String resolvedShard = shardRouter.resolveShard(chatId);
        assertEquals("shard1", resolvedShard);
        
        // Test TTL refresh
        shardRouter.refreshMappingTtl(chatId);
        
        // Should still resolve to same shard
        resolvedShard = shardRouter.resolveShard(chatId);
        assertEquals("shard1", resolvedShard);
    }

    @Test
    void testRemoveShardMapping() {
        Long chatId = 400L;
        shardRouter.setShardMapping(chatId, "shard2");
        
        // Verify mapping exists
        assertTrue(shardRouter.hasExplicitMapping(chatId));
        
        // Remove mapping
        shardRouter.removeShardMapping(chatId);
        
        // Verify mapping is gone and falls back to consistent hashing
        assertFalse(shardRouter.hasExplicitMapping(chatId));
        String resolvedShard = shardRouter.resolveShard(chatId);
        assertNotNull(resolvedShard); // Should still resolve to some shard via hashing
    }
}
