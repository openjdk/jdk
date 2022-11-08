/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8059632
 * @summary Method reference compilation uses incorrect qualifying type
 * @compile MethodRefQualifyingTypeTest.java
 * @compile MethodSupplierImpl.java
 * @run main MethodRefQualifyingTypeTest
 */

import java.lang.reflect.Method;

public class MethodRefQualifyingTypeTest {

    static abstract class MethodSupplierImpl implements MethodSupplier {
        class Inner {
            MethodRefQualifyingTypeTest.MyFunctionalInterface createMethodReference() {
                return MethodSupplierImpl.this::<Integer>m;
            }
        }
    }

    interface MethodSupplier {
        int m(int a);
    }

    interface MyFunctionalInterface {
        int invokeMethodReference(int a);
    }

    static class MethodInvoker {
        public static void invoke() {
            MyFunctionalInterface instance = null;
            MethodSupplierImpl ms = new MethodSupplierImpl() {
                    public int m(int a) {
                        return a;
                    }
                };
            instance = ms.new Inner().createMethodReference();
            instance.invokeMethodReference(1);
        }
    }

    public static void main(String argv[]) throws Exception {

        // Without the fix for JDK-8059632, the invocation below will fail with
        // java.lang.invoke.LambdaConversionException: Invalid receiver type class MethodRefQualifyingTypeTest$MethodSupplierImpl; not a subtype of implementation type interface MethodRefQualifyingTypeTest$MethodSupplier

        // With the fix for JDK-8059632, the invocation would succeed since the bootstrap section
        // would refer to the type of the receiver and not the type of the declaring interface,
        // per JLS 13.1 (see "the qualifying type of the method invocation").

        Class.forName("MethodRefQualifyingTypeTest$MethodInvoker").getMethod("invoke").invoke(null);
    }
}
