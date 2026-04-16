package com.example.websocketdemo.repository;

import com.example.websocketdemo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    
    Optional<User> findBySessionId(String sessionId);
    
    List<User> findByIsOnlineTrue();
    
    @Modifying
    @Query("UPDATE User u SET u.isOnline = false, u.lastSeen = :lastSeen WHERE u.username = :username")
    void setOffline(@Param("username") String username, @Param("lastSeen") LocalDateTime lastSeen);
    
    @Modifying
    @Query("UPDATE User u SET u.isOnline = true WHERE u.username = :username")
    void setOnline(@Param("username") String username);
    
    @Query("SELECT u FROM User u WHERE u.lastSeen > :since")
    List<User> findRecentlyActive(@Param("since") LocalDateTime since);
}
