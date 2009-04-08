/*
 * Copyright 2004 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * read S11.sh
 */
import java.io.*;
import sun.security.util.*;

/**
 * Test OID serialization between versions
 *
 * java SerialTest out oid  // write a OID into System.out
 * java SerialTest in oid   // read from System.in and compare it with oid
 * java SerialTest badin    // make sure *cannot* read from System.in
 */
class SerialTest {
    public static void main(String[] args) throws Exception {
        if (args[0].equals("out"))
            out(args[1]);
        else if (args[0].equals("in"))
            in(args[1]);
        else
            badin();
    }

    static void in(String oid) throws Exception {
        ObjectIdentifier o = (ObjectIdentifier) (new ObjectInputStream(System.in).readObject());
        if (!o.toString().equals(oid))
            throw new Exception("Read Fail " + o + ", not " + oid);
    }

    static void badin() throws Exception {
        boolean pass = true;
        try {
            new ObjectInputStream(System.in).readObject();
        } catch (Exception e) {
            pass = false;
        }
        if (pass) throw new Exception("Should fail but not");
    }

    static void out(String oid) throws Exception {
        new ObjectOutputStream(System.out).writeObject(new ObjectIdentifier(oid));
    }
}
