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

package org.omg.CORBA;

import org.omg.CORBA.DataOutputStream;
import org.omg.CORBA.DataInputStream;

/**
 * An abstract value type that is meant to
 * be used by the ORB, not the user. Semantically it is treated
 * as a custom value type's implicit base class, although the custom
 * valuetype does not actually inherit it in IDL. The implementer
 * of a custom value type shall provide an implementation of the
 * {@code CustomMarshal} operations. The manner in which this is done is
 * specified in the IDL to Java langauge mapping. Each custom
 * marshaled value type shall have its own implementation.
 * @see DataInputStream
 */
public interface CustomMarshal {
    /**
     * Marshal method has to be implemented by the Customized Marshal class.
     * This is the method invoked for Marshalling.
     *
     * @param os a DataOutputStream
     */
    void marshal(DataOutputStream os);
    /**
     * Unmarshal method has to be implemented by the Customized Marshal class.
     * This is the method invoked for Unmarshalling.
     *
     * @param is a DataInputStream
     */
    void unmarshal(DataInputStream is);
}
