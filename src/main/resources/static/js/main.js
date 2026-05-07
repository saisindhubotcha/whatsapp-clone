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
var participantsSelect = document.querySelector('#participants');

console.log('DOM elements initialized successfully');

// State Management
var stompClient = null;
var username = null;
var currentChatId = null;
var userChats = [];
var unreadCounts = {};

// Client cache for sequence-based pagination (per conversation)
var messageCache = {}; // Key: chatId, Value: { messages: Map<seq_no, message>, oldest_seq, newest_seq, server_latest_seq, is_at_tail }
var CACHE_EVICT_THRESHOLD = 350;
var CACHE_TARGET_SIZE = 300;

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
        const loginResponse = await fetch(`/chat/api/users/${username}/login`, {
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
            const onlineResponse = await fetch(`/chat/api/users/${username}/online`, { method: 'POST' });
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
        const response = await fetch(`/chat/api/users/${username}/chats`);
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
        chatParticipants.textContent = '';
        return;
    }

    const participantNames = participants.map(p => p.userUsername || p.user?.username || 'Unknown').join(', ');
    chatParticipants.textContent = `Participants: ${participantNames}`;
}

// Create new chat
async function createChat(event) {
    event.preventDefault();

    const chatNameInput = document.querySelector('#chatName');
    const participants = getSelectedParticipants(participantsSelect);

    // Add current user to participants
    if (!participants.includes(username)) {
        participants.push(username);
    }

    // Determine if it's a direct chat (1-on-1)
    const isDirectChat = participants.length === 2;

    // For direct chats, don't send a chat name - backend will auto-generate "fromName - toName"
    const chatName = isDirectChat ? '' : chatNameInput.value.trim();

    // For group chats, require a chat name
    if (!isDirectChat && !chatName) {
        alert('Please enter a chat name for group chats');
        return;
    }

    try {
        const response = await fetch('/chat/api/chats', {
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

    const chatNameInput = quickChatName;
    const participants = getSelectedParticipants(quickParticipants);

    // Add current user to participants
    if (!participants.includes(username)) {
        participants.push(username);
    }

    // Determine if it's a direct chat (1-on-1)
    const isDirectChat = participants.length === 2;

    // For direct chats, don't send a chat name - backend will auto-generate "fromName - toName"
    const chatName = isDirectChat ? '' : chatNameInput.value.trim();

    // For group chats, require a chat name
    if (!isDirectChat && !chatName) {
        alert('Please enter a chat name for group chats');
        return;
    }

    try {
        console.log('Creating chat with:', { name: chatName, createdBy: username, participants: participants });

        const response = await fetch('/chat/api/chats', {
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
        const response = await fetch(`/chat/api/chats/${chatId}/join`, {
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
        const response = await fetch(`/chat/api/chats/${currentChatId}/leave`, {
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
        const response = await fetch(`/chat/api/chats/${chatId}/read`, {
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

    // Load users for participant selection
    console.log('Loading users for selection...');
    loadUsers();

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

    if(messageContent && currentChatId) {
        // Use HTTP API as primary method
        await sendMessageViaHTTP(currentChatId, username, messageContent, generateMessageId(currentChatId, username, messageContent));
    } else {
        console.log('Cannot send message - missing requirements');
        if (!messageContent) console.log('No message content');
        if (!stompClient) console.log('No WebSocket connection');
        if (!currentChatId) console.log('No current chat selected');
        
        if (!currentChatId) {
            alert('Please select a chat first');
        } else if (!stompClient || !stompClient.connected) {
            console.log('WebSocket not available, using HTTP API');
            await sendMessageViaHTTP(currentChatId, username, messageContent, generateMessageId(currentChatId, username, messageContent));
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

    // Add message to cache if it has seq_no
    if (message.seqNo !== undefined && message.chatId) {
        addMessageToCache(message.chatId, message);
    }

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

    // Add message to cache if it has seq_no
    if (message.seqNo !== undefined && message.chatId) {
        addMessageToCache(message.chatId, message);
    }

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
        // Try new sequence-based API first
        const url = `/chat/api/messages?conv=${chatId}&limit=50`;
        console.log('Fetching chat history from new API:', url);
        const response = await fetch(url);
        console.log('Chat history response status:', response.status);

        if (response.ok) {
            const messages = await response.json();
            console.log('Loaded messages from new API:', messages);

            // Check if messages have seq_no (migration ran)
            if (messages.length > 0 && messages[0].seqNo !== undefined) {
                console.log('Using sequence-based pagination');
                await loadChatHistoryWithSeq(chatId, messages);
            } else {
                console.log('Messages do not have seq_no, falling back to old API');
                await loadChatHistoryOld(chatId);
            }
        } else {
            console.log('New API failed, falling back to old API');
            await loadChatHistoryOld(chatId);
        }
    } catch (error) {
        console.error('Error loading chat history with new API, falling back:', error);
        await loadChatHistoryOld(chatId);
    }
}

async function loadChatHistoryWithSeq(chatId, messages) {
    // Initialize cache for this conversation if not exists
    if (!messageCache[chatId]) {
        messageCache[chatId] = {
            messages: new Map(),
            oldest_seq: null,
            newest_seq: null,
            server_latest_seq: null,
            is_at_tail: false
        };
    }

    const cache = messageCache[chatId];

    console.log('Rendering', messages.length, 'messages');

    // Sort messages by seq_no ascending (oldest first, newest last)
    messages.sort((a, b) => a.seqNo - b.seqNo);

    // Populate cache
    messages.forEach(message => {
        cache.messages.set(message.seqNo, message);
    });

    // Update cache metadata
    if (messages.length > 0) {
        cache.oldest_seq = messages[0].seqNo;
        cache.newest_seq = messages[messages.length - 1].seqNo;
    }

    // Fetch server latest seq
    const latestSeqResponse = await fetch(`/chat/api/conv/${chatId}/latest_seq`);
    if (latestSeqResponse.ok) {
        const latestSeqData = await latestSeqResponse.json();
        cache.server_latest_seq = latestSeqData.latest_seq;

        // Set is_at_tail only if response shorter than limit AND newest_seq == server_latest_seq
        if (messages.length < 50 && cache.newest_seq === cache.server_latest_seq) {
            cache.is_at_tail = true;
        }
    }

    // Assert contiguous range
    assertContiguousRange(chatId);

    // Render messages from cache
    renderCachedMessages(chatId);

    console.log('Chat history loaded and rendered with sequence-based pagination');
    console.log('Cache state:', {
        oldest_seq: cache.oldest_seq,
        newest_seq: cache.newest_seq,
        server_latest_seq: cache.server_latest_seq,
        is_at_tail: cache.is_at_tail,
        cache_size: cache.messages.size
    });
}

async function loadChatHistoryOld(chatId) {
    console.log('Loading chat history using old API');
    try {
        const url = `/chat/api/chats/${chatId}/messages?username=${encodeURIComponent(username)}`;
        console.log('Fetching chat history from old API:', url);
        const response = await fetch(url);
        console.log('Chat history response status:', response.status);

        if (response.ok) {
            const messages = await response.json();
            console.log('Loaded messages:', messages);
            console.log('Rendering', messages.length, 'messages');

            // Sort messages by timestamp ascending (oldest first, newest last)
            messages.sort((a, b) => {
                const timeA = new Date(a.timestamp || a.createdAt || 0).getTime();
                const timeB = new Date(b.timestamp || b.createdAt || 0).getTime();
                return timeA - timeB;
            });

            messages.forEach((message, index) => {
                console.log(`Rendering message ${index + 1}:`, message);
                renderMessage(message);
            });
            console.log('Chat history loaded and rendered with old API');
        } else {
            console.error('Failed to load chat history:', response.status);
            const errorText = await response.text();
            console.error('Error response:', errorText);
        }
    } catch (error) {
        console.error('Error loading chat history:', error);
    }
}

// Load older messages (pagination - scroll up)
async function loadOlderMessages(chatId) {
    console.log('Loading older messages for chat ID:', chatId);
    const cache = messageCache[chatId];
    if (!cache || cache.oldest_seq === null) {
        console.log('No cache or oldest_seq is null, cannot load older messages');
        return;
    }

    try {
        // GET /messages?conv=X&before_seq={oldest_seq}&limit=50
        const url = `/chat/api/messages?conv=${chatId}&before_seq=${cache.oldest_seq}&limit=50`;
        console.log('Fetching older messages from:', url);
        const response = await fetch(url);

        if (response.ok) {
            const messages = await response.json();
            console.log('Loaded older messages:', messages);

            if (messages.length > 0) {
                // Sort by seq_no ascending
                messages.sort((a, b) => a.seqNo - b.seqNo);

                // Add to cache (prepend)
                messages.forEach(message => {
                    cache.messages.set(message.seqNo, message);
                });

                // Update oldest_seq
                cache.oldest_seq = messages[0].seqNo;

                // If size > 350, evict from newest end down to 300, set is_at_tail = false
                if (cache.messages.size > 350) {
                    evictFromNewestEnd(chatId, 300);
                    cache.is_at_tail = false;
                }

                // Assert contiguous range
                assertContiguousRange(chatId);

                // Re-render messages
                renderCachedMessages(chatId);

                console.log('Older messages loaded and rendered');
            } else {
                console.log('No older messages available');
            }
        }
    } catch (error) {
        console.error('Error loading older messages:', error);
    }
}

// Load newer messages (pagination - scroll down)
async function loadNewerMessages(chatId) {
    console.log('Loading newer messages for chat ID:', chatId);
    const cache = messageCache[chatId];
    if (!cache || cache.newest_seq === null) {
        console.log('No cache or newest_seq is null, cannot load newer messages');
        return;
    }

    // Only load if not at tail
    if (cache.is_at_tail) {
        console.log('Already at tail, cannot load newer messages');
        return;
    }

    try {
        // GET /messages?conv=X&after_seq={newest_seq}&limit=50
        const url = `/chat/api/messages?conv=${chatId}&after_seq=${cache.newest_seq}&limit=50`;
        console.log('Fetching newer messages from:', url);
        const response = await fetch(url);

        if (response.ok) {
            const messages = await response.json();
            console.log('Loaded newer messages:', messages);

            if (messages.length > 0) {
                // Sort by seq_no ascending
                messages.sort((a, b) => a.seqNo - b.seqNo);

                // Add to cache (append)
                messages.forEach(message => {
                    cache.messages.set(message.seqNo, message);
                });

                // Update newest_seq
                cache.newest_seq = messages[messages.length - 1].seqNo;

                // If response shorter than limit AND newest_seq == server_latest_seq, set is_at_tail = true
                if (messages.length < 50 && cache.newest_seq === cache.server_latest_seq) {
                    cache.is_at_tail = true;
                }

                // If size > 350, evict from oldest end down to 300
                if (cache.messages.size > 350) {
                    evictFromOldestEnd(chatId, 300);
                }

                // Assert contiguous range
                assertContiguousRange(chatId);

                // Re-render messages
                renderCachedMessages(chatId);

                console.log('Newer messages loaded and rendered');
            } else {
                console.log('No newer messages available');
            }
        }
    } catch (error) {
        console.error('Error loading newer messages:', error);
    }
}

// Evict from newest end (used when scrolling up)
function evictFromNewestEnd(chatId, targetSize) {
    const cache = messageCache[chatId];
    if (!cache) return;

    const cacheSize = cache.messages.size;
    const messagesToRemove = cacheSize - targetSize;

    if (messagesToRemove <= 0) return;

    console.log('Evicting from newest end:', messagesToRemove, 'messages');

    // Get all seq_nos sorted descending, remove from newest end
    const sortedSeqs = Array.from(cache.messages.keys()).sort((a, b) => b - a);
    for (let i = 0; i < messagesToRemove; i++) {
        cache.messages.delete(sortedSeqs[i]);
    }

    // Update newest_seq
    if (cache.messages.size > 0) {
        const newHighestSeq = Math.max(...cache.messages.keys());
        cache.newest_seq = newHighestSeq;
    } else {
        cache.newest_seq = null;
    }

    console.log('Evicted from newest end, new cache size:', cache.messages.size);
}

// Evict from oldest end (used when scrolling down)
function evictFromOldestEnd(chatId, targetSize) {
    const cache = messageCache[chatId];
    if (!cache) return;

    const cacheSize = cache.messages.size;
    const messagesToRemove = cacheSize - targetSize;

    if (messagesToRemove <= 0) return;

    console.log('Evicting from oldest end:', messagesToRemove, 'messages');

    // Get all seq_nos sorted ascending, remove from oldest end
    const sortedSeqs = Array.from(cache.messages.keys()).sort((a, b) => a - b);
    for (let i = 0; i < messagesToRemove; i++) {
        cache.messages.delete(sortedSeqs[i]);
    }

    // Update oldest_seq
    if (cache.messages.size > 0) {
        const newLowestSeq = Math.min(...cache.messages.keys());
        cache.oldest_seq = newLowestSeq;
    } else {
        cache.oldest_seq = null;
    }

    console.log('Evicted from oldest end, new cache size:', cache.messages.size);
}

// Assert contiguous range (debug builds)
function assertContiguousRange(chatId) {
    const cache = messageCache[chatId];
    if (!cache || cache.messages.size < 2) return;

    const sortedSeqs = Array.from(cache.messages.keys()).sort((a, b) => a - b);
    for (let i = 0; i < sortedSeqs.length - 1; i++) {
        if (sortedSeqs[i + 1] !== sortedSeqs[i] + 1) {
            console.error('Cache invariant violated: gap detected at seq', sortedSeqs[i], 'to', sortedSeqs[i + 1]);
            throw new Error(`Cache invariant violated: gap between seq ${sortedSeqs[i]} and ${sortedSeqs[i + 1]}`);
        }
    }
    console.log('Cache contiguous range assertion passed');
}

// Render messages from cache (sorted by seq_no)
function renderCachedMessages(chatId) {
    const cache = messageCache[chatId];
    if (!cache) return;

    // Clear message area
    messageArea.innerHTML = '';

    // Get messages sorted by seq_no
    const sortedMessages = Array.from(cache.messages.entries())
        .sort((a, b) => a[0] - b[0])
        .map(entry => entry[1]);

    // Render each message
    sortedMessages.forEach(message => {
        renderMessage(message);
    });

    // Scroll to bottom
    messageArea.scrollTop = messageArea.scrollHeight;
}

// Add incoming message to cache (real-time via WebSocket)
function addMessageToCache(chatId, message) {
    if (!messageCache[chatId]) {
        messageCache[chatId] = {
            messages: new Map(),
            oldest_seq: null,
            newest_seq: null,
            server_latest_seq: null,
            is_at_tail: false
        };
    }

    const cache = messageCache[chatId];

    // Add message to cache (seq_no is server-assigned only)
    cache.messages.set(message.seqNo, message);

    // Update newest_seq
    if (cache.newest_seq === null || message.seqNo > cache.newest_seq) {
        cache.newest_seq = message.seqNo;
    }

    // Update oldest_seq if this is the first message
    if (cache.oldest_seq === null) {
        cache.oldest_seq = message.seqNo;
    }

    // Update server_latest_seq and set is_at_tail = true (we're at latest)
    cache.server_latest_seq = message.seqNo;
    cache.is_at_tail = true;

    // If size > 350, evict from oldest end down to 300
    if (cache.messages.size > 350) {
        evictFromOldestEnd(chatId, 300);
    }

    // Assert contiguous range
    assertContiguousRange(chatId);
}

// Load all users for participant selection
async function loadUsers() {
    if (!username) return;

    try {
        console.log('Loading users for participant selection...');
        const response = await fetch(`/chat/api/users?exclude=${encodeURIComponent(username)}`);

        if (response.ok) {
            const users = await response.json();
            console.log('Loaded users:', users);
            populateUserSelects(users);
        } else {
            console.error('Failed to load users:', response.status);
        }
    } catch (error) {
        console.error('Error loading users:', error);
    }
}

// Populate user select dropdowns
function populateUserSelects(users) {
    const options = users.map(user => {
        const onlineIndicator = user.isOnline ? ' (Online)' : '';
        return `<option value="${user.username}" class="${user.isOnline ? 'online' : ''}">${user.username}${onlineIndicator}</option>`;
    }).join('');

    const defaultOption = '<option value="">-- Select users --</option>';

    if (quickParticipants) {
        quickParticipants.innerHTML = defaultOption + options;
        console.log('Populated quick participants select');
    }

    if (participantsSelect) {
        participantsSelect.innerHTML = defaultOption + options;
        console.log('Populated modal participants select');
    }
}

// Get selected participants from a multi-select element
function getSelectedParticipants(selectElement) {
    if (!selectElement) return [];
    const selected = Array.from(selectElement.selectedOptions)
        .map(option => option.value)
        .filter(value => value);
    return selected;
}

// Modal functions
function openModal() {
    createChatModal.classList.remove('hidden');
    loadUsers(); // Load users when modal opens
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
    // Handle both millisecond timestamps and LocalDateTime strings
    const date = new Date(typeof timestamp === 'number' ? timestamp : timestamp);
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
} else {
    console.log('Quick create chat form not found (may be normal if not in empty state)');
}

console.log('All event listeners setup completed');

// Chat action event listeners
if (document.querySelector('#leaveChatBtn')) {
    document.querySelector('#leaveChatBtn').addEventListener('click', leaveChat);
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

// HTTP API fallback for sending messages
async function sendMessageViaHTTP(chatId, sender, content, messageId) {
    try {
        console.log('Sending message via HTTP API...');
        
        const response = await fetch(`/chat/api/chats/${chatId}/messages`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                sender: sender,
                content: content,
                messageId: messageId
            })
        });
        
        if (response.ok) {
            const result = await response.json();
            console.log('Message sent via HTTP API successfully:', result);
            messageInput.value = '';
            console.log('sendMessage completed successfully via HTTP');
        } else {
            const errorText = await response.text();
            console.error('HTTP API error:', response.status, errorText);
            alert('Failed to send message via HTTP API: ' + errorText);
        }
    } catch (error) {
        console.error('Error sending message via HTTP API:', error);
        console.error('Error stack:', error.stack);
        alert('Failed to send message: ' + error.message);
    }
}
