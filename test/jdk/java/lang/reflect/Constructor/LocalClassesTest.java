/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8320575
 * @summary reflection test for local classes constructors
 * @modules jdk.compiler/com.sun.tools.javac.util
 */

import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.List;

import com.sun.tools.javac.util.Assert;

public class LocalClassesTest {
    class LocalTest1 {
        void test() {
            class Local {
                public Local(List<String> ls) {}
                void t() {
                    System.err.println(LocalTest1.this);
                }
                void doTest() {
                    System.out.println("invoking test now");
                    try {
                        LocalClassesTest.this.testReflection(
                                this,
                                new String[]{"class LocalClassesTest$LocalTest1", "java.util.List<java.lang.String>"},
                                new String[]{"java.util.List<java.lang.String>"}
                        );
                    } catch (Throwable t) {
                        throw new AssertionError("test failure: " + t.getMessage());
                    }
                }
            }
            Local local = new Local(List.of("1"));
            local.doTest();
            var anonymous = new Local(List.of("1")) {
                @Override
                void doTest() {
                    System.out.println("invoking test now");
                    try {
                        LocalClassesTest.this.testReflection(
                                this,
                                // there is no generic information generated for anonymous classes
                                new String[]{"class LocalClassesTest$LocalTest1", "interface java.util.List"},
                                new String[]{"class LocalClassesTest$LocalTest1", "interface java.util.List"}
                        );
                    } catch (Throwable t) {
                        throw new AssertionError("test failure: " + t.getMessage());
                    }
                }
            };
            anonymous.doTest();
        }
    }

    public static void main(String... args) {
        LocalClassesTest testClass = new LocalClassesTest();
        LocalTest1 localTest1 = testClass.new LocalTest1();
        localTest1.test();
    }

    public void testReflection(Object object,
                               String[] allParamTypes,
                               String[] genericParamTypes)
            throws ReflectiveOperationException
    {
        System.out.println("at testReflection");
        Class<?> klass = object.getClass();
        int i = 0;
        // let's check constructors
        var constructor = klass.getDeclaredConstructors()[0];
        i = 0;
        for (var p: constructor.getParameters()) {
            System.out.println("got parameter " + p.getParameterizedType().toString());
            Assert.check(p.getParameterizedType().toString().equals(allParamTypes[i]),
                    String.format("signature of method \"%s\" different from expected signature \"%s\"",
                            p.getType().toString(), allParamTypes[i]));
            i++;
        }
        // similar as above but testing another API, only for generic params
        i = 0;
        for (var t : constructor.getGenericParameterTypes()) {
            System.out.println("got generic parameter " + t.toString());
            Assert.check(t.toString().equals(genericParamTypes[i]),
                    String.format("signature of method \"%s\" different from expected signature \"%s\"",
                            t.toString(), genericParamTypes[i]));
            i++;
        }
    }
}
