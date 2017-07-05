/*
 * Copyright (c) 2000, 2006, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.transform;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

/**
 * This class specifies an exceptional condition that occured
 * during the transformation process.
 */
public class TransformerException extends Exception {

    /** Field locator specifies where the error occured */
    SourceLocator locator;

    /**
     * Method getLocator retrieves an instance of a SourceLocator
     * object that specifies where an error occured.
     *
     * @return A SourceLocator object, or null if none was specified.
     */
    public SourceLocator getLocator() {
        return locator;
    }

    /**
     * Method setLocator sets an instance of a SourceLocator
     * object that specifies where an error occured.
     *
     * @param location A SourceLocator object, or null to clear the location.
     */
    public void setLocator(SourceLocator location) {
        locator = location;
    }

    /** Field containedException specifies a wrapped exception.  May be null. */
    Throwable containedException;

    /**
     * This method retrieves an exception that this exception wraps.
     *
     * @return An Throwable object, or null.
     * @see #getCause
     */
    public Throwable getException() {
        return containedException;
    }

    /**
     * Returns the cause of this throwable or <code>null</code> if the
     * cause is nonexistent or unknown.  (The cause is the throwable that
     * caused this throwable to get thrown.)
     */
    public Throwable getCause() {

        return ((containedException == this)
                ? null
                : containedException);
    }

    /**
     * Initializes the <i>cause</i> of this throwable to the specified value.
     * (The cause is the throwable that caused this throwable to get thrown.)
     *
     * <p>This method can be called at most once.  It is generally called from
     * within the constructor, or immediately after creating the
     * throwable.  If this throwable was created
     * with {@link #TransformerException(Throwable)} or
     * {@link #TransformerException(String,Throwable)}, this method cannot be called
     * even once.
     *
     * @param  cause the cause (which is saved for later retrieval by the
     *         {@link #getCause()} method).  (A <code>null</code> value is
     *         permitted, and indicates that the cause is nonexistent or
     *         unknown.)
     * @return  a reference to this <code>Throwable</code> instance.
     * @throws IllegalArgumentException if <code>cause</code> is this
     *         throwable.  (A throwable cannot
     *         be its own cause.)
     * @throws IllegalStateException if this throwable was
     *         created with {@link #TransformerException(Throwable)} or
     *         {@link #TransformerException(String,Throwable)}, or this method has already
     *         been called on this throwable.
     */
    public synchronized Throwable initCause(Throwable cause) {

        if (this.containedException != null) {
            throw new IllegalStateException("Can't overwrite cause");
        }

        if (cause == this) {
            throw new IllegalArgumentException(
                "Self-causation not permitted");
        }

        this.containedException = cause;

        return this;
    }

    /**
     * Create a new TransformerException.
     *
     * @param message The error or warning message.
     */
    public TransformerException(String message) {

        super(message);

        this.containedException = null;
        this.locator            = null;
    }

    /**
     * Create a new TransformerException wrapping an existing exception.
     *
     * @param e The exception to be wrapped.
     */
    public TransformerException(Throwable e) {

        super(e.toString());

        this.containedException = e;
        this.locator            = null;
    }

    /**
     * Wrap an existing exception in a TransformerException.
     *
     * <p>This is used for throwing processor exceptions before
     * the processing has started.</p>
     *
     * @param message The error or warning message, or null to
     *                use the message from the embedded exception.
     * @param e Any exception
     */
    public TransformerException(String message, Throwable e) {

        super(((message == null) || (message.length() == 0))
              ? e.toString()
              : message);

        this.containedException = e;
        this.locator            = null;
    }

    /**
     * Create a new TransformerException from a message and a Locator.
     *
     * <p>This constructor is especially useful when an application is
     * creating its own exception from within a DocumentHandler
     * callback.</p>
     *
     * @param message The error or warning message.
     * @param locator The locator object for the error or warning.
     */
    public TransformerException(String message, SourceLocator locator) {

        super(message);

        this.containedException = null;
        this.locator            = locator;
    }

