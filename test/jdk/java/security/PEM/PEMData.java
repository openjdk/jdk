/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.security.DEREncodable;
import java.security.KeyPair;
import java.security.PEMRecord;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.security.interfaces.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Library class for PEMEncoderTest and PEMDecoderTest
 */
class PEMData {
    public static final Entry ecsecp256 = new Entry("ecsecp256",
        """
        -----BEGIN PRIVATE KEY-----
        MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgkW3Jx561NlEgBnut
        KwDdi3cNwu7YYD/QtJ+9+AEBdoqhRANCAASL+REY4vvAI9M3gonaml5K3lRgHq5w
        +OO4oO0VNduC44gUN1nrk7/wdNSpL+xXNEX52Dsff+2RD/fop224ANvB
        -----END PRIVATE KEY-----
        """, KeyPair.class, "SunEC");

    public static final Entry rsapriv = new Entry("rsapriv",
        """
        -----BEGIN PRIVATE KEY-----
        MIICeAIBADANBgkqhkiG9w0BAQEFAASCAmIwggJeAgEAAoGBAOtjMnCzPy4jCeZb
        OdOvmvU3jl7+cvPFgL5MfqDCM5a8yI0yImg/hzibJJHLk3emUVBSnekgHvCqyGLW
        3qGR2DuBEaMy0mkg8hfKcSpHLaYjDYaspO27d2qtb6d1qtsPoPjJFjWFYeW6K463
        OHG654K5/2FcJgQdlLVyp3zCiQU/AgMBAAECgYEAwNkDkTv5rlX8nWLuLJV5kh/T
        H9a93SRZxw8qy5Bv7bZ7ZNrHP7uUkHbi7iPojKWRhwo43692SdzR0dCSk7LGgN9q
        CYvndsYR6gifVGBi0WF+St4+NdtcQ3VlNdsojy2BdIx0oC+r7i3bn+zc968O/kI+
        EgdgrMcjjFqyx6tMHpECQQD8TYPKGHyN7Jdy28llCoUX/sL/yZ2vIi5mnDAFE5ae
        KZQSkNAXG+8i9Qbs/Wdd5S3oZDqu+6DBn9gib80pYY05AkEA7tY59Oy8ka7nBlGP
        g6Wo1usF2bKqk8vjko9ioZQay7f86aB10QFcAjCr+cCUm16Lc9DwzWl02nNggRZa
        Jz8eNwJBAO+1zfLjFOPa14F/JHdlaVKE8EwKCFDuztsapd0M4Vtf8Zk6ERsDpU63
        Ml9T2zOwnM9g+whpdjDAZ59ATdJ1JrECQQDReJQ2SxeL0lGPCiOLu9RcQp7L81aF
        79G1bgp8WlAyEjlAkloiqEWRKiz7DDuKFR7Lwhognng9S+n87aS+PS57AkBh75t8
        6onPAs4hkm+63dfzCojvEkALevO8J3OVX7YS5q9J1r75wDn60Ob0Zh+iiorpx8Ob
        WqcWcoJqfdLEyBT+
        -----END PRIVATE KEY-----
        """, RSAPrivateKey.class, "SunRsaSign");

    public static final Entry rsaCrtCoefZeroPriv = new Entry("rsaCrtCoefZeroPriv",
        """
        -----BEGIN RSA PRIVATE KEY-----
        MIIEIwIBAAKCAQEAuZAPiPMlA5R0oOIbxbq+gOmBRcvptIT+0pmG6rZ6H//r7A/Z
        MRwen0iO2GuhlyUgOk9Fja/TMBrNX99boVDEZtL4oxRTJibdLNjfQqPeFhs3NNvv
        CJJEGD91Dq/fGbWv1TMZcEywqqYSdERDEA7yluw87I7YZc9kXVBwRw5AedvoXOL/
        z5yPjK8W7FTCLHSVKiD/X3P3ZX9TmFjTIbRH15Do5sRxsPdrZczYjWdXFXNQEUuF
        sVFGHFbB/AJiZlGYqMU+hEIErE35sHrKpZYkj9YovYQe0SBJyuROPl8wmz0Cd69s
        rhg142Qf23RPhclBuCNAQQOkefeCppg4IFFh7QIDAQABAoIBADlKHlm4Q7m2uElB
        dbSWwqEXNnefjJBUrT3E84/8dXjysNppTDNqzJN9uchcdn+tESWfeshTO97yr2yF
        j4se3fwm72ed60vwnMFvVYKECBmIHoO90S8yxT49PT0jFDyiSN6IT7bJnpOZAUKP
        HqtTCheJaQfZ1DqejIx4vKlbX5GfShwPQV0Q7KeKnfxhryhAbM5Y5UT8grQGBQU7
        aQUZuasQV10APVRaz39VU8/hzBc081LR3O0tjnZcrMAQ7ANsP9Gu3My04cnQ5WBo
        P8uCCaSPSkrzCvjd9YYkdnwXMbVCfALOa+MxBddMi4IQG0qI28Bpw6dkKziPxage
        KcAQnAsCgYEA0/CwzUlyrG6f+FF3uAvPVn/jEhWatJb7Me7lkhO3suV92brb1pQo
        1W1jHdx0OqMuCVfIJ4bXkTwKuvLQGcWJzNWUVh4Tij/ZAV0Ssw2cKbBSCQGNIFsx
        Ux0V9tDSYJsEdk+Y5grloDNJokYYCCpF5bz/6QYmX+t3yzjyVSvcNeMCgYEA4COU
        ezUXKLSWD+jJZXpS5yopB7oXAg7mmonlc1DRuaIxspwBrbXPLQ/neN8Sefpz9Nxn
        4ksPxGbLnJh0wGCnxSu0Qfn4eNVSsulag4IQmbCO2UBsdXNCJB5KlDpRlVhvvviH
        Zpz2Dkdx+itLf1EV841MCtPAHZJwrs6i6JntEe8CgYEAgJYdjs+rJXcQ04YKDr4L
        k72PtR8qd7rKuObqnhAcegvGqV03mB7YD3WIl0tzsUfj3INHysOC8njtQbOkEp7J
        Fl/W2dDxpgVK0gr4F26AesKhYxlv2Fu7t2OEOfVETpx+vpFYgOnHm8TCPhQs7HdJ
        ZTOgSG8UxUmFquToEkjEGGUCgYB6AMP8wKxHeuzH4iVl+EySCa/lxdRqSWQasH7V
        4yMVkYTNvP9o57LKy4Jql7n97Wca3LIrSkJd3LpuFcpPQQ1xVNW8p+0pEK0AN+cN
        +ElC7wkCln+y+rcA5AAiaRApY8cHw04oe72vjhIrY0+oEKILPVkr95D2R9TQQigI
        xmh1vwIBAA==
        -----END RSA PRIVATE KEY-----
        """, RSAPrivateKey.class, "SunRsaSign");

