/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
package com.sun.xml.internal.ws.util;

import java.util.StringTokenizer;


/**
 * Provides some version utilities.
 *
 * @author JAX-WS Development Team
 */

public final class VersionUtil {

    public static boolean isVersion20(String version) {
        return JAXWS_VERSION_20.equals(version);
    }

    /**
     * @param version
     * @return true if version is a 2.0 version
     */
    public static boolean isValidVersion(String version) {
        return isVersion20(version);
    }

    public static String getValidVersionString() {
        return JAXWS_VERSION_20;
    }

    /**
     * BugFix# 4948171
     * Method getCanonicalVersion.
     *
     * Converts a given version to the format "a.b.c.d"
     * a - major version
     * b - minor version
     * c - minor minor version
     * d - patch version
     *
     * @return int[] Canonical version number
     */
    public static int[] getCanonicalVersion(String version) {
        int[] canonicalVersion = new int[4];

        // initialize the default version numbers
        canonicalVersion[0] = 1;
        canonicalVersion[1] = 1;
        canonicalVersion[2] = 0;
        canonicalVersion[3] = 0;

        final String DASH_DELIM = "_";
        final String DOT_DELIM = ".";

        StringTokenizer tokenizer =
                new StringTokenizer(version, DOT_DELIM);
        String token = tokenizer.nextToken();

        // first token is major version and must not have "_"
        canonicalVersion[0] = Integer.parseInt(token);

        // resolve the minor version
        token = tokenizer.nextToken();
        if (token.indexOf(DASH_DELIM) == -1) {
            // a.b
            canonicalVersion[1] = Integer.parseInt(token);
        } else {
            // a.b_c
            StringTokenizer subTokenizer =
                    new StringTokenizer(token, DASH_DELIM);
            canonicalVersion[1] = Integer.parseInt(subTokenizer.nextToken());
            // leave minorMinor default

            canonicalVersion[3] = Integer.parseInt(subTokenizer.nextToken());
        }

        // resolve the minorMinor and patch version, if any
        if (tokenizer.hasMoreTokens()) {
            token = tokenizer.nextToken();
            if (token.indexOf(DASH_DELIM) == -1) {
                // minorMinor
                canonicalVersion[2] = Integer.parseInt(token);

                // resolve patch, if any
                if (tokenizer.hasMoreTokens())
                    canonicalVersion[3] = Integer.parseInt(tokenizer.nextToken());
            } else {
                // a.b.c_d
                StringTokenizer subTokenizer =
                        new StringTokenizer(token, DASH_DELIM);
                // minorMinor
                canonicalVersion[2] = Integer.parseInt(subTokenizer.nextToken());

                // patch
                canonicalVersion[3] = Integer.parseInt(subTokenizer.nextToken());
            }
        }

        return canonicalVersion;
    }

    /**
     *
     * @param version1
     * @param version2
     * @return -1, 0 or 1 based upon the comparison results
     * -1 if version1 is less than version2
     * 0 if version1 is equal to version2
     * 1 if version1 is greater than version2
     */
    public static int compare(String version1, String version2) {
        int[] canonicalVersion1 = getCanonicalVersion(version1);
        int[] canonicalVersion2 = getCanonicalVersion(version2);

        if (canonicalVersion1[0] < canonicalVersion2[0]) {
            return -1;
        } else if (canonicalVersion1[0] > canonicalVersion2[0]) {
            return 1;
        } else {
            if (canonicalVersion1[1] < canonicalVersion2[1]) {
                return -1;
            } else if (canonicalVersion1[1] > canonicalVersion2[1]) {
                return 1;
            } else {
                if (canonicalVersion1[2] < canonicalVersion2[2]) {
                    return -1;
                } else if (canonicalVersion1[2] > canonicalVersion2[2]) {
                    return 1;
                } else {
                    if (canonicalVersion1[3] < canonicalVersion2[3]) {
                        return -1;
                } else if (canonicalVersion1[3] > canonicalVersion2[3]) {
                    return 1;
                } else
                    return 0;
                }
            }
        }
    }

    public static final String JAXWS_VERSION_20 = "2.0";
    // the latest version is default
    public static final String JAXWS_VERSION_DEFAULT = JAXWS_VERSION_20;
}
