/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package org.jline.terminal;

import java.util.EnumSet;

/**
 * Utility class for parsing raw terminal input sequences into KeyEvent objects.
 */
public class KeyParser {

    private KeyParser() {}

    /**
     * Parses a raw input sequence into a KeyEvent.
     *
     * @param rawSequence the raw input sequence from the terminal
     * @return a KeyEvent representing the parsed input
     */
    public static KeyEvent parse(String rawSequence) {
        if (rawSequence == null || rawSequence.isEmpty()) {
            return new KeyEvent(rawSequence);
        }

        // Handle escape sequences
        if (rawSequence.startsWith("\u001b")) {
            return parseEscapeSequence(rawSequence);
        }

        // Handle control characters
        if (rawSequence.length() == 1) {
            char ch = rawSequence.charAt(0);

            // Control characters (0x00-0x1F)
            if (ch >= 0 && ch <= 31) {
                return parseControlCharacter(ch, rawSequence);
            }

            // Regular printable character
            if (ch >= 32 && ch <= 126) {
                return new KeyEvent(ch, EnumSet.noneOf(KeyEvent.Modifier.class), rawSequence);
            }

            // Extended ASCII or Unicode
            if (ch > 126) {
                return new KeyEvent(ch, EnumSet.noneOf(KeyEvent.Modifier.class), rawSequence);
            }
        }

        // Multi-character sequence that's not an escape sequence
        return new KeyEvent(rawSequence);
    }

    private static KeyEvent parseEscapeSequence(String sequence) {
        if (sequence.length() < 2) {
            return new KeyEvent(KeyEvent.Special.Escape, EnumSet.noneOf(KeyEvent.Modifier.class), sequence);
        }

        // Alt+character sequences (ESC followed by a character)
        if (sequence.length() == 2) {
            char ch = sequence.charAt(1);
            EnumSet<KeyEvent.Modifier> modifiers = EnumSet.of(KeyEvent.Modifier.Alt);

            if (ch >= 32 && ch <= 126) {
                return new KeyEvent(ch, modifiers, sequence);
            }
        }

        // ANSI escape sequences
        if (sequence.startsWith("\u001b[")) {
            return parseAnsiSequence(sequence);
        }

        // SS3 escape sequences (ESC O)
        if (sequence.startsWith("\u001bO")) {
            return parseSS3Sequence(sequence);
        }

        // Other escape sequences
        return new KeyEvent(sequence);
    }

