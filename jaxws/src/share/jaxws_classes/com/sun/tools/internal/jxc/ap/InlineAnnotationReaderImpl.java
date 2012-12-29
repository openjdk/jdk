/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.jxc.ap;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.TypeMirror;
import com.sun.xml.internal.bind.v2.model.annotation.AbstractInlineAnnotationReaderImpl;
import com.sun.xml.internal.bind.v2.model.annotation.AnnotationReader;
import com.sun.xml.internal.bind.v2.model.annotation.Locatable;
import com.sun.xml.internal.bind.v2.model.annotation.LocatableAnnotation;

/**
 * {@link AnnotationReader} implementation that reads annotation inline from Annoation Processing.
 *
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */
public final class InlineAnnotationReaderImpl extends AbstractInlineAnnotationReaderImpl<TypeMirror, TypeElement, VariableElement, ExecutableElement> {

    /** The singleton instance. */
    public static final InlineAnnotationReaderImpl theInstance = new InlineAnnotationReaderImpl();

    private InlineAnnotationReaderImpl() {}

    public <A extends Annotation> A getClassAnnotation(Class<A> a, TypeElement clazz, Locatable srcPos) {
        return LocatableAnnotation.create(clazz.getAnnotation(a),srcPos);
    }

    public <A extends Annotation> A getFieldAnnotation(Class<A> a, VariableElement f, Locatable srcPos) {
        return LocatableAnnotation.create(f.getAnnotation(a),srcPos);
    }

    public boolean hasFieldAnnotation(Class<? extends Annotation> annotationType, VariableElement f) {
        return f.getAnnotation(annotationType)!=null;
    }

    public boolean hasClassAnnotation(TypeElement clazz, Class<? extends Annotation> annotationType) {
        return clazz.getAnnotation(annotationType)!=null;
    }

    public Annotation[] getAllFieldAnnotations(VariableElement field, Locatable srcPos) {
        return getAllAnnotations(field,srcPos);
    }

    public <A extends Annotation> A getMethodAnnotation(Class<A> a, ExecutableElement method, Locatable srcPos) {
        return LocatableAnnotation.create(method.getAnnotation(a),srcPos);
    }

    public boolean hasMethodAnnotation(Class<? extends Annotation> a, ExecutableElement method) {
        return method.getAnnotation(a)!=null;
    }

    public Annotation[] getAllMethodAnnotations(ExecutableElement method, Locatable srcPos) {
        return getAllAnnotations(method,srcPos);
    }

    /**
     * Gets all the annotations on the given declaration.
     */
    private Annotation[] getAllAnnotations(Element decl, Locatable srcPos) {
        List<Annotation> r = new ArrayList<Annotation>();

        for( AnnotationMirror m : decl.getAnnotationMirrors() ) {
            try {
                String fullName = ((TypeElement) m.getAnnotationType().asElement()).getQualifiedName().toString();
                Class<? extends Annotation> type =
                    SecureLoader.getClassClassLoader(getClass()).loadClass(fullName).asSubclass(Annotation.class);
                Annotation annotation = decl.getAnnotation(type);
                if(annotation!=null)
                    r.add( LocatableAnnotation.create(annotation,srcPos) );
            } catch (ClassNotFoundException e) {
                // just continue
            }
        }

        return r.toArray(new Annotation[r.size()]);
    }

    public <A extends Annotation> A getMethodParameterAnnotation(Class<A> a, ExecutableElement m, int paramIndex, Locatable srcPos) {
        VariableElement[] params = m.getParameters().toArray(new VariableElement[m.getParameters().size()]);
        return LocatableAnnotation.create(
            params[paramIndex].getAnnotation(a), srcPos );
    }

    public <A extends Annotation> A getPackageAnnotation(Class<A> a, TypeElement clazz, Locatable srcPos) {
        return LocatableAnnotation.create(clazz.getEnclosingElement().getAnnotation(a), srcPos);
    }

    public TypeMirror getClassValue(Annotation a, String name) {
        try {
            a.annotationType().getMethod(name).invoke(a);
            assert false;
            throw new IllegalStateException("should throw a MirroredTypeException");
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        } catch (InvocationTargetException e) {
            if( e.getCause() instanceof MirroredTypeException ) {
                MirroredTypeException me = (MirroredTypeException)e.getCause();
                return me.getTypeMirror();
            }
            // impossible
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            throw new NoSuchMethodError(e.getMessage());
        }
    }

    public TypeMirror[] getClassArrayValue(Annotation a, String name) {
        try {
            a.annotationType().getMethod(name).invoke(a);
            assert false;
            throw new IllegalStateException("should throw a MirroredTypesException");
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        } catch (InvocationTargetException e) {
            if( e.getCause() instanceof MirroredTypesException ) {
                MirroredTypesException me = (MirroredTypesException)e.getCause();
                Collection<? extends TypeMirror> r = me.getTypeMirrors();
                return r.toArray(new TypeMirror[r.size()]);
            }
            // *********************** TODO: jdk6 bug. Fixed in java7
            // According to the javadocs it should throw the MirroredTypesException
            if( e.getCause() instanceof MirroredTypeException ) {
                MirroredTypeException me = (MirroredTypeException)e.getCause();
                TypeMirror tr = me.getTypeMirror();
                TypeMirror[] trArr = new TypeMirror[1];
                trArr[0] = tr;
                return trArr;
            }
            // *******************************************
            // impossible
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            throw new NoSuchMethodError(e.getMessage());
        }
    }

    protected String fullName(ExecutableElement m) {
        return ((TypeElement) m.getEnclosingElement()).getQualifiedName().toString()+'#'+m.getSimpleName();
    }
}
