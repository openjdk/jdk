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
   @summary Simple stream-copy test for InputStreamReader & OutputStreamWriter
 */

import java.io.*;

public class Cat {

    public static int count = 10000;

    public static void main(String[] args) throws IOException {
        Reader in = new InputStreamReader(new ABCInputStream(count));
        Writer out = new OutputStreamWriter(new ABCOutputStream(count));
        int c;

        while ((c = in.read()) != -1) {
            out.write(c);
            System.err.print((char)c);
        }
        in.close();
        out.close();
        System.err.println();
    }

}
