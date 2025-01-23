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

 /*
 * @test
 * @bug 8341696
 * @summary Allow C2 to also optimize non-fluid string builder calls.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.stringopts.TestFluidAndNonFluid
 */
package compiler.c2.irTests.stringopts;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;

public class TestFluidAndNonFluid {

    public static int unknown = 1;

    public static void main(String[] args) {
        // Dont inline any StringBuilder methods for this IR test to check if string opts are applied or not.
        TestFramework.runWithFlags("-XX:CompileCommand=dontinline,java.lang.StringBuilder::*");
    }

    @DontInline
    public static void opaque(StringBuilder builder) {
        builder.append("Z");
    }

    @Run(test = {"fluid", "nonFluid", "nonFinal", "nonFluidExtraneousVariable", "nonFluidConditional",
        "nonFluidOpaqueCall"})
    public void runMethod() {
        Asserts.assertEQ("0ac", fluidNoParam());
        Asserts.assertEQ("ac", nonFluidNoParam());
        Asserts.assertEQ("ac", fluid("c"));
        Asserts.assertEQ("ac", nonFluid("c"));
        Asserts.assertEQ("ac", nonFinal("c"));
        Asserts.assertEQ("ac", nonFluidExtraneousVariable("c"));
        Asserts.assertEQ("ac", nonFluidConditional("c"));
        Asserts.assertEQ("aZ", nonFluidOpaqueCall());
    }

    @Test
    @IR(failOn = {IRNode.ALLOC_OF, "StringBuilder", IRNode.CALL_OF_METHOD, "toString", IRNode.INTRINSIC_TRAP})
    public static String fluidNoParam() {
        return new StringBuilder("0").append("a").append("c").toString();
    }

    @Test
    @IR(failOn = {IRNode.ALLOC_OF, "StringBuilder", IRNode.CALL_OF_METHOD, "toString", IRNode.INTRINSIC_TRAP})
    public static String nonFluidNoParam() {
        final StringBuilder sb = new StringBuilder();
        sb.append("a");
        sb.append("c");
        return sb.toString();
    }

    @Test
    @IR(failOn = {IRNode.ALLOC_OF, "StringBuilder", IRNode.CALL_OF_METHOD, "toString"})
    public static String fluid(String a) {
        return new StringBuilder().append("a").append(a).toString();
    }

    @Test
    @IR(failOn = {IRNode.ALLOC_OF, "StringBuilder", IRNode.CALL_OF_METHOD, "toString"})
    public static String nonFluid(String a) {
        final StringBuilder sb = new StringBuilder();
        sb.append("a");
        sb.append(a);
        return sb.toString();
    }

    @Test
    @IR(failOn = {IRNode.ALLOC_OF, "StringBuilder", IRNode.CALL_OF_METHOD, "toString"})
    public static String nonFinal(String a) {
        StringBuilder sb = new StringBuilder();
        sb.append("a");
        sb.append(a);
        return sb.toString();
    }

    @Test
    @IR(failOn = {IRNode.ALLOC_OF, "StringBuilder", IRNode.CALL_OF_METHOD, "toString"})
    public static String nonFluidExtraneousVariable(String a) {
        final StringBuilder sb = new StringBuilder();
        final StringBuilder x = sb;
        sb.append("a");
        x.append(a);
        return sb.toString();
    }

    @Test
    @IR(counts = {IRNode.ALLOC_OF, "StringBuilder", "1", IRNode.CALL_OF_METHOD, "toString", "1"})
    @IR(failOn = IRNode.INTRINSIC_TRAP)
    static String nonFluidConditional(String a) {
        final StringBuilder sb = new StringBuilder();
        sb.append("a");
        if (unknown == 1) {
            sb.append(a);
        }
        return sb.toString();
    }

    @Test
    @IR(counts = {IRNode.ALLOC_OF, "StringBuilder", "1", IRNode.CALL_OF_METHOD, "toString", "1"})
    @IR(failOn = IRNode.INTRINSIC_TRAP)
    static String nonFluidOpaqueCall() {
        final StringBuilder sb = new StringBuilder();
        sb.append("a");
        opaque(sb);
        return sb.toString();
    }

}
