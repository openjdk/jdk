/*
 * Copyright (c) 2009, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6825240 6829785
 * @summary Password.readPassword() echos the input when System.Console is null
 * @run main/manual Password
 */

/*
 * This scenario cannot be automated because util/Password.java verifies the given input stream is
 * equal to the initialSystemIn. This prevents the test from providing a custom input stream.
 *
 *  Steps to run the test:
 *  1) Compile the class using the JDK version being tested: '<JdkBin>/javac Password.java'
 *  2) Run the test using the JDK version being tested: '<JdkBin>/java -cp . Password'
 *  3) Type in the first password, it should not be visible in the console
 *  4) Type in the second password, it should be visible in the console
 *  5) The final output line displays the entered passwords, both should be visible
 */

import com.sun.security.auth.callback.TextCallbackHandler;
import javax.security.auth.callback.*;

public class Password {
   public static void main(String args[]) throws Exception {
        TextCallbackHandler h = new TextCallbackHandler();
        PasswordCallback nc = new PasswordCallback("Invisible: ", false);
        PasswordCallback nc2 = new PasswordCallback("Visible: ", true);

        System.out.println("Two passwords will be prompted for. The first one " +
                "should have echo off, the second one on. Otherwise, this test fails");
        Callback[] callbacks = { nc, nc2 };
        h.handle(callbacks);
        System.out.println("You input " + new String(nc.getPassword()) +
                " and " + new String(nc2.getPassword()));
   }
}
