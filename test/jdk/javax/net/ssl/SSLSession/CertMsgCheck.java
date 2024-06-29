/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @library /javax/net/ssl/templates
 * @bug 8311644
 * @summary Verify CertificateMessage alerts are correct to the TLS specs
 * @run main/othervm -Djdk.tls.client.protocols=TLSv1.2 CertMsgCheck handshake_failure
 * @run main/othervm -Djdk.tls.client.protocols=TLSv1.3 CertMsgCheck certificate_required
 *
 */

public class CertMsgCheck {

    public static void main(String[] args) throws Exception {
        // Start server
        TLSBase.Server server = new TLSBase.ServerBuilder().setClientAuth(true).
            build();

        // Initial client session
        TLSBase.Client client1 = new TLSBase.Client(true, false);

        server.getSession(client1).getSessionContext();
        server.done();

        var eList = server.getExceptionList();
        System.out.println("Exception list size is " + eList.size());

        for (Exception e : eList) {
            System.out.println("Looking at " + e.getClass() + " " +
                e.getMessage());
            if (e.getMessage().contains(args[0])) {
                System.out.println("Found correct exception: " + args[0] +
                    " in " + e.getMessage());
                return;
            } else {
                System.out.println("No \"" + args[0] + "\" found.");
            }
        }

        throw new Exception("Failed to find expected alert: " + args[0]);
    }
}
