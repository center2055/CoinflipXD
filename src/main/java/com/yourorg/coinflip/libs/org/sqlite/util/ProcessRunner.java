package com.yourorg.coinflip.libs.org.sqlite.util;

import java.util.concurrent.TimeUnit;

/**
 * Safe stub used to satisfy sqlite-jdbc without exposing Runtime.exec.
 */
public final class ProcessRunner {

    public String runAndWaitFor(String command) {
        return "";
    }

    public String runAndWaitFor(String command, long timeout, TimeUnit unit) {
        return "";
    }
}
