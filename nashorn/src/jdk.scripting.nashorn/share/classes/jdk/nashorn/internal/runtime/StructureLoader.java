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
import static jdk.nashorn.internal.codegen.CompilerConstants.JS_OBJECT_DUAL_FIELD_PREFIX;
import static jdk.nashorn.internal.codegen.CompilerConstants.JS_OBJECT_SINGLE_FIELD_PREFIX;

import java.lang.reflect.Module;
import java.security.ProtectionDomain;
import jdk.nashorn.internal.codegen.ObjectClassGenerator;

/**
 * Responsible for on the fly construction of structure classes.
 */
final class StructureLoader extends NashornLoader {
    private static final String SINGLE_FIELD_PREFIX = binaryName(SCRIPTS_PACKAGE) + '.' + JS_OBJECT_SINGLE_FIELD_PREFIX.symbolName();
    private static final String DUAL_FIELD_PREFIX   = binaryName(SCRIPTS_PACKAGE) + '.' + JS_OBJECT_DUAL_FIELD_PREFIX.symbolName();

    private final Module structuresModule;

    /**
     * Constructor.
     */
    StructureLoader(final ClassLoader parent) {
        super(parent);

        // new structures module, it's exports, read edges
        structuresModule = defineModule("jdk.scripting.nashorn.structures", this);
        addModuleExports(structuresModule, SCRIPTS_PKG, nashornModule);
        addReadsModule(structuresModule, nashornModule);
        addReadsModule(structuresModule, Object.class.getModule());

        // specific exports from nashorn to the structures module
        nashornModule.addExports(SCRIPTS_PKG, structuresModule);
        nashornModule.addExports(RUNTIME_PKG, structuresModule);

        // nashorn has to read fields from classes of the new module
        nashornModule.addReads(structuresModule);
    }

    /**
     * Returns true if the class name represents a structure object with dual primitive/object fields.
     * @param name a class name
     * @return true if a dual field structure class
     */
    private static boolean isDualFieldStructure(final String name) {
        return name.startsWith(DUAL_FIELD_PREFIX);
    }

    /**
     * Returns true if the class name represents a structure object with single object-only fields.
     * @param name a class name
     * @return true if a single field structure class
     */
    static boolean isSingleFieldStructure(final String name) {
        return name.startsWith(SINGLE_FIELD_PREFIX);
    }

    /**
     * Returns true if the class name represents a Nashorn structure object.
     * @param name a class name
     * @return true if a structure class
     */
    static boolean isStructureClass(final String name) {
        return isDualFieldStructure(name) || isSingleFieldStructure(name);
    }

    protected Module getModule() {
        return structuresModule;
    }

    @Override
    protected Class<?> findClass(final String name) throws ClassNotFoundException {
        if (isDualFieldStructure(name)) {
            return generateClass(name, name.substring(DUAL_FIELD_PREFIX.length()), true);
        } else if (isSingleFieldStructure(name)) {
            return generateClass(name, name.substring(SINGLE_FIELD_PREFIX.length()), false);
        }
        return super.findClass(name);
    }

    /**
     * Generate a layout class.
     * @param name       Name of class.
     * @param descriptor Layout descriptor.
     * @return Generated class.
     */
    private Class<?> generateClass(final String name, final String descriptor, final boolean dualFields) {
        final Context context = Context.getContextTrusted();

        final byte[] code = new ObjectClassGenerator(context, dualFields).generate(descriptor);
        return defineClass(name, code, 0, code.length, new ProtectionDomain(null, getPermissions(null)));
    }
}
