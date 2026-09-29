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
 * @bug 8326643
 * @summary Test for out-of-sequence change_cipher_spec in TLSv1.3
 * @library /javax/net/ssl/templates
 * @run main/othervm EngineOutOfSeqCCS isHRRTest
 * @run main/othervm EngineOutOfSeqCCS
 */

import java.nio.ByteBuffer;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;

public class EngineOutOfSeqCCS extends SSLEngineTemplate {

    /*
     * Enables logging of the SSLEngine operations.
     */
    private static final boolean logging = true;
    private static final boolean dumpBufs = true;

    // Define a few basic TLS records we might need
    private static final int TLS_RECTYPE_CCS = 0x14;
    private static final int TLS_RECTYPE_ALERT = 0x15;
    private static final int TLS_RECTYPE_HANDSHAKE = 0x16;
    private static final int TLS_RECTYPE_APPDATA = 0x17;

    SSLEngineResult clientResult, serverResult;

    public EngineOutOfSeqCCS() throws Exception {
        super();
    }

    public static void main(String[] args) throws Exception{
        new EngineOutOfSeqCCS().runDemo(args.length > 0 &&
                args[0].equals("isHRRTest"));
    }

    private void runDemo(boolean isHRRTest) throws Exception {

            if (isHRRTest) {
                SSLParameters sslParams = new SSLParameters();
                sslParams.setNamedGroups(new String[] {"secp384r1"});
                serverEngine.setSSLParameters(sslParams);
            }
            // Client generates Client Hello
            clientResult = clientEngine.wrap(clientOut, cTOs);
            log("client wrap: ", clientResult);
            runDelegatedTasks(clientEngine);
            cTOs.flip();
            dumpByteBuffer("CLIENT-TO-SERVER", cTOs);

            // Server consumes Client Hello
            serverResult = serverEngine.unwrap(cTOs, serverIn);
            log("server unwrap: ", serverResult);
            runDelegatedTasks(serverEngine);
            cTOs.compact();

            // Server generates ServerHello/HelloRetryRequest
            serverResult = serverEngine.wrap(serverOut, sTOc);
            log("server wrap: ", serverResult);
            runDelegatedTasks(serverEngine);
            sTOc.flip();

            dumpByteBuffer("SERVER-TO-CLIENT", sTOc);

            // client consumes ServerHello/HelloRetryRequest
            clientResult = clientEngine.unwrap(sTOc, clientIn);
            log("client unwrap: ", clientResult);
            runDelegatedTasks(clientEngine);
            sTOc.compact();

            // Server generates CCS
            serverResult = serverEngine.wrap(serverOut, sTOc);
            log("server wrap: ", serverResult);
            runDelegatedTasks(serverEngine);
            sTOc.flip();
            dumpByteBuffer("SERVER-TO-CLIENT", sTOc);

            if (isTlsMessage(sTOc, TLS_RECTYPE_CCS)) {
                System.out.println("=========== CCS found ===========");
            } else {
                // In TLS1.3 middlebox compatibility mode the server sends a
                // dummy change_cipher_spec record immediately after its
                // first handshake message. This may either be after
                // a ServerHello or a HelloRetryRequest.
                // (RFC 8446, Appendix D.4)
                throw new SSLException(
                    "Server should generate change_cipher_spec record");
            }
            clientEngine.closeOutbound();
            serverEngine.closeOutbound();
    }

