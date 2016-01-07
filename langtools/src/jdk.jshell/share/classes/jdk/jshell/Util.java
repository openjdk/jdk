/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jshell;

import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.lang.model.element.Name;
import static jdk.internal.jshell.remote.RemoteCodes.DOIT_METHOD_NAME;
import static jdk.internal.jshell.remote.RemoteCodes.prefixPattern;

/**
 * Assorted shared utilities.
 * @author Robert Field
 */
class Util {

    static final String REPL_CLASS_PREFIX = "$REPL";
    static final String REPL_DOESNOTMATTER_CLASS_NAME = REPL_CLASS_PREFIX+"00DOESNOTMATTER";

    static boolean isDoIt(Name name) {
        return isDoIt(name.toString());
    }

    static boolean isDoIt(String sname) {
        return sname.equals(DOIT_METHOD_NAME);
    }

    static String expunge(String s) {
        StringBuilder sb = new StringBuilder();
        for (String comp : prefixPattern.split(s)) {
            sb.append(comp);
        }
        return sb.toString();
    }

    static String asLetters(int i) {
        if (i == 0) {
            return "";
        }

        char buf[] = new char[33];
        int charPos = 32;

        i = -i;
        while (i <= -26) {
            buf[charPos--] = (char) ('A'-(i % 26));
            i = i / 26;
        }
        buf[charPos] = (char) ('A'-i);

        return new String(buf, charPos, (33 - charPos));
    }


    static String trimEnd(String s) {
        int last = s.length() - 1;
        int i = last;
        while (i >= 0 && Character.isWhitespace(s.charAt(i))) {
            --i;
        }
        if (i != last) {
            return s.substring(0, i + 1);
        } else {
            return s;
        }
    }

    static <T> Stream<T> stream(Iterable<T> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    static class Pair<T, U> {
        final T first;
        final U second;

        Pair(T first, U second) {
            this.first = first;
            this.second = second;
        }
    }
}
