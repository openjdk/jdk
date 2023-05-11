/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @modules java.base/sun.security.util
 */

import java.io.*;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.HexFormat;

import sun.security.util.*;

public class TestLMS {
    static final String ALG = "HSS/LMS";
    static final String OID = "1.2.840.113549.1.9.16.3.17";

    public static void main(String[] args) throws Exception {
        // RFC 8554
        if (!kat1()) {
            throw new RuntimeException("kat1 failed");
        }
        if (!kat2()) {
            throw new RuntimeException("kat2 failed");
        }

        // Additional Parameter sets for LMS Hash-Based Signatures (fluhrer)
        if (!katf1()) {
            throw new RuntimeException("katf1 failed");
        }
        if (!katf2()) {
            throw new RuntimeException("katf2 failed");
        }
        if (!katf3()) {
            throw new RuntimeException("katf3 failed");
        }

        // https://github.com/usnistgov/ACVP-Server/blob/master/gen-val/json-files/LMS-sigVer-1.0/prompt.json
        // These are LMS vectors, so you have to prepend 00000001
        // to the public key and 00000000 to the signature to make them HSS.
        if (!kat11()) {
            throw new RuntimeException("kat11 failed");
        }
        if (!kat12()) {
            throw new RuntimeException("kat12 failed");
        }
        if (!kat13()) {
            throw new RuntimeException("kat13 failed");
        }
        if (!kat14()) {
            throw new RuntimeException("kat14 failed");
        }
        if (!kat15()) { // bad vector: false positive
            throw new RuntimeException("kat15 failed");
        }
        if (!kat26()) { // bad vector: false positive
            throw new RuntimeException("kat26 failed");
        }
        if (!kat27()) { // bad vector: false positive
            throw new RuntimeException("kat27 failed");
        }
        if (!kat28()) {
            throw new RuntimeException("kat28 failed");
        }
        if (!kat29()) {
            throw new RuntimeException("kat29 failed");
        }
        if (!kat210()) {
            throw new RuntimeException("kat210 failed");
        }
        if (!testProviderException()) {
            throw new RuntimeException("testProviderException failed");
        }
        if (!serializeTest()) {
            throw new RuntimeException("serializeTest failed");
        }

        System.out.println("All tests passed");
    }

    static boolean kat1() throws Exception {
        // RFC 8554 Test Case 1
        var pk = decode("""
                00000002
                00000005
                00000004
                61a5d57d37f5e46bfb7520806b07a1b8
                50650e3b31fe4a773ea29a07f09cf2ea
                30e579f0df58ef8e298da0434cb2b878
                """);
        var msg = decode("""
                54686520706f77657273206e6f742064
                656c65676174656420746f2074686520
                556e6974656420537461746573206279
                2074686520436f6e737469747574696f
                6e2c206e6f722070726f686962697465
                6420627920697420746f207468652053
                74617465732c20617265207265736572
                76656420746f20746865205374617465
                7320726573706563746976656c792c20
                6f7220746f207468652070656f706c65
                2e0a""");
        var sig = decode("""
                00000001
                00000005
                00000004
                d32b56671d7eb98833c49b433c272586
                bc4a1c8a8970528ffa04b966f9426eb9
                965a25bfd37f196b9073f3d4a232feb6
                9128ec45146f86292f9dff9610a7bf95
                a64c7f60f6261a62043f86c70324b770
                7f5b4a8a6e19c114c7be866d488778a0
                e05fd5c6509a6e61d559cf1a77a970de
                927d60c70d3de31a7fa0100994e162a2
                582e8ff1b10cd99d4e8e413ef469559f
                7d7ed12c838342f9b9c96b83a4943d16
                81d84b15357ff48ca579f19f5e71f184
                66f2bbef4bf660c2518eb20de2f66e3b
                14784269d7d876f5d35d3fbfc7039a46
                2c716bb9f6891a7f41ad133e9e1f6d95
                60b960e7777c52f060492f2d7c660e14
                71e07e72655562035abc9a701b473ecb
                c3943c6b9c4f2405a3cb8bf8a691ca51
                d3f6ad2f428bab6f3a30f55dd9625563
                f0a75ee390e385e3ae0b906961ecf41a
                e073a0590c2eb6204f44831c26dd768c
                35b167b28ce8dc988a3748255230cef9
                9ebf14e730632f27414489808afab1d1
                e783ed04516de012498682212b078105
                79b250365941bcc98142da13609e9768
                aaf65de7620dabec29eb82a17fde35af
                15ad238c73f81bdb8dec2fc0e7f93270
                1099762b37f43c4a3c20010a3d72e2f6
                06be108d310e639f09ce7286800d9ef8
                a1a40281cc5a7ea98d2adc7c7400c2fe
                5a101552df4e3cccfd0cbf2ddf5dc677
                9cbbc68fee0c3efe4ec22b83a2caa3e4
                8e0809a0a750b73ccdcf3c79e6580c15
                4f8a58f7f24335eec5c5eb5e0cf01dcf
                4439424095fceb077f66ded5bec73b27
                c5b9f64a2a9af2f07c05e99e5cf80f00
                252e39db32f6c19674f190c9fbc506d8
                26857713afd2ca6bb85cd8c107347552
                f30575a5417816ab4db3f603f2df56fb
                c413e7d0acd8bdd81352b2471fc1bc4f
                1ef296fea1220403466b1afe78b94f7e
                cf7cc62fb92be14f18c2192384ebceaf
                8801afdf947f698ce9c6ceb696ed70e9
                e87b0144417e8d7baf25eb5f70f09f01
                6fc925b4db048ab8d8cb2a661ce3b57a
                da67571f5dd546fc22cb1f97e0ebd1a6
                5926b1234fd04f171cf469c76b884cf3
                115cce6f792cc84e36da58960c5f1d76
                0f32c12faef477e94c92eb75625b6a37
                1efc72d60ca5e908b3a7dd69fef02491
                50e3eebdfed39cbdc3ce9704882a2072
                c75e13527b7a581a556168783dc1e975
                45e31865ddc46b3c957835da252bb732
                8d3ee2062445dfb85ef8c35f8e1f3371
                af34023cef626e0af1e0bc017351aae2
                ab8f5c612ead0b729a1d059d02bfe18e
                fa971b7300e882360a93b025ff97e9e0
                eec0f3f3f13039a17f88b0cf808f4884
                31606cb13f9241f40f44e537d302c64a
                4f1f4ab949b9feefadcb71ab50ef27d6
                d6ca8510f150c85fb525bf25703df720
                9b6066f09c37280d59128d2f0f637c7d
                7d7fad4ed1c1ea04e628d221e3d8db77
                b7c878c9411cafc5071a34a00f4cf077
                38912753dfce48f07576f0d4f94f42c6
                d76f7ce973e9367095ba7e9a3649b7f4
                61d9f9ac1332a4d1044c96aefee67676
                401b64457c54d65fef6500c59cdfb69a
                f7b6dddfcb0f086278dd8ad0686078df
                b0f3f79cd893d314168648499898fbc0
                ced5f95b74e8ff14d735cdea968bee74
                00000005
                d8b8112f9200a5e50c4a262165bd342c
                d800b8496810bc716277435ac376728d
                129ac6eda839a6f357b5a04387c5ce97
                382a78f2a4372917eefcbf93f63bb591
                12f5dbe400bd49e4501e859f885bf073
                6e90a509b30a26bfac8c17b5991c157e
                b5971115aa39efd8d564a6b90282c316
                8af2d30ef89d51bf14654510a12b8a14
                4cca1848cf7da59cc2b3d9d0692dd2a2
                0ba3863480e25b1b85ee860c62bf5136
                00000005
                00000004
                d2f14ff6346af964569f7d6cb880a1b6
                6c5004917da6eafe4d9ef6c6407b3db0
                e5485b122d9ebe15cda93cfec582d7ab
                0000000a
                00000004
                0703c491e7558b35011ece3592eaa5da
                4d918786771233e8353bc4f62323185c
                95cae05b899e35dffd71705470620998
                8ebfdf6e37960bb5c38d7657e8bffeef
                9bc042da4b4525650485c66d0ce19b31
                7587c6ba4bffcc428e25d08931e72dfb
                6a120c5612344258b85efdb7db1db9e1
                865a73caf96557eb39ed3e3f426933ac
                9eeddb03a1d2374af7bf771855774562
                37f9de2d60113c23f846df26fa942008
                a698994c0827d90e86d43e0df7f4bfcd
                b09b86a373b98288b7094ad81a0185ac
                100e4f2c5fc38c003c1ab6fea479eb2f
                5ebe48f584d7159b8ada03586e65ad9c
                969f6aecbfe44cf356888a7b15a3ff07
                4f771760b26f9c04884ee1faa329fbf4
                e61af23aee7fa5d4d9a5dfcf43c4c26c
                e8aea2ce8a2990d7ba7b57108b47dabf
                beadb2b25b3cacc1ac0cef346cbb90fb
                044beee4fac2603a442bdf7e507243b7
                319c9944b1586e899d431c7f91bcccc8
                690dbf59b28386b2315f3d36ef2eaa3c
                f30b2b51f48b71b003dfb08249484201
                043f65f5a3ef6bbd61ddfee81aca9ce6
                0081262a00000480dcbc9a3da6fbef5c
                1c0a55e48a0e729f9184fcb1407c3152
                9db268f6fe50032a363c9801306837fa
                fabdf957fd97eafc80dbd165e435d0e2
                dfd836a28b354023924b6fb7e48bc0b3
                ed95eea64c2d402f4d734c8dc26f3ac5
                91825daef01eae3c38e3328d00a77dc6
                57034f287ccb0f0e1c9a7cbdc828f627
                205e4737b84b58376551d44c12c3c215
                c812a0970789c83de51d6ad787271963
                327f0a5fbb6b5907dec02c9a90934af5
                a1c63b72c82653605d1dcce51596b3c2
                b45696689f2eb382007497557692caac
                4d57b5de9f5569bc2ad0137fd47fb47e
                664fcb6db4971f5b3e07aceda9ac130e
                9f38182de994cff192ec0e82fd6d4cb7
                f3fe00812589b7a7ce51544045643301
                6b84a59bec6619a1c6c0b37dd1450ed4
                f2d8b584410ceda8025f5d2d8dd0d217
                6fc1cf2cc06fa8c82bed4d944e71339e
                ce780fd025bd41ec34ebff9d4270a322
                4e019fcb444474d482fd2dbe75efb203
                89cc10cd600abb54c47ede93e08c114e
                db04117d714dc1d525e11bed8756192f
                929d15462b939ff3f52f2252da2ed64d
                8fae88818b1efa2c7b08c8794fb1b214
                aa233db3162833141ea4383f1a6f120b
                e1db82ce3630b3429114463157a64e91
                234d475e2f79cbf05e4db6a9407d72c6
                bff7d1198b5c4d6aad2831db61274993
                715a0182c7dc8089e32c8531deed4f74
                31c07c02195eba2ef91efb5613c37af7
                ae0c066babc69369700e1dd26eddc0d2
                16c781d56e4ce47e3303fa73007ff7b9
                49ef23be2aa4dbf25206fe45c20dd888
                395b2526391a724996a44156beac8082
                12858792bf8e74cba49dee5e8812e019
                da87454bff9e847ed83db07af3137430
                82f880a278f682c2bd0ad6887cb59f65
                2e155987d61bbf6a88d36ee93b6072e6
                656d9ccbaae3d655852e38deb3a2dcf8
                058dc9fb6f2ab3d3b3539eb77b248a66
                1091d05eb6e2f297774fe6053598457c
                c61908318de4b826f0fc86d4bb117d33
                e865aa805009cc2918d9c2f840c4da43
                a703ad9f5b5806163d7161696b5a0adc
                00000005
                d5c0d1bebb06048ed6fe2ef2c6cef305
                b3ed633941ebc8b3bec9738754cddd60
                e1920ada52f43d055b5031cee6192520
                d6a5115514851ce7fd448d4a39fae2ab
                2335b525f484e9b40d6a4a969394843b
                dcf6d14c48e8015e08ab92662c05c6e9
                f90b65a7a6201689999f32bfd368e5e3
                ec9cb70ac7b8399003f175c40885081a
                09ab3034911fe125631051df0408b394
                6b0bde790911e8978ba07dd56c73e7ee
                """);

        return verify(pk, sig, msg);
    }

    static boolean kat2() throws Exception {
        // RFC 8554 Test Case 2
        var pk = decode("""
                00000002
                00000006
                00000003
                d08fabd4a2091ff0a8cb4ed834e74534
                32a58885cd9ba0431235466bff9651c6
                c92124404d45fa53cf161c28f1ad5a8e
                """);
        var msg = decode("""
                54686520656e756d65726174696f6e20
                696e2074686520436f6e737469747574
                696f6e2c206f66206365727461696e20
                7269676874732c207368616c6c206e6f
                7420626520636f6e7374727565642074
                6f2064656e79206f7220646973706172
                616765206f7468657273207265746169
                6e6564206279207468652070656f706c
                652e0a""");
        var sig = decode("""
                00000001
                00000003
                00000003
                3d46bee8660f8f215d3f96408a7a64cf
                1c4da02b63a55f62c666ef5707a914ce
                0674e8cb7a55f0c48d484f31f3aa4af9
                719a74f22cf823b94431d01c926e2a76
                bb71226d279700ec81c9e95fb11a0d10
                d065279a5796e265ae17737c44eb8c59
                4508e126a9a7870bf4360820bdeb9a01
                d9693779e416828e75bddd7d8c70d50a
                0ac8ba39810909d445f44cb5bb58de73
                7e60cb4345302786ef2c6b14af212ca1
                9edeaa3bfcfe8baa6621ce88480df237
                1dd37add732c9de4ea2ce0dffa53c926
                49a18d39a50788f4652987f226a1d481
                68205df6ae7c58e049a25d4907edc1aa
                90da8aa5e5f7671773e941d805536021
                5c6b60dd35463cf2240a9c06d694e9cb
                54e7b1e1bf494d0d1a28c0d31acc7516
                1f4f485dfd3cb9578e836ec2dc722f37
                ed30872e07f2b8bd0374eb57d22c614e
                09150f6c0d8774a39a6e168211035dc5
                2988ab46eaca9ec597fb18b4936e66ef
                2f0df26e8d1e34da28cbb3af75231372
                0c7b345434f72d65314328bbb030d0f0
                f6d5e47b28ea91008fb11b05017705a8
                be3b2adb83c60a54f9d1d1b2f476f9e3
                93eb5695203d2ba6ad815e6a111ea293
                dcc21033f9453d49c8e5a6387f588b1e
                a4f706217c151e05f55a6eb7997be09d
                56a326a32f9cba1fbe1c07bb49fa04ce
                cf9df1a1b815483c75d7a27cc88ad1b1
                238e5ea986b53e087045723ce16187ed
                a22e33b2c70709e53251025abde89396
                45fc8c0693e97763928f00b2e3c75af3
                942d8ddaee81b59a6f1f67efda0ef81d
                11873b59137f67800b35e81b01563d18
                7c4a1575a1acb92d087b517a8833383f
                05d357ef4678de0c57ff9f1b2da61dfd
                e5d88318bcdde4d9061cc75c2de3cd47
                40dd7739ca3ef66f1930026f47d9ebaa
                713b07176f76f953e1c2e7f8f271a6ca
                375dbfb83d719b1635a7d8a138919579
                44b1c29bb101913e166e11bd5f34186f
                a6c0a555c9026b256a6860f4866bd6d0
                b5bf90627086c6149133f8282ce6c9b3
                622442443d5eca959d6c14ca8389d12c
                4068b503e4e3c39b635bea245d9d05a2
                558f249c9661c0427d2e489ca5b5dde2
                20a90333f4862aec793223c781997da9
                8266c12c50ea28b2c438e7a379eb106e
                ca0c7fd6006e9bf612f3ea0a454ba3bd
                b76e8027992e60de01e9094fddeb3349
                883914fb17a9621ab929d970d101e45f
                8278c14b032bcab02bd15692d21b6c5c
                204abbf077d465553bd6eda645e6c306
                5d33b10d518a61e15ed0f092c3222628
                1a29c8a0f50cde0a8c66236e29c2f310
                a375cebda1dc6bb9a1a01dae6c7aba8e
                bedc6371a7d52aacb955f83bd6e4f84d
                2949dcc198fb77c7e5cdf6040b0f84fa
                f82808bf985577f0a2acf2ec7ed7c0b0
                ae8a270e951743ff23e0b2dd12e9c3c8
                28fb5598a22461af94d568f29240ba28
                20c4591f71c088f96e095dd98beae456
                579ebbba36f6d9ca2613d1c26eee4d8c
                73217ac5962b5f3147b492e8831597fd
                89b64aa7fde82e1974d2f6779504dc21
                435eb3109350756b9fdabe1c6f368081
                bd40b27ebcb9819a75d7df8bb07bb05d
                b1bab705a4b7e37125186339464ad8fa
                aa4f052cc1272919fde3e025bb64aa8e
                0eb1fcbfcc25acb5f718ce4f7c2182fb
                393a1814b0e942490e52d3bca817b2b2
                6e90d4c9b0cc38608a6cef5eb153af08
                58acc867c9922aed43bb67d7b33acc51
                9313d28d41a5c6fe6cf3595dd5ee63f0
                a4c4065a083590b275788bee7ad875a7
                f88dd73720708c6c6c0ecf1f43bbaada
                e6f208557fdc07bd4ed91f88ce4c0de8
                42761c70c186bfdafafc444834bd3418
                be4253a71eaf41d718753ad07754ca3e
                ffd5960b0336981795721426803599ed
                5b2b7516920efcbe32ada4bcf6c73bd2
                9e3fa152d9adeca36020fdeeee1b7395
                21d3ea8c0da497003df1513897b0f547
                94a873670b8d93bcca2ae47e64424b74
                23e1f078d9554bb5232cc6de8aae9b83
                fa5b9510beb39ccf4b4e1d9c0f19d5e1
                7f58e5b8705d9a6837a7d9bf99cd1338
                7af256a8491671f1f2f22af253bcff54
                b673199bdb7d05d81064ef05f80f0153
                d0be7919684b23da8d42ff3effdb7ca0
                985033f389181f47659138003d712b5e
                c0a614d31cc7487f52de8664916af79c
                98456b2c94a8038083db55391e347586
                2250274a1de2584fec975fb09536792c
                fbfcf6192856cc76eb5b13dc4709e2f7
                301ddff26ec1b23de2d188c999166c74
                e1e14bbc15f457cf4e471ae13dcbdd9c
                50f4d646fc6278e8fe7eb6cb5c94100f
                a870187380b777ed19d7868fd8ca7ceb
                7fa7d5cc861c5bdac98e7495eb0a2cee
                c1924ae979f44c5390ebedddc65d6ec1
                1287d978b8df064219bc5679f7d7b264
                a76ff272b2ac9f2f7cfc9fdcfb6a5142
                8240027afd9d52a79b647c90c2709e06
                0ed70f87299dd798d68f4fadd3da6c51
                d839f851f98f67840b964ebe73f8cec4
                1572538ec6bc131034ca2894eb736b3b
                da93d9f5f6fa6f6c0f03ce43362b8414
                940355fb54d3dfdd03633ae108f3de3e
                bc85a3ff51efeea3bc2cf27e1658f178
                9ee612c83d0f5fd56f7cd071930e2946
                beeecaa04dccea9f97786001475e0294
                bc2852f62eb5d39bb9fbeef75916efe4
                4a662ecae37ede27e9d6eadfdeb8f8b2
                b2dbccbf96fa6dbaf7321fb0e701f4d4
                29c2f4dcd153a2742574126e5eaccc77
                686acf6e3ee48f423766e0fc466810a9
                05ff5453ec99897b56bc55dd49b99114
                2f65043f2d744eeb935ba7f4ef23cf80
                cc5a8a335d3619d781e7454826df720e
                ec82e06034c44699b5f0c44a8787752e
                057fa3419b5bb0e25d30981e41cb1361
                322dba8f69931cf42fad3f3bce6ded5b
                8bfc3d20a2148861b2afc14562ddd27f
                12897abf0685288dcc5c4982f8260268
                46a24bf77e383c7aacab1ab692b29ed8
                c018a65f3dc2b87ff619a633c41b4fad
                b1c78725c1f8f922f6009787b1964247
                df0136b1bc614ab575c59a16d089917b
                d4a8b6f04d95c581279a139be09fcf6e
                98a470a0bceca191fce476f9370021cb
                c05518a7efd35d89d8577c990a5e1996
                1ba16203c959c91829ba7497cffcbb4b
                294546454fa5388a23a22e805a5ca35f
                956598848bda678615fec28afd5da61a
                00000006
                b326493313053ced3876db9d23714818
                1b7173bc7d042cefb4dbe94d2e58cd21
                a769db4657a103279ba8ef3a629ca84e
                e836172a9c50e51f45581741cf808315
                0b491cb4ecbbabec128e7c81a46e62a6
                7b57640a0a78be1cbf7dd9d419a10cd8
                686d16621a80816bfdb5bdc56211d72c
                a70b81f1117d129529a7570cf79cf52a
                7028a48538ecdd3b38d3d5d62d262465
                95c4fb73a525a5ed2c30524ebb1d8cc8
                2e0c19bc4977c6898ff95fd3d310b0ba
                e71696cef93c6a552456bf96e9d075e3
                83bb7543c675842bafbfc7cdb88483b3
                276c29d4f0a341c2d406e40d4653b7e4
                d045851acf6a0a0ea9c710b805cced46
                35ee8c107362f0fc8d80c14d0ac49c51
                6703d26d14752f34c1c0d2c4247581c1
                8c2cf4de48e9ce949be7c888e9caebe4
                a415e291fd107d21dc1f084b11582082
                49f28f4f7c7e931ba7b3bd0d824a4570
                00000005
                00000004
                215f83b7ccb9acbcd08db97b0d04dc2b
                a1cd035833e0e90059603f26e07ad2aa
                d152338e7a5e5984bcd5f7bb4eba40b7
                00000004
                00000004
                0eb1ed54a2460d512388cad533138d24
                0534e97b1e82d33bd927d201dfc24ebb
                11b3649023696f85150b189e50c00e98
                850ac343a77b3638319c347d7310269d
                3b7714fa406b8c35b021d54d4fdada7b
                9ce5d4ba5b06719e72aaf58c5aae7aca
                057aa0e2e74e7dcfd17a0823429db629
                65b7d563c57b4cec942cc865e29c1dad
                83cac8b4d61aacc457f336e6a10b6632
                3f5887bf3523dfcadee158503bfaa89d
                c6bf59daa82afd2b5ebb2a9ca6572a60
                67cee7c327e9039b3b6ea6a1edc7fdc3
                df927aade10c1c9f2d5ff446450d2a39
                98d0f9f6202b5e07c3f97d2458c69d3c
                8190643978d7a7f4d64e97e3f1c4a08a
                7c5bc03fd55682c017e2907eab07e5bb
                2f190143475a6043d5e6d5263471f4ee
                cf6e2575fbc6ff37edfa249d6cda1a09
                f797fd5a3cd53a066700f45863f04b6c
                8a58cfd341241e002d0d2c0217472bf1
                8b636ae547c1771368d9f317835c9b0e
                f430b3df4034f6af00d0da44f4af7800
                bc7a5cf8a5abdb12dc718b559b74cab9
                090e33cc58a955300981c420c4da8ffd
                67df540890a062fe40dba8b2c1c548ce
                d22473219c534911d48ccaabfb71bc71
                862f4a24ebd376d288fd4e6fb06ed870
                5787c5fedc813cd2697e5b1aac1ced45
                767b14ce88409eaebb601a93559aae89
                3e143d1c395bc326da821d79a9ed41dc
                fbe549147f71c092f4f3ac522b5cc572
                90706650487bae9bb5671ecc9ccc2ce5
                1ead87ac01985268521222fb9057df7e
                d41810b5ef0d4f7cc67368c90f573b1a
                c2ce956c365ed38e893ce7b2fae15d36
                85a3df2fa3d4cc098fa57dd60d2c9754
                a8ade980ad0f93f6787075c3f680a2ba
                1936a8c61d1af52ab7e21f416be09d2a
                8d64c3d3d8582968c2839902229f85ae
                e297e717c094c8df4a23bb5db658dd37
                7bf0f4ff3ffd8fba5e383a48574802ed
                545bbe7a6b4753533353d73706067640
                135a7ce517279cd683039747d218647c
                86e097b0daa2872d54b8f3e508598762
                9547b830d8118161b65079fe7bc59a99
                e9c3c7380e3e70b7138fe5d9be255150
                2b698d09ae193972f27d40f38dea264a
                0126e637d74ae4c92a6249fa103436d3
                eb0d4029ac712bfc7a5eacbdd7518d6d
                4fe903a5ae65527cd65bb0d4e9925ca2
                4fd7214dc617c150544e423f450c99ce
                51ac8005d33acd74f1bed3b17b7266a4
                a3bb86da7eba80b101e15cb79de9a207
                852cf91249ef480619ff2af8cabca831
                25d1faa94cbb0a03a906f683b3f47a97
                c871fd513e510a7a25f283b196075778
                496152a91c2bf9da76ebe089f4654877
                f2d586ae7149c406e663eadeb2b5c7e8
                2429b9e8cb4834c83464f079995332e4
                b3c8f5a72bb4b8c6f74b0d45dc6c1f79
                952c0b7420df525e37c15377b5f09843
                19c3993921e5ccd97e097592064530d3
                3de3afad5733cbe7703c5296263f7734
                2efbf5a04755b0b3c997c4328463e84c
                aa2de3ffdcd297baaaacd7ae646e44b5
                c0f16044df38fabd296a47b3a838a913
                982fb2e370c078edb042c84db34ce36b
                46ccb76460a690cc86c302457dd1cde1
                97ec8075e82b393d542075134e2a17ee
                70a5e187075d03ae3c853cff60729ba4
                00000005
                4de1f6965bdabc676c5a4dc7c35f97f8
                2cb0e31c68d04f1dad96314ff09e6b3d
                e96aeee300d1f68bf1bca9fc58e40323
                36cd819aaf578744e50d1357a0e42867
                04d341aa0a337b19fe4bc43c2e79964d
                4f351089f2e0e41c7c43ae0d49e7f404
                b0f75be80ea3af098c9752420a8ac0ea
                2bbb1f4eeba05238aef0d8ce63f0c6e5
                e4041d95398a6f7f3e0ee97cc1591849
                d4ed236338b147abde9f51ef9fd4e1c1
                """);

        return verify(pk, sig, msg);
    }

