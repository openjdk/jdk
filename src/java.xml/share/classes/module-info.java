/*
 * Copyright (c) 2014, 2021, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Defines the Java API for XML Processing (JAXP), the Streaming API for XML (StAX),
 * the Simple API for XML (SAX), and the W3C Document Object Model (DOM) API.
 *
 * @implNote
 * <h2>Implementation Specific Features and Properties</h2>
 *
 * In addition to the standard features and properties described within the public
 * APIs of this module, the JDK implementation supports a further number of
 * implementation specific features and properties. This section describes the
 * naming convention, System Properties, jaxp.properties, scope and order,
 * and processors to which a property applies. A table listing the implementation
 * specific features and properties which the implementation currently supports
 * can be found at the end of this note.
 *
 * <h3>Naming Convention</h3>
 * The names of the features and properties are fully qualified, composed of a
 * prefix and name.
 * <p>
 * <h4>Prefix</h4>
 * The prefix for JDK properties is defined as:
 * <pre>
 *     {@code http://www.oracle.com/xml/jaxp/properties/}
 * </pre>
 *
 * The prefix for features:
 * <pre>
 *     {@code http://www.oracle.com/xml/jaxp/features/}
 * </pre>
 *
 * The prefix for System Properties:
 * <pre>
 *     {@code jdk.xml.}
 * </pre>
 * <p>
 * <h4>Name</h4>
 * A name may consist of one or multiple words that are case-sensitive.
 * All letters of the first word are in lowercase, while the first letter of
 * each subsequent word is capitalized.
 * <p>
 * An example of a property that indicates whether an XML document is standalone
 * would thus have a format:
 * <pre>
 *     {@code http://www.oracle.com/xml/jaxp/properties/isStandalone}
 * </pre>
 * and a corresponding System Property:
 * <pre>
 *     {@systemProperty jdk.xml.isStandalone}
 * </pre>
 *
 * <h3>System Properties</h3>
 * A property may have a corresponding System Property that has the same name
 * except for the prefix as shown above. A System Property should be set prior
 * to the creation of a processor and may be cleared afterwards.
 *
 * <h3>jaxp.properties</h3>
 * A system property can be specified in the {@code jaxp.properties} file to
 * set the behavior for all invocations of the JDK. The format is
 * {@code system-property-name=value}. For example:
 * <pre>
 *     {@code jdk.xml.isStandalone=true}
 * </pre>
 * <p>
 * For details about the JAXP configuration file {@code jaxp.properties}, refer to
 * {@link javax.xml.parsers.SAXParserFactory#newInstance() SAXParserFactory#newInstance}.
 *
 * <h3 id="ScopeAndOrder">Scope and Order</h3>
 * The {@link javax.xml.XMLConstants#FEATURE_SECURE_PROCESSING} feature
 * (hereafter referred to as secure processing) is required for XML processors
 * including DOM, SAX, Schema Validation, XSLT, and XPath. Any properties flagged
 * as {@code "security: yes"} (hereafter referred to as security properties) in
 * table <a href="#FeaturesAndProperties">Features And Properties</a>
 * are enforced when secure processing is set to true. Such enforcement includes
 * setting security features to true and limits to the defined values shown in
 * the table. The property values will not be affected, however, when setting
 * secure processing to false.
 * <p>
 * When the Java Security Manager is present, secure processing is set to true
 * and can not be turned off. The security properties are therefore enforced.
 * <p>
 * Properties specified in the jaxp.properties file affect all invocations of
 * the JDK, and will override their default values, or those that may have been
 * set by secure processing.
 * <p>
 * System properties, when set, affect the invocation of the JDK and override
 * the default settings or those that may have been set in jaxp.properties or
 * by secure processing.
 * <p>
 * JAXP properties specified through JAXP factories or processors (e.g. SAXParser)
 * take preference over system properties, the jaxp.properties file, as well as
 * secure processing.
 * <p>
 *
 * <h3 id="Processor">Processor Support</h3>
 * Features and properties may be supported by one or more processors. The
 * following table lists the processors by IDs that can be used for reference.
 * <p>
 *
 * <table class="plain" id="Processors">
 * <caption>Processors</caption>
 * <thead>
 * <tr>
 * <th scope="col">ID</th>
 * <th scope="col">Name</th>
 * <th scope="col">How to set the property</th>
 * </tr>
 * </thead>
 *
 * <tbody>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="DOM">DOM</th>
 * <td>DOM Parser</td>
 * <td>
 * {@code DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();}<br>
 * {@code dbf.setAttribute(name, value);}
 * </td>
 * </tr>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="SAX">SAX</th>
 * <td>SAX Parser</td>
 * <td>
 * {@code SAXParserFactory spf = SAXParserFactory.newInstance();}<br>
 * {@code SAXParser parser = spf.newSAXParser();}<br>
 * {@code parser.setProperty(name, value);}
 * </td>
 * </tr>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="StAX">StAX</th>
 * <td>StAX Parser</td>
 * <td>
 * {@code XMLInputFactory xif = XMLInputFactory.newInstance();}<br>
 * {@code xif.setProperty(name, value);}
 * </td>
 * </tr>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="Validation">Validation</th>
 * <td>XML Validation API</td>
 * <td>
 * {@code SchemaFactory schemaFactory = SchemaFactory.newInstance(schemaLanguage);}<br>
 * {@code schemaFactory.setProperty(name, value);}
 * </td>
 * </tr>
 * </tr>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="Transform">Transform</th>
 * <td>XML Transform API</td>
 * <td>
 * {@code TransformerFactory factory = TransformerFactory.newInstance();}<br>
 * {@code factory.setAttribute(name, value);}
 * </td>
 * </tr>
 * </tr>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="DOMLS">DOMLS</th>
 * <td>DOM Load and Save</td>
 * <td>
 * {@code LSSerializer serializer = domImplementation.createLSSerializer();} <br>
 * {@code serializer.getDomConfig().setParameter(name, value);}
 * </td>
 * </tr>
 * </tbody>
 * </table>
 *
 * <p>
 * <h3>Implementation Specific Features and Properties</h3>
 * This section lists features and properties supported by the JDK implementation.
 *
 * <p>
 * <table class="plain" id="FeaturesAndProperties">
 * <caption>Features and Properties</caption>
 * <thead>
 * <tr>
 * <th scope="col" rowspan="2">Name [1]</th>
 * <th scope="col" rowspan="2">Description</th>
 * <th scope="col" rowspan="2">System Property [2]</th>
 * <th scope="col" rowspan="2">jaxp.properties [2]</th>
 * <th scope="col" colspan="3" style="text-align:center">Value [3]</th>
 * <th scope="col" rowspan="2">Security [4]</th>
 * <th scope="col" rowspan="2">Supported Processor [5]</th>
 * <th scope="col" rowspan="2">Since [6]</th>
 * </tr>
 * <tr>
 * <th scope="col">Type</th>
 * <th scope="col">Value</th>
 * <th scope="col">Default</th>
 * </tr>
 * </thead>
 *
 * <tbody>
 *
 * <tr>
 * <th scope="row" style="font-weight:normal" id="ISSTANDALONE">isStandalone</th>
 * <td>indicates that the serializer should treat the output as a
 * standalone document. The property can be used to ensure a newline is written
 * after the XML declaration. Unlike the property
 * {@link org.w3c.dom.ls.LSSerializer#getDomConfig() xml-declaration}, this property
 * does not have an effect on whether an XML declaration should be written out.
 * </td>
 * <td>yes</td>
 * <td>yes</td>
 * <td>boolean</td>
 * <th id="Value" scope="row" style="font-weight:normal">true/false</th>
 * <th id="Default" scope="row" style="font-weight:normal">false</th>
 * <td>No</td>
 * <td><a href="#DOMLS">DOMLS</a></td>
 * <td>17</td>
 * </tr>
 * </tbody>
 * </table>
 * <p>
 * <b>[1]</b> The name of a property. The fully-qualified name, prefix + name,
 * should be used when setting the property.
 * <p>
 * <b>[2]</b> A value "yes" indicates there is a corresponding System Property
 * for the property, "no" otherwise.
 *
 * <p>
 * <b>[3]</b> The value must be exactly as listed in this table, case-sensitive.
 * The value type for the corresponding System Property is String. For boolean
 * type, the system property is true only if it is "true" and false otherwise.
 * <p>
 * <b>[4]</b> A value "yes" indicates the property is a Security Property. Refer
 * to the <a href="#ScopeAndOrder">Scope and Order</a> on how secure processing
 * may affect the value of a Security Property.
 * <p>
 * <b>[5]</b> One or more processors that support the property. The values of the
 * field are IDs described in table <a href="#Processor">Processors</a>.
 * <p>
 * <b>[6]</b> Indicates the initial release the property is introduced.
 *
 *
 *
 * @uses javax.xml.datatype.DatatypeFactory
 * @uses javax.xml.parsers.DocumentBuilderFactory
 * @uses javax.xml.parsers.SAXParserFactory
 * @uses javax.xml.stream.XMLEventFactory
 * @uses javax.xml.stream.XMLInputFactory
 * @uses javax.xml.stream.XMLOutputFactory
 * @uses javax.xml.transform.TransformerFactory
 * @uses javax.xml.validation.SchemaFactory
 * @uses javax.xml.xpath.XPathFactory
 * @uses org.xml.sax.XMLReader
 *
 * @moduleGraph
 * @since 9
 */
