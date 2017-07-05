/*
 * Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
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
 * $Id: DOMPGPData.java,v 1.18 2005/05/12 19:28:31 mullan Exp $
 */
package org.jcp.xml.dsig.internal.dom;

import java.util.*;
import javax.xml.crypto.*;
import javax.xml.crypto.dom.DOMCryptoContext;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.keyinfo.PGPData;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.sun.org.apache.xml.internal.security.exceptions.Base64DecodingException;
import com.sun.org.apache.xml.internal.security.utils.Base64;

/**
 * DOM-based implementation of PGPData.
 *
 * @author Sean Mullan
 */
public final class DOMPGPData extends DOMStructure implements PGPData {

    private final byte[] keyId;
    private final byte[] keyPacket;
    private final List externalElements;

    /**
     * Creates a <code>DOMPGPData</code> containing the specified key packet.
     * and optional list of external elements.
     *
     * @param keyPacket a PGP Key Material Packet as defined in section 5.5 of
     *    <a href="http://www.ietf.org/rfc/rfc2440.txt"/>RFC 2440</a>. The
     *    array is cloned to prevent subsequent modification.
     * @param other a list of {@link XMLStructure}s representing elements from
     *    an external namespace. The list is defensively copied to prevent
     *    subsequent modification. May be <code>null</code> or empty.
     * @throws NullPointerException if <code>keyPacket</code> is
     *    <code>null</code>
     * @throws IllegalArgumentException if the key packet is not in the
     *    correct format
     * @throws ClassCastException if <code>other</code> contains any
     *    entries that are not of type {@link XMLStructure}
     */
    public DOMPGPData(byte[] keyPacket, List other) {
        if (keyPacket == null) {
            throw new NullPointerException("keyPacket cannot be null");
        }
        if (other == null || other.isEmpty()) {
            this.externalElements = Collections.EMPTY_LIST;
        } else {
            List otherCopy = new ArrayList(other);
            for (int i = 0, size = otherCopy.size(); i < size; i++) {
                if (!(otherCopy.get(i) instanceof XMLStructure)) {
                    throw new ClassCastException
                        ("other["+i+"] is not a valid PGPData type");
                }
            }
            this.externalElements = Collections.unmodifiableList(otherCopy);
        }
        this.keyPacket = (byte []) keyPacket.clone();
        checkKeyPacket(keyPacket);
        this.keyId = null;
    }

    /**
     * Creates a <code>DOMPGPData</code> containing the specified key id and
     * optional key packet and list of external elements.
     *
     * @param keyId a PGP public key id as defined in section 11.2 of
     *    <a href="http://www.ietf.org/rfc/rfc2440.txt"/>RFC 2440</a>. The
     *    array is cloned to prevent subsequent modification.
     * @param keyPacket a PGP Key Material Packet as defined in section 5.5 of
     *    <a href="http://www.ietf.org/rfc/rfc2440.txt"/>RFC 2440</a> (may
     *    be <code>null</code>). The array is cloned to prevent subsequent
     *    modification.
     * @param other a list of {@link XMLStructure}s representing elements from
     *    an external namespace. The list is defensively copied to prevent
     *    subsequent modification. May be <code>null</code> or empty.
     * @throws NullPointerException if <code>keyId</code> is <code>null</code>
     * @throws IllegalArgumentException if the key id or packet is not in the
     *    correct format
     * @throws ClassCastException if <code>other</code> contains any
     *    entries that are not of type {@link XMLStructure}
     */
    public DOMPGPData(byte[] keyId, byte[] keyPacket, List other) {
        if (keyId == null) {
            throw new NullPointerException("keyId cannot be null");
        }
        // key ids must be 8 bytes
        if (keyId.length != 8) {
            throw new IllegalArgumentException("keyId must be 8 bytes long");
        }
        if (other == null || other.isEmpty()) {
            this.externalElements = Collections.EMPTY_LIST;
        } else {
            List otherCopy = new ArrayList(other);
            for (int i = 0, size = otherCopy.size(); i < size; i++) {
                if (!(otherCopy.get(i) instanceof XMLStructure)) {
                    throw new ClassCastException
                        ("other["+i+"] is not a valid PGPData type");
                }
            }
            this.externalElements = Collections.unmodifiableList(otherCopy);
        }
        this.keyId = (byte []) keyId.clone();
        this.keyPacket = keyPacket == null ? null : (byte []) keyPacket.clone();
        if (keyPacket != null) {
            checkKeyPacket(keyPacket);
        }
    }

