/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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
     * "{@code http://www.w3.org/XML/1998/namespace}".</p>
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
     * <p>Defined by the XML specification to be "{@code xml}".</p>
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
     * "{@code http://www.w3.org/2000/xmlns/}".</p>
     *
     * @see <a
     * href="http://www.w3.org/TR/REC-xml-names/#ns-qualnames">
     * Namespaces in XML, 3. Qualified Names</a>
     * @see <a
     * href="http://www.w3.org/XML/xml-names-19990114-errata">
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
     * "{@code xmlns}".</p>
     *
     * @see <a
     * href="http://www.w3.org/TR/REC-xml-names/#ns-qualnames">
     * Namespaces in XML, 3. Qualified Names</a>
     */
    public static final String XMLNS_ATTRIBUTE = "xmlns";

    /**
     * <p>W3C XML Schema Namespace URI.</p>
     *
     * <p>Defined to be "{@code http://www.w3.org/2001/XMLSchema}".
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
     * <p>Defined to be "{@code http://www.w3.org/2001/XMLSchema-instance}".</p>
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
         * <p>Defined to be "{@code http://www.w3.org/2003/11/xpath-datatypes}".</p>
         *
         * @see <a href="http://www.w3.org/TR/xpath-datamodel">XQuery 1.0 and XPath 2.0 Data Model</a>
         */
        public static final String W3C_XPATH_DATATYPE_NS_URI = "http://www.w3.org/2003/11/xpath-datatypes";

    /**
     * <p>XML Document Type Declaration Namespace URI as an arbitrary value.</p>
     *
     * <p>Since not formally defined by any existing standard, arbitrarily define to be "{@code http://www.w3.org/TR/REC-xml}".
     */
    public static final String XML_DTD_NS_URI = "http://www.w3.org/TR/REC-xml";

        /**
         * <p>RELAX NG Namespace URI.</p>
         *
         * <p>Defined to be "{@code http://relaxng.org/ns/structure/1.0}".</p>
         *
         * @see <a href="http://relaxng.org/spec-20011203.html">RELAX NG Specification</a>
         */
        public static final String RELAXNG_NS_URI = "http://relaxng.org/ns/structure/1.0";

        /**
         * <p>Feature for secure processing.</p>
         *
         * <ul>
         *   <li>
         *     {@code true} instructs the implementation to process XML securely.
         *     This may set limits on XML constructs to avoid conditions such as denial of service attacks.
         *   </li>
         *   <li>
         *     {@code false} instructs the implementation to process XML in accordance with the XML specifications
         *     ignoring security issues such as limits on XML constructs to avoid conditions such as denial of service attacks.
         *   </li>
         * </ul>
         */
        public static final String FEATURE_SECURE_PROCESSING = "http://javax.xml.XMLConstants/feature/secure-processing";


        /**
         * <p>Property: accessExternalDTD</p>
         *
         * <p>
         * Restrict access to external DTDs and external Entity References to the protocols specified.
         * If access is denied due to the restriction of this property, a runtime exception that
         * is specific to the context is thrown. In the case of {@link javax.xml.parsers.SAXParser}
         * for example, {@link org.xml.sax.SAXException} is thrown.
         * </p>
         *
         * <p>
         * <b>Value: </b> a list of protocols separated by comma. A protocol is the scheme portion of a
         * {@link java.net.URI}, or in the case of the JAR protocol, "jar" plus the scheme portion
         * separated by colon.
         * A scheme is defined as:
         *
         * <blockquote>
         * scheme = alpha *( alpha | digit | "+" | "-" | "." )<br>
         * where alpha = a-z and A-Z.<br><br>
         *
         * And the JAR protocol:<br>
         *
         * jar[:scheme]<br><br>
         *
         * Protocols including the keyword "jar" are case-insensitive. Any whitespaces as defined by
         * {@link java.lang.Character#isSpaceChar } in the value will be ignored.
         * Examples of protocols are file, http, jar:file.
         *
         * </blockquote>
         *</p>
         *
         *<p>
         * <b>Default value:</b> The default value is implementation specific and therefore not specified.
         * The following options are provided for consideration:
         * <blockquote>
         * <UL>
         *     <LI>an empty string to deny all access to external references;</LI>
         *     <LI>a specific protocol, such as file, to give permission to only the protocol;</LI>
         *     <LI>the keyword "all" to grant  permission to all protocols.</LI>
         *</UL><br>
         *      When FEATURE_SECURE_PROCESSING is enabled,  it is recommended that implementations
         *      restrict external connections by default, though this may cause problems for applications
         *      that process XML/XSD/XSL with external references.
         * </blockquote>
         * </p>
         *
         * <p>
         * <b>Granting all access:</b>  the keyword "all" grants permission to all protocols.
         * </p>
         * <p>
         * <b>System Property:</b> The value of this property can be set or overridden by
         * system property {@code javax.xml.accessExternalDTD}.
         * </p>
         *
         * <p>
         * <b>${JAVA_HOME}/lib/jaxp.properties:</b> This configuration file is in standard
         * {@link java.util.Properties} format. If the file exists and the system property is specified,
         * its value will be used to override the default of the property.
         * </p>
         *
         * <p>
         *
         * </p>
         * @since 1.7
         */
        public static final String ACCESS_EXTERNAL_DTD = "http://javax.xml.XMLConstants/property/accessExternalDTD";

        /**
         * <p>Property: accessExternalSchema</p>
         *
         * <p>
         * Restrict access to the protocols specified for external reference set by the
         * schemaLocation attribute, Import and Include element. If access is denied
         * due to the restriction of this property, a runtime exception that is specific
         * to the context is thrown. In the case of {@link javax.xml.validation.SchemaFactory}
         * for example, org.xml.sax.SAXException is thrown.
         * </p>
         * <p>
         * <b>Value:</b> a list of protocols separated by comma. A protocol is the scheme portion of a
         * {@link java.net.URI}, or in the case of the JAR protocol, "jar" plus the scheme portion
         * separated by colon.
         * A scheme is defined as:
         *
         * <blockquote>
         * scheme = alpha *( alpha | digit | "+" | "-" | "." )<br>
         * where alpha = a-z and A-Z.<br><br>
         *
         * And the JAR protocol:<br>
         *
         * jar[:scheme]<br><br>
         *
         * Protocols including the keyword "jar" are case-insensitive. Any whitespaces as defined by
         * {@link java.lang.Character#isSpaceChar } in the value will be ignored.
         * Examples of protocols are file, http, jar:file.
         *
         * </blockquote>
         *</p>
         *
         *<p>
         * <b>Default value:</b> The default value is implementation specific and therefore not specified.
         * The following options are provided for consideration:
         * <blockquote>
         * <UL>
         *     <LI>an empty string to deny all access to external references;</LI>
         *     <LI>a specific protocol, such as file, to give permission to only the protocol;</LI>
         *     <LI>the keyword "all" to grant  permission to all protocols.</LI>
         *</UL><br>
         *      When FEATURE_SECURE_PROCESSING is enabled,  it is recommended that implementations
         *      restrict external connections by default, though this may cause problems for applications
         *      that process XML/XSD/XSL with external references.
         * </blockquote>
         * </p>
         * <p>
         * <b>Granting all access:</b>  the keyword "all" grants permission to all protocols.
         * </p>
         *
         * <p>
         * <b>System Property:</b> The value of this property can be set or overridden by
         * system property {@code javax.xml.accessExternalSchema}
         * </p>
         *
         * <p>
         * <b>${JAVA_HOME}/lib/jaxp.properties:</b> This configuration file is in standard
         * java.util.Properties format. If the file exists and the system property is specified,
         * its value will be used to override the default of the property.
         *
         * @since 1.7
         * </p>
         */
        public static final String ACCESS_EXTERNAL_SCHEMA = "http://javax.xml.XMLConstants/property/accessExternalSchema";

        /**
         * <p>Property: accessExternalStylesheet</p>
         *
         * <p>
         * Restrict access to the protocols specified for external references set by the
         * stylesheet processing instruction, Import and Include element, and document function.
         * If access is denied due to the restriction of this property, a runtime exception
         * that is specific to the context is thrown. In the case of constructing new
         * {@link javax.xml.transform.Transformer} for example,
         * {@link javax.xml.transform.TransformerConfigurationException}
         * will be thrown by the {@link javax.xml.transform.TransformerFactory}.
         * </p>
         * <p>
         * <b>Value:</b> a list of protocols separated by comma. A protocol is the scheme portion of a
         * {@link java.net.URI}, or in the case of the JAR protocol, "jar" plus the scheme portion
         * separated by colon.
         * A scheme is defined as:
         *
         * <blockquote>
         * scheme = alpha *( alpha | digit | "+" | "-" | "." )<br>
         * where alpha = a-z and A-Z.<br><br>
         *
         * And the JAR protocol:<br>
         *
         * jar[:scheme]<br><br>
         *
         * Protocols including the keyword "jar" are case-insensitive. Any whitespaces as defined by
         * {@link java.lang.Character#isSpaceChar } in the value will be ignored.
         * Examples of protocols are file, http, jar:file.
         *
         * </blockquote>
         *</p>
         *
         *<p>
         * <b>Default value:</b> The default value is implementation specific and therefore not specified.
         * The following options are provided for consideration:
         * <blockquote>
         * <UL>
         *     <LI>an empty string to deny all access to external references;</LI>
         *     <LI>a specific protocol, such as file, to give permission to only the protocol;</LI>
         *     <LI>the keyword "all" to grant  permission to all protocols.</LI>
         *</UL><br>
         *      When FEATURE_SECURE_PROCESSING is enabled,  it is recommended that implementations
         *      restrict external connections by default, though this may cause problems for applications
         *      that process XML/XSD/XSL with external references.
         * </blockquote>
         * </p>
         * <p>
         * <b>Granting all access:</b>  the keyword "all" grants permission to all protocols.
         * </p>
         *
         * <p>
         * <b>System Property:</b> The value of this property can be set or overridden by
         * system property {@code javax.xml.accessExternalStylesheet}
         * </p>
         *
         * <p>
         * <b>${JAVA_HOME}/lib/jaxp.properties: </b> This configuration file is in standard
         * java.util.Properties format. If the file exists and the system property is specified,
         * its value will be used to override the default of the property.
         *
         * @since 1.7
         */
        public static final String ACCESS_EXTERNAL_STYLESHEET = "http://javax.xml.XMLConstants/property/accessExternalStylesheet";

}
