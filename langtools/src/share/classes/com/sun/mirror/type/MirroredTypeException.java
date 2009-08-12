/*
 * Copyright 2004 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.mirror.type;


import java.lang.annotation.Annotation;

import com.sun.mirror.declaration.Declaration;


/**
 * Thrown when an application attempts to access the {@link Class} object
 * corresponding to a {@link TypeMirror}.
 *
 * @deprecated All components of this API have been superseded by the
 * standardized annotation processing API.  The replacement for the
 * functionality of this exception is {@link
 * javax.lang.model.type.MirroredTypeException}.
 *
 * @see MirroredTypesException
 * @see Declaration#getAnnotation(Class)
 */
@Deprecated
@SuppressWarnings("deprecation")
public class MirroredTypeException extends RuntimeException {

    private static final long serialVersionUID = 1;

    private transient TypeMirror type;          // cannot be serialized
    private String name;                        // type's qualified "name"

    /**
     * Constructs a new MirroredTypeException for the specified type.
     *
     * @param type  the type being accessed
     */
    public MirroredTypeException(TypeMirror type) {
        super("Attempt to access Class object for TypeMirror " + type);
        this.type = type;
        name = type.toString();
    }

    /**
     * Returns the type mirror corresponding to the type being accessed.
     * The type mirror may be unavailable if this exception has been
     * serialized and then read back in.
     *
     * @return the type mirror, or <tt>null</tt> if unavailable
     */
    public TypeMirror getTypeMirror() {
        return type;
    }

    /**
     * Returns the fully qualified name of the type being accessed.
     * More precisely, returns the canonical name of a class,
     * interface, array, or primitive, and returns <tt>"void"</tt> for
     * the pseudo-type representing the type of <tt>void</tt>.
     *
     * @return the fully qualified name of the type being accessed
     */
    public String getQualifiedName() {
        return name;
    }
}
