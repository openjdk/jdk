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
 * @bug 8297977
 * @summary Test that throwing OOM from reflected method gets InvocationTargetException
 * @run main/othervm -Xmx128m ReflectOutOfMemoryError
 */
import java.lang.reflect.*;

// This test salvaged out of vmTestbase except tests.
public class ReflectOutOfMemoryError {

    private static volatile Object pool[] = null;

    public static void raiseOutOfMemory() throws OutOfMemoryError {
        try {
            // Repository for objects, which should be allocated:
            int index = 0;
            for (int size = 1 << 30; size > 0 && pool == null; size >>= 1)
                try {
                    pool = new Object[size];
                } catch (OutOfMemoryError oome) {
                }
            if (pool == null)
                throw new Error("HS bug: cannot allocate new Object[1]");

            // Sum up time spent, when it was hard to JVM to allocate next object
            // (i.e.: when JVM has spent more than 1 second to allocate new object):
            double totalDelay = 0;
            long timeMark = System.currentTimeMillis();

            for (; index < pool.length; index++) {
                //-------------------------
                pool[index] = new Object();
                long nextTimeMark = System.currentTimeMillis();
                long elapsed = nextTimeMark - timeMark;
                timeMark = nextTimeMark;
                //----------------------
                if (elapsed > 1000) {
                    double seconds = elapsed / 1000.0;
                    System.out.println(
                            "pool[" + index +
                                    "]=new Object(); // elapsed " + seconds + "s");
                    totalDelay += seconds;
                    if (totalDelay > 300) {
                        System.out.println(
                                "Memory allocation became slow: so heap seems exhausted.");
                        throw new OutOfMemoryError();
                    }
                }
            }

            // This method should never return:
            throw new Error("TEST_BUG: failed to provoke OutOfMemoryError");
        } finally {
            // Make sure there will be enough memory for next object allocation
             pool = null;
        }
    }

    public static void main(java.lang.String[] unused) throws Exception {
        System.out.println("Starting test");
        Class testClass = ReflectOutOfMemoryError.class;
        try {
            Method testMethod = testClass.getMethod("raiseOutOfMemory", new Class [0]);
            Object junk = testMethod.invoke(null, new Object [0]);
            throw new RuntimeException("InvocationTargetException should be thrown");
        } catch (InvocationTargetException ite) {
            Throwable targetException = ite.getTargetException();
            if (targetException instanceof OutOfMemoryError) {
                System.out.println("OutOfMemoryError thrown as expected.");
                System.out.println("Test passed.");
            } else {
                throw new RuntimeException("Unexpected InvocationTargetException: " + targetException);
            }
        } catch (Exception exception) {
            throw new RuntimeException("Unexpected exception: " + exception);
        }
    }
}