    public static final Entry rsapsspriv = new Entry("rsapsspriv",
        """
        -----BEGIN PRIVATE KEY-----
        MIIEugIBADALBgkqhkiG9w0BAQoEggSmMIIEogIBAAKCAQEAn3qFQtvj9JVqVPRh
        mMMRyT17GiUY+NWOwUHx5bHqfhlHJoCllctSU/YXzrH4Da1w7sSeaMmAKYMW4X5k
        rn9hnKOhgHnm2nkZBaVNQeyrseuDnfwWtLXjnj8rEKpgf9UPRUeXGRSoAb1qpwBf
        epFtLSKZrzswZY2u2UEUGJERABi6Qp+cIZ8uXzBkIsMgrhb50xsdysZ9+qq95z0i
        N1vh/V+Yi2fYpSYVDE8aMWdpvs0oWGvoLQjRgElJx/SknndAfLD42HPYZyyXevyJ
        RgUf+NP0V7c+UtE7m7pgMs1hhxHSmUNdfH9GnOSg9m3+L3WqhaNNWB4aKMqFyhlM
        EsAuawIDAQABAoIBAAMJ9yXIgeYEaN54j67fsg7nJIQ228HLc/5xGWvFQaYofidu
        K87twm0GKKWlf4gR24U5QEQtPst2YNu9fav+PRJv4yEgcYqNOdxWg4veN4Fa7wr2
        JZ/zmARJHjLMRFgl67YSwKmCMBdkZXa24PA5et6cJQM7+gFIELe5b+lt7j3VsxQ7
        JpTJyp3I73lXcJrzcb/OCTxobFPLtkVSgKUNwae26xlNqXX4fQfLp99LHGnkmG3k
        Wlzjs6dUi4fl4yLAJYMxEwxQbSbmY66ZKnM4SkT/YHx67gyJw2CMRp4FQDg94Sor
        0IDDKiSMGzcjuCuUl27/qTuv+iMgCqNB7CSPXtECgYEAvqN8ZuZXeUKz4tn6wxJk
        k1utCl3pSM5PLMF4J43uvX49HF1i3svXrwxzJqug6kPXvB7cuB6U2DFIM3lh2jNE
        u2w0U/5zVz2yEI9EaRjnOXePLsSWjOiC+5MGTafJWy5vZ8+zaWL9tjtUH5hsg1cB
        ZMlXtWrI+AmAUAv6FFDZaHECgYEA1igXzRMGgXAT+YX0wdZQcRMy9jD5WIYIiCex
        6/WRE2LDM855ZBwTU5gyqZC2MWC3yn1ASDraxKdH1m3872Q0NVJfsOaLvBk1HuPk
        dlSHRKO2GeO9m5/YzrZm9jpGs0IH6DKOah/t0aCd2CFxt6qef2wOUmXTCK6tyCXN
        EiUmEpsCgYAMue85E1Ftl+VYVILn+Ndb+ve/RGupX5RrgXLa+R+h6MZ9mUJbazI3
        zlX1k+mHGgZR2aGUbP40vH18ajL9FQUWme+YV9ktTsIPVvETLwVokbGuRpNiTrdH
        whXeoz/O5Xesb3Ijq+cR/j3sagl8bxd5ufMv+jP2UvQM4+/K4WbSEQKBgGuZeVvw
        UzR1u5ODWpaJt6EYpGJN+PohXegLCbokh9/Vn35IH3XNJWi677mCnAfzMGTsyX+B
        Eqn74nw6hvtAvXqNCMc5DrxTbf03Q3KwxcYW+0fGxV2L0sMJonHUlfE7G/3uaN+p
        azQIH0aYhypg74HWKNv9jSqvmWEWnRKg16BBAoGAGLAqCRCP8wxqRVZZjhUJ+5JN
        b6PrykDxCKhlA6JqZKuCYzvXhLABq/7miNDg0CmRREl+yKXlkEoFl4g9LtP7wfjX
        n4us/WNmh+GPZYxlCJSNRTjgk7pm5TjVH5YWURDEnjIHZ7yxbAFlvNUhI1mF5g97
        KVcB4fjBitP1h8P+MoY=
        -----END PRIVATE KEY-----
        """, RSAPrivateKey.class, "SunRsaSign");

