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
 * @summary Test that C2 and bytecode escape analysis (BCEA) together compute
 *          the correct escape state for fields of value objects when these are
 *          passed as scalar arguments.
 * @library /test/lib /
 * @enablePreview
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

    static IdentityObject globalObj = null;
    static ValueHolder globalHolder = null;

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
    static void dontUse(ValueHolder holder) {}

    @DontInline
    static void dontUse(ValueHolderHolder holder) {}

    @DontInline
    static IdentityObject useButDontEscape(ValueHolder holder) {
        return holder.obj;
    }

    @DontInline
    static void escapeField(ValueHolder holder) {
        globalObj = holder.obj;
    }

    @DontInline
    static void escapeField(ValueHolderHolder holderHolder) {
        globalObj = holderHolder.holder.obj;
    }

    @DontInline
    static void escape(ValueHolder holder) {
        globalHolder = holder;
    }

    // BCEA classifies the holder as a "local" object, which guarantees that
    // none of its fields are accessed within the non-inlined callee. C2 should
    // classify the IdentityObject field as not globally escaping and optimize
    // away the synchronization operations.
    @Test
    @IR(failOn = {IRNode.FAST_LOCK, IRNode.FAST_UNLOCK})
    static void testPassLocalValue() {
        ValueHolder holder = createValueHolderWithSynchronizedFieldWrite();
        dontUse(holder);
    }

    // Variant of the above test using a nested value object. The same outcome
    // is expected.
    @Test
    @IR(failOn = {IRNode.FAST_LOCK, IRNode.FAST_UNLOCK})
    static void testPassLocalNestedValue() {
        ValueHolder holder = createValueHolderWithSynchronizedFieldWrite();
        ValueHolderHolder holderHolder = new ValueHolderHolder(holder);
        dontUse(holderHolder);
    }

    // BCEA classifies the holder as a stack-allocatable object, but does not
    // provide any guarantee about the escape state of its IdentityObject field,
    // so C2 should classify the field as globally escaping, and preserve the
    // synchronization operations.
    // In this case, the field does not escape globally, but C2 acts
    // conservatively in the absence of more precise BCEA information.
    @Test
    @IR(counts = {IRNode.FAST_LOCK, "> 0", IRNode.FAST_UNLOCK, "> 0"})
    static void testPassStackValue() {
        ValueHolder holder = createValueHolderWithSynchronizedFieldWrite();
        useButDontEscape(holder);
    }

    // BCEA reports the same result as in the above test for the holder: the
    // holder itself is stack-allocatable, but nothing is known about the escape
    // state of its fields. C2 should classify the field as globally escaping,
    // and preserve the synchronization operations.
    // In this case, this turns out to be critical, because the field does
    // escape within the non-inlined callee.
    @Test
    @IR(counts = {IRNode.FAST_LOCK, "> 0", IRNode.FAST_UNLOCK, "> 0"})
    static void testPassStackValueWithEscapingField() {
        ValueHolder holder = createValueHolderWithSynchronizedFieldWrite();
        escapeField(holder);
    }

    // Variant of the above test using a nested value object. The same outcome
    // is expected.
    @Test
    @IR(counts = {IRNode.FAST_LOCK, "> 0", IRNode.FAST_UNLOCK, "> 0"})
    static void testPassStackNestedValueWithEscapingField() {
        ValueHolder holder = createValueHolderWithSynchronizedFieldWrite();
        ValueHolderHolder holderHolder = new ValueHolderHolder(holder);
        escapeField(holderHolder);
    }

    // BCEA classifies the holder as a globally escaping object, so C2 should
    // classify its field as globally escaping, and preserve the synchronization
    // operations.
    @Test
    @IR(counts = {IRNode.FAST_LOCK, "> 0", IRNode.FAST_UNLOCK, "> 0"})
    static void testPassEscapingValue() {
        ValueHolder holder = createValueHolderWithSynchronizedFieldWrite();
        escape(holder);
    }
}
