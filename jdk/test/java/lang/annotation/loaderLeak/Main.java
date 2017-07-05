/*
 * Copyright 2004 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug 5040740
 * @summary annotations cause memory leak
 * @author gafter
 *
 * @run shell LoaderLeak.sh
 */

import java.net.*;
import java.lang.ref.*;
import java.util.*;
import java.io.*;

public class Main {
    public static void main(String[] args) throws Exception {
        for (int i=0; i<100; i++)
            doTest(args.length != 0);
    }

    static void doTest(boolean readAnn) throws Exception {
        // URL classes = new URL("file://" + System.getProperty("user.dir") + "/classes");
        // URL[] path = { classes };
        // URLClassLoader loader = new URLClassLoader(path);
        ClassLoader loader = new SimpleClassLoader();
        WeakReference<Class<?>> c = new WeakReference(loader.loadClass("C"));
        if (c.get() == null) throw new AssertionError();
        if (c.get().getClassLoader() != loader) throw new AssertionError();
        if (readAnn) System.out.println(c.get().getAnnotations()[0]);
        if (c.get() == null) throw new AssertionError();
        System.gc();
        System.gc();
        if (c.get() == null) throw new AssertionError();
        System.gc();
        System.gc();
        loader = null;
        System.gc();
        System.gc();
        if (c.get() != null) throw new AssertionError();
    }
}

class SimpleClassLoader extends ClassLoader {
    private Hashtable classes = new Hashtable();

    public SimpleClassLoader() {
    }
    private byte getClassImplFromDataBase(String className)[] {
        byte result[];
        try {
            FileInputStream fi = new FileInputStream("classes/"+className+".class");
            result = new byte[fi.available()];
            fi.read(result);
            return result;
        } catch (Exception e) {

            /*
             * If we caught an exception, either the class wasnt found or it
             * was unreadable by our process.
             */
            return null;
        }
    }
    public Class loadClass(String className) throws ClassNotFoundException {
        return (loadClass(className, true));
    }
    public synchronized Class loadClass(String className, boolean resolveIt)
        throws ClassNotFoundException {
        Class result;
        byte  classData[];

        /* Check our local cache of classes */
        result = (Class)classes.get(className);
        if (result != null) {
            return result;
        }

        /* Check with the primordial class loader */
        try {
            result = super.findSystemClass(className);
            return result;
        } catch (ClassNotFoundException e) {
        }

        /* Try to load it from our repository */
        classData = getClassImplFromDataBase(className);
        if (classData == null) {
            throw new ClassNotFoundException();
        }

        /* Define it (parse the class file) */
        result = defineClass(classData, 0, classData.length);
        if (result == null) {
            throw new ClassFormatError();
        }

        if (resolveIt) {
            resolveClass(result);
        }

        classes.put(className, result);
        return result;
    }
}
