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

package compiler.jvmci.common.testcases;

public class MultipleImplementer2 implements MultipleImplementersInterface {

    private static int intStaticField = INT_CONSTANT;
    static long longStaticField = LONG_CONSTANT;
    static float floatStaticField = FLOAT_CONSTANT;
    static double doubleStaticField = DOUBLE_CONSTANT;
    public static String stringStaticField = STRING_CONSTANT;
    protected static Object objectStaticField = OBJECT_CONSTANT;

    public int intField = INT_CONSTANT;
    private long longField = LONG_CONSTANT;
    protected float floatField = FLOAT_CONSTANT;
    double doubleField = DOUBLE_CONSTANT;
    String stringField = STRING_CONSTANT;
    Object objectField = OBJECT_CONSTANT;

    public MultipleImplementer2() {
        intField = Integer.MAX_VALUE;
        longField = Long.MAX_VALUE;
        floatField = Float.MAX_VALUE;
        doubleField = Double.MAX_VALUE;
        stringField = "Message";
        objectField = new Object();
    }

    @Override
    public void testMethod() {
        // empty
    }

    @Override
    public void finalize() throws Throwable {
        super.finalize();
    }

    public void interfaceMethodReferral2(MultipleImplementersInterface obj) {
        obj.interfaceMethodReferral(obj);
    }

    public void lambdaUsingMethod2() {
        Thread t = new Thread(this::testMethod);
        t.start();
    }
}
