/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
public record Version(int major, int minor, int micro) implements Comparable<Version> {

    /**
     * {@return a Version for major, minor versions}
     *
     * @param major major version
     * @param minor minor version
     */
    public Version(int major, int minor) {
        this(major, minor, 0);
    }

    /**
     * {@return Compare this version with another version}
     *
     * @param other the object to be compared
     */
    @Override
    public int compareTo(Version other) {
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
     * @param str a version string
     */
    public static Version parse(String str) throws IllegalArgumentException {
        try {
            int majorStart = 0;
            int majorEnd = skipDigits(str, majorStart);
            if (majorEnd == majorStart)
                throw new IllegalArgumentException("empty or malformed version string: " + str);
            int minorStart = (majorEnd < str.length() && str.charAt(majorEnd) == '.') ? majorEnd + 1 : majorEnd;
            int minorEnd = skipDigits(str, minorStart);
            int microStart = (minorEnd < str.length() && str.charAt(minorEnd) == '.') ? minorEnd + 1 : minorEnd;
            int microEnd = skipDigits(str, microStart);

            int major = Integer.parseInt(str.substring(majorStart, majorEnd));
            int minor = (minorStart < minorEnd) ? Integer.parseInt(str.substring(minorStart, minorEnd)) : 0;
            int micro = (microStart < microEnd) ? Integer.parseInt(str.substring(microStart, microEnd)) : 0;

            return new Version(major, minor, micro);
        } catch (IllegalArgumentException | IndexOutOfBoundsException ex) {
            throw new IllegalArgumentException("malformed version string: " + str);
        }
    }

    // Return the index of the first non-digit from start.
    private static int skipDigits(String s, int start) {
        int len = s.length();
        while (start < len && Character.isDigit(s.charAt(start))) {
            start++;
        }
        return start;
    }
}
