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

package com.sun.tools.internal.ws.wsdl.parser;

import com.sun.tools.internal.ws.api.wsdl.TWSDLExtensible;
import com.sun.tools.internal.ws.api.wsdl.TWSDLParserContext;
import com.sun.tools.internal.ws.resources.ModelerMessages;
import com.sun.tools.internal.ws.resources.WsdlMessages;
import com.sun.tools.internal.ws.util.xml.XmlUtil;
import com.sun.tools.internal.ws.wscompile.ErrorReceiver;
import com.sun.tools.internal.ws.wsdl.document.Fault;
import com.sun.tools.internal.ws.wsdl.document.Input;
import com.sun.tools.internal.ws.wsdl.document.Output;
import com.sun.xml.internal.ws.addressing.W3CAddressingMetadataConstants;
import com.sun.xml.internal.ws.api.addressing.AddressingVersion;
import org.w3c.dom.Element;
import org.xml.sax.Locator;

import javax.xml.namespace.QName;
import java.util.Map;

import static com.sun.xml.internal.ws.addressing.v200408.MemberSubmissionAddressingConstants.WSA_ACTION_QNAME;

/**
 * @author Arun Gupta
 */
public class MemberSubmissionAddressingExtensionHandler extends W3CAddressingExtensionHandler {

    private ErrorReceiver errReceiver;
    private boolean extensionModeOn;

    public MemberSubmissionAddressingExtensionHandler(Map<String, AbstractExtensionHandler> extensionHandlerMap, ErrorReceiver env, boolean extensionModeOn) {
        super(extensionHandlerMap, env);
        this.errReceiver = env;
        this.extensionModeOn = extensionModeOn;
    }

    @Override
    public String getNamespaceURI() {
        return AddressingVersion.MEMBER.wsdlNsUri;
    }

    protected QName getWSDLExtensionQName() {
        return AddressingVersion.MEMBER.wsdlExtensionTag;
    }

    @Override
    public boolean handlePortExtension(TWSDLParserContext context, TWSDLExtensible parent, Element e) {
        // ignore any extension elements
        return false;
    }

    @Override
    public boolean handleInputExtension(TWSDLParserContext context, TWSDLExtensible parent, Element e) {
        if (extensionModeOn) {
            warn(context.getLocation(e));
            String actionValue = XmlUtil.getAttributeNSOrNull(e, WSA_ACTION_QNAME);
            if (actionValue == null || actionValue.equals("")) {
                return warnEmptyAction(parent, context.getLocation(e));
            }
            ((Input) parent).setAction(actionValue);
            return true;
        } else {
            return fail(context.getLocation(e));
        }
    }

    private boolean fail(Locator location) {
        errReceiver.warning(location,
                ModelerMessages.WSDLMODELER_INVALID_IGNORING_MEMBER_SUBMISSION_ADDRESSING(
                        AddressingVersion.MEMBER.nsUri, W3CAddressingMetadataConstants.WSAM_NAMESPACE_NAME));
        return false;
    }

    private void warn(Locator location) {
        errReceiver.warning(location,
                ModelerMessages.WSDLMODELER_WARNING_MEMBER_SUBMISSION_ADDRESSING_USED(
                        AddressingVersion.MEMBER.nsUri, W3CAddressingMetadataConstants.WSAM_NAMESPACE_NAME));
    }

    @Override
    public boolean handleOutputExtension(TWSDLParserContext context, TWSDLExtensible parent, Element e) {
        if (extensionModeOn) {
            warn(context.getLocation(e));
            String actionValue = XmlUtil.getAttributeNSOrNull(e, WSA_ACTION_QNAME);
            if (actionValue == null || actionValue.equals("")) {
                return warnEmptyAction(parent, context.getLocation(e));
            }
            ((Output) parent).setAction(actionValue);
            return true;
        } else {
            return fail(context.getLocation(e));
        }
    }

    @Override
    public boolean handleFaultExtension(TWSDLParserContext context, TWSDLExtensible parent, Element e) {
        if (extensionModeOn) {
            warn(context.getLocation(e));
            String actionValue = XmlUtil.getAttributeNSOrNull(e, WSA_ACTION_QNAME);
            if (actionValue == null || actionValue.equals("")) {
                errReceiver.warning(context.getLocation(e), WsdlMessages.WARNING_FAULT_EMPTY_ACTION(parent.getNameValue(), parent.getWSDLElementName().getLocalPart(), parent.getParent().getNameValue()));
                return false; // keep compiler happy
            }
            ((Fault) parent).setAction(actionValue);
            return true;
        } else {
            return fail(context.getLocation(e));
        }
    }

    private boolean warnEmptyAction(TWSDLExtensible parent, Locator pos) {
        errReceiver.warning(pos, WsdlMessages.WARNING_INPUT_OUTPUT_EMPTY_ACTION(parent.getWSDLElementName().getLocalPart(), parent.getParent().getNameValue()));
        return false;
    }
}
