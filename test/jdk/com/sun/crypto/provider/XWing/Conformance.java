import com.sun.crypto.provider.XWingKeyPairGenerator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import sun.security.pkcs.NamedPKCS8Key;
import sun.security.x509.NamedX509Key;

import javax.crypto.KEM;
import java.lang.Override;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.HexFormat;

/*
 * @test
 * @summary Validates correctness of the X-Wing implementation against published test vectors.
 * @modules java.base/com.sun.crypto.provider
 *          java.base/sun.security.x509
 *          java.base/sun.security.pkcs
 * @run junit Conformance
 */
public class Conformance {

    private static final HexFormat HEX = HexFormat.of();

    // test vectors from https://datatracker.ietf.org/doc/html/draft-connolly-cfrg-xwing-kem-08#appendix-C
    // TODO update tests, as soon as the RFC contains final test vectors
    public record TestData(byte[] sk, byte[] pk, byte[] eseed, byte[] ct, byte[] ss) {}

    private static final TestData TEST1 = new TestData(
            HEX.parseHex("7f9c2ba4e88f827d616045507605853ed73b8093f6efbc88eb1a6eacfa66ef26"),
            HEX.parseHex("""
                e2236b35a8c24b39b10aa1323a96a919a2ced88400633a7b07131713fc14b2b5b19cfc3d
                a5fa1a92c49f25513e0fd30d6b1611c9ab9635d7086727a4b7d21d34244e66969cf15b3b
                2a785329f61b096b277ea037383479a6b556de7231fe4b7fa9c9ac24c0699a0018a52534
                01bacfa905ca816573e56a2d2e067e9b7287533ba13a937dedb31fa44baced4076992361
                0034ae31e619a170245199b3c5c39864859fe1b4c9717a07c30495bdfb98a0a002ccf56c
                1286cef5041dede3c44cf16bf562c7448518026b3d8b9940680abd38a1575fd27b58da06
                3bfac32c39c30869374c05c1aeb1898b6b303cc68be455346ee0af699636224a148ca2ae
                a10463111c709f69b69c70ce8538746698c4c60a9aef0030c7924ceec42a5d36816f545e
                ae13293460b3acb37ea0e13d70e4aa78686da398a8397c08eaf96882113fe4f7bad4da40
                b0501e1c753efe73053c87014e8661c33099afe8bede414a5b1aa27d8392b3e131e9a70c
                1055878240cad0f40d5fe3cdf85236ead97e2a97448363b2808caafd516cd25052c5c362
                543c2517e4acd0e60ec07163009b6425fc32277acee71c24bab53ed9f29e74c66a0a3564
                955998d76b96a9a8b50d1635a4d7a67eb42df5644d330457293a8042f53cc7a69288f17e
                d55827e82b28e82665a86a14fbd96645eca8172c044f83bc0d8c0b4c8626985631ca87af
                829068f1358963cb333664ca482763ba3b3bb208577f9ba6ac62c25f76592743b64be519
                317714cb4102cb7b2f9a25b2b4f0615de31decd9ca55026d6da0b65111b16fe52feed8a4
                87e144462a6dba93728f500b6ffc49e515569ef25fed17aff520507368253525860f58be
                3be61c964604a6ac814e6935596402a520a4670b3d284318866593d15a4bb01c35e3e587
                ee0c67d2880d6f2407fb7a70712b838deb96c5d7bf2b44bcf6038ccbe33fbcf51a54a584
                fe90083c91c7a6d43d4fb15f48c60c2fd66e0a8aad4ad64e5c42bb8877c0ebec2b5e387c
                8a988fdc23beb9e16c8757781e0a1499c61e138c21f216c29d076979871caa6942bafc09
                0544bee99b54b16cb9a9a364d6246d9f42cce53c66b59c45c8f9ae9299a75d15180c3c95
                2151a91b7a10772429dc4cbae6fcc622fa8018c63439f890630b9928db6bb7f9438ae406
                5ed34d73d486f3f52f90f0807dc88dfdd8c728e954f1ac35c06c000ce41a0582580e3bb5
                7b672972890ac5e7988e7850657116f1b57d0809aaedec0bede1ae148148311c6f7e3173
                46e5189fb8cd635b986f8c0bdd27641c584b778b3a911a80be1c9692ab8e1bbb12839573
                cce19df183b45835bbb55052f9fc66a1678ef2a36dea78411e6c8d60501b4e60592d1369
                8a943b509185db912e2ea10be06171236b327c71716094c964a68b03377f513a05bcd99c
                1f346583bb052977a10a12adfc758034e5617da4c1276585e5774e1f3b9978b09d0e9c44
                d3bc86151c43aad185712717340223ac381d21150a04294e97bb13bbda21b5a182b6da96
                9e19a7fd072737fa8e880a53c2428e3d049b7d2197405296ddb361912a7bcf4827ced611
                d0c7a7da104dde4322095339f64a61d5bb108ff0bf4d780cae509fb22c256914193ff734
                9042581237d522828824ee3bdfd07fb03f1f942d2ea179fe722f06cc03de5b69859edb06
                eff389b27dce59844570216223593d4ba32d9abac8cd049040ef6534
                """.replaceAll("\\s", "")),
            HEX.parseHex("""
                3cb1eea988004b93103cfb0aeefd2a686e01fa4a58e8a3639ca8a1e3f9ae57e235b8cc87
                3c23dc62b8d260169afa2f75ab916a58d974918835d25e6a435085b2
                """.replaceAll("\\s", "")),
            HEX.parseHex("""
                b83aa828d4d62b9a83ceffe1d3d3bb1ef31264643c070c5798927e41fb07914a273f8f96
                e7826cd5375a283d7da885304c5de0516a0f0654243dc5b97f8bfeb831f68251219aabdd
                723bc6512041acbaef8af44265524942b902e68ffd23221cda70b1b55d776a92d1143ea3
                a0c475f63ee6890157c7116dae3f62bf72f60acd2bb8cc31ce2ba0de364f52b8ed38c79d
                719715963a5dd3842d8e8b43ab704e4759b5327bf027c63c8fa857c4908d5a8a7b88ac7f
                2be394d93c3706ddd4e698cc6ce370101f4d0213254238b4a2e8821b6e414a1cf20f6c12
                44b699046f5a01caa0a1a55516300b40d2048c77cc73afba79afeea9d2c0118bdf2adb88
                70dc328c5516cc45b1a2058141039e2c90a110a9e16b318dfb53bd49a126d6b73f215787
                517b8917cc01cabd107d06859854ee8b4f9861c226d3764c87339ab16c3667d2f49384e5
                5456dd40414b70a6af841585f4c90c68725d57704ee8ee7ce6e2f9be582dbee985e038ff
                c346ebfb4e22158b6c84374a9ab4a44e1f91de5aac5197f89bc5e5442f51f9a5937b102b
                a3beaebf6e1c58380a4a5fedce4a4e5026f88f528f59ffd2db41752b3a3d90efabe46389
                9b7d40870c530c8841e8712b733668ed033adbfafb2d49d37a44d4064e5863eb0af0a08d
                47b3cc888373bc05f7a33b841bc2587c57eb69554e8a3767b7506917b6b70498727f16ea
                c1a36ec8d8cfaf751549f2277db277e8a55a9a5106b23a0206b4721fa9b3048552c5bd5b
                594d6e247f38c18c591aea7f56249c72ce7b117afcc3a8621582f9cf71787e183dee0936
                7976e98409ad9217a497df888042384d7707a6b78f5f7fb8409e3b535175373461b77600
                2d799cbad62860be70573ecbe13b246e0da7e93a52168e0fb6a9756b895ef7f0147a0dc8
                1bfa644b088a9228160c0f9acf1379a2941cd28c06ebc80e44e17aa2f8177010afd78a97
                ce0868d1629ebb294c5151812c583daeb88685220f4da9118112e07041fcc24d5564a99f
                dbde28869fe0722387d7a9a4d16e1cc8555917e09944aa5ebaaaec2cf62693afad42a3f5
                18fce67d273cc6c9fb5472b380e8573ec7de06a3ba2fd5f931d725b493026cb0acbd3fe6
                2d00e4c790d965d7a03a3c0b4222ba8c2a9a16e2ac658f572ae0e746eafc4feba023576f
                08942278a041fb82a70a595d5bacbf297ce2029898a71e5c3b0d1c6228b485b1ade509b3
                5fbca7eca97b2132e7cb6bc465375146b7dceac969308ac0c2ac89e7863eb8943015b243
                14cafb9c7c0e85fe543d56658c213632599efabfc1ec49dd8c88547bb2cc40c9d38cbd30
                99b4547840560531d0188cd1e9c23a0ebee0a03d5577d66b1d2bcb4baaf21cc7fef1e038
                06ca96299df0dfbc56e1b2b43e4fc20c37f834c4af62127e7dae86c3c25a2f696ac8b589
                dec71d595bfbe94b5ed4bc07d800b330796fda89edb77be0294136139354eb8cd3759157
                8f9c600dd9be8ec6219fdd507adf3397ed4d68707b8d13b24ce4cd8fb22851bfe9d63240
                7f31ed6f7cb1600de56f17576740ce2a32fc5145030145cfb97e63e0e41d354274a079d3
                e6fb2e15
                """.replaceAll("\\s", "")),
            HEX.parseHex("d2df0522128f09dd8e2c92b1e905c793d8f57a54c3da25861f10bf4ca613e384")
            );

