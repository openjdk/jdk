/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package jdk.internal.util;

/**
 * A software Version with major, minor, and micro components.
 * @param major major version
 * @param minor minor version
 * @param micro micro version
 */
public record OSVersion(int major, int minor, int micro) implements Comparable<OSVersion> {

    // Parse and save the current OS version
    private static final OSVersion CURRENT_OSVERSION = initVersion();

    /**
     * {@return a Version for major, minor versions}
     *
     * @param major major version
     * @param minor minor version
     */
    public OSVersion(int major, int minor) {
        this(major, minor, 0);
    }

    /*
     * Initialize the current Version from the os.version system property
     */
    private static OSVersion initVersion() {
        final String osVer = StaticProperty.osVersion();
        try {
            return parse(osVer);
        } catch (IllegalArgumentException iae) {
            throw new InternalError("os.version malformed: " + osVer, iae);
        }
    }

    /**
     * {@return the current operating system version}
     */
    public static OSVersion current() {
        return CURRENT_OSVERSION;
    }

    /**
     * {@return Compare this version with another version}
     *
     * @param other the object to be compared
     */
    @Override
    public int compareTo(OSVersion other) {
        int result = Integer.compare(major, other.major);
        if (result == 0) {
            result = Integer.compare(minor, other.minor);
            if (result == 0) {
                return Integer.compare(micro, other.micro);
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return (micro == 0)
                ? major + "." + minor
                : major + "." + minor + "." + micro;
    }

    /**
     * {@return A Version parsed from a version string split on "." characters}
     * Only major, minor, and micro version numbers are parsed, finer detail is ignored.
     * Missing values for minor and micro are replaced with zero.
     * The string must start with a number, if there is a '.' it must be followed by a number.
     * <p>
     * Parsed by hand because it is called before RegEx can be initialized safely.
     *
     * @param str a version string
     * @throws IllegalArgumentException if the string does not start with digits
     *          or digits do not follow '.'
     */
    public static OSVersion parse(String str) throws IllegalArgumentException {
        int len = str.length();
        int majorStart = 0;
        int majorEnd = skipDigits(str, majorStart);
        int major = Integer.parseInt(str.substring(majorStart, majorEnd));

        int minor = 0, micro = 0;
        if (majorEnd < len && str.charAt(majorEnd) == '.') {
            int minorStart = majorEnd + 1;
            int minorEnd = skipDigits(str, minorStart);
            minor = Integer.parseInt(str.substring(minorStart, minorEnd));

            if (minorEnd < len && str.charAt(minorEnd) == '.') {
                int microStart = minorEnd + 1;
                int microEnd = skipDigits(str, microStart);
                micro = Integer.parseInt(str.substring(microStart, microEnd));
            }
        }
        return new OSVersion(major, minor, micro);
    }

    /**
     * {@return The index of the first non-digit from start}
     * @throws IllegalArgumentException if there are no digits
     */
    private static int skipDigits(String s, int start) {
        int index = start;
        while (index < s.length() && Character.isDigit(s.charAt(index))) {
            index++;
        }
        if (index == start)
            throw new IllegalArgumentException("malformed version, missing digits: " + s);
        return index;
    }
}
