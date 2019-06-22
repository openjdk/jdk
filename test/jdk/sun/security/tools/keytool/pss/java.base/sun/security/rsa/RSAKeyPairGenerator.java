/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.rsa;

import java.math.BigInteger;

import java.security.*;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.RSAKeyGenParameterSpec;

import sun.security.jca.JCAUtil;
import sun.security.x509.AlgorithmId;
import static sun.security.rsa.RSAUtil.KeyType;

/**
 * Fake RSA keypair generation.
 */
public abstract class RSAKeyPairGenerator extends KeyPairGeneratorSpi {

    // public exponent to use
    private BigInteger publicExponent;

    // size of the key to generate, >= RSAKeyFactory.MIN_MODLEN
    private int keySize;

    private final KeyType type;
    private AlgorithmId rsaId;

    RSAKeyPairGenerator(KeyType type, int defKeySize) {
        this.type = type;
        // initialize to default in case the app does not call initialize()
        initialize(defKeySize, null);
    }

    // initialize the generator. See JCA doc
    public void initialize(int keySize, SecureRandom random) {
        try {
            initialize(new RSAKeyGenParameterSpec(keySize,
                    RSAKeyGenParameterSpec.F4), random);
        } catch (InvalidAlgorithmParameterException iape) {
            throw new InvalidParameterException(iape.getMessage());
        }
    }

    // second initialize method. See JCA doc.
    public void initialize(AlgorithmParameterSpec params, SecureRandom random)
            throws InvalidAlgorithmParameterException {
        if (params instanceof RSAKeyGenParameterSpec == false) {
            throw new InvalidAlgorithmParameterException
                ("Params must be instance of RSAKeyGenParameterSpec");
        }

        RSAKeyGenParameterSpec rsaSpec = (RSAKeyGenParameterSpec)params;
        int tmpKeySize = rsaSpec.getKeysize();
        BigInteger tmpPublicExponent = rsaSpec.getPublicExponent();
        AlgorithmParameterSpec tmpParams = rsaSpec.getKeyParams();

        if (tmpPublicExponent == null) {
            tmpPublicExponent = RSAKeyGenParameterSpec.F4;
        } else {
            if (tmpPublicExponent.compareTo(RSAKeyGenParameterSpec.F0) < 0) {
                throw new InvalidAlgorithmParameterException
                        ("Public exponent must be 3 or larger");
            }
            if (tmpPublicExponent.bitLength() > tmpKeySize) {
                throw new InvalidAlgorithmParameterException
                        ("Public exponent must be smaller than key size");
            }
        }

        // do not allow unreasonably large key sizes, probably user error
        try {
            RSAKeyFactory.checkKeyLengths(tmpKeySize, tmpPublicExponent,
                512, 64 * 1024);
        } catch (InvalidKeyException e) {
            throw new InvalidAlgorithmParameterException(
                "Invalid key sizes", e);
        }

        try {
            this.rsaId = RSAUtil.createAlgorithmId(type, tmpParams);
        } catch (ProviderException e) {
            throw new InvalidAlgorithmParameterException(
                "Invalid key parameters", e);
        }

        this.keySize = tmpKeySize;
        this.publicExponent = tmpPublicExponent;
    }

