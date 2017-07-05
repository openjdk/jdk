/*
 * Copyright (c) 1997, Oracle and/or its affiliates. All rights reserved.
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

/* @test
   @bug 4017158
   @summary Check for correct implementation of ByteArrayInputStream.write
   */

import java.io.*;


public class WriteBounds{

    private static void dotest(byte[] b, int off, int len,
                               ByteArrayOutputStream baos)
        throws Exception
    {

        if (b != null) {
            System.err.println("ByteArrayOutStream.write -- b.length = " +
                               b.length + " off = " + off + " len = " + len);
        }
        else{
            System.err.println("ByteArrayOutStream.write - b is null off = " +
                               off + " len = " + len);
        }

        try {
            baos.write(b, off, len);
        } catch (IndexOutOfBoundsException e) {
            System.err.println("IndexOutOfBoundsException is thrown -- OKAY");
        } catch (NullPointerException e) {
            System.err.println("NullPointerException is thrown -- OKAY");
        } catch (Throwable e){
            throw new RuntimeException("Unexpected Exception is thrown");
        }

    }

    public static void main( String argv[] ) throws Exception {

        ByteArrayOutputStream y1;
        byte array1[]={1 , 2 , 3 , 4 , 5};     // Simple array

        //Create new ByteArrayOutputStream object
        y1 = new ByteArrayOutputStream(5);

        dotest(array1, 0, Integer.MAX_VALUE , y1);
        dotest(array1, 0, array1.length+100, y1);
        dotest(array1, -1, 2, y1);
        dotest(array1, 0, -1, y1);
        dotest(null, 0, 2, y1);

    }

}
