/*
 * Copyright (c) 1999, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @author Gary Ellison
 * @bug 4170635
 * @summary Verify equals()/hashCode() contract honored
 * @run main/othervm/policy=Allow.policy DerInputBufferEqualsHashCode
 */

import java.io.*;
import sun.security.util.*;
import sun.security.x509.*;
import java.lang.reflect.*;


public class DerInputBufferEqualsHashCode {

    public static void main(String[] args) throws Exception {

        String name1 = "CN=eve s. dropper";
        DerOutputStream deros;
        byte[] ba;
        // encode
        X500Name dn1 = new X500Name(name1);

        deros = new DerOutputStream();
        dn1.encode(deros);
        ba = deros.toByteArray();

        GetDIBConstructor a = new GetDIBConstructor();
        java.security.AccessController.doPrivileged(a);
        Constructor c = a.getCons();

        Object[] objs = new Object[1];
        objs[0] = ba;

        Object db1 = null, db2 = null;
        try {
            db1 = c.newInstance(objs);
            db2 = c.newInstance(objs);
        } catch (Exception e) {
            System.out.println("Caught unexpected exception " + e);
            throw e;
        }

        if ( (db1.equals(db2)) == (db1.hashCode()==db2.hashCode()) )
            System.out.println("PASSED");
        else
            throw new Exception("FAILED equals()/hashCode() contract");

    }
}


class GetDIBConstructor implements java.security.PrivilegedExceptionAction {

    private Class dibClass = null;
    private Constructor dibCons = null;

    public Object run() throws Exception {
        try {
            dibClass = Class.forName("sun.security.util.DerInputBuffer");
            Constructor[] cons = dibClass.getDeclaredConstructors();

            int i;
            for (i = 0; i < cons.length; i++) {
                Class [] parms = cons[i].getParameterTypes();
                if (parms.length == 1) {
                    if (parms[0].getName().equalsIgnoreCase("[B")) {
                        cons[i].setAccessible(true);
                        break;
                    }
                }
            }
            dibCons = cons[i];
        } catch (Exception e) {
            System.out.println("Caught unexpected exception " + e);
            throw e;
        }
        return dibCons;
    }

    public Constructor getCons(){
        return dibCons;
    }

}
