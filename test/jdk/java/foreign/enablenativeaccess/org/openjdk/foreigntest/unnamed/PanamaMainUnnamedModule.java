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

package org.openjdk.foreigntest.unnamed;

import java.lang.foreign.*;
import java.lang.foreign.Linker.Option;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

public class PanamaMainUnnamedModule {

    static {
        System.loadLibrary("LinkerInvokerUnnamed");
    }

    public static void main(String[] args) throws Throwable {
        testReflection();
        testInvoke();
        testDirectAccess();
        testJNIAccess();
    }

   public static void testReflection() throws Throwable {
       Linker linker = Linker.nativeLinker();
       Method method = Linker.class.getDeclaredMethod("downcallHandle", FunctionDescriptor.class, Option[].class);
       method.invoke(linker, FunctionDescriptor.ofVoid(), new Linker.Option[0]);
   }

   public static void testInvoke() throws Throwable {
       var mh = MethodHandles.lookup().findVirtual(Linker.class, "downcallHandle",
           MethodType.methodType(MethodHandle.class, FunctionDescriptor.class, Linker.Option[].class));
       var downcall = (MethodHandle)mh.invokeExact(Linker.nativeLinker(), FunctionDescriptor.ofVoid(), new Linker.Option[0]);
   }

   public static void testDirectAccess() {
       Linker.nativeLinker().downcallHandle(FunctionDescriptor.ofVoid());
   }

   public static void testJNIAccess() {
        nativeLinker0();
    }

    static native void nativeLinker0();
}
