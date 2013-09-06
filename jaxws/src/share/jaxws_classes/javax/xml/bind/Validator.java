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

package javax.xml.bind;

/**
 * As of JAXB 2.0, this class is deprecated and optional.
 * <p>
 * The <tt>Validator</tt> class is responsible for controlling the validation
 * of content trees during runtime.
 *
 * <p>
 * <a name="validationtypes"></a>
 * <b>Three Forms of Validation</b><br>
 * <blockquote>
 *    <dl>
 *        <dt><b>Unmarshal-Time Validation</b></dt>
 *        <dd>This form of validation enables a client application to receive
 *            information about validation errors and warnings detected while
 *            unmarshalling XML data into a Java content tree and is completely
 *            orthogonal to the other types of validation.  To enable or disable
 *            it, see the javadoc for
 *            {@link Unmarshaller#setValidating(boolean) Unmarshaller.setValidating}.
 *            All JAXB 1.0 Providers are required to support this operation.
 *        </dd>
 *
 *        <dt><b>On-Demand Validation</b></dt>
 *        <dd> This form of validation enables a client application to receive
 *             information about validation errors and warnings detected in the
 *             Java content tree.  At any point, client applications can call
 *             the {@link Validator#validate(Object) Validator.validate} method
 *             on the Java content tree (or any sub-tree of it).  All JAXB 1.0
 *             Providers are required to support this operation.
 *        </dd>
 *
 *        <dt><b>Fail-Fast Validation</b></dt>
 *        <dd> This form of validation enables a client application to receive
 *             immediate feedback about modifications to the Java content tree
 *             that violate type constraints on Java Properties as defined in
 *             the specification.  JAXB Providers are not required support
 *             this type of validation.  Of the JAXB Providers that do support
 *             this type of validation, some may require you to decide at schema
 *             compile time whether or not a client application will be allowed
 *             to request fail-fast validation at runtime.
 *        </dd>
 *    </dl>
 * </blockquote>
 *
 * <p>
 * The <tt>Validator</tt> class is responsible for managing On-Demand Validation.
 * The <tt>Unmarshaller</tt> class is responsible for managing Unmarshal-Time
 * Validation during the unmarshal operations.  Although there is no formal
 * method of enabling validation during the marshal operations, the
 * <tt>Marshaller</tt> may detect errors, which will be reported to the
 * <tt>ValidationEventHandler</tt> registered on it.
 *
 * <p>
 * <a name="defaulthandler"></a>
 * <b>Using the Default EventHandler</b><br>
 * <blockquote>
 *   If the client application does not set an event handler on their
 *   <tt>Validator</tt>, <tt>Unmarshaller</tt>, or <tt>Marshaller</tt> prior to
 *   calling the validate, unmarshal, or marshal methods, then a default event
 *   handler will receive notification of any errors or warnings encountered.
 *   The default event handler will cause the current operation to halt after
 *   encountering the first error or fatal error (but will attempt to continue
 *   after receiving warnings).
 * </blockquote>
 *
 * <p>
 * <a name="handlingevents"></a>
 * <b>Handling Validation Events</b><br>
 * <blockquote>
 *   There are three ways to handle events encountered during the unmarshal,
 *   validate, and marshal operations:
 *    <dl>
 *        <dt>Use the default event handler</dt>
 *        <dd>The default event handler will be used if you do not specify one
 *            via the <tt>setEventHandler</tt> API's on <tt>Validator</tt>,
 *            <tt>Unmarshaller</tt>, or <tt>Marshaller</tt>.
 *        </dd>
 *
 *        <dt>Implement and register a custom event handler</dt>
 *        <dd>Client applications that require sophisticated event processing
 *            can implement the <tt>ValidationEventHandler</tt> interface and
 *            register it with the <tt>Unmarshaller</tt> and/or
 *            <tt>Validator</tt>.
 *        </dd>
 *
 *        <dt>Use the {@link javax.xml.bind.util.ValidationEventCollector ValidationEventCollector}
 *            utility</dt>
 *        <dd>For convenience, a specialized event handler is provided that
 *            simply collects any <tt>ValidationEvent</tt> objects created
 *            during the unmarshal, validate, and marshal operations and
 *            returns them to the client application as a
 *            <tt>java.util.Collection</tt>.
 *        </dd>
 *    </dl>
 * </blockquote>
 *
 * <p>
 * <b>Validation and Well-Formedness</b><br>
 * <blockquote>
 * <p>
 * Validation events are handled differently depending on how the client
 * application is configured to process them as described in the previous
 * section.  However, there are certain cases where a JAXB Provider indicates
 * that it is no longer able to reliably detect and report errors.  In these
 * cases, the JAXB Provider will set the severity of the ValidationEvent to
 * FATAL_ERROR to indicate that the unmarshal, validate, or marshal operations
 * should be terminated.  The default event handler and
 * <tt>ValidationEventCollector</tt> utility class must terminate processing
 * after being notified of a fatal error.  Client applications that supply their
 * own <tt>ValidationEventHandler</tt> should also terminate processing after
 * being notified of a fatal error.  If not, unexpected behaviour may occur.
 * </blockquote>
 *
 * <p>
 * <a name="supportedProps"></a>
 * <b>Supported Properties</b><br>
 * <blockquote>
 * <p>
 * There currently are not any properties required to be supported by all
 * JAXB Providers on Validator.  However, some providers may support
 * their own set of provider specific properties.
 * </blockquote>
 *
 *
 * @author <ul><li>Ryan Shoemaker, Sun Microsystems, Inc.</li><li>Kohsuke Kawaguchi, Sun Microsystems, Inc.</li><li>Joe Fialli, Sun Microsystems, Inc.</li></ul>
 * @see JAXBContext
 * @see Unmarshaller
 * @see ValidationEventHandler
 * @see ValidationEvent
 * @see javax.xml.bind.util.ValidationEventCollector
 * @since JAXB1.0
 * @deprecated since JAXB 2.0
 */
