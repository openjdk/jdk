/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package java.io;

import jdk.internal.javac.PreviewFeature;

/**
 * A collection of static convenience methods that provide access to
 * {@link System#console()} for implicitly declared classes.
 *
 * <p> Each of this class' methods calls a similarly-named method on
 * {@code Console} returned by {@code System.console()}, or throws
 * {@link IOError} if {@code System.console()} returns {@code null}.
 *
 * @since 23
 */
@PreviewFeature(feature = PreviewFeature.Feature.IMPLICIT_CLASSES)
public class IO {

    private IO() {
        throw new Error("no instances");
    }

    /**
     * Calls {@link Console#println(Object) Console.println(obj)} on
     * {@link System#console()}, or throws {@link IOError} if
     * {@code System.console()} returns {@code null}.
     *
     * @param obj the object to print
     */
    public static void println(Object obj) {
        con().println(obj);
    }

    /**
     * Calls {@link Console#print(Object) Console.print(obj)} on
     * {@link System#console()}, or throws {@link IOError} if
     * {@code System.console()} returns {@code null}.
     *
     * @param obj the object to print
     */
    public static void print(Object obj) {
        con().print(obj);
    }

    /**
     * {@return the result of a call to {@link Console#readln(String)
     * Console.readln(prompt)} on {@link System#console()}, or throws
     * {@link IOError} if {@code System.console()} returns {@code null}}
     *
     * @param prompt the prompt string
     */
    public static String readln(String prompt) {
        return con().readln(prompt);
    }

    private static Console con() {
        var con = System.console();
        if (con != null) {
            return con;
        } else {
            throw new IOError(null);
        }
    }
}
