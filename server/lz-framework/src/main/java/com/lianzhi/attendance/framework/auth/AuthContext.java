package com.lianzhi.attendance.framework.auth;

import java.util.concurrent.Callable;

/**
 * 存放当前请求的登录身份。
 */
public final class AuthContext {

    private static final ThreadLocal<AuthUser> HOLDER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(AuthUser user) {
        HOLDER.set(user);
    }

    public static AuthUser get() {
        return HOLDER.get();
    }

    public static Long currentUserId() {
        AuthUser user = HOLDER.get();
        return user == null ? null : user.getUserId();
    }

    public static String currentUsername() {
        AuthUser user = HOLDER.get();
        return user == null ? null : user.getUsername();
    }

    public static void clear() {
        HOLDER.remove();
    }

    /** 定时任务等非 Web 线程中需要临时身份时使用，执行完必须清理。 */
    public static void runAs(AuthUser user, Runnable task) {
        AuthUser previous = HOLDER.get();
        set(user);
        try {
            task.run();
        } finally {
            if (previous == null) {
                clear();
            } else {
                set(previous);
            }
        }
    }

    public static <T> T callAs(AuthUser user, Callable<T> task) throws Exception {
        AuthUser previous = HOLDER.get();
        set(user);
        try {
            return task.call();
        } finally {
            if (previous == null) {
                clear();
            } else {
                set(previous);
            }
        }
    }
}
