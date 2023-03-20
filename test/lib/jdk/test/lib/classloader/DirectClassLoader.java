/*
 * Copyright (c) 2023 SAP SE. All rights reserved.
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

package jdk.test.lib.classloader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;

/**
 * Read the bytes of a class that would actually be loaded by the parent and define the class directly.
 */
public class DirectClassLoader extends ClassLoader {

    public Class<?> findClass(String className) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(className)) {
            // First, check if the class has already been loaded
            Class<?> c = findLoadedClass(className);
            if (c == null) {
                try {
                    String binaryName = className.replace(".", "/") + ".class";
                    URL url = getParent().getResource(binaryName);
                    if (url == null) {
                        throw new IOException("Resource not found: '" + binaryName + "'");
                    }
                    InputStream is = url.openStream();
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();

                    int nRead;
                    byte[] data = new byte[16384];
                    while ((nRead = is.read(data, 0, data.length)) != -1) {
                        buffer.write(data, 0, nRead);
                    }

                    byte[] b = buffer.toByteArray();
                    c = defineClass(className, b, 0, b.length);
                    System.out.println(this + " defined class " + className);
                } catch (IOException e) {
                    e.printStackTrace();
                    throw new ClassNotFoundException("Could not define directly '" + className + "'");
                }
            }
            return c;
        }
    }

    /**
     * Define the class with the given name directly and for convenience
     * instantiate it using the default constructor.
     */
    public Object newInstance(String className)
            throws ClassNotFoundException,
            InstantiationException, IllegalAccessException, IllegalArgumentException,
            InvocationTargetException, NoSuchMethodException, SecurityException {
        Class<?> c = findClass(className);
        return c.getDeclaredConstructor().newInstance();
    }

    public DirectClassLoader(ClassLoader parent) {
        super(parent);
        System.identityHashCode(this);
    }
}
