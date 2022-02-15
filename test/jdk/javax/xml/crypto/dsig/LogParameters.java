/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.hexdump.HexPrinter;

import java.io.ByteArrayOutputStream;
import java.util.logging.*;

/**
 * @test
 * @bug 8247907 8254267
 * @summary Tests that parameterized log messages (the ones that use "{}") generated
 * through the use of com.sun.org.slf4j.internal.Logger work as expected and the parameter
 * values are properly replaced in the logged message.
 * @library /test/lib
 * @modules java.xml.crypto/com.sun.org.slf4j.internal
 * @run main/othervm LogParameters
 */
public class LogParameters {

    private static final Logger julLogger = Logger.getLogger(LogParameters.class.getName());

    public static void main(String[] args) {

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        julLogger.setLevel(Level.ALL);
        Handler h = new StreamHandler(bout, new SimpleFormatter());
        h.setLevel(Level.ALL);
        julLogger.addHandler(h);

        // now create a com.sun.org.slf4j.internal.Logger for the same class
        // for which we just configured the java.util.logging.Logger instance
        com.sun.org.slf4j.internal.Logger log =
                com.sun.org.slf4j.internal.LoggerFactory.getLogger(LogParameters.class);
        // issue a parameterized log message
        log.debug("I have {} {}s.", 10, "apple");

        h.flush();

        byte[] data = bout.toByteArray();
        String s = new String(data);
        if (!s.contains("LogParameters main")
                || !s.contains("FINE: I have 10 apples.")) {
            HexPrinter.simple().format(data);
            throw new RuntimeException("Unexpected log output: " + s);
        }
    }
}
