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
package com.sun.tools.internal.jxc.apt;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.sun.mirror.declaration.AnnotationMirror;
import com.sun.mirror.declaration.Declaration;
import com.sun.mirror.declaration.FieldDeclaration;
import com.sun.mirror.declaration.MethodDeclaration;
import com.sun.mirror.declaration.ParameterDeclaration;
import com.sun.mirror.declaration.TypeDeclaration;
import com.sun.mirror.type.MirroredTypeException;
import com.sun.mirror.type.MirroredTypesException;
import com.sun.mirror.type.TypeMirror;
import com.sun.xml.internal.bind.v2.model.annotation.AbstractInlineAnnotationReaderImpl;
import com.sun.xml.internal.bind.v2.model.annotation.AnnotationReader;
import com.sun.xml.internal.bind.v2.model.annotation.Locatable;
import com.sun.xml.internal.bind.v2.model.annotation.LocatableAnnotation;

/**
 * {@link AnnotationReader} implementation that reads annotation inline from APT.
 *
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */
public final class InlineAnnotationReaderImpl extends AbstractInlineAnnotationReaderImpl<TypeMirror,TypeDeclaration,FieldDeclaration,MethodDeclaration> {

    /** The singleton instance. */
    public static final InlineAnnotationReaderImpl theInstance = new InlineAnnotationReaderImpl();

    private InlineAnnotationReaderImpl() {}

    public <A extends Annotation> A getClassAnnotation(Class<A> a, TypeDeclaration clazz, Locatable srcPos) {
        return LocatableAnnotation.create(clazz.getAnnotation(a),srcPos);
    }

    public <A extends Annotation> A getFieldAnnotation(Class<A> a, FieldDeclaration f, Locatable srcPos) {
        return LocatableAnnotation.create(f.getAnnotation(a),srcPos);
    }

    public boolean hasFieldAnnotation(Class<? extends Annotation> annotationType, FieldDeclaration f) {
        return f.getAnnotation(annotationType)!=null;
    }

    public boolean hasClassAnnotation(TypeDeclaration clazz, Class<? extends Annotation> annotationType) {
        return clazz.getAnnotation(annotationType)!=null;
    }

    public Annotation[] getAllFieldAnnotations(FieldDeclaration field, Locatable srcPos) {
        return getAllAnnotations(field,srcPos);
    }

    public <A extends Annotation> A getMethodAnnotation(Class<A> a, MethodDeclaration method, Locatable srcPos) {
        return LocatableAnnotation.create(method.getAnnotation(a),srcPos);
    }

    public boolean hasMethodAnnotation(Class<? extends Annotation> a, MethodDeclaration method) {
        return method.getAnnotation(a)!=null;
    }

    private static final Annotation[] EMPTY_ANNOTATION = new Annotation[0];

    public Annotation[] getAllMethodAnnotations(MethodDeclaration method, Locatable srcPos) {
        return getAllAnnotations(method,srcPos);
    }

    /**
     * Gets all the annotations on the given declaration.
     */
    private Annotation[] getAllAnnotations(Declaration decl, Locatable srcPos) {
        List<Annotation> r = new ArrayList<Annotation>();

        for( AnnotationMirror m : decl.getAnnotationMirrors() ) {
            try {
                String fullName = m.getAnnotationType().getDeclaration().getQualifiedName();
                Class<? extends Annotation> type =
                    getClass().getClassLoader().loadClass(fullName).asSubclass(Annotation.class);
                Annotation annotation = decl.getAnnotation(type);
                if(annotation!=null)
                    r.add( LocatableAnnotation.create(annotation,srcPos) );
            } catch (ClassNotFoundException e) {
                // just continue
            }
        }

        return r.toArray(EMPTY_ANNOTATION);
    }

    public <A extends Annotation> A getMethodParameterAnnotation(Class<A> a, MethodDeclaration m, int paramIndex, Locatable srcPos) {
        ParameterDeclaration[] params = m.getParameters().toArray(new ParameterDeclaration[0]);
        return LocatableAnnotation.create(
            params[paramIndex].getAnnotation(a), srcPos );
    }

    public <A extends Annotation> A getPackageAnnotation(Class<A> a, TypeDeclaration clazz, Locatable srcPos) {
        return LocatableAnnotation.create(clazz.getPackage().getAnnotation(a),srcPos);
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
                Collection<TypeMirror> r = me.getTypeMirrors();
                return r.toArray(new TypeMirror[r.size()]);
            }
            // impossible
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            throw new NoSuchMethodError(e.getMessage());
        }
    }

    protected String fullName(MethodDeclaration m) {
        return m.getDeclaringType().getQualifiedName()+'#'+m.getSimpleName();
    }
}
