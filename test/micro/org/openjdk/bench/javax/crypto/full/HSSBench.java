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

package org.openjdk.bench.javax.crypto.full;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import sun.security.util.RawKeySpec;

import java.security.KeyFactory;
import java.security.Security;
import java.security.Signature;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark measuring HSS/LMS
 */
@Warmup(iterations = 3, time = 3)
@Measurement(iterations = 8, time = 2)
@Fork(value = 5, jvmArgs = {"-XX:+AlwaysPreTouch", "--add-exports", "java.base/sun.security.util=ALL-UNNAMED"})
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
public class HSSBench {

    @Param({"HSS/LMS"})
    private String algorithm;  // do not change. Added for visibility

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

    public static void verify(Signature v, byte[] msg, byte[] sig)
            throws Exception {
        v.update(msg);
        if (!v.verify(sig)) {
            throw new RuntimeException();
        }
    }

    public static class TestData01 {
        static final byte[] pk = decode("""
                00000002
                00000005
                00000004
                0e975b10a6b33473d01baa138c155e81d7b7156b389b2a6a09d49f42c1ac4984
                2d977e65fceb6bc80e06eace38ce0116
                """);
        static final byte[] msg = decode("""
                312e20546869732069732061207465737420666f72204853532f4c4d53207768
                6963682069732076657279206c6f6e6720616e64206e6f74206d65616e742074
                6f207265616420627920612068756d616e206265696e672e
                """);
        static final byte[] sig = decode("""
                00000001
                00000005
                00000004
                a63c20fdbd752910d2a87d25c4ba6bb233d5dd363e5f44a25f766a477f13aa79
                1474acc5f205d27f1f89234282a2f27205bb914efa34ea8bdd8d7eab6e748614
                79b5e6088451c07b079196d0d897edc8974e3d2fa244e38242c6831b9124a298
                ed74872b0c6c5cd1c25fcc5279fb1bc30d4b1ee122b9292aedbcfd7e49d4d54b
                a1a330d88774e6de909a81624c3440b687495758c83b0439fb104d9e7a695a9e
                62247b52162121c1a80999d11e4211cd23c374a1a23e57dfc49792427806276f
                c03cc2d0958990c030e2f8c85e2922547e4ae4918833357ed8ff16ae189fbc76
                1da5a2e02f824d4ebd8ff731f9c3a8268d3056c1917129a918981bb35079a73b
                bac9cba7a99a4536ed4420def0d1ca3b9702be27c6d638fb3a8449edf699d349
                fbd44a8c80985d85946d31221942541143ede3b557929c7f2de6840b0910b6ab
                c0dac975200be5f36fbcb5d0ebe0c79d7c8b1218e6afa1fb487399f53ac2a10e
                e70113fdc20ab4e1689122568eeeacd0da903cb56ddfe54d386e6f7ab7971878
                e3dddcf7f78e31ce95c9aa6dfa26501027e42f7066ee5a94c4ccf6647d85776f
                b244ffa85794e873a12634a5468967f05a9a5efc4276944ec9664aaf9b684db4
                bd417669f2e151f174ae1eed01565b563eabf8177fbbeb2ab61667dcb7423a86
                2dbea29581cada5c0ebab53c0811ec8ebab234b7dbb30456837e82bd5e23e8a8
                a75f4331a4da821abc63d50b1bdc4bd861a0ba1ebfad6db2fd5d44e587ce9bf3
                095f314be58411b4b2ea5f0fd10bf091c5038bb8290b5964d6f24edb2aecccf2
                eb81cbcecaf1e1320853e326a8af9bb1f456105ea58b42ae9d994e35c505a74b
                50e04e406b247ff34e3ba27ddb4d7d2a6ade1e2652c25da88a8c6a76024c7c7d
                7e879597438e608741ac8bd3f35c9212db9978d7d54fda331df3e302e3b59201
                4be842a48a0abc58f5421c8f9d32e191aad082ac04bde2e3695e346d2af5e20a
                00c232def87a5a3dfca8809b1260a43403242b303197a5329b951632aab0738c
                b45a51c76f1c97a6c836f40c5165e0c68c16a8ab50b078c2b8fe09f280838648
                c6c74d59fc2c1a46ce424e28bdc7c44a683902b06e9995c67e4efd3981f5424a
                3930100b11bf7ef17c99c5d27a661e8d366ee49441278959c59e7af1ae25bd93
                a504291b3e834c5b68c1e84ee885c6e0152c7cf015aac2065ec19ba3b16166b9
                02621389a60efea12d8b4a5b5d59ab8145c804bb45694d443b09d4111faa2ba6
                e7e48d3bfa3d1c48b427f383b393c7d0e422fd1e958e14e5866cf6dfd06764c0
                fe384c6ae8e1650860dbc12b0e2731ece7ea40e11fdeba8ae7dff74375d7d717
                2ab0fb15d6c40b1677a2079b5a4af563fc4c08363176ce4da74b1ef34b0b8627
                1d405a442b60a554fa88d0320363e7b818bf532d3fbc52764479886d3f4d3d7b
                b35d0402b17025abad9cc1525e722cca4c961497f784e55f46d8f8067e4705c3
                6b62c260391575cfaa949ad99a98e9ca2a55d4e2241c33b3dd8b2505a804c6f7
                67617d02ebaefaa6cd02cc93041395d1c47be820abc647bd531549aee307cbca
                00000005
                d443111b4f3c3e02ea3043a3db682e8720e10e67852a4331f55deaf62ff8673b
                fc8e3fed9740f6cd4a276556c40de3ce8c7c0e60d7b2744f2a481a0d7854b703
                cc47a30d27ed823933e6d87c5573c135c0ced4c944f766de92f216756824c4a4
                d80b9d5337dee0498becbd5b248920283dac655346a847f93555d9a469c66858
                51891eb06cdfa25179c9054ee8795477c4806df8790becc8eb9ea458656f82a1
                00000005
                00000004
                bb8df6a42d2b9f29644c2c99967d2cc569b3a8470a960d6fa954aafe5e15660a
                9fa56cc9a583d7b1dab98da3e6c121c1
                0000000a
                00000004
                3431bace4875824faf76e488612472b3a535893056745f0f20e615806d87ba36
                ab0935d2b080fd6281fa5fca325926ba219c04a02aa2e2faca7ba0f885cb90ce
                5bf07a6dfb60f55541092cbb9386ab47438debaed31b648cb3cab090ab2c3eaf
                386a2024899e4cb83e75340933c564a22c060e5ac792316123d004125babf59e
                082dc93d4817ab8a7cd772fe28a400060ca4dcecc001203a474f294031c44dcf
                ebc3c73f672556cda6ad193883c5c95fb1587042b733f936dc0977cbc4aa13b9
                fd22794a7204968e0cdb73cc19061447617bfdcef141fb9b87538232d92b1482
                54b0917f252efc8766d6b09b0215c675ab5d41c87630121e5e536bcfea3fc134
                f57de38eff427a531ed403d903c1c96b5fd71d8431c03ca8d521a2c686051e0a
                5a57e199d65b0a60a086f70a3e42ad6b343e5565e996e7bb678ebdc1e01f9041
                289f45c3e0f56e5aeb269cfe70deca17153ee46388ac0d88b24ee7a32c607726
                1664a5d10fb5f64ed925351b2475494920acb7272962da31c8db6f76baadbc83
                9d94373f8a203ac3c62fad998913277153e1db8872f75cb3ab18f4b6ae785d35
                54785bd261b66535f34a756e8749f6d2a6049c9bf8aa991b1c5d79eae63ebde0
                99f853608dae24d68d4709c0898bf1e1fb44198a23fd7a7ecd653db67688153f
                f2a1c399cb3379903e31e4c4f06ef50671eb071a2084d16b08270ff203d51c1a
                5334d777d169c331db8e3e1c6653649d69cf1a3853423c7de5e688b10d741aa3
                5233ef53440db7a3bd6b9a613183820ea7ec1e7469d1743cf1d8aefb599f6f1c
                08865c99afd652558b73955734b36794351c246c9822e96bc276cfa7cd942f6a
                afcdb162a361332ee79a128c6ccf67d0ae3dec0b0a80e716c572162e9eb9230d
                83c3d758d4efecdfb573864f7fc1f203cf0c64d2fc9a68029ea67d7e53885c60
                c7dffc6e1af3c0460a64a8b21c62107f7a3408b724e478d0018eaee33e60350b
                7e355758d236bf8fbaa82d43955a9df4a5073ab1eab25269e89c9278097ed20c
                0d46b0722c9a0ab613ae09a5220dfffb930f013f83262f524061fb47f16dde4e
                48ba37d1e73c5b63bfd3f80ea1b36ad690476e0338271555de0e8a88a2f4ec14
                e05ab9753633ee4b792a715c6c7c1d01209619c4a4070235fb1022781f5ee437
                8514cf87b6b274bcf25551fec891fba127dcf09bbbaad957ea1e4f512b64d787
                f32538f53fdffe99e3914e9c9815c812b0bc6c1aee4d9d5a5cacd7f0d6f5ecc5
                958a595788400787615d3ab0bccdf9fc94c6bcf7154c0975d90fb60fafe0834a
                1cd44979b52548c875094b1adc9c63b5de19f3e888db7a0b7a2c238b4090c55f
                f57734c24e69df21697c650c1d8f5f62e067bda229e4ec3e9da93e6fa76a9ff9
                48327baba8c7b8b14a63291fd7fd6847e20f6653edabbc21e257827be4089fbc
                39047197e40ad2553bcdc603c82a1b80c03bf5a461f31e073295a4cbbc02530f
                be5a6c3092813f37ae9e2ec7ee8c0dc1a2379cbcfe2ad550e90f0a73f4e789a1
                270a3332e277e7113ed38527c241817d391b5a2742b6a4cb789a407525f35982
                00000005
                e6ffc1535a60978d93a81a64564b61821c7125542c0fe5996a93e0da0f5f8a71
                2d2e444d1bd96837bf0354ca844e90b82bf08746bbc95189e2268bfa686e88f9
                f90f35a095a2ab26402fc87ddf3656edfb16fe3816ffaf99e983915ebbcf2f51
                85f294d491c47fb90d3ce9046d2f05da6a723ac342a32154d1c18b465b49308f
                41ca2f0475adf5ed46413766a6057bc810aeb6dd593691b84752b883c8a1a422
                """);
    }