    public static final Entry rsaprivbc = new Entry("rsaprivbc",
        """
        -----BEGIN PRIVATE KEY-----
        MIICeAIBADANBgkqhkiG9w0BAQEFAASCAmIwggJeAgEAAoGBAOtjMnCzPy4jCeZb
        OdOvmvU3jl7+cvPFgL5MfqDCM5a8yI0yImg/hzibJJHLk3emUVBSnekgHvCqyGLW
        3qGR2DuBEaMy0mkg8hfKcSpHLaYjDYaspO27d2qtb6d1qtsPoPjJFjWFYeW6K463
        OHG654K5/2FcJgQdlLVyp3zCiQU/AgMBAAECgYEAwNkDkTv5rlX8nWLuLJV5kh/T
        H9a93SRZxw8qy5Bv7bZ7ZNrHP7uUkHbi7iPojKWRhwo43692SdzR0dCSk7LGgN9q
        CYvndsYR6gifVGBi0WF+St4+NdtcQ3VlNdsojy2BdIx0oC+r7i3bn+zc968O/kI+
        EgdgrMcjjFqyx6tMHpECQQD8TYPKGHyN7Jdy28llCoUX/sL/yZ2vIi5mnDAFE5ae
        KZQSkNAXG+8i9Qbs/Wdd5S3oZDqu+6DBn9gib80pYY05AkEA7tY59Oy8ka7nBlGP
        g6Wo1usF2bKqk8vjko9ioZQay7f86aB10QFcAjCr+cCUm16Lc9DwzWl02nNggRZa
        Jz8eNwJBAO+1zfLjFOPa14F/JHdlaVKE8EwKCFDuztsapd0M4Vtf8Zk6ERsDpU63
        Ml9T2zOwnM9g+whpdjDAZ59ATdJ1JrECQQDReJQ2SxeL0lGPCiOLu9RcQp7L81aF
        79G1bgp8WlAyEjlAkloiqEWRKiz7DDuKFR7Lwhognng9S+n87aS+PS57AkBh75t8
        6onPAs4hkm+63dfzCojvEkALevO8J3OVX7YS5q9J1r75wDn60Ob0Zh+iiorpx8Ob
        WqcWcoJqfdLEyBT+
        -----END PRIVATE KEY-----
        """, RSAPrivateKey.class, "SunRsaSign");

    public static final Entry ec25519priv = new Entry("ed25519priv",
        """
        -----BEGIN PRIVATE KEY-----
        MC4CAQAwBQYDK2VwBCIEIFFZsmD+OKk67Cigc84/2fWtlKsvXWLSoMJ0MHh4jI4I
        -----END PRIVATE KEY-----
        """, EdECPrivateKey.class, "SunEC");

    public static final Entry rsapub = new Entry("rsapub",
        """
        -----BEGIN PUBLIC KEY-----
        MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDrYzJwsz8uIwnmWznTr5r1N45e
        /nLzxYC+TH6gwjOWvMiNMiJoP4c4mySRy5N3plFQUp3pIB7wqshi1t6hkdg7gRGj
        MtJpIPIXynEqRy2mIw2GrKTtu3dqrW+ndarbD6D4yRY1hWHluiuOtzhxuueCuf9h
        XCYEHZS1cqd8wokFPwIDAQAB
        -----END PUBLIC KEY-----
        """, RSAPublicKey.class, "SunRsaSign");

    public static final Entry rsapsspub = new Entry("rsapsspub",
        """
        -----BEGIN PUBLIC KEY-----
        MIIBIDALBgkqhkiG9w0BAQoDggEPADCCAQoCggEBAJ96hULb4/SValT0YZjDEck9
        exolGPjVjsFB8eWx6n4ZRyaApZXLUlP2F86x+A2tcO7EnmjJgCmDFuF+ZK5/YZyj
        oYB55tp5GQWlTUHsq7Hrg538FrS1454/KxCqYH/VD0VHlxkUqAG9aqcAX3qRbS0i
        ma87MGWNrtlBFBiREQAYukKfnCGfLl8wZCLDIK4W+dMbHcrGffqqvec9Ijdb4f1f
        mItn2KUmFQxPGjFnab7NKFhr6C0I0YBJScf0pJ53QHyw+Nhz2Gcsl3r8iUYFH/jT
        9Fe3PlLRO5u6YDLNYYcR0plDXXx/RpzkoPZt/i91qoWjTVgeGijKhcoZTBLALmsC
        AwEAAQ==
        -----END PUBLIC KEY-----
        """, RSAPublicKey.class, "SunRsaSign");

    public static final Entry rsapubbc = new Entry("rsapubbc",
        """
        -----BEGIN PUBLIC KEY-----
        MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDrYzJwsz8uIwnmWznTr5r1N45e
        /nLzxYC+TH6gwjOWvMiNMiJoP4c4mySRy5N3plFQUp3pIB7wqshi1t6hkdg7gRGj
        MtJpIPIXynEqRy2mIw2GrKTtu3dqrW+ndarbD6D4yRY1hWHluiuOtzhxuueCuf9h
        XCYEHZS1cqd8wokFPwIDAQAB
        -----END PUBLIC KEY-----
        """, RSAPublicKey.class, "SunRsaSign");

    public static final Entry ecsecp256pub = new Entry("ecsecp256pub", """
        -----BEGIN PUBLIC KEY-----
        MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEi/kRGOL7wCPTN4KJ2ppeSt5UYB6u
        cPjjuKDtFTXbguOIFDdZ65O/8HTUqS/sVzRF+dg7H3/tkQ/36KdtuADbwQ==
        -----END PUBLIC KEY-----
        """, ECPublicKey.class, "SunEC");

