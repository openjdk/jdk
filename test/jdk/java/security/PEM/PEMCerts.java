/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

import javax.crypto.EncryptedPrivateKeyInfo;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecurityObject;
import java.security.cert.Certificate;
import java.security.interfaces.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

class PEMCerts {
    public static final String ecprivpem = """
        -----BEGIN PRIVATE KEY-----\
        MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgkW3Jx561NlEgBnut\
        KwDdi3cNwu7YYD/QtJ+9+AEBdoqhRANCAASL+REY4vvAI9M3gonaml5K3lRgHq5w\
        +OO4oO0VNduC44gUN1nrk7/wdNSpL+xXNEX52Dsff+2RD/fop224ANvB\
        -----END PRIVATE KEY-----\
        """;

    public static final String privpem = """
        -----BEGIN PRIVATE KEY-----\
        MIICeAIBADANBgkqhkiG9w0BAQEFAASCAmIwggJeAgEAAoGBAOtjMnCzPy4jCeZb\
        OdOvmvU3jl7+cvPFgL5MfqDCM5a8yI0yImg/hzibJJHLk3emUVBSnekgHvCqyGLW\
        3qGR2DuBEaMy0mkg8hfKcSpHLaYjDYaspO27d2qtb6d1qtsPoPjJFjWFYeW6K463\
        OHG654K5/2FcJgQdlLVyp3zCiQU/AgMBAAECgYEAwNkDkTv5rlX8nWLuLJV5kh/T\
        H9a93SRZxw8qy5Bv7bZ7ZNrHP7uUkHbi7iPojKWRhwo43692SdzR0dCSk7LGgN9q\
        CYvndsYR6gifVGBi0WF+St4+NdtcQ3VlNdsojy2BdIx0oC+r7i3bn+zc968O/kI+\
        EgdgrMcjjFqyx6tMHpECQQD8TYPKGHyN7Jdy28llCoUX/sL/yZ2vIi5mnDAFE5ae\
        KZQSkNAXG+8i9Qbs/Wdd5S3oZDqu+6DBn9gib80pYY05AkEA7tY59Oy8ka7nBlGP\
        g6Wo1usF2bKqk8vjko9ioZQay7f86aB10QFcAjCr+cCUm16Lc9DwzWl02nNggRZa\
        Jz8eNwJBAO+1zfLjFOPa14F/JHdlaVKE8EwKCFDuztsapd0M4Vtf8Zk6ERsDpU63\
        Ml9T2zOwnM9g+whpdjDAZ59ATdJ1JrECQQDReJQ2SxeL0lGPCiOLu9RcQp7L81aF\
        79G1bgp8WlAyEjlAkloiqEWRKiz7DDuKFR7Lwhognng9S+n87aS+PS57AkBh75t8\
        6onPAs4hkm+63dfzCojvEkALevO8J3OVX7YS5q9J1r75wDn60Ob0Zh+iiorpx8Ob\
        WqcWcoJqfdLEyBT+\
        -----END PRIVATE KEY-----\
        """;

    public static final String privpembc = """
        -----BEGIN PRIVATE KEY-----\
        MIICeAIBADANBgkqhkiG9w0BAQEFAASCAmIwggJeAgEAAoGBAOtjMnCzPy4jCeZb\
        OdOvmvU3jl7+cvPFgL5MfqDCM5a8yI0yImg/hzibJJHLk3emUVBSnekgHvCqyGLW\
        3qGR2DuBEaMy0mkg8hfKcSpHLaYjDYaspO27d2qtb6d1qtsPoPjJFjWFYeW6K463\
        OHG654K5/2FcJgQdlLVyp3zCiQU/AgMBAAECgYEAwNkDkTv5rlX8nWLuLJV5kh/T\
        H9a93SRZxw8qy5Bv7bZ7ZNrHP7uUkHbi7iPojKWRhwo43692SdzR0dCSk7LGgN9q\
        CYvndsYR6gifVGBi0WF+St4+NdtcQ3VlNdsojy2BdIx0oC+r7i3bn+zc968O/kI+\
        EgdgrMcjjFqyx6tMHpECQQD8TYPKGHyN7Jdy28llCoUX/sL/yZ2vIi5mnDAFE5ae\
        KZQSkNAXG+8i9Qbs/Wdd5S3oZDqu+6DBn9gib80pYY05AkEA7tY59Oy8ka7nBlGP\
        g6Wo1usF2bKqk8vjko9ioZQay7f86aB10QFcAjCr+cCUm16Lc9DwzWl02nNggRZa\
        Jz8eNwJBAO+1zfLjFOPa14F/JHdlaVKE8EwKCFDuztsapd0M4Vtf8Zk6ERsDpU63\
        Ml9T2zOwnM9g+whpdjDAZ59ATdJ1JrECQQDReJQ2SxeL0lGPCiOLu9RcQp7L81aF\
        79G1bgp8WlAyEjlAkloiqEWRKiz7DDuKFR7Lwhognng9S+n87aS+PS57AkBh75t8\
        6onPAs4hkm+63dfzCojvEkALevO8J3OVX7YS5q9J1r75wDn60Ob0Zh+iiorpx8Ob\
        WqcWcoJqfdLEyBT+\
        -----END PRIVATE KEY-----\
        """;

