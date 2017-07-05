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
package com.sun.tools.internal.jxc.model.nav;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sun.istack.internal.tools.APTTypeVisitor;
import com.sun.mirror.apt.AnnotationProcessorEnvironment;
import com.sun.mirror.declaration.ClassDeclaration;
import com.sun.mirror.declaration.ConstructorDeclaration;
import com.sun.mirror.declaration.Declaration;
import com.sun.mirror.declaration.EnumConstantDeclaration;
import com.sun.mirror.declaration.EnumDeclaration;
import com.sun.mirror.declaration.FieldDeclaration;
import com.sun.mirror.declaration.InterfaceDeclaration;
import com.sun.mirror.declaration.MemberDeclaration;
import com.sun.mirror.declaration.MethodDeclaration;
import com.sun.mirror.declaration.Modifier;
import com.sun.mirror.declaration.ParameterDeclaration;
import com.sun.mirror.declaration.TypeDeclaration;
import com.sun.mirror.type.ArrayType;
import com.sun.mirror.type.ClassType;
import com.sun.mirror.type.DeclaredType;
import com.sun.mirror.type.InterfaceType;
import com.sun.mirror.type.PrimitiveType;
import com.sun.mirror.type.ReferenceType;
import com.sun.mirror.type.TypeMirror;
import com.sun.mirror.type.TypeVariable;
import com.sun.mirror.type.VoidType;
import com.sun.mirror.type.WildcardType;
import com.sun.mirror.util.Declarations;
import com.sun.mirror.util.SourcePosition;
import com.sun.mirror.util.TypeVisitor;
import com.sun.mirror.util.Types;
import com.sun.xml.internal.bind.v2.model.nav.Navigator;
import com.sun.xml.internal.bind.v2.runtime.Location;

/**
 * {@link Navigator} implementation for APT.
 *
 * TODO: check the spec on how generics are supposed to be handled
 *
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */
public class APTNavigator implements Navigator<TypeMirror,TypeDeclaration,FieldDeclaration,MethodDeclaration> {

    private final AnnotationProcessorEnvironment env;

    private final PrimitiveType primitiveByte;

    public APTNavigator(AnnotationProcessorEnvironment env) {
        this.env = env;
        this.primitiveByte = env.getTypeUtils().getPrimitiveType(PrimitiveType.Kind.BYTE);
    }

    public TypeDeclaration getSuperClass(TypeDeclaration t) {
        if (t instanceof ClassDeclaration) {
            ClassDeclaration c = (ClassDeclaration) t;
            ClassType sup = c.getSuperclass();
            if(sup!=null)
                return sup.getDeclaration();
            else
                return null;
        }
        return env.getTypeDeclaration(Object.class.getName());
    }

    public TypeMirror getBaseClass(TypeMirror type, TypeDeclaration sup) {
        return baseClassFinder.apply(type,sup);
    }

    public String getClassName(TypeDeclaration t) {
        return t.getQualifiedName();
    }

    public String getTypeName(TypeMirror typeMirror) {
        return typeMirror.toString();
    }

    public String getClassShortName(TypeDeclaration t) {
        return t.getSimpleName();
    }

    public Collection<FieldDeclaration> getDeclaredFields(TypeDeclaration c) {
        List<FieldDeclaration> l = new ArrayList<FieldDeclaration>(c.getFields());
        return sort(l);
    }

    public FieldDeclaration getDeclaredField(TypeDeclaration clazz, String fieldName) {
        for( FieldDeclaration fd : clazz.getFields() ) {
            if(fd.getSimpleName().equals(fieldName))
                return fd;
        }
        return null;
    }

    public Collection<MethodDeclaration> getDeclaredMethods(TypeDeclaration c) {
        List<MethodDeclaration> l = new ArrayList<MethodDeclaration>(c.getMethods());
        return sort(l);
    }

    private <A extends Declaration> List<A> sort(List<A> l) {
        if(l.isEmpty())     return l;

        // APT supports the operation mode where it creates Declarations from
        // a class file, in which case the source position is not available
        // use that as a key to sort them correctly. This isn't "correct" in
        // the sense that it relies on undocumented behavior of APT where
        // it returns declarations in the reverse order, but this makes things work.
        SourcePosition pos = l.get(0).getPosition();
        if(pos!=null)
            Collections.sort(l,SOURCE_POS_COMPARATOR);
        else
            Collections.reverse(l);
        return l;
    }

