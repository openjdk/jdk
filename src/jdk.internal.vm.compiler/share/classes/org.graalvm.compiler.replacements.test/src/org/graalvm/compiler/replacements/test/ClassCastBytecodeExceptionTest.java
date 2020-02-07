/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements.test;

import java.util.ArrayList;
import java.util.Collection;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ClassCastBytecodeExceptionTest extends BytecodeExceptionTest {

    @Parameter(0) public Object object;
    @Parameter(1) public Class<?> cls;

    @Parameters(name = "{1}")
    public static Collection<Object[]> data() {
        Object[] objects = {"string", 42, new int[0], new Object[0], new double[0][]};

        ArrayList<Object[]> ret = new ArrayList<>(objects.length);
        for (Object o : objects) {
            ret.add(new Object[]{o, o.getClass()});
        }
        return ret;
    }

    public static void castToDouble(Object obj) {
        /*
         * We don't use cls.cast(obj) here because that gives a different exception message than the
         * checkcast bytecode.
         */
        if (Double.class == Double.class) {
            Double cast = (Double) obj;
            GraalDirectives.blackhole(cast);
        } else if ((Class<?>) Double.class == byte[].class) {
            byte[] cast = (byte[]) obj;
            GraalDirectives.blackhole(cast);
        } else if ((Class<?>) Double.class == String[].class) {
            String[] cast = (String[]) obj;
            GraalDirectives.blackhole(cast);
        } else if ((Class<?>) Double.class == Object[][].class) {
            Object[][] cast = (Object[][]) obj;
            GraalDirectives.blackhole(cast);
        } else {
            Assert.fail("unexpected class argument");
        }
    }

    @Test
    public void testCastToDouble() {
        test("castToDouble", object);
    }

    public static void castToByteArray(Object obj) {
        /*
         * We don't use cls.cast(obj) here because that gives a different exception message than the
         * checkcast bytecode.
         */
        if ((Class<?>) byte[].class == Double.class) {
            Double cast = (Double) obj;
            GraalDirectives.blackhole(cast);
        } else if (byte[].class == byte[].class) {
            byte[] cast = (byte[]) obj;
            GraalDirectives.blackhole(cast);
        } else if ((Class<?>) byte[].class == String[].class) {
            String[] cast = (String[]) obj;
            GraalDirectives.blackhole(cast);
        } else if ((Class<?>) byte[].class == Object[][].class) {
            Object[][] cast = (Object[][]) obj;
            GraalDirectives.blackhole(cast);
        } else {
            Assert.fail("unexpected class argument");
        }
    }

    @Test
    public void testCastToByteArray() {
        test("castToByteArray", object);
    }

    public static void castToStringArray(Object obj) {
        /*
         * We don't use cls.cast(obj) here because that gives a different exception message than the
         * checkcast bytecode.
         */
        if ((Class<?>) String[].class == Double.class) {
            Double cast = (Double) obj;
            GraalDirectives.blackhole(cast);
        } else if ((Class<?>) String[].class == byte[].class) {
            byte[] cast = (byte[]) obj;
            GraalDirectives.blackhole(cast);
        } else if (String[].class == String[].class) {
            String[] cast = (String[]) obj;
            GraalDirectives.blackhole(cast);
        } else if ((Class<?>) String[].class == Object[][].class) {
            Object[][] cast = (Object[][]) obj;
            GraalDirectives.blackhole(cast);
        } else {
            Assert.fail("unexpected class argument");
        }
    }

    @Test
    public void testCastToStringArray() {
        test("castToStringArray", object);
    }

    public static void castToArrayArray(Object obj) {
        /*
         * We don't use cls.cast(obj) here because that gives a different exception message than the
         * checkcast bytecode.
         */
        if ((Class<?>) Object[][].class == Double.class) {
            Double cast = (Double) obj;
            GraalDirectives.blackhole(cast);
        } else if ((Class<?>) Object[][].class == byte[].class) {
            byte[] cast = (byte[]) obj;
            GraalDirectives.blackhole(cast);
        } else if ((Class<?>) Object[][].class == String[].class) {
            String[] cast = (String[]) obj;
            GraalDirectives.blackhole(cast);
        } else if (Object[][].class == Object[][].class) {
            Object[][] cast = (Object[][]) obj;
            GraalDirectives.blackhole(cast);
        } else {
            Assert.fail("unexpected class argument");
        }
    }

    @Test
    public void testCastToArrayArray() {
        test("castToArrayArray", object);
    }
}
