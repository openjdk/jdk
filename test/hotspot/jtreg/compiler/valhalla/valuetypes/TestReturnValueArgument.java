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
 * @bug 8392337
 * @summary TBD
 * @library /test/lib /
 * @enablePreview
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64" | os.simpleArch == "riscv64")
 * @run driver ${test.main.class}
 */

class TestReturnValueArgument {

    public static void main(String[] args) {
        TestFramework framework = new TestFramework();
        Scenario[] scenarios = new Scenario[2*2];
        int scenarioIndex = 0;
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                scenarios[scenarioIndex] =
                    new Scenario(scenarioIndex,
                                 "--enable-preview",
                                 "-XX:" + (i == 0 ? "-" : "+") + "ValueTypePassFieldsAsArgs",
                                 "-XX:" + (j == 0 ? "-" : "+") + "ValueTypeReturnedAsFields");
                scenarioIndex++;
            }
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

    static IdentityObject globalIdObj = null;
    static Object globalObj = null;

    @DontInline
    static ValueHolder returnArgument(ValueHolder holder) {
        return holder;
    }

    @DontInline
    static Object returnAsObject(ValueHolder holder) {
        return holder;
    }

    // Return holder passed as argument. Because the IdentityObject held by it
    // is then stored globally, its synchronization should not be removed.
    // Depending on the values of the ValueTypePassFieldsAsArgs and
    // ValueTypeReturnedAsFields flags, the holder is scalarized as argument
    // and/or return of the returnArgument() call.
    @Test
    @Arguments(values = {Argument.NUMBER_42})
    @IR(counts = {IRNode.FAST_LOCK, "> 0",
                  IRNode.FAST_UNLOCK, "> 0"})
    static void testReturnHolderAndEscapeIdentityObject(int val) {
        IdentityObject obj = new IdentityObject();
        synchronized (obj) {
            obj.val = val;
        }
        ValueHolder holder = new ValueHolder(obj);
        ValueHolder returnedHolder = returnArgument(holder);
        globalIdObj = returnedHolder.obj;
    }

    // Return holder cast as an Object, which naturally inhibits scalarization
    // of the returned value. Because the holder is then stored globally, the
    // IdentityObject it points to escapes, and its synchronization should not
    // be removed. Depending on the value of the ValueTypePassFieldsAsArgs flag,
    // the holder is scalarized as argument of the returnAsObject() call.
    @Test
    @Arguments(values = {Argument.NUMBER_42})
    @IR(counts = {IRNode.FAST_LOCK, "> 0",
                  IRNode.FAST_UNLOCK, "> 0"})
    static void testReturnHolderAsObjectAndEscapeHolder(int val) {
        IdentityObject obj = new IdentityObject();
        synchronized (obj) {
            obj.val = val;
        }
        ValueHolder holder = new ValueHolder(obj);
        Object holderAsObject = returnAsObject(holder);
        globalObj = holderAsObject;
    }
}