    static boolean katf1() throws Exception {
        var pk = decode("""
00000001
0000000a
00000008
202122232425262728292a2b2c2d2e2f
2c571450aed99cfb4f4ac285da148827
96618314508b12d2
                """);
        var msg = decode("""
54657374206d65737361676520666f72
205348413235362d3139320a
                """);
        var sig = decode("""
00000000
00000005
00000008
                0b5040a18c1b5cabcbc85b047402ec62
                94a30dd8da8fc3da
                e13b9f0875f09361dc77fcc4481ea463
                c073716249719193
                614b835b4694c059f12d3aedd34f3db9
                3f3580fb88743b8b
                3d0648c0537b7a50e433d7ea9d6672ff
                fc5f42770feab4f9
                8eb3f3b23fd2061e4d0b38f832860ae7
                6673ad1a1a52a900
                5dcf1bfb56fe16ff723627612f9a48f7
                90f3c47a67f870b8
                1e919d99919c8db48168838cece0abfb
                683da48b9209868b
                e8ec10c63d8bf80d36498dfc205dc45d
                0dd870572d6d8f1d
                90177cf5137b8bbf7bcb67a46f86f26c
                fa5a44cbcaa4e18d
                a099a98b0b3f96d5ac8ac375d8da2a7c
                248004ba11d7ac77
                5b9218359cddab4cf8ccc6d54cb7e1b3
                5a36ddc9265c0870
                63d2fc6742a7177876476a324b03295b
                fed99f2eaf1f3897
                0583c1b2b616aad0f31cd7a4b1bb0a51
                e477e94a01bbb4d6
                f8866e2528a159df3d6ce244d2b6518d
                1f0212285a3c2d4a
                927054a1e1620b5b02aab0c8c10ed48a
                e518ea73cba81fcf
                ff88bff461dac51e7ab4ca75f47a6259
                d24820b9995792d1
                39f61ae2a8186ae4e3c9bfe0af2cc717
                f424f41aa67f03fa
                edb0665115f2067a46843a4cbbd297d5
                e83bc1aafc18d1d0
                3b3d894e8595a6526073f02ab0f08b99
                fd9eb208b59ff631
                7e5545e6f9ad5f9c183abd043d5acd6e
                b2dd4da3f02dbc31
                67b468720a4b8b92ddfe7960998bb7a0
                ecf2a26a37598299
                413f7b2aecd39a30cec527b4d9710c44
                73639022451f50d0
                1c0457125da0fa4429c07dad859c846c
                bbd93ab5b91b01bc
                770b089cfede6f651e86dd7c15989c8b
                5321dea9ca608c71
                fd862323072b827cee7a7e28e4e2b999
                647233c3456944bb
                7aef9187c96b3f5b79fb98bc76c3574d
                d06f0e95685e5b3a
                ef3a54c4155fe3ad817749629c30adbe
                897c4f4454c86c49
                0000000a
                e9ca10eaa811b22ae07fb195e3590a33
                4ea64209942fbae3
                38d19f152182c807d3c40b189d3fcbea
                942f44682439b191
                332d33ae0b761a2a8f984b56b2ac2fd4
                ab08223a69ed1f77
                19c7aa7e9eee96504b0e60c6bb5c942d
                695f0493eb25f80a
                5871cffd131d0e04ffe5065bc7875e82
                d34b40b69dd9f3c1
                """);

        try {
            return verify(pk, sig, msg);
        } catch (InvalidKeySpecException ex) {
            // SHA256_M24 not supported
        }
        return true;
    }

    static boolean katf2() throws Exception {
        var pk = decode("""
00000001
00000014
00000010
505152535455565758595a5b5c5d5e5f
db54a4509901051c01e26d9990e55034
7986da87924ff0b1
                """);
        var msg = decode("""
54657374206d65737361676520666f72
205348414b453235362d3139320a
                """);
        var sig = decode("""
00000000
00000006
00000010
                84219da9ce9fffb16edb94527c6d1056
                5587db28062deac4
                208e62fc4fbe9d85deb3c6bd2c01640a
                ccb387d8a6093d68
                511234a6a1a50108091c034cb1777e02
                b5df466149a66969
                a498e4200c0a0c1bf5d100cdb97d2dd4
                0efd3cada278acc5
                a570071a043956112c6deebd1eb3a7b5
                6f5f6791515a7b5f
                fddb0ec2d9094bfbc889ea15c3c7b9be
                a953efb75ed648f5
                35b9acab66a2e9631e426e4e99b733ca
                a6c55963929b77fe
                c54a7e703d8162e736875cb6a455d4a9
                015c7a6d8fd5fe75
                e402b47036dc3770f4a1dd0a559cb478
                c7fb1726005321be
                9d1ac2de94d731ee4ca79cff454c811f
                46d11980909f047b
                2005e84b6e15378446b1ca691efe491e
                a98acc9d3c0f785c
                aba5e2eb3c306811c240ba2280292382
                7d582639304a1e97
                83ba5bc9d69d999a7db8f749770c3c04
                a152856dc726d806
                7921465b61b3f847b13b2635a45379e5
                adc6ff58a99b00e6
                0ac767f7f30175f9f7a140257e218be3
                07954b1250c9b419
                02c4fa7c90d8a592945c66e86a76defc
                b84500b55598a199
                0faaa10077c74c94895731585c8f900d
                e1a1c675bd8b0c18
                0ebe2b5eb3ef8019ece3e1ea7223eb79
                06a2042b6262b4aa
                25c4b8a05f205c8befeef11ceff12825
                08d71bc2a8cfa0a9
                9f73f3e3a74bb4b3c0d8ca2abd0e1c2c
                17dafe18b4ee2298
                e87bcfb1305b3c069e6d385569a4067e
                d547486dd1a50d6f
                4a58aab96e2fa883a9a39e1bd45541ee
                e94efc32faa9a94b
                e66dc8538b2dab05aee5efa6b3b2efb3
                fd020fe789477a93
                afff9a3e636dbba864a5bffa3e28d13d
                49bb597d94865bde
                88c4627f206ab2b465084d6b780666e9
                52f8710efd748bd0
                f1ae8f1035087f5028f14affcc5fffe3
                32121ae4f87ac5f1
                eac9062608c7d87708f1723f38b23237
                a4edf4b49a5cd3d7
                00000014
                dd4bdc8f928fb526f6fb7cdb944a7eba
                a7fb05d995b5721a
                27096a5007d82f79d063acd434a04e97
                f61552f7f81a9317
                b4ec7c87a5ed10c881928fc6ebce6dfc
                e9daae9cc9dba690
                7ca9a9dd5f9f573704d5e6cf22a43b04
                e64c1ffc7e1c442e
                cb495ba265f465c56291a902e62a461f
                6dfda232457fad14
                """);

        try {
            return verify(pk, sig, msg);
        } catch (InvalidKeySpecException ex) {
            // SHAKE_M24 not supported
        }
        return true;
    }

    static boolean katf3() throws Exception {
        var pk = decode("""
00000001
0000000f
0000000c
808182838485868788898a8b8c8d8e8f
9bb7faee411cae806c16a466c3191a8b
65d0ac31932bbf0c2d07c7a4a36379fe
                """);
        var msg = decode("""
54657374206d657361676520666f7220
5348414b453235362d3235360a
                """);
        var sig = decode("""
00000000
00000007
0000000c
                b82709f0f00e83759190996233d1ee4f
                4ec50534473c02ffa145e8ca2874e32b
                16b228118c62b96c9c77678b33183730
                debaade8fe607f05c6697bc971519a34
                1d69c00129680b67e75b3bd7d8aa5c8b
                71f02669d177a2a0eea896dcd1660f16
                864b302ff321f9c4b8354408d0676050
                4f768ebd4e545a9b0ac058c575078e6c
                1403160fb45450d61a9c8c81f6bd69bd
                fa26a16e12a265baf79e9e233eb71af6
                34ecc66dc88e10c6e0142942d4843f70
                a0242727bc5a2aabf7b0ec12a99090d8
                caeef21303f8ac58b9f200371dc9e41a
                b956e1a3efed9d4bbb38975b46c28d5f
                5b3ed19d847bd0a737177263cbc1a226
                2d40e80815ee149b6cce2714384c9b7f
                ceb3bbcbd25228dda8306536376f8793
                ecadd6020265dab9075f64c773ef97d0
                7352919995b74404cc69a6f3b469445c
                9286a6b2c9f6dc839be76618f053de76
                3da3571ef70f805c9cc54b8e501a98b9
                8c70785eeb61737eced78b0e380ded4f
                769a9d422786def59700eef3278017ba
                bbe5f9063b468ae0dd61d94f9f99d5cc
                36fbec4178d2bda3ad31e1644a2bcce2
                08d72d50a7637851aa908b94dc437612
                0d5beab0fb805e1945c41834dd6085e6
                db1a3aa78fcb59f62bde68236a10618c
                ff123abe64dae8dabb2e84ca705309c2
                ab986d4f8326ba0642272cb3904eb96f
                6f5e3bb8813997881b6a33cac0714e4b
                5e7a882ad87e141931f97d612b84e903
                e773139ae377f5ba19ac86198d485fca
                97742568f6ff758120a89bf19059b8a6
                bfe2d86b12778164436ab2659ba86676
                7fcc435584125fb7924201ee67b535da
                f72c5cb31f5a0b1d926324c26e67d4c3
                836e301aa09bae8fb3f91f1622b1818c
                cf440f52ca9b5b9b99aba8a6754aae2b
                967c4954fa85298ad9b1e74f27a46127
                c36131c8991f0cc2ba57a15d35c91cf8
                bc48e8e20d625af4e85d8f9402ec44af
                bd4792b924b839332a64788a7701a300
                94b9ec4b9f4b648f168bf457fbb3c959
                4fa87920b645e42aa2fecc9e21e000ca
                7d3ff914e15c40a8bc533129a7fd3952
                9376430f355aaf96a0a13d13f2419141
                b3cc25843e8c90d0e551a355dd90ad77
                0ea7255214ce11238605de2f000d2001
                04d0c3a3e35ae64ea10a3eff37ac7e95
                49217cdf52f307172e2f6c7a2a4543e1
                4314036525b1ad53eeaddf0e24b1f369
                14ed22483f2889f61e62b6fb78f5645b
                dbb02c9e5bf97db7a0004e87c2a55399
                b61958786c97bd52fa199c27f6bb4d68
                c4907933562755bfec5d4fb52f06c289
                d6e852cf6bc773ffd4c07ee2d6cc55f5
                7edcfbc8e8692a49ad47a121fe3c1b16
                cab1cc285faf6793ffad7a8c341a49c5
                d2dce7069e464cb90a00b2903648b23c
                81a68e21d748a7e7b1df8a593f3894b2
                477e8316947ca725d141135202a9442e
                1db33bbd390d2c04401c39b253b78ce2
                97b0e14755e46ec08a146d279c67af70
                de256890804d83d6ec5ca3286f1fca9c
                72abf6ef868e7f6eb0fddda1b040ecec
                9bbc69e2fd8618e9db3bdb0af13dda06
                c6617e95afa522d6a2552de15324d991
                19f55e9af11ae3d5614b564c642dbfec
                6c644198ce80d2433ac8ee738f9d825e
                0000000f
                71d585a35c3a908379f4072d070311db
                5d65b242b714bc5a756ba5e228abfa0d
                1329978a05d5e815cf4d74c1e547ec4a
                a3ca956ae927df8b29fb9fab3917a7a4
                ae61ba57e5342e9db12caf6f6dbc5253
                de5268d4b0c4ce4ebe6852f012b162fc
                1c12b9ffc3bcb1d3ac8589777655e22c
                d9b99ff1e4346fd0efeaa1da044692e7
                ad6bfc337db69849e54411df8920c228
                a2b7762c11e4b1c49efb74486d3931ea
                """);

        try {
            return verify(pk, sig, msg);
        } catch (InvalidKeySpecException ex) {
            // SHAKE_M24 not supported
        }
        return true;
    }

    static boolean kat11() throws Exception {
        // ACVP tgId 1, tcId 1
        var pk = decode("""
00000001
0000000A0000000666BE230DE39669DFB77133DEEC2DB8C476B5F2B82B260E7C984D10A239F1597C93943E53FCDADEB4
                """);
        var msg = decode("""
FCC308C74B9FD9F6CDB2CAB75E263348196FB78D7399B3051375DC74424ADAD794A71FAC96834CEFA24EAED1695CD312CF12AB679AA5801C6F9E4B978F7C457C8434143138035BC5D75133F3B522A45E0D6BF087C65E0BA8D957AFF2482340D64264F40EBBE07DC09A72C6A54E5A731F9193BBD05287E07842D012B6C4F5379E
                """);
        var sig = decode("""
00000000
80000005000000069910F8327FED62BA21EBD77A9B37EB231C0A33A4CF7DCFA3938DE94FF2D6AC471739D6F8E4DFDB4154EE1DAAAC46A10155D5F78E5761E180A0E8C8DBD3CB26882A303AABD233F1CF44C0CD958C82F0C124C80869DC723ADC10407249DD2D5756C153C3448B9FBBC001C82EF41AC309398209EF06889BC60F4FF281914A61C7314FE0D44D90C9CAAE60B4B979E4946BE378B75AA3C6869C350868487961BBB85D48F534DAFB6B5E1D98075E7DD5568AC32A50010DB326616F7EF088563573C9F25C9722196E5F4F50618CD41612E2FCB7656F0C610374A95D0B9E5F89B990CF5CE629D24684D20229528622AD383A6840992582E1BED481164AA441A76D59F147C4D1559FE071E44A090F2C14D33C28D50B45CD67CCBCF44B83891252B139218C1BE7826B5D35011F6DB99A0D85379B69B42F9DE9AFC09E31C5A583C1214F9DBA579D4E35FE989E78F89EF64C0F80AA76377FB7CBC244F98A74B0754FBEDCF9BC61FD5408CEE1B4D284ACEE20779C3877821DF4F009CB6D1C21FDB637826B9B5F1BB660F844C3EA3BFD6F556643B73B03DDF7CF5D7E8411919C0B35EC5A1256C9B51B220B4E686C21166AFA17473850C8E74F8FC8CE1A9D4DA243E7EC57D4BEC9B83121077183CF9D155FEA2D08D6F767956A8DCD11D26C5117EB8CBB5F83D1C76F39CDC129D3590874BDE650095448DB59B0349CE5C4E1105C9D8C2EEF0503225999BA95449BF21BBB484D6F1D5597CB801DE12581F1F8F26C57B133F67EE1C68C7F9363732188B7DCD403543FF7C0CA20F40B0A008A3466049196A9079B81B25C9BEEF27A8A2526EB56A871B2EFDFE3BAC5A8409AD270DCE8E5E431CD46949A6B2B0B096CB6A9BECE089A7C789CF342E970CF64CF6B49332D2A0FF274B47E848AF9912FBF49552BE6432AD7388A14C4CC4B5690099D45BE3D8FBEDF53BB8BB9745580B96BF67FEB41C13C624DE271452449007D72E8B5F6E0119ED34DBAD23D1E41AA588163B6AD9E80FD4CCC52C30156623A33E72EC89AC46ED528A450FD23D14B680D2137D24D0E6D89D58774A54ED94BE7A979A6D46056DF12DC14E2EE8F1403AD57B7D5513FCBE431A93FC0B14BBC7E68D8A9A15312B339F9B680F3494141798C43DC9EA8C1F456E6095116BE26277045894C00E9A4D46229C214308D87896C85770F5BC54E80A6527957B9EEC52283D3BB9F6C0DA53046845E733EF5290D33EE180CD5A08FEB0DE7E3CD013CF25524760A2977BBD375138A182D2C9BECDE3E81FB24E9997D760679364A894855AD167E25BD30F7932269E713AE7482D99DD21DB763175FD9B82BEE41BB9FF0781391A9446F1BB038AF8F1628162F3BE8FC3DC2A07A05B1D32E6CD1B1140BEE3F528AB82F932FC33B3D8EDE376834420EA569DB1017DC07CC5755185512229875AD1C353B3785B632B3168B410F0518802495BE1DC85CE0A228DD2461D003903742F859587F18CD229AE6DD9BD1A28F9CC32A937C60BAF5E90FB7FC5F6686BF8CF3BE3F4A18929D024A1DE29380149D61ECE0A96C0481B4621C3CFC67E199847A80AFF5559C1D405D9647AD42CA4E4D1F8F67746BF7FA36A00B50EA5C7E21DF5EB3DEA5E55B9E20B946DDDA8F675A35666210EB3FF3A1DD179C45294D60BA644572215502F529184A6F5684E927448BA8AA71EEBB928EF3A4CF930BADF77FE24F9FEED2816AE75C396067534F2F0D2CAF60B0F0C494E166E1C35899EC250C8E392A0548A832EA6BE0D8B1AE915714859F9FB1D911328FB20129D3C1BE3F40FD542C48E9C27FBB4D1462A0B609A6956A73500ABAE2FFA05A56BD1A74C1CECA72015C4348CDF375762E3F8FC650520DF25B53C618941E45C6DBADBCA80DF169E7B0875AEAD2BECA2184D85C3F63E8F754A0533E5C5AED49270FEE6D46A2BB838B365B8A7E4F1A3E550AFF594FD1CFEF963BD0204976378FB224E4A1931463F83986E1905FA6574C75025AC1096990547938C559BC116BDDDBD22C432891551D0F033DCB6C6856B68A3B22DECCF16D665756BFDA866561B21EF13D0E6A2B76799DC0BBE8160139DAC9BAB62B2DF31F5503776BB6650EB3C590B7DD2EE2B2981DFCB9EFCAFD50C014D1DBFCB032B802F77A33C9ECB11FE9E70D03398E415F1517DF23DAB1B0B75E47C825B44DB863BF5A4F4308AF9C93FE6AC1BAC550E8AD0361557C4D1ECF9FD05A74E9B94D196AFEBE11348DCD1B44B1C154835C4B0BEFBF3997CA6D7E759A9CC90A5712697E36DA10395DD40B292D5532114C63C6950140F44ED25DDDD7D03A5FEBE7B2A5D590650FA2B9A18F2447A04124DCDA2FE3ED7ACECEB25EAE0FE6166B00FCAE0DEC025B0B6F7B9773F4A925984D5F0FE85CEC57BB3D97E6E70FC0204635C4A52DB9F812B289A4CC4D7F65C7913750A5838F5DC706B4642357AB240D14B67628246C031AAC55C1BB57D20FC3A4240A0B8D3D54102EC4DE815419069A091387EDD9F61477C0DBD6D84334D00BDA18DD8776ACED25A2B3FB586CE6DA6E05A4320467D8DFA6086D4E7F38C7718BBF81C7504720053C96EEA023C7FC5E582E5E7DA0390702D51A557C665D07D9B2AA29459F7E2C46420875138ED41BBCD775765AD6D66ADA05FCB9B8BE77D8E210F81CCB266F2536111636A16870CA7EDD7063358DD32BF593FABDF8E9E5361D368ED844FA2BE8057E3AA7F6D8113E76E7026550411E8BF5D7F7633EA45BBAB5B0BA0150DB1ADA23405D1CF747F8C5AC275C6E087253F62F1CA699AB2BED3CB10953F0E4F3BD38323D00DFA9BAAA908A9A39132E071E108FAADFBABDB2548F843679821DB43F7C8E821659A0F1B05D2B5CF15A4E58EB545D9943B83BE1D039CC866C03A2694100463B8C8BE5A3058888E1CABA82F52FD9C61A873C5B39FA98360007B6DE5311FF01926EDAE33155F64363C2A37B265E18D56B8C6AE4B229284B02AA2874C13746FE9CF20EDBABE499E74E8EA57075D5CD2B6B9D4CAF4415A71F5DAB2A643BB079065ED844F4CCAAC2CAE60077226EF44C90B9E74A67E085B24BCFDD944945EEA7C68025FB84BAAFCBA2235FC9ACCB727A8AFDB3218C30FA785B1A36F6CCB6D4A3FD45834845E28D34F387CE3D46A72A1AF6DDE0183D32BC6A2252EC71AA05AAFF22D3D4958AFE4CDD6E02FEAF3CE92D447FA1B6C6D3CE9F85B74105632283861F1C7F44D1574E64511336D995C69AE8D1F9920FC05A7930B0117019F5F0242E95A1B09335838B7AEF3C0BD41D4A8E4C3C49037636BA70EAF19892D916309024B19B52EB94F6EEC011D1C6F996B4FEE2AFCD326BC1D8808115D037BB343E513163B270A24066554FA22902397F25774AA37EEDB1D74B9940650A912ED1666822CF801070B1FBE837B00FFCFC668EE859C9FF8DB15769EF450619C52290FAD9148D5408E77AA45BB60734B834E48B9E2E2A869660DC83E10CEB7E62E3AC19BF1F0A5B270395EF0000000A1D249A66D1557EE90E7042B7F7679A8D0DD3662B59E542691AD5F667DC48B6F876BAAFAC9AB1CD10636B84DDC5CA936CBD0BB7F642C8EE7332DC681805D25B9DB0894BCC3FF339F5BB97882BEDEC846CFBA001FCA89ECB79099B8AFC070BE2D2ED5A49E2020732137DC9C54A4A080A808BF51C9DA61992D0
                """);

        try {
            verify(pk, sig, msg);
        } catch (InvalidKeySpecException ex) {
            // SHA256_M24 not supported
        }
        return true;
    }

