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
 * @summary Test that C2 propagates escape state correctly for returned
 *          arguments in non-inlined calls for different scalarization choices.
 * @library /test/lib /
 * @enablePreview
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

    static IdentityObject globalObj = null;
    static Object globalPlainObj = null;

    @ForceInline
    static ValueHolder createValueHolderWithSynchronizedFieldWrite() {
        IdentityObject obj = new IdentityObject();
        ValueHolder holder = new ValueHolder(obj);
        synchronized (obj) {
            obj.val = 42;
        }
        return holder;
    }

    @DontInline
    static ValueHolder returnArgument(ValueHolder holder) {
        return holder;
    }

    @DontInline
    static Object returnAsObject(ValueHolder holder) {
        return holder;
    }

    static class BaseReturner {
        native ValueHolder returnArgument(ValueHolder holder);
    }

    static final class Returner extends BaseReturner {
        @DontInline
        @Override
        ValueHolder returnArgument(ValueHolder holder) {
            return holder;
        }
    }

    // Return holder passed as argument. Because the IdentityObject held by it
    // is then stored globally, its synchronization should not be removed.
    // Depending on the values of the ValueTypePassFieldsAsArgs and
    // ValueTypeReturnedAsFields flags, the holder is scalarized as argument
    // and/or return of the returnArgument() call.
    @Test
    @IR(counts = {IRNode.FAST_LOCK, "> 0", IRNode.FAST_UNLOCK, "> 0"})
    static void testReturnHolderAndEscapeIdentityObject() {
        ValueHolder holder = createValueHolderWithSynchronizedFieldWrite();
        ValueHolder returnedHolder = returnArgument(holder);
        globalObj = returnedHolder.obj;
    }

    // Return holder as an Object, which naturally inhibits scalarization of the
    // returned value. Because the holder is then stored globally, the
    // IdentityObject it points to escapes, and its synchronization should not
    // be removed. Depending on the value of the ValueTypePassFieldsAsArgs flag,
    // the holder is scalarized as argument of the returnAsObject() call.
    @Test
    @IR(counts = {IRNode.FAST_LOCK, "> 0", IRNode.FAST_UNLOCK, "> 0"})
    static void testReturnHolderAsObjectAndEscapeHolder() {
        ValueHolder holder = createValueHolderWithSynchronizedFieldWrite();
        Object holderAsObject = returnAsObject(holder);
        globalPlainObj = holderAsObject;
    }

    // Return holder via a non-static override of a native method, which
    // naturally inhibits scalarization of the holder as argument, but allows it
    // to be scalarized on return. Because the IdentityObject held by it is then
    // stored globally, its synchronization should not be removed.
    @Test
    @IR(counts = {IRNode.FAST_LOCK, "> 0", IRNode.FAST_UNLOCK, "> 0"})
    static void testReturnHolderViaNativeSuperclassAndEscapeIdentityObject() {
        ValueHolder holder = createValueHolderWithSynchronizedFieldWrite();
        ValueHolder returnedHolder = new Returner().returnArgument(holder);
        globalObj = returnedHolder.obj;
    }

    @Test
    // Return holder passed as argument, do not force a global escape. In the
    // cases when the holder is passed and returned in the same form (both
    // scalarized or both buffered), we expect C2 to exploit the bytecode escape
    // analyzer's ability to detect the argument as "local" and "returned" and
    // optimize away the lock and unlock operations implementing the
    // synchronized block.
    // Currently C2 does not exploit bytecode escape analyzer information for
    // returned arguments with mismatched buffer/scalar representations.
    @IR(applyIfAnd = {"ValueTypePassFieldsAsArgs", "true", "ValueTypeReturnedAsFields", "true"},
        failOn = {IRNode.FAST_LOCK, IRNode.FAST_UNLOCK})
    @IR(applyIfAnd = {"ValueTypePassFieldsAsArgs", "false", "ValueTypeReturnedAsFields", "false"},
        failOn = {IRNode.FAST_LOCK, IRNode.FAST_UNLOCK})
    static void testReturnHolderWithoutEscape() {
        ValueHolder holder = createValueHolderWithSynchronizedFieldWrite();
        returnArgument(holder);
    }

}
