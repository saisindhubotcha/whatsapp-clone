-- MySQL Shard Setup Script
-- Run this script to create the shard databases

-- Create shard databases
CREATE DATABASE IF NOT EXISTS chat_shard0 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS chat_shard1 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS chat_shard2 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Grant permissions (adjust username as needed)
-- GRANT ALL PRIVILEGES ON chat_shard0.* TO 'root'@'localhost';
-- GRANT ALL PRIVILEGES ON chat_shard1.* TO 'root'@'localhost';
-- GRANT ALL PRIVILEGES ON chat_shard2.* TO 'root'@'localhost';

-- Flush privileges
FLUSH PRIVILEGES;
