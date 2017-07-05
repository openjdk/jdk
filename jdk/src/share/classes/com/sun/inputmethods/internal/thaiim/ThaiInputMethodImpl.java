/*
 * Copyright (c) 2002, Oracle and/or its affiliates. All rights reserved.
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
 *
 * (C) Copyright IBM Corp. 2000 - All Rights Reserved
 *
 * The original version of this source code and documentation is
 * copyrighted and owned by IBM. These materials are provided
 * under terms of a License Agreement between IBM and Sun.
 * This technology is protected by multiple US and International
 * patents. This notice and attribution to IBM may not be removed.
 *
 */

package com.sun.inputmethods.internal.thaiim;

import java.awt.im.InputMethodRequests;
import java.awt.im.spi.InputMethodContext;

import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.InputMethodEvent;
import java.awt.font.TextAttribute;
import java.awt.font.TextHitInfo;

import java.text.AttributedCharacterIterator;
import java.text.AttributedString;

class ThaiInputMethodImpl {

    private static final char[] keyboardMap = {
        /* 00 */ '\u0000',
        /* 01 */ '\u0001',
        /* 02 */ '\u0002',
        /* 03 */ '\u0003',
        /* 04 */ '\u0004',
        /* 05 */ '\u0005',
        /* 06 */ '\u0006',
        /* 07 */ '\u0007',
        /* 08 */ '\u0008',
        /* 09 */ '\u0009',
        /* 0A */ '\012',
        /* 0B */ '\u000B',
        /* 0C */ '\u000C',
        /* 0D */ '\015',
        /* 0E */ '\u000E',
        /* 0F */ '\u000F',
        /* 10 */ '\u0010',
        /* 11 */ '\u0011',
        /* 12 */ '\u0012',
        /* 13 */ '\u0013',
        /* 14 */ '\u0014',
        /* 15 */ '\u0015',
        /* 16 */ '\u0016',
        /* 17 */ '\u0017',
        /* 18 */ '\u0018',
        /* 19 */ '\u0019',
        /* 1A */ '\u001A',
        /* 1B */ '\u001B',
        /* 1C */ '\u001C',
        /* 1D */ '\u001D',
        /* 1E */ '\u001E',
        /* 1F */ '\u001F',
        /* 20 */ '\u0020',
        /* 21 */ '\u0e45',   // '!'
        /* 22 */ '\u002e',   // '"'
        /* 23 */ '\u0e52',   // '#'
        /* 24 */ '\u0e53',   // '$'
        /* 25 */ '\u0e54',   // '%'
        /* 26 */ '\u0e4e',   // '&'
        /* 27 */ '\u0e07',   // '''
        /* 28 */ '\u0e56',   // '('
        /* 29 */ '\u0e57',   // ')'
        /* 2A */ '\u0e55',   // '*'
        /* 2B */ '\u0e59',   // '+'
        /* 2C */ '\u0e21',   // ','
        /* 2D */ '\u0e02',   // '-'
        /* 2E */ '\u0e43',   // '.'
        /* 2F */ '\u0e1d',   // '/'
        /* 30 */ '\u0e08',   // '0'
        /* 31 */ '\u0e3f',   // '1'
        /* 32 */ '\u002f',   // '2'
        /* 33 */ '\u002d',   // '3'
        /* 34 */ '\u0e20',   // '4'
        /* 35 */ '\u0e16',   // '5'
        /* 36 */ '\u0e38',   // '6'
        /* 37 */ '\u0e36',   // '7'
        /* 38 */ '\u0e04',   // '8'
        /* 39 */ '\u0e15',   // '9'
        /* 3A */ '\u0e0b',   // ':'
        /* 3B */ '\u0e27',   // ';'
        /* 3C */ '\u0e12',   // '<'
        /* 3D */ '\u0e0a',   // '='
        /* 3E */ '\u0e2c',   // '>'
        /* 3F */ '\u0e26',   // '?'
        /* 40 */ '\u0e51',   // '@'
        /* 41 */ '\u0e24',   // 'A'
        /* 42 */ '\u0e3a',   // 'B'
        /* 43 */ '\u0e09',   // 'C'
        /* 44 */ '\u0e0f',   // 'D'
        /* 45 */ '\u0e0e',   // 'E'
        /* 46 */ '\u0e42',   // 'F'
        /* 47 */ '\u0e0c',   // 'G'
        /* 48 */ '\u0e47',   // 'H'
        /* 49 */ '\u0e13',   // 'I'
        /* 4A */ '\u0e4b',   // 'J'
        /* 4B */ '\u0e29',   // 'K'
        /* 4C */ '\u0e28',   // 'L'
        /* 4D */ '\u003f',   // 'M'
        /* 4E */ '\u0e4c',   // 'N'
        /* 4F */ '\u0e2f',   // 'O'
        /* 50 */ '\u0e0d',   // 'P'
        /* 51 */ '\u0e50',   // 'Q'
        /* 52 */ '\u0e11',   // 'R'
        /* 53 */ '\u0e06',   // 'S'
        /* 54 */ '\u0e18',   // 'T'
        /* 55 */ '\u0e4a',   // 'U'
        /* 56 */ '\u0e2e',   // 'V'
        /* 57 */ '\u0022',   // 'W'
        /* 58 */ '\u0029',   // 'X'
        /* 59 */ '\u0e4d',   // 'Y'
        /* 5A */ '\u0028',   // 'Z'
        /* 5B */ '\u0e1a',   // '['
        /* 5C */ '\u0e05',   // '\'
        /* 5D */ '\u0e25',   // ']'
        /* 5E */ '\u0e39',   // '^'
        /* 5F */ '\u0e58',   // '_'
        /* 60 */ '\u0e4f',   // '`'
        /* 61 */ '\u0e1f',   // 'a'
        /* 62 */ '\u0e34',   // 'b'
        /* 63 */ '\u0e41',   // 'c'
        /* 64 */ '\u0e01',   // 'd'
        /* 65 */ '\u0e33',   // 'e'
        /* 66 */ '\u0e14',   // 'f'
        /* 67 */ '\u0e40',   // 'g'
        /* 68 */ '\u0e49',   // 'h'
        /* 69 */ '\u0e23',   // 'i'
        /* 6A */ '\u0e48',   // 'j'
        /* 6B */ '\u0e32',   // 'k'
        /* 6C */ '\u0e2a',   // 'l'
        /* 6D */ '\u0e17',   // 'm'
        /* 6E */ '\u0e37',   // 'n'
        /* 6F */ '\u0e19',   // 'o'
        /* 70 */ '\u0e22',   // 'p'
        /* 71 */ '\u0e46',   // 'q'
        /* 72 */ '\u0e1e',   // 'r'
        /* 73 */ '\u0e2b',   // 's'
        /* 74 */ '\u0e30',   // 't'
        /* 75 */ '\u0e35',   // 'u'
        /* 76 */ '\u0e2d',   // 'v'
        /* 77 */ '\u0e44',   // 'w'
        /* 78 */ '\u0e1b',   // 'x'
        /* 79 */ '\u0e31',   // 'y'
        /* 7A */ '\u0e1c',   // 'z'
        /* 7B */ '\u0e10',   // '{'
        /* 7C */ '\u0e03',   // '|'
        /* 7D */ '\u002c',   // '}'
        /* 7E */ '\u0e5b',   // '~'
        /* 7F */ '\u007F'    //
    };

