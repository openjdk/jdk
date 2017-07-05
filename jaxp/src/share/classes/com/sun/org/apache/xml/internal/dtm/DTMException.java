/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 1999-2004 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * $Id: DTMException.java,v 1.3 2005/09/28 13:48:50 pvedula Exp $
 */
package com.sun.org.apache.xml.internal.dtm;


import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.xml.transform.SourceLocator;

import com.sun.org.apache.xml.internal.res.XMLErrorResources;
import com.sun.org.apache.xml.internal.res.XMLMessages;


/**
 * This class specifies an exceptional condition that occured
 * in the DTM module.
 */
public class DTMException extends RuntimeException {
    static final long serialVersionUID = -775576419181334734L;

    /** Field locator specifies where the error occured.
     *  @serial */
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

    /** Field containedException specifies a wrapped exception.  May be null.
     *  @serial */
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
     * with {@link #DTMException(Throwable)} or
     * {@link #DTMException(String,Throwable)}, this method cannot be called
     * even once.
     *
     * @param  cause the cause (which is saved for later retrieval by the
     *         {@link #getCause()} method).  (A <tt>null</tt> value is
     *         permitted, and indicates that the cause is nonexistent or
     *         unknown.)
     * @return  a reference to this <code>Throwable</code> instance.
     * @throws IllegalArgumentException if <code>cause</code> is this
     *         throwable.  (A throwable cannot
     *         be its own cause.)
     * @throws IllegalStateException if this throwable was
     *         created with {@link #DTMException(Throwable)} or
     *         {@link #DTMException(String,Throwable)}, or this method has already
     *         been called on this throwable.
     */
    public synchronized Throwable initCause(Throwable cause) {

        if ((this.containedException == null) && (cause != null)) {
            throw new IllegalStateException(XMLMessages.createXMLMessage(XMLErrorResources.ER_CANNOT_OVERWRITE_CAUSE, null)); //"Can't overwrite cause");
        }

        if (cause == this) {
            throw new IllegalArgumentException(
                XMLMessages.createXMLMessage(XMLErrorResources.ER_SELF_CAUSATION_NOT_PERMITTED, null)); //"Self-causation not permitted");
        }

        this.containedException = cause;

        return this;
    }

    /**
     * Create a new DTMException.
     *
     * @param message The error or warning message.
     */
    public DTMException(String message) {

        super(message);

        this.containedException = null;
        this.locator            = null;
    }

    /**
     * Create a new DTMException wrapping an existing exception.
     *
     * @param e The exception to be wrapped.
     */
    public DTMException(Throwable e) {

        super(e.getMessage());

        this.containedException = e;
        this.locator            = null;
    }

    /**
     * Wrap an existing exception in a DTMException.
     *
     * <p>This is used for throwing processor exceptions before
     * the processing has started.</p>
     *
     * @param message The error or warning message, or null to
     *                use the message from the embedded exception.
     * @param e Any exception
     */
    public DTMException(String message, Throwable e) {

        super(((message == null) || (message.length() == 0))
              ? e.getMessage()
              : message);

        this.containedException = e;
        this.locator            = null;
    }

    /**
     * Create a new DTMException from a message and a Locator.
     *
     * <p>This constructor is especially useful when an application is
     * creating its own exception from within a DocumentHandler
     * callback.</p>
     *
     * @param message The error or warning message.
     * @param locator The locator object for the error or warning.
     */
    public DTMException(String message, SourceLocator locator) {

        super(message);

        this.containedException = null;
        this.locator            = locator;
    }

    /**
     * Wrap an existing exception in a DTMException.
     *
     * @param message The error or warning message, or null to
     *                use the message from the embedded exception.
     * @param locator The locator object for the error or warning.
     * @param e Any exception
     */
    public DTMException(String message, SourceLocator locator,
                                Throwable e) {

        super(message);

        this.containedException = e;
        this.locator            = locator;
    }

    /**
     * Get the error message with location information
     * appended.
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

        boolean isJdk14OrHigher = false;
        try {
            Throwable.class.getMethod("getCause", (Class[]) null);
            isJdk14OrHigher = true;
        } catch (NoSuchMethodException nsme) {
            // do nothing
        }

        // The printStackTrace method of the Throwable class in jdk 1.4
        // and higher will include the cause when printing the backtrace.
        // The following code is only required when using jdk 1.3 or lower
        if (!isJdk14OrHigher) {
            Throwable exception = getException();

            for (int i = 0; (i < 10) && (null != exception); i++) {
                s.println("---------");

                try {
                    if (exception instanceof DTMException) {
                        String locInfo =
                            ((DTMException) exception)
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
        }
    }
}
