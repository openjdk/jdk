/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.spi.db;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;

import javax.xml.bind.JAXBException;
import javax.xml.bind.attachment.AttachmentMarshaller;
import javax.xml.bind.attachment.AttachmentUnmarshaller;
import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Result;
import javax.xml.transform.Source;

import org.w3c.dom.Node;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

//import com.sun.xml.internal.ws.spi.db.BindingContext;
//import com.sun.xml.internal.ws.spi.db.RepeatedElementBridge;
//import com.sun.xml.internal.ws.spi.db.XMLBridge;
//import com.sun.xml.internal.ws.spi.db.DatabindingException;
//import com.sun.xml.internal.ws.spi.db.TypeInfo;
//import com.sun.xml.internal.ws.spi.db.WrapperComposite;

/**
 * WrapperBridge handles RPC-Literal body and Document-Literal wrappers without static
 * wrapper classes.
 *
 * @author shih-chang.chen@oracle.com
 */
public class WrapperBridge<T> implements XMLBridge<T> {

    BindingContext parent;
    TypeInfo typeInfo;
    static final String WrapperPrefix  = "w";
    static final String WrapperPrefixColon = WrapperPrefix + ":";

    public WrapperBridge(BindingContext p, TypeInfo ti) {
        this.parent = p;
        this.typeInfo = ti;
    }

    @Override
    public BindingContext context() {
        return parent;
    }

    @Override
    public TypeInfo getTypeInfo() {
        return typeInfo;
    }

    @Override
    public final void marshal(T object, ContentHandler contentHandler, AttachmentMarshaller am) throws JAXBException {
        WrapperComposite w = (WrapperComposite) object;
        Attributes att = new Attributes() {
            @Override public int getLength() { return 0; }
            @Override public String getURI(int index) { return null; }
            @Override public String getLocalName(int index)  { return null; }
            @Override public String getQName(int index) { return null; }
            @Override public String getType(int index) { return null; }
            @Override public String getValue(int index)  { return null; }
            @Override public int getIndex(String uri, String localName)  { return 0; }
            @Override public int getIndex(String qName) {  return 0; }
            @Override public String getType(String uri, String localName)  { return null; }
            @Override public String getType(String qName)  { return null; }
            @Override public String getValue(String uri, String localName)  { return null; }
            @Override public String getValue(String qName)  { return null; }
        };
        try {
            contentHandler.startPrefixMapping(WrapperPrefix, typeInfo.tagName.getNamespaceURI());
            contentHandler.startElement(typeInfo.tagName.getNamespaceURI(), typeInfo.tagName.getLocalPart(), WrapperPrefixColon + typeInfo.tagName.getLocalPart(), att);
        } catch (SAXException e) {
            throw new JAXBException(e);
        }
        if (w.bridges != null) for (int i = 0; i < w.bridges.length; i++) {
            if (w.bridges[i] instanceof RepeatedElementBridge) {
                RepeatedElementBridge rbridge = (RepeatedElementBridge) w.bridges[i];
                for (Iterator itr = rbridge.collectionHandler().iterator(w.values[i]); itr.hasNext();) {
                    rbridge.marshal(itr.next(), contentHandler, am);
                }
            } else {
                w.bridges[i].marshal(w.values[i], contentHandler, am);
            }
        }
        try {
            contentHandler.endElement(typeInfo.tagName.getNamespaceURI(), typeInfo.tagName.getLocalPart(), null);
            contentHandler.endPrefixMapping(WrapperPrefix);
        } catch (SAXException e) {
            throw new JAXBException(e);
        }
//      bridge.marshal(object, contentHandler, am);
    }

    @Override
    public void marshal(T object, Node output) throws JAXBException {
        throw new UnsupportedOperationException();
//      bridge.marshal(object, output);
//      bridge.marshal((T) convert(object), output);
    }

    @Override
    public void marshal(T object, OutputStream output, NamespaceContext nsContext, AttachmentMarshaller am) throws JAXBException {
//      bridge.marshal((T) convert(object), output, nsContext, am);
    }

    @Override
    public final void marshal(T object, Result result) throws JAXBException {
        throw new UnsupportedOperationException();
//      bridge.marshal(object, result);
    }

    @Override
    public final void marshal(T object, XMLStreamWriter output, AttachmentMarshaller am) throws JAXBException {
        WrapperComposite w = (WrapperComposite) object;
        try {
//          output.writeStartElement(typeInfo.tagName.getNamespaceURI(), typeInfo.tagName.getLocalPart());
//          System.out.println(typeInfo.tagName.getNamespaceURI());

            //The prefix is to workaround an eclipselink bug
            String prefix = output.getPrefix(typeInfo.tagName.getNamespaceURI());
            if (prefix == null) prefix = WrapperPrefix;
            output.writeStartElement(prefix, typeInfo.tagName.getLocalPart(), typeInfo.tagName.getNamespaceURI());
            output.writeNamespace(prefix, typeInfo.tagName.getNamespaceURI());

//          output.writeStartElement("", typeInfo.tagName.getLocalPart(), typeInfo.tagName.getNamespaceURI());
//          output.writeDefaultNamespace(typeInfo.tagName.getNamespaceURI());
//          System.out.println("======== " + output.getPrefix(typeInfo.tagName.getNamespaceURI()));
//          System.out.println("======== " + output.getNamespaceContext().getPrefix(typeInfo.tagName.getNamespaceURI()));
//          System.out.println("======== " + output.getNamespaceContext().getNamespaceURI(""));
        } catch (XMLStreamException e) {
            e.printStackTrace();
            throw new DatabindingException(e);
        }
        if (w.bridges != null) for (int i = 0; i < w.bridges.length; i++) {
            if (w.bridges[i] instanceof RepeatedElementBridge) {
                RepeatedElementBridge rbridge = (RepeatedElementBridge) w.bridges[i];
                for (Iterator itr = rbridge.collectionHandler().iterator(w.values[i]); itr.hasNext();) {
                    rbridge.marshal(itr.next(), output, am);
                }
            } else {
                w.bridges[i].marshal(w.values[i], output, am);
            }
        }
        try {
            output.writeEndElement();
        } catch (XMLStreamException e) {
            throw new DatabindingException(e);
        }
    }

    @Override
    public final T unmarshal(InputStream in) throws JAXBException {
        //EndpointArgumentsBuilder.RpcLit.readRequest
        throw new UnsupportedOperationException();
//      return bridge.unmarshal(in);
    }

    @Override
    public final T unmarshal(Node n, AttachmentUnmarshaller au) throws JAXBException {
        //EndpointArgumentsBuilder.RpcLit.readRequest
        throw new UnsupportedOperationException();
//      return bridge.unmarshal(n, au);
    }

    @Override
    public final T unmarshal(Source in, AttachmentUnmarshaller au) throws JAXBException {
        //EndpointArgumentsBuilder.RpcLit.readRequest
        throw new UnsupportedOperationException();
//      return bridge.unmarshal(in, au);
    }

    @Override
    public final T unmarshal(XMLStreamReader in, AttachmentUnmarshaller au) throws JAXBException {
        //EndpointArgumentsBuilder.RpcLit.readRequest
        throw new UnsupportedOperationException();
//      return bridge.unmarshal(in, au);
    }

    @Override
    public boolean supportOutputStream() {
        return false;
    }
}
