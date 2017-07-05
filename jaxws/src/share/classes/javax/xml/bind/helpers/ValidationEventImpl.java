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
package javax.xml.bind.helpers;

import java.text.MessageFormat;

import javax.xml.bind.ValidationEvent;
import javax.xml.bind.ValidationEventLocator;

/**
 * Default implementation of the ValidationEvent interface.
 *
 * <p>
 * JAXB providers are allowed to use whatever class that implements
 * the ValidationEvent interface. This class is just provided for a
 * convenience.
 *
 * @author <ul><li>Kohsuke Kawaguchi, Sun Microsystems, Inc.</li></ul>
 * @version $Revision: 1.2 $
 * @see javax.xml.bind.Validator
 * @see javax.xml.bind.ValidationEventHandler
 * @see javax.xml.bind.ValidationEvent
 * @see javax.xml.bind.ValidationEventLocator
 * @since JAXB1.0
 */
public class ValidationEventImpl implements ValidationEvent
{

    /**
     * Create a new ValidationEventImpl.
     *
     * @param _severity The severity value for this event.  Must be one of
     * ValidationEvent.WARNING, ValidationEvent.ERROR, or
     * ValidationEvent.FATAL_ERROR
     * @param _message The text message for this event - may be null.
     * @param _locator The locator object for this event - may be null.
     * @throws IllegalArgumentException if an illegal severity field is supplied
     */
    public ValidationEventImpl( int _severity, String _message,
                                 ValidationEventLocator _locator ) {

        this(_severity,_message,_locator,null);
    }

    /**
     * Create a new ValidationEventImpl.
     *
     * @param _severity The severity value for this event.  Must be one of
     * ValidationEvent.WARNING, ValidationEvent.ERROR, or
     * ValidationEvent.FATAL_ERROR
     * @param _message The text message for this event - may be null.
     * @param _locator The locator object for this event - may be null.
     * @param _linkedException An optional linked exception that may provide
     * additional information about the event - may be null.
     * @throws IllegalArgumentException if an illegal severity field is supplied
     */
    public ValidationEventImpl( int _severity, String _message,
                                 ValidationEventLocator _locator,
                                 Throwable _linkedException ) {

        setSeverity( _severity );
        this.message = _message;
        this.locator = _locator;
        this.linkedException = _linkedException;
    }

    private int severity;
    private String message;
    private Throwable linkedException;
    private ValidationEventLocator locator;

    public int getSeverity() {
        return severity;
    }


    /**
     * Set the severity field of this event.
     *
     * @param _severity Must be one of ValidationEvent.WARNING,
     * ValidationEvent.ERROR, or ValidationEvent.FATAL_ERROR.
     * @throws IllegalArgumentException if an illegal severity field is supplied
     */
    public void setSeverity( int _severity ) {

        if( _severity != ValidationEvent.WARNING &&
            _severity != ValidationEvent.ERROR &&
            _severity != ValidationEvent.FATAL_ERROR ) {
                throw new IllegalArgumentException(
                    Messages.format( Messages.ILLEGAL_SEVERITY ) );
        }

        this.severity = _severity;
    }

    public String getMessage() {
        return message;
    }
    /**
     * Set the message field of this event.
     *
     * @param _message String message - may be null.
     */
    public void setMessage( String _message ) {
        this.message = _message;
    }

    public Throwable getLinkedException() {
        return linkedException;
    }
    /**
     * Set the linked exception field of this event.
     *
     * @param _linkedException Optional linked exception - may be null.
     */
    public void setLinkedException( Throwable _linkedException ) {
        this.linkedException = _linkedException;
    }

    public ValidationEventLocator getLocator() {
        return locator;
    }
    /**
     * Set the locator object for this event.
     *
     * @param _locator The locator - may be null.
     */
    public void setLocator( ValidationEventLocator _locator ) {
        this.locator = _locator;
    }

    /**
     * Returns a string representation of this object in a format
     * helpful to debugging.
     *
     * @see Object#equals(Object)
     */
    public String toString() {
        String s;
        switch(getSeverity()) {
        case WARNING:   s="WARNING";break;
        case ERROR: s="ERROR";break;
        case FATAL_ERROR: s="FATAL_ERROR";break;
        default: s=String.valueOf(getSeverity());break;
        }
        return MessageFormat.format("[severity={0},message={1},locator={2}]",
            new Object[]{
                s,
                getMessage(),
                getLocator()
            });
    }
}
