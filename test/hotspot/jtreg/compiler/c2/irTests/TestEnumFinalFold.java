/*
 * Copyright (c) 2022, Red Hat, Inc. All rights reserved.
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

import jdk.test.lib.Asserts;
import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8286190
 * @summary Verify constant folding for Enum fields
 * @library /test/lib /
 * @requires vm.compiler2.enabled
 * @run driver compiler.c2.irTests.TestEnumFinalFold
 */
public class TestEnumFinalFold {

    public static void main(String[] args) {
        TestFramework.run();
    }

    private enum MyEnum {
        VALUE1,
        VALUE2;
    }

    @Test
    @IR(failOn = {IRNode.ADD_I, IRNode.LOAD_I})
    public int testOrdinalSum() {
        return MyEnum.VALUE1.ordinal() + MyEnum.VALUE2.ordinal();
    }

    @Run(test = "testOrdinalSum")
    public void runOrdinalSum() {
        testOrdinalSum();
    }

    @Test
    @IR(failOn = {IRNode.ADD_I})
    public int testNameLengthSum() {
        return MyEnum.VALUE1.name().length() + MyEnum.VALUE2.name().length();
    }

    @Run(test = "testNameLengthSum")
    public void runNameLengthSum() {
        testNameLengthSum();
    }

}
