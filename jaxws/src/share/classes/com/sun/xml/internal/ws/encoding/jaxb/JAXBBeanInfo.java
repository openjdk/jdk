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

import com.sun.xml.internal.ws.encoding.soap.SerializationException;
import com.sun.xml.internal.bind.api.BridgeContext;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.Source;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.XMLStreamReader;
import java.io.OutputStream;

/**
 * XML infoset represented as a JAXB object.
 *
 * @author WS Development Team
 */
public final class JAXBBeanInfo {
    private final Object jaxbBean;
    private JAXBContext jaxbContext;
    private BridgeContext bc;
    private Marshaller marshaller;
    private Unmarshaller unmarshaller;

    public JAXBBeanInfo(Object payload, JAXBContext jaxbContext) {
        this.jaxbBean = payload;
        this.jaxbContext = jaxbContext;
    }

    public static JAXBBeanInfo fromSource(Source source, JAXBContext context) {
        Object obj = JAXBTypeSerializer.deserialize(source, context);
        return new JAXBBeanInfo(obj, context);
    }

    public static JAXBBeanInfo fromStAX(XMLStreamReader reader, JAXBContext context) {

        Object obj = JAXBTypeSerializer.deserialize(reader, context);
        return new JAXBBeanInfo(obj, context);
    }

    public static JAXBBeanInfo fromStAX(XMLStreamReader reader, JAXBContext context, Unmarshaller um) {

        Object obj = JAXBTypeSerializer.deserialize(reader, context, um);
        return new JAXBBeanInfo(obj, context);
    }



    public Object getBean() {
        return jaxbBean;
    }

    public JAXBContext getJAXBContext() {
        return jaxbContext;
    }

    /**
     * Creates a {@link DOMSource} from this JAXB bean.
     */
    public DOMSource toDOMSource() {
        return JAXBTypeSerializer.serialize(jaxbBean,jaxbContext);
    }

    /**
     * Writes this bean to StAX.
     */
        public void writeTo(XMLStreamWriter w) {
            if (marshaller != null)
                JAXBTypeSerializer.serialize(jaxbBean, w, jaxbContext, marshaller);
            else
            JAXBTypeSerializer.serialize(jaxbBean, w, jaxbContext);
        }

        public void writeTo(OutputStream os) {
            if (marshaller != null)
                JAXBTypeSerializer.serialize(jaxbBean, os, jaxbContext, marshaller);
            else
             JAXBTypeSerializer.serialize(jaxbBean,os,jaxbContext);
        }

    public void setMarshallers(Marshaller m, Unmarshaller u) {
        this.marshaller = m;
        this.unmarshaller = u;
    }
}
