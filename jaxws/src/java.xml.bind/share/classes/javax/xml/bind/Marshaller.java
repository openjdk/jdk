/*
 * Copyright (c) 2003, 2015, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.bind;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.attachment.AttachmentMarshaller;
import javax.xml.validation.Schema;
import java.io.File;

/**
 * <p>
 * The {@code Marshaller} class is responsible for governing the process
 * of serializing Java content trees back into XML data.  It provides the basic
 * marshalling methods:
 *
 * <p>
 * <i>Assume the following setup code for all following code fragments:</i>
 * <blockquote>
 *    <pre>
 *       JAXBContext jc = JAXBContext.newInstance( "com.acme.foo" );
 *       Unmarshaller u = jc.createUnmarshaller();
 *       Object element = u.unmarshal( new File( "foo.xml" ) );
 *       Marshaller m = jc.createMarshaller();
 *    </pre>
 * </blockquote>
 *
 * <p>
 * Marshalling to a File:
 * <blockquote>
 *    <pre>
 *       OutputStream os = new FileOutputStream( "nosferatu.xml" );
 *       m.marshal( element, os );
 *    </pre>
 * </blockquote>
 *
 * <p>
 * Marshalling to a SAX ContentHandler:
 * <blockquote>
 *    <pre>
 *       // assume MyContentHandler instanceof ContentHandler
 *       m.marshal( element, new MyContentHandler() );
 *    </pre>
 * </blockquote>
 *
 * <p>
 * Marshalling to a DOM Node:
 * <blockquote>
 *    <pre>
 *       DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
 *       dbf.setNamespaceAware(true);
 *       DocumentBuilder db = dbf.newDocumentBuilder();
 *       Document doc = db.newDocument();
 *
 *       m.marshal( element, doc );
 *    </pre>
 * </blockquote>
 *
 * <p>
 * Marshalling to a java.io.OutputStream:
 * <blockquote>
 *    <pre>
 *       m.marshal( element, System.out );
 *    </pre>
 * </blockquote>
 *
 * <p>
 * Marshalling to a java.io.Writer:
 * <blockquote>
 *    <pre>
 *       m.marshal( element, new PrintWriter( System.out ) );
 *    </pre>
 * </blockquote>
 *
 * <p>
 * Marshalling to a javax.xml.transform.SAXResult:
 * <blockquote>
 *    <pre>
 *       // assume MyContentHandler instanceof ContentHandler
 *       SAXResult result = new SAXResult( new MyContentHandler() );
 *
 *       m.marshal( element, result );
 *    </pre>
 * </blockquote>
 *
 * <p>
 * Marshalling to a javax.xml.transform.DOMResult:
 * <blockquote>
 *    <pre>
 *       DOMResult result = new DOMResult();
 *
 *       m.marshal( element, result );
 *    </pre>
 * </blockquote>
 *
 * <p>
 * Marshalling to a javax.xml.transform.StreamResult:
 * <blockquote>
 *    <pre>
 *       StreamResult result = new StreamResult( System.out );
 *
 *       m.marshal( element, result );
 *    </pre>
 * </blockquote>
 *
 * <p>
 * Marshalling to a javax.xml.stream.XMLStreamWriter:
 * <blockquote>
 *    <pre>
 *       XMLStreamWriter xmlStreamWriter =
 *           XMLOutputFactory.newInstance().createXMLStreamWriter( ... );
 *
 *       m.marshal( element, xmlStreamWriter );
 *    </pre>
 * </blockquote>
 *
 * <p>
 * Marshalling to a javax.xml.stream.XMLEventWriter:
 * <blockquote>
 *    <pre>
 *       XMLEventWriter xmlEventWriter =
 *           XMLOutputFactory.newInstance().createXMLEventWriter( ... );
 *
 *       m.marshal( element, xmlEventWriter );
 *    </pre>
 * </blockquote>
 *
 * <p>
 * <a name="elementMarshalling"></a>
 * <b>Marshalling content tree rooted by a JAXB element</b><br>
 * <blockquote>
 * The first parameter of the overloaded
 * {@code Marshaller.marshal(java.lang.Object, ...)} methods must be a
 * JAXB element as computed by
 * {@link JAXBIntrospector#isElement(java.lang.Object)};
 * otherwise, a {@code Marshaller.marshal} method must throw a
 * {@link MarshalException}. There exist two mechanisms
 * to enable marshalling an instance that is not a JAXB element.
 * One method is to wrap the instance as a value of a {@link JAXBElement},
 * and pass the wrapper element as the first parameter to
 * a {@code Marshaller.marshal} method. For java to schema binding, it
 * is also possible to simply annotate the instance's class with
 * &#64;{@link XmlRootElement}.
 * </blockquote>
 *
 * <p>
 * <b>Encoding</b><br>
 * <blockquote>
 * By default, the Marshaller will use UTF-8 encoding when generating XML data
 * to a {@code java.io.OutputStream}, or a {@code java.io.Writer}.  Use the
 * {@link #setProperty(String,Object) setProperty} API to change the output
 * encoding used during these marshal operations.  Client applications are
 * expected to supply a valid character encoding name as defined in the
 * <a href="http://www.w3.org/TR/2000/REC-xml-20001006#charencoding">W3C XML 1.0
 * Recommendation</a> and supported by your Java Platform.
 * </blockquote>
 *
 * <p>
 * <b>Validation and Well-Formedness</b><br>
 * <blockquote>
 * <p>
 * Client applications are not required to validate the Java content tree prior
 * to calling any of the marshal API's.  Furthermore, there is no requirement
 * that the Java content tree be valid with respect to its original schema in
 * order to marshal it back into XML data.  Different JAXB Providers will
 * support marshalling invalid Java content trees at varying levels, however
 * all JAXB Providers must be able to marshal a valid content tree back to
 * XML data.  A JAXB Provider must throw a {@code MarshalException} when it
 * is unable to complete the marshal operation due to invalid content.  Some
 * JAXB Providers will fully allow marshalling invalid content, others will fail
 * on the first validation error.
 * <p>
 * Even when schema validation is not explictly enabled for the marshal operation,
 * it is possible that certain types of validation events will be detected
 * during the operation.  Validation events will be reported to the registered
 * event handler.  If the client application has not registered an event handler
 * prior to invoking one of the marshal API's, then events will be delivered to
 * a default event handler which will terminate the marshal operation after
 * encountering the first error or fatal error. Note that for JAXB 2.0 and
 * later versions, {@link javax.xml.bind.helpers.DefaultValidationEventHandler} is
 * no longer used.
 *
 * </blockquote>
 *
 * <p>
 * <a name="supportedProps"></a>
 * <b>Supported Properties</b><br>
 * <blockquote>
 * <p>
 * All JAXB Providers are required to support the following set of properties.
 * Some providers may support additional properties.
 * <dl>
 *   <dt>{@code jaxb.encoding} - value must be a java.lang.String</dt>
 *   <dd>The output encoding to use when marshalling the XML data.  The
 *               Marshaller will use "UTF-8" by default if this property is not
 *       specified.</dd>
 *   <dt>{@code jaxb.formatted.output} - value must be a java.lang.Boolean</dt>
 *   <dd>This property controls whether or not the Marshaller will format
 *       the resulting XML data with line breaks and indentation.  A
 *       true value for this property indicates human readable indented
 *       xml data, while a false value indicates unformatted xml data.
 *       The Marshaller will default to false (unformatted) if this
 *       property is not specified.</dd>
 *   <dt>{@code jaxb.schemaLocation} - value must be a java.lang.String</dt>
 *   <dd>This property allows the client application to specify an
 *       xsi:schemaLocation attribute in the generated XML data.  The format of
 *       the schemaLocation attribute value is discussed in an easy to
 *       understand, non-normative form in
 *       <a href="http://www.w3.org/TR/xmlschema-0/#schemaLocation">Section 5.6
 *       of the W3C XML Schema Part 0: Primer</a> and specified in
 *       <a href="http://www.w3.org/TR/xmlschema-1/#Instance_Document_Constructions">
 *       Section 2.6 of the W3C XML Schema Part 1: Structures</a>.</dd>
 *   <dt>{@code jaxb.noNamespaceSchemaLocation} - value must be a java.lang.String</dt>
 *   <dd>This property allows the client application to specify an
 *       xsi:noNamespaceSchemaLocation attribute in the generated XML
 *       data.  The format of the schemaLocation attribute value is discussed in
 *       an easy to understand, non-normative form in
 *       <a href="http://www.w3.org/TR/xmlschema-0/#schemaLocation">Section 5.6
 *       of the W3C XML Schema Part 0: Primer</a> and specified in
 *       <a href="http://www.w3.org/TR/xmlschema-1/#Instance_Document_Constructions">
 *       Section 2.6 of the W3C XML Schema Part 1: Structures</a>.</dd>
 *   <dt>{@code jaxb.fragment} - value must be a java.lang.Boolean</dt>
 *   <dd>This property determines whether or not document level events will be
 *       generated by the Marshaller.  If the property is not specified, the
 *       default is {@code false}. This property has different implications depending
 *       on which marshal api you are using - when this property is set to true:<br>
 *       <ul>
 *         <li>{@link #marshal(Object,org.xml.sax.ContentHandler) marshal(Object,ContentHandler)} - the Marshaller won't
 *             invoke {@link org.xml.sax.ContentHandler#startDocument()} and
 *             {@link org.xml.sax.ContentHandler#endDocument()}.</li>
 *         <li>{@link #marshal(Object,org.w3c.dom.Node) marshal(Object,Node)} - the property has no effect on this
 *             API.</li>
 *         <li>{@link #marshal(Object,java.io.OutputStream) marshal(Object,OutputStream)} - the Marshaller won't
 *             generate an xml declaration.</li>
 *         <li>{@link #marshal(Object,java.io.Writer) marshal(Object,Writer)} - the Marshaller won't
 *             generate an xml declaration.</li>
 *         <li>{@link #marshal(Object,javax.xml.transform.Result) marshal(Object,Result)} - depends on the kind of
 *             Result object, see semantics for Node, ContentHandler, and Stream APIs</li>
 *         <li>{@link #marshal(Object,javax.xml.stream.XMLEventWriter) marshal(Object,XMLEventWriter)} - the
 *             Marshaller will not generate {@link javax.xml.stream.events.XMLEvent#START_DOCUMENT} and
 *             {@link javax.xml.stream.events.XMLEvent#END_DOCUMENT} events.</li>
 *         <li>{@link #marshal(Object,javax.xml.stream.XMLStreamWriter) marshal(Object,XMLStreamWriter)} - the
 *             Marshaller will not generate {@link javax.xml.stream.events.XMLEvent#START_DOCUMENT} and
 *             {@link javax.xml.stream.events.XMLEvent#END_DOCUMENT} events.</li>
 *       </ul>
 *   </dd>
 * </dl>
 * </blockquote>
 *
 * <p>
 * <a name="marshalEventCallback"></a>
 * <b>Marshal Event Callbacks</b><br>
 * <blockquote>
 * "The {@link Marshaller} provides two styles of callback mechanisms
 * that allow application specific processing during key points in the
 * unmarshalling process.  In 'class defined' event callbacks, application
 * specific code placed in JAXB mapped classes is triggered during
 * marshalling.  'External listeners' allow for centralized processing
 * of marshal events in one callback method rather than by type event callbacks.
 *
 * <p>
 * Class defined event callback methods allow any JAXB mapped class to specify
 * its own specific callback methods by defining methods with the following method signatures:
 * <blockquote>
 * <pre>
 *   // Invoked by Marshaller after it has created an instance of this object.
 *   boolean beforeMarshal(Marshaller);
 *
 *   // Invoked by Marshaller after it has marshalled all properties of this object.
 *   void afterMarshal(Marshaller);
 * </pre>
 * </blockquote>
 * The class defined event callback methods should be used when the callback method requires
 * access to non-public methods and/or fields of the class.
 * <p>
 * The external listener callback mechanism enables the registration of a {@link Listener}
 * instance with a {@link Marshaller#setListener(Listener)}. The external listener receives all callback events,
 * allowing for more centralized processing than per class defined callback methods.
 * <p>
 * The 'class defined' and external listener event callback methods are independent of each other,
 * both can be called for one event. The invocation ordering when both listener callback methods exist is
 * defined in {@link Listener#beforeMarshal(Object)} and {@link Listener#afterMarshal(Object)}.
 * <p>
 * An event callback method throwing an exception terminates the current marshal process.
 * </blockquote>
 *
 * @author <ul><li>Kohsuke Kawaguchi, Sun Microsystems, Inc.</li><li>Ryan Shoemaker, Sun Microsystems, Inc.</li><li>Joe Fialli, Sun Microsystems, Inc.</li></ul>
 * @see JAXBContext
 * @see Validator
 * @see Unmarshaller
 * @since 1.6, JAXB 1.0
 */