    public static final String privpemed25519 = """
        -----BEGIN PRIVATE KEY-----\
        MC4CAQAwBQYDK2VwBCIEIFFZsmD+OKk67Cigc84/2fWtlKsvXWLSoMJ0MHh4jI4I\
        -----END PRIVATE KEY-----\
        """;

    public static final String pubrsapem = """
        -----BEGIN PUBLIC KEY-----\
        MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDrYzJwsz8uIwnmWznTr5r1N45e\
        /nLzxYC+TH6gwjOWvMiNMiJoP4c4mySRy5N3plFQUp3pIB7wqshi1t6hkdg7gRGj\
        MtJpIPIXynEqRy2mIw2GrKTtu3dqrW+ndarbD6D4yRY1hWHluiuOtzhxuueCuf9h\
        XCYEHZS1cqd8wokFPwIDAQAB\
        -----END PUBLIC KEY-----\
        """;

    public static final String pubrsapembc = """
        -----BEGIN PUBLIC KEY-----\
        MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDrYzJwsz8uIwnmWznTr5r1N45e\
        /nLzxYC+TH6gwjOWvMiNMiJoP4c4mySRy5N3plFQUp3pIB7wqshi1t6hkdg7gRGj\
        MtJpIPIXynEqRy2mIw2GrKTtu3dqrW+ndarbD6D4yRY1hWHluiuOtzhxuueCuf9h\
        XCYEHZS1cqd8wokFPwIDAQAB\
        -----END PUBLIC KEY-----\
        """;

    public static final String pubrsaold =
        "-----BEGIN PUBLIC KEY-----\n" +
            "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDrYzJwsz8uIwnmWznTr5r1N45e\n" +
            "/nLzxYC+TH6gwjOWvMiNMiJoP4c4mySRy5N3plFQUp3pIB7wqshi1t6hkdg7gRGj\n" +
            "MtJpIPIXynEqRy2mIw2GrKTtu3dqrW+ndarbD6D4yRY1hWHluiuOtzhxuueCuf9h\n" +
            "XCYEHZS1cqd8wokFPwIDAQAB\n" +
            "-----END PUBLIC KEY-----\n";

//    public static final String pubecpem = """
//        -----BEGIN PUBLIC KEY-----\
//        MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEi/kRGOL7wCPTN4KJ2ppeSt5UYB6u\
//        cPjjuKDtFTXbguOIFDdZ65O/8HTUqS/sVzRF+dg7H3/tkQ/36KdtuADbwQ==\
//        -----END PUBLIC KEY-----\
//        """;

    public static final String pubecpem =
        "-----BEGIN PUBLIC KEY-----\r\n" +
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEi/kRGOL7wCPTN4KJ2ppeSt5UYB6u\r\n" +
            "cPjjuKDtFTXbguOIFDdZ65O/8HTUqS/sVzRF+dg7H3/tkQ/36KdtuADbwQ==\r\n" +
            "-----END PUBLIC KEY-----\r\n";

    public static final String pubec_explicit = """
        -----BEGIN PUBLIC KEY-----\
        MIIBSzCCAQMGByqGSM49AgEwgfcCAQEwLAYHKoZIzj0BAQIhAP////8AAAABAAAA\
        AAAAAAAAAAAA////////////////MFsEIP////8AAAABAAAAAAAAAAAAAAAA////\
        ///////////8BCBaxjXYqjqT57PrvVV2mIa8ZR0GsMxTsPY7zjw+J9JgSwMVAMSd\
        NgiG5wSTamZ44ROdJreBn36QBEEEaxfR8uEsQkf4vOblY6RA8ncDfYEt6zOg9KE5\
        RdiYwpZP40Li/hp/m47n60p8D54WK84zV2sxXs7LtkBoN79R9QIhAP////8AAAAA\
        //////////+85vqtpxeehPO5ysL8YyVRAgEBA0IABIv5ERji+8Aj0zeCidqaXkre\
        VGAernD447ig7RU124LjiBQ3WeuTv/B01Kkv7Fc0RfnYOx9/7ZEP9+inbbgA28E=\
        -----END PUBLIC KEY-----\
        """;

