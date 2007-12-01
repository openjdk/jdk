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

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.ws.WebServiceException;

import com.sun.xml.internal.ws.encoding.soap.streaming.SOAPNamespaceConstants;
import com.sun.xml.internal.ws.encoding.soap.streaming.SOAP12NamespaceConstants;

/**
 * SOAP 1.2 version of NotUnderstoodHeaderBlock.
 *
 * @author WS Development Team
 */
public class SOAP12NotUnderstoodHeaderBlock extends HeaderBlock {

    private QName nuHeader;

    // super(null) is a hack in this case
    public SOAP12NotUnderstoodHeaderBlock(QName header) {
        super(null);
        nuHeader = header;
    }

    public QName getName() {
        return new QName(SOAP12NamespaceConstants.ENVELOPE,
            SOAP12NamespaceConstants.TAG_NOT_UNDERSTOOD);
    }

    public void write(XMLStreamWriter writer) {
        try {
            String prefix = "t"; // should not have been used before <header>
            writer.writeStartElement(
                SOAPNamespaceConstants.NSPREFIX_SOAP_ENVELOPE,
                SOAP12NamespaceConstants.TAG_NOT_UNDERSTOOD,
                SOAP12NamespaceConstants.ENVELOPE);
            writer.writeAttribute(
                SOAP12NamespaceConstants.ATTR_NOT_UNDERSTOOD_QNAME,
                prefix + ":" + nuHeader.getLocalPart());
            writer.writeNamespace(prefix, nuHeader.getNamespaceURI());
            writer.writeEndElement();
        } catch (XMLStreamException e) {
            throw new WebServiceException(e);
        }
    }

}