public interface Marshaller {

    /**
     * The name of the property used to specify the output encoding in
     * the marshalled XML data.
     */
    public static final String JAXB_ENCODING =
        "jaxb.encoding";

    /**
     * The name of the property used to specify whether or not the marshalled
     * XML data is formatted with linefeeds and indentation.
     */
    public static final String JAXB_FORMATTED_OUTPUT =
        "jaxb.formatted.output";

    /**
     * The name of the property used to specify the xsi:schemaLocation
     * attribute value to place in the marshalled XML output.
     */
    public static final String JAXB_SCHEMA_LOCATION =
        "jaxb.schemaLocation";

    /**
     * The name of the property used to specify the
     * xsi:noNamespaceSchemaLocation attribute value to place in the marshalled
     * XML output.
     */
    public static final String JAXB_NO_NAMESPACE_SCHEMA_LOCATION =
        "jaxb.noNamespaceSchemaLocation";

    /**
     * The name of the property used to specify whether or not the marshaller
     * will generate document level events (ie calling startDocument or endDocument).
     */
    public static final String JAXB_FRAGMENT =
        "jaxb.fragment";

    /**
     * Marshal the content tree rooted at {@code jaxbElement} into the specified
     * {@code javax.xml.transform.Result}.
     *
     * <p>
     * All JAXB Providers must at least support
     * {@link javax.xml.transform.dom.DOMResult},
     * {@link javax.xml.transform.sax.SAXResult}, and
     * {@link javax.xml.transform.stream.StreamResult}. It can
     * support other derived classes of {@code Result} as well.
     *
     * @param jaxbElement
     *      The root of content tree to be marshalled.
     * @param result
     *      XML will be sent to this Result
     *
     * @throws JAXBException
     *      If any unexpected problem occurs during the marshalling.
     * @throws MarshalException
     *      If the {@link ValidationEventHandler ValidationEventHandler}
     *      returns false from its {@code handleEvent} method or the
     *      {@code Marshaller} is unable to marshal {@code jaxbElement} (or any
     *      object reachable from {@code jaxbElement}).  See <a href="#elementMarshalling">
     *      Marshalling a JAXB element</a>.
     * @throws IllegalArgumentException
     *      If any of the method parameters are null
     */
    public void marshal( Object jaxbElement, javax.xml.transform.Result result )
        throws JAXBException;

