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

package com.sun.tools.internal.ws.processor.model.jaxb;

import com.sun.tools.internal.ws.processor.model.AbstractType;

import javax.xml.namespace.QName;

/**
 * @author Vivek Pandey
 *
 * Represents RpcLit member parts
 */
public class RpcLitMember extends AbstractType {

    //wsdl:part type attribute java mapped object
    private String javaTypeName;
    private QName schemaTypeName;

    /**
     *
     */
    public RpcLitMember() {
        super();
        // TODO Auto-generated constructor stub
    }
    public RpcLitMember(QName name, String javaTypeName){
        setName(name);
        this.javaTypeName = javaTypeName;
    }
    public RpcLitMember(QName name, String javaTypeName, QName schemaTypeName){
        setName(name);
        this.javaTypeName = javaTypeName;
        this.schemaTypeName = schemaTypeName;
    }

    /**
     * @return Returns the type.
     */
    public String getJavaTypeName() {
        return javaTypeName;
    }
    /**
     * @param type The type to set.
     */
    public void setJavaTypeName(String type) {
        this.javaTypeName = type;
    }

    /**
     * @return Returns the type.
     */
    public QName getSchemaTypeName() {
        return schemaTypeName;
    }
    /**
     * @param type The type to set.
     */
    public void setSchemaTypeName(QName type) {
        this.schemaTypeName = type;
    }
}