    private static KeyEvent parseAnsiSequence(String sequence) {
        // Common ANSI sequences
        switch (sequence) {
            // Arrow keys
            case "\u001b[A":
                return new KeyEvent(KeyEvent.Arrow.Up, EnumSet.noneOf(KeyEvent.Modifier.class), sequence);
            case "\u001b[B":
                return new KeyEvent(KeyEvent.Arrow.Down, EnumSet.noneOf(KeyEvent.Modifier.class), sequence);
            case "\u001b[C":
                return new KeyEvent(KeyEvent.Arrow.Right, EnumSet.noneOf(KeyEvent.Modifier.class), sequence);
            case "\u001b[D":
                return new KeyEvent(KeyEvent.Arrow.Left, EnumSet.noneOf(KeyEvent.Modifier.class), sequence);

            // Function keys
            case "\u001b[11~":
            case "\u001bOP":
                return new KeyEvent(1, EnumSet.noneOf(KeyEvent.Modifier.class), sequence);
            case "\u001b[12~":
            case "\u001bOQ":
                return new KeyEvent(2, EnumSet.noneOf(KeyEvent.Modifier.class), sequence);
            case "\u001b[13~":
            case "\u001bOR":
                return new KeyEvent(3, EnumSet.noneOf(KeyEvent.Modifier.class), sequence);
            case "\u001b[14~":
            case "\u001bOS":
                return new KeyEvent(4, EnumSet.noneOf(KeyEvent.Modifier.class), sequence);
            case "\u001b[15~":
                return new KeyEvent(5, EnumSet.noneOf(KeyEvent.Modifier.class), sequence);
            case "\u001b[17~":
                return new KeyEvent(6, EnumSet.noneOf(KeyEvent.Modifier.class), sequence);
            case "\u001b[18~":
                return new KeyEvent(7, EnumSet.noneOf(KeyEvent.Modifier.class), sequence);
            case "\u001b[19~":
                return new KeyEvent(8, EnumSet.noneOf(KeyEvent.Modifier.class), sequence);
            case "\u001b[20~":
                return new KeyEvent(9, EnumSet.noneOf(KeyEvent.Modifier.class), sequence);
            case "\u001b[21~":
                return new KeyEvent(10, EnumSet.noneOf(KeyEvent.Modifier.class), sequence);
            case "\u001b[23~":
                return new KeyEvent(11, EnumSet.noneOf(KeyEvent.Modifier.class), sequence);
            case "\u001b[24~":
                return new KeyEvent(12, EnumSet.noneOf(KeyEvent.Modifier.class), sequence);

            // Special keys
            case "\u001b[H":
                return new KeyEvent(KeyEvent.Special.Home, EnumSet.noneOf(KeyEvent.Modifier.class), sequence);
            case "\u001b[F":
                return new KeyEvent(KeyEvent.Special.End, EnumSet.noneOf(KeyEvent.Modifier.class), sequence);
            case "\u001b[2~":
                return new KeyEvent(KeyEvent.Special.Insert, EnumSet.noneOf(KeyEvent.Modifier.class), sequence);
            case "\u001b[3~":
                return new KeyEvent(KeyEvent.Special.Delete, EnumSet.noneOf(KeyEvent.Modifier.class), sequence);
            case "\u001b[5~":
                return new KeyEvent(KeyEvent.Special.PageUp, EnumSet.noneOf(KeyEvent.Modifier.class), sequence);
            case "\u001b[6~":
                return new KeyEvent(KeyEvent.Special.PageDown, EnumSet.noneOf(KeyEvent.Modifier.class), sequence);

            // Backtab (Shift+Tab)
            case "\u001b[Z":
                return new KeyEvent(KeyEvent.Special.Tab, EnumSet.of(KeyEvent.Modifier.Shift), sequence);

            default:
                // Try to parse modified keys (with Shift, Alt, Ctrl)
                return parseModifiedAnsiSequence(sequence);
        }
    }

    private static KeyEvent parseSS3Sequence(String sequence) {
        // SS3 sequences (ESC O)
        switch (sequence) {
            // Function keys
            case "\u001bOP":
                return new KeyEvent(1, EnumSet.noneOf(KeyEvent.Modifier.class), sequence);
            case "\u001bOQ":
                return new KeyEvent(2, EnumSet.noneOf(KeyEvent.Modifier.class), sequence);
            case "\u001bOR":
                return new KeyEvent(3, EnumSet.noneOf(KeyEvent.Modifier.class), sequence);
            case "\u001bOS":
                return new KeyEvent(4, EnumSet.noneOf(KeyEvent.Modifier.class), sequence);
            default:
                return new KeyEvent(sequence);
        }
    }

    private static KeyEvent parseModifiedAnsiSequence(String sequence) {
        // Pattern: \E[1;modifiers{A,B,C,D} for modified arrow keys
        // Pattern: \E[{number};modifiers~ for modified special keys

        // Modified arrow keys: \E[1;{mod}{A,B,C,D}
        if (sequence.matches("\\u001b\\[1;[2-8][ABCD]")) {
            int modCode = Character.getNumericValue(sequence.charAt(4));
            char arrowChar = sequence.charAt(5);

            EnumSet<KeyEvent.Modifier> modifiers = parseModifierCode(modCode);
            KeyEvent.Arrow arrow = parseArrowChar(arrowChar);

            if (arrow != null) {
                return new KeyEvent(arrow, modifiers, sequence);
            }
        }

        // Modified function keys: \E[{fn};{mod}~
        if (sequence.matches("\\u001b\\[[0-9]+;[2-8]~")) {
            String[] parts = sequence.substring(2, sequence.length() - 1).split(";");
            if (parts.length == 2) {
                try {
                    int fnNum = Integer.parseInt(parts[0]);
                    int modCode = Integer.parseInt(parts[1]);

                    EnumSet<KeyEvent.Modifier> modifiers = parseModifierCode(modCode);
                    int functionKey = mapFunctionKeyNumber(fnNum);

                    if (functionKey > 0) {
                        return new KeyEvent(functionKey, modifiers, sequence);
                    }
                } catch (NumberFormatException e) {
                    // Fall through to unknown
                }
            }
        }

        // Modified special keys: \E[{special};{mod}~
        if (sequence.matches("\\u001b\\[[2-6];[2-8]~")) {
            String[] parts = sequence.substring(2, sequence.length() - 1).split(";");
            if (parts.length == 2) {
                try {
                    int specialCode = Integer.parseInt(parts[0]);
                    int modCode = Integer.parseInt(parts[1]);

                    EnumSet<KeyEvent.Modifier> modifiers = parseModifierCode(modCode);
                    KeyEvent.Special special = mapSpecialKeyCode(specialCode);

                    if (special != null) {
                        return new KeyEvent(special, modifiers, sequence);
                    }
                } catch (NumberFormatException e) {
                    // Fall through to unknown
                }
            }
        }

        return new KeyEvent(sequence);
    }

