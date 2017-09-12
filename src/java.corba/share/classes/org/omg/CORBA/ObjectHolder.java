/*
 * Copyright (c) 1996, 2001, Oracle and/or its affiliates. All rights reserved.
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

import org.omg.CORBA.portable.Streamable;
import org.omg.CORBA.portable.InputStream;
import org.omg.CORBA.portable.OutputStream;


/**
 * The Holder for {@code Object}. For more information on
 * Holder files, see <a href="doc-files/generatedfiles.html#holder">
 * "Generated Files: Holder Files"</a>.<P>
 * A Holder class for a CORBA object reference (a value of type
 * {@code org.omg.CORBA.Object}).  It is usually
 * used to store "out" and "inout" parameters in IDL methods.
 * If an IDL method signature has a CORBA Object reference as an "out"
 * or "inout" parameter, the programmer must pass an instance of
 * {@code ObjectHolder} as the corresponding
 * parameter in the method invocation; for "inout" parameters, the programmer
 * must also fill the "in" value to be sent to the server.
 * Before the method invocation returns, the ORB will fill in the
 * value corresponding to the "out" value returned from the server.
 * <P>
 * If {@code myObjectHolder} is an instance of {@code ObjectHolder},
 * the value stored in its {@code value} field can be accessed with
 * {@code myObjectHolder.value}.
 *
 * @since       JDK1.2
 */
public final class ObjectHolder implements Streamable {
    /**
     * The {@code Object} value held by this {@code ObjectHolder}
     * object.
     */
    public Object value;

    /**
     * Constructs a new {@code ObjectHolder} object with its
     * {@code value} field initialized to {@code null}.
     */
    public ObjectHolder() {
    }

    /**
     * Constructs a new {@code ObjectHolder} object with its
     * {@code value} field initialized to the given
     * {@code Object}.
     * @param initial the {@code Object} with which to initialize
     *                the {@code value} field of the newly-created
     *                {@code ObjectHolder} object
     */
    public ObjectHolder(Object initial) {
        value = initial;
    }

    /**
     * Reads from {@code input} and initalizes the value in
     * this {@code ObjectHolder} object
     * with the unmarshalled data.
     *
     * @param input the InputStream containing CDR formatted data from the wire.
     */
    public void _read(InputStream input) {
        value = input.read_Object();
    }

    /**
     * Marshals to {@code output} the value in
     * this {@code ObjectHolder} object.
     *
     * @param output the OutputStream which will contain the CDR formatted data.
     */
    public void _write(OutputStream output) {
        output.write_Object(value);
    }

    /**
     * Returns the TypeCode corresponding to the value held in
     * this {@code ObjectHolder} object
     *
     * @return    the TypeCode of the value held in
     *            this {@code ObjectHolder} object
     */
    public org.omg.CORBA.TypeCode _type() {
        return org.omg.CORBA.ORB.init().get_primitive_tc(TCKind.tk_objref);
    }
}