    /**
     * Look at an incoming TLS record and see if it is the desired
     * record type, and where appropriate the correct subtype.
     *
     * @param srcRecord The input TLS record to be evaluated.  This
     *        method will only look at the leading message if multiple
     *        TLS handshake messages are coalesced into a single record.
     * @param reqRecType The requested TLS record type
     * @param recParams Zero or more integer sub type fields.  For CCS
     *        and ApplicationData, no params are used.  For handshake records,
     *        one value corresponding to the HandshakeType is required.
     *        For Alerts, two values corresponding to AlertLevel and
     *        AlertDescription are necessary.
     *
     * @return true if the proper handshake message is the first one
     *         in the input record, false otherwise.
     */
    private boolean isTlsMessage(ByteBuffer srcRecord, int reqRecType,
            int... recParams) {
        boolean foundMsg = false;

        if (srcRecord.hasRemaining()) {
            srcRecord.mark();

            // Grab the fields from the TLS Record
            int recordType = Byte.toUnsignedInt(srcRecord.get());
            byte ver_major = srcRecord.get();
            byte ver_minor = srcRecord.get();

            if (recordType == reqRecType) {
                // For any zero-length recParams, making sure the requested
                // type is sufficient.
                if (recParams.length == 0) {
                    foundMsg = true;
                } else {
                    switch (recordType) {
                        case TLS_RECTYPE_CCS:
                        case TLS_RECTYPE_APPDATA:
                            // We really shouldn't find ourselves here, but
                            // if someone asked for these types and had more
                            // recParams we can ignore them.
                            foundMsg = true;
                            break;
                        case TLS_RECTYPE_ALERT:
                            // Needs two params, AlertLevel and
                            //AlertDescription
                            if (recParams.length != 2) {
                                throw new RuntimeException(
                                    "Test for Alert requires level and desc.");
                            } else {
                                int level = Byte.toUnsignedInt(
                                            srcRecord.get());
                                int desc = Byte.toUnsignedInt(srcRecord.get());
                                if (level == recParams[0] &&
                                        desc == recParams[1]) {
                                    foundMsg = true;
                                }
                            }
                            break;
                        case TLS_RECTYPE_HANDSHAKE:
                            // Needs one parameter, HandshakeType
                            if (recParams.length != 1) {
                                throw new RuntimeException(
                                    "Test for Handshake requires only HS type");
                            } else {
                                // Go into the first handshake message in the
                                // record and grab the handshake message header.
                                // All we need to do is parse out the leading
                                // byte.
                                int msgHdr = srcRecord.getInt();
                                int msgType = (msgHdr >> 24) & 0x000000FF;
                                if (msgType == recParams[0]) {
                                foundMsg = true;
                            }
                        }
                        break;
                    }
                }
            }

            srcRecord.reset();
        }

        return foundMsg;
    }

    private static String tlsRecType(int type) {
        switch (type) {
            case 20:
                return "Change Cipher Spec";
            case 21:
                return "Alert";
            case 22:
                return "Handshake";
            case 23:
                return "Application Data";
            default:
                return ("Unknown (" + type + ")");
        }
    }

    /*
     * Logging code
     */
    private static boolean resultOnce = true;

    private static void log(String str, SSLEngineResult result) {
        if (!logging) {
            return;
        }
        if (resultOnce) {
            resultOnce = false;
            System.out.println("The format of the SSLEngineResult is: \n" +
                "\t\"getStatus() / getHandshakeStatus()\" +\n" +
                "\t\"bytesConsumed() / bytesProduced()\"\n");
        }
        HandshakeStatus hsStatus = result.getHandshakeStatus();
        log(str +
            result.getStatus() + "/" + hsStatus + ", " +
            result.bytesConsumed() + "/" + result.bytesProduced() +
            " bytes");
        if (hsStatus == HandshakeStatus.FINISHED) {
            log("\t...ready for application data");
        }
    }

    private static void log(String str) {
        if (logging) {
            System.out.println(str);
        }
    }

    /**
     * Hex-dumps a ByteBuffer to stdout.
     */
    private static void dumpByteBuffer(String header, ByteBuffer bBuf) {
        if (!dumpBufs) {
            return;
        }

        int bufLen = bBuf.remaining();
        if (bufLen > 0) {
            bBuf.mark();

            // We expect the position of the buffer to be at the
            // beginning of a TLS record.  Get the type, version and length.
            int type = Byte.toUnsignedInt(bBuf.get());
            int ver_major = Byte.toUnsignedInt(bBuf.get());
            int ver_minor = Byte.toUnsignedInt(bBuf.get());

            log("===== " + header + " (" + tlsRecType(type) + " / " +
                ver_major + "." + ver_minor + " / " +
                bufLen + " bytes) =====");
            bBuf.reset();
            for (int i = 0; i < bufLen; i++) {
                if (i != 0 && i % 16 == 0) {
                    System.out.print("\n");
                }
                System.out.format("%02X ", bBuf.get(i));
            }
            log("\n===============================================");
            bBuf.reset();
        }
    }
}
