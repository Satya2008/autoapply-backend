package com.autoapply.apply.risk;

public enum BotRisk {

    /** Simple form, no anti-automation defences worth worrying about. Safe to submit automatically. */
    LOW,

    /** Some friction likely - a login wall or light fingerprinting. Automated with care, or handed over. */
    MEDIUM,

    /**
     * Known to fingerprint, challenge, or ban automated submissions. Never driven by the browser
     * engine; the candidate finishes these by hand with everything pre-filled for them.
     */
    HIGH
}
