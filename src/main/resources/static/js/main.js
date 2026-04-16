'use strict';

// DOM Elements
console.log('Initializing DOM elements...');
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

console.log('DOM elements initialized successfully');
console.log('Message area element:', messageArea);
console.log('Message area parent:', messageArea ? messageArea.parentElement : 'null');
console.log('Message area classes:', messageArea ? messageArea.className : 'null');
console.log('Message area children count:', messageArea ? messageArea.children.length : 'null');

// New DOM Elements
var createChatBox = document.querySelector('#createChatBox');
var quickCreateChatForm = document.querySelector('#quickCreateChatForm');
var quickChatName = document.querySelector('#quickChatName');
var quickParticipants = document.querySelector('#quickParticipants');
var emptyChatMessage = document.querySelector('#emptyChatMessage');

console.log('DOM elements initialized successfully');

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
    console.log('Connect function called');
    username = document.querySelector('#name').value.trim();
    console.log('Username entered:', username);

    if(username) {
        console.log('Starting user authentication process...');
        try {
            // Login user (backend will auto-create if not exists)
            console.log('Authenticating user:', username);
            const loginResponse = await fetch(`/api/v1/users/${username}/login`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                }
            });
            console.log('Authentication request completed');

            console.log('Authentication response status:', loginResponse.status);
            console.log('Authentication response ok:', loginResponse.ok);
            
            if (!loginResponse.ok) {
                const errorText = await loginResponse.text();
                console.error('Authentication failed with status:', loginResponse.status);
                console.error('Error response:', errorText);
                throw new Error(`Failed to authenticate user: ${loginResponse.status} - ${errorText}`);
            }

            const userResult = await loginResponse.json();
            console.log('User authenticated successfully:', userResult);
            
            if (userResult.isNewUser) {
                console.log('New user created automatically');
            } else {
                console.log('Existing user logged in');
            }

            // Set user as online
            const onlineResponse = await fetch(`/api/v1/users/${username}/online`, { method: 'POST' });
            console.log('Online response status:', onlineResponse.status);

            // Update UI with user info
            console.log('Updating UI...');
            currentUsername.textContent = username;
            currentUserAvatar.textContent = username[0].toUpperCase();
            currentUserAvatar.style.backgroundColor = getAvatarColor(username);

            console.log('Switching pages...');
            console.log('Username page element:', usernamePage);
            console.log('Chat page element:', chatPage);
            console.log('Username page classes before:', usernamePage.className);
            console.log('Chat page classes before:', chatPage.className);
            
            usernamePage.classList.add('hidden');
            chatPage.classList.remove('hidden');
            
            console.log('Username page classes after:', usernamePage.className);
            console.log('Chat page classes after:', chatPage.className);
            console.log('Page switch completed');

            // Establish WebSocket connection for real-time updates
            console.log('Creating WebSocket connection...');
            var socket = new SockJS('/ws');
            stompClient = Stomp.over(socket);

            stompClient.connect({}, onConnected, onError);

        } catch (error) {
            console.error('Error joining chat:', error);
            console.error('Error stack:', error.stack);
            alert('Failed to join chat: ' + error.message);
        }
    } else {
        console.log('No username entered');
    }
    console.log('Connect function completed');
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
    
    // Show/hide appropriate UI elements based on chat list state
    if (userChats.length === 0) {
        // Show create chat box and empty message for new users
        createChatBox.classList.remove('hidden');
        emptyChatMessage.classList.remove('hidden');
        chatList.classList.add('hidden');
        currentChatName.textContent = 'Welcome! Create your first chat';
        messageArea.innerHTML = '<li class="welcome-message">Start by creating a new chat or wait for someone to invite you!</li>';
    } else {
        // Show chat list for existing users
        createChatBox.classList.add('hidden');
        emptyChatMessage.classList.add('hidden');
        chatList.classList.remove('hidden');
        
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
}

