/*
 * Copyright (c) 2004, 2023, Oracle and/or its affiliates. All rights reserved.
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

//
// SunJSSE does not support dynamic system properties, no way to re-use
// system properties in samevm/agentvm mode.
//

/*
 * @test
 * @bug 5019096
 * @summary Add scatter/gather APIs for SSLEngine
 * @library /javax/net/ssl/templates
 * @run main/othervm CloseStart
 */

//
// Check to see if the args are being parsed properly.
//

import javax.net.ssl.*;

public class CloseStart extends SSLContextTemplate {

    private static void checkDone(SSLEngine ssle) throws Exception {
        if (!ssle.isInboundDone()) {
            throw new Exception("isInboundDone isn't done");
        }
        if (!ssle.isOutboundDone()) {
            throw new Exception("isOutboundDone isn't done");
        }
    }

    private static void runTest2(SSLEngine ssle) throws Exception {
        ssle.closeOutbound();
        checkDone(ssle);
    }

    public static void main(String args[]) throws Exception {
        new CloseStart().run();
    }

    private void run() throws Exception {
        SSLEngine ssle = createSSLEngine();
        ssle.closeInbound();
        if (!ssle.isInboundDone()) {
            throw new Exception("isInboundDone isn't done");
        }

        ssle = createSSLEngine();
        ssle.closeOutbound();
        if (!ssle.isOutboundDone()) {
            throw new Exception("isOutboundDone isn't done");
        }

        System.out.println("Test Passed.");
    }

    /*
     * Create an initialized SSLContext to use for this test.
     */
    private SSLEngine createSSLEngine() throws Exception {

        SSLContext sslCtx = createClientSSLContext();
        SSLEngine ssle = sslCtx.createSSLEngine("client", 1001);
        ssle.setUseClientMode(true);

        return ssle;
    }
}
