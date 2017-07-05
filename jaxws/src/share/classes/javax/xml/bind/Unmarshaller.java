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

package javax.xml.bind;

import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.attachment.AttachmentUnmarshaller;
import javax.xml.validation.Schema;
import java.io.Reader;

/**
 * The <tt>Unmarshaller</tt> class governs the process of deserializing XML
 * data into newly created Java content trees, optionally validating the XML
 * data as it is unmarshalled.  It provides an overloading of unmarshal methods
 * for many different input kinds.
 *
 * <p>
 * Unmarshalling from a File:
 * <blockquote>
 *    <pre>
 *       JAXBContext jc = JAXBContext.newInstance( "com.acme.foo" );
 *       Unmarshaller u = jc.createUnmarshaller();
 *       Object o = u.unmarshal( new File( "nosferatu.xml" ) );
 *    </pre>
 * </blockquote>
 *
 *
 * <p>
 * Unmarshalling from an InputStream:
 * <blockquote>
 *    <pre>
 *       InputStream is = new FileInputStream( "nosferatu.xml" );
 *       JAXBContext jc = JAXBContext.newInstance( "com.acme.foo" );
 *       Unmarshaller u = jc.createUnmarshaller();
 *       Object o = u.unmarshal( is );
 *    </pre>
 * </blockquote>
 *
 * <p>
 * Unmarshalling from a URL:
 * <blockquote>
 *    <pre>
 *       JAXBContext jc = JAXBContext.newInstance( "com.acme.foo" );
 *       Unmarshaller u = jc.createUnmarshaller();
 *       URL url = new URL( "http://beaker.east/nosferatu.xml" );
 *       Object o = u.unmarshal( url );
 *    </pre>
 * </blockquote>
 *
 * <p>
 * Unmarshalling from a StringBuffer using a
 * <tt>javax.xml.transform.stream.StreamSource</tt>:
 * <blockquote>
 *    <pre>
 *       JAXBContext jc = JAXBContext.newInstance( "com.acme.foo" );
 *       Unmarshaller u = jc.createUnmarshaller();
 *       StringBuffer xmlStr = new StringBuffer( "&lt;?xml version=&quot;1.0&quot;?&gt;..." );
 *       Object o = u.unmarshal( new StreamSource( new StringReader( xmlStr.toString() ) ) );
 *    </pre>
 * </blockquote>
 *
 * <p>
 * Unmarshalling from a <tt>org.w3c.dom.Node</tt>:
 * <blockquote>
 *    <pre>
 *       JAXBContext jc = JAXBContext.newInstance( "com.acme.foo" );
 *       Unmarshaller u = jc.createUnmarshaller();
 *
 *       DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
 *       dbf.setNamespaceAware(true);
 *       DocumentBuilder db = dbf.newDocumentBuilder();
 *       Document doc = db.parse(new File( "nosferatu.xml"));

 *       Object o = u.unmarshal( doc );
 *    </pre>
 * </blockquote>
 *
 * <p>
 * Unmarshalling from a <tt>javax.xml.transform.sax.SAXSource</tt> using a
 * client specified validating SAX2.0 parser:
 * <blockquote>
 *    <pre>
 *       // configure a validating SAX2.0 parser (Xerces2)
 *       static final String JAXP_SCHEMA_LANGUAGE =
 *           "http://java.sun.com/xml/jaxp/properties/schemaLanguage";
 *       static final String JAXP_SCHEMA_LOCATION =
 *           "http://java.sun.com/xml/jaxp/properties/schemaSource";
 *       static final String W3C_XML_SCHEMA =
 *           "http://www.w3.org/2001/XMLSchema";
 *
 *       System.setProperty( "javax.xml.parsers.SAXParserFactory",
 *                           "org.apache.xerces.jaxp.SAXParserFactoryImpl" );
 *
 *       SAXParserFactory spf = SAXParserFactory.newInstance();
 *       spf.setNamespaceAware(true);
 *       spf.setValidating(true);
 *       SAXParser saxParser = spf.newSAXParser();
 *
 *       try {
 *           saxParser.setProperty(JAXP_SCHEMA_LANGUAGE, W3C_XML_SCHEMA);
 *           saxParser.setProperty(JAXP_SCHEMA_LOCATION, "http://....");
 *       } catch (SAXNotRecognizedException x) {
 *           // exception handling omitted
 *       }
 *
 *       XMLReader xmlReader = saxParser.getXMLReader();
 *       SAXSource source =
 *           new SAXSource( xmlReader, new InputSource( "http://..." ) );
 *
 *       // Setup JAXB to unmarshal
 *       JAXBContext jc = JAXBContext.newInstance( "com.acme.foo" );
 *       Unmarshaller u = jc.createUnmarshaller();
 *       ValidationEventCollector vec = new ValidationEventCollector();
 *       u.setEventHandler( vec );
 *
 *       // turn off the JAXB provider's default validation mechanism to
 *       // avoid duplicate validation
 *       u.setValidating( false )
 *
 *       // unmarshal
 *       Object o = u.unmarshal( source );
 *
 *       // check for events
 *       if( vec.hasEvents() ) {
 *          // iterate over events
 *       }
 *    </pre>
 * </blockquote>
 *
 * <p>
 * Unmarshalling from a StAX XMLStreamReader:
 * <blockquote>
 *    <pre>
 *       JAXBContext jc = JAXBContext.newInstance( "com.acme.foo" );
 *       Unmarshaller u = jc.createUnmarshaller();
 *
 *       javax.xml.stream.XMLStreamReader xmlStreamReader =
 *           javax.xml.stream.XMLInputFactory().newInstance().createXMLStreamReader( ... );
 *
 *       Object o = u.unmarshal( xmlStreamReader );
 *    </pre>
 * </blockquote>
 *
 * <p>
 * Unmarshalling from a StAX XMLEventReader:
 * <blockquote>
 *    <pre>
 *       JAXBContext jc = JAXBContext.newInstance( "com.acme.foo" );
 *       Unmarshaller u = jc.createUnmarshaller();
 *
 *       javax.xml.stream.XMLEventReader xmlEventReader =
 *           javax.xml.stream.XMLInputFactory().newInstance().createXMLEventReader( ... );
 *
 *       Object o = u.unmarshal( xmlEventReader );
 *    </pre>
 * </blockquote>
 *
 * <p>
 * <a name="unmarshalEx"></a>
 * <b>Unmarshalling XML Data</b><br>
 * <blockquote>
 * Unmarshalling can deserialize XML data that represents either an entire XML document
 * or a subtree of an XML document. Typically, it is sufficient to use the
 * unmarshalling methods described by
 * <a href="#unmarshalGlobal">Unmarshal root element that is declared globally</a>.
 * These unmarshal methods utilize {@link JAXBContext}'s mapping of global XML element
 * declarations and type definitions to JAXB mapped classes to initiate the
 * unmarshalling of the root element of  XML data.  When the {@link JAXBContext}'s
 * mappings are not sufficient to unmarshal the root element of XML data,
 * the application can assist the unmarshalling process by using the
 * <a href="#unmarshalByDeclaredType">unmarshal by declaredType methods</a>.
 * These methods are useful for unmarshalling XML data where
 * the root element corresponds to a local element declaration in the schema.
 * </blockquote>
 *
 * <blockquote>
 * An unmarshal method never returns null. If the unmarshal process is unable to unmarshal
 * the root of XML content to a JAXB mapped object, a fatal error is reported that
 * terminates processing by throwing JAXBException.
 * </blockquote>
 *
 * <p>
 * <a name="unmarshalGlobal"></a>
 * <b>Unmarshal a root element that is globally declared</b><br>
 * <blockquote>
 * The unmarshal methods that do not have an <tt>declaredType</tt> parameter use
 * {@link JAXBContext} to unmarshal the root element of an XML data. The {@link JAXBContext}
 * instance is the one that was used to create this <tt>Unmarshaller</tt>. The {@link JAXBContext}
 * instance maintains a mapping of globally declared XML element and type definition names to
 * JAXB mapped classes. The unmarshal method checks if {@link JAXBContext} has a mapping
 * from the root element's XML name and/or <tt>@xsi:type</tt> to a JAXB mapped class.  If it does, it umarshalls the
 * XML data using the appropriate JAXB mapped class. Note that when the root element name is unknown and the root
 * element has an <tt>@xsi:type</tt>, the XML data is unmarshalled
 * using that JAXB mapped class as the value of a {@link JAXBElement}.
 * When the {@link JAXBContext} object does not have a mapping for the root element's name
 * nor its <tt>@xsi:type</tt>, if it exists,
 * then the unmarshal operation will abort immediately by throwing a {@link UnmarshalException
 * UnmarshalException}. This exception scenario can be worked around by using the unmarshal by
 * declaredType methods described in the next subsection.
 * </blockquote>
 *
 * <p>
 * <a name="unmarshalByDeclaredType"></a>
 * <b>Unmarshal by Declared Type</b><br>
 * <blockquote>
 * The unmarshal methods with a <code>declaredType</code> parameter enable an
 * application to deserialize a root element of XML data, even when
 * there is no mapping in {@link JAXBContext} of the root element's XML name.
 * The unmarshaller unmarshals the root element using the application provided
 * mapping specified as the <tt>declaredType</tt> parameter.
 * Note that even when the root element's element name is mapped by {@link JAXBContext},
 * the <code>declaredType</code> parameter overrides that mapping for
 * deserializing the root element when using these unmarshal methods.
 * Additionally, when the root element of XML data has an <tt>xsi:type</tt> attribute and
 * that attribute's value references a type definition that is mapped
 * to a JAXB mapped class by {@link JAXBContext}, that the root
 * element's <tt>xsi:type</tt> attribute takes
 * precedence over the unmarshal methods <tt>declaredType</tt> parameter.
 * These methods always return a <tt>JAXBElement&lt;declaredType></tt>
 * instance. The table below shows how the properties of the returned JAXBElement
 * instance are set.
 *
 * <a name="unmarshalDeclaredTypeReturn"></a>
 * <p>
 *   <table border="2" rules="all" cellpadding="4">
 *   <thead>
 *     <tr>
 *       <th align="center" colspan="2">
 *       Unmarshal By Declared Type returned JAXBElement
 *       </tr>
 *     <tr>
 *       <th>JAXBElement Property</th>
 *       <th>Value</th>
 *     </tr>
 *     </tr>
 *     <tr>
 *       <td>name</td>
 *       <td><code>xml element name</code></td>
 *     </tr>
 *   </thead>
 *   <tbody>
 *     <tr>
 *       <td>value</td>
 *       <td><code>instanceof declaredType</code></td>
 *     </tr>
 *     <tr>
 *       <td>declaredType</td>
 *       <td>unmarshal method <code>declaredType</code> parameter</td>
 *     </tr>
 *     <tr>
 *       <td>scope</td>
 *       <td><code>null</code> <i>(actual scope is unknown)</td>
 *     </tr>
 *   </tbody>
 *  </table>
 * </blockquote>
 *
 * <p>
 * The following is an example of
 * <a href="#unmarshalByDeclaredType">unmarshal by declaredType method</a>.
 * <p>
 * Unmarshal by declaredType from a <tt>org.w3c.dom.Node</tt>:
 * <blockquote>
 *    <pre>
 *       Schema fragment for example
 *       &lt;xs:schema>
 *          &lt;xs:complexType name="FooType">...&lt;\xs:complexType>
 *          &lt;!-- global element declaration "PurchaseOrder" -->
 *          &lt;xs:element name="PurchaseOrder">
 *              &lt;xs:complexType>
 *                 &lt;xs:sequence>
 *                    &lt;!-- local element declaration "foo" -->
 *                    &lt;xs:element name="foo" type="FooType"/>
 *                    ...
 *                 &lt;/xs:sequence>
 *              &lt;/xs:complexType>
 *          &lt;/xs:element>
 *       &lt;/xs:schema>
 *
 *       JAXBContext jc = JAXBContext.newInstance( "com.acme.foo" );
 *       Unmarshaller u = jc.createUnmarshaller();
 *
 *       DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
 *       dbf.setNamespaceAware(true);
 *       DocumentBuilder db = dbf.newDocumentBuilder();
 *       Document doc = db.parse(new File( "nosferatu.xml"));
 *       Element  fooSubtree = ...; // traverse DOM till reach xml element foo, constrained by a
 *                                  // local element declaration in schema.
 *
 *       // FooType is the JAXB mapping of the type of local element declaration foo.
 *       JAXBElement&lt;FooType> foo = u.unmarshal( fooSubtree, FooType.class);
 *    </pre>
 * </blockquote>
 *
 * <p>
 * <b>Support for SAX2.0 Compliant Parsers</b><br>
 * <blockquote>
 * A client application has the ability to select the SAX2.0 compliant parser
 * of their choice.  If a SAX parser is not selected, then the JAXB Provider's
 * default parser will be used.  Even though the JAXB Provider's default parser
 * is not required to be SAX2.0 compliant, all providers are required to allow
 * a client application to specify their own SAX2.0 parser.  Some providers may
 * require the client application to specify the SAX2.0 parser at schema compile
 * time. See {@link #unmarshal(javax.xml.transform.Source) unmarshal(Source)}
 * for more detail.
 * </blockquote>
 *
 * <p>
 * <b>Validation and Well-Formedness</b><br>
 * <blockquote>
 * <p>
 * A client application can enable or disable JAXP 1.3 validation
 * mechanism via the <tt>setSchema(javax.xml.validation.Schema)</tt> API.
 * Sophisticated clients can specify their own validating SAX 2.0 compliant
 * parser and bypass the JAXP 1.3 validation mechanism using the
 * {@link #unmarshal(javax.xml.transform.Source) unmarshal(Source)}  API.
 *
 * <p>
 * Since unmarshalling invalid XML content is defined in JAXB 2.0,
 * the Unmarshaller default validation event handler was made more lenient
 * than in JAXB 1.0.  When schema-derived code generated
 * by JAXB 1.0 binding compiler is registered with {@link JAXBContext},
 * the default unmarshal validation handler is
 * {@link javax.xml.bind.helpers.DefaultValidationEventHandler} and it
 * terminates the marshal  operation after encountering either a fatal error or an error.
 * For a JAXB 2.0 client application, there is no explicitly defined default
 * validation handler and the default event handling only
 * terminates the marshal operation after encountering a fatal error.
 *
 * </blockquote>
 *
 * <p>
 * <a name="supportedProps"></a>
 * <b>Supported Properties</b><br>
 * <blockquote>
 * <p>
 * There currently are not any properties required to be supported by all
 * JAXB Providers on Unmarshaller.  However, some providers may support
 * their own set of provider specific properties.
 * </blockquote>
 *
 * <p>
 * <a name="unmarshalEventCallback"></a>
 * <b>Unmarshal Event Callbacks</b><br>
 * <blockquote>
 * The {@link Unmarshaller} provides two styles of callback mechanisms
 * that allow application specific processing during key points in the
 * unmarshalling process.  In 'class defined' event callbacks, application
 * specific code placed in JAXB mapped classes is triggered during
 * unmarshalling.  'External listeners' allow for centralized processing
 * of unmarshal events in one callback method rather than by type event callbacks.
 * <p>
 * 'Class defined' event callback methods allow any JAXB mapped class to specify
 * its own specific callback methods by defining methods with the following method signature:
 * <blockquote>
 * <pre>
 *   // This method is called immediately after the object is created and before the unmarshalling of this
 *   // object begins. The callback provides an opportunity to initialize JavaBean properties prior to unmarshalling.
 *   void beforeUnmarshal(Unmarshaller, Object parent);
 *
 *   //This method is called after all the properties (except IDREF) are unmarshalled for this object,
 *   //but before this object is set to the parent object.
 *   void afterUnmarshal(Unmarshaller, Object parent);
 * </pre>
 * </blockquote>
 * The class defined callback methods should be used when the callback method requires
 * access to non-public methods and/or fields of the class.
 * <p>
 * The external listener callback mechanism enables the registration of a {@link Listener}
 * instance with an {@link Unmarshaller#setListener(Listener)}. The external listener receives all callback events,
 * allowing for more centralized processing than per class defined callback methods.  The external listener
 * receives events when unmarshalling proces is marshalling to a JAXB element or to JAXB mapped class.
 * <p>
 * The 'class defined' and external listener event callback methods are independent of each other,
 * both can be called for one event.  The invocation ordering when both listener callback methods exist is
 * defined in {@link Listener#beforeUnmarshal(Object, Object)} and {@link Listener#afterUnmarshal(Object, Object)}.
* <p>
 * An event callback method throwing an exception terminates the current unmarshal process.
 *
 * </blockquote>
 *
 * @author <ul><li>Ryan Shoemaker, Sun Microsystems, Inc.</li><li>Kohsuke Kawaguchi, Sun Microsystems, Inc.</li><li>Joe Fialli, Sun Microsystems, Inc.</li></ul>
 * @version $Revision: 1.32 $ $Date: 2005/08/18 15:18:26 $
 * @see JAXBContext
 * @see Marshaller
 * @see Validator
 * @since JAXB1.0
 */
