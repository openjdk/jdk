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

package jdk.nashorn.api.scripting;

import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptRuntime;

/**
 * Utilities that are to be called from script code
 */
public final class ScriptUtils {
    private ScriptUtils() {}

    /**
     * Returns AST as JSON compatible string. This is used to
     * implement "parse" function in resources/parse.js script.
     *
     * @param code code to be parsed
     * @param name name of the code source (used for location)
     * @param includeLoc tells whether to include location information for nodes or not
     * @return JSON string representation of AST of the supplied code
     */
    public static String parse(final String code, final String name, final boolean includeLoc) {
        return ScriptRuntime.parse(code, name, includeLoc);
    }

    /**
     * Method which converts javascript types to java types for the
     * String.format method (jrunscript function sprintf).
     *
     * @param format a format string
     * @param args arguments referenced by the format specifiers in format
     * @return a formatted string
     */
    public static String format(final String format, final Object[] args) {
        return Formatter.format(format, args);
    }

    /**
     * Create a wrapper function that calls {@code func} synchronized on {@code sync} or, if that is undefined,
     * {@code self}. Used to implement "sync" function in resources/mozilla_compat.js.
     *
     * @param func the function to invoke
     * @param sync the object to synchronize on
     * @return a synchronizing wrapper function
     */
    public static Object makeSynchronizedFunction(final ScriptFunction func, final Object sync) {
        return func.makeSynchronizedFunction(sync);
    }

}
