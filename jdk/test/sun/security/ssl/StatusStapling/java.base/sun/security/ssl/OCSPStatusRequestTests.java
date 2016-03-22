/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.ssl;

import java.security.cert.*;
import java.util.*;
import java.nio.ByteBuffer;
import javax.security.auth.x500.X500Principal;
import sun.security.provider.certpath.ResponderId;
import sun.security.provider.certpath.OCSPNonceExtension;

/*
 * Checks that the hash value for a certificate's issuer name is generated
 * correctly. Requires any certificate that is not self-signed.
 *
 * NOTE: this test uses Sun private classes which are subject to change.
 */
public class OCSPStatusRequestTests {

    private static final boolean debug = false;

    // The default (no Responder IDs or Extensions)
    private static final byte[] DEF_OCSPREQ_BYTES = { 0, 0, 0, 0 };

    // OCSP Extension with one Responder ID (byName: CN=OCSP Signer) and
    // a nonce extension (32 bytes).
    private static final byte[] OCSPREQ_1RID_1EXT = {
            0,   28,    0,   26,  -95,   24,   48,   22,
           49,   20,   48,   18,    6,    3,   85,    4,
            3,   19,   11,   79,   67,   83,   80,   32,
           83,  105,  103,  110,  101,  114,    0,   51,
           48,   49,   48,   47,    6,    9,   43,    6,
            1,    5,    5,    7,   48,    1,    2,    4,
           34,    4,   32,  -34,  -83,  -66,  -17,  -34,
          -83,  -66,  -17,  -34,  -83,  -66,  -17,  -34,
          -83,  -66,  -17,  -34,  -83,  -66,  -17,  -34,
          -83,  -66,  -17,  -34,  -83,  -66,  -17,  -34,
          -83,  -66,  -17
    };

    public static void main(String[] args) throws Exception {
        Map<String, TestCase> testList =
                new LinkedHashMap<String, TestCase>() {{
            put("CTOR (default)", testCtorDefault);
            put("CTOR (Responder Id and Extension)", testCtorRidsExts);
            put("CTOR (HandshakeInStream)", testCtorInStream);
            put("CTOR (byte array)", testCtorByteArray);
            put("Length tests", testLength);
            put("Equals tests", testEquals);
        }};

        TestUtils.runTests(testList);
    }

    // Test the default constructor and its encoding
    public static final TestCase testCtorDefault = new TestCase() {
        @Override
        public Map.Entry<Boolean, String> runTest() {
            Boolean pass = Boolean.FALSE;
            String message = null;
            try {
                // Create a OCSPStatusRequest with a single ResponderId
                // and Extension
                OCSPStatusRequest osrDefault = new OCSPStatusRequest();
                HandshakeOutStream hsout = new HandshakeOutStream(null);
                osrDefault.send(hsout);
                System.out.println("Encoded Result:");
                TestUtils.dumpBytes(hsout.toByteArray());

                TestUtils.valueCheck(DEF_OCSPREQ_BYTES, hsout.toByteArray());
                pass = Boolean.TRUE;
            } catch (Exception e) {
                e.printStackTrace(System.out);
                message = e.getClass().getName();
            }

            return new AbstractMap.SimpleEntry<>(pass, message);
        }
    };