    public ClassDeclaration getDeclaringClassForField(FieldDeclaration f) {
        return (ClassDeclaration)f.getDeclaringType();
    }

    public ClassDeclaration getDeclaringClassForMethod(MethodDeclaration m) {
        return (ClassDeclaration)m.getDeclaringType();
    }

    public TypeMirror getFieldType(FieldDeclaration f) {
        return f.getType();
    }

    public String getFieldName(FieldDeclaration f) {
        return f.getSimpleName();
    }

    public String getMethodName(MethodDeclaration m) {
        return m.getSimpleName();
    }

    public TypeMirror getReturnType(MethodDeclaration m) {
        return m.getReturnType();
    }

    public TypeMirror[] getMethodParameters(MethodDeclaration m) {
        Collection<ParameterDeclaration> ps = m.getParameters();
        TypeMirror[] r = new TypeMirror[ps.size()];
        int i=0;
        for( ParameterDeclaration p : ps )
            r[i++] = p.getType();
        return r;
    }

    public boolean isStaticMethod(MethodDeclaration m) {
        return hasModifier(m, Modifier.STATIC);
    }

    private boolean hasModifier(Declaration d, Modifier mod) {
        return d.getModifiers().contains(mod);
    }

    public boolean isSubClassOf(TypeMirror sub, TypeMirror sup) {
        if(sup==DUMMY)
            // see ref(). if the sub type is known to APT,
            // its base class must be known. Thus if the sup is DUMMY,
            // it cannot possibly be the super type.
            return false;
        return env.getTypeUtils().isSubtype(sub,sup);
    }

    private String getSourceClassName(Class clazz) {
        Class<?> d = clazz.getDeclaringClass();
        if(d==null)
            return clazz.getName();
        else {
            String shortName = clazz.getName().substring(d.getName().length()+1/*for $*/);
            return getSourceClassName(d)+'.'+shortName;
        }
    }

    public TypeMirror ref(Class c) {
        if(c.isArray())
            return env.getTypeUtils().getArrayType( ref(c.getComponentType()) );
        if(c.isPrimitive())
            return getPrimitive(c);
        TypeDeclaration t = env.getTypeDeclaration(getSourceClassName(c));
        // APT only operates on a set of classes used in the compilation,
        // and it won't recognize additional classes (even if they are visible from javac)
        // and return null.
        //
        // this is causing a problem where we check if a type is collection.
        // so until the problem is fixed in APT, work around the issue
        // by returning a dummy token
        if(t==null)
            return DUMMY;
        return env.getTypeUtils().getDeclaredType(t);
    }

    public TypeMirror use(TypeDeclaration t) {
        assert t!=null;
        return env.getTypeUtils().getDeclaredType(t);
    }

    public TypeDeclaration asDecl(TypeMirror m) {
        m = env.getTypeUtils().getErasure(m);
        if (m instanceof DeclaredType) {
            DeclaredType d = (DeclaredType) m;
            return d.getDeclaration();
        } else
            return null;
    }

    public TypeDeclaration asDecl(Class c) {
        return env.getTypeDeclaration(getSourceClassName(c));
    }

    public <T> TypeMirror erasure(TypeMirror t) {
        Types tu = env.getTypeUtils();
        t = tu.getErasure(t);
        if(t instanceof DeclaredType) {
            DeclaredType dt = (DeclaredType)t;
            if(!dt.getActualTypeArguments().isEmpty())
            return tu.getDeclaredType(dt.getDeclaration());
        }
        return t;
    }

    public boolean isAbstract(TypeDeclaration clazz) {
        return hasModifier(clazz,Modifier.ABSTRACT);
    }

    public boolean isFinal(TypeDeclaration clazz) {
        return hasModifier(clazz,Modifier.FINAL);
    }

    public FieldDeclaration[] getEnumConstants(TypeDeclaration clazz) {
        EnumDeclaration ed = (EnumDeclaration) clazz;
        Collection<EnumConstantDeclaration> constants = ed.getEnumConstants();
        return constants.toArray(new EnumConstantDeclaration[constants.size()]);
    }

    public TypeMirror getVoidType() {
        return env.getTypeUtils().getVoidType();
    }

    public String getPackageName(TypeDeclaration clazz) {
        return clazz.getPackage().getQualifiedName();
    }

    public TypeDeclaration findClass(String className, TypeDeclaration referencePoint) {
        return env.getTypeDeclaration(className);
    }