module java.xml {
    exports javax.xml;
    exports javax.xml.catalog;
    exports javax.xml.datatype;
    exports javax.xml.namespace;
    exports javax.xml.parsers;
    exports javax.xml.stream;
    exports javax.xml.stream.events;
    exports javax.xml.stream.util;
    exports javax.xml.transform;
    exports javax.xml.transform.dom;
    exports javax.xml.transform.sax;
    exports javax.xml.transform.stax;
    exports javax.xml.transform.stream;
    exports javax.xml.validation;
    exports javax.xml.xpath;
    exports org.w3c.dom;
    exports org.w3c.dom.bootstrap;
    exports org.w3c.dom.events;
    exports org.w3c.dom.ls;
    exports org.w3c.dom.ranges;
    exports org.w3c.dom.traversal;
    exports org.w3c.dom.views;
    exports org.xml.sax;
    exports org.xml.sax.ext;
    exports org.xml.sax.helpers;

    exports com.sun.org.apache.xml.internal.dtm to
        java.xml.crypto;
    exports com.sun.org.apache.xml.internal.utils to
        java.xml.crypto;
    exports com.sun.org.apache.xpath.internal to
        java.xml.crypto;
    exports com.sun.org.apache.xpath.internal.compiler to
        java.xml.crypto;
    exports com.sun.org.apache.xpath.internal.functions to
        java.xml.crypto;
    exports com.sun.org.apache.xpath.internal.objects to
        java.xml.crypto;
    exports com.sun.org.apache.xpath.internal.res to
        java.xml.crypto;

    uses javax.xml.datatype.DatatypeFactory;
    uses javax.xml.parsers.DocumentBuilderFactory;
    uses javax.xml.parsers.SAXParserFactory;
    uses javax.xml.stream.XMLEventFactory;
    uses javax.xml.stream.XMLInputFactory;
    uses javax.xml.stream.XMLOutputFactory;
    uses javax.xml.transform.TransformerFactory;
    uses javax.xml.validation.SchemaFactory;
    uses javax.xml.xpath.XPathFactory;
    uses org.xml.sax.XMLReader;
}
