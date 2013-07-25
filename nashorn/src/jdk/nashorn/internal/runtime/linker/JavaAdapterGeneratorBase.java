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

package jdk.nashorn.internal.runtime.linker;

import jdk.internal.org.objectweb.asm.Type;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.ScriptObject;

/**
 * Base class for both {@link JavaAdapterBytecodeGenerator} and {@link JavaAdapterClassLoader}, containing those
 * bytecode types, type names and method descriptor that are used by both.
 */
@SuppressWarnings("javadoc")
abstract class JavaAdapterGeneratorBase {
    static final Type CONTEXT_TYPE       = Type.getType(Context.class);
    static final Type OBJECT_TYPE        = Type.getType(Object.class);
    static final Type SCRIPT_OBJECT_TYPE = Type.getType(ScriptObject.class);

    static final String CONTEXT_TYPE_NAME = CONTEXT_TYPE.getInternalName();
    static final String OBJECT_TYPE_NAME  = OBJECT_TYPE.getInternalName();

    static final String INIT = "<init>";

    static final String GLOBAL_FIELD_NAME = "global";

    static final String SCRIPT_OBJECT_TYPE_DESCRIPTOR = SCRIPT_OBJECT_TYPE.getDescriptor();

    static final String SET_GLOBAL_METHOD_DESCRIPTOR = Type.getMethodDescriptor(Type.VOID_TYPE, SCRIPT_OBJECT_TYPE);
    static final String VOID_NOARG_METHOD_DESCRIPTOR = Type.getMethodDescriptor(Type.VOID_TYPE);

    protected JavaAdapterGeneratorBase() {
    }
}
