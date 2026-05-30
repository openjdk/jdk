/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.terminal.spi;

import jdk.internal.org.jline.terminal.Terminal;

/**
 * Extended Terminal interface that provides access to internal implementation details.
 *
 * <p>
 * The {@code TerminalExt} interface extends the standard {@link Terminal} interface
 * with additional methods that provide access to the terminal's internal implementation
 * details. These methods are primarily used by terminal providers and other internal
 * components of the JLine library.
 * </p>
 *
 * <p>
 * Terminal implementations typically implement this interface to expose information
 * about their creation and configuration, such as the provider that created them
 * and the system stream they are associated with.
 * </p>
 *
 * <p>
 * Application code should generally use the standard {@link Terminal} interface
 * rather than this extended interface, unless specific access to these internal
 * details is required.
 * </p>
 *
 * @see Terminal
 * @see TerminalProvider
 * @see SystemStream
 */
public interface TerminalExt extends Terminal {

    /**
     * Returns the terminal provider that created this terminal.
     *
     * <p>
     * The terminal provider is responsible for creating and managing terminal
     * instances on a specific platform. This method allows access to the provider
     * that created this terminal, which can be useful for accessing provider-specific
     * functionality or for creating additional terminals with the same provider.
     * </p>
     *
     * @return the {@code TerminalProvider} that created this terminal,
     *         or {@code null} if the terminal was created with no provider
     * @see TerminalProvider
     */
    TerminalProvider getProvider();

    /**
     * Returns the system stream associated with this terminal, if any.
     *
     * <p>
     * This method indicates whether the terminal is bound to a standard system stream
     * (standard input, standard output, or standard error). Terminals that are connected
     * to system streams typically represent the actual terminal window or console that
     * the application is running in.
     * </p>
     *
     * @return the underlying system stream, which may be {@link SystemStream#Input},
     *         {@link SystemStream#Output}, {@link SystemStream#Error}, or {@code null}
     *         if this terminal is not bound to a system stream
     * @see SystemStream
     */
    SystemStream getSystemStream();
}
