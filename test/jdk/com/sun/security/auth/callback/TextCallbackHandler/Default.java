/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @library /test/lib /java/security/testlibrary
 * @bug 4470717
 * @summary fix default handling and other misc
 * @run main/othervm Default
 */

import com.sun.security.auth.callback.TextCallbackHandler;
import jdk.test.lib.Asserts;

import javax.security.auth.callback.*;
import java.io.*;

public class Default {
    public static void main(String args[]) throws Exception {
        InputStream in = System.in;
        PrintStream err = System.err;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final String defaultName = "charlie";
        final String simulatedInput = "-1\n-1\n";
        HumanInputStream humanInputStream = new HumanInputStream(simulatedInput);

        try (PrintStream prints = new PrintStream(baos)) {
            System.setIn(humanInputStream);
            System.setErr(prints);
            NameCallback nameCallback = new NameCallback("Name: ", defaultName);
            ConfirmationCallback confirmationCallback = new ConfirmationCallback(
                    "Correct?",
                    ConfirmationCallback.INFORMATION,
                    ConfirmationCallback.YES_NO_OPTION,
                    ConfirmationCallback.NO);
            new TextCallbackHandler().handle(new Callback[]{nameCallback, confirmationCallback});

            Asserts.assertEquals(nameCallback.getDefaultName(), defaultName);
            Asserts.assertEquals(confirmationCallback.getSelectedIndex(), ConfirmationCallback.NO);

        } finally {
            System.setIn(in);
            System.setErr(err);
        }

        // check that the default name and confirmation were visible in the output
        Asserts.assertTrue(baos.toString().contains(String.format("Name:  [%s]", defaultName)));
        Asserts.assertTrue(baos.toString().contains("1. No [default]"));
    }
}
