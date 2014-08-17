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

package com.sun.tools.internal.ws.wsdl.document;

import com.sun.tools.internal.ws.api.wsdl.TWSDLExtensible;
import com.sun.tools.internal.ws.api.wsdl.TWSDLExtension;
import com.sun.tools.internal.ws.wsdl.framework.*;
import org.xml.sax.Locator;

import javax.xml.namespace.QName;

/**
 * Entity corresponding to the "fault" child element of a port type operation.
 *
 * @author WS Development Team
 */
public class Fault extends Entity implements TWSDLExtensible {

    public Fault(Locator locator) {
        super(locator);
        _helper = new ExtensibilityHelper();
    }

    public String getName() {
        return _name;
    }

    public void setName(String name) {
        _name = name;
    }

    public QName getMessage() {
        return _message;
    }

    public void setMessage(QName n) {
        _message = n;
    }

    public Message resolveMessage(AbstractDocument document) {
        return (Message) document.find(Kinds.MESSAGE, _message);
    }

    @Override
    public QName getElementName() {
        return WSDLConstants.QNAME_FAULT;
    }

    public Documentation getDocumentation() {
        return _documentation;
    }

    public void setDocumentation(Documentation d) {
        _documentation = d;
    }

    @Override
    public void withAllQNamesDo(QNameAction action) {
        if (_message != null) {
            action.perform(_message);
        }
    }

    @Override
    public void withAllEntityReferencesDo(EntityReferenceAction action) {
        super.withAllEntityReferencesDo(action);
        if (_message != null) {
            action.perform(Kinds.MESSAGE, _message);
        }
    }

    public void accept(WSDLDocumentVisitor visitor) throws Exception {
        visitor.preVisit(this);
        visitor.postVisit(this);
    }

    @Override
    public void validateThis() {
        if (_name == null) {
            failValidation("validation.missingRequiredAttribute", "name");
        }
        if (_message == null) {
            failValidation("validation.missingRequiredAttribute", "message");
        }
    }

    private Documentation _documentation;
    private String _name;
    private QName _message;
    private String _action;
    private ExtensibilityHelper _helper;

    @Override
    public String getNameValue() {
        return getName();
    }

    @Override
    public String getNamespaceURI() {
        return (parent == null) ? null : parent.getNamespaceURI();
    }

    @Override
    public QName getWSDLElementName() {
        return getElementName();
    }

    /* (non-Javadoc)
    * @see TWSDLExtensible#addExtension(ExtensionImpl)
    */
    @Override
    public void addExtension(TWSDLExtension e) {
        _helper.addExtension(e);

    }

    /* (non-Javadoc)
     * @see TWSDLExtensible#extensions()
     */
    @Override
    public Iterable<TWSDLExtension> extensions() {
        return _helper.extensions();
    }

    @Override
    public TWSDLExtensible getParent() {
        return parent;
    }


    public void setParent(TWSDLExtensible parent) {
        this.parent = parent;
    }

    private TWSDLExtensible parent;

    public String getAction() {
        return _action;
    }

    public void setAction(String _action) {
        this._action = _action;
    }
}
