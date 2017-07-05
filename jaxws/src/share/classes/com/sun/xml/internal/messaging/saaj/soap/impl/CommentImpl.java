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


package com.sun.xml.internal.messaging.saaj.soap.impl;

import java.util.ResourceBundle;
import java.util.logging.Logger;

import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPException;

import org.w3c.dom.DOMException;
import org.w3c.dom.Text;

import com.sun.xml.internal.messaging.saaj.soap.SOAPDocumentImpl;
import com.sun.xml.internal.messaging.saaj.util.LogDomainConstants;

public class CommentImpl
    extends com.sun.org.apache.xerces.internal.dom.CommentImpl
    implements javax.xml.soap.Text, org.w3c.dom.Comment {

    protected static final Logger log =
        Logger.getLogger(LogDomainConstants.SOAP_IMPL_DOMAIN,
                         "com.sun.xml.internal.messaging.saaj.soap.impl.LocalStrings");
    protected static ResourceBundle rb =
        log.getResourceBundle();

    public CommentImpl(SOAPDocumentImpl ownerDoc, String text) {
        super(ownerDoc, text);
    }

    public String getValue() {
        String nodeValue = getNodeValue();
        return (nodeValue.equals("") ? null : nodeValue);
    }

    public void setValue(String text) {
        setNodeValue(text);
    }


    public void setParentElement(SOAPElement element) throws SOAPException {
        if (element == null) {
            log.severe("SAAJ0112.impl.no.null.to.parent.elem");
            throw new SOAPException("Cannot pass NULL to setParentElement");
        }
        ((ElementImpl) element).addNode(this);
    }

    public SOAPElement getParentElement() {
        return (SOAPElement) getParentNode();
    }

    public void detachNode() {
        org.w3c.dom.Node parent = getParentNode();
        if (parent != null) {
            parent.removeChild(this);
        }
    }

    public void recycleNode() {
        detachNode();
        // TBD
        //  - add this to the factory so subsequent
        //    creations can reuse this object.
    }

    public boolean isComment() {
        return true;
    }

    public Text splitText(int offset) throws DOMException {
        log.severe("SAAJ0113.impl.cannot.split.text.from.comment");
        throw new UnsupportedOperationException("Cannot split text from a Comment Node.");
    }

    public Text replaceWholeText(String content) throws DOMException {
        log.severe("SAAJ0114.impl.cannot.replace.wholetext.from.comment");
        throw new UnsupportedOperationException("Cannot replace Whole Text from a Comment Node.");
    }

    public String getWholeText() {
        //TODO: maybe we have to implement this in future.
        throw new UnsupportedOperationException("Not Supported");
    }

    public boolean isElementContentWhitespace() {
        //TODO: maybe we have to implement this in future.
        throw new UnsupportedOperationException("Not Supported");
    }

}
