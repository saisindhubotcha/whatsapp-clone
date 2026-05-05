package com.example.websocketdemo.service;

import com.example.websocketdemo.model.User;
import com.example.websocketdemo.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
@AllArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public Map<String, Object> registerUser(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }

        if (userRepository.existsById(username)) {
            throw new IllegalArgumentException("Username already taken");
        }

        User user = User.builder()
                .username(username)
                .sessionId(UUID.randomUUID().toString())
                .createdAt(LocalDateTime.now())
                .lastSeen(LocalDateTime.now())
                .isOnline(true)
                .build();

        userRepository.save(user);

        return Map.of("success", true, "user", user);
    }

    public Map<String, Object> loginUser(String username) {
        User user = userRepository.findById(username).orElse(null);
        boolean isNew = false;

        if (user == null) {
            isNew = true;
            user = User.builder()
                    .username(username)
                    .sessionId(UUID.randomUUID().toString())
                    .createdAt(LocalDateTime.now())
                    .lastSeen(LocalDateTime.now())
                    .isOnline(true)
                    .build();
        } else {
            user.setLastSeen(LocalDateTime.now());
            user.setIsOnline(true);
        }

        userRepository.save(user);

        return Map.of("success", true, "user", user, "isNewUser", isNew);
    }

    public boolean userExists(String username) {
        return userRepository.existsById(username);
    }

    public void setUserOnline(String username) {
        User user = userRepository.findById(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        user.setIsOnline(true);
        user.setLastSeen(LocalDateTime.now());
        userRepository.save(user);
    }

    public void setUserOffline(String username) {
        User user = userRepository.findById(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        user.setIsOnline(false);
        user.setLastSeen(LocalDateTime.now());
        userRepository.save(user);
    }

    public void handleWebSocketDisconnect(String username) {
        if (username != null) {
            setUserOffline(username);
        }
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }
}
