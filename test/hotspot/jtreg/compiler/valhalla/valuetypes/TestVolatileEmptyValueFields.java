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

import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;
import jdk.internal.vm.annotation.NullRestricted;

/*
 * @test
 * @bug 8397215
 * @summary Empty value fields must retain volatile accesses and memory barriers
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.vm.annotation
 * @run driver ${test.main.class}
 */
public class TestVolatileEmptyValueFields {

    public static void main(String[] args) {
        new TestFramework().addFlags("--enable-preview",
                                     "--add-exports", "java.base/jdk.internal.vm.annotation=ALL-UNNAMED")
                           .start();
    }

    static value class Empty { }
    static final Empty EMPTY = new Empty();
    static final TestVolatileEmptyValueFields INSTANCE = new TestVolatileEmptyValueFields();

    @NullRestricted
    volatile Empty instanceField;

    @NullRestricted
    static volatile Empty staticField = new Empty();

    TestVolatileEmptyValueFields() {
        instanceField = new Empty();
        super();
    }

    @Test
    @IR(counts = {IRNode.LOAD_OF_FIELD, "instanceField", "1",
                  IRNode.MEMBAR_ACQUIRE, "1"})
    static Object testInstanceLoad() {
        return INSTANCE.instanceField;
    }

    @Test
    @IR(counts = {IRNode.LOAD_OF_FIELD, "staticField", "1",
                  IRNode.MEMBAR_ACQUIRE, "1"})
    static Object testStaticLoad() {
        return staticField;
    }

    @Test
    @IR(counts = {IRNode.STORE_OF_FIELD, "instanceField", "1",
                  IRNode.MEMBAR_RELEASE, "1"})
    static void testInstanceStore() {
        INSTANCE.instanceField = EMPTY;
    }

    @Test
    @IR(counts = {IRNode.STORE_OF_FIELD, "staticField", "1",
                  IRNode.MEMBAR_RELEASE, "1"})
    static void testStaticStore() {
        staticField = EMPTY;
    }
}

