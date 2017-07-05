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

import java.io.IOException;
import java.util.*;
import java.nio.ByteBuffer;

/*
 * Checks that the hash value for a certificate's issuer name is generated
 * correctly. Requires any certificate that is not self-signed.
 *
 * NOTE: this test uses Sun private classes which are subject to change.
 */
public class CertStatusReqExtensionTests {

    private static final boolean debug = false;

    // Default status_request extension (type = ocsp, OCSPStatusRequest
    // with no responder IDs or extensions
    private static final byte[] CSRE_DEF_OSR = {1, 0, 0, 0, 0};

    // A status_request extension using a user-defined type (0xFF) and
    // an underlying no-Responder ID/no-extension OCSPStatusRequest
    private static final byte[] CSRE_TYPE_FF = {-1, 0, 0, 0, 0};

    // A CertStatusReqExtension with 5 ResponderIds and 1 Extension
    private static final byte[] CSRE_REQ_RID_EXTS = {
           1,    0,  -13,    0,   59,  -95,   57,   48,
          55,   49,   16,   48,   14,    6,    3,   85,
           4,   10,   19,    7,   83,  111,  109,  101,
          73,  110,   99,   49,   16,   48,   14,    6,
           3,   85,    4,   11,   19,    7,   83,  111,
         109,  101,   80,   75,   73,   49,   17,   48,
          15,    6,    3,   85,    4,    3,   19,    8,
          83,  111,  109,  101,   79,   67,   83,   80,
           0,   68,  -95,   66,   48,   64,   49,   13,
          48,   11,    6,    3,   85,    4,   10,   19,
           4,   79,  104,   77,  121,   49,   14,   48,
          12,    6,    3,   85,    4,   11,   19,    5,
          66,  101,   97,  114,  115,   49,   15,   48,
          13,    6,    3,   85,    4,   11,   19,    6,
          84,  105,  103,  101,  114,  115,   49,   14,
          48,   12,    6,    3,   85,    4,    3,   19,
           5,   76,  105,  111,  110,  115,    0,   58,
         -95,   56,   48,   54,   49,   16,   48,   14,
           6,    3,   85,    4,   10,   19,    7,   67,
         111,  109,  112,   97,  110,  121,   49,   13,
          48,   11,    6,    3,   85,    4,   11,   19,
           4,   87,  101,  115,  116,   49,   19,   48,
          17,    6,    3,   85,    4,    3,   19,   10,
          82,  101,  115,  112,  111,  110,  100,  101,
         114,   49,    0,   24,  -94,   22,    4,   20,
         -67,  -36,  114,  121,   92,  -79,  116,   -1,
         102, -107,    7,  -21,   18, -113,   64,   76,
          96,   -7,  -66,  -63,    0,   24,  -94,   22,
           4,   20,  -51,  -69,  107,  -82,  -39,  -87,
          45,   25,   41,   28,  -76,  -68,  -11, -110,
         -94,  -97,   62,   47,   58, -125,    0,   51,
          48,   49,   48,   47,    6,    9,   43,    6,
           1,    5,    5,    7,   48,    1,    2,    4,
          34,    4,   32,  -26,  -81, -120,  -61, -127,
         -79,    0,  -39,  -54,   49,    3,  -51,  -57,
         -85,   19, -126,   94,   -2,   21,   26,   98,
           6,  105,  -35,  -37,  -29,  -73,  101,   53,
          44,   15,  -19
    };

    public static void main(String[] args) throws Exception {
        Map<String, TestCase> testList =
                new LinkedHashMap<String, TestCase>() {{
            put("CTOR (default)", testCtorDefault);
            put("CTOR (int, StatusRequest)", testCtorStatReqs);
            put("CTOR (HandshakeInStream, length, getReqType, getRequest)",
                    testCtorInStream);
        }};

        TestUtils.runTests(testList);
    }

