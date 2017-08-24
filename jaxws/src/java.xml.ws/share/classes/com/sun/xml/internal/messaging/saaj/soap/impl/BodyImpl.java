/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.messaging.saaj.soap.impl;

import java.util.Iterator;
import java.util.Locale;
import java.util.logging.Level;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import com.sun.xml.internal.messaging.saaj.util.SAAJUtil;

import com.sun.xml.internal.messaging.saaj.SOAPExceptionImpl;
import com.sun.xml.internal.messaging.saaj.soap.SOAPDocument;
import com.sun.xml.internal.messaging.saaj.soap.SOAPDocumentImpl;
import com.sun.xml.internal.messaging.saaj.soap.StaxBridge;
import com.sun.xml.internal.messaging.saaj.soap.name.NameImpl;
import javax.xml.soap.Name;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPBodyElement;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPFault;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * The implementation of SOAP-ENV:BODY or the SOAPBody abstraction.
 *
 * @author Anil Vijendran (anil@sun.com)
 */
public abstract class BodyImpl extends ElementImpl implements SOAPBody {
    private SOAPFault fault;
//  private XMLStreamReaderToXMLStreamWriter staxBridge;
    private StaxBridge staxBridge;
    private boolean payloadStreamRead = false;

    protected BodyImpl(SOAPDocumentImpl ownerDoc, NameImpl bodyName) {
        super(ownerDoc, bodyName);
    }

    public BodyImpl(SOAPDocumentImpl ownerDoc, Element domElement) {
        super(ownerDoc, domElement);
    }

    protected abstract NameImpl getFaultName(String name);
    protected abstract boolean isFault(SOAPElement child);
    protected abstract SOAPBodyElement createBodyElement(Name name);
    protected abstract SOAPBodyElement createBodyElement(QName name);
    protected abstract SOAPFault createFaultElement();
    protected abstract QName getDefaultFaultCode();

    @Override
    public SOAPFault addFault() throws SOAPException {
        if (hasFault()) {
            log.severe("SAAJ0110.impl.fault.already.exists");
            throw new SOAPExceptionImpl("Error: Fault already exists");
        }

        fault = createFaultElement();

        addNode(fault);

        fault.setFaultCode(getDefaultFaultCode());
        fault.setFaultString("Fault string, and possibly fault code, not set");

        return fault;
    }

    @Override
    public SOAPFault addFault(
        Name faultCode,
        String faultString,
        Locale locale)
        throws SOAPException {

        SOAPFault fault = addFault();
        fault.setFaultCode(faultCode);
        fault.setFaultString(faultString, locale);
        return fault;
    }

    @Override
   public SOAPFault addFault(
        QName faultCode,
        String faultString,
        Locale locale)
        throws SOAPException {

        SOAPFault fault = addFault();
        fault.setFaultCode(faultCode);
        fault.setFaultString(faultString, locale);
        return fault;
    }

    @Override
    public SOAPFault addFault(Name faultCode, String faultString)
        throws SOAPException {

        SOAPFault fault = addFault();
        fault.setFaultCode(faultCode);
        fault.setFaultString(faultString);
        return fault;
    }

    @Override
    public SOAPFault addFault(QName faultCode, String faultString)
        throws SOAPException {

        SOAPFault fault = addFault();
        fault.setFaultCode(faultCode);
        fault.setFaultString(faultString);
        return fault;
    }

    void initializeFault() {
        FaultImpl flt = (FaultImpl) findFault();
        fault = flt;
    }

    protected SOAPElement findFault() {
        Iterator<Node> eachChild = getChildElementNodes();
        while (eachChild.hasNext()) {
            SOAPElement child = (SOAPElement) eachChild.next();
            if (isFault(child)) {
                return child;
            }
        }

        return null;
    }

    @Override
    public boolean hasFault() {
        QName payloadQName = getPayloadQName();
        return getFaultQName().equals(payloadQName);
    }

    private Object getFaultQName() {
        return new QName(getNamespaceURI(), "Fault");
    }

    @Override
    public SOAPFault getFault() {
        if (hasFault()) {
            if (fault == null) {
                //initialize fault member
                fault = (SOAPFault) getSoapDocument().find(getFirstChildElement());
            }
            return fault;
        }
        return null;
    }

    @Override
    public SOAPBodyElement addBodyElement(Name name) throws SOAPException {
        SOAPBodyElement newBodyElement =
            (SOAPBodyElement) ElementFactory.createNamedElement(
                ((SOAPDocument) getOwnerDocument()).getDocument(),
                name.getLocalName(),
                name.getPrefix(),
                name.getURI());
        if (newBodyElement == null) {
            newBodyElement = createBodyElement(name);
        }
        addNode(newBodyElement);
        return newBodyElement;
    }

