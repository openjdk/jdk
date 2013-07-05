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

package jdk.nashorn.internal.tools.nasgen;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import jdk.internal.org.objectweb.asm.Type;
import jdk.nashorn.internal.lookup.Lookup;
import jdk.nashorn.internal.objects.PrototypeObject;
import jdk.nashorn.internal.objects.ScriptFunctionImpl;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptObject;

/**
 * String constants used for code generation/instrumentation.
 */
@SuppressWarnings("javadoc")
public interface StringConstants {
    static final Type TYPE_METHOD             = Type.getType(Method.class);
    static final Type TYPE_METHODHANDLE       = Type.getType(MethodHandle.class);
    static final Type TYPE_METHODHANDLE_ARRAY = Type.getType(MethodHandle[].class);
    static final Type TYPE_OBJECT             = Type.getType(Object.class);
    static final Type TYPE_CLASS              = Type.getType(Class.class);
    static final Type TYPE_STRING             = Type.getType(String.class);

    // Nashorn types
    static final Type TYPE_LOOKUP             = Type.getType(Lookup.class);
    static final Type TYPE_PROPERTYMAP        = Type.getType(PropertyMap.class);
    static final Type TYPE_PROTOTYPEOBJECT    = Type.getType(PrototypeObject.class);
    static final Type TYPE_SCRIPTFUNCTION     = Type.getType(ScriptFunction.class);
    static final Type TYPE_SCRIPTFUNCTIONIMPL = Type.getType(ScriptFunctionImpl.class);
    static final Type TYPE_SCRIPTOBJECT       = Type.getType(ScriptObject.class);

    static final String PROTOTYPE_SUFFIX = "$Prototype";
    static final String CONSTRUCTOR_SUFFIX = "$Constructor";
    // This field name is known to Nashorn runtime (Context).
    // Synchronize the name change, if needed at all.
    static final String MAP_FIELD_NAME = "$nasgenmap$";
    static final String $CLINIT$ = "$clinit$";
    static final String CLINIT = "<clinit>";
    static final String INIT = "<init>";
    static final String DEFAULT_INIT_DESC = Type.getMethodDescriptor(Type.VOID_TYPE);

    static final String SCRIPTOBJECT_INIT_DESC = Type.getMethodDescriptor(Type.VOID_TYPE, TYPE_PROPERTYMAP);

    static final String METHODHANDLE_TYPE = TYPE_METHODHANDLE.getInternalName();

    static final String OBJECT_TYPE = TYPE_OBJECT.getInternalName();
    static final String OBJECT_DESC = TYPE_OBJECT.getDescriptor();
    static final String OBJECT_ARRAY_DESC = Type.getDescriptor(Object[].class);

    static final String SCRIPTFUNCTION_TYPE = TYPE_SCRIPTFUNCTION.getInternalName();
    static final String SCRIPTFUNCTIONIMPL_TYPE = TYPE_SCRIPTFUNCTIONIMPL.getInternalName();
    static final String SCRIPTFUNCTIONIMPL_MAKEFUNCTION = "makeFunction";
    static final String SCRIPTFUNCTIONIMPL_MAKEFUNCTION_DESC =
        Type.getMethodDescriptor(TYPE_SCRIPTFUNCTION, TYPE_STRING, TYPE_METHODHANDLE);
    static final String SCRIPTFUNCTIONIMPL_MAKEFUNCTION_SPECS_DESC =
        Type.getMethodDescriptor(TYPE_SCRIPTFUNCTION, TYPE_STRING, TYPE_METHODHANDLE, TYPE_METHODHANDLE_ARRAY);

    static final String SCRIPTFUNCTIONIMPL_INIT_DESC3 =
        Type.getMethodDescriptor(Type.VOID_TYPE, TYPE_STRING, TYPE_METHODHANDLE, TYPE_METHODHANDLE_ARRAY);
    static final String SCRIPTFUNCTIONIMPL_INIT_DESC4 =
        Type.getMethodDescriptor(Type.VOID_TYPE, TYPE_STRING, TYPE_METHODHANDLE, TYPE_PROPERTYMAP, TYPE_METHODHANDLE_ARRAY);
    static final String SCRIPTFUNCTION_SETARITY = "setArity";
    static final String SCRIPTFUNCTION_SETARITY_DESC = Type.getMethodDescriptor(Type.VOID_TYPE, Type.INT_TYPE);
    static final String SCRIPTFUNCTION_SETPROTOTYPE = "setPrototype";
    static final String SCRIPTFUNCTION_SETPROTOTYPE_DESC = Type.getMethodDescriptor(Type.VOID_TYPE, TYPE_OBJECT);
    static final String PROTOTYPEOBJECT_TYPE = TYPE_PROTOTYPEOBJECT.getInternalName();
    static final String PROTOTYPEOBJECT_SETCONSTRUCTOR = "setConstructor";
    static final String PROTOTYPEOBJECT_SETCONSTRUCTOR_DESC = Type.getMethodDescriptor(Type.VOID_TYPE, TYPE_OBJECT, TYPE_OBJECT);
    static final String SCRIPTOBJECT_TYPE = TYPE_SCRIPTOBJECT.getInternalName();
    static final String MAP_TYPE = TYPE_PROPERTYMAP.getInternalName();
    static final String MAP_DESC = TYPE_PROPERTYMAP.getDescriptor();
    static final String MAP_NEWMAP = "newMap";
    static final String MAP_NEWMAP_DESC = Type.getMethodDescriptor(TYPE_PROPERTYMAP);
    static final String MAP_DUPLICATE = "duplicate";
    static final String MAP_DUPLICATE_DESC = Type.getMethodDescriptor(TYPE_PROPERTYMAP);
    static final String LOOKUP_TYPE = TYPE_LOOKUP.getInternalName();
    static final String LOOKUP_NEWPROPERTY = "newProperty";
    static final String LOOKUP_NEWPROPERTY_DESC =
        Type.getMethodDescriptor(TYPE_PROPERTYMAP, TYPE_PROPERTYMAP, TYPE_STRING, Type.INT_TYPE, TYPE_METHODHANDLE, TYPE_METHODHANDLE);
    static final String GETTER_PREFIX = "G$";
    static final String SETTER_PREFIX = "S$";

    // ScriptObject.getClassName() method.
    static final String GET_CLASS_NAME = "getClassName";
    static final String GET_CLASS_NAME_DESC = Type.getMethodDescriptor(TYPE_STRING);
}
