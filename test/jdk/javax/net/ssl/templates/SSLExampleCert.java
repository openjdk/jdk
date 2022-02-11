/*
 * Copyright (C) 2022 THL A29 Limited, a Tencent company. All rights reserved.
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

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.*;
import java.net.InetAddress;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

// A template to use "www.example.com" as the server name.  The caller should
// set a virtual hosts file with System Property, "jdk.net.hosts.file". This
// class will map the loopback address to "www.example.com", and write to
// the specified hosts file.
public enum SSLExampleCert {
    // Version: 3 (0x2)
    // Serial Number: 15223159159760931473 (0xd34386999cc8ca91)
    // Signature Algorithm: sha256WithRSAEncryption
    // Issuer: C=US, ST=California, O=Example, OU=Test
    // Validity
    //     Not Before: Jan 26 04:50:29 2022 GMT
    //     Not After : Feb 25 04:50:29 2022 GMT
    // Subject: C=US, ST=California, O=Example, OU=Test
    // Public Key Algorithm: rsaEncryption
    CA_RSA("RSA",
            """
            -----BEGIN CERTIFICATE-----
            MIIDXDCCAkSgAwIBAgIJANNDhpmcyMqRMA0GCSqGSIb3DQEBCwUAMEMxCzAJBgNV
            BAYTAlVTMRMwEQYDVQQIDApDYWxpZm9ybmlhMRAwDgYDVQQKDAdFeGFtcGxlMQ0w
            CwYDVQQLDARUZXN0MB4XDTIyMDEyNjA0NTAyOVoXDTIyMDIyNTA0NTAyOVowQzEL
            MAkGA1UEBhMCVVMxEzARBgNVBAgMCkNhbGlmb3JuaWExEDAOBgNVBAoMB0V4YW1w
            bGUxDTALBgNVBAsMBFRlc3QwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIB
            AQDnOL2hB/GYyYziXo/ppxi7V1LfMMLeHt0lZbYlrmNxUlln4naI4B4Lkg75eb1Y
            DgC7MZQd5nKijK9Dkq52Z2zLxaqBYnLxKJ36qKPqbtTL3I8mfUvVEeNIDN/8YTAt
            suIEQi54dNtQVrB4YReMdnUq+xCKLAfEio4QLEQr7KtyCBXHZpM7RYRT0giQFvDU
            2kls9lFLeqKXgocnA7VpoL0V12hpxDeJoRm1szf0M5YXGJumQLaE5qM/+P2OOhw/
            T+xkupy2GF02s6FBXkH7NrFIjtuBSaVhSvCG/N7njWSn339thr3kiPEaCS4KSH5E
            E6FEazxZQrTCbkQQ+v3y1pS1AgMBAAGjUzBRMA8GA1UdEwEB/wQFMAMBAf8wHQYD
            VR0OBBYEFMFw2FWUvwZx3FJjm1G9TujCjAJSMB8GA1UdIwQYMBaAFMFw2FWUvwZx
            3FJjm1G9TujCjAJSMA0GCSqGSIb3DQEBCwUAA4IBAQCJsJjeYcT/GtKp64C+9KCi
            Vgw/WnBZwbosSFZmqyID8aAnAxaGMkZ2B2pUZHTtCkBf6d9c0tuWb5yF8npV77sE
            bZqeNg2GU7EvH3WPgPbQVT7+Qb+WbY3EEPgJHLytch61Rm/TRQ3OqD0B+Gs7YjAU
            fEspmk1JJ6DWuXX13SHoGWgVnO7rXBnCJaGnGpONtggG4oO5hrwnMzQZKh5eZDhC
            7tkNPqVDoLv+QqnFk8q6k8hhxnVf+aw56IdsebN+9Bi+Lv6OQ+stKUo/u/RTW2z1
            odCOwc8DPF3jbacJsOmLhl3ciuWGOckx9KCvaBeTTgkPdLQH1gpNha2tnqgxUXmC
            -----END CERTIFICATE-----""",

            """
            MIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKkwggSlAgEAAoIBAQDnOL2hB/GYyYzi
            Xo/ppxi7V1LfMMLeHt0lZbYlrmNxUlln4naI4B4Lkg75eb1YDgC7MZQd5nKijK9D
            kq52Z2zLxaqBYnLxKJ36qKPqbtTL3I8mfUvVEeNIDN/8YTAtsuIEQi54dNtQVrB4
            YReMdnUq+xCKLAfEio4QLEQr7KtyCBXHZpM7RYRT0giQFvDU2kls9lFLeqKXgocn
            A7VpoL0V12hpxDeJoRm1szf0M5YXGJumQLaE5qM/+P2OOhw/T+xkupy2GF02s6FB
            XkH7NrFIjtuBSaVhSvCG/N7njWSn339thr3kiPEaCS4KSH5EE6FEazxZQrTCbkQQ
            +v3y1pS1AgMBAAECggEBAJQAKLkTWZx/njMjbiCT+Wuo6H2+O21r+ge/BAk4h6R4
            nou1VEQmmHS1h+o992mOhP9NK867vDK5tFGfaRaW+vevzYTF3GbqpbxVB56+VG0s
            /2AWoVx/96gdvZ1RJEKMFsm9BvvJaLwS0SAsnaMmC7d4Ps0Cg/JU8bv+aaBn/BGf
            TWiofYWeUk6llco4kO9H2APxUVzlaUUU/cPAJqX7XktnhDCI9/esuVg7nVR0XxOF
            GDrV/jdqSYmSbp4aTRXgI9nwxOmlKiGgevTrCUXl3/KaJxZekllVjushY1VVzgbY
            K5R4bcN5MXMmFdgF9DTEW72RqEfg9iXqyhYbZp3Q/UECgYEA/yiaJd0w2HS22HSg
            o4dJ072WbyR3qUqQmPbSUn9hBQTJAz1eX3cci8u4oawo/S+jN38b/DfpSg3eIMLB
            vnXW3wZtodpJnFaweKd3yUaSF2r9vZRHJgfPfe67VbruEOF6BsCjTq/deGeNnGeH
            /IDVn9WtSeRX/bv/s5YHvGaHGGUCgYEA5/vugmilOBq979EqksCG/7EQHSOoEukO
            J/aunDyEwz+BMEHOEW7tDMUefdiSfnGXSW+ZTmpmvc+aLk37Xuo34jpugK5ayUFY
            iYVgiqdnygGiBevBM2o0O/parQkAGEB8GPButrYItUzubUgXnuw+EdMiXGnpjJaK
            S3dPYJEHvhECgYEAjqIIwV/LPUTJLWjMn30yBN43KLvu9ECNYiSfX6R6/I43O8tj
            ZOQ1nePsutt9MkMd7xjr8OrkSxRDdnbITQqcaaGzSUW33mALV/btnCMJ6XNSklZA
            C39UOuZn7D2JdQBF8V5gK81ddUAVxjeNqdXvFOEidGrj0R/1iVM10dhSbo0CgYBk
            P8GtR02Gtj+4P/qW2m48VqbxALSkH2SHrpl8WMbCnVHVqcpETFxSNWjc11dPHwVS
            rdBhS6fEhM9LDVYAiVTHBZs1LqN67ys0mpfCs18ts5Dx4BRohI+4D5NZzVbmJA+8
            s0IU4QtYVbt/LDVQ7yRPjZ7+sqJDp9ZxkEiUIXhoEQKBgQCPG2f8BhsCYNAOV60F
            hJVjhDdf59Mia3B2J9SSnrg6Tl2rWB7GPP19TiSPFS6Sn95mWrMjZ2ZuOXtCYV4H
            +hmu87AV2CXFhW5c588cajXMT92GFVMSXdE1OHRzDjhpH+/ll8SnmQa7sXmEV36+
            sawd7mwcB9IEi562wglADxBUCA==
            """),

    // Version: 3 (0x2)
    // Serial Number: 14845986384898254166 (0xce0789f5ac256556)
    // Signature Algorithm: sha1WithRSAEncryption
    // Issuer: C=US, ST=California, O=Example, OU=Test
    // Validity
    //     Not Before: Jan 26 04:50:29 2022 GMT
    //     Not After : Feb 25 04:50:29 2022 GMT
    // Subject: C=US, ST=California, O=Example, OU=Test, CN=www.example.com
    // Public Key Algorithm: rsaEncryption
    SERVER_EXAMPLE_RSA("RSA",
            """
            -----BEGIN CERTIFICATE-----
            MIIDjDCCAnSgAwIBAgIJAM4HifWsJWVWMA0GCSqGSIb3DQEBBQUAMEMxCzAJBgNV
            BAYTAlVTMRMwEQYDVQQIDApDYWxpZm9ybmlhMRAwDgYDVQQKDAdFeGFtcGxlMQ0w
            CwYDVQQLDARUZXN0MB4XDTIyMDEyNjA0NTAyOVoXDTIyMDIyNTA0NTAyOVowXTEL
            MAkGA1UEBhMCVVMxEzARBgNVBAgMCkNhbGlmb3JuaWExEDAOBgNVBAoMB0V4YW1w
            bGUxDTALBgNVBAsMBFRlc3QxGDAWBgNVBAMMD3d3dy5leGFtcGxlLmNvbTCCASIw
            DQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAK/i4bVSAF6/aFHyzrFAON8Uwn2a
            Q9jFoAMowUkH6+PCexlt4wCOEMVH9IQPa1yzeVlkYRqHggz3i6aVYvd27Q+c6vOF
            FuRegWdJusyEuoXd5nwxSGiZZMLmFQswODSPmucroCG+Tq9RKY5oEKiRP8tUDqyn
            eE52PaeSR2Q6mng7BM5Llj7amVgimO3jlH2AWLLpHNTkc3j/M1QrLFV2PqN4dpxT
            OeFuVWzpfTWJSH+9Kq4u/zBMbYl8ON7DSgJAzc2BOw8VPIYIK6JIw6IDbLn4dIQf
            GikdgHKB2K6EDOc9LuX6UqQQODDFU88muAyPgpGfUQjxKZeUoTqoAFyisI0CAwEA
            AaNpMGcwCQYDVR0TBAIwADAdBgNVHQ4EFgQUQqf/4nqluVMZ8huD3I5FNqLXTqAw
            HwYDVR0jBBgwFoAUwXDYVZS/BnHcUmObUb1O6MKMAlIwGgYDVR0RBBMwEYIPd3d3
            LmV4YW1wbGUuY29tMA0GCSqGSIb3DQEBBQUAA4IBAQBl8FJD98fJh/0KY+3LtZDW
            CQZDeBSfnpq4milrvHH+gcOCaKYlB9702tAQ6rL1c1dLz/Lpw1x7EYvO8XIwXMRc
            DZghf8EJ6wMpZbLVLAQLCTggiB65XPwmhUoMvgVRVLYoXT3ozmNPt7P+ZURisqem
            0/xVVfxqmHw9Hb4DNlc7oZDgOK7IrqriaBK6Amsu3m2eThsvkwTdJfeFG3ZdV6x5
            mbaGZDMt/wgWIqyq5CZNpXPaFRH7KM4zhcoqXvFAoq9jxWOuBkJUUx5EHaxGhhPw
            SMgE1yl4O+N7GJmF/WMR5zp2LGKMqJ3vwLPv6QNkUmLwB5ZLYJ8E2dpj6DjQWa7X
            -----END CERTIFICATE-----""",

            """
            MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCv4uG1UgBev2hR
            8s6xQDjfFMJ9mkPYxaADKMFJB+vjwnsZbeMAjhDFR/SED2tcs3lZZGEah4IM94um
            lWL3du0PnOrzhRbkXoFnSbrMhLqF3eZ8MUhomWTC5hULMDg0j5rnK6Ahvk6vUSmO
            aBCokT/LVA6sp3hOdj2nkkdkOpp4OwTOS5Y+2plYIpjt45R9gFiy6RzU5HN4/zNU
            KyxVdj6jeHacUznhblVs6X01iUh/vSquLv8wTG2JfDjew0oCQM3NgTsPFTyGCCui
            SMOiA2y5+HSEHxopHYBygdiuhAznPS7l+lKkEDgwxVPPJrgMj4KRn1EI8SmXlKE6
            qABcorCNAgMBAAECggEBAJb2SvfP7BVmf+lmV9V249lE/jHECFu0M8TCZDOEowiX
            0gRfdqjxRp+tRMdcXK/yM0Nwjo+wowTyK2DNc2YnIw11h4uAPcfA/ZxjgfssKNPh
            Q4Rw4E826W8HACTcPEGQyEmF/ik4KFz9coeR9kpYcMLZ4MZ77xyZDA4Z1UDHs/Fg
            egrd4k35fxIFOavsjNuekMIjZlyQ2xU1a11QDBrZAu/pjITXXulg4jCxLbeNOOPs
            hH+Sx+jnsVV7qIBNwulzxEdpb3+NkWIMmOQK4HOSeHgjRVvSXXBPgYadMaP/Bzvx
            AwJ9WeLZg7KWHE03aOc7CSMoyHfmhXx3gD8icGpSq8ECgYEA3TWGBSgny/wket+V
            65ldWjp4NOKHbLPtBdL9ygIO+1bGfLl5srCxUWHBYdcWCXKuB5ALCBu0hMY+TlwF
            s/BzZmvVW7RLAEZZ3Q5HpawDlr9j8Kenm4X2Mqh0MSkmwIDRDHF8jRXAvWIzcBiS
            +rPZm2CMZpznUSE4X+GrvTSCBbkCgYEAy4yJ58wNUavj/tR8KySn5ygPABFZ1Uoy
            pIyabm18Oe3gCl5UulBskoN0WreTKnA4r9jGlnrgeuu5WfMK53AZKbC+YduZixW4
            QFuJBSMbFCBDt4nkhkMMR7VcV4jIqaOK7qNrs7ubGTZhsG8wj6/WWdCp3Avnx1rS
            WCCoYNhAK3UCgYADaLnCBpZmbGJbimqTEPABXflQR1Vy9WrnthK3NETq1rGEZo9b
            k6GH8Yu7aEcsqhnIgA3LeDHWAgAf0Qc9eK0unObS3Ppy7KKh54BvKzF690QhB1Rr
            7yqWKUZxI4M3YETYfj8/JWCtCoBkb9yEBJWL8Xb4dd6Sv4JQ5/dvmQmP8QKBgBX+
            5+Aeksnik060U36uBV7bW1OcjGKaFALoFsAcILJ53B4Ct5Eyo6jpf6dV8xdA7T9D
            Y6JbQOrHkk4AD4uW94Ej0k7s1hjLjg+WVKYzdvejzO2GfyVrFWaiWIo1A8ohHCBR
            lI/llAsTb1cLjOnaDIXEILbgqnlGfTh8vvVIKRcJAoGALF6Q1Ca2GIx4kJZ2/Az5
            qx89q95wQoWVHcJCA91g/GKg/bD0ZJMWEhhVH3QcnDOU7afRGKOPGdSSBdPQnspQ
            1OPpPjA3U5Mffy5PJ2bHTpPBeeHOzDO4Jt073zK3N81QovJzS8COoOwuGdcocURH
            mFMnEtK7d+EK1C1NTWDyNzU=
            """),


    // Version: 3 (0x2)
    // Serial Number: 14845986384898254167 (0xce0789f5ac256557)
    // Signature Algorithm: sha1WithRSAEncryption
    // Issuer: C=US, ST=California, O=Example, OU=Test
    // Validity
    //     Not Before: Jan 26 04:50:29 2022 GMT
    //     Not After : Feb 25 04:50:29 2022 GMT
    // Subject: C=US, ST=California, O=Example, OU=Test, CN=Do-Not-Reply
    // Public Key Algorithm: rsaEncryption
    CLIENT_EXAMPLE_RSA("RSA",
            """
            -----BEGIN CERTIFICATE-----
            MIIDkjCCAnqgAwIBAgIJAM4HifWsJWVXMA0GCSqGSIb3DQEBBQUAMEMxCzAJBgNV
            BAYTAlVTMRMwEQYDVQQIDApDYWxpZm9ybmlhMRAwDgYDVQQKDAdFeGFtcGxlMQ0w
            CwYDVQQLDARUZXN0MB4XDTIyMDEyNjA0NTAyOVoXDTIyMDIyNTA0NTAyOVowWjEL
            MAkGA1UEBhMCVVMxEzARBgNVBAgMCkNhbGlmb3JuaWExEDAOBgNVBAoMB0V4YW1w
            bGUxDTALBgNVBAsMBFRlc3QxFTATBgNVBAMMDERvLU5vdC1SZXBseTCCASIwDQYJ
            KoZIhvcNAQEBBQADggEPADCCAQoCggEBAODbsCteHcAg3dktUO5fiPTytIahKZMg
            U2h6h0+BT803tYcdN6WDHnLNlU/3iFnBMpyTwzWhYIftC9c/TIkXBAGfWl4HHQgc
            x08NHPms0E+GF6CDthvHERSvRCBrIyVna0KIZPemBzUfeBeNdiqwsLvg+C5dqWu5
            YcILvL6GzTMvdMwJeEX+c2ZYHibqd8aZydWsT+IPVZnueDX6KTOnYvexLFIoid2a
            62FavnMWiPKnICertDDg+gHlN2XceW3tlYQwO+HMig4DH3u2x0SApOoM3y8k29Ew
            Fn6wquSRomcbDiI8SEOeaBFenu6W0g24iaxIZfqEM52kPbQFdzqg+O0CAwEAAaNy
            MHAwCQYDVR0TBAIwADAdBgNVHQ4EFgQU0t+I5iAfPq6C/I2CSvTKHGK6+2kwHwYD
            VR0jBBgwFoAUwXDYVZS/BnHcUmObUb1O6MKMAlIwIwYDVR0RBBwwGoEYZG8tbm90
            LXJlcGx5QGV4YW1wbGUuY29tMA0GCSqGSIb3DQEBBQUAA4IBAQBqWk35XXpb3L+N
            7kPlNmSlhfamenVOVYxPBq/tSFpl728CV0OrGAy79awEFDvzhpbBg9Mz7HS/a7ax
            f+lBbsAt1maWlsVSsaaJrmy3znl9CZiIg+ykZ5ZzLS5FkIbQ6LkzvxYZJ1JYCzXm
            P/5+rbQyIvQaDGL23PmE7AB2Q0q+imt4b9HKs+SnI4XERyj8OF/kseRtILtP2ltV
            r+3XgcBxwyU17CLwsHrjnQ8/1eGovBKzGAfUXeHBdzOuD3ZemjnqwlzQTf2TAkBP
            OuMc8gr2Umc5tMtdiRUFPODO7DG7vB7LKEsJGKWLUtGbR+3S55lIcCF5NFZ//TNZ
            4x2GPOJ+
            -----END CERTIFICATE-----""",

            """
            MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDg27ArXh3AIN3Z
            LVDuX4j08rSGoSmTIFNoeodPgU/NN7WHHTelgx5yzZVP94hZwTKck8M1oWCH7QvX
            P0yJFwQBn1peBx0IHMdPDRz5rNBPhhegg7YbxxEUr0QgayMlZ2tCiGT3pgc1H3gX
            jXYqsLC74PguXalruWHCC7y+hs0zL3TMCXhF/nNmWB4m6nfGmcnVrE/iD1WZ7ng1
            +ikzp2L3sSxSKIndmuthWr5zFojypyAnq7Qw4PoB5Tdl3Hlt7ZWEMDvhzIoOAx97
            tsdEgKTqDN8vJNvRMBZ+sKrkkaJnGw4iPEhDnmgRXp7ultINuImsSGX6hDOdpD20
            BXc6oPjtAgMBAAECggEAKjqX900RoUeK4oKUNHBUtEvwg2g4+pyTjYeVaeULK6tO
            uDVQghEB4uWhKQd/3/tcmfNWMfhAvMZT9vS4Vvavle5rdkU3upJNDBeWXX2LEaRJ
            Q6f4x3a3Sn8v+DamvxuRFUmwTKItsFhcoW+7xYCxcFdrxKlqbATAy0SRCecfGoFw
            9pAsFIgIqLGQtV9V9fZWKdXIfLkH3venNImHt0n5pYadl4kZu0gNCmOGRO1MqB51
            bdAck3dBg22TE5+sZBIZmCjBAEbrc2RUQu5mAoDs8eeenxBlFYszWZxqM8BkJL5e
            SqQkbc18E8Gzdx52xEx6BCLTq0MITKliKX4B2tsQsQKBgQD1DIvt13yg9LlyoiAb
            Fc1zzKZQBPRgDUJCGzCPeC6AqbPUjoxvFAHxGNgmJXqAXwsqcs88qlSGAjk6ANAx
            fHWUQ3UmkZjGvV0ru3rIPcXvS7isjzm/cbq1YZua6+ohFl4+hojc/iyUrOaCd9uY
            V2zwrr+A0JJrlev72niEuAtEmwKBgQDq6CaLP79dHqAOIV43+SyX7KdwNkMMWIR7
            El6E/74V0IWOYWFXLmV4sX6BKi0ZBTFZ3wKwMTDqQBYD2/a7gq43HjzuWu+2dFhA
            pCQumMOKNqcNS9NldqoxAiGLxC+JMhGGyf3RO0Ey9gdPnQuje3133Wce8WWaHHv2
            lD5BcmzdFwKBgQCCeca7wiPy07s2ZUqxAT/eq5XWP30a85RW/IEzsusXyMQepjPy
            JPYPuInGbeg3F+QrGuxrQco1fFOaJbq0zq8QXYawHY/6KfPFCFMM8Y9FpczT3IME
            A3tFfo5Kw9hq+6z8n8eZ26BDHXiy+Tysdchksrb20JdVv4LiG+ZVzGT7hwKBgG+b
            Bp0IF4JFh6PPBLWxRBeWT2MH1Mkr0R2r945W91fj72BbMeU63Oj/42u4vx5xEiZx
            xxQw+t2AvzTsMAicqOr1CdvxBoz4L+neUnZ1DApBtxKhIPnG7EtGiOuftToIuL0C
            gP4EmhB9RbH0mk/83vqxDUptRGl4+QiJHB76H3DXAoGASGT6tfs1/92rGqe0COm3
            aHpelvW7Wwr9AuBumE7/ur/JWAAiM4pm6w7m5H5duYZtG3eWz1s+jvjy8jIPBIkN
            RA2DoneC2J/tsRRNFBqZrOWon5Jv4dc9R79qR13B/Oqu8H6FYSB2oDyh67Csud3q
            PRrz4o7UKM+HQ/obr1rUYqM=
            """);

    final String keyAlgo;
    final String certStr;
    final String privateKeyStr;

    // Trusted certificate
    private final static SSLExampleCert[] TRUSTED_CERTS = {
            SSLExampleCert.CA_RSA
    };

    // Server certificate.
    private final static SSLExampleCert[] SERVER_CERTS = {
            SSLExampleCert.SERVER_EXAMPLE_RSA
    };

    // Client certificate.
    private final static SSLExampleCert[] CLIENT_CERTS = {
            SSLExampleCert.CLIENT_EXAMPLE_RSA
    };

    // Set "www.example.com" to loopback address.
    static {
        String hostsFileName = System.getProperty("jdk.net.hosts.file");
        String loopbackHostname =
                InetAddress.getLoopbackAddress().getHostAddress() +
                " " + "www.example.com    www.example.com.\n";
        try (FileWriter writer= new FileWriter(hostsFileName, false)) {
             writer.write(loopbackHostname);
        } catch (IOException ioe) {
             // ignore
        }
    }

    SSLExampleCert(String keyAlgo, String certStr, String privateKeyStr) {
        this.keyAlgo = keyAlgo;
        this.certStr = certStr;
        this.privateKeyStr = privateKeyStr;
    }

    public static SSLContext createClientSSLContext() throws Exception {
        return createSSLContext(TRUSTED_CERTS, CLIENT_CERTS);
    }

    public static SSLContext createServerSSLContext() throws Exception {
        return createSSLContext(TRUSTED_CERTS, SERVER_CERTS);
    }

    private static SSLContext createSSLContext(
            SSLExampleCert[] trustedCerts,
            SSLExampleCert[] endEntityCerts) throws Exception {
        char[] passphrase = "passphrase".toCharArray();

        // Generate certificate from cert string.
        CertificateFactory cf = CertificateFactory.getInstance("X.509");

        // Import the trusted certs.
        KeyStore ts = null;     // trust store
        if (trustedCerts != null && trustedCerts.length != 0) {
            ts = KeyStore.getInstance("PKCS12");
            ts.load(null, null);

            Certificate[] trustedCert = new Certificate[trustedCerts.length];
            for (int i = 0; i < trustedCerts.length; i++) {
                try (ByteArrayInputStream is = new ByteArrayInputStream(
                        trustedCerts[i].certStr.getBytes())) {
                    trustedCert[i] = cf.generateCertificate(is);
                }

                ts.setCertificateEntry("trusted-cert-" +
                        trustedCerts[i].name(), trustedCert[i]);
            }
        }

        // Import the key materials.
        KeyStore ks = null;     // trust store
        if (endEntityCerts != null && endEntityCerts.length != 0) {
            ks = KeyStore.getInstance("PKCS12");
            ks.load(null, null);

            for (SSLExampleCert endEntityCert : endEntityCerts) {
                // generate the private key.
                PKCS8EncodedKeySpec priKeySpec = new PKCS8EncodedKeySpec(
                        Base64.getMimeDecoder()
                                .decode(endEntityCert.privateKeyStr));
                KeyFactory kf =
                        KeyFactory.getInstance(
                                endEntityCert.keyAlgo);
                PrivateKey priKey = kf.generatePrivate(priKeySpec);

                // generate certificate chain
                Certificate keyCert;
                try (ByteArrayInputStream is = new ByteArrayInputStream(
                        endEntityCert.certStr.getBytes())) {
                    keyCert = cf.generateCertificate(is);
                }

                Certificate[] chain = new Certificate[]{keyCert};

                // import the key entry.
                ks.setKeyEntry("end-entity-cert-" +
                                endEntityCert.name(),
                        priKey, passphrase, chain);
            }
        }

        // Create an SSLContext object.
        TrustManagerFactory tmf =
                TrustManagerFactory.getInstance("PKIX");
        tmf.init(ts);

        SSLContext context = SSLContext.getInstance("TLS");
        if (endEntityCerts != null && endEntityCerts.length != 0) {
            KeyManagerFactory kmf =
                    KeyManagerFactory.getInstance("NewSunX509");
            kmf.init(ks, passphrase);

            context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        } else {
            context.init(null, tmf.getTrustManagers(), null);
        }

        return context;
    }
}
