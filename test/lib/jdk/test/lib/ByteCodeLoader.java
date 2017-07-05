/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib;

import java.security.SecureClassLoader;

/**
 * {@code ByteCodeLoader} can be used for easy loading of byte code already
 * present in memory.
 *
 * {@code InMemoryCompiler} can be used for compiling source code in a string
 * into byte code, which then can be loaded with {@code ByteCodeLoader}.
 *
 * @see InMemoryCompiler
 */
public class ByteCodeLoader extends SecureClassLoader {
    private final String className;
    private final byte[] byteCode;
    private volatile Class<?> holder;

    /**
     * Creates a new {@code ByteCodeLoader} ready to load a class with the
     * given name and the given byte code.
     *
     * @param className The name of the class
     * @param byteCode The byte code of the class
     */
    public ByteCodeLoader(String className, byte[] byteCode) {
        this.className = className;
        this.byteCode = byteCode;
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        if (!name.equals(className)) {
            return super.loadClass(name);
        }
        if (holder == null) {
            synchronized(this) {
                if (holder == null) {
                    holder = findClass(name);
                }
            }
        }
        return holder;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (!name.equals(className)) {
            throw new ClassNotFoundException(name);
        }

        return defineClass(name, byteCode, 0, byteCode.length);
    }

    /**
     * Utility method for creating a new {@code ByteCodeLoader} and then
     * directly load the given byte code.
     *
     * @param className The name of the class
     * @param byteCode The byte code for the class
     * @throws ClassNotFoundException if the class can't be loaded
     * @return A {@see Class} object representing the class
     */
    public static Class<?> load(String className, byte[] byteCode) throws ClassNotFoundException {
        return new ByteCodeLoader(className, byteCode).loadClass(className);
    }
}