    public static final TestCase testCtorDefault = new TestCase() {
        @Override
        public Map.Entry<Boolean, String> runTest() {
            Boolean pass = Boolean.FALSE;
            String message = null;
            try {
                CertStatusReqExtension csreDef = new CertStatusReqExtension();
                HandshakeOutStream hsout =
                        new HandshakeOutStream(null);
                csreDef.send(hsout);
                TestUtils.valueCheck(wrapExtData(null), hsout.toByteArray());

                // The length should be 4 (2 bytes for the type, 2 for the
                // encoding of zero-length
                if (csreDef.length() != 4) {
                    throw new RuntimeException("Incorrect length from " +
                            "default object.  Expected 4, got " +
                            csreDef.length());
                }

                // Since there's no data, there are no status_type or request
                // data fields defined.  Both should return null in this case
                if (csreDef.getType() != null) {
                    throw new RuntimeException("Default CSRE returned " +
                            "non-null status_type");
                } else if (csreDef.getRequest() != null) {
                    throw new RuntimeException("Default CSRE returned " +
                            "non-null request object");
                }

                pass = Boolean.TRUE;
            } catch (Exception e) {
                e.printStackTrace(System.out);
                message = e.getClass().getName();
            }

            return new AbstractMap.SimpleEntry<>(pass, message);
        }
    };