    private static final TestData TEST2 = new TestData(
            HEX.parseHex("badfd6dfaac359a5efbb7bcc4b59d538df9a04302e10c8bc1cbf1a0b3a5120ea"),
            HEX.parseHex("""
                0333285fa253661508c9fb444852caa4061636cb060e69943b431400134ae1fbc0228724
                7cb38068bbb89e6714af10a3fcda6613acc4b5e4b0d6eb960c302a0253b1f507b596f088
                4d351da89b01c35543214c8e542390b2bc497967961ef10286879c34316e6483b644fc27
                e8019d73024ba1d1cc83650bb068a5431b33d1221b3d122dc1239010a55cb13782140893
                f30aca7c09380255a0c621602ffbb6a9db064c1406d12723ab3bbe2950a21fe521b160b3
                0b16724cc359754b4c88342651333ea9412d5137791cf75558ebc5c54c520dd6c622a059
                f6b332ccebb9f24103e59a297cd69e4a48a3bfe53a5958559e840db5c023f66c10ce2308
                1c2c8261d744799ba078285cfa71ac51f44708d0a6212c3993340724b3ac38f63e82a889
                a4fc581f6b8353cc6233ac8f5394b6cca292f892360570a3031c90c4da3f02a895677390
                e60c24684a405f69ccf1a7b95312a47c844a4f9c2c4a37696dc10072a87bf41a2717d45b
                2a99ce09a4898d5a3f6b67085f9a626646bcf369982d483972b9cd7d244c4f49970f766a
                22507925eca7df99a491d80c27723e84c7b49b633a46b46785a16a41e02c538251622117
                364615d9c2cdaa1687a860c18bfc9ce8690efb2a524cb97cdfd1a4ea661fa7d08817998a
                f838679b07c9db8455e2167a67c14d6a347522e89e8971270bec858364b1c1023b82c483
                cf8a8b76f040fe41c24dec2d49f6376170660605b80383391c4abad1136d874a77ef73b4
                40758b6e7059add20873192e6e372e069c22c5425188e5c240cb3a6e29197ad17e87ec41
                a813af68531f262a6db25bbdb8a15d2ed9c9f35b9f2063890bd26ef09426f225aa1e6008
                d31600a29bcdf3b10d0bc72788d35e25f4976b3ca6ac7cbf0b442ae399b225d9714d0638
                a864bda7018d3b7c793bd2ace6ac68f4284d10977cc029cf203c5698f15a06b162d6c8b4
                fd40c6af40824f9c6101bb94e9327869ab7efd835dfc805367160d6c8571e3643ac70cba
                d5b96a1ad99352793f5af71705f95126cb4787392e94d808491a2245064ba5a7a30c0663
                01392a6c315336e10dbc9c2177c7af382765b6c88eeab51588d01d6a95747f3652dc5b5c
                401a23863c7a0343737c737c99287a40a90896d4594730b552b910d23244684206f0eb84
                2fb9aa316ab182282a75fb72b6806cea4774b822169c386a58773c3edc8229d85905abb8
                7ac228f0f7a2ce9a497bb5325e17a6a82777a997c036c3b862d29c14682ad325a9600872
                f3913029a1588648ba590a7157809ff740b5138380015c40e9fb90f0311107946f28e596
                2e21666ad65092a3a60480cd16e61ff7fb5b44b70cf12201878428ef8067fceb1e1dcb49
                d66c773d312c7e53238cb620e126187009472d41036b702032411dc96cb750631df9d994
                52e495deb4300df660c8d35f32b424e98c7ed14b12d8ab11a289ac63c50a24d52925950e
                49ba6bf4c2c38953c92d60b6cd034e575c711ac41bfa66951f62b9392828d7b45aed377a
                c69c35f1c6b80f388f34e0bb9ce8167eb2bc630382825c396a407e905108081b444ac8a0
                7c2507376a750d18248ee0a81c4318d9a38fc44c3b41e8681f87c34138442659512c4127
                6e1cc8fc4eb66e12727bcb5a9e0e405cdea21538d6ea885ab169050e6b91e1b69f7ed34b
                cbb48fd4c562a576549f85b528c953926d96ea8a160b8843f1c89c62
                """.replaceAll("\\s", "")),
            HEX.parseHex("""
                17cda7cfad765f5623474d368ccca8af0007cd9f5e4c849f167a580b14aabdefaee7eef4
                7cb0fca9767be1fda69419dfb927e9df07348b196691abaeb580b32d
                """.replaceAll("\\s", "")),
            HEX.parseHex("""
                c93beb22326705699bbc3d1d0aa6339be7a405debe61a7c337e1a91453c097a6f77c1306
                39d1aaeb193175f1a987aa1fd789a63c9cd487ebd6965f5d8389c8d7c8cfacbba4b44d2f
                be0ae84de9e96fb11215d9b76acd51887b752329c1a3e0468ccc49392c1e0f1aad61a73c
                10831e60a9798cb2e7ec07596b5803db3e243ecbb94166feade0c9197378700f8eb65a43
                502bbac4605992e2de2b906ab30ba401d7e1ff3c98f42cfc4b30b974d3316f331461ac05
                f43e0db7b41d3da702a4f567b6ee7295199c7be92f6b4a47e7307d34278e03c872fb4864
                7c446a64a3937dccd7c6d8de4d34b9dea45a0b065ef15b9e94d1b6df6dca7174d9bc9d14
                c6225e3a78a58785c3fe4e2fe6a0706f3365389e4258fbb61ecf1a1957715982b3f18444
                24e03acd83da7eee50573f6cd3ff396841e9a00ad679da92274129da277833d0524674fe
                ea09a98d25b888616f338412d8e65e151e65736c8c6fb448c9260fa20e7b2712148bcd3a
                0853865f50c1fc9e4f201aee3757120e034fd509d954b7a749ff776561382c4cb64cebcb
                b6aa82d04cd5c2b40395ecaf231bde8334ecfd955d09efa8c6e7935b1cb0298fb8b6740b
                e4593360eed5f129d59d98822a6cea37c57674e919e84d6b90f695fca58e7d29092bd70f
                7c97c6dfb021b9f87216a6271d8b144a364d03b6bf084f972dc59800b14a2c008bbd0992
                b5b82801020978f2bdddb3ca3367d876cffb3548dab695a29882cae2eb5ba7c847c3c71b
                d0150fa9c33aac8e6240e0c269b8e295ddb7b77e9c17bd310be65e28c0802136d086777b
                e5652d6f1ac879d3263e9c712d1af736eac048fe848a577d6afaea1428dc71db8c430edd
                7b584ae6e6aeaf7257aff0fd8fe25c30840e30ccfa1d95118ef0f6657367e9070f3d97a2
                e9a7bae19957bd707b00e31b6b0ebb9d7df4bd22e44c060830a194b5b8288353255b5295
                4ff5905ab2b126d9aa049e44599368c27d6cb033eae5182c2e1504ee4e3745f51488997b
                8f958f0209064f6f44a7e4de5226d5594d1ad9b42ac59a2d100a2f190df873a2e141552f
                33c923b4c927e8747c6f830c441a8bd3c5b371f6b3ab8103ebcfb18543aefc1beb6f776b
                bfd5344779f4aa23daaf395f69ec31dc046b491f0e5cc9c651dfc306bd8f2105be7bc7a4
                f4e21957f87278c771528a8740a92e2daefa76a3525f1fae17ec4362a2700988001d8600
                11d6ca3a95f79a0205bcf634cef373a8ea273ff0f4250eb8617d0fb92102a6aa09cf0c3e
                e2cad1ad96438c8e4dfd6ee0fcc85833c3103dd6c1600cd305bc2df4cda89b55ca237a3f
                9c3f82390074ff30825fc750130ebaf13d0cf7556d2c52a98a4bad39ca5d44aaadeaef77
                5c695e64d06e966acfcd552a14e2df6c63ae541f0fa88fc48263089685704506a21a0385
                6ce65d4f06d54f3157eeabd62491cb4ac7bf029e79f9fbd4c77e2a3588790c710e611da8
                b2040c76a61507a8020758dcc30894ad018fef98e401cc54106e20d94bd544a8f0e1fd05
                00342d123f618aa8c91bdf6e0e03200693c9651e469aee6f91c98bea4127ae66312f4ae3
                ea155b67
                """.replaceAll("\\s", "")),
            HEX.parseHex("f2e86241c64d60f6649fbc6c5b7d17180b780a3f34355e64a85749949c45f150")
    );