    public static class TestData02 {
        static final byte[] pk = decode("""
                00000002
                00000006
                00000003
                ff466afe664c2581845b2c6af92aeb6e5c4dd15affc86c82ef4e807ad3c648a6
                4561666c975fd9cb150d6c7acd6e577f
                """);
        static final byte[] msg = decode("""
                322e20546869732069732061207465737420666f72204853532f4c4d53207768
                6963682069732076657279206c6f6e6720616e64206e6f74206d65616e742074
                6f207265616420627920612068756d616e206265696e672e
                """);
        static final byte[] sig = decode("""
                00000001
                00000003
                00000003
                8b0b372cbd26c8e43b5feee870169c7c8345f7d353980ec3f6f6b81c696c672c
                7d0cfc8f74de7a0950f30151ab06c218c4ca0dc425a713060e1f14a3009ed09f
                565b6f6a07d6fee14a618a34fd02dd43745c7c11572f3e2c9a7d7c80d1d16e5c
                ca11706861fb5359bb1a8e78d2da42d528d913cc593b414fe8ceb03e71171fb6
                6722dd5677c5bd6446c372356e8d4dba0da50ce696b80deeac51fac231e59241
                84706f9dd5820a430a1d0404071a3ce75f14a8fbee1573ad893cf2f5dea0fe21
                f899305b15fb971b785cb7432ca8a92b13fe055d7a1ddc46628b9591291bceba
                0c9c6d76d8c24b919c2c1b6d5c1dcdcfc177e6c7ac67ef0af08222d6780bd686
                439b78a5575494330d824b8b6962652b15b3d5d4f8ce2216033741f51b6e3aab
                c69817097649124460c9ddd7ffb14df8ed1de436f0958e193ba118334fc12859
                68ae32b6c3d1af8b8c95e4a620e442efb221ae5eb1953c7b8dfe645587916a4e
                c26f60ca2b088accfcf5f8f724b558c527f31cf3ed9311315a629d81f2702704
                56f237d745ea92ba6777d934df150ed9b3e10ce5a8a16011118081287463793c
                3818c2448b10e1ede5bfa8da646d9417a97ae70db9aa8df030c6019dab6ebb18
                6b9d4db14b3d9b33d1df3b23169963e371e2ec25bc3d932b8503ccbbe85d5675
                c433e62b5926de825420727da4c6bd70dd93fc4fcf9062d4f2acb96699b910db
                9788ffd122d88911f98e12fc57551b8282e5f296cc0dd075121c88e1a5838c3e
                239e2968af2eceec9ee4f7c3433d3145f2b7345d7c418febd0839fd45771debe
                52c0cefb71a38b55ddfb9b8386ec6e7fb39047df9e963d056d6e0b02a2620ba1
                f58264de9b09347ac0320919df80e5a66ebee3d6e801792b19a31c07cf28fff5
                85b4788aec445d4d04ca005f6a240d8e90c6bc2398df5ec6a9a1858549b67026
                b6c4a91d10faf47fcc7afa56d228d0518fb7a786d2f24decc476203fd84150e9
                165f11ec6eb168934bb7055ae2ae0f499ec8205565fcd397c6cb1c8f0bafa90f
                6c61a08a4657a19009085217ac255081bf280c726f2c818c543bde5fd2a67549
                efccf9e0d35144f5c8af2c69dda3b01b7333a31d6d6210475f0168db67c22940
                4d36818b6d530fb23941ae4c63226ec6135e598c879d6ab88928f84da104ee82
                30d96494b818b9d7004b2f15c7565dee0f611d1faf2ef538c70f2c08919e7919
                25b81e658743cf8903e40a1323bec2530288820f2c46e85f0622e50eed0a93fe
                5cf92db7d672d2d3186710c0b7e509afd1d498cb21936befc8029c936f9e1f6c
                587071dae0b5999e407929199f4bf03dea05bded0ddc6ccba2a8e880e972f876
                142c99fbefd3a362b27416cc2aadd8c595ab31cc53af7cfd87362118e9cb409c
                32cf3600eecd314073e8393fe390e3ed60d105a7d054143227874e96a1a16ce8
                1f9a17f03aa647ef08670e5ba7e561c025775a574ee84bd294b808c2908b7112
                d02f4d6462be588bf27d418545a64dc6fc472e743c913bf7f702356872166a4c
                9fcda456ca69651832c72786546a9e9011511bfdf7351f2333f944ac82e5f14e
                a1be1ad7b0b01caf273c9f3ef540cc79c47c7b32a8febcf8e3c34a3a0641e09a
                fbcb9a1c6bbf594e44faad8671d6e4c9d8bc053db2ebdd2e22ddd8a154462b23
                6b47d1fc2205a4e7405d8e883189d8b863b31152459e602bc6c30d0baed658a6
                4f2ac6858ea5e11ac3fae173f1a251cd7e5b26693bf994a952cc12a9b684a919
                77510077cc11002a325697eb0a7f86f7b45275e13e89d959a593d51c98cbb48f
                bea795ac5d5e61c4360431bb0712096c1e88a03d2367b2772f34ca938a4c0180
                68aa0f90314b6b23ae01437178bbf812590e347a7759581748d877a31c94a7eb
                9e839b0f2654a354faae234a819fbab9c32374f3b99ea7deb42e6a169e03d59a
                0ff3a6b4809059fcd4e5c426e5d580f6fff8e49be1c9a1baacad5b1cf0284060
                6969a2c6d04fbb30952ea3f792a60e517cdd3cd8f427c335b9a66ba6ac321657
                beb77de5e2b8d4a8f5c425665b9cadd6a379b7ef5ba513cfb2b2022d8e057637
                38d0edad6a24b1e57d7ebd11434a1e6fb00c1f5f9228bcdd2f24c468b7b4408c
                35b54e09fe59ed0dbab9760b3fe415c277e39a0bdd74783d7d0feb4d7428b609
                319f8e667899fb52c822076dfd07286d8c6058c2fcf8618cb77d0ba1077f49c8
                8f8ef55e980307dfa8d9a3076d8a7ba32f1c822e1c6bf7beb997e03181bade32
                b0b69507210466acd24cc9e13e4913c7e31dbfcf84021fe947e37f469de19488
                097852097888f11de144fc03606c68d63c7309c054853e1c66ca1b499898fd8b
                5edc934e7f368022c2576e18db909b88a8f9c6caa5fdc00f7da47995ad2e21f9
                b9e3133321ec6e7f6d43ee30b5376b846ce6390e1168b0543e4a50d961f82588
                58569f6a98e5bcad0dd90c4b88e982f34e7ad2897d8218aaa9284d947ddba4eb
                25a1f02ea59504d82c1e570a2804dd9fbde7dfaaa40996e9aeb0a3d65c27e3cf
                d81005ad070375c868ac754bd849700c52b10828deab05eefd2d0575a3ef7338
                d7ae9fe2c1c6c0f38abb7a63381a6903035ce87b93e0c4927e6b15a32d8bf86c
                fd9f9bd21366a2a264ebf96bb1213ae8bf7e193bd3eb041a7beebc8d5c474b2d
                1a2afd2488dd1f5176e99b11d6986bd8a30f2c4de7abc0a8b9d18e591c2e7deb
                05944d7661cce336f0c08ed3283a8500d91030e2553b1fa7839ff232ac8f70ee
                e1656c964a627bec73bf015b10b59551a38cc0a7b95b4cce2f8292ea28c85c20
                d51a97d0e0c59f99b9af2fc7585cb97999c298885522e6bd8a102cbeb891eeca
                a7d3a2402c0a00ab85f66f0267fbb707ea1052f1f926e4328bfc31cbbfab6689
                5f290ffb90970a62e506ef39513a7d3792c0c3f8d7cd285db8326c6b0d5d4323
                261ef9ccaefb1fa8e68b9de82345578729904ad9de4f2c5640fddef0c4f54ca2
                e12115befeecd8145be43d3ade502970e255cae2af3cc221569592056eb9f61f
                fe511566f235325bac531d8b25bcbf31429426d002f265a4971927246efe218e
                00000006
                c3aba396848a87900478ae558564722df671d145ad178dd9ef736fc5c353a8db
                82e4d9999c4c2e0fae928a66cd3aaf71678ff745d5726d65b0dd6a0ee5f85ca5
                cecd79ef77e4aa47d284a0bdb71662148029d8d891e2381bb7e6045efae1f641
                505c4b31a8d5acae51cbef029b047e52a5b495aabeae20ee94f8c56ae9b0ac6c
                2d01207d212698865cb0a47e73b6247e777118a617c4b49a558dd4f1d0886f19
                cc2344598bf5cd9039bf7879d37abc66aa3947caf939425adc599d3d1190747c
                dce7f61a12f92bd2c1ad95eda9a3b2af26c8ee652b9be2f36e1f80b2ef37bd23
                2a0d94cc605402848df45afd8ae36d729b25f00d1a09e8fbac6e323669227506
                d0b7b2bab0b320bf58e69a6879e67880c6818ce83f8630c91ab0e82a3f54ceeb
                1053a66d540d800956b248e951695e52c4290014c4fd7eee8c93393e593cf54c
                00000005
                00000004
                0f5cf24b2d72360a58d71392202a49fbef628a53b11e3617bbc1723a879a89e4
                c64e0e5dea7faf8cac58d4e7fcd71774
                00000004
                00000004
                4849cdc5ce9923584e66bda4a1e96c856ead6a30672d94cab9ba1dcffe30843a
                646bd1ecba04f13bf7c6d7d8aabebfa8cb9a2190cd93d65d921ab7035cc81951
                d073609e5dedeb6ef7d00e2ed4c2680772077a9ce3f99ca74eb97f91339a81b1
                bb1c0dc10ad799ea594d4e7760463b4e441661c5f8e09ee9cc82c461dcfdf13d
                391db836b3d30fb42d33cabccb8ef9c81d054114e298a8100ac8ba9bcee4f518
                afd7bd2a158db134dd5ed299435ab7d2dc32db8b7fe35b24186e3c4b6114f7d0
                46ba7de17e65ce9e1c5618c308c650a828d1c0f786a3d6a69ebbf04e51449308
                868ecd7a5ce8f87f796396dc4837d08740376205249d8517a47c2ba816ab6c9c
                8bd8fd841ba4667ec53d353a661ea9e5ffa1769d981e4a31178e4f5ad3d99623
                d58c0ab2514b3c5306f3c83927f1b11a3784a384322780b100b44369f80b340f
                8212ad7f5092c44178a22a54110da8f8d3e8ac473aaa687587ea725806f44eaa
                c1e340bb7aa801b455914418c1cdd6b59007f4ac5e9d519f2158762e40957507
                3d6f30736f065bb74cd2c5d60b917cbad94af657ef37c1b92938c959e21d23f1
                6950c38c2f6eeede9b1675dcc4b4985bf892f3a2a0ac601bcde4acde5ccdf71f
                0a850d9813f92f7279482ae2697fed0722d456d8557ab5bb3fd49421f8d2e71f
                06769fcd695fc186ecb0236051b534bb6a6641d859fd013afa975c0bf889b900
                433581712b4b04c758fc121deea327817456f70135c3db767e16041f933186c4
                081fe52635b917bc809c2063d035e14fb512a452c8f443f2a66362ea6b08ddd1
                6d3a04065611cc962ccad06598fce7821837302ba00d57a83654130f582ce62c
                3266cae361d7db6f2b740ed7f90dca1ff4bf9ec4a0b7cbce226390bc2c38b3bf
                a27a40508139f985749420e6fc4ebafcf4799248661e1134e5442f1ef8714739
                1d589b45ecb61ac90ad6f82cb2bc3f006b62c40dcda5c362b5d4a8c56e42f672
                1b8598de29d5d6c1293b3751d48ccff5c5805a59a41c00a6758913c86861f2ad
                2400f93fec76e1062db46e4e21f70d7881b7c91bafb9cd34afbfb05ce072fb3d
                d4d9970ea49b57ed3ac73a2d75973f8d2a29de6e963d10bc62b695359d5807ed
                7ea0eb6908207698d08e96b619755872e840248550f053cc2597a65df404577d
                828c4604dc143cd42df691daae06a50502d7ad26d577478a00131fb4de24e780
                39b56b4fb1408a26fa72a9f5db80fe39e49e414a27d57ab3abef508628812bea
                6f61e9e182148ddd920efb7493d3b68a79c3946c8dec31fe6963e8c2dea92dd7
                9f789b20036b493bbdf605f45654498b93493181b71f261194f1faa6180f23ae
                6417720055d0b966a650e5f217bbfb1c711470fc9014874f8283d6efcabed894
                ec72f5887580f5fb9eb5e9be81a9e1f8ad650acd07069d499720815d052f4e11
                8d14be427742a206f275a1b6570f684feb8c4cd62cbc89d0a93de601a7f0ff40
                4125b7d29c1820d0b26490efde697f1699f4490a8b5db33a07af8df86da46817
                2ebb99d63c45a3786d2be23395d5761f1c59ee591e5ecf35ba5cf2b5d39e8615
                00000005
                2510a21e43c9b53b86557646b2c891ba432813ca61fffb57c4a8e598753542c1
                a179d3af5254a37c01eeb4393d626771858d06041c76f1960e754e9e04aeb91b
                1d9b7736193f49e15c47f44a8f1c8aca6133c34eeb682fdccf94886fe80d971a
                b4a0bb3b72197d2d5b2111da8647be1675983e8ed1c0d8ec7cada282dc698656
                95f1e8806c7892b65fc17103ee3b5366b3fe31e57e653336be283962f488eaa5
                """);
    }

    @State(Scope.Thread)
    public static class Verifier01 {
        Signature v;

        @Setup
        public void setup() throws Exception {
            v = getVerifier(TestData01.pk);
        }
    }
    @State(Scope.Thread)
    public static class Verifier02 {
        Signature v;

        @Setup
        public void setup() throws Exception {
            v = getVerifier(TestData02.pk);
        }
    }

    @Benchmark
    public void verify01(Verifier01 v) throws Exception {
        verify(v.v, TestData01.msg, TestData01.sig);
    }
    @Benchmark
    public void verify02(Verifier02 v) throws Exception {
        verify(v.v, TestData02.msg, TestData02.sig);
    }
}
