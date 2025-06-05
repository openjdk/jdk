/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * Defines the Java APIs for XML Processing (JAXP).
 *
 * <ul>
 * <li><a href="#JAXP">The JAXP APIs</a></li>
 * <li><a href="#FacPro">Factories and Processors</a></li>
 * <li><a href="#Conf">Configuration</a>
 *     <ul>
 *     <li><a href="#Conf_Properties">JAXP Properties</a></li>
 *     <li><a href="#Conf_SystemProperties">System Properties</a></li>
 *     <li><a href="#Conf_CF">Configuration File</a>
 *         <ul>
 *         <li><a href="#Conf_CF_Default">{@code jaxp.properties} File</a></li>
 *         <li><a href="#Conf_CF_SP">User-defined Configuration File</a></li>
 *         </ul>
 *     </li>
 *     <li><a href="#Conf_PP">Property Precedence</a></li>
 *     </ul>
 * </li>
 * <li><a href="#LookupMechanism">JAXP Lookup Mechanism</a>
 *      <ul>
 *      <li><a href="#LookupProcedure">Lookup Procedure</a></li>
 *      </ul>
 * </li>
 * <li><a href="#implNote">Implementation Note</a></li>
 * </ul>
 *
 * <h2 id="JAXP">The JAXP APIs</h2>
 * JAXP comprises a set of APIs built upon a number of XML technologies and
 * standards that are essential for XML processing. These include APIs for:
 *
 * <ul>
 * <li>Parsing: the {@link javax.xml.parsers JAXP Parsing API} based on
 * {@link org.w3c.dom Document Object Model (DOM)} and
 * {@link org.xml.sax Simple API for XML Parsing (SAX)}, and
 * {@link javax.xml.stream Streaming API for XML (StAX)};
 * </li>
 * <li>Serializing: StAX and
 * {@link javax.xml.transform Extensible Stylesheet Language Transformations (XSLT)};
 * </li>
 * <li>Validation: the {@link javax.xml.validation JAXP Validation API}
 * based on the XML Schema Definition Language;</li>
 * <li>Transformation: the {@link javax.xml.transform JAXP Transformation API}
 * or XSLT (Extensible Stylesheet Language Transformations);</li>
 * <li>Querying and traversing XML documents: the
 * {@link javax.xml.xpath XML XPath Language API (XPath)};</li>
 * <li>Resolving external resources: the {@link javax.xml.catalog XML Catalog API};</li>
 * </ul>
 *
 * <h2 id="FacPro">Factories and Processors</h2>
 * Factories are the entry points of each API, providing methods to allow applications
 * to set <a href="#Conf_Properties">JAXP Properties</a> programmatically, before
 * creating processors. The <a href="#Conf">Configuration</a> section provides more
 * details on this. Factories also support the
 * <a href="#LookupMechanism">JAXP Lookup Mechanism</a>, in which applications can be
 * deployed with third party implementations to use instead of JDK implementations
 * <p>
 *
 * Processors are aggregates of parsers (or readers), serializers (or writers),
 * validators, and transformers that control and perform the processing in their
 * respective areas. They are defined in their relevant packages.
 * In the {@link javax.xml.parsers parsers} package for example,
 * are the {@link javax.xml.parsers.DocumentBuilder DocumentBuilder} and
 * {@link javax.xml.parsers.SAXParser SAXParser}, that represent the DOM and
 * SAX processors.
 * <p>
 * The processors are configured and instantiated with their corresponding factories.
 * The DocumentBuilder and SAXParser for example are constructed with the
 * {@link javax.xml.parsers.DocumentBuilderFactory DocumentBuilderFactory}
 * and {@link javax.xml.parsers.SAXParserFactory SAXParserFactory} respectively.
 *
 * <h2 id="Conf">Configuration</h2>
 * When a JAXP factory is invoked for the first time, it performs a configuration
 * process to determine the implementation to be used and its subsequent behaviors.
 * During configuration, the factory examines configuration sources such as the
 * <a href="#Conf_Properties">JAXP Properties</a>,
 * <a href="#Conf_SystemProperties">System Properties</a>,
 * and the <a href="#Conf_CF">JAXP Configuration File</a>, and sets the values
 * following the <a href="#Conf_PP">Property Precedence</a>. The terminologies and
 * process are defined below.
 *
 * <h3 id="Conf_Properties">JAXP Properties</h3>
 * JAXP properties are configuration settings that are applied to XML processors.
 * They can be used to control and customize the behavior of a processor.
 * Depending on the JAXP API that is being used, JAXP properties may be referred
 * to as <em>Attributes, Properties</em>, or <em>Features</em>.
 *
 * <h3 id="Conf_SystemProperties">System Properties</h3>
 * Select JAXP properties have corresponding System Properties allowing the properties
 * to be set at runtime, on the command line, or within the
 * <a href="#Conf_CF">JAXP Configuration File</a>.
 * For example, the System Property {@code javax.xml.catalog.resolve} may be used
 * to set the {@link javax.xml.catalog.CatalogFeatures CatalogFeatures}' RESOLVE
 * property.
 * <p>
 * The exact time at which system properties are read is unspecified. In order to
 * ensure that the desired values are properly applied, applications should ensure
 * that system properties are set appropriately prior to the creation of the first
 * JAXP factory and are not modified thereafter.
 *
 * <h3 id="Conf_CF">Configuration File</h3>
 * JAXP supports the use of configuration files for
 * <a href="#LookupMechanism">specifying the implementation class to load for the JAXP factories</a>
 * as well as for setting JAXP properties.
 * <p>
 * Configuration files are Java {@link java.util.Properties} files that consist
 * of mappings between system properties and their values defined by various APIs
 * or processes. The following configuration file entries demonstrate setting the
 * {@code javax.xml.parsers.DocumentBuilderFactory}
 * and {@code CatalogFeatures.RESOLVE} properties:
 *
 * {@snippet :
 *    javax.xml.parsers.DocumentBuilderFactory=packagename.DocumentBuilderFactoryImpl
 *    javax.xml.catalog.resolve=strict
 * }
 *
 * <h4 id="Conf_CF_Default">{@code jaxp.properties} File</h4>
 * By default, JAXP looks for the configuration file {@code jaxp.properties},
 * located in the ${java.home}/conf directory; and if the file exists, loads the
 * specified properties to customize the behavior of the XML factories and processors.
 * <p>
 * The {@code jaxp.properties} file will be read only once during the initialization
 * of the JAXP implementation and cached in memory. If there is an error accessing
 * or reading the file, the configuration process proceeds as if the file does not exist.
 *
 * <h4 id="Conf_CF_SP">User-defined Configuration File</h4>
 * In addition to the {@code jaxp.properties} file, the system property
 * {@systemProperty java.xml.config.file} can be set to specify the location of
 * a configuration file. If the {@code java.xml.config.file} property is included
 * within a configuration file, it will be ignored.
 *
 * <p>
 * When the {@code java.xml.config.file} is specified, the configuration file will be
 * read and the included properties will override the same properties that were
 * defined in the {@code jaxp.properties} file. If the {@code java.xml.config.file}
 * has not been set when the JAXP implementation is initialized, no further attempt
 * will be made to check for its existence.
 * <p>
 * The {@code java.xml.config.file} value must contain a valid pathname
 * to a configuration file. If the pathname is not absolute, it will be considered
 * relative to the working directory of the JVM.
 * If there is an error reading the configuration file, the configuration process
 * proceeds as if the {@code java.xml.config.file} property was not set.
 * Implementations may optionally issue a warning message.
 *
 * <h3 id="Conf_PP">Property Precedence</h3>
 * JAXP properties can be set in multiple ways, including by API methods, system
 * properties, and the <a href="#Conf_CF">JAXP Configuration File</a>. When not
 * explicitly set, they will be initialized with default values or more restrictive
 * values when
 * {@link javax.xml.XMLConstants#FEATURE_SECURE_PROCESSING FEATURE_SECURE_PROCESSING}
 * (FSP) is enabled. The configuration order of precedence for properties is as
 * follows, from highest to lowest:
 *
 * <ul>
 * <li><p>
 *      The APIs for factories or processors
 * </li>
 * <li><p>
 *      System Property
 * </li>
 * <li><p>
 *      User-defined <a href="#Conf_CF">Configuration File</a>
 * </li>
 * <li><p>
 *      The default JAXP Configuration File <a href="#Conf_CF_Default">{@code jaxp.properties}</a>
 * </li>
 * <li><p>
 *      The default values for JAXP Properties. If the
 * {@link javax.xml.XMLConstants#FEATURE_SECURE_PROCESSING FSP} is true,
 * the default values will be set to process XML securely.
 * </li>
 * </ul>
 *
 * Using the {@link javax.xml.catalog.CatalogFeatures CatalogFeatures}' RESOLVE
 * property as an example, the following illustrates how these rules are applied:
 * <ul>
 * <li><p>
 *      Properties specified with factory or processor APIs have the highest
 * precedence. The following code effectively sets the RESOLVE property to
 * {@code strict}, regardless of settings in any other configuration sources.
 *
 * {@snippet :
 *    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
 *    dbf.setAttribute(CatalogFeatures.Feature.RESOLVE.getPropertyName(), "strict");
 * }
 *
 * </li>
 * <li><p>
 *      If the property is not set on the factory as in the above code, a
 * system property setting will be in effect.
 * {@snippet :
 *     // in the following example, the RESOLVE property is set to 'continue'
 *     // for the entire application
 *     java -Djavax.xml.catalog.resolve=continue myApp
 * }
 * </li>
 * <li><p>
 *      If the property is not set on the factory, or using a system property,
 * the setting in a configuration file will take effect. The following entry
 * sets the property to '{@code continue}'.
 * {@snippet :
 *     javax.xml.catalog.resolve=continue
 * }
 *
 * </li>
 * <li><p>
 *     If the property is not set anywhere, it will be resolved to its
 * default value that is '{@code strict}'.
 * </li>
 * </ul>
 *
 * <h2 id="LookupMechanism">JAXP Lookup Mechanism</h2>
 * JAXP defines an ordered lookup procedure to determine the implementation class
 * to load for the JAXP factories. Factories that support the mechanism are listed
 * in the table below along with the method, System Property, and System
 * Default method to be used in the procedure.
 *
 * <table class="plain" id="Factories">
 * <caption>JAXP Factories</caption>
 * <thead>
 * <tr>
 * <th scope="col">Factory</th>
 * <th scope="col">Method</th>
 * <th scope="col">System Property</th>
 * <th scope="col">System Default</th>
 * </tr>
 * </thead>
 *
 * <tbody>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="DATA">
 *     {@link javax.xml.datatype.DatatypeFactory DatatypeFactory}
 * </th>
 * <td style="text-align:center">{@link javax.xml.datatype.DatatypeFactory#newInstance() newInstance()}</td>
 * <td style="text-align:center">{@code javax.xml.datatype.DatatypeFactory}</td>
 * <td style="text-align:center">{@link javax.xml.datatype.DatatypeFactory#newDefaultInstance() newDefaultInstance()}</td>
 * </tr>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="DocumentBuilderFactory">
 *     {@link javax.xml.parsers.DocumentBuilderFactory DocumentBuilderFactory}
 * </th>
 * <td style="text-align:center">{@link javax.xml.parsers.DocumentBuilderFactory#newInstance() newInstance()}</td>
 * <td style="text-align:center">{@code javax.xml.parsers.DocumentBuilderFactory}</td>
 * <td style="text-align:center">{@link javax.xml.parsers.DocumentBuilderFactory#newDefaultInstance() newDefaultInstance()}</td>
 * </tr>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="SAXParserFactory">
 *     {@link javax.xml.parsers.SAXParserFactory SAXParserFactory}
 * </th>
 * <td style="text-align:center">{@link javax.xml.parsers.SAXParserFactory#newInstance() newInstance()}</td>
 * <td style="text-align:center">{@code javax.xml.parsers.SAXParserFactory}</td>
 * <td style="text-align:center">{@link javax.xml.parsers.SAXParserFactory#newDefaultInstance() newDefaultInstance()}</td>
 * </tr>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="StAXEvent">
 *     {@link javax.xml.stream.XMLEventFactory XMLEventFactory}
 * </th>
 * <td style="text-align:center">{@link javax.xml.stream.XMLEventFactory#newFactory() newFactory()}</td>
 * <td style="text-align:center">{@code javax.xml.stream.XMLEventFactory}</td>
 * <td style="text-align:center">{@link javax.xml.stream.XMLEventFactory#newDefaultFactory() newDefaultFactory()}</td>
 * </tr>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="StAXInput">
 *     {@link javax.xml.stream.XMLInputFactory XMLInputFactory}
 * </th>
 * <td style="text-align:center">{@link javax.xml.stream.XMLInputFactory#newFactory() newFactory()}</td>
 * <td style="text-align:center">{@code javax.xml.stream.XMLInputFactory}</td>
 * <td style="text-align:center">{@link javax.xml.stream.XMLInputFactory#newDefaultFactory() newDefaultFactory()}</td>
 * </tr>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="StAXOutput">
 *     {@link javax.xml.stream.XMLOutputFactory XMLOutputFactory}
 * </th>
 * <td style="text-align:center">{@link javax.xml.stream.XMLOutputFactory#newFactory() newFactory()}</td>
 * <td style="text-align:center">{@code javax.xml.stream.XMLOutputFactory}</td>
 * <td style="text-align:center">{@link javax.xml.stream.XMLOutputFactory#newDefaultFactory() newDefaultFactory()}</td>
 * </tr>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="XSLT">
 *     {@link javax.xml.transform.TransformerFactory TransformerFactory}
 * </th>
 * <td style="text-align:center">{@link javax.xml.transform.TransformerFactory#newInstance() newInstance()}</td>
 * <td style="text-align:center">{@code javax.xml.transform.TransformerFactory}</td>
 * <td style="text-align:center">{@link javax.xml.transform.TransformerFactory#newDefaultInstance() newDefaultInstance()}</td>
 * </tr>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="SchemaFactory">
 *     {@link javax.xml.validation.SchemaFactory SchemaFactory}
 * </th>
 * <td style="text-align:center">{@link javax.xml.validation.SchemaFactory#newInstance(java.lang.String) newInstance(schemaLanguage)}</td>
 * <td style="text-align:center">{@code javax.xml.validation.SchemaFactory:}<i>schemaLanguage</i>[1]</td>
 * <td style="text-align:center">{@link javax.xml.validation.SchemaFactory#newDefaultInstance() newDefaultInstance()}</td>
 * </tr>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="XPathFactory">
 *     {@link javax.xml.xpath.XPathFactory XPathFactory}
 * </th>
 * <td style="text-align:center">{@link javax.xml.xpath.XPathFactory#newInstance(java.lang.String) newInstance(uri)}</td>
 * <td style="text-align:center">{@link javax.xml.xpath.XPathFactory#DEFAULT_PROPERTY_NAME DEFAULT_PROPERTY_NAME} + ":uri"[2]</td>
 * <td style="text-align:center">{@link javax.xml.xpath.XPathFactory#newDefaultInstance() newDefaultInstance()}</td>
 * </tr>
 * </tbody>
 * </table>
 *
 * <b>[1]</b> where <i>schemaLanguage</i> is the parameter to the
 * {@link javax.xml.validation.SchemaFactory#newInstance(java.lang.String) newInstance(schemaLanguage)}
 * method.
 * <p>
 * <b>[2]</b> where <i>uri</i> is the parameter to the
 * {@link javax.xml.xpath.XPathFactory#newInstance(java.lang.String) newInstance(uri)}
 * method.
 *
 * <h3 id="LookupProcedure">Lookup Procedure</h3>
 * The order of precedence for locating the implementation class of a
 * <a href="#Factories">JAXP Factory</a> is as follows, from highest to lowest:
 * <ul>
 * <li>
 * The system property as listed in the column System Property of the table
 * <a href="#Factories">JAXP Factories</a> above
 * </li>
 * <li>
 * <p>
 * The <a href="#Conf_CF">Configuration File</a>
 * </li>
 * <li>
 * <p>
 * The service-provider loading facility, defined by the
 * {@link java.util.ServiceLoader} class, to attempt to locate and load an
 * implementation of the service using the {@linkplain
 * java.util.ServiceLoader#load(java.lang.Class) default loading mechanism}:
 * the service-provider loading facility will use the {@linkplain
 * java.lang.Thread#getContextClassLoader() current thread's context class loader}
 * to attempt to load the service. If the context class
 * loader is null, the {@linkplain
 * ClassLoader#getSystemClassLoader() system class loader} will be used.
 *
 * <h3>{@link javax.xml.validation.SchemaFactory SchemaFactory}</h3>
 * In case of the {@link javax.xml.validation.SchemaFactory SchemaFactory},
 * each potential service provider is required to implement the method
 * {@link javax.xml.validation.SchemaFactory#isSchemaLanguageSupported(java.lang.String)
 * isSchemaLanguageSupported(String schemaLanguage)}.
 * The first service provider found that supports the specified schema language
 * is returned.
 *
 * <h3>{@link javax.xml.xpath.XPathFactory XPathFactory}</h3>
 * In case of the {@link javax.xml.xpath.XPathFactory XPathFactory},
 * each potential service provider is required to implement the method
 * {@link javax.xml.xpath.XPathFactory#isObjectModelSupported(String objectModel)
 * isObjectModelSupported(String objectModel)}.
 * The first service provider found that supports the specified object model is
 * returned.
 * </li>
 * <li>
 * <p>
 * Otherwise, the {@code system-default} implementation is returned, which is
 * equivalent to calling the {@code newDefaultInstance() or newDefaultFactory()}
 * method as shown in column System Default of the table
 * <a href="#Factories">JAXP Factories</a> above.
 *
 * <h3>{@link javax.xml.validation.SchemaFactory SchemaFactory}</h3>
 * In case of the {@link javax.xml.validation.SchemaFactory SchemaFactory},
 * there must be a {@linkplain javax.xml.validation.SchemaFactory#newDefaultInstance()
 * platform default} {@code SchemaFactory} for W3C XML Schema.
 *
 * <h3>{@link javax.xml.xpath.XPathFactory XPathFactory}</h3>
 * In case of the {@link javax.xml.xpath.XPathFactory XPathFactory},
 * there must be a
 * {@linkplain javax.xml.xpath.XPathFactory#newDefaultInstance() platform default}
 * {@code XPathFactory} for the W3C DOM, i.e.
 * {@link javax.xml.xpath.XPathFactory#DEFAULT_OBJECT_MODEL_URI DEFAULT_OBJECT_MODEL_URI}.
 * </li>
 * </ul>
 *
 *
 * <div id="implNote"></div>
 * @implNote
 *
 * <ul>
 * <li><a href="#JDKCATALOG">JDK built-in Catalog</a>
 *      <ul>
 *      <li><a href="#JC_PROCESS">External Resource Resolution Process with the built-in Catalog</a></li>
 *      </ul>
 * </li>
 * <li><a href="#IN_ISFP">Implementation Specific Properties</a>
 *      <ul>
 *      <li><a href="#Processor">Processor Support</a></li>
 *      <li><a href="#IN_ISFPtable">List of Implementation Specific Properties</a></li>
 *      <li><a href="#IN_Legacy">Legacy Property Names (deprecated)</a></li>
 *      </ul>
 * </li>
 * </ul>
 *
 * <h2 id="JDKCATALOG">JDK built-in Catalog</h2>
 * The JDK has a built-in catalog that hosts DTDs and XSDs list in the following table.
 * <table class="plain" id="JDKCatalog">
 * <caption>DTDs and XSDs in JDK built-in Catalog</caption>
 * <thead>
 * <tr>
 * <th scope="col">Source</th>
 * <th scope="col">Files</th>
 * </tr>
 * </thead>
 *
 * <tbody>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="util_preferences">
 * {@link java.util.prefs.Preferences java.util.prefs.Preferences}</th>
 * <td style="text-align:center">
 * preferences.dtd
 * </td>
 * </tr>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="util_properties">
 * {@link java.util.Properties java.util.Properties}</th>
 * <td style="text-align:center">
 * properties.dtd
 * </td>
 * </tr>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="XMLSchema">
 * XML Schema Part 1: Structures Second Edition<br>
 * XML Schema Part 2: Datatypes Second Edition
 * </th>
 * <td style="text-align:center">
 * XMLSchema.dtd<br>
 * datatypes.dtd<br>
 * XMLSchema.xsd<br>
 * datatypes.xsd
 * </td>
 * </tr>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="XHTML10">
 * XHTML&trade; 1.0 The Extensible HyperText Markup Language
 * </th>
 * <td style="text-align:center">
 * xhtml1-frameset.dtd<br>
 * xhtml1-strict.dtd<br>
 * xhtml1-transitional.dtd
 * </td>
 * </tr>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="XHTML10Schema">
 * XHTML&trade; 1.0 in XML Schema
 * </th>
 * <td style="text-align:center">
 * xhtml1-frameset.xsd<br>
 * xhtml1-strict.xsd<br>
 * xhtml1-transitional.xsd
 * </td>
 * </tr>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="XHTML11">
 * XHTML&trade; 1.1 - Module-based XHTML - Second Edition
 * </th>
 * <td style="text-align:center">
 * xhtml11.dtd
 * </td>
 * </tr>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="XHTML11Schema">
 * XHTML 1.1 XML Schema Definition
 * </th>
 * <td style="text-align:center">
 * xhtml11.xsd
 * </td>
 * </tr>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="XMLSPEC">
 * XML DTD for W3C specifications
 * </th>
 * <td style="text-align:center">
 * xmlspec.dtd
 * </td>
 * </tr>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="Namespace">
 * The "xml:" Namespace
 * </th>
 * <td style="text-align:center">
 * xml.xsd
 * </td>
 * </tr>
 * </tbody>
 * </table>
 * <p>
 * The catalog is loaded once when the first JAXP processor factory is created.
 *
 * <h3 id="JC_PROCESS">External Resource Resolution Process with the built-in Catalog</h3>
 * The JDK creates a {@link javax.xml.catalog.CatalogResolver CatalogResolver}
 * with the built-in catalog when needed. This CatalogResolver is used as the
 * default external resource resolver.
 * <p>
 * XML processors may use resolvers (such as {@link org.xml.sax.EntityResolver EntityResolver},
 * {@link javax.xml.stream.XMLResolver XMLResolver}, and {@link javax.xml.catalog.CatalogResolver CatalogResolver})
 * to handle external references. In the absence of the user-defined resolvers,
 * the JDK XML processors fall back to the default CatalogResolver to attempt to
 * find a resolution before making a connection to fetch the resources. The fall-back
 * also takes place if a user-defined resolver exists but allows the process to
 * continue when unable to resolve the resource.
 * <p>
 * If the default CatalogResolver is unable to locate a resource, it may signal
 * the XML processors to continue processing, or skip the resource, or
 * throw a CatalogException. The behavior is configured with the
 * <a href="#JDKCATALOG_RESOLVE">{@code jdk.xml.jdkcatalog.resolve}</a> property.
 *
 * <h2 id="IN_ISFP">Implementation Specific Properties</h2>
 * In addition to the standard <a href="#Conf_Properties">JAXP Properties</a>,
 * the JDK implementation supports a number of implementation specific properties
 * whose name is prefixed by "{@code jdk.xml.}". These properties also follow the
 * configuration process as defined in the <a href="#Conf">Configuration</a> section.
 * <p>
 * Refer to the <a href="#Properties">Implementation Specific Properties</a> table
 * for the list of properties supported by the JDK implementation.
 *
 * <h3 id="Processor">Processor Support</h3>
 * The properties may be supported by one or more processors as listed in the table
 * below. Depending on the type of the property, they may be set via
 * Method 1: setAttribute/Parameter/Property or 2: setFeature as illustrated
 * in the relevant columns.
 *
 * <table class="plain" id="Processors">
 * <caption>Processors</caption>
 * <thead>
 * <tr>
 * <th scope="col">ID</th>
 * <th scope="col">Name</th>
 * <th scope="col">Method 1: setAttribute/Parameter/Property</th>
 * <th scope="col">Method 2: setFeature</th>
 * </tr>
 * </thead>
 *
 * <tbody>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="DOM">DOM</th>
 * <td style="text-align:center">DOM Parser</td>
 * <td>
 * {@code DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();}<br>
 * {@code dbf.setAttribute(name, value);}
 * </td>
 * <td>
 * {@code DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();}<br>
 * {@code dbf.setFeature(name, value);}
 * </td>
 * </tr>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="SAX">SAX</th>
 * <td style="text-align:center">SAX Parser</td>
 * <td>
 * {@code SAXParserFactory spf = SAXParserFactory.newInstance();}<br>
 * {@code SAXParser parser = spf.newSAXParser();}<br>
 * {@code parser.setProperty(name, value);}
 * </td>
 * <td>
 * {@code SAXParserFactory spf = SAXParserFactory.newInstance();}<br>
 * {@code spf.setFeature(name, value);}<br>
 * </td>
 * </tr>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="StAX">StAX</th>
 * <td style="text-align:center">StAX Parser</td>
 * <td>
 * {@code XMLInputFactory xif = XMLInputFactory.newInstance();}<br>
 * {@code xif.setProperty(name, value);}
 * </td>
 * <td>
 * {@code XMLInputFactory xif = XMLInputFactory.newInstance();}<br>
 * {@code xif.setProperty(name, value);}
 * </td>
 * </tr>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="Validation">Validation</th>
 * <td style="text-align:center">XML Validation API</td>
 * <td>
 * {@code SchemaFactory schemaFactory = SchemaFactory.newInstance(schemaLanguage);}<br>
 * {@code schemaFactory.setProperty(name, value);}
 * </td>
 * <td>
 * {@code SchemaFactory schemaFactory = SchemaFactory.newInstance(schemaLanguage);}<br>
 * {@code schemaFactory.setFeature(name, value);}
 * </td>
 * </tr>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="Transform">Transform</th>
 * <td style="text-align:center">XML Transform API</td>
 * <td>
 * {@code TransformerFactory factory = TransformerFactory.newInstance();}<br>
 * {@code factory.setAttribute(name, value);}
 * </td>
 * <td>
 * {@code TransformerFactory factory = TransformerFactory.newInstance();}<br>
 * {@code factory.setFeature(name, value);}
 * </td>
 * </tr>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="XSLTCSerializer">XSLTC Serializer</th>
 * <td style="text-align:center">XSLTC Serializer</td>
 * <td>
 * {@code Transformer transformer = TransformerFactory.newInstance().newTransformer();}<br>
 * {@code transformer.setOutputProperty(name, value);}
 * </td>
 * <td>
 * </td>
 * </tr>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="DOMLS">DOMLS</th>
 * <td style="text-align:center">DOM Load and Save</td>
 * <td>
 * {@code LSSerializer serializer = domImplementation.createLSSerializer();} <br>
 * {@code serializer.getDomConfig().setParameter(name, value);}
 * </td>
 * <td>
 * </td>
 * </tr>
 * <tr>
 * <th scope="row" style="font-weight:normal" id="XPATH">XPath</th>
 * <td style="text-align:center">XPath</td>
 * <td>
 * {@code XPathFactory factory = XPathFactory.newInstance();}<br>
 * {@code factory.setProperty(name, value);}
 * </td>
 * <td>
 * {@code XPathFactory factory = XPathFactory.newInstance();} <br>
 * {@code factory.setFeature(name, value);}
 * </td>
 * </tr>
 * </tbody>
 * </table>
 *
 * <div id="IN_ISFPtable"></div>
 * <table class="striped" id="Properties">
 * <caption>Implementation Specific Properties</caption>
 * <thead>
 * <tr>
 * <th scope="col" rowspan="2">Full Name (prefix {@code jdk.xml.})
 * <a href="#Note1">[1]</a></th>
 * <th scope="col" rowspan="2">Description</th>
 * <th scope="col" rowspan="2">System Property <a href="#Note2">[2]</a></th>
 * <th scope="col" colspan="4" style="text-align:center">Value <a href="#Note3">[3]</a></th>
 * <th scope="col" rowspan="2">Security <a href="#Note4">[4]</a></th>
 * <th scope="col" colspan="2">Supported Processor <a href="#Note5">[5]</a></th>
 * <th scope="col" rowspan="2">Since <a href="#Note6">[6]</a></th>
 * </tr>
 * <tr>
 * <th scope="col">Type</th>
 * <th scope="col">Value</th>
 * <th scope="col">Default</th>
 * <th scope="col">Enforced</th>
 * <th scope="col">ID</th>
 * <th scope="col">Set Method</th>
 * </tr>
 * </thead>
 *
 * <tbody>
 *
 * <tr>
 * <td id="EELimit">{@systemProperty jdk.xml.entityExpansionLimit}</td>
 * <td>Limits the number of entity expansions.
 * </td>
 * <td style="text-align:center" rowspan="11">yes</td>
 * <td style="text-align:center" rowspan="9">Integer</td>
 * <td rowspan="9">
 * A positive integer. A value less than or equal to 0 indicates no limit.
 * If the value is not an integer, a NumberFormatException is thrown.
 * </td>
 * <td style="text-align:center">2500</td>
 * <td style="text-align:center">2500</td>
 * <td style="text-align:center" rowspan="9">Yes</td>
 * <td style="text-align:center" rowspan="9">
 *     <a href="#DOM">DOM</a><br>
 *     <a href="#SAX">SAX</a><br>
 *     <a href="#StAX">StAX</a><br>
 *     <a href="#Validation">Validation</a><br>
 *     <a href="#Transform">Transform</a>
 * </td>
 * <td style="text-align:center" rowspan="16"><a href="#Processor">Method 1</a></td>
 * <td style="text-align:center" rowspan="9">8</td>
 * </tr>
 * <tr>
 * <td id="EALimit">{@systemProperty jdk.xml.elementAttributeLimit}</td>
 * <td>Limits the number of attributes an element can have.
 * </td>
 * <td style="text-align:center">200</td>
 * <td style="text-align:center">200</td>
 * </tr>
 * <tr>
 * <td id="OccurLimit">{@systemProperty jdk.xml.maxOccurLimit}</td>
 * <td>Limits the number of content model nodes that may be created when building
 * a grammar for a W3C XML Schema that contains maxOccurs attributes with values
 * other than "unbounded".
 * </td>
 * <td style="text-align:center">5000</td>
 * <td style="text-align:center">5000</td>
 * </tr>
 * <tr>
 * <td id="SizeLimit">{@systemProperty jdk.xml.totalEntitySizeLimit}</td>
 * <td>Limits the total size of all entities that include general and parameter
 * entities. The size is calculated as an aggregation of all entities.
 * </td>
 * <td style="text-align:center">100000</td>
 * <td style="text-align:center">100000</td>
 * </tr>
 * <tr>
 * <td id="GELimit">{@systemProperty jdk.xml.maxGeneralEntitySizeLimit}</td>
 * <td>Limits the maximum size of any general entities.
 * </td>
 * <td style="text-align:center">100000</td>
 * <td style="text-align:center">100000</td>
 * </tr>
 * <tr>
 * <td id="PELimit">{@systemProperty jdk.xml.maxParameterEntitySizeLimit}</td>
 * <td>Limits the maximum size of any parameter entities, including the result
 * of nesting multiple parameter entities.
 * </td>
 * <td style="text-align:center">15000</td>
 * <td style="text-align:center">15000</td>
 * </tr>
 * <tr>
 * <td id="ERLimit">{@systemProperty jdk.xml.entityReplacementLimit}</td>
 * <td>Limits the total number of nodes in all entity references.
 * </td>
 * <td style="text-align:center">100000</td>
 * <td style="text-align:center">100000</td>
 * </tr>
 * <tr>
 * <td id="ElementDepth">{@systemProperty jdk.xml.maxElementDepth}</td>
 * <td>Limits the maximum element depth.
 * </td>
 * <td style="text-align:center">100</td>
 * <td style="text-align:center">100</td>
 * </tr>
 * <tr>
 * <td id="NameLimit">{@systemProperty jdk.xml.maxXMLNameLimit}</td>
 * <td>Limits the maximum size of XML names, including element name, attribute
 * name and namespace prefix and URI.
 * </td>
 * <td style="text-align:center">1000</td>
 * <td style="text-align:center">1000</td>
 * </tr>
 *
 * <tr>
 * <td id="ISSTANDALONE">{@systemProperty jdk.xml.isStandalone}</td>
 * <td>Indicates that the serializer should treat the output as a
 * standalone document. The property can be used to ensure a newline is written
 * after the XML declaration. Unlike the property
 * {@link org.w3c.dom.ls.LSSerializer#getDomConfig() xml-declaration}, this property
 * does not have an effect on whether an XML declaration should be written out.
 * </td>
 * <td style="text-align:center">boolean</td>
 * <td style="text-align:center">true/false</td>
 * <td style="text-align:center">false</td>
 * <td style="text-align:center">N/A</td>
 * <td style="text-align:center">No</td>
 * <td style="text-align:center"><a href="#DOMLS">DOMLS</a></td>
 * <td style="text-align:center">17</td>
 * </tr>
 * <tr>
 * <td id="XSLTCISSTANDALONE">{@systemProperty jdk.xml.xsltcIsStandalone}</td>
 * <td>Indicates that the <a href="#XSLTCSerializer">XSLTC serializer</a> should
 * treat the output as a standalone document. The property can be used to ensure
 * a newline is written after the XML declaration. Unlike the property
 * {@link javax.xml.transform.OutputKeys#OMIT_XML_DECLARATION OMIT_XML_DECLARATION},
 * this property does not have an effect on whether an XML declaration should be
 * written out.
 * <p>
 * This property behaves similar to that for <a href="#DOMLS">DOMLS</a> above,
 * except that it is for the <a href="#XSLTCSerializer">XSLTC Serializer</a>
 * and its value is a String.
 * </td>
 * <td style="text-align:center">String</td>
 * <td style="text-align:center">yes/no</td>
 * <td style="text-align:center">no</td>
 * <td style="text-align:center">N/A</td>
 * <td style="text-align:center">No</td>
 * <td style="text-align:center"><a href="#XSLTCSerializer">XSLTC Serializer</a></td>
 * <td style="text-align:center">17</td>
 * </tr>
 * <tr>
 * <td id="cdataChunkSize">{@systemProperty jdk.xml.cdataChunkSize}</td>
 * <td>Instructs the parser to return the data in a CData section in a single chunk
 * when the property is zero or unspecified, or in multiple chunks when it is greater
 * than zero. The parser shall split the data by linebreaks, and any chunks that are
 * larger than the specified size to ones that are equal to or smaller than the size.
 * </td>
 * <td style="text-align:center">yes</td>
 * <td style="text-align:center">Integer</td>
 * <td>A positive integer. A value less than
 * or equal to 0 indicates that the property is not specified. If the value is not
 * an integer, a NumberFormatException is thrown.</td>
 * <td style="text-align:center">0</td>
 * <td style="text-align:center">N/A</td>
 * <td style="text-align:center">No</td>
 * <td style="text-align:center"><a href="#SAX">SAX</a><br><a href="#StAX">StAX</a></td>
 * <td style="text-align:center">9</td>
 * </tr>
 * <tr>
 * <td id="extensionClassLoader">jdk.xml.extensionClassLoader</td>
 * <td>Sets a non-null ClassLoader instance to be used for loading XSLTC java
 * extension functions.
 * </td>
 * <td style="text-align:center">no</td>
 * <td style="text-align:center">Object</td>
 * <td>A reference to a ClassLoader object. Null if the value is not specified.</td>
 * <td style="text-align:center">null</td>
 * <td style="text-align:center">N/A</td>
 * <td style="text-align:center">No</td>
 * <td style="text-align:center"><a href="#Transform">Transform</a></td>
 * <td style="text-align:center">9</td>
 * </tr>
 * <tr>
 * <td id="xpathExprGrpLimit">jdk.xml.xpathExprGrpLimit</td>
 * <td>Limits the number of groups an XPath expression can contain.
 * </td>
 * <td style="text-align:center" rowspan="3">yes</td>
 * <td style="text-align:center" rowspan="3">Integer</td>
 * <td rowspan="3">A positive integer. A value less than or equal to 0 indicates no limit.
 * If the value is not an integer, a NumberFormatException is thrown. </td>
 * <td style="text-align:center">10</td>
 * <td style="text-align:center">10</td>
 * <td style="text-align:center" rowspan="3">Yes</td>
 * <td style="text-align:center" rowspan="2">
 *     <a href="#Transform">Transform</a><br>
 *     <a href="#XPATH">XPath</a>
 * </td>
 * <td style="text-align:center" rowspan="3">19</td>
 * </tr>
 * <tr>
 * <td id="xpathExprOpLimit">jdk.xml.xpathExprOpLimit</td>
 * <td>Limits the number of operators an XPath expression can contain.
 * </td>
 * <td style="text-align:center">100</td>
 * <td style="text-align:center">100</td>
 * </tr>
 * <tr>
 * <td id="xpathTotalOpLimit">jdk.xml.xpathTotalOpLimit</td>
 * <td>Limits the total number of XPath operators in an XSL Stylesheet.
 * </td>
 * <td style="text-align:center">10000</td>
 * <td style="text-align:center">10000</td>
 * <td style="text-align:center">
 *     <a href="#Transform">Transform</a><br>
 * </td>
 * </tr>
 * <tr>
 * <td id="ExtFunc">{@systemProperty jdk.xml.enableExtensionFunctions}</td>
 * <td>Determines whether extension functions in the Transform API are to be allowed.
 * The extension functions in the XPath API are not affected by this property.
 * </td>
 * <td style="text-align:center" rowspan="5">yes</td>
 * <td style="text-align:center" rowspan="3">Boolean</td>
 * <td>
 * true or false. True indicates that extension functions are allowed; False otherwise.
 * </td>
 * <td style="text-align:center">false</td>
 * <td style="text-align:center">false</td>
 * <td style="text-align:center">Yes</td>
 * <td style="text-align:center">
 *     <a href="#Transform">Transform</a><br>
 * </td>
 * <td style="text-align:center"><a href="#Processor">Method 2</a></td>
 * <td style="text-align:center">8</td>
 * </tr>
 * <tr>
 * <td id="ORParser">{@systemProperty jdk.xml.overrideDefaultParser}</td>
 * <td>Enables the use of a 3rd party's parser implementation to override the
 * system-default parser for the JDK's Transform, Validation and XPath implementations.
 * </td>
 * <td>
 * true or false. True enables the use of 3rd party's parser implementations
 * to override the system-default implementation during XML Transform, Validation
 * or XPath operation. False disables the use of 3rd party's parser
 * implementations.
 * </td>
 * <td style="text-align:center">false</td>
 * <td style="text-align:center">false</td>
 * <td style="text-align:center">Yes</td>
 * <td style="text-align:center">
 *     <a href="#Transform">Transform</a><br>
 *     <a href="#Validation">Validation</a><br>
 *     <a href="#XPATH">XPath</a>
 * </td>
 * <td style="text-align:center"><a href="#Processor">Method 2</a></td>
 * <td style="text-align:center">9</td>
 * </tr>
 * <tr>
 * <td id="symbolTable">{@systemProperty jdk.xml.resetSymbolTable}</td>
 * <td>Instructs the parser to reset its internal symbol table during each parse operation.
 * </td>
 * <td>
 * true or false. True indicates that the SymbolTable associated with a parser needs to be
 * reallocated during each parse operation.<br>
 * False indicates that the parser's SymbolTable instance shall be reused
 * during subsequent parse operations.
 * </td>
 * <td style="text-align:center">false</td>
 * <td style="text-align:center">N/A</td>
 * <td style="text-align:center">No</td>
 * <td style="text-align:center">
 *     <a href="#SAX">SAX</a>
 * </td>
 * <td style="text-align:center"><a href="#Processor">Method 2</a></td>
 * <td style="text-align:center">9</td>
 * </tr>
 * <tr>
 * <td id="DTD">{@systemProperty jdk.xml.dtd.support}<a href="#Note7">[7]</a></td>
 * <td>Instructs the parser to handle DTDs in accordance with the setting of this property.
 * The options are:
 * <ul>
 * <li><p>
 * {@code allow} -- indicates that the parser shall continue processing DTDs;
 * </li>
 * <li><p>
 * {@code ignore} -- indicates that the parser shall skip DTDs;
 * </li>
 * <li><p>
 * {@code deny} -- indicates that the parser shall reject DTDs as an error.
 * The parser shall report the error in accordance with its relevant specification.
 * </li>
 * </ul>
 * </td>
 * <td style="text-align:center">String</td>
 * <td>
 * {@code allow, ignore, and deny}. Values are case-insensitive.
 * </td>
 * <td style="text-align:center">allow</td>
 * <td style="text-align:center">No</td>
 * <td style="text-align:center">Yes</td>
 * <td style="text-align:center">
 *     <a href="#DOM">DOM</a><br>
 *     <a href="#SAX">SAX</a><br>
 *     <a href="#StAX">StAX</a><br>
 *     <a href="#Validation">Validation</a><br>
 *     <a href="#Transform">Transform</a>
 * </td>
 * <td style="text-align:center"><a href="#Processor">Method 1</a></td>
 * <td style="text-align:center">22</td>
 * </tr>
 * <tr>
 * <td id="JDKCATALOG_RESOLVE">{@systemProperty jdk.xml.jdkcatalog.resolve}</td>
 * <td>Instructs the JDK default CatalogResolver to act in accordance with the setting
 * of this property when unable to resolve an external reference with the built-in Catalog.
 * The options are:
 * <ul>
 * <li><p>
 * {@code continue} -- Indicates that the processing should continue
 * </li>
 * <li><p>
 * {@code ignore} -- Indicates that the reference is skipped
 * </li>
 * <li><p>
 * {@code strict} -- Indicates that the resolver should throw a CatalogException
 * </li>
 * </ul>
 * </td>
 * <td style="text-align:center">String</td>
 * <td>
 * {@code continue, ignore, and strict}. Values are case-insensitive.
 * </td>
 * <td style="text-align:center">continue</td>
 * <td style="text-align:center">No</td>
 * <td style="text-align:center">Yes</td>
 * <td style="text-align:center">
 *     <a href="#DOM">DOM</a><br>
 *     <a href="#SAX">SAX</a><br>
 *     <a href="#StAX">StAX</a><br>
 *     <a href="#Validation">Validation</a><br>
 *     <a href="#Transform">Transform</a>
 * </td>
 * <td style="text-align:center"><a href="#Processor">Method 1</a></td>
 * <td style="text-align:center">22</td>
 * </tr>
 * </tbody>
 * </table>
 * <p id="Note1">
 * <b>[1]</b> The full name of a property should be used to set the property.
 * <p id="Note2">
 * <b>[2]</b> A value "yes" indicates there is a corresponding System Property
 * for the property, "no" otherwise. The name of the System Property is the same
 * as that of the property.
 *
 * <p id="Note3">
 * <b>[3]</b> The value must be exactly as listed in this table, case-sensitive.
 * The value of the corresponding System Property is the String representation of
 * the property value. If the type is boolean, the system property is true only
 * if it is "true"; If the type is String, the system property is true only if
 * it is exactly the same string representing the positive value (e.g. "yes" for
 * {@code xsltcIsStandalone}); The system property is false otherwise. If the type
 * is Integer, the value of the System Property is the String representation of
 * the value (e.g. "64000" for {@code entityExpansionLimit}).
 *
 * <p id="Note4">
 * <b>[4]</b> A value "yes" indicates the property is a Security Property. As indicated
 * in the <a href="#Conf_PP">Property Precedence</a>, the values listed in the column
 * {@code enforced} will be used to initialize these properties when
 * {@link javax.xml.XMLConstants#FEATURE_SECURE_PROCESSING FSP} is true.
 *
 * <p id="Note5">
 * <b>[5]</b> One or more processors that support the property. The IDs and Set Method
 * are as shown in the table <a href="#Processor">Processors</a>.
 * <p id="Note6">
 * <b>[6]</b> Indicates the initial release the property is introduced.
 * <p id="Note7">
 * <b>[7]</b> The {@code jdk.xml.dtd.support} property complements the two existing
 * DTD-related properties, {@code disallow-doctype-decl}(fully qualified name:
 * {@code http://apache.org/xml/features/disallow-doctype-decl}) and supportDTD
 * ({@code javax.xml.stream.supportDTD}), by providing a uniformed support for the
 * processors listed and a system property that can be used in the
 * <a href="#Conf_CF">JAXP Configuration File</a>. When {@code disallow-doctype-decl} is
 * set on the DOM or SAX factory, or supportDTD on StAX factory, the {@code jdk.xml.dtd.support}
 * property will have no effect.
 * <p>
 * These three properties control whether DTDs as a whole shall be processed. When
 * they are set to deny or ignore, other properties that regulate a part or an
 * aspect of DTD shall have no effect.
 *
 * <h3 id="IN_Legacy">Legacy Property Names (deprecated)</h3>
 * JDK releases prior to JDK 17 support the use of URI style prefix for properties.
 * These legacy property names are <b>deprecated</b> as of JDK 17 and may be removed
 * in future releases. If both new and legacy properties are set, the new property
 * names take precedence regardless of how and where they are set. The overriding order
 * as defined in <a href="#Conf_PP">Property Precedence</a> thus becomes:
 *
 * <ul>
 * <li>Value set on factories or processors using new property names.</li>
 * <li>Value set on factories or processors using <b>legacy property names</b>;</li>
 * <li>Value set as System Property;</li>
 * <li>Value set in the configuration file;</li>
 * <li>Value set by FEATURE_SECURE_PROCESSING;</li>
 * <li>The default value;</li>
 * </ul>
 * <p>
 * The following table lists the properties and their corresponding legacy names.
 *
 * <table class="striped" id="LegacyProperties">
 * <caption>Legacy Property Names (deprecated since 17)</caption>
 * <thead>
 * <tr>
 * <th>Property</th>
 * <th>Legacy Property Name(s)</th>
 * </tr>
 * </thead>
 * <tbody>
 * <tr>
 * <td>{@systemProperty jdk.xml.entityExpansionLimit}</td>
 * <td>{@code http://www.oracle.com/xml/jaxp/properties/entityExpansionLimit}</td>
 * </tr>
 * <tr>
 * <td>{@systemProperty jdk.xml.elementAttributeLimit}</td>
 * <td>{@code http://www.oracle.com/xml/jaxp/properties/elementAttributeLimit}</td>
 * </tr>
 * <tr>
 * <td>{@systemProperty jdk.xml.maxOccurLimit}</td>
 * <td>{@code http://www.oracle.com/xml/jaxp/properties/maxOccurLimit}</td>
 * </tr>
 * <tr>
 * <td>{@systemProperty jdk.xml.totalEntitySizeLimit}</td>
 * <td>{@code http://www.oracle.com/xml/jaxp/properties/totalEntitySizeLimit}</td>
 * </tr>
 * <tr>
 * <td>{@systemProperty jdk.xml.maxGeneralEntitySizeLimit}</td>
 * <td>{@code http://www.oracle.com/xml/jaxp/properties/maxGeneralEntitySizeLimit}</td>
 * </tr>
 * <tr>
 * <td>{@systemProperty jdk.xml.maxParameterEntitySizeLimit}</td>
 * <td>{@code http://www.oracle.com/xml/jaxp/properties/maxParameterEntitySizeLimit}</td>
 * </tr>
 * <tr>
 * <td>{@systemProperty jdk.xml.entityReplacementLimit}</td>
 * <td>{@code http://www.oracle.com/xml/jaxp/properties/entityReplacementLimit}</td>
 * </tr>
 * <tr>
 * <td>{@systemProperty jdk.xml.maxElementDepth}</td>
 * <td>{@code http://www.oracle.com/xml/jaxp/properties/maxElementDepth}</td>
 * </tr>
 * <tr>
 * <td>{@systemProperty jdk.xml.maxXMLNameLimit}</td>
 * <td>{@code http://www.oracle.com/xml/jaxp/properties/maxXMLNameLimit}</td>
 * </tr>
 * <tr>
 * <td>{@systemProperty jdk.xml.isStandalone}</td>
 * <td>{@code http://www.oracle.com/xml/jaxp/properties/isStandalone}</td>
 * </tr>
 * <tr>
 * <td>{@systemProperty jdk.xml.xsltcIsStandalone}</td>
 * <td>{@code http://www.oracle.com/xml/is-standalone}<br>
 * {@code http://www.oracle.com/xml/jaxp/properties/xsltcIsStandalone}</td>
 * </tr>
 * <tr>
 * <td>{@code jdk.xml.extensionClassLoader}</td>
 * <td>{@code jdk.xml.transform.extensionClassLoader}</td>
 * </tr>
 * <tr>
 * <td>{@systemProperty jdk.xml.enableExtensionFunctions}</td>
 * <td>{@code http://www.oracle.com/xml/jaxp/properties/enableExtensionFunctions}</td>
 * </tr>
 * </tbody>
 * </table>
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
