/*
 * Copyright (c) 2009, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @modules java.base/java.lang:+open
 * @run main/othervm Password
 */

import com.sun.security.auth.callback.TextCallbackHandler;


import javax.security.auth.callback.*;
import java.io.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

import jdk.test.lib.Asserts;

public class Password {
    private static final String VISIBLE_LINE = "lineVisible";

    public static void main(String args[]) throws Exception {

        InputStream originalInput = System.in;

        // setting the initial input stream from the System class
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(System.class, MethodHandles.lookup());
        VarHandle initialIn = lookup.findStaticVarHandle(System.class, "initialIn", InputStream.class);

        // setting the input stream and the output stream
        ByteArrayInputStream inputStream = (new ByteArrayInputStream((VISIBLE_LINE + "\nlineInvisible\n").getBytes()));
        System.setIn(inputStream);
        initialIn.set(inputStream);

        // handling the password callbacks, as the input stream is here the invisible should not echo and should be null
        TextCallbackHandler h = new TextCallbackHandler();
        PasswordCallback nc = new PasswordCallback("Invisible: ", false);
        PasswordCallback nc2 = new PasswordCallback("Visible: ", true);

        System.out.println("Two passwords will be prompted for. They will automatically be populated. " +
                "The invisible password will remain null with this input stream configuration.");
        Callback[] callbacks = {nc, nc2};
        h.handle(callbacks);

        //reverting everything back
        initialIn.set(originalInput);
        System.setIn(originalInput);


        Asserts.assertNull(nc.getPassword());
        Asserts.assertEquals(VISIBLE_LINE, new String(nc2.getPassword()));
    }
}
