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

package jdk.internal.natives;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;

import static jdk.internal.foreign.support.LookupUtil.downcall;
import static jdk.internal.foreign.support.InvokeUtil.*;
import static jdk.internal.natives.WindowsTypes.DWORD;
import static jdk.internal.natives.WindowsTypes.HANDLE;

public final class WindowsMethods {

    private WindowsMethods() {}

    // https://learn.microsoft.com/en-us/windows/win32/api/fileapi/nf-fileapi-getfiletype
    private static final MethodHandle GET_FILE_TYPE = downcall("GetFileType", DWORD, HANDLE);

    public static int getFileType(MemorySegment hFile) {
        try {
            return (int) GET_FILE_TYPE.invokeExact(hFile);
        } catch (Throwable t) {
            throw newInternalError(GET_FILE_TYPE, t);
        }
    }

    // https://learn.microsoft.com/en-us/windows/console/getstdhandle
    private static final MethodHandle GET_STD_HANDLE = downcall("GetStdHandle", HANDLE, DWORD);

    public static MemorySegment getStdHandle(int nStdHandle) {
        try {
            return (MemorySegment) GET_STD_HANDLE.invokeExact(nStdHandle);
        } catch (Throwable t) {
            throw newInternalError(GET_STD_HANDLE, t);
        }
    }

}
