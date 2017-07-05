/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.jxc.model.nav;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import com.sun.xml.internal.bind.v2.model.nav.Navigator;
import com.sun.xml.internal.bind.v2.runtime.Location;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleTypeVisitor6;
import javax.lang.model.util.Types;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * {@link Navigator} implementation for annotation processing.
 * TODO: check the spec on how generics are supposed to be handled
 *
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */
public final class ApNavigator implements Navigator<TypeMirror, TypeElement, VariableElement, ExecutableElement> {

    private final ProcessingEnvironment env;

    private final PrimitiveType primitiveByte;

    public ApNavigator(ProcessingEnvironment env) {
        this.env = env;
        this.primitiveByte = env.getTypeUtils().getPrimitiveType(TypeKind.BYTE);
    }

    public TypeElement getSuperClass(TypeElement typeElement) {
        if (typeElement.getKind().equals(ElementKind.CLASS)) {
            TypeMirror sup = typeElement.getSuperclass();
            if (!sup.getKind().equals(TypeKind.NONE))
                return (TypeElement) ((DeclaredType) sup).asElement();
            else
                return null;
        }
        return env.getElementUtils().getTypeElement(Object.class.getName());
    }

    public TypeMirror getBaseClass(TypeMirror type, TypeElement sup) {
        return baseClassFinder.visit(type, sup);
    }

    public String getClassName(TypeElement t) {
        return t.getQualifiedName().toString();
    }

    public String getTypeName(TypeMirror typeMirror) {
        return typeMirror.toString();
    }

    public String getClassShortName(TypeElement t) {
        return t.getSimpleName().toString();
    }

    public Collection<VariableElement> getDeclaredFields(TypeElement typeElement) {
        return ElementFilter.fieldsIn(typeElement.getEnclosedElements());
    }

    public VariableElement getDeclaredField(TypeElement clazz, String fieldName) {
        for (VariableElement fd : ElementFilter.fieldsIn(clazz.getEnclosedElements())) {
            if (fd.getSimpleName().toString().equals(fieldName))
                return fd;
        }
        return null;
    }

    public Collection<ExecutableElement> getDeclaredMethods(TypeElement typeElement) {
        return ElementFilter.methodsIn(typeElement.getEnclosedElements());
    }

    public TypeElement getDeclaringClassForField(VariableElement f) {
        return (TypeElement) f.getEnclosingElement();
    }

    public TypeElement getDeclaringClassForMethod(ExecutableElement m) {
        return (TypeElement) m.getEnclosingElement();
    }

    public TypeMirror getFieldType(VariableElement f) {
        return f.asType();
    }

    public String getFieldName(VariableElement f) {
        return f.getSimpleName().toString();
    }

    public String getMethodName(ExecutableElement m) {
        return m.getSimpleName().toString();
    }

    public TypeMirror getReturnType(ExecutableElement m) {
        return m.getReturnType();
    }

    public TypeMirror[] getMethodParameters(ExecutableElement m) {
        Collection<? extends VariableElement> ps = m.getParameters();
        TypeMirror[] r = new TypeMirror[ps.size()];
        int i=0;
        for (VariableElement p : ps)
            r[i++] = p.asType();
        return r;
    }

    public boolean isStaticMethod(ExecutableElement m) {
        return hasModifier(m, Modifier.STATIC);
    }

    public boolean isFinalMethod(ExecutableElement m) {
        return hasModifier(m, Modifier.FINAL);
    }

    private boolean hasModifier(Element d, Modifier mod) {
        return d.getModifiers().contains(mod);
    }

    public boolean isSubClassOf(TypeMirror sub, TypeMirror sup) {
        if(sup==DUMMY)
        // see ref(). if the sub type is known to Annotation Processing,
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
        TypeElement t = env.getElementUtils().getTypeElement(getSourceClassName(c));
        // Annotation Processing only operates on a set of classes used in the compilation,
        // and it won't recognize additional classes (even if they are visible from javac)
        // and return null.
        //
        // this is causing a problem where we check if a type is collection.
        // so until the problem is fixed in Annotation Processing, work around the issue
        // by returning a dummy token
        // TODO: check if this is still valid
        if(t==null)
            return DUMMY;
        return env.getTypeUtils().getDeclaredType(t);
    }

    public TypeMirror use(TypeElement t) {
        assert t != null;
        return env.getTypeUtils().getDeclaredType(t);
    }

    public TypeElement asDecl(TypeMirror m) {
        m = env.getTypeUtils().erasure(m);
        if (m.getKind().equals(TypeKind.DECLARED)) {
            DeclaredType d = (DeclaredType) m;
            return (TypeElement) d.asElement();
        } else
            return null;
    }

    public TypeElement asDecl(Class c) {
        return env.getElementUtils().getTypeElement(getSourceClassName(c));
    }

