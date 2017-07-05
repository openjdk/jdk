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

/*
  @test
  @bug 4017728 4079849
  @summary Check for correct Array Bounds check in read of FileInputStream and
  RandomAccessFile
  */

import java.io.*;


/*
 * The test calls the read(byte buf[] , int off , int len) of FileInputStream with
 * different values of off and len to see if the ArrayOutOfBoundsException is
 * thrown according to the JLS1.0 specification.  The read(...) method calls
 * readBytes(...) in native code(io_util.c).  The read(...) method in RandomAccessFile
 * also calls the same native method.  So one should see similar results.
 */


public class ReadBytesBounds {

    public static void main(String argv[]) throws Exception{

        int num_test_cases = 12;
        int off[] =     {-1 , -1 ,  0 , 0  , 33 , 33 , 0  , 32 , 32 , 4  , 1  , 0};
        int len[] =     {-1 ,  0 , -1 , 33 , 0  , 4  , 32 , 0  , 4  , 16 , 31 , 0};
        boolean results[] = { false ,  false ,  false , false  , false  , false  ,
                              true  , true  , false  , true  , true  , true};


        FileInputStream fis = null;
        RandomAccessFile raf = null;
        byte b[] = new byte[32];

        int num_good = 0;
        int num_bad = 0;

        String dir = System.getProperty("test.src", ".");
        File testFile = new File(dir, "input.txt");
        fis = new FileInputStream(testFile);
        for(int i = 0; i < num_test_cases; i++) {

            try {
                int bytes_read = fis.read(b , off[i] , len[i]);
            } catch(IndexOutOfBoundsException aiobe) {
                if (results[i]) {
                    throw new RuntimeException("Unexpected result");
                }
                else {
                    num_good++;
                }
                continue;
            }

            if (results[i]) {
                num_good++;
            }
            else {
                throw new RuntimeException("Unexpected result");
            }

        }
        System.out.println("Results for FileInputStream.read");
        System.out.println("\nTotal number of test cases = " + num_test_cases +
                           "\nNumber succeded = " + num_good +
                           "\nNumber failed   = " + num_bad);



        num_good = 0;
        num_bad = 0;

        raf = new RandomAccessFile(testFile , "r");
        for(int i = 0; i < num_test_cases; i++) {

            try {
                int bytes_read = raf.read(b , off[i] , len[i]);
            } catch(IndexOutOfBoundsException aiobe) {
                if (results[i]) {
                    throw new RuntimeException("Unexpected result");
                }
                else {
                    num_good++;
                }
                continue;
            }

            if (results[i]) {
                num_good++;
            }
            else {
                throw new RuntimeException("Unexpected result");
            }

        }

        System.out.println("Results for RandomAccessFile.read");
        System.out.println("\nTotal number of test cases = " + num_test_cases +
                           "\nNumber succeded = " + num_good +
                           "\nNumber failed   = " + num_bad);


    }

}
