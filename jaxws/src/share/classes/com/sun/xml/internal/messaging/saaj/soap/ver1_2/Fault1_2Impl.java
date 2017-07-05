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
/*
 *
 */



/**
*
* @author SAAJ RI Development Team
*/
package com.sun.xml.internal.messaging.saaj.soap.ver1_2;

import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

import javax.xml.namespace.QName;
import javax.xml.soap.*;

import com.sun.xml.internal.messaging.saaj.SOAPExceptionImpl;
import com.sun.xml.internal.messaging.saaj.soap.SOAPDocument;
import com.sun.xml.internal.messaging.saaj.soap.SOAPDocumentImpl;
import com.sun.xml.internal.messaging.saaj.soap.impl.*;
import com.sun.xml.internal.messaging.saaj.soap.name.NameImpl;
import com.sun.xml.internal.messaging.saaj.util.LogDomainConstants;


public class Fault1_2Impl extends FaultImpl {

    protected static final Logger log =
        Logger.getLogger(
            LogDomainConstants.SOAP_VER1_2_DOMAIN,
            "com.sun.xml.internal.messaging.saaj.soap.ver1_2.LocalStrings");

    private static final QName textName =
        new QName(SOAPConstants.URI_NS_SOAP_1_2_ENVELOPE, "Text");
    private final QName valueName =
        new QName(SOAPConstants.URI_NS_SOAP_1_2_ENVELOPE, "Value", getPrefix());
    private final QName subcodeName =
        new QName(SOAPConstants.URI_NS_SOAP_1_2_ENVELOPE, "Subcode", getPrefix());

    private SOAPElement innermostSubCodeElement = null;

    public Fault1_2Impl(SOAPDocumentImpl ownerDoc, String name, String prefix) {
        super(ownerDoc, NameImpl.createFault1_2Name(name, prefix));
    }

    public Fault1_2Impl(SOAPDocumentImpl ownerDocument, String prefix) {
        super(ownerDocument, NameImpl.createFault1_2Name(null, prefix));
    }

    protected NameImpl getDetailName() {
        return NameImpl.createSOAP12Name("Detail", getPrefix());
    }

    protected NameImpl getFaultCodeName() {
        return NameImpl.createSOAP12Name("Code", getPrefix());
    }

    protected NameImpl getFaultStringName() {
        return getFaultReasonName();
    }

    protected NameImpl getFaultActorName() {
        return getFaultRoleName();
    }

    private  NameImpl getFaultRoleName() {
        return NameImpl.createSOAP12Name("Role", getPrefix());
    }

    private  NameImpl getFaultReasonName() {
        return NameImpl.createSOAP12Name("Reason", getPrefix());
    }

    private  NameImpl getFaultReasonTextName() {
        return NameImpl.createSOAP12Name("Text", getPrefix());
    }

    private  NameImpl getFaultNodeName() {
        return NameImpl.createSOAP12Name("Node", getPrefix());
    }

    private static NameImpl getXmlLangName() {
        return NameImpl.createXmlName("lang");
    }

    protected DetailImpl createDetail() {
        return new Detail1_2Impl(
                       ((SOAPDocument) getOwnerDocument()).getDocument());
    }

    protected FaultElementImpl createSOAPFaultElement(String localName) {
        return new FaultElement1_2Impl(
                       ((SOAPDocument) getOwnerDocument()).getDocument(),
                       localName);
    }

    protected void checkIfStandardFaultCode(String faultCode, String uri)
        throws SOAPException {
        QName qname = new QName(uri, faultCode);
        if (SOAPConstants.SOAP_DATAENCODINGUNKNOWN_FAULT.equals(qname) ||
            SOAPConstants.SOAP_MUSTUNDERSTAND_FAULT.equals(qname) ||
            SOAPConstants.SOAP_RECEIVER_FAULT.equals(qname) ||
            SOAPConstants.SOAP_SENDER_FAULT.equals(qname) ||
            SOAPConstants.SOAP_VERSIONMISMATCH_FAULT.equals(qname))
            return;
        log.log(
            Level.SEVERE,
            "SAAJ0435.ver1_2.code.not.standard",
            qname);
        throw new SOAPExceptionImpl(qname + " is not a standard Code value");
    }

    protected void finallySetFaultCode(String faultcode) throws SOAPException {
        SOAPElement value = this.faultCodeElement.addChildElement(valueName);
        value.addTextNode(faultcode);
    }

    private void findReasonElement() {
        findFaultStringElement();
    }

