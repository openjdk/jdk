/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

package compiler.c2.irTests;

import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8145948
 * @summary Test default value initializations in constructors
 * @library /test/lib /
 * @requires os.arch=="aarch64" | os.arch=="riscv64" | os.arch=="x86_64" | os.arch=="amd64"
 * @run driver compiler.c2.irTests.ConstructorDefaultInitBarriers
 */
public class ConstructorDefaultInitBarriers {
    public static void main(String[] args) {
        TestFramework.run();
     }

    public static class PlainInit {
        int x;
        public PlainInit(int x) {
            this.x = x;
        }
    }

    public static class VolatileInit {
        volatile int x;
        public VolatileInit(int x) {
            this.x = x;
        }
    }

    @DontInline
    public void consume(Object o) {}

    @Test
    @IR(counts = {IRNode.MEMBAR_STORESTORE, "1"})
    public Object plain_init_0() {
        return new PlainInit(0);
    }

    @Test
    @IR(counts = {IRNode.MEMBAR_STORESTORE, "1"})
    public Object plain_init_42() {
        return new PlainInit(42);
    }

    @Test
    @IR(counts = {IRNode.MEMBAR_STORESTORE, "1"})
    @IR(failOn = IRNode.MEMBAR_VOLATILE)
    public Object volatile_init_0() {
        return new VolatileInit(0);
    }

    @Test
    @IR(counts = {IRNode.MEMBAR_STORESTORE, "1"})
    @IR(counts = {IRNode.MEMBAR_VOLATILE, "1"})
    public Object volatile_init_42() {
        return new VolatileInit(42);
    }

}