    /**
     * Marshal the content tree rooted at {@code jaxbElement} into an output stream.
     *
     * @param jaxbElement
     *      The root of content tree to be marshalled.
     * @param os
     *      XML will be added to this stream.
     *
     * @throws JAXBException
     *      If any unexpected problem occurs during the marshalling.
     * @throws MarshalException
     *      If the {@link ValidationEventHandler ValidationEventHandler}
     *      returns false from its {@code handleEvent} method or the
     *      {@code Marshaller} is unable to marshal {@code jaxbElement} (or any
     *      object reachable from {@code jaxbElement}).  See <a href="#elementMarshalling">
     *      Marshalling a JAXB element</a>.
     * @throws IllegalArgumentException
     *      If any of the method parameters are null
     */
    public void marshal( Object jaxbElement, java.io.OutputStream os )
        throws JAXBException;

    /**
     * Marshal the content tree rooted at {@code jaxbElement} into a file.
     *
     * @param jaxbElement
     *      The root of content tree to be marshalled.
     * @param output
     *      File to be written. If this file already exists, it will be overwritten.
     *
     * @throws JAXBException
     *      If any unexpected problem occurs during the marshalling.
     * @throws MarshalException
     *      If the {@link ValidationEventHandler ValidationEventHandler}
     *      returns false from its {@code handleEvent} method or the
     *      {@code Marshaller} is unable to marshal {@code jaxbElement} (or any
     *      object reachable from {@code jaxbElement}).  See <a href="#elementMarshalling">
     *      Marshalling a JAXB element</a>.
     * @throws IllegalArgumentException
     *      If any of the method parameters are null
     * @since 1.6, JAXB 2.1
     */
    public void marshal( Object jaxbElement, File output )
        throws JAXBException;