    private static final TestData TEST3 = new TestData(
            HEX.parseHex("ef58538b8d23f87732ea63b02b4fa0f4873360e2841928cd60dd4cee8cc0d4c9"),
            HEX.parseHex("""
                36244278824f77c621c660892c1c3886a9560caa52a97c461fd3958a598e749bbc8c7798
                ac8870bac7318ac2b863000ca3b0bdcbbc1ccfcb1a30875df9a76976763247083e646ccb
                2499a4e4f0c9f4125378ba3da1999538b86f99f2328332c177d1192b849413e655101289
                73f679d23253850bb6c347ba7ca81b5e6ac4c574565c731740b3cd8c9756caac39fba7ac
                422acc60c6c1a645b94e3b6d21485ebad9c4fe5bb4ea0853670c5246652bff65ce8381cb
                473c40c1a0cd06b54dcec11872b351397c0eaf995bebdb6573000cbe2496600ba76c8cb0
                23ec260f0571e3ec12a9c82d9db3c57b3a99e8701f78db4fabc1cc58b1bae02745073a81
                fc8045439ba3b885581a283a1ba64e103610aabb4ddfe9959e7241011b2638b56ba6a982
                ef610c514a57212555db9a98fb6bcf0e91660ec15dfa66a67408596e9ccb97489a09a073
                ffd1a0a7ebbe71aa5ff793cb91964160703b4b6c9c5390842c2c905d4a9f88111fed5787
                4ba9b03cf611e70486edf539767c7485189d5f1b08e32a274dc24a39c918fd2a4dfa946a
                8c897486f2c974031b2804aabc81749db430b85311372a3b8478868200b40e043f7bf4a1
                c3a08b0771b431e342ee277410bca034a0c77086c8f702b3aed2b4108bbd3af471633373
                a1ac74b128b148d1b9412aa66948cac6dc6614681fda02ca86675d2a756003c49c50f06e
                13c63ce4bc9f321c860b202ee931834930011f485c9af86b9f642f0c353ad305c66996b9
                a136b753973929495f0d8048db75529edcb4935904797ac66605490f66329c3bb36b8573
                a3e00f817b3082162ff106674d11b261baae0506cde7e69fdce93c6c7b59b9d4c759758a
                cf287c2e4c4bfab5170a9236daf21bdb6005e92464ee8863f845cf37978ef19969264a51
                6fe992c93b5f7ae7cb6718ac69257d630379e4aac6029cb906f98d91c92d118c36a6d161
                15d4c8f16066078badd161a65ba51e0252bc358c67cd2c4beab2537e42956e08a39cfccf
                0cd875b5499ee952c83a162c68084f6d35cf92f71ec66baec74ab87e2243160b64df54af
                b5a07f78ec0f5c5759e5a4322bca2643425748a1a97c62108510c44fd9089c5a7c14e57b
                1b77532800013027cff91922d7c935b4202bb507aa47598a6a5a030117210d4c49c17470
                0550ad6f82ad40e965598b86bc575448eb19d70380d465c1f870824c026d74a2522a799b
                7b122d06c83aa64c0974635897261433914fdfb14106c230425a83dc8467ad8234f086c7
                2a47418be9cfb582b1dcfa3d9aa45299b79fff265356d8286a1ca2f3c2184b2a70d15289
                e5b202d03b64c735a867b1154c55533ff61d6c296277011848143bc85a4b823040ae025a
                29293ab77747d85310078682e0ba0ac236548d905a79494324574d417c7a3457bd5fb525
                3c4876679034ae844d0d05010fec722db5621e3a67a2d58e2ff33b432269169b51f9dcc0
                95b8406dc1864cf0aeb6a2132661a38d641877594b3c51892b9364d25c63d637140a2018
                d10931b0daa5a2f2a405017688c991e586b522f94b1132bc7e87a63246475816c8be9c62
                b731691ab912eb656ce2619225663364701a014b7d0337212caa2ecc731f34438289e0ca
                4590a276802d980056b5d0d316cae2ecfea6d86696a9f161aa90ad47eaad8cadd31ae3cb
                c1c013747dfee80fb35b5299f555dcc2b787ea4f6f16ffdf66952461
                """.replaceAll("\\s", "")),
            HEX.parseHex("""
                22a96188d032675c8ac850933c7aff1533b94c834adbb69c6115bad4692d8619f90b0cdf
                8a7b9c264029ac185b70b83f2801f2f4b3f70c593ea3aeeb613a7f1b
                """.replaceAll("\\s", "")),
            HEX.parseHex("""
                0d2e38cbf17a2e2e4e0c87a94ca1e7701ae1552e02509b3b00f9c82c39e3fd435b05b912
                75f47abc9f1021429a26a346598cd6cd9efdc8adc1dbc35036d0290bf89733c835309202
                232f9bf652ea82f3d49280d6e8a3bd3135fb883445ab5b074d949c5350c7c7d6ac59905b
                dbfce6639da8a9d4b390ecc1dd05522d2956f2d37a05593996e5cb3fd8d5a9eb52417732
                e1ebf545588713b4760227115aab7ada178dadbca583b26cfedba2888a0c95b950bf07f7
                50d7aa8103798aa3470a042c0105c6a037de2f9ebc396021b2ba2c16aba696fbac3454dc
                8e053b8fa55edd45215eeb57a1eab9106fb426b375a9b9e5c3419efc7610977e72640f9f
                d1b2ec337de33c35e5a7581b2aae4d8ee86d2e0ebf82a1350714de50d2d788687878a196
                44ae4e3175e8d59dc90171b3badeff65aeaf600e5e5483a3595fdeb40cbafcbd040c29a2
                f6900533ae999d24f54dfcef748c30313ca447cdddfa57ad78eaa890e90f3f7bf8d11696
                8a5713cc75fd0408f36364fa265c5617039304eaeac4cbee6fc49b9fe2276768cdbec2d7
                3a507b543cc028dc1b154b7c2b0412254c466a94a8d6ea3a47e1743469bd45c08f54cf96
                5884be3696e961741ede16e3b1bc4feb93faaef31d911dc0cb3fa90bcda991959a9d2cbc
                817a5564c5c01177a59e9577589ea344d60cf5b0aa39f31863febd54603ca87ad2363c76
                6642a3f52557bcd9e4c05a87665842ba336b83156a677030f0bad531a8387a1486a599ca
                a748fcea7bdc1eb63f3cdb97173551ab7c1c36b69acbbdb2ff7a1e7bc70439632ddc67b9
                7f3da1f59b3c1588515957cb8a2f86ab635ce0a78b7cdf24eac3445e8fc8b79ba04da9e9
                03f49a7d912c197a84b4cfabc779b97d24788419bcf58035db99717edb9fd1c1df8c4005
                f700eabba528ddfcbaeda6dd30754f795948a34c9319ab653524b19931c7900c4167988a
                f52292fe902e746b524d20ceffb4339e8f5535f41cf35f0f8ea8b4a7b949c5d2381116b1
                46e9b913a83a3fa1c65ff9468c835fe4114554a6c66a80e1c9a6bb064b380be3c95e5595
                ec979bf1c85aa938938e3f10e72b0c87811969e8ab0d83de0b0604c4016ac3a015e19514
                089271bdc6ebf2ec56fab6018e44de749b4c36cc235e370da8466dbdc253542a2d704eb3
                316fd70d5d238cb7eaaf05966d973f62c7ef43b9a806f4ed213ac8099ea15d61a9024441
                60883f6bf441a3e1469945c9b79489ea18390f1ebc83caca10bdb8f2429877b52bd44c94
                a228ef91c392ef5398c5c83982701318ccedab92f7a279c4fddebaa7fe5e986c48b7d813
                5b3fe4cd15be2004ce73ff86b1e55f8ecd6ba5b8114315f8e716ef3ab0a64564a4644651
                166ebd68b1f783e2e443dbccadfe189368647629f1a12215840b7f1d026de2f665c2eb02
                3ff51a6df160912811ee03444ae4227fb941dc9ec4f31b445006fd384de5e60e0a5061b5
                0cb1202f863090fc05eb814e2d42a03586c0b56f533847ac7b8184ce9690bc8dece32a88
                ca934f541d4cc520fa64de6b6e1c3c8e03db5971a445992227c825590688d203523f5271
                61137334
                """.replaceAll("\\s", "")),
            HEX.parseHex("953f7f4e8c5b5049bdc771d1dffada0dd961477d1a2ae0988baa7ea6898d893f")
    );