    static boolean kat12() throws Exception {
        // ACVP tgId 1, tcId 2
        var pk = decode("""
00000001
0000000A0000000666BE230DE39669DFB77133DEEC2DB8C476B5F2B82B260E7C984D10A239F1597C93943E53FCDADEB4
                """);
        var msg = decode("""
FCC308C74B9FD9F6CDB2CAB75E263348196FB78D7399B3051375DC74424ADAD794A71FAC96834CEFA24EAED1695CD312CF12AB679AA5801C6F9E4B978F7C457C8434143138035BC5D75133F3B522A45E0D6BF087C65E0BA8D957AFF2482340D64264F40EBBE07DC09A72C6A54E5A731F9193BBD05287E07842D012B6C4F5379E
                """);
        var sig = decode("""
00000000
80000005000000069910F8327FED62BA21EBD77A9B37EB231C0A33A4CF7DCFA3938DE94FF2D6AC471739D6F8E4DFDB4154EE1DAAAC46A10155D5F78E5761E180A0E8C8DBD3CB26882A303AABD233F1CF44C0CD958C82F0C124C80869DC723ADC10407249DD2D5756C153C3448B9FBBC001C82EF41AC309398209EF06889BC60F4FF281914A61C7314FE0D44D90C9CAAE60B4B979E4946BE378B75AA3C6869C350868487961BBB85D48F534DAFB6B5E1D98075E7DD5568AC32A50010DB326616F7EF088563573C9F25C9722196E5F4F50618CD41612E2FCB7656F0C610374A95D0B9E5F89B990CF5CE629D24684D20229528622AD383A6840992582E1BED481164AA441A76D59F147C4D1559FE071E44A090F2C14D33C28D50B45CD67CCBCF44B83891252B139218C1BE7826B5D35011F6DB99A0D85379B69B42F9DE9AFC09E31C5A583C1214F9DBA579D4E35FE989E78F89EF64C0F80AA76377FB7CBC244F98A74B0754FBEDCF9BC61FD5408CEE1B4D284ACEE20779C3877821DF4F009CB6D1C21FDB637826B9B5F1BB660F844C3EA3BFD6F556643B73B03DDF7CF5D7E8411919C0B35EC5A1256C9B51B220B4E686C21166AFA17473850C8E74F8FC8CE1A9D4DA243E7EC57D4BEC9B83121077183CF9D155FEA2D08D6F767956A8DCD11D26C5117EB8CBB5F83D1C76F39CDC129D3590874BDE650095448DB59B0349CE5C4E1105C9D8C2EEF0503225999BA95449BF21BBB484D6F1D5597CB801DE12581F1F8F26C57B133F67EE1C68C7F9363732188B7DCD403543FF7C0CA20F40B0A008A3466049196A9079B81B25C9BEEF27A8A2526EB56A871B2EFDFE3BAC5A8409AD270DCE8E5E431CD46949A6B2B0B096CB6A9BECE089A7C789CF342E970CF64CF6B49332D2A0FF274B47E848AF9912FBF49552BE6432AD7388A14C4CC4B5690099D45BE3D8FBEDF53BB8BB9745580B96BF67FEB41C13C624DE271452449007D72E8B5F6E0119ED34DBAD23D1E41AA588163B6AD9E80FD4CCC52C30156623A33E72EC89AC46ED528A450FD23D14B680D2137D24D0E6D89D58774A54ED94BE7A979A6D46056DF12DC14E2EE8F1403AD57B7D5513FCBE431A93FC0B14BBC7E68D8A9A15312B339F9B680F3494141798C43DC9EA8C1F456E6095116BE26277045894C00E9A4D46229C214308D87896C85770F5BC54E80A6527957B9EEC52283D3BB9F6C0DA53046845E733EF5290D33EE180CD5A08FEB0DE7E3CD013CF25524760A2977BBD375138A182D2C9BECDE3E81FB24E9997D760679364A894855AD167E25BD30F7932269E713AE7482D99DD21DB763175FD9B82BEE41BB9FF0781391A9446F1BB038AF8F1628162F3BE8FC3DC2A07A05B1D32E6CD1B1140BEE3F528AB82F932FC33B3D8EDE376834420EA569DB1017DC07CC5755185512229875AD1C353B3785B632B3168B410F0518802495BE1DC85CE0A228DD2461D003903742F859587F18CD229AE6DD9BD1A28F9CC32A937C60BAF5E90FB7FC5F6686BF8CF3BE3F4A18929D024A1DE29380149D61ECE0A96C0481B4621C3CFC67E199847A80AFF5559C1D405D9647AD42CA4E4D1F8F67746BF7FA36A00B50EA5C7E21DF5EB3DEA5E55B9E20B946DDDA8F675A35666210EB3FF3A1DD179C45294D60BA644572215502F529184A6F5684E927448BA8AA71EEBB928EF3A4CF930BADF77FE24F9FEED2816AE75C396067534F2F0D2CAF60B0F0C494E166E1C35899EC250C8E392A0548A832EA6BE0D8B1AE915714859F9FB1D911328FB20129D3C1BE3F40FD542C48E9C27FBB4D1462A0B609A6956A73500ABAE2FFA05A56BD1A74C1CECA72015C4348CDF375762E3F8FC650520DF25B53C618941E45C6DBADBCA80DF169E7B0875AEAD2BECA2184D85C3F63E8F754A0533E5C5AED49270FEE6D46A2BB838B365B8A7E4F1A3E550AFF594FD1CFEF963BD0204976378FB224E4A1931463F83986E1905FA6574C75025AC1096990547938C559BC116BDDDBD22C432891551D0F033DCB6C6856B68A3B22DECCF16D665756BFDA866561B21EF13D0E6A2B76799DC0BBE8160139DAC9BAB62B2DF31F5503776BB6650EB3C590B7DD2EE2B2981DFCB9EFCAFD50C014D1DBFCB032B802F77A33C9ECB11FE9E70D03398E415F1517DF23DAB1B0B75E47C825B44DB863BF5A4F4308AF9C93FE6AC1BAC550E8AD0361557C4D1ECF9FD05A74E9B94D196AFEBE11348DCD1B44B1C154835C4B0BEFBF3997CA6D7E759A9CC90A5712697E36DA10395DD40B292D5532114C63C6950140F44ED25DDDD7D03A5FEBE7B2A5D590650FA2B9A18F2447A04124DCDA2FE3ED7ACECEB25EAE0FE6166B00FCAE0DEC025B0B6F7B9773F4A925984D5F0FE85CEC57BB3D97E6E70FC0204635C4A52DB9F812B289A4CC4D7F65C7913750A5838F5DC706B4642357AB240D14B67628246C031AAC55C1BB57D20FC3A4240A0B8D3D54102EC4DE815419069A091387EDD9F61477C0DBD6D84334D00BDA18DD8776ACED25A2B3FB586CE6DA6E05A4320467D8DFA6086D4E7F38C7718BBF81C7504720053C96EEA023C7FC5E582E5E7DA0390702D51A557C665D07D9B2AA29459F7E2C46420875138ED41BBCD775765AD6D66ADA05FCB9B8BE77D8E210F81CCB266F2536111636A16870CA7EDD7063358DD32BF593FABDF8E9E5361D368ED844FA2BE8057E3AA7F6D8113E76E7026550411E8BF5D7F7633EA45BBAB5B0BA0150DB1ADA23405D1CF747F8C5AC275C6E087253F62F1CA699AB2BED3CB10953F0E4F3BD38323D00DFA9BAAA908A9A39132E071E108FAADFBABDB2548F843679821DB43F7C8E821659A0F1B05D2B5CF15A4E58EB545D9943B83BE1D039CC866C03A2694100463B8C8BE5A3058888E1CABA82F52FD9C61A873C5B39FA98360007B6DE5311FF01926EDAE33155F64363C2A37B265E18D56B8C6AE4B229284B02AA2874C13746FE9CF20EDBABE499E74E8EA57075D5CD2B6B9D4CAF4415A71F5DAB2A643BB079065ED844F4CCAAC2CAE60077226EF44C90B9E74A67E085B24BCFDD944945EEA7C68025FB84BAAFCBA2235FC9ACCB727A8AFDB3218C30FA785B1A36F6CCB6D4A3FD45834845E28D34F387CE3D46A72A1AF6DDE0183D32BC6A2252EC71AA05AAFF22D3D4958AFE4CDD6E02FEAF3CE92D447FA1B6C6D3CE9F85B74105632283861F1C7F44D1574E64511336D995C69AE8D1F9920FC05A7930B0117019F5F0242E95A1B09335838B7AEF3C0BD41D4A8E4C3C49037636BA70EAF19892D916309024B19B52EB94F6EEC011D1C6F996B4FEE2AFCD326BC1D8808115D037BB343E513163B270A24066554FA22902397F25774AA37EEDB1D74B9940650A912ED1666822CF801070B1FBE837B00FFCFC668EE859C9FF8DB15769EF450619C52290FAD9148D5408E77AA45BB60734B834E48B9E2E2A869660DC83E10CEB7E62E3AC19BF1F0A5B270395EF0000000A1D249A66D1557EE90E7042B7F7679A8D0DD3662B59E542691AD5F667DC48B6F876BAAFAC9AB1CD10636B84DDC5CA936CBD0BB7F642C8EE7332DC681805D25B9DB0894BCC3FF339F5BB97882BEDEC846CFBA001FCA89ECB79099B8AFC070BE2D2ED5A49E2020732137DC9C54A4A080A808BF51C9DA61992D0
                """);

        try {
            verify(pk, sig, msg);
        } catch (InvalidKeySpecException ex) {
            // SHA256_M24 not supported
        }
        return true;
    }

    static boolean kat13() throws Exception {
        // ACVP tgId 1, tcId 3
        var pk = decode("""
00000001
0000000A0000000666BE230DE39669DFB77133DEEC2DB8C476B5F2B82B260E7C984D10A239F1597C93943E53FCDADEB4
                """);
        var msg = decode("""
FE39074C2A621EA4107184B65EA17BDE9D039702C1B9383D095DC22BF39560FF568CE903152D5A602F35914ABEE79951882AF050C012CBE83C1D7985C34ED9A1BB429EF8F303098D5357148DA1755A82E38679CC371C41F484DD071FBAAEB844C333BF196A7DD701F7276D020750C5433C34347B929DD57B5C0875CB0501B5C1
                """);
        var sig = decode("""
00000000
0000001400000006F456BC60614BD3C5E6EFDC33128B37FDA6DB12199EA26104370D4237CB99D2821C855E14D73006BCB2D5F8A34713CD285FE09A478DF20B9204A6A819467295FAEEDF9793D7F3288EA8902E522E760F35DC6D8DA04814A4274B8A9934B5C8847C4C958A133DB321C5B90EB4F9A4180BF5F7AEE92850F9E0DC6E551FFC6F89C99846737B6B2A692EE0D5040337CF615F307FEBFEA89FD59A53A3EDCA0CAD5F3A352458C0AD1BF41C2ADC6C8B9DC052BBDCB0A487BBD5E02D964D305A25F78BAD5560F1B8749E7F7502C84021F3278EC57D61114041A05F06C33BF69C2B71681B0F0146AE705876941D17A4D95C9CA1413E2196EAD4AF55B8EC295A2B16BF423F8063960C96E9299AAA3C98F39B78AB779B5338CC783ED7FED89BCE0B32A3B572599D22E7B55379F580E525AEFCB3086795AF6C1E5EE73F5CEB54DDAC8CB6011A00C5A09F17D454F612618BA547F1FC308850BAF0B7B63BAD0A211A423AB3E7028045703EA46181A8C316367BB8E0D910E6DB5A3085060491A8062228BDD297F85CF937D4017A205FA47DBD289DCEF0C98A49FC390BACF1CE85FD5397498AFF04D4B4E89CEB9E62BC90291856C09B45704E047D0DCE0A8FB2CB7728607F1920D783B6C375B028B25DFE22134921F6C5B4963D9112D4A1BFB4291B1A6DD703EFBA640F4280E4B1B1C98B647990295AB56E91559A3A89B892550029D0F2DE8330F3EE2B78BA1EAD490E1088B29F37928B80E72AD0C41BF5AB975FCC58B2A211E9E0CD38D203E8A781FC0185DC698A4A9B37B13B2F002A13405812A568C14915B04D0536EFE1B85FC2448BB82C43E9D3EE569BCEA68B330E93EB18FEC61963B21ECD559D428F204493024596FE9461BA157B7AF6E11DD03E2253E3DA8D5227F388B684B75B166FDE7A635CB349E63C9FD59A53793741500CE33FE6412ACEE390DCD16E2FAE9D776D409E19BE99E5D78605D19FE8719A0F3C2C3F507E83515E52A163902E9BFA6296E97332838381BC93CCFF277C2566BF310A1B2755EBAF177F78B069741563182184CE65ED5E24434EACAA88D8C4F7D2EAD8471D23A3AECAEE3B3B2866B802F32DC19880FA4517477126F44FF5CBF4FC212B8DAAE6BF9C87D915D5EEF119D13D946C7EB9DF6A18A614FC6827AA84E6F191E2B19E8178A09712C1A0E111A076E46031A57A55F9E0E81C0CA9330E1AA7049E247C463E5894372781F87F875297004C895E3F11985BA16E41D37208A413407349DE4EDCC68A1B00185909A4E36491958E2466FAAC6C0EF15C46BE2CBFB93DD9247651173AA2D9DB690026632380A7A5BEAF247744ED25CD00F0937820B2E1CBBBC5B051F2B7E88505F8B06AA4BB7399D3418ECFEFAD221052BE7ED23B695883684681D18D45B93DE53535EA4F4F9981A765DD370C2DFA7FB9FC262DF24DD28651429FE28BD87C302D7ADDBEC31BFCD436ACFA9795B143525563FB14C619BC1ABF5074BB90469ED79844BD5E2CE8C401612566722DB26DC90DBCE23D947A0CD108706D6CFD4599A9F921E3941F8FDC8ADC3478732BB967C49C04FE756D7232AC580D02FBAB731A259D410ABC67A9AA9D759462567963639BB19B6EFFC0E1AAC3D6DDFE50012BD3E5B035DE5A7ABBDF920F28195306F662129EB2F62367CCECB2CDE8944168D9DACAA153D447D100F25AD1150C00266A73C9215E853195F096A2C1A1724489AE8128BF81A0417F60190E39E0B7D47A4DAC941310DB1CA2EE3F13BBCB2620E4DF6C99AC08F33E1F0031F048F389584C60E8ABB337EC0ABC3615BA4DF3253C066624DDFC3065159909B7CE41C57941969F626D913A5C9490E44B482A4E60BE0ABA98973149E99634972F64E911BE020303789D6837480FB803A2C6A6901A8F3BF9F1237B4D5C6148D63EBDE6B79C6DC5D1EB44D0B4B6B9B39BD64CC6279D88806BCB86B5355B3B0211721C5A9D9E0FCB4926FA56874E0F7BDB2C9364C8C7DB86CC680D25288BE38CF6DC873B28E8DC8276E129063011762617D5E33EEB77EB60F52B73F80A5AE15D9884E81854A50D88EDADB6C273A0A30A0807BC7C9CB4A327D15E724AD1C664C3678411B50E33467A048B2E19EB931A2D2CE6C2576E641123881B536512BB1B8C1182BD6AA58E7AA05EF29FDD563BAA9B6DB39275A1B05535F733702767B8D78F01BFF20F29CDFCC7EBBAEEDDAF640DDD7553CF64ABC7D31BDEE5E6110B893C14E7253A142E9FDBE92E90B106858D735447AC475B37320C7A7EF42EC69C45C1C3FD742473713039D401EA5ACC5DE9294CD46647AC03B77D2CC4298D0BF2D24FE278483716E0C08C710622B4E4E17675FFA190D20E040C8C0F09E5EC06D9577CC6B2DFCDB80F84AD8F7EE10853D8B97BB05F365CAA0D54B058273AFF283CAEFAFFB2B2D350C9B3BB0D2BC722906FD7F5CA180E1073C0BE602B9EA4769C2F705D7E5D26F045306B8C6C80EA380D7DF8898DC9B7AB4251C67602376C2D8E2C00B52B6BDA9226755C6058361C5C854337013BE1C0EDE87EDEA17491CB46CA75952A4C51BB2AE0E830FAA8F3B4D1A8CC778DB582189C185B7AE21F678E7D53287924562C8F2348489C23B2851ED4B6D0315E701EBDB66FE18EFEC119C83DE2DDD0F1D2F7DEE0C886BF840CAC1184D93FBF6BF1140F3165E0B31E8D412AF892BC10AFEBFC2CDAAA5D4FFB2813B9999BD3A7830A98B9AB66A1C5D17D1A70221C6E70CBBDED81D2546A15783CF4018B9640368D5C9A90AA88C64430070982BABD7CC2A76DA6719D834BF6F3A805AC335941F7A0E2B520055481A0082A733753A82479CEBF8C73EC8B865A822E063ABDD02B2F7FC30A8D992D6862A79A54F94708997C4A723B0D88E6248D9D919D1319B3467042F99AB04E0AB1A3F9A1A67C8F0C19857638D8B7A8FE39FF95A66FC069690DEC9A4FBAC73530567F6505274EA70942D7925CED83EBE2BEE36F3FF0351677C8975866DAE0A15E1F5F2CEEEC1AC379B93D6F367228FEB0EF47FBD0C8A01502E6D620407616861885C9504593F795094F397CD2F46CAA283618877DEC57A08A88CAC7D91CB0182530DADA602F6EB218F3A53843AA16DF9FD162D1517F703FD51DFB993AD1E4312C2ABB950C4846BAD4DC6BEED394C21B2FF363050BFBC33EEDFABE2623C25F4A44D3CB61FAB7B39A7770EB9AC687F195B3FD5E92AD9EA918DD718D01BAA7A2116151F01F22FA620F7934D103C8F30008B6695CC170FC95A4B69A9176F7B11E31273189BB735202D53EE3C8A0ABF10C45F25693C68B6B9D061E9D8D67800A6441F15C66043943A2EF5675D6E2D55FE63B61155CC1B5D288F1BA45A85B86C559C62855BCAC8DE6A4F5494DC017B97A63F3DA073DFA939050E967C70B4522EF06828F9FF8440C1D0A58D84C9E0D9FD341097ADD596D9EE7F894393A06D92109192DA0A0ED45720B029FE0B59E5B20BBCB9EB2F82A52507ECE9FC3C1E40000000A8873846BDAE7795D811A4974FA82B7C3A565DA163DB170B9162CD644B09FB57092C31B630026B779BBF05E2CA538F3F6B77342CD17B853B412026D067BE32E7AAA218A42BD06892219C9C496DCC245AE2C7C013A5D4DEB2E6F3FE02683D7362B50682E0ED61D588C63797D5C46FB8E8EFB3210EFFE1B2477
                """);

        try {
            verify(pk, sig, msg);
        } catch (InvalidKeySpecException ex) {
            // SHA256_M24 not supported
        }
        return true;
    }