    // EC key with explicit parameters -- Not currently supported by SunEC
    public static final String pubec_explicit = """
        -----BEGIN PUBLIC KEY-----
        MIIBSzCCAQMGByqGSM49AgEwgfcCAQEwLAYHKoZIzj0BAQIhAP////8AAAABAAAA
        AAAAAAAAAAAA////////////////MFsEIP////8AAAABAAAAAAAAAAAAAAAA////
        ///////////8BCBaxjXYqjqT57PrvVV2mIa8ZR0GsMxTsPY7zjw+J9JgSwMVAMSd
        NgiG5wSTamZ44ROdJreBn36QBEEEaxfR8uEsQkf4vOblY6RA8ncDfYEt6zOg9KE5
        RdiYwpZP40Li/hp/m47n60p8D54WK84zV2sxXs7LtkBoN79R9QIhAP////8AAAAA
        //////////+85vqtpxeehPO5ysL8YyVRAgEBA0IABIv5ERji+8Aj0zeCidqaXkre
        VGAernD447ig7RU124LjiBQ3WeuTv/B01Kkv7Fc0RfnYOx9/7ZEP9+inbbgA28E=
        -----END PUBLIC KEY-----
        """;

    public static final Entry oasbcpem = new Entry("oasbcpem",
        """
        -----BEGIN PRIVATE KEY-----
        MIIDCAIBATANBgkqhkiG9w0BAQEFAASCAmIwggJeAgEAAoGBAOtjMnCzPy4jCeZbOdOvmvU3jl7+
        cvPFgL5MfqDCM5a8yI0yImg/hzibJJHLk3emUVBSnekgHvCqyGLW3qGR2DuBEaMy0mkg8hfKcSpH
        LaYjDYaspO27d2qtb6d1qtsPoPjJFjWFYeW6K463OHG654K5/2FcJgQdlLVyp3zCiQU/AgMBAAEC
        gYEAwNkDkTv5rlX8nWLuLJV5kh/TH9a93SRZxw8qy5Bv7bZ7ZNrHP7uUkHbi7iPojKWRhwo43692
        SdzR0dCSk7LGgN9qCYvndsYR6gifVGBi0WF+St4+NdtcQ3VlNdsojy2BdIx0oC+r7i3bn+zc968O
        /kI+EgdgrMcjjFqyx6tMHpECQQD8TYPKGHyN7Jdy28llCoUX/sL/yZ2vIi5mnDAFE5aeKZQSkNAX
        G+8i9Qbs/Wdd5S3oZDqu+6DBn9gib80pYY05AkEA7tY59Oy8ka7nBlGPg6Wo1usF2bKqk8vjko9i
        oZQay7f86aB10QFcAjCr+cCUm16Lc9DwzWl02nNggRZaJz8eNwJBAO+1zfLjFOPa14F/JHdlaVKE
        8EwKCFDuztsapd0M4Vtf8Zk6ERsDpU63Ml9T2zOwnM9g+whpdjDAZ59ATdJ1JrECQQDReJQ2SxeL
        0lGPCiOLu9RcQp7L81aF79G1bgp8WlAyEjlAkloiqEWRKiz7DDuKFR7Lwhognng9S+n87aS+PS57
        AkBh75t86onPAs4hkm+63dfzCojvEkALevO8J3OVX7YS5q9J1r75wDn60Ob0Zh+iiorpx8ObWqcW
        coJqfdLEyBT+gYGNADCBiQKBgQDrYzJwsz8uIwnmWznTr5r1N45e/nLzxYC+TH6gwjOWvMiNMiJo
        P4c4mySRy5N3plFQUp3pIB7wqshi1t6hkdg7gRGjMtJpIPIXynEqRy2mIw2GrKTtu3dqrW+ndarb
        D6D4yRY1hWHluiuOtzhxuueCuf9hXCYEHZS1cqd8wokFPwIDAQAB
        -----END PRIVATE KEY-----
        """, KeyPair.class, "SunRsaSign");

    public static final Entry oasrfc8410 = new Entry("oasrfc8410",
        """
        -----BEGIN PRIVATE KEY-----
        MHICAQEwBQYDK2VwBCIEINTuctv5E1hK1bbY8fdp+K06/nwoy/HU++CXqI9EdVhC
        oB8wHQYKKoZIhvcNAQkJFDEPDA1DdXJkbGUgQ2hhaXJzgSEAGb9ECWmEzf6FQbrB
        Z9w7lshQhqowtrbLDFw4rXAxZuE=
        -----END PRIVATE KEY-----
        """, KeyPair.class, "SunEC");

    public static final Entry oasxdh = new Entry("oasxdh",
        """
        -----BEGIN PRIVATE KEY-----
        MFECAQEwBQYDK2VuBCIEIIrMS7w5YxuBTyPFiaFvp6ILiGET7wY9ybk7Qqhe3hSq
        gSEAB7ODPxRePrPnJMaj3f47blVx6c5bfxcllQzLp4bW5x4=
        -----END PRIVATE KEY-----
        """, KeyPair.class, "SunEC");

    public static final Entry oasec = new Entry("oasec",
        """
        -----BEGIN PRIVATE KEY-----
        MIGFAgEBMBMGByqGSM49AgEGCCqGSM49AwEHBCcwJQIBAQQgkGEVbZE1yAiO11Ya
        eepcrBQL+HpVE4fy0V6jbpJcmkiBQgAERCqYYmN9uNT9Z1O2Z2VC3Zag9eUAhz7G
        p8DqC21VrIgpqVQ4BrcWsieNg9fSd4N2hgfMpk9PCQwJQ8ifCMiBVQ==
        -----END PRIVATE KEY-----
        """, KeyPair.class, "SunEC");

