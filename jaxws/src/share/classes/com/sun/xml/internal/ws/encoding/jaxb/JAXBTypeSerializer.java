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

import com.sun.xml.internal.bind.api.BridgeContext;
import com.sun.xml.internal.bind.v2.runtime.MarshallerImpl;
import com.sun.xml.internal.ws.encoding.soap.DeserializationException;
import com.sun.xml.internal.ws.encoding.soap.SerializationException;
import com.sun.xml.internal.ws.streaming.XMLStreamReaderUtil;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import java.io.OutputStream;

/**
 * @author Vivek Pandey
 */
public final class JAXBTypeSerializer {
    private JAXBTypeSerializer() {
    }    // no instanciation please

    public static void serialize(Object obj, XMLStreamWriter writer, JAXBContext context) {

        try {
            Marshaller marshaller = context.createMarshaller();
            marshaller.setProperty("jaxb.fragment", Boolean.TRUE);
            marshaller.marshal(obj, writer);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    public static void serialize(Object obj, XMLStreamWriter writer,
                                 JAXBContext context, Marshaller marshaller) {

        try {
            if (marshaller == null)
                marshaller = context.createMarshaller();
            marshaller.setProperty("jaxb.fragment", Boolean.TRUE);
            marshaller.marshal(obj, writer);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }


    /* for FI, it will be a whole document, not fragment
     * called by setPayload and writeTo methods in XMLMessage class
     */
    public static void serializeDocument(Object obj, XMLStreamWriter writer, JAXBContext context) {
        try {
            Marshaller marshaller = context.createMarshaller();
            marshaller.marshal(obj, writer);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    public static void serialize(Object obj, OutputStream os,
                                 JAXBContext context, Marshaller marshaller) {

        try {
            if (marshaller == null)
                marshaller = context.createMarshaller();
            marshaller.setProperty("jaxb.fragment", Boolean.TRUE);
            marshaller.marshal(obj, os);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    public static void serialize(Object obj, OutputStream os, JAXBContext context) {

        try {
            Marshaller marshaller = context.createMarshaller();
            marshaller.setProperty("jaxb.fragment", Boolean.TRUE);
            marshaller.marshal(obj, os);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    /*
    * Marshalls arbitrary type object with the given tag name
    */
    public static DOMSource serialize(Object bean, JAXBContext context) {
        try {
            Marshaller marshaller = context.createMarshaller();
            marshaller.setProperty("jaxb.fragment", Boolean.TRUE);
            DOMResult domResult = new DOMResult();
            marshaller.marshal(bean, domResult);
            return new DOMSource(domResult.getNode());
        } catch (JAXBException e) {
            throw new SerializationException(e);
        }
    }

    /*
    * @see JAXBTypeSerializerIf#deserialize(XMLStreamReader,JAXBContext)
    */
    public static Object deserialize(XMLStreamReader reader, JAXBContext context) {
        Object obj = null;
        try {
            Unmarshaller unmarshaller = context.createUnmarshaller();
            if (reader.getEventType() == XMLStreamConstants.START_ELEMENT)
                obj = unmarshaller.unmarshal(reader);

            // reader could be left on CHARS token rather than </body>
            if (reader.getEventType() == XMLStreamConstants.CHARACTERS
                && reader.isWhiteSpace()) {
                XMLStreamReaderUtil.nextContent(reader);
            }
            return obj;

        } catch (DeserializationException e) {
            throw e;
        } catch (Exception e) {
            throw new DeserializationException(e);
        }
    }

    public static Object deserialize(XMLStreamReader reader, JAXBContext context, Unmarshaller bc) {
        Object obj = null;
        try {
            Unmarshaller unmarshaller = context.createUnmarshaller();
            if (bc != null)
                unmarshaller.setAttachmentUnmarshaller(bc.getAttachmentUnmarshaller());

            if (reader.getEventType() == XMLStreamConstants.START_ELEMENT)
                obj = unmarshaller.unmarshal(reader);

            // reader could be left on CHARS token rather than </body>
            if (reader.getEventType() == XMLStreamConstants.CHARACTERS
                && reader.isWhiteSpace()) {
                XMLStreamReaderUtil.nextContent(reader);
            }
            return obj;

        } catch (DeserializationException e) {
            throw e;
        } catch (Exception e) {
            throw new DeserializationException(e);
        }
    }

    /*
     * convert JAXB bean as a Source
     *
    public static Object toSource(Object obj, JAXBContext context) {
        try {
            // Use ctxt to marshall the object
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            Marshaller marshaller = context.createMarshaller();
            marshaller.setProperty("jaxb.fragment", Boolean.TRUE);
            marshaller.marshal(obj, bos);
            ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
            return new StreamSource(bis);
        } catch (JAXBException e) {
            throw new DeserializationException(new LocalizableExceptionAdapter(
                    e));
        }
    }
    */

    /*
    * Convert Source object as a JAXB bean
    */
    public static Object deserialize(Source source, JAXBContext context) {
        try {
            Unmarshaller unmarshaller = context.createUnmarshaller();
            return unmarshaller.unmarshal(source);
        } catch (JAXBException e) {
            throw new DeserializationException(e);
        }
    }


}