    public boolean isBridgeMethod(MethodDeclaration method) {
        return method.getModifiers().contains(Modifier.VOLATILE);
    }

    public boolean isOverriding(MethodDeclaration method, TypeDeclaration base) {
        ClassDeclaration sc = (ClassDeclaration) base;

        Declarations declUtil = env.getDeclarationUtils();

        while(true) {
            for (MethodDeclaration m : sc.getMethods()) {
                if(declUtil.overrides(method,m))
                    return true;
            }

            if(sc.getSuperclass()==null)
                return false;
            sc = sc.getSuperclass().getDeclaration();
        }
    }

    public boolean isInterface(TypeDeclaration clazz) {
        return clazz instanceof InterfaceDeclaration;
    }

    public boolean isTransient(FieldDeclaration f) {
        return f.getModifiers().contains(Modifier.TRANSIENT);
    }

    public boolean isInnerClass(TypeDeclaration clazz) {
        return clazz.getDeclaringType()!=null;
    }

    public boolean isArray(TypeMirror t) {
        return t instanceof ArrayType;
    }

    public boolean isArrayButNotByteArray(TypeMirror t) {
        if(!isArray(t))
            return false;

        ArrayType at = (ArrayType) t;
        TypeMirror ct = at.getComponentType();

        return !ct.equals(primitiveByte);
    }

    public TypeMirror getComponentType(TypeMirror t) {
        if (t instanceof ArrayType) {
            ArrayType at = (ArrayType) t;
            return at.getComponentType();
        }

        throw new IllegalArgumentException();
    }

    public TypeMirror getTypeArgument(TypeMirror typeMirror, int i) {
        if (typeMirror instanceof DeclaredType){
            DeclaredType d = (DeclaredType)typeMirror;
            TypeMirror[] args = d.getActualTypeArguments().toArray(new TypeMirror[0]);
            return args[i];
        } else throw  new IllegalArgumentException();
    }

    public boolean isParameterizedType(TypeMirror t) {
        if (t instanceof DeclaredType) {
            DeclaredType d = (DeclaredType) t;
            return !d.getActualTypeArguments().isEmpty();
        }
        return false;
    }

    public boolean isPrimitive(TypeMirror t) {
        return t instanceof PrimitiveType;
    }

    private static final Map<Class,PrimitiveType.Kind> primitives = new HashMap<Class,PrimitiveType.Kind>();

    static {
        primitives.put(Integer.TYPE,    PrimitiveType.Kind.INT);
        primitives.put(Byte.TYPE,       PrimitiveType.Kind.BYTE);
        primitives.put(Float.TYPE,      PrimitiveType.Kind.FLOAT);
        primitives.put(Boolean.TYPE, PrimitiveType.Kind.BOOLEAN);
        primitives.put(Short.TYPE,      PrimitiveType.Kind.SHORT);
        primitives.put(Long.TYPE,      PrimitiveType.Kind.LONG);
        primitives.put(Double.TYPE,      PrimitiveType.Kind.DOUBLE);
        primitives.put(Character.TYPE,      PrimitiveType.Kind.CHAR);

    }

    public TypeMirror getPrimitive(Class primitiveType) {
        assert primitiveType.isPrimitive();
        if(primitiveType==void.class)
            return getVoidType();
        return env.getTypeUtils().getPrimitiveType(primitives.get(primitiveType));
    }

    /**
     * see {@link #ref(Class)}.
     */
    private static final TypeMirror DUMMY = new TypeMirror() {
        public void accept(TypeVisitor v) {
            throw new IllegalStateException();
        }
    };

