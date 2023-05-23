// Copyright 2017 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
package jdk.internal.icu.impl.locale;

import java.util.Objects;

public final class LSR {
    public static final int REGION_INDEX_LIMIT = 1001 + 26 * 26;

    public static final int EXPLICIT_LSR = 7;
    public static final int EXPLICIT_LANGUAGE = 4;
    public static final int EXPLICIT_SCRIPT = 2;
    public static final int EXPLICIT_REGION = 1;
    public static final int IMPLICIT_LSR = 0;
    public static final int DONT_CARE_FLAGS = 0;

    public static final boolean DEBUG_OUTPUT = false;

    public final String language;
    public final String script;
    public final String region;
    /** Index for region, negative if ill-formed. @see indexForRegion */
    final int regionIndex;
    public final int flags;

    public LSR(String language, String script, String region, int flags) {
        this.language = language;
        this.script = script;
        this.region = region;
        regionIndex = indexForRegion(region);
        this.flags = flags;
    }

    /**
     * Returns a positive index (>0) for a well-formed region code.
     * Do not rely on a particular region->index mapping; it may change.
     * Returns 0 for ill-formed strings.
     */
    public static final int indexForRegion(String region) {
        if (region.length() == 2) {
            int a = region.charAt(0) - 'A';
            if (a < 0 || 25 < a) { return 0; }
            int b = region.charAt(1) - 'A';
            if (b < 0 || 25 < b) { return 0; }
            return 26 * a + b + 1001;
        } else if (region.length() == 3) {
            int a = region.charAt(0) - '0';
            if (a < 0 || 9 < a) { return 0; }
            int b = region.charAt(1) - '0';
            if (b < 0 || 9 < b) { return 0; }
            int c = region.charAt(2) - '0';
            if (c < 0 || 9 < c) { return 0; }
            return (10 * a + b) * 10 + c + 1;
        }
        return 0;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder(language);
        if (!script.isEmpty()) {
            result.append('-').append(script);
        }
        if (!region.isEmpty()) {
            result.append('-').append(region);
        }
        return result.toString();
    }

    public boolean isEquivalentTo(LSR other) {
        return language.equals(other.language)
                && script.equals(other.script)
                && region.equals(other.region);
    }

    @Override
    public boolean equals(Object obj) {
        LSR other;
        return this == obj ||
                (obj != null
                && obj.getClass() == this.getClass()
                && language.equals((other = (LSR) obj).language)
                && script.equals(other.script)
                && region.equals(other.region)
                && flags == other.flags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(language, script, region, flags);
    }
}