    static boolean kat14() throws Exception {
        // ACVP tgId 1, tcId 4
        var pk = decode("""
00000001
0000000A0000000666BE230DE39669DFB77133DEEC2DB8C476B5F2B82B260E7C984D10A239F1597C93943E53FCDADEB4
                """);
        var msg = decode("""
F461121E5EC2538EFB279B511DF91A846C2860018249FF2FFA132AB2E993F369C062605E957ECF357D68F58D3A397749EF5B61A8820AC9D4BFA89C2DF7D1F7F1B5D58B95430AE0CED0E30C4C1EBFD897402184E085A83F4BF8A368764C9F188AB5134D12D0B86B743CE750C156C278281FE9CD105BAD0402E85C4EAE7E1DB521
                """);
        var sig = decode("""
00000000
00000007000000065B58C2B120884F4847B6D49B862749501483035488D6CF883C0CE3D0F8606D48455E432CEF346DFF845CD4A6B0CB67C365FBC6A7CA1B8907C47AFCBD0B7C7A6CC06B6A3EE8167F6E385D5F1F7E408C85876680C8FCFEFA8B1315782DA18B6400F1AA0C3F1D9189BBC345903C48D0F6E1DF90D3B592CC6D5AF3B6D4442187FA46DB3B5B424427F43F0C7518FA30FD78B791DA8CBDD931C26A00EDB68FC068EAF7D6235C6E9EB1B1BECD5C5CA191D37FC516A5434AC4406123451D7DE40A73DA9B31250BE81B53E7C3B952B3E6BA0C92581B847140181BA9A974958330ADE83749BA6A67881D58A8CB4F7BBC8A5FE66114CBBCB6B604B9C9EAB0BE0243207756B2A746C787050E61B568B6E480083DBADC513B6DC7D7B37D3A0A34F555F1789DECB3DCC26C1EB6A5E647ED20CE9110C0E366481678D2031201B14DFB8442ABB1876F0CE291211082A656F361CBAB5E31B12978B125168B3E790B7F5E4B8A61F517655647E12BCC147EECB4D6A3CC87F6BE22C84BF4131C9C0404374AC226C62A5E9EBC747B22E8A0545756B6141FB12A98D92756ED6253789ED237DB3DC9BCB06AC3C69BF6B4AA2334C26C68D70A1DE1CA8F7EBD9CACB621C8105AD35EDCD986F4C3029E2DE09C0E9F72F8957D7BE472F3CD537288B379D2A63D91E6F428D8FCA00A3F288F38E29BA17525F3B844AA44D9E653A7E1EE28CBF50A4AAE5F2C3C082AB57C2D39A9EB9A1E2799C0455DB59B590D4EB7825D08836ADDFA00BD8AF9280244FD4F5051822C1083469EEF420ED7454FB4E288D9131D1E7E50549BFF34267B1B9B74810EAB5A8DE938B2DCBE1E302D2F10312DC58BD434B2D2866482B1AAF3A1E0AA00DBEBEF3BAFBB32F558C42C75C3F8DFA39881233BD11FD1204A931B416AD2F1FADCE7D9F6C3C65C43A84231EA05421C1AA8F63DB22699E81901DCC1EC9E5A7471B8E873763AF5C955B5CC717A5A4B9D2E9D6880BFA48641833E5401BB86CC272FD83B1AF46D76CEDB63E886039DA517E07C13DB01D93E7F36DE751B88332968BFAE2D32800B830550F2EC2670BA41ED50CE5E4B67E02584C04170CC28BF29BF809F35602676D83B09F58F195B2DA449C2924A9A3A6F6C8011ACBCBA6C103E6EA769F2215CC5238F7DA8E7CFB2EDA94070FAEACA95668DA3FAE6548C72B75B3DD27F5DE7FA3381EA1BABA1F6CA0791274635F6480E7F6A79F39C522DEB36664417AF6736440EFA4079661C4EC4F543CBB5F0AA7E5B797EB01F6CA926959F0244A7A5021E526AD3D907A90D4432DA8A1514F5F9CF71D804C552323689B0DB9077D0EBCEA6A5DA3D604E23758F1735EDB92E27C60D7B73D8FD931895DA1B9A913DCACE5964A4C8261E5E4E8457F8E50AA5CB7DCD72499A32349AC1FBAC032CFF0F6F4750205420EA2A5D8474F8B4776BD64B2FC7A2C57F4B0FC02E2E9ED8C061E28653AF6EBB324E4ECB73AF503534635D4BC21F4F3CA08AB26A9B61C549908BD1396573D8A0E0491A4E3FEC7B1E4DBDFBC7AA6CEF717E5FF0DC2F59A59C83B704401CDBDF3D70C850139A878654BAFF3B71CFE817871850DE14C295C961D097C40ADF9D2BBCE4FBA96F38000E34751190562423B51CEFF3237C15125CF05AD2637F1EDBAC4312696ADC617A77C49AAA6C0E326A2ABE6D0935B0608F6D59BFAA355A22FF4F8DE4DD5723B120B7E93A8019CD6F86BD080047D4FE79AEA762D5BC254A2126A59B9820B8B10CE01ACFD246B39FEC58CA9F2861EBDCF8FDB848E26CEDB85D024E50C2888A8D2907AF1D36CC61E04A67A8654D38FB5FD492E0D8CC1B8046C2871A9BFDCDCD0B2B7FD1D19C7634263618B4775B7A068F42A5F35179D366FEF952BE439F8B0F4F0D4701D2B4E059F2034B25C96D792E0E0BFF573EC5731ED313B06DC3818124CB572085266BC99113A1D59DB36F22EC52290EE884234927049C4DB3FDA128863A855DCBD0FC710285032DCC67E5902AC29AFC99E08D9E61DFF43DB064D66226D9B66BAD31F3369060989EC6DDC81BE350CF3E41DCB2821F23A26CC9350A861D2F18AEB4795C558738A1A0A8D1C137F163DACC4F4FDDEF1A5666C20309740D11FD06544A598DFDEBDB76F1833F3B4F90E77CA642659D55E2C7688EFC4C3E36F5660AE4EFCA00AA7F984633CAA5BAAD1661FD91D4991DD7EA02B73C8F17FED5BE4DA5823CE7B2683CEAE982D5E37354FC11A2CC44334CC0FFDC670EB45F2B44C63A0FAF900BD9971C720AA5B60BE215DAECE6B126185939B86807B9A72D4062CCC5AB32BD7C03150D558E1B8A22A439176E2B744C16EA3C4552CD2AB4B77477B8E0A7496E25CE2F573818BA043E80023BE1A2BDA70F5B35ACF8D694B67A5F80A48E7428DD73B275FD7E4C902EED321AEDDCA0C9C1FB4F49DD57197028D89634C4DEF9A76520A3E5970E50853CC846BD153397576D25D9AB5C2832D402DD069D50C14F580B84F20704BF1763A2480BCABF015B8357B85F6B928C6A13F1574C979207258399F97887015D88BD2B4E5D43D05F37698F36986E08B1BA4C5BA11391549F75A312BF936B50FDB1CFEA820B961F8897372DE3B1355D34385C8FCCF2E2EA29247C281507CFAE605E8B49E645775D4549F1E2892BFEEDBF8D641882EEBD8E330A3CC8E2B712B6329BB2C6004DE5A6268F241C9BE19433E866E09AB281C2915A6A873FB9BE9102F22B91E0FD4F1F66B2FD724D45372A07389E1BF2AD71B27D0D234881FE84B27312BCDBF22194EEE56C2A49A34729C83A5B73E541A6CA9DCF85F6C19C5FA82757376F7F56D047E1083813BB1F11C3A6BDB9189A0030BE2DB651CA165624BAB32DDC3C5E21905C8232CF3925D3C85A75838342454AD7D822600F15C18A1576CD4A6E34E818FAD6A90FAAD8F595CC67EE36DD0BDA9C99514B6F076EA803FA0A0F76D4598B3668FB6BD0AB83FDF17F7D19DDE11DC65CE1D2EBF6B2D93AC28368C2B7B36BFE61500C6C77A9F491A3CDF46958F14396706259D5912C00E8FD7B124283F28C3CF5062453B85B3FEDB6689E9841D9AB4C3310C83EEC0AD3E362472B97009092F00601018E71BFC15B8135419FF09A40E8E966B47A21213740C8610CB04D880C2C138E58A6B24112177B438082E86957FF8AA18974C9A3239FED5069F602370B3BA33DC3C35BB719E6975AFA2F6401D1E196A67C175797CCBA699657EDBD0021C3746E210FB26F1608A8460D513E9CC4EB7460E9571032A989C64C68C8617DB51B43FBAA4650F8F4442972F1C9CE89277B01934FD3FF821E93A2EA447A176E36125D83B2147D4FD6D70FE46D17C3A267B42AD0972D622A0C14B929C9F30E0247771D0740DBBE6EA1904C76DE3E29F0F8C05FD9E7D497E166836256FC93F63563D3F73745516318C9650E6FB46342D0C14427E2D23D34F8609B694E269D12DCDAAA83C026EDFDA88B22223F831692F75B54CB63EAAFBE3515825B490000000A3493844D55FA2BCB2623C8EBEEE901C02071478F7596A0F76AD74FD64F82698C831472304C9D6A661EFB0940BEDB4E99BD0BB7F642C8EE7332DC681805D25B9DB0894BCC3FF339F5BB97882BEDEC846CFBA001FCA89ECB79099B8AFC070BE2D2ED5A49E2020732137DC9C54A4A080A808BF51C9DA61992D0
                """);

        try {
            verify(pk, sig, msg);
        } catch (InvalidKeySpecException ex) {
            // SHA256_M24 not supported
        }
        return true;
    }

    static boolean kat15() throws Exception {
        // ACVP tgId 1, tcId 5
        var pk = decode("""
00000001
0000000A0000000666BE230DE39669DFB77133DEEC2DB8C476B5F2B82B260E7C984D10A239F1597C93943E53FCDADEB4
                """);
        var msg = decode("""
2A3D9FAF58F66D41BF985728FC995D3EB69C421E0F623113377C1C3BEA78F5D0958CDAFCBA3D728DB4FDC2DE24CEF9149B05C1D9BCC7EB053A59EDCB83223F82777CF7F6AEF9447876FFE6FB676C6C2547A05E29FA6C4D8C8288F6FEB6E98D9BF92A4A91F340B5F92FBC4609546E8D3058A4B410BD38ACC10EEB3663F37434BC
                """);
        var sig = decode("""
00000000
000000040000000669EC4B802F718F89FB34BA169C3DAF208FBBA7AE2EDC19C92841F296667AF569A4D274D437BDD1AC96CDD5A5B2976237A9B1FF168DCF04B91B4CDEC835805FDD67A8CB2F1CCB50C65F60F025BED50FBA9FC9CA691D21D78F42008FC2D0DBB443500DB7A4F753CF304C6113D5BB094293F1BE0C37546C043E79E6A1282D686A3ECEA0AD33800E40DA94FBF78B316B506152860FD051F606F6EEF17F87C006FA780BDF98DC0F404C48B654C15A35D00005001F9B99C9B9F9BE895435B7D41C0E91769FDA2D065310B48982EA64F6862223747A927274F12ABD03DEF4AFFC5124FBB4A4555D2E8205B813391B4BFCCCDBBF979BCB29101E7EEFCFF9BC91371076197F9C66D8C9D267B75A34C194B994D51DB4E143CFDE5C0AA63428B223E655C3CCC4DFE93E512240F3EBC0C50501FB8768463098753FBB109793F7067325431978ED4BFCCFAF9A7FCB57918152743E34E76A3C13888424BF9A0C5F9922843334E76C5CC306687A511F97C04AF503C4837D632524FE132FD53E4FB3F452D1153188FBA731764BA004A57E4993380F6FE0FC499E8EFFDF6EE2CAE7C76BB1B48BA37D41AE7E1B0DB61B2241C9FA434F36F692BBA51D695A83F2F0E95223A2C9E58A96411ABE5C23772F8758E0F878A2FF1C82519231C01C95EE5907C1C80574E71E69845903E68A0C2CEEB1231CD3848F8522A17910296AEE01322F7D9E2E9AA00981CD848C18C62848828060513402998C146E1A587330627DCD23FBA5172DB48748587E193B55C9CA8D7606BDF66A41E1030CC3BC314CBE4689F72AC99E8EF4BC807E386FE42A49842B9B6B84BDBB271BDAD6B83F9C1867C4AB3372F4BA213484F2E0EF81B0CAE046EAB62541D807FC6A72E4F2AF269876A6A7EA55BBC297D7DD76CFE2897788461A2E07C821CD743E867224DE6F0E1621B105BE82BD8ADCAE704F131D78C79B3144E6D99EBCAC4DF98B133596629B8A4FF2A4BACA3C41C5F6004B4421F79C88FCBB845CEE748C862C095865D25860F466D9FF2F8BF9E6BC755F4149B651C1732C88624D99C2D871719CA4E3A821FD20C44A31A2385312228546C4ECDC54C941A27C4CC2B2A4B2745FD173755665989973670CBA64E264E360CF68DCE9B4F5859EA37E720BE62E07D8ECD23C51D7DFDA431DBC09F3FF70736D66F8DB391583C4BBB0DEF44130F6FC2EEAC2986C8383E523CF8911759D647AF62A9DE5BF00FCFE4A8C42474171C771918330EDABB802C8F50C36B2A09E9AB6B3E85E3971D100A73E23FB29E583B7EFAD60E3E1FCB003009CAF87CEC2E9B3C065B5B6C55EF2DDA054A1928FC9CA32B976DBE55C135F960C32576E92D98B4BA5A3D45D60236C39CAE82CD45F3FDA27C5A184990B1633BDED583299E84FBB193FA53460D54AA946422B7B8AC9AD2B4A169205EFF5BAC0B9A5D307FC662C6902C05D781F633A8DA82CDF5C3BFEAC98DA3A2D5A2271C232129C4CCB5A7D581CD7955CF31A8539E616BE982590C5F462782D2C87776E1C995FDC861AC500C4EFDABEB40C8DB31962320B52699B89CA1422374F45E7D8FD136FCE774072725C25C4D211F6260F17781C67A0437DBD31ECA8E7AE158D9ADD76B3F1CA9A5CC8F5E706B4E554AF224A9823AD115DF0CF04A246411DB49D5455DD9FE19ED8A7FB82722ACCF2A6BFD0ADFBE4BCF56F8FBB5C594C79CD6F7AFACFCE56A5F4BF3C2B3F658584B878137B6DEE098F3B0AF12EAC46DEAEE5BCA404067A8AF42EE4C6ECDC8889623E58D9E7478683D3BA1153312805DC543C6AE591100A0D747D852C3F1422FD318B7B59161A24E71F4216AD3D1217801308A6BB66EF714847FAD701B3AECAF8B5E605CFA09E5D9A1405EA95A69D0D0F01D08C59C52C93EBEE42E88DFF8BB1602CDB4CDBA367492F4411DA66DF66CC1A4969F37998A4AB8D76C0EFAAB5F4AED4A8B98EEBFBD6982104E595F3F7EAD18B023B7E2ED9A235A0F0DAA6A4AE65272A0FDE1FCDE8786FB7978A3778DACDFD493543D3CA08ACB2FDB93DF74F28B5B7ED734E575B8B401F0CB1130A0F15695F75516E053FC261988A5C58338A1D780321C4044F671D8C5E21F315F8443DB767B634B8A3AF7F97BD356885CBCF3E7000A5A109EC95D2ACED3C6235ED32DF2C5D6987486D6E26F206729C21FDE957E7DE45B68EE5CE30213DD8FEDEAC0C3290059675710EBB7F6A51711C16C90D3CFDBCC4FFAC197F60C6A960938D5F095F0A6EF58AF1097AC8E5A29B27BF690AE1FABD7A203B9065E73F49C2EAE47F1E178ED7FA79BB037B75BAAC1F0E9077F78A6DE581C4ED06BE9EF4169C6844A38AB9AE95B5DFDADA4A410BE7AA387639CBE14B3E2CFFDA08C85834B92CEBEC481464C00A2C19FB5CD625F51DBE62ED06CB796C0E87F9524493E1BE6B9F1795A9E2D3385C65C36AFDD307B2EBA3CEF0CF418306EC53F7FCA09AB7586E42BADB23A0A34E8D9F213DA0358CE8542225B367F0E62692AE72D75FA948559BD40FE423750DCD5D797EBF8BAAB2C6677C549CFB2FB8F57846C079E1F386E0A56CBA081E805FCD9853D822F7A06274C80CEE902D213C8E44A8E9957545F69E46F335F138E8BB80C42A0F76CF8BC8B38D867330E91F17135842BF1B15D9C2D6CEAB4E1D8EC6D7DDB7DE86F172B47E1134782576AC2152B924263381B5A9A7A8ED65B1A1471C280CAFB81EE72369BC83311BBF34460FE2BB87882C8F7F068D41F9960546EEE2A2A4EF510AC5C9122D12D55D2769CF8EB86FF5255D1F41CC6BA6C5D32568616595AC06B4DD0119F4D34688048A313AAB05FEEC94BC40FB0D63241E1C8A35F10DEBC4B4B437F1E674E3E4FC7E82006091766E7039426E5C6DC0AA28B1F4393B0388D1BD6B03CC9DE6BC1F73D7C9C68FED37CA28E6549AEB0756E537FD0044D0A186C30BAE2EA6DFD0065B80087AEBC6B63930BCA8064D113577496F3A4E8265203420F8F7EE2ACDFE2F4B0C756F3ABB00037DDF68F9B3A205EFD80680155610427368EC77CB528BEC41C70887D4D98F61D0B1FE10087D9C07B4E9BD3214C13F32E64431F2E7490CC233A8FDB8239213BC6936B19B65510ECB38E5B2E1256CEBF61E652B10837764D7A2055590C65AA3DBD6BB284825B51F8F3780FFDDB0797CE6A3293C1A98C7C5323E7588FD3B50FFA722AAAFEFB1C984885D91EB9F5DF2A8D335B4EE450DE007F81E9F12FA3E8903639FEB7E6F037DB95FC8D0855B2D685852F1EEEEE91A20595C10D1374D82605B559FDB52AB98BCA2C2A85EB2F9A8464E71ED3A4E15C43482AB080CEA3BF09E0605363EAEA15554451A919DC882AAE71567B1AF216629905CD94E24155231FD67C6C3359BCA21CA1F192EFD0D4ACFD6A6A1E89A2D6A985DC3922C28F2D85A91F609537243D981E7DB079B92E3296291B0FB7B3E0BB58CCBD399B9855696E6B55B8A1AB0FF22416D78278DD9D6A38BF3948AC0913EB66D6D347C0000000A5C7761C5EBBE4F44ADDE319FB60E3647C6171414D34BDD121AD5F667DC48B6F876BAAFAC9AB1CD10636B84DDC5CA936CBD0BB7F642C8EE7332DC681805D25B9DB0894BCC3FF339F5BB97882BEDEC846CFBA001FCA89ECB79099B8AFC070BE2D2ED5A49E2020732137DC9C54A4A080A808BF51C9DA61992D0
                """);

        try {
            verify(pk, sig, msg);
        } catch (InvalidKeySpecException ex) {
            // SHA256_M24 not supported
        }
        return true;
    }

