/*
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

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, a notice that is now available elsewhere in this distribution
 * accompanied the original version of this file, and, per its terms,
 * should not be removed.
 */

package jdk.internal.org.commonmark.internal.util;

import jdk.internal.org.commonmark.parser.beta.Scanner;

public class LinkScanner {

    /**
     * Attempt to scan the contents of a link label (inside the brackets), stopping after the content or returning false.
     * The stopped position can bei either the closing {@code ]}, or the end of the line if the label continues on
     * the next line.
     */
    public static boolean scanLinkLabelContent(Scanner scanner) {
        while (scanner.hasNext()) {
            switch (scanner.peek()) {
                case '\\':
                    scanner.next();
                    if (isEscapable(scanner.peek())) {
                        scanner.next();
                    }
                    break;
                case ']':
                    return true;
                case '[':
                    // spec: Unescaped square bracket characters are not allowed inside the opening and closing
                    // square brackets of link labels.
                    return false;
                default:
                    scanner.next();
            }
        }
        return true;
    }

    /**
     * Attempt to scan a link destination, stopping after the destination or returning false.
     */
    public static boolean scanLinkDestination(Scanner scanner) {
        if (!scanner.hasNext()) {
            return false;
        }

        if (scanner.next('<')) {
            while (scanner.hasNext()) {
                switch (scanner.peek()) {
                    case '\\':
                        scanner.next();
                        if (isEscapable(scanner.peek())) {
                            scanner.next();
                        }
                        break;
                    case '\n':
                    case '<':
                        return false;
                    case '>':
                        scanner.next();
                        return true;
                    default:
                        scanner.next();
                }
            }
            return false;
        } else {
            return scanLinkDestinationWithBalancedParens(scanner);
        }
    }

    public static boolean scanLinkTitle(Scanner scanner) {
        if (!scanner.hasNext()) {
            return false;
        }

        char endDelimiter;
        switch (scanner.peek()) {
            case '"':
                endDelimiter = '"';
                break;
            case '\'':
                endDelimiter = '\'';
                break;
            case '(':
                endDelimiter = ')';
                break;
            default:
                return false;
        }
        scanner.next();

        if (!scanLinkTitleContent(scanner, endDelimiter)) {
            return false;
        }
        if (!scanner.hasNext()) {
            return false;
        }
        scanner.next();
        return true;
    }

    public static boolean scanLinkTitleContent(Scanner scanner, char endDelimiter) {
        while (scanner.hasNext()) {
            char c = scanner.peek();
            if (c == '\\') {
                scanner.next();
                if (isEscapable(scanner.peek())) {
                    scanner.next();
                }
            } else if (c == endDelimiter) {
                return true;
            } else if (endDelimiter == ')' && c == '(') {
                // unescaped '(' in title within parens is invalid
                return false;
            } else {
                scanner.next();
            }
        }
        return true;
    }

    // spec: a nonempty sequence of characters that does not start with <, does not include ASCII space or control
    // characters, and includes parentheses only if (a) they are backslash-escaped or (b) they are part of a balanced
    // pair of unescaped parentheses
    private static boolean scanLinkDestinationWithBalancedParens(Scanner scanner) {
        int parens = 0;
        boolean empty = true;
        while (scanner.hasNext()) {
            char c = scanner.peek();
            switch (c) {
                case ' ':
                    return !empty;
                case '\\':
                    scanner.next();
                    if (isEscapable(scanner.peek())) {
                        scanner.next();
                    }
                    break;
                case '(':
                    parens++;
                    // Limit to 32 nested parens for pathological cases
                    if (parens > 32) {
                        return false;
                    }
                    scanner.next();
                    break;
                case ')':
                    if (parens == 0) {
                        return true;
                    } else {
                        parens--;
                    }
                    scanner.next();
                    break;
                default:
                    // or control character
                    if (Character.isISOControl(c)) {
                        return !empty;
                    }
                    scanner.next();
                    break;
            }
            empty = false;
        }
        return true;
    }

    private static boolean isEscapable(char c) {
        switch (c) {
            case '!':
            case '"':
            case '#':
            case '$':
            case '%':
            case '&':
            case '\'':
            case '(':
            case ')':
            case '*':
            case '+':
            case ',':
            case '-':
            case '.':
            case '/':
            case ':':
            case ';':
            case '<':
            case '=':
            case '>':
            case '?':
            case '@':
            case '[':
            case '\\':
            case ']':
            case '^':
            case '_':
            case '`':
            case '{':
            case '|':
            case '}':
            case '~':
                return true;
        }
        return false;
    }
}
