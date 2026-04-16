package com.example.websocketdemo.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Builder;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponseDto {
    private Boolean success;
    private String message;
    private String error;
    private Object data;
    
    public static ApiResponseDto success(Object data, String message) {
        return ApiResponseDto.builder()
                .success(true)
                .data(data)
                .message(message)
                .build();
    }
    
    public static ApiResponseDto success(String message) {
        return ApiResponseDto.builder()
                .success(true)
                .message(message)
                .build();
    }
    
    public static ApiResponseDto error(String error) {
        return ApiResponseDto.builder()
                .success(false)
                .error(error)
                .build();
    }
}