    /**
     * Marshal the content tree rooted at {@code jaxbElement} into a Writer.
     *
     * @param jaxbElement
     *      The root of content tree to be marshalled.
     * @param writer
     *      XML will be sent to this writer.
     *
     * @throws JAXBException
     *      If any unexpected problem occurs during the marshalling.
     * @throws MarshalException
     *      If the {@link ValidationEventHandler ValidationEventHandler}
     *      returns false from its {@code handleEvent} method or the
     *      {@code Marshaller} is unable to marshal {@code jaxbElement} (or any
     *      object reachable from {@code jaxbElement}).  See <a href="#elementMarshalling">
     *      Marshalling a JAXB element</a>.
     * @throws IllegalArgumentException
     *      If any of the method parameters are null
     */
    public void marshal( Object jaxbElement, java.io.Writer writer )
        throws JAXBException;

    /**
     * Marshal the content tree rooted at {@code jaxbElement} into SAX2 events.
     *
     * @param jaxbElement
     *      The root of content tree to be marshalled.
     * @param handler
     *      XML will be sent to this handler as SAX2 events.
     *
     * @throws JAXBException
     *      If any unexpected problem occurs during the marshalling.
     * @throws MarshalException
     *      If the {@link ValidationEventHandler ValidationEventHandler}
     *      returns false from its {@code handleEvent} method or the
     *      {@code Marshaller} is unable to marshal {@code jaxbElement} (or any
     *      object reachable from {@code jaxbElement}).  See <a href="#elementMarshalling">
     *      Marshalling a JAXB element</a>.
     * @throws IllegalArgumentException
     *      If any of the method parameters are null
     */
    public void marshal( Object jaxbElement, org.xml.sax.ContentHandler handler )
        throws JAXBException;

