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
package com.sun.tools.internal.xjc.api;

import com.sun.mirror.apt.AnnotationProcessorEnvironment;
import com.sun.mirror.declaration.Declaration;
import com.sun.mirror.declaration.MethodDeclaration;
import com.sun.mirror.declaration.ParameterDeclaration;
import com.sun.mirror.declaration.TypeDeclaration;
import com.sun.mirror.type.TypeMirror;
import com.sun.mirror.util.SourcePosition;

/**
 * Reference to a JAXB type (from JAX-RPC.)
 *
 * <p>
 * A reference is a Java type (represented as a {@link TypeMirror})
 * and a set of annotations (represented as a {@link Declaration}).
 * Together they describe a root reference to a JAXB type binding.
 *
 * <p>
 * Those two values can be supplied independently, or you can use
 * other convenience constructors to supply two values at once.
 *
 *
 * @author Kohsuke Kawaguchi
 */
public final class Reference {
    /**
     * The JAXB type being referenced. Must not be null.
     */
    public final TypeMirror type;
    /**
     * The declaration from which annotations for the {@link #type} is read.
     * Must not be null.
     */
    public final Declaration annotations;

    /**
     * Creates a reference from the return type of the method
     * and annotations on the method.
     */
    public Reference(MethodDeclaration method) {
        this(method.getReturnType(),method);
    }

    /**
     * Creates a reference from the parameter type
     * and annotations on the parameter.
     */
    public Reference(ParameterDeclaration param) {
        this(param.getType(),param);
    }

    /**
     * Creates a reference from a class declaration and its annotations.
     */
    public Reference(TypeDeclaration type,AnnotationProcessorEnvironment env) {
        this(env.getTypeUtils().getDeclaredType(type),type);
    }

    /**
     * Creates a reference by providing two values independently.
     */
    public Reference(TypeMirror type, Declaration annotations) {
        if(type==null || annotations==null)
            throw new IllegalArgumentException();
        this.type = type;
        this.annotations = annotations;
    }

    /**
     * Gets the source location that can be used to report error messages regarding
     * this reference.
     */
    public SourcePosition getPosition() {
        return annotations.getPosition();
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Reference)) return false;

        final Reference that = (Reference) o;

        return annotations.equals(that.annotations) && type.equals(that.type);
    }

    public int hashCode() {
        return 29 * type.hashCode() + annotations.hashCode();
    }
}
