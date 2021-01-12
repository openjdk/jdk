/*
 * Copyright (c) 2004, 2021 Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Field;

import common.TestMe;

class RunEnclosingClassTest {
    static void info(Class<?> c, Class<?> encClass, String desc) {
        if (!"".equals(desc))
            System.out.println(desc + ":");
        System.out.println(c);
        System.out.println("\tis enclosed by:\t\t" + encClass);
        System.out.println("\thas simple name:\t`" +
                c.getSimpleName() + "'");
        System.out.println("\thas canonical name:\t`" +
                c.getCanonicalName() + "'");
    }

    static void match(String actual, String expected) {
        assert((actual == null && expected == null) || actual.equals(expected));
        System.out.println("\t`" + actual + "' matches expected `" + expected + "'");
    }

    static void check(Class<?> c, Class<?> enc,
                      String encName, String encNameExpected,
                      String simpleName, String simpleNameExpected,
                      String canonicalName, String canonicalNameExpected) {
        match(encName, encNameExpected);
        match(simpleName, simpleNameExpected);
        match(canonicalName, canonicalNameExpected);
    }

    static void testClass(Class<?> c, TestMe annotation, Field f) {
        if (Void.class.equals(c))
            return;
        Class<?> encClass = c.getEnclosingClass();
        c.getEnclosingMethod(); // make sure it does not crash
        c.getEnclosingConstructor(); // make sure it does not crash
        info(c, encClass, annotation.desc());
        check(c, encClass,
                ""+encClass, annotation.encl(),
                c.getSimpleName(), annotation.simple(),
                c.getCanonicalName(),
                annotation.hasCanonical() ? annotation.canonical() : null);
        if (void.class.equals(c))
            return;
        Class<?> array = java.lang.reflect.Array.newInstance(c, 0).getClass();
        check(array, array.getEnclosingClass(),
                "", "",
                array.getSimpleName(), annotation.simple() + "[]",
                array.getCanonicalName(),
                annotation.hasCanonical() ? annotation.canonical() + "[]" : null);
    }

    static void test(final Object tests) {
        for (Field f : tests.getClass().getFields()) {
            TestMe annotation = f.getAnnotation(TestMe.class);
            if (annotation != null) {
                try {
                    testClass((Class<?>)f.get(tests), annotation, f);
                } catch (AssertionError ex) {
                    System.err.println("Error in " + tests.getClass().getName() + "." + f.getName());
                    throw ex;
                } catch (IllegalAccessException ex) {
                    ex.printStackTrace();
                    throw new RuntimeException(ex);
                }
            }
        }
    }
    public static void main(final String[] args) {
        test(new EnclosingClass());
        test(new pkg1.EnclosingClass());
        test(new pkg1.pkg2.EnclosingClass());
    }
}
