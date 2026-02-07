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
 * @bug 8366453
 * @summary TLS 1.3 KeyUpdate record is not rejected if not on a record boundary
 * @library /javax/net/ssl/templates
 *          /test/lib
 * @run main/othervm TLS13UnalignedKeyChangeHSMessage
 */

public class TLS13UnalignedKeyChangeHSMessage extends SSLEngineTemplate {

    private static final String exMsg = "(unexpected_message) SERVER_HELLO messages must align with a record boundary";

    private static final int SERVER_HELLO_ID = 2;
    // HandShake type: encrypted_extension(8)
    // Body length:    0x000002
    // Body:           0x0000
    private static final byte[] SERVER_ENCRYPTED_EXTENSIONS = { 0x08, 0x00, 0x00, 0x02, 0x00, 0x00 };

    protected TLS13UnalignedKeyChangeHSMessage()
            throws Exception {
        super();
    }

    @Override
    protected ContextParameters getServerContextParameters() {
        return getContextParameters();
    }

    @Override
    protected ContextParameters getClientContextParameters() {
        return getContextParameters();
    }

    private ContextParameters getContextParameters() {
        return new ContextParameters("TLSv1.3", "PKIX", "SunX509");
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 0) {
            throw new RuntimeException("Wrong number of arguments");
        }

        new TLS13UnalignedKeyChangeHSMessage().run();
    }

    protected void run() throws Exception {

        // Run handshake until the server sends a ServerHello
        ByteBuffer serverHelloMsg = null;
        do {
            clientEngine.wrap(clientOut, cTOs);
            runDelegatedTasks(clientEngine);

            serverEngine.wrap(serverOut, sTOc);
            runDelegatedTasks(serverEngine);

            cTOs.flip();
            sTOc.flip();

            // check if server sent ServerHello
            if (sTOc.remaining() > 5
                    && (serverHelloMsg =
                            extractHandshakeMsg(sTOc, SERVER_HELLO_ID, false)
                        ) != null) {
                break;
            }

            clientEngine.unwrap(sTOc, clientIn);
            runDelegatedTasks(clientEngine);

            serverEngine.unwrap(cTOs, serverIn);
            runDelegatedTasks(serverEngine);

            cTOs.compact();
            sTOc.compact();
        } while (serverEngine.getHandshakeStatus()
                != HandshakeStatus.NOT_HANDSHAKING);

        if (serverHelloMsg == null) {
            throw new RuntimeException("ServerHello hs message not found!");
        }

        // Send unaligned ServerHello to client, by concatenating a valid
        // (empty) ServerEncryptedExtensions to the existing ServerHello
        ByteBuffer unalignedMessage = ByteBuffer.allocate(sTOc.limit()
                + 6);
        // grab record header from original message
        unalignedMessage.put(sTOc.get()); // Record type
        unalignedMessage.putShort(sTOc.getShort()); // TLS legacy version

        // discard original body length
        int _originalBodyLen = sTOc.getShort();

        // serverHello hs header + serverHello hs body length + encrypted extensions
        // hs message
        unalignedMessage.putShort((short)(4 + serverHelloMsg.remaining() +
                                SERVER_ENCRYPTED_EXTENSIONS.length));

        unalignedMessage.putInt((SERVER_HELLO_ID << 24) | serverHelloMsg.remaining());
        unalignedMessage.put(serverHelloMsg);

        unalignedMessage.put(SERVER_ENCRYPTED_EXTENSIONS);

        unalignedMessage.flip();

        runAndCheckException(
                () -> {
                    clientEngine.unwrap(unalignedMessage, clientIn);
                    runDelegatedTasks(clientEngine);

                    cTOs.compact();

                    // need to wrap again on the client to throw delegated exception
                    clientEngine.wrap(clientOut, cTOs);
                },
                ex -> {
                    assertTrue(ex instanceof SSLProtocolException);
                    assertEquals(exMsg, ex.getMessage());
                });
    }
}