    public static final String encprivpem = """
        -----BEGIN ENCRYPTED PRIVATE KEY-----\
        MIIC3TBXBgkqhkiG9w0BBQ0wSjApBgkqhkiG9w0BBQwwHAQIHjNFO2U/GIICAggA\
        MAwGCCqGSIb3DQILBQAwHQYJYIZIAWUDBAEqBBCM8PGUSHbDu40eUO/qYYSuBIIC\
        gPrw1becq4PBdLG/lk2PGpFQGp9uf3Yvn9fT5LBZl6qFuMXaecWxfjrly6kTPrZ4\
        w+KjWEKKXZfhFeeuPv5uAeW2eS/lBQK8PypAryJZPbZz9F6MIGPL9AFpmZcYLi7x\
        QhRaIRAgr2rF5PoC+lfuAg29HbM5hXdMuW9LcbDoTe8IQK/tPiT17cCax9YNiVWn\
        0oxUQbuAsuLHadcL0Q5/6WAk296EIqzMSZrhmMIvYvU4VHSI6h5S8Bmq0GQhChvn\
        uwrSc2NUrQTT/CwbcYdWDxCkA0XMXF82fF38TWSwV1IindgKY0h/vNLTpjiFqR87\
        7IfPFtfoyRE3awOCim61nHLj9Y3BRAYi9giyu0rLXti1lfkEod/Z0fE8iNirqxRP\
        LBrumIpvEcgTpkvjSSlRdwYGX2btfYZRsfHg1ERps5ozgxGyByCZDRJXs6OAduFG\
        wm2OvsastpHnB8t2G9jYZCodzNX6TLhZDMUOeKixcBNfn3qhyKAaEivwDrenPKUr\
        yO3I3hL8jiQ8x2f9jL7esU98ukLHKxMseYCpzNXVUGOeUcLRisRnbpHJYWs+D/Nv\
        8vlBpjdXrFbvf8TCoh41jgM+YnhAf4v4mJIcrwd3RnIwnhgofxND0abe6eMEOsah\
        CL/RIPDsCMkOukQKR69sQe478ueGBZfqXR4jEL4XvLz5GZcJTVwNPsEQExH6JhVr\
        dSub0dsy9FWZVJftrf4EMW3evS39IPVm0ld35i7HPTeb09a6Yqwq+bhKqYFhh4ZN\
        31eg0x2k0OccwCVJk4du510Fzw0T2nx9KdGW7y8UeRdPozGwKs6a9o4wefMG+UZF\
        XnpbZ9Cfa0c1Z4z37O290LY=\
        -----END ENCRYPTED PRIVATE KEY-----\
        """;

    public static final String oastestpem = """
        -----BEGIN PRIVATE KEY-----\
        MIIDCQIBATCCAwIGCSqGSIb3DQEBAQSCAmIwggJeAgEAAoGBAOtjMnCzPy4jCeZbOdOvmvU3jl7+\
        cvPFgL5MfqDCM5a8yI0yImg/hzibJJHLk3emUVBSnekgHvCqyGLW3qGR2DuBEaMy0mkg8hfKcSpH\
        LaYjDYaspO27d2qtb6d1qtsPoPjJFjWFYeW6K463OHG654K5/2FcJgQdlLVyp3zCiQU/AgMBAAEC\
        gYEAwNkDkTv5rlX8nWLuLJV5kh/TH9a93SRZxw8qy5Bv7bZ7ZNrHP7uUkHbi7iPojKWRhwo43692\
        SdzR0dCSk7LGgN9qCYvndsYR6gifVGBi0WF+St4+NdtcQ3VlNdsojy2BdIx0oC+r7i3bn+zc968O\
        /kI+EgdgrMcjjFqyx6tMHpECQQD8TYPKGHyN7Jdy28llCoUX/sL/yZ2vIi5mnDAFE5aeKZQSkNAX\
        G+8i9Qbs/Wdd5S3oZDqu+6DBn9gib80pYY05AkEA7tY59Oy8ka7nBlGPg6Wo1usF2bKqk8vjko9i\
        oZQay7f86aB10QFcAjCr+cCUm16Lc9DwzWl02nNggRZaJz8eNwJBAO+1zfLjFOPa14F/JHdlaVKE\
        8EwKCFDuztsapd0M4Vtf8Zk6ERsDpU63Ml9T2zOwnM9g+whpdjDAZ59ATdJ1JrECQQDReJQ2SxeL\
        0lGPCiOLu9RcQp7L81aF79G1bgp8WlAyEjlAkloiqEWRKiz7DDuKFR7Lwhognng9S+n87aS+PS57\
        AkBh75t86onPAs4hkm+63dfzCojvEkALevO8J3OVX7YS5q9J1r75wDn60Ob0Zh+iiorpx8ObWqcW\
        coJqfdLEyBT+gQOBjQAwgYkCgYEA62MycLM/LiMJ5ls506+a9TeOXv5y88WAvkx+oMIzlrzIjTIi\
        aD+HOJskkcuTd6ZRUFKd6SAe8KrIYtbeoZHYO4ERozLSaSDyF8pxKkctpiMNhqyk7bt3aq1vp3Wq\
        2w+g+MkWNYVh5borjrc4cbrngrn/YVwmBB2UtXKnfMKJBT8CAwEAAQ==\
        -----END PRIVATE KEY-----\
        """;

