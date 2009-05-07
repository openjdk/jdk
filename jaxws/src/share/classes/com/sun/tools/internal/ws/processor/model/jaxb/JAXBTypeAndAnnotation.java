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
package com.sun.tools.internal.ws.processor.model.jaxb;

import com.sun.codemodel.internal.JAnnotatable;
import com.sun.codemodel.internal.JType;
import com.sun.tools.internal.xjc.api.TypeAndAnnotation;

/**
 * Holds JAXB JType and TypeAndAnnotation. This provides abstration over
 * types from JAXBMapping and Property.
 */
public class JAXBTypeAndAnnotation {
    TypeAndAnnotation typeAnn;
    JType type;

    public JAXBTypeAndAnnotation(TypeAndAnnotation typeAnn) {
        this.typeAnn = typeAnn;
        this.type = typeAnn.getTypeClass();
    }

    public JAXBTypeAndAnnotation(JType type) {
        this.type = type;
    }

    public JAXBTypeAndAnnotation(TypeAndAnnotation typeAnn, JType type) {
        this.typeAnn = typeAnn;
        this.type = type;
    }

    public void annotate(JAnnotatable typeVar) {
        if(typeAnn != null)
            typeAnn.annotate(typeVar);
    }

    public JType getType() {
        return type;
    }

    public String getName(){
        return type.fullName();
    }

    public TypeAndAnnotation getTypeAnn() {
        return typeAnn;
    }

    public void setTypeAnn(TypeAndAnnotation typeAnn) {
        this.typeAnn = typeAnn;
    }

    public void setType(JType type) {
        this.type = type;
    }
}
