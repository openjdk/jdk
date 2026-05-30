/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.terminal;

import java.util.EnumSet;

/**
 * Represents a mouse event in a terminal that supports mouse tracking.
 *
 * <p>
 * The MouseEvent class encapsulates information about mouse actions in a terminal,
 * including the type of event (press, release, move, etc.), which button was involved,
 * any modifier keys that were pressed, and the coordinates where the event occurred.
 * </p>
 *
 * <p>
 * Mouse events are only available in terminals that support mouse tracking, which can be
 * enabled using {@link Terminal#trackMouse(Terminal.MouseTracking)}. Once mouse tracking
 * is enabled, mouse events can be read using {@link Terminal#readMouseEvent()}.
 * </p>
 *
 * <p>
 * Mouse events include:
 * </p>
 * <ul>
 *   <li><b>Pressed</b> - A mouse button was pressed</li>
 *   <li><b>Released</b> - A mouse button was released</li>
 *   <li><b>Moved</b> - The mouse was moved without any buttons pressed</li>
 *   <li><b>Dragged</b> - The mouse was moved with a button pressed</li>
 *   <li><b>Wheel</b> - The mouse wheel was scrolled</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>
 * Terminal terminal = TerminalBuilder.terminal();
 *
 * // Enable mouse tracking
 * if (terminal.hasMouseSupport()) {
 *     terminal.trackMouse(Terminal.MouseTracking.Normal);
 *
 *     // Read mouse events
 *     MouseEvent event = terminal.readMouseEvent();
 *     System.out.println("Mouse event: type=" + event.getType() +
 *                        ", button=" + event.getButton() +
 *                        ", position=" + event.getX() + "," + event.getY());
 * }
 * </pre>
 *
 * @see Terminal#hasMouseSupport()
 * @see Terminal#trackMouse(Terminal.MouseTracking)
 * @see Terminal#readMouseEvent()
 */
public class MouseEvent {

    /**
     * Defines the types of mouse events that can occur.
     */
    public enum Type {
        /**
         * A mouse button was released.
         */
        Released,

        /**
         * A mouse button was pressed.
         */
        Pressed,

        /**
         * The mouse wheel was scrolled.
         */
        Wheel,

        /**
         * The mouse was moved without any buttons pressed.
         */
        Moved,

        /**
         * The mouse was moved with a button pressed (drag operation).
         */
        Dragged
    }

    /**
     * Defines the mouse buttons that can be involved in a mouse event.
     */
    public enum Button {
        /**
         * No specific button is involved (used for move events).
         */
        NoButton,

        /**
         * The primary mouse button (usually the left button).
         */
        Button1,

        /**
         * The middle mouse button.
         */
        Button2,

        /**
         * The secondary mouse button (usually the right button).
         */
        Button3,

        /**
         * The mouse wheel was scrolled upward.
         */
        WheelUp,

        /**
         * The mouse wheel was scrolled downward.
         */
        WheelDown
    }

    /**
     * Defines the modifier keys that can be pressed during a mouse event.
     */
    public enum Modifier {
        /**
         * The Shift key was pressed during the mouse event.
         */
        Shift,

        /**
         * The Alt key was pressed during the mouse event.
         */
        Alt,

        /**
         * The Control key was pressed during the mouse event.
         */
        Control
    }

    private final Type type;
    private final Button button;
    private final EnumSet<Modifier> modifiers;
    private final int x;
    private final int y;

    /**
     * Creates a new MouseEvent with the specified parameters.
     *
     * @param type      the type of mouse event (press, release, etc.)
     * @param button    the button involved in the event
     * @param modifiers the modifier keys pressed during the event
     * @param x         the column (horizontal) position of the event
     * @param y         the row (vertical) position of the event
     */
    public MouseEvent(Type type, Button button, EnumSet<Modifier> modifiers, int x, int y) {
        this.type = type;
        this.button = button;
        this.modifiers = modifiers;
        this.x = x;
        this.y = y;
    }

    /**
     * Returns the type of this mouse event.
     *
     * @return the event type (press, release, move, etc.)
     */
    public Type getType() {
        return type;
    }

    /**
     * Returns the button involved in this mouse event.
     *
     * @return the mouse button
     */
    public Button getButton() {
        return button;
    }

    /**
     * Returns the set of modifier keys pressed during this mouse event.
     *
     * @return the set of modifier keys (Shift, Alt, Control)
     */
    public EnumSet<Modifier> getModifiers() {
        return modifiers;
    }

    /**
     * Returns the column (horizontal) position of this mouse event.
     *
     * @return the X coordinate (column)
     */
    public int getX() {
        return x;
    }

    /**
     * Returns the row (vertical) position of this mouse event.
     *
     * @return the Y coordinate (row)
     */
    public int getY() {
        return y;
    }

    /**
     * Returns a string representation of this MouseEvent object.
     *
     * <p>
     * The string representation includes all properties of the mouse event:
     * the event type, button, modifier keys, and coordinates.
     * </p>
     *
     * <p>Example output:</p>
     * <pre>
     * MouseEvent[type=Pressed, button=Button1, modifiers=[Shift], x=10, y=20]
     * </pre>
     *
     * @return a string representation of this object
     */
    @Override
    public String toString() {
        return "MouseEvent[" + "type="
                + type + ", button="
                + button + ", modifiers="
                + modifiers + ", x="
                + x + ", y="
                + y + ']';
    }
}
