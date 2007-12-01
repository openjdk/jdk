/*
 * Portions Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.xml.internal.ws.encoding.jaxb;

import java.util.ArrayList;
import java.util.List;

import javax.xml.namespace.QName;

public class RpcLitPayload {
    private QName operation;
    private List<JAXBBridgeInfo> bridgeParameters;

    public RpcLitPayload(QName operation) {
        this.operation = operation;
        this.bridgeParameters = new ArrayList<JAXBBridgeInfo>();
    }

    /* Same as the above one. Need to remove the above constructor.
    public RpcLitPayload(QName operation, List<JAXBBridgeInfo> parameters) {
        this.operation = operation;
        this.bridgeParameters = parameters;
    }
     */

    public QName getOperation() {
        return operation;
    }

    public List<JAXBBridgeInfo> getBridgeParameters() {
        return bridgeParameters;
    }

    public static RpcLitPayload copy(RpcLitPayload payload) {
        RpcLitPayload newPayload = new RpcLitPayload(payload.getOperation());
        for(JAXBBridgeInfo param: payload.getBridgeParameters()) {
            JAXBBridgeInfo newParam = JAXBBridgeInfo.copy(param);
            newPayload.addParameter(newParam);
        }
        return newPayload;
    }

    public JAXBBridgeInfo getBridgeParameterByName(String name){
        for(JAXBBridgeInfo param : bridgeParameters) {
                if (param.getName().getLocalPart().equals(name)) {
                        return param;
            }
        }
        return null;
    }

    public void addParameter(JAXBBridgeInfo parameter) {
        bridgeParameters.add(parameter);
    }
}
