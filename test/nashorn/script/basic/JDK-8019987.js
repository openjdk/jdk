/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

/**
 * JDK-8019987: String trimRight and trimLeft could be defined.
 *
 * @test
 * @run
 */

var TESTSTRING = "abcde";

var SPACES                       = "     ";
var TESTSTRING_LEFT_SPACES       = SPACES + TESTSTRING;
var TESTSTRING_RIGHT_SPACES      = TESTSTRING + SPACES;
var TESTSTRING_BOTH_SPACES       = SPACES + TESTSTRING + SPACES;
var TESTSTRING_MIDDLE_SPACES     = TESTSTRING + SPACES + TESTSTRING;

var WHITESPACE =
        " \t"    + // space and tab
        "\n\r"   + // newline and return
        "\u2028" + // line separator
        "\u2029" + // paragraph separator
        "\u000b" + // tabulation line
        "\u000c" + // ff (ctrl-l)
        "\u00a0" + // Latin-1 space
        "\u1680" + // Ogham space mark
        "\u180e" + // separator, Mongolian vowel
        "\u2000" + // en quad
        "\u2001" + // em quad
        "\u2002" + // en space
        "\u2003" + // em space
        "\u2004" + // three-per-em space
        "\u2005" + // four-per-em space
        "\u2006" + // six-per-em space
        "\u2007" + // figure space
        "\u2008" + // punctuation space
        "\u2009" + // thin space
        "\u200a" + // hair space
        "\u202f" + // narrow no-break space
        "\u205f" + // medium mathematical space
        "\u3000" + // ideographic space
        "\ufeff";  // byte order mark
var TESTSTRING_LEFT_WHITESPACE   = WHITESPACE + TESTSTRING;
var TESTSTRING_RIGHT_WHITESPACE  = TESTSTRING + WHITESPACE;
var TESTSTRING_BOTH_WHITESPACE   = WHITESPACE + TESTSTRING + WHITESPACE;
var TESTSTRING_MIDDLE_WHITESPACE = TESTSTRING + WHITESPACE + TESTSTRING;

function escape(string) {
    var sb = new java.lang.StringBuilder();
    sb.append("\"");

    for (var i = 0; i < string.length; i++) {
        var ch = string.charAt(i);

        switch (ch) {
        case '\\':
            sb.append("\\\\");
            break;
        case '"':
            sb.append("\\\"");
            break;
        case '\'':
            sb.append("\\\'");
            break;
        case '\b':
            sb.append("\\b");
            break;
        case '\f':
            sb.append("\\f");
            break;
        case '\n':
            sb.append("\\n");
            break;
        case '\r':
            sb.append("\\r");
            break;
        case '\t':
            sb.append("\\t");
            break;
        default:
            var code = string.charCodeAt(i);

            if (code < 0x20 || code >= 0xFF) {
                sb.append("\\u");

                var hex = java.lang.Integer.toHexString(code);
                for (var i = hex.length; i < 4; i++) {
                    sb.append('0');
                }
                sb.append(hex);
            } else {
                sb.append(ch);
            }

            break;
        }
    }

    sb.append("\"");

    return sb.toString();
}

var count = 0;
function test(expected, trimmed) {
    count++;
    if (trimmed != expected) {
        print(count + ": Expected: " + escape(expected) + ", found: " + escape(trimmed));
    }
}

test("",                           SPACES.trim());
test("",                           SPACES.trimLeft());
test("",                           SPACES.trimRight());

test(TESTSTRING,                   TESTSTRING_LEFT_SPACES.trim());
test(TESTSTRING,                   TESTSTRING_LEFT_SPACES.trimLeft());
test(TESTSTRING_LEFT_SPACES,       TESTSTRING_LEFT_SPACES.trimRight());

test(TESTSTRING,                   TESTSTRING_RIGHT_SPACES.trim());
test(TESTSTRING_RIGHT_SPACES,      TESTSTRING_RIGHT_SPACES.trimLeft());
test(TESTSTRING,                   TESTSTRING_RIGHT_SPACES.trimRight());

test(TESTSTRING,                   TESTSTRING_BOTH_SPACES.trim());
test(TESTSTRING_RIGHT_SPACES,      TESTSTRING_BOTH_SPACES.trimLeft());
test(TESTSTRING_LEFT_SPACES,       TESTSTRING_BOTH_SPACES.trimRight());

test(TESTSTRING_MIDDLE_SPACES,     TESTSTRING_MIDDLE_SPACES.trim());
test(TESTSTRING_MIDDLE_SPACES,     TESTSTRING_MIDDLE_SPACES.trimLeft());
test(TESTSTRING_MIDDLE_SPACES,     TESTSTRING_MIDDLE_SPACES.trimRight());

test("",                           WHITESPACE.trim());
test("",                           WHITESPACE.trimLeft());
test("",                           WHITESPACE.trimRight());

test(TESTSTRING,                   TESTSTRING_LEFT_WHITESPACE.trim());
test(TESTSTRING,                   TESTSTRING_LEFT_WHITESPACE.trimLeft());
test(TESTSTRING_LEFT_WHITESPACE,   TESTSTRING_LEFT_WHITESPACE.trimRight());

test(TESTSTRING,                   TESTSTRING_RIGHT_WHITESPACE.trim());
test(TESTSTRING_RIGHT_WHITESPACE,  TESTSTRING_RIGHT_WHITESPACE.trimLeft());
test(TESTSTRING,                   TESTSTRING_RIGHT_WHITESPACE.trimRight());

test(TESTSTRING,                   TESTSTRING_BOTH_WHITESPACE.trim());
test(TESTSTRING_RIGHT_WHITESPACE,  TESTSTRING_BOTH_WHITESPACE.trimLeft());
test(TESTSTRING_LEFT_WHITESPACE,   TESTSTRING_BOTH_WHITESPACE.trimRight());

test(TESTSTRING_MIDDLE_WHITESPACE, TESTSTRING_MIDDLE_WHITESPACE.trim());
test(TESTSTRING_MIDDLE_WHITESPACE, TESTSTRING_MIDDLE_WHITESPACE.trimLeft());
test(TESTSTRING_MIDDLE_WHITESPACE, TESTSTRING_MIDDLE_WHITESPACE.trimRight());
