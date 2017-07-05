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

import com.sun.xml.internal.ws.pept.ept.MessageInfo;
import com.sun.xml.internal.bind.api.BridgeContext;
import static com.sun.xml.internal.ws.client.BindingProviderProperties.JAXB_OUTPUTSTREAM;
import com.sun.xml.internal.ws.encoding.soap.DeserializationException;
import com.sun.xml.internal.ws.encoding.soap.SerializationException;
import com.sun.xml.internal.ws.streaming.XMLStreamReaderUtil;

import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.io.OutputStream;

public class RpcLitPayloadSerializer {

    /*
     * Uses BridgeContext to serialize the rpc/lit payload. First it writes
     * the operation, and then it serializes each parameter
     */
    public static void serialize(RpcLitPayload obj, BridgeContext bridgeContext,
        MessageInfo messageInfo, XMLStreamWriter writer)
    {
        try {
            QName op = obj.getOperation();
            String opURI = op.getNamespaceURI();

            writer.writeStartElement("ans", op.getLocalPart(), opURI);
            writer.setPrefix("ans", opURI);
            writer.writeNamespace("ans", opURI);


            // Pass output stream directly to JAXB when available
            OutputStream os = (OutputStream) messageInfo.getMetaData(JAXB_OUTPUTSTREAM);
            if (os != null) {
                /*
                 * Make sure that current element is closed before passing the
                 * output stream to JAXB. Using Zephyr, it suffices to write
                 * an empty string (TODO: other StAX impls?).
                 */
                writer.writeCharacters("");

                // Flush output of StAX serializer
                writer.flush();

                NamespaceContext nsc = writer.getNamespaceContext();

                // Let JAXB serialize each param to the output stream
                for (JAXBBridgeInfo param : obj.getBridgeParameters()) {
                    param.serialize(bridgeContext, os, nsc);
                }
            }
            else {
                // Otherwise, use a StAX writer
                for (JAXBBridgeInfo param : obj.getBridgeParameters()) {
                    param.serialize(bridgeContext, writer);
                }
            }

            writer.writeEndElement();            // </ans:operation>
        }
        catch (XMLStreamException e) {
            throw new SerializationException(e);
        }
    }

    public static void serialize(RpcLitPayload obj, BridgeContext bridgeContext, OutputStream writer) {
        QName op = obj.getOperation();
        String opURI = op.getNamespaceURI();
        String startElm = "<ans:"+op.getLocalPart()+" xmlns:ans=\""+opURI+"\">";
        String endElm="</ans:"+op.getLocalPart()+">";
        try {
            writer.write(startElm.getBytes());
            for (JAXBBridgeInfo param : obj.getBridgeParameters()) {
                param.serialize(bridgeContext,writer,null);
            }
            writer.write(endElm.getBytes());
        } catch (IOException e) {
            throw new SerializationException(e);
        }
    }

    /*
     * Uses BridgeContext to deserialize the rpc/lit payload. First it reads
     * the operation element, and then it deserializes each parameter. If the
     * expected parameter is not found, it throws an exception
     */
    public static void deserialize(XMLStreamReader reader, RpcLitPayload payload,
        BridgeContext bridgeContext)
    {
        XMLStreamReaderUtil.nextElementContent(reader);     // </operation> or <partName>
        for (JAXBBridgeInfo param: payload.getBridgeParameters()) {
            // throw exception if the part accessor name is not what we expect
            QName partName = reader.getName();
            if (!partName.equals(param.getName())) {
                throw new DeserializationException("xsd.unexpectedElementName",
                        new Object[]{param.getName(), partName});
            }
            param.deserialize(reader, bridgeContext);

            // reader could be left on CHARS token rather than <partName>
            if (reader.getEventType() == XMLStreamConstants.CHARACTERS &&
                    reader.isWhiteSpace()) {
                XMLStreamReaderUtil.nextContent(reader);
            }
        }
        XMLStreamReaderUtil.nextElementContent(reader);     // </env:body>
    }
}
