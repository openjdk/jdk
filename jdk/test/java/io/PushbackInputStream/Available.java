/*
 * Copyright 1997 Sun Microsystems, Inc.  All Rights Reserved.
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


/* @test
   @bug 4017414
   @summary Check for correct implementation of PushbackInputStream.available
   */

import java.io.*;



public class Available{

    private static void dotest(PushbackInputStream in , int expected)
        throws Exception
    {

        int got = in.available();
        System.err.println("available must be " + expected + " , got : " + got);
        if (got != expected) {
            throw new
                RuntimeException("Unexpected number of bytes available in the PushBackInputStream");
        }

    }



    public static void main(String args[]) throws Exception{

        PushbackInputStream in = new
            PushbackInputStream(new ByteArrayInputStream(new byte[10]),5);

        dotest(in , 10);

        in.read();
        dotest(in , 9);

        in.unread(20);
        dotest(in , 10);

        in.unread(20);
        dotest(in , 11);

    }

}
