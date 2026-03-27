/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.terminal.impl;

import jdk.internal.org.jline.terminal.Terminal.Signal;
import jdk.internal.org.jline.terminal.Terminal.SignalHandler;

/**
 * Implementation of SignalHandler for native signal handling.
 *
 * <p>
 * The NativeSignalHandler class provides an implementation of the SignalHandler
 * interface that represents native signal handlers. It defines two special
 * instances that correspond to the standard POSIX signal dispositions:
 * </p>
 * <ul>
 *   <li>{@link #SIG_DFL} - The default signal handler</li>
 *   <li>{@link #SIG_IGN} - The signal handler that ignores the signal</li>
 * </ul>
 *
 * <p>
 * This class is used internally by terminal implementations to represent native
 * signal handlers. It cannot be instantiated directly, and its {@link #handle(Signal)}
 * method throws an UnsupportedOperationException because native signal handling
 * is performed by the underlying platform, not by Java code.
 * </p>
 *
 * @see org.jline.terminal.Terminal.SignalHandler
 * @see org.jline.terminal.Terminal#handle(Signal, SignalHandler)
 */
public final class NativeSignalHandler implements SignalHandler {

    /**
     * The default signal handler.
     *
     * <p>
     * This constant represents the default signal handler, which corresponds to
     * the SIG_DFL disposition in POSIX systems. When a signal is handled by the
     * default handler, the default action for that signal is taken, which varies
     * depending on the signal (e.g., termination, core dump, ignore, etc.).
     * </p>
     */
    public static final NativeSignalHandler SIG_DFL = new NativeSignalHandler();

    /**
     * The signal handler that ignores signals.
     *
     * <p>
     * This constant represents the signal handler that ignores signals, which
     * corresponds to the SIG_IGN disposition in POSIX systems. When a signal is
     * handled by this handler, the signal is ignored and no action is taken.
     * </p>
     */
    public static final NativeSignalHandler SIG_IGN = new NativeSignalHandler();

    /**
     * Private constructor to prevent direct instantiation.
     *
     * <p>
     * This constructor is private because NativeSignalHandler instances should
     * only be created for the predefined constants SIG_DFL and SIG_IGN.
     * </p>
     */
    private NativeSignalHandler() {}

    /**
     * Handles the specified signal.
     *
     * <p>
     * This method always throws an UnsupportedOperationException because native
     * signal handling is performed by the underlying platform, not by Java code.
     * The NativeSignalHandler instances are only used as markers to indicate
     * which native signal handler should be used.
     * </p>
     *
     * @param signal the signal to handle
     * @throws UnsupportedOperationException always thrown to indicate that this
     *                                       method cannot be called directly
     */
    public void handle(Signal signal) {
        throw new UnsupportedOperationException();
    }
}
