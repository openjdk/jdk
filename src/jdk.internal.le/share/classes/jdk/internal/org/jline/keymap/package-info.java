/*
 * Copyright (c) 2002-2025, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
/**
 * JLine 3 Keymap Package - Components for handling keyboard input and key bindings.
 * <p>
 * This package provides the fundamental classes for mapping keyboard input sequences
 * to actions in interactive terminal applications. It enables the creation of
 * customizable key bindings similar to those found in editors like Emacs and Vi.
 * <p>
 * Key components in this package include:
 * <ul>
 *   <li>{@link org.jline.keymap.KeyMap} - Maps key sequences to actions or commands</li>
 *   <li>{@link org.jline.keymap.BindingReader} - Reads input and translates it to bound actions</li>
 * </ul>
 * <p>
 * The keymap system supports:
 * <ul>
 *   <li>Multi-character key sequences (e.g., Escape followed by other keys)</li>
 *   <li>Special keys like function keys, arrow keys, etc.</li>
 *   <li>Control key combinations</li>
 *   <li>Alt/Meta key combinations</li>
 *   <li>Unicode character input</li>
 * </ul>
 * <p>
 * This package is used extensively by the {@link org.jline.reader.LineReader} to implement
 * customizable editing capabilities.
 *
 * @since 3.0
 */
package org.jline.keymap;