    public static final String oasminepem = """
        -----BEGIN PRIVATE KEY-----\
        MIIDCAIBATANBgkqhkiG9w0BAQEFAASCAmIwggJeAgEAAoGBAOtjMnCzPy4jCeZbOdOvmvU3jl7+\
        cvPFgL5MfqDCM5a8yI0yImg/hzibJJHLk3emUVBSnekgHvCqyGLW3qGR2DuBEaMy0mkg8hfKcSpH\
        LaYjDYaspO27d2qtb6d1qtsPoPjJFjWFYeW6K463OHG654K5/2FcJgQdlLVyp3zCiQU/AgMBAAEC\
        gYEAwNkDkTv5rlX8nWLuLJV5kh/TH9a93SRZxw8qy5Bv7bZ7ZNrHP7uUkHbi7iPojKWRhwo43692\
        SdzR0dCSk7LGgN9qCYvndsYR6gifVGBi0WF+St4+NdtcQ3VlNdsojy2BdIx0oC+r7i3bn+zc968O\
        /kI+EgdgrMcjjFqyx6tMHpECQQD8TYPKGHyN7Jdy28llCoUX/sL/yZ2vIi5mnDAFE5aeKZQSkNAX\
        G+8i9Qbs/Wdd5S3oZDqu+6DBn9gib80pYY05AkEA7tY59Oy8ka7nBlGPg6Wo1usF2bKqk8vjko9i\
        oZQay7f86aB10QFcAjCr+cCUm16Lc9DwzWl02nNggRZaJz8eNwJBAO+1zfLjFOPa14F/JHdlaVKE\
        8EwKCFDuztsapd0M4Vtf8Zk6ERsDpU63Ml9T2zOwnM9g+whpdjDAZ59ATdJ1JrECQQDReJQ2SxeL\
        0lGPCiOLu9RcQp7L81aF79G1bgp8WlAyEjlAkloiqEWRKiz7DDuKFR7Lwhognng9S+n87aS+PS57\
        AkBh75t86onPAs4hkm+63dfzCojvEkALevO8J3OVX7YS5q9J1r75wDn60Ob0Zh+iiorpx8ObWqcW\
        coJqfdLEyBT+gYGNADCBiQKBgQDrYzJwsz8uIwnmWznTr5r1N45e/nLzxYC+TH6gwjOWvMiNMiJo\
        P4c4mySRy5N3plFQUp3pIB7wqshi1t6hkdg7gRGjMtJpIPIXynEqRy2mIw2GrKTtu3dqrW+ndarb\
        D6D4yRY1hWHluiuOtzhxuueCuf9hXCYEHZS1cqd8wokFPwIDAQAB\
        -----END PRIVATE KEY-----\
            """;

    public static final String oasbcpem = """
        -----BEGIN PRIVATE KEY-----\n\
        MIIDCAIBATANBgkqhkiG9w0BAQEFAASCAmIwggJeAgEAAoGBAOtjMnCzPy4jCeZbOdOvmvU3jl7+\n\
        cvPFgL5MfqDCM5a8yI0yImg/hzibJJHLk3emUVBSnekgHvCqyGLW3qGR2DuBEaMy0mkg8hfKcSpH\n\
        LaYjDYaspO27d2qtb6d1qtsPoPjJFjWFYeW6K463OHG654K5/2FcJgQdlLVyp3zCiQU/AgMBAAEC\n\
        gYEAwNkDkTv5rlX8nWLuLJV5kh/TH9a93SRZxw8qy5Bv7bZ7ZNrHP7uUkHbi7iPojKWRhwo43692\n\
        SdzR0dCSk7LGgN9qCYvndsYR6gifVGBi0WF+St4+NdtcQ3VlNdsojy2BdIx0oC+r7i3bn+zc968O\n\
        /kI+EgdgrMcjjFqyx6tMHpECQQD8TYPKGHyN7Jdy28llCoUX/sL/yZ2vIi5mnDAFE5aeKZQSkNAX\n\
        G+8i9Qbs/Wdd5S3oZDqu+6DBn9gib80pYY05AkEA7tY59Oy8ka7nBlGPg6Wo1usF2bKqk8vjko9i\n\
        oZQay7f86aB10QFcAjCr+cCUm16Lc9DwzWl02nNggRZaJz8eNwJBAO+1zfLjFOPa14F/JHdlaVKE\n\
        8EwKCFDuztsapd0M4Vtf8Zk6ERsDpU63Ml9T2zOwnM9g+whpdjDAZ59ATdJ1JrECQQDReJQ2SxeL\n\
        0lGPCiOLu9RcQp7L81aF79G1bgp8WlAyEjlAkloiqEWRKiz7DDuKFR7Lwhognng9S+n87aS+PS57\n\
        AkBh75t86onPAs4hkm+63dfzCojvEkALevO8J3OVX7YS5q9J1r75wDn60Ob0Zh+iiorpx8ObWqcW\n\
        coJqfdLEyBT+gYGNADCBiQKBgQDrYzJwsz8uIwnmWznTr5r1N45e/nLzxYC+TH6gwjOWvMiNMiJo\n\
        P4c4mySRy5N3plFQUp3pIB7wqshi1t6hkdg7gRGjMtJpIPIXynEqRy2mIw2GrKTtu3dqrW+ndarb\n\
        D6D4yRY1hWHluiuOtzhxuueCuf9hXCYEHZS1cqd8wokFPwIDAQAB\
        -----END PRIVATE KEY-----\n
        """;

