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

package com.sun.xml.internal.ws.addressing;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.namespace.QName;
import javax.xml.ws.WebServiceException;

import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.internal.ws.api.model.SEIModel;
import org.w3c.dom.Element;

/**
 * @author Arun Gupta
 */
public class WsaTubeHelperImpl extends WsaTubeHelper {
    static final JAXBContext jc;

    static {
        try {
            jc = JAXBContext.newInstance(ProblemAction.class,
                                         ProblemHeaderQName.class);
        } catch (JAXBException e) {
            throw new WebServiceException(e);
        }
    }

    public WsaTubeHelperImpl(WSDLPort wsdlPort, SEIModel seiModel, WSBinding binding) {
        super(binding,seiModel,wsdlPort);
    }

    private Marshaller createMarshaller() throws JAXBException {
        Marshaller marshaller = jc.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
        return marshaller;
    }

    @Override
    public final void getProblemActionDetail(String action, Element element) {
        ProblemAction pa = new ProblemAction(action);
        try {
            createMarshaller().marshal(pa, element);
        } catch (JAXBException e) {
            throw new WebServiceException(e);
        }
    }

    @Override
    public final void getInvalidMapDetail(QName name, Element element) {
        ProblemHeaderQName phq = new ProblemHeaderQName(name);
        try {
            createMarshaller().marshal(phq, element);
        } catch (JAXBException e) {
            throw new WebServiceException(e);
        }
    }

    @Override
    public final void getMapRequiredDetail(QName name, Element element) {
        getInvalidMapDetail(name, element);
    }
}
