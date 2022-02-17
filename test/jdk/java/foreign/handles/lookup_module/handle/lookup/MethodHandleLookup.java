/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package handle.lookup;

import jdk.incubator.foreign.CLinker;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import jdk.incubator.foreign.Addressable;
import jdk.incubator.foreign.SymbolLookup;
import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;

import jdk.incubator.foreign.VaList;
import jdk.incubator.foreign.ValueLayout;
import org.testng.annotations.*;

public class MethodHandleLookup {

    @Test(dataProvider = "restrictedMethods")
    public void testRestrictedHandles(MethodHandle handle, String testName) throws Throwable {
        new handle.invoker.MethodHandleInvoker().call(handle);
    }

    @DataProvider(name = "restrictedMethods")
    static Object[][] restrictedMethods() {
        try {
            return new Object[][]{
                    { MethodHandles.lookup().findStatic(CLinker.class, "systemCLinker",
                            MethodType.methodType(CLinker.class)), "ForeignLinker::systemCLinker" },
                    { MethodHandles.lookup().findStatic(VaList.class, "ofAddress",
                            MethodType.methodType(VaList.class, MemoryAddress.class, ResourceScope.class)),
                            "VaList::ofAddress/1" },
                    { MethodHandles.lookup().findStatic(SymbolLookup.class, "loaderLookup",
                            MethodType.methodType(SymbolLookup.class)),
                            "SymbolLookup::loaderLookup" },
                    { MethodHandles.lookup().findStatic(MemorySegment.class, "ofAddress",
                            MethodType.methodType(MemorySegment.class, MemoryAddress.class, long.class, ResourceScope.class)),
                            "MemorySegment::ofAddress" },
                    { MethodHandles.lookup().findVirtual(MemoryAddress.class, "getUtf8String",
                            MethodType.methodType(String.class, long.class)),
                            "MemoryAddress::getUtf8String" },
                    { MethodHandles.lookup().findVirtual(MemoryAddress.class, "setUtf8String",
                            MethodType.methodType(void.class, long.class, String.class)),
                            "MemoryAddress::setUtf8String" },
                    { MethodHandles.lookup().findVirtual(MemoryAddress.class, "get",
                            MethodType.methodType(byte.class, ValueLayout.OfByte.class, long.class)),
                            "MemoryAddress::get/byte" },
                    { MethodHandles.lookup().findVirtual(MemoryAddress.class, "get",
                            MethodType.methodType(boolean.class, ValueLayout.OfBoolean.class, long.class)),
                            "MemoryAddress::get/boolean" },
                    { MethodHandles.lookup().findVirtual(MemoryAddress.class, "get",
                            MethodType.methodType(char.class, ValueLayout.OfChar.class, long.class)),
                            "MemoryAddress::get/char" },
                    { MethodHandles.lookup().findVirtual(MemoryAddress.class, "get",
                            MethodType.methodType(short.class, ValueLayout.OfShort.class, long.class)),
                            "MemoryAddress::get/short" },
                    { MethodHandles.lookup().findVirtual(MemoryAddress.class, "get",
                            MethodType.methodType(int.class, ValueLayout.OfInt.class, long.class)),
                            "MemoryAddress::get/int" },
                    { MethodHandles.lookup().findVirtual(MemoryAddress.class, "get",
                            MethodType.methodType(float.class, ValueLayout.OfFloat.class, long.class)),
                            "MemoryAddress::get/float" },
                    { MethodHandles.lookup().findVirtual(MemoryAddress.class, "get",
                            MethodType.methodType(long.class, ValueLayout.OfLong.class, long.class)),
                            "MemoryAddress::get/long" },
                    { MethodHandles.lookup().findVirtual(MemoryAddress.class, "get",
                            MethodType.methodType(double.class, ValueLayout.OfDouble.class, long.class)),
                            "MemoryAddress::get/double" },
                    { MethodHandles.lookup().findVirtual(MemoryAddress.class, "get",
                            MethodType.methodType(MemoryAddress.class, ValueLayout.OfAddress.class, long.class)),
                            "MemoryAddress::get/address" },
                    { MethodHandles.lookup().findVirtual(MemoryAddress.class, "set",
                            MethodType.methodType(void.class, ValueLayout.OfByte.class, long.class, byte.class)),
                            "MemoryAddress::set/byte" },
                    { MethodHandles.lookup().findVirtual(MemoryAddress.class, "set",
                            MethodType.methodType(void.class, ValueLayout.OfBoolean.class, long.class, boolean.class)),
                            "MemoryAddress::set/boolean" },
                    { MethodHandles.lookup().findVirtual(MemoryAddress.class, "set",
                            MethodType.methodType(void.class, ValueLayout.OfChar.class, long.class, char.class)),
                            "MemoryAddress::set/char" },
                    { MethodHandles.lookup().findVirtual(MemoryAddress.class, "set",
                            MethodType.methodType(void.class, ValueLayout.OfShort.class, long.class, short.class)),
                            "MemoryAddress::set/short" },
                    { MethodHandles.lookup().findVirtual(MemoryAddress.class, "set",
                            MethodType.methodType(void.class, ValueLayout.OfInt.class, long.class, int.class)),
                            "MemoryAddress::set/int" },
                    { MethodHandles.lookup().findVirtual(MemoryAddress.class, "set",
                            MethodType.methodType(void.class, ValueLayout.OfFloat.class, long.class, float.class)),
                            "MemoryAddress::set/float" },
                    { MethodHandles.lookup().findVirtual(MemoryAddress.class, "set",
                            MethodType.methodType(void.class, ValueLayout.OfLong.class, long.class, long.class)),
                            "MemoryAddress::set/long" },
                    { MethodHandles.lookup().findVirtual(MemoryAddress.class, "set",
                            MethodType.methodType(void.class, ValueLayout.OfDouble.class, long.class, double.class)),
                            "MemoryAddress::set/double" },
                    { MethodHandles.lookup().findVirtual(MemoryAddress.class, "set",
                            MethodType.methodType(void.class, ValueLayout.OfAddress.class, long.class, Addressable.class)),
                            "MemoryAddress::set/address" },
            };
        } catch (Throwable ex) {
            throw new ExceptionInInitializerError((ex));
        }
    }
}
