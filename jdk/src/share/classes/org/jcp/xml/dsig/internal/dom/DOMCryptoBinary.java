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
 * $Id: DOMCryptoBinary.java,v 1.14 2005/05/12 19:28:29 mullan Exp $
 */
package org.jcp.xml.dsig.internal.dom;

import java.math.BigInteger;
import javax.xml.crypto.*;
import javax.xml.crypto.dom.DOMCryptoContext;
import javax.xml.crypto.dsig.*;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

import com.sun.org.apache.xml.internal.security.utils.Base64;

/**
 * A DOM-based representation of the XML <code>CryptoBinary</code> simple type
 * as defined in the W3C specification for XML-Signature Syntax and Processing.
 * The XML Schema Definition is defined as:
 *
 * <xmp>
 * <simpleType name="CryptoBinary">
 *   <restriction base = "base64Binary">
 *   </restriction>
 * </simpleType>
 * </xmp>
 *
 * @author Sean Mullan
 */
public final class DOMCryptoBinary extends DOMStructure {

    private final BigInteger bigNum;
    private final String value;

    /**
     * Create a <code>DOMCryptoBinary</code> instance from the specified
     * <code>BigInteger</code>
     *
     * @param bigNum the arbitrary-length integer
     * @throws NullPointerException if <code>bigNum</code> is <code>null</code>
     */
    public DOMCryptoBinary(BigInteger bigNum) {
        if (bigNum == null) {
            throw new NullPointerException("bigNum is null");
        }
        this.bigNum = bigNum;
        // convert to bitstring
        value = Base64.encode(bigNum);
    }

    /**
     * Creates a <code>DOMCryptoBinary</code> from a node.
     *
     * @param cbNode a CryptoBinary text node
     * @throws MarshalException if value cannot be decoded (invalid format)
     */
    public DOMCryptoBinary(Node cbNode) throws MarshalException {
        value = cbNode.getNodeValue();
        try {
            bigNum = Base64.decodeBigIntegerFromText((Text) cbNode);
        } catch (Exception ex) {
            throw new MarshalException(ex);
        }
    }

    /**
     * Returns the <code>BigInteger</code> that this object contains.
     *
     * @return the <code>BigInteger</code> that this object contains
     */
    public BigInteger getBigNum() {
        return bigNum;
    }

    public void marshal(Node parent, String prefix, DOMCryptoContext context)
        throws MarshalException {
        parent.appendChild
            (DOMUtils.getOwnerDocument(parent).createTextNode(value));
    }
}
