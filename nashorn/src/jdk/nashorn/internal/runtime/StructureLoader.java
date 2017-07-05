/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.runtime;

import static jdk.nashorn.internal.codegen.Compiler.SCRIPTS_PACKAGE;
import static jdk.nashorn.internal.codegen.Compiler.binaryName;
import static jdk.nashorn.internal.codegen.CompilerConstants.JS_OBJECT_PREFIX;

import java.security.ProtectionDomain;
import jdk.nashorn.internal.codegen.ObjectClassGenerator;

/**
 * Responsible for on the fly construction of structure classes.
 *
 */
final class StructureLoader extends NashornLoader {
    private static final String JS_OBJECT_PREFIX_EXTERNAL = binaryName(SCRIPTS_PACKAGE) + '.' + JS_OBJECT_PREFIX.symbolName();

    /**
     * Constructor.
     */
    StructureLoader(final ClassLoader parent, final Context context) {
        super(parent, context);
    }

    @Override
    protected synchronized Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
        // check the cache first
        final Class<?> loadedClass = findLoadedClass(name);
        if (loadedClass != null) {
            if (resolve) {
                resolveClass(loadedClass);
            }
            return loadedClass;
        }

        return super.loadClassTrusted(name, resolve);
    }

    @Override
    protected Class<?> findClass(final String name) throws ClassNotFoundException {
        if (name.startsWith(JS_OBJECT_PREFIX_EXTERNAL)) {
            return generateClass(name, name.substring(JS_OBJECT_PREFIX_EXTERNAL.length()));
        }
        return super.findClass(name);
    }

    /**
     * Generate a layout class.
     * @param name       Name of class.
     * @param descriptor Layout descriptor.
     * @return Generated class.
     */
    private Class<?> generateClass(final String name, final String descriptor) {
        Context context = getContext();

        if (context == null) {
            context = Context.getContextTrusted();
        }

        final byte[] code = new ObjectClassGenerator(context).generate(descriptor);
        return defineClass(name, code, 0, code.length, new ProtectionDomain(null, getPermissions(null)));
    }
}