    // Test the constructor form that allows the user to specify zero
    // or more ResponderId objects and/or Extensions
    public static final TestCase testCtorRidsExts = new TestCase() {
        @Override
        public Map.Entry<Boolean, String> runTest() {
            Boolean pass = Boolean.FALSE;
            String message = null;
            try {
                List<ResponderId> ridList = new LinkedList<ResponderId>() {{
                    add(new ResponderId(new X500Principal("CN=OCSP Signer")));
                }};
                List<Extension> extList = new LinkedList<Extension>() {{
                    add(new OCSPNonceExtension(32));
                }};

                // Default-style OCSPStatusRequest using both empty Lists and
                // null inputs
                OCSPStatusRequest osrDef1 =
                        new OCSPStatusRequest(new LinkedList<ResponderId>(),
                                null);
                OCSPStatusRequest osrDef2 =
                        new OCSPStatusRequest(null,
                                new LinkedList<Extension>());
                HandshakeOutStream hsout = new HandshakeOutStream(null);
                osrDef1.send(hsout);
                System.out.println("Encoded Result:");
                TestUtils.dumpBytes(hsout.toByteArray());
                TestUtils.valueCheck(DEF_OCSPREQ_BYTES, hsout.toByteArray());

                hsout.reset();
                osrDef2.send(hsout);
                System.out.println("Encoded Result:");
                TestUtils.dumpBytes(hsout.toByteArray());
                TestUtils.valueCheck(DEF_OCSPREQ_BYTES, hsout.toByteArray());

                hsout.reset();
                OCSPStatusRequest osrWithItems =
                        new OCSPStatusRequest(ridList, extList);
                osrWithItems.send(hsout);
                System.out.println("Encoded Result:");
                byte[] encodedData = hsout.toByteArray();
                TestUtils.dumpBytes(encodedData);
                // Check everything except the last 32 bytes (nonce data)
                TestUtils.valueCheck(OCSPREQ_1RID_1EXT, encodedData, 0, 0,
                        encodedData.length - 32);

                pass = Boolean.TRUE;
            } catch (Exception e) {
                e.printStackTrace(System.out);
                message = e.getClass().getName();
            }

            return new AbstractMap.SimpleEntry<>(pass, message);
        }
    };

    // Test the constructor that builds the ob ject using data from
    // a HandshakeInStream
    public static final TestCase testCtorInStream = new TestCase() {
        @Override
        public Map.Entry<Boolean, String> runTest() {
            Boolean pass = Boolean.FALSE;
            String message = null;
            try {
                ResponderId checkRid =
                        new ResponderId(new X500Principal("CN=OCSP Signer"));
                Extension checkExt = new OCSPNonceExtension(32);

                HandshakeInStream hsis = new HandshakeInStream();
                hsis.incomingRecord(ByteBuffer.wrap(OCSPREQ_1RID_1EXT));
                OCSPStatusRequest osr = new OCSPStatusRequest(hsis);

                List<ResponderId> ridList = osr.getResponderIds();
                List<Extension> extList = osr.getExtensions();

                if (ridList.size() != 1 || !ridList.contains(checkRid)) {
                    throw new RuntimeException("Responder list mismatch");
                } else if (extList.size() !=  1 ||
                        !extList.get(0).getId().equals(checkExt.getId())) {
                    throw new RuntimeException("Extension list mismatch");
                }

                pass = Boolean.TRUE;
            } catch (Exception e) {
                e.printStackTrace(System.out);
                message = e.getClass().getName();
            }

            return new AbstractMap.SimpleEntry<>(pass, message);
        }
    };

    // Test the constructor form that takes the data from a byte array
    public static final TestCase testCtorByteArray = new TestCase() {
        @Override
        public Map.Entry<Boolean, String> runTest() {
            Boolean pass = Boolean.FALSE;
            String message = null;
            try {
                ResponderId checkRid =
                        new ResponderId(new X500Principal("CN=OCSP Signer"));
                Extension checkExt = new OCSPNonceExtension(32);

                OCSPStatusRequest osr =
                        new OCSPStatusRequest(OCSPREQ_1RID_1EXT);

                List<ResponderId> ridList = osr.getResponderIds();
                List<Extension> extList = osr.getExtensions();

                if (ridList.size() != 1 || !ridList.contains(checkRid)) {
                    throw new RuntimeException("Responder list mismatch");
                } else if (extList.size() !=  1 ||
                        !extList.get(0).getId().equals(checkExt.getId())) {
                    throw new RuntimeException("Extension list mismatch");
                }
                pass = Boolean.TRUE;
            } catch (Exception e) {
                e.printStackTrace(System.out);
                message = e.getClass().getName();
            }

            return new AbstractMap.SimpleEntry<>(pass, message);
        }
    };

