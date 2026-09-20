package com.autoapply.notification;

public interface NotificationChannel {

    /** Name used in the notify.channels setting. */
    String name();

    boolean isConfigured();

    void send(NotificationEvent event) throws Exception;
}
