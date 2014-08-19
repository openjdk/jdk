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

package com.sun.xml.internal.ws.spi.db;

import java.net.URL;
import java.util.Collection;
import java.util.Map;

import com.sun.xml.internal.ws.api.model.SEIModel;

/**
 * BindingInfo
 *
 * @author shih-chang.chen@oracle.com
 */
public class BindingInfo {

        private String databindingMode;
        private String defaultNamespace;

        private Collection<Class> contentClasses = new java.util.ArrayList<Class>();
    private Collection<TypeInfo> typeInfos = new java.util.ArrayList<TypeInfo>();
    private Map<Class,Class> subclassReplacements = new java.util.HashMap<Class, Class>();
    private Map<String, Object> properties = new java.util.HashMap<String, Object>();
        protected ClassLoader classLoader;

    private SEIModel seiModel;
    private URL wsdlURL;

    public String getDatabindingMode() {
                return databindingMode;
        }
        public void setDatabindingMode(String databindingMode) {
                this.databindingMode = databindingMode;
        }

        public String getDefaultNamespace() {
                return defaultNamespace;
        }
        public void setDefaultNamespace(String defaultNamespace) {
                this.defaultNamespace = defaultNamespace;
        }

        public Collection<Class> contentClasses() {
                return contentClasses;
        }
        public Collection<TypeInfo> typeInfos() {
                return typeInfos;
        }
        public Map<Class, Class> subclassReplacements() {
                return subclassReplacements;
        }
        public Map<String, Object> properties() {
                return properties;
        }

        public SEIModel getSEIModel() {
                return seiModel;
        }
        public void setSEIModel(SEIModel seiModel) {
                this.seiModel = seiModel;
        }
        public ClassLoader getClassLoader() {
                return classLoader;
        }
        public void setClassLoader(ClassLoader classLoader) {
                this.classLoader = classLoader;
        }
    public URL getWsdlURL() {
        return wsdlURL;
    }
    public void setWsdlURL(URL wsdlURL) {
        this.wsdlURL = wsdlURL;
    }
}
