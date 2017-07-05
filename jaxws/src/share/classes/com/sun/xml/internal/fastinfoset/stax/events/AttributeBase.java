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
 *
 * THIS FILE WAS MODIFIED BY SUN MICROSYSTEMS, INC.
 */


package com.sun.xml.internal.fastinfoset.stax.events;

import javax.xml.namespace.QName;
import javax.xml.stream.events.Attribute;

import com.sun.xml.internal.fastinfoset.stax.events.Util;


public class AttributeBase extends EventBase implements Attribute

{
    //an Attribute consists of a qualified name and value
    private QName _QName;
    private String _value;

    private String _attributeType = null;
    //A flag indicating whether this attribute was actually specified in the start-tag
    //of its element or was defaulted from the schema.
    private boolean _specified = false;

    public AttributeBase(){
        super(ATTRIBUTE);
    }

    public AttributeBase(String name, String value) {
        super(ATTRIBUTE);
        _QName = new QName(name);
        _value = value;
    }

    public AttributeBase(QName qname, String value) {
        _QName = qname;
        _value = value;
    }

    public AttributeBase(String prefix, String localName, String value) {
        this(prefix, null,localName, value, null);
    }

    public AttributeBase(String prefix, String namespaceURI, String localName,
                        String value, String attributeType) {
        if (prefix == null) prefix = "";
        _QName = new QName(namespaceURI, localName,prefix);
        _value = value;
        _attributeType = (attributeType == null) ? "CDATA":attributeType;
    }


    public void setName(QName name){
        _QName = name ;
    }

  /**
   * Returns the QName for this attribute
   */
    public QName getName() {
        return _QName;
    }

    public void setValue(String value){
        _value = value;
    }

    public String getLocalName() {
        return _QName.getLocalPart();
    }
  /**
   * Gets the normalized value of this attribute
   */
    public String getValue() {
        return _value;
    }

    public void setAttributeType(String attributeType){
        _attributeType = attributeType ;
    }

    /**
   * Gets the type of this attribute, default is
   * the String "CDATA"
   * @return the type as a String, default is "CDATA"
   */
    public String getDTDType() {
        return _attributeType;
    }


  /**
   * A flag indicating whether this attribute was actually
   * specified in the start-tag of its element, or was defaulted from the schema.
   * @return returns true if this was specified in the start element
   */
    public boolean isSpecified() {
        return _specified ;
    }

    public void setSpecified(boolean isSpecified){
        _specified = isSpecified ;
    }


    public String toString() {
        String prefix = _QName.getPrefix();
        if (!Util.isEmptyString(prefix))
            return prefix + ":" + _QName.getLocalPart() + "='" + _value + "'";

        return _QName.getLocalPart() + "='" + _value + "'";
    }


}
