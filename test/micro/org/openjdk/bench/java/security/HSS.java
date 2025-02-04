/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.bench.java.security;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.io.IOException;
import java.util.HexFormat;
import java.security.ProviderException;
import java.security.PublicKey;
import java.security.Security;
import java.security.Provider;
import java.security.KeyFactory;
import java.security.Signature;
import java.util.concurrent.TimeUnit;

import sun.security.util.RawKeySpec;

/**
 * Benchmark measuring HSS/LMS
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3, jvmArgs = {"--add-exports", "java.base/sun.security.util=ALL-UNNAMED"})

// Tests 1-2 are from RFC 8554, Appendix F.

public class HSS {

    static byte[] decode(String s) {
        return HexFormat.of().parseHex(s
                        .replaceAll("//.*", "")
                        .replaceAll("\\s", ""));
    }

    public static Signature getVerifier(byte[] pk) throws Exception {
        var kf = KeyFactory.getInstance("HSS/LMS", Security.getProvider("SUN"));
        var pk1 = kf.generatePublic(new RawKeySpec(pk));

        var vv = Signature.getInstance("HSS/LMS");
        vv.initVerify(pk1);
        return vv;
    }

    public static void verify(Signature v, byte[] pk, byte[] msg, byte[] sig)
            throws Exception {
        v.update(msg);
        if (!v.verify(sig)) {
            throw new RuntimeException();
        }
    }

    // RFC 8554 Test Case 1
    @State(Scope.Benchmark)
    public static class test01 {
        byte[] pk;
        byte[] msg;
        byte[] sig;

        @Param({"RFC 8554 1"})
        private String test;

        @Setup
        public void setup() throws Exception {
            pk = decode("""
                00000002
                00000005
                00000004
                61a5d57d37f5e46bfb7520806b07a1b850650e3b31fe4a773ea29a07f09cf2ea
                30e579f0df58ef8e298da0434cb2b878""");
            msg = decode("""
                54686520706f77657273206e6f742064656c65676174656420746f2074686520
                556e69746564205374617465732062792074686520436f6e737469747574696f
                6e2c206e6f722070726f6869626974656420627920697420746f207468652053
                74617465732c2061726520726573657276656420746f20746865205374617465
                7320726573706563746976656c792c206f7220746f207468652070656f706c65
                2e0a""");
            sig = decode("""
                00000001
                00000005
                00000004
                d32b56671d7eb98833c49b433c272586bc4a1c8a8970528ffa04b966f9426eb9
                965a25bfd37f196b9073f3d4a232feb69128ec45146f86292f9dff9610a7bf95
                a64c7f60f6261a62043f86c70324b7707f5b4a8a6e19c114c7be866d488778a0
                e05fd5c6509a6e61d559cf1a77a970de927d60c70d3de31a7fa0100994e162a2
                582e8ff1b10cd99d4e8e413ef469559f7d7ed12c838342f9b9c96b83a4943d16
                81d84b15357ff48ca579f19f5e71f18466f2bbef4bf660c2518eb20de2f66e3b
                14784269d7d876f5d35d3fbfc7039a462c716bb9f6891a7f41ad133e9e1f6d95
                60b960e7777c52f060492f2d7c660e1471e07e72655562035abc9a701b473ecb
                c3943c6b9c4f2405a3cb8bf8a691ca51d3f6ad2f428bab6f3a30f55dd9625563
                f0a75ee390e385e3ae0b906961ecf41ae073a0590c2eb6204f44831c26dd768c
                35b167b28ce8dc988a3748255230cef99ebf14e730632f27414489808afab1d1
                e783ed04516de012498682212b07810579b250365941bcc98142da13609e9768
                aaf65de7620dabec29eb82a17fde35af15ad238c73f81bdb8dec2fc0e7f93270
                1099762b37f43c4a3c20010a3d72e2f606be108d310e639f09ce7286800d9ef8
                a1a40281cc5a7ea98d2adc7c7400c2fe5a101552df4e3cccfd0cbf2ddf5dc677
                9cbbc68fee0c3efe4ec22b83a2caa3e48e0809a0a750b73ccdcf3c79e6580c15
                4f8a58f7f24335eec5c5eb5e0cf01dcf4439424095fceb077f66ded5bec73b27
                c5b9f64a2a9af2f07c05e99e5cf80f00252e39db32f6c19674f190c9fbc506d8
                26857713afd2ca6bb85cd8c107347552f30575a5417816ab4db3f603f2df56fb
                c413e7d0acd8bdd81352b2471fc1bc4f1ef296fea1220403466b1afe78b94f7e
                cf7cc62fb92be14f18c2192384ebceaf8801afdf947f698ce9c6ceb696ed70e9
                e87b0144417e8d7baf25eb5f70f09f016fc925b4db048ab8d8cb2a661ce3b57a
                da67571f5dd546fc22cb1f97e0ebd1a65926b1234fd04f171cf469c76b884cf3
                115cce6f792cc84e36da58960c5f1d760f32c12faef477e94c92eb75625b6a37
                1efc72d60ca5e908b3a7dd69fef0249150e3eebdfed39cbdc3ce9704882a2072
                c75e13527b7a581a556168783dc1e97545e31865ddc46b3c957835da252bb732
                8d3ee2062445dfb85ef8c35f8e1f3371af34023cef626e0af1e0bc017351aae2
                ab8f5c612ead0b729a1d059d02bfe18efa971b7300e882360a93b025ff97e9e0
                eec0f3f3f13039a17f88b0cf808f488431606cb13f9241f40f44e537d302c64a
                4f1f4ab949b9feefadcb71ab50ef27d6d6ca8510f150c85fb525bf25703df720
                9b6066f09c37280d59128d2f0f637c7d7d7fad4ed1c1ea04e628d221e3d8db77
                b7c878c9411cafc5071a34a00f4cf07738912753dfce48f07576f0d4f94f42c6
                d76f7ce973e9367095ba7e9a3649b7f461d9f9ac1332a4d1044c96aefee67676
                401b64457c54d65fef6500c59cdfb69af7b6dddfcb0f086278dd8ad0686078df
                b0f3f79cd893d314168648499898fbc0ced5f95b74e8ff14d735cdea968bee74
                00000005
                d8b8112f9200a5e50c4a262165bd342cd800b8496810bc716277435ac376728d
                129ac6eda839a6f357b5a04387c5ce97382a78f2a4372917eefcbf93f63bb591
                12f5dbe400bd49e4501e859f885bf0736e90a509b30a26bfac8c17b5991c157e
                b5971115aa39efd8d564a6b90282c3168af2d30ef89d51bf14654510a12b8a14
                4cca1848cf7da59cc2b3d9d0692dd2a20ba3863480e25b1b85ee860c62bf5136
                00000005
                00000004
                d2f14ff6346af964569f7d6cb880a1b66c5004917da6eafe4d9ef6c6407b3db0
                e5485b122d9ebe15cda93cfec582d7ab
                0000000a
                00000004
                0703c491e7558b35011ece3592eaa5da4d918786771233e8353bc4f62323185c
                95cae05b899e35dffd717054706209988ebfdf6e37960bb5c38d7657e8bffeef
                9bc042da4b4525650485c66d0ce19b317587c6ba4bffcc428e25d08931e72dfb
                6a120c5612344258b85efdb7db1db9e1865a73caf96557eb39ed3e3f426933ac
                9eeddb03a1d2374af7bf77185577456237f9de2d60113c23f846df26fa942008
                a698994c0827d90e86d43e0df7f4bfcdb09b86a373b98288b7094ad81a0185ac
                100e4f2c5fc38c003c1ab6fea479eb2f5ebe48f584d7159b8ada03586e65ad9c
                969f6aecbfe44cf356888a7b15a3ff074f771760b26f9c04884ee1faa329fbf4
                e61af23aee7fa5d4d9a5dfcf43c4c26ce8aea2ce8a2990d7ba7b57108b47dabf
                beadb2b25b3cacc1ac0cef346cbb90fb044beee4fac2603a442bdf7e507243b7
                319c9944b1586e899d431c7f91bcccc8690dbf59b28386b2315f3d36ef2eaa3c
                f30b2b51f48b71b003dfb08249484201043f65f5a3ef6bbd61ddfee81aca9ce6
                0081262a00000480dcbc9a3da6fbef5c1c0a55e48a0e729f9184fcb1407c3152
                9db268f6fe50032a363c9801306837fafabdf957fd97eafc80dbd165e435d0e2
                dfd836a28b354023924b6fb7e48bc0b3ed95eea64c2d402f4d734c8dc26f3ac5
                91825daef01eae3c38e3328d00a77dc657034f287ccb0f0e1c9a7cbdc828f627
                205e4737b84b58376551d44c12c3c215c812a0970789c83de51d6ad787271963
                327f0a5fbb6b5907dec02c9a90934af5a1c63b72c82653605d1dcce51596b3c2
                b45696689f2eb382007497557692caac4d57b5de9f5569bc2ad0137fd47fb47e
                664fcb6db4971f5b3e07aceda9ac130e9f38182de994cff192ec0e82fd6d4cb7
                f3fe00812589b7a7ce515440456433016b84a59bec6619a1c6c0b37dd1450ed4
                f2d8b584410ceda8025f5d2d8dd0d2176fc1cf2cc06fa8c82bed4d944e71339e
                ce780fd025bd41ec34ebff9d4270a3224e019fcb444474d482fd2dbe75efb203
                89cc10cd600abb54c47ede93e08c114edb04117d714dc1d525e11bed8756192f
                929d15462b939ff3f52f2252da2ed64d8fae88818b1efa2c7b08c8794fb1b214
                aa233db3162833141ea4383f1a6f120be1db82ce3630b3429114463157a64e91
                234d475e2f79cbf05e4db6a9407d72c6bff7d1198b5c4d6aad2831db61274993
                715a0182c7dc8089e32c8531deed4f7431c07c02195eba2ef91efb5613c37af7
                ae0c066babc69369700e1dd26eddc0d216c781d56e4ce47e3303fa73007ff7b9
                49ef23be2aa4dbf25206fe45c20dd888395b2526391a724996a44156beac8082
                12858792bf8e74cba49dee5e8812e019da87454bff9e847ed83db07af3137430
                82f880a278f682c2bd0ad6887cb59f652e155987d61bbf6a88d36ee93b6072e6
                656d9ccbaae3d655852e38deb3a2dcf8058dc9fb6f2ab3d3b3539eb77b248a66
                1091d05eb6e2f297774fe6053598457cc61908318de4b826f0fc86d4bb117d33
                e865aa805009cc2918d9c2f840c4da43a703ad9f5b5806163d7161696b5a0adc
                00000005
                d5c0d1bebb06048ed6fe2ef2c6cef305b3ed633941ebc8b3bec9738754cddd60
                e1920ada52f43d055b5031cee6192520d6a5115514851ce7fd448d4a39fae2ab
                2335b525f484e9b40d6a4a969394843bdcf6d14c48e8015e08ab92662c05c6e9
                f90b65a7a6201689999f32bfd368e5e3ec9cb70ac7b8399003f175c40885081a
                09ab3034911fe125631051df0408b3946b0bde790911e8978ba07dd56c73e7ee
                """);
        }
    }

    // RFC 8554 Test Case 2
    @State(Scope.Benchmark)
    public static class test02 {
        byte[] pk;
        byte[] msg;
        byte[] sig;

        @Param({"RFC 8554 2"})
        private String test;

        @Setup
        public void setup() throws Exception {
            pk = decode("""
                00000002
                00000006
                00000003
                d08fabd4a2091ff0a8cb4ed834e7453432a58885cd9ba0431235466bff9651c6
                c92124404d45fa53cf161c28f1ad5a8e""");
            msg = decode("""
                54686520656e756d65726174696f6e20696e2074686520436f6e737469747574
                696f6e2c206f66206365727461696e207269676874732c207368616c6c206e6f
                7420626520636f6e73747275656420746f2064656e79206f7220646973706172
                616765206f74686572732072657461696e6564206279207468652070656f706c
                652e0a""");
            sig = decode("""
                00000001
                00000003
                00000003
                3d46bee8660f8f215d3f96408a7a64cf1c4da02b63a55f62c666ef5707a914ce
                0674e8cb7a55f0c48d484f31f3aa4af9719a74f22cf823b94431d01c926e2a76
                bb71226d279700ec81c9e95fb11a0d10d065279a5796e265ae17737c44eb8c59
                4508e126a9a7870bf4360820bdeb9a01d9693779e416828e75bddd7d8c70d50a
                0ac8ba39810909d445f44cb5bb58de737e60cb4345302786ef2c6b14af212ca1
                9edeaa3bfcfe8baa6621ce88480df2371dd37add732c9de4ea2ce0dffa53c926
                49a18d39a50788f4652987f226a1d48168205df6ae7c58e049a25d4907edc1aa
                90da8aa5e5f7671773e941d8055360215c6b60dd35463cf2240a9c06d694e9cb
                54e7b1e1bf494d0d1a28c0d31acc75161f4f485dfd3cb9578e836ec2dc722f37
                ed30872e07f2b8bd0374eb57d22c614e09150f6c0d8774a39a6e168211035dc5
                2988ab46eaca9ec597fb18b4936e66ef2f0df26e8d1e34da28cbb3af75231372
                0c7b345434f72d65314328bbb030d0f0f6d5e47b28ea91008fb11b05017705a8
                be3b2adb83c60a54f9d1d1b2f476f9e393eb5695203d2ba6ad815e6a111ea293
                dcc21033f9453d49c8e5a6387f588b1ea4f706217c151e05f55a6eb7997be09d
                56a326a32f9cba1fbe1c07bb49fa04cecf9df1a1b815483c75d7a27cc88ad1b1
                238e5ea986b53e087045723ce16187eda22e33b2c70709e53251025abde89396
                45fc8c0693e97763928f00b2e3c75af3942d8ddaee81b59a6f1f67efda0ef81d
                11873b59137f67800b35e81b01563d187c4a1575a1acb92d087b517a8833383f
                05d357ef4678de0c57ff9f1b2da61dfde5d88318bcdde4d9061cc75c2de3cd47
                40dd7739ca3ef66f1930026f47d9ebaa713b07176f76f953e1c2e7f8f271a6ca
                375dbfb83d719b1635a7d8a13891957944b1c29bb101913e166e11bd5f34186f
                a6c0a555c9026b256a6860f4866bd6d0b5bf90627086c6149133f8282ce6c9b3
                622442443d5eca959d6c14ca8389d12c4068b503e4e3c39b635bea245d9d05a2
                558f249c9661c0427d2e489ca5b5dde220a90333f4862aec793223c781997da9
                8266c12c50ea28b2c438e7a379eb106eca0c7fd6006e9bf612f3ea0a454ba3bd
                b76e8027992e60de01e9094fddeb3349883914fb17a9621ab929d970d101e45f
                8278c14b032bcab02bd15692d21b6c5c204abbf077d465553bd6eda645e6c306
                5d33b10d518a61e15ed0f092c32226281a29c8a0f50cde0a8c66236e29c2f310
                a375cebda1dc6bb9a1a01dae6c7aba8ebedc6371a7d52aacb955f83bd6e4f84d
                2949dcc198fb77c7e5cdf6040b0f84faf82808bf985577f0a2acf2ec7ed7c0b0
                ae8a270e951743ff23e0b2dd12e9c3c828fb5598a22461af94d568f29240ba28
                20c4591f71c088f96e095dd98beae456579ebbba36f6d9ca2613d1c26eee4d8c
                73217ac5962b5f3147b492e8831597fd89b64aa7fde82e1974d2f6779504dc21
                435eb3109350756b9fdabe1c6f368081bd40b27ebcb9819a75d7df8bb07bb05d
                b1bab705a4b7e37125186339464ad8faaa4f052cc1272919fde3e025bb64aa8e
                0eb1fcbfcc25acb5f718ce4f7c2182fb393a1814b0e942490e52d3bca817b2b2
                6e90d4c9b0cc38608a6cef5eb153af0858acc867c9922aed43bb67d7b33acc51
                9313d28d41a5c6fe6cf3595dd5ee63f0a4c4065a083590b275788bee7ad875a7
                f88dd73720708c6c6c0ecf1f43bbaadae6f208557fdc07bd4ed91f88ce4c0de8
                42761c70c186bfdafafc444834bd3418be4253a71eaf41d718753ad07754ca3e
                ffd5960b0336981795721426803599ed5b2b7516920efcbe32ada4bcf6c73bd2
                9e3fa152d9adeca36020fdeeee1b739521d3ea8c0da497003df1513897b0f547
                94a873670b8d93bcca2ae47e64424b7423e1f078d9554bb5232cc6de8aae9b83
                fa5b9510beb39ccf4b4e1d9c0f19d5e17f58e5b8705d9a6837a7d9bf99cd1338
                7af256a8491671f1f2f22af253bcff54b673199bdb7d05d81064ef05f80f0153
                d0be7919684b23da8d42ff3effdb7ca0985033f389181f47659138003d712b5e
                c0a614d31cc7487f52de8664916af79c98456b2c94a8038083db55391e347586
                2250274a1de2584fec975fb09536792cfbfcf6192856cc76eb5b13dc4709e2f7
                301ddff26ec1b23de2d188c999166c74e1e14bbc15f457cf4e471ae13dcbdd9c
                50f4d646fc6278e8fe7eb6cb5c94100fa870187380b777ed19d7868fd8ca7ceb
                7fa7d5cc861c5bdac98e7495eb0a2ceec1924ae979f44c5390ebedddc65d6ec1
                1287d978b8df064219bc5679f7d7b264a76ff272b2ac9f2f7cfc9fdcfb6a5142
                8240027afd9d52a79b647c90c2709e060ed70f87299dd798d68f4fadd3da6c51
                d839f851f98f67840b964ebe73f8cec41572538ec6bc131034ca2894eb736b3b
                da93d9f5f6fa6f6c0f03ce43362b8414940355fb54d3dfdd03633ae108f3de3e
                bc85a3ff51efeea3bc2cf27e1658f1789ee612c83d0f5fd56f7cd071930e2946
                beeecaa04dccea9f97786001475e0294bc2852f62eb5d39bb9fbeef75916efe4
                4a662ecae37ede27e9d6eadfdeb8f8b2b2dbccbf96fa6dbaf7321fb0e701f4d4
                29c2f4dcd153a2742574126e5eaccc77686acf6e3ee48f423766e0fc466810a9
                05ff5453ec99897b56bc55dd49b991142f65043f2d744eeb935ba7f4ef23cf80
                cc5a8a335d3619d781e7454826df720eec82e06034c44699b5f0c44a8787752e
                057fa3419b5bb0e25d30981e41cb1361322dba8f69931cf42fad3f3bce6ded5b
                8bfc3d20a2148861b2afc14562ddd27f12897abf0685288dcc5c4982f8260268
                46a24bf77e383c7aacab1ab692b29ed8c018a65f3dc2b87ff619a633c41b4fad
                b1c78725c1f8f922f6009787b1964247df0136b1bc614ab575c59a16d089917b
                d4a8b6f04d95c581279a139be09fcf6e98a470a0bceca191fce476f9370021cb
                c05518a7efd35d89d8577c990a5e19961ba16203c959c91829ba7497cffcbb4b
                294546454fa5388a23a22e805a5ca35f956598848bda678615fec28afd5da61a
                00000006
                b326493313053ced3876db9d237148181b7173bc7d042cefb4dbe94d2e58cd21
                a769db4657a103279ba8ef3a629ca84ee836172a9c50e51f45581741cf808315
                0b491cb4ecbbabec128e7c81a46e62a67b57640a0a78be1cbf7dd9d419a10cd8
                686d16621a80816bfdb5bdc56211d72ca70b81f1117d129529a7570cf79cf52a
                7028a48538ecdd3b38d3d5d62d26246595c4fb73a525a5ed2c30524ebb1d8cc8
                2e0c19bc4977c6898ff95fd3d310b0bae71696cef93c6a552456bf96e9d075e3
                83bb7543c675842bafbfc7cdb88483b3276c29d4f0a341c2d406e40d4653b7e4
                d045851acf6a0a0ea9c710b805cced4635ee8c107362f0fc8d80c14d0ac49c51
                6703d26d14752f34c1c0d2c4247581c18c2cf4de48e9ce949be7c888e9caebe4
                a415e291fd107d21dc1f084b1158208249f28f4f7c7e931ba7b3bd0d824a4570
                00000005
                00000004
                215f83b7ccb9acbcd08db97b0d04dc2ba1cd035833e0e90059603f26e07ad2aa
                d152338e7a5e5984bcd5f7bb4eba40b7
                00000004
                00000004
                0eb1ed54a2460d512388cad533138d240534e97b1e82d33bd927d201dfc24ebb
                11b3649023696f85150b189e50c00e98850ac343a77b3638319c347d7310269d
                3b7714fa406b8c35b021d54d4fdada7b9ce5d4ba5b06719e72aaf58c5aae7aca
                057aa0e2e74e7dcfd17a0823429db62965b7d563c57b4cec942cc865e29c1dad
                83cac8b4d61aacc457f336e6a10b66323f5887bf3523dfcadee158503bfaa89d
                c6bf59daa82afd2b5ebb2a9ca6572a6067cee7c327e9039b3b6ea6a1edc7fdc3
                df927aade10c1c9f2d5ff446450d2a3998d0f9f6202b5e07c3f97d2458c69d3c
                8190643978d7a7f4d64e97e3f1c4a08a7c5bc03fd55682c017e2907eab07e5bb
                2f190143475a6043d5e6d5263471f4eecf6e2575fbc6ff37edfa249d6cda1a09
                f797fd5a3cd53a066700f45863f04b6c8a58cfd341241e002d0d2c0217472bf1
                8b636ae547c1771368d9f317835c9b0ef430b3df4034f6af00d0da44f4af7800
                bc7a5cf8a5abdb12dc718b559b74cab9090e33cc58a955300981c420c4da8ffd
                67df540890a062fe40dba8b2c1c548ced22473219c534911d48ccaabfb71bc71
                862f4a24ebd376d288fd4e6fb06ed8705787c5fedc813cd2697e5b1aac1ced45
                767b14ce88409eaebb601a93559aae893e143d1c395bc326da821d79a9ed41dc
                fbe549147f71c092f4f3ac522b5cc57290706650487bae9bb5671ecc9ccc2ce5
                1ead87ac01985268521222fb9057df7ed41810b5ef0d4f7cc67368c90f573b1a
                c2ce956c365ed38e893ce7b2fae15d3685a3df2fa3d4cc098fa57dd60d2c9754
                a8ade980ad0f93f6787075c3f680a2ba1936a8c61d1af52ab7e21f416be09d2a
                8d64c3d3d8582968c2839902229f85aee297e717c094c8df4a23bb5db658dd37
                7bf0f4ff3ffd8fba5e383a48574802ed545bbe7a6b4753533353d73706067640
                135a7ce517279cd683039747d218647c86e097b0daa2872d54b8f3e508598762
                9547b830d8118161b65079fe7bc59a99e9c3c7380e3e70b7138fe5d9be255150
                2b698d09ae193972f27d40f38dea264a0126e637d74ae4c92a6249fa103436d3
                eb0d4029ac712bfc7a5eacbdd7518d6d4fe903a5ae65527cd65bb0d4e9925ca2
                4fd7214dc617c150544e423f450c99ce51ac8005d33acd74f1bed3b17b7266a4
                a3bb86da7eba80b101e15cb79de9a207852cf91249ef480619ff2af8cabca831
                25d1faa94cbb0a03a906f683b3f47a97c871fd513e510a7a25f283b196075778
                496152a91c2bf9da76ebe089f4654877f2d586ae7149c406e663eadeb2b5c7e8
                2429b9e8cb4834c83464f079995332e4b3c8f5a72bb4b8c6f74b0d45dc6c1f79
                952c0b7420df525e37c15377b5f0984319c3993921e5ccd97e097592064530d3
                3de3afad5733cbe7703c5296263f77342efbf5a04755b0b3c997c4328463e84c
                aa2de3ffdcd297baaaacd7ae646e44b5c0f16044df38fabd296a47b3a838a913
                982fb2e370c078edb042c84db34ce36b46ccb76460a690cc86c302457dd1cde1
                97ec8075e82b393d542075134e2a17ee70a5e187075d03ae3c853cff60729ba4
                00000005
                4de1f6965bdabc676c5a4dc7c35f97f82cb0e31c68d04f1dad96314ff09e6b3d
                e96aeee300d1f68bf1bca9fc58e4032336cd819aaf578744e50d1357a0e42867
                04d341aa0a337b19fe4bc43c2e79964d4f351089f2e0e41c7c43ae0d49e7f404
                b0f75be80ea3af098c9752420a8ac0ea2bbb1f4eeba05238aef0d8ce63f0c6e5
                e4041d95398a6f7f3e0ee97cc1591849d4ed236338b147abde9f51ef9fd4e1c1
                """);
        }
    }

    @State(Scope.Thread)
    public static class verifier01 {
        Signature v;

        @Setup
        public void setup(test01 test) throws Exception {
            v = getVerifier(test.pk);
        }
    }
    @State(Scope.Thread)
    public static class verifier02 {
        Signature v;

        @Setup
        public void setup(test02 test) throws Exception {
            v = getVerifier(test.pk);
        }
    }

    @Benchmark
    public void verify01(test01 test, verifier01 v) throws Exception {
        HSS.verify(v.v, test.pk, test.msg, test.sig);
    }
    @Benchmark
    public void verify02(test02 test, verifier02 v) throws Exception {
        HSS.verify(v.v, test.pk, test.msg, test.sig);
    }
}
