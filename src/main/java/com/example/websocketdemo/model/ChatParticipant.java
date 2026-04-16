package com.example.websocketdemo.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Builder;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_participants")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatParticipant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_id", nullable = false)
    private Chat chat;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_username", nullable = false)
    private User user;
    
    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;
    
    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;
    
    @Column(name = "is_admin", nullable = false)
    private Boolean isAdmin = false;
    
    @Column(name = "left_at")
    private LocalDateTime leftAt;
}