// Select a chat
async function selectChat(chatId) {
    console.log('selectChat called with chatId:', chatId);
    currentChatId = chatId;
    const chat = userChats.find(c => c.id === chatId);
    console.log('Found chat:', chat);
    
    if (chat) {
        console.log('Setting up chat interface for:', chat.name);
        currentChatName.textContent = chat.name;
        updateChatParticipants(chat.participants || []);
        
        // Unsubscribe from previous chat topic if exists
        if (window.currentChatSubscription) {
            window.currentChatSubscription.unsubscribe();
            console.log('Unsubscribed from previous chat topic');
        }
        
        // Subscribe to chat-specific topic for real-time messages
        console.log('Subscribing to chat topic:', `/topic/chat/${chatId}`);
        window.currentChatSubscription = stompClient.subscribe(`/topic/chat/${chatId}`, onChatMessageReceived);
        console.log('Subscribed to chat-specific topic:', `/topic/chat/${chatId}`);
        
        // Clear previous messages
        console.log('Clearing previous messages');
        messageArea.innerHTML = '';
        
        // Show loading message
        const loadingMessage = document.createElement('li');
        loadingMessage.className = 'loading-message';
        loadingMessage.textContent = 'Loading chat history...';
        messageArea.appendChild(loadingMessage);
        console.log('Added loading message');
        
        // Load chat history
        console.log('Starting chat history load');
        await loadChatHistory(chatId);
        
        // Remove loading message
        if (loadingMessage.parentNode) {
            loadingMessage.parentNode.removeChild(loadingMessage);
            console.log('Removed loading message');
        }
        
        // Mark messages as read
        console.log('Marking messages as read');
        await markMessagesAsRead(chatId);
        console.log('Chat selection completed');
        
        // Highlight only the selected chat - remove active class from all chats first
        document.querySelectorAll('.chat-item').forEach(item => {
            item.classList.remove('active');
            console.log('Removed active class from chat:', item.dataset.chatId);
        });
        
        // Add active class only to selected chat
        const selectedChatItem = document.querySelector(`[data-chat-id="${chatId}"]`);
        if (selectedChatItem) {
            selectedChatItem.classList.add('active');
            console.log('Added active class to selected chat:', chatId);
            
            // Scroll selected chat into view if needed
            selectedChatItem.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
        } else {
            console.warn('Selected chat item not found:', chatId);
        }
        
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
            
            // Show success message with missing participants info if any
            if (result.missingParticipants && result.missingParticipants.length > 0) {
                alert(`Chat created successfully!\n\nNote: The following users were not found and not added: ${result.missingParticipants.join(', ')}`);
            } else {
                alert('Chat created successfully!');
            }
            
            // Reload user chats
            await loadUserChats();
            
            // Close modal
            closeModal();
            
            // Select the new chat
            if (result.chatId) {
                await selectChat(result.chatId);
            }
        } else {
            const errorData = await response.json();
            throw new Error(errorData.message || 'Failed to create chat');
        }
    } catch (error) {
        console.error('Error creating chat:', error);
        alert('Failed to create chat: ' + error.message);
    }
}

// Quick create chat from sidebar (for empty state)
async function quickCreateChat(event) {
    event.preventDefault();
    
    const chatName = quickChatName.value.trim();
    const participantsInput = quickParticipants.value.trim();
    
    const participants = participantsInput 
        ? participantsInput.split(',').map(p => p.trim()).filter(p => p)
        : [];
    
    // Add current user to participants
    if (!participants.includes(username)) {
        participants.push(username);
    }
    
    try {
        console.log('Creating chat with:', { name: chatName, createdBy: username, participants: participants });
        
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
        
        console.log('Chat creation response status:', response.status);
        console.log('Chat creation response ok:', response.ok);
        
        if (response.ok) {
            const result = await response.json();
            console.log('Chat created successfully:', result);
            
            // Show success message with missing participants info if any
            if (result.missingParticipants && result.missingParticipants.length > 0) {
                alert(`Chat created successfully!\n\nNote: The following users were not found and not added: ${result.missingParticipants.join(', ')}`);
            }
            
            // Clear form
            quickCreateChatForm.reset();
            
            // Reload user chats
            await loadUserChats();
            
            // Select new chat
            if (result.chatId) {
                await selectChat(result.chatId);
            }
        } else {
            const errorData = await response.json();
            console.error('Chat creation failed:', response.status, errorData);
            throw new Error(errorData.message || `Failed to create chat: ${response.status}`);
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
            
            // Show leave message in current chat before clearing
            const leaveMessage = {
                sender: username,
                content: username + ' left the chat',
                type: 'LEAVE',
                timestamp: new Date().toISOString()
            };
            renderMessage(leaveMessage);
            
            // Wait a moment to show the leave message
            await new Promise(resolve => setTimeout(resolve, 1000));
            
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
    console.log('WebSocket connected successfully');
    // Subscribe to the Public Topic for general updates
    console.log('Subscribing to /topic/public');
    stompClient.subscribe('/topic/public', onMessageReceived);
    console.log('Subscribed to /topic/public');
    
    connectingElement.classList.add('hidden');
    
    // Load user chats
    console.log('Loading user chats...');
    loadUserChats();
    
    // Send JOIN message
    const joinMessage = {
        type: 'JOIN',
        sender: username
    };
    console.log('Sending JOIN message:', joinMessage);
    stompClient.send("/app/user/add", {}, JSON.stringify(joinMessage));
    console.log('JOIN message sent');
}

function onError(error) {
    connectingElement.textContent = 'Could not connect to WebSocket server. Please refresh this page to try again!';
    connectingElement.style.color = 'red';
}

async function sendMessage(event) {
    console.log('sendMessage function called');
    var messageContent = messageInput.value.trim();
    console.log('Message content:', messageContent);
    console.log('stompClient:', stompClient ? 'connected' : 'null');
    console.log('currentChatId:', currentChatId);

    if(messageContent && stompClient && currentChatId) {
        try {
            console.log('Sending message...');
            // Send message through WebSocket for real-time delivery
            const chatMessage = {
                type: 'CHAT',
                sender: username,
                content: messageContent,
                chatId: currentChatId
            };
            
            console.log('WebSocket message:', chatMessage);
            stompClient.send("/app/chat.sendMessage", {}, JSON.stringify(chatMessage));
            messageInput.value = '';
            console.log('Message sent via WebSocket');
            
            // Also store in database via REST API
            try {
                console.log('Storing message in database...');
                const dbResponse = await fetch(`/api/v1/chats/${currentChatId}/messages`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                    },
                    body: JSON.stringify({
                        senderUsername: username,
                        content: messageContent
                    })
                });
                console.log('Database response status:', dbResponse.status);
                if (dbResponse.ok) {
                    console.log('Message stored in database successfully');
                } else {
                    console.error('Database storage failed:', dbResponse.status);
                }
            } catch (dbError) {
                console.error('Error storing message in database:', dbError);
            }
            
            console.log('sendMessage completed successfully');
            
        } catch (error) {
            console.error('Error sending message:', error);
            console.error('Error stack:', error.stack);
            alert('Failed to send message: ' + error.message);
        }
    } else {
        console.log('Cannot send message - missing requirements');
        if (!messageContent) console.log('No message content');
        if (!stompClient) console.log('No WebSocket connection');
        if (!currentChatId) console.log('No current chat selected');
        
        if (!currentChatId) {
            alert('Please select a chat first');
        }
    }
    console.log('sendMessage function finished');
}

