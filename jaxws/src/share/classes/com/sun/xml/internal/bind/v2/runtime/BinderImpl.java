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
package com.sun.xml.internal.bind.v2.runtime;

import javax.xml.bind.Binder;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.PropertyException;
import javax.xml.bind.ValidationEventHandler;
import javax.xml.validation.Schema;
import javax.xml.namespace.QName;

import com.sun.xml.internal.bind.unmarshaller.InfosetScanner;
import com.sun.xml.internal.bind.v2.runtime.output.DOMOutput;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.InterningXmlVisitor;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.SAXConnector;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.UnmarshallerImpl;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * Implementation of {@link Binder}.
 *
 * TODO: investigate how much in-place unmarshalling is implemented
 *      - some preliminary work is there. Probably buggy.
 * TODO: work on the marshaller side.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public class BinderImpl<XmlNode> extends Binder<XmlNode> {

    /**
     * The parent context object.
     */
    private final JAXBContextImpl context;

    /**
     * Lazily created unmarshaller to do XML->Java binding.
     * @see #getUnmarshaller()
     */
    private UnmarshallerImpl unmarshaller;

    /**
     * Lazily create marshaller to do Java->XML binding.
     * @see #getMarshaller()
     */
    private MarshallerImpl marshaller;

    private final InfosetScanner<XmlNode> scanner;

    /**
     * A {@link Binder} always works with the same
     * association map.
     */
    private final AssociationMap<XmlNode> assoc = new AssociationMap<XmlNode>();

    BinderImpl(JAXBContextImpl _context,InfosetScanner<XmlNode> scanner) {
        this.context = _context;
        this.scanner = scanner;
    }

    private UnmarshallerImpl getUnmarshaller() {
        if(unmarshaller==null)
            unmarshaller = new UnmarshallerImpl(context,assoc);
        return unmarshaller;
    }

    private MarshallerImpl getMarshaller() {
        if(marshaller==null)
            marshaller = new MarshallerImpl(context,assoc);
        return marshaller;
    }

    public void marshal(Object jaxbObject, XmlNode xmlNode) throws JAXBException {
        if ((xmlNode == null) || (jaxbObject == null))
            throw new IllegalArgumentException();
        getMarshaller().marshal(jaxbObject,createOutput(xmlNode));
    }

    // TODO move this to a sub class once we support something other than W3C DOM
    private DOMOutput createOutput(XmlNode xmlNode) {
        return new DOMOutput((Node)xmlNode,assoc);
    }


    public Object updateJAXB(XmlNode xmlNode) throws JAXBException {
        return associativeUnmarshal(xmlNode,true,null);
    }

    public Object unmarshal( XmlNode xmlNode ) throws JAXBException {
        return associativeUnmarshal(xmlNode,false,null);
    }

    public <T> JAXBElement<T> unmarshal(XmlNode xmlNode, Class<T> expectedType) throws JAXBException {
        if(expectedType==null)  throw new IllegalArgumentException();
        return (JAXBElement)associativeUnmarshal(xmlNode,true,expectedType);
    }

    public void setSchema(Schema schema) {
        getMarshaller().setSchema(schema);
        getUnmarshaller().setSchema(schema);
    }

    public Schema getSchema() {
        return getUnmarshaller().getSchema();
    }

    private Object associativeUnmarshal(XmlNode xmlNode, boolean inplace, Class expectedType) throws JAXBException {
        if (xmlNode == null)
            throw new IllegalArgumentException();

        JaxBeanInfo bi = null;
        if(expectedType!=null)
            bi = context.getBeanInfo(expectedType, true);

        InterningXmlVisitor handler = new InterningXmlVisitor(
            getUnmarshaller().createUnmarshallerHandler(scanner,inplace,bi));
        scanner.setContentHandler(new SAXConnector(handler,scanner.getLocator()));
        try {
            scanner.scan(xmlNode);
        } catch( SAXException e ) {
            throw unmarshaller.createUnmarshalException(e);
        }

        return handler.getContext().getResult();
    }

    public XmlNode getXMLNode(Object jaxbObject) {
        AssociationMap.Entry<XmlNode> e = assoc.byPeer(jaxbObject);
        if(e==null)     return null;
        return e.element();
    }

    public Object getJAXBNode(XmlNode xmlNode) {
        if(xmlNode==null)
            throw new IllegalArgumentException();
        AssociationMap.Entry e = assoc.byElement(xmlNode);
        if(e==null)     return null;
        if(e.outer()!=null)     return e.outer();
        return e.inner();
    }

    public XmlNode updateXML(Object jaxbObject) throws JAXBException {
        return updateXML(jaxbObject,getXMLNode(jaxbObject));
    }

    public XmlNode updateXML(Object jaxbObject, XmlNode xmlNode) throws JAXBException {
        if(jaxbObject==null || xmlNode==null)   throw new IllegalArgumentException();

        // TODO
        // for now just marshal
        // TODO: object model independenc
        Element e = (Element)xmlNode;
        Node ns = e.getNextSibling();
        Node p = e.getParentNode();
        p.removeChild(e);

        // if the type object is passed, the following step is necessary to make
        // the marshalling successful.
        JaxBeanInfo bi = context.getBeanInfo(jaxbObject, true);
        if(!bi.isElement())
            jaxbObject = new JAXBElement(new QName(e.getNamespaceURI(),e.getLocalName()),bi.jaxbType,jaxbObject);


        getMarshaller().marshal(jaxbObject,p);
        Node newNode = p.getLastChild();
        p.removeChild(newNode);
        p.insertBefore(newNode,ns);

        return (XmlNode)newNode;
    }

    public void setEventHandler(ValidationEventHandler handler) throws JAXBException {
        getUnmarshaller().setEventHandler(handler);
        getMarshaller().setEventHandler(handler);
    }

    public ValidationEventHandler getEventHandler() {
        return getUnmarshaller().getEventHandler();
    }

    public Object getProperty(String name) throws PropertyException {
        if (name == null)
            throw new IllegalArgumentException(Messages.NULL_PROPERTY_NAME.format());

        // exclude RI properties that don't make sense for Binder
        if (excludeProperty(name)) {
            throw new PropertyException(name);
        }

        Object prop = null;
        PropertyException pe = null;

        try {
            prop = getMarshaller().getProperty(name);
            return prop;
        } catch (PropertyException p) {
            pe = p;
        }

        try {
            prop = getUnmarshaller().getProperty(name);
            return prop;
        } catch (PropertyException p) {
            pe = p;
        }

        pe.setStackTrace(Thread.currentThread().getStackTrace());
        throw pe;
    }

    public void setProperty(String name, Object value) throws PropertyException {
        if (name == null)
            throw new IllegalArgumentException(Messages.NULL_PROPERTY_NAME.format());

        // exclude RI properties that don't make sense for Binder
        if (excludeProperty(name)) {
            throw new PropertyException(name, value);
        }

        PropertyException pe = null;

        try {
            getMarshaller().setProperty(name, value);
            return;
        } catch (PropertyException p) {
            pe = p;
        }

        try {
            getUnmarshaller().setProperty(name, value);
            return;
        } catch (PropertyException p) {
            pe = p;
        }

        // replace the stacktrace - we don't want to see a trace
        // originating from Un|Marshaller.setProperty
        pe.setStackTrace(Thread.currentThread().getStackTrace());
        throw pe;
    }

    private boolean excludeProperty(String name) {
        return name.equals(
                MarshallerImpl.ENCODING_HANDLER) ||
                        name.equals(MarshallerImpl.XMLDECLARATION) ||
                        name.equals(MarshallerImpl.XML_HEADERS);
    }
}
