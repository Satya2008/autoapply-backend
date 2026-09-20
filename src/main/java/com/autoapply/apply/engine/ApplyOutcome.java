package com.autoapply.apply.engine;

public record ApplyOutcome(Status status, String message, String screenshotPath) {

    public enum Status {
        SUCCESS, FAILED, SKIPPED, DRY_RUN
    }

    public static ApplyOutcome success(String message) {
        return new ApplyOutcome(Status.SUCCESS, message, null);
    }

    public static ApplyOutcome failed(String message, String screenshotPath) {
        return new ApplyOutcome(Status.FAILED, message, screenshotPath);
    }

    public static ApplyOutcome skipped(String message) {
        return new ApplyOutcome(Status.SKIPPED, message, null);
    }

    public static ApplyOutcome dryRun(String message) {
        return new ApplyOutcome(Status.DRY_RUN, message, null);
    }

    public boolean isSuccessful() {
        return status == Status.SUCCESS || status == Status.DRY_RUN;
    }
}
