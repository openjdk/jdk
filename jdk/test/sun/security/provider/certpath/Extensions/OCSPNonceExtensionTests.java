/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8046321
 * @summary Unit tests for OCSPNonceExtension objects
 */

import java.security.cert.Extension;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

import sun.security.util.DerValue;
import sun.security.util.DerInputStream;
import sun.security.util.ObjectIdentifier;
import sun.security.provider.certpath.OCSPNonceExtension;
import sun.security.x509.PKIXExtensions;

public class OCSPNonceExtensionTests {
    public static final boolean DEBUG = true;
    public static final String OCSP_NONCE_OID = "1.3.6.1.5.5.7.48.1.2";
    public static final String ELEMENT_NONCE = "nonce";
    public static final String EXT_NAME = "OCSPNonce";

    // DER encoding for OCSP nonce extension:
    // OID = 1.3.6.1.5.5.7.48.1.2
    // Critical = true
    // 48 bytes of 0xDEADBEEF
    public static final byte[] OCSP_NONCE_DER = {
          48,   66,    6,    9,   43,    6,    1,    5,
           5,    7,   48,    1,    2,    1,    1,   -1,
           4,   50,    4,   48,  -34,  -83,  -66,  -17,
         -34,  -83,  -66,  -17,  -34,  -83,  -66,  -17,
         -34,  -83,  -66,  -17,  -34,  -83,  -66,  -17,
         -34,  -83,  -66,  -17,  -34,  -83,  -66,  -17,
         -34,  -83,  -66,  -17,  -34,  -83,  -66,  -17,
         -34,  -83,  -66,  -17,  -34,  -83,  -66,  -17,
         -34,  -83,  -66,  -17,
    };

    // 16 bytes of 0xDEADBEEF
    public static final byte[] DEADBEEF_16 = {
         -34,  -83,  -66,  -17,  -34,  -83,  -66,  -17,
         -34,  -83,  -66,  -17,  -34,  -83,  -66,  -17,
    };

    // DER encoded extension using 16 bytes of DEADBEEF
    public static final byte[] OCSP_NONCE_DB16 = {
          48,   31,    6,    9,   43,    6,    1,    5,
           5,    7,   48,    1,    2,    4,   18,    4,
          16,  -34,  -83,  -66,  -17,  -34,  -83,  -66,
         -17,  -34,  -83,  -66,  -17,  -34,  -83,  -66,
         -17
    };

    public static void main(String [] args) throws Exception {
        Map<String, TestCase> testList =
                new LinkedHashMap<String, TestCase>() {{
            put("CTOR Test (provide length)", testCtorByLength);
            put("CTOR Test (provide extension DER encoding)",
                    testCtorSuperByDerValue);
            put("Use set() call to provide random data", testResetValue);
            put("Test get() method", testGet);
            put("test set() method", testSet);
            put("Test getElements() method", testGetElements);
            put("Test getName() method", testGetName);
            put("Test delete() method", testDelete);
        }};

        System.out.println("============ Tests ============");
        int testNo = 0;
        int numberFailed = 0;
        Map.Entry<Boolean, String> result;
        for (String testName : testList.keySet()) {
            System.out.println("Test " + ++testNo + ": " + testName);
            result = testList.get(testName).runTest();
            System.out.print("Result: " + (result.getKey() ? "PASS" : "FAIL"));
            System.out.println(" " +
                    (result.getValue() != null ? result.getValue() : ""));
            System.out.println("-------------------------------------------");
            if (!result.getKey()) {
                numberFailed++;
            }
        }
        System.out.println("End Results: " + (testList.size() - numberFailed) +
                " Passed" + ", " + numberFailed + " Failed.");
        if (numberFailed > 0) {
            throw new RuntimeException(
                    "One or more tests failed, see test output for details");
        }
    }

