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
 * @run main/othervm -Djava.security.manager=allow TestInterop sm
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

public class TestInterop extends PKCS11Test {

    private final static BigInteger p = new BigInteger
    ("323170060713110073001535134778251633624880571334890751745884341392698068"
        + "34136210002792056362640164685458556357935330816928829023080573472625273"
        +  "55474246124574102620252791657297286270630032526342821314576693141422365"
        +  "42209411113486299916574782680342305530863490506355577122191878903327295"
        +  "69696129743856241741236237225197346402691855797767976823014625397933058"
        +  "01522685873076119753243646747585546071504389684494036613049769781285429"
        +  "59586595975670512838521327844685229255045682728791137200989318739591433"
        +  "74175837826000278034973198552060607533234122603254684088120031105907484"
        +  "281003994966956119696956248629032338072839127039");

    private final static BigInteger g = new BigInteger("2");

    private final static BigInteger ya = new BigInteger
    ("195915939935224588949565311035626707771530451981381185028563637852254495"
        + "75430045343862430996036405475686075240600888109630632414838068143976582"
        + "17011775615116685720200523355168263209779641001610639850900370886316106"
        + "01497415008487435898265740844239098380930685529838159939938827324425756"
        + "56884732725098478772809413830970072696033051548634351893065967185269311"
        + "78581483599462810365908846191052202349732975102754706671569415242231975"
        + "55517006036214709466769896628120316528268893831142765343193489772971325"
        + "84959563371468678134821398236925014752356749975178892610677362563508220"
        + "496241271516706808514454828287465344991932554923");

    private final static BigInteger xa = new BigInteger
            ("11384668052674958769021615158479523900020200976232975769568008330127");

    private final static BigInteger yb  = new BigInteger
    ("877715229997539617233720559981595158212365876455522851897223898750163746"
         + "86621766709350927569855881458726716527681127097048465895222721262741195"
         + "88773842161784827843574089987774473829243488973959184851302526873165070"
         + "92883284782352474060295826192678855794824743985812477250255387701300909"
         + "79028097349734370777609731287504870119408936827387370660880107280174314"
         + "00167645482417462793275820469641753557081753330911240014603100278602507"
         + "99526954243362959305600702457765853688950579775867915968240296800645809"
         + "10089764994212653303564730581912352826509226575994190450669619273753770"
         + "57904736025426318925061598176467271268250091530");

    private final static BigInteger xb = new BigInteger
            ("502117379354732837025657157713043334787505517644055959578005928377");

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
            KeyAgreement kbSunJCE = KeyAgreement.getInstance("DH", "SunJCE");
            DHPrivateKeySpec privSpecA = new DHPrivateKeySpec(xa, p, g);
            DHPublicKeySpec pubSpecA = new DHPublicKeySpec(ya, p, g);
            PrivateKey privA = kf.generatePrivate(privSpecA);
            PublicKey pubA = kf.generatePublic(pubSpecA);

            DHPrivateKeySpec privSpecB = new DHPrivateKeySpec(xb, p, g);
            DHPublicKeySpec pubSpecB = new DHPublicKeySpec(yb, p, g);
            PrivateKey privB = kf.generatePrivate(privSpecB);
            PublicKey pubB = kf.generatePublic(pubSpecB);

            ka.init(privA);
            ka.doPhase(pubB, true);
            byte[] n1 = ka.generateSecret();

            kbSunJCE.init(privB);
            kbSunJCE.doPhase(pubA, true);
            byte[] n2 = kbSunJCE.generateSecret();

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
