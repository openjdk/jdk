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



/**
*
* @author SAAJ RI Development Team
*/
package com.sun.xml.internal.messaging.saaj.soap.ver1_1;

import java.util.Iterator;
import java.util.Locale;
import java.util.logging.Logger;
import java.util.logging.Level;

import javax.xml.namespace.QName;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPFaultElement;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPConstants;
import javax.xml.soap.Name;
import javax.xml.soap.Name;

import com.sun.xml.internal.messaging.saaj.soap.SOAPDocument;
import com.sun.xml.internal.messaging.saaj.soap.SOAPDocumentImpl;
import com.sun.xml.internal.messaging.saaj.soap.impl.*;
import com.sun.xml.internal.messaging.saaj.soap.name.NameImpl;
import com.sun.xml.internal.messaging.saaj.util.LogDomainConstants;
import com.sun.xml.internal.messaging.saaj.SOAPExceptionImpl;


public class Fault1_1Impl extends FaultImpl {

    protected static final Logger log =
        Logger.getLogger(
            LogDomainConstants.SOAP_VER1_1_DOMAIN,
            "com.sun.xml.internal.messaging.saaj.soap.ver1_1.LocalStrings");

    public Fault1_1Impl(SOAPDocumentImpl ownerDocument, String prefix) {
       super(ownerDocument, NameImpl.createFault1_1Name(prefix));
    }

    protected NameImpl getDetailName() {
        return NameImpl.createDetail1_1Name();
    }

    protected NameImpl getFaultCodeName() {
        return NameImpl.createFromUnqualifiedName("faultcode");
    }

    protected NameImpl getFaultStringName() {
        return NameImpl.createFromUnqualifiedName("faultstring");
    }

    protected NameImpl getFaultActorName() {
        return NameImpl.createFromUnqualifiedName("faultactor");
    }

    protected DetailImpl createDetail() {
        return new Detail1_1Impl(
                       ((SOAPDocument) getOwnerDocument()).getDocument());
    }

    protected FaultElementImpl createSOAPFaultElement(String localName) {
        return new FaultElement1_1Impl(
                       ((SOAPDocument) getOwnerDocument()).getDocument(),
                       localName);
    }

    protected void checkIfStandardFaultCode(String faultCode, String uri)
        throws SOAPException {
        // SOAP 1.1 doesn't seem to mandate using faultcode from a particular
        // set of values.
        // Also need to be backward compatible.
    }

    protected void finallySetFaultCode(String faultcode) throws SOAPException {
        this.faultCodeElement.addTextNode(faultcode);
    }

    public String getFaultCode() {
        if (this.faultCodeElement == null)
            findFaultCodeElement();
        return this.faultCodeElement.getValue();
    }

    public Name getFaultCodeAsName() {

        String faultcodeString = getFaultCode();
        if (faultcodeString == null) {
            return null;
        }
        int prefixIndex = faultcodeString.indexOf(':');
        if (prefixIndex == -1) {
            // Not a valid SOAP message, but we return the unqualified name
            // anyway since some apps do not strictly conform to SOAP
            // specs.  A message that does not contain a <faultcode>
            // element itself is also not valid in which case we return
            // null instead of throwing an exception so this is consistent.
            return NameImpl.createFromUnqualifiedName(faultcodeString);
        }

        // Get the prefix and map it to a namespace name (AKA namespace URI)
        String prefix = faultcodeString.substring(0, prefixIndex);
        if (this.faultCodeElement == null)
            findFaultCodeElement();
        String nsName = this.faultCodeElement.getNamespaceURI(prefix);
        return NameImpl.createFromQualifiedName(faultcodeString, nsName);
    }

    public QName getFaultCodeAsQName() {
        String faultcodeString = getFaultCode();
        if (faultcodeString == null) {
            return null;
        }
        if (this.faultCodeElement == null)
            findFaultCodeElement();
        return convertCodeToQName(faultcodeString, this.faultCodeElement);
    }

