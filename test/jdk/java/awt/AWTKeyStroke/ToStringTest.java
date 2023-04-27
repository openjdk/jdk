/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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

/*
  @test
  @bug 4370733
  @summary AWTKeyStroke's getAWTKeyStroke(String) and toString() method aren't symmetric
*/

import java.awt.AWTKeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.KeyStroke;

public class ToStringTest {

    /* Note this test is deliberately testing the deprecated constants
     * as well as their replacements.
     */
    @SuppressWarnings("deprecation")
    public static final int[] modifiers = {
        0,
        InputEvent.SHIFT_MASK,
        InputEvent.CTRL_MASK,
        InputEvent.META_MASK,
        InputEvent.ALT_MASK,
        InputEvent.ALT_GRAPH_MASK,
        InputEvent.BUTTON1_MASK,
        InputEvent.BUTTON2_MASK,
        InputEvent.BUTTON3_MASK,
        InputEvent.SHIFT_DOWN_MASK,
        InputEvent.CTRL_DOWN_MASK,
        InputEvent.META_DOWN_MASK,
        InputEvent.ALT_DOWN_MASK,
        InputEvent.BUTTON1_DOWN_MASK,
        InputEvent.BUTTON2_DOWN_MASK,
        InputEvent.BUTTON3_DOWN_MASK,
        InputEvent.ALT_GRAPH_DOWN_MASK
    };

