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

/**
 * Implemented by objects that can have annotations.
 *
 * @author Kohsuke Kawaguchi
 */
public interface AnnotationSource {
    /**
     * Gets the value of the specified annotation from the given property.
     *
     * <p>
     * When this method is used for a property that consists of a getter and setter,
     * it returns the annotation on either of those methods. If both methods have
     * the same annotation, it is an error.
     *
     * @return
     *      null if the annotation is not present.
     */
    <A extends Annotation> A readAnnotation(Class<A> annotationType);

    /**
     * Returns true if the property has the specified annotation.
     * <p>
     * Short for <code>readAnnotation(annotationType)!=null</code>,
     * but this method is typically faster.
     */
    boolean hasAnnotation(Class<? extends Annotation> annotationType);
}
