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

import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;

import static jdk.internal.foreign.support.DefaultNativeLookupUtil.downcall;
import static jdk.internal.foreign.support.DefaultNativeLookupUtil.downcallOfVoid;
import static jdk.internal.foreign.support.InvokeUtil.invokeAsInt;
import static jdk.internal.natives.WindowsConstants.*;
import static jdk.internal.natives.WindowsMethods.getFileType;
import static jdk.internal.natives.WindowsMethods.getStdHandle;
import static jdk.internal.natives.WindowsTypes.*;

public final class NativeConsole {

    private NativeConsole() {}

    public static boolean istty() {
        MemorySegment hStdIn = getStdHandle(STD_INPUT_HANDLE);
        MemorySegment hStdOut = getStdHandle(STD_OUTPUT_HANDLE);

        return !isInvalidHandleValue(hStdIn) &&
                !isInvalidHandleValue(hStdOut) &&
                getFileType(hStdIn) == FILE_TYPE_CHAR &&
                getFileType(hStdOut) == FILE_TYPE_CHAR;

    }

    public static String encoding() {
        int cp = invokeAsInt(GET_CONSOLE_CP);
        if (cp >= 874 && cp <= 950) {
            return "ms" + cp;
        }
        if (cp == 65001) {
            return StandardCharsets.UTF_8.name();
        }
        return "cp" + cp;
    }

    // Native methods

    // https://learn.microsoft.com/en-us/windows/console/getconsolecp
    private static final MethodHandle GET_CONSOLE_CP = downcall("GetConsoleCP", UINT);

}
