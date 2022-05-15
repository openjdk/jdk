/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.foreigntest;

import java.lang.foreign.*;
import java.lang.invoke.*;

public class PanamaMainInvoke {
    public static void main(String[] args) throws Throwable {
       testInvokenativeLinker();
       testInvokeMemorySegment();
    }

    public static void testInvokenativeLinker() throws Throwable {
        System.out.println("Trying to get Linker");
        var mh = MethodHandles.lookup().findStatic(Linker.class, "nativeLinker",
                MethodType.methodType(Linker.class));
        var linker = (Linker)mh.invokeExact();
        System.out.println("Got Linker");
    }

    public static void testInvokeMemorySegment() throws Throwable {
        System.out.println("Trying to get MemorySegment");
        var mh = MethodHandles.lookup().findStatic(MemorySegment.class, "ofAddress",
                MethodType.methodType(MemorySegment.class, MemoryAddress.class, long.class, MemorySession.class));
        var seg = (MemorySegment)mh.invokeExact(MemoryAddress.NULL, 4000L, MemorySession.global());
        System.out.println("Got MemorySegment");
    }
}
