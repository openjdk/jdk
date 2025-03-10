/*
 * Copyright (c) 2024, Intel Corporation. All rights reserved.
 *
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

import java.util.Random;
import java.util.List;
import java.util.LinkedList;
import java.math.BigInteger;
import java.lang.reflect.Field;
import java.security.spec.ECParameterSpec;
import sun.security.ec.ECOperations;
import sun.security.util.ECUtil;
import sun.security.util.NamedCurve;
import sun.security.util.CurveDB;
import sun.security.ec.point.*;
import java.security.spec.ECPoint;
import sun.security.util.KnownOIDs;
import sun.security.util.math.IntegerMontgomeryFieldModuloP;
import sun.security.util.math.intpoly.*;

/*
 * @test
 * @modules java.base/sun.security.ec java.base/sun.security.ec.point
 *          java.base/sun.security.util java.base/sun.security.util.math
 *          java.base/sun.security.util.math.intpoly
 * @run main/othervm --add-opens java.base/sun.security.ec=ALL-UNNAMED
 *      ECOperationsKATTest
 * @summary Unit test ECOperationsKATTest.
 */

/*
 * @test
 * @modules java.base/sun.security.ec java.base/sun.security.ec.point
 *          java.base/sun.security.util java.base/sun.security.util.math
 *          java.base/sun.security.util.math.intpoly
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -Xcomp
 *      -XX:-TieredCompilation --add-opens java.base/sun.security.ec=ALL-UNNAMED
 *      -XX:+UnlockDiagnosticVMOptions ECOperationsKATTest
 * @summary Unit test ECOperationsKATTest.
 */

 public class ECOperationsKATTest {
    final private static java.util.HexFormat hex = java.util.HexFormat.of();

    public static void main(String args[]) throws Exception {
        int testsPassed = 0;
        int testNumber = 0;

        for (TestData test : testList) {
            System.out.println("*** Test " + ++testNumber + ": " + test.testName);
            if (runSingleTest(test)) {
                testsPassed++;
            }
        }
        System.out.println();

        if (testsPassed != testNumber) {
            throw new RuntimeException(
                    "One or more tests failed. Check output for details");
        }
    }

    private static boolean check(MutablePoint testValue, ECPoint reference) {
        AffinePoint affine = testValue.asAffine();
        BigInteger x = affine.getX().asBigInteger();
        BigInteger y = affine.getY().asBigInteger();
        BigInteger refX = reference.getAffineX();
        BigInteger refY = reference.getAffineY();

        if (!refX.equals(x) || !refY.equals(y)) {
            System.out.println("ERROR - Output Mismatch!");
            System.out.println("Expected: X: " + refX.toString(16) + " Y: "
                    + refY.toString(16));
            System.out.println(
                    "Result:   X: " + x.toString(16) + " Y: " + y.toString(16));
            return false;
        }
        return true;
    }

    private static class TestData {
        public TestData(String name, String keyStr, String xStr1, String yStr1,
                String xStr2, String yStr2) {
            testName = name;
            // multiplier = (new BigInteger(keyStr, 16)).toByteArray();
            multiplier = hex.parseHex(keyStr);
            sun.security.util.ArrayUtil.reverse(multiplier);
            reference1 = new ECPoint(new BigInteger(xStr1, 16),
                    new BigInteger(yStr1, 16));
            reference2 = new ECPoint(new BigInteger(xStr2, 16),
                    new BigInteger(yStr2, 16));
        }

        String testName;
        byte[] multiplier;
        ECPoint reference1; // For generator multiplier test
        ECPoint reference2; // For non-generator multiplier test
    }

    public static final List<TestData> testList = new LinkedList<TestData>() {{
    // (x1,y1) = mult*generator
    // (x2,y2) = mult*mult*generator
    add(new TestData("Test Vector #1",
        "0000000000000000000000000000000000000000000000000000000000000012", // mult
        "1057E0AB5780F470DEFC9378D1C7C87437BB4C6F9EA55C63D936266DBD781FDA", // x1
        "F6F1645A15CBE5DC9FA9B7DFD96EE5A7DCC11B5C5EF4F1F78D83B3393C6A45A2", // y1
        "4954047A366A91E3FD94E574DB6F2B04F3A8465883DBC55A816EA563BF54A324", // x2
        "B5A54786FD9EA48C9FC38A0557B0C4D54F285908A7291B630D06BEE970F530D3") // y2
    );
    add(new TestData("Test Vector #2",
        "1200000000000000000000000000000000000000000000000000000000000000", // mult
        "DF684E6D0D57AF8B89DA11E8F7436C3D360F531D62BDCE42C5A8B72D73D5C717", // x
        "9D3576BD03C09B8F416EE9C27D70AD4A425119271ACF549312CA48758F4E1FEC", // y
        "57C8257EEAABF5446DCFACB99DEE104367B6C9950C76797C372EB177D5FA23B3", // x
        "1CD3E8A34521C1C8E574EB4B99343CAA57E00725D8618F0231C7C79AA6837725") // y
    );
    add(new TestData("Test Vector #3",
        "0000000000000000000000000000000120000000000000000000000000000012", // mult
        "A69DFD47B24485E5F523BDA5FBACF03F5A7C3D22E0C2BC6705594B7B051A06D0", // x
        "ECF19629416BE5C9AF1E30988F3AA8B803809CF4D12944EB49C5E9892723798A", // y
        "1E28559F5B681C308632EE11A007B9891B3FD592C982C4926153795794295E58", // x
        "3C373046C27BB34609A43C91DF6D4B9AB9EB08F3B69A8F8FAE944211D8297F30") // y
    );
    add(new TestData("Test Vector #4",
        "0000000000000000000000000000000000000000000000000000000000000001", // mult
        "6B17D1F2E12C4247F8BCE6E563A440F277037D812DEB33A0F4A13945D898C296", // x
        "4FE342E2FE1A7F9B8EE7EB4A7C0F9E162BCE33576B315ECECBB6406837BF51F5", // y
        "6B17D1F2E12C4247F8BCE6E563A440F277037D812DEB33A0F4A13945D898C296", // x
        "4FE342E2FE1A7F9B8EE7EB4A7C0F9E162BCE33576B315ECECBB6406837BF51F5") // y
    );
    add(new TestData("Test Vector #5",
        "EFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF", // mult
        "66B71D0BD47344197CCFB0C9578EAF0ADB609E05BB4E8F87D56BD34F24EE7C47", // x
        "14A0ECB7F708C02B2BAE238D2C4607BB9D04FCE64E10A428C911D6FA25B2F0FD", // y
        "D25AAFD0FCC5B5E95C84C0702C138BC4D7FEB4E5F9C2DFB4301E313507EFDF44", // x
        "F3F04EBC7D308511B0392BB7171CF92688D6484A95A8100EDFC933613A359133") // y
    );
    add(new TestData("Test Vector #6",
        "1111111111111111111111111111111111111111111111111111111111111111", // mult
        "0217E617F0B6443928278F96999E69A23A4F2C152BDF6D6CDF66E5B80282D4ED", // x
        "194A7DEBCB97712D2DDA3CA85AA8765A56F45FC758599652F2897C65306E5794", // y
        "A83A07D6AE918359DEBCC385DA1E416EB83417435079CA8DB06005E107C309A0", // x
        "5AACDF816850C33EB3E54F3D0DD759B97B5E7065B2060016F73735E4A6AADE23") // y
    );
    }};

    private static boolean runSingleTest(TestData testData) {
        int keySize = 256;
        ECParameterSpec params = ECUtil.getECParameterSpec(keySize);
        NamedCurve curve = CurveDB.lookup(KnownOIDs.secp256r1.value());
        ECPoint generator = curve.getGenerator();
        BigInteger b = curve.getCurve().getB();
        if (params == null || generator == null) {
            throw new RuntimeException(
                    "No EC parameters available for key size " + keySize + " bits");
        }

        ECOperations ops = ECOperations.forParameters(params).get();
        ECOperations opsReference = new ECOperations(
                IntegerPolynomialP256.ONE.getElement(b), P256OrderField.ONE);

        boolean instanceTest1 = ops
                .getField() instanceof IntegerMontgomeryFieldModuloP;
        boolean instanceTest2 = opsReference
                .getField() instanceof IntegerMontgomeryFieldModuloP;
        if (instanceTest1 == false || instanceTest2 == true) {
            throw new RuntimeException("Bad Initialization: [" + instanceTest1 + ","
                    + instanceTest2 + "]");
        }

        MutablePoint nextPoint = ops.multiply(generator, testData.multiplier);
        MutablePoint nextReferencePoint = opsReference.multiply(generator,
                testData.multiplier);
        if (!check(nextReferencePoint, testData.reference1)
                || !check(nextPoint, testData.reference1)) {
            return false;
        }

        nextPoint = ops.multiply(nextPoint.asAffine(), testData.multiplier);
        nextReferencePoint = opsReference.multiply(nextReferencePoint.asAffine(),
                testData.multiplier);
        if (!check(nextReferencePoint, testData.reference2)
                || !check(nextPoint, testData.reference2)) {
            return false;
        }

        return true;
    }
}

