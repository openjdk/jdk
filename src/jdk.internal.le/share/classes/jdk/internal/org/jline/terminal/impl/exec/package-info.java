/*
 * Copyright (c) 2002-2025, the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */

/**
 * Implementation of terminal functionality using external command-line utilities.
 *
 * <p>
 * This package provides implementations of terminal-related interfaces that rely on
 * external command-line utilities (such as stty, tput, etc.) rather than native code
 * or JNI libraries. This approach allows JLine to work in environments where native
 * libraries are not available or cannot be used.
 * </p>
 *
 * <p>
 * The key components in this package are:
 * </p>
 *
 * <ul>
 *   <li>{@link org.jline.terminal.impl.exec.ExecPty} - A pseudoterminal implementation
 *       that uses external commands to interact with the terminal. It provides functionality
 *       for terminal attribute manipulation, size detection, and process control using
 *       standard Unix utilities.</li>
 *   <li>{@link org.jline.terminal.impl.exec.ExecTerminalProvider} - A terminal provider
 *       that creates terminals using the exec-based approach. It serves as a fallback
 *       when native terminal access is not available.</li>
 * </ul>
 *
 * <p>
 * This package is particularly useful in the following scenarios:
 * </p>
 *
 * <ul>
 *   <li>When running on platforms where JLine's native libraries cannot be loaded</li>
 *   <li>In restricted environments where JNI access is limited or prohibited</li>
 *   <li>As a fallback mechanism when preferred terminal access methods fail</li>
 *   <li>For debugging or testing terminal functionality without native dependencies</li>
 * </ul>
 *
 * <p>
 * The implementations in this package execute external commands to perform operations
 * such as:
 * </p>
 *
 * <ul>
 *   <li>Getting and setting terminal attributes (using stty)</li>
 *   <li>Determining terminal size (using stty or tput)</li>
 *   <li>Sending signals to processes</li>
 *   <li>Creating and managing pseudoterminals</li>
 * </ul>
 *
 * <p>
 * While this approach is more portable than native code, it may have performance
 * implications due to the overhead of executing external processes. It is typically
 * used as a fallback when more efficient methods are not available.
 * </p>
 *
 * @see org.jline.terminal.spi.TerminalProvider
 * @see org.jline.terminal.spi.Pty
 */
package org.jline.terminal.impl.exec;
