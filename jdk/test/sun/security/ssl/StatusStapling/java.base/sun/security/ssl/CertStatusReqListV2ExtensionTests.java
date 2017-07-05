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
import javax.net.ssl.*;

/*
 * Checks that the hash value for a certificate's issuer name is generated
 * correctly. Requires any certificate that is not self-signed.
 *
 * NOTE: this test uses Sun private classes which are subject to change.
 */
public class CertStatusReqListV2ExtensionTests {

    private static final boolean debug = false;

    // Default status_request_v2 extension with two items
    // 1. Type = ocsp_multi, OCSPStatusRequest is default
    // 2. Type = ocsp, OCSPStatusRequest is default
    private static final byte[] CSRLV2_DEF = {
           0,   14,    2,    0,    4,    0,    0,    0,
           0,    1,    0,    4,    0,    0,    0,    0
    };

    // A status_request_v2 where the item list length is
    // longer than the provided data
    private static final byte[] CSRLV2_LEN_TOO_LONG = {
           0,   18,    2,    0,    4,    0,    0,    0,
           0,    1,    0,    4,    0,    0,    0,    0
    };

    // A status_request_v2 where the item list length is
    // shorter than the provided data
    private static final byte[] CSRLV2_LEN_TOO_SHORT = {
           0,   11,    2,    0,    4,    0,    0,    0,
           0,    1,    0,    4,    0,    0,    0,    0
    };

    // A status_request_v2 extension with a zero-length
    // certificate_status_req_list (not allowed by the spec)
    private static final byte[] CSRLV2_INVALID_ZEROLEN = {0, 0};

    // A status_request_v2 extension with two items (ocsp_multi and ocsp)
    // using OCSPStatusRequests with 5 ResponderIds and 1 Extension each.
    private static final byte[] CSRLV2_TWO_NON_DEF_ITEMS = {
            2,   90,    2,    1,   42,    0,  -13,    0,
           59,  -95,   57,   48,   55,   49,   16,   48,
           14,    6,    3,   85,    4,   10,   19,    7,
           83,  111,  109,  101,   73,  110,   99,   49,
           16,   48,   14,    6,    3,   85,    4,   11,
           19,    7,   83,  111,  109,  101,   80,   75,
           73,   49,   17,   48,   15,    6,    3,   85,
            4,    3,   19,    8,   83,  111,  109,  101,
           79,   67,   83,   80,    0,   68,  -95,   66,
           48,   64,   49,   13,   48,   11,    6,    3,
           85,    4,   10,   19,    4,   79,  104,   77,
          121,   49,   14,   48,   12,    6,    3,   85,
            4,   11,   19,    5,   66,  101,   97,  114,
          115,   49,   15,   48,   13,    6,    3,   85,
            4,   11,   19,    6,   84,  105,  103,  101,
          114,  115,   49,   14,   48,   12,    6,    3,
           85,    4,    3,   19,    5,   76,  105,  111,
          110,  115,    0,   58,  -95,   56,   48,   54,
           49,   16,   48,   14,    6,    3,   85,    4,
           10,   19,    7,   67,  111,  109,  112,   97,
          110,  121,   49,   13,   48,   11,    6,    3,
           85,    4,   11,   19,    4,   87,  101,  115,
          116,   49,   19,   48,   17,    6,    3,   85,
            4,    3,   19,   10,   82,  101,  115,  112,
          111,  110,  100,  101,  114,   49,    0,   24,
          -94,   22,    4,   20,  -67,  -36,  114,  121,
           92,  -79,  116,   -1,  102, -107,    7,  -21,
           18, -113,   64,   76,   96,   -7,  -66,  -63,
            0,   24,  -94,   22,    4,   20,  -51,  -69,
          107,  -82,  -39,  -87,   45,   25,   41,   28,
          -76,  -68,  -11, -110,  -94,  -97,   62,   47,
           58, -125,    0,   51,   48,   49,   48,   47,
            6,    9,   43,    6,    1,    5,    5,    7,
           48,    1,    2,    4,   34,    4,   32,  -26,
          -81, -120,  -61, -127,  -79,    0,  -39,  -54,
           49,    3,  -51,  -57,  -85,   19, -126,   94,
           -2,   21,   26,   98,    6,  105,  -35,  -37,
          -29,  -73,  101,   53,   44,   15,  -19,    1,
            1,   42,    0,  -13,    0,   59,  -95,   57,
           48,   55,   49,   16,   48,   14,    6,    3,
           85,    4,   10,   19,    7,   83,  111,  109,
          101,   73,  110,   99,   49,   16,   48,   14,
            6,    3,   85,    4,   11,   19,    7,   83,
          111,  109,  101,   80,   75,   73,   49,   17,
           48,   15,    6,    3,   85,    4,    3,   19,
            8,   83,  111,  109,  101,   79,   67,   83,
           80,    0,   68,  -95,   66,   48,   64,   49,
           13,   48,   11,    6,    3,   85,    4,   10,
           19,    4,   79,  104,   77,  121,   49,   14,
           48,   12,    6,    3,   85,    4,   11,   19,
            5,   66,  101,   97,  114,  115,   49,   15,
           48,   13,    6,    3,   85,    4,   11,   19,
            6,   84,  105,  103,  101,  114,  115,   49,
           14,   48,   12,    6,    3,   85,    4,    3,
           19,    5,   76,  105,  111,  110,  115,    0,
           58,  -95,   56,   48,   54,   49,   16,   48,
           14,    6,    3,   85,    4,   10,   19,    7,
           67,  111,  109,  112,   97,  110,  121,   49,
           13,   48,   11,    6,    3,   85,    4,   11,
           19,    4,   87,  101,  115,  116,   49,   19,
           48,   17,    6,    3,   85,    4,    3,   19,
           10,   82,  101,  115,  112,  111,  110,  100,
          101,  114,   49,    0,   24,  -94,   22,    4,
           20,  -67,  -36,  114,  121,   92,  -79,  116,
           -1,  102, -107,    7,  -21,   18, -113,   64,
           76,   96,   -7,  -66,  -63,    0,   24,  -94,
           22,    4,   20,  -51,  -69,  107,  -82,  -39,
          -87,   45,   25,   41,   28,  -76,  -68,  -11,
         -110,  -94,  -97,   62,   47,   58, -125,    0,
           51,   48,   49,   48,   47,    6,    9,   43,
            6,    1,    5,    5,    7,   48,    1,    2,
            4,   34,    4,   32,  -26,  -81, -120,  -61,
         -127,  -79,    0,  -39,  -54,   49,    3,  -51,
          -57,  -85,   19, -126,   94,   -2,   21,   26,
           98,    6,  105,  -35,  -37,  -29,  -73,  101,
           53,   44,   15,  -19
    };

