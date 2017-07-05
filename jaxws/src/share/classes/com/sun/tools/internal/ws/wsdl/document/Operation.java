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

import com.sun.codemodel.internal.JClass;
import com.sun.tools.internal.ws.api.wsdl.TWSDLExtensible;
import com.sun.tools.internal.ws.api.wsdl.TWSDLExtension;
import com.sun.tools.internal.ws.api.wsdl.TWSDLOperation;
import com.sun.tools.internal.ws.wsdl.framework.Entity;
import com.sun.tools.internal.ws.wsdl.framework.EntityAction;
import com.sun.tools.internal.ws.wsdl.framework.ExtensibilityHelper;
import org.xml.sax.Locator;

import javax.xml.namespace.QName;
import java.util.*;

/**
 * Entity corresponding to the "operation" child element of a "portType" WSDL element.
 *
 * @author WS Development Team
 */
public class Operation extends Entity implements TWSDLOperation {

    public Operation(Locator locator) {
        super(locator);
        _faults = new ArrayList<Fault>();
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
            StringBuffer sb = new StringBuffer();
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

    public Input getInput() {
        return _input;
    }

    public void setInput(Input i) {
        _input = i;
    }

    public Output getOutput() {
        return _output;
    }

    public void setOutput(Output o) {
        _output = o;
    }

    public void addFault(Fault f) {
        _faults.add(f);
    }

    public Iterable<Fault> faults() {
        return _faults;
    }

    public String getParameterOrder() {
        return _parameterOrder;
    }

    public void setParameterOrder(String s) {
        _parameterOrder = s;
    }

    public QName getElementName() {
        return WSDLConstants.QNAME_OPERATION;
    }

    public Documentation getDocumentation() {
        return _documentation;
    }

    public void setDocumentation(Documentation d) {
        _documentation = d;
    }

    public void withAllSubEntitiesDo(EntityAction action) {
        super.withAllSubEntitiesDo(action);

        if (_input != null) {
            action.perform(_input);
        }
        if (_output != null) {
            action.perform(_output);
        }
        for (Fault _fault : _faults) {
            action.perform(_fault);
        }
        _helper.withAllSubEntitiesDo(action);
    }

    public void accept(WSDLDocumentVisitor visitor) throws Exception {
        visitor.preVisit(this);
        if (_input != null) {
            _input.accept(visitor);
        }
        if (_output != null) {
            _output.accept(visitor);
        }
        for (Fault _fault : _faults) {
            _fault.accept(visitor);
        }
        visitor.postVisit(this);
    }

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
            if (_faults != null && _faults.size() != 0) {
                failValidation("validation.invalidSubEntity", "fault");
            }
        } else if (_style == OperationStyle.NOTIFICATION) {
            if (_parameterOrder != null) {
                failValidation("validation.invalidAttribute", "parameterOrder");
            }
        }
    }

    public String getNameValue() {
        return getName();
    }

    public String getNamespaceURI() {
        return parent.getNamespaceURI();
    }

    public QName getWSDLElementName() {
        return getElementName();
    }

    /* (non-Javadoc)
    * @see TWSDLExtensible#addExtension(ExtensionImpl)
    */
    public void addExtension(TWSDLExtension e) {
        _helper.addExtension(e);

    }

    /* (non-Javadoc)
     * @see TWSDLExtensible#extensions()
     */
    public Iterable<? extends TWSDLExtension> extensions() {
        return _helper.extensions();
    }

    public TWSDLExtensible getParent() {
        return parent;
    }

    public void setParent(TWSDLExtensible parent) {
        this.parent = parent;
    }

    public Map<String, JClass> getFaults() {
        return unmodifiableFaultClassMap;
    }

    public void putFault(String faultName, JClass exception){
        faultClassMap.put(faultName, exception);
    }

    private TWSDLExtensible parent;
    private Documentation _documentation;
    private String _name;
    private Input _input;
    private Output _output;
    private List<Fault> _faults;
    private OperationStyle _style;
    private String _parameterOrder;
    private String _uniqueKey;
    private ExtensibilityHelper _helper;
    private final Map<String, JClass> faultClassMap = new HashMap<String, JClass>();
    private final Map<String, JClass> unmodifiableFaultClassMap = Collections.unmodifiableMap(faultClassMap);
}
