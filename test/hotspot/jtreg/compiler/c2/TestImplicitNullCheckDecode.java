/*
 * Copyright (c) 2026, Alibaba Group Holding Limited. All rights reserved.
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

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;

/**
 * @test
 * @summary Verify C2 implicit null checks on field loads and stores across
 *          compressed and uncompressed oop modes.
 * @library /test/lib /
 * @requires vm.bits == "64" & vm.compiler2.enabled
 * @requires os.family != "aix"
 * @run driver compiler.c2.TestImplicitNullCheckDecode
 */
// AIX is excluded because a load through null does not fault on its readable
// zero page (see os::zero_page_read_protected()).
public class TestImplicitNullCheckDecode {

    static class Node {
        Node next;
        int f;
        int g;
    }

    public static void main(String[] args) {
        TestFramework framework = new TestFramework();
        framework.addScenarios(
                // Uncompressed oops: the checked value is a full pointer.
                new Scenario(0, "-XX:-UseCompressedOops"),
                // A small heap is encoded zero based, so no heap base has to
                // be added when the oop is decoded.
                new Scenario(1, "-Xms64m", "-Xmx128m", "-XX:+UseCompressedOops"),
                // HeapBaseMinAddress asks for a heap above the zero-based
                // range. If the VM still maps the heap zero based, no heap base
                // is added; otherwise the port decides whether a scaled decode
                // folds into the address, and where it does not the reference
                // is decoded separately.
                new Scenario(2, "-XX:HeapBaseMinAddress=64g",
                             "-Xms128m", "-Xmx128m", "-XX:+UseCompressedOops"),
                // A shift of 4 rules out a scaled fold into the address on all
                // ports, so the access always uses a separately decoded
                // reference.
                new Scenario(3, "-XX:ObjectAlignmentInBytes=16",
                             "-XX:HeapBaseMinAddress=64g",
                             "-Xms128m", "-Xmx128m", "-XX:+UseCompressedOops"),
                // The same scenario with implicit null checks disabled: the
                // checks must stay explicit.
                new Scenario(4, "-XX:-ImplicitNullChecks",
                             "-XX:ObjectAlignmentInBytes=16",
                             "-XX:HeapBaseMinAddress=64g",
                             "-Xms128m", "-Xmx128m", "-XX:+UseCompressedOops"));
        framework.start();
    }

    // With implicit null checks enabled, the load of n.next checks n and the
    // load of f checks n.next. Both checks are implicit.
    @Test
    @IR(applyIf = {"ImplicitNullChecks", "true"},
        counts = {IRNode.NULL_CHECK, "2"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIf = {"ImplicitNullChecks", "false"},
        counts = {IRNode.NULL_CHECK, "0"},
        phase = CompilePhase.FINAL_CODE)
    static int loadNext(Node n) {
        return n.next.f;
    }

    @Test
    @IR(applyIf = {"ImplicitNullChecks", "true"},
        counts = {IRNode.NULL_CHECK, "2"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIf = {"ImplicitNullChecks", "false"},
        counts = {IRNode.NULL_CHECK, "0"},
        phase = CompilePhase.FINAL_CODE)
    static void storeNext(Node n, int v) {
        n.next.f = v;
    }

    static volatile int consumeCount;

    @DontInline
    static void consume(Node n) {
        consumeCount++;
    }

    // The decoded n.next is used by the access and by the call. Only one
    // decode is needed, and the load can still serve as the null check.
    @Test
    @IR(applyIf = {"ImplicitNullChecks", "true"},
        counts = {IRNode.NULL_CHECK, "2"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIf = {"ImplicitNullChecks", "false"},
        counts = {IRNode.NULL_CHECK, "0"},
        phase = CompilePhase.FINAL_CODE)
    static int loadThenCall(Node n) {
        Node m = n.next;
        int x = m.f;
        consume(m);
        return x;
    }

    // Here the call needs the decoded reference before the access does, so the
    // decode is already available when the load is hoisted to the check.
    @Test
    @IR(applyIf = {"ImplicitNullChecks", "true"},
        counts = {IRNode.NULL_CHECK, "2"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIf = {"ImplicitNullChecks", "false"},
        counts = {IRNode.NULL_CHECK, "0"},
        phase = CompilePhase.FINAL_CODE)
    static int callThenLoad(Node n) {
        Node m = n.next;
        consume(m);
        return m.f;
    }

    // Two loads use the same decoded reference, so there are two candidates
    // for the null check of n.next; either can be chosen.
    @Test
    @IR(applyIf = {"ImplicitNullChecks", "true"},
        counts = {IRNode.NULL_CHECK, "2"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIf = {"ImplicitNullChecks", "false"},
        counts = {IRNode.NULL_CHECK, "0"},
        phase = CompilePhase.FINAL_CODE)
    static int twoAccesses(Node n) {
        Node m = n.next;
        return m.f + m.g;
    }

    @Run(test = {"loadNext", "storeNext", "loadThenCall", "callThenLoad", "twoAccesses"})
    static void run(RunInfo info) {
        Node n = new Node();
        if (info.isWarmUp()) {
            // Keep the null path unprofiled until after C2 compilation.
            n.next = new Node();
            loadNext(n);
            storeNext(n, 1);
            loadThenCall(n);
            callThenLoad(n);
            twoAccesses(n);
            return;
        }
        // n is non-null, but n.next is null.
        Asserts.assertThrows(NullPointerException.class, () -> loadNext(n));
        Asserts.assertThrows(NullPointerException.class, () -> storeNext(n, 1));
        consumeCount = 0;
        Asserts.assertThrows(NullPointerException.class, () -> loadThenCall(n));
        Asserts.assertEQ(consumeCount, 0, "The load must throw before consume is called");
        Asserts.assertThrows(NullPointerException.class, () -> callThenLoad(n));
        Asserts.assertEQ(consumeCount, 1, "consume must run before the load throws");
        Asserts.assertThrows(NullPointerException.class, () -> twoAccesses(n));
    }
}