    static boolean kat26() throws Exception {
        // ACVP tgId 2, tcId 6
        var pk = decode("""
00000001
0000000B00000005C4F16134975C1D54584726C7294D0A6337E4A3447C53540A32DDBFAF20E4EF031EE6F050EF51C3E9
                """);
        var msg = decode("""
814E294136F2C83B430FA7D74E78EB0D73E87FC23857908D3544AEA8B903A91A9DF4C746819279137D08AA186EAE867EBA6FC79F513BEB4994AAD4AF064543ED271585D4F7A67D3B88D1FD23A125380AF4B4EEFDF1D12F22C8BEEDFFCE02E57B2B4B3DA6B87A32A96DB870815EB40781A9A3B1EBC16F3865BC3D4F2950EBF4F9
                """);
        var sig = decode("""
00000000
000000CC00000005BDD39F63152293D74742C30B9C8D1C2B3991FD17A4A2DF7DD9F1A366DB52C976C62D555E5D75891D7D6647A64C07804ECB30B2749D20EB6978C6D06B128C088DB55A2BAD2AD07CA391BCE08AF614C71F00E2C12258B40D5E9BE57A44259729C7541000CDCEEC8DA8BCE77C4947CEC55910FEC699230EC39FA0187FC5C161C437CCEA74060FCB27E5B1A4D7ED9353304E5641ABB21197409E2F64D5E6F0B7AB5632F1C10A8C1A98F80B1F66E10879A11F3ADC76B2C99FE23E005C1FC5545BBBBE2E94FF9E5A806A2D1F44F04178D3C0CCA1E5DC4883320E108A2BAB2E86D42959523C7FF4EC64ED95E4FB4C49CFCEB3EE286F8D17EA2CB7E745D27A1354E33913948E3A87517A9F22E498400A0071B804F850B0D8FC81BA46B48F45CB288F0979BFC06FAC61F6D47F364501D647FE4D60BD1B66FBB703CBD9109BE67740D103780DFC2D9D453CBDD29B7EA539308ED3DA38571109807030AE633FCCC596B9AFC43D73E35CB42AC3C1024919CA69F3227EE2FEDD05F4A509C5EF70D66125DA942BE42808D78F919F48D741275FE3F7A68C309FD820CB358C753A1827FE3251D92D3C94F148FE90D742527C54E8A2628852F7F0604F3F211E61ED1C5635E6B0502388FBE3D8CC39A2DB72D2100C939B3A17F577941299BCDEEDBE95E6AFFE9709F58798729E5DD885A556879EDFF210EA634B0106BF69BCD4E6235A0DA52F7AEAC27195132EE8DBBAD64CC837E8E19B68A0106575E3E72386F135C970D8BDDEED34200E2CC57D6F43467A35AFE044958CF7EA5590876A58C6A33283AC6DDF4913DEBD537EFA893E5661FFBF71055B1F27CF0F97C14E2C52A565427E13DEFBDF792E6591617DF8A582C5D6F63206DDE89DEC19080597CA14D7E5A54DCC1CAFC850511B80F465F4FF8AC9C827D328EF84B9DCBD2FD8D81D11527036B891DD791962F6958DAD6C9B5D0D7239017F545513B17AACAE7BAB3C59BF4B631BE3325125EA43BAEDBBF5D1184D124EB4A2FD48B59F173C29E470BF69D0DFD9D878F0CA8EEC7FB7FF225ADEB9BBD9536E96F49A498C8BF25A5F2EF257CC07214C8FF2D24C485C937694F5744B5863E103579A7A1534AA4712C6C591931A00D6913B4A56BC7916D16A79ABAEFD0C8F98BFAB7E0D33A39360E7DDC1EEC14CF8EADD72E66E1B5FE17AC5679AAB88D0D149DCE2E448C7256B2854D6138A55E79F11AD4BE7A16553B691825257DF0D471C0D13D6B6982F6BB71A968F9995591B0A8C7633073FB0FF16CF78919BAC50BDFC1852A8326C2C40B18BA016FFD1DA368C70BDF5CB1304F9EF6DC09B3FE982770E90B5AEEA815C170AC566058424C7FEC392BF30C2C3CF352150A80112192EEBA8C8B26F8B3A7BFEA316D2135E9EA325CEC1864811D8F661DCAAA4D26EFF4FA8E4937BD9B8B696436A1B2AA4E3DB206B6FD1DB913BD537CA477FECD680EC95AE7CF63513EEAB30EA6421CEBF19B064C2389F8DA1B64854F998FF4DF1BB27C6398D9F5C3DE619B8EDDD12AA661B179418A54F1C282B2568BF48B616C328674B058941B919C984B139632530388D77E6776A55E99D13CE49768C638F3FB6EDD4799010BFB43B63F56E2FDE813B7A6F7EB2CF752156C5C6DCA0A0A2CEDDEFF90889CFA49E3B1F7290447D0BF5EBAA4FCB144BB346F7256F6AE66580DE22F75F9E797D6C9F1137378CFD28109646D21FD9A04835D7F49A008EAB5CEB27C648E66EFCCF1E8CC5D830AFEFFAC8D51A8F8181CEF1D665A2C1243D9B5890AE6064DB5DC38BB951F4BB05E3B51722A3FAE74A84663AE402702027E09CF565CA8C498189271D5868B4F74530551EE8B5E8C484D7D7E5ECCF049F5BB9D4275031B9BFD2D950BB76017296B7C7029CE3EEE32E8079C917DFC3439A80BC89C8A2CF8D8531A915A3EFEF25BB985C5CEF0A10C6BF0276FE0161E37EEF75061C684D1DD005EC72451F48E28597A308BB1FD6D1E0440119DF9B1C357B23F4D69A29EF1A208E862C180B38868FB1F6F351F0F04F706BF3DCF16F515C02BB7A58BBBDCF878B2E0CC9E6B1F837E31C30055DE752DB2B7439FBE0123878582D062027965755F32F32F2DC20FFE6F0D9715FCFA55B143A100A45290BE10360E85BA7ACE911CB561BB6240EE5F01FCA31F648AE423866E875511215FB46AE4BC325E65DE1CFC2AEE33EBE45152E77F9BAE3DAEE521A1A23D11CF99B7A19E0D82324756ECF17E666C83E2F72D3CF06B7354A0679177C4E59C4664D9A8AFDF0318743F92AD8EBE60FA0A1976A8BEE5E2C0758C9331FB421610C8A52766AE8530EDDE548DF303DB034F76A1DA28F0785AF0C67E856A26498B763752C2E191567A1A7F80209B24F7B20ED660B4F98AB47AB659D0BE55E56DC58313A040D1AD7F34D76AAEF559E988A75B74FFEE2B47DF1BBAE1D5D7A5EC1146D44F39715FC298FD089CA9EEFD72806046AF3615BC18D109DEA5B4FB76A6405D1424BE46CED3235D299C6A34BC7E92314CE6C5436AD78C9396A852FDF09BED6DCCFB2EA511F92EF8C69CD1E4A38BF96E3AA473518A62BD28D1739492C7F0E023FECAB03D075D067AE196FF15D010B16F418FC62849FFC22AC6DD09EA01E804DACB59C5881A280087DDF7B2B0D8A7E72FC94647CEB43A7512900743948442A330D8BF061C36DAEB5FB69A6552E4C227EDDB57C113F225380E4D1423D106E191546488713A767B3F35CB916AB37C9196553B184AAD1886EBB3FEEC74DEE98CA8242E1851F2CC548D3244958F2A3D77AB04BA1D3CCE5FDE7E5C3B2A68B52BD5447C6ADF5FFD6DA38FCDDCCD7DF89C5F1F993A8E7CFDA2FA3B3A5C4D522E6AB8456D090B9CF8E35D2D61417B7159687BA5EB047FA26E7E482CD4A57F457DF170C045C8FE48D391EE910A1DC36E30E3942A57C63053172A06B08B2C03C187AADC302B33C41652F6DBC6D66F6CEC3360CEB742E0653787AD959291A13FD2EC5C118E7B9C2DD75E4540FFE28B9265B000B628A315A52D670DA5642CCF053CA1D0F1706E3686AE1AC378D75898E783D2E7D798B472BD1DF35AA48E4FCFBC74FDD8FBA170221C5CE1C46B7A935277343714E6292EF33FBBD84288F8A9C367260484D0F459048CD4FF923679533246A0A8DC2C3CF67194F94DBFB2ACCC053D7062B75EF59701E9098984B9C97D02F00AE4B09AF60746F9A9FDE20614CDFEFEF8838628554425DC235EB251E6655494ABC58E28B0CDEE1A0E8CE2B46165EEA26DDDC9BC066A3E1569B9EE7E8563ACFA74B87E94BD72B26A97F5E035242C0412C664281D8AC08781AB93378D51B1B2419EB28EA904E43E549F09B0D63DA5BBE5D6BE50DD8DCAD8F92C3CACF7D721042E0E0198634F25DDE5E6E8AD5CDA9063CEDBFCE801572F220B5BBC289ED6DC1C55666DFE16D3CC767B08EEFF6ED4B28D5415807F412DE673F7A0835B3C7C77D5FCDDD13867048F64BBDB9D2EBDC5DD03DDB61A0DA7712D59B56E87880953F9153FBEA646CBCD540148EEA6C47BB6F20402F7F2728DFB89E0216DBC2C0A7A425B62E69AF6BDF8EE2452188B7D40D447E66F1B35AFD326EA0C1887CF760827FB7D50E13BA71F23C1ACB5645DBFC21B8B6838D0B7F77458A0582A2B73C809F15D2A10B3B21B4F09ABB6225E993611154A07676F686648200B15BBE2308243D5248D89C0F71F50742D6A4738C38BC3694BC94B6463C74A300C31615A96757C79651EBC0D83F7D30AF56C17867A0A5F23178C732326398CC8199A2E160F4BB322612C5AC19DC4E0438CB32A3F3B4086451BAC58EC0EBB901E6F37964E29C5B42F0E9237A373E3CC032AFB0C7C947239B388444B25FD6735DFE806FA31FE8E91F8C6097538795470D6AB4FEE402B4A49B6D5A57B1E6E8AEC9B8918045066FC43B2D404485A2D6D8734B62833BAC8B23C8FB52D8C88561E71097A82C259C2B97400F3951A89BE21DDAC20D3D3F6B9724B91F999E8F6746DE7DD302AE29EDAC5DCBFAD5DA02ACA92D73EDB53252BCC8A64264EC017B3F76F0046B6458321FFE93B23B57CDA363C1B3571DD10A76B8BD2DC3B06847A642FC79C7A4D78593E4999F68BF70B2ED04D8390BBD2F0F60537C2FE0369BF7D580B7371912CF0EC640AC33DC9923AAF15957ABC91EC6DAA1D595A9F085ABD5DB14654EA8FDC6C5A5AE845AC21A5EDA0A26EBDEFD9B76EC90F850BA87C9D9AE2F57CAEB5F58B15283681A267D1567299224F0BDF9B1639457C4DD883757C95547C00BA43A759DF13850FABB858ADB910640078B9C0E169BB0865776E346299900A39F748B8A43BC4AA4EF2A896BC9EA1A472DD364884674F6F752CBBFD6C753CE40307E2C13DB7FBB7E679E35832B47CBE7B723BED19CDE5B17BA714CF908C8673534CD15DE9B793FA94010A12EF66F941C84A5293B40FF2C94934F86ABBC3579E8D38F3B7E31D1264C41628EA3732ACFFAB29B00B4E23302F94320F655E49690A18CB14AB316C7B280D6AC3FCAD2079452EE796C7B31C1B69F5A5EE7E746B485AEA12C0CBE8E4E4193237BD691FFAACC4AFF9F5243FDEE8B26FA275AD8622314500789894519EBE3BB66E4F06E05A95E0CC4FB6F8D04C727757A1B21A4C2EFBCBAA7C54F07967678C22759554EA87C240976136FD4543FC32BC26F3808B00654E0BFCD4C5FDCEAF0EC1969CA73DDB78FD54DDE7191A2EE3B47DAC0B22AD82D409DA94F5F8CD7615A9676B82DC92864A36C27950BEE21767CA3F70A031DCBF7C4935C9DA0A51915AFE8E29387BB5E748401487D27C4E191474B78FBA2C63644C3A63F7A8ED4C8B2027F885B3C9DACF1028344DFA8246336F68A7A515AF46F6914FC11C2857C374020AD5A80212E385D0A1CFD9806C14E2B140553AB40D4B3D84FEAE81CED8AD39DEA489977EB1F1DF93538A829EEB9123828902A1A72F65E65890257668D178CE5EE62A3D765C76BD265D55211AF6AB022ED155FA57A111B0D4205B4F860B3B3999C833DA921685B30D3753BC1B3B9F19B5818D7FFA5398C04BF7954FC19A1F714E8E0D1AB3F8365729E74AFA05FA97A928817330A6E066F477F60F3D892F50FCC886950A0EA1A394D0AE022DA25C6E121B4078166153C8BF98333B9CC2FFA2F234D833E904AD23B9DE5EF101A0280872D12DD6674871BB7831F799462BC9DEFF9256540148151331F2F8FF3F87B0A1CA992EF042E24D87ED65C6516CE4E2F3577DBF84C39128C05B93D75F6FD573F0E1512C31800FD073BD1B68F158F0E4F6D27270A9F0FA0692AABA7618E7FA21F6AAC1FA45D5545B1336BDDE45486E2AAA6AC5A42EF0E2EF6CBCA5C9512A8CB12540AE1C0A1D4B2A9259C9DDE3EB417C2559389EA5A58909A6A2965957C192E0607795EFAB82C3975CAD13EF9E47B70ABA60A35D05678A1E03F58B5805C9F817D7102786C24620691C41C4CE2819347968081E01F40F51E2DD26DA5E3A455B79114B926BBBBCC5770CA818FF18D2400E733B372E9162B69EE122ECC52339815ABBC86280DF4BF78FD7B5B20503B24592E7A34F7F0AC740527618699EA16D638E9D1D609A3124BE1D1247F2CAFD6879D70EEC06FC9C6EA2F81726032B31EC338E8F2D2D27EF4FBF6D61A57210C0764C4F216AC08FA63683EC8CA043B7C264F5BA69F2F11627C050035900CCE2DAE2E945C35438BD2E134EEDC871B9EB6B67FA390A4AD1D3F774949D2B26BB4EDA2E353AF8EF1CE17D2FB1BE2ABDC8119003F6BF88CFE2EB5418D2436A11B3DD843C3F63302EB8CA5F9BA4D91999E6738D89D3B89C9E71DCB6E1094B4D72A1936CC19FCC38FC5BD28AAE4F93D1582F4B5FE40C587FA6FD2A67594766F80FD94B467C9C822BD5FCAE295E700DD0AF3F3C235D479D30FA4C881191323AB3729784D3C6D625772316364C724271070F29047655C6C439A831153DC140B756B58D5244D992B040963567B984003A5B9AE2C1431ED53B2F2465AA7865AFBE997D990D7143F66BF8668B5F653D91A883023082239E0FF6C7C97E64C2837BD8C221392FD6BA6830C757233AFA6FFF6D3B57A12550F45447D5A65C2AA868BC590C02D8306F67F18B7BE1FA02958B66C0E2901050F3104DA056789932D9EA7E821449A9BC5DD3889DC3F94B9DC5690112CB17345F51CA88E7A01A65FCC8132C50F5245820A28090EBDCFDA23FC3519937F8008A067EBF2A13710480750CCDEFD604872E7DE90BEBC61F7B241A528365F818EE7607EAC4155D8FEE2969401C2E16E3B192C28CA34634B694D49C5B40F0B22400BA97755D8711C2E6249D61C1F49168C5DBC5F38A01766179FC6F11C5256E277E12122B4C29323B3452DAC511943DA753C5FA88E52491EAE54067A374B1CCE7A2B45342E818369F3F24CC2CDF01A37C9D679FC51E4BEABC0803A7D07DF2739DD66F907887FB44CC177C5FC5C4DD2E8D7D47E78857665E64A93426E3B38F6E49D80F1E4DB206A7E17006A42CDBD7C8D897DF524E037A19DEA34614BA908848CF32F9FAD6C9F28A769FA320ADB4C15DA426F72582D1A9A4692A2EDA6BB82924CD75BAA95345978669188046CC4ECC047AA83D20802F3E5EA15CC5F3677807C3DC1E87C9852F1780E35C627BC55E4C17FB1409B8853C95B00CE3FB548B796127E803C0C34B403FEBDBC1FCC5E74C37308F676B4BC5573A1C5F3DAD6323969BB2F2FA42A301D7066DB98F568FBADF935877D3E01BF67704B33F37F9E4F5F064334F8AF86E5DC59BBAAE7B31A316EB57F082988530717992B8C18BE1430485FD22E0FCC4734465EDDBA03103AEE92827C96417437FBF0DE91E78F4BF900FCDF81E35CC9DE825A2399E7C0D93FC9A9E36A3C0FD356BD1986442CAEF6F05F4D920000000BFA1D4AE8EB42BB7ED9CC0E748D91D1241CE7EDCCB5164F287A7277B08C0CF7475687B2F86654ABBA436071C49F9576227BD1422F93392149CDBA82704B6D4CD83441FBE73E13B03D385FB11A71972D63A4C666E74AAD82CCF75CCDDC1DA83B311AF774339BD01694D70C45B92E015B8E3D99E80438DF35B19A10126A87379FD8CDC2F35F37B44E9823658D991A8D1F79602C53C40FB2661670DB9972511D05396E4CA0BF676C340D898BE5ABC52C4599E238E2390C9834B6B68E6F26E437865BA9291C8308E9D0E1DB2BB3FA1FE46B3CAE68255E7FB489E03E1CB19BA4F8995EEBCC07D1075CC2169BA55904599C0E62
                """);

        try {
            verify(pk, sig, msg);
        } catch (InvalidKeySpecException ex) {
            // SHA256_M24 not supported
        }
        return true;
    }

    static boolean kat27() throws Exception {
        // ACVP tgId 2, tcId 7
        var pk = decode("""
00000001
0000000B00000005C4F16134975C1D54584726C7294D0A6337E4A3447C53540A32DDBFAF20E4EF031EE6F050EF51C3E9
                """);
        var msg = decode("""
050CA790E9D0116DD392B43D5667EDB70E809681B70298866113DF77ABA2B0082200F9678AFCDBF9C6811AD0FB7A87B148313C5B8B8C65FF74328CC4CAD8EEEE62B0B7DD1836E94BDC220D1D010CB8EDD7A94F999013B9DADD129E7447B71BD49E1CDCB4675F5A97CBCD6E4466431112A6D47BE5AC8D0A3E641F3C72CEB4194A
                """);
        var sig = decode("""
00000000
0000023500000005327CB71C3F9DC1992154AF0E81E770F607832A34A997F60775E9B3F70B0CBD7A1FB6AAB30810CEDDF4583C681E02ABC81837C34B36CA15A1ABE3A8C091805FA18BFFB9C2787653D2CBAEC53D6B340EBC34082AECC74BC113731097FEC6A8D703B3746AFE9BDC3EA47EFB7FDD1445FC4628A3291002E80631EE2FB72F6896FC08224F01CF7E683C519F46BC587297D2AFB24F380390D0E682FF230F65DA9CD79D7CCC420D6B75C04B63458D88D2FA0DF6AD1C0DA8568BA8ACDA90CEBFD3E172813ACFF06298516BF4A7E6BD1D6805A28B89C5EE284A6FF004FE34A6E46BAFC431773A229A07F20054824A0B244C11A7D9196DA6CC3EFCD69A1B30E42B0A89CADC3009618D2CF2070DF39B410D0AA4209AC6FE271D464FD14FBB02E743B8A09096A9DAA4F6FC096957D7AB7265E4ED5CFFFC97BB86670F4A067B9CADCD5BE7A3C1386017AA17A4CFE2501D88BE0E1D93448DA572F4C897F26FC954DE9454F495CE21FE06466E4C7D9695A1FF9098DB9ADF91E8FBA40663DDDEDAD9AEF3934800B5EB599CF5CE1A76F5BEC18D02E2B4A327DCC604CE9F63D5A1133D6BBC93CEF19A72F402ECAF287FD81E3982F460E4FDC604D86FA67DDFDBC268A4C2B0BBB6CE667B234DF45841ECCA5B845A60710CD10EA8D0101CF1725E01A67AA6CC3D9BC0F3D6574352661FBD208AC98DBC59058136786A9841487D8376FA09DA2E412A2693E8D1C32C2B37A2FE0FCAB9364B35B18AF6C3EB8A4316D00CE6CD2D4824D303A0171D573DDB1819A60E566B6A194F4902725940575D767D72DA1FBE04541BB608C3C00C8669FAF2824A3EE9B5E30A19233A95E520F52577C222028D23C0248ED21D8D421AC04F9B5473C98194A8241DBCF40D9D234C94D0593F77EEC9D4AEA256E2DE6FCE72CC85747893116D507CF51C5A0E4E68D8D58FCD5E6734DFE6415836195B575937FDD9CB5DAD96D81C52E239450A0CF5289A913DA7832904652CCFF7660BB13F7C13D3537610912F9CBE5A8C537FB493FF17DEC42EC68D2C575D98196827832FFC85B07F5006DEE6E106C7B4C2934ABF7C70B20BE9BF0FFBE78F33C1C40F5CB969D39BD2E3D04BD1258416FB49794EA6C6215F2B8EADA5B00F11CAF1CF8C122E07032F80D000D600F57DD4E291B65A1BEC46CF7EAAD25C83FDAF7957DDAFDF3B906901827736580BB1004D845FAEFA31C2C4E9026C947C35F634B1D2AC9D7B5DE41C1371738A1F0DDFCE59B322A431ED273AA5FDC31DB53EAC28F97406CC6D6F4E0795760383EFE888963E05B72F05A72019DFED64EC91EA49C287849E320221BB82DADFFE1FF325AACE6F2330E035A1CB6A3A2F616B19AD97E480FBAFC324B1405E52284C27B54CAF90669C77E795C452747AB4DB3E005E51D747551AB1728D44B2E4F96EACB0A1C72EA5D91CDFFC972EC0B9FF31366B351DEB3CEFB973CA98B2E3BE0F62FA3E2043E84678479E26AE37FBA436B3BEF189FF6DC62801EF418D1A95DBC9F24FDCBA196A6532F0D529831BC3AFB3F99599029E1F61C5FF9CED4446D70BB2892E51492092A3367FE2008B33E6698EA5A7ABEC97239A90E642215565D007898FC12ABE7828580E1A66F4828158AAD0D526EA98FEDC7153D0DDC65BF8785F6B999D9F12C562D25A48565FE5BB73F4E4AD4F2E0DDCFEC3666FAE8550FE585220F5A51AC386200B1CA22D67DE17455356A23D4C84BA924575D9D10F2B3A0A2A2C8893AFCBB0490AE9C7E8D71E007B5C2B2B5C88ED6913EA563A096DA00F1178A9C9A6AE56DB1E33CEA02145C412B9CEDEE9487372E789E77F27F0F5D28483313DA30D484EE490216916E9086C2E2831676EFD85857F5F2B60913E1EBE9C4E594D5586369D5525D69C49EF7F39F37D08EE0A0843F6E1954245FE4E691255CCB7CEEE51B16ED87C2AC55210B49ECBABE52311B64A11B666BA47EFF51652352333FC6F4DBCAA8E84DDC5DE462D02A6EBA3B13A4374D801E28980CBDB1594628A87CC82C83F697C40F6733E3D1242B5801993741F34D2460D63362BE093384D88E5189E604B161E076E33031ACDB689BDBB633396C3A6C055C48AB0ABE4A2D10E16B6C301D3C518AAA85E8B682576715E3AFAD2CB3FA9D4C315D6E162F6D0820211666D6ED9EE72A3F7F5BB007AE30C39E372D39DECAFC4D0B0EE0794DC56AD254E486C4CB5F46DA5B792DB39E7185FC7C8E12ABE07198AC38D2E038A5E74C556D0A19D11D1C585F18E3555412620FC781F8B6F949C25F7526E9342B46CECC5387E62B8862D851C3688294BD66CBEE494685AF619A4622E8F223379237F6E23EDE61C7B855276E39A751FEB2208CD8B4D1BE8F4AC55026EF44CF6D87864D9C84B3AE8DB70A5B8079A607374DB8A76DFBFFEFBFBA49428B3772506B587D2D2BFB719215ED1E829DE1A7695C14E2E8BC5957006ED6F4774D0F8C72DA552341251B6CF6F024018C91DC0FDCD0D0406A2D897CFB50423F5410C68D71DC64BFEF609707A1CEA2231B32C0A3EF97DB0E7DEA791EB2DA1365A95122E5E844CAB82C984E3DFBA7BE2CF4A87F675F99BF7272B55219103310729F1C9BE6140DFD91FE06311DE216AE2C53FE3286F4E2275AE7DDE32D98DADFC7BCECE34E76BD32234C52071FCC216C5E9FA239AFAEF0D41D21155357870B05CBD4C6E62A668DD09A3FD66D45F649457E8F0CEDF92903AFB3735DC9FCF67C258658AA90A44B7DB3D8CC061BD15D79E121FDF043AC557BBE53FFB814CC8B2A242E0022DFE2AEF5424E6F421D6B3634B4D76D0A2EC9A2213DCB771BBDC729942134D7E30E0CC2D27B61DB1DCE2241309C1CD822ED70946537A7FBA80DB8E03EE4E22A7034C43EC2018DEF555134A2B4635E068CF55CD277D86C9D79E6998AD65E83071D338B09A260E0082FEDF67B5FC1C3DFEEA37F0ADF7048BA5365A87DFC609B5320FEAAB7D0D3B740807B3E19C97D4C8B6AAA4CC8F4099313ACF59FAB0549B28C566A1EB47A160E8044AEADBC1E1E98D0CCEA474C886E55AD19AB147FF5361F31D7AAC311D170635D63FE089A3AEB5A35BA5439F650D88E8C28810F1128EE7CDF41255FFA7F12330B60C51FDA05EC2F2B206CBBDDEDCD3691A87106E5ACB741817C9724C4D90FDF416996FF4ECEE7AD54FFA92E54954081F138D8BCC1D38A9E9536096C4DD5F68E317096B67403F44CBD8D6FEBB4014895842E9E8395A1C7913B365DE62971B566F1F05013C521DCB3D0CEA249418048B6EF8F733266A19F24A69D1E56E7A8DF05347C37D818A1C765EB59A582FF86F9B3DE9DD4D68146BFECA54CE47AD0EBBE0BDD24971536F37C8D39899C3108BC55FF7DD3DAECA4DEC0147889F75AC67302E5E9E8C73762F8D1340640DBBFF0E747FA3D96CD8D5EC2419A3D753F38A660E44BA47DC4EFA3FF38872DD044CE339E0590D87ABEB088A12C830A10262AC08D304CA9FFB4C76E5239DC7C0C02D7BD714B6C78CDBBD312CAC57090D45793363663BC6ADA6CAF6763733F2C02C79A53C0A3BF4C3E5E4941AA370F8A63970A985E85AB6C20D83B18C5690CE16A6792426D9D56B3E9720252A14198A6536F0416D59B36315EC373B92A05C78CA155214D1B81240E65D0DEE1994E91891975D9ED7A1D5F5D051FABEE1FABCDF4DD68560A6F73B247CE069BAB9A465B43C4F83C70B7143D9B56ADF9C92FAA1CED6AB09067D7CCC42FBB53EB1C9CB1D16F7804328A09F262ACE59E5FF333BB720C6BE048375BB875EC659A0806AE6DDF76F86C2B324199BD552B98CBD05527F939B160AF95255EB906625F74BB0A593700483EDC566002699209D37D59E6F076442CF5FF4E9FBF0B3E0761329F3196F5BA75791B6A319B7F903C627EBAF2638DDFAD8C38030D389532E71B94AF28B047644C409E6880CE22C3AF94D01BD192406E77B0FD623E9967DDEA4649E1D71A5CD0034D460DB25D25E703654CF276E71B32967A08D5193ABE667A64EC30604C4F495A96FE4174F020160C3E2DB5B4FCB9AFC989B5A28B69A182BA6212E3385D40A842E25B2F964EFF9B56A6923B29112248DEE9D639C137B9303CA99E6981178F7E997421F019AA03D3C0E15CFADBE1B8903AFFEA5EAD8949F5348E910DD8EBC83D37E7B5AB08AAA73EF12B52CBF87AF64CBE9D36DC57A53336FD7B2DE4D0C1F49FCBE995817E39483AACF84482E2E9C82F8D6B43B800397A55336BC792F4942BCD96E034F3902300124EB2D0D8AB0C67B370F4A96F776C007542401D4803A1EF560530179F0D4EA8C5EFB009E0B6B927A38754086A84D1DF6E3A0E3D9C5763A6E0A7E953A97F5AE2FD32DEACE20EC18414AD5CA61162EF89ACB1E33F3D5796CF1EE97672A9E5F76A0CD43B654C46649857CAF737138C0D0E87F508A98EEB8A19CA568125B31F7C79AC66114DB24697865FD47E7774D54524217FAD6A133414967BFA34B6516167F3A050540E114FBD17F24075BBE599AA1D055A36812F30CD7E63ECFFCD7C2304D4302FCA32A6BFFD092B98E955F1950EC2DFCD2E7E0FA297DAA5C2D795DB4691FDE6903485CB4B3A0AF54FC2A0B192E3FF264F42C1D297B54154968A75E5CDD26193BCD9EAD07CA3797DF82849FA39E53D066A328023BB757B9D63D1C7BF0B02B250FF4EFD81624513824859CC3B31ACE678C9D2FEEBE808280847DF6C6FEFE88BFD826533C5706C32CB1D9B9F295A75817C4F507C0B38CCDF2ECA8A062B876BFC676C53357191F635AE26AFD001CA0575A4FB65D5E1CB5C1C8C0A24B76467AF89BE41CFDF0F347668CAB27EE4D06C37FF7AB2229D19112331737C0469BDCD726B225D69DE925A351100F5FE6A3969E92C7F1C66B66D660E349A292BCB6F2A99C1E20489C6E5E83709B18A5AF7728AEBC38D40D877D6479C54EDBB7F5096541DB277D0E4B7E919B17CF5B9549D691B4F56D90878BC803157AF76DAC7212210F7805956105885BE00119D6D2BCC655A02753995307FEAF943B55A8166BFF8859CC4372253E4EAFE21E1785733D25A5C361A41F3616214D8B47C7934F9966DCB60717AF7D76D8A31341CD6D2AD58B6B3F539779D5E681E6A0CF67A6B6DB6A96C27672E441D0466D63241381100011E0C6CB9CEE8AF28BE13AD4E68A10005C6E81C86955896F9A7531F3586DD7F71477DF54CB4F9CC3C0679184113D3B7E06989EAA6B99C19A65E7D71A3087D2005A61F659A986650BD3F73B401F6742554E9D113FDBE4FCA5692F304841FD1EEF8BFA1875B40D5D0EB53603F1E97303FFC0BCF0EE7999A86249A6E32834AC502C8B7B119BE191AE1CAFB011D8994008BFB6AF396A5E0C35E5264C8D2A2C5389BF2B6E69EB9A0DD4E6F31A6E2C4C682D5ADF6599EF6C0B0C982C2DCE6DF4CE8544E9BE28464F525FE888F1BE3083CD7BA7EEAA042FAE71A1BB4B86D6410E79549EDD6071AE188407D80CEE5B307176A3CCF8483BEA30599309F3C9154B38859FBE9CECB643764F28D5914E0AEDD4ADF52BEDC63E761F10B3573335DA61861F2544127A9C1BED4EBB71B50F73438C34AA19C2758C0E70195BDCE95B33F67443C27AA1024C591A657C50CFF29BDF1F51FBE0568E1C3A1F0D01F1038EF138CA117541236D5C118C107D8EA8EA8D4F6989EFB490AABF260CFA5E521D62C80856EF008EC9FE6AF1CADBE90FE0CA2D82FAAF04307BFE889A2A9090AA7411BE875A2ABEC0B26CC41273CC02AD62FAEC13717BBF066B060836B8745BC2E7BF0706D0B058E26AFB2C2D419F069A13E46E565707666B03666D9C494B7F10ACA486808AE673F5EFC48E48D420C4903454372C34BE32216DB1B9086DF4D3B2F77603BFEDAFCCCD9088BB209A7FB6D21646AAA0F41CD66F7866B5EDE3760C4F2FB466DBE94E1E6E27E079C5FE09EA275EBB66E1E71DF01C238701B26A4E82109DDE5E6D25E0AC23FFF90D4E0CBFA6DD40C564F0D5470A0C0537D5CA4DE27F96D45A55BAD24EEFDE4A4F3BD6A507F1EFCE9E6DA81FCF63A3200E81F16822B46CD1F816ED03A93B6DB0820D2215B17DD5B0BC59FF33FA03840773C540B7F5DFD70C04A307F57972FC4FBD155E612CDA187D24317091CC973972D66C4CF05600E8670909A0A0AB2FCDE1D2E112D799B5E8FCBAC9514123F1C4B0BE2EA8C2E5F9CAFE81CB17F736BA2828D51CF9BDF66BF7110A5DCB7233F3ADB8BCB8E99AC8A5BE541A3EEC797060F690F04C28E1766618D4CB51E2D0C5D6FC5014BF1F9B07D694D56E776ED149CD4BA4E0ED0DE4933D6305BBF13DF0D8E278B101E1BE12E55F91D98FCDD3A0D3B49FE4A1585C7AC455F38468DEF456741D29DC9113FA5E4955E2B7C1FC481BE2574EA6CC0499BAFA60EFDBB73C24CA5431F01295051D7517D865E0D41505588D9092B5D2CE712741A752D0168812E1D6A854203D9E2F552C68427E59E2C92FF90CD1C1F38B4C7D5B84D75033FDA0E49162895DEC036B81A7892568AEE824A1FF98983A91BF787C1B038E02B18B1EF078654229E590E24110A39E581B2E5B86D1F2463A030E3D14B020EA53749BEE8934501F07117244DBEECEE2A9FB06472E8C6500F40329651A1D58A934BA6037E3264CD395825682B0F8D38DC2651045C64F8002850736C330B52768BE592D7B4385CAA9799501FF570FF0119C8C346FADDF66B66AE4F055987F9192D8ACAAE14890E94817E9280E5F69ED1620CAFD5E54B79A504F4F8B9EB2544197164BDE3605B5AD5F23C35F0E6E2BBF443F2C8959AD6E10B202AFCAFCF85CC57448EC87A275D09AF20DAF3BC42E6E83DBBDBBFADA41DF7601D4D24AC377F78A1FB32D21C567FA7D5C759F867EA65E9D1F809D384C3F042D4E5C353F1EC593CBC09BFE0000000BC4430E49BD007389DCA512AE55E47CAD6E6A368BB86980A7D72FD88BEF0FB48E6101C21F0DC7B6FD0BFA209B57D9226626247420E0E5EC4581250F0F301E1F8868307305E165C9210DFD877F84A0BC304A58892EFAD26E7409128FFC60BD9762B96791026EFAE3DE703625A6BD2C10B2F0FE57938A90F8147F42B7FF1179AB5380968B489FB42B47F1D96F203A89F6C54F2A2BC22D28065312F019E33DA7122E95B0C6BAFC8B9E496898ED534746E7E34554CF6BD59A4062ABC1D5FEF59E358850EF5F99ABB64B7E954F07D41EF19107CCFA0E4D1EC6AD524176EE3F9433F71AF566BF5CFB974620FC2CF5B1968D0D5B
                """);

        try {
            verify(pk, sig, msg);
        } catch (InvalidKeySpecException ex) {
            // SHA256_M24 not supported
        }
        return true;
    }