    public void setFaultString(String faultString) throws SOAPException {

        if (this.faultStringElement == null)
            findFaultStringElement();

        if (this.faultStringElement == null)
            this.faultStringElement = addSOAPFaultElement("faultstring");
        else {
            this.faultStringElement.removeContents();
            //this.faultStringElement.removeAttributeNS("http://www.w3.org/XML/1998/namespace", "lang");
            this.faultStringElement.removeAttribute("xml:lang");
        }

        this.faultStringElement.addTextNode(faultString);
    }

    public String getFaultString() {
        if (this.faultStringElement == null)
            findFaultStringElement();
        return this.faultStringElement.getValue();

    }

    public Locale getFaultStringLocale() {
        if (this.faultStringElement == null)
            findFaultStringElement();
        if (this.faultStringElement != null) {
            String xmlLangAttr =
                this.faultStringElement.getAttributeValue(
                    NameImpl.createFromUnqualifiedName("xml:lang"));
            if (xmlLangAttr != null)
                return xmlLangToLocale(xmlLangAttr);
        }
        return null;
    }

    public void setFaultString(String faultString, Locale locale)
        throws SOAPException {
        setFaultString(faultString);
        this.faultStringElement.addAttribute(
            NameImpl.createFromTagName("xml:lang"),
            localeToXmlLang(locale));
    }

    protected boolean isStandardFaultElement(String localName) {
        if (localName.equalsIgnoreCase("detail") ||
            localName.equalsIgnoreCase("faultcode") ||
            localName.equalsIgnoreCase("faultstring") ||
            localName.equalsIgnoreCase("faultactor")) {
            return true;
        }
        return false;
    }

    public void appendFaultSubcode(QName subcode) {
        log.log(
            Level.SEVERE,
            "SAAJ0303.ver1_1.msg.op.unsupported.in.SOAP1.1",
            "appendFaultSubcode");
        throw new UnsupportedOperationException("Not supported in SOAP 1.1");
    }

    public void removeAllFaultSubcodes() {
        log.log(
            Level.SEVERE,
            "SAAJ0303.ver1_1.msg.op.unsupported.in.SOAP1.1",
            "removeAllFaultSubcodes");
        throw new UnsupportedOperationException("Not supported in SOAP 1.1");
    }

    public Iterator getFaultSubcodes() {
        log.log(
            Level.SEVERE,
            "SAAJ0303.ver1_1.msg.op.unsupported.in.SOAP1.1",
            "getFaultSubcodes");
        throw new UnsupportedOperationException("Not supported in SOAP 1.1");
    }

    public String getFaultReasonText(Locale locale) {
        log.log(
            Level.SEVERE,
            "SAAJ0303.ver1_1.msg.op.unsupported.in.SOAP1.1",
            "getFaultReasonText");
        throw new UnsupportedOperationException("Not supported in SOAP 1.1");
    }

    public Iterator getFaultReasonTexts() {
        log.log(
            Level.SEVERE,
            "SAAJ0303.ver1_1.msg.op.unsupported.in.SOAP1.1",
            "getFaultReasonTexts");
        throw new UnsupportedOperationException("Not supported in SOAP 1.1");
    }

    public Iterator getFaultReasonLocales() {
        log.log(
            Level.SEVERE,
            "SAAJ0303.ver1_1.msg.op.unsupported.in.SOAP1.1",
            "getFaultReasonLocales");
        throw new UnsupportedOperationException("Not supported in SOAP 1.1");
    }

    public void addFaultReasonText(String text, java.util.Locale locale)
        throws SOAPException {
        log.log(
            Level.SEVERE,
            "SAAJ0303.ver1_1.msg.op.unsupported.in.SOAP1.1",
            "addFaultReasonText");
        throw new UnsupportedOperationException("Not supported in SOAP 1.1");
    }

    public String getFaultRole() {
        log.log(
            Level.SEVERE,
            "SAAJ0303.ver1_1.msg.op.unsupported.in.SOAP1.1",
            "getFaultRole");
        throw new UnsupportedOperationException("Not supported in SOAP 1.1");
    }