    public static final Entry rsaOpenSSL = new Entry("rsaOpenSSL",
        """
        -----BEGIN RSA PRIVATE KEY-----
        MIIEowIBAAKCAQEAqozTLan1qFcOCWnS63jXQn5lLyGOKDv3GM11n2zkGGrChayj
        cSzB2KTlDmN9NgOyFdqGNWbSgdmXR5ToHGHYwaKubJoQIoPQcsipWDI156d3+X/8
        BxCGY8l5nYwvS4olOXc+2kEjeFF1eamnm9IQ5DHZfaFPl0ri4Yfm1YHBAbt/7HvF
        3MBjgBj1xSsSFLW4O6ws6guRVGDfKBVyyRNUhRTbSua/nEz0wAjxF2PWT+ZTHS6M
        0siYwVTuPI4/n4ItoYoahvGb9JskkXP+bc/QZJCTFYdyxF5tKqVMSdYaJTxop02p
        Jo3oeafVKSlBrr0K731xgNBKqBud44aKT5R96QIDAQABAoIBAQCD9Q/T7gOvayPm
        LqXOISJURV1emRTXloX5/8Y5QtQ8/CVjrg6Lm3ikefjsKBgR+cwJUpmyqcrIQyXk
        cZchlqdSMt/IEW/YdKqMlStJnRfOE+ok9lx2ztdcT9+0AWn6hXmFu/i6f9nE1yoQ
        py6SxnbhSJyhsnTVd1CR9Uep/InsHvYW/15WlVMD1VuCSIt9sefqXwavbAfBaqbn
        mjwBB/ulsqKhHSuRq/QWqlj+jyGqhhYmTguC1Qwt0woDbThiHtK+suCTAlGBj/A+
        IZ1U9d+VsHBcWDKBkxmlKWcJAGR3xXiKKy9vfzC+DU7L99kgay80VZarDyXgiy78
        9xMMzRMBAoGBANoxnZhu1bUFtLqTJ1HfDm6UB+1zVd2Mu4DXYdy/AHjoaCLp05OQ
        0ZeyhO/eXPT+eGpzCxkWD7465KO/QDfnp54p/NS73jaJVdWQHBhzJx1MymqURy3N
        JQeW4+ojzwSmVXcrs7Og6EBa4L+PWLpMLW2kODniCY+vp9f5LS6m8UPJAoGBAMgZ
        4rBw7B9YFZZW/EE4eos4Q7KtA5tEP6wvCq04oxfiSytWXifYX0ToPp0CHhZlWOxk
        v9a/BDGqM7AxAQJs7mmIvT5AT2V1w7oTbFPnnAo6pQtLcfaxdFFqr0h6t0sXSOKC
        rQeZAqqFqwuOyP7vT0goGlBruHkwS21NKkzCyzkhAoGAc2JjhbWu+8Cdt0CUPX5o
        ol9T5eTlFnkSuuqrTNIQzN+SGkxu341o2QDFvhdoLwLW6OwXhVeeUanROSqtKiMu
        B70Kf/EtbMephXtk8CUNHTh7nmr1TSo8F8xakHoJQts3PQL2T9qal1W3nnWOpU4d
        g+qg9TMsfTiV2OdjVlVgJskCgYBSnjV1qjojuue22hVvDFW0c7en5z2M9wHfItEi
        sjbMnrdwnklj5Dd5qPZpNz2a+59ag0Kd9OJTazXKMoF7MeTCGB4ivMTLXHNCudBJ
        WGCZ7JrGbhEQzTX8g7L5lwlk7KlANLoiX++03lm//OVKNR6j6ULsH33cM6+A4pJr
        fSYRYQKBgCr9iMTmL0x+n6AmMNecR+MhDxi99Oy0s2EBAYqN9g/8yNgwM4KR0cjz
        EcgIOtkvoTrJ9Cquvuj+O7/d2yNoH0SZQ4IYJKq47/Z4kKhwXzJnBCCCBKgkjfub
        RTQSNnSEgTaBD29l7FrhNRHX9lIKFZ23caCTBS6o3q3+KgPbq7ao
        -----END RSA PRIVATE KEY-----
        """, RSAPrivateKey.class, "SunRsaSign");

    static final Entry ed25519ep8 = new Entry("ed25519ep8",
        """
        -----BEGIN ENCRYPTED PRIVATE KEY-----
        MIGqMGYGCSqGSIb3DQEFDTBZMDgGCSqGSIb3DQEFDDArBBRyYnoNyrcqvubzch00
        jyuAb5YizgICEAACARAwDAYIKoZIhvcNAgkFADAdBglghkgBZQMEAQIEEM8BgEgO
        vdMyi46+Dw7cOjwEQLtx5ME0NOOo7vlCGm3H/4j+Tf5UXrMb1UrkPjqc8OiLbC0n
        IycFtI70ciPjgwDSjtCcPxR8fSxJPrm2yOJsRVo=
        -----END ENCRYPTED PRIVATE KEY-----
        """, EdECPrivateKey.class, "SunEC", "fish".toCharArray());

    // This is not meant to be decrypted and to stay as an EKPI
    static final Entry ed25519ekpi = new Entry("ed25519ekpi",
        ed25519ep8.pem(), EncryptedPrivateKeyInfo.class, "SunEC", null);

    static final Entry rsaCert = new Entry("rsaCert",
        """
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
        """, X509Certificate.class, "SUN");

