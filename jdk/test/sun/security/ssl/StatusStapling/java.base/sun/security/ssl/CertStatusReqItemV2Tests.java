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
import javax.net.ssl.SSLException;
import javax.security.auth.x500.X500Principal;
import sun.security.provider.certpath.ResponderId;
import sun.security.provider.certpath.OCSPNonceExtension;

/*
 * Checks that the hash value for a certificate's issuer name is generated
 * correctly. Requires any certificate that is not self-signed.
 *
 * NOTE: this test uses Sun private classes which are subject to change.
 */
public class CertStatusReqItemV2Tests {

    private static final boolean debug = false;

    private static final byte[] DEF_CSRIV2_OCSP_MULTI_BYTES = {
           2,    0,    4,    0,    0,    0,    0
    };

    private static final byte[] DEF_CSRIV2_OCSP_BYTES = {
           1,    0,    4,    0,    0,    0,    0
    };

    // This is a CSRIV2 (ocsp_multi) that has a single
    // responder ID and no extensions.
    private static final byte[] CSRIV2_1RID = {
            2,    0,   32,     0,   28,    0,   26,  -95,
           24,   48,   22,    49,   20,   48,   18,    6,
            3,   85,    4,     3,   19,   11,   79,   67,
           83,   80,   32,    83,  105,  103,  110,  101,
          114,    0 ,   0
    };

    // This is a CSRIV2 (ocsp_multi) that has a single
    // responder ID and no extensions.  The request_length
    // field is too short in this case.
    private static final byte[] CSRIV2_LENGTH_TOO_SHORT = {
            2,    0,   27,     0,   28,    0,   26,  -95,
           24,   48,   22,    49,   20,   48,   18,    6,
            3,   85,    4,     3,   19,   11,   79,   67,
           83,   80,   32,    83,  105,  103,  110,  101,
          114,    0 ,   0
    };

    // This is a CSRIV2 (ocsp_multi) that has a single
    // responder ID and no extensions.  The request_length
    // field is too long in this case.
    private static final byte[] CSRIV2_LENGTH_TOO_LONG = {
            2,    0,   54,     0,   28,    0,   26,  -95,
           24,   48,   22,    49,   20,   48,   18,    6,
            3,   85,    4,     3,   19,   11,   79,   67,
           83,   80,   32,    83,  105,  103,  110,  101,
          114,    0 ,   0
    };

    // A CSRIV2 (ocsp) with one Responder ID (byName: CN=OCSP Signer)
    // and a nonce extension (32 bytes).
    private static final byte[] CSRIV2_OCSP_1RID_1EXT = {
            1,    0,   83,    0,   28,    0,   26,  -95,
           24,   48,   22,   49,   20,   48,   18,    6,
            3,   85,    4,    3,   19,   11,   79,   67,
           83,   80,   32,   83,  105,  103,  110,  101,
          114,    0,   51,   48,   49,   48,   47,    6,
            9,   43,    6,    1,    5,    5,    7,   48,
            1,    2,    4,   34,    4,   32,  -34,  -83,
          -66,  -17,  -34,  -83,  -66,  -17,  -34,  -83,
          -66,  -17,  -34,  -83,  -66,  -17,  -34,  -83,
          -66,  -17,  -34,  -83,  -66,  -17,  -34,  -83,
          -66,  -17,  -34,  -83,  -66,  -17
    };

    public static void main(String[] args) throws Exception {
        Map<String, TestCase> testList =
                new LinkedHashMap<String, TestCase>() {{
            put("CTOR (Default)", testCtorTypeStatReq);
            put("CTOR (Byte array)", testCtorByteArray);
            put("CTOR (invalid lengths)", testCtorInvalidLengths);
        }};

        TestUtils.runTests(testList);
    }

