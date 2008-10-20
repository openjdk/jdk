/*
 * Copyright 1998-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package javax.swing.text.html.parser;

import java.util.Vector;
import java.util.Hashtable;
import java.util.Enumeration;
import java.io.*;

/**
 * This class defines the attributes of an SGML element
 * as described in a DTD using the ATTLIST construct.
 * An AttributeList can be obtained from the Element
 * class using the getAttributes() method.
 * <p>
 * It is actually an element in a linked list. Use the
 * getNext() method repeatedly to enumerate all the attributes
 * of an element.
 *
 * @see         Element
 * @author      Arthur Van Hoff
 *
 */
public final
class AttributeList implements DTDConstants, Serializable {
    public String name;
    public int type;
    public Vector<?> values;
    public int modifier;
    public String value;
    public AttributeList next;

    AttributeList() {
    }

    /**
     * Create an attribute list element.
     */
    public AttributeList(String name) {
        this.name = name;
    }

    /**
     * Create an attribute list element.
     */
    public AttributeList(String name, int type, int modifier, String value, Vector<?> values, AttributeList next) {
        this.name = name;
        this.type = type;
        this.modifier = modifier;
        this.value = value;
        this.values = values;
        this.next = next;
    }

    /**
     * @return attribute name
     */
    public String getName() {
        return name;
    }

    /**
     * @return attribute type
     * @see DTDConstants
     */
    public int getType() {
        return type;
    }

    /**
     * @return attribute modifier
     * @see DTDConstants
     */
    public int getModifier() {
        return modifier;
    }

    /**
     * @return possible attribute values
     */
    public Enumeration<?> getValues() {
        return (values != null) ? values.elements() : null;
    }

    /**
     * @return default attribute value
     */
    public String getValue() {
        return value;
    }

    /**
     * @return the next attribute in the list
     */
    public AttributeList getNext() {
        return next;
    }

    /**
     * @return string representation
     */
    public String toString() {
        return name;
    }

    /**
     * Create a hashtable of attribute types.
     */
    static Hashtable<Object, Object> attributeTypes = new Hashtable<Object, Object>();

    static void defineAttributeType(String nm, int val) {
        Integer num = Integer.valueOf(val);
        attributeTypes.put(nm, num);
        attributeTypes.put(num, nm);
    }

    static {
        defineAttributeType("CDATA", CDATA);
        defineAttributeType("ENTITY", ENTITY);
        defineAttributeType("ENTITIES", ENTITIES);
        defineAttributeType("ID", ID);
        defineAttributeType("IDREF", IDREF);
        defineAttributeType("IDREFS", IDREFS);
        defineAttributeType("NAME", NAME);
        defineAttributeType("NAMES", NAMES);
        defineAttributeType("NMTOKEN", NMTOKEN);
        defineAttributeType("NMTOKENS", NMTOKENS);
        defineAttributeType("NOTATION", NOTATION);
        defineAttributeType("NUMBER", NUMBER);
        defineAttributeType("NUMBERS", NUMBERS);
        defineAttributeType("NUTOKEN", NUTOKEN);
        defineAttributeType("NUTOKENS", NUTOKENS);

        attributeTypes.put("fixed", Integer.valueOf(FIXED));
        attributeTypes.put("required", Integer.valueOf(REQUIRED));
        attributeTypes.put("current", Integer.valueOf(CURRENT));
        attributeTypes.put("conref", Integer.valueOf(CONREF));
        attributeTypes.put("implied", Integer.valueOf(IMPLIED));
    }

    public static int name2type(String nm) {
        Integer i = (Integer)attributeTypes.get(nm);
        return (i == null) ? CDATA : i.intValue();
    }

    public static String type2name(int tp) {
        return (String)attributeTypes.get(Integer.valueOf(tp));
    }
}
