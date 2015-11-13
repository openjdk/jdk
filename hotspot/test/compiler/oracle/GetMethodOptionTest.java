/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Executable;
import java.util.function.BiFunction;

import jdk.test.lib.Asserts;
import sun.hotspot.WhiteBox;

/*
 * @test
 * @bug 8074980
 * @library /testlibrary /test/lib
 * @build sun.hotspot.WhiteBox jdk.test.lib.Asserts GetMethodOptionTest
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:CompileCommand=option,GetMethodOptionTest::test,ccstrlist,MyListOption,_foo,_bar
 *                   -XX:CompileCommand=option,GetMethodOptionTest::test,ccstr,MyStrOption,_foo
 *                   -XX:CompileCommand=option,GetMethodOptionTest::test,bool,MyBoolOption,false
 *                   -XX:CompileCommand=option,GetMethodOptionTest::test,intx,MyIntxOption,-1
 *                   -XX:CompileCommand=option,GetMethodOptionTest::test,uintx,MyUintxOption,1
 *                   -XX:CompileCommand=option,GetMethodOptionTest::test,MyFlag
 *                   -XX:CompileCommand=option,GetMethodOptionTest::test,double,MyDoubleOption1,1.123
 *                   -XX:CompileCommand=option,GetMethodOptionTest.test,double,MyDoubleOption2,1.123
 *                   -XX:CompileCommand=option,GetMethodOptionTest::test,bool,MyBoolOptionX,false,intx,MyIntxOptionX,-1,uintx,MyUintxOptionX,1,MyFlagX,double,MyDoubleOptionX,1.123
 *                   GetMethodOptionTest
 */

public class GetMethodOptionTest {
    private static final  WhiteBox WB = WhiteBox.getWhiteBox();
    public static void main(String[] args) {
        Executable test = getMethod("test");
        Executable test2 = getMethod("test2");
        BiFunction<Executable, String, Object> getter = WB::getMethodOption;
        for (TestCase testCase : TestCase.values()) {
            Object expected = testCase.value;
            String name = testCase.name();
            Asserts.assertEQ(expected, getter.apply(test, name),
                    testCase + ": universal getter returns wrong value");
            Asserts.assertEQ(expected, testCase.getter.apply(test, name),
                    testCase + ": specific getter returns wrong value");
            Asserts.assertEQ(null, getter.apply(test2, name),
                    testCase + ": universal getter returns value for unused method");
            Asserts.assertEQ(null, testCase.getter.apply(test2, name),
                    testCase + ": type specific getter returns value for unused method");
        }
    }
    private static void test() { }
    private static void test2() { }

    private static enum TestCase {
        MyListOption("_foo _bar", WB::getMethodStringOption),
        MyStrOption("_foo", WB::getMethodStringOption),
        MyBoolOption(false, WB::getMethodBooleanOption),
        MyIntxOption(-1L, WB::getMethodIntxOption),
        MyUintxOption(1L, WB::getMethodUintxOption),
        MyFlag(true, WB::getMethodBooleanOption),
        MyDoubleOption1(1.123d, WB::getMethodDoubleOption),
        MyDoubleOption2(1.123d, WB::getMethodDoubleOption),
        MyBoolOptionX(false, WB::getMethodBooleanOption),
        MyIntxOptionX(-1L, WB::getMethodIntxOption),
        MyUintxOptionX(1L, WB::getMethodUintxOption),
        MyFlagX(true, WB::getMethodBooleanOption),
        MyDoubleOptionX(1.123d, WB::getMethodDoubleOption);

        public final Object value;
        public final BiFunction<Executable, String, Object> getter;
        private TestCase(Object value, BiFunction<Executable, String, Object> getter) {
            this.value = value;
            this.getter = getter;
        }
    }

    private static Executable getMethod(String name) {
        Executable result;
        try {
            result = GetMethodOptionTest.class.getDeclaredMethod(name);
        } catch (NoSuchMethodException | SecurityException e) {
            throw new Error("TESTBUG : can't get method " + name, e);
        }
        return result;
    }
}
