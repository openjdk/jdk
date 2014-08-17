/*
 * Copyright (c) 2001, 2002, Oracle and/or its affiliates. All rights reserved.
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
 */
package com.sun.corba.se.impl.orbutil;

import java.io.*;
import java.util.Hashtable;

/**
 * Since ObjectOutputStream.PutField methods specify no exceptions,
 * we are not checking for null parameters on put methods.
 */
class LegacyHookPutFields extends ObjectOutputStream.PutField
{
    private Hashtable fields = new Hashtable();

    /**
     * Put the value of the named boolean field into the persistent field.
     */
    public void put(String name, boolean value){
        fields.put(name, new Boolean(value));
    }

    /**
     * Put the value of the named char field into the persistent fields.
     */
    public void put(String name, char value){
        fields.put(name, new Character(value));
    }

    /**
     * Put the value of the named byte field into the persistent fields.
     */
    public void put(String name, byte value){
        fields.put(name, new Byte(value));
    }

    /**
     * Put the value of the named short field into the persistent fields.
     */
    public void put(String name, short value){
        fields.put(name, new Short(value));
    }

    /**
     * Put the value of the named int field into the persistent fields.
     */
    public void put(String name, int value){
        fields.put(name, new Integer(value));
    }

    /**
     * Put the value of the named long field into the persistent fields.
     */
    public void put(String name, long value){
        fields.put(name, new Long(value));
    }

    /**
     * Put the value of the named float field into the persistent fields.
     *
     */
    public void put(String name, float value){
        fields.put(name, new Float(value));
    }

    /**
     * Put the value of the named double field into the persistent field.
     */
    public void put(String name, double value){
        fields.put(name, new Double(value));
    }

    /**
     * Put the value of the named Object field into the persistent field.
     */
    public void put(String name, Object value){
        fields.put(name, value);
    }

    /**
     * Write the data and fields to the specified ObjectOutput stream.
     */
    public void write(ObjectOutput out) throws IOException {
        out.writeObject(fields);
    }
}
