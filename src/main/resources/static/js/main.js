'use strict';

var usernamePage = document.querySelector('#username-page');
var chatPage = document.querySelector('#chat-page');
var usernameForm = document.querySelector('#usernameForm');
var messageForm = document.querySelector('#messageForm');
var messageInput = document.querySelector('#message');
var messageArea = document.querySelector('#messageArea');
var connectingElement = document.querySelector('.connecting');

var stompClient = null;
var username = null;

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

            // Create a default chat for demo purposes
            const createChatResponse = await fetch('/api/v1/chats', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    name: 'General Chat',
                    createdBy: username,
                    participants: [username]
                })
            });

            if (createChatResponse.ok) {
                const chatResult = await createChatResponse.json();
                console.log('Created/Joined chat:', chatResult);
            }

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

function onConnected() {
    // Subscribe to the Public Topic for real-time updates
    stompClient.subscribe('/topic/public', onMessageReceived);
    
    // Send JOIN message
    const joinMessage = {
        type: 'JOIN',
        sender: username
    };
    stompClient.send("/app/chat.addUser", {}, JSON.stringify(joinMessage));
    
    connectingElement.classList.add('hidden');
    
    // Load chat history
    loadChatHistory();
}

function onError(error) {
    connectingElement.textContent = 'Could not connect to WebSocket server. Please refresh this page to try again!';
    connectingElement.style.color = 'red';
}

async function sendMessage(event) {
    var messageContent = messageInput.value.trim();

    if(messageContent && stompClient) {
        try {
            // Send message through WebSocket for real-time delivery
            const chatMessage = {
                type: 'CHAT',
                sender: username,
                content: messageContent
            };
            
            stompClient.send("/app/chat.sendMessage", {}, JSON.stringify(chatMessage));
            messageInput.value = '';
            
            // Also store in database via REST API
            try {
                await fetch('/api/v1/chats/1/messages', {
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
    }
    event.preventDefault();
}

// UI-focused functions
function onMessageReceived(payload) {
    var message = JSON.parse(payload.body);
    renderMessage(message);
}

function renderMessage(message) {
    var messageElement = document.createElement('li');
    
    // Handle both WebSocket messages and database messages
    var sender = message.sender || message.senderUsername;
    var content = message.content;
    var type = message.type;

    if(type === 'JOIN') {
        messageElement.classList.add('event-message');
        content = sender + ' joined!';
    } else if (type === 'LEAVE') {
        messageElement.classList.add('event-message');
        content = sender + ' left!';
    } else {
        messageElement.classList.add('chat-message');

        var avatarElement = document.createElement('i');
        var avatarText = document.createTextNode(sender[0]);
        avatarElement.appendChild(avatarText);
        avatarElement.style['background-color'] = getAvatarColor(sender);

        messageElement.appendChild(avatarElement);

        var usernameElement = document.createElement('span');
        var usernameText = document.createTextNode(sender);
        usernameElement.appendChild(usernameText);
        messageElement.appendChild(usernameElement);
    }

    var textElement = document.createElement('p');
    var messageText = document.createTextNode(content);
    textElement.appendChild(messageText);

    messageElement.appendChild(textElement);

    messageArea.appendChild(messageElement);
    messageArea.scrollTop = messageArea.scrollHeight;
}

async function loadChatHistory() {
    try {
        const response = await fetch('/api/v1/chats/1/messages?username=' + encodeURIComponent(username));
        if (response.ok) {
            const messages = await response.json();
            messages.forEach(message => renderMessage(message));
        }
    } catch (error) {
        console.error('Error loading chat history:', error);
    }
}

function getAvatarColor(messageSender) {
    var hash = 0;
    for (var i = 0; i < messageSender.length; i++) {
        hash = 31 * hash + messageSender.charCodeAt(i);
    }

    var index = Math.abs(hash % colors.length);
    return colors[index];
}

usernameForm.addEventListener('submit', connect, true);
messageForm.addEventListener('submit', sendMessage, true);
