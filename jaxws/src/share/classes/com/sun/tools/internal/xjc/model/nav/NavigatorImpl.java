/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.tools.internal.xjc.model.nav;

import java.lang.reflect.Type;
import java.util.Collection;

import com.sun.codemodel.internal.JClass;
import com.sun.xml.internal.bind.v2.model.nav.Navigator;
import com.sun.xml.internal.bind.v2.runtime.Location;

/**
 * {@link Navigator} implementation for XJC.
 *
 * Most of the Navigator methods are used for parsing the model, which doesn't happen
 * in XJC. So Most of the methods aren't really implemented. Implementations should
 * be filled in as needed.
 *
 * @author Kohsuke Kawaguchi
 */
public final class NavigatorImpl implements Navigator<NType,NClass,Void,Void> {
    public static final NavigatorImpl theInstance = new NavigatorImpl();

    private NavigatorImpl() {
    }

    public NClass getSuperClass(NClass nClass) {
        throw new UnsupportedOperationException();
    }

    public NType getBaseClass(NType nt, NClass base) {
        if(nt instanceof EagerNType) {
            EagerNType ent = (EagerNType) nt;
            if (base instanceof EagerNClass) {
                EagerNClass enc = (EagerNClass) base;
                return create(REFLECTION.getBaseClass(ent.t, enc.c));
            }
            // lazy class can never be a base type of an eager type
            return null;
        }
        if (nt instanceof NClassByJClass) {
            NClassByJClass nnt = (NClassByJClass) nt;
            if (base instanceof EagerNClass) {
                EagerNClass enc = (EagerNClass) base;
                return ref(nnt.clazz.getBaseClass(enc.c));
            }
        }

        throw new UnsupportedOperationException();
    }

    public String getClassName(NClass nClass) {
        throw new UnsupportedOperationException();
    }

    public String getTypeName(NType type) {
        return type.fullName();
    }

    public String getClassShortName(NClass nClass) {
        throw new UnsupportedOperationException();
    }

    public Collection<? extends Void> getDeclaredFields(NClass nClass) {
        throw new UnsupportedOperationException();
    }

    public Void getDeclaredField(NClass clazz, String fieldName) {
        throw new UnsupportedOperationException();
    }

    public Collection<? extends Void> getDeclaredMethods(NClass nClass) {
        throw new UnsupportedOperationException();
    }

    public NClass getDeclaringClassForField(Void aVoid) {
        throw new UnsupportedOperationException();
    }

    public NClass getDeclaringClassForMethod(Void aVoid) {
        throw new UnsupportedOperationException();
    }

    public NType getFieldType(Void aVoid) {
        throw new UnsupportedOperationException();
    }

    public String getFieldName(Void aVoid) {
        throw new UnsupportedOperationException();
    }

    public String getMethodName(Void aVoid) {
        throw new UnsupportedOperationException();
    }

    public NType getReturnType(Void aVoid) {
        throw new UnsupportedOperationException();
    }

    public NType[] getMethodParameters(Void aVoid) {
        throw new UnsupportedOperationException();
    }

    public boolean isStaticMethod(Void aVoid) {
        throw new UnsupportedOperationException();
    }

    public boolean isSubClassOf(NType sub, NType sup) {
        throw new UnsupportedOperationException();
    }

    public NClass ref(Class c) {
        return create(c);
    }

    public NClass ref(JClass c) {
        if(c==null)     return null;
        return new NClassByJClass(c);
    }

    public NType use(NClass nc) {
        return nc;
    }

    public NClass asDecl(NType nt) {
        if(nt instanceof NClass)
            return (NClass)nt;
        else
            return null;
    }

    public NClass asDecl(Class c) {
        return ref(c);
    }

    public boolean isArray(NType nType) {
        throw new UnsupportedOperationException();
    }

    public boolean isArrayButNotByteArray(NType t) {
        throw new UnsupportedOperationException();
    }


    public NType getComponentType(NType nType) {
        throw new UnsupportedOperationException();
    }