    public static final int[] keys = {
        KeyEvent.VK_A,
        KeyEvent.VK_B,
        KeyEvent.VK_C,
        KeyEvent.VK_D,
        KeyEvent.VK_E,
        KeyEvent.VK_F,
        KeyEvent.VK_G,
        KeyEvent.VK_H,
        KeyEvent.VK_I,
        KeyEvent.VK_J,
        KeyEvent.VK_K,
        KeyEvent.VK_L,
        KeyEvent.VK_M,
        KeyEvent.VK_N,
        KeyEvent.VK_O,
        KeyEvent.VK_P,
        KeyEvent.VK_Q,
        KeyEvent.VK_R,
        KeyEvent.VK_S,
        KeyEvent.VK_T,
        KeyEvent.VK_U,
        KeyEvent.VK_V,
        KeyEvent.VK_W,
        KeyEvent.VK_X,
        KeyEvent.VK_Y,
        KeyEvent.VK_Z,
        KeyEvent.VK_0,
        KeyEvent.VK_1,
        KeyEvent.VK_2,
        KeyEvent.VK_3,
        KeyEvent.VK_4,
        KeyEvent.VK_5,
        KeyEvent.VK_6,
        KeyEvent.VK_7,
        KeyEvent.VK_8,
        KeyEvent.VK_9,

        KeyEvent.VK_COMMA,
        KeyEvent.VK_PERIOD,
        KeyEvent.VK_SLASH,
        KeyEvent.VK_SEMICOLON,
        KeyEvent.VK_EQUALS,
        KeyEvent.VK_OPEN_BRACKET,
        KeyEvent.VK_BACK_SLASH,
        KeyEvent.VK_CLOSE_BRACKET,

        KeyEvent.VK_ENTER,
        KeyEvent.VK_BACK_SPACE,
        KeyEvent.VK_TAB,
        KeyEvent.VK_CANCEL,
        KeyEvent.VK_CLEAR,
        KeyEvent.VK_SHIFT,
        KeyEvent.VK_CONTROL,
        KeyEvent.VK_ALT,
        KeyEvent.VK_PAUSE,
        KeyEvent.VK_CAPS_LOCK,
        KeyEvent.VK_ESCAPE,
        KeyEvent.VK_SPACE,
        KeyEvent.VK_PAGE_UP,
        KeyEvent.VK_PAGE_DOWN,
        KeyEvent.VK_END,
        KeyEvent.VK_HOME,
        KeyEvent.VK_LEFT,
        KeyEvent.VK_UP,
        KeyEvent.VK_RIGHT,
        KeyEvent.VK_DOWN,
        KeyEvent.VK_ADD,
        KeyEvent.VK_SEPARATOR,
        KeyEvent.VK_SUBTRACT,
        KeyEvent.VK_DECIMAL,
        KeyEvent.VK_DIVIDE,
        KeyEvent.VK_DELETE,
        KeyEvent.VK_NUM_LOCK,
        KeyEvent.VK_SCROLL_LOCK,

        KeyEvent.VK_WINDOWS,
        KeyEvent.VK_CONTEXT_MENU,

        KeyEvent.VK_F1,
        KeyEvent.VK_F2,
        KeyEvent.VK_F3,
        KeyEvent.VK_F4,
        KeyEvent.VK_F5,
        KeyEvent.VK_F6,
        KeyEvent.VK_F7,
        KeyEvent.VK_F8,
        KeyEvent.VK_F9,
        KeyEvent.VK_F10,
        KeyEvent.VK_F11,
        KeyEvent.VK_F12,
        KeyEvent.VK_F13,
        KeyEvent.VK_F14,
        KeyEvent.VK_F15,
        KeyEvent.VK_F16,
        KeyEvent.VK_F17,
        KeyEvent.VK_F18,
        KeyEvent.VK_F19,
        KeyEvent.VK_F20,
        KeyEvent.VK_F21,
        KeyEvent.VK_F22,
        KeyEvent.VK_F23,
        KeyEvent.VK_F24,

        KeyEvent.VK_PRINTSCREEN,
        KeyEvent.VK_INSERT,
        KeyEvent.VK_HELP,
        KeyEvent.VK_META,
        KeyEvent.VK_BACK_QUOTE,
        KeyEvent.VK_QUOTE,

        KeyEvent.VK_KP_UP,
        KeyEvent.VK_KP_DOWN,
        KeyEvent.VK_KP_LEFT,
        KeyEvent.VK_KP_RIGHT,

        KeyEvent.VK_DEAD_GRAVE,
        KeyEvent.VK_DEAD_ACUTE,
        KeyEvent.VK_DEAD_CIRCUMFLEX,
        KeyEvent.VK_DEAD_TILDE,
        KeyEvent.VK_DEAD_MACRON,
        KeyEvent.VK_DEAD_BREVE,
        KeyEvent.VK_DEAD_ABOVEDOT,
        KeyEvent.VK_DEAD_DIAERESIS,
        KeyEvent.VK_DEAD_ABOVERING,
        KeyEvent.VK_DEAD_DOUBLEACUTE,
        KeyEvent.VK_DEAD_CARON,
        KeyEvent.VK_DEAD_CEDILLA,
        KeyEvent.VK_DEAD_OGONEK,
        KeyEvent.VK_DEAD_IOTA,
        KeyEvent.VK_DEAD_VOICED_SOUND,
        KeyEvent.VK_DEAD_SEMIVOICED_SOUND,

        KeyEvent.VK_AMPERSAND,
        KeyEvent.VK_ASTERISK,
        KeyEvent.VK_QUOTEDBL,
        KeyEvent.VK_LESS,
        KeyEvent.VK_GREATER,
        KeyEvent.VK_BRACELEFT,
        KeyEvent.VK_BRACERIGHT,
        KeyEvent.VK_AT,
        KeyEvent.VK_COLON,
        KeyEvent.VK_CIRCUMFLEX,
        KeyEvent.VK_DOLLAR,
        KeyEvent.VK_EURO_SIGN,
        KeyEvent.VK_EXCLAMATION_MARK,
        KeyEvent.VK_INVERTED_EXCLAMATION_MARK,
        KeyEvent.VK_LEFT_PARENTHESIS,
        KeyEvent.VK_NUMBER_SIGN,
        KeyEvent.VK_MINUS,
        KeyEvent.VK_PLUS,
        KeyEvent.VK_RIGHT_PARENTHESIS,
        KeyEvent.VK_UNDERSCORE,

        KeyEvent.VK_FINAL,
        KeyEvent.VK_CONVERT,
        KeyEvent.VK_NONCONVERT,
        KeyEvent.VK_ACCEPT,
        KeyEvent.VK_MODECHANGE,
        KeyEvent.VK_KANA,
        KeyEvent.VK_KANJI,
        KeyEvent.VK_ALPHANUMERIC,
        KeyEvent.VK_KATAKANA,
        KeyEvent.VK_HIRAGANA,
        KeyEvent.VK_FULL_WIDTH,
        KeyEvent.VK_HALF_WIDTH,
        KeyEvent.VK_ROMAN_CHARACTERS,
        KeyEvent.VK_ALL_CANDIDATES,
        KeyEvent.VK_PREVIOUS_CANDIDATE,
        KeyEvent.VK_CODE_INPUT,
        KeyEvent.VK_JAPANESE_KATAKANA,
        KeyEvent.VK_JAPANESE_HIRAGANA,
        KeyEvent.VK_JAPANESE_ROMAN,
        KeyEvent.VK_KANA_LOCK,
        KeyEvent.VK_INPUT_METHOD_ON_OFF,

        KeyEvent.VK_AGAIN,
        KeyEvent.VK_UNDO,
        KeyEvent.VK_COPY,
        KeyEvent.VK_PASTE,
        KeyEvent.VK_CUT,
        KeyEvent.VK_FIND,
        KeyEvent.VK_PROPS,
        KeyEvent.VK_STOP,

        KeyEvent.VK_COMPOSE,
        KeyEvent.VK_ALT_GRAPH,
        KeyEvent.VK_BEGIN,

        KeyEvent.VK_NUMPAD0,
        KeyEvent.VK_NUMPAD1,
        KeyEvent.VK_NUMPAD2,
        KeyEvent.VK_NUMPAD3,
        KeyEvent.VK_NUMPAD4,
        KeyEvent.VK_NUMPAD5,
        KeyEvent.VK_NUMPAD6,
        KeyEvent.VK_NUMPAD7,
        KeyEvent.VK_NUMPAD8,
        KeyEvent.VK_NUMPAD9
    };

