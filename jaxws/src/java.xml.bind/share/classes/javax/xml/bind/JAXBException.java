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

import java.io.PrintWriter;

/**
 * This is the root exception class for all JAXB exceptions.
 *
 * @author <ul><li>Ryan Shoemaker, Sun Microsystems, Inc.</li></ul>
 * @see JAXBContext
 * @see Marshaller
 * @see Unmarshaller
 * @since 1.6, JAXB 1.0
 */
public class JAXBException extends Exception {

    /**
     * Vendor specific error code
     *
     */
    private String errorCode;

    /**
     * Exception reference
     *
     */
    private volatile Throwable linkedException;

    static final long serialVersionUID = -5621384651494307979L;

    /**
     * Construct a JAXBException with the specified detail message.  The
     * errorCode and linkedException will default to null.
     *
     * @param message a description of the exception
     */
    public JAXBException(String message) {
        this( message, null, null );
    }

    /**
     * Construct a JAXBException with the specified detail message and vendor
     * specific errorCode.  The linkedException will default to null.
     *
     * @param message a description of the exception
     * @param errorCode a string specifying the vendor specific error code
     */
    public JAXBException(String message, String errorCode) {
        this( message, errorCode, null );
    }

    /**
     * Construct a JAXBException with a linkedException.  The detail message and
     * vendor specific errorCode will default to null.
     *
     * @param exception the linked exception
     */
    public JAXBException(Throwable exception) {
        this( null, null, exception );
    }

    /**
     * Construct a JAXBException with the specified detail message and
     * linkedException.  The errorCode will default to null.
     *
     * @param message a description of the exception
     * @param exception the linked exception
     */
    public JAXBException(String message, Throwable exception) {
        this( message, null, exception );
    }

    /**
     * Construct a JAXBException with the specified detail message, vendor
     * specific errorCode, and linkedException.
     *
     * @param message a description of the exception
     * @param errorCode a string specifying the vendor specific error code
     * @param exception the linked exception
     */
    public JAXBException(String message, String errorCode, Throwable exception) {
        super( message );
        this.errorCode = errorCode;
        this.linkedException = exception;
    }

    /**
     * Get the vendor specific error code
     *
     * @return a string specifying the vendor specific error code
     */
    public String getErrorCode() {
        return this.errorCode;
    }

    /**
     * Get the linked exception
     *
     * @return the linked Exception, null if none exists
     */
    public Throwable getLinkedException() {
        return linkedException;
    }

    /**
     * Add a linked Exception.
     *
     * @param exception the linked Exception (A null value is permitted and
     *                  indicates that the linked exception does not exist or
     *                  is unknown).
     */
    public void setLinkedException( Throwable exception ) {
        this.linkedException = exception;
    }

    /**
     * Returns a short description of this JAXBException.
     *
     */
    public String toString() {
        return linkedException == null ?
            super.toString() :
            super.toString() + "\n - with linked exception:\n[" +
                                linkedException.toString()+ "]";
    }

    /**
     * Prints this JAXBException and its stack trace (including the stack trace
     * of the linkedException if it is non-null) to the PrintStream.
     *
     * @param s PrintStream to use for output
     */
    public void printStackTrace( java.io.PrintStream s ) {
        super.printStackTrace(s);
    }

    /**
     * Prints this JAXBException and its stack trace (including the stack trace
     * of the linkedException if it is non-null) to {@code System.err}.
     *
     */
    public void printStackTrace() {
        super.printStackTrace();
    }

    /**
     * Prints this JAXBException and its stack trace (including the stack trace
     * of the linkedException if it is non-null) to the PrintWriter.
     *
     * @param s PrintWriter to use for output
     */
    public void printStackTrace(PrintWriter s) {
        super.printStackTrace(s);
    }

    @Override
    public Throwable getCause() {
        return linkedException;
    }
}
