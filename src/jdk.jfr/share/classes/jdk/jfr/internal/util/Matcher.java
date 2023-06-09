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
package jdk.jfr.internal.util;

public final class Matcher {

    /**
     * Returns true if text matches pattern of characters, '*' and '?'
     */
    public static boolean match(String text, String pattern) {
        if (pattern.length() == 0) {
            // empty filter string matches if string is empty
            return text.length() == 0;
        }
        if (pattern.charAt(0) == '*') { // recursive check
            pattern = pattern.substring(1);
            for (int n = 0; n <= text.length(); n++) {
                if (match(text.substring(n), pattern))
                    return true;
            }
        } else if (text.length() == 0) {
            // empty string and non-empty filter does not match
            return false;
        } else if (pattern.charAt(0) == '?') {
            // eat any char and move on
            return match(text.substring(1), pattern.substring(1));
        } else if (pattern.charAt(0) == text.charAt(0)) {
            // eat chars and move on
            return match(text.substring(1), pattern.substring(1));
        }
        return false;
    }
}