// UI-focused functions
function onMessageReceived(payload) {
    console.log('WebSocket message received:', payload);
    var message = JSON.parse(payload.body);
    console.log('Parsed message:', message);
    console.log('Current chat ID:', currentChatId);
    
    // If message is for current chat, render it
    if (!message.chatId || message.chatId === currentChatId) {
        console.log('Rendering message for current chat');
        renderMessage(message);
    } else {
        console.log('Message for different chat, updating unread count');
        // Update unread count for other chats
        if (message.chatId && message.sender !== username) {
            unreadCounts[message.chatId] = (unreadCounts[message.chatId] || 0) + 1;
            updateChatList();
        }
    }
}

// Handle chat-specific messages
function onChatMessageReceived(payload) {
    console.log('Chat-specific message received:', payload);
    var message = JSON.parse(payload.body);
    console.log('Parsed chat message:', message);
    
    // Only render if message is for current chat
    if (message.chatId === currentChatId) {
        console.log('Rendering chat message for current chat');
        renderMessage(message);
    } else {
        console.log('Chat message for different chat, updating unread count');
        // Update unread count for other chats
        if (message.sender !== username) {
            unreadCounts[message.chatId] = (unreadCounts[message.chatId] || 0) + 1;
            updateChatList();
        }
    }
}