    public static void main(String[] args) throws Exception {
        Map<String, TestCase> testList =
                new LinkedHashMap<String, TestCase>() {{
            put("CTOR (default)", testCtorDefault);
            put("CTOR (List<CertStatusReqItemV2)", testCtorItemList);
            put("CTOR (HandshakeInStream, getRequestList",
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
                CertStatusReqListV2Extension csrlV2 =
                        new CertStatusReqListV2Extension();
                HandshakeOutStream hsout = new HandshakeOutStream(null);
                csrlV2.send(hsout);
                TestUtils.valueCheck(wrapExtData(new byte[0]),
                        hsout.toByteArray());

                // The length should be 4 (2 bytes for the type, 2 for the
                // encoding of zero-length
                if (csrlV2.length() != 4) {
                    throw new RuntimeException("Incorrect length from " +
                            "default object.  Expected 4, got " +
                            csrlV2.length());
                }

                // Since there's no data, there are no status_type or request
                // data fields defined.  An empty, unmodifiable list should be
                // returned when obtained from the extension.
                List<CertStatusReqItemV2> itemList = csrlV2.getRequestItems();
                if (!itemList.isEmpty()) {
                    throw new RuntimeException("Default CSRLV2 returned " +
                            "non-empty request list");
                } else {
                    try {
                        itemList.add(new CertStatusReqItemV2(
                                StatusRequestType.OCSP_MULTI,
                                new OCSPStatusRequest()));
                        throw new RuntimeException("Returned itemList is " +
                                "modifiable!");
                    } catch (UnsupportedOperationException uoe) { }
                }

                pass = Boolean.TRUE;
            } catch (Exception e) {
                e.printStackTrace(System.out);
                message = e.getClass().getName();
            }

            return new AbstractMap.SimpleEntry<>(pass, message);
        }
    };

    public static final TestCase testCtorItemList = new TestCase() {
        @Override
        public Map.Entry<Boolean, String> runTest() {
            Boolean pass = Boolean.FALSE;
            String message = null;
            OCSPStatusRequest osr = new OCSPStatusRequest();
            List<CertStatusReqItemV2> noItems = Collections.emptyList();
            List<CertStatusReqItemV2> defList =
                    new ArrayList<CertStatusReqItemV2>() {{
                add(new CertStatusReqItemV2(StatusRequestType.OCSP_MULTI, osr));
                add(new CertStatusReqItemV2(StatusRequestType.OCSP, osr));
            }};
            List<CertStatusReqItemV2> unknownTypesList =
                    new ArrayList<CertStatusReqItemV2>() {{
                add(new CertStatusReqItemV2(StatusRequestType.get(8),
                        new UnknownStatusRequest(new byte[0])));
                add(new CertStatusReqItemV2(StatusRequestType.get(12),
                        new UnknownStatusRequest(new byte[5])));
            }};

            try {
                HandshakeOutStream hsout = new HandshakeOutStream(null);
                StatusRequest basicStatReq = new OCSPStatusRequest();

                // Create an extension using a default-style OCSPStatusRequest
                // (no responder IDs, no extensions).
                CertStatusReqListV2Extension csrlv2 =
                        new CertStatusReqListV2Extension(defList);
                csrlv2.send(hsout);
                TestUtils.valueCheck(wrapExtData(CSRLV2_DEF),
                        hsout.toByteArray());
                hsout.reset();

                // Create the extension using a StatusRequestType not already
                // instantiated as a static StatusRequestType
                // (e.g. OCSP/OCSP_MULTI)
                csrlv2 = new CertStatusReqListV2Extension(unknownTypesList);
                List<CertStatusReqItemV2> itemList = csrlv2.getRequestItems();
                if (itemList.size() != unknownTypesList.size()) {
                    throw new RuntimeException("Custom CSRLV2 returned " +
                            "an incorrect number of items: expected " +
                            unknownTypesList.size() + ", got " +
                            itemList.size());
                } else {
                    // Verify that the list is unmodifiable
                    try {
                        itemList.add(new CertStatusReqItemV2(
                                StatusRequestType.OCSP_MULTI,
                                new OCSPStatusRequest()));
                        throw new RuntimeException("Returned itemList is " +
                                "modifiable!");
                    } catch (UnsupportedOperationException uoe) { }
                }

                // Pass a null value for the item list.  This should throw
                // an exception
                try {
                    CertStatusReqListV2Extension csrlv2Null =
                            new CertStatusReqListV2Extension(null);
                    throw new RuntimeException("Constructor accepted a " +
                            "null request list");
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
            CertStatusReqListV2Extension csrlv2;

            try {
                // To simulate the extension coming in a ServerHello, the
                // type and length would already be read by HelloExtensions
                // and there is no extension data
                HandshakeInStream hsis = new HandshakeInStream();
                hsis.incomingRecord(ByteBuffer.wrap(new byte[0]));
                csrlv2 = new CertStatusReqListV2Extension(hsis,
                        hsis.available());

                // Verify length/request list
                if (csrlv2.length() != 4) {
                     throw new RuntimeException("Invalid length: received " +
                            csrlv2.length() + ", expected 4");
                } else {
                    List<CertStatusReqItemV2> itemList =
                            csrlv2.getRequestItems();
                    if (!itemList.isEmpty()) {
                        throw new RuntimeException("Default CSRLV2 returned " +
                                "non-empty request list");
                    } else {
                        try {
                            itemList.add(new CertStatusReqItemV2(
                                    StatusRequestType.OCSP_MULTI,
                                    new OCSPStatusRequest()));
                            throw new RuntimeException("Returned itemList is " +
                                    "modifiable!");
                        } catch (UnsupportedOperationException uoe) { }
                    }
                }

                // Try the an extension with our basic client-generated
                // status_request_v2 (2 items, ocsp_multi and ocsp, each with
                // a default OCSPStatusRequest
                hsis = new HandshakeInStream();
                hsis.incomingRecord(ByteBuffer.wrap(CSRLV2_DEF));
                csrlv2 = new CertStatusReqListV2Extension(hsis,
                        hsis.available());
                if (csrlv2.length() != (CSRLV2_DEF.length + 4)) {
                    throw new RuntimeException("Invalid length: received " +
                            csrlv2.length() + ", expected " +
                            CSRLV2_DEF.length + 4);
                } else {
                    List<CertStatusReqItemV2> itemList =
                            csrlv2.getRequestItems();
                    if (itemList.size() != 2) {
                        throw new RuntimeException("Unexpected number of " +
                                "items request list, expected 2, got " +
                                itemList.size());
                    } else {
                        try {
                            itemList.add(new CertStatusReqItemV2(
                                    StatusRequestType.OCSP_MULTI,
                                    new OCSPStatusRequest()));
                            throw new RuntimeException("Returned itemList is " +
                                    "modifiable!");
                        } catch (UnsupportedOperationException uoe) { }
                    }
                }

                // Try incoming data with an illegal zero-length
                // certificate_status_req_list
                try {
                    hsis = new HandshakeInStream();
                    hsis.incomingRecord(
                            ByteBuffer.wrap(CSRLV2_INVALID_ZEROLEN));
                    csrlv2 = new CertStatusReqListV2Extension(hsis,
                            hsis.available());
                    throw new RuntimeException("Unxpected successful " +
                            "object construction");
                } catch (SSLException ssle) { }

                // Try extensions where the certificate_status_req_list length
                // is either too long or too short
                try {
                    hsis = new HandshakeInStream();
                    hsis.incomingRecord(ByteBuffer.wrap(CSRLV2_LEN_TOO_LONG));
                    csrlv2 = new CertStatusReqListV2Extension(hsis,
                            hsis.available());
                    throw new RuntimeException("Unxpected successful " +
                            "object construction");
                } catch (SSLException ssle) { }

                try {
                    hsis = new HandshakeInStream();
                    hsis.incomingRecord(ByteBuffer.wrap(CSRLV2_LEN_TOO_SHORT));
                    csrlv2 = new CertStatusReqListV2Extension(hsis,
                            hsis.available());
                    throw new RuntimeException("Unxpected successful " +
                            "object construction");
                } catch (SSLException ssle) { }

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
        int bufferLen = extData.length + 4;
        ByteBuffer bb = ByteBuffer.allocate(bufferLen);

        bb.putShort((short)ExtensionType.EXT_STATUS_REQUEST_V2.id);
        bb.putShort((short)extData.length);
        if (extData.length != 0) {
            bb.put(extData);
        }
        return bb.array();
    }
}
