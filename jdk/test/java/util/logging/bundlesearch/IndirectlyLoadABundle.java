/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;

/**
 * This class is used to ensure that a resource bundle loadable by a classloader
 * is on the caller's stack, but not on the classpath or TCCL to ensure that
 * Logger.getLogger() can't load the bundle via a stack search
 *
 * @author Jim Gish
 */
public class IndirectlyLoadABundle {

    private final static String rbName = "StackSearchableResource";

    public boolean loadAndTest() throws Throwable {
        // Find out where we are running from so we can setup the URLClassLoader URLs
        // test.src and test.classes will be set if running in jtreg, but probably
        // not otherwise
        String testDir = System.getProperty("test.src", System.getProperty("user.dir"));
        String testClassesDir = System.getProperty("test.classes",
                System.getProperty("user.dir"));
        String sep = System.getProperty("file.separator");

        URL[] urls = new URL[2];

        // Allow for both jtreg and standalone cases here
        urls[0] = Paths.get(testDir, "resources").toUri().toURL();
        urls[1] = Paths.get(testClassesDir).toUri().toURL();

        System.out.println("INFO: urls[0] = " + urls[0]);
        System.out.println("INFO: urls[1] = " + urls[1]);

        // Make sure we can find it via the URLClassLoader
        URLClassLoader yetAnotherResourceCL = new URLClassLoader(urls, null);
        if (!testForValidResourceSetup(yetAnotherResourceCL)) {
            throw new Exception("Couldn't directly load bundle " + rbName
                    + " as expected. Test config problem");
        }
        // But it shouldn't be available via the system classloader
        ClassLoader myCL = this.getClass().getClassLoader();
        if (testForValidResourceSetup(myCL)) {
            throw new Exception("Was able to directly load bundle " + rbName
                    + " from " + myCL + " but shouldn't have been"
                    + " able to. Test config problem");
        }

        Class<?> loadItUpClazz = Class.forName("LoadItUp", true, yetAnotherResourceCL);
        ClassLoader actual = loadItUpClazz.getClassLoader();
        if (actual != yetAnotherResourceCL) {
            throw new Exception("LoadItUp was loaded by an unexpected CL: " + actual);
        }
        Object loadItUp = loadItUpClazz.newInstance();
        Method testMethod = loadItUpClazz.getMethod("test", String.class);
        try {
            return (Boolean) testMethod.invoke(loadItUp, rbName);
        } catch (InvocationTargetException ex) {
            throw ex.getTargetException();
        }
    }

    private boolean testForValidResourceSetup(ClassLoader cl) {
        // First make sure the test environment is setup properly and the bundle actually
        // exists
        return ResourceBundleSearchTest.isOnClassPath(rbName, cl);
    }
}
