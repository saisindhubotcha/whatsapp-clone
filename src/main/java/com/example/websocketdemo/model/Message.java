package com.example.websocketdemo.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Builder;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "messages")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;
    
    @Column(name = "sender_username", nullable = false)
    private String senderUsername;
    
    @Column(columnDefinition = "TEXT")
    private String content;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageType type;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "read_at")
    private LocalDateTime readAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "edited_at")
    private LocalDateTime editedAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
    
    // Utility method for JSON serialization
    @JsonIgnore
    public Long getCreatedAtMillis() {
        return createdAt != null ? createdAt.atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() : null;
    }
    
    @JsonIgnore
    public Long getDeliveredAtMillis() {
        return deliveredAt != null ? deliveredAt.atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() : null;
    }
    
    @JsonIgnore
    public Long getReadAtMillis() {
        return readAt != null ? readAt.atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() : null;
    }
    
    @JsonIgnore
    public Long getEditedAtMillis() {
        return editedAt != null ? editedAt.atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() : null;
    }
    
    @JsonIgnore
    public Long getDeletedAtMillis() {
        return deletedAt != null ? deletedAt.atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() : null;
    }
    
    @Column(name = "message_id", unique = true)
    private String messageId;
    
    @ElementCollection
    @CollectionTable(name = "message_read_by_users", joinColumns = @JoinColumn(name = "message_id"))
    @Column(name = "username")
    private Set<String> readByUsers = new HashSet<>();
    
    public enum MessageType {
        CHAT,
        JOIN,
        LEAVE,
        SYSTEM
    }
}
