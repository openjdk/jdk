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
package com.sun.xml.internal.ws.encoding.soap.internal;

import javax.xml.transform.Source;

import com.sun.xml.internal.ws.encoding.jaxb.JAXBBeanInfo;
import com.sun.xml.internal.ws.encoding.jaxb.JAXBBridgeInfo;
import com.sun.xml.internal.ws.encoding.jaxb.RpcLitPayload;
import com.sun.xml.internal.ws.encoding.soap.SOAPConstants;
import com.sun.xml.internal.ws.encoding.soap.message.SOAPFaultInfo;
import com.sun.xml.internal.ws.encoding.soap.message.SOAP12FaultInfo;

/**
 * @author WS Development Team
 */
public class BodyBlock {

    private Object value;

    public BodyBlock(Object value) {
        this.value = value;
    }

    public BodyBlock(JAXBBeanInfo beanInfo) {
        this.value = beanInfo;
    }

    public BodyBlock(JAXBBridgeInfo bridgeInfo) {
        this.value = bridgeInfo;
    }

    public BodyBlock(Source source) {
        setSource(source);
    }

    public BodyBlock(SOAPFaultInfo faultInfo) {
        setFaultInfo(faultInfo);
    }

    public BodyBlock(RpcLitPayload rpcLoad) {
        this.value = rpcLoad;
    }

    public void setSource(Source source) {
        this.value = source;
    }

    public void setFaultInfo(SOAPFaultInfo faultInfo) {
        this.value = faultInfo;
    }

    /**
     * There is no need to have so many setter to set to an Object. Just setValue is all that we need?
     * @param value
     */
    public void setValue(Object value){
        this.value = value;
    }
    public Object getValue() {
        return value;
    }

}
