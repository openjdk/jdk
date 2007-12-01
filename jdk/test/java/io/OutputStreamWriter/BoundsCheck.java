/*
 * Copyright 1999 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @bug 4221901
 * @summary Ensure that negative offset or negative len parameter for
 *          write(String str, int off, int len) throws
 *          IndexOutOfBoundsException.
 */

import java.io.*;

public class BoundsCheck {
    public static void main(String args[]) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        OutputStreamWriter osw = new OutputStreamWriter(bos);
        String data = "Data to be written";
        char cdata[] = {'a', 'b', 'c', 'd', 'a', 'b', 'c', 'd'};

        boolean caughtException = false;
        try {
            osw.write(data, -3, 5);
            throw new RuntimeException("Test failed for negative offset");
        } catch (IndexOutOfBoundsException  e){ }

        try {
            osw.write(data, 3, -5);
            throw new RuntimeException("Test failed for negative length");
        } catch (IndexOutOfBoundsException  e){ }

        try {
            osw.write(data, 3, 75);
            throw new RuntimeException("Test failed for len+off > str.length");
        } catch (IndexOutOfBoundsException  e){ }

        try {
            osw.write(cdata, -3, 5);
            throw new RuntimeException("Test failed for negative offset");
        } catch (IndexOutOfBoundsException  e){ }

        try {
            osw.write(cdata, 3, -5);
            throw new RuntimeException("Test failed for negative length");
        } catch (IndexOutOfBoundsException  e){ }

        try {
            osw.write(cdata, 3, 75);
            throw new RuntimeException("Test failed for len+off > str.length");
        } catch (IndexOutOfBoundsException  e){ }
    }
}