    public TypeMirror erasure(TypeMirror t) {
        Types tu = env.getTypeUtils();
        t = tu.erasure(t);
        if (t.getKind().equals(TypeKind.DECLARED)) {
            DeclaredType dt = (DeclaredType)t;
            if (!dt.getTypeArguments().isEmpty())
                return tu.getDeclaredType((TypeElement) dt.asElement());
        }
        return t;
    }

    public boolean isAbstract(TypeElement clazz) {
        return hasModifier(clazz,Modifier.ABSTRACT);
    }

    public boolean isFinal(TypeElement clazz) {
        return hasModifier(clazz, Modifier.FINAL);
    }

    public VariableElement[] getEnumConstants(TypeElement clazz) {
        List<? extends Element> elements = env.getElementUtils().getAllMembers(clazz);
        Collection<VariableElement> constants = new HashSet<VariableElement>();
        for (Element element : elements) {
            if (element.getKind().equals(ElementKind.ENUM_CONSTANT)) {
                constants.add((VariableElement) element);
            }
        }
        return constants.toArray(new VariableElement[constants.size()]);
    }

    public TypeMirror getVoidType() {
        return env.getTypeUtils().getNoType(TypeKind.VOID);
    }

    public String getPackageName(TypeElement clazz) {
        return env.getElementUtils().getPackageOf(clazz).getQualifiedName().toString();
    }

    @Override
    public TypeElement loadObjectFactory(TypeElement referencePoint, String packageName) {
        return env.getElementUtils().getTypeElement(packageName + ".ObjectFactory");
    }

    public boolean isBridgeMethod(ExecutableElement method) {
        return method.getModifiers().contains(Modifier.VOLATILE);
    }

    public boolean isOverriding(ExecutableElement method, TypeElement base) {
        Elements elements = env.getElementUtils();

        while (true) {
            for (ExecutableElement m : ElementFilter.methodsIn(elements.getAllMembers(base))) {
                if (elements.overrides(method, m, base))
                    return true;
            }

            if (base.getSuperclass().getKind().equals(TypeKind.NONE))
                return false;
            base = (TypeElement) env.getTypeUtils().asElement(base.getSuperclass());
        }
    }

    public boolean isInterface(TypeElement clazz) {
        return clazz.getKind().isInterface();
    }

    public boolean isTransient(VariableElement f) {
        return f.getModifiers().contains(Modifier.TRANSIENT);
    }

    public boolean isInnerClass(TypeElement clazz) {
        return clazz.getEnclosingElement() != null && !clazz.getModifiers().contains(Modifier.STATIC);
    }

    @Override
    public boolean isSameType(TypeMirror t1, TypeMirror t2) {
        return env.getTypeUtils().isSameType(t1, t2);
    }

    public boolean isArray(TypeMirror type) {
        return type != null && type.getKind().equals(TypeKind.ARRAY);
    }

    public boolean isArrayButNotByteArray(TypeMirror t) {
        if(!isArray(t))
            return false;

        ArrayType at = (ArrayType) t;
        TypeMirror ct = at.getComponentType();

        return !ct.equals(primitiveByte);
    }

    public TypeMirror getComponentType(TypeMirror t) {
        if (isArray(t)) {
            ArrayType at = (ArrayType) t;
            return at.getComponentType();
        }

        throw new IllegalArgumentException();
    }

    public TypeMirror getTypeArgument(TypeMirror typeMirror, int i) {
        if (typeMirror != null && typeMirror.getKind().equals(TypeKind.DECLARED)) {
            DeclaredType declaredType = (DeclaredType) typeMirror;
            TypeMirror[] args = declaredType.getTypeArguments().toArray(new TypeMirror[declaredType.getTypeArguments().size()]);
            return args[i];
        } else throw new IllegalArgumentException();
    }

    public boolean isParameterizedType(TypeMirror typeMirror) {
        if (typeMirror != null && typeMirror.getKind().equals(TypeKind.DECLARED)) {
            DeclaredType d = (DeclaredType) typeMirror;
            return !d.getTypeArguments().isEmpty();
        }
        return false;
    }

    public boolean isPrimitive(TypeMirror t) {
        return t.getKind().isPrimitive();
    }

    private static final Map<Class, TypeKind> primitives = new HashMap<Class, TypeKind>();

