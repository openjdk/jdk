/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7146728
 * @summary Interop test for DH with secret that has a leading 0x00 byte
 * @library /test/lib ..
 * @modules jdk.crypto.cryptoki
 * @run main/othervm TestInterop
 */
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.util.Arrays;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.DHPrivateKeySpec;
import javax.crypto.spec.DHPublicKeySpec;
import jdk.test.lib.security.DiffieHellmanGroup;
import jdk.test.lib.security.SecurityUtils;

public class TestInterop extends PKCS11Test {

    private final static BigInteger ya = new BigInteger
   ("22272412859242949963897309866268099957623364986192222381531147912319" +
           "23153170556019072276127184001075566033823724518300406542189341984" +
           "14728033901164887842157675409022004721268960808255834930605035809" +
           "96449867261598768663006346373969582073599358922631400907241847771" +
           "58539394502794451638884093173505103869438428833148912071609829581" +
           "89477284513896649100113024962862016311693389603523142235630316916" +
           "51727812401021776761600004971782662420311224757086651213529674905" +
           "34921437167341469749945865459714558842881915928697452568830704027" +
           "08840053484115995358953663434943150292283157101600109003253293611" +
           "67575903571371898272633920086");

    private final static BigInteger xa = new BigInteger
    ("20959988947516815975588968321965141642005944293655257916834342975849");

    private final static BigInteger yb  = new BigInteger
    ("1788841814501653834923092375117807364896992833810838802030127811094" +
            "8450381275318704655838368105000403140578033341448162321874634765" +
            "6870663019881556386613144025875613921737258766185138415793010195" +
            "3802511267742963370821568963965936108932734114202964873644126233" +
            "6937947954023458790417933403303562491144788202839815534782475160" +
            "7813094179390506418017926774832227342290968359943612529948409558" +
            "4647213355501260440663649115694263879691520265343063263385211121" +
            "3396751542827391711077192604441343359832896902306354119121777576" +
            "6479255602858536672821464920683781338851326155035757018336622673" +
            "39973666608754923308482789421630138499");

    private final static BigInteger xb = new BigInteger
    ("37339373137107550077381337769340105015086522284791968753218309293526");

    @Override
    public void main(Provider prov) throws Exception {
        if (prov.getService("KeyAgreement", "DH") == null) {
            System.out.println("DH not supported, skipping");
            return;
        }
        try {
            System.out.println("testing generateSecret()");

            DHPublicKeySpec publicSpec;
            DHPrivateKeySpec privateSpec;
            KeyFactory kf = KeyFactory.getInstance("DH");
            KeyAgreement ka = KeyAgreement.getInstance("DH", prov);
            KeyAgreement kbSunJCE = KeyAgreement.getInstance("DH",
                    System.getProperty("test.provider.name", "SunJCE"));
            DiffieHellmanGroup dhGroup = SecurityUtils.getTestDHGroup();
            DHPrivateKeySpec privSpecA = new DHPrivateKeySpec(xa, dhGroup.getPrime(),
                    dhGroup.getBase());
            DHPublicKeySpec pubSpecA = new DHPublicKeySpec(ya, dhGroup.getPrime(),
                    dhGroup.getBase());
            PrivateKey privA = kf.generatePrivate(privSpecA);
            PublicKey pubA = kf.generatePublic(pubSpecA);

            DHPrivateKeySpec privSpecB = new DHPrivateKeySpec(xb, dhGroup.getPrime(),
                    dhGroup.getBase());
            DHPublicKeySpec pubSpecB = new DHPublicKeySpec(yb, dhGroup.getPrime(),
                    dhGroup.getBase());
            PrivateKey privB = kf.generatePrivate(privSpecB);
            PublicKey pubB = kf.generatePublic(pubSpecB);

            ka.init(privA);
            ka.doPhase(pubB, true);
            byte[] n1 = ka.generateSecret();

            kbSunJCE.init(privB);
            kbSunJCE.doPhase(pubA, true);
            byte[] n2 = kbSunJCE.generateSecret();

            // verify that a leading zero is present in secrets
            if (n1[0] != 0 || n2[0] != 0) {
                throw new Exception("First byte is not zero as expected");
            }
            if (Arrays.equals(n1, n2) == false) {
                throw new Exception("values mismatch!");
            } else {
                System.out.println("values: same");
            }

            System.out.println("testing generateSecret(byte[], int)");
            byte[] n3 = new byte[n1.length];
            ka.init(privB);
            ka.doPhase(pubA, true);
            int n3Len = ka.generateSecret(n3, 0);
            if (n3Len != n3.length) {
                throw new Exception("PKCS11 Length mismatch!");
            } else System.out.println("PKCS11 Length: ok");
            byte[] n4 = new byte[n2.length];
            kbSunJCE.init(privA);
            kbSunJCE.doPhase(pubB, true);
            int n4Len = kbSunJCE.generateSecret(n4, 0);
            if (n4Len != n4.length) {
                throw new Exception("SunJCE Length mismatch!");
            } else System.out.println("SunJCE Length: ok");

            if (Arrays.equals(n3, n4) == false) {
                throw new Exception("values mismatch! ");
            } else {
                System.out.println("values: same");
            }
        } catch (Exception ex) {
            System.out.println("Unexpected ex: " + ex);
            ex.printStackTrace();
            throw ex;
        }
    }

    public static void main(String[] args) throws Exception {
        main(new TestInterop(), args);
    }
}
