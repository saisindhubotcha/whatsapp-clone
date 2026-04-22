package com.example.websocketdemo.repository;

import com.example.websocketdemo.model.ChatParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, Long> {
    
    Optional<ChatParticipant> findByChatIdAndUserUsername(Long chatId, String username);
    
    List<ChatParticipant> findByChatIdAndLeftAtIsNull(Long chatId);
    
    List<ChatParticipant> findByUserUsernameAndLeftAtIsNull(String username);
    
    @Query("SELECT cp FROM ChatParticipant cp WHERE cp.chatId = :chatId AND cp.leftAt IS NULL ORDER BY cp.joinedAt")
    List<ChatParticipant> findActiveParticipantsByChatId(@Param("chatId") Long chatId);
    
    @Modifying
    @Query("UPDATE ChatParticipant cp SET cp.leftAt = :leftAt WHERE cp.chatId = :chatId AND cp.userUsername = :username")
    void leaveChat(@Param("chatId") Long chatId, @Param("username") String username, @Param("leftAt") LocalDateTime leftAt);
    
    @Query("SELECT COUNT(cp) FROM ChatParticipant cp WHERE cp.chatId = :chatId AND cp.leftAt IS NULL")
    Long countActiveParticipants(@Param("chatId") Long chatId);
}
