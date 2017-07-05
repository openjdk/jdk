/*
 * Portions Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.internal.ws.wsdl.document.schema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.QName;

import com.sun.tools.internal.ws.wsdl.framework.AbstractDocument;
import com.sun.tools.internal.ws.wsdl.framework.Defining;
import com.sun.tools.internal.ws.wsdl.framework.Extension;
import com.sun.tools.internal.ws.wsdl.framework.Kind;
import com.sun.tools.internal.ws.wsdl.framework.ValidationException;
import com.sun.tools.internal.ws.wsdl.parser.Constants;

/**
 *
 * @author WS Development Team
 */
public class Schema extends Extension implements Defining {

    public Schema(AbstractDocument document) {
        _document = document;
        _nsPrefixes = new HashMap();
        _definedEntities = new ArrayList();
    }

    public QName getElementName() {
        return SchemaConstants.QNAME_SCHEMA;
    }

    public SchemaElement getContent() {
        return _content;
    }

    public void setContent(SchemaElement entity) {
        _content = entity;
        _content.setSchema(this);
    }

    public void setTargetNamespaceURI(String uri) {
        _targetNamespaceURI = uri;
    }

    public String getTargetNamespaceURI() {
        return _targetNamespaceURI;
    }

    public void addPrefix(String prefix, String uri) {
        _nsPrefixes.put(prefix, uri);
    }

    public String getURIForPrefix(String prefix) {
        return (String) _nsPrefixes.get(prefix);
    }

    public Iterator prefixes() {
        return _nsPrefixes.keySet().iterator();
    }

    public void defineAllEntities() {
        if (_content == null) {
            throw new ValidationException(
                "validation.shouldNotHappen",
                "missing schema content");
        }

        for (Iterator iter = _content.children(); iter.hasNext();) {
            SchemaElement child = (SchemaElement) iter.next();
            if (child.getQName().equals(SchemaConstants.QNAME_ATTRIBUTE)) {
                QName name =
                    new QName(
                        _targetNamespaceURI,
                        child.getValueOfMandatoryAttribute(
                            Constants.ATTR_NAME));
                defineEntity(child, SchemaKinds.XSD_ATTRIBUTE, name);
            } else if (
                child.getQName().equals(
                    SchemaConstants.QNAME_ATTRIBUTE_GROUP)) {
                QName name =
                    new QName(
                        _targetNamespaceURI,
                        child.getValueOfMandatoryAttribute(
                            Constants.ATTR_NAME));
                defineEntity(child, SchemaKinds.XSD_ATTRIBUTE_GROUP, name);
            } else if (
                child.getQName().equals(SchemaConstants.QNAME_ELEMENT)) {
                QName name =
                    new QName(
                        _targetNamespaceURI,
                        child.getValueOfMandatoryAttribute(
                            Constants.ATTR_NAME));
                defineEntity(child, SchemaKinds.XSD_ELEMENT, name);
            } else if (child.getQName().equals(SchemaConstants.QNAME_GROUP)) {
                QName name =
                    new QName(
                        _targetNamespaceURI,
                        child.getValueOfMandatoryAttribute(
                            Constants.ATTR_NAME));
                defineEntity(child, SchemaKinds.XSD_GROUP, name);
            } else if (
                child.getQName().equals(SchemaConstants.QNAME_COMPLEX_TYPE)) {
                QName name =
                    new QName(
                        _targetNamespaceURI,
                        child.getValueOfMandatoryAttribute(
                            Constants.ATTR_NAME));
                defineEntity(child, SchemaKinds.XSD_TYPE, name);
            } else if (
                child.getQName().equals(SchemaConstants.QNAME_SIMPLE_TYPE)) {
                QName name =
                    new QName(
                        _targetNamespaceURI,
                        child.getValueOfMandatoryAttribute(
                            Constants.ATTR_NAME));
                defineEntity(child, SchemaKinds.XSD_TYPE, name);
            }
        }
    }

    public void defineEntity(SchemaElement element, Kind kind, QName name) {
        SchemaEntity entity = new SchemaEntity(this, element, kind, name);
        _document.define(entity);
        _definedEntities.add(entity);
    }

    public Iterator definedEntities() {
        return _definedEntities.iterator();
    }

    public void validateThis() {
        if (_content == null) {
            throw new ValidationException(
                "validation.shouldNotHappen",
                "missing schema content");
        }
    }

    public String asString(QName name) {
        if (name.getNamespaceURI().equals("")) {
            return name.getLocalPart();
        } else {
            // look for a prefix
            for (Iterator iter = prefixes(); iter.hasNext();) {
                String prefix = (String) iter.next();
                if (prefix.equals(name.getNamespaceURI())) {
                    return prefix + ":" + name.getLocalPart();
                }
            }

            // not found
            return null;
        }
    }

    private AbstractDocument _document;
    private String _targetNamespaceURI;
    private SchemaElement _content;
    private List _definedEntities;
    private Map _nsPrefixes;
}
