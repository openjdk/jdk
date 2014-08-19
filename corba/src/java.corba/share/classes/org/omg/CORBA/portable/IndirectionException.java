/*
 * Copyright (c) 1999, 2003, Oracle and/or its affiliates. All rights reserved.
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
/*
 * Licensed Materials - Property of IBM
 * RMI-IIOP v1.0
 * Copyright IBM Corp. 1998 1999  All Rights Reserved
 *
 */

package org.omg.CORBA.portable;

import org.omg.CORBA.SystemException;
/**
 * The Indirection exception is a Java specific system exception.
 * It is thrown when the ORB's input stream is called to demarshal
 * a value that is encoded as an indirection that is in the process
 * of being demarshaled. This can occur when the ORB input stream
 * calls the ValueHandler to demarshal an RMI value whose state
 * contains a recursive reference to itself. Because the top-level
 * ValueHandler.read_value() call has not yet returned a value,
 * the ORB input stream's indirection table does not contain an entry
 * for an object with the stream offset specified by the indirection
 * tag. The stream offset is returned in the exception's offset field.
 * @see org.omg.CORBA_2_3.portable.InputStream
 * @see org.omg.CORBA_2_3.portable.OutputStream
 */
public class IndirectionException extends SystemException {

    /**
     * Points to the stream's offset.
     */
    public int offset;

    /**
     * Creates an IndirectionException with the right offset value.
     * The stream offset is returned in the exception's offset field.
     * This exception is constructed and thrown during reading
     * recursively defined values off of a stream.
     *
     * @param offset the stream offset where recursion is detected.
     */
    public IndirectionException(int offset){
        super("", 0, org.omg.CORBA.CompletionStatus.COMPLETED_MAYBE);
        this.offset = offset;
    }
}