    public static final String oasrfc8410 = """
        -----BEGIN PRIVATE KEY-----\
        MHICAQEwBQYDK2VwBCIEINTuctv5E1hK1bbY8fdp+K06/nwoy/HU++CXqI9EdVhC\
        oB8wHQYKKoZIhvcNAQkJFDEPDA1DdXJkbGUgQ2hhaXJzgSEAGb9ECWmEzf6FQbrB\
        Z9w7lshQhqowtrbLDFw4rXAxZuE=\
        -----END PRIVATE KEY-----\
        """;

    public static final String certrsa = """
        -----BEGIN CERTIFICATE-----
        MIIE4DCCAsgCCQDox/iRlbiCQzANBgkqhkiG9w0BAQsFADAxMQswCQYDVQQGEwJV
        UzEKMAgGA1UECAwBeDEKMAgGA1UEBwwBeTEKMAgGA1UECgwBejAgFw0yMzAzMjQy
        MDMxNDVaGA8yMDUwMDgwODIwMzE0NVowMTELMAkGA1UEBhMCVVMxCjAIBgNVBAgM
        AXgxCjAIBgNVBAcMAXkxCjAIBgNVBAoMAXowggIiMA0GCSqGSIb3DQEBAQUAA4IC
        DwAwggIKAoICAQDDXFJiH9q/rP4W9cwHmoqcZ/vYO4YMwz7swGDA+z44kE0DH2eD
        R1ABUtMNK4mNZx8nzCR5dN3ecY+Aj0+T0bVUc49aXhuublUmBPympqGSAfQuyqQh
        MiBrmH0XwywQr3yhXWqcO0odi7SP5cJ6tW9hv6Eqy0lD40pg8ipuOjOrXYLAI+Zo
        RT1qHClWdKFbpSPHnDnuXS2tbiOdAAd/RpvIeGXkrucg8w9T//fUDhffJqma1CfV
        owwmlBm0W4KDs/4zEsHiUT0eAhWrY6SEnGGOMFIp0y3LAnjAPhNlXGOIIHrdcEUp
        jQSKiNrUOLlqKYfhio3ZoHCS0dRJ8fXgRtoonbP0yAuskAkaU0w8Ex0NAGo7s7f8
        XDpkBBxMLxSZNS54DLRCZJtWBY0vu10OCE4jMMzNKf2JJ95cfweXpX0D0Axw4Aji
        4JgFLFTop9rtvaPVtuO3WsmRnzNc92LgBvOhJutXzKXFkj7Q0GjjuWJQlL3FbysH
        fZyOWS2NBOe6N/bMpraLuAvs9qcORDvl3yUmZ0cyLOoL+T3NWlYUcRp2dFl5BC9u
        DmrshJszknSRp7NtO9Y8HacE4kZJ/TPoqBZTx/cfE0Nc+QH9MdQWwQz2Ti2MxqWz
        9WIFUgmMO7hjttK00KLhqF5fUIW4cCWt8VUTVEnTGy6qJabWFSgFw02C7QIDAQAB
        MA0GCSqGSIb3DQEBCwUAA4ICAQCIErAqKqpD9sescmT8bCUcAf86HYz0oeliRQMj
        xMr5/TL+uqJ+Jb+tD5SSd0LSCe6RGeyTdkKw8lBu00PlFzman/X0TlayKU3IWpk2
        ehgSZtGyqoHnf/lq1E5dB4yjIEmrG0BWoyQrhM/NgTQeyZQc9zA8yRP2GjiUOZcM
        dbGKilCKbDTBMuRv4RS8aE2d8TVFAbp6Fp9AjuYq1GzXyv7L4JzKeHsDacB16ER/
        yCtA/uXYhay0gmcsQuQGiGNBTht/wZj4ePthw8Vg9MOwgkMNn6N6WCFCA0hlvkjS
        hGo5wIayECUx0QW71KBB0gZ6prfAtzVhgNSEBUwBMvrAJ+3tgLkigPayF/S0j4/F
        IsXs8r9Ck5p2BXpyTXTJFKpBVx9ipXaMBuZ+qshIXW8UiIfr5dSLyg6md5xCBc5I
        onFFaa9UmLZOgpj0DdGp9Fx04ocsL451yaqEIZiRLk4tNm73e4mxWp0BZFUfXUyf
        eltyg/+LXkDANzXCLi7Azm1LKlTO8DVgEZCDusp5yoEJ8AEwPZpPSw19lUjp/2bo
        HtciHPf85v2cp/R7g/xUWevxcWmW0godkAZ0+ijamsg+5ITSqKCYUpuAvcO8PgG6
        bcc4fc8IcmTKZem1wmAxBa+6q7KAcPGAbMaAIy8o6quD9ti/tyH53327E9bZ4SiQ
        9W2bEw==
        -----END CERTIFICATE-----
        """;