    public Iterator getFaultReasonTexts() throws SOAPException {
        // Fault Reason has similar semantics as faultstring
        if (this.faultStringElement == null)
            findReasonElement();
        Iterator eachTextElement =
            this.faultStringElement.getChildElements(textName);
        List texts = new ArrayList();
        while (eachTextElement.hasNext()) {
            SOAPElement textElement = (SOAPElement) eachTextElement.next();
            Locale thisLocale = getLocale(textElement);
            if (thisLocale == null) {
                log.severe("SAAJ0431.ver1_2.xml.lang.missing");
                throw new SOAPExceptionImpl("\"xml:lang\" attribute is not present on the Text element");
            }
            texts.add(textElement.getValue());
        }
        if (texts.isEmpty()) {
            log.severe("SAAJ0434.ver1_2.text.element.not.present");
            throw new SOAPExceptionImpl("env:Text must be present inside env:Reason");
        }
        return texts.iterator();
    }

    public void addFaultReasonText(String text, java.util.Locale locale)
        throws SOAPException {

        if (locale == null) {
            log.severe("SAAJ0430.ver1_2.locale.required");
            throw new SOAPException("locale is required and must not be null");
        }

        // Fault Reason has similar semantics as faultstring
        if (this.faultStringElement == null)
            findReasonElement();
        SOAPElement reasonText;

        if (this.faultStringElement == null) {
            this.faultStringElement = addSOAPFaultElement("Reason");
            reasonText =
                this.faultStringElement.addChildElement(
                    getFaultReasonTextName());
        } else {
            removeDefaultFaultString();
            reasonText = getFaultReasonTextElement(locale);
            if (reasonText != null) {
                reasonText.removeContents();
            } else {
                reasonText =
                    this.faultStringElement.addChildElement(
                        getFaultReasonTextName());
            }
        }

        String xmlLang = localeToXmlLang(locale);
        reasonText.addAttribute(getXmlLangName(), xmlLang);
        reasonText.addTextNode(text);
    }

    private void removeDefaultFaultString() throws SOAPException {
        SOAPElement reasonText = getFaultReasonTextElement(Locale.getDefault());
        if (reasonText != null) {
            String defaultFaultString =
                "Fault string, and possibly fault code, not set";
            if (defaultFaultString.equals(reasonText.getValue())) {
                reasonText.detachNode();
            }
        }
    }

    public String getFaultReasonText(Locale locale) throws SOAPException {

        if (locale == null)
            return null;

        // Fault Reason has similar semantics as faultstring
        if (this.faultStringElement == null)
            findReasonElement();

        if (this.faultStringElement != null) {
            SOAPElement textElement = getFaultReasonTextElement(locale);
            if (textElement != null) {
                textElement.normalize();
                return textElement.getFirstChild().getNodeValue();
            }
        }

        return null;
    }

    public Iterator getFaultReasonLocales() throws SOAPException {
        // Fault Reason has similar semantics as faultstring
        if (this.faultStringElement == null)
            findReasonElement();
        Iterator eachTextElement =
            this.faultStringElement.getChildElements(textName);
        List localeSet = new ArrayList();
        while (eachTextElement.hasNext()) {
            SOAPElement textElement = (SOAPElement) eachTextElement.next();
            Locale thisLocale = getLocale(textElement);
            if (thisLocale == null) {
                log.severe("SAAJ0431.ver1_2.xml.lang.missing");
                throw new SOAPExceptionImpl("\"xml:lang\" attribute is not present on the Text element");
            }
            localeSet.add(thisLocale);
        }
        if (localeSet.isEmpty()) {
            log.severe("SAAJ0434.ver1_2.text.element.not.present");
            throw new SOAPExceptionImpl("env:Text elements with mandatory xml:lang attributes must be present inside env:Reason");
        }
        return localeSet.iterator();
    }

    public Locale getFaultStringLocale() {
        Locale locale = null;
        try {
            locale = (Locale) getFaultReasonLocales().next();
        } catch (SOAPException e) {}
        return locale;
    }

    /*
     * This method assumes that locale and faultStringElement are non-null
     */
    private SOAPElement getFaultReasonTextElement(Locale locale)
        throws SOAPException {

        // Fault Reason has similar semantics as faultstring
        Iterator eachTextElement =
            this.faultStringElement.getChildElements(textName);
        while (eachTextElement.hasNext()) {
            SOAPElement textElement = (SOAPElement) eachTextElement.next();
            Locale thisLocale = getLocale(textElement);
            if (thisLocale == null) {
                log.severe("SAAJ0431.ver1_2.xml.lang.missing");
                throw new SOAPExceptionImpl("\"xml:lang\" attribute is not present on the Text element");
            }
            if (thisLocale.equals(locale)) {
                return textElement;
            }
        }
        return null;
    }