public interface Unmarshaller {

    /**
     * Unmarshal XML data from the specified file and return the resulting
     * content tree.
     *
     * <p>
     * Implements <a href="#unmarshalGlobal">Unmarshal Global Root Element</a>.
     *
     * @param f the file to unmarshal XML data from
     * @return the newly created root object of the java content tree
     *
     * @throws JAXBException
     *     If any unexpected errors occur while unmarshalling
     * @throws UnmarshalException
     *     If the {@link ValidationEventHandler ValidationEventHandler}
     *     returns false from its <tt>handleEvent</tt> method or the
     *     <tt>Unmarshaller</tt> is unable to perform the XML to Java
     *     binding.  See <a href="#unmarshalEx">Unmarshalling XML Data</a>
     * @throws IllegalArgumentException
     *      If the file parameter is null
     */
    public Object unmarshal( java.io.File f ) throws JAXBException;

    /**
     * Unmarshal XML data from the specified InputStream and return the
     * resulting content tree.  Validation event location information may
     * be incomplete when using this form of the unmarshal API.
     *
     * <p>
     * Implements <a href="#unmarshalGlobal">Unmarshal Global Root Element</a>.
     *
     * @param is the InputStream to unmarshal XML data from
     * @return the newly created root object of the java content tree
     *
     * @throws JAXBException
     *     If any unexpected errors occur while unmarshalling
     * @throws UnmarshalException
     *     If the {@link ValidationEventHandler ValidationEventHandler}
     *     returns false from its <tt>handleEvent</tt> method or the
     *     <tt>Unmarshaller</tt> is unable to perform the XML to Java
     *     binding.  See <a href="#unmarshalEx">Unmarshalling XML Data</a>
     * @throws IllegalArgumentException
     *      If the InputStream parameter is null
     */
    public Object unmarshal( java.io.InputStream is ) throws JAXBException;

