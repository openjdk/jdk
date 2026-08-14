/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8349945
 * @summary Tracking of strict static fields
 * @library /test/lib
 * @enablePreview
 * @modules java.base/jdk.internal.vm.annotation
 * @compile Bnoinit_BAD.jasm
 *          Brbefore_BAD.jasm
 *          Cwreflective_OK.jasm
 *          Creflbefore_BAD.jasm
 *          WriteAfterReadRefl.jasm
 * @compile StrictStaticFieldsTest.java
 * @run driver jdk.test.lib.helpers.StrictProcessor
 *             StrictStaticFieldsTest
 *             Aregular_OK
 *             Anulls_OK
 *             Arepeat_OK
 *             Aupdate_OK
 * @run main/othervm StrictStaticFieldsTest
 */


import java.lang.reflect.Field;
import jdk.test.lib.helpers.StrictInit;

public class StrictStaticFieldsTest {
    public static void main(String[] args) throws Exception {
        // --------------
        // POSITIVE TESTS
        // --------------

        // Base Case
        printStatics(Aregular_OK.class);

        // Strict statics initialized to null and zero
        printStatics(Anulls_OK.class);

        // Assign strict static twice
        printStatics(Arepeat_OK.class);

        // Read and write initialized strict static
        printStatics(Aupdate_OK.class);

        // Reflectively set static fields
        printStaticsReflective(Cwreflective_OK.class);

        // --------------
        // NEGATIVE TESTS
        // --------------

        // Strict statics not initialized
        try {
            printStatics(Bnoinit_BAD.class);
            throw new RuntimeException("Should throw");
        } catch(ExceptionInInitializerError ex) {
            Throwable e = (ex.getCause() != null) ? ex.getCause() : ex;
            if (!e.getMessage().contains("unset after initialization")) {
                throw new RuntimeException("wrong exception: " + e.getMessage());
            }
            e.printStackTrace();
        }

        // Read before write
        try {
            printStatics(Brbefore_BAD.class);
            throw new RuntimeException("Should throw");
        } catch(ExceptionInInitializerError ex) {
            Throwable e = (ex.getCause() != null) ? ex.getCause() : ex;
            if (!e.getMessage().contains("is unset before first read")) {
                throw new RuntimeException("wrong exception: " + e.getMessage());
            }
            e.printStackTrace();
        }

        // Reflective read before write
        try {
            printStaticsReflective(Creflbefore_BAD.class);
            throw new RuntimeException("Should throw");
        } catch(ExceptionInInitializerError ex) {
            Throwable e = (ex.getCause() != null) ? ex.getCause() : ex;
            if (!e.getMessage().contains("is unset before first read")) {
                throw new RuntimeException("wrong exception: " + e.getMessage());
            }
            e.printStackTrace();
        }

        // Reflective write after read
        try {
            printStaticsReflective(WriteAfterReadRefl.class);
            throw new RuntimeException("Should throw");
        } catch(ExceptionInInitializerError ex) {
            Throwable e = (ex.getCause() != null) ? ex.getCause() : ex;
            if (!e.getMessage().contains("set after read")) {
                throw new RuntimeException("wrong exception: " + e.getMessage());
            }
            e.printStackTrace();
        }

        System.out.println("Passed");
    }

    static void printStatics(Class<?> cls) throws Exception {
        Field f1 = cls.getDeclaredField("F1__STRICT");
        Field f2 = cls.getDeclaredField("F2__STRICT");
        System.out.println(f1.get(null));
        System.out.println(f2.get(null));
    }

    // Methods for reflective access
    static void printStaticsReflective(Class<?> cls) throws Exception {
        Field FIELD_F1 = findField(cls, "F1__STRICT");
        Field FIELD_F2 = findField(cls, "F2__STRICT");

        String f1 = (String)getstaticReflective(FIELD_F1);
        int f2 = (int)getstaticReflective(FIELD_F2);

        System.out.println(f1);
        System.out.println(f2);
    }

    static void putstaticReflective(Field f, Object x) {
        try {
            f.set(null, x);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    static Object getstaticReflective(Field f) {
        try {
            return f.get(null);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    static Field findField(Class<?> cls, String name) {
        try {
            return cls.getDeclaredField(name);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }
}

class Aregular_OK {
        @StrictInit static final String F1__STRICT = "hello";
        @StrictInit static final int    F2__STRICT = 42;
    }

class Anulls_OK {
    @StrictInit static String F1__STRICT = null;
    @StrictInit static int    F2__STRICT = 0;
}

class Arepeat_OK {
    @StrictInit static String F1__STRICT = "hello";
    @StrictInit static int    F2__STRICT = 42;
    static {
        System.out.print("(making second putstatic)");
        F2__STRICT = 43;
    }
}

class Aupdate_OK {
    @StrictInit static String F1__STRICT = "hello";
    @StrictInit static int    F2__STRICT = 42;
    static {
        System.out.println("(making getstatic and second putstatic)");
        F2__STRICT++;
    }
}
