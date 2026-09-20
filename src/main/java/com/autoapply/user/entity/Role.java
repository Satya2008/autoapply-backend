package com.autoapply.user.entity;

public enum Role {
    USER,
    ADMIN,
    SUPER_ADMIN;

    public boolean isAdmin() {
        return this == ADMIN || this == SUPER_ADMIN;
    }
}
