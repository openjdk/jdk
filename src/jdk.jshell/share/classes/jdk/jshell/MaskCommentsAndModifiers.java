/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Within a String, mask code comments and ignored modifiers (within context).
 *
 * @author Robert Field
 */
class MaskCommentsAndModifiers {

    private final static Set<String> IGNORED_MODIFIERS =
            Stream.of( "public", "protected", "private", "static", "final" )
                    .collect( Collectors.toSet() );

    private final static Set<String> OTHER_MODIFIERS =
            Stream.of( "abstract", "strictfp", "transient", "volatile", "synchronized", "native", "default" )
                    .collect( Collectors.toSet() );

    // Builder to accumulate non-masked characters
    private final StringBuilder sbCleared = new StringBuilder();

    // Builder to accumulate masked characters
    private final StringBuilder sbMask = new StringBuilder();

    // The input string
    private final String str;

    // Entire input string length
    private final int length;

    // The next character position
    private int next = 0;

    // The current character
    private int c;

    // Do we mask-off ignored modifiers?  Set by parameter and turned off after
    // initial modifier section
    private boolean maskModifiers;

    // Does the string end with an unclosed '/*' style comment?
    private boolean openToken = false;

    MaskCommentsAndModifiers(String s, boolean maskModifiers) {
        this.str = s;
        this.length = s.length();
        this.maskModifiers = maskModifiers;
        read();
        while (c >= 0) {
            next();
            read();
        }
    }

    String cleared() {
        return sbCleared.toString();
    }

    String mask() {
        return sbMask.toString();
    }

    boolean endsWithOpenToken() {
        return openToken;
    }

    /****** private implementation methods ******/

    /**
     * Read the next character
     */
    private int read() {
        return c = (next >= length)
                ? -1
                : str.charAt(next++);
    }

    private void unread() {
        if (c >= 0) {
            --next;
        }
    }

    private void writeTo(StringBuilder sb, int ch) {
        sb.append((char)ch);
    }

    private void write(int ch) {
        if (ch != -1) {
            writeTo(sbCleared, ch);
            writeTo(sbMask, Character.isWhitespace(ch) ? ch : ' ');
        }
    }

    private void writeMask(int ch) {
        if (ch != -1) {
            writeTo(sbMask, ch);
            writeTo(sbCleared, Character.isWhitespace(ch) ? ch : ' ');
        }
    }

    private void write(CharSequence s) {
        for (int cp : s.chars().toArray()) {
            write(cp);
        }
    }

    private void writeMask(CharSequence s) {
        for (int cp : s.chars().toArray()) {
            writeMask(cp);
        }
    }

    private void next() {
        switch (c) {
            case '\'':
            case '"': {
                maskModifiers = false;
                write(c);
                int match = c;
                while (read() >= 0 && c != match && c != '\n' && c != '\r') {
                    write(c);
                    if (c == '\\') {
                        write(read());
                    }
                }
                write(c); // write match // line-end
                break;
            }
            case '`': { // RawString
                maskModifiers = false;
                int backtickCount = 0;
                do {
                    write(c);
                    ++backtickCount;
                    read();
                } while (c == '`');
                while (true) {
                    if (c == '`') {
                        int cnt = 0;
                        do {
                            write(c);
                            ++cnt;
                            read();
                        } while (c == '`');
                        if (cnt == backtickCount) {
                            unread();
                            break;
                        }
                    } else {
                        write(c);
                        if (c < 0) {
                            openToken = true;
                            break;
                        }
                        read();
                    }
                }
                break;
            }
            case '/':
                read();
                switch (c) {
                    case '*':
                        writeMask('/');
                        writeMask(c);
                        int prevc = 0;
                        while (read() >= 0 && (c != '/' || prevc != '*')) {
                            writeMask(c);
                            prevc = c;
                        }
                        writeMask(c);
                        openToken = c < 0;
                        break;
                    case '/':
                        writeMask('/');
                        writeMask(c);
                        while (read() >= 0 && c != '\n' && c != '\r') {
                            writeMask(c);
                        }
                        writeMask(c);
                        break;
                    default:
                        maskModifiers = false;
                        write('/');
                        unread();
                        break;
                }
                break;
            case '@':
                do {
                    write(c);
                    read();
                } while (Character.isJavaIdentifierPart(c));
                while (Character.isWhitespace(c)) {
                    write(c);
                    read();
                }
                // if this is an annotation with arguments, process those recursively
                if (c == '(') {
                    write(c);
                    boolean prevMaskModifiers = maskModifiers;
                    int parenCnt = 1;
                    while (read() >= 0) {
                        if (c == ')') {
                            if (--parenCnt == 0) {
                                break;
                            }
                        } else if (c == '(') {
                            ++parenCnt;
                        }
                        next(); // recurse to handle quotes and comments
                    }
                    write(c);
                    // stuff in annotation arguments doesn't effect inside determination
                    maskModifiers = prevMaskModifiers;
                } else {
                    unread();
                }
                break;
            default:
                if (Character.isJavaIdentifierStart(c)) {
                    StringBuilder sb = new StringBuilder();
                    do {
                        writeTo(sb, c);
                        read();
                    } while (Character.isJavaIdentifierPart(c));
                    unread();
                    String id = sb.toString();
                    if (maskModifiers && IGNORED_MODIFIERS.contains(id)) {
                        writeMask(sb);
                    } else {
                        write(sb);
                        if (maskModifiers && !OTHER_MODIFIERS.contains(id)) {
                            maskModifiers = false;
                        }
                    }
                } else {
                    if (!Character.isWhitespace(c)) {
                        maskModifiers = false;
                    }
                    write(c);
                }
                break;
        }
    }
}
