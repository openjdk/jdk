/*
 * Copyright (c) 1995, 2004, Oracle and/or its affiliates. All rights reserved.
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

package sun.misc;

/**
 * A class to represent a regular expression.  Only handles '*'s.
 * @author  James Gosling
 */

public class Regexp {
    /** if true then the matching process ignores case. */
    public boolean ignoreCase;

    /*
     * regular expressions are carved into three regions: a constant string
     * prefix, a constant string suffix, and a series of floating strings in
     * between.  In the input regular expression, they are separated by *s
     */
    public String exp;
    public String prefix, suffix;
    public boolean exact;
    public int prefixLen, suffixLen, totalLen;
    public String mids[];

    /** Create a new regular expression object.  The regular expression
        is a series of constant strings separated by *s.  For example:
        <dl>
        <dt>*.gif       <dd>Matches any string that ends in ".gif".
        <dt>/tmp/*      <dd>Matches any string that starts with "/tmp/".
        <dt>/tmp/*.gif  <dd>Matches any string that starts with "/tmp/" and ends
                        with ".gif".
        <dt>/tmp/*new*.gif <dd>Matches any string that starts with "/tmp/"
                        and ends with ".gif" and has "new" somewhere in between.
        </dl>
     */
    public Regexp (String s) {
        exp = s;
        int firstst = s.indexOf('*');
        int lastst = s.lastIndexOf('*');
        if (firstst < 0) {
            totalLen = s.length();
            exact = true;       // no * s
        } else {
            prefixLen = firstst;
            if (firstst == 0)
                prefix = null;
            else
                prefix = s.substring(0, firstst);
            suffixLen = s.length() - lastst - 1;
            if (suffixLen == 0)
                suffix = null;
            else
                suffix = s.substring(lastst + 1);
            int nmids = 0;
            int pos = firstst;
            while (pos < lastst && pos >= 0) {
                nmids++;
                pos = s.indexOf('*', pos + 1);
            }
            totalLen = prefixLen + suffixLen;
            if (nmids > 0) {
                mids = new String[nmids];
                pos = firstst;
                for (int i = 0; i < nmids; i++) {
                    pos++;
                    int npos = s.indexOf('*', pos);
                    if (pos < npos) {
                        mids[i] = s.substring(pos, npos);
                        totalLen += mids[i].length();
                    }
                    pos = npos;
                }
            }
        }
    }

    /** Returns true iff the String s matches this regular expression. */
    final boolean matches(String s) {
        return matches(s, 0, s.length());
    }

    /** Returns true iff the substring of s from offset for len characters
        matches this regular expression. */
    boolean matches(String s, int offset, int len) {
        if (exact)
            return len == totalLen &&
                exp.regionMatches(ignoreCase, 0, s, offset, len);
        if (len < totalLen)
            return false;
        if (prefixLen > 0 &&
                !prefix.regionMatches(ignoreCase,
                                      0, s, offset, prefixLen)
                ||
                suffixLen > 0 &&
                !suffix.regionMatches(ignoreCase,
                                      0, s, offset + len - suffixLen,
                                      suffixLen))
            return false;
        if (mids == null)
            return true;
        int nmids = mids.length;
        int spos = offset + prefixLen;
        int limit = offset+len-suffixLen;
        for (int i = 0; i<nmids; i++) {
            String ms = mids[i];
            int ml = ms.length();
            while (spos+ml<=limit &&
                   !ms.regionMatches(ignoreCase,
                                     0, s, spos, ml))
                spos++;
            if (spos+ml>limit)
                return false;
            spos+=ml;
        }
        return true;
    }

}
