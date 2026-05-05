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
import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(Message.MessageId.class)
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.TABLE, generator = "message_id_gen")
    @TableGenerator(name = "message_id_gen", table = "id_generator", pkColumnName = "gen_name", valueColumnName = "gen_val", pkColumnValue = "message_id", allocationSize = 50)
    private Long id;

    @Id
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

    public enum MessageType {
        CHAT,
        JOIN,
        LEAVE,
        SYSTEM
    }

    /**
     * Composite primary key class for partitioned table support
     * Required for MySQL HASH partitioning on chat_id
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageId implements Serializable {
        private Long id;
        private Long chatId;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MessageId messageId = (MessageId) o;
            return java.util.Objects.equals(id, messageId.id) &&
                   java.util.Objects.equals(chatId, messageId.chatId);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(id, chatId);
        }
    }
}
