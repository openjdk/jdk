/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.natives.java.io;

import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static jdk.internal.foreign.support.LookupUtil.*;
import static jdk.internal.foreign.support.InvokeUtil.newInternalError;

public final class NativeConsole {

    private NativeConsole() {}

    public static boolean istty() {
        return isatty(0) && isatty(1);
    }

    public static String encoding() {
        return null;
    }

    // Native methods

    // https://man7.org/linux/man-pages/man3/isatty.3.html
    private static final MethodHandle IS_A_TTY = downcall("isatty", JAVA_INT, JAVA_INT);

    static boolean isatty(int fd) {
        try {
            return (int) IS_A_TTY.invokeExact(fd) == 1;
        } catch (Throwable t) {
            throw newInternalError(IS_A_TTY, t);
        }
    }
}
