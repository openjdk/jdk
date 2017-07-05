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

package com.sun.xml.internal.ws.api.message;

import com.sun.xml.internal.bind.api.Bridge;
import com.sun.xml.internal.bind.api.JAXBRIContext;
import com.sun.xml.internal.bind.v2.runtime.MarshallerImpl;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.pipe.Pipe;
import com.sun.xml.internal.ws.message.DOMHeader;
import com.sun.xml.internal.ws.message.StringHeader;
import com.sun.xml.internal.ws.message.jaxb.JAXBHeader;
import com.sun.xml.internal.ws.message.saaj.SAAJHeader;
import com.sun.xml.internal.ws.message.stream.StreamHeader11;
import com.sun.xml.internal.ws.message.stream.StreamHeader12;
import org.w3c.dom.Element;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.Marshaller;
import javax.xml.namespace.QName;
import javax.xml.soap.SOAPHeaderElement;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Factory methods for various {@link Header} implementations.
 *
 * <p>
 * This class provides various methods to create different
 * flavors of {@link Header} classes that store data
 * in different formats.
 *
 * <p>
 * This is a part of the JAX-WS RI internal API so that
 * {@link Pipe} implementations can reuse the implementations
 * done inside the JAX-WS without having a strong dependency
 * to the actual class.
 *
 * <p>
 * If you find some of the useful convenience methods missing
 * from this class, please talk to us.
 *
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class Headers {
    private Headers() {}

    /**
     * @deprecated
     *      Use {@link #create(JAXBRIContext, Object)} instead.
     */
    public static Header create(SOAPVersion soapVersion, Marshaller m, Object o) {
        return new JAXBHeader(((MarshallerImpl)m).getContext(),o);
    }

    /**
     * Creates a {@link Header} backed a by a JAXB bean.
     */
    public static Header create(JAXBRIContext context, Object o) {
        return new JAXBHeader(context,o);
    }

    /**
     * Creates a {@link Header} backed a by a JAXB bean, with the given tag name.
     *
     * See {@link #create(SOAPVersion, Marshaller, Object)} for the meaning
     * of other parameters.
     *
     * @param tagName
     *      The name of the newly created header. Must not be null.
     * @param o
     *      The JAXB bean that represents the contents of the header. Must not be null.
     */
    public static Header create(SOAPVersion soapVersion, Marshaller m, QName tagName, Object o) {
        return create(soapVersion,m,new JAXBElement(tagName,o.getClass(),o));
    }

    /**
     * Creates a {@link Header} backed a by a JAXB bean.
     */
    public static Header create(Bridge bridge, Object jaxbObject) {
        return new JAXBHeader(bridge, jaxbObject);
    }

    /**
     * Creates a new {@link Header} backed by a SAAJ object.
     */
    public static Header create(SOAPHeaderElement header) {
        return new SAAJHeader(header);
    }

    /**
     * Creates a new {@link Header} backed by an {@link Element}.
     */
    public static Header create( Element node ) {
        return new DOMHeader<Element>(node);
    }

    /**
     * @deprecated
     *      Use {@link #create(Element)}
     */
    public static Header create( SOAPVersion soapVersion, Element node ) {
        return create(node);
    }

    /**
     * Creates a new {@link Header} that reads from {@link XMLStreamReader}.
     *
     * <p>
     * Note that the header implementation will read the entire data
     * into memory anyway, so this might not be as efficient as you might hope.
     */
    public static Header create( SOAPVersion soapVersion, XMLStreamReader reader ) throws XMLStreamException {
        switch(soapVersion) {
        case SOAP_11:
            return new StreamHeader11(reader);
        case SOAP_12:
            return new StreamHeader12(reader);
        default:
            throw new AssertionError();
        }
    }

    /**
     * Creates a new {@link Header} that that has a single text value in it
     * (IOW, of the form &lt;foo>text&lt;/foo>.)
     *
     * @param name QName of the header element
     * @param value text value of the header
     */
    public static Header create(QName name, String value) {
        return new StringHeader(name, value);
    }
}
