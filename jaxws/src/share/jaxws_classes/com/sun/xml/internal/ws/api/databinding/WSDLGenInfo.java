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

import com.oracle.webservices.internal.api.databinding.WSDLResolver;
import com.sun.xml.internal.ws.api.server.Container;
import com.sun.xml.internal.ws.api.wsdl.writer.WSDLGeneratorExtension;

/**
 * WSDLGenInfo provides the WSDL generation options
 *
 * @author shih-chang.chen@oracle.com
 */
public class WSDLGenInfo {
        WSDLResolver wsdlResolver;
        Container container;
    boolean inlineSchemas;
    boolean secureXmlProcessingDisabled;
    WSDLGeneratorExtension[] extensions;

        public WSDLResolver getWsdlResolver() {
                return wsdlResolver;
        }
        public void setWsdlResolver(WSDLResolver wsdlResolver) {
                this.wsdlResolver = wsdlResolver;
        }
        public Container getContainer() {
                return container;
        }
        public void setContainer(Container container) {
                this.container = container;
        }
        public boolean isInlineSchemas() {
                return inlineSchemas;
        }
        public void setInlineSchemas(boolean inlineSchemas) {
                this.inlineSchemas = inlineSchemas;
        }
        public WSDLGeneratorExtension[] getExtensions() {
            if (extensions == null) return new WSDLGeneratorExtension[0];
                return extensions;
        }
        public void setExtensions(WSDLGeneratorExtension[] extensions) {
                this.extensions = extensions;
        }

    public void setSecureXmlProcessingDisabled(boolean secureXmlProcessingDisabled) {
        this.secureXmlProcessingDisabled = secureXmlProcessingDisabled;
    }

    public boolean isSecureXmlProcessingDisabled() {
        return secureXmlProcessingDisabled;
    }
}
