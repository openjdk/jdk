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
package com.sun.xml.internal.ws.model;

import com.sun.xml.internal.ws.api.model.ParameterBinding;

import javax.jws.WebParam.Mode;
import javax.xml.namespace.QName;
import javax.xml.ws.WebServiceFeature;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Creates SOAP specific RuntimeModel
 *
 * @author Vivek Pandey
 */
public class SOAPSEIModel extends AbstractSEIModelImpl {

    public SOAPSEIModel(WebServiceFeature[] features) {
        super(features);
    }

    @Override
    protected void populateMaps() {
        int emptyBodyCount = 0;
        for(JavaMethodImpl jm : getJavaMethods()){
            put(jm.getMethod(), jm);
            boolean bodyFound = false;
            for(ParameterImpl p:jm.getRequestParameters()){
                ParameterBinding binding = p.getBinding();
                if(binding.isBody()){
                    put(p.getName(), jm);
                    bodyFound = true;
                }
            }
            if(!bodyFound){
                put(emptyBodyName, jm);
//                System.out.println("added empty body for: "+jm.getMethod().getName());
                emptyBodyCount++;
            }
        }
        if(emptyBodyCount > 1){
            //TODO throw exception
//            System.out.println("Error: Unqiue signature violation - more than 1 empty body!");
        }
    }

    public Set<QName> getKnownHeaders() {
        Set<QName> headers = new HashSet<QName>();
        for (JavaMethodImpl method : getJavaMethods()) {
            // fill in request headers
            Iterator<ParameterImpl> params = method.getRequestParameters().iterator();
            fillHeaders(params, headers, Mode.IN);

            // fill in response headers
            params = method.getResponseParameters().iterator();
            fillHeaders(params, headers, Mode.OUT);
        }
        return headers;
    }

    /**
     * @param params
     * @param headers
     */
    private void fillHeaders(Iterator<ParameterImpl> params, Set<QName> headers, Mode mode) {
        while (params.hasNext()) {
            ParameterImpl param = params.next();
            ParameterBinding binding = (mode == Mode.IN)?param.getInBinding():param.getOutBinding();
            QName name = param.getName();
            if (binding.isHeader() && !headers.contains(name)) {
                headers.add(name);
            }
        }
    }
}