    public static void main(String[] args) throws Exception {

        System.err.println("**** Testing AWTKeyStrokes");
        for (int n_key=0; n_key < keys.length; n_key++) {
            for (int n_mod=0; n_mod < modifiers.length; n_mod++) {
                checkStroke(AWTKeyStroke.getAWTKeyStroke(keys[n_key],
                                                         modifiers[n_mod],
                                                         true));
                checkStroke(AWTKeyStroke.getAWTKeyStroke(keys[n_key],
                                                         modifiers[n_mod],
                                                         false));
            }
        }

        System.err.println("**** Testing Swing KeyStrokes");
        for (int n_key=0; n_key < keys.length; n_key++) {
            for (int n_mod=0; n_mod < modifiers.length; n_mod++) {
                checkStroke(KeyStroke.getKeyStroke(keys[n_key],
                                                         modifiers[n_mod],
                                                         true));
                checkStroke(KeyStroke.getKeyStroke(keys[n_key],
                                                         modifiers[n_mod],
                                                         false));
            }
        }

        Character a = Character.valueOf('a');
        System.err.println("**** Testing KEY_TYPED AWTKeyStrokes");
        for (int n_mod = 0; n_mod < modifiers.length; n_mod++) {
            checkStroke(AWTKeyStroke.getAWTKeyStroke(a, modifiers[n_mod]));
        }
        System.err.println("**** Testing KEY_TYPED Swing KeyStrokes");
        for (int n_mod = 0; n_mod < modifiers.length; n_mod++) {
            checkStroke(KeyStroke.getKeyStroke(a, modifiers[n_mod]));
        }

        System.out.println("Test passed.");
    }

    public static void checkStroke(AWTKeyStroke original) {
        System.err.println("AWT Original >> " + original);
        AWTKeyStroke copy = AWTKeyStroke.getAWTKeyStroke(original.toString());
        // System.err.println("AWT Copy >> " + copy);
        if (!original.equals(copy)) {
            System.out.println("AWT bad copy for VK= 0x" +
                           Integer.toString(original.getKeyCode(), 16));
            throw new RuntimeException("Test Failed: for " + original);
        }
    }

    public static void checkStroke(KeyStroke original) {
        System.err.println("Swing Original >> " + original);
        KeyStroke copy = KeyStroke.getKeyStroke(original.toString());
        // System.err.println("Swing Copy >> " + copy);
        if (!original.equals(copy)) {
            System.out.println("Swing bad copy for VK= 0x" +
                           Integer.toString(original.getKeyCode(), 16));
            throw new RuntimeException("Test Failed: for " + original);
        }
    }

}
