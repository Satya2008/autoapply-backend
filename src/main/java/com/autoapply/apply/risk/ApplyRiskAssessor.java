package com.autoapply.apply.risk;

import com.autoapply.apply.portal.ApplyPortalConfig;
import com.autoapply.apply.portal.ApplyPortalConfigRepository;
import com.autoapply.settings.SettingKeys;
import com.autoapply.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * Decides whether an application can safely be submitted by the browser engine.
 *
 * Sites that fingerprint or challenge automation are never driven automatically - the
 * candidate finishes those themselves with every answer prepared in advance. Which
 * domains fall into which band is configuration, so the lists can be corrected as
 * sites change without touching this class.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApplyRiskAssessor {

    private final ApplyPortalConfigRepository portalRepository;
    private final SettingsService settings;

    public Assessment assess(String applyUrl) {
        if (applyUrl == null || applyUrl.isBlank()) {
            return new Assessment(BotRisk.HIGH, null, "This posting has no apply link");
        }

        String host = hostOf(applyUrl);
        String url = applyUrl.toLowerCase(Locale.ROOT);

        // An explicit portal configuration always wins - an administrator has looked at this site.
        ApplyPortalConfig portal = findPortal(url);
        if (portal != null && portal.getBotRisk() != null) {
            return new Assessment(portal.getBotRisk(), portal,
                    "Portal '" + portal.getName() + "' is marked " + portal.getBotRisk() + " risk");
        }

        if (matchesAny(url, host, settings.getList(SettingKeys.APPLY_RISK_HIGH_DOMAINS))) {
            return new Assessment(BotRisk.HIGH, portal,
                    host + " is known to block automated applications");
        }
        if (matchesAny(url, host, settings.getList(SettingKeys.APPLY_RISK_LOW_DOMAINS))) {
            return new Assessment(BotRisk.LOW, portal,
                    host + " accepts straightforward form submissions");
        }

        // Unknown sites are treated as medium: handed to the candidate unless a portal
        // configuration exists that says how to fill them.
        return new Assessment(portal == null ? BotRisk.MEDIUM : BotRisk.LOW, portal,
                portal == null
                        ? "No configuration for " + host + " yet"
                        : "Using the '" + portal.getName() + "' configuration");
    }

    /** True when the engine is allowed to submit this application without the candidate. */
    public boolean canAutomate(Assessment assessment) {
        if (!settings.getBoolean(SettingKeys.SELENIUM_ENABLED, false)) return false;
        if (!"BROWSER".equalsIgnoreCase(settings.getString(SettingKeys.APPLY_MODE, "SIMULATE"))) return false;
        if (assessment.portal() == null) return false;

        return switch (assessment.risk()) {
            case LOW -> true;
            case MEDIUM -> settings.getBoolean(SettingKeys.APPLY_AUTOMATE_MEDIUM_RISK, false);
            case HIGH -> false;
        };
    }

    private ApplyPortalConfig findPortal(String url) {
        for (ApplyPortalConfig portal : portalRepository.findByEnabledTrueOrderByPriorityAsc()) {
            String pattern = portal.getUrlPattern();
            if (pattern == null || pattern.isBlank()) continue;
            if (url.contains(pattern.toLowerCase(Locale.ROOT))) return portal;
            try {
                if (java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.CASE_INSENSITIVE)
                        .matcher(url).find()) {
                    return portal;
                }
            } catch (Exception ignored) {
                // pattern is a plain substring, already checked above
            }
        }
        return null;
    }

    private boolean matchesAny(String url, String host, List<String> patterns) {
        return patterns.stream()
                .map(p -> p.trim().toLowerCase(Locale.ROOT))
                .filter(p -> !p.isEmpty())
                .anyMatch(p -> host.contains(p) || url.contains(p));
    }

    private String hostOf(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? url.toLowerCase(Locale.ROOT) : host.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return url.toLowerCase(Locale.ROOT);
        }
    }

    public record Assessment(BotRisk risk, ApplyPortalConfig portal, String reason) {
    }
}