    // generate the keypair. See JCA doc
    public KeyPair generateKeyPair() {

        // accommodate odd key sizes in case anybody wants to use them
        BigInteger e = publicExponent;
        if (!e.equals(RSAKeyGenParameterSpec.F4)) {
            throw new AssertionError("Only support F4 now");
        }
        BigInteger p, q, n;

        // Pre-calculated p and q for e == RSAKeyGenParameterSpec.F4
        switch (keySize) {
            case 2048:
                p = new BigInteger("1600840041787354447543653385760927"
                        + "2642568308955833364523274045522752644800599"
                        + "8669541532595690224703734511692014533312515"
                        + "1867029838883431415692353449578487671384896"
                        + "6611685764860941767986520897595108597563035"
                        + "4023785639802607792535812062420427283857665"
                        + "9883578590844700707106157871508280052743363"
                        + "65749456332400771");
                q = new BigInteger("1303880717101677622201474394769850"
                        + "7257196073324816341282215626935164930077468"
                        + "5999131251387556761167658937349436378464220"
                        + "4831804147777472146628148336776639855791417"
                        + "3849903041999943901924899580268176393595653"
                        + "7357080543898614581363167420619163047562600"
                        + "6155574020606891195960345238780709194499010"
                        + "43652862954645301");
                break;
            case 4096:
                p = new BigInteger("2985635754414679487171962796211911"
                        + "1563710734938215274736352092606404045130913"
                        + "2477365484439939846705721840432140066578525"
                        + "0762327458086280430118434094733412377416194"
                        + "8736124795243564050755767519346747209606612"
                        + "5835460937739428885308798309679495432910469"
                        + "0294757621321446003970767164933974474924664"
                        + "1513767092845098947552598109657871041666676"
                        + "2945573325433283821164032766425479703026349"
                        + "9433641551427112483593214628620450175257586"
                        + "4350119143877183562692754400346175237007314"
                        + "7121580349193179272551363894896336921717843"
                        + "3734726842184251708799134654802475890197293"
                        + "9094908310578403843742664173424031260840446"
                        + "591633359364559754200663");
                q = new BigInteger("2279248439141087793789384816271625"
                        + "1304008816573950275844533962181244003563987"
                        + "6638461665174020058827698592331066726709304"
                        + "9231319346136709972639455506783245161859951"
                        + "6191872757335765533547033659834427437142631"
                        + "3801232751161907082392011429712327250253948"
                        + "6012497852063361866175243227579880020724881"
                        + "9393797645220239009219998518884396282407710"
                        + "7199202450846395844337846503427790307364624"
                        + "5124871273035872938616425951596065309519651"
                        + "1519189356431513094684173807318945903212527"
                        + "7712469749366620048658571121822171067675915"
                        + "5479178304648399924549334007222294762969503"
                        + "5341584429803583589276956979963609078497238"
                        + "760757619468018224491053");
                break;
            case 8192:
                p = new BigInteger("9821669838446774374944535804569858"
                        + "0553278885576950130485823829973470553571905"
                        + "3014418421996241500307589880457361653957913"
                        + "9176499436767288125182942994089196450118944"
                        + "8701794862752733776161684616570463744619126"
                        + "4981622564763630694110472008409561205704867"
                        + "0221819623405201369630462487520858670679048"
                        + "5854008441429858453634949980424333056803703"
                        + "1205609490778445762604050796894221725977551"
                        + "1428887194691696420765173256600200430067305"
                        + "4364524177041858044598166859757042904625691"
                        + "4292728453597609683799189454690202563236931"
                        + "8171122071288244573793276051041975005528757"
                        + "0228306442708182141334279133965507583927772"
                        + "9244311696220253059281524393613278272067808"
                        + "7017494446447670799055720358621918361716353"
                        + "5018317015764698318012095108914870478138809"
                        + "8204738169777192718869484177321870413838036"
                        + "8149216482968887382371881239714335470844573"
                        + "1862934371951394070111726593305334971041399"
                        + "5517260339034138718517336990212463882142363"
                        + "9154412320743552301967162100734381046548816"
                        + "3883737645359595416600487444018399886391071"
                        + "3777667222706059170707223589163679915863781"
                        + "4662302526078720977228426750718207481384357"
                        + "7918717041190413457052439016978578217755022"
                        + "7370720979516554707297685239584071755267452"
                        + "6021894842754355160100506065457679069228273"
                        + "95209345267367982516553449135291473361");
                q = new BigInteger("7902448465953646210110784092684896"
                        + "0265474424590294110174550047938700740921014"
                        + "1981650823416127449143596912363210790070524"
                        + "2903784112701128957948996730263815210531364"
                        + "0489145287401377007608600217628773627723381"
                        + "1194123533939872283952535576847014977682278"
                        + "9332064706645169741712060131540562788886577"
                        + "3762235020990267901959745687867018811088495"
                        + "3716021011509120447248882358515954471433808"
                        + "2782236662758287959413069553620728137831579"
                        + "2321174813204514354999978428741310035945405"
                        + "0226661395731921098764192439072425262100813"
                        + "9732949866553839713092238096261034339815187"
                        + "2832617055364163276140160068136296115910569"
                        + "9466440903693740716929166334256441926903849"
                        + "1082968246155177124035336609654226388424434"
                        + "5775783323612758615407928446164631651292743"
                        + "8428509642959278732826297890909454571009075"
                        + "7836191622138731918099379467912681177757761"
                        + "6141378131042432093843778753846726589215845"
                        + "7402160146427434508515156204064224022904659"
                        + "8645441448874409852211668374267341177082462"
                        + "7341410218867175406105046487057429530801973"
                        + "0931082058719258230993681115780999537424968"
                        + "2385515792331573549935317407789344892257264"
                        + "7464569110078675090194686816764429827739815"
                        + "0566036514181547634372488184242167294602000"
                        + "8232780963578241583529875079397308150506597"
                        + "37190564909892937290776929541076192569");
                break;
            default:
                throw new AssertionError("Unknown keySize " + keySize);
        }

        n = p.multiply(q);

        // phi = (p - 1) * (q - 1) must be relative prime to e
        // otherwise RSA just won't work ;-)
        BigInteger p1 = p.subtract(BigInteger.ONE);
        BigInteger q1 = q.subtract(BigInteger.ONE);
        BigInteger phi = p1.multiply(q1);
        // generate new p and q until they work. typically
        // the first try will succeed when using F4
        if (e.gcd(phi).equals(BigInteger.ONE) == false) {
            throw new AssertionError("Should not happen");
        }

        // private exponent d is the inverse of e mod phi
        BigInteger d = e.modInverse(phi);

        // 1st prime exponent pe = d mod (p - 1)
        BigInteger pe = d.mod(p1);
        // 2nd prime exponent qe = d mod (q - 1)
        BigInteger qe = d.mod(q1);

        // crt coefficient coeff is the inverse of q mod p
        BigInteger coeff = q.modInverse(p);

        try {
            PublicKey publicKey = new RSAPublicKeyImpl(rsaId, n, e);
            PrivateKey privateKey = new RSAPrivateCrtKeyImpl(
                    rsaId, n, e, d, p, q, pe, qe, coeff);
            return new KeyPair(publicKey, privateKey);
        } catch (InvalidKeyException exc) {
            // invalid key exception only thrown for keys < 512 bit,
            // will not happen here
            throw new RuntimeException(exc);
        }
    }
}