    // cached TextHitInfo. Only one type of TextHitInfo is required.
    private static final TextHitInfo ZERO_TRAILING_HIT_INFO = TextHitInfo.trailing(0);

    private ThaiRules rules;

    /**
     * Returns the equivalent character for thai locale.
     * @param originalChar The original character.
     */
    private char getMappedChar( char originalChar )
    {
        if (originalChar <= keyboardMap.length) {
            return keyboardMap[originalChar];
        }

        return originalChar;
    }//getMappedChar()

    private InputMethodContext context;

    void setInputMethodContext(InputMethodContext context) {
        this.context = context;
        rules = new ThaiRules((InputMethodRequests)context);
    }

    void handleKeyTyped(KeyEvent kevent) {
        char keyChar = kevent.getKeyChar();
        char currentChar = getMappedChar(keyChar);
        if (!Character.UnicodeBlock.THAI.equals(Character.UnicodeBlock.of(currentChar))) {
            // don't care
            return;
        } else if (rules.isInputValid(currentChar)) {
            Character tmp = new Character(currentChar);
            String tmp2 = tmp.toString();
            context.dispatchInputMethodEvent(InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                                             (new AttributedString(tmp2)).getIterator(),
                                             1,
                                             ZERO_TRAILING_HIT_INFO,
                                             ZERO_TRAILING_HIT_INFO);
        } else {
            // input sequence is not allowed
            Toolkit.getDefaultToolkit().beep();
        }

        kevent.consume();// prevent client from getting this event.
        return;
    }//dispatchEvent()

    void endComposition() {
    }//endComposition()
}
