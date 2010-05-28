/*
 * Copyright (c) 2003, 2007, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 * Parent class implements serializable and provides static initializers
 * for a bunch of primitive type class constants
 * (required for regtest 4786406, 4780341)
 */

import java.io.*;

public class SuperClassConsts implements Serializable {

    // Define class constant values, base class is serializable

    private static final long serialVersionUID = 6733861379283244755L;
    public static final int SUPER_INT_CONSTANT = 3;
    public final static float SUPER_FLOAT_CONSTANT = 99.3f;
    public final static double SUPER_DOUBLE_CONSTANT  = 33.2;
    public final static boolean SUPER_BOOLEAN_CONSTANT  = false;

    // A token instance field
    int instanceField;

    public SuperClassConsts(String p) {
    }

    public native int numValues();

    private void writeObject(ObjectOutputStream s)
        throws IOException
    {
        System.err.println("writing state");
    }

    /**
     * readObject is called to restore the state of the FilePermission from
     * a stream.
     */
    private void readObject(ObjectInputStream s)
         throws IOException, ClassNotFoundException
    {
        System.err.println("reading back state");
    }
}
