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

import java.security.SecurityObject;
import java.security.interfaces.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    public static final String pubecpem = """
        -----BEGIN PUBLIC KEY-----\
        MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEi/kRGOL7wCPTN4KJ2ppeSt5UYB6u\
        cPjjuKDtFTXbguOIFDdZ65O/8HTUqS/sVzRF+dg7H3/tkQ/36KdtuADbwQ==\
        -----END PUBLIC KEY-----\
        """;

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

    static List<String> pubList = new ArrayList<>(Arrays.asList(pubrsapem,
        pubrsapembc, pubecpem));
    static List<String> privList = new ArrayList<>(Arrays.asList(privpem,
        privpembc, ecprivpem, privpemed25519));
    static List<String> oasList = new ArrayList<>(Arrays.asList(oasrfc8410,
        oasbcpem));


    public record Entry(String name, String pem, Class type) {
    }

    static List<Entry> entryList = new ArrayList<>();
    static List<Entry> failureEntryList = new ArrayList<>();

    static public Entry getEntry(String varname) {
        return getEntry(entryList, varname);
    }

    static public Entry getEntry(List<Entry> list, String varname) {
        for (Entry entry : list) {
            if (entry.name.compareToIgnoreCase(varname) == 0) {
                return entry;
            }
        }
        return null;
    }


    static {
        entryList.add(new Entry("pubrsapem", pubrsapem, RSAPublicKey.class));
        entryList.add(new Entry("pubrsapembc", pubrsapembc, RSAPublicKey.class));
        entryList.add(new Entry("pubecpem", pubecpem, ECPublicKey.class));
        entryList.add(new Entry("privpem", privpem, RSAPrivateKey.class));
        entryList.add(new Entry("privpembc", privpembc, RSAPrivateKey.class));
        entryList.add(new Entry("ecprivpem", ecprivpem, ECPrivateKey.class));
        entryList.add(new Entry("privpemed25519", privpemed25519, EdECPrivateKey.class));
        entryList.add(new Entry("oasrfc8410", oasrfc8410, SecurityObject.class));
        entryList.add(new Entry("oasbcpem", oasbcpem, SecurityObject.class));

        failureEntryList.add(new Entry("rsaOpenSSL", rsaOpenSSL, RSAPrivateKey.class));
    }
}