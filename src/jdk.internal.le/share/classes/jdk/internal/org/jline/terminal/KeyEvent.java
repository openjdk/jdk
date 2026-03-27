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
 * Represents a keyboard event in a terminal.
 *
 * <p>
 * The KeyEvent class encapsulates information about keyboard actions in a terminal,
 * including the type of key pressed, any modifier keys that were held, and the
 * raw sequence that was received from the terminal.
 * </p>
 *
 * <p>
 * Key events include:
 * </p>
 * <ul>
 *   <li><b>Character</b> - A printable character was typed</li>
 *   <li><b>Arrow</b> - An arrow key was pressed (Up, Down, Left, Right)</li>
 *   <li><b>Function</b> - A function key was pressed (F1-F12)</li>
 *   <li><b>Special</b> - A special key was pressed (Enter, Tab, Escape, etc.)</li>
 *   <li><b>Unknown</b> - An unrecognized key sequence</li>
 * </ul>
 */
public class KeyEvent {

    /**
     * Defines the types of key events that can occur.
     */
    public enum Type {
        /**
         * A printable character was typed.
         */
        Character,

        /**
         * An arrow key was pressed.
         */
        Arrow,

        /**
         * A function key was pressed (F1-F12).
         */
        Function,

        /**
         * A special key was pressed (Enter, Tab, Escape, etc.).
         */
        Special,

        /**
         * An unrecognized key sequence.
         */
        Unknown
    }

    /**
     * Defines arrow key directions.
     */
    public enum Arrow {
        Up,
        Down,
        Left,
        Right
    }

    /**
     * Defines special keys.
     */
    public enum Special {
        Enter,
        Tab,
        Escape,
        Backspace,
        Delete,
        Home,
        End,
        PageUp,
        PageDown,
        Insert
    }

    /**
     * Defines modifier keys that can be held during a key event.
     */
    public enum Modifier {
        /**
         * The Shift key was held.
         */
        Shift,

        /**
         * The Alt key was held.
         */
        Alt,

        /**
         * The Control key was held.
         */
        Control
    }

    private final Type type;
    private final char character;
    private final Arrow arrow;
    private final Special special;
    private final int functionKey;
    private final EnumSet<Modifier> modifiers;
    private final String rawSequence;

    /**
     * Creates a character key event.
     */
    public KeyEvent(char character, EnumSet<Modifier> modifiers, String rawSequence) {
        this.type = Type.Character;
        this.character = character;
        this.arrow = null;
        this.special = null;
        this.functionKey = 0;
        this.modifiers = modifiers;
        this.rawSequence = rawSequence;
    }

    /**
     * Creates an arrow key event.
     */
    public KeyEvent(Arrow arrow, EnumSet<Modifier> modifiers, String rawSequence) {
        this.type = Type.Arrow;
        this.character = '\0';
        this.arrow = arrow;
        this.special = null;
        this.functionKey = 0;
        this.modifiers = modifiers;
        this.rawSequence = rawSequence;
    }

    /**
     * Creates a special key event.
     */
    public KeyEvent(Special special, EnumSet<Modifier> modifiers, String rawSequence) {
        this.type = Type.Special;
        this.character = '\0';
        this.arrow = null;
        this.special = special;
        this.functionKey = 0;
        this.modifiers = modifiers;
        this.rawSequence = rawSequence;
    }

    /**
     * Creates a function key event.
     */
    public KeyEvent(int functionKey, EnumSet<Modifier> modifiers, String rawSequence) {
        this.type = Type.Function;
        this.character = '\0';
        this.arrow = null;
        this.special = null;
        this.functionKey = functionKey;
        this.modifiers = modifiers;
        this.rawSequence = rawSequence;
    }

    /**
     * Creates an unknown key event.
     */
    public KeyEvent(String rawSequence) {
        this.type = Type.Unknown;
        this.character = '\0';
        this.arrow = null;
        this.special = null;
        this.functionKey = 0;
        this.modifiers = EnumSet.noneOf(Modifier.class);
        this.rawSequence = rawSequence;
    }

    public Type getType() {
        return type;
    }

    public char getCharacter() {
        return character;
    }

    public Arrow getArrow() {
        return arrow;
    }

    public Special getSpecial() {
        return special;
    }

    public int getFunctionKey() {
        return functionKey;
    }

    public EnumSet<Modifier> getModifiers() {
        return modifiers;
    }

    public String getRawSequence() {
        return rawSequence;
    }

    public boolean hasModifier(Modifier modifier) {
        return modifiers.contains(modifier);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("KeyEvent[type=").append(type);
        switch (type) {
            case Character:
                sb.append(", character='").append(character).append("'");
                break;
            case Arrow:
                sb.append(", arrow=").append(arrow);
                break;
            case Special:
                sb.append(", special=").append(special);
                break;
            case Function:
                sb.append(", function=F").append(functionKey);
                break;
            case Unknown:
                sb.append(", unknown");
                break;
        }
        if (!modifiers.isEmpty()) {
            sb.append(", modifiers=").append(modifiers);
        }
        sb.append(", raw='").append(rawSequence).append("']");
        return sb.toString();
    }
}
