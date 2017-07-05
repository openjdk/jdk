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
import com.sun.tools.internal.ws.wsdl.framework.Entity;
import com.sun.tools.internal.ws.wsdl.framework.EntityAction;
import com.sun.tools.internal.ws.wsdl.framework.ExtensibilityHelper;
import org.xml.sax.Locator;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity corresponding to the "operation" child element of a WSDL "binding" element.
 *
 * @author WS Development Team
 */
public class BindingOperation extends Entity implements TWSDLExtensible {

    public BindingOperation(Locator locator) {
        super(locator);
        _faults = new ArrayList<BindingFault>();
        _helper = new ExtensibilityHelper();
    }

    public String getName() {
        return _name;
    }

    public void setName(String name) {
        _name = name;
    }

    public String getUniqueKey() {
        if (_uniqueKey == null) {
            StringBuilder sb = new StringBuilder();
            sb.append(_name);
            sb.append(' ');
            if (_input != null) {
                sb.append(_input.getName());
            } else {
                sb.append(_name);
                if (_style == OperationStyle.REQUEST_RESPONSE) {
                    sb.append("Request");
                } else if (_style == OperationStyle.SOLICIT_RESPONSE) {
                    sb.append("Response");
                }
            }
            sb.append(' ');
            if (_output != null) {
                sb.append(_output.getName());
            } else {
                sb.append(_name);
                if (_style == OperationStyle.SOLICIT_RESPONSE) {
                    sb.append("Solicit");
                } else if (_style == OperationStyle.REQUEST_RESPONSE) {
                    sb.append("Response");
                }
            }
            _uniqueKey = sb.toString();
        }

        return _uniqueKey;
    }

    public OperationStyle getStyle() {
        return _style;
    }

    public void setStyle(OperationStyle s) {
        _style = s;
    }

    public BindingInput getInput() {
        return _input;
    }

    public void setInput(BindingInput i) {
        _input = i;
    }

    public BindingOutput getOutput() {
        return _output;
    }

    public void setOutput(BindingOutput o) {
        _output = o;
    }

    public void addFault(BindingFault f) {
        _faults.add(f);
    }

    public Iterable<BindingFault> faults() {
        return _faults;
    }

    @Override
    public QName getElementName() {
        return WSDLConstants.QNAME_OPERATION;
    }

    public Documentation getDocumentation() {
        return _documentation;
    }

    public void setDocumentation(Documentation d) {
        _documentation = d;
    }

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

    @Override
    public void addExtension(TWSDLExtension e) {
        _helper.addExtension(e);
    }

    @Override
    public Iterable<TWSDLExtension> extensions() {
        return _helper.extensions();
    }

    @Override
    public TWSDLExtensible getParent() {
        return parent;
    }

    @Override
    public void withAllSubEntitiesDo(EntityAction action) {
        if (_input != null) {
            action.perform(_input);
        }
        if (_output != null) {
            action.perform(_output);
        }
        for (BindingFault _fault : _faults) {
            action.perform(_fault);
        }
        _helper.withAllSubEntitiesDo(action);
    }

    public void accept(WSDLDocumentVisitor visitor) throws Exception {
        visitor.preVisit(this);
        //bug fix: 4947340, extensions should be the first element
        _helper.accept(visitor);
        if (_input != null) {
            _input.accept(visitor);
        }
        if (_output != null) {
            _output.accept(visitor);
        }
        for (BindingFault _fault : _faults) {
            _fault.accept(visitor);
        }
        visitor.postVisit(this);
    }

    @Override
    public void validateThis() {
        if (_name == null) {
            failValidation("validation.missingRequiredAttribute", "name");
        }
        if (_style == null) {
            failValidation("validation.missingRequiredProperty", "style");
        }

        // verify operation style
        if (_style == OperationStyle.ONE_WAY) {
            if (_input == null) {
                failValidation("validation.missingRequiredSubEntity", "input");
            }
            if (_output != null) {
                failValidation("validation.invalidSubEntity", "output");
            }
            if (_faults != null && !_faults.isEmpty()) {
                failValidation("validation.invalidSubEntity", "fault");
            }
        }
    }

    private ExtensibilityHelper _helper;
    private Documentation _documentation;
    private String _name;
    private BindingInput _input;
    private BindingOutput _output;
    private List<BindingFault> _faults;
    private OperationStyle _style;
    private String _uniqueKey;

    public void setParent(TWSDLExtensible parent) {
        this.parent = parent;
    }

    private TWSDLExtensible parent;
}
