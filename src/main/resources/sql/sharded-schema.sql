-- Sharded Database Schema
-- This script creates the necessary tables for sharded Message and Chat entities
-- User and ChatParticipant tables remain on the main (non-sharded) database

-- Main Database Tables (Non-Sharded)
-- These tables exist on the default datasource and are not sharded

-- Users table - stores user information
CREATE TABLE IF NOT EXISTS users (
    username VARCHAR(255) PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL,
    last_seen DATETIME,
    is_online BOOLEAN NOT NULL DEFAULT FALSE,
    INDEX idx_users_online (is_online),
    INDEX idx_users_last_seen (last_seen)
);

-- Chat participants table - stores chat membership information
CREATE TABLE IF NOT EXISTS chat_participants (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    chat_id BIGINT NOT NULL,
    user_username VARCHAR(255) NOT NULL,
    joined_at DATETIME NOT NULL,
    last_read_message_id BIGINT,
    is_admin BOOLEAN NOT NULL DEFAULT FALSE,
    left_at DATETIME,
    INDEX idx_chat_participants_chat_id (chat_id),
    INDEX idx_chat_participants_user_username (user_username),
    INDEX idx_chat_participants_active (chat_id, user_username, left_at),
    FOREIGN KEY (user_username) REFERENCES users(username) ON DELETE CASCADE
);

-- Sharded Database Tables
-- These tables are created on each shard database and contain Message and Chat data

-- Messages table - stores chat messages (sharded by chat_id)
CREATE TABLE IF NOT EXISTS messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    chat_id BIGINT NOT NULL,
    sender_username VARCHAR(255) NOT NULL,
    content TEXT,
    type ENUM('CHAT', 'JOIN', 'LEAVE', 'SYSTEM') NOT NULL,
    created_at DATETIME NOT NULL,
    delivered_at DATETIME,
    read_at DATETIME,
    edited_at DATETIME,
    deleted_at DATETIME,
    message_id VARCHAR(255) UNIQUE NOT NULL,
    INDEX idx_messages_chat_id (chat_id),
    INDEX idx_messages_created_at (created_at),
    INDEX idx_messages_sender (sender_username),
    INDEX idx_messages_message_id (message_id),
    INDEX idx_messages_chat_created (chat_id, created_at)
);

-- Message read tracking table - tracks which users have read which messages
CREATE TABLE IF NOT EXISTS message_read_by_users (
    message_id VARCHAR(255) NOT NULL,
    username VARCHAR(255) NOT NULL,
    PRIMARY KEY (message_id, username),
    FOREIGN KEY (message_id) REFERENCES messages(message_id) ON DELETE CASCADE,
    FOREIGN KEY (username) REFERENCES users(username) ON DELETE CASCADE
);

-- Chats table - stores chat information (sharded by chat_id)
CREATE TABLE IF NOT EXISTS chats (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL,
    last_message_at DATETIME,
    last_message_id VARCHAR(255),
    is_group_chat BOOLEAN NOT NULL DEFAULT FALSE,
    INDEX idx_chats_created_by (created_by),
    INDEX idx_chats_last_message_at (last_message_at),
    INDEX idx_chats_created_at (created_at),
    FOREIGN KEY (created_by) REFERENCES users(username) ON DELETE CASCADE
);

-- Additional indexes for performance optimization
CREATE INDEX idx_messages_chat_type_created ON messages (chat_id, type, created_at);
CREATE INDEX idx_messages_unread ON messages (chat_id, sender_username, read_at) WHERE read_at IS NULL;

-- Stored procedures for common operations (optional)
DELIMITER //

-- Procedure to get user's unread message count across all chats
CREATE PROCEDURE IF NOT EXISTS GetUserUnreadCount(IN p_username VARCHAR(255))
BEGIN
    SELECT COUNT(*) as unread_count
    FROM messages m
    JOIN chat_participants cp ON m.chat_id = cp.chat_id
    WHERE cp.user_username = p_username 
    AND cp.left_at IS NULL
    AND m.sender_username != p_username
    AND m.id > IFNULL(cp.last_read_message_id, 0)
    AND m.read_at IS NULL;
END //

-- Procedure to mark messages as read for a user in a chat
CREATE PROCEDURE IF NOT EXISTS MarkMessagesAsRead(IN p_chat_id BIGINT, IN p_username VARCHAR(255), IN p_read_time DATETIME)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;
    
    START TRANSACTION;
    
    -- Update message read status
    UPDATE messages 
    SET read_at = p_read_time 
    WHERE chat_id = p_chat_id 
    AND sender_username != p_username 
    AND read_at IS NULL;
    
    -- Update participant's last read message
    UPDATE chat_participants 
    SET last_read_message_id = (
        SELECT MAX(id) 
        FROM messages 
        WHERE chat_id = p_chat_id 
        AND sender_username != p_username
    )
    WHERE chat_id = p_chat_id 
    AND user_username = p_username;
    
    COMMIT;
END //

DELIMITER ;

-- Views for common queries
CREATE VIEW IF NOT EXISTS user_chat_summary AS
SELECT 
    c.id as chat_id,
    c.name as chat_name,
    c.created_by,
    c.created_at,
    c.last_message_at,
    c.last_message_id,
    c.is_group_chat,
    cp.user_username,
    cp.joined_at,
    cp.is_admin,
    cp.last_read_message_id,
    CASE 
        WHEN m.sender_username != cp.user_username AND m.read_at IS NULL THEN 1
        ELSE 0
    END as has_unread_messages
FROM chats c
JOIN chat_participants cp ON c.id = cp.chat_id
LEFT JOIN messages m ON c.id = m.chat_id AND m.id = IFNULL(cp.last_read_message_id, 0) + 1
WHERE cp.left_at IS NULL;