    static boolean kat28() throws Exception {
        // ACVP tgId 2, tcId 8
        var pk = decode("""
00000001
0000000B00000005C4F16134975C1D54584726C7294D0A6337E4A3447C53540A32DDBFAF20E4EF031EE6F050EF51C3E9
                """);
        var msg = decode("""
62DA7D045138DF89220F7721EA541CD10EC548FBAAE3AC74222BD93E650070CEF718F3D67C3F470998A443EEBBD6D630C693A66F47089FD5B1FA439941782E66FD33F8C085E265B53490034AB95D6E66C41BB794666E01F0D878B8E2F47B999CEFFD4B2A3D9A4B7D270DDD48F9784E69DC3815DB151E63495D355BD0608DC211
                """);
        var sig = decode("""
00000000
0000030500000005E6A75AA4B5607B8E415F0D587F9DABFF54D664845BB65414B0D827D81C6788C1235E92AE692F6FAD8AA42C3EE8C84F3CB9074FBF14BCFE7D71B0F918FB568961A67A94A7A91CAB53FC82CE788F659EA45B6ED07E2602EF02083315C32570DFE65AF3B3A71551E0B8BFC142B3342D05C807F2E585BEEA4A9D98D3782C757E7240C04A59C09F53E46E7960C7D37097874424468C05801298C055377F72415916ED9B3F33CD8F6201B5AACAD49947125F8B1F9AA5B1454D7515478B9862A31D396D0AE5DC2C97E5A541A7C88BCC18F75BF7244DF123EFB961B0FB85F625E8D0E284562FAE2DE54C425298D526262AD00A401E42315A054BF6B9C859B157F4D59008AB713187D1F3BA1D2AF2533184CB0362C9C8D93495816CEB213ED73210D23AF3A2EBE9783612F13CE2A49D80B6CB117C8EE22B53A3979D476D4341A306022D39AB933712B080C4512D966918D0255A3D3523791D22AFA2FB4112E3EDFF4F6C547160452BBF0983CDBE6F4AFAECB99D55EE5DEDCE0EEA7CDBBBF710453B2F7FB08F2E27DC4F1AD3B45B691789CEA82664FA56CCFE7A778C3B2AB68D5BA6315AD972BD84B74A44F51CC2A34848F5B3B4ED603725C9A65E21A2F1724B16E821C07B4252A276831CEA051ECE557711F0367720187EF71A66AB8F502EA5D47146C9537221D70FBAB21CAF53A271482CEC0B4FC4D957AF24126BEAD93E4BE3CE6D14FD4E64FD1AF49C2EB1B193407AA164284BB535932DD8EB6F5223368BABD5928E33A133D4178A74172D690D537E9A1F79DF26CF9330BEA4CFA08B0A2FF5B325027D93AE672A93789898FFAA39AAECC50921241613C92EE735B0E092D8388E461023FCF4D9C6E43773675178975DBBE888AE7E16CFF38F5A73BA22B63FA0AF18B30FA332ADC899D12B24FEE190AF9E9FC8FC2E1F5A339A9E15BE0A9A77C147ED933F2F17C9B223E592BB3F30C09A4CE76F1C6C3049D7CF71FD126CFBF4065D839E1CAEA83ED66CF4508B24E7A7A0863B444D44465B3B82E13C98D54027AAC32FE315D6987120A962B2D9A84B3D52AB9488F32420FB421F121257DAAB4BC4B9B7B3CB253A0662217C4864A401641AA8DEC6CAB631969AEB622FA904BE643346E00660FDC6CB826EF80FD36C49F21301E1D9585332479266195C3B9BC8E6E34A0B69CFA4C3977ED90B00B13FFF021763A75BE3FDA82C40A3CBC16F37DC20E9EE27E9E79241DF8C1AC938A4E34478C4D141222984920A83F464FECBA59B9D763D94741BC163F790E88F4A5B989F8AE5DB3A4652397F72895B2D79BDEF629BCEF0F23A6B1046DA728BF8CE398B804028B73FCDF79E59B3870CEDFBD126F65E1DCF9188E2B983A5FC38F77961720BF55D3017CEA23EEAAE6AC04992FB304CC8B88BE31DC910493A20AA635A3B2956B9209E4B53384429B36A16CB395C9A811DE9EF12F786D32E0A3FFE64356C1B79D6BCCCEA8F1BB51992A2941D1C234A20E3F07E133C1BDCB497F319C5C67D998DBBFC3D7D19E0C18527DA1CFFA24E9C82FAB154F2C52EEC5D8A23E9C4AD6DD8C501BB56B0A3459E6065CAC5B96DFE4B8015D076CD3BE83EE64B17FE1506BA2E0A6D1F5F66D8C4DFB1BBA5D7EF0FAF22055CAB5E734A93F5C09E89C9B4DDAB0057C6E39147932E40D687050B72A20844AF66536D7DD18769DC19C324FAE1745EEEFCCC237E4D1E7DBFFEA6F53F404071886BAAD98DACA844765580FED6CB9D93C3C29656D4BD0A1F5BE9F2BF1FA136B6A1D6CCC50920BC15EE0E8DB01900583B7FDD6D9C7436C5DD2B9D3DAA417E9AC7C796910F55ED58CD4C6CCE915F93EF755317FB06B8052CF293ED7A02822C88CBFF6C494EC6A7156D4DB19861850897F44604F88A31EDE033A87DCB21B4558677B8CBCA693A7069787D2FC7E4ECE73F4646892BA410162ADBA4B87411492A98550316A9A52A358EC581F14BB42D28DB007A21353A885AF615844CFFC6E71A75C3A7760A297C8752CF9653FEFB3B090470FE4C844BBF2035451DD8E860B3F3B425B731B79C8A3C8EC34F8A314EE01E7079A674AFC7B65C55101698F5C850ED9DA941CB1DE8DD9754B2F81D70D1C03332579352AF5D8E0572373757C2543F6818F58DEF87F1DC1BA2B33B791A63085735159140CF5D402C019CEA1D36EC713281850A16E5ABACB219D3603C44919D6BF90A9FBB335900BBD26A90C8C1DCEF3A82698AD75FB4A3FB76CF11A62B0F7A79A1FC08771D7DF6854E82F71A39885CEA6CD493BF8A5A8280C042758BEDC7975EE676D9DF22F109A3D447B009516576ED77B4DF3A14CD99920F357579E000EED8650227C701EDFAE71F32CF34380066E0D22DD83B52F2372E457AEF7CDEA480A521E69B0B5B5B01837D1E8906040BDC77B29DF75C838238645D00E93290C72D59DF9E2FAACB1A0CD1E8CBC36D0BE625CDD0592943059DB07F8EE6201AB0779E665EAE3B408A23244BBB8CE08CAC67991FE59C6F17744BD7E4E6288C6F7B164CCC6874870B4612E51853E3B830C84CC9FC60A1CD0D5C60351A07CFE69863D31E7A221273D245E506F263756C973B1A94581961FFDAC958FA65BB2CA21368A12AF079CBFA0A6FD79AD973330E5B8D56DE9F196AD3D033406A854DB72C530E7677B037E23810B198BB5ADF586C1DC0DD22BF99271E5D69C0EEB3A6E4862D9CEB9650911FB46851C8CF411DF4736DC0F92C4B1B43A4005A1AC538A49578A5065D856B38AB73701E8D957073E7D491DE7B34E40504FAFFB97955C16B9DB1D0EA19A2BC930B4DF5E48CF2037BE75E1E2A40C7E433AE5B24E9D1F90CE3066F96E535F919B78CBE333709E7EC703154DDDAE7BF48A5098C2118409C4C8ED4E570EBC30B651C480FB70C2332145411503A045AA351EB5D19AC810B625D75C41902D8C958C3EF04F8E044D1D149938C0D1EBD6E45E7D7FA7BE7734052B101B36142B0C623B947CD4E43540CFCE31789BB137EC1E429D83687104F15FDF21BF599F1D601DC18EFD2FBE7E7DC98D19BCCAD8FA5D0BDEEDC13D465A12ABD6A9B3FC8F9281BF243F76DEEE137CC5D737389927CC94F4897BA0C73E7E00EC19A356EDCB52B2DCB217B4A958EE410BD94B79F41F4DC7821E540B96F1954EF9BD6728AD5AD5520E4492944969236FF8DBF87241D0FF8B0688B8621952484B59BB97CDB150F6E6F559F18BD96087DCFF5F4A63B7DF8DA55BF2D04D67DF90AC575B7941AFDF5A4DCB0DC38D37AEB84B5158A0FAF19816473824DF58B09ED64D975A36111642DB5D12C427F763A2CCEDA841B7C68666A694A3AF3104530091A00F5262F63E9D2BCBEE9170167BA7A7DBD62194BA1750E76F2174C121F31F93755EDAE6AD508D9BF316EDB409A773BC706B1BCAB096EBCCF45EEF49D12FD5584EC7C9C715192B0676D323D7E543902A35D8BE7BE3FB4D7B6DBBD7D6A45C8F4D7FEC683EB86F968A4D74BA89AB8A89A8A5933CA43F8F0074DCA37CF1D0BC0BEA22574CCE9E9CF439EDECCCC388889A7928067C47FADE74D548B38FAB6D55803FDFAF702B2A0F292E8A0D7A4A085AC43242C9368B6134D207EA2B48CA79D8D9FF3A92C57F8E7118E18A2D8A0740312D0E250C36F556F4FDAF405D1749D843E31AF33F558141AE0A218011FF8F8A2F1B7103A88F4C282457967AB68264FF85F9CB029FC96C75C4C370C425FEE12E763ECA330501DD2D65821F82F174151DBA1C4CBB941644EB9C40A1F98053D4812DAC7373E3FAAFEA182F7B5322225DB770BA93C4ABC32633BA2F210801720718D04B7AC9B9FA591C76DF972A587E0655EE18CD1DEEC1B1E0B833E9BB932917BC3DAA6A9580DB535509BC82B96B384B21192A3FFA8EB39197E95EF9F5A9CFF0B61AF1FF1431B6442B4CD27B6BA12885826DBE7629EFAD7AF5E61F99EF83FA5023986CAB3CE9DA652E6067DC3853CDDB0DC5B21E84F1E8EEB0E1F798D650AC13A251690CA66C1C7741B25442DB726B78B6989CBFBFF0394F6551DFA58F9E7A2C0CF72C368C684AD3A64A3BA77CFB6282CE00D229B349C49B62B09F499D95A623528C854F1E42B058AF46EF17D2584AD09D0B8B98E1B699C2B44DFA5EE5FE612EC1EAB9263455E5F1BFB829BFB3DA6D5E8EF45B916E2D26DAEF9A359A23E319F7CBA663070E46FFF2E25F5A6137E4AA99651B4A4B13B392F2D7ACF61058FC3F944D6901C3AAAE95E9098997BEA1643CECEC737A2A5C63C25EB862AF6980CCFB5013BA2D2203E83CDC49629DB37ACD4F09FDE7B4E2C0ABE1653890BCA53E15BF843AFB5825E851EEEA255C94A609B036F43A01BA7FC3437B93EA196D359FC8E0E55C0A92F47770FC5E4E7AD79D37281345C9F8C80522E0CCA2C9ACEFC4D2F730E238A8CB3FA4C1E01D32613AD52B02EB015A693A6C6D62C5F6B5583311E8F5CD2EFE95BD6B8E5280729B0991EC9D3CFE0BF5BB6714FF5FDBCFD109D340D59DCDD9EE12E08BDDEF392E76AA5E956E43A042686CDF5C1D58844770B8638B8441ED6B3B96B06C8F2B7457D3BAC60801B24B90432E3032F30F44270E2B9CBC69299D759C04D0302C621C90877BEADDFD42E38F4BF78EFB1E35116FA9FB5F8C482832870504F41B65315505800676BAFE86DC90E47A2EC161D8964509EAC102611926E8F331E6D9A2F71DF87AFE44954139480FF2490DFDDD190152214202BBB4399A3832F1D197311FB12AC12EA61D672DB409F398F3EC71BE3CEB0D81011753FA4E5048E352AD99433786B9BECEC29A7037C3A231CC549E439CF2117A4234555B6B2A7C08143EE2CED918FBA0E01969F1B87C883F82F4B12801BB7001FB97CE13C96D4941E1076B77BE52C7F87C48E09F7E13B4DB89324C72C42103ECD67E7D65DE43A2E05B4904F0BAC7E798CBCC66FE4BBEFAAC94182AEB4C54DBB9AA53B9A6CC8ACD56594321169232AE3EAA6BB8B9C8727222580663FE7DBD8F9E18E5DE9A607B5F28998A6C63CB9C86E74C1D329CAD83690A65793316B75215055155EF947AFD4A7F35B60CE7F2378B979CE56B7812277AD350D801F9F124E388119CD2B9F34B25C8DD6A351E804EE216104504B9A55AAEEC77139055D5746AFF637B5CD9EB1DE165E6B63D37D6114B81D79C2B740A92A0AFD668C3EE1B1446FC90797DC7C37658C7D67472527F1FA270D123B35E1D7CF97DB6E0A0B9FF0FE48B0256D7C8D8D0962660118A53A667C9DAE04DB287C59E2BD147A08D12F52E4BDEB5B1003E71B9D65692825E22749C6B69EFCAE3B1E930881DC9E038E04490D86FC256CE74BC1A7EA55BBB30FA6CA406FB5EE97D3A7725D60A978CDBA8126D0CB047260C6F059F8C8472E7DDEC65D32769B7C7092387FCA57CFFF2E18CA004C2C9F7BCCEC5EA8FDD350498EB997485E93EFE4D2BF61723E1268209789A15383C818DBF9A7610511240AF530B9F8A8EACC6DA67AAD1001350EEB3A6293E7A0C3F967C06B88D163B0537F78532B99D31A1995570200A14EE95B7A6B5382B9B5121075E559B77DF7DB8EA7C3462978E0CBCFDEBD0CD741936F7972A9DDE38ACF409B5A936B39635C16563DB7D7B5BA432863D117BE096E7756986C21BED8C6141811BD2190D42D2397B24621C554D55AD574A8718B8043CA29ED451320B56105995703BBE4C3E1FBCF0C1EA63184537A68034176300B72E920597FCE0DE15921A245F8962F67565B1D3D438CA3FD3596533DCFD4EBBA1835C79BC7F3D410650E7EEBF9299E44AD954211F39EEED71A70CC890DF00DE33D70EB75B3FFB7514FD87A43E5F7CF22BC20CE977CC63363E1EC07413AE32B240272BE72ED7537A684F705B0DE8854151B19EA6F94FE7E083F261B79DB8D886BBAA693FA5BC542FC7FA86649FAB15770B180CB8F3C1ACFF7EDB396E1BE4C52C9E1063D44E58CF0B959688F3D14DDC8551772FB2FE805B07F1B31C60B2E05010779923AC426F0F811B41695CF30C3FB444B584F5AF9B23478BEFFEBFBFF29E07AA046A66EE0EE6912A41F8E104FCFFEC32400A8124E02B25C74FCA5C220BFB913E6BC87E3D14984F25C1177570F0693CFC663FD9D084322F8EE2E85C52216441E822E3C8FB3035688500E4E28D65F1AD200FEA1789DBA202A3E918FF49AD1E935AE252169EA55D5B60AC2A28929C06EA372225372C354A453E415626E00C2950C4619B50484B9FBF1B6DE95DC500774DAB2171083043D458C1B4B99BEB0EB2B8158BBE67BF268AE63E72D3C6C64DF50DBD42A040E855CF2D8C2E9CB31EC9F281B44091108D652E12EE288604863214D6E7FBA0FE824219E1F0F8F18DD15F9D54E045B8225D21AB9DD36E56DD800264D920DD66566664DA75EF3040CE34348D3E8F4934A65B1594BCD5981E549AB34424E4DFDD0190B863AEF88A2A35C37931A45314E8B773363B047372508437C9305EFEED600651F3D6F5325B89BBA7EA286170F103783F3C65F61966DC902304C1EC9B225F9EE00D8CD9C6FD75CC973241031D24C5D4D684E297D62449BE8F504AF57B0EFC27E684CCE7A0995AFE9B05042946E93C17EC83DA23A35A029C8B0B18FA463CE66C30226F6E28ACE33AF4689AC14A21FE44C4BCBC80337D0F38C8996EA18EEBB18068589F60A8138E61C954E2AB1FDE12FED60A292BDDB627857572779936AF13344C8CC4D0E0299956FEB4BAC0C48AFAFD07FE39421C5F3BBC972BCCDA69F68AD441FE22966951EFD039CB58F247564E1797300C8BFD4F01B429C847A971A1A6F5D1941E4CD4D507EFBBE47E3B3130982E0375EDF896DD57BCDF326EE12DC9D98B3CA1482786ED4DDF50568F31A1D19444AC4BBC75A812197F3219062A2EC5FEA2665AA0B771374B5C9B8C59B313715B00000000BC9955379AEE98A1A570EA8542D352604747B35E2D82E22F4EB7FCD29FC006920685BE9A9E3C303012D5DD52FC4E66C65DB4D1AF136D9E9072045AA4768EA7F921D34561EBC13EB12C4B3199C1EEAA94CF1EAE093588D94BB37487D0B667F0CAA8CA37829CC550FE315FFD8D3C5C510A906F1AF4B15AE061421014D3ACB0E413489CBAED7132753596C0E726E6464E3C7C1E7AA7E04DBD4BE9A3211E2555E40AC5912F5600AAD85731D7CC2E880B41E862776A1D879F9757D6494B80617F399DC9B877433B635760D5D9331173CB037C4E7695570BAE8C57E4176EE3F9433F71AF566BF5CFB974620FC2CF5B1968D0D5B
                """);

        try {
            verify(pk, sig, msg);
        } catch (InvalidKeySpecException ex) {
            // SHA256_M24 not supported
        }
        return true;
    }

