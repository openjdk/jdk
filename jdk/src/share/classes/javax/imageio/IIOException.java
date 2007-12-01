/*
 * Copyright 2000 Sun Microsystems, Inc.  All Rights Reserved.
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

package javax.imageio;

import java.io.IOException;

/**
 * An exception class used for signaling run-time failure of reading
 * and writing operations.
 *
 * <p> In addition to a message string, a reference to another
 * <code>Throwable</code> (<code>Error</code> or
 * <code>Exception</code>) is maintained.  This reference, if
 * non-<code>null</code>, refers to the event that caused this
 * exception to occur.  For example, an <code>IOException</code> while
 * reading from a <code>File</code> would be stored there.
 *
 */
public class IIOException extends IOException {

    /**
     * Constructs an <code>IIOException</code> with a given message
     * <code>String</code>.  No underlying cause is set;
     * <code>getCause</code> will return <code>null</code>.
     *
     * @param message the error message.
     *
     * @see #getMessage
     */
    public IIOException(String message) {
        super(message);
    }

    /**
     * Constructs an <code>IIOException</code> with a given message
     * <code>String</code> and a <code>Throwable</code> that was its
     * underlying cause.
     *
     * @param message the error message.
     * @param cause the <code>Throwable</code> (<code>Error</code> or
     * <code>Exception</code>) that caused this exception to occur.
     *
     * @see #getCause
     * @see #getMessage
     */
    public IIOException(String message, Throwable cause) {
        super(message);
        initCause(cause);
    }
}
