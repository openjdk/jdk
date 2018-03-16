/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8195976
 * @summary Tests that we can get the attributes of a DNS entry using special
 *          qualifiers.
 * @modules java.base/sun.security.util
 * @library ../lib/
 * @build DNSTestUtils DNSServer DNSTracer
 * @run main GetAny
 */

import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Hashtable;

public class GetAny {
    private static final String KEY = "host1";

    private static final String[] MANDATORY = { "A", "MX", "HINFO", "TXT", "29"
            // "LOC"
    };

    private static final String[] OPTIONAL = {};

    public static void main(String argv[]) throws Exception {
        // Create socket on localhost only to avoid possible noise packet
        DatagramSocket socket = new DatagramSocket(0,
                InetAddress.getLoopbackAddress());

        // initialize test
        Hashtable<Object, Object> env;

        env = DNSTestUtils.initEnv(socket, GetAny.class.getName(), argv);

        DirContext ctx = null;

        try {
            // connect to server
            ctx = new InitialDirContext(env);

            // Any type from IN class
            Attributes retAttrs = ctx.getAttributes(KEY, new String[] { "*" });
            DNSTestUtils.verifySchema(retAttrs, MANDATORY, OPTIONAL);

            retAttrs = ctx.getAttributes(KEY, new String[] { "* *" });
            DNSTestUtils.verifySchema(retAttrs, MANDATORY, OPTIONAL);

            retAttrs = ctx.getAttributes(KEY, new String[] { "IN *" });
            DNSTestUtils.verifySchema(retAttrs, MANDATORY, OPTIONAL);

        } finally {
            DNSTestUtils.cleanup(ctx);
        }
    }
}
