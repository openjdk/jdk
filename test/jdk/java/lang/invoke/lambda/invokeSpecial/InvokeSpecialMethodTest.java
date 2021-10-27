/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8274848
 * @run main InvokeSpecialMethodTest
 * @summary ensure REF_invokeSpecial on a non-private implementation method
 *          behaves as if `super::m` is invoked regardless of its access flag
 */

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import static java.lang.invoke.MethodType.methodType;

public class InvokeSpecialMethodTest {
    static class Test {
        static final Lookup LOOKUP = MethodHandles.lookup();

        public String m_public() {
            return "test_public";
        }
        protected String m_protected() {
            return "test_protected";
        }
        private String m_private() {
            return "test_private";
        }

        public static class SubClass extends Test {
            public String m_public() {
                return "subclass_public";
            }
            public String m_protected() {
                return "subclass_protected";
            }
            public String m_private() {
                return "subclass_private";
            }
        }

        /*
         * findSpecial with Test class as the special caller matching
         * the factory type `StringFactory(Test)`
         */
        static MethodHandle mh(String name) {
            try {
                return LOOKUP.findSpecial(Test.class, name, methodType(String.class), Test.class);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        static final MethodHandle M_PUBLIC = mh("m_public");
        static final MethodHandle M_PROTECTED = mh("m_protected");
        static final MethodHandle M_PRIVATE = mh("m_private");
    }
    interface StringFactory {
        String get();
    }

    public static void main(String... args) throws Throwable {
        test(Test.M_PUBLIC, "test_public");
        test(Test.M_PROTECTED, "test_protected");
        test(Test.M_PRIVATE, "test_private");
    }

    static void test(MethodHandle implMethod, String expected) throws Throwable {
        testMetafactory(implMethod, expected);
        testAltMetafactory(implMethod, expected);
    }

    static void testMetafactory(MethodHandle implMethod, String expected) throws Throwable {
        CallSite cs = LambdaMetafactory.metafactory(Test.LOOKUP, "get",
                                                    methodType(StringFactory.class, Test.class),
                                                    methodType(String.class), implMethod, methodType(String.class));
        Test o = new Test.SubClass();
        StringFactory factory = (StringFactory) cs.dynamicInvoker().invokeExact(o);
        String actual = factory.get();
        if (!expected.equals(actual)) throw new AssertionError("Unexpected result: " + actual);
    }

    static void testAltMetafactory(MethodHandle implMethod, String expected) throws Throwable {
        CallSite cs = LambdaMetafactory.altMetafactory(Test.LOOKUP, "get",
                                                       methodType(StringFactory.class, Test.class),
                                                       methodType(String.class), implMethod, methodType(String.class),
                                                       LambdaMetafactory.FLAG_SERIALIZABLE);
        Test o = new Test.SubClass();
        StringFactory factory = (StringFactory) cs.dynamicInvoker().invokeExact(o);
        String actual = factory.get();
        if (!expected.equals(actual)) throw new AssertionError("Unexpected result: " + actual);
    }
}
