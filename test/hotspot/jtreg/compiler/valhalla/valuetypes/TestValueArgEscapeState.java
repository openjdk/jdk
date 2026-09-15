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

package compiler.valhalla.valuetypes;

import compiler.lib.ir_framework.*;

/**
 * @test
 * @bug 8392283
 * @summary Test that C2 and the bytecode escape analyzer together compute the
 *          correct escape state for fields of value objects when these are
 *          passed as scalar arguments.
 * @library /test/lib /
 * @enablePreview
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64" | os.simpleArch == "riscv64")
 * @run driver ${test.main.class}
 */

class TestValueArgEscapeState {

    public static void main(String[] args) {
        TestFramework framework = new TestFramework();
        Scenario[] scenarios = new Scenario[2];
        for (int i = 0; i < 2; i++) {
            scenarios[i] =
                new Scenario(i, "--enable-preview",
                                "-XX:" + (i == 0 ? "-" : "+") + "ValueTypePassFieldsAsArgs");
        }
        framework.addScenarios(scenarios);
        framework.start();
    }

    static class IdentityObject {
        int val;
    }

    static value class ValueHolder {
        IdentityObject obj;
        ValueHolder(IdentityObject obj) {
            this.obj = obj;
        }
    }

    static value class ValueHolderHolder {
        ValueHolder holder;
        ValueHolderHolder(ValueHolder holder) {
            this.holder = holder;
        }
    }

    static IdentityObject global = null;

    @DontInline
    static void escape(ValueHolder holder) {
        global = holder.obj;
    }

    @DontInline
    static void escape(ValueHolderHolder holderHolder) {
        global = holderHolder.holder.obj;
    }

    @DontInline
    static void dontUse(ValueHolder holder) {}

    @DontInline
    static ValueHolder justReturn(ValueHolder holder) {
        return holder;
    }

    // The IdentityObject allocation referenced by holder.obj escapes within
    // escape(), so C2 should classify it as globally escaping, and preserve the
    // lock and unlock operations implementing the synchronized block.
    @Test
    @Arguments(values = {Argument.NUMBER_42})
    @IR(counts = {IRNode.FAST_LOCK, "> 0",
                  IRNode.FAST_UNLOCK, "> 0"})
    static void testEscapingScalarizedValue(int val) {
        IdentityObject obj = new IdentityObject();
        ValueHolder holder = new ValueHolder(obj);
        synchronized (obj) {
            obj.val = val;
        }
        escape(holder);
    }

    @Test
    @Arguments(values = {Argument.NUMBER_42})
    @IR(counts = {IRNode.FAST_LOCK, "> 0",
                  IRNode.FAST_UNLOCK, "> 0"})
    static void testEscapingNestedScalarizedValue(int val) {
        IdentityObject obj = new IdentityObject();
        ValueHolder holder = new ValueHolder(obj);
        ValueHolderHolder holderHolder = new ValueHolderHolder(holder);
        synchronized (obj) {
            obj.val = val;
        }
        escape(holderHolder);
    }

    // The IdentityObject allocation referenced by holder.obj is unused by the
    // callee, so C2 should classify it as not globally escaping (exploiting the
    // bytecode escape analyzer's ability to detect the argument as "local"),
    // and optimize away the lock and unlock operations implementing the
    // synchronized block.
    @Test
    @Arguments(values = {Argument.NUMBER_42})
    @IR(failOn = {IRNode.FAST_LOCK, IRNode.FAST_UNLOCK})
    static void testUnusedScalarizedValue(int val) {
        IdentityObject obj = new IdentityObject();
        ValueHolder holder = new ValueHolder(obj);
        synchronized (obj) {
            obj.val = val;
        }
        dontUse(holder);
    }

    // The IdentityObject allocation referenced by holder.obj is just returned
    // by the callee, so C2 should classify it as not globally escaping
    // (exploiting the bytecode escape analyzer's ability to detect the argument
    // as "local" and "returned"), and optimize away the lock and unlock
    // operations implementing the synchronized block.
    @Test
    @Arguments(values = {Argument.NUMBER_42})
    @IR(failOn = {IRNode.FAST_LOCK, IRNode.FAST_UNLOCK})
    static void testJustReturnedScalarizedValue(int val) {
        IdentityObject obj = new IdentityObject();
        ValueHolder holder = new ValueHolder(obj);
        synchronized (obj) {
            obj.val = val;
        }
        justReturn(holder);
    }
}
