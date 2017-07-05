/*
 * Copyright (c) 1996, 2000, Oracle and/or its affiliates. All rights reserved.
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

package java.lang;

/**
 * Signals that an unexpected exception has occurred in a static initializer.
 * An <code>ExceptionInInitializerError</code> is thrown to indicate that an
 * exception occurred during evaluation of a static initializer or the
 * initializer for a static variable.
 *
 * <p>As of release 1.4, this exception has been retrofitted to conform to
 * the general purpose exception-chaining mechanism.  The "saved throwable
 * object" that may be provided at construction time and accessed via
 * the {@link #getException()} method is now known as the <i>cause</i>,
 * and may be accessed via the {@link Throwable#getCause()} method, as well
 * as the aforementioned "legacy method."
 *
 * @author  Frank Yellin
 * @since   JDK1.1
 */
public class ExceptionInInitializerError extends LinkageError {
    /**
     * Use serialVersionUID from JDK 1.1.X for interoperability
     */
    private static final long serialVersionUID = 1521711792217232256L;

    /**
     * This field holds the exception if the
     * ExceptionInInitializerError(Throwable thrown) constructor was
     * used to instantiate the object
     *
     * @serial
     *
     */
    private Throwable exception;

    /**
     * Constructs an <code>ExceptionInInitializerError</code> with
     * <code>null</code> as its detail message string and with no saved
     * throwable object.
     * A detail message is a String that describes this particular exception.
     */
    public ExceptionInInitializerError() {
        initCause(null);  // Disallow subsequent initCause
    }

    /**
     * Constructs a new <code>ExceptionInInitializerError</code> class by
     * saving a reference to the <code>Throwable</code> object thrown for
     * later retrieval by the {@link #getException()} method. The detail
     * message string is set to <code>null</code>.
     *
     * @param thrown The exception thrown
     */
    public ExceptionInInitializerError(Throwable thrown) {
        initCause(null);  // Disallow subsequent initCause
        this.exception = thrown;
    }

    /**
     * Constructs an ExceptionInInitializerError with the specified detail
     * message string.  A detail message is a String that describes this
     * particular exception. The detail message string is saved for later
     * retrieval by the {@link Throwable#getMessage()} method. There is no
     * saved throwable object.
     *
     *
     * @param s the detail message
     */
    public ExceptionInInitializerError(String s) {
        super(s);
        initCause(null);  // Disallow subsequent initCause
    }

    /**
     * Returns the exception that occurred during a static initialization that
     * caused this error to be created.
     *
     * <p>This method predates the general-purpose exception chaining facility.
     * The {@link Throwable#getCause()} method is now the preferred means of
     * obtaining this information.
     *
     * @return the saved throwable object of this
     *         <code>ExceptionInInitializerError</code>, or <code>null</code>
     *         if this <code>ExceptionInInitializerError</code> has no saved
     *         throwable object.
     */
    public Throwable getException() {
        return exception;
    }

    /**
     * Returns the cause of this error (the exception that occurred
     * during a static initialization that caused this error to be created).
     *
     * @return  the cause of this error or <code>null</code> if the
     *          cause is nonexistent or unknown.
     * @since   1.4
     */
    public Throwable getCause() {
        return exception;
    }
}