    /**
     * Unmarshal XML data from the specified Reader and return the
     * resulting content tree.  Validation event location information may
     * be incomplete when using this form of the unmarshal API,
     * because a Reader does not provide the system ID.
     *
     * <p>
     * Implements <a href="#unmarshalGlobal">Unmarshal Global Root Element</a>.
     *
     * @param reader the Reader to unmarshal XML data from
     * @return the newly created root object of the java content tree
     *
     * @throws JAXBException
     *     If any unexpected errors occur while unmarshalling
     * @throws UnmarshalException
     *     If the {@link ValidationEventHandler ValidationEventHandler}
     *     returns false from its <tt>handleEvent</tt> method or the
     *     <tt>Unmarshaller</tt> is unable to perform the XML to Java
     *     binding.  See <a href="#unmarshalEx">Unmarshalling XML Data</a>
     * @throws IllegalArgumentException
     *      If the InputStream parameter is null
     * @since JAXB2.0
     */
    public Object unmarshal( Reader reader ) throws JAXBException;

    /**
     * Unmarshal XML data from the specified URL and return the resulting
     * content tree.
     *
     * <p>
     * Implements <a href="#unmarshalGlobal">Unmarshal Global Root Element</a>.
     *
     * @param url the url to unmarshal XML data from
     * @return the newly created root object of the java content tree
     *
     * @throws JAXBException
     *     If any unexpected errors occur while unmarshalling
     * @throws UnmarshalException
     *     If the {@link ValidationEventHandler ValidationEventHandler}
     *     returns false from its <tt>handleEvent</tt> method or the
     *     <tt>Unmarshaller</tt> is unable to perform the XML to Java
     *     binding.  See <a href="#unmarshalEx">Unmarshalling XML Data</a>
     * @throws IllegalArgumentException
     *      If the URL parameter is null
     */
    public Object unmarshal( java.net.URL url ) throws JAXBException;

