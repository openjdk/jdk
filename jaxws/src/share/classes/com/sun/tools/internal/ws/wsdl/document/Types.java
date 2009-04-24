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

package com.sun.tools.internal.ws.wsdl.document;

import com.sun.tools.internal.ws.api.wsdl.TWSDLExtensible;
import com.sun.tools.internal.ws.api.wsdl.TWSDLExtension;
import com.sun.tools.internal.ws.wsdl.framework.Entity;
import com.sun.tools.internal.ws.wsdl.framework.EntityAction;
import com.sun.tools.internal.ws.wsdl.framework.ExtensibilityHelper;
import com.sun.tools.internal.ws.wsdl.framework.ExtensionVisitor;
import org.xml.sax.Locator;

import javax.xml.namespace.QName;

/**
 * Entity corresponding to the "types" WSDL element.
 *
 * @author WS Development Team
 */
public class Types extends Entity implements TWSDLExtensible {

    public Types(Locator locator) {
        super(locator);
        _helper = new ExtensibilityHelper();
    }

    public QName getElementName() {
        return WSDLConstants.QNAME_TYPES;
    }

    public Documentation getDocumentation() {
        return _documentation;
    }

    public void setDocumentation(Documentation d) {
        _documentation = d;
    }

    public void accept(WSDLDocumentVisitor visitor) throws Exception {
        visitor.preVisit(this);
        _helper.accept(visitor);
        visitor.postVisit(this);
    }

    public void validateThis() {
    }

    /**
     * wsdl:type does not have any name attribute
     */
    public String getNameValue() {
        return null;
    }

    public String getNamespaceURI() {
        return parent.getNamespaceURI();
    }

    public QName getWSDLElementName() {
        return getElementName();
    }

    public void addExtension(TWSDLExtension e) {
        _helper.addExtension(e);
    }

    public Iterable<TWSDLExtension> extensions() {
        return _helper.extensions();
    }

    public TWSDLExtensible getParent() {
        return parent;
    }

    public void setParent(TWSDLExtensible parent) {
        this.parent = parent;
    }

    public void withAllSubEntitiesDo(EntityAction action) {
        _helper.withAllSubEntitiesDo(action);
    }

    public void accept(ExtensionVisitor visitor) throws Exception {
        _helper.accept(visitor);
    }

    private TWSDLExtensible parent;
    private ExtensibilityHelper _helper;
    private Documentation _documentation;
}
