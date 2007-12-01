/*
 * Copyright 2003-2005 Sun Microsystems, Inc.  All Rights Reserved.
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

package javax.xml;

/**
 * <p>Utility class to contain basic XML values as constants.</p>
 *
 * @author <a href="mailto:Jeff.Suttor@Sun.com">Jeff Suttor</a>
 * @see <a href="http://www.w3.org/TR/xml11/">Extensible Markup Language (XML) 1.1</a>
 * @see <a href="http://www.w3.org/TR/REC-xml">Extensible Markup Language (XML) 1.0 (Second Edition)</a>
 * @see <a href="http://www.w3.org/XML/xml-V10-2e-errata">XML 1.0 Second Edition Specification Errata</a>
 * @see <a href="http://www.w3.org/TR/xml-names11/">Namespaces in XML 1.1</a>
 * @see <a href="http://www.w3.org/TR/REC-xml-names">Namespaces in XML</a>
 * @see <a href="http://www.w3.org/XML/xml-names-19990114-errata">Namespaces in XML Errata</a>
 * @see <a href="http://www.w3.org/TR/xmlschema-1/">XML Schema Part 1: Structures</a>
 * @since 1.5
 **/

public final class XMLConstants {

    /**
     * <p>Private constructor to prevent instantiation.</p>
     */
        private XMLConstants() {
        }

    /**
     * <p>Namespace URI to use to represent that there is no Namespace.</p>
     *
     * <p>Defined by the Namespace specification to be "".</p>
     *
     * @see <a href="http://www.w3.org/TR/REC-xml-names/#defaulting">
     * Namespaces in XML, 5.2 Namespace Defaulting</a>
     */
    public static final String NULL_NS_URI = "";

    /**
     * <p>Prefix to use to represent the default XML Namespace.</p>
     *
     * <p>Defined by the XML specification to be "".</p>
     *
     * @see <a
     * href="http://www.w3.org/TR/REC-xml-names/#ns-qualnames">
     * Namespaces in XML, 3. Qualified Names</a>
     */
    public static final String DEFAULT_NS_PREFIX = "";

    /**
     * <p>The official XML Namespace name URI.</p>
     *
     * <p>Defined by the XML specification to be
     * "<code>http://www.w3.org/XML/1998/namespace</code>".</p>
     *
     * @see <a
     * href="http://www.w3.org/TR/REC-xml-names/#ns-qualnames">
     * Namespaces in XML, 3. Qualified Names</a>
     */
    public static final String XML_NS_URI =
        "http://www.w3.org/XML/1998/namespace";

    /**
     * <p>The official XML Namespace prefix.</p>
     *
     * <p>Defined by the XML specification to be "<code>xml</code>".</p>
     *
     * @see <a
     * href="http://www.w3.org/TR/REC-xml-names/#ns-qualnames">
     * Namespaces in XML, 3. Qualified Names<</a>
     */
    public static final String XML_NS_PREFIX = "xml";

    /**
     * <p>The official XML attribute used for specifying XML Namespace
     * declarations, {@link #XMLNS_ATTRIBUTE
     * XMLConstants.XMLNS_ATTRIBUTE}, Namespace name URI.</p>
     *
     * <p>Defined by the XML specification to be
     * "<code>http://www.w3.org/2000/xmlns/</code>".</p>
     *
     * @see <a
     * href="http://www.w3.org/TR/REC-xml-names/#ns-qualnames">
     * Namespaces in XML, 3. Qualified Names</a>
     * @see <a
     * href="http://www.w3.org/XML/xml-names-19990114-errata/">
     * Namespaces in XML Errata</a>
     */
    public static final String XMLNS_ATTRIBUTE_NS_URI =
        "http://www.w3.org/2000/xmlns/";

    /**
     * <p>The official XML attribute used for specifying XML Namespace
     * declarations.</p>
     *
     * <p>It is <strong><em>NOT</em></strong> valid to use as a
     * prefix.  Defined by the XML specification to be
     * "<code>xmlns</code>".</p>
     *
     * @see <a
     * href="http://www.w3.org/TR/REC-xml-names/#ns-qualnames">
     * Namespaces in XML, 3. Qualified Names</a>
     */
    public static final String XMLNS_ATTRIBUTE = "xmlns";

    /**
     * <p>W3C XML Schema Namespace URI.</p>
     *
     * <p>Defined to be "<code>http://www.w3.org/2001/XMLSchema</code>".
     *
     * @see <a href=
     *  "http://www.w3.org/TR/xmlschema-1/#Instance_Document_Constructions">
     *  XML Schema Part 1:
     *  Structures, 2.6 Schema-Related Markup in Documents Being Validated</a>
     */
    public static final String W3C_XML_SCHEMA_NS_URI =
        "http://www.w3.org/2001/XMLSchema";

    /**
     * <p>W3C XML Schema Instance Namespace URI.</p>
     *
     * <p>Defined to be "<code>http://www.w3.org/2001/XMLSchema-instance</code>".</p>
     *
     * @see <a href=
     *  "http://www.w3.org/TR/xmlschema-1/#Instance_Document_Constructions">
     *  XML Schema Part 1:
     *  Structures, 2.6 Schema-Related Markup in Documents Being Validated</a>
     */
    public static final String W3C_XML_SCHEMA_INSTANCE_NS_URI =
        "http://www.w3.org/2001/XMLSchema-instance";

        /**
         * <p>W3C XPath Datatype Namespace URI.</p>
         *
         * <p>Defined to be "<code>http://www.w3.org/2003/11/xpath-datatypes</code>".</p>
         *
         * @see <a href="http://www.w3.org/TR/xpath-datamodel">XQuery 1.0 and XPath 2.0 Data Model</a>
         */
        public static final String W3C_XPATH_DATATYPE_NS_URI = "http://www.w3.org/2003/11/xpath-datatypes";

    /**
     * <p>XML Document Type Declaration Namespace URI as an arbitrary value.</p>
     *
     * <p>Since not formally defined by any existing standard, arbitrarily define to be "<code>http://www.w3.org/TR/REC-xml</code>".
     */
    public static final String XML_DTD_NS_URI = "http://www.w3.org/TR/REC-xml";

        /**
         * <p>RELAX NG Namespace URI.</p>
         *
         * <p>Defined to be "<code>http://relaxng.org/ns/structure/1.0</code>".</p>
         *
         * @see <a href="http://relaxng.org/spec-20011203.html">RELAX NG Specification</a>
         */
        public static final String RELAXNG_NS_URI = "http://relaxng.org/ns/structure/1.0";

        /**
         * <p>Feature for secure processing.</p>
         *
         * <ul>
         *   <li>
         *     <code>true</code> instructs the implementation to process XML securely.
         *     This may set limits on XML constructs to avoid conditions such as denial of service attacks.
         *   </li>
         *   <li>
         *     <code>false</code> instructs the implementation to process XML acording the letter of the XML specifications
         *     ingoring security issues such as limits on XML constructs to avoid conditions such as denial of service attacks.
         *   </li>
         * </ul>
         */
        public static final String FEATURE_SECURE_PROCESSING = "http://javax.xml.XMLConstants/feature/secure-processing";
}
