/*
 * Copyright (c) 2004, 2006, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 5033583 6316717 6470106
 * @summary Check toGenericString() and toString() methods
 * @author Joseph D. Darcy
 */

import java.lang.reflect.*;
import java.lang.annotation.*;
import java.util.*;

public class GenericStringTest {
    public static void main(String argv[]) throws Exception {
        int failures = 0;
        List<Class<?>> classList = new LinkedList<Class<?>>();
        classList.add(TestClass1.class);
        classList.add(TestClass2.class);
        classList.add(Roebling.class);


        for(Class<?> clazz: classList)
            for(Method method: clazz.getDeclaredMethods()) {
                ExpectedGenericString egs = method.getAnnotation(ExpectedGenericString.class);
                if (egs != null) {
                    String actual = method.toGenericString();
                    System.out.println(actual);
                    if (! egs.value().equals(actual)) {
                        failures++;
                        System.err.printf("ERROR: Expected ''%s''; got ''%s''.\n",
                                          egs.value(), actual);
                    }
                }

                if (method.isAnnotationPresent(ExpectedString.class)) {
                    ExpectedString es = method.getAnnotation(ExpectedString.class);
                    String actual = method.toString();
                    if (! es.value().equals(actual)) {
                        failures++;
                        System.err.printf("ERROR: Expected ''%s''; got ''%s''.\n",
                                          es.value(), actual);
                    }
                }

            }

        // Bridge Test; no "volatile" methods
        for(Method method: Roebling.class.getDeclaredMethods()) {
            String s1 = method.toGenericString();
            String s2 = method.toString();
            System.out.println("Generic: " + s1);
            System.out.println("Regular: " + s2);
            if (s1.indexOf("volatile") != -1 ||
                s2.indexOf("volatile") != -1) {
                failures++;
                System.err.println("ERROR: Bad string; unexpected  ``volatile''");
            }
        }

        if (failures > 0) {
            System.err.println("Test failed.");
            throw new RuntimeException();
        }
    }
}

class TestClass1 {
    @ExpectedGenericString(
   "void TestClass1.method1(int,double)")
    void method1(int x, double y) {}

    @ExpectedGenericString(
   "private static java.lang.String TestClass1.method2(int,int)")
    private static String method2(int x, int param2) {return null;}

    @ExpectedGenericString(
   "static void TestClass1.method3() throws java.lang.RuntimeException")
    static void method3() throws RuntimeException {return;}

    @ExpectedGenericString(
   "protected <S,T> S TestClass1.method4(S,T) throws java.lang.Exception")
    protected <S, T> S method4(S s, T t) throws Exception {return null;}
}

class TestClass2<E, F extends Exception> {
    @ExpectedGenericString(
   "public <T> T TestClass2.method1(E,T)")
    public <T> T method1(E e, T t) {return null;}

    @ExpectedGenericString(
   "public void TestClass2.method2() throws F")
    public void method2() throws F {return;}
}

class Roebling implements Comparable<Roebling> {
    @ExpectedGenericString(
   "public int Roebling.compareTo(Roebling)")
    public int compareTo(Roebling r) {
        throw new IllegalArgumentException();
    }

    // Not a transient method, (transient var-arg overlap)
    @ExpectedGenericString(
   "void Roebling.varArg(java.lang.Object...)")
    @ExpectedString(
   "void Roebling.varArg(java.lang.Object[])")
    void varArg(Object ... arg) {}
}

@Retention(RetentionPolicy.RUNTIME)
@interface ExpectedGenericString {
    String value();
}

@Retention(RetentionPolicy.RUNTIME)
@interface ExpectedString {
    String value();
}
