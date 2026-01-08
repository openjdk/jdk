/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8349890
 * @summary Make sure debug with AVA option does not interfere with parsing special characters.
 * @library /test/lib
 * @run main/othervm -Djava.security.debug=x509:ava PrintX500PrincipalInDebugModeWithAvaOption
 */

import jdk.test.lib.Asserts;
import javax.security.auth.x500.X500Principal;

public class PrintX500PrincipalInDebugModeWithAvaOption {

    public static void main(String[] args) throws Exception {

        X500Principal name = new X500Principal("cn=john doe + l=ca\\+lifornia + l =sf, O=Ñ");

        //Test the name in default String format. This will perform the hex conversion to
        //"\\C3\\91" for special character "Ñ"
        Asserts.assertTrue(name.toString().contains("\\C3\\91"),
                "String does not contain expected value");

        //Test the name in RFC2253 format. This should skip the hex conversion to return
        //"\u00d1" for special character "Ñ"
        Asserts.assertTrue(name.getName().contains("\u00d1"),
                "String does not contain expected value");

        //Test the name in canonical name in RFC2253 format. This should skip the hex conversion to return
        //"n\u0303" for special character "Ñ"
        Asserts.assertTrue(name.getName(X500Principal.CANONICAL).contains("n\u0303"),
                "String does not contain expected value");


        //Test to print name in RFC1779 format. This should skip the hex conversion to print
        //"\u00d1" for special character "Ñ"
        Asserts.assertTrue(name.getName(X500Principal.RFC1779).contains("\u00d1"),
                "String does not contain expected value");
    }
}