    /**
     * Implements {@link #getBaseClass}.
     */
    private final APTTypeVisitor<TypeMirror,TypeDeclaration> baseClassFinder = new APTTypeVisitor<TypeMirror,TypeDeclaration>(){
        public TypeMirror onClassType(ClassType type, TypeDeclaration sup) {
            TypeMirror r = onDeclaredType(type,sup);
            if(r!=null)     return r;

            // otherwise recursively apply super class and base types
            if(type.getSuperclass()!=null) {
                r = onClassType(type.getSuperclass(),sup);
                if(r!=null)     return r;
            }

            return null;
        }

        protected TypeMirror onPrimitiveType(PrimitiveType type, TypeDeclaration param) {
            return type;
        }

        protected TypeMirror onVoidType(VoidType type, TypeDeclaration param) {
            return type;
        }

        public TypeMirror onInterfaceType(InterfaceType type, TypeDeclaration sup) {
            return onDeclaredType(type,sup);
        }

        private TypeMirror onDeclaredType(DeclaredType t, TypeDeclaration sup) {
            // t = sup<...>
            if(t.getDeclaration().equals(sup))
                return t;

            for(InterfaceType i : t.getSuperinterfaces()) {
                TypeMirror r = onInterfaceType(i,sup);
                if(r!=null)     return r;
            }

            return null;
        }

        public TypeMirror onTypeVariable(TypeVariable t, TypeDeclaration sup) {
            // we are checking if T (declared as T extends A&B&C) is assignable to sup.
            // so apply bounds recursively.
            for( ReferenceType r : t.getDeclaration().getBounds() ) {
                TypeMirror m = apply(r,sup);
                if(m!=null)     return m;
            }
            return null;
        }

        public TypeMirror onArrayType(ArrayType type, TypeDeclaration sup) {
            // we are checking if t=T[] is assignable to sup.
            // the only case this is allowed is sup=Object,
            // and Object isn't parameterized.
            return null;
        }

        public TypeMirror onWildcard(WildcardType type, TypeDeclaration sup) {
            // we are checking if T (= ? extends A&B&C) is assignable to sup.
            // so apply bounds recursively.
            for( ReferenceType r : type.getLowerBounds() ) {
                TypeMirror m = apply(r,sup);
                if(m!=null)     return m;
            }
            return null;
        }
    } ;


    public Location getClassLocation(TypeDeclaration decl) {
        return getLocation(decl.getQualifiedName(),decl.getPosition());
    }

    public Location getFieldLocation(FieldDeclaration decl) {
        return getLocation(decl);
    }

    public Location getMethodLocation(MethodDeclaration decl) {
        return getLocation(decl);
    }

    public boolean hasDefaultConstructor(TypeDeclaration t) {
        if(!(t instanceof ClassDeclaration))
            return false;

        ClassDeclaration c = (ClassDeclaration) t;
        for( ConstructorDeclaration init : c.getConstructors() ) {
            if(init.getParameters().isEmpty())
                return true;
        }
        return false;
    }

    public boolean isStaticField(FieldDeclaration f) {
        return hasModifier(f,Modifier.STATIC);
    }

    public boolean isPublicMethod(MethodDeclaration m) {
        return hasModifier(m,Modifier.PUBLIC);
    }

    public boolean isPublicField(FieldDeclaration f) {
        return hasModifier(f,Modifier.PUBLIC);
    }

    public boolean isEnum(TypeDeclaration t) {
        return t instanceof EnumDeclaration;
    }

    private Location getLocation(MemberDeclaration decl) {
        return getLocation(decl.getDeclaringType().getQualifiedName()+'.'+decl.getSimpleName(),decl.getPosition());
    }

    private Location getLocation(final String name, final SourcePosition sp) {
        return new Location() {
            public String toString() {
                if(sp==null)
                    return name+" (Unknown Source)";
                // just like stack trace, we just print the file name and
                // not the whole path. The idea is that the pakage name should
                // provide enough clue on which directory it lives.
                return name+'('+sp.file().getName()+':'+sp.line()+')';
            }
        };
    }

    /**
     * Comparator that uses the source position
     */
    private static final Comparator<Declaration> SOURCE_POS_COMPARATOR = new Comparator<Declaration>() {
        public int compare(Declaration d1, Declaration d2) {
            if (d1 == d2)
                return 0;

            SourcePosition p1 = d1.getPosition();
            SourcePosition p2 = d2.getPosition();

            if (p1 == null) {
                return (p2 == null) ? 0 : 1;
            } else {
                if (p2 == null)
                    return -1;

                int fileComp = p1.file().compareTo(p2.file());
                if (fileComp == 0) {
                    long diff = (long) p1.line() - (long) p2.line();
                    if (diff == 0) {
                        diff = Long.signum((long) p1.column() - (long) p2.column());
                        if (diff != 0)
                            return (int) diff;
                        else {
                            // declarations may be two
                            // compiler-generated members with the
                            // same source position
                            return (Long.signum((long) System.identityHashCode(d1) -
                                    (long) System.identityHashCode(d2)));
                        }
                    } else
                        return (diff < 0) ? -1 : 1;
                } else
                    return fileComp;
            }
        }
    };
}
