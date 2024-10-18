/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8072452 8163498
 * @summary Support DHE sizes up to 8192-bits and DSA sizes up to 3072-bits
 *          This test has been split based on lower/higher key sizes in order to
 *          reduce individual execution times and run in parallel
 *          (see SupportedDHParamGensLongKey.java)
 * @run main/timeout=300 SupportedDHParamGens 512
 * @run main/timeout=300 SupportedDHParamGens 768
 * @run main/timeout=300 SupportedDHParamGens 832
 * @run main/timeout=300 SupportedDHParamGens 1024
 * @run main/timeout=600 SupportedDHParamGens 2048
 * @run main/timeout=600 SupportedDHParamGens 3072
 * @run main/timeout=600 SupportedDHParamGens 4096
 */
import java.math.BigInteger;

import java.security.*;
import javax.crypto.*;
import javax.crypto.interfaces.*;
import javax.crypto.spec.*;

public class SupportedDHParamGens {

    static DHParameterSpec FFDHE2048_SPEC =  new DHParameterSpec(
            new BigInteger(
                    "32317006071311007300153513477825163362488057133489075174588434139269806834136210" +
                    "00279205636264016468545855635793533081692882902308057347262527355474246124574102" +
                    "62025279165729728627063003252634282131457669314142236542209411113486299916574782" +
                    "68034230553086349050635557712219187890332729569696129743856241741236237225197346" +
                    "40269185579776797682301462539793305801522685873076119753243646747585546071504389" +
                    "68449403661304976978128542959586595975670512838521327844685229255045682728791137" +
                    "20098931873959143374175837826000278034973198552060607533234122603254684088120031" +
                    "105907484281003994966956119696956248629032338072839127039"),
            BigInteger.valueOf(2));

    static DHParameterSpec FFDHE3072_SPEC =  new DHParameterSpec(
            new BigInteger(
                    "58096059953699580627585866542745800477917221049706565074388697400877932949390221" +
                    "79753100900150316602414836960597893531254315756065700170507943025794723871619068" +
                    "28282257914820765998433172428605713380020701482035695793333436453517620139309440" +
                    "69642803681463603224173972019215566563106962984174143184349293928069288683148317" +
                    "84332237038568260988712237196665742900353512788403877776568945491183287529096888" +
                    "88434888717690199575758854934021980760614995505687178104611719545342707025453385" +
                    "89647291017542811217873303255065749285035013349375791913491789018018664512628315" +
                    "60570379780282604068262795024384318599710948857446185134652829941527736472860172" +
                    "35451673386787778082905134616715359432959233925229587197688906988596412803859300" +
                    "23368461535221490262299843947816385011253126764518371449454513318325229466846209" +
                    "54184360294871798125320434686136230055213248587935623124338652624786221871129902" +
                    "570119964134282018641257113252046271726747647"),
            BigInteger.valueOf(2));

    static DHParameterSpec FFDHE4096_SPEC =  new DHParameterSpec(
            new BigInteger(
                    "10443888814131525066796027198465295458312690609921350090225887564443381720223226" +
                    "90710444046669809783930111585737890362691860127079270495454517218673016928427459" +
                    "14600186688577976298222932119236830334623520436805101030915567415569746034717694" +
                    "63940765351572849948952848216337009218117167389724518349794558970103063334685907" +
                    "51358365138782250372269117968985194322444535687415522007151638638141456178420621" +
                    "27782267499502799027867345862954439173691976629900551150544617766815444623488266" +
                    "59616807965769031991160893476349471877789065280080047566925716669229641225661745" +
                    "82776707332452371001272163776841229318324903125740713574141005124561965913888899" +
                    "75346173534797001169325631675166067895083002751025580484610558346505544661509044" +
                    "43095830507758085092970400396800574353422539265662408981958636315888889363641299" +
                    "20059308455669454034010391478238784189888594672336242763795138176353222845524644" +
                    "04009425896243361335403610464388192523848922401019419308891166616558422942466816" +
                    "54416889277904606082648642042377170020547443379889419746612146996897065215430062" +
                    "62604535890998125752275942608772174376107314217749233048217904944409836238235772" +
                    "30674987439676046337648021513346133347839568274660824258513395388388222678611803" +
                    "0184028136755970045385534758453247"),
            BigInteger.valueOf(2));


    public static void main(String[] args) throws Exception {
        int primeSize = Integer.valueOf(args[0]).intValue();

        System.out.println("Checking " + primeSize + " ...");
        DHParameterSpec spec = null;
        switch (primeSize) {
            case 2048: spec = FFDHE2048_SPEC;
                break;
            case 3072: spec = FFDHE3072_SPEC;
                break;
            case 4096: spec = FFDHE4096_SPEC;
                break;
            default:
                AlgorithmParameterGenerator apg =
                        AlgorithmParameterGenerator.getInstance("DH", "SunJCE");
                apg.init(primeSize);
                AlgorithmParameters ap = apg.generateParameters();
                spec = ap.getParameterSpec(DHParameterSpec.class);
                break;
        }

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("DH", System.getProperty("test.provider.name", "SunJCE"));
        kpg.initialize(spec);
        KeyPair kp = kpg.generateKeyPair();
        checkKeyPair(kp, primeSize);
    }

    private static void checkKeyPair(KeyPair kp, int pSize) throws Exception {

        DHPrivateKey privateKey = (DHPrivateKey)kp.getPrivate();
        BigInteger p = privateKey.getParams().getP();
        if (p.bitLength() != pSize) {
            throw new Exception(
                "Invalid modulus size: " + p.bitLength() + "/" + pSize);
        }

        if (!p.isProbablePrime(128)) {
            throw new Exception("Good luck, the modulus is composite!");
        }

        DHPublicKey publicKey = (DHPublicKey)kp.getPublic();
        p = publicKey.getParams().getP();
        if (p.bitLength() != pSize) {
            throw new Exception(
                "Invalid modulus size: " + p.bitLength() + "/" + pSize);
        }

        BigInteger leftOpen = BigInteger.ONE;
        BigInteger rightOpen = p.subtract(BigInteger.ONE);

        BigInteger x = privateKey.getX();
        if ((x.compareTo(leftOpen) <= 0) ||
                (x.compareTo(rightOpen) >= 0)) {
            throw new Exception(
                "X outside range [2, p - 2]:  x: " + x + " p: " + p);
        }

        BigInteger y = publicKey.getY();
        if ((y.compareTo(leftOpen) <= 0) ||
                (y.compareTo(rightOpen) >= 0)) {
            throw new Exception(
                "Y outside range [2, p - 2]:  x: " + x + " p: " + p);
        }
    }
}
