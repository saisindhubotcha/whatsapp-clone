package com.example.websocketdemo.sharding;

import com.example.websocketdemo.model.Chat;
import com.example.websocketdemo.model.Message;
import com.example.websocketdemo.repository.ChatRepository;
import com.example.websocketdemo.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ShardedRepository {

    private final ChatRepository chatRepository;
    private final MessageRepository messageRepository;
    private final ShardRouter shardRouter;

    @Value("${sharding.enabled:false}")
    private boolean shardingEnabled;

    @Transactional
    public Optional<Chat> findChatById(Long chatId) {
        if (!shardingEnabled) {
            return chatRepository.findById(chatId);
        }

        String primaryShard = shardRouter.resolveShard(chatId);
        String originalShard = ShardContext.getShard();
        
        try {
            ShardContext.setShard(primaryShard);
            Optional<Chat> result = chatRepository.findById(chatId);
            
            if (result.isEmpty() && !shardRouter.hasExplicitMapping(chatId)) {
                // Try fallback shard for migration scenarios
                List<String> allShards = shardRouter.getAllShards();
                for (String shard : allShards) {
                    if (!shard.equals(primaryShard)) {
                        ShardContext.setShard(shard);
                        result = chatRepository.findById(chatId);
                        if (result.isPresent()) {
                            // Migrate data to new shard
                            migrateChatData(chatId, shard, primaryShard);
                            ShardContext.setShard(primaryShard);
                            result = chatRepository.findById(chatId);
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
    public List<Message> findMessagesByChatId(Long chatId) {
        if (!shardingEnabled) {
            return messageRepository.findByChatIdOrderByCreatedAtAsc(chatId);
        }

        String primaryShard = shardRouter.resolveShard(chatId);
        String originalShard = ShardContext.getShard();
        
        try {
            ShardContext.setShard(primaryShard);
            List<Message> result = messageRepository.findByChatIdOrderByCreatedAtAsc(chatId);
            
            if (result.isEmpty() && !shardRouter.hasExplicitMapping(chatId)) {
                // Try fallback shard for migration scenarios
                List<String> allShards = shardRouter.getAllShards();
                for (String shard : allShards) {
                    if (!shard.equals(primaryShard)) {
                        ShardContext.setShard(shard);
                        result = messageRepository.findByChatIdOrderByCreatedAtAsc(chatId);
                        if (!result.isEmpty()) {
                            // Migrate data to new shard
                            migrateMessageData(chatId, shard, primaryShard);
                            ShardContext.setShard(primaryShard);
                            result = messageRepository.findByChatIdOrderByCreatedAtAsc(chatId);
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
    public Chat saveChat(Chat chat) {
        if (!shardingEnabled) {
            return chatRepository.save(chat);
        }

        String shard = shardRouter.resolveShard(chat.getId());
        String originalShard = ShardContext.getShard();
        
        try {
            ShardContext.setShard(shard);
            return chatRepository.save(chat);
        } finally {
            if (originalShard != null) {
                ShardContext.setShard(originalShard);
            } else {
                ShardContext.clear();
            }
        }
    }

    @Transactional
    public Message saveMessage(Message message) {
        if (!shardingEnabled) {
            return messageRepository.save(message);
        }

        String shard = shardRouter.resolveShard(message.getChatId());
        String originalShard = ShardContext.getShard();
        
        try {
            ShardContext.setShard(shard);
            return messageRepository.save(message);
        } finally {
            if (originalShard != null) {
                ShardContext.setShard(originalShard);
            } else {
                ShardContext.clear();
            }
        }
    }

    @Transactional
    public void deleteChat(Long chatId) {
        if (!shardingEnabled) {
            chatRepository.deleteById(chatId);
            return;
        }

        String shard = shardRouter.resolveShard(chatId);
        String originalShard = ShardContext.getShard();
        
        try {
            ShardContext.setShard(shard);
            chatRepository.deleteById(chatId);
        } finally {
            if (originalShard != null) {
                ShardContext.setShard(originalShard);
            } else {
                ShardContext.clear();
            }
        }
    }

    private void executeWithShard(String shardKey, Runnable operation) {
        String originalShard = ShardContext.getShard();
        try {
            ShardContext.setShard(shardKey);
            operation.run();
        } finally {
            if (originalShard != null) {
                ShardContext.setShard(originalShard);
            } else {
                ShardContext.clear();
            }
        }
    }

    private void migrateChatData(Long chatId, String fromShard, String toShard) {
        try {
            executeWithShard(fromShard, () -> {
                Optional<Chat> chat = chatRepository.findById(chatId);
                if (chat.isPresent()) {
                    executeWithShard(toShard, () -> {
                        chatRepository.save(chat.get());
                    });
                    // Update explicit mapping
                    shardRouter.setShardMapping(chatId, toShard);
                    // Optionally clean up old shard data after verification
                    executeWithShard(fromShard, () -> {
                        chatRepository.delete(chat.get());
                    });
                }
            });
        } catch (Exception e) {
            System.err.println("Failed to migrate chat data: " + e.getMessage());
        }
    }

    private void migrateMessageData(Long chatId, String fromShard, String toShard) {
        try {
            executeWithShard(fromShard, () -> {
                List<Message> messages = messageRepository.findByChatIdOrderByCreatedAtAsc(chatId);
                if (!messages.isEmpty()) {
                    executeWithShard(toShard, () -> {
                        messageRepository.saveAll(messages);
                    });
                    // Update explicit mapping
                    shardRouter.setShardMapping(chatId, toShard);
                    // Optionally clean up old shard data after verification
                    executeWithShard(fromShard, () -> {
                        messageRepository.deleteAll(messages);
                    });
                }
            });
        } catch (Exception e) {
            System.err.println("Failed to migrate message data: " + e.getMessage());
        }
    }
}
