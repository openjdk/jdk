/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * @test TestStableMemoryBarrier
 * @bug 8139758
 * @summary tests memory barrier correctly inserted for stable fields
 * @library /testlibrary /../../test/lib
 *
 * @run main/bootclasspath -Xcomp -XX:CompileOnly=::testCompile
 *                   java.lang.invoke.TestStableMemoryBarrier
 *
 * @author hui.shi@linaro.org
 */
package java.lang.invoke;

import jdk.internal.vm.annotation.Stable;

import java.lang.reflect.InvocationTargetException;

public class TestStableMemoryBarrier {

    public static void main(String[] args) throws Exception {
        run(NotDominate.class);

    }

    /* ====================================================
     * Stable field initialized in method, but its allocation
     * doesn't dominate MemBar Release at the end of method.
     */

    static class NotDominate{
        public @Stable int v;
        public static int[] array = new int[100];
        public static NotDominate testCompile(int n) {
           if ((n % 2) == 0) return null;
           // add a loop here, trigger PhaseIdealLoop::verify_dominance
           for (int i = 0; i < 100; i++) {
              array[i] = n;
           }
           NotDominate nm = new NotDominate();
           nm.v = n;
           return nm;
        }

        public static void test() throws Exception {
           for (int i = 0; i < 1000000; i++)
               testCompile(i);
        }
    }

    public static void run(Class<?> test) {
        Throwable ex = null;
        System.out.print(test.getName()+": ");
        try {
            test.getMethod("test").invoke(null);
        } catch (InvocationTargetException e) {
            ex = e.getCause();
        } catch (Throwable e) {
            ex = e;
        } finally {
            if (ex == null) {
                System.out.println("PASSED");
            } else {
                System.out.println("FAILED");
                ex.printStackTrace(System.out);
            }
        }
    }
}
