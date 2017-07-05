/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.xjc.util;

import java.text.ParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class to parse a string
 *
 * @author Kohsuke Kawaguchi
 */
public final class StringCutter {
    private final String original;
    private String s;
    private boolean ignoreWhitespace;

    public StringCutter(String s, boolean ignoreWhitespace) {
        this.s = this.original = s;
        this.ignoreWhitespace = ignoreWhitespace;
    }

    public void skip(String regexp) throws ParseException {
        next(regexp);
    }

    public String next(String regexp) throws ParseException {
        trim();
        Pattern p = Pattern.compile(regexp);
        Matcher m = p.matcher(s);
        if(m.lookingAt()) {
            String r = m.group();
            s = s.substring(r.length());
            trim();
            return r;
        } else
            throw error();
    }

    private ParseException error() {
        return new ParseException(original,original.length()-s.length());
    }

    public String until(String regexp) throws ParseException {
        Pattern p = Pattern.compile(regexp);
        Matcher m = p.matcher(s);
        if(m.find()) {
            String r =  s.substring(0,m.start());
            s = s.substring(m.start());
            if(ignoreWhitespace)
                r = r.trim();
            return r;
        } else {
            // return everything left
            String r = s;
            s = "";
            return r;
        }
    }

    public char peek() {
        return s.charAt(0);
    }

    private void trim() {
        if(ignoreWhitespace)
            s = s.trim();
    }

    public int length() {
        return s.length();
    }
}
