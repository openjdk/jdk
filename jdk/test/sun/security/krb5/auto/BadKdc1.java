/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @test
 * @bug 6843127
 * @run main/timeout=300 BadKdc1
 * @summary krb5 should not try to access unavailable kdc too often
 */

import java.io.*;
import java.security.Security;

public class BadKdc1 {

   public static void main(String[] args)
           throws Exception {
       Security.setProperty("krb5.kdc.bad.policy", "tryLess");
       BadKdc.go(
               new int[]{1,2,1,2,1,2,2,2,2,2,2,2,3,2,1,2,2,2,3,2}, // 1, 2
               // The above line means try kdc1 for 2 seconds, then kdc1
               // for 2 seconds,..., finally kdc3 for 2 seconds.
               new int[]{1,2,2,2,3,2,1,2,2,2,3,2}, // 1, 2
               // refresh
               new int[]{1,2,1,2,1,2,2,2,2,2,2,2,3,2,1,2,2,2,3,2}, // 1, 2
               // k3 off, k2 on
               new int[]{1,2,2,2,1,2,2,2}, // 1
               // k1 on
               new int[]{1,2,1,2}  // empty
       );
   }
}