    public static final TestCase testCtorStatReqs = new TestCase() {
        @Override
        public Map.Entry<Boolean, String> runTest() {
            Boolean pass = Boolean.FALSE;
            String message = null;
            try {
                HandshakeOutStream hsout =
                        new HandshakeOutStream(null);
                StatusRequest basicStatReq = new OCSPStatusRequest();

                // Create an extension using a default-style OCSPStatusRequest
                // (no responder IDs, no extensions).
                CertStatusReqExtension csre1 = new CertStatusReqExtension(
                        StatusRequestType.OCSP, basicStatReq);
                csre1.send(hsout);
                TestUtils.valueCheck(wrapExtData(CSRE_DEF_OSR),
                        hsout.toByteArray());
                hsout.reset();

                // Create the extension using a StatusRequestType not already
                // instantiated as a static StatusRequestType
                // (e.g. OCSP/OCSP_MULTI)
                CertStatusReqExtension csre2 =
                        new CertStatusReqExtension(StatusRequestType.get(-1),
                                basicStatReq);
                csre2.send(hsout);
                TestUtils.valueCheck(wrapExtData(CSRE_TYPE_FF),
                        hsout.toByteArray());

                // Create the extension using a StatusRequest that
                // does not match the status_type field
                // This should throw an IllegalArgumentException
                try {
                    CertStatusReqExtension csreBadRequest =
                            new CertStatusReqExtension(StatusRequestType.OCSP,
                                    new BogusStatusRequest());
                    throw new RuntimeException("Constructor accepted a " +
                            "StatusRequest that is inconsistent with " +
                            "the status_type");
                } catch (IllegalArgumentException iae) { }

                // We don't allow a null value for the StatusRequestType
                // parameter in this constructor.
                try {
                    CertStatusReqExtension csreBadRequest =
                            new CertStatusReqExtension(null, basicStatReq);
                    throw new RuntimeException("Constructor accepted a " +
                            "null StatusRequestType");
                } catch (NullPointerException npe) { }

                // We also don't allow a null value for the StatusRequest
                // parameter in this constructor.
                try {
                    CertStatusReqExtension csreBadRequest =
                            new CertStatusReqExtension(StatusRequestType.OCSP,
                                    null);
                    throw new RuntimeException("Constructor accepted a " +
                            "null StatusRequest");
                } catch (NullPointerException npe) { }

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
    // This also tests the length, getReqType and getRequest methods
    public static final TestCase testCtorInStream = new TestCase() {
        @Override
        public Map.Entry<Boolean, String> runTest() {
            Boolean pass = Boolean.FALSE;
            String message = null;
            OCSPStatusRequest osr;

            try {
                // To simulate the extension coming in a ServerHello, the
                // type and length would already be read by HelloExtensions
                // and there is no extension data
                HandshakeInStream hsis = new HandshakeInStream();
                hsis.incomingRecord(ByteBuffer.wrap(new byte[0]));
                CertStatusReqExtension csre =
                        new CertStatusReqExtension(hsis, hsis.available());
                // Verify length/type/request
                if (csre.length() != 4) {
                     throw new RuntimeException("Invalid length: received " +
                            csre.length() + ", expected 4");
                } else if (csre.getType() != null) {
                    throw new RuntimeException("Non-null type from default " +
                            "extension");
                } else if (csre.getRequest() != null) {
                    throw new RuntimeException("Non-null request from default " +
                            "extension");
                }

                // Try the an extension with a default OCSPStatusRequest
                hsis = new HandshakeInStream();
                hsis.incomingRecord(ByteBuffer.wrap(CSRE_DEF_OSR));
                csre = new CertStatusReqExtension(hsis, hsis.available());
                if (csre.length() != (CSRE_DEF_OSR.length + 4)) {
                    throw new RuntimeException("Invalid length: received " +
                            csre.length() + ", expected " +
                            CSRE_DEF_OSR.length + 4);
                } else if (!csre.getType().equals(StatusRequestType.OCSP)) {
                    throw new RuntimeException("Unknown status_type: " +
                            String.format("0x%02X", csre.getType().id));
                } else {
                    osr = (OCSPStatusRequest)csre.getRequest();
                    if (!osr.getResponderIds().isEmpty() ||
                            !osr.getExtensions().isEmpty()) {
                        throw new RuntimeException("Non-default " +
                                "OCSPStatusRequest found in extension");
                    }
                }

                // Try with a non-default extension
                hsis = new HandshakeInStream();
                hsis.incomingRecord(ByteBuffer.wrap(CSRE_REQ_RID_EXTS));
                csre = new CertStatusReqExtension(hsis, hsis.available());
                if (csre.length() != (CSRE_REQ_RID_EXTS.length + 4)) {
                    throw new RuntimeException("Invalid length: received " +
                            csre.length() + ", expected " +
                            CSRE_REQ_RID_EXTS.length + 4);
                } else if (!(csre.getType().equals(StatusRequestType.OCSP))) {
                    throw new RuntimeException("Unknown status_type: " +
                            String.format("0x%02X", csre.getType().id));
                } else {
                    osr = (OCSPStatusRequest)csre.getRequest();
                    if (osr.getResponderIds().size() != 5 ||
                            osr.getExtensions().size() != 1) {
                        throw new RuntimeException("Incorrect number of " +
                                "ResponderIds or Extensions found in " +
                                "OCSPStatusRequest");
                    }
                }

                // Create a CSRE that asserts status_request and has the
                // proper length, but really is a bunch of random junk inside
                // In this case, it will create an UnknownStatusRequest to
                // handle the unparseable data.
                byte[] junkData = new byte[48];
                Random r = new Random(System.currentTimeMillis());
                r.nextBytes(junkData);
                junkData[0] = 7;        // Ensure it isn't a valid status_type
                hsis = new HandshakeInStream();
                hsis.incomingRecord(ByteBuffer.wrap(junkData));
                csre = new CertStatusReqExtension(hsis, hsis.available());
                StatusRequest sr = csre.getRequest();
                if (!(sr instanceof UnknownStatusRequest)) {
                    throw new RuntimeException("Expected returned status " +
                            "request to be of type UnknownStatusRequest but " +
                            "received " + sr.getClass().getName());
                } else if (csre.length() != (junkData.length + 4)) {
                    throw new RuntimeException("Invalid length: received " +
                            csre.length() + ", expected " +
                            junkData.length + 4);
                }

                // Set the leading byte to 1 (OCSP type) and run again
                // It should pass the argument check and fail trying to parse
                // the underlying StatusRequest.
                junkData[0] = (byte)StatusRequestType.OCSP.id;
                hsis = new HandshakeInStream();
                hsis.incomingRecord(ByteBuffer.wrap(junkData));
                try {
                    csre = new CertStatusReqExtension(hsis, hsis.available());
                    throw new RuntimeException("Expected CTOR exception did " +
                            "not occur");
                } catch (IOException ioe) { }

                pass = Boolean.TRUE;
            } catch (Exception e) {
                e.printStackTrace(System.out);
                message = e.getClass().getName();
            }

            return new AbstractMap.SimpleEntry<>(pass, message);
        }
    };

    // Take CSRE extension data and add extension type and length decorations
    private static byte[] wrapExtData(byte[] extData) {
        int bufferLen = (extData != null ? extData.length : 0) + 4;
        ByteBuffer bb = ByteBuffer.allocate(bufferLen);
        bb.putShort((short)ExtensionType.EXT_STATUS_REQUEST.id);
        bb.putShort((short)(extData != null ? extData.length: 0));
        if (extData != null) {
            bb.put(extData);
        }
        return bb.array();
    }
}
