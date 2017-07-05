/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.xjc.api;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

/**
 * Reference to a JAXB type (from JAX-RPC.)
 *
 * <p>
 * A reference is a Java type (represented as a {@link javax.lang.model.type.TypeMirror})
 * and a set of annotations (represented as a {@link javax.lang.model.element.Element}).
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
    public final Element annotations;

    /**
     * Creates a reference from the return type of the method
     * and annotations on the method.
     */
    public Reference(ExecutableElement method) {
        this(method.getReturnType(),method);
    }

    /**
     * Creates a reference from the parameter type
     * and annotations on the parameter.
     */
    public Reference(VariableElement param) {
        this(param.asType(), param);
    }

    /**
     * Creates a reference from a class declaration and its annotations.
     */
    public Reference(TypeElement type, ProcessingEnvironment env) {
        this(env.getTypeUtils().getDeclaredType(type),type);
    }

    /**
     * Creates a reference by providing two values independently.
     */
    public Reference(TypeMirror type, Element annotations) {
        if(type==null || annotations==null)
            throw new IllegalArgumentException();
        this.type = type;
        this.annotations = annotations;
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
