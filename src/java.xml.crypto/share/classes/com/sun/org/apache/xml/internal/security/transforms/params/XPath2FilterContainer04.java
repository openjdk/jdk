/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.sun.org.apache.xml.internal.security.transforms.params;

import com.sun.org.apache.xml.internal.security.exceptions.XMLSecurityException;
import com.sun.org.apache.xml.internal.security.transforms.TransformParam;
import com.sun.org.apache.xml.internal.security.utils.ElementProxy;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Implements the parameters for the <A
 * HREF="http://www.w3.org/TR/xmldsig-filter2/">XPath Filter v2.0</A>.
 *
 * @see <A HREF="http://www.w3.org/TR/xmldsig-filter2/">XPath Filter v2.0 (TR)</A>
 */
public class XPath2FilterContainer04 extends ElementProxy implements TransformParam {

    /** Field _ATT_FILTER */
    private static final String _ATT_FILTER = "Filter";

    /** Field _ATT_FILTER_VALUE_INTERSECT */
    private static final String _ATT_FILTER_VALUE_INTERSECT = "intersect";

    /** Field _ATT_FILTER_VALUE_SUBTRACT */
    private static final String _ATT_FILTER_VALUE_SUBTRACT = "subtract";

    /** Field _ATT_FILTER_VALUE_UNION */
    private static final String _ATT_FILTER_VALUE_UNION = "union";

    /** Field _TAG_XPATH2 */
    public static final String _TAG_XPATH2 = "XPath";

    /** Field XPathFiler2NS */
    public static final String XPathFilter2NS =
        "http://www.w3.org/2002/04/xmldsig-filter2";

    /**
     * Constructor XPath2FilterContainer04
     *
     */
    private XPath2FilterContainer04() {

        // no instantiation
    }

    /**
     * Constructor XPath2FilterContainer04
     *
     * @param doc
     * @param xpath2filter
     * @param filterType
     */
    private XPath2FilterContainer04(Document doc, String xpath2filter, String filterType) {
        super(doc);

        setLocalAttribute(XPath2FilterContainer04._ATT_FILTER, filterType);

        if (xpath2filter.length() > 2
            && !Character.isWhitespace(xpath2filter.charAt(0))) {
            addReturnToSelf();
            appendSelf(createText(xpath2filter));
            addReturnToSelf();
        } else {
            appendSelf(createText(xpath2filter));
        }
    }

    /**
     * Constructor XPath2FilterContainer04
     *
     * @param element
     * @param baseURI
     * @throws XMLSecurityException
     */
    private XPath2FilterContainer04(Element element, String baseURI)
        throws XMLSecurityException {

        super(element, baseURI);

        String filterStr = getLocalAttribute(XPath2FilterContainer04._ATT_FILTER);

        if (!filterStr.equals(XPath2FilterContainer04._ATT_FILTER_VALUE_INTERSECT)
            && !filterStr.equals(XPath2FilterContainer04._ATT_FILTER_VALUE_SUBTRACT)
            && !filterStr.equals(XPath2FilterContainer04._ATT_FILTER_VALUE_UNION)) {
            Object exArgs[] = { XPath2FilterContainer04._ATT_FILTER, filterStr,
                                XPath2FilterContainer04._ATT_FILTER_VALUE_INTERSECT
                                + ", "
                                + XPath2FilterContainer04._ATT_FILTER_VALUE_SUBTRACT
                                + " or "
                                + XPath2FilterContainer04._ATT_FILTER_VALUE_UNION };

            throw new XMLSecurityException("attributeValueIllegal", exArgs);
        }
    }

    /**
     * Creates a new XPath2FilterContainer04 with the filter type "intersect".
     *
     * @param doc
     * @param xpath2filter
     * @return the instance
     */
    public static XPath2FilterContainer04 newInstanceIntersect(
        Document doc, String xpath2filter
    ) {
        return new XPath2FilterContainer04(
            doc, xpath2filter, XPath2FilterContainer04._ATT_FILTER_VALUE_INTERSECT);
    }

    /**
     * Creates a new XPath2FilterContainer04 with the filter type "subtract".
     *
     * @param doc
     * @param xpath2filter
     * @return the instance
     */
    public static XPath2FilterContainer04 newInstanceSubtract(
        Document doc, String xpath2filter
    ) {
        return new XPath2FilterContainer04(
            doc, xpath2filter, XPath2FilterContainer04._ATT_FILTER_VALUE_SUBTRACT);
    }

    /**
     * Creates a new XPath2FilterContainer04 with the filter type "union".
     *
     * @param doc
     * @param xpath2filter
     * @return the instance
     */
    public static XPath2FilterContainer04 newInstanceUnion(
        Document doc, String xpath2filter
    ) {
        return new XPath2FilterContainer04(
            doc, xpath2filter, XPath2FilterContainer04._ATT_FILTER_VALUE_UNION);
    }

    /**
     * Creates a XPath2FilterContainer04 from an existing Element; needed for verification.
     *
     * @param element
     * @param baseURI
     * @return the instance
     *
     * @throws XMLSecurityException
     */
    public static XPath2FilterContainer04 newInstance(
        Element element, String baseURI
    ) throws XMLSecurityException {
        return new XPath2FilterContainer04(element, baseURI);
    }

    /**
     * Returns {@code true} if the {@code Filter} attribute has value "intersect".
     *
     * @return {@code true} if the {@code Filter} attribute has value "intersect".
     */
    public boolean isIntersect() {
        return getLocalAttribute(XPath2FilterContainer04._ATT_FILTER
        ).equals(XPath2FilterContainer04._ATT_FILTER_VALUE_INTERSECT);
    }

    /**
     * Returns {@code true} if the {@code Filter} attribute has value "subtract".
     *
     * @return {@code true} if the {@code Filter} attribute has value "subtract".
     */
    public boolean isSubtract() {
        return getLocalAttribute(XPath2FilterContainer04._ATT_FILTER
        ).equals(XPath2FilterContainer04._ATT_FILTER_VALUE_SUBTRACT);
    }

    /**
     * Returns {@code true} if the {@code Filter} attribute has value "union".
     *
     * @return {@code true} if the {@code Filter} attribute has value "union".
     */
    public boolean isUnion() {
        return getLocalAttribute(XPath2FilterContainer04._ATT_FILTER
        ).equals(XPath2FilterContainer04._ATT_FILTER_VALUE_UNION);
    }

    /**
     * Returns the XPath 2 Filter String
     *
     * @return the XPath 2 Filter String
     */
    public String getXPathFilterStr() {
        return this.getTextFromTextChild();
    }

    /**
     * Returns the first Text node which contains information from the XPath 2
     * Filter String. We must use this stupid hook to enable the here() function
     * to work.
     *
     * @return the first Text node which contains information from the XPath 2 Filter String
     */
    public Node getXPathFilterTextNode() {
        Node childNode = getElement().getFirstChild();
        while (childNode != null) {
            if (childNode.getNodeType() == Node.TEXT_NODE) {
                return childNode;
            }
            childNode = childNode.getNextSibling();
        }

        return null;
    }

    /** {@inheritDoc} */
    public final String getBaseLocalName() {
        return XPath2FilterContainer04._TAG_XPATH2;
    }

    /** {@inheritDoc} */
    public final String getBaseNamespace() {
        return XPath2FilterContainer04.XPathFilter2NS;
    }
}
