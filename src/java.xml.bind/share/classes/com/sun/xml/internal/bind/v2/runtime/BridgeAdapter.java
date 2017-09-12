/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.bind.v2.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.xml.bind.JAXBException;
import javax.xml.bind.MarshalException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.UnmarshalException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Result;
import javax.xml.transform.Source;

import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.bind.api.Bridge;
import com.sun.xml.internal.bind.api.TypeReference;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.UnmarshallerImpl;

import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * {@link Bridge} decorator for {@link XmlAdapter}.
 *
 * @author Kohsuke Kawaguchi
 */
final class BridgeAdapter<OnWire,InMemory> extends InternalBridge<InMemory> {
    private final InternalBridge<OnWire> core;
    private final Class<? extends XmlAdapter<OnWire,InMemory>> adapter;

    public BridgeAdapter(InternalBridge<OnWire> core, Class<? extends XmlAdapter<OnWire,InMemory>> adapter) {
        super(core.getContext());
        this.core = core;
        this.adapter = adapter;
    }

    public void marshal(Marshaller m, InMemory inMemory, XMLStreamWriter output) throws JAXBException {
        core.marshal(m,adaptM(m,inMemory),output);
    }

    public void marshal(Marshaller m, InMemory inMemory, OutputStream output, NamespaceContext nsc) throws JAXBException {
        core.marshal(m,adaptM(m,inMemory),output,nsc);
    }

    public void marshal(Marshaller m, InMemory inMemory, Node output) throws JAXBException {
        core.marshal(m,adaptM(m,inMemory),output);
    }

    public void marshal(Marshaller context, InMemory inMemory, ContentHandler contentHandler) throws JAXBException {
        core.marshal(context,adaptM(context,inMemory),contentHandler);
    }

    public void marshal(Marshaller context, InMemory inMemory, Result result) throws JAXBException {
        core.marshal(context,adaptM(context,inMemory),result);
    }

    private OnWire adaptM(Marshaller m,InMemory v) throws JAXBException {
        XMLSerializer serializer = ((MarshallerImpl)m).serializer;
        serializer.pushCoordinator();
        try {
            return _adaptM(serializer, v);
        } finally {
            serializer.popCoordinator();
        }
    }

    private OnWire _adaptM(XMLSerializer serializer, InMemory v) throws MarshalException {
        XmlAdapter<OnWire,InMemory> a = serializer.getAdapter(adapter);
        try {
            return a.marshal(v);
        } catch (Exception e) {
            serializer.handleError(e,v,null);
            throw new MarshalException(e);
        }
    }


    public @NotNull InMemory unmarshal(Unmarshaller u, XMLStreamReader in) throws JAXBException {
        return adaptU(u, core.unmarshal(u,in));
    }

    public @NotNull InMemory unmarshal(Unmarshaller u, Source in) throws JAXBException {
        return adaptU(u, core.unmarshal(u,in));
    }

    public @NotNull InMemory unmarshal(Unmarshaller u, InputStream in) throws JAXBException {
        return adaptU(u, core.unmarshal(u,in));
    }

    public @NotNull InMemory unmarshal(Unmarshaller u, Node n) throws JAXBException {
        return adaptU(u, core.unmarshal(u,n));
    }

    public TypeReference getTypeReference() {
        return core.getTypeReference();
    }

    private @NotNull InMemory adaptU(Unmarshaller _u, OnWire v) throws JAXBException {
        UnmarshallerImpl u = (UnmarshallerImpl) _u;
        XmlAdapter<OnWire,InMemory> a = u.coordinator.getAdapter(adapter);
        u.coordinator.pushCoordinator();
        try {
            return a.unmarshal(v);
        } catch (Exception e) {
            throw new UnmarshalException(e);
        } finally {
            u.coordinator.popCoordinator();
        }
    }

    void marshal(InMemory o, XMLSerializer out) throws IOException, SAXException, XMLStreamException {
        try {
            core.marshal(_adaptM( XMLSerializer.getInstance(), o ), out );
        } catch (MarshalException e) {
            // recover from error by not marshalling this element.
        }
    }
}