    private static final TestData[] TEST_VECTORS = {TEST1, TEST2, TEST3};

    @ParameterizedTest
    @FieldSource("TEST_VECTORS")
    public void generateKeyPairDerand(TestData test) {
        // test derivation of pk from sk; see https://datatracker.ietf.org/doc/html/draft-connolly-cfrg-xwing-kem-08#section-5.2.1

        byte[][] generated = XWingKeyPairGenerator.generateKeyPairDerand(test.sk);
        Assertions.assertEquals(3, generated.length);
        Assertions.assertArrayEquals(test.pk, generated[0]);
    }

    @ParameterizedTest
    @FieldSource("TEST_VECTORS")
    public void encapsulateDerand(TestData test) throws GeneralSecurityException {
        // test encapsulation; see https://datatracker.ietf.org/doc/html/draft-connolly-cfrg-xwing-kem-08#section-5.4.1
        var pk = new NamedX509Key("X-Wing", "X-Wing", test.pk);
        var kem = KEM.getInstance("X-Wing", "SunJCE");
        var randomMock = new DerandomizedRandom(test.eseed);

        var encapsulator = kem.newEncapsulator(pk, randomMock);
        Assertions.assertEquals(test.ct.length, encapsulator.encapsulationSize(), "Invalid encapsulation size");
        Assertions.assertEquals(test.ss.length, encapsulator.secretSize(), "Invalid secret size");

        var encapsulated = encapsulator.encapsulate();
        Assertions.assertEquals(2, randomMock.invokeCount, "Expected two invocations of the derandomized random generator");
        Assertions.assertArrayEquals(test.ct, encapsulated.encapsulation(), "Encapsulation does not match expected value");
        Assertions.assertArrayEquals(test.ss, encapsulated.key().getEncoded(), "Secret does not match expected value");
    }

