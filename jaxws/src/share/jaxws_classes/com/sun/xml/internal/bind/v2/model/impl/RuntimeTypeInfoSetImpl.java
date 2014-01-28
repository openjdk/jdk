/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.bind.v2.model.impl;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Map;

import javax.xml.namespace.QName;

import com.sun.xml.internal.bind.v2.model.annotation.AnnotationReader;
import com.sun.xml.internal.bind.v2.model.core.TypeInfoSet;
import com.sun.xml.internal.bind.v2.model.nav.Navigator;
import com.sun.xml.internal.bind.v2.model.runtime.RuntimeNonElement;
import com.sun.xml.internal.bind.v2.model.runtime.RuntimeTypeInfoSet;

/**
 * {@link TypeInfoSet} specialized for runtime.
 *
 * @author Kohsuke Kawaguchi
 */
final class RuntimeTypeInfoSetImpl extends TypeInfoSetImpl<Type,Class,Field,Method> implements RuntimeTypeInfoSet {
    public RuntimeTypeInfoSetImpl(AnnotationReader<Type,Class,Field,Method> reader) {
        super(Utils.REFLECTION_NAVIGATOR,reader,RuntimeBuiltinLeafInfoImpl.LEAVES);
    }

    @Override
    protected RuntimeNonElement createAnyType() {
        return RuntimeAnyTypeImpl.theInstance;
    }

    public RuntimeNonElement getTypeInfo( Type type ) {
        return (RuntimeNonElement)super.getTypeInfo(type);
    }

    public RuntimeNonElement getAnyTypeInfo() {
        return (RuntimeNonElement)super.getAnyTypeInfo();
    }

    public RuntimeNonElement getClassInfo(Class clazz) {
        return (RuntimeNonElement)super.getClassInfo(clazz);
    }

    public Map<Class,RuntimeClassInfoImpl> beans() {
        return (Map<Class,RuntimeClassInfoImpl>)super.beans();
    }

    public Map<Type,RuntimeBuiltinLeafInfoImpl<?>> builtins() {
        return (Map<Type,RuntimeBuiltinLeafInfoImpl<?>>)super.builtins();
    }

    public Map<Class,RuntimeEnumLeafInfoImpl<?,?>> enums() {
        return (Map<Class,RuntimeEnumLeafInfoImpl<?,?>>)super.enums();
    }

    public Map<Class,RuntimeArrayInfoImpl> arrays() {
        return (Map<Class,RuntimeArrayInfoImpl>)super.arrays();
    }

    public RuntimeElementInfoImpl getElementInfo(Class scope,QName name) {
        return (RuntimeElementInfoImpl)super.getElementInfo(scope,name);
    }

    public Map<QName,RuntimeElementInfoImpl> getElementMappings(Class scope) {
        return (Map<QName,RuntimeElementInfoImpl>)super.getElementMappings(scope);
    }

    public Iterable<RuntimeElementInfoImpl> getAllElements() {
        return (Iterable<RuntimeElementInfoImpl>)super.getAllElements();
    }
}
