/*
 * Copyright (c) 1999, Oracle and/or its affiliates. All rights reserved.
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
import java.io.Serializable;

/**
 * The ValueFactory interface is the native mapping for the IDL
 * type CORBA::ValueFactory. The read_value() method is called by
 * the ORB runtime while in the process of unmarshaling a value type.
 * A user shall implement this method as part of implementing a type
 * specific value factory. In the implementation, the user shall call
 * is.read_value(java.io.Serializable) with a uninitialized valuetype
 * to use for unmarshaling. The value returned by the stream is
 * the same value passed in, with all the data unmarshaled.
 * @see org.omg.CORBA_2_3.ORB
 */

public interface ValueFactory {
    /**
     * Is called by
     * the ORB runtime while in the process of unmarshaling a value type.
     * A user shall implement this method as part of implementing a type
     * specific value factory.
     * @param is an InputStream object--from which the value will be read.
     * @return a Serializable object--the value read off of "is" Input stream.
     */
    Serializable read_value(org.omg.CORBA_2_3.portable.InputStream is);
}