    public static final TestCase testCtorTypeStatReq = new TestCase() {
        @Override
        public Map.Entry<Boolean, String> runTest() {
            Boolean pass = Boolean.FALSE;
            String message = null;
            try {
                // Attempt to create CSRIv2 objects using null pointers
                // for either parameter.  In either case NPE should be thrown
                CertStatusReqItemV2 csriNull;
                try {
                    csriNull = new CertStatusReqItemV2(null,
                            new OCSPStatusRequest());
                    throw new RuntimeException("Did not catch expected NPE " +
                            "for null status_type parameter");
                } catch (NullPointerException npe) { }

                try {
                    csriNull = new CertStatusReqItemV2(StatusRequestType.OCSP,
                            null);
                    throw new RuntimeException("Did not catch expected NPE " +
                            "for null StatusRequest parameter");
                } catch (NullPointerException npe) { }

                // Create an "ocsp_multi" type request using a default
                // (no Responder IDs, no Extensions) OCSPStatusRequest
                CertStatusReqItemV2 csriMulti =
                        new CertStatusReqItemV2(StatusRequestType.OCSP_MULTI,
                                new OCSPStatusRequest());
                HandshakeOutStream hsout = new HandshakeOutStream(null);
                csriMulti.send(hsout);
                TestUtils.valueCheck(DEF_CSRIV2_OCSP_MULTI_BYTES,
                        hsout.toByteArray());
                hsout.reset();

                // Create an "ocsp" type request using a default
                // (no Responder IDs, no Extensions) OCSPStatusRequest
                CertStatusReqItemV2 csriSingle =
                        new CertStatusReqItemV2(StatusRequestType.OCSP,
                                new OCSPStatusRequest(new LinkedList<>(),
                                        new LinkedList<>()));
                csriSingle.send(hsout);
                TestUtils.valueCheck(DEF_CSRIV2_OCSP_BYTES,
                        hsout.toByteArray());

                // Create the CertStatusRequestItemV2 with a user-defined
                // StatusRequestType value
                CertStatusReqItemV2 csriNine =
                        new CertStatusReqItemV2(StatusRequestType.get(9),
                                new OCSPStatusRequest(null, null));
                if (csriNine.getType().id != 9) {
                    throw new RuntimeException("Expected status_type = 9, " +
                            "got " + csriNine.getType().id);
                } else {
                    StatusRequest sr = csriNine.getRequest();
                    if (!(sr instanceof OCSPStatusRequest)) {
                        throw new RuntimeException("Expected " +
                                "OCSPStatusRequest, got " +
                                sr.getClass().getName());
                    }
                }

                // Create the CertStatusRequestItemV2 with a StatusRequest
                // that does not match the status_type argument.
                // We expect IllegalArgumentException in this case.
                try {
                    CertStatusReqItemV2 csriBadSR = new CertStatusReqItemV2(
                            StatusRequestType.OCSP_MULTI,
                            new BogusStatusRequest());
                    throw new RuntimeException("Constructor accepted a " +
                            "StatusRequest that is inconsistent with " +
                            "the status_type");
                } catch (IllegalArgumentException iae) {
                    // The expected result...nothing to do here
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
                StatusRequestType sType;
                StatusRequest sReq;
                ResponderId checkRid =
                        new ResponderId(new X500Principal("CN=OCSP Signer"));
                Extension checkExt = new OCSPNonceExtension(32);

                CertStatusReqItemV2 csriv =
                        new CertStatusReqItemV2(CSRIV2_OCSP_1RID_1EXT);
                sType = csriv.getType();
                if (sType != StatusRequestType.OCSP) {
                    throw new RuntimeException("Unexpected StatusRequestType " +
                            sType.getClass().getName());
                }

                sReq = csriv.getRequest();
                if (sReq instanceof OCSPStatusRequest) {
                    OCSPStatusRequest osr = (OCSPStatusRequest)sReq;
                    List<ResponderId> ridList = osr.getResponderIds();
                    List<Extension> extList = osr.getExtensions();

                    if (ridList.size() != 1 || !ridList.contains(checkRid)) {
                        throw new RuntimeException("Responder list mismatch");
                    } else if (extList.size() !=  1 ||
                            !extList.get(0).getId().equals(checkExt.getId())) {
                        throw new RuntimeException("Extension list mismatch");
                    }
                } else {
                    throw new RuntimeException("Expected OCSPStatusRequest " +
                            "from decoded bytes, got " +
                            sReq.getClass().getName());
                }

                // Create a CSRIV2 out of random data.  A non-OCSP/OCSP_MULTI
                // type will be forcibly set and the outer length field will
                // be correct.
                // The constructor should create a StatusRequestType object
                // and an UnknownStatusRequest object consisting of the
                // data segment.
                byte[] junkData = new byte[48];
                Random r = new Random(System.currentTimeMillis());
                r.nextBytes(junkData);
                junkData[0] = 7;        // status_type = 7
                junkData[1] = 0;
                junkData[2] = 45;       // request_length = 45
                csriv = new CertStatusReqItemV2(junkData);

                sType = csriv.getType();
                sReq = csriv.getRequest();
                if (sType.id != junkData[0]) {
                    throw new RuntimeException("StatusRequestType mismatch: " +
                            "expected 7, got " + sType.id);
                }
                if (sReq instanceof UnknownStatusRequest) {
                    // Verify the underlying StatusRequest bytes have been
                    // preserved correctly.
                    HandshakeOutStream hsout = new HandshakeOutStream(null);
                    sReq.send(hsout);
                    byte[] srDataOut = hsout.toByteArray();
                    TestUtils.valueCheck(srDataOut, junkData, 0, 3,
                            srDataOut.length);
                } else {
                    throw new RuntimeException("StatusRequest mismatch: " +
                            "expected UnknownStatusRequest, got " +
                            sReq.getClass().getName());
                }

                // Test the parsing of the default OCSP/OCSP_MULTI extensions
                // and make sure the underlying StatusRequestType and
                // StatusRequest objects are correct.
                csriv = new CertStatusReqItemV2(DEF_CSRIV2_OCSP_MULTI_BYTES);
                sType = csriv.getType();
                sReq = csriv.getRequest();
                if (sType != StatusRequestType.OCSP_MULTI) {
                    throw new RuntimeException("StatusRequestType mismatch: " +
                            "expected OCSP_MULTI (2), got " + sType.id);
                }
                if (!(sReq instanceof OCSPStatusRequest)) {
                    throw new RuntimeException("StatusRequest mismatch: " +
                            "expected OCSPStatusRequest, got " +
                            sReq.getClass().getName());
                }

                csriv = new CertStatusReqItemV2(DEF_CSRIV2_OCSP_BYTES);
                sType = csriv.getType();
                sReq = csriv.getRequest();
                if (sType != StatusRequestType.OCSP) {
                    throw new RuntimeException("StatusRequestType mismatch: " +
                            "expected OCSP (1), got " + sType.id);
                }
                if (!(sReq instanceof OCSPStatusRequest)) {
                    throw new RuntimeException("StatusRequest mismatch: " +
                            "expected OCSPStatusRequest, got " +
                            sReq.getClass().getName());
                }

                pass = Boolean.TRUE;
            } catch (Exception e) {
                e.printStackTrace(System.out);
                message = e.getClass().getName();
            }

            return new AbstractMap.SimpleEntry<>(pass, message);
        }
    };

    public static final TestCase testCtorInvalidLengths = new TestCase() {
        @Override
        public Map.Entry<Boolean, String> runTest() {
            Boolean pass = Boolean.FALSE;
            String message = null;
            try {
                try {
                    CertStatusReqItemV2 csriTooShort =
                            new CertStatusReqItemV2(CSRIV2_LENGTH_TOO_SHORT);
                    throw new RuntimeException("Expected exception not thrown");
                } catch (SSLException ssle) { }

                try {
                    CertStatusReqItemV2 csriTooLong =
                            new CertStatusReqItemV2(CSRIV2_LENGTH_TOO_LONG);
                    throw new RuntimeException("Expected exception not thrown");
                } catch (SSLException ssle) { }

                pass = Boolean.TRUE;
            } catch (Exception e) {
                e.printStackTrace(System.out);
                message = e.getClass().getName();
            }

            return new AbstractMap.SimpleEntry<>(pass, message);
        }
    };

    // Test the constructor form that takes the data from HandshakeInputStream
    public static final TestCase testCtorInputStream = new TestCase() {
        @Override
        public Map.Entry<Boolean, String> runTest() {
            Boolean pass = Boolean.FALSE;
            String message = null;
            try {
                StatusRequestType sType;
                StatusRequest sReq;
                ResponderId checkRid =
                        new ResponderId(new X500Principal("CN=OCSP Signer"));
                Extension checkExt = new OCSPNonceExtension(32);

                HandshakeInStream hsis = new HandshakeInStream();
                hsis.incomingRecord(ByteBuffer.wrap(CSRIV2_OCSP_1RID_1EXT));
                CertStatusReqItemV2 csriv = new CertStatusReqItemV2(hsis);
                sType = csriv.getType();
                if (sType != StatusRequestType.OCSP) {
                    throw new RuntimeException("Unexpected StatusRequestType " +
                            sType.getClass().getName());
                }

                sReq = csriv.getRequest();
                if (sReq instanceof OCSPStatusRequest) {
                    OCSPStatusRequest osr = (OCSPStatusRequest)sReq;
                    List<ResponderId> ridList = osr.getResponderIds();
                    List<Extension> extList = osr.getExtensions();

                    if (ridList.size() != 1 || !ridList.contains(checkRid)) {
                        throw new RuntimeException("Responder list mismatch");
                    } else if (extList.size() !=  1 ||
                            !extList.get(0).getId().equals(checkExt.getId())) {
                        throw new RuntimeException("Extension list mismatch");
                    }
                } else {
                    throw new RuntimeException("Expected OCSPStatusRequest " +
                            "from decoded bytes, got " +
                            sReq.getClass().getName());
                }

                // Create a CSRIV2 out of random data.  A non-OCSP/OCSP_MULTI
                // type will be forcibly set and the outer length field will
                // be correct.
                // The constructor should create a StatusRequestType object
                // and an UnknownStatusRequest object consisting of the
                // data segment.
                byte[] junkData = new byte[48];
                Random r = new Random(System.currentTimeMillis());
                r.nextBytes(junkData);
                junkData[0] = 7;        // status_type = 7
                junkData[1] = 0;
                junkData[2] = 45;       // request_length = 45
                hsis = new HandshakeInStream();
                hsis.incomingRecord(ByteBuffer.wrap(junkData));
                csriv = new CertStatusReqItemV2(hsis);

                sType = csriv.getType();
                sReq = csriv.getRequest();
                if (sType.id != junkData[0]) {
                    throw new RuntimeException("StatusRequestType mismatch: " +
                            "expected 7, got " + sType.id);
                }
                if (sReq instanceof UnknownStatusRequest) {
                    // Verify the underlying StatusRequest bytes have been
                    // preserved correctly.
                    HandshakeOutStream hsout = new HandshakeOutStream(null);
                    sReq.send(hsout);
                    byte[] srDataOut = hsout.toByteArray();
                    TestUtils.valueCheck(srDataOut, junkData, 0, 3,
                            srDataOut.length);
                } else {
                    throw new RuntimeException("StatusRequest mismatch: " +
                            "expected UnknownStatusRequest, got " +
                            sReq.getClass().getName());
                }

                // Test the parsing of the default OCSP/OCSP_MULTI extensions
                // and make sure the underlying StatusRequestType and
                // StatusRequest objects are correct.
                hsis = new HandshakeInStream();
                hsis.incomingRecord(
                        ByteBuffer.wrap(DEF_CSRIV2_OCSP_MULTI_BYTES));
                csriv = new CertStatusReqItemV2(hsis);
                sType = csriv.getType();
                sReq = csriv.getRequest();
                if (sType != StatusRequestType.OCSP_MULTI) {
                    throw new RuntimeException("StatusRequestType mismatch: " +
                            "expected OCSP_MULTI (2), got " + sType.id);
                }
                if (!(sReq instanceof OCSPStatusRequest)) {
                    throw new RuntimeException("StatusRequest mismatch: " +
                            "expected OCSPStatusRequest, got " +
                            sReq.getClass().getName());
                }

                hsis = new HandshakeInStream();
                hsis.incomingRecord(ByteBuffer.wrap(DEF_CSRIV2_OCSP_BYTES));
                csriv = new CertStatusReqItemV2(hsis);
                sType = csriv.getType();
                sReq = csriv.getRequest();
                if (sType != StatusRequestType.OCSP) {
                    throw new RuntimeException("StatusRequestType mismatch: " +
                            "expected OCSP (1), got " + sType.id);
                }
                if (!(sReq instanceof OCSPStatusRequest)) {
                    throw new RuntimeException("StatusRequest mismatch: " +
                            "expected OCSPStatusRequest, got " +
                            sReq.getClass().getName());
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
