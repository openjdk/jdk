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

import static jdk.test.lib.Asserts.assertEquals;
import static jdk.test.lib.Asserts.assertTrue;
import static jdk.test.lib.Utils.runAndCheckException;

import java.nio.ByteBuffer;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLProtocolException;

/*
 * @test
 * @bug 8366244
 * @summary TLS1.3 ChangeCipherSpec message received after the client's
 *          Finished message should trigger a connection abort with
 *          "unexpected message"
 * @library /javax/net/ssl/templates
 *          /test/lib
 * @run main/othervm TLS13ChangeCipherSpecAfterFinished false
 * @run main/othervm TLS13ChangeCipherSpecAfterFinished true
 */

public class TLS13ChangeCipherSpecAfterFinished extends SSLEngineTemplate {

    private static final ContextParameters testContextParams =
            new ContextParameters("TLSv1.3", "PKIX", "SunX509");

    private final String exMsg;

    protected TLS13ChangeCipherSpecAfterFinished(String exMsg)
            throws Exception {
        super();
        this.exMsg = exMsg;
    }

    @Override
    protected ContextParameters getServerContextParameters() {
        return testContextParams;
    }

    @Override
    protected ContextParameters getClientContextParameters() {
        return testContextParams;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new RuntimeException("Wrong number of arguments");
        }

        boolean useCompatibilityMode = Boolean.parseBoolean(args[0]);

        if (useCompatibilityMode) {
            // Test existing unexpected message detection mechanism
            // with client's Middlebox Compatibility Mode on
            // (which is the default).
            new TLS13ChangeCipherSpecAfterFinished(
                    "(unexpected_message) Unexpected content: 20").run();
        } else {
            // Switch off client Middlebox Compatibility Mode: do not send CCS
            // immediately before the second flight. This is needed to reproduce
            // the conditions for the issue to manifest itself.
            System.setProperty("jdk.tls.client.useCompatibilityMode", "false");

            // Test the fix.
            new TLS13ChangeCipherSpecAfterFinished("(unexpected_message) "
                    + "Malformed or unexpected ChangeCipherSpec "
                    + "message").run();
        }
    }

    protected void run() throws Exception {

        // Complete handshake.
        do {
            clientEngine.wrap(clientOut, cTOs);
            runDelegatedTasks(clientEngine);

            serverEngine.wrap(serverOut, sTOc);
            runDelegatedTasks(serverEngine);

            cTOs.flip();
            sTOc.flip();

            clientEngine.unwrap(sTOc, clientIn);
            runDelegatedTasks(clientEngine);

            serverEngine.unwrap(cTOs, serverIn);
            runDelegatedTasks(serverEngine);

            cTOs.compact();
            sTOc.compact();
        } while (serverEngine.getHandshakeStatus()
                != HandshakeStatus.NOT_HANDSHAKING);

        // Send a valid CCS message after Finished.
        ByteBuffer changeCipher = ByteBuffer.allocate(6);
        // ContentType type: change_cipher_spec(20)
        // ProtocolVersion:  0x0303
        // uint16 length:    0x0001
        // opaque fragment:  0x01
        changeCipher.put(new byte[]{20, 3, 3, 0, 1, 1});
        changeCipher.flip();

        runAndCheckException(
                () -> serverEngine.unwrap(changeCipher, serverIn),
                ex -> {
                    assertTrue(ex instanceof SSLProtocolException);
                    assertEquals(exMsg, ex.getMessage());
                });
    }
}
