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

import javax.xml.namespace.QName;
import javax.xml.soap.*;

import com.sun.xml.internal.messaging.saaj.soap.SOAPDocumentImpl;
import com.sun.xml.internal.messaging.saaj.soap.name.NameImpl;
import org.w3c.dom.Element;

public abstract class HeaderElementImpl
    extends ElementImpl
    implements SOAPHeaderElement {

    protected static Name RELAY_ATTRIBUTE_LOCAL_NAME =
        NameImpl.createFromTagName("relay");
    protected static Name MUST_UNDERSTAND_ATTRIBUTE_LOCAL_NAME =
        NameImpl.createFromTagName("mustUnderstand");

    public HeaderElementImpl(SOAPDocumentImpl ownerDoc, Name qname) {
        super(ownerDoc, qname);
    }
    public HeaderElementImpl(SOAPDocumentImpl ownerDoc, QName qname) {
        super(ownerDoc, qname);
    }

    public HeaderElementImpl(SOAPDocumentImpl ownerDoc, Element domElement) {
        super(ownerDoc, domElement);
    }

    protected abstract NameImpl getActorAttributeName();
    protected abstract NameImpl getRoleAttributeName();
    protected abstract NameImpl getMustunderstandAttributeName();
    protected abstract boolean getMustunderstandAttributeValue(String str);
    protected abstract String getMustunderstandLiteralValue(boolean mu);
    protected abstract NameImpl getRelayAttributeName();
    protected abstract boolean getRelayAttributeValue(String str);
    protected abstract String getRelayLiteralValue(boolean mu);
    protected abstract String getActorOrRole();


    @Override
    public void setParentElement(SOAPElement element) throws SOAPException {
        if (!(element instanceof SOAPHeader)) {
            log.severe("SAAJ0130.impl.header.elem.parent.mustbe.header");
            throw new SOAPException("Parent of a SOAPHeaderElement has to be a SOAPHeader");
        }

        super.setParentElement(element);
    }

    @Override
    public void setActor(String actorUri) {
        try {
            removeAttribute(getActorAttributeName());
            addAttribute((Name) getActorAttributeName(), actorUri);
        } catch (SOAPException ex) {
        }
    }

    //SOAP 1.2 supports Role
    @Override
    public void setRole(String roleUri) throws SOAPException {
        // runtime exception thrown if called for SOAP 1.1
        removeAttribute(getRoleAttributeName());
        addAttribute((Name) getRoleAttributeName(), roleUri);
    }


    Name actorAttNameWithoutNS = NameImpl.createFromTagName("actor");

    @Override
    public String getActor() {
        String actor = getAttributeValue(getActorAttributeName());
        return actor;
    }

    Name roleAttNameWithoutNS = NameImpl.createFromTagName("role");

    @Override
    public String getRole() {
        // runtime exception thrown for 1.1
        String role = getAttributeValue(getRoleAttributeName());
        return role;
    }

    @Override
    public void setMustUnderstand(boolean mustUnderstand) {
        try {
            removeAttribute(getMustunderstandAttributeName());
            addAttribute(
                (Name) getMustunderstandAttributeName(),
                getMustunderstandLiteralValue(mustUnderstand));
        } catch (SOAPException ex) {
        }
    }

    @Override
    public boolean getMustUnderstand() {
        String mu = getAttributeValue(getMustunderstandAttributeName());

        if (mu != null)
            return getMustunderstandAttributeValue(mu);

        return false;
    }

    @Override
    public void setRelay(boolean relay) throws SOAPException {
        // runtime exception thrown for 1.1
        removeAttribute(getRelayAttributeName());
        addAttribute(
            (Name) getRelayAttributeName(),
            getRelayLiteralValue(relay));
    }

    @Override
    public boolean getRelay() {
        String mu = getAttributeValue(getRelayAttributeName());
        if (mu != null)
            return getRelayAttributeValue(mu);

        return false;
    }
}