//make test TEST="test/jdk/com/sun/security/ec/ECOperationsKATTest.java"

/*
 * KAT generator using OpenSSL for reference vectors
 * g++ ecpoint.cpp -g -lcrypto -Wno-deprecated-declarations && ./a.out
 * (Some OpenSSL EC operations are marked internal i.e. deprecated)
 *

#include <openssl/obj_mac.h>
#include <openssl/ec.h>

void check(int rc, const char* locator) {
  if (rc != 1) {
    printf("Failed at %s\n", locator);
    exit(55);
  }
}

int main(){
  BN_CTX* ctx = BN_CTX_new();
  BIGNUM* k = BN_CTX_get(ctx);
  BIGNUM* x1 = BN_CTX_get(ctx);
  BIGNUM* y1 = BN_CTX_get(ctx);
  BIGNUM* x2 = BN_CTX_get(ctx);
  BIGNUM* y2 = BN_CTX_get(ctx);
  EC_GROUP *ec_group = EC_GROUP_new_by_curve_name(NID_X9_62_prime256v1);
  EC_POINT* pubkey = EC_POINT_new(ec_group);
  EC_POINT* pubkey2 = EC_POINT_new(ec_group);
  int rc;

  rc = BN_hex2bn(&k, "1111111111111111111111111111111111111111111111111111111111111111"); //check(rc, "set raw key");
  rc = EC_POINT_mul(ec_group, pubkey, k, NULL, NULL, ctx);  check(rc, "mult public key");
  rc = EC_POINT_get_affine_coordinates(ec_group, pubkey, x1, y1, ctx);   check(rc, "get affine coordinates");
  rc = EC_POINT_mul(ec_group, pubkey2, NULL, pubkey, k, ctx);  check(rc, "mult public key");
  rc = EC_POINT_get_affine_coordinates(ec_group, pubkey2, x2, y2, ctx);   check(rc, "get affine coordinates");
  printf("k: %s\n", BN_bn2hex(k));
  printf("x: %s\ny: %s\n", BN_bn2hex(x1), BN_bn2hex(y1));
  printf("x: %s\ny: %s\n", BN_bn2hex(x2), BN_bn2hex(y2));

  BN_CTX_free(ctx);
  return 0;
}
 */
