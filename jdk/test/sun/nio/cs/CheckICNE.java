/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
   @bug 4849617
   @summary Checks "+" is a legal character for charset name
 */
import java.nio.charset.*;

public class CheckICNE {
    static int failed = 0;
    public static void main (String[] args) throws Exception {
        try {
            Charset.forName("abc+");
        } catch (UnsupportedCharsetException uce) {}

        try {
            java.nio.charset.Charset.forName("+abc");
        } catch (IllegalCharsetNameException icne) {}

        String[] euros = {"PC-Multilingual-850+euro",
                          "ebcdic-us-037+euro",
                          "ebcdic-de-273+euro",
                          "ebcdic-no-277+euro",
                          "ebcdic-dk-277+euro",
                          "ebcdic-fi-278+euro",
                          "ebcdic-se-278+euro",
                          "ebcdic-it-280+euro",
                          "ebcdic-es-284+euro",
                          "ebcdic-gb-285+euro",
                          "ebcdic-fr-277+euro",
                          "ebcdic-international-500+euro",
                          "ebcdic-s-871+euro"
        };

        System.out.println("Test Passed!");
    }
}
