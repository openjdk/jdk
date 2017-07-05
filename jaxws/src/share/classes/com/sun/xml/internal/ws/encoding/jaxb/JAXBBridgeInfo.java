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

import com.sun.xml.internal.bind.api.Bridge;
import com.sun.xml.internal.bind.api.TypeReference;
import com.sun.xml.internal.bind.api.BridgeContext;
import com.sun.xml.internal.ws.encoding.soap.SerializationException;
import com.sun.xml.internal.ws.encoding.soap.DeserializationException;
import com.sun.xml.internal.ws.streaming.XMLStreamReaderUtil;

import javax.xml.namespace.QName;
import javax.xml.namespace.NamespaceContext;
import javax.xml.bind.JAXBException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.transform.Source;
import java.io.OutputStream;
import java.io.InputStream;

import org.w3c.dom.Node;

/**
 * XML infoset represented as a JAXB object and {@link Bridge}.
 *
 * @author WS Development Team
 */
public final class JAXBBridgeInfo {
    private final Bridge bridge;
    private Object value;

    public JAXBBridgeInfo(Bridge bridge) {
        this.bridge = bridge;
    }

    public JAXBBridgeInfo(Bridge bridge, Object value) {
        this(bridge);
        this.value = value;
    }

    public QName getName() {
        return bridge.getTypeReference().tagName;
    }

    public TypeReference getType(){
        return bridge.getTypeReference();
    }

    public Bridge getBridge() {
        return bridge;
    }

    public Object getValue() {
        return value;
    }

    public static JAXBBridgeInfo copy(JAXBBridgeInfo payload) {
        return new JAXBBridgeInfo(payload.getBridge(), payload.getValue());
    }

    /**
     * JAXB object is serialized. Note that the BridgeContext is cached per
     * thread, and JAXBBridgeInfo should contain correct BridgeContext for the
     * current thread.
     */
    public void serialize(BridgeContext bridgeContext, OutputStream os, NamespaceContext nsContext) {
        try {
            bridge.marshal(bridgeContext, value, os, nsContext);
        } catch (JAXBException e) {
            throw new SerializationException(e);
        }
    }

    /**
     * Serialize to StAX.
     */
    public void serialize(BridgeContext bridgeContext, XMLStreamWriter writer) {
        try {
            bridge.marshal(bridgeContext, value, writer);
        } catch (JAXBException e) {
            throw new SerializationException(e);
        }
    }

    /**
     * Serialize to DOM.
     */
    public void serialize(BridgeContext bridgeContext, Node node) {
        try {
            bridge.marshal(bridgeContext, value, node);
        } catch (JAXBException e) {
            throw new SerializationException(e);
        }
    }

    public void deserialize(Source source, BridgeContext bridgeContext) {
        try {
            value = bridge.unmarshal(bridgeContext, source);
        } catch (JAXBException e) {
            throw new DeserializationException(e);
        }
    }

    public void deserialize(InputStream stream, BridgeContext bridgeContext) {
        try {
            value = bridge.unmarshal(bridgeContext, stream);
        } catch (JAXBException e) {
            throw new DeserializationException(e);
        }
    }

    /*
    * JAXB object is deserialized and is set in JAXBBridgeInfo. Note that
    * the BridgeContext is cached per thread, and JAXBBridgeInfo should contain
    * correct BridgeContext for the current thread.
    */
    public void deserialize(XMLStreamReader reader, BridgeContext bridgeContext)  {
        try {
            value = bridge.unmarshal(bridgeContext, reader);

            // reader could be left on CHARS token rather than </body>
            if (reader.getEventType() == XMLStreamConstants.CHARACTERS &&
                    reader.isWhiteSpace()) {
                XMLStreamReaderUtil.nextContent(reader);
            }
        } catch (JAXBException e) {
            throw new DeserializationException(e);
        }
    }
}