    public void setFaultRole(String uri) {
        log.log(
            Level.SEVERE,
            "SAAJ0303.ver1_1.msg.op.unsupported.in.SOAP1.1",
            "setFaultRole");
        throw new UnsupportedOperationException("Not supported in SOAP 1.1");
    }

    public String getFaultNode() {
        log.log(
            Level.SEVERE,
            "SAAJ0303.ver1_1.msg.op.unsupported.in.SOAP1.1",
            "getFaultNode");
        throw new UnsupportedOperationException("Not supported in SOAP 1.1");
    }

    public void setFaultNode(String uri) {
        log.log(
            Level.SEVERE,
            "SAAJ0303.ver1_1.msg.op.unsupported.in.SOAP1.1",
            "setFaultNode");
        throw new UnsupportedOperationException("Not supported in SOAP 1.1");
    }

    protected QName getDefaultFaultCode() {
        return new QName(SOAPConstants.URI_NS_SOAP_1_1_ENVELOPE, "Server");
    }

    public SOAPElement addChildElement(SOAPElement element)
        throws SOAPException {
        String localName = element.getLocalName();
        if ("Detail".equalsIgnoreCase(localName)) {
            if (hasDetail()) {
                log.severe("SAAJ0305.ver1_2.detail.exists.error");
                throw new SOAPExceptionImpl("Cannot add Detail, Detail already exists");
            }
        }
        return super.addChildElement(element);
    }

    protected FaultElementImpl createSOAPFaultElement(QName qname) {
         return new FaultElement1_1Impl(
                       ((SOAPDocument) getOwnerDocument()).getDocument(),
                       qname);
    }

    protected FaultElementImpl createSOAPFaultElement(Name qname) {
         return new FaultElement1_1Impl(
                       ((SOAPDocument) getOwnerDocument()).getDocument(),
                       (NameImpl)qname);
    }

    public void setFaultCode(String faultCode, String prefix, String uri)
        throws SOAPException {
        if (prefix == null || prefix.equals("")) {
            if (uri != null && !"".equals(uri)) {
                prefix = getNamespacePrefix(uri);
                if (prefix == null || prefix.equals("")) {
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

        if (uri == null || uri.equals("")) {
            if (prefix != null && !"".equals("prefix")) {
                uri = this.faultCodeElement.getNamespaceURI(prefix);
            }
        }

//        if (standardFaultCode(faultCode) &&
//                ((uri == null) || uri.equals(""))) {
//             log.log(Level.WARNING, "SAAJ0306.ver1_1.faultcode.incorrect.namespace", new Object[]{faultCode});
//               // throw new SOAPExceptionImpl("Namespace Error, Standard Faultcode: " +  faultCode + ", should be in SOAP 1.1 Namespace");
//        }

        if (uri == null) {
            //SOAP 1.1 Allows this
            if (prefix != null && !"".equals(prefix)) {
                log.severe("SAAJ0140.impl.no.ns.URI");
                throw new SOAPExceptionImpl("No NamespaceURI, SOAP requires faultcode content to be a QName");
            }
        } else {
            checkIfStandardFaultCode(faultCode, uri);
            ((FaultElementImpl) this.faultCodeElement).ensureNamespaceIsDeclared(prefix, uri);
        }

        if (prefix == null || "".equals(prefix)) {
            finallySetFaultCode(faultCode);
        } else {
            finallySetFaultCode(prefix + ":" + faultCode);
        }
    }

    private boolean standardFaultCode(String faultCode) {
        if (faultCode.equals("VersionMismatch") || faultCode.equals("MustUnderstand")
            || faultCode.equals("Client") || faultCode.equals("Server")) {
            return true;
        }
        if (faultCode.startsWith("VersionMismatch.") || faultCode.startsWith("MustUnderstand.")
            || faultCode.startsWith("Client.") || faultCode.startsWith("Server.")) {
            return true;
        }
        return false;
    }
}
