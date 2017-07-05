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

/**
 * A basic event handler interface for validation errors.
 *
 * <p>
 * If an application needs to implement customized event handling, it must
 * implement this interface and then register it with either the
 * {@link Unmarshaller#setEventHandler(ValidationEventHandler) Unmarshaller},
 * the {@link Validator#setEventHandler(ValidationEventHandler) Validator}, or
 * the {@link Marshaller#setEventHandler(ValidationEventHandler) Marshaller}.
 * The JAXB Provider will then report validation errors and warnings encountered
 * during the unmarshal, marshal, and validate operations to these event
 * handlers.
 *
 * <p>
 * If the <tt>handleEvent</tt> method throws an unchecked runtime exception,
 * the JAXB Provider must treat that as if the method returned false, effectively
 * terminating whatever operation was in progress at the time (unmarshal,
 * validate, or marshal).
 *
 * <p>
 * Modifying the Java content tree within your event handler is undefined
 * by the specification and may result in unexpected behaviour.
 *
 * <p>
 * Failing to return false from the <tt>handleEvent</tt> method after
 * encountering a fatal error is undefined by the specification and may result
 * in unexpected behavior.
 *
 * <p>
 * <b>Default Event Handler</b>
 * <blockquote>
 *    See: <a href="Validator.html#defaulthandler">Validator javadocs</a>
 * </blockquote>
 *
 * @author <ul><li>Ryan Shoemaker, Sun Microsystems, Inc.</li><li>Kohsuke Kawaguchi, Sun Microsystems, Inc.</li><li>Joe Fialli, Sun Microsystems, Inc.</li></ul>
 * @version $Revision: 1.2 $
 * @see Unmarshaller
 * @see Validator
 * @see Marshaller
 * @see ValidationEvent
 * @see javax.xml.bind.util.ValidationEventCollector
 * @since JAXB1.0
 */
public interface ValidationEventHandler {
    /**
     * Receive notification of a validation warning or error.
     *
     * The ValidationEvent will have a
     * {@link ValidationEventLocator ValidationEventLocator} embedded in it that
     * indicates where the error or warning occurred.
     *
     * <p>
     * If an unchecked runtime exception is thrown from this method, the JAXB
     * provider will treat it as if the method returned false and interrupt
     * the current unmarshal, validate, or marshal operation.
     *
     * @param event the encapsulated validation event information.  It is a
     * provider error if this parameter is null.
     * @return true if the JAXB Provider should attempt to continue the current
     *         unmarshal, validate, or marshal operation after handling this
     *         warning/error, false if the provider should terminate the current
     *         operation with the appropriate <tt>UnmarshalException</tt>,
     *         <tt>ValidationException</tt>, or <tt>MarshalException</tt>.
     * @throws IllegalArgumentException if the event object is null.
     */
    public boolean handleEvent( ValidationEvent event );

}
