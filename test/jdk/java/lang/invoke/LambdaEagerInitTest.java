/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8242451
 * @summary Test that the LAMBDA_INSTANCE$ field is present depending on disableEagerInitialization
 * @compile LambdaEagerInitTest.java
 * @run main LambdaEagerInitTest
 * @run main/othervm -Djdk.internal.lambda.disableEagerInitialization=true LambdaEagerInitTest
 */

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

public class LambdaEagerInitTest {

    interface H {Object m(String s);}

    private static Set<String> allowedStaticFields() {
        Set<String> s = new HashSet<>();
        if (Boolean.getBoolean("jdk.internal.lambda.disableEagerInitialization")) {
            s.add("LAMBDA_INSTANCE$");
        }
        return s;
    }

    private void test1() {
        H la = s -> s;
        assert "hi".equals(la.m("hi"));
        Class<? extends H> c1 = la.getClass();
        Set<String> staticFields = new HashSet<>();
        Set<String> instanceFields = new HashSet<>();
        for (Field f : c1.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) {
                staticFields.add(f.getName());
            } else {
                instanceFields.add(f.getName());
            }
        }
        assert instanceFields.isEmpty() : "Unexpected instance fields: " + instanceFields;
        assert staticFields.equals(allowedStaticFields()) :
                "Unexpected static fields. Got " + instanceFields + ", expected " + allowedStaticFields();
    }


    public static void main(String[] args) {
        LambdaEagerInitTest test = new LambdaEagerInitTest();
        test.test1();
    }
}
