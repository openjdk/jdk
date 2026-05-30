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
 * Marker interface for objects that can be bound to key sequences in a KeyMap.
 * <p>
 * The Binding interface serves as a common type for different kinds of actions
 * that can be triggered by key sequences in the line editor. JLine supports
 * three main types of bindings:
 * <ul>
 *   <li>{@link Widget} - Executes a specific editing function</li>
 *   <li>{@link Macro} - Executes a sequence of keystrokes</li>
 *   <li>{@link Reference} - References another widget by name</li>
 * </ul>
 * <p>
 * Key bindings are managed through KeyMaps, which map key sequences to Binding
 * objects. When a user presses a key sequence, the LineReader looks up the
 * corresponding Binding in the current KeyMap and executes it.
 * <p>
 * This interface doesn't define any methods; it's used purely as a marker
 * to identify objects that can be bound to key sequences.
 *
 * @see Macro
 * @see Reference
 * @see Widget
 * @see org.jline.keymap.KeyMap
 * @see LineReader#getKeyMaps()
 */
public interface Binding {}