public interface Validator {

    /**
     * Allow an application to register a validation event handler.
     * <p>
     * The validation event handler will be called by the JAXB Provider if any
     * validation errors are encountered during calls to
     * {@link #validate(Object) validate}.  If the client application does not
     * register a validation event handler before invoking the validate method,
     * then validation events will be handled by the default event handler which
     * will terminate the validate operation after the first error or fatal error
     * is encountered.
     * <p>
     * Calling this method with a null parameter will cause the Validator
     * to revert back to the default default event handler.
     *
     * @param handler the validation event handler
     * @throws JAXBException if an error was encountered while setting the
     *         event handler
     * @deprecated since JAXB2.0
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
     * @deprecated since JAXB2.0
     */
    public ValidationEventHandler getEventHandler()
        throws JAXBException;

    /**
     * Validate the Java content tree starting at <tt>subrootObj</tt>.
     * <p>
     * Client applications can use this method to validate Java content trees
     * on-demand at runtime.  This method can be used to validate any arbitrary
     * subtree of the Java content tree.  Global constraint checking <b>will not
     * </b> be performed as part of this operation (i.e. ID/IDREF constraints).
     *
     * @param subrootObj the obj to begin validation at
     * @throws JAXBException if any unexpected problem occurs during validation
     * @throws ValidationException
     *     If the {@link ValidationEventHandler ValidationEventHandler}
     *     returns false from its <tt>handleEvent</tt> method or the
     *     <tt>Validator</tt> is unable to validate the content tree rooted
     *     at <tt>subrootObj</tt>
     * @throws IllegalArgumentException
     *      If the subrootObj parameter is null
     * @return true if the subtree rooted at <tt>subrootObj</tt> is valid, false
     *         otherwise
     * @deprecated since JAXB2.0
     */
    public boolean validate( Object subrootObj ) throws JAXBException;

    /**
     * Validate the Java content tree rooted at <tt>rootObj</tt>.
     * <p>
     * Client applications can use this method to validate Java content trees
     * on-demand at runtime.  This method is used to validate an entire Java
     * content tree.  Global constraint checking <b>will</b> be performed as
     * part of this operation (i.e. ID/IDREF constraints).
     *
     * @param rootObj the root obj to begin validation at
     * @throws JAXBException if any unexpected problem occurs during validation
     * @throws ValidationException
     *     If the {@link ValidationEventHandler ValidationEventHandler}
     *     returns false from its <tt>handleEvent</tt> method or the
     *     <tt>Validator</tt> is unable to validate the content tree rooted
     *     at <tt>rootObj</tt>
     * @throws IllegalArgumentException
     *      If the rootObj parameter is null
     * @return true if the tree rooted at <tt>rootObj</tt> is valid, false
     *         otherwise
     * @deprecated since JAXB2.0
     */
    public boolean validateRoot( Object rootObj ) throws JAXBException;

    /**
     * Set the particular property in the underlying implementation of
     * <tt>Validator</tt>.  This method can only be used to set one of
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
     * @deprecated since JAXB2.0
     */
    public void setProperty( String name, Object value )
        throws PropertyException;

    /**
     * Get the particular property in the underlying implementation of
     * <tt>Validator</tt>.  This method can only be used to get one of
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
     * @deprecated since JAXB2.0
     */
    public Object getProperty( String name ) throws PropertyException;

}
