/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @summary Verifies that a VMObjectAlloc event is generated for a value object created using MethodHandle
 * @requires vm.jvmti
 * @enablePreview
 * @run main/othervm/native -agentlib:VMObjectAllocValueTest VMObjectAllocValueTest
 */

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public value class VMObjectAllocValueTest {

    private static native int getNumberOfAllocation();

    public VMObjectAllocValueTest(String str) {
    }

    public static void main(String[] args) throws Throwable {

        MethodHandles.Lookup publicLookup = MethodHandles.publicLookup();
        MethodType mt = MethodType.methodType(void.class, String.class);
        MethodHandle mh = publicLookup.findConstructor(VMObjectAllocValueTest.class, mt);
        mh.invoke("str"); // to trigger a VMObjectAlloc event invoke ctor not from Java

        if (getNumberOfAllocation() != 1) {
            throw new Exception("Number of allocation != 1");
        }
    }
}
