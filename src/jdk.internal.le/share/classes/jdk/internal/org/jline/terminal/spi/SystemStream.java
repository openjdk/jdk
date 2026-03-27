/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.terminal.spi;

/**
 * Represents the standard system streams available in a terminal environment.
 *
 * <p>
 * This enum defines the three standard streams that are typically available
 * in a terminal environment: standard input, standard output, and standard error.
 * These streams are used for communication between the terminal and the processes
 * running within it.
 * </p>
 *
 * <p>
 * Terminal implementations and PTY objects may be associated with one of these
 * system streams to indicate their role in the terminal environment.
 * </p>
 *
 * @see org.jline.terminal.spi.Pty#getSystemStream()
 * @see org.jline.terminal.spi.TerminalExt#getSystemStream()
 */
public enum SystemStream {
    /**
     * Standard input stream (stdin).
     *
     * <p>
     * This stream is used to provide input to processes running in the terminal.
     * It typically represents keyboard input from the user.
     * </p>
     */
    Input,

    /**
     * Standard output stream (stdout).
     *
     * <p>
     * This stream is used by processes to output normal data. It typically
     * represents the main display output of the terminal.
     * </p>
     */
    Output,

    /**
     * Standard error stream (stderr).
     *
     * <p>
     * This stream is used by processes to output error messages and diagnostic
     * information. It may be displayed differently from standard output or
     * redirected to a separate destination.
     * </p>
     */
    Error
}
