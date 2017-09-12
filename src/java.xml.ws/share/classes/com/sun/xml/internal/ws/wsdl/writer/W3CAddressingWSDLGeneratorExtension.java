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

package com.sun.xml.internal.ws.wsdl.writer;

import com.sun.xml.internal.txw2.TypedXmlWriter;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.addressing.AddressingVersion;
import com.sun.xml.internal.ws.api.model.CheckedException;
import com.sun.xml.internal.ws.api.model.JavaMethod;
import com.sun.xml.internal.ws.api.wsdl.writer.WSDLGenExtnContext;
import com.sun.xml.internal.ws.api.wsdl.writer.WSDLGeneratorExtension;

import javax.xml.ws.Action;
import javax.xml.ws.FaultAction;
import javax.xml.ws.soap.AddressingFeature;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Logger;

/**
 * @author Arun Gupta
 * @author Rama Pulavarthi
 */
public class W3CAddressingWSDLGeneratorExtension extends WSDLGeneratorExtension {
    private boolean enabled;
    private boolean required = false;

    @Override
    public void start(WSDLGenExtnContext ctxt) {
        WSBinding binding = ctxt.getBinding();
        TypedXmlWriter root = ctxt.getRoot();
        enabled = binding.isFeatureEnabled(AddressingFeature.class);
        if (!enabled)
            return;
        AddressingFeature ftr = binding.getFeature(AddressingFeature.class);
        required = ftr.isRequired();
        root._namespace(AddressingVersion.W3C.wsdlNsUri, AddressingVersion.W3C.getWsdlPrefix());
    }

    @Override
    public void addOperationInputExtension(TypedXmlWriter input, JavaMethod method) {
        if (!enabled)
            return;

        Action a = method.getSEIMethod().getAnnotation(Action.class);
        if (a != null && !a.input().equals("")) {
            addAttribute(input, a.input());
        } else {

            String soapAction = method.getBinding().getSOAPAction();
            // in SOAP 1.2 soapAction is optional ...
            if (soapAction == null || soapAction.equals("")) {
                //hack: generate default action for interop with .Net3.0 when soapAction is non-empty
                String defaultAction = getDefaultAction(method);
                addAttribute(input, defaultAction);
            }
        }
    }

    protected static final String getDefaultAction(JavaMethod method) {
        String tns = method.getOwner().getTargetNamespace();
        String delim = "/";
        // TODO: is this the correct way to find the separator ?
        try {
            URI uri = new URI(tns);
            if(uri.getScheme().equalsIgnoreCase("urn"))
                delim = ":";
        } catch (URISyntaxException e) {
            LOGGER.warning("TargetNamespace of WebService is not a valid URI");
        }
        if (tns.endsWith(delim))
            tns = tns.substring(0, tns.length() - 1);
        //this assumes that fromjava case there won't be input name.
        // if there is input name in future, then here name=inputName
        //else use operation name as follows.
        String name = (method.getMEP().isOneWay())?method.getOperationName():method.getOperationName()+"Request";

        return new StringBuilder(tns).append(delim).append(
                method.getOwner().getPortTypeName().getLocalPart()).append(
                delim).append(name).toString();
    }

    @Override
    public void addOperationOutputExtension(TypedXmlWriter output, JavaMethod method) {
        if (!enabled)
            return;

        Action a = method.getSEIMethod().getAnnotation(Action.class);
        if (a != null && !a.output().equals("")) {
            addAttribute(output, a.output());
        }
    }

    @Override
    public void addOperationFaultExtension(TypedXmlWriter fault, JavaMethod method, CheckedException ce) {
        if (!enabled)
            return;

        Action a = method.getSEIMethod().getAnnotation(Action.class);
        Class[] exs = method.getSEIMethod().getExceptionTypes();

        if (exs == null)
            return;

        if (a != null && a.fault() != null) {
            for (FaultAction fa : a.fault()) {
                if (fa.className().getName().equals(ce.getExceptionClass().getName())) {
                    if (fa.value().equals(""))
                        return;

                    addAttribute(fault, fa.value());
                    return;
                }
            }
        }
    }

    private void addAttribute(TypedXmlWriter writer, String attrValue) {
        writer._attribute(AddressingVersion.W3C.wsdlActionTag, attrValue);
    }

    @Override
    public void addBindingExtension(TypedXmlWriter binding) {
        if (!enabled)
            return;
        binding._element(AddressingVersion.W3C.wsdlExtensionTag, UsingAddressing.class);
        /*
        Do not generate wsdl:required=true
        if(required) {
            ua.required(true);
        }
        */
    }
     private static final Logger LOGGER = Logger.getLogger(W3CAddressingWSDLGeneratorExtension.class.getName());
}
