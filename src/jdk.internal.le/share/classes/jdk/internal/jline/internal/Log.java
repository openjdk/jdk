/*
 * Copyright (c) 2002-2016, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package jdk.internal.jline.internal;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
//import java.util.logging.LogRecord;
//import java.util.logging.Logger;

import static jdk.internal.jline.internal.Preconditions.checkNotNull;

/**
 * Internal logger.
 *
 * @author <a href="mailto:jason@planet57.com">Jason Dillon</a>
 * @author <a href="mailto:gnodet@gmail.com">Guillaume Nodet</a>
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

    public static final boolean TRACE = Configuration.getBoolean(Log.class.getName() + ".trace");

    public static final boolean DEBUG = TRACE || Configuration.getBoolean(Log.class.getName() + ".debug");

    private static PrintStream output = System.err;

    private static boolean useJul = Configuration.getBoolean("jline.log.jul");

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
        if (useJul) {
            logWithJul(level, messages);
            return;
        }
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

    static void logWithJul(Level level, Object... messages) {
//        Logger logger = Logger.getLogger("jline");
//        Throwable cause = null;
//        ByteArrayOutputStream baos = new ByteArrayOutputStream();
//        PrintStream ps = new PrintStream(baos);
//        for (int i = 0; i < messages.length; i++) {
//            // Special handling for the last message if its a throwable, render its stack on the next line
//            if (i + 1 == messages.length && messages[i] instanceof Throwable) {
//                cause = (Throwable) messages[i];
//            }
//            else {
//                render(ps, messages[i]);
//            }
//        }
//        ps.close();
//        LogRecord r = new LogRecord(toJulLevel(level), baos.toString());
//        r.setThrown(cause);
//        logger.log(r);
    }

//    private static java.util.logging.Level toJulLevel(Level level) {
//        switch (level) {
//            case TRACE:
//                return java.util.logging.Level.FINEST;
//            case DEBUG:
//                return java.util.logging.Level.FINE;
//            case INFO:
//                return java.util.logging.Level.INFO;
//            case WARN:
//                return java.util.logging.Level.WARNING;
//            case ERROR:
//                return java.util.logging.Level.SEVERE;
//            default:
//                throw new IllegalArgumentException();
//        }
//    }

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