    private static void dumpHexBytes(byte[] data) {
        if (data != null) {
            for (int i = 0; i < data.length; i++) {
                if (i % 16 == 0 && i != 0) {
                    System.out.print("\n");
                }
                System.out.print(String.format("%02X ", data[i]));
            }
            System.out.print("\n");
        }
    }

    private static void debuglog(String message) {
        if (DEBUG) {
            System.out.println(message);
        }
    }

    public static void verifyExtStructure(byte[] derData) throws IOException {
        debuglog("verifyASN1Extension() received " + derData.length + " bytes");
        DerInputStream dis = new DerInputStream(derData);

        // The sequenceItems array should be either two or three elements
        // long.  If three, then the criticality bit setting has been asserted.
        DerValue[] sequenceItems = dis.getSequence(3);
        debuglog("Found sequence containing " + sequenceItems.length +
                " elements");
        if (sequenceItems.length != 2 && sequenceItems.length != 3) {
            throw new RuntimeException("Incorrect number of items found in " +
                    "the SEQUENCE (Got " + sequenceItems.length +
                    ", expected 2 or 3 items)");
        }

        int seqIndex = 0;
        ObjectIdentifier extOid = sequenceItems[seqIndex++].getOID();
        debuglog("Found OID: " + extOid.toString());
        if (!extOid.equals((Object)PKIXExtensions.OCSPNonce_Id)) {
            throw new RuntimeException("Incorrect OID (Got " +
                    extOid.toString() + ", expected " +
                    PKIXExtensions.OCSPNonce_Id.toString() + ")");
        }

        if (sequenceItems.length == 3) {
            // Non-default criticality bit setting should be at index 1
            boolean isCrit = sequenceItems[seqIndex++].getBoolean();
            debuglog("Found BOOLEAN (critical): " + isCrit);
        }

        // The extnValue is an encapsulating OCTET STRING that contains the
        // extension's value.  For the OCSP Nonce, that value itself is also
        // an OCTET STRING consisting of the random bytes.
        DerValue extnValue =
                new DerValue(sequenceItems[seqIndex++].getOctetString());
        byte[] nonceData = extnValue.getOctetString();
        debuglog("Found " + nonceData.length + " bytes of nonce data");
    }

    public interface TestCase {
        Map.Entry<Boolean, String> runTest();
    }

