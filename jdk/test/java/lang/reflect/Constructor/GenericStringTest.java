/*
 * Copyright 2004-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
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
    public static void main(String argv[]) throws Exception{
        int failures = 0;
        List<Class<?>> classList = new LinkedList<Class<?>>();
        classList.add(TestClass1.class);
        classList.add(TestClass2.class);


        for(Class<?> clazz: classList)
            for(Constructor<?> ctor: clazz.getDeclaredConstructors()) {
                ExpectedGenericString egs = ctor.getAnnotation(ExpectedGenericString.class);
                String actual = ctor.toGenericString();
                System.out.println(actual);
                if (! egs.value().equals(actual)) {
                    failures++;
                    System.err.printf("ERROR: Expected generic string ''%s''; got ''%s''.\n",
                                      egs.value(), actual);
                }

                if (ctor.isAnnotationPresent(ExpectedString.class)) {
                    ExpectedString es = ctor.getAnnotation(ExpectedString.class);
                    String result = ctor.toString();
                    if (! es.value().equals(result)) {
                        failures++;
                        System.err.printf("ERROR: Expected ''%s''; got ''%s''.\n",
                                          es.value(), result);
                    }
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
   "TestClass1(int,double)")
    TestClass1(int x, double y) {}

    @ExpectedGenericString(
   "private TestClass1(int,int)")
    private TestClass1(int x, int param2) {}

    @ExpectedGenericString(
   "private TestClass1(java.lang.Object) throws java.lang.RuntimeException")
    private TestClass1(Object o) throws RuntimeException {}

    @ExpectedGenericString(
   "protected <S,T> TestClass1(S,T) throws java.lang.Exception")
    protected <S, T> TestClass1(S s, T t) throws Exception{}

    @ExpectedGenericString(
   "TestClass1(java.lang.Object...)")
    @ExpectedString(
   "TestClass1(java.lang.Object[])")
    TestClass1(Object... o){}
}

class TestClass2<E> {
    @ExpectedGenericString(
   "public <T> TestClass2(E,T)")
    public <T> TestClass2(E e, T t) {}
}

@Retention(RetentionPolicy.RUNTIME)
@interface ExpectedGenericString {
    String value();
}

@Retention(RetentionPolicy.RUNTIME)
@interface ExpectedString {
    String value();
}