    static final Entry ecCert = new Entry("ecCert",
        """
        -----BEGIN CERTIFICATE-----
        MIIBFzCBvgIJAOGVk/ky59ojMAoGCCqGSM49BAMCMBMxETAPBgNVBAMMCFBFTSB0
        ZXN0MCAXDTI0MDEwOTIzMzEwNloYDzIwNTEwNTI2MjMzMTA2WjATMREwDwYDVQQD
        DAhQRU0gdGVzdDBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABGYI0jD7JZzw4RYD
        y9DCfaYNz0CHrpr9gJU5NXe6czvuNBdAOl/lJGQ1pqpEQSQaMDII68obvQyQQyFY
        lU3G9QAwCgYIKoZIzj0EAwIDSAAwRQIgMwYld7aBzkcRt9mn27YOed5+n0xN1y8Q
        VEcFjLI/tBYCIQDU3szDZ/PK2mUZwtgQxLqHdh+f1JY0UwQS6M8QUvoDHw==
        -----END CERTIFICATE-----
        """, X509Certificate.class, "SUN");

    private static final Entry rsaCrl = new Entry("rsaCrl",
            """
            -----BEGIN X509 CRL-----
            MIIBRTCBrwIBATANBgkqhkiG9w0BAQUFADBMMQswCQYDVQQGEwJVUzEaMBgGA1UE
            ChMRVGVzdCBDZXJ0aWZpY2F0ZXMxITAfBgNVBAMTGEJhc2ljIEhUVFAgVVJJIFBl
            ZXIgMSBDQRcNMDUwNjAzMjE0NTQ3WhcNMTUwNjAxMjE0NTQ3WqAvMC0wHwYDVR0j
            BBgwFoAUa+bxcvx1zVdUhvIEd9hcfbmFdw4wCgYDVR0UBAMCAQEwDQYJKoZIhvcN
            AQEFBQADgYEAZ+21yt1pJn2FU6vBwpFtAKVeBCCCqJVFiRxT84XbUw0BpLrCFvlk
            FOo6tC95aoV7vPGwOEyUNbKJJOCzLliIwV1PPfgZQV20xohSIPISHdUjmlyttglv
            AuEvltGnbP7ENxw18HxvM20XmHz+akuFu6npI6MkBjfoxvlq1bcdTrI=
            -----END X509 CRL-----
            """, X509CRL.class, "SUN");

    // Few random manipulated Base64 characters in PEM content
    private static final Entry invalidDer = new Entry("invalidDER", """
        -----BEGIN PRIVATE KEY-----
        MIICeAIBADANBhkqhkiG9w0BAQEFAASCAmIwggJeAgEAAoGBAOtjMnCzPy4jCeZb
        OdOvmvU3jl7+cvPFgL5MfqDCM5a8yI0yImg/hzibJJHLk3emUVBSnekgHvCqyGLW
        3qGR2DuBEaMy0mkg8hfKcSpHLaYjDYaspO27d2qtb6d1qtsPoPjJFjWFYeW6K463
        OHG654K5/2FcJgQdlLVyp3zCiQU/AgMBAAECgYEAwNkDkTv5rlX8nWLuLJV5kh/T
        H9a93SRZxw8qy5Bv7bZ7ZNfHP7uUkHbi7iPojKWRhwo43692SdzR0dCSk7LGgN9q
        CYvndsYR6gifVGBi0WF+St4+NdtcQ3VlNdsojy2BdIx0oC+r7i3bn+zc968O/kI+
        EgdgrMcjjFqyx6tMHpECQQD8TYPKGHyN7Jdy28llCoUX/sL/yZ2vIi5mnDAFE5ae
        KZQSkNAXG+8i9Qbs/Wdd5S3oZDqu+6DBn9gib80pYY05AkEA7tY59Oy8ka7nBlGP
        g6Wo1usF2bKqk8vjko9ioZQay7f86aB10QFcAjCr+cCUm16Lc9DwzWl02nNggRZa
        Jz8eNwJBAO+1zfLjFOPb14F/JHdlaVKE8EwKCFDuztsapd0M4Vtf8Zk6ERsDpU63
        Ml9T2zOwnM9g+whpdjDAZ59ATdJ1JrECQQDReJQ2SxeL0lGPCiOLu9RcQp7L81aF
        79G1bgp8WlAyEjlAkloiqEWRKiz7DDuKFR7Lwhognng9S+n87aS+PS57AkBh75t8
        6onPAs4hkm+63dfzCojvEkALevO8J3OVX7YS5q9J1r75wDn60Ob0Zh+iiorpx8Ob
        WqcWcoJqfdLEyBT+
        -----END PRIVATE KEY-----
        """, DEREncodable.class, null);

    private static final Entry invalidPEM = new Entry("invalidPEM", """
        -----BEGIN INVALID PEM-----
        MIG2AgEAMBAGByqGSM49AgEGBSuBBAAiBIGeMIGbAgEBBDBVS52ZSKZ0oES7twD2
        GGwRIVu3uHlGIwlu0xzFe7sgIPntca2bHfYMhgGxrlCm0q+hZANiAAQNWgwWfLX8
        8pYVjvwbfvDF9f+Oa9w6JjrfpWwFAUI6b1OPgrNUh+yXtUXnQNXnfUcIu0Os53bM
        """, DEREncodable.class, null);

    private static final Entry invalidHeader = new Entry("invalidHeader", """
        ---BEGIN PRIVATE KEY---
        MC4CAQAwBQYDK2VwBCIEIFFZsmD+OKk67Cigc84/2fWtlKsvXWLSoMJ0MHh4jI4I
        -----END PRIVATE KEY-----
        """, DEREncodable.class, null);

