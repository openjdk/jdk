/*
 * Copyright (c) 2000, 2002, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.corba.se.impl.orbutil;

import java.io.*;

/**
 * Implements legacy behavior from before Ladybird to maintain
 * backwards compatibility.
 */
public class IIOPOutputStream_1_3 extends com.sun.corba.se.impl.io.IIOPOutputStream
{
    // We can't assume that the superclass's putFields
    // member will be non-private.  We must allow
    // the RI to run on JDK 1.3.1 FCS as well as
    // the JDK 1.3.1_01 patch.
    private ObjectOutputStream.PutField putFields_1_3;

    // The newer version in the io package correctly writes a wstring instead.
    // This concerns bug 4379597.
    protected void internalWriteUTF(org.omg.CORBA.portable.OutputStream stream,
                                    String data)
    {
        stream.write_string(data);
    }

    public IIOPOutputStream_1_3()
        throws java.io.IOException {
        super();
    }

    /**
     * Before JDK 1.3.1_01, the PutField/GetField implementation
     * actually sent a Hashtable.
     */
    public ObjectOutputStream.PutField putFields()
        throws IOException {
        putFields_1_3 = new LegacyHookPutFields();
        return putFields_1_3;
    }

    public void writeFields()
        throws IOException {
        putFields_1_3.write(this);
    }
}