    static boolean kat29() throws Exception {
        // ACVP tgId 2, tcId 9
        var pk = decode("""
00000001
0000000B00000005C4F16134975C1D54584726C7294D0A6337E4A3447C53540A32DDBFAF20E4EF031EE6F050EF51C3E9
                """);
        var msg = decode("""
620E96E7F7810E31E08E8AAA27A74651E79302C1BDECC4A9340952C79D54CA71FAE9FD08C90FB3EB8ECD4871AE36F1AE154BD3E8261B8E60175FF6AA6C9DFF29FF38E019555C9CBA7C28DC4C4DDB092BB4BF659C7DCAAADB02536466C92F146B1A964DB861A66D7AE850B2D0F690B37CC6C0D981081ABF4CF142BFC3D7850A2E
                """);
        var sig = decode("""
00000000
0000015500000005808755F059130FF132652D843DAF620AA5A67D848DA021E67182354FFA9D2730919300234BA6B3B5500FB0F93C2DA4F6D894AE5976087C9B404CAECA19F742D70064FA26BCC21313AD348C002F3A90BD14712A09503AB0A304AF8A6C6A8AFDCB1191DD3EECE608FE79855B14B199FE13160B5E9687C03EEE3E222C161B7F49FDEE93DF38E67FCE7787E328D1177700EF3D311D0D53BFC682FD02B92EA5FA65CAAC3E27365AA7E46C687D7A7FF56C8BF17FAAF5672F7C0FB7BF9C5842FBFB44E933C8F2564E8810F2213727D23497926991791EBF05D236CA62E2628EE69E4C97924F317C76C99251615116FB0E9007FEB7280DB84529D5984EB32141B5052E1CCC50A2F96DECF1FA5AE9C2609E768458521264F8A8B56915D9CB102CC30A3389D97F3A3372335FDB331B8B8AAF739E91A8D698C7BD05C7719D3D320DC1EF4A13FDDC0DE0A7F71DF3F10E550C2A0F30EB45D7EA157EF45E29662C38C22758AEB297D56FFA62A95268EA96A24302477D5264B4A3E05AF40B429B18A18239843B8CBC0DA453A4F2924B82D1BFC09E534505D4F4ABFCD3D4092292FBD7DCEEBFA129164EB40924E0C8DADB43970F2E7D5854DA5CCD2D734457AA8F62CFE88E2DB2CE48C22D5141205BD8454C604F167D03560B28B133E9969688AB070A7CF8471B76CC5DCA008D2825EA3AF5B6CD06FF33FC66C72DFA644A304B9E3ED54587CC727492753C0A5F9507D9AFBAB36B3BC9875E8D8B2E93BFA99991187DE0FFC35E8CDFE05553C1645E8339B3D6A2FA64C9FE2EBF497ACF77937F8778C0C0643897DDFD9DB920FDB10B79867F107F341BA84910190AB07815C182F7AE0ACA9E55E7A39462D903940CBD2211FB373AC64F561DA657F4A906D8F5A3EF709294E25B7A91FA6E87BB337DEF170F69A2578B9383B45BDE034FBA495795F33205977C8BB86AEE94D8FFAA6CD2FA07A6D7A0F78255966826EC77AA15EFA970694C2B4E654458CAE3EC76EF710BBACED345A3644E7427D8430BC39650134D04E7CF2D08B02E4C5E3BBE770E0284423011CA911078FC46A00E803D58C9D2F08D6A1CA41AF991812D6FD6D10B623501066CFEB07AD467B7FF9C1B45F09576B7D273F2EE132BBE3E123E2E1E9E111C87DA0C35B55B958030B695AD918CEDC179C2E53D72E0E7D3A4B92F0A3CA1CCCF68E3FFD9C758A5E33AE2DFE1D6EB6ACFEC9FB964CFF3F369120EDD5B35E44484184D23FB657702E686B4B50F02C9C887DA364CDF45C86421A22C339FC474B5B6030C24CC6A547579DB1585C203A010F74BE7131B0434E7734E8A450880899F9E51D7E22F6E6390A8C0A454F1D8CFB972A0657017C9E3B574932111A8200BF3B11619B796090A5E7A6F037C1F54434637AFF672B7BCCC8076B29EE5666E9C895B779E546A7D61FBA2C9169B5B699885E47D4B1164A2783B8438D5E88F82986A1D96A2D4A915FA21F511D43BB19F7B46B989C01DAEF59A3446AB3F6CC86CFBC907F9CA970F9C04CDDB1D7AD0DFA299638649B5C80481A179DFA70ECD1F4FB6D2EDD73CC4E0558A3675CC8597A0F0DC34CC3BAD2DF1CCE40A8FB558A770134A2EBDC3D48F7C021F5E6AE4A46925D28AFD30A08B35FD05362CF7C3CF72FCEE82B772975AB50D8B4E884D6B93D56DA22F257869F94D2FE537040A77BF26AFC5FF897E21791D9278C4CADE373FAB58A99974044FA2E808156F015089E0B6E3FCB484ED7A1C68889179751987795D41519980F5FB9BBCC1F27147E8058230C767E8819EF69A95718490EF278686B727C96DDA31F7053767B37596EDCF904F1EFF76EA36FC84D56A863DEAD8A9E96F30C9319FD75C11F359501D66161B6FD03F37236541EC8970C0F8F01360A041507CC92AE173C79758645635DAA8E3FD68891FC67A9D1A655F3A8DDEC378681BC44145C3CA52B21790DB72D6581CA1E03AC6E786DFB1E33BE4477584686451B2CF332C8B635A1652BE214A6BBBCD0BF6C6B2FAACF9C52FAD7307128D7B1EBC3B2DFA3CE04C708DA2D7A5FF8BCF6A7B397B8DDD98CE3DFB61EC89799DF59DC600861281C7E61C1A0E1FC7061BCC0FEECFE611ED0F76FC0988E48ED2796BFBA4A6F78D49F2FB7CC07675A94DCBB3161A41DA9DA4236C2DE5B0DB824CDE5DBAA44C353A6DAFE9A970EA6109690F9BBBE2FA8ABCC1EF09D350F8C93E2B88EF06DCEAAD4032C3F8E0B14D543746CFE4EFE81C8F74E24B875D96DE6C02225192E1A34D59F9FC6A5C54719BD7AC6E6EC32DF23C9D4DB8E597F6F0CF89F534800204F9F4B2EB41ABA5C1A10D2F75317E81250C2E56F926DADDC83C5B00AE7F073400523B68AC17284EF5A41F9F53A485AAFD43943F721B690D8DA5C5D61D1907D24305525A5DD1338968FC102AA6EFC2CD92349C35D454FE162784CE5EB6EB1C2F49B446BFE580029F3BB5DA6227EEE75B7605D8BDAD2DE26EDD7C8B3827E867758E2B3CE466AAFBF4C3A5A930C7A54B30A9E06EDE19BD415E023CD3B675AA597553CB42E50B06268B1F5596225F5CD7198794286147573C0BBC3DC82B137FB6EB91F51D5373F772FDB4FA5F084FE0CFC2F30008363E6A0C02CCB45CD252E0775C4BE99DB59E66D3766FD0F47B4C98B21240C3A225C24F719CC42F99FF852EF577BBE4ACDB539845CFC177EAB5795D11C5B5940ADEEBA2D012963E4D394AFA079E13AB95DAD98FB5967A8CF0550C0B1ECDE5201C2CD22D92AB99AB7DF9AB67DED6E765FBBCB03214FB7AE81BE8C3C632E77AFEC533D66F9485C1BA7D2B77C2E990D3833C654903EB407AE1EF11EE39BDA68860EC4B215124DC7FBF5DF4D8D3EA6BDD5E4718A2C8A75E7183B8C474D182DA60346FAA59D3C3133484800C048159861D0C974E7669F942723D3F095360DB3183D7137147611D381FD259441A560391B7666E7D61ED64379482EA7253CB863B0CF385ABEF802E34C07DDE108C92680FF5966CFF353DB857FD67105C403EE8049B3ACDFA85444F2526044D23BFFE931286BD3A9E7644EF7E5642E7F1917167B3AADCC33F46E4E4C496E5751E2454AD58A9FDBCF41EE240B02ABC59DF3831FC04E0715824DEEA7E5A8EA92123A1901FAA05462C204ECA1B66AB0156DC2419E38D2F497559838FCBFEFA25A73D6475C6F892C5AAA5872288EFD4A2E4F13F5CDE4BAABADB8757E7EAA7BA93FBFDF9111CCBAA8D09CE79D72B483F4C9C1701D76EF7AD05166524DCAD9D1B139F614B64D2A3EA8D0B73FFCB4A0D35E4B215E9AAC2E951231C0F7B81F0756500D3787DF6D42A9761949CFAC893A9140FAFD7EE982C01C4C1E184BFCF19B0BE266377FF5E33EC32944501AE18984726725C3F480FBEBA40ABB376111938BFCFBECE5564AEF09BA515A41BA2A1FC6E815A1B9163AF1C64C9050D6B3FF8FFC4FDF461771170A53DB304B114AE39CB18AC2AE5D9F2C8D70BCD7DE760F9AC98281C5A7ECC2D5B848E5EAA2CBA9B944E383A3168056654E458C4395F29BEC0585DDAA47316508FED221879F3864685506C05241D1EF4886DAD792EE84451F65B941C6CDD2CDB1F7648A5A167840B2AB00312B44546E772D7F828298AFF1AC98890F11311151EABB6F38B89770CA0D9778C2C32BED1628E190109A7F5AC5B19A64495222FB4C74D1BB2CCBD99BBBE6BEF73B52F2EA4B0CDD01B5E3481ECAF57562D7CB6707FDA48A710A90A4FEAD03809EDA95B2C4B80D73A9D2322E715F1B925FBF27EE119F54F9AFE1CDAD0EC48BC1B8A4695872EAFBEA4113399077967D410D4209E065543DAFCD598A22EC60FF2A1EB9F847CEF28047045C39126DE50C9D919642CCE52EF8398D11D702306DDE97F07D03CA8AF35CCC84FBD7CDBAB78ED68478497CA9C05152F66335ABD150D5200848472A48A48F0E45B26EB34506CFF7D7DA57B9F3331270BAB86E48287D61582694CB752674FAFFF5EEC9FBB28E4918AB9D743E1C3D309E4396310182EF0AAD9C7840A9F7BC6B82C2C0BAFB5D6F44A76433CE65DADB1965D0903540A759FC3EA8E408411A1C8AB8C0B4ABF9AAC6F78AF91F3B71A5E4DF06BC8B340677F800D26BAE34B02D9C58D2CFC3F8767F23C53CB8E159674BEC88CFAFA62CCDE65433ECDAE4138A1FEE9652CE8D0C6987B3CE5B30CFA9BCB71CAF9FD5FCC106D9EBBE1673E92EE7D7ABBA189441ABEC1B48CACA87B54B82565B437F68AC3930F767FB7094884DB44FE15921544DCACEF897BFE54512ECE81524177EA5C5BF1A591D585E17E345B019822EE55A81E4EDBE6AC7983152296F18ACBC9DBB326525D3339DB4B976CC2378DAC0F255C5066EBE7C7423F49DC6B3E7BCCAC256B3F2179703254AF95FA9AE2C62812762521CCBC9523ADF5059E379002D918704C4BB8E46BDF68BFCC7A056791BDA89650B4FB5847478D474DB33F14CCB3F69BFBE4E310CCF561D33AA3623623E537FA4FCC0BF8F438E77334CACE9B8627637B74E92212AFF9B34B26A96D5E48839280DD5A177D17153336D9AC7DD45AD1EBE95DF1DFB16FEE984B34091BAEF6E03EAC2052AF1331F1EFA205DAE597D2247096C52BD160CFF656DBC6B8E0F02C623B3E0F54BA6A55F8DA373F5A0543D8FF5AC06A5CAE057F4DB4339FFF137294CD3917CA70AE49272985680CA8A2F19907CCFE730331F1FDC9263ADED11A9DC7DB2C8A7B409230B97590706EF85AF2900CEB8D026912393C3C0647A106C9E71FACDC1048617CB9E329249F3A4BC131B721B17426CEA155BD524A52032A7BA5258CA810B4045345ED78D8B1125C5E43811864C018A45B067F6F5ACEACC72E67A0EF99F4F99575DCD6F4A05C81105D76609F183B35A649EE3513355D9DF969B11F1FB1CD20969C4ADC2F891ECE52CFE8D330B366EE1AB2A3D7F9C3D01072FC018EA533253B0D8749C2E2937FFA3CBE3F928E7D53C8FE24631B93859F8EC82F04D4A70540063572BE37A3C4806C9AB8928A7A007F17010A915DF052809907D4926571CCAB97129901F6FA67028FA21B0DE1505CEDA53313B1FB2AE581CB1C3582A41E296C8DD80E49ADEBF35F9B05A35C23480BC6CBBDC2B5C647AF44B9ABC4E3B33EFE73D6EA9CA5CED2047065D38169E4C91618362C89EABB9DED888F4E075B02B49FDF0F4DE52240F3A5767EB04B7C28D958D04C2028DFA4EA1E15981F05C58E2D260E81E8124E75295FF0E3EB2DFD60865F0DED04DBEEF836477860D31CEF2E0FA2553239A0F734920DDFF64C9AD542DA299A66DE656BEB43151CBB0934E552AB9A725F5822FE336CBE5161028906C549ABEE494B7359538E8C9AD6E9F7F73A9AAFD9349A94F0954D1AF6072FA698E01105980EE64A4463470E65285FA29B10E3420E582C804FAF4AE02E67D438086BF974C34FA8F543D37B3EAB18E4C9FB9E2CE6AA8C8E7D689C825B96E18E70100036FE4142B65A50ABA601FA35EB80039F1837485B5FD1957538FC3691918BEBBF728826AF8FA0556F33F486B74345434B3E6449C069934530BFBCB1EFF1D5B7A0C65FCDDDF183555C1EE8486420A05F5D0A2E9D3400F8E31EC679C53E0064BA11B06C423EAE466A2F95BCCBB01DEDC0D5D39F77E54E9D4E1F0F8AD88DE97EF6BCFF2F9495B3DC5A5AC1FC5594FF53A404FDAB51F346DD32D05519A1905C9E1FABDC827CCE92EAC30CBEBA4A5E66AC745458FA1CD6131E086533AE3B9010EE5C01AADD00B1B1308CC4ED2E18DD7543B4E63326D22ADDA4658EB9E5481C4EF265569D511FFA3AB9196FA702A9C8220A5A01372F5A64D29160326E7F091E6FAA19B3EE7F612FF9AC2CFE13A549B2275FD06E996DBBBFEFD44F48958DA21EF0A2AD3A0F8D128B567845022BB442F4802BC3646A81029619EB77EDEC4154F497844128DF2745259F8C68170B1DA65117B5C0BEDAAD796B10A9888D67E4F2820E432B3C66E3622B525906AB98E6128763FE5B6E3CF6315DB5BDC1CD83556915228F846D6F45A0D6E4425EDD9101E194D23459DF226F2FE5A2746CBFF9BD8194DA979520E79312695C62BA287CAF18D440908624B5668F7E5AD3B5004B1E0FD4F82DC1A2453004D333B5D0E82B3B6A1EB9A3F81CC3521E5440D6B988115CC29822FE40A928F553C3FF86B924F928C7510732525D406D4E606D6D4ABE6AA68D01AE1E3B52C4F4316768B2DB22690BC3BC02923C892C709AD1B37547DDF11437AA317937775AB9A9BC598842FC33E3275016CB1F1A295AE8A1EA3BD12B1DB8B69622A8B3BCDAD15BAFAF041986BEABF448E21F6207276889443D50B04683888036F9854235EDAE34746503A4BAE59BC31FEEF1C9D08BDC310F6EA6B396B1D8F37B2C3B6671EF089E113CEC0634D0D0EE8FA5A12F9865E7CDA59C7B5669E4825170BC2F62BC65E57DE29007655E2438ED583B10FAB930CBCA818459DCBF7B74B4393B43B2E113FEB24FEAE46D0AC320C87DCE0DBA3B183E9730F81D54929B49957D9AB6FD4FE487F49F894D1E52B977172D001BFB110759BCDA347488CD6F4F7B74E66A48FAEB803932093B68DF8F16177BC8ECBC3368F516091E7386C91CFFB6F278B019E1DD5E6785BD517C45E0CBB91241EDC0E1FE694FA53F9ABF670D54ABD0E7349F9FACDA8CAA2156BD47E63ACEEE8864C6BE802086581EF04CCF47017958493285FA1A5CD7566D357E418C84427FDDF8C86B53B97E3D7BA204CC3FFB1297957A416E09844860BF4D17053E452383B1DA89E6D524ACB49B0A07E91680D37A31E82BB58B095F05D624AA89DFC01E752B1293AE6928FDBE11E9E6F1D0BFE232504975D80751B4AEFA40CFAD95965A9A001850C6192CB639E60F096964E5684C5A9F33057F36F716F6F1ED7A9ABA34E0CCDF040000000B419F1E8F9198C00A0CEE79247642C37FC37BCE999E8CC129F5AD57A5E02A11928BC4E2B08F6FA261ECF967567B81AC981A68A333501BDDBCBC84BFB52B3DAF7DC4B0E38AB22F05F981401A0EBDD78CC08C10BFE8425F7A1F896C057B22E48D6DBB6C252E04F489780AF37E5AD22DE8F65D83BA311B2589DFF70485985EEBE7814363FB59A52A74FA6B57653B0A8B44901F93B7F509ABA400E97E632064DE5B9EB7F916859B67F9147790974ABF63B24668E4484BD84556B472991F3081FDBF76A2A5065992E37554CAF864E1569F32A5DBA90DCB6CBE11873E1CB19BA4F8995EEBCC07D1075CC2169BA55904599C0E62
                """);

        try {
            verify(pk, sig, msg);
        } catch (InvalidKeySpecException ex) {
            // SHA256_M24 not supported
        }
        return true;
    }