    @ParameterizedTest
    @FieldSource("TEST_VECTORS")
    public void decapsulate(TestData test) throws GeneralSecurityException {
        var pkcs8Encoded = NamedPKCS8Key.internalCreate("X-Wing", "X-Wing", test.sk, null).getEncoded();
        var kf = KeyFactory.getInstance("X-Wing", "SunJCE");
        var sk = kf.generatePrivate(new PKCS8EncodedKeySpec(pkcs8Encoded));
        var kem = KEM.getInstance("X-Wing", "SunJCE");

        var decapsulated = kem.newDecapsulator(sk).decapsulate(test.ct);
        Assertions.assertArrayEquals(test.ss, decapsulated.getEncoded(), "Decapsulated secret does not match expected value");
    }

    // Mock used to inject a derandomized seed into the KEM encapsulator
    private static class DerandomizedRandom extends SecureRandom {

        private final byte[] eseed;
        public int invokeCount = 0;

        DerandomizedRandom(byte[] eseed) {
            assert eseed.length == 64;
            this.eseed = eseed;
        }

        @Override
        public void nextBytes(byte[] bytes) {
            if (invokeCount == 0) {
                // First call by ML-KEM encapsulator, fill with the first 32 bytes of the eseed
                Assertions.assertEquals(32, bytes.length);
                System.arraycopy(eseed, 0, bytes, 0, 32);
            } else if (invokeCount == 1) {
                // Second call by X25519 ephemeral private key generator, fill with the remaining 32 bytes of the eseed
                Assertions.assertEquals(32, bytes.length);
                System.arraycopy(eseed, 32, bytes, 0, 32);
            } else {
                // No more calls expected
                Assertions.fail();
            }
            invokeCount++;
        }
    }

}
