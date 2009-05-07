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
package com.sun.xml.internal.bind.v2.model.nav;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.Collection;

import com.sun.xml.internal.bind.v2.runtime.Location;

/**
 * {@link Navigator} implementation for {@code java.lang.reflect}.
 *
 */
public final class ReflectionNavigator implements Navigator<Type,Class,Field,Method> {
    /**
     * Singleton.
     *
     * Use {@link Navigator#REFLECTION}
     */
    ReflectionNavigator() {}

    public Class getSuperClass(Class clazz) {
        if(clazz==Object.class) return null;
        Class sc = clazz.getSuperclass();
        if(sc==null)    sc=Object.class;        // error recovery
        return sc;
    }

    private static final TypeVisitor<Type,Class> baseClassFinder = new TypeVisitor<Type,Class>() {
        public Type onClass(Class c, Class sup) {
            // t is a raw type
            if(sup==c)
                return sup;

            Type r;

            Type sc = c.getGenericSuperclass();
            if(sc!=null) {
                r = visit(sc,sup);
                if(r!=null)     return r;
            }

            for( Type i : c.getGenericInterfaces() ) {
                r = visit(i,sup);
                if(r!=null)  return r;
            }

            return null;
        }

        public Type onParameterizdType(ParameterizedType p, Class sup) {
            Class raw = (Class) p.getRawType();
            if(raw==sup) {
                // p is of the form sup<...>
                return p;
            } else {
                // recursively visit super class/interfaces
                Type r = raw.getGenericSuperclass();
                if(r!=null)
                    r = visit(bind(r,raw,p),sup);
                if(r!=null)
                    return r;
                for( Type i : raw.getGenericInterfaces() ) {
                    r = visit(bind(i,raw,p),sup);
                    if(r!=null)  return r;
                }
                return null;
            }
        }

        public Type onGenericArray(GenericArrayType g, Class sup) {
            // not clear what I should do here
            return null;
        }

        public Type onVariable(TypeVariable v, Class sup) {
            return visit(v.getBounds()[0],sup);
        }

        public Type onWildcard(WildcardType w, Class sup) {
            // not clear what I should do here
            return null;
        }

        /**
         * Replaces the type variables in {@code t} by its actual arguments.
         *
         * @param decl
         *      provides a list of type variables. See {@link GenericDeclaration#getTypeParameters()}
         * @param args
         *      actual arguments. See {@link ParameterizedType#getActualTypeArguments()}
         */
        private Type bind( Type t, GenericDeclaration decl, ParameterizedType args ) {
            return binder.visit(t,new BinderArg(decl,args.getActualTypeArguments()));
        }
    };

    private static class BinderArg {
        final TypeVariable[] params;
        final Type[] args;

        BinderArg(TypeVariable[] params, Type[] args) {
            this.params = params;
            this.args = args;
            assert params.length==args.length;
        }

        public BinderArg( GenericDeclaration decl, Type[] args ) {
            this(decl.getTypeParameters(),args);
        }

        Type replace( TypeVariable v ) {
            for(int i=0; i<params.length; i++)
                if(params[i]==v)
                    return args[i];
            return v;   // this is a free variable
        }
    }
    private static final TypeVisitor<Type,BinderArg> binder = new TypeVisitor<Type,BinderArg>() {
        public Type onClass(Class c, BinderArg args) {
            return c;
        }

        public Type onParameterizdType(ParameterizedType p, BinderArg args) {
            Type[] params = p.getActualTypeArguments();

            boolean different = false;
            for( int i=0; i<params.length; i++ ) {
                Type t = params[i];
                params[i] = visit(t,args);
                different |= t!=params[i];
            }

            Type newOwner = p.getOwnerType();
            if(newOwner!=null)
                newOwner = visit(newOwner,args);
            different |= p.getOwnerType()!=newOwner;

            if(!different)  return p;

            return new ParameterizedTypeImpl( (Class<?>)p.getRawType(), params, newOwner );
        }

        public Type onGenericArray(GenericArrayType g, BinderArg types) {
            Type c = visit(g.getGenericComponentType(),types);
            if(c==g.getGenericComponentType())  return g;

            return new GenericArrayTypeImpl(c);
        }

        public Type onVariable(TypeVariable v, BinderArg types) {
            return types.replace(v);
        }

        public Type onWildcard(WildcardType w, BinderArg types) {
            // TODO: this is probably still incorrect
            // bind( "? extends T" ) with T= "? extends Foo" should be "? extends Foo",
            // not "? extends (? extends Foo)"
            Type[] lb = w.getLowerBounds();
            Type[] ub = w.getUpperBounds();
            boolean diff = false;

            for( int i=0; i<lb.length; i++ ) {
                Type t = lb[i];
                lb[i] = visit(t,types);
                diff |= (t!=lb[i]);
            }

            for( int i=0; i<ub.length; i++ ) {
                Type t = ub[i];
                ub[i] = visit(t,types);
                diff |= (t!=ub[i]);
            }

            if(!diff)       return w;

            return new WildcardTypeImpl(lb,ub);
        }
    };


