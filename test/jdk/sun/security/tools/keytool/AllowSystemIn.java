/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8368692
 * @summary Restrict Password::readPassword from reading from System.in
 * @library /test/lib
 * @run main AllowSystemIn succeed
 * @run main/othervm -Djdk.security.password.allowSystemIn=true AllowSystemIn succeed
 * @run main/othervm -Djdk.security.password.allowSystemIn=false AllowSystemIn fail
 * @run main/othervm -Djdk.security.password.allowSystemIn=bogus AllowSystemIn invalid
 */

import com.sun.security.auth.callback.TextCallbackHandler;
import jdk.test.lib.Asserts;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.PasswordCallback;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

public class AllowSystemIn{

    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "succeed" -> Asserts.assertEQ("password", getPassword());
            case "fail" -> Asserts.assertThrows(
                    UnsupportedOperationException.class,
                    AllowSystemIn::getPassword);
            case "invalid" -> Asserts.assertThrows(
                    ExceptionInInitializerError.class, // implementation detail
                    AllowSystemIn::getPassword);
        }
    }

    static String getPassword() throws Exception {
        var in = System.in;
        try {
            var bin = new ByteArrayInputStream(
                    "password".getBytes(StandardCharsets.UTF_8));
            System.setIn(bin);
            var pb = new PasswordCallback("> ", false);
            new TextCallbackHandler().handle(new Callback[]{pb});
            return new String(pb.getPassword());
        } finally {
            System.setIn(in);
        }
    }
}
