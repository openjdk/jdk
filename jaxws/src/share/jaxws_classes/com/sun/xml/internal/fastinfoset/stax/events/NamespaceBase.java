/*
 * Copyright (c) 2004, 2012, Oracle and/or its affiliates. All rights reserved.
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
 *
 * THIS FILE WAS MODIFIED BY SUN MICROSYSTEMS, INC.
 */

package com.sun.xml.internal.fastinfoset.stax.events;

import javax.xml.namespace.QName;
import javax.xml.stream.events.Namespace;


public class NamespaceBase extends AttributeBase implements Namespace{
    //J2SE1.5.0 javax.xml.XMLConstants
    static final String DEFAULT_NS_PREFIX = "";
    static final String XML_NS_URI = "http://www.w3.org/XML/1998/namespace";
    static final String XML_NS_PREFIX = "xml";
    static final String XMLNS_ATTRIBUTE_NS_URI = "http://www.w3.org/2000/xmlns/";
    static final String XMLNS_ATTRIBUTE = "xmlns";
    static final String W3C_XML_SCHEMA_NS_URI = "http://www.w3.org/2001/XMLSchema";
    static final String W3C_XML_SCHEMA_INSTANCE_NS_URI = "http://www.w3.org/2001/XMLSchema-instance";

    //is this namespace default declaration?
    private boolean defaultDeclaration = false;

    /** a namespace attribute has a form: xmlns:NCName="URI reference" */
    public NamespaceBase(String namespaceURI) {
        super(XMLNS_ATTRIBUTE, "", namespaceURI);
        setEventType(NAMESPACE);
    }

  /**
   * Create a new Namespace
   * @param prefix prefix of a namespace is the local name for an attribute
   * @param namespaceURI the uri reference of a namespace is the value for an attribute
   */
    public NamespaceBase(String prefix, String namespaceURI){
        super(XMLNS_ATTRIBUTE, prefix, namespaceURI);
        setEventType(NAMESPACE);
        if (Util.isEmptyString(prefix)) {
            defaultDeclaration=true;
        }
    }

    void setPrefix(String prefix){
        if(prefix == null)
            setName(new QName(XMLNS_ATTRIBUTE_NS_URI,DEFAULT_NS_PREFIX,XMLNS_ATTRIBUTE));
        else// new QName(uri, localpart, prefix)
            setName(new QName(XMLNS_ATTRIBUTE_NS_URI,prefix,XMLNS_ATTRIBUTE));
    }

    public String getPrefix() {
        if (defaultDeclaration) return "";
        return super.getLocalName();
    }


  /**
   * set Namespace URI reference (xmlns:prefix = "uri")
   * @param uri the uri reference of a namespace is the value for an attribute
   */
    void setNamespaceURI(String uri) {
        setValue(uri);
    }
    public String getNamespaceURI() {
        return getValue();
    }


    public boolean isNamespace(){
        return true;
    }

    public boolean isDefaultNamespaceDeclaration() {
        return defaultDeclaration;
    }


}
