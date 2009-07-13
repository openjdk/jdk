/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.nio.fs;

/**
 * Utility methods
 */

class Util {
    private Util() { }

    /**
     * Splits a string around the given character. The array returned by this
     * method contains each substring that is terminated by the character. Use
     * for simple string spilting cases when needing to avoid loading regex.
     */
    static String[] split(String s, char c) {
        int count = 0;
        for (int i=0; i<s.length(); i++) {
            if (s.charAt(i) == c)
                count++;
        }
        String[] result = new String[count+1];
        int n = 0;
        int last = 0;
        for (int i=0; i<s.length(); i++) {
            if (s.charAt(i) == c) {
                result[n++] = s.substring(last, i);
                last = i + 1;
            }
        }
        result[n] = s.substring(last, s.length());
        return result;

    }
}
