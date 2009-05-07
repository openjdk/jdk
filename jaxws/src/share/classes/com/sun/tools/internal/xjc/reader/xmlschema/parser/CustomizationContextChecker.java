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
package com.sun.tools.internal.xjc.reader.xmlschema.parser;

import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

import javax.xml.namespace.QName;

import com.sun.tools.internal.xjc.reader.Const;
import com.sun.xml.internal.bind.v2.WellKnownNamespace;

import org.xml.sax.Attributes;
import org.xml.sax.ErrorHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.XMLFilterImpl;

/**
 * Checks if binding declarations are placed where they are allowed.
 *
 * <p>
 * For example, if a &lt;jaxb:property> customization is given under
 * the &lt;xs:simpleContent> element, this class raises an error.
 *
 * <p>
 * our main checkpoint of misplaced customizations are in BGMBuilder.
 * There, we mark a customization whenever we use it. At the end of the
 * day, we look for unmarked customizations and raise errors for them.
 *
 * <p>
 * Between this approach and the JAXB spec 1.0 is a problem that
 * the spec allows/prohibits customizations at schema element level,
 * while BGMBuilder and XSOM works on schema component levels.
 *
 * <p>
 * For example, a property customization is allowed on a complex type
 * schema component, but it's only allowed on the &lt;complexType>
 * element. The spec team informed us that they would consider resolving
 * this discrepancy in favor of RI, but meanwhile we need to detect
 * errors correctly.
 *
 * <p>
 * This filter is implemented for this purpose.
 *
 *
 * <h2>Customization and allowed locations</h2>
 *
 * - globalBinding/schemaBinding
 *     schema
 *
 * - class
 *     complexType(*), modelGroupDecl, modelGroup, element
 *
 * - property
 *     attribute, element, any, modelGroup, modelGroupRef, complexType(*)
 *
 * - javaType
 *     simpleType(*)
 *
 * - typesafeEnumClass
 *     simpleType(*)
 *
 * - typesafeEnumMember
 *     simpleType(*), enumeration
 *
 * Components marked with '*' needs a check by this component
 * since more than one schema element corresponds to one schema component
 * of that type.
 *
 * <p>
 * For simple types, customizations are allowed only under the &lt;xs:simpleType>
 * element, and for complex types they are allowed only under the
 * &lt;xs:cimplexType> element.
 *
 * <p>
 * So the bottom line is that it would be suffice if we just make sure
 * that no customization will be attached under other elements of
 * simple types and complex types. Those are:
 *
 * - simpleType/restriction
 * - list
 * - union
 * - complexType/(simple or complex)Content
 * - complexType/(simple or complex)Content/(restriction of extension)
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public class CustomizationContextChecker extends XMLFilterImpl {

    /** Keep names of all the ancestor elements. */
    private final Stack<QName> elementNames = new Stack<QName>();

    private final ErrorHandler errorHandler;

    private Locator locator;

    /** Set of element names that cannot have JAXB customizations. */
    private static final Set<String> prohibitedSchemaElementNames = new HashSet<String>();

    /**
     * @param _errorHandler
     *      Detected errors will be sent to this object.
     */
    public CustomizationContextChecker( ErrorHandler _errorHandler ) {
        this.errorHandler = _errorHandler;
    }

    static {
        prohibitedSchemaElementNames.add("restriction");
        prohibitedSchemaElementNames.add("extension");
        prohibitedSchemaElementNames.add("simpleContent");
        prohibitedSchemaElementNames.add("complexContent");
        prohibitedSchemaElementNames.add("list");
        prohibitedSchemaElementNames.add("union");
    }




    /** Gets the stack top. */
    private QName top() {
        return elementNames.peek();
    }

    public void startElement(String namespaceURI, String localName, String qName, Attributes atts) throws SAXException {
        QName newElement = new QName(namespaceURI,localName);

        if( newElement.getNamespaceURI().equals(Const.JAXB_NSURI)
         && top().getNamespaceURI().equals(WellKnownNamespace.XML_SCHEMA) ) {
            // we hit a JAXB customization. the stack top should be
            // <xs:appinfo>
            if( elementNames.size()>=3 ) {
                // the above statement checks if the following statement doesn't
                // cause an exception.
                QName schemaElement = elementNames.get( elementNames.size()-3 );
                if( prohibitedSchemaElementNames.contains(schemaElement.getLocalPart()) ) {
                    // the owner schema element is in the wanted list.
                    errorHandler.error( new SAXParseException(
                        Messages.format(
                            Messages.ERR_UNACKNOWLEDGED_CUSTOMIZATION,
                            localName ),
                        locator ) );
                }
            }


        }

        elementNames.push(newElement);

        super.startElement(namespaceURI, localName, qName, atts );
    }

    public void endElement(String namespaceURI, String localName, String qName)
        throws SAXException {

        super.endElement(namespaceURI, localName, qName);

        elementNames.pop();
    }

    public void setDocumentLocator(Locator locator) {
        super.setDocumentLocator(locator);
        this.locator = locator;
    }

}
