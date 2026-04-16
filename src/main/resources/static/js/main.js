'use strict';

// DOM Elements
var usernamePage = document.querySelector('#username-page');
var chatPage = document.querySelector('#chat-page');
var usernameForm = document.querySelector('#usernameForm');
var messageForm = document.querySelector('#messageForm');
var messageInput = document.querySelector('#message');
var messageArea = document.querySelector('#messageArea');
var connectingElement = document.querySelector('#connectingStatus');
var chatList = document.querySelector('#chatList');
var currentChatName = document.querySelector('#currentChatName');
var currentUsername = document.querySelector('#currentUsername');
var currentUserAvatar = document.querySelector('#currentUserAvatar');
var chatCount = document.querySelector('#chatCount');
var chatParticipants = document.querySelector('#chatParticipants');
var createChatModal = document.querySelector('#createChatModal');
var createChatForm = document.querySelector('#createChatForm');

// State Management
var stompClient = null;
var username = null;
var currentChatId = null;
var userChats = [];
var unreadCounts = {};

var colors = [
    '#2196F3', '#32c787', '#00BCD4', '#ff5652',
    '#ffc107', '#ff85af', '#FF9800', '#39bbb0'
];

// Server-side focused functions
async function connect(event) {
    username = document.querySelector('#name').value.trim();

    if(username) {
        try {
            // Use REST API to register user
            const registerResponse = await fetch('/api/v1/users/register', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ username: username })
            });

            if (!registerResponse.ok) {
                throw new Error('Failed to register user');
            }

            const registerResult = await registerResponse.json();
            console.log('Registered user:', registerResult);

            // Set user as online
            await fetch(`/api/v1/users/${username}/online`, { method: 'POST' });

            // Update UI with user info
            currentUsername.textContent = username;
            currentUserAvatar.textContent = username[0].toUpperCase();
            currentUserAvatar.style.backgroundColor = getAvatarColor(username);

            usernamePage.classList.add('hidden');
            chatPage.classList.remove('hidden');

            // Establish WebSocket connection for real-time updates
            var socket = new SockJS('/ws');
            stompClient = Stomp.over(socket);

            stompClient.connect({}, onConnected, onError);

        } catch (error) {
            console.error('Error joining chat:', error);
            alert('Failed to join chat: ' + error.message);
        }
    }
    event.preventDefault();
}

// Load user's chats
async function loadUserChats() {
    try {
        const response = await fetch(`/api/v1/users/${username}/chats`);
        if (response.ok) {
            userChats = await response.json();
            updateChatList();
        }
    } catch (error) {
        console.error('Error loading user chats:', error);
    }
}

// Update chat list in sidebar
function updateChatList() {
    chatList.innerHTML = '';
    chatCount.textContent = userChats.length;
    
    userChats.forEach(chat => {
        const chatItem = document.createElement('div');
        chatItem.className = 'chat-item';
        chatItem.dataset.chatId = chat.id;
        
        const unreadCount = unreadCounts[chat.id] || 0;
        
        chatItem.innerHTML = `
            <div class="chat-avatar">${chat.name[0].toUpperCase()}</div>
            <div class="chat-info">
                <div class="chat-name">${chat.name}</div>
                <div class="chat-meta">
                    <span class="chat-type">${chat.isGroupChat ? 'Group' : 'Direct'}</span>
                    <span class="participant-count">${chat.participants ? chat.participants.length : 1} members</span>
                </div>
            </div>
            ${unreadCount > 0 ? `<div class="unread-badge">${unreadCount}</div>` : ''}
        `;
        
        chatItem.style.backgroundColor = getAvatarColor(chat.name);
        
        chatItem.addEventListener('click', () => selectChat(chat.id));
        chatList.appendChild(chatItem);
    });
}

// Select a chat
async function selectChat(chatId) {
    currentChatId = chatId;
    const chat = userChats.find(c => c.id === chatId);
    
    if (chat) {
        currentChatName.textContent = chat.name;
        updateChatParticipants(chat.participants || []);
        
        // Clear previous messages
        messageArea.innerHTML = '';
        
        // Load chat history
        await loadChatHistory(chatId);
        
        // Mark messages as read
        await markMessagesAsRead(chatId);
        
        // Update UI to show selected chat
        document.querySelectorAll('.chat-item').forEach(item => {
            item.classList.remove('active');
        });
        document.querySelector(`[data-chat-id="${chatId}"]`).classList.add('active');
        
        // Clear unread count
        unreadCounts[chatId] = 0;
        updateChatList();
    }
}

