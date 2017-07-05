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
/*
 * $Id: SOAPExceptionImpl.java,v 1.3 2006/01/27 12:49:17 vj135062 Exp $
 * $Revision: 1.3 $
 * $Date: 2006/01/27 12:49:17 $
 */


package com.sun.xml.internal.messaging.saaj;

import java.io.PrintStream;
import java.io.PrintWriter;

import javax.xml.soap.SOAPException;

/**
 * An exception that signals that a SOAP exception has occurred. A
 * <code>SOAPExceptionImpl</code> object may contain a <code>String</code>
 * that gives the reason for the exception, an embedded
 * <code>Throwable</code> object, or both. This class provides methods
 * for retrieving reason messages and for retrieving the embedded
 * <code>Throwable</code> object.
 *
 * <P> Typical reasons for throwing a <code>SOAPExceptionImpl</code>
 * object are problems such as difficulty setting a header, not being
 * able to send a message, and not being able to get a connection with
 * the provider.  Reasons for embedding a <code>Throwable</code>
 * object include problems such as input/output errors or a parsing
 * problem, such as an error in parsing a header.
 */
public class SOAPExceptionImpl extends SOAPException {
    private Throwable cause;

    /**
     * Constructs a <code>SOAPExceptionImpl</code> object with no
     * reason or embedded <code>Throwable</code> object.
     */
    public SOAPExceptionImpl() {
        super();
        this.cause = null;
    }

    /**
     * Constructs a <code>SOAPExceptionImpl</code> object with the given
     * <code>String</code> as the reason for the exception being thrown.
     *
     * @param reason a description of what caused the exception
     */
    public SOAPExceptionImpl(String reason) {
        super(reason);
        this.cause = null;
    }

    /**
     * Constructs a <code>SOAPExceptionImpl</code> object with the given
     * <code>String</code> as the reason for the exception being thrown
     * and the given <code>Throwable</code> object as an embedded
     * exception.
     *
     * @param reason a description of what caused the exception
     * @param cause a <code>Throwable</code> object that is to
     *        be embedded in this <code>SOAPExceptionImpl</code> object
     */
    public SOAPExceptionImpl(String reason, Throwable cause) {
       super (reason);
       initCause(cause);
    }

    /**
     * Constructs a <code>SOAPExceptionImpl</code> object initialized
     * with the given <code>Throwable</code> object.
     */
    public SOAPExceptionImpl(Throwable cause) {
        super (cause.toString());
        initCause(cause);
    }

    /**
     * Returns the detail message for this <code>SOAPExceptionImpl</code>
     * object.
     * <P>
     * If there is an embedded <code>Throwable</code> object, and if the
     * <code>SOAPExceptionImpl</code> object has no detail message of its
     * own, this method will return the detail message from the embedded
     * <code>Throwable</code> object.
     *
     * @return the error or warning message for this
     *         <code>SOAPExceptionImpl</code> or, if it has none, the
     *         message of the embedded <code>Throwable</code> object,
     *         if there is one
     */
    public String getMessage() {
        String message = super.getMessage ();
        if (message == null && cause != null) {
            return cause.getMessage();
        } else {
            return message;
        }
    }

    /**
     * Returns the <code>Throwable</code> object embedded in this
     * <code>SOAPExceptionImpl</code> if there is one. Otherwise, this method
     * returns <code>null</code>.
     *
     * @return the embedded <code>Throwable</code> object or <code>null</code>
     *         if there is none
     */

    public Throwable getCause() {
        return cause;
    }

    /**
     * Initializes the <code>cause</code> field of this <code>SOAPExceptionImpl</code>
     * object with the given <code>Throwable</code> object.
     * <P>
     * This method can be called at most once.  It is generally called from
     * within the constructor or immediately after the constructor has
     * returned a new <code>SOAPExceptionImpl</code> object.
     * If this <code>SOAPExceptionImpl</code> object was created with the
     * constructor {@link #SOAPExceptionImpl(Throwable)} or
     * {@link #SOAPExceptionImpl(String,Throwable)}, meaning that its
     * <code>cause</code> field already has a value, this method cannot be
     * called even once.
     *
     * @param  cause the <code>Throwable</code> object that caused this
     *         <code>SOAPExceptionImpl</code> object to be thrown.  The value of this
     *         parameter is saved for later retrieval by the
     *         {@link #getCause()} method.  A <tt>null</tt> value is
     *         permitted and indicates that the cause is nonexistent or
     *         unknown.
     * @return  a reference to this <code>SOAPExceptionImpl</code> instance
     * @throws IllegalArgumentException if <code>cause</code> is this
     *         <code>Throwable</code> object.  (A <code>Throwable</code> object
     *         cannot be its own cause.)
     * @throws IllegalStateException if this <code>SOAPExceptionImpl</code> object
     *         was created with {@link #SOAPExceptionImpl(Throwable)} or
     *         {@link #SOAPExceptionImpl(String,Throwable)}, or this
     *         method has already been called on this <code>SOAPExceptionImpl</code>
     *         object
     */
    public synchronized Throwable initCause(Throwable cause)
    {
        if(this.cause != null) {
            throw new IllegalStateException("Can't override cause");
        }
        if(cause == this) {
            throw new IllegalArgumentException("Self-causation not permitted");
        }
        this.cause = cause;

        return this;
    }

    public void printStackTrace() {
        super.printStackTrace();
        if (cause != null) {
            System.err.println("\nCAUSE:\n");
            cause.printStackTrace();
        }
    }

    public void printStackTrace(PrintStream s) {
        super.printStackTrace(s);
        if (cause != null) {
            s.println("\nCAUSE:\n");
            cause.printStackTrace(s);
        }
    }

    public void printStackTrace(PrintWriter s) {
        super.printStackTrace(s);
        if (cause != null) {
            s.println("\nCAUSE:\n");
            cause.printStackTrace(s);
        }
    }
}
