package com.example.websocketdemo.repository;

import com.example.websocketdemo.model.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRepository extends JpaRepository<Chat, Long> {
    
    @Query("SELECT c FROM Chat c JOIN c.participants cp WHERE cp.user.username = :username AND cp.leftAt IS NULL")
    List<Chat> findUserChats(@Param("username") String username);
    
    @Query("SELECT c FROM Chat c JOIN c.participants cp WHERE cp.user.username = :username AND cp.leftAt IS NULL ORDER BY c.lastMessageAt DESC")
    List<Chat> findUserChatsOrderByLastMessage(@Param("username") String username);
    
    @Query("SELECT c FROM Chat c WHERE c.createdBy = :username")
    List<Chat> findChatsCreatedByUser(@Param("username") String username);
    
    @Query("SELECT c FROM Chat c JOIN c.participants cp WHERE cp.user.username = :username AND cp.leftAt IS NULL AND c.id = :chatId")
    Optional<Chat> findUserChatById(@Param("username") String username, @Param("chatId") Long chatId);
}
