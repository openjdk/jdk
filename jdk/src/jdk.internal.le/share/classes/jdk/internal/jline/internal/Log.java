/*
 * Copyright (c) 2002-2012, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package jdk.internal.jline.internal;

import java.io.PrintStream;

import static jdk.internal.jline.internal.Preconditions.checkNotNull;

/**
 * Internal logger.
 *
 * @author <a href="mailto:jason@planet57.com">Jason Dillon</a>
 * @since 2.0
 */
public final class Log
{
    ///CLOVER:OFF

    public static enum Level
    {
        TRACE,
        DEBUG,
        INFO,
        WARN,
        ERROR
    }

    @SuppressWarnings({"StringConcatenation"})
    public static final boolean TRACE = Boolean.getBoolean(Log.class.getName() + ".trace");

    @SuppressWarnings({"StringConcatenation"})
    public static final boolean DEBUG = TRACE || Boolean.getBoolean(Log.class.getName() + ".debug");

    private static PrintStream output = System.err;

    public static PrintStream getOutput() {
        return output;
    }

    public static void setOutput(final PrintStream out) {
        output = checkNotNull(out);
    }

    /**
     * Helper to support rendering messages.
     */
    @TestAccessible
    static void render(final PrintStream out, final Object message) {
        if (message.getClass().isArray()) {
            Object[] array = (Object[]) message;

            out.print("[");
            for (int i = 0; i < array.length; i++) {
                out.print(array[i]);
                if (i + 1 < array.length) {
                    out.print(",");
                }
            }
            out.print("]");
        }
        else {
            out.print(message);
        }
    }

    @TestAccessible
    static void log(final Level level, final Object... messages) {
        //noinspection SynchronizeOnNonFinalField
        synchronized (output) {
            output.format("[%s] ", level);

            for (int i=0; i<messages.length; i++) {
                // Special handling for the last message if its a throwable, render its stack on the next line
                if (i + 1 == messages.length && messages[i] instanceof Throwable) {
                    output.println();
                    ((Throwable)messages[i]).printStackTrace(output);
                }
                else {
                    render(output, messages[i]);
                }
            }

            output.println();
            output.flush();
        }
    }

    public static void trace(final Object... messages) {
        if (TRACE) {
            log(Level.TRACE, messages);
        }
    }

    public static void debug(final Object... messages) {
        if (TRACE || DEBUG) {
            log(Level.DEBUG, messages);
        }
    }

    /**
     * @since 2.7
     */
    public static void info(final Object... messages) {
        log(Level.INFO, messages);
    }

    public static void warn(final Object... messages) {
        log(Level.WARN, messages);
    }

    public static void error(final Object... messages) {
        log(Level.ERROR, messages);
    }
}
