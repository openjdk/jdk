/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

import java.awt.EventQueue;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;

import javax.print.DocFlavor;
import javax.print.PrintServiceLookup;

/**
 * @test
 * @bug 8273831
 * @summary Tests custom class loader cleanup
 */
public final class FlushCustomClassLoader {

    public static void main(String[] args) throws Exception {
        Reference<ClassLoader> loader = getLoader("testMethod");

        int attempt = 0;
        while (loader.get() != null) {
            if (++attempt > 10) {
                throw new RuntimeException("Too many attempts: " + attempt);
            }
            System.gc();
            Thread.sleep(1000);
            System.out.println("Not freed, attempt: " + attempt);
        }
    }

    public static void testMethod() {
        DocFlavor flavor = DocFlavor.SERVICE_FORMATTED.PRINTABLE;
        PrintServiceLookup.lookupPrintServices(flavor, null);
    }

    private static Reference<ClassLoader> getLoader(String m) throws Exception {
        /*
         * The print services are stored per the AppContext, and each AppContext
         * caches the "current" class loader during creation.
         * see javax.print.PrintServiceLookup.
         *
         * To prevent AppContext from cache our test loader we force AppContext
         * creation early by the invokeAndWait.
         * The "EventQueue.invokeAndWait(() -> {});" can be removed when the
         * AppContext usage will be deleted in the PrintServiceLookup
         */
        EventQueue.invokeAndWait(() -> {});

        URL url = FlushCustomClassLoader.class.getProtectionDomain()
                                              .getCodeSource().getLocation();
        URLClassLoader loader = new URLClassLoader(new URL[]{url}, null);

        Thread ct = Thread.currentThread();
        ct.setContextClassLoader(loader);
        Class<?> cls = Class.forName("FlushCustomClassLoader", true, loader);
        cls.getDeclaredMethod(m).invoke(null);
        ct.setContextClassLoader(null);
        loader.close();
        return new WeakReference<>(loader);
    }
}