    public String getFaultNode() {
        SOAPElement faultNode = findChild(getFaultNodeName());
        if (faultNode == null) {
            return null;
        }
        return faultNode.getValue();
    }

    public void setFaultNode(String uri) throws SOAPException {
        SOAPElement faultNode = findChild(getFaultNodeName());
        if (faultNode != null) {
            faultNode.detachNode();
        }
        faultNode = createSOAPFaultElement(getFaultNodeName());
        faultNode = faultNode.addTextNode(uri);
        if (getFaultRole() != null) {
            insertBefore(faultNode, this.faultActorElement);
            return;
        }
        if (hasDetail()) {
            insertBefore(faultNode, this.detail);
            return;
        }
        addNode(faultNode);
    }

    public String getFaultRole() {
        return getFaultActor();
    }

    public void setFaultRole(String uri) throws SOAPException {
        if (this.faultActorElement == null)
            findFaultActorElement();
        if (this.faultActorElement != null)
            this.faultActorElement.detachNode();
        this.faultActorElement =
            createSOAPFaultElement(getFaultActorName());
        this.faultActorElement.addTextNode(uri);
        if (hasDetail()) {
            insertBefore(this.faultActorElement, this.detail);
            return;
        }
        addNode(this.faultActorElement);
    }

    public String getFaultCode() {
        if (this.faultCodeElement == null)
            findFaultCodeElement();
        Iterator codeValues =
            this.faultCodeElement.getChildElements(valueName);
        return ((SOAPElement) codeValues.next()).getValue();
    }

    public QName getFaultCodeAsQName() {
        String faultcode = getFaultCode();
        if (faultcode == null) {
            return null;
        }
        if (this.faultCodeElement == null)
            findFaultCodeElement();
        Iterator valueElements =
            this.faultCodeElement.getChildElements(valueName);
        return convertCodeToQName(
            faultcode,
            (SOAPElement) valueElements.next());
    }

    public Name getFaultCodeAsName() {
        String faultcode = getFaultCode();
        if (faultcode == null) {
            return null;
        }
        if (this.faultCodeElement == null)
            findFaultCodeElement();
        Iterator valueElements =
            this.faultCodeElement.getChildElements(valueName);
        return NameImpl.convertToName(
            convertCodeToQName(
                faultcode,
                (SOAPElement) valueElements.next()));
    }

    public String getFaultString() {
        String reason = null;
        try {
            //reason = getFaultReasonText(Locale.getDefault());
            //if (reason == null)
            reason = (String) getFaultReasonTexts().next();
        } catch (SOAPException e) {}
        return reason;
    }

    public void setFaultString(String faultString) throws SOAPException {
        addFaultReasonText(faultString, Locale.getDefault());
    }

    public void setFaultString(
        String faultString,
        Locale locale)
        throws SOAPException {
        addFaultReasonText(faultString, locale);
    }

    public void appendFaultSubcode(QName subcode) throws SOAPException {
        if (subcode == null) {
            return;
        }
        if (subcode.getNamespaceURI() == null ||
            "".equals(subcode.getNamespaceURI())) {

            log.severe("SAAJ0432.ver1_2.subcode.not.ns.qualified");
            throw new SOAPExceptionImpl("A Subcode must be namespace-qualified");
        }
        if (innermostSubCodeElement == null) {
            if (faultCodeElement == null)
                findFaultCodeElement();
            innermostSubCodeElement = faultCodeElement;
        }
        String prefix = null;
        if (subcode.getPrefix() == null || "".equals(subcode.getPrefix())) {
            prefix =
                ((ElementImpl) innermostSubCodeElement).getNamespacePrefix(
                    subcode.getNamespaceURI());
        } else
            prefix = subcode.getPrefix();
        if (prefix == null || "".equals(prefix)) {
            prefix = "ns1";
        }
        innermostSubCodeElement =
            innermostSubCodeElement.addChildElement(subcodeName);
        SOAPElement subcodeValueElement =
            innermostSubCodeElement.addChildElement(valueName);
        ((ElementImpl) subcodeValueElement).ensureNamespaceIsDeclared(
            prefix,
            subcode.getNamespaceURI());
        subcodeValueElement.addTextNode(prefix + ":" + subcode.getLocalPart());
    }

    public void removeAllFaultSubcodes() {
        if (this.faultCodeElement == null)
            findFaultCodeElement();
        Iterator subcodeElements =
            this.faultCodeElement.getChildElements(subcodeName);
        if (subcodeElements.hasNext()) {
            SOAPElement subcode = (SOAPElement) subcodeElements.next();
            subcode.detachNode();
        }
    }

