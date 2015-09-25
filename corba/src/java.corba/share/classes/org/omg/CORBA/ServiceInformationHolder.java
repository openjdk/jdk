/*
 * Copyright (c) 1998, 2001, Oracle and/or its affiliates. All rights reserved.
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

/**
 * The Holder for {@code ServiceInformation}. For more information on
 * Holder files, see <a href="doc-files/generatedfiles.html#holder">
 * "Generated Files: Holder Files"</a>.<P>
 * A Holder class for a {@code ServiceInformation} object
 * that is used to store "out" and "inout" parameters in IDL methods.
 * If an IDL method signature has an IDL {@code xxx} as an "out"
 * or "inout" parameter, the programmer must pass an instance of
 * {@code ServiceInformationHolder} as the corresponding
 * parameter in the method invocation; for "inout" parameters, the programmer
 * must also fill the "in" value to be sent to the server.
 * Before the method invocation returns, the ORB will fill in the
 * value corresponding to the "out" value returned from the server.
 * <P>
 * If {@code myServiceInformationHolder} is an instance of {@code ServiceInformationHolder},
 * the value stored in its {@code value} field can be accessed with
 * {@code myServiceInformationHolder.value}.
 */
public final class ServiceInformationHolder
    implements org.omg.CORBA.portable.Streamable {

    /**
     * The {@code ServiceInformation} value held by this
     * {@code ServiceInformationHolder} object in its {@code value} field.
     */
    public ServiceInformation value;

    /**
     * Constructs a new {@code ServiceInformationHolder} object with its
     * {@code value} field initialized to null.
     */
    public ServiceInformationHolder() {
        this(null);
    }

    /**
     * Constructs a new {@code ServiceInformationHolder} object with its
     * {@code value} field initialized to the given
     * {@code ServiceInformation} object.
     *
     * @param arg the {@code ServiceInformation} object with which to initialize
     *                the {@code value} field of the newly-created
     *                {@code ServiceInformationHolder} object
     */
    public ServiceInformationHolder(org.omg.CORBA.ServiceInformation arg) {
        value = arg;
    }


    /**
     * Marshals the value in this {@code ServiceInformationHolder} object's
     * {@code value} field to the output stream {@code out}.
     *
     * @param out the {@code OutputStream} object that will contain
     *               the CDR formatted data
     */
    public void _write(org.omg.CORBA.portable.OutputStream out) {
        org.omg.CORBA.ServiceInformationHelper.write(out, value);
    }

    /**
     * Reads unmarshalled data from the input stream {@code in} and assigns it to
     * the {@code value} field in this {@code ServiceInformationHolder} object.
     *
     * @param in the {@code InputStream} object containing CDR
     *              formatted data from the wire
     */
    public void _read(org.omg.CORBA.portable.InputStream in) {
        value = org.omg.CORBA.ServiceInformationHelper.read(in);
    }

    /**
     * Retrieves the {@code TypeCode} object that corresponds
     * to the value held in this {@code ServiceInformationHolder} object's
     * {@code value} field.
     *
     * @return    the type code for the value held in this {@code ServiceInformationHolder}
     *            object
     */
    public org.omg.CORBA.TypeCode _type() {
        return org.omg.CORBA.ServiceInformationHelper.type();
    }
}
