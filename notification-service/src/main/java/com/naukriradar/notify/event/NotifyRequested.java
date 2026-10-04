package com.naukriradar.notify.event;

import java.util.List;
import java.util.Map;

import com.naukriradar.notify.channel.Channel;
import com.naukriradar.notify.channel.Recipient;

/**
 * Payload of notify.requested. The sender (core-api, which knows the users) already decided
 * who gets it and on which channels; this service only renders and delivers.
 */
public record NotifyRequested(String template, Recipient recipient, List<Channel> channels, Map<String, Object> data) {
}