    /**
     * Unmarshal XML data from the specified SAX InputSource and return the
     * resulting content tree.
     *
     * <p>
     * Implements <a href="#unmarshalGlobal">Unmarshal Global Root Element</a>.
     *
     * @param source the input source to unmarshal XML data from
     * @return the newly created root object of the java content tree
     *
     * @throws JAXBException
     *     If any unexpected errors occur while unmarshalling
     * @throws UnmarshalException
     *     If the {@link ValidationEventHandler ValidationEventHandler}
     *     returns false from its <tt>handleEvent</tt> method or the
     *     <tt>Unmarshaller</tt> is unable to perform the XML to Java
     *     binding.  See <a href="#unmarshalEx">Unmarshalling XML Data</a>
     * @throws IllegalArgumentException
     *      If the InputSource parameter is null
     */
    public Object unmarshal( org.xml.sax.InputSource source ) throws JAXBException;

    /**
     * Unmarshal global XML data from the specified DOM tree and return the resulting
     * content tree.
     *
     * <p>
     * Implements <a href="#unmarshalGlobal">Unmarshal Global Root Element</a>.
     *
     * @param node
     *      the document/element to unmarshal XML data from.
     *      The caller must support at least Document and Element.
     * @return the newly created root object of the java content tree
     *
     * @throws JAXBException
     *     If any unexpected errors occur while unmarshalling
     * @throws UnmarshalException
     *     If the {@link ValidationEventHandler ValidationEventHandler}
     *     returns false from its <tt>handleEvent</tt> method or the
     *     <tt>Unmarshaller</tt> is unable to perform the XML to Java
     *     binding.  See <a href="#unmarshalEx">Unmarshalling XML Data</a>
     * @throws IllegalArgumentException
     *      If the Node parameter is null
     * @see #unmarshal(org.w3c.dom.Node, Class)
     */
    public Object unmarshal( org.w3c.dom.Node node ) throws JAXBException;

    /**
     * Unmarshal XML data by JAXB mapped <tt>declaredType</tt>
     * and return the resulting content tree.
     *
     * <p>
     * Implements <a href="#unmarshalByDeclaredType">Unmarshal by Declared Type</a>
     *
     * @param node
     *      the document/element to unmarshal XML data from.
     *      The caller must support at least Document and Element.
     * @param declaredType
     *      appropriate JAXB mapped class to hold <tt>node</tt>'s XML data.
     *
     * @return <a href="#unmarshalDeclaredTypeReturn">JAXB Element</a> representation of <tt>node</tt>
     *
     * @throws JAXBException
     *     If any unexpected errors occur while unmarshalling
     * @throws UnmarshalException
     *     If the {@link ValidationEventHandler ValidationEventHandler}
     *     returns false from its <tt>handleEvent</tt> method or the
     *     <tt>Unmarshaller</tt> is unable to perform the XML to Java
     *     binding.  See <a href="#unmarshalEx">Unmarshalling XML Data</a>
     * @throws IllegalArgumentException
     *      If any parameter is null
     * @since JAXB2.0
     */
    public <T> JAXBElement<T> unmarshal( org.w3c.dom.Node node, Class<T> declaredType ) throws JAXBException;

