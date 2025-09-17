/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package compiler.c2;

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.Stable;

import java.lang.ref.Cleaner;
import java.lang.ref.Reference;

/*
 * @test
 * @bug 8290892
 * @summary Tests to ensure that reachabilityFence() correctly keeps objects from being collected prematurely.
 * @modules java.base/jdk.internal.misc
 *          java.base/jdk.internal.vm.annotation
 * @run main/bootclasspath/othervm -Xbatch -XX:-TieredCompilation -XX:CompileCommand=quiet
 *                                 -XX:CompileCommand=compileonly,*::test
 *                                 -XX:+UnlockDiagnosticVMOptions -XX:+PreserveReachabilityFencesOnConstants
 *                                 compiler.c2.TestReachabilityFenceOnConstant
 */
public class TestReachabilityFenceOnConstant {
    static final Unsafe U = Unsafe.getUnsafe();

    static final long BUFFER_SIZE = 1024;
    static @Stable MyBuffer BUFFER = new MyBuffer();

    static volatile boolean isCleaned = false;

    static class MyBuffer {
        final @Stable long address;
        final @Stable long limit;

        MyBuffer() {
            final long adr = U.allocateMemory(BUFFER_SIZE);
            U.setMemory(adr, BUFFER_SIZE, (byte)0);
            address = adr;
            limit = BUFFER_SIZE;
            System.out.printf("Allocated memory (%d bytes): 0x%016x\n", BUFFER_SIZE, adr);
            Cleaner.create().register(this, () -> {
                System.out.printf("Freed memory (%d bytes): 0x%016x\n", BUFFER_SIZE, adr);
                U.setMemory(adr, BUFFER_SIZE, (byte)-1); // clear
                U.freeMemory(adr);
                isCleaned = true;
            });
        }

        byte getByte(long offset) {
            return U.getByte(null, address + offset);
        }
    }

    static int test() {
        int acc = 0;
        MyBuffer buf = BUFFER;
        try {
            for (long i = 0; i < buf.limit; i++) {
                acc += buf.getByte(i);
            }
        } finally {
            Reference.reachabilityFence(buf);
        }
        return acc;
    }

    static void runTest() {
        for (int i = 0; i < 20_000; i++) {
            if (test() != 0) {
                throw new AssertionError("observed stale buffer: TestConstantOop::isCleaned=" + isCleaned);
            }
        }
    }

    public static void main(String[] args) throws NoSuchFieldException, IllegalAccessException {
        runTest(); // run test() and constant fold accesses to BUFFER (and it's state) during JIT-compilation

        BUFFER = null; // remove last strong root

        // Ensure the instance is GCed.
        while (!isCleaned) {
            try {
                System.gc();
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        try {
            runTest(); // repeat to ensure stale BUFFER contents is not accessed
        } catch (NullPointerException e) {
            // expected; ignore
        }
        System.out.println("TEST PASSED");
    }
}