    // Test the length functions for both default and non-default
    // OCSPStatusRequest objects
    public static final TestCase testLength = new TestCase() {
        @Override
        public Map.Entry<Boolean, String> runTest() {
            Boolean pass = Boolean.FALSE;
            String message = null;
            try {
                HandshakeInStream hsis = new HandshakeInStream();
                hsis.incomingRecord(ByteBuffer.wrap(OCSPREQ_1RID_1EXT));
                OCSPStatusRequest osr = new OCSPStatusRequest(hsis);
                OCSPStatusRequest osrDefault = new OCSPStatusRequest();

                if (osrDefault.length() != DEF_OCSPREQ_BYTES.length) {
                    throw new RuntimeException("Invalid length for default: " +
                            "Expected" + DEF_OCSPREQ_BYTES.length +
                            ", received " + osrDefault.length());
                } else if (osr.length() != OCSPREQ_1RID_1EXT.length) {
                    throw new RuntimeException("Invalid length for default: " +
                            "Expected" + OCSPREQ_1RID_1EXT.length +
                            ", received " + osr.length());
                }

                pass = Boolean.TRUE;
            } catch (Exception e) {
                e.printStackTrace(System.out);
                message = e.getClass().getName();
            }

            return new AbstractMap.SimpleEntry<>(pass, message);
        }
    };

    // Test the equals method with default and non-default objects
    public static final TestCase testEquals = new TestCase() {
        @Override
        public Map.Entry<Boolean, String> runTest() {
            Boolean pass = Boolean.FALSE;
            String message = null;
            try {
                // Make two different lists with the same ResponderId values
                // and also make a extension list
                List<ResponderId> ridList1 = new LinkedList<ResponderId>() {{
                    add(new ResponderId(new X500Principal("CN=OCSP Signer")));
                }};
                List<ResponderId> ridList2 = new LinkedList<ResponderId>() {{
                    add(new ResponderId(new X500Principal("CN=OCSP Signer")));
                }};
                List<Extension> extList = new LinkedList<Extension>() {{
                    add(new OCSPNonceExtension(32));
                }};

                // We expect two default OCSP objects to be equal
                OCSPStatusRequest osrDefault = new OCSPStatusRequest();
                if (!osrDefault.equals(new OCSPStatusRequest())) {
                    throw new RuntimeException("Default OCSPStatusRequest" +
                            " equality test failed");
                }

                // null test (expect false return)
                if (osrDefault.equals(null)) {
                    throw new RuntimeException("OCSPStatusRequest matched" +
                            " unexpectedly");
                }

                // Self-reference test
                OCSPStatusRequest osrSelfRef = osrDefault;
                if (!osrDefault.equals(osrSelfRef)) {
                    throw new RuntimeException("Default OCSPStatusRequest" +
                            " equality test failed");
                }

                // Two OCSPStatusRequests with matching ResponderIds should
                // be considered equal
                OCSPStatusRequest osrByList1 =
                        new OCSPStatusRequest(ridList1, null);
                OCSPStatusRequest osrByList2 = new OCSPStatusRequest(ridList2,
                        Collections.emptyList());
                if (!osrByList1.equals(osrByList2)) {
                    throw new RuntimeException("Single Responder ID " +
                            "OCSPStatusRequest equality test failed");
                }

                // We expect OCSPStatusRequests with different nonces to be
                // considered unequal.
                HandshakeInStream hsis = new HandshakeInStream();
                hsis.incomingRecord(ByteBuffer.wrap(OCSPREQ_1RID_1EXT));
                OCSPStatusRequest osrStream = new OCSPStatusRequest(hsis);
                OCSPStatusRequest osrRidExt = new OCSPStatusRequest(ridList1,
                        extList);
                if (osrStream.equals(osrRidExt)) {
                    throw new RuntimeException("OCSPStatusRequest matched" +
                            " unexpectedly");
                }

                pass = Boolean.TRUE;
            } catch (Exception e) {
                e.printStackTrace(System.out);
                message = e.getClass().getName();
            }

            return new AbstractMap.SimpleEntry<>(pass, message);
        }
    };

}
