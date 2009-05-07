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
package com.sun.tools.internal.ws.processor.modeler.annotation;

import com.sun.istack.internal.NotNull;
import com.sun.mirror.declaration.Declaration;
import com.sun.mirror.type.TypeMirror;

import javax.xml.namespace.QName;
import java.lang.annotation.Annotation;

/**
 *
 * @author  WS Development Team
 */
public final class MemberInfo implements Comparable<MemberInfo> {
    private final TypeMirror paramType;
    private final String paramName;
    private final QName elementName;
    private final Annotation[] jaxbAnnotations;
    /**
     * Use this to look up annotations on this parameter/return type.
     */
    private final Declaration decl;

    public MemberInfo(TypeMirror paramType, String paramName, QName elementName, @NotNull Declaration decl, Annotation... jaxbAnnotations) {
        this.paramType = paramType;
        this.paramName = paramName;
        this.elementName = elementName;
        this.decl = decl;
        this.jaxbAnnotations = jaxbAnnotations;
    }


    public Annotation[] getJaxbAnnotations() {
        return jaxbAnnotations;
    }

    public TypeMirror getParamType() {
        return paramType;
    }

    public String getParamName() {
        return paramName;
    }

    public QName getElementName() {
        return elementName;
    }

    public @NotNull Declaration getDecl() {
        return decl;
    }

    public int compareTo(MemberInfo member) {
        return paramName.compareTo(member.paramName);
    }
}