    static boolean kat210() throws Exception {
        // ACVP tgId 2, tcId 10
        var pk = decode("""
00000001
0000000B00000005C4F16134975C1D54584726C7294D0A6337E4A3447C53540A32DDBFAF20E4EF031EE6F050EF51C3E9
                """);
        var msg = decode("""
D0DD3161BD8016C4E720A96836CA8671CD5405804842708EA80A3F1B4EB6B718E4CDBE6E12902970054D640060C51A1D4D8E78C70C35698F4C44F1E7F11C124B21092E51E8D400451C11B5B1D1BCD29AF130386002D822121EA74313D64682BBFF35BD33600C4E85EF7B9F2E4887368F57C82D73D71181DE3C8E5D375AEFC0BC
                """);
        var sig = decode("""
00000000
800003BF000000056164B5C4668B35F4CA7F7E2EA9432067563A83FD88DCA1889F001C3ACAC36A5615DA8E76F170453291FC1BBEEF00565D1153A5D68104CCFB618006E073FAB81EBA58B2C013F9A3020E1EAEE51EA98BEDD385A5B68A953A76FBD317062B5C670801CF737CCF666763EEBF95CA7ED8B345767A519EAAF7C368CC6AE8E51AC99E429D158C1D254FEDD044797C3A837C3CBE80B1EDE3C07C684898AE511E42F46E424B49E1527A2304E1DC75C443A0C9873A124CD62E82D2B96C6B3A45E84EB886F171D1CC6069DFD31F66318D5B8FED090C5F44135836296C8B7C9187C791A6DD66723A596E94B9F39C88A9ABDF8879CE518C3F142E3E544000CBA71EB46BB22903A9721BBAB25471FA36F6453A0E62066F890DDFC9D5264005E1599A8EA5B1D70D108FB12ADCB56C3D29DA09C6194A35CA698D6FCBB115FC18075773E8C68F1AEFA39BD22D4C713AF77D8693D23C4927063B1B4DBE2BFE425F26B756091670000DC96A49591DD4CD8B74D573CA60903A248BFC4FF1099BAE8C795BD44AD0C4469F39B42B0B190B3798DFF49C86A67E70D273145343C18605D4488029257C1394DF5011F52BA1110C8F7A725B354CCDF803D00BAB3925B7FBDB62C9F4BB730EC8C97DADF1BCDDB416ABD43FF8CC0074240F731FC77E0E52A959EE668613E18F31CF0E6A15AB21A06C5D4FF16AC7B102CA995359E74D73971C32260BBC0F72DC2E2BC3CA7BF006D60573346B2C86BD7158F271F233D00236ACDB29E6987BEA83916C953698C602CFF553ADA3F245BE89E214E972D62C8A020D3826B31063541C7075B98E89F5F8A019299E86DC854C6750A4DE06D13C643B071321A81A22DD3B86B0B81FC150EF3ED188CCBE4B2227A3B27429481EF2B2D5BBA40DCCA14358DD0937416071878E78104F78B10F16E30A22CDDBA659ECB5AFB4D53E05EED6FFC5C7490412A842B07B451C541D95794E136E31FCF147D287A955F6C3FF8FF32099522987D4BBBDFC42A85F5C70C460E61FD41C88E989F79603FA532B78675D150166A6FC1BEA3B6099B5664288B6778163ECCD7CC1DB66B3C0407BE245C209B3E513C1FD3220C720C8F74522C9861F9893C9D5D61B5F4E83EB3448AC7FBEA05A27FDCD5A5EB33A0FBE1E0889BBBA288281055EB17D8C55684F7A0B702D6630D3F01FAF83984412C5D323015F69EAF5483E01A3DA53DD19E6999266EEDBF2C137D3B57B6CB61407C2EC234BCC53B46D8C5AC51623E4F7E2D2CAF95EF562CB36CD9186151BA82DF8D4B7FD700DDF60439558B491EB329E2EDB38FCE49156ADB8B77F4BB5CE594BAC977113AA14B78CE12F21A8EB27407C1DE63F50FE39302355D05CD4EE82D2A4393DC6DB16D56E71C0F7AF39F640EAA6C309436135986D92B9449886B9FA044B9E22E1DB7AAA296A0EC6067A898605C808FB07834C5AE0A98921E0A92774582E123A73106E207C522BCA8F0E42358C272EA8DF08D5930ECD161E38FF8F158ED881088942860E2BB8253C922B4D573A5ACBD2B5E3EAAE81420A197D6FE5246B42B851319F7EFBEC37FA02CD1224D0E51EA2762DDE96E1E01A6B42971B45DB9A3B6B58CE0CC92E41D2433DBD7D34B716B64D8AC11CD7A23F27FD39290ADC2B4138E60619D0770F6B8885C48034FDA2DE4645BFAC0F912D4E5CEDBEA88925F4D8FD20CFE21325A32BB6961E0072341F2B128F78062843B0E105CCC371A52D5E5524AEF034AD21308E585213C2CF6914E5EB4A8E69FE00058161F29FAB751A531A0BBDC1615949688C2B866B855642BACD16DE7A9492C69C9958B8D799183A1DD2D307665896646344EADC5EEB896EFE31F37DF6B013FB1CF43FFE7F3D7B577511C7DB85EF2ACC0E7003C4A22E58E4BFA0901F1A0C7B59C608CE2EE961DC95D0920E4D08FBB5711F557F735BA96E38006CD66C37265D5B9689F04E4A58893CD16FEC5710F967E375BE854370C0835026E4E4C65C404FB55AA57E7D8B98957316EAD1209EB8615511A0F197450A3AD9FB5A5B4372780C73163FF6DFB7336446196654A8D046FF388B4ACE356CBECB7EF4603CAF6E807688D71DED5E656F8FD65E780184C38908A09449C90A5242B72D650D052E331FF1EBD88A5198F4DB69FF8209A8FD4A914C7F9E439270289E40B8FF1E2F0BEA70E19D760F88ADBB384FF5508487622DFABABE6C41F7F0CA3F9C87648506AC06227A981FB7EA377D7C9DD4B1A785DD1668C0593745409DA049F46557D05E561B2422EE2FD036791174D93AF0E5C438F038DFD91BA5B88DCA3276548006FD08BC53F946B2FF3461927A6BF9E58DE09BD0E5F4C540F4432AF4C90BFED20E4A5418ECFCB6FD212BCCAB614005B18A66D15491D3CD5CE6BB8E78099B7C119410DF336E5D5BF95A3FEE7197E147EDAD8F657DFB2D2681B5FB4AD1074B9E6FA1BFACE01C1A560C948620FA99C9963B3AE58226071BD07C46EC5D12A97E8589B13ABF88E41A0A2AC19EE724916F33C2E3FDDFD4C39360FE8CF3B24C7B1CFD27EA12013220633F208A03267FE288A55BECA05218094121580B0F06935C79F8C3D63E8D1E3653A79E71E476FA64D1D1FF59C2B9901290816376B96BB399E3B0C909E3831915B21D76C535E2EB04DEB45A4FE130FC19CC1160193A91E3FF113289EC246255227CF8FF16ECE1B3BC6AFBA64D14F81400D9EC41882994A526412E205C185E715D128D6BDA577044775B44A0D7A488E60A37B14B198455A0414FDA11B96E89037CDFBABFF466BBD45D9006A4DC322E177FD32DAF3D4D6A818A185CBCF74A6AC65A952AC76A29C6C1F655F84649519F39F2FFFF8F042E3DC6102CB36214A7C830842254F043501D47039731959BBF00913CF72FB515974E55C77AC46AF8114BF8F7DBB32A4B27994C12529D3377145201EE83B92EF36C1DF77A75D56A68668D2AA05CC8EE2DDC81095F571EDEA5EEC0C562F9F1149896C6D9DBD2E8D4E17613398A1E9C3577BA762F116B67004279E70349951DF0DAA7DD1B6D1C310FA2E459D99A6DF2F28D998BC17B1FEC62D12B6A6498892C53E2F17364E3FB24908B0A784B1A22A180E65CD740814364DC7CE7C2BADEB2AD87A9BD04EE266C3374F016C85E0B96846A4300EC2DB835C1D8E0D2EA07499D5ADE5B597676964C12AC20802CD4D1B6F4B1AEA8B14F56075DB7C9E69F68A31EA4C8B22559CDA8F0268E282E79B201680943238E11D988C6D3C3FFD7D31F41D4AB2787CDC282EA70B4B8F56C7573DA913303A406327CBA9700742D47E4D5DA6068C26D89BC8A26DA19ED14FE7FDE07A3D340F1B8D132C0B740C4DF8B488501F9F16B9AC88712D2BE05F3FE4C5F658A52D0509065E2BCBEEEFDCB53B156AA4E362FD69BF60013C73E81EB7E4ED37CEC0702025B9717B11AD7E87FEC4246763B17C4F2BADAD47A0F1C9A94840234C1626FA71BDC987B1C4F7CE7E8B5AEF92791A07AABED432A599FAF9DA27DF01F7477C3230A448E9F4B20AAAF8CA3E5E9045AC817DE02F318F71AD87B95149B658344C94FDA5439BD814554CCB44459A4A7270D64A5C5B489C4FF28435875184CEC30D3508C83DD27A0F986938DB301BE24A0B9995C06C80463B4987D55A9C7B452613A14CF53289AF80732979FF359AB2C9AA6380CFBC2B612911E4ADE305D78AD88F1265CC18F2208A96274D5D9FAF43C6D95F43BE3A7E7FAF67663D4963FC98520BEEF87F2A816D2DAC5F6B6C58C624530E8ABCD690388DF74F23B979500D99F745B8B4AAC62F207FA601D663F095F825D98FA5C3E6E99D7526C482AB52F3D2F622D7A310D85B20DB001C86BECC69C9B82614B7C7431F5C935F4ECA0693C6769CF0DE016AA97EB58C4DC62BF605980A0161F639ECFACD585D6740539E4230FAC3C6E541A8879A53B75961DCA1C176AC697E861862C61455EFA25FDB5BBDEDD4F86A008DF4CEBC4E2E79F6E7486A6DC7D4069EB80025322C61573126CC166C1DAE50C9EACE7456905C8BFD77DAC288B804C3FBA4D66668D1726D972394EF8CB6C072AC9A85DE81DE09598B8EFAE59C4B668339FCF6E530045F78355ED190129206E275E7F8BA481EC2644496B8AF12028C55AB72BA9A279527B3A751064FBFB66993BC972588F70998058C03AA61F1FD7A410A53ECC448E6B0CE2DB99B8DF5000ABF1A0EFC1F8EBDE52B51746C758770249EC64A64B85B3CDD8D3AE556BB5B949F6F46F073609FE14F5800DA0D80B6FE60DA880B169DCA592499DF12B080ADDCF7D08ADDB079A82F0334AF0F90766623B1F43EF8621751B1FD06BD4F6A21AC41FB29B5F57FD9599EA1F33409665B62E876BB1B0257580B2889A8F14414581D7858DF06F5F2AB5E5B5B5ADFEFD0DC35C721611E8E316C78C673DA3B6B43C51AC64E0F6E318FAFF9D36D23649F878A58D48A19B969560083F28693E58313CEDE67C2337A24E2F4006145DF9BF937636B10718B5E339E2F3ED1D9DF7E6BBFA58BE33B60FDA314977E58B2DA88D6CAC5617DF317C60578052E861A32BD5BA067D1C4428D82DAD28A321DD54490E0C493BE0DE5C1D2BEA7BD66CD42DD442C0D4FB68FCB91F247CCFE0D39616496014E186988D2AB9F865C738362338AA567930D72ED9A14F76729FB1D4287FFF2B39223F44B4CC6993A8D571BEA4E1EDC516B9CF3B7E4C5C2FDA95CEF1A1A0C35B3C4765F2F47E479EF3FC48B5066601C15D78DA5F40107E96CF4138F90CB91E85E1E3F92FA477C20693B28174CA54F89704E2897102ED8A2930E36A89929C50AF4DC9B43E79C717899101EE8DC7FE0B0F61E32206310EE174E63C7AB37397B87A76CC2854E757833DEAA00A21A55DB87B52D0302C27BF055F6A920D6E05ADDA29C2268CC30F317A83CC295129660A65A091C44D52EB26AF594750AF17C21748EEC12BB733A2D5863A47457792E8959192FE4800253C2A6F81F8677EB9EEE5DD2C1BE2FCC10148E4D46DE3CCF3FF525CF036566E99AF734D5EAAF6E629BFEB8B867BFA11875B6549E8773A67E22E2EE36BA0020D93773061EED4741C490D117ABC2D0B28C524D434E8ED58D9636D0118315324E5D7E3E36AFA298823A52229F995DF82FD2A1D966BA382E289ABA3D21608998BBAFD9E782F5570D528A7B8E76F9C13BD72A5B0771DEA7F852A64D9E40E3E9B4B8BE7C3FAB7E58DDF899ECCB3E24AE74E53C92DD557DF018775E36E4658EAE4E9E054D71C2B63180065866EDCBB3BD54F5105A504CA0E41B061A7E5D52CC1C6EA8E1B689A9638C38A0ABCF67EEE48218276C62C0553F532CD1AEE44D9730CBEB2C4B133707C8AF203A853D7CC8781A8201E1D3FDF6BCFA4CBCA0F41B2BE151EF3DF9FF16D5F1ABBDA3217A8BF79B2FB15B21E675E567673CD17DA1F7F10E76DE6F174CAF65255B1E7CD4D326E0F6F7CAE1223CBA040ABD822FE3143CDF69041D605F979AAEFE992D8554192F7866CF76B2F5DB86139F797C8F950B80EF399A5E20B4CC660EBCFF657AB56BA6AE2CC45AA762507082E33660B8BDB77CB51C49551B1EA5AF7522FE390FCE249D565A182B0ACC75C2B3A673C98B6687769770DE34446DD3849C3D4A5D63FD7804B48AA429775664A21F8B87A58569807BF1522BCF6B7AE80B6E93C1E49531B5724A7F39C8DC86F13A151CB5E9BC6255E7547AEC7854A3809D766C50C0D208F8DA6E15167B4465F4BF946158C180E2E6BFFCF3BFB2F86EE0E30A04F56CF41D4E6BCD99E71E1363216E09EFA538E0FD6B2D3FF407D7B3A51AECA019BFC444EE3242E6D188776445A1E9CA6CC50E3B0A372D34124EED112A53CF5973B449ABC55C826FACAA328C98E0BA1CAD2E180FC25FA05880FE497600A259D7DB75449471CF2F81B457A0C999F86E31A6EF91E9036EB2517FF141F81898049DC526BA08081C81B7DF8016E8E5822D8EFBFFC756D4EF27473CF62D9197A37BE77132053480442AA6D1541541E1D242BAFCB3F0D14E5E5191F5202D4DEBEC9DBF437BE89FFDA0D7A9D469D87F2006E23BD6756398CDDBC5F9AC6177AB9F6DAA60655A9D42332524D3C3CBDCEBC5BD3C5B93D77E9866EEA4F09D32F6A372C3955DA0504F7437562667A94FBEEFF8FF1221886E4CE47E0F825B6FA8EFC786491059B83050C142F5DDB541E8F3067DD3A39C6CCD53347B7B4178370427F26B81C295E5B7A3E690D3250ECE62DB81ED47A37B2D6CAC169517DAEAC208C17ACC17B2E4515EBEED7E59EB8B0CD12184CE1FAAE0776F9C1EE312199DA732EDE772CA65FDD08A9BC84675AA0AD3DEA87BCBD16033F9B8B62F150693EED3CAC6C39B3457A0CBFD45F0BEFF86F0BE875708F6F14857E86B1ED8A36F4CAE44B49B2A26F38725A16B65104DDF0C6ABD12102E801A10DB90F2D0A82DEBA66B2C015F72BD712A478BD52D0D0D183A0DD9C567AC12D3850B14575C7486AD16EAC2E8F1A4F7D4B64AA7E1DF1D1026831971FC5EA149B9D07C2B36A7FCE28C244BA5B47760F7BE039F3F4661B34D5B8BB490F9A4368C8C2FB9ED2A00A2AB777D330BD61CA27B350F6AC957FDD33DF86F4670A228866B9E7E4EC9800182E688D9D66AF0C060BE12CC66377F81E328ED66DE39F07D1472290D91619EEB8CAE7053E514F113249B40EE1E62E1CFDFBCEBC356332395FA8776E7C82B1D49ACA0031E186894D3554A3FF5BDE484D769CC1756E85D246502D8067B4F889FE6976CD0873B2AD0E65EC11DA5DB9A9A50F866240DB0EE4B7B021CF3DDE86D047F1390D6D98912B047891D86619E0F4F9E9B8778A1E0B690F25EBD268A4C4BB62B05F27D7128F0462836AC2BA7C7480B335358E5C47A8FCE88BBFB1DC7CD40163D12907E3C2E8F8DC4074C3F09888830000000BD0FCFDEBC100336649EDA5EB8B076A161A4566ADAD35DEE74F21C103571DE6B86B188488844D3DEDFEE2E11AA266EE1DA77E2B112433DDDBA9660A8B96037F72105D66B6A3D50F00855005807447AA2DFA507B953B1422A443409A4F2594C38C11C0DDA74D26D43ADDF1B8FD77F75BF2FB86275F05EFF780BE2A2713E27CE5C766C24EEC965423EC335698BA63C5FE55B1A9ECA14145E27EC2FA2321E39A627C393CE9E6174CBE9870D49DC9C9D3B1AF8AD5BBA7095ED9AAC6E10F3F7A29C5829B877433B635760D5D9331173CB037C4E7695570BAE8C57E4176EE3F9433F71AF566BF5CFB974620FC2CF5B1968D0D5B
                """);

        try {
            verify(pk, sig, msg);
        } catch (InvalidKeySpecException ex) {
            // SHA256_M24 not supported
        }
        return true;
    }

    static boolean testProviderException() {
        // kat1 with bad bit in the signature
        // First byte changed from 61 to 71.
        var pk = decode("""
                00000002
                00000005
                00000004
                71a5d57d37f5e46bfb7520806b07a1b8
                50650e3b31fe4a773ea29a07f09cf2ea
                30e579f0df58ef8e298da0434cb2b878
                """);
        var msg = decode("""
                54686520706f77657273206e6f742064
                656c65676174656420746f2074686520
                556e6974656420537461746573206279
                2074686520436f6e737469747574696f
                6e2c206e6f722070726f686962697465
                6420627920697420746f207468652053
                74617465732c20617265207265736572
                76656420746f20746865205374617465
                7320726573706563746976656c792c20
                6f7220746f207468652070656f706c65
                2e0a""");
        var sig = decode("""
                00000001
                00000005
                00000004
                d32b56671d7eb98833c49b433c272586
                bc4a1c8a8970528ffa04b966f9426eb9
                965a25bfd37f196b9073f3d4a232feb6
                9128ec45146f86292f9dff9610a7bf95
                a64c7f60f6261a62043f86c70324b770
                7f5b4a8a6e19c114c7be866d488778a0
                e05fd5c6509a6e61d559cf1a77a970de
                927d60c70d3de31a7fa0100994e162a2
                582e8ff1b10cd99d4e8e413ef469559f
                7d7ed12c838342f9b9c96b83a4943d16
                81d84b15357ff48ca579f19f5e71f184
                66f2bbef4bf660c2518eb20de2f66e3b
                14784269d7d876f5d35d3fbfc7039a46
                2c716bb9f6891a7f41ad133e9e1f6d95
                60b960e7777c52f060492f2d7c660e14
                71e07e72655562035abc9a701b473ecb
                c3943c6b9c4f2405a3cb8bf8a691ca51
                d3f6ad2f428bab6f3a30f55dd9625563
                f0a75ee390e385e3ae0b906961ecf41a
                e073a0590c2eb6204f44831c26dd768c
                35b167b28ce8dc988a3748255230cef9
                9ebf14e730632f27414489808afab1d1
                e783ed04516de012498682212b078105
                79b250365941bcc98142da13609e9768
                aaf65de7620dabec29eb82a17fde35af
                15ad238c73f81bdb8dec2fc0e7f93270
                1099762b37f43c4a3c20010a3d72e2f6
                06be108d310e639f09ce7286800d9ef8
                a1a40281cc5a7ea98d2adc7c7400c2fe
                5a101552df4e3cccfd0cbf2ddf5dc677
                9cbbc68fee0c3efe4ec22b83a2caa3e4
                8e0809a0a750b73ccdcf3c79e6580c15
                4f8a58f7f24335eec5c5eb5e0cf01dcf
                4439424095fceb077f66ded5bec73b27
                c5b9f64a2a9af2f07c05e99e5cf80f00
                252e39db32f6c19674f190c9fbc506d8
                26857713afd2ca6bb85cd8c107347552
                f30575a5417816ab4db3f603f2df56fb
                c413e7d0acd8bdd81352b2471fc1bc4f
                1ef296fea1220403466b1afe78b94f7e
                cf7cc62fb92be14f18c2192384ebceaf
                8801afdf947f698ce9c6ceb696ed70e9
                e87b0144417e8d7baf25eb5f70f09f01
                6fc925b4db048ab8d8cb2a661ce3b57a
                da67571f5dd546fc22cb1f97e0ebd1a6
                5926b1234fd04f171cf469c76b884cf3
                115cce6f792cc84e36da58960c5f1d76
                0f32c12faef477e94c92eb75625b6a37
                1efc72d60ca5e908b3a7dd69fef02491
                50e3eebdfed39cbdc3ce9704882a2072
                c75e13527b7a581a556168783dc1e975
                45e31865ddc46b3c957835da252bb732
                8d3ee2062445dfb85ef8c35f8e1f3371
                af34023cef626e0af1e0bc017351aae2
                ab8f5c612ead0b729a1d059d02bfe18e
                fa971b7300e882360a93b025ff97e9e0
                eec0f3f3f13039a17f88b0cf808f4884
                31606cb13f9241f40f44e537d302c64a
                4f1f4ab949b9feefadcb71ab50ef27d6
                d6ca8510f150c85fb525bf25703df720
                9b6066f09c37280d59128d2f0f637c7d
                7d7fad4ed1c1ea04e628d221e3d8db77
                b7c878c9411cafc5071a34a00f4cf077
                38912753dfce48f07576f0d4f94f42c6
                d76f7ce973e9367095ba7e9a3649b7f4
                61d9f9ac1332a4d1044c96aefee67676
                401b64457c54d65fef6500c59cdfb69a
                f7b6dddfcb0f086278dd8ad0686078df
                b0f3f79cd893d314168648499898fbc0
                ced5f95b74e8ff14d735cdea968bee74
                00000005
                d8b8112f9200a5e50c4a262165bd342c
                d800b8496810bc716277435ac376728d
                129ac6eda839a6f357b5a04387c5ce97
                382a78f2a4372917eefcbf93f63bb591
                12f5dbe400bd49e4501e859f885bf073
                6e90a509b30a26bfac8c17b5991c157e
                b5971115aa39efd8d564a6b90282c316
                8af2d30ef89d51bf14654510a12b8a14
                4cca1848cf7da59cc2b3d9d0692dd2a2
                0ba3863480e25b1b85ee860c62bf5136
                00000005
                00000004
                d2f14ff6346af964569f7d6cb880a1b6
                6c5004917da6eafe4d9ef6c6407b3db0
                e5485b122d9ebe15cda93cfec582d7ab
                0000000a
                00000004
                0703c491e7558b35011ece3592eaa5da
                4d918786771233e8353bc4f62323185c
                95cae05b899e35dffd71705470620998
                8ebfdf6e37960bb5c38d7657e8bffeef
                9bc042da4b4525650485c66d0ce19b31
                7587c6ba4bffcc428e25d08931e72dfb
                6a120c5612344258b85efdb7db1db9e1
                865a73caf96557eb39ed3e3f426933ac
                9eeddb03a1d2374af7bf771855774562
                37f9de2d60113c23f846df26fa942008
                a698994c0827d90e86d43e0df7f4bfcd
                b09b86a373b98288b7094ad81a0185ac
                100e4f2c5fc38c003c1ab6fea479eb2f
                5ebe48f584d7159b8ada03586e65ad9c
                969f6aecbfe44cf356888a7b15a3ff07
                4f771760b26f9c04884ee1faa329fbf4
                e61af23aee7fa5d4d9a5dfcf43c4c26c
                e8aea2ce8a2990d7ba7b57108b47dabf
                beadb2b25b3cacc1ac0cef346cbb90fb
                044beee4fac2603a442bdf7e507243b7
                319c9944b1586e899d431c7f91bcccc8
                690dbf59b28386b2315f3d36ef2eaa3c
                f30b2b51f48b71b003dfb08249484201
                043f65f5a3ef6bbd61ddfee81aca9ce6
                0081262a00000480dcbc9a3da6fbef5c
                1c0a55e48a0e729f9184fcb1407c3152
                9db268f6fe50032a363c9801306837fa
                fabdf957fd97eafc80dbd165e435d0e2
                dfd836a28b354023924b6fb7e48bc0b3
                ed95eea64c2d402f4d734c8dc26f3ac5
                91825daef01eae3c38e3328d00a77dc6
                57034f287ccb0f0e1c9a7cbdc828f627
                205e4737b84b58376551d44c12c3c215
                c812a0970789c83de51d6ad787271963
                327f0a5fbb6b5907dec02c9a90934af5
                a1c63b72c82653605d1dcce51596b3c2
                b45696689f2eb382007497557692caac
                4d57b5de9f5569bc2ad0137fd47fb47e
                664fcb6db4971f5b3e07aceda9ac130e
                9f38182de994cff192ec0e82fd6d4cb7
                f3fe00812589b7a7ce51544045643301
                6b84a59bec6619a1c6c0b37dd1450ed4
                f2d8b584410ceda8025f5d2d8dd0d217
                6fc1cf2cc06fa8c82bed4d944e71339e
                ce780fd025bd41ec34ebff9d4270a322
                4e019fcb444474d482fd2dbe75efb203
                89cc10cd600abb54c47ede93e08c114e
                db04117d714dc1d525e11bed8756192f
                929d15462b939ff3f52f2252da2ed64d
                8fae88818b1efa2c7b08c8794fb1b214
                aa233db3162833141ea4383f1a6f120b
                e1db82ce3630b3429114463157a64e91
                234d475e2f79cbf05e4db6a9407d72c6
                bff7d1198b5c4d6aad2831db61274993
                715a0182c7dc8089e32c8531deed4f74
                31c07c02195eba2ef91efb5613c37af7
                ae0c066babc69369700e1dd26eddc0d2
                16c781d56e4ce47e3303fa73007ff7b9
                49ef23be2aa4dbf25206fe45c20dd888
                395b2526391a724996a44156beac8082
                12858792bf8e74cba49dee5e8812e019
                da87454bff9e847ed83db07af3137430
                82f880a278f682c2bd0ad6887cb59f65
                2e155987d61bbf6a88d36ee93b6072e6
                656d9ccbaae3d655852e38deb3a2dcf8
                058dc9fb6f2ab3d3b3539eb77b248a66
                1091d05eb6e2f297774fe6053598457c
                c61908318de4b826f0fc86d4bb117d33
                e865aa805009cc2918d9c2f840c4da43
                a703ad9f5b5806163d7161696b5a0adc
                00000005
                d5c0d1bebb06048ed6fe2ef2c6cef305
                b3ed633941ebc8b3bec9738754cddd60
                e1920ada52f43d055b5031cee6192520
                d6a5115514851ce7fd448d4a39fae2ab
                2335b525f484e9b40d6a4a969394843b
                dcf6d14c48e8015e08ab92662c05c6e9
                f90b65a7a6201689999f32bfd368e5e3
                ec9cb70ac7b8399003f175c40885081a
                09ab3034911fe125631051df0408b394
                6b0bde790911e8978ba07dd56c73e7ee
                """);

        try {
            verify(pk, sig, msg);
        } catch (Exception ex) { // ProviderException
            // bad public key
        }
        return true;
    }

    static boolean serializeTest() throws Exception {
        final ObjectIdentifier oid;
        var pk = decode("""
                00000002
                00000005
                00000004
                61a5d57d37f5e46bfb7520806b07a1b8
                50650e3b31fe4a773ea29a07f09cf2ea
                30e579f0df58ef8e298da0434cb2b878
                """);

        // build x509 public key
        try {
            oid = ObjectIdentifier.of(OID);
        } catch (IOException e) {
            throw new AssertionError(e);
        }

        var keyBits = new DerOutputStream().putOctetString(pk).toByteArray();
        var oidBytes = new DerOutputStream().write(DerValue.tag_Sequence,
                new DerOutputStream().putOID(oid));
        var x509encoding = new DerOutputStream().write(DerValue.tag_Sequence,
                oidBytes
                .putUnalignedBitString(new BitArray(keyBits.length * 8, keyBits)))
                .toByteArray();

        var x509KeySpec = new X509EncodedKeySpec(x509encoding);
        var pk1 = KeyFactory.getInstance(ALG).generatePublic(x509KeySpec);

        // serialize
        try (FileOutputStream fos = new FileOutputStream("PublicKey");
                ObjectOutputStream oos = new ObjectOutputStream(fos)) {
            oos.writeObject(pk1);
        }
        // deserialize
        try (FileInputStream fis = new FileInputStream("PublicKey");
                ObjectInputStream ois = new ObjectInputStream(fis)) {
            PublicKey deserializedPublicKey = (PublicKey)ois.readObject();
            return deserializedPublicKey.equals(pk1);
        }
    }

    static boolean verify(byte[] pk, byte[] sig, byte[] msg) throws Exception {
        return verifyRawKey(pk, sig, msg) && verifyX509Key(pk, sig, msg);
    }

    static boolean verifyX509Key(byte[] pk, byte[] sig, byte[] msg)
            throws Exception {
        final ObjectIdentifier oid;

        // build x509 public key
        try {
            oid = ObjectIdentifier.of(OID);
        } catch (IOException e) {
            throw new AssertionError(e);
        }

        var keyBits = new DerOutputStream().putOctetString(pk).toByteArray();
        var oidBytes = new DerOutputStream().write(DerValue.tag_Sequence,
                new DerOutputStream().putOID(oid));
        var x509encoding = new DerOutputStream().write(DerValue.tag_Sequence,
                oidBytes
                .putUnalignedBitString(new BitArray(keyBits.length * 8, keyBits)))
                .toByteArray();

        var x509KeySpec = new X509EncodedKeySpec(x509encoding);
        var pk1 = KeyFactory.getInstance(ALG).generatePublic(x509KeySpec);

        var v = Signature.getInstance(ALG);
        v.initVerify(pk1);
        v.update(msg);
        return v.verify(sig);
    }

    static boolean verifyRawKey(byte[] pk, byte[] sig, byte[] msg)
            throws Exception {
        var  provider = Security.getProvider("SUN");
        PublicKey pk1;

        // build public key
        RawKeySpec rks = new RawKeySpec(pk);
        KeyFactory kf = KeyFactory.getInstance(ALG, provider);
        pk1 = kf.generatePublic(rks);

        var v = Signature.getInstance(ALG);
        v.initVerify(pk1);
        v.update(msg);
        return v.verify(sig);
    }

    static byte[] decode(String s) {
        return HexFormat.of().parseHex(s
                        .replaceAll("//.*", "")
                        .replaceAll("\\s", ""));
    }
}
