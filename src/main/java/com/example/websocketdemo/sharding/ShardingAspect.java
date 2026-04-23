package com.example.websocketdemo.sharding;

import com.example.websocketdemo.model.Chat;
import com.example.websocketdemo.model.Message;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Aspect
@Component
public class ShardingAspect {

    @Autowired
    private ShardRouter shardRouter;

    @Value("${sharding.enabled:false}")
    private boolean shardingEnabled;

    @Around("@annotation(com.example.websocketdemo.sharding.ShardOperation)")
    public Object aroundShardOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!shardingEnabled) {
            return joinPoint.proceed();
        }

        Long chatId = extractChatId(joinPoint.getArgs());
        if (chatId == null) {
            return joinPoint.proceed();
        }

        String shard = shardRouter.resolveShard(chatId);
        String originalShard = ShardContext.getShard();
        
        try {
            ShardContext.setShard(shard);
            return joinPoint.proceed();
        } finally {
            if (originalShard != null) {
                ShardContext.setShard(originalShard);
            } else {
                ShardContext.clear();
            }
        }
    }

    private Long extractChatId(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof Long) {
                return (Long) arg;
            } else if (arg instanceof Chat) {
                return ((Chat) arg).getId();
            } else if (arg instanceof Message) {
                return ((Message) arg).getChatId();
            } else if (arg instanceof List && !((List<?>) arg).isEmpty()) {
                Object first = ((List<?>) arg).get(0);
                if (first instanceof Chat) {
                    return ((Chat) first).getId();
                } else if (first instanceof Message) {
                    return ((Message) first).getChatId();
                }
            }
        }
        return null;
    }
}
