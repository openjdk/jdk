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
package com.sun.xml.internal.bind.v2.model.annotation;

import java.lang.annotation.Annotation;

import com.sun.xml.internal.bind.v2.model.core.ErrorHandler;
import com.sun.xml.internal.bind.v2.runtime.IllegalAnnotationException;

/**
 * {@link AnnotationReader} that reads annotation from classes,
 * not from external binding files.
 *
 * This is meant to be used as a convenient partial implementation.
 *
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */
public abstract class AbstractInlineAnnotationReaderImpl<T,C,F,M>
    implements AnnotationReader<T,C,F,M> {

    private ErrorHandler errorHandler;

    public void setErrorHandler(ErrorHandler errorHandler) {
        if(errorHandler==null)
            throw new IllegalArgumentException();
        this.errorHandler = errorHandler;
    }

    /**
     * Always return a non-null valid {@link ErrorHandler}
     */
    public final ErrorHandler getErrorHandler() {
        assert errorHandler!=null : "error handler must be set before use";
        return errorHandler;
    }

    public final <A extends Annotation> A getMethodAnnotation(Class<A> annotation, M getter, M setter, Locatable srcPos) {
        A a1 = getter==null?null:getMethodAnnotation(annotation,getter,srcPos);
        A a2 = setter==null?null:getMethodAnnotation(annotation,setter,srcPos);

        if(a1==null) {
            if(a2==null)
                return null;
            else
                return a2;
        } else {
            if(a2==null)
                return a1;
            else {
                // both are present
                getErrorHandler().error(new IllegalAnnotationException(
                    Messages.DUPLICATE_ANNOTATIONS.format(
                        annotation.getName(), fullName(getter),fullName(setter)),
                    a1, a2 ));
                // recover by ignoring one of them
                return a1;
            }
        }
    }

    public boolean hasMethodAnnotation(Class<? extends Annotation> annotation, String propertyName, M getter, M setter, Locatable srcPos) {
        boolean x = ( getter != null && hasMethodAnnotation(annotation, getter) );
        boolean y = ( setter != null && hasMethodAnnotation(annotation, setter) );

        if(x && y) {
            // both are present. have getMethodAnnotation report an error
            getMethodAnnotation(annotation,getter,setter,srcPos);
        }

        return x||y;
    }

    /**
     * Gets the fully-qualified name of the method.
     *
     * Used for error messages.
     */
    protected abstract String fullName(M m);
}
