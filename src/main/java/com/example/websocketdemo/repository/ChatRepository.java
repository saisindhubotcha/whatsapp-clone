package com.example.websocketdemo.repository;

import com.example.websocketdemo.model.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRepository extends JpaRepository<Chat, Long> {

    @Query("SELECT DISTINCT c FROM Chat c JOIN ChatParticipant cp ON c.id = cp.chatId WHERE cp.userUsername = :username AND cp.leftAt IS NULL")
    List<Chat> findUserChats(@Param("username") String username);

    @Query("SELECT DISTINCT c FROM Chat c JOIN ChatParticipant cp ON c.id = cp.chatId WHERE cp.userUsername = :username AND cp.leftAt IS NULL ORDER BY c.lastMessageAt DESC")
    List<Chat> findUserChatsOrderByLastMessage(@Param("username") String username);

    @Query("SELECT c FROM Chat c WHERE c.createdBy = :username")
    List<Chat> findChatsCreatedByUser(@Param("username") String username);

    @Query("SELECT c FROM Chat c WHERE c.id = :chatId AND c.id IN (SELECT cp.chatId FROM ChatParticipant cp WHERE cp.userUsername = :username AND cp.leftAt IS NULL)")
    Optional<Chat> findUserChatById(@Param("username") String username, @Param("chatId") Long chatId);

    @Query("SELECT c.seqCounter FROM Chat c WHERE c.id = :chatId")
    Long findSeqCounterByChatId(@Param("chatId") Long chatId);

    @Modifying
    @Query("UPDATE Chat c SET c.seqCounter = c.seqCounter + 1 WHERE c.id = :chatId")
    void incrementSeqCounter(@Param("chatId") Long chatId);
}