    /**
     * Wrap an existing exception in a TransformerException.
     *
     * @param message The error or warning message, or null to
     *                use the message from the embedded exception.
     * @param locator The locator object for the error or warning.
     * @param e Any exception
     */
    public TransformerException(String message, SourceLocator locator,
                                Throwable e) {

        super(message);

        this.containedException = e;
        this.locator            = locator;
    }

    /**
     * Get the error message with location information
     * appended.
     *
     * @return A <code>String</code> representing the error message with
     *         location information appended.
     */
    public String getMessageAndLocation() {

        StringBuffer sbuffer = new StringBuffer();
        String       message = super.getMessage();

        if (null != message) {
            sbuffer.append(message);
        }

        if (null != locator) {
            String systemID = locator.getSystemId();
            int    line     = locator.getLineNumber();
            int    column   = locator.getColumnNumber();

            if (null != systemID) {
                sbuffer.append("; SystemID: ");
                sbuffer.append(systemID);
            }

            if (0 != line) {
                sbuffer.append("; Line#: ");
                sbuffer.append(line);
            }

            if (0 != column) {
                sbuffer.append("; Column#: ");
                sbuffer.append(column);
            }
        }

        return sbuffer.toString();
    }

    /**
     * Get the location information as a string.
     *
     * @return A string with location info, or null
     * if there is no location information.
     */
    public String getLocationAsString() {

        if (null != locator) {
            StringBuffer sbuffer  = new StringBuffer();
            String       systemID = locator.getSystemId();
            int          line     = locator.getLineNumber();
            int          column   = locator.getColumnNumber();

            if (null != systemID) {
                sbuffer.append("; SystemID: ");
                sbuffer.append(systemID);
            }

            if (0 != line) {
                sbuffer.append("; Line#: ");
                sbuffer.append(line);
            }

            if (0 != column) {
                sbuffer.append("; Column#: ");
                sbuffer.append(column);
            }

            return sbuffer.toString();
        } else {
            return null;
        }
    }

    /**
     * Print the the trace of methods from where the error
     * originated.  This will trace all nested exception
     * objects, as well as this object.
     */
    public void printStackTrace() {
        printStackTrace(new java.io.PrintWriter(System.err, true));
    }

    /**
     * Print the the trace of methods from where the error
     * originated.  This will trace all nested exception
     * objects, as well as this object.
     * @param s The stream where the dump will be sent to.
     */
    public void printStackTrace(java.io.PrintStream s) {
        printStackTrace(new java.io.PrintWriter(s));
    }

    /**
     * Print the the trace of methods from where the error
     * originated.  This will trace all nested exception
     * objects, as well as this object.
     * @param s The writer where the dump will be sent to.
     */
    public void printStackTrace(java.io.PrintWriter s) {

        if (s == null) {
            s = new java.io.PrintWriter(System.err, true);
        }

        try {
            String locInfo = getLocationAsString();

            if (null != locInfo) {
                s.println(locInfo);
            }

            super.printStackTrace(s);
        } catch (Throwable e) {}

        Throwable exception = getException();

        for (int i = 0; (i < 10) && (null != exception); i++) {
            s.println("---------");

            try {
                if (exception instanceof TransformerException) {
                    String locInfo =
                        ((TransformerException) exception)
                            .getLocationAsString();

                    if (null != locInfo) {
                        s.println(locInfo);
                    }
                }

                exception.printStackTrace(s);
            } catch (Throwable e) {
                s.println("Could not print stack trace...");
            }

            try {
                Method meth =
                    ((Object) exception).getClass().getMethod("getException",
                        (Class[]) null);

                if (null != meth) {
                    Throwable prev = exception;

                    exception = (Throwable) meth.invoke(exception, (Object[]) null);

                    if (prev == exception) {
                        break;
                    }
                } else {
                    exception = null;
                }
            } catch (InvocationTargetException ite) {
                exception = null;
            } catch (IllegalAccessException iae) {
                exception = null;
            } catch (NoSuchMethodException nsme) {
                exception = null;
            }
        }
        // insure output is written
        s.flush();
    }
}
