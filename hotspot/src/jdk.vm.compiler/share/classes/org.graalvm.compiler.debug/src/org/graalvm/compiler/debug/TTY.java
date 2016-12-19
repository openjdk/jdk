/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.compiler.debug;

import java.io.PrintStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.graalvm.compiler.serviceprovider.GraalServices;

/**
 * A collection of static methods for printing debug and informational output to a global
 * {@link LogStream}. The output can be (temporarily) suppressed per thread through use of a
 * {@linkplain Filter filter}.
 */
public class TTY {

    /**
     * Support for thread-local suppression of {@link TTY}.
     */
    public static class Filter {

        private LogStream previous;
        private final Thread thread = Thread.currentThread();

        /**
         * Creates an object that will suppress {@link TTY} for the current thread if the given
         * filter does not match the given object. To revert the suppression state to how it was
         * before this call, the {@link #remove()} method must be called on the suppression object.
         *
         * @param filter the pattern for matching. If {@code null}, then the match is successful. If
         *            it starts with "~", then a regular expression
         *            {@linkplain Pattern#matches(String, CharSequence) match} is performed where
         *            the regular expression is specified by {@code filter} without the "~" prefix.
         *            Otherwise, a simple {@linkplain String#contains(CharSequence) substring} match
         *            is performed where {@code filter} is the substring used.
         * @param object an object whose {@linkplain Object#toString() string} value is matched
         *            against {@code filter}
         */
        public Filter(String filter, Object object) {
            boolean suppressed = false;
            if (filter != null) {
                String input = object.toString();
                if (filter.startsWith("~")) {
                    suppressed = !Pattern.matches(filter.substring(1), input);
                } else {
                    suppressed = !input.contains(filter);
                }
                if (suppressed) {
                    previous = out();
                    log.set(LogStream.SINK);
                }
            }
        }

        /**
         * Creates an object that will suppress {@link TTY} for the current thread. To revert the
         * suppression state to how it was before this call, the {@link #remove()} method must be
         * called on this filter object.
         */
        public Filter() {
            previous = out();
            log.set(LogStream.SINK);
        }

        /**
         * Reverts the suppression state of {@link TTY} to how it was before this object was
         * constructed.
         */
        public void remove() {
            assert thread == Thread.currentThread();
            if (previous != null) {
                log.set(previous);
            }
        }
    }

    /**
     * The {@link PrintStream} to which all non-suppressed output from {@link TTY} is written.
     */
    public static final PrintStream out;
    static {
        TTYStreamProvider p = GraalServices.loadSingle(TTYStreamProvider.class, false);
        out = p == null ? System.out : p.getStream();
    }

    private static final ThreadLocal<LogStream> log = new ThreadLocal<LogStream>() {

        @Override
        protected LogStream initialValue() {
            return new LogStream(out);
        }
    };

    public static boolean isSuppressed() {
        return log.get() == LogStream.SINK;
    }

    /**
     * Gets the thread-local log stream to which the static methods of this class send their output.
     * This will either be a global log stream or the global {@linkplain LogStream#SINK sink}
     * depending on whether any suppression {@linkplain Filter filters} are in effect for the
     * current thread.
     */
    public static LogStream out() {
        return log.get();
    }

    /**
     * @see LogStream#print(String)
     */
    public static void print(String s) {
        out().print(s);
    }

    /**
     * @see LogStream#print(int)
     */
    public static void print(int i) {
        out().print(i);
    }

    /**
     * @see LogStream#print(long)
     */
    public static void print(long i) {
        out().print(i);
    }

    /**
     * @see LogStream#print(char)
     */
    public static void print(char c) {
        out().print(c);
    }

    /**
     * @see LogStream#print(boolean)
     */
    public static void print(boolean b) {
        out().print(b);
    }

    /**
     * @see LogStream#print(double)
     */
    public static void print(double d) {
        out().print(d);
    }

    /**
     * @see LogStream#print(float)
     */
    public static void print(float f) {
        out().print(f);
    }

    /**
     * @see LogStream#println(String)
     */
    public static void println(String s) {
        out().println(s);
    }

    /**
     * @see LogStream#println()
     */
    public static void println() {
        out().println();
    }

    /**
     * @see LogStream#println(int)
     */
    public static void println(int i) {
        out().println(i);
    }

    /**
     * @see LogStream#println(long)
     */
    public static void println(long l) {
        out().println(l);
    }

    /**
     * @see LogStream#println(char)
     */
    public static void println(char c) {
        out().println(c);
    }

    /**
     * @see LogStream#println(boolean)
     */
    public static void println(boolean b) {
        out().println(b);
    }

    /**
     * @see LogStream#println(double)
     */
    public static void println(double d) {
        out().println(d);
    }

    /**
     * @see LogStream#println(float)
     */
    public static void println(float f) {
        out().println(f);
    }

    public static void printf(String format, Object... args) {
        out().printf(format, args);
    }

    public static void println(String format, Object... args) {
        out().printf(format + "%n", args);
    }

    public static void fillTo(int i) {
        out().fillTo(i, ' ');
    }

    public static void printFields(Class<?> javaClass) {
        final String className = javaClass.getSimpleName();
        TTY.println(className + " {");
        for (final Field field : javaClass.getFields()) {
            printField(field, false);
        }
        TTY.println("}");
    }

    public static void printField(final Field field, boolean tabbed) {
        final String fieldName = String.format("%35s", field.getName());
        try {
            String prefix = tabbed ? "" : "    " + fieldName + " = ";
            String postfix = tabbed ? "\t" : "\n";
            if (field.getType() == int.class) {
                TTY.print(prefix + field.getInt(null) + postfix);
            } else if (field.getType() == boolean.class) {
                TTY.print(prefix + field.getBoolean(null) + postfix);
            } else if (field.getType() == float.class) {
                TTY.print(prefix + field.getFloat(null) + postfix);
            } else if (field.getType() == String.class) {
                TTY.print(prefix + field.get(null) + postfix);
            } else if (field.getType() == Map.class) {
                Map<?, ?> m = (Map<?, ?>) field.get(null);
                TTY.print(prefix + printMap(m) + postfix);
            } else {
                TTY.print(prefix + field.get(null) + postfix);
            }
        } catch (IllegalAccessException e) {
            // do nothing.
        }
    }

    private static String printMap(Map<?, ?> m) {
        StringBuilder sb = new StringBuilder();

        List<String> keys = new ArrayList<>();
        for (Object key : m.keySet()) {
            keys.add((String) key);
        }
        Collections.sort(keys);

        for (String key : keys) {
            sb.append(key);
            sb.append("\t");
            sb.append(m.get(key));
            sb.append("\n");
        }

        return sb.toString();
    }

    public static void flush() {
        out().flush();
    }
}
