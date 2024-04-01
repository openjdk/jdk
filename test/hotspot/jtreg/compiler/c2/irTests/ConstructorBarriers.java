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
 * @bug 8300148
 * @summary Test barriers emitted in constructors
 * @library /test/lib /
 * @run main compiler.c2.irTests.ConstructorBarriers
 */
public class ConstructorBarriers {
    private static class ClassBasic {
        int field;
        public ClassBasic(int i) { field = i; }
    }
    private static class ClassWithFinal {
        final int field;
        public ClassWithFinal(int i) { field = i; }
    }
    private static class ClassWithVolatile {
        volatile int field;
        public ClassWithVolatile(int i) { field = i; }
    }

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH})
    @IR(failOn = IRNode.MEMBAR_RELEASE)
    @IR(counts = {IRNode.MEMBAR_STORESTORE, "1"})
    public Object classBasic(int i) {
        return new ClassBasic(i);
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH})
    @IR(failOn = IRNode.MEMBAR_RELEASE)
    @IR(failOn = IRNode.MEMBAR_STORESTORE)
    public int classBasicNoEscape(int i) {
        return new ClassBasic(i).field;
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH})
    @IR(failOn = IRNode.MEMBAR_RELEASE)
    @IR(counts = {IRNode.MEMBAR_STORESTORE, "1"})
    public Object classWithFinal(int i) {
        return new ClassWithFinal(i);
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH})
    @IR(failOn = IRNode.MEMBAR_RELEASE)
    @IR(failOn = IRNode.MEMBAR_STORESTORE)
    public int classWithFinalNoEscape(int i) {
        return new ClassWithFinal(i).field;
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH})
    @IR(counts = {IRNode.MEMBAR_STORESTORE, "1"})
    @IR(counts = {IRNode.MEMBAR_RELEASE, "1"})
    @IR(counts = {IRNode.MEMBAR_VOLATILE, "1"})
    public Object classWithVolatile(int i) {
        return new ClassWithVolatile(i);
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH})
    @IR(failOn = IRNode.MEMBAR_STORESTORE)
    @IR(failOn = IRNode.MEMBAR_RELEASE)
    @IR(failOn = IRNode.MEMBAR_VOLATILE)
    public int classWithVolatileNoEscape(int i) {
        return new ClassWithVolatile(i).field;
    }

    @Setup
    Object[] stringBuilderSetup() {
        return new Object[] { "foo", "bar", "baz" };
    }

    @Test
    @Arguments(setup = "stringBuilderSetup")
    @IR(failOn = IRNode.MEMBAR_RELEASE)
    @IR(failOn = IRNode.MEMBAR_VOLATILE)
    @IR(counts = {IRNode.MEMBAR_STORESTORE, "3"})
    public String stringBuilder(String s1, String s2, String s3) {
        return new StringBuilder().append(s1).append(s2).append(s3).toString();
    }
}
