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

import com.sun.xml.internal.ws.encoding.soap.streaming.SOAPNamespaceConstants;
import com.sun.xml.internal.ws.encoding.soap.streaming.SOAP12NamespaceConstants;
import com.sun.xml.internal.ws.encoding.soap.SOAP12Constants;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.XMLStreamException;
import java.util.Iterator;

/**
 * SOAP 1.2 soapenv:Code
 *
 * <soapenv:Fault>
 *  <soapenv:Code>
 *      <soapenv:Value>soapenv:Sender</soapenv:Value>
 *      <soapenv:Subcode>
 *          <soapenv:Value>ns1:incorectRequest</Value>
 *          <soapenv:Subcode>
 *          ...
 *      </soapenv:Subcode>
 *  </soapenv:Code>
 * </soapenv:Fault>
 *
 * @author Vivek Pandey
 */
public class FaultSubcode {
    private QName value;
    private FaultSubcode subcode;

    public FaultSubcode(QName value, FaultSubcode subcode) {
        this.value = value;
        this.subcode = subcode;
    }

    public FaultSubcode(QName value, Iterator<QName> subcodes) {
        this.value = value;
        if(subcodes.hasNext()){
            subcode = new FaultSubcode(subcodes.next(), subcodes);
        }
    }

    public QName getValue() {
        return value;
    }

    public FaultSubcode getSubcode() {
        return subcode;
    }

    public FaultSubcode setSubCode(FaultSubcode sc){
        this.subcode = sc;
        return subcode;
    }

    void write(XMLStreamWriter writer) throws XMLStreamException {
        // <soapenv:Subcode>
        if(value == null)
            return;
        writer.writeStartElement(SOAPNamespaceConstants.NSPREFIX_SOAP_ENVELOPE,
            SOAP12Constants.QNAME_FAULT_SUBCODE.getLocalPart(), SOAP12NamespaceConstants.ENVELOPE);

        // <soapenv:Value>
        writer.writeStartElement(SOAPNamespaceConstants.NSPREFIX_SOAP_ENVELOPE,
            SOAP12Constants.QNAME_FAULT_VALUE.getLocalPart(), SOAP12NamespaceConstants.ENVELOPE);

        writer.setPrefix(value.getPrefix(), value.getNamespaceURI());
        if(value.getPrefix().equals(""))
            writer.writeCharacters(value.getLocalPart());
        else
            writer.writeCharacters(value.getPrefix()+":"+value.getLocalPart());
        writer.writeEndElement(); // </soapenv:Value>
        if(subcode != null)
            subcode.write(writer);
        writer.writeEndElement(); // </soapenv:Subcode>
    }
}