    /**
     * Unmarshal XML data from the specified XML Source and return the
     * resulting content tree.
     *
     * <p>
     * Implements <a href="#unmarshalGlobal">Unmarshal Global Root Element</a>.
     *
     * <p>
     * <a name="saxParserPlugable"></a>
     * <b>SAX 2.0 Parser Pluggability</b>
     * <p>
     * A client application can choose not to use the default parser mechanism
     * supplied with their JAXB provider.  Any SAX 2.0 compliant parser can be
     * substituted for the JAXB provider's default mechanism.  To do so, the
     * client application must properly configure a <tt>SAXSource</tt> containing
     * an <tt>XMLReader</tt> implemented by the SAX 2.0 parser provider.  If the
     * <tt>XMLReader</tt> has an <tt>org.xml.sax.ErrorHandler</tt> registered
     * on it, it will be replaced by the JAXB Provider so that validation errors
     * can be reported via the <tt>ValidationEventHandler</tt> mechanism of
     * JAXB.  If the <tt>SAXSource</tt> does not contain an <tt>XMLReader</tt>,
     * then the JAXB provider's default parser mechanism will be used.
     * <p>
     * This parser replacement mechanism can also be used to replace the JAXB
     * provider's unmarshal-time validation engine.  The client application
     * must properly configure their SAX 2.0 compliant parser to perform
     * validation (as shown in the example above).  Any <tt>SAXParserExceptions
     * </tt> encountered by the parser during the unmarshal operation will be
     * processed by the JAXB provider and converted into JAXB
     * <tt>ValidationEvent</tt> objects which will be reported back to the
     * client via the <tt>ValidationEventHandler</tt> registered with the
     * <tt>Unmarshaller</tt>.  <i>Note:</i> specifying a substitute validating
     * SAX 2.0 parser for unmarshalling does not necessarily replace the
     * validation engine used by the JAXB provider for performing on-demand
     * validation.
     * <p>
     * The only way for a client application to specify an alternate parser
     * mechanism to be used during unmarshal is via the
     * <tt>unmarshal(SAXSource)</tt> API.  All other forms of the unmarshal
     * method (File, URL, Node, etc) will use the JAXB provider's default
     * parser and validator mechanisms.
     *
     * @param source the XML Source to unmarshal XML data from (providers are
     *        only required to support SAXSource, DOMSource, and StreamSource)
     * @return the newly created root object of the java content tree
     *
     * @throws JAXBException
     *     If any unexpected errors occur while unmarshalling
     * @throws UnmarshalException
     *     If the {@link ValidationEventHandler ValidationEventHandler}
     *     returns false from its <tt>handleEvent</tt> method or the
     *     <tt>Unmarshaller</tt> is unable to perform the XML to Java
     *     binding.  See <a href="#unmarshalEx">Unmarshalling XML Data</a>
     * @throws IllegalArgumentException
     *      If the Source parameter is null
     * @see #unmarshal(javax.xml.transform.Source, Class)
     */
    public Object unmarshal( javax.xml.transform.Source source )
        throws JAXBException;


    /**
     * Unmarshal XML data from the specified XML Source by <tt>declaredType</tt> and return the
     * resulting content tree.
     *
     * <p>
     * Implements <a href="#unmarshalByDeclaredType">Unmarshal by Declared Type</a>
     *
     * <p>
     * See <a href="#saxParserPlugable">SAX 2.0 Parser Pluggability</a>
     *
     * @param source the XML Source to unmarshal XML data from (providers are
     *        only required to support SAXSource, DOMSource, and StreamSource)
     * @param declaredType
     *      appropriate JAXB mapped class to hold <tt>source</tt>'s xml root element
     * @return Java content rooted by <a href="#unmarshalDeclaredTypeReturn">JAXB Element</a>
     *
     * @throws JAXBException
     *     If any unexpected errors occur while unmarshalling
     * @throws UnmarshalException
     *     If the {@link ValidationEventHandler ValidationEventHandler}
     *     returns false from its <tt>handleEvent</tt> method or the
     *     <tt>Unmarshaller</tt> is unable to perform the XML to Java
     *     binding.  See <a href="#unmarshalEx">Unmarshalling XML Data</a>
     * @throws IllegalArgumentException
     *      If any parameter is null
     * @since JAXB2.0
     */
    public <T> JAXBElement<T> unmarshal( javax.xml.transform.Source source, Class<T> declaredType )
        throws JAXBException;

    /**
     * Unmarshal XML data from the specified pull parser and return the
     * resulting content tree.
     *
     * <p>
     * Implements <a href="#unmarshalGlobal">Unmarshal Global Root Element</a>.
     *
     * <p>
     * This method assumes that the parser is on a START_DOCUMENT or
     * START_ELEMENT event.  Unmarshalling will be done from this
     * start event to the corresponding end event.  If this method
     * returns successfully, the <tt>reader</tt> will be pointing at
     * the token right after the end event.
     *
     * @param reader
     *      The parser to be read.
     * @return
     *      the newly created root object of the java content tree.
     *
     * @throws JAXBException
     *     If any unexpected errors occur while unmarshalling
     * @throws UnmarshalException
     *     If the {@link ValidationEventHandler ValidationEventHandler}
     *     returns false from its <tt>handleEvent</tt> method or the
     *     <tt>Unmarshaller</tt> is unable to perform the XML to Java
     *     binding.  See <a href="#unmarshalEx">Unmarshalling XML Data</a>
     * @throws IllegalArgumentException
     *      If the <tt>reader</tt> parameter is null
     * @throws IllegalStateException
     *      If <tt>reader</tt> is not pointing to a START_DOCUMENT or
     *      START_ELEMENT  event.
     * @since JAXB2.0
     * @see #unmarshal(javax.xml.stream.XMLStreamReader, Class)
     */
    public Object unmarshal( javax.xml.stream.XMLStreamReader reader )
        throws JAXBException;

