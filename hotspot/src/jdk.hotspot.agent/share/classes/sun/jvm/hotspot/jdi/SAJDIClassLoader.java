/*
 * Copyright (c) 2003, 2008, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package sun.jvm.hotspot.jdi;

import java.io.*;
import java.net.*;

/*
 * This class loader is used for two different reasons:
 *
 * 1) To support multiple simultaneous debuggees.
 *
 * SA's architecture does not allow us to use multiple simultaneous
 * debuggees. This is because of lots of static fields caching
 * vmStruct fields and singleton assumption in classes such as
 * 'sun.jvm.hotspot.runtime.VM'. Hence, we use instances of this
 * class loader to create a separate namespace for each debuggee VM.
 *
 * 2) To support cross VM version debugging.
 *
 * SA has very close dependency on VM data structures. Due to this, a
 * version of SA can only support debuggees running a same dot-dot release and
 * update releases only. For eg. this version of SA supports only 1.4.2 and
 * 1.4.2_xx releases only. But, users may want to debug debuggees running
 * a different version of VM. To support this, we use an instance of this
 * class loader to load classes from corresponding sa-jdi.jar.
 *
 * Note that JDI classes would still be loaded from the debugger's tools.jar
 * and not from debuggee's tools.jar. This means that if JDI interface evolved
 * b/w debuggee and debugger VM versions, user may still get problems. This is
 * the case when debugger runs on 1.5.0 and debuggee runs on 1.4.2. Because JDI
 * evolved b/w these versions (generics, enum, varargs etc.), 1.4.2 sa-jdi.jar
 * won't implement 1.5.0 JDI properly and user would get verifier errors. This
 * class loader solution is suited for different dot-dot release where JDI will
 * not evolve but VM data structures might change and SA implementation might
 * have to change. For example, a debuggee running 1.5.1 VM can be debugged
 * with debugger running on 1.5.0 VM. Here, JDI is same but VM data structures
 * could still change.
 */

class SAJDIClassLoader extends URLClassLoader {
    private static final boolean DEBUG;
    static {
        DEBUG = System.getProperty("sun.jvm.hotspot.jdi.SAJDIClassLoader.DEBUG") != null;
    }

    private ClassLoader parent;
    private boolean classPathSet;

    SAJDIClassLoader(ClassLoader parent) {
        super(new URL[0], parent);
        this.parent = parent;
    }

    SAJDIClassLoader(ClassLoader parent, String classPath) {
        this(parent);
        this.classPathSet = true;
        try {
            addURL(new File(classPath).toURI().toURL());
        } catch(MalformedURLException mue) {
            throw new RuntimeException(mue);
        }
    }

    public synchronized Class loadClass(String name)
        throws ClassNotFoundException {
        // First, check if the class has already been loaded
        Class c = findLoadedClass(name);
        if (c == null) {
            /* If we are loading any class in 'sun.jvm.hotspot.'  or any of the
             * sub-packages (except for 'debugger' sub-pkg. please refer below),
             * we load it by 'this' loader. Or else, we forward the request to
             * 'parent' loader, system loader etc. (rest of the code follows
             * the patten in java.lang.ClassLoader.loadClass).
             *
             * 'sun.jvm.hotspot.debugger.' and sub-package classes are
             * also loaded by parent loader. This is done for two reasons:
             *
             * 1. to avoid code bloat by too many classes.
             * 2. to avoid loading same native library multiple times
             *    from multiple class loaders (which results in getting a
             *    UnsatisifiedLinkageError from System.loadLibrary).
             */

            if (name.startsWith("sun.jvm.hotspot.") &&
                !name.startsWith("sun.jvm.hotspot.debugger.")) {
                return findClass(name);
            }
            if (parent != null) {
                c = parent.loadClass(name);
            } else {
                c = findSystemClass(name);
            }
        }
        return c;
    }

    protected Class findClass(String name) throws ClassNotFoundException {
        if (DEBUG) {
            System.out.println("SA/JDI loader: about to load " + name);
        }
        if (classPathSet) {
            return super.findClass(name);
        } else {
            byte[] b = null;
            try {
                InputStream in = getResourceAsStream(name.replace('.', '/') + ".class");
                // Read until end of stream is reached
                b = new byte[1024];
                int total = 0;
                int len = 0;
                while ((len = in.read(b, total, b.length - total)) != -1) {
                    total += len;
                    if (total >= b.length) {
                        byte[] tmp = new byte[total * 2];
                        System.arraycopy(b, 0, tmp, 0, total);
                        b = tmp;
                    }
                }
                // Trim array to correct size, if necessary
                if (total != b.length) {
                    byte[] tmp = new byte[total];
                    System.arraycopy(b, 0, tmp, 0, total);
                    b = tmp;
                }
            } catch (Exception exp) {
                throw (ClassNotFoundException) new ClassNotFoundException().initCause(exp);
            }
            return defineClass(name, b, 0, b.length);
        }
    }
}
