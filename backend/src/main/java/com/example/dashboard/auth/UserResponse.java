package com.example.dashboard.auth;

import com.example.dashboard.user.AppUser;

public record UserResponse(String username, String displayName) {

    public static UserResponse from(AppUser user) {
        return new UserResponse(user.username(), user.displayName());
    }
}
