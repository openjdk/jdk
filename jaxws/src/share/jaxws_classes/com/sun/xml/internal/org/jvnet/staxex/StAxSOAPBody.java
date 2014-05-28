/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.org.jvnet.staxex;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

/**
 * A StAxSOAPBody is a SOAPBody that allows to be loaded from a StAX style payload.
 *
 * @author shih-chang.chen@oracle.com
 */
public interface StAxSOAPBody {

    /**
     * The StAxSOAPBody represents the StAX source of SOAPBody payload.
     */
    public static interface Payload {

        /**
         * Retrieve payload qname without materializing its contents
         * @return
         */
       public QName getPayloadQName();

       public XMLStreamReader readPayload() throws XMLStreamException;

       public void writePayloadTo(XMLStreamWriter writer)throws XMLStreamException;

       /**
        * Retrieve payload attribute value without materializing its contents
        * @param localName
        * @return
        * @throws XMLStreamException
        */
       public String getPayloadAttributeValue(String localName) throws XMLStreamException;

       /**
        * Retrieve payload attribute value without materializing its contents
        * @param qName
        * @return
        * @throws XMLStreamException
        */
       public String getPayloadAttributeValue(QName qName) throws XMLStreamException;

       public void materialize() throws XMLStreamException;
    }

    public void setPayload(Payload src) throws XMLStreamException;

    public Payload getPayload()throws XMLStreamException;

    public boolean hasStaxPayload();
 }