    public Iterator getFaultSubcodes() {
        if (this.faultCodeElement == null)
            findFaultCodeElement();
        final List subcodeList = new ArrayList();
        SOAPElement currentCodeElement = this.faultCodeElement;
        Iterator subcodeElements =
            currentCodeElement.getChildElements(subcodeName);
        while (subcodeElements.hasNext()) {
            currentCodeElement = (ElementImpl) subcodeElements.next();
            Iterator valueElements =
                currentCodeElement.getChildElements(valueName);
            SOAPElement valueElement = (SOAPElement) valueElements.next();
            String code = valueElement.getValue();
            subcodeList.add(convertCodeToQName(code, valueElement));
            subcodeElements = currentCodeElement.getChildElements(subcodeName);
        }
        //return subcodeList.iterator();
        return new Iterator() {
            Iterator subCodeIter = subcodeList.iterator();

            public boolean hasNext() {
                return subCodeIter.hasNext();
            }

            public Object next() {
                return subCodeIter.next();
            }

            public void remove() {
                throw new UnsupportedOperationException(
                    "Method remove() not supported on SubCodes Iterator");
            }
        };
    }

    private static Locale getLocale(SOAPElement reasonText) {
        return xmlLangToLocale(reasonText.getAttributeValue(getXmlLangName()));
    }

    /*
     * Override setEncodingStyle of ElementImpl to restrict adding encodingStyle
     * attribute to SOAP Fault (SOAP 1.2 spec, part 1, section 5.1.1)
     */
    public void setEncodingStyle(String encodingStyle) throws SOAPException {
        log.severe("SAAJ0407.ver1_2.no.encodingStyle.in.fault");
        throw new SOAPExceptionImpl("encodingStyle attribute cannot appear on Fault");
    }

    public SOAPElement addAttribute(Name name, String value)
        throws SOAPException {
        if (name.getLocalName().equals("encodingStyle")
            && name.getURI().equals(NameImpl.SOAP12_NAMESPACE)) {
            setEncodingStyle(value);
        }
        return super.addAttribute(name, value);
    }

    public SOAPElement addAttribute(QName name, String value)
        throws SOAPException {
        if (name.getLocalPart().equals("encodingStyle")
            && name.getNamespaceURI().equals(NameImpl.SOAP12_NAMESPACE)) {
            setEncodingStyle(value);
        }
        return super.addAttribute(name, value);
    }

    public SOAPElement addTextNode(String text) throws SOAPException {
        log.log(
            Level.SEVERE,
            "SAAJ0416.ver1_2.adding.text.not.legal",
            getElementQName());
        throw new SOAPExceptionImpl("Adding text to SOAP 1.2 Fault is not legal");
    }

    public SOAPElement addChildElement(SOAPElement element)
        throws SOAPException {
        String localName = element.getLocalName();
        if ("Detail".equalsIgnoreCase(localName)) {
            if (hasDetail()) {
                log.severe("SAAJ0436.ver1_2.detail.exists.error");
                throw new SOAPExceptionImpl("Cannot add Detail, Detail already exists");
            }
            String uri = element.getElementQName().getNamespaceURI();
            if (!uri.equals(SOAPConstants.URI_NS_SOAP_1_2_ENVELOPE)) {
                log.severe("SAAJ0437.ver1_2.version.mismatch.error");
                throw new SOAPExceptionImpl("Cannot add Detail, Incorrect SOAP version specified for Detail element");
            }
        }
        if (element instanceof Detail1_2Impl) {
            ElementImpl importedElement = (ElementImpl) importElement(element);
            addNode(importedElement);
            return convertToSoapElement(importedElement);
        } else
            return super.addChildElement(element);
    }

    protected boolean isStandardFaultElement(String localName) {
        if (localName.equalsIgnoreCase("code") ||
            localName.equalsIgnoreCase("reason") ||
            localName.equalsIgnoreCase("node") ||
            localName.equalsIgnoreCase("role") ||
            localName.equalsIgnoreCase("detail")) {
            return true;
        }
        return false;
    }

    protected QName getDefaultFaultCode() {
        return SOAPConstants.SOAP_SENDER_FAULT;
    }

     protected FaultElementImpl createSOAPFaultElement(QName qname) {
         return new FaultElement1_2Impl(
                       ((SOAPDocument) getOwnerDocument()).getDocument(),
                       qname);
    }

    protected FaultElementImpl createSOAPFaultElement(Name qname) {
         return new FaultElement1_2Impl(
                       ((SOAPDocument) getOwnerDocument()).getDocument(),
                       (NameImpl)qname);
    }

}
