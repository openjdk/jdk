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

package com.sun.xml.internal.ws.encoding.soap.message;

import com.sun.xml.internal.ws.encoding.jaxb.JAXBBridgeInfo;
import com.sun.xml.internal.ws.util.SOAPUtil;

import javax.xml.namespace.QName;
import javax.xml.soap.SOAPFault;
import javax.xml.soap.Detail;
import javax.xml.ws.soap.SOAPBinding;

/**
 * @author WS Development Team
 */
public class SOAPFaultInfo {

    public SOAPFaultInfo(){}
    /**
     * create SOAPFaultInfo with SOAPFault
     *
     * @param fault
     */
    public SOAPFaultInfo(SOAPFault fault) {
        this.soapFault = fault;
    }

    /**
     * Accessor method to get the fault bean
     *
     * @return the JAXBBidgeInfo for this fault
     */
    public JAXBBridgeInfo getFaultBean() {
        return faultBean;
    }

    /**
     * creates SOAPFaultInfo, could be SOAP 1.1 or SOAP 1.2 fault.
     *
     * @param string
     * @param code
     * @param actor
     * @param detail
     * @param bindingId
     */
    public SOAPFaultInfo(String string, QName code, String actor, Object detail, String bindingId) {
        if (detail == null || detail instanceof Detail) {
            Detail det = (detail != null) ? (Detail) detail : null;
            soapFault = SOAPUtil.createSOAPFault(string, code, actor, det, bindingId);
        } else {
            soapFault = SOAPUtil.createSOAPFault(string, code, actor, null, bindingId);
            faultBean = (JAXBBridgeInfo) detail;
        }
    }

    public QName getCode() {
        return soapFault.getFaultCodeAsQName();
    }

    public String getString() {
        return soapFault.getFaultString();
    }

    public String getActor() {
        return soapFault.getFaultActor();
    }

    public Object getDetail() {
        if (faultBean != null)
            return faultBean;

        return soapFault.getDetail();
    }

    public SOAPFault getSOAPFault() {
        return soapFault;
    }

    protected SOAPFault soapFault;
    protected JAXBBridgeInfo faultBean;
}
