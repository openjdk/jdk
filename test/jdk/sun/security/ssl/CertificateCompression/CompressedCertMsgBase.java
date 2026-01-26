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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

public abstract class CompressedCertMsgBase extends SSLSocketTemplate {

    protected static String runAndGetLog(Runnable runnable) {
        System.setProperty("javax.net.debug", "ssl");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream err = new PrintStream(baos);
        PrintStream origErr = System.err;
        System.setErr(err);

        runnable.run();
        err.close();

        // Save the log output and then print it as usual.
        String log = baos.toString();
        System.setErr(origErr);
        System.err.print(log);
        return log;
    }

    // Helper method to find log messages.
    protected static int countSubstringOccurrences(String str, String sub) {
        if (str == null || sub == null || sub.isEmpty()) {
            return 0;
        }

        int count = 0;
        int lastIndex = 0;

        while ((lastIndex = str.indexOf(sub, lastIndex)) != -1) {
            count++;
            lastIndex += sub.length();
        }

        return count;
    }
}