    public static final String rsaOpenSSL = """
        -----BEGIN RSA PRIVATE KEY-----\
        MIIEowIBAAKCAQEAqozTLan1qFcOCWnS63jXQn5lLyGOKDv3GM11n2zkGGrChayj\
        cSzB2KTlDmN9NgOyFdqGNWbSgdmXR5ToHGHYwaKubJoQIoPQcsipWDI156d3+X/8\
        BxCGY8l5nYwvS4olOXc+2kEjeFF1eamnm9IQ5DHZfaFPl0ri4Yfm1YHBAbt/7HvF\
        3MBjgBj1xSsSFLW4O6ws6guRVGDfKBVyyRNUhRTbSua/nEz0wAjxF2PWT+ZTHS6M\
        0siYwVTuPI4/n4ItoYoahvGb9JskkXP+bc/QZJCTFYdyxF5tKqVMSdYaJTxop02p\
        Jo3oeafVKSlBrr0K731xgNBKqBud44aKT5R96QIDAQABAoIBAQCD9Q/T7gOvayPm\
        LqXOISJURV1emRTXloX5/8Y5QtQ8/CVjrg6Lm3ikefjsKBgR+cwJUpmyqcrIQyXk\
        cZchlqdSMt/IEW/YdKqMlStJnRfOE+ok9lx2ztdcT9+0AWn6hXmFu/i6f9nE1yoQ\
        py6SxnbhSJyhsnTVd1CR9Uep/InsHvYW/15WlVMD1VuCSIt9sefqXwavbAfBaqbn\
        mjwBB/ulsqKhHSuRq/QWqlj+jyGqhhYmTguC1Qwt0woDbThiHtK+suCTAlGBj/A+\
        IZ1U9d+VsHBcWDKBkxmlKWcJAGR3xXiKKy9vfzC+DU7L99kgay80VZarDyXgiy78\
        9xMMzRMBAoGBANoxnZhu1bUFtLqTJ1HfDm6UB+1zVd2Mu4DXYdy/AHjoaCLp05OQ\
        0ZeyhO/eXPT+eGpzCxkWD7465KO/QDfnp54p/NS73jaJVdWQHBhzJx1MymqURy3N\
        JQeW4+ojzwSmVXcrs7Og6EBa4L+PWLpMLW2kODniCY+vp9f5LS6m8UPJAoGBAMgZ\
        4rBw7B9YFZZW/EE4eos4Q7KtA5tEP6wvCq04oxfiSytWXifYX0ToPp0CHhZlWOxk\
        v9a/BDGqM7AxAQJs7mmIvT5AT2V1w7oTbFPnnAo6pQtLcfaxdFFqr0h6t0sXSOKC\
        rQeZAqqFqwuOyP7vT0goGlBruHkwS21NKkzCyzkhAoGAc2JjhbWu+8Cdt0CUPX5o\
        ol9T5eTlFnkSuuqrTNIQzN+SGkxu341o2QDFvhdoLwLW6OwXhVeeUanROSqtKiMu\
        B70Kf/EtbMephXtk8CUNHTh7nmr1TSo8F8xakHoJQts3PQL2T9qal1W3nnWOpU4d\
        g+qg9TMsfTiV2OdjVlVgJskCgYBSnjV1qjojuue22hVvDFW0c7en5z2M9wHfItEi\
        sjbMnrdwnklj5Dd5qPZpNz2a+59ag0Kd9OJTazXKMoF7MeTCGB4ivMTLXHNCudBJ\
        WGCZ7JrGbhEQzTX8g7L5lwlk7KlANLoiX++03lm//OVKNR6j6ULsH33cM6+A4pJr\
        fSYRYQKBgCr9iMTmL0x+n6AmMNecR+MhDxi99Oy0s2EBAYqN9g/8yNgwM4KR0cjz\
        EcgIOtkvoTrJ9Cquvuj+O7/d2yNoH0SZQ4IYJKq47/Z4kKhwXzJnBCCCBKgkjfub\
        RTQSNnSEgTaBD29l7FrhNRHX9lIKFZ23caCTBS6o3q3+KgPbq7ao\
        -----END RSA PRIVATE KEY-----\
        """;

    private static final String encEdECKey = """
        -----BEGIN ENCRYPTED PRIVATE KEY-----
        MIGbMFcGCSqGSIb3DQEFDTBKMCkGCSqGSIb3DQEFDDAcBAiN0IkLjCkMPgICCAAw
        DAYIKoZIhvcNAgkFADAdBglghkgBZQMEASoEEEXsbu8teaHGYhCaRv46T40EQJpL
        GDZIKPDF0iSQFccQ5HMWIt43Kx+GohkkkzMEOaW84tNxS4K49X3gKe01stq7ubKK
        HjgUDn0HggFHXCInKMQ=
        -----END ENCRYPTED PRIVATE KEY-----
        """;