    /**
     * Marshal the content tree rooted at {@code jaxbElement} into a DOM tree.
     *
     * @param jaxbElement
     *      The content tree to be marshalled.
     * @param node
     *      DOM nodes will be added as children of this node.
     *      This parameter must be a Node that accepts children
     *      ({@link org.w3c.dom.Document},
     *      {@link  org.w3c.dom.DocumentFragment}, or
     *      {@link  org.w3c.dom.Element})
     *
     * @throws JAXBException
     *      If any unexpected problem occurs during the marshalling.
     * @throws MarshalException
     *      If the {@link ValidationEventHandler ValidationEventHandler}
     *      returns false from its {@code handleEvent} method or the
     *      {@code Marshaller} is unable to marshal {@code jaxbElement} (or any
     *      object reachable from {@code jaxbElement}).  See <a href="#elementMarshalling">
     *      Marshalling a JAXB element</a>.
     * @throws IllegalArgumentException
     *      If any of the method parameters are null
     */
    public void marshal( Object jaxbElement, org.w3c.dom.Node node )
        throws JAXBException;

    /**
     * Marshal the content tree rooted at {@code jaxbElement} into a
     * {@link javax.xml.stream.XMLStreamWriter}.
     *
     * @param jaxbElement
     *      The content tree to be marshalled.
     * @param writer
     *      XML will be sent to this writer.
     *
     * @throws JAXBException
     *      If any unexpected problem occurs during the marshalling.
     * @throws MarshalException
     *      If the {@link ValidationEventHandler ValidationEventHandler}
     *      returns false from its {@code handleEvent} method or the
     *      {@code Marshaller} is unable to marshal {@code jaxbElement} (or any
     *      object reachable from {@code jaxbElement}).  See <a href="#elementMarshalling">
     *      Marshalling a JAXB element</a>.
     * @throws IllegalArgumentException
     *      If any of the method parameters are null
     * @since 1.6, JAXB 2.0
     */
    public void marshal( Object jaxbElement, javax.xml.stream.XMLStreamWriter writer )
        throws JAXBException;

