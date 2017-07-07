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

import java.util.Locale;
import java.util.logging.Level;

import javax.xml.namespace.QName;
import javax.xml.soap.*;

import com.sun.xml.internal.messaging.saaj.util.SAAJUtil;
import org.w3c.dom.Element;

import com.sun.xml.internal.messaging.saaj.SOAPExceptionImpl;
import com.sun.xml.internal.messaging.saaj.soap.SOAPDocumentImpl;
import com.sun.xml.internal.messaging.saaj.soap.name.NameImpl;

public abstract class FaultImpl extends ElementImpl implements SOAPFault {

    /* This can also represent a fault reason element */
    protected SOAPFaultElement faultStringElement;

    /* This can also represent a fault role element */
    protected SOAPFaultElement faultActorElement;

    protected SOAPFaultElement faultCodeElement;

    protected Detail detail;

    protected FaultImpl(SOAPDocumentImpl ownerDoc, NameImpl name) {
        super(ownerDoc, name);
    }

    public FaultImpl(SOAPDocumentImpl ownerDoc, Element domElement) {
        super(ownerDoc, domElement);
    }

    protected abstract NameImpl getDetailName();
    protected abstract NameImpl getFaultCodeName();
    protected abstract NameImpl getFaultStringName();
    protected abstract NameImpl getFaultActorName();
    protected abstract DetailImpl createDetail();
    protected abstract FaultElementImpl createSOAPFaultElement(String localName);
    protected abstract FaultElementImpl createSOAPFaultElement(QName qname);
    protected abstract FaultElementImpl createSOAPFaultElement(Name qname);
    protected abstract void checkIfStandardFaultCode(String faultCode, String uri) throws SOAPException;
    protected abstract void finallySetFaultCode(String faultcode) throws SOAPException;
    protected abstract boolean isStandardFaultElement(String localName);
    protected abstract QName getDefaultFaultCode();


    protected void findFaultCodeElement() {
        this.faultCodeElement =
            (SOAPFaultElement) findAndConvertChildElement(getFaultCodeName());
    }

    protected void findFaultActorElement() {
        this.faultActorElement =
            (SOAPFaultElement) findAndConvertChildElement(getFaultActorName());
    }

    protected void findFaultStringElement() {
        this.faultStringElement =
            (SOAPFaultElement) findAndConvertChildElement(getFaultStringName());
    }

    @Override
    public void setFaultCode(String faultCode) throws SOAPException {
        setFaultCode(
            NameImpl.getLocalNameFromTagName(faultCode),
            NameImpl.getPrefixFromTagName(faultCode),
            null);
    }

    public void setFaultCode(String faultCode, String prefix, String uri)
        throws SOAPException {

        if (prefix == null || "".equals(prefix)) {
            if (uri != null && !"".equals(uri)) {
                prefix = getNamespacePrefix(uri);
                if (prefix == null || "".equals(prefix)) {
                    prefix = "ns0";
                }
            }
        }
        if (this.faultCodeElement == null)
            findFaultCodeElement();

        if (this.faultCodeElement == null)
            this.faultCodeElement = addFaultCodeElement();
        else
            this.faultCodeElement.removeContents();

        if (uri == null || "".equals(uri)) {
            uri = this.faultCodeElement.getNamespaceURI(prefix);
        }
        if (uri == null || "".equals(uri)) {
            if (prefix != null && !"".equals(prefix)) {
                //cannot allow an empty URI for a non-Empty prefix
                log.log(Level.SEVERE, "SAAJ0140.impl.no.ns.URI", new Object[]{prefix + ":" + faultCode});
                throw new SOAPExceptionImpl("Empty/Null NamespaceURI specified for faultCode \"" + prefix + ":" + faultCode + "\"");
            } else {
                uri = "";
            }
        }
        checkIfStandardFaultCode(faultCode, uri);
        ((FaultElementImpl) this.faultCodeElement).ensureNamespaceIsDeclared(prefix, uri);

        if (prefix == null || "".equals(prefix)) {
            finallySetFaultCode(faultCode);
        } else {
            finallySetFaultCode(prefix + ":" + faultCode);
        }
    }

    @Override
    public void setFaultCode(Name faultCodeQName) throws SOAPException {
        setFaultCode(
            faultCodeQName.getLocalName(),
            faultCodeQName.getPrefix(),
            faultCodeQName.getURI());
    }

    @Override
    public void setFaultCode(QName faultCodeQName) throws SOAPException {
        setFaultCode(
            faultCodeQName.getLocalPart(),
            faultCodeQName.getPrefix(),
            faultCodeQName.getNamespaceURI());
    }

    protected static QName convertCodeToQName(
        String code,
        SOAPElement codeContainingElement) {

        int prefixIndex = code.indexOf(':');
        if (prefixIndex == -1) {
            return new QName(code);
        }

        String prefix = code.substring(0, prefixIndex);
        String nsName =((ElementImpl) codeContainingElement).lookupNamespaceURI(prefix);
            //((ElementImpl) codeContainingElement).getNamespaceURI(prefix);
        return new QName(nsName, getLocalPart(code), prefix);
    }

    protected void initializeDetail() {
        NameImpl detailName = getDetailName();
        detail = (Detail) findAndConvertChildElement(detailName);
    }

    @Override
    public Detail getDetail() {
        if (detail == null)
            initializeDetail();
        if ((detail != null) && (detail.getParentNode() == null)) {
        // a detach node was called on it
            detail = null;
        }
        return detail;
    }