function renderMessage(message) {
    console.log('renderMessage called with:', message);
    var messageElement = document.createElement('li');
    
    // Handle both WebSocket messages and database messages
    var sender = message.sender || message.senderUsername;
    var content = message.content;
    var type = message.type;
    var timestamp = message.timestamp || message.createdAt;
    
    console.log('Message details - sender:', sender, 'content:', content, 'type:', type, 'timestamp:', timestamp);
    console.log('Message area element:', messageArea);

    console.log('Message type:', type, 'typeof type:', typeof type);
    
    if(type === 'JOIN') {
        messageElement.classList.add('event-message');
        content = sender + ' joined!';
        console.log('Rendering JOIN message for:', sender);
    } else if(type === 'LEAVE') {
        messageElement.classList.add('event-message');
        content = sender + ' left!';
        console.log('Rendering LEAVE message for:', sender);
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

    console.log('Appending message to message area');
    messageArea.appendChild(messageElement);
    console.log('Message appended, current message area children count:', messageArea.children.length);
    
    // Check if message is visible
    const computedStyle = window.getComputedStyle(messageElement);
    console.log('Message element styles:', {
        display: computedStyle.display,
        visibility: computedStyle.visibility,
        opacity: computedStyle.opacity,
        height: computedStyle.height,
        width: computedStyle.width
    });
    
    messageArea.scrollTop = messageArea.scrollHeight;
    console.log('Message rendered and scrolled');
    
    // Force visibility check
    if (messageElement.offsetHeight === 0) {
        console.warn('Message element has zero height - might be hidden by CSS');
        messageElement.style.display = 'block';
        messageElement.style.visibility = 'visible';
        messageElement.style.opacity = '1';
        console.log('Applied forced visibility styles');
    }
}

async function loadChatHistory(chatId) {
    console.log('Loading chat history for chat ID:', chatId);
    try {
        const url = `/api/v1/chats/${chatId}/messages?username=${encodeURIComponent(username)}`;
        console.log('Fetching chat history from:', url);
        const response = await fetch(url);
        console.log('Chat history response status:', response.status);
        
        if (response.ok) {
            const messages = await response.json();
            console.log('Loaded messages:', messages);
            console.log('Rendering', messages.length, 'messages');
            messages.forEach((message, index) => {
                console.log(`Rendering message ${index + 1}:`, message);
                renderMessage(message);
            });
            console.log('Chat history loaded and rendered');
        } else {
            console.error('Failed to load chat history:', response.status);
            const errorText = await response.text();
            console.error('Error response:', errorText);
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

// Update participant preview in real-time
function updateParticipantPreview() {
    const participantInput = quickParticipants.value.trim();
    const previewElement = document.querySelector('#participantPreview');
    
    if (!previewElement) return;
    
    if (participantInput) {
        const participants = participantInput.split(',')
            .map(p => p.trim())
            .filter(p => p.length > 0);
        
        if (participants.length > 0) {
            const participantList = participants.map(p => 
                `<span class="participant-tag">${p}</span>`
            ).join('');
            
            previewElement.innerHTML = `
                <div class="participant-list">
                    <small>Participants (${participants.length + 1} including you):</small>
                    <div class="participant-tags">
                        <span class="participant-tag current-user">${username}</span>
                        ${participantList}
                    </div>
                </div>
            `;
        } else {
            previewElement.innerHTML = '';
        }
    } else {
        previewElement.innerHTML = '';
    }
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
console.log('Setting up event listeners...');
if (usernameForm) {
    usernameForm.addEventListener('submit', function(event) {
        console.log('Form submit event triggered');
        event.preventDefault();
        event.stopPropagation();
        console.log('Event prevented and stopped');
        connect(event);
        return false;
    }, true);
    console.log('Username form listener attached');
} else {
    console.error('Username form not found!');
}

if (messageForm) {
    messageForm.addEventListener('submit', function(event) {
        console.log('Message form submit event triggered');
        event.preventDefault();
        event.stopPropagation();
        console.log('Message event prevented and stopped');
        sendMessage(event);
        return false;
    }, true);
    console.log('Message form listener attached');
}

// Modal event listeners
if (document.querySelector('#createChatBtn')) {
    document.querySelector('#createChatBtn').addEventListener('click', openModal);
}
if (document.querySelector('#closeModalBtn')) {
    document.querySelector('#closeModalBtn').addEventListener('click', closeModal);
}
if (document.querySelector('#cancelCreateBtn')) {
    document.querySelector('#cancelCreateBtn').addEventListener('click', closeModal);
}
if (createChatForm) {
    createChatForm.addEventListener('submit', createChat);
}

// Quick create chat form listener
if (quickCreateChatForm) {
    quickCreateChatForm.addEventListener('submit', quickCreateChat);
    console.log('Quick create chat form listener attached');
    
    // Add real-time participant preview
    if (quickParticipants) {
        quickParticipants.addEventListener('input', updateParticipantPreview);
        console.log('Participant preview listener attached');
    }
} else {
    console.log('Quick create chat form not found (may be normal if not in empty state)');
}

console.log('All event listeners setup completed');

// Chat action event listeners
if (document.querySelector('#leaveChatBtn')) {
    document.querySelector('#leaveChatBtn').addEventListener('click', leaveChat);
}
if (document.querySelector('#markReadBtn')) {
    document.querySelector('#markReadBtn').addEventListener('click', () => {
        if (currentChatId) {
            markMessagesAsRead(currentChatId);
        }
    });
}

// Close modal when clicking outside
if (createChatModal) {
    createChatModal.addEventListener('click', (e) => {
        if (e.target === createChatModal) {
            closeModal();
        }
    });
}

console.log('All event listeners setup completed successfully!');