    private static final Entry invalidFooter = new Entry("invalidFooter", """
        -----BEGIN PRIVATE KEY-----
        MC4CAQAwBQYDK2VwBCIEIFFZsmD+OKk67Cigc84/2fWtlKsvXWLSoMJ0MHh4jI4I
        ---END PRIVATE KEY---
        """, DEREncodable.class, null);

    private static final Entry incorrectFooter = new Entry("incorrectFooter", """
        -----BEGIN PRIVATE KEY-----
        MIG2AgEAMBAGByqGSM49AgEGBSuBBAAiBIGeMIGbAgEBBDBVS52ZSKZ0oES7twD2
        GGwRIVu3uHlGIwlu0xzFe7sgIPntca2bHfYMhgGxrlCm0q+hZANiAAQNWgwWfLX8
        8pYVjvwbfvDF9f+Oa9w6JjrfpWwFAUI6b1OPgrNUh+yXtUXnQNXnfUcIu0Os53bM
        8fTqPkQl6RyWEDHeXqJK8zTBHMeBq9nLfDPSbzQgLDyC64Orn0D8exM=
        -----END PUBLIC KEY-----
        """, DEREncodable.class, null);

    // EC cert with explicit parameters -- Not currently supported by SunEC
    static final String ecCertEX = """
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

    static final Entry ecsecp384 = new Entry("ecsecp384",
        """
        -----BEGIN PRIVATE KEY-----
        MIG2AgEAMBAGByqGSM49AgEGBSuBBAAiBIGeMIGbAgEBBDBVS52ZSKZ0oES7twD2
        GGwRIVu3uHlGIwlu0xzFe7sgIPntca2bHfYMhgGxrlCm0q+hZANiAAQNWgwWfLX8
        8pYVjvwbfvDF9f+Oa9w6JjrfpWwFAUI6b1OPgrNUh+yXtUXnQNXnfUcIu0Os53bM
        8fTqPkQl6RyWEDHeXqJK8zTBHMeBq9nLfDPSbzQgLDyC64Orn0D8exM=
        -----END PRIVATE KEY-----
        """, KeyPair.class, "SunEC");

    public static final Entry ecCSR = new Entry("ecCSR",
        """
        -----BEGIN CERTIFICATE REQUEST-----
        MIICCTCCAbACAQAwRTELMAkGA1UEBhMCVVMxDTALBgNVBAgMBFRlc3QxFDASBgNV
        BAcMC1NhbnRhIENsYXJhMREwDwYDVQQDDAhUZXN0IENTUjCCAUswggEDBgcqhkjO
        PQIBMIH3AgEBMCwGByqGSM49AQECIQD/////AAAAAQAAAAAAAAAAAAAAAP//////
        /////////zBbBCD/////AAAAAQAAAAAAAAAAAAAAAP///////////////AQgWsY1
        2Ko6k+ez671VdpiGvGUdBrDMU7D2O848PifSYEsDFQDEnTYIhucEk2pmeOETnSa3
        gZ9+kARBBGsX0fLhLEJH+Lzm5WOkQPJ3A32BLeszoPShOUXYmMKWT+NC4v4af5uO
        5+tKfA+eFivOM1drMV7Oy7ZAaDe/UfUCIQD/////AAAAAP//////////vOb6racX
        noTzucrC/GMlUQIBAQNCAAT3UJgGXD7xMwFSzBnkhsEXz3eJLjIE0HTP1Ax6x7QX
        G3/+Z/qgOZ6UQCxeHOWMEgF1Ufc/tZkzgbvxWJ6gokeToBUwEwYJKoZIhvcNAQkH
        MQYMBGZpc2gwCgYIKoZIzj0EAwIDRwAwRAIgUBTdrMDE4BqruYRh1rRyKQBf48WR
        kIX8R4dBK9h1VRcCIEBR2Mzvku/huTbWTwKVlXBZeEmwIlxKwpRepPtViXcW
        -----END CERTIFICATE REQUEST-----
        """, PEMRecord.class, "SunEC");

    public static final String preData = "TEXT BLAH TEXT BLAH" +
        System.lineSeparator();
    public static final String postData = "FINISHED" + System.lineSeparator();

    public static final Entry ecCSRWithData = new Entry("ecCSRWithData",
        preData + """
        -----BEGIN CERTIFICATE REQUEST-----
        MIICCTCCAbACAQAwRTELMAkGA1UEBhMCVVMxDTALBgNVBAgMBFRlc3QxFDASBgNV
        BAcMC1NhbnRhIENsYXJhMREwDwYDVQQDDAhUZXN0IENTUjCCAUswggEDBgcqhkjO
        PQIBMIH3AgEBMCwGByqGSM49AQECIQD/////AAAAAQAAAAAAAAAAAAAAAP//////
        /////////zBbBCD/////AAAAAQAAAAAAAAAAAAAAAP///////////////AQgWsY1
        2Ko6k+ez671VdpiGvGUdBrDMU7D2O848PifSYEsDFQDEnTYIhucEk2pmeOETnSa3
        gZ9+kARBBGsX0fLhLEJH+Lzm5WOkQPJ3A32BLeszoPShOUXYmMKWT+NC4v4af5uO
        5+tKfA+eFivOM1drMV7Oy7ZAaDe/UfUCIQD/////AAAAAP//////////vOb6racX
        noTzucrC/GMlUQIBAQNCAAT3UJgGXD7xMwFSzBnkhsEXz3eJLjIE0HTP1Ax6x7QX
        G3/+Z/qgOZ6UQCxeHOWMEgF1Ufc/tZkzgbvxWJ6gokeToBUwEwYJKoZIhvcNAQkH
        MQYMBGZpc2gwCgYIKoZIzj0EAwIDRwAwRAIgUBTdrMDE4BqruYRh1rRyKQBf48WR
        kIX8R4dBK9h1VRcCIEBR2Mzvku/huTbWTwKVlXBZeEmwIlxKwpRepPtViXcW
        -----END CERTIFICATE REQUEST-----
        """ + postData, PEMRecord.class, "SunEC");

    final static Pattern CR = Pattern.compile("\r");
    final static Pattern LF = Pattern.compile("\n");
    final static Pattern LSDEFAULT = Pattern.compile(System.lineSeparator());


    public record Entry(String name, String pem, Class clazz, String provider, char[] password,
                        byte[] der) {

        public Entry(String name, String pem, Class clazz, String provider, char[] password,
            byte[] der) {
            this.name = name;
            this.pem = pem;
            this.clazz = clazz;
            this.provider = provider;
            this.password = password;
            if (pem != null && pem.length() > 0 &&
                    !name.contains("incorrect") && !name.contains("invalid")) {
                String[] pemtext = pem.split("-----");
                this.der = Base64.getMimeDecoder().decode(pemtext[2]);
            } else {
                this.der = null;
            }
        }
        Entry(String name, String pem, Class clazz, String provider, char[] password) {
            this(name, pem, clazz, provider, password, null);
        }

        Entry(String name, String pem, Class clazz, String provider) {
            this(name, pem, clazz, provider, null, null);
        }

        public Entry newClass(String name, Class c) {
            return new Entry(name, pem, c, provider, password);
        }

        public Entry newClass(Class c) {
            return newClass(name, c);
        }

        Entry makeCRLF(String name) {
            return new Entry(name,
                Pattern.compile(System.lineSeparator()).matcher(pem).replaceAll("\r\n"),
                clazz, provider, password());
        }

        Entry makeCR(String name) {
            return new Entry(name,
                Pattern.compile(System.lineSeparator()).matcher(pem).replaceAll("\r"),
                clazz, provider, password());
        }

        Entry makeNoCRLF(String name) {
            return new Entry(name,
                LF.matcher(CR.matcher(pem).replaceAll("")).
                    replaceAll(""),
                clazz, provider, password());
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

    static List<Entry> passList = new ArrayList<>();
    static List<Entry> entryList = new ArrayList<>();
    static List<Entry> pubList = new ArrayList<>();
    static List<Entry> privList = new ArrayList<>();
    static List<Entry> oasList = new ArrayList<>();
    static List<Entry> certList = new ArrayList<>();
    static List<Entry> encryptedList = new ArrayList<>();
    static List<Entry> failureEntryList = new ArrayList<>();

    static {
        pubList.add(rsapub);
        pubList.add(rsapsspub);
        pubList.add(rsapubbc);
        pubList.add(ecsecp256pub.makeCR("ecsecp256pub-r"));
        pubList.add(ecsecp256pub.makeCRLF("ecsecp256pub-rn"));
        privList.add(rsapriv);
        privList.add(rsapsspriv);
        privList.add(rsaprivbc);
        privList.add(ecsecp256);
        privList.add(ecsecp384);
        privList.add(ec25519priv);
        privList.add(ed25519ekpi);  // The non-EKPI version needs decryption
        privList.add(rsaOpenSSL);
        oasList.add(oasrfc8410);
        oasList.add(oasbcpem);
        oasList.add(oasec);
        oasList.add(oasxdh);

        certList.add(rsaCert);
        certList.add(ecCert);
        certList.add(rsaCrl);

        entryList.addAll(pubList);
        entryList.addAll(privList);
        entryList.addAll(oasList);
        entryList.addAll(certList);

        encryptedList.add(ed25519ep8);

        passList.addAll(entryList);
        passList.addAll(encryptedList);

        failureEntryList.add(new Entry("emptyPEM", "", DEREncodable.class, null));
        failureEntryList.add(new Entry("nullPEM", null, DEREncodable.class, null));
        failureEntryList.add(incorrectFooter);
        failureEntryList.add(invalidPEM);
        failureEntryList.add(invalidDer);
        failureEntryList.add(invalidHeader);
        failureEntryList.add(invalidFooter);
    }

    static void checkResults(PEMData.Entry entry, String result) {
        try {
            checkResults(entry.pem(), result);
        } catch (AssertionError e) {
            throw new AssertionError("Encoder PEM mismatch " +
                entry.name(), e);
        }
    }

    static void checkResults(String expected, String result) {
        // The below matches the \r\n generated PEM with the PEM passed
        // into the test.
        String pem = LF.matcher(CR.matcher(expected).replaceAll("")).
            replaceAll("");
        result = LF.matcher(CR.matcher(result).replaceAll("")).
            replaceAll("");
        try {
            if (pem.compareTo(result) != 0) {
                System.out.println("expected:\n" + pem);
                System.out.println("generated:\n" + result);
                indexDiff(pem, result);
            }
        } catch (AssertionError e) {
            throw new AssertionError("Encoder PEM mismatch ");
        }
    }

    static void indexDiff(String a, String b) {
        String lenerr = "";
        int len = a.length();
        int lenb = b.length();
        if (len != lenb) {
            lenerr = ":  Length mismatch: " + len + " vs " + lenb;
            len = Math.min(len, lenb);
        }
        for (int i = 0; i < len; i++) {
            if (a.charAt(i) != b.charAt(i)) {
                throw new AssertionError("Char mistmatch, index #" + i +
                    "  (" + a.charAt(i) + " vs " + b.charAt(i) + ")" + lenerr);
            }
        }
    }
}