    public static final TestCase testCtorByLength = new TestCase() {
        @Override
        public Map.Entry<Boolean, String> runTest() {
            Boolean pass = Boolean.FALSE;
            String message = null;
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                Extension nonceByLen = new OCSPNonceExtension(32);

                // Verify overall encoded extension structure
                nonceByLen.encode(baos);
                verifyExtStructure(baos.toByteArray());

                // Verify the name, elements, and data conform to
                // expected values for this specific object.
                boolean crit = nonceByLen.isCritical();
                String oid = nonceByLen.getId();
                DerValue nonceData = new DerValue(nonceByLen.getValue());

                if (crit) {
                    message = "Extension incorrectly marked critical";
                } else if (!oid.equals(OCSP_NONCE_OID)) {
                    message = "Incorrect OID (Got " + oid + ", Expected " +
                            OCSP_NONCE_OID + ")";
                } else if (nonceData.getTag() != DerValue.tag_OctetString) {
                    message = "Incorrect nonce data tag type (Got " +
                            String.format("0x%02X", nonceData.getTag()) +
                            ", Expected 0x04)";
                } else if (nonceData.getOctetString().length != 32) {
                    message = "Incorrect nonce byte length (Got " +
                            nonceData.getOctetString().length +
                            ", Expected 32)";
                } else {
                    pass = Boolean.TRUE;
                }
            } catch (Exception e) {
                e.printStackTrace(System.out);
                message = e.getClass().getName();
            }

            return new AbstractMap.SimpleEntry<>(pass, message);
        }
    };

    public static final TestCase testCtorSuperByDerValue = new TestCase() {
        @Override
        public Map.Entry<Boolean, String> runTest() {
            Boolean pass = Boolean.FALSE;
            String message = null;
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                Extension nonceByDer = new sun.security.x509.Extension(
                        new DerValue(OCSP_NONCE_DER));

                // Verify overall encoded extension structure
                nonceByDer.encode(baos);
                verifyExtStructure(baos.toByteArray());

                // Verify the name, elements, and data conform to
                // expected values for this specific object.
                boolean crit = nonceByDer.isCritical();
                String oid = nonceByDer.getId();
                DerValue nonceData = new DerValue(nonceByDer.getValue());

                if (!crit) {
                    message = "Extension lacks expected criticality setting";
                } else if (!oid.equals(OCSP_NONCE_OID)) {
                    message = "Incorrect OID (Got " + oid + ", Expected " +
                            OCSP_NONCE_OID + ")";
                } else if (nonceData.getTag() != DerValue.tag_OctetString) {
                    message = "Incorrect nonce data tag type (Got " +
                            String.format("0x%02X", nonceData.getTag()) +
                            ", Expected 0x04)";
                } else if (nonceData.getOctetString().length != 48) {
                    message = "Incorrect nonce byte length (Got " +
                            nonceData.getOctetString().length +
                            ", Expected 48)";
                } else {
                    pass = Boolean.TRUE;
                }
            } catch (Exception e) {
                e.printStackTrace(System.out);
                message = e.getClass().getName();
            }

            return new AbstractMap.SimpleEntry<>(pass, message);
        }
    };

    public static final TestCase testResetValue = new TestCase() {
        @Override
        public Map.Entry<Boolean, String> runTest() {
            Boolean pass = Boolean.FALSE;
            String message = null;
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                OCSPNonceExtension nonce = new OCSPNonceExtension(32);

                // Reset the nonce data to reflect 16 bytes of DEADBEEF
                nonce.set(OCSPNonceExtension.NONCE, (Object)DEADBEEF_16);

                // Verify overall encoded extension content
                nonce.encode(baos);
                dumpHexBytes(OCSP_NONCE_DB16);
                System.out.println();
                dumpHexBytes(baos.toByteArray());

                pass = Arrays.equals(baos.toByteArray(), OCSP_NONCE_DB16);
            } catch (Exception e) {
                e.printStackTrace(System.out);
                message = e.getClass().getName();
            }

            return new AbstractMap.SimpleEntry<>(pass, message);
        }
    };

    public static final TestCase testSet = new TestCase() {
        @Override
        public Map.Entry<Boolean, String> runTest() {
            Boolean pass = Boolean.FALSE;
            String message = null;
            try {
                OCSPNonceExtension nonceByLen = new OCSPNonceExtension(32);

                // Set the nonce data to 16 bytes of DEADBEEF
                nonceByLen.set(ELEMENT_NONCE, DEADBEEF_16);
                byte[] nonceData = (byte[])nonceByLen.get(ELEMENT_NONCE);
                if (!Arrays.equals(nonceData, DEADBEEF_16)) {
                    throw new RuntimeException("Retuned nonce data does not " +
                            "match expected result");
                }

                // Now try to set a value using an object that is not a byte
                // array
                int[] INT_DB_16 = {
                    0xDEADBEEF, 0xDEADBEEF, 0xDEADBEEF, 0xDEADBEEF
                };
                try {
                    nonceByLen.set(ELEMENT_NONCE, INT_DB_16);
                    throw new RuntimeException("Accepted get() for " +
                            "unsupported element name");
                } catch (IOException ioe) { }     // Expected result

                // And try setting a value using an unknown element name
                try {
                    nonceByLen.set("FOO", DEADBEEF_16);
                    throw new RuntimeException("Accepted get() for " +
                            "unsupported element name");
                } catch (IOException ioe) { }     // Expected result

                pass = Boolean.TRUE;
            } catch (Exception e) {
                e.printStackTrace(System.out);
                message = e.getClass().getName();
            }

            return new AbstractMap.SimpleEntry<>(pass, message);
        }
    };

        public static final TestCase testGet = new TestCase() {
        @Override
        public Map.Entry<Boolean, String> runTest() {
            Boolean pass = Boolean.FALSE;
            String message = null;
            try {
                OCSPNonceExtension nonceByLen = new OCSPNonceExtension(32);

                // Grab the nonce data by its correct element name
                byte[] nonceData = (byte[])nonceByLen.get(ELEMENT_NONCE);
                if (nonceData == null || nonceData.length != 32) {
                    throw new RuntimeException("Unexpected return value from " +
                            "get() method: either null or incorrect length");
                }

                // Now try to get any kind of data using an element name that
                // doesn't exist for this extension.
                try {
                    nonceByLen.get("FOO");
                    throw new RuntimeException("Accepted get() for " +
                            "unsupported element name");
                } catch (IOException ioe) { }     // Expected result

                pass = Boolean.TRUE;
            } catch (Exception e) {
                e.printStackTrace(System.out);
                message = e.getClass().getName();
            }

            return new AbstractMap.SimpleEntry<>(pass, message);
        }
    };

    public static final TestCase testGetElements = new TestCase() {
        @Override
        public Map.Entry<Boolean, String> runTest() {
            Boolean pass = Boolean.FALSE;
            String message = null;
            try {
                OCSPNonceExtension nonceByLen = new OCSPNonceExtension(32);

                int elementCount = 0;
                boolean foundElement = false;

                // There should be exactly one element and its name should
                // be "nonce"
                for (Enumeration<String> elements = nonceByLen.getElements();
                        elements.hasMoreElements(); elementCount++) {
                    if (elements.nextElement().equals(ELEMENT_NONCE)) {
                        foundElement = true;
                    }
                }

                if (!foundElement || elementCount != 1) {
                    throw new RuntimeException("Unexpected or missing " +
                            "Enumeration element");
                }

                pass = Boolean.TRUE;
            } catch (Exception e) {
                e.printStackTrace(System.out);
                message = e.getClass().getName();
            }

            return new AbstractMap.SimpleEntry<>(pass, message);
        }
    };

    public static final TestCase testGetName = new TestCase() {
        @Override
        public Map.Entry<Boolean, String> runTest() {
            Boolean pass = Boolean.FALSE;
            String message = null;
            try {
                OCSPNonceExtension nonceByLen = new OCSPNonceExtension(32);
                pass = new Boolean(nonceByLen.getName().equals(EXT_NAME));
            } catch (Exception e) {
                e.printStackTrace(System.out);
                message = e.getClass().getName();
            }

            return new AbstractMap.SimpleEntry<>(pass, message);
        }
    };

    public static final TestCase testDelete = new TestCase() {
        @Override
        public Map.Entry<Boolean, String> runTest() {
            Boolean pass = Boolean.FALSE;
            String message = null;
            try {
                OCSPNonceExtension nonceByLen = new OCSPNonceExtension(32);

                // First verify that there's data to begin with
                byte[] nonceData = (byte[])nonceByLen.get(ELEMENT_NONCE);
                if (nonceData == null || nonceData.length != 32) {
                    throw new RuntimeException("Unexpected return value from " +
                            "get() method: either null or incorrect length");
                }

                // Attempt to delete using an element name that doesn't exist
                // for this extension.
                try {
                    nonceByLen.delete("FOO");
                    throw new RuntimeException("Accepted delete() for " +
                            "unsupported element name");
                } catch (IOException ioe) { }     // Expected result

                // Now attempt to properly delete the extension data
                nonceByLen.delete(ELEMENT_NONCE);
                nonceData = (byte[])nonceByLen.get(ELEMENT_NONCE);
                if (nonceData != null) {
                    throw new RuntimeException("Unexpected non-null return");
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
