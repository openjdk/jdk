/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package helpers;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;

public class ByteArrayClassLoader extends ClassLoader {
    final Map<String, ClassData> classNameToClass;

    public ByteArrayClassLoader(ClassLoader parent, String name, byte[] bytes) {
        this(parent, Collections.singletonMap(name, new ClassData(name, bytes)));
    }

    public ByteArrayClassLoader(ClassLoader parent, Map<String, ClassData> classNameToClass) {
        super(parent);
        this.classNameToClass = classNameToClass;
    }

    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (classNameToClass.containsKey(name)) {
            return findClass(name);
        }
        return super.loadClass(name, resolve);
    }

    public Class<?> findClass(String name) throws ClassNotFoundException {
        ClassData d = classNameToClass.get(name);
        if (d != null) {
            if (d.klass != null) {
                return d.klass;
            }
            return d.klass = defineClass(name, d.bytes, 0, d.bytes.length);
        }
        return super.findClass(name);
    }

    public void loadAll() throws Exception {
        for (String className : classNameToClass.keySet()) {
            loadClass(className);
        }
    }

    public Method getMethod(String className, String methodName) throws Exception {
            for (Method m : loadClass(className).getDeclaredMethods()) {
            if (m.getName().equals(methodName)) {
                return m;
            }
        }
        throw new IllegalArgumentException("Method name '" + methodName + "' not found in '" + className + "'");
    }

    public static class ClassData {
        final String name;
        final byte[] bytes;
        Class<?> klass;

        public ClassData(String name, byte[] bytes) {
            this.name = name;
            this.bytes = bytes;
        }
    }
}
