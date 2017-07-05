/*
 * Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 4921029
 * @bug 6656849
 * @summary  java.net.Inet6Address fails to be serialized with IPv6 support
 * @summary  NullPointerException thrown while de-serializing IPV6 Address.
 */

import java.net.*;
import java.io.*;
import java.util.*;

public class Serialize {

     public static void main(String[] args)
         throws Exception
     {
         Enumeration nifs = NetworkInterface.getNetworkInterfaces();
         while (nifs.hasMoreElements()) {
            NetworkInterface nif = (NetworkInterface)nifs.nextElement();
            Enumeration addrs = nif.getInetAddresses();
            while (addrs.hasMoreElements()) {
                Object o = addrs.nextElement();
                if (o instanceof Inet6Address) {
                    Inet6Address addr = (Inet6Address) o;
                    System.out.println ("serializing " + addr);
                    if (!test (addr)) {
                        throw new RuntimeException ("failed on " + addr.toString());
                    }

                    /* now reconstruct address with string name
                     * and test again
                     */
                    byte[] bytes = addr.getAddress();
                    Inet6Address addr1 = Inet6Address.getByAddress ("foo", bytes, nif);
                    System.out.println ("serializing " + addr1);
                    if (!test (addr1)) {
                        throw new RuntimeException ("failed on " + addr1.toString());
                    }
                }
            }
        }

        ObjectInputStream ois;
        Inet6Address nobj;

         // check ::1 object serialised with 1.4.2 is readable

         File file = new File (System.getProperty("test.src"), "serial1.4.2.ser");
         ois = new ObjectInputStream(new FileInputStream(file));
         nobj = (Inet6Address) ois.readObject();
         ois.close();
         if (!nobj.equals (InetAddress.getByName ("::1"))) {
            throw new RuntimeException ("old ::1 not deserialized right");
         }

         System.out.println(nobj);

         // create an address with an unlikely numeric scope_id
         if (!test ((Inet6Address)InetAddress.getByName ("fe80::1%99"))) {
             throw new RuntimeException ("test failed on fe80::1%99");
         }

         // Deserialize an Inet6 address with a named interface
         file = new File (System.getProperty("test.src"), "serial-bge0.ser");
         ois = new ObjectInputStream(new FileInputStream(file));
         try {
             nobj = (Inet6Address) ois.readObject();
         } catch (NullPointerException e) {
             throw new RuntimeException("6656849 Not fixed: NullPointer when deserializing");
         } finally {
             ois.close();
         }
         System.out.println(nobj);
         System.out.println("All tests passed");
     }

     static boolean test (Inet6Address obj) throws Exception {
         ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("i6a1.ser"));
         oos.writeObject(obj);
         oos.close();

         ObjectInputStream ois = new ObjectInputStream(new FileInputStream("i6a1.ser"));
         Inet6Address nobj = (Inet6Address) ois.readObject();
         ois.close();

         if (nobj.equals(obj)) {
             return true;
         } else {
             return false;
         }
     }
 }
