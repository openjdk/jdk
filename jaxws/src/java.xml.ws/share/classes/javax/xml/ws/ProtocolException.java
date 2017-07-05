/*
 * Copyright (c) 2005, 2015, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.ws;

/** The {@code ProtocolException} class is a
 *  base class for exceptions related to a specific protocol binding. Subclasses
 *  are used to communicate protocol level fault information to clients and may
 *  be used on the server to control the protocol specific fault representation.
 *
 *  @since 1.6, JAX-WS 2.0
**/
public class ProtocolException extends WebServiceException {
    /**
     * Constructs a new protocol exception with {@code null} as its detail message. The
     * cause is not initialized, and may subsequently be initialized by a call
     * to {@code Throwable.initCause(java.lang.Throwable)}.
     */
    public ProtocolException() {
        super();
    }

    /**
     * Constructs a new protocol exception with the specified detail message.
     * The cause is not initialized, and may subsequently be initialized by a
     * call to {@code Throwable.initCause(java.lang.Throwable)}.
     *
     * @param message the detail message. The detail message is saved for later
     *   retrieval by the Throwable.getMessage() method.
     */
    public ProtocolException(String message) {
        super(message);
    }

    /**
     * Constructs a new runtime exception with the specified detail message and
     * cause.
     *
     * Note that the detail message associated with  cause is not automatically
     * incorporated in  this runtime exception's detail message.
     *
     * @param message the detail message (which is saved for later retrieval  by
     *   the Throwable.getMessage() method).
     * @param cause the cause (which is saved for later retrieval by the
     * {@code Throwable.getCause()} method). (A {@code null} value is  permitted, and indicates
     * that the cause is nonexistent or  unknown.)
     */
    public ProtocolException(String message,  Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new runtime exception with the specified cause and a  detail
     * message of {@code (cause==null ? null : cause.toString())}  (which typically
     * contains the class and detail message of  cause). This constructor is
     * useful for runtime exceptions  that are little more than wrappers for
     * other throwables.
     *
     * @param cause the cause (which is saved for later retrieval by the
     * {@code Throwable.getCause()} method). (A {@code null} value is  permitted, and indicates
     * that the cause is nonexistent or  unknown.)
     */
    public ProtocolException(Throwable cause) {
        super(cause);
    }
}