    @Override
    public Detail addDetail() throws SOAPException {
        if (detail == null)
            initializeDetail();
        if (detail == null) {
            detail = createDetail();
            addNode(detail);
            return detail;
        } else {
            // Log
            throw new SOAPExceptionImpl("Error: Detail already exists");
        }
    }

    @Override
    public boolean hasDetail() {
        return (getDetail() != null);
    }

    @Override
    public abstract void setFaultActor(String faultActor) throws SOAPException;

    @Override
    public String getFaultActor() {
        if (this.faultActorElement == null)
            findFaultActorElement();
        if (this.faultActorElement != null) {
                return this.faultActorElement.getValue();
        }
        return null;
    }

    @Override
    public SOAPElement setElementQName(QName newName) throws SOAPException {

        log.log(
            Level.SEVERE,
            "SAAJ0146.impl.invalid.name.change.requested",
            new Object[] {elementQName.getLocalPart(), newName.getLocalPart()});
        throw new SOAPException(
            "Cannot change name for " + elementQName.getLocalPart() + " to " + newName.getLocalPart());
    }

    @Override
    protected SOAPElement convertToSoapElement(Element element) {
        final org.w3c.dom.Node soapNode = getSoapDocument().findIfPresent(element);
        if (soapNode instanceof SOAPFaultElement) {
            return (SOAPElement) soapNode;
        } else if (soapNode instanceof SOAPElement) {
            SOAPElement soapElement = (SOAPElement) soapNode;
            if (getDetailName().equals(soapElement.getElementName())) {
                return replaceElementWithSOAPElement(element, createDetail());
            } else {
                String localName =
                    soapElement.getElementName().getLocalName();
                if (isStandardFaultElement(localName))
                    return replaceElementWithSOAPElement(
                               element,
                               createSOAPFaultElement(soapElement.getElementQName()));
                return soapElement;
            }
        } else {
            Name elementName = NameImpl.copyElementName(element);
            ElementImpl newElement;
            if (getDetailName().equals(elementName)) {
                newElement = createDetail();
            } else {
                String localName = elementName.getLocalName();
                if (isStandardFaultElement(localName))
                    newElement =
                        createSOAPFaultElement(elementName);
                else
                    newElement = (ElementImpl) createElement(elementName);
            }
            return replaceElementWithSOAPElement(element, newElement);
        }
    }

    protected SOAPFaultElement addFaultCodeElement() throws SOAPException {
        if (this.faultCodeElement == null)
            findFaultCodeElement();
        if (this.faultCodeElement == null) {
            this.faultCodeElement =
                addSOAPFaultElement(getFaultCodeName().getLocalName());
            return this.faultCodeElement;
        } else {
            throw new SOAPExceptionImpl("Error: Faultcode already exists");
        }
    }

    private SOAPFaultElement addFaultStringElement() throws SOAPException {
        if (this.faultStringElement == null)
            findFaultStringElement();
        if (this.faultStringElement == null) {
            this.faultStringElement =
                addSOAPFaultElement(getFaultStringName().getLocalName());
            return this.faultStringElement;
        } else {
            // Log
            throw new SOAPExceptionImpl("Error: Faultstring already exists");
        }
    }

    private SOAPFaultElement addFaultActorElement() throws SOAPException {
        if (this.faultActorElement == null)
            findFaultActorElement();
        if (this.faultActorElement == null) {
            this.faultActorElement =
                addSOAPFaultElement(getFaultActorName().getLocalName());
            return this.faultActorElement;
        } else {
            // Log
            throw new SOAPExceptionImpl("Error: Faultactor already exists");
        }
    }

    @Override
    protected SOAPElement addElement(Name name) throws SOAPException {
        if (getDetailName().equals(name)) {
            return addDetail();
        } else if(getFaultCodeName().equals(name)) {
            return addFaultCodeElement();
        } else if(getFaultStringName().equals(name)) {
            return addFaultStringElement();
        } else if(getFaultActorName().equals(name)) {
            return addFaultActorElement();
        }
        return super.addElement(name);
    }

    @Override
    protected SOAPElement addElement(QName name) throws SOAPException {
        return addElement(NameImpl.convertToName(name));
    }

    protected FaultElementImpl addSOAPFaultElement(String localName)
        throws SOAPException {

        FaultElementImpl faultElem = createSOAPFaultElement(localName);
        addNode(faultElem);
        return faultElem;
    }

    /**
     * Convert an xml:lang attribute value into a Locale object
     * @param xmlLang xml:lang attribute value
     * @return Locale
     */
    protected static Locale xmlLangToLocale(String xmlLang) {
        if (xmlLang == null) {
            return null;
        }

        // Spec uses hyphen as separator
        int index = xmlLang.indexOf("-");

        // Accept underscore as separator as well
        if (index == -1) {
            index = xmlLang.indexOf("_");
        }

        if (index == -1) {
            // No separator so assume only a language component
            return new Locale(xmlLang, "");
        }

        String language = xmlLang.substring(0, index);
        String country = xmlLang.substring(index + 1);
        return new Locale(language, country);
    }

    protected static String localeToXmlLang(Locale locale) {
        String xmlLang = locale.getLanguage();
        String country = locale.getCountry();
        if (!"".equals(country)) {
            xmlLang += "-" + country;
        }
        return xmlLang;
    }
}
