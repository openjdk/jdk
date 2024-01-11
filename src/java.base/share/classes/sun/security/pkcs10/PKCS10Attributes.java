/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.pkcs10;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Hashtable;

import sun.security.util.DerEncoder;
import sun.security.util.DerInputStream;
import sun.security.util.DerOutputStream;
import sun.security.util.DerValue;

/**
 * This class defines the PKCS10 attributes for the request.
 * The ASN.1 syntax for this is:
 * <pre>
 * Attributes ::= SET OF Attribute
 * </pre>
 *
 * @author Amit Kapoor
 * @author Hemma Prafullchandra
 * @see PKCS10
 * @see PKCS10Attribute
 */
public class PKCS10Attributes implements DerEncoder {

    private final Hashtable<String, PKCS10Attribute> map =
            new Hashtable<>(3);

    /**
     * Default constructor for the PKCS10 attribute.
     */
    public PKCS10Attributes() { }

    /**
     * Create the object from the array of PKCS10Attribute objects.
     *
     * @param attrs the array of PKCS10Attribute objects.
     */
    public PKCS10Attributes(PKCS10Attribute[] attrs) {
        for (int i = 0; i < attrs.length; i++) {
            map.put(attrs[i].getAttributeId().toString(), attrs[i]);
        }
    }

    /**
     * Create the object, decoding the values from the passed DER stream.
     * The DER stream contains the SET OF Attribute.
     *
     * @param in the DerInputStream to read the attributes from.
     * @exception IOException on decoding errors.
     */
    public PKCS10Attributes(DerInputStream in) throws IOException {
        DerValue[] attrs = in.getSet(3, true);

        if (attrs == null)
            throw new IOException("Illegal encoding of attributes");
        for (int i = 0; i < attrs.length; i++) {
            PKCS10Attribute attr = new PKCS10Attribute(attrs[i]);
            map.put(attr.getAttributeId().toString(), attr);
        }
    }

    /**
     * Encode the attributes in DER form to the stream.
     * Implements the {@code DerEncoder} interface.
     *
     * @param out the DerOutputStream to marshal the contents to.
     */
    @Override
    public void encode(DerOutputStream out) {
        // first copy the elements into an array
        Collection<PKCS10Attribute> allAttrs = map.values();
        PKCS10Attribute[] attribs =
                allAttrs.toArray(new PKCS10Attribute[map.size()]);

        out.putOrderedSetOf(
                DerValue.createTag(DerValue.TAG_CONTEXT, true, (byte)0), attribs);
    }

    /**
     * Set the attribute value.
     */
    public void setAttribute(String name, Object obj) {
        if (obj instanceof PKCS10Attribute) {
            map.put(name, (PKCS10Attribute)obj);
        }
    }

    /**
     * Get the attribute value.
     */
    public Object getAttribute(String name) {
        return map.get(name);
    }

    /**
     * Delete the attribute value.
     */
    public void deleteAttribute(String name) {
        map.remove(name);
    }

    /**
     * Return an enumeration of names of attributes existing within this
     * attribute.
     */
    public Enumeration<PKCS10Attribute> getElements() {
        return (map.elements());
    }

    /**
     * Return a Collection of attributes existing within this
     * PKCS10Attributes object.
     */
    public Collection<PKCS10Attribute> getAttributes() {
        return (Collections.unmodifiableCollection(map.values()));
    }

    /**
     * Compares this PKCS10Attributes for equality with the specified
     * object. If the {@code other} object is an
     * {@code instanceof PKCS10Attributes}, then
     * all the entries are compared with the entries from this.
     *
     * @param other the object to test for equality with this PKCS10Attributes.
     * @return true if all the entries match that of the Other,
     * false otherwise.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other)
            return true;
        if (!(other instanceof PKCS10Attributes))
            return false;

        Collection<PKCS10Attribute> othersAttribs =
                ((PKCS10Attributes)other).getAttributes();
        PKCS10Attribute[] attrs =
            othersAttribs.toArray(new PKCS10Attribute[0]);
        int len = attrs.length;
        if (len != map.size())
            return false;
        PKCS10Attribute thisAttr;
        String key;
        for (PKCS10Attribute otherAttr : attrs) {
            key = otherAttr.getAttributeId().toString();

            thisAttr = map.get(key);
            if (thisAttr == null)
                return false;
            if (!thisAttr.equals(otherAttr))
                return false;
        }
        return true;
    }

    /**
     * {@return the hashcode value for this PKCS10Attributes}
     */
    @Override
    public int hashCode() {
        return map.hashCode();
    }

    /**
     * Returns a string representation of this {@code PKCS10Attributes} object
     * in the form of a set of entries, enclosed in braces and separated
     * by the ASCII characters "<code>,&nbsp;</code>" (comma and space).
     * <p>Overrides the {@code toString} method of {@code Object}.
     *
     * @return  a string representation of this PKCS10Attributes.
     */
    public String toString() {
        return map.size() + "\n" + map;
    }
}
