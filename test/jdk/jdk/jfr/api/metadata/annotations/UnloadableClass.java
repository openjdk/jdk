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

package jdk.jfr.api.metadata.annotations;

import java.io.DataInputStream;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;

/* Purpose of this class is to load a specified class in its
 * own class loader, but delegate every other class.
 */
public final class UnloadableClass<T> extends ClassLoader {
    private final String className;

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static <T> Class<T> load(Class<T> clazz) throws ClassNotFoundException {
        UnloadableClass cl = new UnloadableClass(clazz.getName());
        return cl.loadClass(cl.className);
    }

    private UnloadableClass(String className) {
        super("Class loader for class " + className, ClassLoader.getSystemClassLoader());
        this.className = className;
    }

    public Class<?> loadClass(String name) throws ClassNotFoundException {
        if (!className.equals(name)) {
            return super.loadClass(name);
        }
        String resourceName = name.replace('.', '/') + ".class";
        try (var is = getResourceAsStream(resourceName); var dis = new DataInputStream(is)) {
            int size = is.available();
            byte buffer[] = new byte[size];
            dis.readFully(buffer);
            CodeSource cs = new CodeSource(getResource(resourceName), (Certificate[]) null);
            ProtectionDomain pd = new ProtectionDomain(cs, null);
            return defineClass(name, buffer, 0, buffer.length, pd);
        } catch (Exception e) {
            throw new InternalError(e);
        }
    }
}
