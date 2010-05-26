/*
 * Copyright (c) 2004, 2005, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.mirror.type;


import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import com.sun.mirror.declaration.Declaration;


/**
 * Thrown when an application attempts to access a sequence of {@link Class}
 * objects each corresponding to a {@link TypeMirror}.
 *
 * @deprecated All components of this API have been superseded by the
 * standardized annotation processing API.  The replacement for the
 * functionality of this exception is {@link
 * javax.lang.model.type.MirroredTypesException}.
 *
 * @see MirroredTypeException
 * @see Declaration#getAnnotation(Class)
 */
@Deprecated
@SuppressWarnings("deprecation")
public class MirroredTypesException extends RuntimeException {

    private static final long serialVersionUID = 1;

    private transient Collection<TypeMirror> types;     // cannot be serialized
    private Collection<String> names;           // types' qualified "names"

    /**
     * Constructs a new MirroredTypesException for the specified types.
     *
     * @param types  an ordered collection of the types being accessed
     */
    public MirroredTypesException(Collection<TypeMirror> types) {
        super("Attempt to access Class objects for TypeMirrors " + types);
        this.types = types;
        names = new ArrayList<String>();
        for (TypeMirror t : types) {
            names.add(t.toString());
        }
    }

    /**
     * Returns the type mirrors corresponding to the types being accessed.
     * The type mirrors may be unavailable if this exception has been
     * serialized and then read back in.
     *
     * @return the type mirrors in order, or <tt>null</tt> if unavailable
     */
    public Collection<TypeMirror> getTypeMirrors() {
        return (types != null)
                ? Collections.unmodifiableCollection(types)
                : null;
    }

    /**
     * Returns the fully qualified names of the types being accessed.
     * More precisely, returns the canonical names of each class,
     * interface, array, or primitive, and <tt>"void"</tt> for
     * the pseudo-type representing the type of <tt>void</tt>.
     *
     * @return the fully qualified names, in order, of the types being
     *          accessed
     */
    public Collection<String> getQualifiedNames() {
        return Collections.unmodifiableCollection(names);
    }
}
