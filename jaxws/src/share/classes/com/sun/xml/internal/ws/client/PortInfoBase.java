/*
 * Portions Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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
package com.sun.xml.internal.ws.client;

import javax.xml.namespace.QName;

/**
 * JAXWS Development Team
 */
public class PortInfoBase {

    protected String targetEndpoint;
    protected String defaultNamespace;
    protected QName name;
    protected QName portTypeName;
    protected String bindingId;

    public PortInfoBase(QName name) {
        this.name = name;
        targetEndpoint = "";
        defaultNamespace = "";
    }

    public PortInfoBase(String targetEndpoint, QName name, String bindingId) {
        this.targetEndpoint = targetEndpoint;
        this.name = name;
        this.bindingId = bindingId;
    }

    void setName(QName nm) {
        name = nm;
    }

    public QName getName() {
        return name;
    }

    void setTargetEndpoint(String endpoint) {
        targetEndpoint = endpoint;
    }

    public String getTargetEndpoint() {
        return targetEndpoint;
    }

    void setBindingId(String id) {
        bindingId = id;
    }

    public String getBindingId() {
        return bindingId;
    }

    void setPortTypeName(QName typeName) {
        portTypeName = typeName;
    }

    public QName getPortTypeName() {
        return portTypeName;
    }

    void setDefaultNamespace(String namespace) {
        defaultNamespace = namespace;
    }
}