    /**
     * Marshal the content tree rooted at {@code jaxbElement} into a
     * {@link javax.xml.stream.XMLEventWriter}.
     *
     * @param jaxbElement
     *      The content tree rooted at jaxbElement to be marshalled.
     * @param writer
     *      XML will be sent to this writer.
     *
     * @throws JAXBException
     *      If any unexpected problem occurs during the marshalling.
     * @throws MarshalException
     *      If the {@link ValidationEventHandler ValidationEventHandler}
     *      returns false from its {@code handleEvent} method or the
     *      {@code Marshaller} is unable to marshal {@code jaxbElement} (or any
     *      object reachable from {@code jaxbElement}).  See <a href="#elementMarshalling">
     *      Marshalling a JAXB element</a>.
     * @throws IllegalArgumentException
     *      If any of the method parameters are null
     * @since 1.6, JAXB 2.0
     */
    public void marshal( Object jaxbElement, javax.xml.stream.XMLEventWriter writer )
        throws JAXBException;

    /**
     * Get a DOM tree view of the content tree(Optional).
     *
     * If the returned DOM tree is updated, these changes are also
     * visible in the content tree.
     * Use {@link #marshal(Object, org.w3c.dom.Node)} to force
     * a deep copy of the content tree to a DOM representation.
     *
     * @param contentTree - JAXB Java representation of XML content
     *
     * @return the DOM tree view of the contentTree
     *
     * @throws UnsupportedOperationException
     *      If the JAXB provider implementation does not support a
     *      DOM view of the content tree
     *
     * @throws IllegalArgumentException
     *      If any of the method parameters are null
     *
     * @throws JAXBException
     *      If any unexpected problem occurs
     *
     */
    public org.w3c.dom.Node getNode( java.lang.Object contentTree )
        throws JAXBException;

    /**
     * Set the particular property in the underlying implementation of
     * {@code Marshaller}.  This method can only be used to set one of
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
     * {@code Marshaller}.  This method can only be used to get one of
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
     * Allow an application to register a validation event handler.
     * <p>
     * The validation event handler will be called by the JAXB Provider if any
     * validation errors are encountered during calls to any of the marshal
     * API's.  If the client application does not register a validation event
     * handler before invoking one of the marshal methods, then validation
     * events will be handled by the default event handler which will terminate
     * the marshal operation after the first error or fatal error is encountered.
     * <p>
     * Calling this method with a null parameter will cause the Marshaller
     * to revert back to the default default event handler.
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
     * Associates a configured instance of {@link XmlAdapter} with this marshaller.
     *
     * <p>
     * This is a convenience method that invokes {@code setAdapter(adapter.getClass(),adapter);}.
     *
     * @see #setAdapter(Class,XmlAdapter)
     * @throws IllegalArgumentException
     *      if the adapter parameter is null.
     * @throws UnsupportedOperationException
     *      if invoked agains a JAXB 1.0 implementation.
     * @since 1.6, JAXB 2.0
     */
    public void setAdapter( XmlAdapter adapter );

    /**
     * Associates a configured instance of {@link XmlAdapter} with this marshaller.
     *
     * <p>
     * Every marshaller internally maintains a
     * {@link java.util.Map}&lt;{@link Class},{@link XmlAdapter}&gt;,
     * which it uses for marshalling classes whose fields/methods are annotated
     * with {@link javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter}.
     *
     * <p>
     * This method allows applications to use a configured instance of {@link XmlAdapter}.
     * When an instance of an adapter is not given, a marshaller will create
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
     * @since 1.6, JAXB 2.0
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
     * @since 1.6, JAXB 2.0
     */
    public <A extends XmlAdapter> A getAdapter( Class<A> type );


