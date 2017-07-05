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

package com.sun.tools.internal.ws.wsdl.document;

import com.sun.tools.internal.ws.api.wsdl.TWSDLExtensible;
import com.sun.tools.internal.ws.api.wsdl.TWSDLExtension;
import com.sun.tools.internal.ws.wsdl.framework.*;
import com.sun.tools.internal.ws.resources.WsdlMessages;
import com.sun.tools.internal.ws.wscompile.AbortException;
import com.sun.tools.internal.ws.wscompile.ErrorReceiver;
import org.xml.sax.Locator;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Entity corresponding to the "binding" WSDL element.
 *
 * @author WS Development Team
 */
public class Binding extends GlobalEntity implements TWSDLExtensible {

    public Binding(Defining defining, Locator locator, ErrorReceiver receiver) {
        super(defining, locator, receiver);
        _operations = new ArrayList();
        _helper = new ExtensibilityHelper();
    }

    public void add(BindingOperation operation) {
        _operations.add(operation);
    }

    public Iterator operations() {
        return _operations.iterator();
    }

    public QName getPortType() {
        return _portType;
    }

    public void setPortType(QName n) {
        _portType = n;
    }

    public PortType resolvePortType(AbstractDocument document) {
        try {
            return (PortType) document.find(Kinds.PORT_TYPE, _portType);
        } catch (NoSuchEntityException e) {
            errorReceiver.error(getLocator(), WsdlMessages.ENTITY_NOT_FOUND_PORT_TYPE(_portType, new QName(getNamespaceURI(), getName())));
            throw new AbortException();
        }
    }

    public Kind getKind() {
        return Kinds.BINDING;
    }

    public QName getElementName() {
        return WSDLConstants.QNAME_BINDING;
    }

    public Documentation getDocumentation() {
        return _documentation;
    }

    public void setDocumentation(Documentation d) {
        _documentation = d;
    }

    public void withAllSubEntitiesDo(EntityAction action) {
        for (Iterator iter = _operations.iterator(); iter.hasNext();) {
            action.perform((Entity) iter.next());
        }
        _helper.withAllSubEntitiesDo(action);
    }

    public void withAllQNamesDo(QNameAction action) {
        super.withAllQNamesDo(action);

        if (_portType != null) {
            action.perform(_portType);
        }
    }

    public void withAllEntityReferencesDo(EntityReferenceAction action) {
        super.withAllEntityReferencesDo(action);
        if (_portType != null) {
            action.perform(Kinds.PORT_TYPE, _portType);
        }
    }

    public void accept(WSDLDocumentVisitor visitor) throws Exception {
        visitor.preVisit(this);
        //bug fix: 4947340, extensions should be the first element
        _helper.accept(visitor);
        for (Iterator iter = _operations.iterator(); iter.hasNext();) {
            ((BindingOperation) iter.next()).accept(visitor);
        }
        visitor.postVisit(this);
    }

    public void validateThis() {
        if (getName() == null) {
            failValidation("validation.missingRequiredAttribute", "name");
        }
        if (_portType == null) {
            failValidation("validation.missingRequiredAttribute", "type");
        }
    }

    public String getNameValue() {
        return getName();
    }

    public String getNamespaceURI() {
        return getDefining().getTargetNamespaceURI();
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

    private ExtensibilityHelper _helper;
    private Documentation _documentation;
    private QName _portType;
    private List _operations;

    public void setParent(TWSDLExtensible parent) {
        this.parent = parent;
    }

    private TWSDLExtensible parent;
}
