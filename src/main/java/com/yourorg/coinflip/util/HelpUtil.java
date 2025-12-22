package com.yourorg.coinflip.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class HelpUtil {

    public record HelpEntry(String command, String description, boolean adminOnly) {
    }

    private static final List<HelpEntry> ENTRIES = List.of(
            new HelpEntry("/cf", "open browser", false),
            new HelpEntry("/cf <amount>", "create public coinflip", false),
            new HelpEntry("/cf <amount> <player>", "challenge player", false),
            new HelpEntry("/cf <player> accept|deny", "respond to private challenge", false),
            new HelpEntry("/cf cancel", "cancel your coinflip", false),
            new HelpEntry("/cf stats [player]", "view stats", false),
            new HelpEntry("/cf reload", "reload configuration", true),
            new HelpEntry("/cf cancel <player>", "force cancel coinflip", true)
    );

    private HelpUtil() {
    }

    public static List<HelpEntry> entries(boolean includeAdmin) {
        if (includeAdmin) {
            return ENTRIES;
        }
        List<HelpEntry> filtered = new ArrayList<>();
        for (HelpEntry entry : ENTRIES) {
            if (!entry.adminOnly()) {
                filtered.add(entry);
            }
        }
        return Collections.unmodifiableList(filtered);
    }

    public static List<String> lines(boolean includeAdmin) {
        List<String> lines = new ArrayList<>();
        lines.add("CoinflipXD Commands:");
        for (HelpEntry entry : entries(includeAdmin)) {
            lines.add(entry.command() + " - " + entry.description());
        }
        return Collections.unmodifiableList(lines);
    }
}
