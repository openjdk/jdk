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
/*
 * $Id: FaultElement1_2Impl.java,v 1.13 2006/01/27 12:49:48 vj135062 Exp $
 */



/**
*
* @author SAAJ RI Development Team
*/
package com.sun.xml.internal.messaging.saaj.soap.ver1_2;

import javax.xml.namespace.QName;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.Name;

import com.sun.xml.internal.messaging.saaj.soap.SOAPDocumentImpl;
import com.sun.xml.internal.messaging.saaj.soap.impl.FaultElementImpl;
import com.sun.xml.internal.messaging.saaj.soap.name.NameImpl;
import com.sun.xml.internal.messaging.saaj.SOAPExceptionImpl;

public class FaultElement1_2Impl extends FaultElementImpl {

    public FaultElement1_2Impl(SOAPDocumentImpl ownerDoc, NameImpl qname) {
        super(ownerDoc, qname);
    }

    public FaultElement1_2Impl(SOAPDocumentImpl ownerDoc, QName qname) {
        super(ownerDoc, qname);
    }

    public FaultElement1_2Impl(SOAPDocumentImpl ownerDoc, String localName) {
        super(ownerDoc, NameImpl.createSOAP12Name(localName));
    }

    protected boolean isStandardFaultElement() {
        String localName = elementQName.getLocalPart();
        if (localName.equalsIgnoreCase("code") ||
            localName.equalsIgnoreCase("reason") ||
            localName.equalsIgnoreCase("node") ||
            localName.equalsIgnoreCase("role")) {
            return true;
        }
        return false;
    }

    public SOAPElement setElementQName(QName newName) throws SOAPException {
        if (!isStandardFaultElement()) {
            FaultElement1_2Impl copy =
                new FaultElement1_2Impl((SOAPDocumentImpl) getOwnerDocument(), newName);
            return replaceElementWithSOAPElement(this,copy);
        } else {
            return super.setElementQName(newName);
        }
    }

    public void setEncodingStyle(String encodingStyle) throws SOAPException {
        log.severe("SAAJ0408.ver1_2.no.encodingStyle.in.fault.child");
        throw new SOAPExceptionImpl("encodingStyle attribute cannot appear on a Fault child element");
    }

    public SOAPElement addAttribute(Name name, String value)
        throws SOAPException {
        if (name.getLocalName().equals("encodingStyle")
            && name.getURI().equals(NameImpl.SOAP12_NAMESPACE)) {
            setEncodingStyle(value);
        }
        return super.addAttribute(name, value);
    }

    public SOAPElement addAttribute(QName name, String value)
        throws SOAPException {
        if (name.getLocalPart().equals("encodingStyle")
            && name.getNamespaceURI().equals(NameImpl.SOAP12_NAMESPACE)) {
            setEncodingStyle(value);
        }
        return super.addAttribute(name, value);
    }
}