// Update chat participants display
function updateChatParticipants(participants) {
    if (!participants || participants.length === 0) {
        chatParticipants.textContent = 'No participants';
        return;
    }
    
    const participantNames = participants.map(p => p.user ? p.user.username : 'Unknown').join(', ');
    chatParticipants.textContent = `Participants: ${participantNames}`;
}

// Create new chat
async function createChat(event) {
    event.preventDefault();
    
    const chatName = document.querySelector('#chatName').value.trim();
    const participantsInput = document.querySelector('#participants').value.trim();
    const isGroupChat = document.querySelector('#isGroupChat').checked;
    
    const participants = participantsInput 
        ? participantsInput.split(',').map(p => p.trim()).filter(p => p)
        : [];
    
    // Add current user to participants
    if (!participants.includes(username)) {
        participants.push(username);
    }
    
    try {
        const response = await fetch('/api/v1/chats', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                name: chatName,
                createdBy: username,
                participants: participants
            })
        });
        
        if (response.ok) {
            const result = await response.json();
            console.log('Chat created:', result);
            
            // Reload user chats
            await loadUserChats();
            
            // Close modal
            closeModal();
            
            // Select the new chat
            if (result.chatId) {
                await selectChat(result.chatId);
            }
        } else {
            throw new Error('Failed to create chat');
        }
    } catch (error) {
        console.error('Error creating chat:', error);
        alert('Failed to create chat: ' + error.message);
    }
}

// Join existing chat
async function joinChat(chatId) {
    try {
        const response = await fetch(`/api/v1/chats/${chatId}/join`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({ username: username })
        });
        
        if (response.ok) {
            const result = await response.json();
            console.log('Joined chat:', result);
            
            // Reload user chats
            await loadUserChats();
            
            // Select the joined chat
            await selectChat(chatId);
        } else {
            throw new Error('Failed to join chat');
        }
    } catch (error) {
        console.error('Error joining chat:', error);
        alert('Failed to join chat: ' + error.message);
    }
}

// Leave chat
async function leaveChat() {
    if (!currentChatId) {
        alert('Please select a chat first');
        return;
    }
    
    if (!confirm('Are you sure you want to leave this chat?')) {
        return;
    }
    
    try {
        const response = await fetch(`/api/v1/chats/${currentChatId}/leave`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({ username: username })
        });
        
        if (response.ok) {
            const result = await response.json();
            console.log('Left chat:', result);
            
            // Reload user chats
            await loadUserChats();
            
            // Clear current chat
            currentChatId = null;
            currentChatName.textContent = 'Select a chat';
            messageArea.innerHTML = '';
            chatParticipants.textContent = '';
        } else {
            throw new Error('Failed to leave chat');
        }
    } catch (error) {
        console.error('Error leaving chat:', error);
        alert('Failed to leave chat: ' + error.message);
    }
}

// Mark messages as read
async function markMessagesAsRead(chatId) {
    try {
        const response = await fetch(`/api/v1/chats/${chatId}/read`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({ username: username })
        });
        
        if (response.ok) {
            console.log('Messages marked as read');
        }
    } catch (error) {
        console.error('Error marking messages as read:', error);
    }
}

function onConnected() {
    // Subscribe to the Public Topic for real-time updates
    stompClient.subscribe('/topic/public', onMessageReceived);
    
    connectingElement.classList.add('hidden');
    
    // Load user chats
    loadUserChats();
    
    // Send JOIN message
    const joinMessage = {
        type: 'JOIN',
        sender: username
    };
    stompClient.send("/app/chat.addUser", {}, JSON.stringify(joinMessage));
}

function onError(error) {
    connectingElement.textContent = 'Could not connect to WebSocket server. Please refresh this page to try again!';
    connectingElement.style.color = 'red';
}

async function sendMessage(event) {
    var messageContent = messageInput.value.trim();

    if(messageContent && stompClient && currentChatId) {
        try {
            // Send message through WebSocket for real-time delivery
            const chatMessage = {
                type: 'CHAT',
                sender: username,
                content: messageContent,
                chatId: currentChatId
            };
            
            stompClient.send("/app/chat.sendMessage", {}, JSON.stringify(chatMessage));
            messageInput.value = '';
            
            // Also store in database via REST API
            try {
                await fetch(`/api/v1/chats/${currentChatId}/messages`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                    },
                    body: JSON.stringify({
                        senderUsername: username,
                        content: messageContent
                    })
                });
            } catch (dbError) {
                console.error('Error storing message in database:', dbError);
            }
            
        } catch (error) {
            console.error('Error sending message:', error);
            alert('Failed to send message: ' + error.message);
        }
    } else if (!currentChatId) {
        alert('Please select a chat first');
    }
    event.preventDefault();
}

