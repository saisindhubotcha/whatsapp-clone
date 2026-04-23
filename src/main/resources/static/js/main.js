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

            stompClient.connect({username: username}, onConnected, onError);

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
    
    // Subscribe to user-specific topic for reconnect notifications
    console.log('Subscribing to /topic/user/' + username);
    stompClient.subscribe('/topic/user/' + username, onUserMessageReceived);
    console.log('Subscribed to /topic/user/' + username);
    
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

// Handle user-specific messages (reconnect subscriptions)
function onUserMessageReceived(payload) {
    console.log('User-specific message received:', payload);
    var message = JSON.parse(payload.body);
    
    if (message.type === 'reconnect_subscriptions' && message.subscriptions) {
        console.log('Received reconnect subscriptions:', message.subscriptions);
        message.subscriptions.forEach(function(destination) {
            // Skip /topic/public and /topic/user/* since we already subscribed in onConnected
            if (destination === '/topic/public' || destination.startsWith('/topic/user/')) {
                console.log('Skipping already-subscribed topic:', destination);
                return;
            }
            console.log('Auto-resubscribing to:', destination);
            stompClient.subscribe(destination, onChatMessageReceived);
        });
        
        // If there was a previous chat selected, re-select it
        if (currentChatId) {
            console.log('Re-selecting previous chat:', currentChatId);
            selectChat(currentChatId);
        }
    }
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
            
            // Generate deterministic messageId for deduplication
            const messageId = generateMessageId(currentChatId, username, messageContent);
            
            // Send message through WebSocket for real-time delivery
            const chatMessage = {
                type: 'CHAT',
                sender: username,
                content: messageContent,
                chatId: currentChatId,
                messageId: messageId
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
                        content: messageContent,
                        messageId: messageId
                    })
                });
                console.log('Database response status:', dbResponse.status);
                if (dbResponse.ok) {
                    const result = await dbResponse.json();
                    console.log('Message stored in database successfully:', result);
                    if (result.duplicate) {
                        console.log('Duplicate message detected and handled');
                    }
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
function generateMessageId(chatId, sender, content) {
    // Create deterministic messageId based on message content for idempotency
    const messageData = `${chatId}_${sender}_${content}`;
    
    // MD5 hash implementation
    function md5(string) {
        function add32(a, b) {
            return (a + b) & 0xFFFFFFFF;
        }
        
        function mm(a, b, c, d, x, s, t) {
            return add32(rol(add32(add32(b, c), x), s), t);
        }
        
        function rol(n, c) {
            return (n << c) | (n >>> (32 - c));
        }
        
        function cmn(q, a, b, x, s, t) {
            a = add32(add32(a, q), add32(x, t));
            return add32(rol(a, s), b);
        }
        
        function ff(a, b, c, d, x, s, t) {
            return cmn((b & c) | ((~b) & d), a, b, x, s, t);
        }
        
        function gg(a, b, c, d, x, s, t) {
            return cmn((b & d) | (c & (~d)), a, b, x, s, t);
        }
        
        function hh(a, b, c, d, x, s, t) {
            return cmn(b ^ c ^ d, a, b, x, s, t);
        }
        
        function ii(a, b, c, d, x, s, t) {
            return cmn(c ^ (b | (~d)), a, b, x, s, t);
        }
        
        function md5cycle(x, k) {
            let a = x[0], b = x[1], c = x[2], d = x[3];
            
            a = ff(a, b, c, d, k[0], 7, -680876936);
            d = ff(d, a, b, c, k[1], 12, -389564586);
            c = ff(c, d, a, b, k[2], 17, 606105819);
            b = ff(b, c, d, a, k[3], 22, -1044525330);
            a = ff(a, b, c, d, k[4], 7, -176418897);
            d = ff(d, a, b, c, k[5], 12, 1200080426);
            c = ff(c, d, a, b, k[6], 17, -1473231341);
            b = ff(b, c, d, a, k[7], 22, -45705983);
            a = ff(a, b, c, d, k[8], 7, 1770035416);
            d = ff(d, a, b, c, k[9], 12, -1958414417);
            c = ff(c, d, a, b, k[10], 17, -42063);
            b = ff(b, c, d, a, k[11], 22, -1990404162);
            a = ff(a, b, c, d, k[12], 7, 1804603682);
            d = ff(d, a, b, c, k[13], 12, -40341101);
            c = ff(c, d, a, b, k[14], 17, -1502002290);
            b = ff(b, c, d, a, k[15], 22, 1236535329);
            
            a = gg(a, b, c, d, k[1], 5, -165796510);
            d = gg(d, a, b, c, k[6], 9, -1069501632);
            c = gg(c, d, a, b, k[11], 14, 643717713);
            b = gg(b, c, d, a, k[0], 20, -373897302);
            a = gg(a, b, c, d, k[5], 5, -701558691);
            d = gg(d, a, b, c, k[10], 9, 38016083);
            c = gg(c, d, a, b, k[15], 14, -660478335);
            b = gg(b, c, d, a, k[4], 20, -405537848);
            a = gg(a, b, c, d, k[9], 5, 568446438);
            d = gg(d, a, b, c, k[14], 9, -1019803690);
            c = gg(c, d, a, b, k[3], 14, -187363961);
            b = gg(b, c, d, a, k[8], 20, 1163531501);
            a = gg(a, b, c, d, k[13], 5, -1444681467);
            d = gg(d, a, b, c, k[2], 9, -51403784);
            c = gg(c, d, a, b, k[7], 14, 1735328473);
            b = gg(b, c, d, a, k[12], 20, -1926607734);
            
            a = hh(a, b, c, d, k[5], 4, -378558);
            d = hh(d, a, b, c, k[8], 11, -2022574463);
            c = hh(c, d, a, b, k[11], 16, 1839030562);
            b = hh(b, c, d, a, k[14], 23, -35309556);
            a = hh(a, b, c, d, k[1], 4, -1530992060);
            d = hh(d, a, b, c, k[4], 11, 1272893353);
            c = hh(c, d, a, b, k[7], 16, -155497632);
            b = hh(b, c, d, a, k[10], 23, -1094730640);
            a = hh(a, b, c, d, k[13], 4, 681279174);
            d = hh(d, a, b, c, k[0], 11, -358537222);
            c = hh(c, d, a, b, k[3], 16, -722521979);
            b = hh(b, c, d, a, k[6], 23, 76029189);
            a = hh(a, b, c, d, k[9], 4, -640364487);
            d = hh(d, a, b, c, k[12], 11, -421815835);
            c = hh(c, d, a, b, k[15], 16, 530742520);
            b = hh(b, c, d, a, k[2], 23, -995338651);
            
            a = ii(a, b, c, d, k[0], 6, -198630844);
            d = ii(d, a, b, c, k[7], 10, 1126891415);
            c = ii(c, d, a, b, k[14], 15, -1416354905);
            b = ii(b, c, d, a, k[5], 21, -57434055);
            a = ii(a, b, c, d, k[12], 6, 1700485571);
            d = ii(d, a, b, c, k[3], 10, -1894986606);
            c = ii(c, d, a, b, k[10], 15, -1051523);
            b = ii(b, c, d, a, k[1], 21, -2054922799);
            a = ii(a, b, c, d, k[8], 6, 1873313359);
            d = ii(d, a, b, c, k[15], 10, -30611744);
            c = ii(c, d, a, b, k[6], 15, -1560198380);
            b = ii(b, c, d, a, k[13], 21, 1309151649);
            a = ii(a, b, c, d, k[4], 6, -145523070);
            d = ii(d, a, b, c, k[11], 10, -1120210379);
            c = ii(c, d, a, b, k[2], 15, 718787259);
            b = ii(b, c, d, a, k[9], 21, -343485551);
            
            x[0] = add32(a, x[0]);
            x[1] = add32(b, x[1]);
            x[2] = add32(c, x[2]);
            x[3] = add32(d, x[3]);
        }
        
        function md51(s) {
            let n = s.length;
            const state = [1732584193, -271733879, -1732584194, 271733878];
            let i;
            for (i = 64; i <= s.length; i += 64) {
                md5cycle(state, md5blk(s.substring(i - 64, i)));
            }
            s = s.substring(i - 64);
            const tail = [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0];
            for (i = 0; i < s.length; i++) {
                tail[i >> 2] |= s.charCodeAt(i) << ((i % 4) * 8);
            }
            tail[i >> 2] |= 0x80 << ((i % 4) * 8);
            if (i > 55) {
                md5cycle(state, tail);
                for (i = 0; i < 16; i++) tail[i] = 0;
            }
            tail[14] = n * 8;
            md5cycle(state, tail);
            return state;
        }
        
        function md5blk(s) {
            const md5blks = [];
            for (let i = 0; i < 64; i += 4) {
                md5blks[i >> 2] = s.charCodeAt(i) + (s.charCodeAt(i + 1) << 8) + (s.charCodeAt(i + 2) << 16) + (s.charCodeAt(i + 3) << 24);
            }
            return md5blks;
        }
        
        function rhex(n) {
            let s = '';
            for (let i = 0; i < 4; i++) {
                s += hexchr((n >> (i * 8)) & 0xFF);
            }
            return s;
        }
        
        function hexchr(i) {
            return '0123456789abcdef'.charAt(i);
        }
        
        const x = md51(string);
        return rhex(x[0]) + rhex(x[1]) + rhex(x[2]) + rhex(x[3]);
    }
    
    return 'msg_' + md5(messageData);
}

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