    /**
     * Unmarshal root element to JAXB mapped <tt>declaredType</tt>
     * and return the resulting content tree.
     *
     * <p>
     * This method implements <a href="unmarshalByDeclaredType">unmarshal by declaredType</a>.
     * <p>
     * This method assumes that the parser is on a START_DOCUMENT or
     * START_ELEMENT event. Unmarshalling will be done from this
     * start event to the corresponding end event.  If this method
     * returns successfully, the <tt>reader</tt> will be pointing at
     * the token right after the end event.
     *
     * @param reader
     *      The parser to be read.
     * @param declaredType
     *      appropriate JAXB mapped class to hold <tt>reader</tt>'s START_ELEMENT XML data.
     *
     * @return   content tree rooted by <a href="#unmarshalDeclaredTypeReturn">JAXB Element representation</a>
     *
     * @throws JAXBException
     *     If any unexpected errors occur while unmarshalling
     * @throws UnmarshalException
     *     If the {@link ValidationEventHandler ValidationEventHandler}
     *     returns false from its <tt>handleEvent</tt> method or the
     *     <tt>Unmarshaller</tt> is unable to perform the XML to Java
     *     binding.  See <a href="#unmarshalEx">Unmarshalling XML Data</a>
     * @throws IllegalArgumentException
     *      If any parameter is null
     * @since JAXB2.0
     */
    public <T> JAXBElement<T> unmarshal( javax.xml.stream.XMLStreamReader reader, Class<T> declaredType ) throws JAXBException;

    /**
     * Unmarshal XML data from the specified pull parser and return the
     * resulting content tree.
     *
     * <p>
     * This method is an <a href="#unmarshalGlobal">Unmarshal Global Root method</a>.
     *
     * <p>
     * This method assumes that the parser is on a START_DOCUMENT or
     * START_ELEMENT event.  Unmarshalling will be done from this
     * start event to the corresponding end event.  If this method
     * returns successfully, the <tt>reader</tt> will be pointing at
     * the token right after the end event.
     *
     * @param reader
     *      The parser to be read.
     * @return
     *      the newly created root object of the java content tree.
     *
     * @throws JAXBException
     *     If any unexpected errors occur while unmarshalling
     * @throws UnmarshalException
     *     If the {@link ValidationEventHandler ValidationEventHandler}
     *     returns false from its <tt>handleEvent</tt> method or the
     *     <tt>Unmarshaller</tt> is unable to perform the XML to Java
     *     binding.  See <a href="#unmarshalEx">Unmarshalling XML Data</a>
     * @throws IllegalArgumentException
     *      If the <tt>reader</tt> parameter is null
     * @throws IllegalStateException
     *      If <tt>reader</tt> is not pointing to a START_DOCUMENT or
     *      START_ELEMENT event.
     * @since JAXB2.0
     * @see #unmarshal(javax.xml.stream.XMLEventReader, Class)
     */
    public Object unmarshal( javax.xml.stream.XMLEventReader reader )
        throws JAXBException;

    /**
     * Unmarshal root element to JAXB mapped <tt>declaredType</tt>
     * and return the resulting content tree.
     *
     * <p>
     * This method implements <a href="unmarshalByDeclaredType">unmarshal by declaredType</a>.
     *
     * <p>
     * This method assumes that the parser is on a START_DOCUMENT or
     * START_ELEMENT event. Unmarshalling will be done from this
     * start event to the corresponding end event.  If this method
     * returns successfully, the <tt>reader</tt> will be pointing at
     * the token right after the end event.
     *
     * @param reader
     *      The parser to be read.
     * @param declaredType
     *      appropriate JAXB mapped class to hold <tt>reader</tt>'s START_ELEMENT XML data.
     *
     * @return   content tree rooted by <a href="#unmarshalDeclaredTypeReturn">JAXB Element representation</a>
     *
     * @throws JAXBException
     *     If any unexpected errors occur while unmarshalling
     * @throws UnmarshalException
     *     If the {@link ValidationEventHandler ValidationEventHandler}
     *     returns false from its <tt>handleEvent</tt> method or the
     *     <tt>Unmarshaller</tt> is unable to perform the XML to Java
     *     binding.  See <a href="#unmarshalEx">Unmarshalling XML Data</a>
     * @throws IllegalArgumentException
     *      If any parameter is null
     * @since JAXB2.0
     */
    public <T> JAXBElement<T> unmarshal( javax.xml.stream.XMLEventReader reader, Class<T> declaredType ) throws JAXBException;

    /**
     * Get an unmarshaller handler object that can be used as a component in
     * an XML pipeline.
     *
     * <p>
     * The JAXB Provider can return the same handler object for multiple
     * invocations of this method. In other words, this method does not
     * necessarily create a new instance of <tt>UnmarshallerHandler</tt>. If the
     * application needs to use more than one <tt>UnmarshallerHandler</tt>, it
     * should create more than one <tt>Unmarshaller</tt>.
     *
     * @return the unmarshaller handler object
     * @see UnmarshallerHandler
     */
    public UnmarshallerHandler getUnmarshallerHandler();