    public NType getTypeArgument(NType nt, int i) {
        if (nt instanceof EagerNType) {
            EagerNType ent = (EagerNType) nt;
            return create(REFLECTION.getTypeArgument(ent.t,i));
        }
        if (nt instanceof NClassByJClass) {
            NClassByJClass nnt = (NClassByJClass) nt;
            return ref(nnt.clazz.getTypeParameters().get(i));
        }

        throw new UnsupportedOperationException();
    }

    public boolean isParameterizedType(NType nt) {
        if (nt instanceof EagerNType) {
            EagerNType ent = (EagerNType) nt;
            return REFLECTION.isParameterizedType(ent.t);
        }
        if (nt instanceof NClassByJClass) {
            NClassByJClass nnt = (NClassByJClass) nt;
            return nnt.clazz.isParameterized();
        }

        throw new UnsupportedOperationException();
    }

    public boolean isPrimitive(NType type) {
        throw new UnsupportedOperationException();
    }

    public NType getPrimitive(Class primitiveType) {
        return create(primitiveType);
    }


    public static final NType create(Type t) {
        if(t==null)     return null;
        if(t instanceof Class)
            return create((Class)t);

        return new EagerNType(t);
    }

    public static NClass create( Class c ) {
        if(c==null)     return null;
        return new EagerNClass(c);
    }

    /**
     * Creates a {@link NType} representation for a parameterized type
     * {@code RawType&lt;ParamType1,ParamType2,...> }.
     */
    public static NType createParameterizedType( NClass rawType, NType... args ) {
        return new NParameterizedType(rawType,args);
    }

    public static NType createParameterizedType( Class rawType, NType... args ) {
        return new NParameterizedType(create(rawType),args);
    }

    public Location getClassLocation(final NClass c) {
        // not really needed for XJC but doesn't hurt to have one
        return new Location() {
            public String toString() {
                return c.fullName();
            }
        };
    }

    public Location getFieldLocation(Void _) {
        throw new IllegalStateException();
    }

    public Location getMethodLocation(Void _) {
        throw new IllegalStateException();
    }

    public boolean hasDefaultConstructor(NClass nClass) {
        throw new UnsupportedOperationException();
    }

    public boolean isStaticField(Void aVoid) {
        throw new IllegalStateException();
    }

    public boolean isPublicMethod(Void aVoid) {
        throw new IllegalStateException();
    }

    public boolean isPublicField(Void aVoid) {
        throw new IllegalStateException();
    }

    public boolean isEnum(NClass c) {
        return isSubClassOf(c,create(Enum.class));
    }

    public <T> NType erasure(NType type) {
        if(type instanceof NParameterizedType) {
            NParameterizedType pt = (NParameterizedType) type;
            return pt.rawType;
        }
        return type;
    }

    public boolean isAbstract(NClass clazz) {
        return clazz.isAbstract();
    }

    /**
     * @deprecated
     *      no class generated by XJC is final.
     */
    public boolean isFinal(NClass clazz) {
        return false;
    }

    public Void[] getEnumConstants(NClass clazz) {
        throw new UnsupportedOperationException();
    }

    public NType getVoidType() {
        return ref(void.class);
    }

    public String getPackageName(NClass clazz) {
        // TODO: implement this method later
        throw new UnsupportedOperationException();
    }

    public NClass findClass(String className, NClass referencePoint) {
        // TODO: implement this method later
        throw new UnsupportedOperationException();
    }

    public boolean isBridgeMethod(Void method) {
        throw new UnsupportedOperationException();
    }

    public boolean isOverriding(Void method,NClass clazz) {
        throw new UnsupportedOperationException();
    }

    public boolean isInterface(NClass clazz) {
        throw new UnsupportedOperationException();
    }

    public boolean isTransient(Void f) {
        throw new UnsupportedOperationException();
    }

    public boolean isInnerClass(NClass clazz) {
        throw new UnsupportedOperationException();
    }
}