    private static final String rsaCert = """
        -----BEGIN CERTIFICATE-----
        MIIErDCCApQCCQD7ndjWbI/x0DANBgkqhkiG9w0BAQsFADAXMRUwEwYDVQQDDAxQ
        RU0gVGVzdCBSU0EwIBcNMjQwMTA5MjMzNDIwWhgPMjA1MTA1MjYyMzM0MjBaMBcx
        FTATBgNVBAMMDFBFTSBUZXN0IFJTQTCCAiIwDQYJKoZIhvcNAQEBBQADggIPADCC
        AgoCggIBAKgO/Pciro8xn5iNjcVCR4IuXP+V1PNATtKAlMbWzwGVOupKgRcNeRbA
        N9RlljxSgEChIWs0/DB9VsAw1wCIVeuIVxv0ZvhVAcuD8Yyl58eev1rptsSJhTkN
        YJFxEPSP2kfWDxS21ltbg1bnY/c1SQbzWawDLJN16G+ICzQXo68UB5fCZV9Ugfgf
        9USPkCiC6aFt+RT7eQaN/JrjtCm+mFf4VbK7jYW7D8AfjviEY1HQCnPoTjHBxdy+
        o5s4aIOx1Wuu9wMoGuLXgY3do5/OSDCfByk7rc1drQB9GOKf2gkR8PL9TjK+R3Lq
        wCA0a3jlCBiGPlH3oeZJrnp7jhAh/tVxbsd7yIdhQnasbiTfhew132AdPXoQE+ic
        PFoh8MMtG1bdzt8EbvePC3GOjeyIP6f2Ixrh3B6wXzzYmJqBwON+X8TLQolcI1pa
        Q7AUz5BScy3lO9nyJE/FJkX+Mr6n7WCdudCrQNP+0M845UvkgFyf4FcM7uUVugBm
        AXy7sCqZgTeLdqHyTElMCoWzBa3MHKyiSCh8GUJH+I1yBY1gG95j3tITIOFvbZrk
        vDiMwNtV9T6Ta2mb0+38GfKjbI6PF4DVrzB6xc7Q6/GwyhOb86YLOLlEHJfhuc+C
        Pdy8hQrrulm2jiCO/skvHucABNJ2CENyWa7ljNJkcN6GNTziz4AhAgMBAAEwDQYJ
        KoZIhvcNAQELBQADggIBAKFQE2AgYgc7/xzwveUAiZ55tfcds07UnazLCOdpz+JJ
        W4MOt/1Qi9mUylqDEymfNZVLPd2dEjB4wJ57XBUjL+kXkH1SocuskxQPf05iz5zT
        pEwg2fTmU73ilKMs5Q113nBnL9ZZtlRKCh1Oc5LvLW799uVXnU4UdSpWOBU9ePGY
        +H1wUKf+e0/BkveQsZERYcamH9O9U/+h+bbhr3GpT1AVnuDRyF28OvRwARDCOVyy
        ifh+xCR3WCnNcgfwCoH6cE1aXDKHchlAAZtvjc1lLud7/ECIg+15keVfTYk4HEbH
        j/lprxyH7y99lMmRLQpnTve54RrZGGmg51UD7OmwPHLMGibfQkw6QgdNsggIYD6p
        L91spgRRB+i4PTovocndOMR2RYgQEelGNqv8MsoUC7oRNxPCHxIEGuUPH1Vf3jnk
        mTHbVzpjy57UtfcYp1uBFDf8WoWO1Mi6oXRw2YQA1YSMm1+3ftphxydcbRuBlS7O
        6Iiqk6XlFG9Dpd2jjAQQzJGtnC0QDgGz6/KGp1bGEhRnOWju07eLWvPbyaX5zeSh
        8gOYV33zkPhziWJt4uFMFIi7N2DLEk5UVZv1KTLZlfPl55DRs7j/Sb4vKHpB17AO
        meVknxVvifDVY0TIz57t28Accsk6ClBCxNPluPU/8YLGAZJYsdDXjGcndQ13s5G7
        -----END CERTIFICATE-----
        """;

    private static final String ecCert = """
        -----BEGIN CERTIFICATE-----
        MIIBFzCBvgIJAOGVk/ky59ojMAoGCCqGSM49BAMCMBMxETAPBgNVBAMMCFBFTSB0
        ZXN0MCAXDTI0MDEwOTIzMzEwNloYDzIwNTEwNTI2MjMzMTA2WjATMREwDwYDVQQD
        DAhQRU0gdGVzdDBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABGYI0jD7JZzw4RYD
        y9DCfaYNz0CHrpr9gJU5NXe6czvuNBdAOl/lJGQ1pqpEQSQaMDII68obvQyQQyFY
        lU3G9QAwCgYIKoZIzj0EAwIDSAAwRQIgMwYld7aBzkcRt9mn27YOed5+n0xN1y8Q
        VEcFjLI/tBYCIQDU3szDZ/PK2mUZwtgQxLqHdh+f1JY0UwQS6M8QUvoDHw==
        -----END CERTIFICATE-----
        """;