    /**
     * Specifies whether or not the default validation mechanism of the
     * <tt>Unmarshaller</tt> should validate during unmarshal operations.
     * By default, the <tt>Unmarshaller</tt> does not validate.
     * <p>
     * This method may only be invoked before or after calling one of the
     * unmarshal methods.
     * <p>
     * This method only controls the JAXB Provider's default unmarshal-time
     * validation mechanism - it has no impact on clients that specify their
     * own validating SAX 2.0 compliant parser.  Clients that specify their
     * own unmarshal-time validation mechanism may wish to turn off the JAXB
     * Provider's default validation mechanism via this API to avoid "double
     * validation".
     * <p>
     * This method is deprecated as of JAXB 2.0 - please use the new
     * {@link #setSchema(javax.xml.validation.Schema)} API.
     *
     * @param validating true if the Unmarshaller should validate during
     *        unmarshal, false otherwise
     * @throws JAXBException if an error occurred while enabling or disabling
     *         validation at unmarshal time
     * @throws UnsupportedOperationException could be thrown if this method is
     *         invoked on an Unmarshaller created from a JAXBContext referencing
     *         JAXB 2.0 mapped classes
     * @deprecated since JAXB2.0, please see {@link #setSchema(javax.xml.validation.Schema)}
     */
    public void setValidating( boolean validating )
        throws JAXBException;

    /**
     * Indicates whether or not the <tt>Unmarshaller</tt> is configured to
     * validate during unmarshal operations.
     * <p>
     * This API returns the state of the JAXB Provider's default unmarshal-time
     * validation mechanism.
     * <p>
     * This method is deprecated as of JAXB 2.0 - please use the new
     * {@link #getSchema()} API.
     *
     * @return true if the Unmarshaller is configured to validate during
     *         unmarshal operations, false otherwise
     * @throws JAXBException if an error occurs while retrieving the validating
     *         flag
     * @throws UnsupportedOperationException could be thrown if this method is
     *         invoked on an Unmarshaller created from a JAXBContext referencing
     *         JAXB 2.0 mapped classes
     * @deprecated since JAXB2.0, please see {@link #getSchema()}
     */
    public boolean isValidating()
        throws JAXBException;

    /**
     * Allow an application to register a <tt>ValidationEventHandler</tt>.
     * <p>
     * The <tt>ValidationEventHandler</tt> will be called by the JAXB Provider
     * if any validation errors are encountered during calls to any of the
     * unmarshal methods.  If the client application does not register a
     * <tt>ValidationEventHandler</tt> before invoking the unmarshal methods,
     * then <tt>ValidationEvents</tt> will be handled by the default event
     * handler which will terminate the unmarshal operation after the first
     * error or fatal error is encountered.
     * <p>
     * Calling this method with a null parameter will cause the Unmarshaller
     * to revert back to the default event handler.
     *
     * @param handler the validation event handler
     * @throws JAXBException if an error was encountered while setting the
     *         event handler
     */
    public void setEventHandler( ValidationEventHandler handler )
        throws JAXBException;

    /**
     * Return the current event handler or the default event handler if one
     * hasn't been set.
     *
     * @return the current ValidationEventHandler or the default event handler
     *         if it hasn't been set
     * @throws JAXBException if an error was encountered while getting the
     *         current event handler
     */
    public ValidationEventHandler getEventHandler()
        throws JAXBException;

    /**
     * Set the particular property in the underlying implementation of
     * <tt>Unmarshaller</tt>.  This method can only be used to set one of
     * the standard JAXB defined properties above or a provider specific
     * property.  Attempting to set an undefined property will result in
     * a PropertyException being thrown.  See <a href="#supportedProps">
     * Supported Properties</a>.
     *
     * @param name the name of the property to be set. This value can either
     *              be specified using one of the constant fields or a user
     *              supplied string.
     * @param value the value of the property to be set
     *
     * @throws PropertyException when there is an error processing the given
     *                            property or value
     * @throws IllegalArgumentException
     *      If the name parameter is null
     */
    public void setProperty( String name, Object value )
        throws PropertyException;

    /**
     * Get the particular property in the underlying implementation of
     * <tt>Unmarshaller</tt>.  This method can only be used to get one of
     * the standard JAXB defined properties above or a provider specific
     * property.  Attempting to get an undefined property will result in
     * a PropertyException being thrown.  See <a href="#supportedProps">
     * Supported Properties</a>.
     *
     * @param name the name of the property to retrieve
     * @return the value of the requested property
     *
     * @throws PropertyException
     *      when there is an error retrieving the given property or value
     *      property name
     * @throws IllegalArgumentException
     *      If the name parameter is null
     */
    public Object getProperty( String name ) throws PropertyException;

    /**
     * Specify the JAXP 1.3 {@link javax.xml.validation.Schema Schema}
     * object that should be used to validate subsequent unmarshal operations
     * against.  Passing null into this method will disable validation.
     * <p>
     * This method replaces the deprecated {@link #setValidating(boolean) setValidating(boolean)}
     * API.
     *
     * <p>
     * Initially this property is set to <tt>null</tt>.
     *
     * @param schema Schema object to validate unmarshal operations against or null to disable validation
     * @throws UnsupportedOperationException could be thrown if this method is
     *         invoked on an Unmarshaller created from a JAXBContext referencing
     *         JAXB 1.0 mapped classes
     * @since JAXB2.0
     */
    public void setSchema( javax.xml.validation.Schema schema );

    /**
     * Get the JAXP 1.3 {@link javax.xml.validation.Schema Schema} object
     * being used to perform unmarshal-time validation.  If there is no
     * Schema set on the unmarshaller, then this method will return null
     * indicating that unmarshal-time validation will not be performed.
     * <p>
     * This method provides replacement functionality for the deprecated
     * {@link #isValidating()} API as well as access to the Schema object.
     * To determine if the Unmarshaller has validation enabled, simply
     * test the return type for null:
     * <p>
     * <code>
     *   boolean isValidating = u.getSchema()!=null;
     * </code>
     *
     * @return the Schema object being used to perform unmarshal-time
     *      validation or null if not present
     * @throws UnsupportedOperationException could be thrown if this method is
     *         invoked on an Unmarshaller created from a JAXBContext referencing
     *         JAXB 1.0 mapped classes
     * @since JAXB2.0
     */
    public javax.xml.validation.Schema getSchema();

