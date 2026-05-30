/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.reader;

/**
 * A Widget represents an action that can be bound to a key sequence in the LineReader.
 * <p>
 * Widgets are the fundamental building blocks of the line editor's functionality.
 * Each widget implements a specific editing action or command that can be invoked
 * by the user through key bindings. Examples include moving the cursor, deleting
 * characters, searching history, and completing words.
 * <p>
 * Widgets can be bound to key sequences using the LineReader's key maps. When the
 * user presses a key sequence that is bound to a widget, the widget's {@link #apply()}
 * method is called to perform the associated action.
 * <p>
 * JLine provides a set of built-in widgets that implement common editing functions,
 * and applications can define custom widgets to extend the editor's functionality.
 * <p>
 * This interface is designed as a functional interface, making it easy to implement
 * widgets using lambda expressions.
 *
 * @see LineReader#getWidgets()
 * @see LineReader#getBuiltinWidgets()
 * @see LineReader#callWidget(String)
 * @see Binding
 */
@FunctionalInterface
public interface Widget extends Binding {

    /**
     * Executes the action associated with this widget.
     * <p>
     * This method is called when the key sequence bound to this widget is pressed.
     * It should perform the widget's specific editing action or command.
     *
     * @return true if the widget was successfully applied, false otherwise
     */
    boolean apply();
}
