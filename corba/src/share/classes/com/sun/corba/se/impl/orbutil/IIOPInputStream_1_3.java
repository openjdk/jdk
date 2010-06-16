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
import java.util.Hashtable;

/**
 * Implements legacy behavior from before Ladybird to maintain
 * backwards compatibility.
 */
public class IIOPInputStream_1_3 extends com.sun.corba.se.impl.io.IIOPInputStream
{
    // The newer version in the io package correctly reads a wstring instead.
    // This concerns bug 4379597.
    protected String internalReadUTF(org.omg.CORBA.portable.InputStream stream)
    {
        return stream.read_string();
    }

    /**
     * Before JDK 1.3.1_01, the PutField/GetField implementation
     * actually sent a Hashtable.
     */
    public ObjectInputStream.GetField readFields()
        throws IOException, ClassNotFoundException, NotActiveException {
        Hashtable fields = (Hashtable)readObject();
        return new LegacyHookGetFields(fields);
    }

    public IIOPInputStream_1_3()
        throws java.io.IOException {
        super();
    }
}
