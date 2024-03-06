/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8318626
 * @summary Verifies JVMTI GetClassFields function filters out fields
 *          the same way Class.getDeclaredFields() does.
 *
 * @run main/othervm/native -agentlib:FilteredFieldsTest FilteredFieldsTest
 */

import java.lang.reflect.Field;

public class FilteredFieldsTest {

    static {
        System.loadLibrary("FilteredFieldsTest");
    }

    private native static int getJVMTIFieldCount(Class cls);

    private static int getDeclaredFieldsCount(Class cls) {
        Field[] declaredFields = cls.getDeclaredFields();
        System.out.println("Class.getDeclaredFields reported " + declaredFields.length + " fields:");
        for (int i = 0; i < declaredFields.length; i++) {
            System.out.println("  [" + i + "] : " + declaredFields[i]);
        }
        return declaredFields.length;
    }

    public static void main(String args[]) throws Exception {
        Class cls = Class.forName("jdk.internal.reflect.ConstantPool");
        int declaredCount = getDeclaredFieldsCount(cls);
        int jvmtiCount = getJVMTIFieldCount(cls);
        if (declaredCount != jvmtiCount) {
            throw new Exception("declaredCount != jvmtiCount: " + declaredCount + " != " + jvmtiCount);
        }
    }
}