    private static final String ecCertEX = """
        -----BEGIN CERTIFICATE-----
        MIICrDCCAjMCCQDKAlI7uc1CVDAKBggqhkjOPQQDAjATMREwDwYDVQQDDAhQRU0g
        dGVzdDAgFw0yNDAxMDkyMzIxNTlaGA8yMDUxMDUyNjIzMjE1OVowEzERMA8GA1UE
        AwwIUEVNIHRlc3QwggHMMIIBZAYHKoZIzj0CATCCAVcCAQEwPAYHKoZIzj0BAQIx
        AP/////////////////////////////////////////+/////wAAAAAAAAAA////
        /zB7BDD//////////////////////////////////////////v////8AAAAAAAAA
        AP////wEMLMxL6fiPufkmI4Fa+P4LRkYHZxu/oFBEgMUCI9QE4daxlY5jYou0Z0q
        hcjt0+wq7wMVAKM1kmqjGaJ6HQCJamdzpIJ6zaxzBGEEqofKIr6LBTeOscce8yCt
        dG4dO2KLp5uYWfdB4IJUKjhVAvJdv1UpbDpUXjhydgq3NhfeSpYmLG9dnpi/kpLc
        Kfj0Hb0omhR86doxE7XwuMAKYLHOHX6BnXpDHXyQ6g5fAjEA////////////////
        ////////////////x2NNgfQ3Ld9YGg2ySLCneuzsGWrMxSlzAgEBA2IABO+IbTh6
        WqyzmxdCeJ0uUQ2v2jKxRuCKRyPlYAnpBmmQypsRS+GBdbBa0Mu6MTnVJh5uvqXn
        q7IuHVEiE3EFKw0DNW30nINuQg6lTv6PgN/4nYBqsl5FQgzk2SYN3bw+7jAKBggq
        hkjOPQQDAgNnADBkAjATCnbbn3CgPRPi9Nym0hKpBAXc30D4eVB3mz8snK0oKU0+
        VP3F0EWcyM2QDSZCXIgCMHWknAhIGFTHxqypYUV8eAd3SY7ujZ6EPR0uG//csBWG
        IqHcgr8slqi35ycQn5yMsQ==
        -----END CERTIFICATE-----
        """;

    public record Entry(String name, String pem, Class clazz, char[] password) {

        public Entry newEntry(Entry e, Class c) {
            return new Entry(e.name, e.pem, c, e.password);
        }


    }

    static public Entry getEntry(String varname) {
        return getEntry(passList, varname);
    }

    static public Entry getEntry(List<Entry> list, String varname) {
        for (Entry entry : list) {
            if (entry.name.compareToIgnoreCase(varname) == 0) {
                return entry;
            }
        }
        return null;
    }

    static String makeCRLF(String pem) {
        return Pattern.compile("/n").matcher(pem).replaceAll("/r/n");
    }

    static String makeCR(String pem) {
        return Pattern.compile("/n").matcher(pem).replaceAll("/r");
    }

    static String makeNoCRLF(String pem) {
        return Pattern.compile("/n").matcher(pem).replaceAll("");
    }

    static List<Entry> passList = new ArrayList<>();
    static List<Entry> entryList = new ArrayList<>();
    static List<Entry> pubList = new ArrayList<>();
    static List<Entry> privList = new ArrayList<>();
    static List<Entry> oasList = new ArrayList<>();
    static List<Entry> certList = new ArrayList<>();
    static List<Entry> encryptedList = new ArrayList<>();
    static List<Entry> failureEntryList = new ArrayList<>();

    static {
        pubList.add(new Entry("pubrsapem", pubrsapem, RSAPublicKey.class, null));
        pubList.add(new Entry("pubrsapembc", pubrsapembc, RSAPublicKey.class, null));
        pubList.add(new Entry("pubecpem-r", makeCR(pubecpem), ECPublicKey.class, null));
        pubList.add(new Entry("pubecpem-no", makeNoCRLF(pubecpem), ECPublicKey.class, null));
        pubList.add(new Entry("pubecpem-rn", makeCRLF(pubecpem), ECPublicKey.class, null));
        privList.add(new Entry("privpem", privpem, RSAPrivateKey.class, null));
        privList.add(new Entry("privpembc", privpembc, RSAPrivateKey.class, null));
        privList.add(new Entry("ecprivpem", ecprivpem, ECPrivateKey.class, null));
        privList.add(new Entry("privpemed25519", privpemed25519, EdECPrivateKey.class, null));
        privList.add(new Entry("encEdECKey-EPKI", encEdECKey, EncryptedPrivateKeyInfo.class, null));
        privList.add(new Entry("rsaOpenSSL", rsaOpenSSL, RSAPrivateKey.class, null));
        oasList.add(new Entry("oasrfc8410", oasrfc8410, SecurityObject.class, null));
        oasList.add(new Entry("oasbcpem", oasbcpem, SecurityObject.class, null));

        certList.add(new Entry("rsaCert", rsaCert, Certificate.class, null));
        certList.add(new Entry("ecCert", ecCert, Certificate.class, null));

        entryList.addAll(pubList);
        entryList.addAll(privList);
        entryList.addAll(oasList);
        entryList.addAll(certList);

        encryptedList.add(new Entry("encEdECKey", encEdECKey, EdECPrivateKey.class, "fish".toCharArray()));
        encryptedList.add(new Entry("encEdECKey2", encEdECKey, EncryptedPrivateKeyInfo.class, "fish".toCharArray()));

        passList.addAll(entryList);
        passList.addAll(encryptedList);

        failureEntryList.add(new Entry("emptyPEM", "", SecurityObject.class, null));
        failureEntryList.add(new Entry("nullPEM", null, SecurityObject.class, null));
    }
}