// UI-focused functions
function onMessageReceived(payload) {
    var message = JSON.parse(payload.body);
    
    // If message is for current chat, render it
    if (!message.chatId || message.chatId === currentChatId) {
        renderMessage(message);
    } else {
        // Update unread count for other chats
        if (message.chatId && message.sender !== username) {
            unreadCounts[message.chatId] = (unreadCounts[message.chatId] || 0) + 1;
            updateChatList();
        }
    }
}

function renderMessage(message) {
    var messageElement = document.createElement('li');
    
    // Handle both WebSocket messages and database messages
    var sender = message.sender || message.senderUsername;
    var content = message.content;
    var type = message.type;
    var timestamp = message.timestamp || message.createdAt;

    if(type === 'JOIN') {
        messageElement.classList.add('event-message');
        content = sender + ' joined!';
    } else if (type === 'LEAVE') {
        messageElement.classList.add('event-message');
        content = sender + ' left!';
    } else {
        messageElement.classList.add('chat-message');

        var avatarElement = document.createElement('div');
        avatarElement.className = 'message-avatar';
        var avatarText = document.createTextNode(sender[0].toUpperCase());
        avatarElement.appendChild(avatarText);
        avatarElement.style['background-color'] = getAvatarColor(sender);

        messageElement.appendChild(avatarElement);

        var messageContent = document.createElement('div');
        messageContent.className = 'message-content';
        
        var usernameElement = document.createElement('div');
        usernameElement.className = 'message-sender';
        var usernameText = document.createTextNode(sender);
        usernameElement.appendChild(usernameText);
        messageContent.appendChild(usernameElement);
        
        var textElement = document.createElement('div');
        textElement.className = 'message-text';
        var messageText = document.createTextNode(content);
        textElement.appendChild(messageText);
        messageContent.appendChild(textElement);
        
        if (timestamp) {
            var timeElement = document.createElement('div');
            timeElement.className = 'message-time';
            var timeText = document.createTextNode(formatTime(timestamp));
            timeElement.appendChild(timeText);
            messageContent.appendChild(timeElement);
        }
        
        messageElement.appendChild(messageContent);
    }

    messageArea.appendChild(messageElement);
    messageArea.scrollTop = messageArea.scrollHeight;
}

async function loadChatHistory(chatId) {
    try {
        const response = await fetch(`/api/v1/chats/${chatId}/messages?username=${encodeURIComponent(username)}`);
        if (response.ok) {
            const messages = await response.json();
            messages.forEach(message => renderMessage(message));
        }
    } catch (error) {
        console.error('Error loading chat history:', error);
    }
}

// Modal functions
function openModal() {
    createChatModal.classList.remove('hidden');
}

function closeModal() {
    createChatModal.classList.add('hidden');
    createChatForm.reset();
}

// Utility functions
function getAvatarColor(messageSender) {
    var hash = 0;
    for (var i = 0; i < messageSender.length; i++) {
        hash = 31 * hash + messageSender.charCodeAt(i);
    }

    var index = Math.abs(hash % colors.length);
    return colors[index];
}

function formatTime(timestamp) {
    const date = new Date(timestamp);
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

// Handle page unload - set user offline
window.addEventListener('beforeunload', async () => {
    if (username) {
        try {
            await fetch(`/api/v1/users/${username}/offline`, { method: 'POST' });
        } catch (error) {
            console.error('Error setting user offline:', error);
        }
    }
});

// Event listeners
usernameForm.addEventListener('submit', connect, true);
messageForm.addEventListener('submit', sendMessage, true);

// Modal event listeners
document.querySelector('#createChatBtn').addEventListener('click', openModal);
document.querySelector('#closeModalBtn').addEventListener('click', closeModal);
document.querySelector('#cancelCreateBtn').addEventListener('click', closeModal);
createChatForm.addEventListener('submit', createChat);

// Chat action event listeners
document.querySelector('#leaveChatBtn').addEventListener('click', leaveChat);
document.querySelector('#markReadBtn').addEventListener('click', () => {
    if (currentChatId) {
        markMessagesAsRead(currentChatId);
    }
});

// Close modal when clicking outside
createChatModal.addEventListener('click', (e) => {
    if (e.target === createChatModal) {
        closeModal();
    }
});