    /**
     * Creates a <code>DOMPGPData</code> from an element.
     *
     * @param pdElem a PGPData element
     */
    public DOMPGPData(Element pdElem) throws MarshalException {
        // get all children nodes
        byte[] keyId = null;
        byte[] keyPacket = null;
        NodeList nl = pdElem.getChildNodes();
        int length = nl.getLength();
        List other = new ArrayList(length);
        for (int x = 0; x < length; x++) {
            Node n = nl.item(x);
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                Element childElem = (Element) n;
                String localName = childElem.getLocalName();
                try {
                    if (localName.equals("PGPKeyID")) {
                        keyId = Base64.decode(childElem);
                    } else if (localName.equals("PGPKeyPacket")){
                        keyPacket = Base64.decode(childElem);
                    } else {
                        other.add
                            (new javax.xml.crypto.dom.DOMStructure(childElem));
                    }
                } catch (Base64DecodingException bde) {
                    throw new MarshalException(bde);
                }
            }
        }
        this.keyId = keyId;
        this.keyPacket = keyPacket;
        this.externalElements = Collections.unmodifiableList(other);
    }

    public byte[] getKeyId() {
        return (keyId == null ? null : (byte []) keyId.clone());
    }

    public byte[] getKeyPacket() {
        return (keyPacket == null ? null : (byte []) keyPacket.clone());
    }

    public List getExternalElements() {
        return externalElements;
    }

    public void marshal(Node parent, String dsPrefix, DOMCryptoContext context)
        throws MarshalException {
        Document ownerDoc = DOMUtils.getOwnerDocument(parent);

        Element pdElem = DOMUtils.createElement
            (ownerDoc, "PGPData", XMLSignature.XMLNS, dsPrefix);

        // create and append PGPKeyID element
        if (keyId != null) {
            Element keyIdElem = DOMUtils.createElement
                (ownerDoc, "PGPKeyID", XMLSignature.XMLNS, dsPrefix);
            keyIdElem.appendChild
                (ownerDoc.createTextNode(Base64.encode(keyId)));
            pdElem.appendChild(keyIdElem);
        }

        // create and append PGPKeyPacket element
        if (keyPacket != null) {
            Element keyPktElem = DOMUtils.createElement
                (ownerDoc, "PGPKeyPacket", XMLSignature.XMLNS, dsPrefix);
            keyPktElem.appendChild
                (ownerDoc.createTextNode(Base64.encode(keyPacket)));
            pdElem.appendChild(keyPktElem);
        }

        // create and append any elements
        for (int i = 0, size = externalElements.size(); i < size; i++) {
            DOMUtils.appendChild(pdElem, ((javax.xml.crypto.dom.DOMStructure)
                externalElements.get(i)).getNode());
        }

        parent.appendChild(pdElem);
    }

    /**
     * We assume packets use the new format packet syntax, as specified in
     * section 4 of RFC 2440.
     *
     * This method only checks if the packet contains a valid tag. The
     * contents of the packet should be checked by the application.
     */
    private void checkKeyPacket(byte[] keyPacket) {
        // length must be at least 3 (one byte for tag, one byte for length,
        // and minimally one byte of content
        if (keyPacket.length < 3) {
            throw new IllegalArgumentException("keypacket must be at least " +
                "3 bytes long");
        }

        int tag = keyPacket[0];
        // first bit must be set
        if ((tag & 128) != 128) {
            throw new IllegalArgumentException("keypacket tag is invalid: " +
                "bit 7 is not set");
        }
        // make sure using new format
        if ((tag & 64) != 64) {
            throw new IllegalArgumentException("old keypacket tag format is " +
                "unsupported");
        }

        // tag value must be 6, 14, 5 or 7
        if (((tag & 6) != 6) && ((tag & 14) != 14) &&
            ((tag & 5) != 5) && ((tag & 7) != 7)) {
            throw new IllegalArgumentException("keypacket tag is invalid: " +
                "must be 6, 14, 5, or 7");
        }
    }
}
