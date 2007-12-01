/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package java.util;

import java.io.NotSerializableException;
import java.io.IOException;

/**
 * Thrown to indicate that an operation could not complete because
 * the input did not conform to the appropriate XML document type
 * for a collection of properties, as per the {@link Properties}
 * specification.<p>
 *
 * Note, that although InvalidPropertiesFormatException inherits Serializable
 * interface from Exception, it is not intended to be Serializable. Appropriate
 * serialization methods are implemented to throw NotSerializableException.
 *
 * @see     Properties
 * @since   1.5
 * @serial exclude
 */

public class InvalidPropertiesFormatException extends IOException {
    /**
     * Constructs an InvalidPropertiesFormatException with the specified
     * cause.
     *
     * @param  cause the cause (which is saved for later retrieval by the
     *         {@link Throwable#getCause()} method).
     */
    public InvalidPropertiesFormatException(Throwable cause) {
        super(cause==null ? null : cause.toString());
        this.initCause(cause);
    }

   /**
    * Constructs an InvalidPropertiesFormatException with the specified
    * detail message.
    *
    * @param   message   the detail message. The detail message is saved for
    *          later retrieval by the {@link Throwable#getMessage()} method.
    */
    public InvalidPropertiesFormatException(String message) {
        super(message);
    }

    /**
     * Throws NotSerializableException, since InvalidPropertiesFormatException
     * objects are not intended to be serializable.
     */
    private void writeObject(java.io.ObjectOutputStream out)
        throws NotSerializableException
    {
        throw new NotSerializableException("Not serializable.");
    }

    /**
     * Throws NotSerializableException, since InvalidPropertiesFormatException
     * objects are not intended to be serializable.
     */
    private void readObject(java.io.ObjectInputStream in)
        throws NotSerializableException
    {
        throw new NotSerializableException("Not serializable.");
    }

}