    /**
     * <p>Associate a context that enables binary data within an XML document
     * to be transmitted as XML-binary optimized attachment.
     * The attachment is referenced from the XML document content model
     * by content-id URIs(cid) references stored within the xml document.
     *
     * @throws IllegalStateException if attempt to concurrently call this
     *                               method during a marshal operation.
     */
    void setAttachmentMarshaller(AttachmentMarshaller am);

    AttachmentMarshaller getAttachmentMarshaller();

    /**
     * Specify the JAXP 1.3 {@link javax.xml.validation.Schema Schema}
     * object that should be used to validate subsequent marshal operations
     * against.  Passing null into this method will disable validation.
     *
     * <p>
     * This method allows the caller to validate the marshalled XML as it's marshalled.
     *
     * <p>
     * Initially this property is set to {@code null}.
     *
     * @param schema Schema object to validate marshal operations against or null to disable validation
     * @throws UnsupportedOperationException could be thrown if this method is
     *         invoked on an Marshaller created from a JAXBContext referencing
     *         JAXB 1.0 mapped classes
     * @since 1.6, JAXB 2.0
     */
    public void setSchema( Schema schema );

    /**
     * Get the JAXP 1.3 {@link javax.xml.validation.Schema Schema} object
     * being used to perform marshal-time validation.  If there is no
     * Schema set on the marshaller, then this method will return null
     * indicating that marshal-time validation will not be performed.
     *
     * @return the Schema object being used to perform marshal-time
     *      validation or null if not present.
     * @throws UnsupportedOperationException could be thrown if this method is
     *         invoked on an Marshaller created from a JAXBContext referencing
     *         JAXB 1.0 mapped classes
     * @since 1.6, JAXB 2.0
     */
    public Schema getSchema();

    /**
     * <p>
     * Register an instance of an implementation of this class with a {@link Marshaller} to externally listen
     * for marshal events.
     * </p>
     * <p>
     * This class enables pre and post processing of each marshalled object.
     * The event callbacks are called when marshalling from an instance that maps to an xml element or
     * complex type definition. The event callbacks are not called when marshalling from an instance of a
     * Java datatype that represents a simple type definition.
     * </p>
     * <p>
     * External listener is one of two different mechanisms for defining marshal event callbacks.
     * See <a href="Marshaller.html#marshalEventCallback">Marshal Event Callbacks</a> for an overview.
     *
     * @see Marshaller#setListener(Listener)
     * @see Marshaller#getListener()
     * @since 1.6, JAXB 2.0
     */
    public static abstract class Listener {
        /**
         * <p>
         * Callback method invoked before marshalling from {@code source} to XML.
         * </p>
         * <p>
         * This method is invoked just before marshalling process starts to marshal {@code source}.
         * Note that if the class of {@code source} defines its own {@code beforeMarshal} method,
         * the class specific callback method is invoked just before this method is invoked.
         *
         * @param source instance of JAXB mapped class prior to marshalling from it.
         */
        public void beforeMarshal(Object source) {
        }

        /**
         * <p>
         * Callback method invoked after marshalling {@code source} to XML.
         * </p>
         * <p>
         * This method is invoked after {@code source} and all its descendants have been marshalled.
         * Note that if the class of {@code source} defines its own {@code afterMarshal} method,
         * the class specific callback method is invoked just before this method is invoked.
         *
         * @param source instance of JAXB mapped class after marshalling it.
         */
        public void afterMarshal(Object source) {
        }
    }

    /**
     * <p>
     * Register marshal event callback {@link Listener} with this {@link Marshaller}.
     *
     * <p>
     * There is only one Listener per Marshaller. Setting a Listener replaces the previous set Listener.
     * One can unregister current Listener by setting listener to {@code null}.
     *
     * @param listener an instance of a class that implements {@link Listener}
     * @since 1.6, JAXB 2.0
     */
    public void setListener(Listener listener);

    /**
     * <p>Return {@link Listener} registered with this {@link Marshaller}.
     *
     * @return registered {@link Listener} or {@code null}
     *         if no Listener is registered with this Marshaller.
     * @since 1.6, JAXB 2.0
     */
    public Listener getListener();
}