    static {
        primitives.put(Integer.TYPE, TypeKind.INT);
        primitives.put(Byte.TYPE, TypeKind.BYTE);
        primitives.put(Float.TYPE, TypeKind.FLOAT);
        primitives.put(Boolean.TYPE, TypeKind.BOOLEAN);
        primitives.put(Short.TYPE, TypeKind.SHORT);
        primitives.put(Long.TYPE, TypeKind.LONG);
        primitives.put(Double.TYPE, TypeKind.DOUBLE);
        primitives.put(Character.TYPE, TypeKind.CHAR);
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
        @Override
        public <R, P> R accept(TypeVisitor<R, P> v, P p) {
            throw new IllegalStateException();
        }

        @Override
        public TypeKind getKind() {
            throw new IllegalStateException();
        }

//        @Override
        public List<? extends AnnotationMirror> getAnnotationMirrors() {
            throw new IllegalStateException();
        }

//        @Override
        public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
            throw new IllegalStateException();
        }

//        @Override
        public <A extends Annotation> A[] getAnnotationsByType(Class<A> annotationType) {
            throw new IllegalStateException();
        }
    };

    public Location getClassLocation(TypeElement typeElement) {
        Trees trees = Trees.instance(env);
        return getLocation(typeElement.getQualifiedName().toString(), trees.getPath(typeElement));
    }

    public Location getFieldLocation(VariableElement variableElement) {
        return getLocation(variableElement);
    }

    public Location getMethodLocation(ExecutableElement executableElement) {
        return getLocation(executableElement);
    }

    public boolean hasDefaultConstructor(TypeElement t) {
        if (t == null || !t.getKind().equals(ElementKind.CLASS))
            return false;

        for (ExecutableElement init : ElementFilter.constructorsIn(env.getElementUtils().getAllMembers(t))) {
            if (init.getParameters().isEmpty())
                return true;
        }
        return false;
    }

    public boolean isStaticField(VariableElement f) {
        return hasModifier(f,Modifier.STATIC);
    }

    public boolean isPublicMethod(ExecutableElement m) {
        return hasModifier(m,Modifier.PUBLIC);
    }

    public boolean isPublicField(VariableElement f) {
        return hasModifier(f,Modifier.PUBLIC);
    }

    public boolean isEnum(TypeElement t) {
        return t != null && t.getKind().equals(ElementKind.ENUM);
    }

    private Location getLocation(Element element) {
        Trees trees = Trees.instance(env);
        return getLocation(
                ((TypeElement) element.getEnclosingElement()).getQualifiedName() + "." + element.getSimpleName(),
                trees.getPath(element)
        );
    }

    private Location getLocation(final String name, final TreePath treePath) {
        return new Location() {
            public String toString() {
                if (treePath == null)
                    return name + " (Unknown Source)";
                // just like stack trace, we just print the file name and
                // not the whole path. The idea is that the package name should
                // provide enough clue on which directory it lives.
                CompilationUnitTree compilationUnit = treePath.getCompilationUnit();
                Trees trees = Trees.instance(env);
                long startPosition = trees.getSourcePositions().getStartPosition(compilationUnit, treePath.getLeaf());
                return name + "(" +
                        compilationUnit.getSourceFile().getName() + ":" + compilationUnit.getLineMap().getLineNumber(startPosition) +
                        ")";
            }
        };
    }

    /**
     * Implements {@link #getBaseClass}.
     */
    private final SimpleTypeVisitor6<TypeMirror, TypeElement> baseClassFinder = new SimpleTypeVisitor6<TypeMirror, TypeElement>() {
        @Override
        public TypeMirror visitDeclared(DeclaredType t, TypeElement sup) {
            if (t.asElement().equals(sup))
                return t;

            for (TypeMirror i : env.getTypeUtils().directSupertypes(t)) {
                TypeMirror r = visitDeclared((DeclaredType) i, sup);
                if (r != null)
                    return r;
            }

            // otherwise recursively apply super class and base types
            TypeMirror superclass = ((TypeElement) t.asElement()).getSuperclass();
            if (!superclass.getKind().equals(TypeKind.NONE)) {
                TypeMirror r = visitDeclared((DeclaredType) superclass, sup);
                if (r != null)
                    return r;
            }
            return null;
        }

        @Override
        public TypeMirror visitTypeVariable(TypeVariable t, TypeElement typeElement) {
            // we are checking if T (declared as T extends A&B&C) is assignable to sup.
            // so apply bounds recursively.
            for (TypeMirror typeMirror : ((TypeParameterElement) t.asElement()).getBounds()) {
                TypeMirror m = visit(typeMirror, typeElement);
                if (m != null)
                    return m;
            }
            return null;
        }

        @Override
        public TypeMirror visitArray(ArrayType t, TypeElement typeElement) {
            // we are checking if t=T[] is assignable to sup.
            // the only case this is allowed is sup=Object,
            // and Object isn't parameterized.
            return null;
        }

        @Override
        public TypeMirror visitWildcard(WildcardType t, TypeElement typeElement) {
            // we are checking if T (= ? extends A&B&C) is assignable to sup.
            // so apply bounds recursively.
            return visit(t.getExtendsBound(), typeElement);
        }

        @Override
        protected TypeMirror defaultAction(TypeMirror e, TypeElement typeElement) {
            return e;
        }
    };
}
