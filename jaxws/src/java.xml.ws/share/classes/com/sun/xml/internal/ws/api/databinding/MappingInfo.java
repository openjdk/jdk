/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.api.databinding;

import javax.xml.namespace.QName;

import com.sun.xml.internal.ws.api.BindingID;

/**
 * A MappingInfo object is the collection of all the properties of the mapping
 * between a JAVA contract class (SEI) and it's corresponding WSDL artifacts
 * (wsdl:portType and wsdl:binding). A MappingInfo object can be used to provide
 * additional mapping metadata for WSDL generation and the runtime of WebService
 * databinding.
 *
 * @author shih-chang.chen@oracle.com
 */
public class MappingInfo {
        protected String targetNamespace;
        protected String databindingMode;
        protected SoapBodyStyle soapBodyStyle;
        protected BindingID bindingID;
        protected QName serviceName;
        protected QName portName;
        protected String defaultSchemaNamespaceSuffix;

    public String getTargetNamespace() {
                return targetNamespace;
        }
        public void setTargetNamespace(String targetNamespace) {
                this.targetNamespace = targetNamespace;
        }
        public String getDatabindingMode() {
                return databindingMode;
        }
        public void setDatabindingMode(String databindingMode) {
                this.databindingMode = databindingMode;
        }
        public SoapBodyStyle getSoapBodyStyle() {
                return soapBodyStyle;
        }
        public void setSoapBodyStyle(SoapBodyStyle soapBodyStyle) {
                this.soapBodyStyle = soapBodyStyle;
        }
        public BindingID getBindingID() {
                return bindingID;
        }
        public void setBindingID(BindingID bindingID) {
                this.bindingID = bindingID;
        }
        public QName getServiceName() {
                return serviceName;
        }
        public void setServiceName(QName serviceName) {
                this.serviceName = serviceName;
        }
        public QName getPortName() {
                return portName;
        }
        public void setPortName(QName portName) {
                this.portName = portName;
        }
    public String getDefaultSchemaNamespaceSuffix() {
        return defaultSchemaNamespaceSuffix;
    }
    public void setDefaultSchemaNamespaceSuffix(String defaultSchemaNamespaceSuffix) {
        this.defaultSchemaNamespaceSuffix = defaultSchemaNamespaceSuffix;
    }
}