    private static EnumSet<KeyEvent.Modifier> parseModifierCode(int modCode) {
        EnumSet<KeyEvent.Modifier> modifiers = EnumSet.noneOf(KeyEvent.Modifier.class);

        // Modifier codes: 2=Shift, 3=Alt, 4=Shift+Alt, 5=Ctrl, 6=Shift+Ctrl, 7=Alt+Ctrl, 8=Shift+Alt+Ctrl
        // The encoding is: 1 + (shift ? 1 : 0) + (alt ? 2 : 0) + (ctrl ? 4 : 0)
        int mod = modCode - 1; // Remove base offset

        if ((mod & 1) != 0) { // Shift bit
            modifiers.add(KeyEvent.Modifier.Shift);
        }
        if ((mod & 2) != 0) { // Alt bit
            modifiers.add(KeyEvent.Modifier.Alt);
        }
        if ((mod & 4) != 0) { // Ctrl bit
            modifiers.add(KeyEvent.Modifier.Control);
        }

        return modifiers;
    }

    private static KeyEvent.Arrow parseArrowChar(char arrowChar) {
        switch (arrowChar) {
            case 'A':
                return KeyEvent.Arrow.Up;
            case 'B':
                return KeyEvent.Arrow.Down;
            case 'C':
                return KeyEvent.Arrow.Right;
            case 'D':
                return KeyEvent.Arrow.Left;
            default:
                return null;
        }
    }

    private static int mapFunctionKeyNumber(int fnNum) {
        // Map ANSI function key numbers to F1-F12
        switch (fnNum) {
            case 11:
                return 1; // F1
            case 12:
                return 2; // F2
            case 13:
                return 3; // F3
            case 14:
                return 4; // F4
            case 15:
                return 5; // F5
            case 17:
                return 6; // F6
            case 18:
                return 7; // F7
            case 19:
                return 8; // F8
            case 20:
                return 9; // F9
            case 21:
                return 10; // F10
            case 23:
                return 11; // F11
            case 24:
                return 12; // F12
            default:
                return 0;
        }
    }

    private static KeyEvent.Special mapSpecialKeyCode(int specialCode) {
        switch (specialCode) {
            case 2:
                return KeyEvent.Special.Insert;
            case 3:
                return KeyEvent.Special.Delete;
            case 5:
                return KeyEvent.Special.PageUp;
            case 6:
                return KeyEvent.Special.PageDown;
            default:
                return null;
        }
    }

    private static KeyEvent parseControlCharacter(char ch, String sequence) {
        switch (ch) {
            case '\t':
                return new KeyEvent(KeyEvent.Special.Tab, EnumSet.noneOf(KeyEvent.Modifier.class), sequence);
            case '\r':
            case '\n':
                return new KeyEvent(KeyEvent.Special.Enter, EnumSet.noneOf(KeyEvent.Modifier.class), sequence);
            case '\u001b':
                return new KeyEvent(KeyEvent.Special.Escape, EnumSet.noneOf(KeyEvent.Modifier.class), sequence);
            case '\b':
            case '\u007f':
                return new KeyEvent(KeyEvent.Special.Backspace, EnumSet.noneOf(KeyEvent.Modifier.class), sequence);
            default:
                // Other control characters - could be Ctrl+letter combinations
                if (ch >= 1 && ch <= 26) {
                    // Ctrl+A through Ctrl+Z
                    char letter = (char) ('a' + ch - 1);
                    return new KeyEvent(letter, EnumSet.of(KeyEvent.Modifier.Control), sequence);
                }
                return new KeyEvent(sequence);
        }
    }
}
