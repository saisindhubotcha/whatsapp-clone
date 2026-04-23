package com.example.websocketdemo.sharding;

import com.example.websocketdemo.model.Chat;
import com.example.websocketdemo.repository.ChatRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class ChatShardedRepository {

    @Autowired
    private ChatRepository chatRepository;

    @Autowired
    private ShardRouter shardRouter;

    @Value("${sharding.enabled:false}")
    private boolean shardingEnabled;

    @Transactional
    public List<Chat> findUserChats(String username) {
        if (!shardingEnabled) {
            return chatRepository.findUserChatsOrderByLastMessage(username);
        }

        List<Chat> allChats = new ArrayList<>();
        List<String> allShards = shardRouter.getAllShards();
        
        for (String shard : allShards) {
            String originalShard = ShardContext.getShard();
            try {
                ShardContext.setShard(shard);
                allChats.addAll(chatRepository.findUserChatsOrderByLastMessage(username));
            } finally {
                if (originalShard != null) {
                    ShardContext.setShard(originalShard);
                } else {
                    ShardContext.clear();
                }
            }
        }
        
        // Sort by last message time and remove duplicates
        return allChats.stream()
                .distinct()
                .sorted((a, b) -> {
                    if (a.getLastMessageAt() == null && b.getLastMessageAt() == null) return 0;
                    if (a.getLastMessageAt() == null) return 1;
                    if (b.getLastMessageAt() == null) return -1;
                    return b.getLastMessageAt().compareTo(a.getLastMessageAt());
                })
                .toList();
    }

    @Transactional
    public List<Chat> findChatsCreatedByUser(String username) {
        if (!shardingEnabled) {
            return chatRepository.findChatsCreatedByUser(username);
        }

        List<Chat> allChats = new ArrayList<>();
        List<String> allShards = shardRouter.getAllShards();
        
        for (String shard : allShards) {
            String originalShard = ShardContext.getShard();
            try {
                ShardContext.setShard(shard);
                allChats.addAll(chatRepository.findChatsCreatedByUser(username));
            } finally {
                if (originalShard != null) {
                    ShardContext.setShard(originalShard);
                } else {
                    ShardContext.clear();
                }
            }
        }
        
        return allChats.stream().distinct().toList();
    }

    @Transactional
    public Optional<Chat> findUserChatById(String username, Long chatId) {
        if (!shardingEnabled) {
            return chatRepository.findUserChatById(username, chatId);
        }

        String primaryShard = shardRouter.resolveShard(chatId);
        String originalShard = ShardContext.getShard();
        
        try {
            ShardContext.setShard(primaryShard);
            Optional<Chat> result = chatRepository.findUserChatById(username, chatId);
            
            if (result.isEmpty() && !shardRouter.hasExplicitMapping(chatId)) {
                // Try fallback shard for migration scenarios
                List<String> allShards = shardRouter.getAllShards();
                for (String shard : allShards) {
                    if (!shard.equals(primaryShard)) {
                        ShardContext.setShard(shard);
                        result = chatRepository.findUserChatById(username, chatId);
                        if (result.isPresent()) {
                            break;
                        }
                    }
                }
            }
            
            return result;
        } finally {
            if (originalShard != null) {
                ShardContext.setShard(originalShard);
            } else {
                ShardContext.clear();
            }
        }
    }

    @Transactional
    public List<Chat> findAllChats() {
        if (!shardingEnabled) {
            return chatRepository.findAll();
        }

        List<Chat> allChats = new ArrayList<>();
        List<String> allShards = shardRouter.getAllShards();
        
        for (String shard : allShards) {
            String originalShard = ShardContext.getShard();
            try {
                ShardContext.setShard(shard);
                allChats.addAll(chatRepository.findAll());
            } finally {
                if (originalShard != null) {
                    ShardContext.setShard(originalShard);
                } else {
                    ShardContext.clear();
                }
            }
        }
        
        return allChats.stream().distinct().toList();
    }
}
