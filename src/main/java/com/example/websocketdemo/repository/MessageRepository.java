package com.example.websocketdemo.repository;

import com.example.websocketdemo.model.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    
    List<Message> findByChatIdOrderByCreatedAtAsc(Long chatId);
    
    Page<Message> findByChatIdOrderByCreatedAtAsc(Long chatId, Pageable pageable);
    
    @Query("SELECT m FROM Message m WHERE m.chatId = :chatId AND m.createdAt > :since ORDER BY m.createdAt ASC")
    List<Message> findMessagesAfter(@Param("chatId") Long chatId, @Param("since") LocalDateTime since);
    
    @Query("SELECT m FROM Message m WHERE m.chatId = :chatId AND m.id > :lastReadMessageId ORDER BY m.createdAt ASC")
    List<Message> findUnreadMessages(@Param("chatId") Long chatId, @Param("lastReadMessageId") Long lastReadMessageId);
    
    @Query("SELECT m FROM Message m WHERE m.chatId = :chatId AND m.senderUsername != :username AND m.readAt IS NULL ORDER BY m.createdAt ASC")
    List<Message> findUnreadMessagesForUser(@Param("chatId") Long chatId, @Param("username") String username);
    
    @Modifying
    @Query("UPDATE Message m SET m.readAt = :readAt WHERE m.chatId = :chatId AND m.senderUsername != :username AND m.readAt IS NULL")
    void markMessagesAsRead(@Param("chatId") Long chatId, @Param("username") String username, @Param("readAt") LocalDateTime readAt);
    
    @Modifying
    @Query("UPDATE Message m SET m.deliveredAt = :deliveredAt WHERE m.id IN :messageIds")
    void markMessagesAsDelivered(@Param("messageIds") List<Long> messageIds, @Param("deliveredAt") LocalDateTime deliveredAt);
    
    @Query("SELECT COUNT(m) FROM Message m WHERE m.chatId = :chatId AND m.senderUsername != :username AND m.readAt IS NULL")
    Long countUnreadMessages(@Param("chatId") Long chatId, @Param("username") String username);

    List<Message> findByChatIdOrderByCreatedAtDesc(Long chatId, Pageable pageable);
    
    List<Message> findByChatIdAndCreatedAtBeforeOrderByCreatedAtDesc(Long chatId, LocalDateTime before, Pageable pageable);
    
    Optional<Message> findByMessageId(String messageId);

    Long countByChatId(Long chatId);

    Optional<Message> findTopByChatIdOrderByCreatedAtDesc(Long chatId);
}