    public Type getBaseClass(Type t, Class sup) {
        return baseClassFinder.visit(t,sup);
    }

    public String getClassName(Class clazz) {
        return clazz.getName();
    }

    public String getTypeName(Type type) {
        if (type instanceof Class) {
            Class c = (Class) type;
            if(c.isArray())
                return getTypeName(c.getComponentType())+"[]";
            return c.getName();
        }
        return type.toString();
    }

    public String getClassShortName(Class clazz) {
        return clazz.getSimpleName();
    }

    public Collection<? extends Field> getDeclaredFields(Class clazz) {
        return Arrays.asList(clazz.getDeclaredFields());
    }

    public Field getDeclaredField(Class clazz, String fieldName) {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    public Collection<? extends Method> getDeclaredMethods(Class clazz) {
        return Arrays.asList(clazz.getDeclaredMethods());
    }

    public Class getDeclaringClassForField(Field field) {
        return field.getDeclaringClass();
    }

    public Class getDeclaringClassForMethod(Method method) {
        return method.getDeclaringClass();
    }

    public Type getFieldType(Field field) {
        return fix(field.getGenericType());
    }

    public String getFieldName(Field field) {
        return field.getName();
    }

    public String getMethodName(Method method) {
        return method.getName();
    }

    public Type getReturnType(Method method) {
        return fix(method.getGenericReturnType());
    }

    public Type[] getMethodParameters(Method method) {
        return method.getGenericParameterTypes();
    }

    public boolean isStaticMethod(Method method) {
        return Modifier.isStatic(method.getModifiers());
    }

    public boolean isSubClassOf(Type sub, Type sup) {
        return erasure(sup).isAssignableFrom(erasure(sub));
    }

    public Class ref(Class c) {
        return c;
    }

    public Class use(Class c) {
        return c;
    }

    public Class asDecl(Type t) {
        return erasure(t);
    }

    public Class asDecl(Class c) {
        return c;
    }


    /**
     * Implements the logic for {@link #erasure(Type)}.
     */
    private static final TypeVisitor<Class,Void> eraser = new TypeVisitor<Class,Void>() {
        public Class onClass(Class c,Void _) {
            return c;
        }

        public Class onParameterizdType(ParameterizedType p,Void _) {
            // TODO: why getRawType returns Type? not Class?
            return visit(p.getRawType(),null);
        }

        public Class onGenericArray(GenericArrayType g,Void _) {
            return Array.newInstance(
                visit(g.getGenericComponentType(),null),
                0 ).getClass();
        }

        public Class onVariable(TypeVariable v,Void _) {
            return visit(v.getBounds()[0],null);
        }

        public Class onWildcard(WildcardType w,Void _) {
            return visit(w.getUpperBounds()[0],null);
        }
    };

    /**
     * Returns the runtime representation of the given type.
     *
     * This corresponds to the notion of the erasure in JSR-14.
     *
     * <p>
     * Because of the difference in the way APT and the Java reflection
     * treats primitive type and array type, we can't define this method
     * on {@link Navigator}.
     *
     * <p>
     * It made me realize how difficult it is to define the common navigation
     * layer for two different underlying reflection library. The other way
     * is to throw away the entire parameterization and go to the wrapper approach.
     */
    public <T> Class<T> erasure(Type t) {
        return eraser.visit(t,null);
    }

    public boolean isAbstract(Class clazz) {
        return Modifier.isAbstract(clazz.getModifiers());
    }

    public boolean isFinal(Class clazz) {
        return Modifier.isFinal(clazz.getModifiers());
    }

    /**
     * Returns the {@link Type} object that represents {@code clazz&lt;T1,T2,T3>}.
     */
    public Type createParameterizedType( Class rawType, Type... arguments ) {
        return new ParameterizedTypeImpl(rawType,arguments,null);
    }

    public boolean isArray(Type t) {
        if (t instanceof Class) {
            Class c = (Class) t;
            return c.isArray();
        }
        if(t instanceof GenericArrayType)
            return true;
        return false;
    }

    public boolean isArrayButNotByteArray(Type t) {
        if (t instanceof Class) {
            Class c = (Class) t;
            return c.isArray() && c!=byte[].class;
        }
        if(t instanceof GenericArrayType) {
            t = ((GenericArrayType)t).getGenericComponentType();
            return t!=Byte.TYPE;
        }
        return false;
    }


    public Type getComponentType(Type t) {
        if (t instanceof Class) {
            Class c = (Class) t;
            return c.getComponentType();
        }
        if(t instanceof GenericArrayType)
            return ((GenericArrayType)t).getGenericComponentType();

        throw new IllegalArgumentException();
    }

    public Type getTypeArgument(Type type, int i) {
        if (type instanceof ParameterizedType) {
            ParameterizedType p = (ParameterizedType) type;
            return fix(p.getActualTypeArguments()[i]);
        } else
            throw new IllegalArgumentException();
    }

    public boolean isParameterizedType(Type type) {
        return type instanceof ParameterizedType;
    }

    public boolean isPrimitive(Type type) {
        if (type instanceof Class) {
            Class c = (Class) type;
            return c.isPrimitive();
        }
        return false;
    }

    public Type getPrimitive(Class primitiveType) {
        assert primitiveType.isPrimitive();
        return primitiveType;
    }

    public Location getClassLocation(final Class clazz) {
        return new Location() {
            public String toString() {
                return clazz.getName();
            }
        };
    }

    public Location getFieldLocation(final Field field) {
        return new Location() {
            public String toString() {
                return field.toString();
            }
        };
    }

    public Location getMethodLocation(final Method method) {
        return new Location() {
            public String toString() {
                return method.toString();
            }
        };
    }

    public boolean hasDefaultConstructor(Class c) {
        try {
            c.getDeclaredConstructor();
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    public boolean isStaticField(Field field) {
        return Modifier.isStatic(field.getModifiers());
    }

    public boolean isPublicMethod(Method method) {
        return Modifier.isPublic(method.getModifiers());
    }

    public boolean isPublicField(Field field) {
        return Modifier.isPublic(field.getModifiers());
    }

    public boolean isEnum(Class c) {
        return Enum.class.isAssignableFrom(c);
    }

    public Field[] getEnumConstants(Class clazz) {
        try {
            Object[] values = clazz.getEnumConstants();
            Field[] fields = new Field[values.length];
            for( int i=0; i<values.length; i++ ) {
                fields[i] = clazz.getField(((Enum)values[i]).name());
            }
            return fields;
        } catch (NoSuchFieldException e) {
            // impossible
            throw new NoSuchFieldError(e.getMessage());
        }
    }

    public Type getVoidType() {
        return Void.class;
    }

    public String getPackageName(Class clazz) {
        String name = clazz.getName();
        int idx = name.lastIndexOf('.');
        if(idx<0)   return "";
        else        return name.substring(0,idx);
    }

    public Class findClass(String className, Class referencePoint) {
        try {
        ClassLoader cl = referencePoint.getClassLoader();
        if(cl==null)  cl = ClassLoader.getSystemClassLoader();
            return cl.loadClass(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public boolean isBridgeMethod(Method method) {
        return method.isBridge();
    }

    public boolean isOverriding(Method method, Class base) {
        // this isn't actually correct,
        // as the JLS considers
        // class Derived extends Base<Integer> {
        //   Integer getX() { ... }
        // }
        // class Base<T> {
        //   T getX() { ... }
        // }
        // to be overrided. Handling this correctly needs a careful implementation

        String name = method.getName();
        Class[] params = method.getParameterTypes();

        while(base!=null) {
            try {
                if(base.getDeclaredMethod(name,params)!=null)
                    return true;
            } catch (NoSuchMethodException e) {
                // recursively go into the base class
            }

            base = base.getSuperclass();
        }

        return false;
    }

    public boolean isInterface(Class clazz) {
        return clazz.isInterface();
    }

    public boolean isTransient(Field f) {
        return Modifier.isTransient(f.getModifiers());
    }

    public boolean isInnerClass(Class clazz) {
        return clazz.getEnclosingClass()!=null;
    }


    /**
     * JDK 5.0 has a bug of createing {@link GenericArrayType} where it shouldn't.
     * fix that manually to work around the problem.
     *
     * See bug 6202725.
     */
    private Type fix(Type t) {
        if(!(t instanceof GenericArrayType))
            return t;

        GenericArrayType gat = (GenericArrayType) t;
        if(gat.getGenericComponentType() instanceof Class) {
            Class c = (Class) gat.getGenericComponentType();
            return Array.newInstance(c,0).getClass();
        }

        return t;
    }
}
