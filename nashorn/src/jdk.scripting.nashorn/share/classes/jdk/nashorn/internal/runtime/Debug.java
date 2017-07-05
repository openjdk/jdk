/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.nashorn.internal.runtime;

import static jdk.nashorn.internal.parser.TokenType.EOF;
import jdk.nashorn.internal.parser.Lexer;
import jdk.nashorn.internal.parser.Token;
import jdk.nashorn.internal.parser.TokenStream;
import jdk.nashorn.internal.parser.TokenType;

/**
 * Utilities for debugging Nashorn.
 *
 */
public final class Debug {
    private Debug() {
    }

    /**
     * Return the topmost JavaScript frame in a stack trace
     * @param t throwable that contains the stack trace
     * @return line describing the topmost JavaScript frame
     */
    public static String firstJSFrame(final Throwable t) {
        for (final StackTraceElement ste : t.getStackTrace()) {
            if (ECMAErrors.isScriptFrame(ste)) {
                return ste.toString();
            }
        }
        return "<native code>";
    }

    /**
     * Return the topmost JavaScript frame from the current
     * continuation
     * @return line describing the topmost JavaScript frame
     */
    public static String firstJSFrame() {
        return firstJSFrame(new Throwable());
    }

    /**
     * Return the system identity hashcode for an object as a human readable
     * string
     *
     * @param x object
     * @return system identity hashcode as string
     */
    public static String id(final Object x) {
        return String.format("0x%08x", System.identityHashCode(x));
    }

    /**
     * Same as {@link Debug#id} but returns the identity hashcode as
     * an integer
     *
     * @param x object
     * @return system identity hashcode
     */
    public static int intId(final Object x) {
        return System.identityHashCode(x);
    }

    /**
     * Return a stack trace element description at a depth from where we are not
     *
     * @param depth depth
     * @return stack trace element as string
     */
    public static String stackTraceElementAt(final int depth) {
        return new Throwable().getStackTrace()[depth + 1].toString(); // add 1 to compensate for this method
    }

    /**
     * Determine caller for tracing purposes.
     * @param depth depth to trace
     * @param count max depth
     * @param ignores elements to ignore in stack trace
     * @return caller
     */
    public static String caller(final int depth, final int count, final String... ignores) {
        String result = "";
        final StackTraceElement[] callers = Thread.currentThread().getStackTrace();

        int c = count;
loop:
        for (int i = depth + 1; i < callers.length && c != 0; i++) {
            final StackTraceElement element = callers[i];
            final String method = element.getMethodName();

            for (final String ignore : ignores) {
                if (method.compareTo(ignore) == 0) {
                    continue loop;
                }
            }

            result += (method + ":" + element.getLineNumber() +
                       "                              ").substring(0, 30);
            c--;
        }

        return result.isEmpty() ? "<no caller>" : result;
    }

    /**
     * Dump a token stream to stdout
     *
     * TODO: most other bugging goes to stderr, change?
     *
     * @param source the source
     * @param lexer  the lexer
     * @param stream the stream to dump
     */
    public static void dumpTokens(final Source source, final Lexer lexer, final TokenStream stream) {
        TokenType type;
        int k = 0;
        do {
            while (k > stream.last()) {
                // Get more tokens.
                lexer.lexify();
            }

            final long token = stream.get(k);
            type = Token.descType(token);
            System.out.println("" + k + ": " + Token.toString(source, token, true));
            k++;
        } while(type != EOF);
    }

}