    @Override
    public SOAPBodyElement addBodyElement(QName qname) throws SOAPException {
        SOAPBodyElement newBodyElement =
            (SOAPBodyElement) ElementFactory.createNamedElement(
                ((SOAPDocument) getOwnerDocument()).getDocument(),
                qname.getLocalPart(),
                qname.getPrefix(),
                qname.getNamespaceURI());
        if (newBodyElement == null) {
            newBodyElement = createBodyElement(qname);
        }
        addNode(newBodyElement);
        return newBodyElement;
    }

    @Override
    public void setParentElement(SOAPElement element) throws SOAPException {

        if (!(element instanceof SOAPEnvelope)) {
            log.severe("SAAJ0111.impl.body.parent.must.be.envelope");
            throw new SOAPException("Parent of SOAPBody has to be a SOAPEnvelope");
        }
        super.setParentElement(element);
    }

    @Override
    protected SOAPElement addElement(Name name) throws SOAPException {
        return addBodyElement(name);
    }

    @Override
    protected SOAPElement addElement(QName name) throws SOAPException {
        return addBodyElement(name);
    }

    //    public Node insertBefore(Node newElement, Node ref) throws DOMException {
    //        if (!(newElement instanceof SOAPBodyElement) && (newElement instanceof SOAPElement)) {
    //            newElement = new ElementWrapper((ElementImpl) newElement);
    //        }
    //        return super.insertBefore(newElement, ref);
    //    }
    //
    //    public Node replaceChild(Node newElement, Node ref) throws DOMException {
    //        if (!(newElement instanceof SOAPBodyElement) && (newElement instanceof SOAPElement)) {
    //            newElement = new ElementWrapper((ElementImpl) newElement);
    //        }
    //        return super.replaceChild(newElement, ref);
    //    }

    @Override
    public SOAPBodyElement addDocument(Document document)
        throws SOAPException {
        /*

                Element rootNode =
                    document.getDocumentElement();
                // Causes all deferred nodes to be inflated
                rootNode.normalize();
                adoptElement(rootNode);
                SOAPBodyElement bodyElement = (SOAPBodyElement) convertToSoapElement(rootNode);
                addNode(bodyElement);
                return bodyElement;
        */
        ///*
        SOAPBodyElement newBodyElement = null;
        DocumentFragment docFrag = document.createDocumentFragment();
        Element rootElement = document.getDocumentElement();
        if(rootElement != null) {
            docFrag.appendChild(rootElement);

            Document ownerDoc = getOwnerDocument();
            // This copies the whole tree which could be very big so it's slow.
            // However, it does have the advantage of actually working.
            org.w3c.dom.Node replacingNode = ownerDoc.importNode(docFrag, true);
            // Adding replacingNode at the last of the children list of body
            addNode(replacingNode);
            Iterator<javax.xml.soap.Node> i =
                getChildElements(NameImpl.copyElementName(rootElement));
            // Return the child element with the required name which is at the
            // end of the list
            while(i.hasNext())
                newBodyElement = (SOAPBodyElement) i.next();
        }
        return newBodyElement;
        //*/
    }

    @Override
    protected SOAPElement convertToSoapElement(Element element) {
        final Node soapNode = getSoapDocument().findIfPresent(element);
        if ((soapNode instanceof SOAPBodyElement) &&
            //this check is required because ElementImpl currently
            // implements SOAPBodyElement
            !(soapNode.getClass().equals(ElementImpl.class))) {
            return (SOAPElement) soapNode;
        } else {
            return replaceElementWithSOAPElement(
                element,
                (ElementImpl) createBodyElement(NameImpl
                    .copyElementName(element)));
        }
    }

    @Override
    public SOAPElement setElementQName(QName newName) throws SOAPException {
        log.log(Level.SEVERE,
                "SAAJ0146.impl.invalid.name.change.requested",
                new Object[] {elementQName.getLocalPart(),
                              newName.getLocalPart()});
        throw new SOAPException("Cannot change name for "
                                + elementQName.getLocalPart() + " to "
                                + newName.getLocalPart());
    }

