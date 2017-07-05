/*
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
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Written by Doug Lea and Martin Buchholz with assistance from
 * members of JCP JSR-166 Expert Group and released to the public
 * domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.CompletableFuture;

import junit.framework.Test;
import junit.framework.TestSuite;

public class ForkJoinPool9Test extends JSR166TestCase {
    public static void main(String[] args) {
        main(suite(), args);
    }

    public static Test suite() {
        return new TestSuite(ForkJoinPool9Test.class);
    }

    /**
     * Check handling of common pool thread context class loader
     */
    public void testCommonPoolThreadContextClassLoader() throws Throwable {
        if (!testImplementationDetails) return;
        VarHandle CCL =
            MethodHandles.privateLookupIn(Thread.class, MethodHandles.lookup())
            .findVarHandle(Thread.class, "contextClassLoader", ClassLoader.class);
        ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
        boolean haveSecurityManager = (System.getSecurityManager() != null);
        CompletableFuture.runAsync(
            () -> {
                assertSame(systemClassLoader,
                           Thread.currentThread().getContextClassLoader());
                assertSame(systemClassLoader,
                           CCL.get(Thread.currentThread()));
                if (haveSecurityManager)
                    assertThrows(
                        SecurityException.class,
                        () -> System.getProperty("foo"),
                        () -> Thread.currentThread().setContextClassLoader(null));

                // TODO ?
//                 if (haveSecurityManager
//                     && Thread.currentThread().getClass().getSimpleName()
//                     .equals("InnocuousForkJoinWorkerThread"))
//                     assertThrows(SecurityException.class, /* ?? */);
            }).join();
    }

}