    /**
     * Associates a configured instance of {@link XmlAdapter} with this unmarshaller.
     *
     * <p>
     * This is a convenience method that invokes <code>setAdapter(adapter.getClass(),adapter);</code>.
     *
     * @see #setAdapter(Class,XmlAdapter)
     * @throws IllegalArgumentException
     *      if the adapter parameter is null.
     * @throws UnsupportedOperationException
     *      if invoked agains a JAXB 1.0 implementation.
     * @since JAXB2.0
     */
    public void setAdapter( XmlAdapter adapter );

    /**
     * Associates a configured instance of {@link XmlAdapter} with this unmarshaller.
     *
     * <p>
     * Every unmarshaller internally maintains a
     * {@link java.util.Map}&lt;{@link Class},{@link XmlAdapter}>,
     * which it uses for unmarshalling classes whose fields/methods are annotated
     * with {@link javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter}.
     *
     * <p>
     * This method allows applications to use a configured instance of {@link XmlAdapter}.
     * When an instance of an adapter is not given, an unmarshaller will create
     * one by invoking its default constructor.
     *
     * @param type
     *      The type of the adapter. The specified instance will be used when
     *      {@link javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter#value()}
     *      refers to this type.
     * @param adapter
     *      The instance of the adapter to be used. If null, it will un-register
     *      the current adapter set for this type.
     * @throws IllegalArgumentException
     *      if the type parameter is null.
     * @throws UnsupportedOperationException
     *      if invoked agains a JAXB 1.0 implementation.
     * @since JAXB2.0
     */
    public <A extends XmlAdapter> void setAdapter( Class<A> type, A adapter );

    /**
     * Gets the adapter associated with the specified type.
     *
     * This is the reverse operation of the {@link #setAdapter} method.
     *
     * @throws IllegalArgumentException
     *      if the type parameter is null.
     * @throws UnsupportedOperationException
     *      if invoked agains a JAXB 1.0 implementation.
     * @since JAXB2.0
     */
    public <A extends XmlAdapter> A getAdapter( Class<A> type );

    /**
     * <p>Associate a context that resolves cid's, content-id URIs, to
     * binary data passed as attachments.</p>
     * <p/>
     * <p>Unmarshal time validation, enabled via {@link #setSchema(Schema)},
     * must be supported even when unmarshaller is performing XOP processing.
     * </p>
     *
     * @throws IllegalStateException if attempt to concurrently call this
     *                               method during a unmarshal operation.
     */
    void setAttachmentUnmarshaller(AttachmentUnmarshaller au);

    AttachmentUnmarshaller getAttachmentUnmarshaller();

    /**
     * <p/>
     * Register an instance of an implementation of this class with {@link Unmarshaller} to externally listen
     * for unmarshal events.
     * <p/>
     * <p/>
     * This class enables pre and post processing of an instance of a JAXB mapped class
     * as XML data is unmarshalled into it. The event callbacks are called when unmarshalling
     * XML content into a JAXBElement instance or a JAXB mapped class that represents a complex type definition.
     * The event callbacks are not called when unmarshalling to an instance of a
     * Java datatype that represents a simple type definition.
     * <p/>
     * <p/>
     * External listener is one of two different mechanisms for defining unmarshal event callbacks.
     * See <a href="Unmarshaller.html#unmarshalEventCallback">Unmarshal Event Callbacks</a> for an overview.
     * <p/>
     * (@link #setListener(Listener)}
     * (@link #getListener()}
     *
     * @since JAXB2.0
     */
    public static abstract class Listener {
        /**
         * <p/>
         * Callback method invoked before unmarshalling into <tt>target</tt>.
         * <p/>
         * <p/>
         * This method is invoked immediately after <tt>target</tt> was created and
         * before the unmarshalling of this object begins. Note that
         * if the class of <tt>target</tt> defines its own <tt>beforeUnmarshal</tt> method,
         * the class specific callback method is invoked before this method is invoked.
         *
         * @param target non-null instance of JAXB mapped class prior to unmarshalling into it.
         * @param parent instance of JAXB mapped class that will eventually reference <tt>target</tt>.
         *               <tt>null</tt> when <tt>target</tt> is root element.
         */
        public void beforeUnmarshal(Object target, Object parent) {
        }

        /**
         * <p/>
         * Callback method invoked after unmarshalling XML data into <tt>target</tt>.
         * <p/>
         * <p/>
         * This method is invoked after all the properties (except IDREF) are unmarshalled into <tt>target</tt>,
         * but before <tt>target</tt> is set into its <tt>parent</tt> object.
         * Note that if the class of <tt>target</tt> defines its own <tt>afterUnmarshal</tt> method,
         * the class specific callback method is invoked before this method is invoked.
         *
         * @param target non-null instance of JAXB mapped class prior to unmarshalling into it.
         * @param parent instance of JAXB mapped class that will reference <tt>target</tt>.
         *               <tt>null</tt> when <tt>target</tt> is root element.
         */
        public void afterUnmarshal(Object target, Object parent) {
        }
    }

    /**
     * <p>
     * Register unmarshal event callback {@link Listener} with this {@link Unmarshaller}.
     *
     * <p>
     * There is only one Listener per Unmarshaller. Setting a Listener replaces the previous set Listener.
     * One can unregister current Listener by setting listener to <tt>null</tt>.
     *
     * @param listener  provides unmarshal event callbacks for this {@link Unmarshaller}
     * @since JAXB2.0
     */
    public void     setListener(Listener listener);

    /**
     * <p>Return {@link Listener} registered with this {@link Unmarshaller}.
     *
     * @return registered {@link Listener} or <code>null</code> if no Listener is registered with this Unmarshaller.
     * @since JAXB2.0
     */
    public Listener getListener();
}