    @Override
    public Document extractContentAsDocument() throws SOAPException {

        Iterator<javax.xml.soap.Node> eachChild = getChildElements();
        javax.xml.soap.Node firstBodyElement = null;

        while (eachChild.hasNext() &&
               !(firstBodyElement instanceof SOAPElement))
            firstBodyElement = (javax.xml.soap.Node) eachChild.next();

        boolean exactlyOneChildElement = true;
        if (firstBodyElement == null)
            exactlyOneChildElement = false;
        else {
            for (org.w3c.dom.Node node = firstBodyElement.getNextSibling();
                 node != null;
                 node = node.getNextSibling()) {

                if (node instanceof Element) {
                    exactlyOneChildElement = false;
                    break;
                }
            }
        }

        if(!exactlyOneChildElement) {
            log.log(Level.SEVERE,
                    "SAAJ0250.impl.body.should.have.exactly.one.child");
            throw new SOAPException("Cannot extract Document from body");
        }

        Document document = null;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance("com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl", SAAJUtil.getSystemClassLoader());
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            document = builder.newDocument();

            Element rootElement = (Element) document.importNode(
                                                firstBodyElement,
                                                true);

            document.appendChild(rootElement);

        } catch(Exception e) {
            log.log(Level.SEVERE,
                    "SAAJ0251.impl.cannot.extract.document.from.body");
            throw new SOAPExceptionImpl(
                "Unable to extract Document from body", e);
        }

        firstBodyElement.detachNode();

        return document;
    }

    private void materializePayloadWrapException() {
        try {
            materializePayload();
        } catch (SOAPException e) {
            throw new RuntimeException(e);
        }
    }
    private void materializePayload() throws SOAPException {
        if (staxBridge != null) {
            if (payloadStreamRead) {
                //the payload has already been read via stream reader and the
                //stream has been exhausted already. Throw an
                //exception since we are now trying to materialize as DOM and
                //there is no stream left to read
                throw new SOAPException("SOAPBody payload stream has been fully read - cannot materialize as DOM!");
            }
            try {
                staxBridge.bridgePayload();
                staxBridge = null;
                payloadStreamRead = true;
            } catch (XMLStreamException e) {
                throw new SOAPException(e);
            }
        }
    }

    @Override
    public boolean hasChildNodes() {
        boolean hasChildren = super.hasChildNodes();
        //to answer this question we need to know _whether_ we have at least one child
        //So no need to materialize body if we already know we have a header child
        if (!hasChildren) {
            materializePayloadWrapException();
        }
        return super.hasChildNodes();
    }

    @Override
    public NodeList getChildNodes() {
        materializePayloadWrapException();
        return super.getChildNodes();
    }

    @Override
    public Node getFirstChild() {
        Node child = super.getFirstChild();
        if (child == null) {
            materializePayloadWrapException();
        }
        return super.getFirstChild();
    }

    public Node getFirstChildNoMaterialize() {
        return super.getFirstChild();
    }

    @Override
    public Node getLastChild() {
        materializePayloadWrapException();
        return super.getLastChild();
    }

    XMLStreamReader getPayloadReader() {
        return staxBridge.getPayloadReader();
    }

    void setStaxBridge(StaxBridge bridge) {
        this.staxBridge = bridge;
    }

    StaxBridge getStaxBridge() {
        return staxBridge;
    }

    void setPayloadStreamRead() {
        this.payloadStreamRead = true;
    }

    QName getPayloadQName() {
        if (staxBridge != null) {
                return staxBridge.getPayloadQName();
        } else {
            //not lazy - Just get first child element and return its name
            Element elem = getFirstChildElement();
            if (elem != null) {
                String ns = elem.getNamespaceURI();
                String pref = elem.getPrefix();
                String local = elem.getLocalName();
                if (pref != null) return new QName(ns, local, pref);
                if (ns != null) return new QName(ns, local);
                return new QName(local);
            }
        }
        return null;
    }

    String getPayloadAttributeValue(String attName) {
        if (staxBridge != null) {
            return staxBridge.getPayloadAttributeValue(attName);
        } else {
            //not lazy -Just get first child element and return its attribute
            Element elem = getFirstChildElement();
            if (elem != null) {
                return elem.getAttribute(getLocalName());
            }
        }
        return null;
    }

    String getPayloadAttributeValue(QName attNAme) {
        if (staxBridge != null) {
            return staxBridge.getPayloadAttributeValue(attNAme);
        } else {
            //not lazy -Just get first child element and return its attribute
            Element elem = getFirstChildElement();
            if (elem != null) {
                return elem.getAttributeNS(attNAme.getNamespaceURI(), attNAme.getLocalPart());
            }
        }
        return null;
    }

    public boolean isLazy() {
        return (staxBridge != null && !payloadStreamRead);
    }

}
