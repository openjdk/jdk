/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @build Initializer Test ExceptionInClassInitialization
 * @run main ExceptionInClassInitialization
 * @summary ensure InvocationTargetException thrown due to the initialization of
 *          the declaring class wrapping with the proper cause
 */

import java.lang.reflect.InvocationTargetException;

public class ExceptionInClassInitialization {
    public static void main(String... argv) throws ReflectiveOperationException {
        Class<?> c = Class.forName("Initializer");
        testExecMethod(c);
        testFieldAccess(c);
    }

    static void testExecMethod(Class<?> cls) throws ReflectiveOperationException {
        try {
            cls.getDeclaredMethod("execMethod").invoke(null);
            throw new RuntimeException("InvocationTargetException not thrown");
        } catch (InvocationTargetException e) {
            // InvocationTargetException wraps the exception that was thrown while reflection.
            Throwable t = e.getCause();
            if (t instanceof ExceptionInInitializerError eiie) {
                if (eiie.getCause() instanceof MyException) {
                    return;
                }
                throw new RuntimeException("ExceptionInInitializerError due to other exception than MyException!", eiie);
            }
            throw new RuntimeException("InvocationTargetException was thrown not due to error while initialization!", e);
        }
    }

    static void testFieldAccess(Class<?> cls) throws ReflectiveOperationException {
        try {
            cls.getDeclaredMethod("fieldAccess").invoke(null);
            throw new RuntimeException("InvocationTargetException not thrown");
        } catch (InvocationTargetException e) {
            // the class initialization was run and failed.  NoClassDefFoundError
            // should be thrown in this second attempt.
            Throwable t = e.getCause();
            if (t instanceof NoClassDefFoundError ncdfe) {
                t = t.getCause();
                if (t instanceof ExceptionInInitializerError eiie) {
                    return;
                }
            }
            throw new RuntimeException("InvocationTargetException was thrown not due to error while initialization!", e);
        }
    }

}
