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

import javax.net.ssl.*;
import java.io.*;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXBuilderParameters;
import java.security.spec.PKCS8EncodedKeySpec;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;

/**
 * A template to use "www.example.com" as the server name.  The caller should
 *  set a virtual hosts file with System Property, "jdk.net.hosts.file". This
 *  class will map the loopback address to "www.example.com", and write to
 *  the specified hosts file.
 *
 *  Commands used:
 *  # Root CA
 *  > openssl req -new -config openssl.cnf -out root-ca.csr \
 *        -keyout private/root-ca.key -days 7300 -newkey rsa:2048
 *  > openssl ca -selfsign -config openssl.cnf -in root-ca.csr \
 *        -out certs/root-ca.crt -extensions v3_ca
 *  -keyfile private/root-ca.key -days 7300
 *
 *  # www.example.com
 *  > openssl req -new -keyout private/example.key \
 *        -out example.csr -days 7299 -newkey rsa:2048
 *  > openssl ca -config openssl.cnf -in example.csr \
 *        -out certs/example.crt -extensions usr_cert
 *  -keyfile private/root-ca.key -days 7299
 *
 *  # Client
 *  > openssl req -new -keyout private/client.key \
 *        -out client.csr -days 7299 -newkey rsa:2048
 *  > openssl ca -config openssl.cnf -in client.csr \
 *        -out certs/client.crt -extensions usr_cert
 *  -keyfile private/root-ca.key -days 7299
 *
 *  The key files should be in PKCS8 format:
 *  > openssl pkcs8 -topk8 -inform PEM -outform pem \
 *         -in private/example.key -out private/example-pkcs.key -nocrypt
 */
public enum SSLExampleCert {
    // Version: 3 (0x2)
    // Serial Number: 4097 (0x1001)
    // Signature Algorithm: sha256WithRSAEncryption
    // Issuer: C = US, ST = California, O = Example, OU = Test
    // Validity
    //     Not Before: Feb 25 20:12:04 2022 GMT
    //     Not After : Feb 20 20:12:04 2042 GMT
    // Subject: C = US, ST = California, O = Example, OU = Test
    // Subject Public Key Info:
    //     Public Key Algorithm: rsaEncryption
    //         RSA Public-Key: (2048 bit)
    CA_RSA("RSA",
        """
            -----BEGIN CERTIFICATE-----
            MIIDtDCCApygAwIBAgICEAEwDQYJKoZIhvcNAQELBQAwQzELMAkGA1UEBhMCVVMx
            EzARBgNVBAgTCkNhbGlmb3JuaWExEDAOBgNVBAoTB0V4YW1wbGUxDTALBgNVBAsT
            BFRlc3QwHhcNMjIwMjI1MjAxMjA0WhcNNDIwMjIwMjAxMjA0WjBDMQswCQYDVQQG
            EwJVUzETMBEGA1UECBMKQ2FsaWZvcm5pYTEQMA4GA1UEChMHRXhhbXBsZTENMAsG
            A1UECxMEVGVzdDCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAKOGhEDj
            lZ5R6o20kJdgrIRcY1he4qKLWQ4vU0thqAg4mEcKCZZn4/NL05UgJCFLwYaxMZZe
            etb/WaTRvQpDDFh7AhsMR24m6zKKJVk9E/e/8ur7sGDIVq8hZOBTq85ZdxPj/zKW
            wB1BR/RcY4DsGno1USlkV7TVeZc1qpJHTPImesevzH7zX8nhnFlf4TTQbpQt6RxU
            cr+udWpMOyP9xMerIyp7jpPy79tIaGP2x7ryt2BB9FU4RwPk4DcdfOkmdS86md1c
            GI9H5qM5rUzyqey0J8wMRLj+E0Vx0F1XELZeTtyulbIbhrBhu/KOXZG2zNIeK+2F
            XxDlx9tD+bbcJfUCAwEAAaOBsTCBrjAdBgNVHQ4EFgQULjM9fwJnC3Tp1QYM8HNL
            y60btl0wbAYDVR0jBGUwY4AULjM9fwJnC3Tp1QYM8HNLy60btl2hR6RFMEMxCzAJ
            BgNVBAYTAlVTMRMwEQYDVQQIEwpDYWxpZm9ybmlhMRAwDgYDVQQKEwdFeGFtcGxl
            MQ0wCwYDVQQLEwRUZXN0ggIQATAPBgNVHRMBAf8EBTADAQH/MA4GA1UdDwEB/wQE
            AwIBBjANBgkqhkiG9w0BAQsFAAOCAQEAR0Mk+2X/rr4kYYfHsQUIsROwDZSQhQr3
            QOeLc7fyTjkM96OHXN2dKVoOcpzgKi1goHW7lh8vVmKRQk2wfFqRZV9/kQBFK/gz
            QtN5gp+pA8Wk912Uj5gD0loiPcRf5bDElvLnr2iwt4VdKkvGIYa9Eu9CYbkf1x3t
            ahVLmrZLBkqvKxo4MG4KGYXkqtII3M6clM4ScFa/0rR1nGZOgZyqG7AMMHc01csA
            oLlEZx2hUcpJbz+sfCGUWYaF2uKJvuWMNFbGSDhfs8pOMGgelMOHaKVtgOEfEASN
            TSqzqn0vzjJ78Mi6mN7/6L/onDKzROxClw0hc6L+PIhIHftD1ckvVQ==
            -----END CERTIFICATE-----""",

        """
            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
            tG1GZkIlwM+jlJjRew05YQ==
            """),

    // Version: 3 (0x2)
    // Serial Number: 4098 (0x1002)
    // Signature Algorithm: sha256WithRSAEncryption
    // Issuer: C = US, ST = California, O = Example, OU = Test
    // Validity
    //     Not Before: Feb 25 20:31:29 2022 GMT
    //     Not After : Feb 19 20:31:29 2042 GMT
    // Subject: C = US, ST = California, O = Example, OU = Test, CN = www.example.com
    // Subject Public Key Info:
    //     Public Key Algorithm: rsaEncryption
    //         RSA Public-Key: (2048 bit)
    SERVER_EXAMPLE_RSA("RSA",
        """
            -----BEGIN CERTIFICATE-----
            MIIDaTCCAlGgAwIBAgICEAIwDQYJKoZIhvcNAQELBQAwQzELMAkGA1UEBhMCVVMx
            EzARBgNVBAgTCkNhbGlmb3JuaWExEDAOBgNVBAoTB0V4YW1wbGUxDTALBgNVBAsT
            BFRlc3QwHhcNMjIwMjI1MjAzMTI5WhcNNDIwMjE5MjAzMTI5WjBdMQswCQYDVQQG
            EwJVUzETMBEGA1UECAwKQ2FsaWZvcm5pYTEQMA4GA1UECgwHRXhhbXBsZTENMAsG
            A1UECwwEVGVzdDEYMBYGA1UEAwwPd3d3LmV4YW1wbGUuY29tMIIBIjANBgkqhkiG
            9w0BAQEFAAOCAQ8AMIIBCgKCAQEA3crcRzecIV08Muh6kA0CuVKnPkU2bLC+6bpV
            7/iBZ4D3qMwO8Q02+gP71pPNoAQ1nsifxR4k9mBVYOjar35RVpuFmLRRVMargrxg
            4WWDfVgLMhOeCy8+Tl4Mp/yRL3nkr0MJd57RCOPcPE84J/1Crq1Luy2+hsXSj25L
            VJKx2o6LE0tfwPWnufdNUHzHRuNoBR83OpqIT0uXH15THZS+0ZcQwrJMcKYe4JWl
            6oXWcsWbtTG+r7QLIRKck2IG7jjHFpE83Q6Iv2HkhctgGZofwSTZyMmJ8eClovva
            WFLDaLL2WuI3NwZM//knjMyfsEWtWsILXayCn5NTT74ClQjWQQIDAQABo00wSzAJ
            BgNVHRMEAjAAMB0GA1UdDgQWBBQ9nPjenO4PMLtMTBddNiIDsPywjzAfBgNVHSME
            GDAWgBQuMz1/AmcLdOnVBgzwc0vLrRu2XTANBgkqhkiG9w0BAQsFAAOCAQEAVOvM
            fMDOxOCkWB244cx7J+f2qZU6/1qGlJUiL0WRLRj1XEmB8AYSZEb6Os1suF8sotka
            nA9Aw1SFA/wNyrSKazXNlOKo0In1mu/OjHU7n6XYVAyDmFGziYY8zTqG1h8ZPrI7
            oAkNgnNDwmwy7uCAvMj+Q4QQ0Q4YxTHV/i3X1HuEwThRgz9cJGdDRIAsimRHDSDO
            5hsIJo6VASz0ISrYMxNZQ1og+XktdNssPK616bPf+APwXXnsWSuGkIdGDU059DII
            cTSsLTbWkTWDXAAQo+sfDZUrvqopCK000eoywEmPQrTf7O8oAQdRvTsyxwMvOONd
            EWQ9pDW9+RC8l5DtRA==
            -----END CERTIFICATE-----""",

        """
            MIIEwAIBADANBgkqhkiG9w0BAQEFAASCBKowggSmAgEAAoIBAQDdytxHN5whXTwy
            6HqQDQK5Uqc+RTZssL7pulXv+IFngPeozA7xDTb6A/vWk82gBDWeyJ/FHiT2YFVg
            6NqvflFWm4WYtFFUxquCvGDhZYN9WAsyE54LLz5OXgyn/JEveeSvQwl3ntEI49w8
            Tzgn/UKurUu7Lb6GxdKPbktUkrHajosTS1/A9ae5901QfMdG42gFHzc6mohPS5cf
            XlMdlL7RlxDCskxwph7glaXqhdZyxZu1Mb6vtAshEpyTYgbuOMcWkTzdDoi/YeSF
            y2AZmh/BJNnIyYnx4KWi+9pYUsNosvZa4jc3Bkz/+SeMzJ+wRa1awgtdrIKfk1NP
            vgKVCNZBAgMBAAECggEBAMUMAtJe7J6Tx/TuqF0swfvGHAHt2eGM0cCzpMATh1xe
            rylPSgMNG4faXDcSj4AX3U+ZrKCjHHGruo7jsc5yqm8IsxOtOAjajOwU0vnNh5mn
            zCKMXUBQk8lqM1JXyOFmKS8wnsug1NRSJIuMUjbtAf5QxlSg2oHAZUa61cBoqAyk
            KXbw9uBYnM4n8WGXdax/LLPuonjnz2Sc35CC1LhRAF/K7oyjg7KvScnphIFRaLiU
            X4tFH0nLpcao5de0fP5eUEkbUZ3hE6MEZvOsxn5CFkjH2VdtZ9D5dc3ArV3UMe26
            +3swdenriYZ73HNJDiLAdeIVh9IrGVxhH9UowF9psIUCgYEA/Ldlx4vTTlM7URFn
            luqK7D8WH9x4JiCLEGxU80nJxxIgF8eqhOFzsQemytTrf4o1xAkyyPIweHzwApCA
            lBdwC4Mc44DjoLFVdTET9hEq7E/UK81znc0mD4v8Hz2JI6h3f2sQrcEAPBvjBwtc
            TpS9WlSBKSO3NOb3Hlucq7COVKcCgYEA4KyZ+dOyKVLyGjd0g22v4YW7VC016Hql
            uQ7SN1vuI3zQMa2rZfEv5z2L7olJKrDFcmqk8W1tfElrMaSsuohm8khhx0lPtHMw
            4Su/tci/3rEUl+DPrQExdjrrDXCqpUunOAlMP9qElsNBGdkrQ6QlMnSVVi2v8Vf1
            f86Mey2UEtcCgYEAqcOlmqPigfZFnZLcjLPoOQW0HhkjmTE5WgH8GybRZmpVpsPZ
            V8R/zEeAkzbvMFEvBw7Kz9RqHTaIoKBjz5fjC8i7ClVWFGesKbqbVyx3MiH6PKaa
            aUIbtEvsRSw4SPztsWnB3YcOWlK9csj97Efc36Zu0a0NcHtLPFh8aZWEN3cCgYEA
            oQFv8oWPlmeXkcwN1iWjtfT1EtS3XhuOaXjCkuNxW8MVG5S+UHawAoGrpsyBP3Og
            e2cLPuxRWpDunYvKMH6Rb60JTRwvXzxxWdvVLbtoLHkwLcrwaKWDQZvlWCNWVtBJ
            TDH1j4jUHYpdO93SUE3wTiEX58Mj48tJ5kYpjBhUlc8CgYEA7PG3ORGqZtfCiNmj
            CxvPtQiFC+ogf+v8cMQtrKVTgpnI2pxzG+cSXYvL4cnyY2JnwqarWorUic8JU2e2
            EhW53PWUg7VpITlLsqOpATIDiviFAN4qOOxgDt5v0C1PyB3aXe2B5VA3IgczssyR
            OLy7p/DhOpu2bqnpKyIkAuzZgFc=
            """),


    // Version: 3 (0x2)
    // Serial Number: 4099 (0x1003)
    // Signature Algorithm: sha256WithRSAEncryption
    // Issuer: C = US, ST = California, O = Example, OU = Test
    // Validity
    //     Not Before: Feb 25 20:33:59 2022 GMT
    //     Not After : Feb 19 20:33:59 2042 GMT
    // Subject: C = US, ST = California, O = Example, OU = Test, CN = Do-Not-Reply
    // Subject Public Key Info:
    //     Public Key Algorithm: rsaEncryption
    //         RSA Public-Key: (2048 bit)
    CLIENT_EXAMPLE_RSA("RSA",
        """
            -----BEGIN CERTIFICATE-----
            MIIDZjCCAk6gAwIBAgICEAMwDQYJKoZIhvcNAQELBQAwQzELMAkGA1UEBhMCVVMx
            EzARBgNVBAgTCkNhbGlmb3JuaWExEDAOBgNVBAoTB0V4YW1wbGUxDTALBgNVBAsT
            BFRlc3QwHhcNMjIwMjI1MjAzMzU5WhcNNDIwMjE5MjAzMzU5WjBaMQswCQYDVQQG
            EwJVUzETMBEGA1UECAwKQ2FsaWZvcm5pYTEQMA4GA1UECgwHRXhhbXBsZTENMAsG
            A1UECwwEVGVzdDEVMBMGA1UEAwwMRG8tTm90LVJlcGx5MIIBIjANBgkqhkiG9w0B
            AQEFAAOCAQ8AMIIBCgKCAQEA2yJgm3Lthr+97vdEWTb4zaNuLTa/DkCXdmVNIQk9
            kVn2hjZrPc+JghBCaWpohGVTQ+zxplIJXk+QVZ0ePEimE7ahBClz4MlAgMpt1uxy
            mYYUAsSZDCaFUI9Cpx1f0BiSWu330196K/AfRIoT+/SOZucnpbepxyrt+Az5SKrH
            TJR/OSqeX4XKGPoRI96pKxDOV8pY5/I9h9yKGuxfufbpOdVODngVLcMKgBAkiD+2
            sguEHM+iGLx970+W6yycu1dFY1CAgWLUF3evUxe8avwePgx7lTFXnNueYt96Ny9v
            L1o/WzoBe3z1mTl5Qb//3tYbXn8vdiDYm0dT8wImpDbpvwIDAQABo00wSzAJBgNV
            HRMEAjAAMB0GA1UdDgQWBBSXqW/B1BVjNgowSwa3MBiHMkzp6zAfBgNVHSMEGDAW
            gBQuMz1/AmcLdOnVBgzwc0vLrRu2XTANBgkqhkiG9w0BAQsFAAOCAQEABIMAjT5T
            lZDV/1wmdKCyJQJ7WUjA44N5/yBGtEmpAJ0VM7/COnk8lqiYxrk50wK7lt0tiklX
            4aLqbAgnDc27z9AQGHOqB69dZprGQT9PsTByjK6i7KPGs30ygyND41j0rju/GM2e
            3xprZbusODENRyL196QV4ai0WVe1hEvv0wTMIcnXYmZHMP8ArdVRHWaDQF6zW0Mh
            QbFqklt5W0ZIl2ZmC8z7z2Z6jv/BYyDo3U96LfdCWsEKxSKiX/PGHqZu4D3A4VSE
            0+fE7cX61kgRdGvZJgFjtYxtfkXd1HlyJ48Dqilzl+rvgvR5XA68zijjN0khPhml
            wZhPIOCIaWMZYw==
            -----END CERTIFICATE-----""",

        """
            MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDbImCbcu2Gv73u
            90RZNvjNo24tNr8OQJd2ZU0hCT2RWfaGNms9z4mCEEJpamiEZVND7PGmUgleT5BV
            nR48SKYTtqEEKXPgyUCAym3W7HKZhhQCxJkMJoVQj0KnHV/QGJJa7ffTX3or8B9E
            ihP79I5m5yelt6nHKu34DPlIqsdMlH85Kp5fhcoY+hEj3qkrEM5Xyljn8j2H3Ioa
            7F+59uk51U4OeBUtwwqAECSIP7ayC4Qcz6IYvH3vT5brLJy7V0VjUICBYtQXd69T
            F7xq/B4+DHuVMVec255i33o3L28vWj9bOgF7fPWZOXlBv//e1htefy92INibR1Pz
            AiakNum/AgMBAAECggEAW0WxWW4AMyzwDnWdWU+FSBm3TUvNPkF3FNBS1NzFcSI4
            hWRrPJ6R1sOw9blleSe/C77IVA89abPYGWDM9C0KR5G89T/SzSDmJf6qy2dGwF1R
            PmnmmWH+CzTwfSzF+KYTZ55QqBDPkTd9vo2Ij1woaAIFyId8RsHBxpyYxESlqGY4
            C6IzEqxFQ0obHXasNR+dP4iOWS4ONhxeUHixcrDHxbmoqmHt0WuJwXhlOjxHgr+i
            lUPTe5y+Y2B1gVNYrN4KlDTJqJ6lu4n6MFQ46jhfddzTk3uiEOTVWK6nE8Cf0NM7
            djTzTcR8xAVpoY5XDlk0aBfEd8Np7TLSjV4vU3J04QKBgQD6scazH/H9Yu9ZbR7w
            EeN/k7uDDlgahWg8/93syzdFtSNIRGvdalNMhTfM/zXaM/Cl63gvZilWxC+56Uvg
            6QC+rBUwzZrm7ryb6hT6Zyoo4w72bw3jGOJ3e2/bclSLrAcJnL/1Gq87J3CS16wl
            NIHrlOlY8orToEdki+6HaagyEQKBgQDfxZz4Uqsa+jDO/rEm959+nz2RkaXYu1Ld
            DhYONxmlw69/BbwzOvzr88qKNbd+b+oIK8kpm7Lvpc2/cuqItTFdehmw+tGhMWYo
            XizKCeKeCByFTjXI2/PEPUHMy0D8M68Tx/Hq0NbIYqCyzkaamHhXpuJGftxGfd3/
            U0NB4WGOzwKBgQDgnyN7YfcwY1I0XUqoLk8aA2Oy5MpaUQh6B4RwZBENO2T2np/L
            TzZ9zKuX2WAGOB26fMY+KhqGLNjaike7qOpK7eM6zC6sFmMWjGHpj0A+TFwewJi/
            z48zIX2zMbjBQQ05NqLkWdmCdi8u02HiIC78x3thgEiVn/n4BE1gNXJIEQKBgEdr
            dfcXw36/vZZDWd07CU/LmUX9u3YaC498MHPnCCuM8lVTSkb7m7/fNpS4IlGbfJGR
            EApUpF6yh6GEFvD9C71u/AYtd3zAHH/j1t3BG/AeXKP7W1U5RmsqtfacJKiaAlYI
            6eBtOTAJsop/Ja+v3DD1laC0Wq+w+orEU2ISgiWnAoGBAK9/9m3RCYPNYzS/PQ2B
            AgE2FQRuY8FXxHegZo2tBBwIojPeVHO1OoThYVNgiQfW9k27dFkRwXVAtt6Jqgax
            fvOby8rWRStXH2qHVyvHicceL7iXs6v2bX20Szsy44eMkoFfAImea6ZdErLdVWvI
            fxlYpTIVpBt3Nu2BRJn28ili
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

        // Set the date for the verifying of certificates.
        DateFormat df = new SimpleDateFormat("MM/dd/yyyy");
        Date verifyingDate = df.parse("02/02/2023");

        // Create an SSLContext object.
        TrustManagerFactory tmf =
                TrustManagerFactory.getInstance("PKIX");
        if (ts != null) {
            PKIXBuilderParameters pkixParams =
                    new PKIXBuilderParameters(ts, null);
            pkixParams.setDate(verifyingDate);
            pkixParams.setRevocationEnabled(false);
            ManagerFactoryParameters managerFactoryParameters =
                    new CertPathTrustManagerParameters(pkixParams);
            tmf.init(managerFactoryParameters);
        } else {
            tmf.init((KeyStore)null);
        }

        SSLContext context = SSLContext.getInstance("TLS");
        if (endEntityCerts != null && endEntityCerts.length != 0) {
            KeyManagerFactory kmf =
                    KeyManagerFactory.getInstance("NewSunX509");
            kmf.init(ks, passphrase);

            KeyManager[] kms = kmf.getKeyManagers();
            if (kms != null && kms.length != 0) {
                KeyManager km = kms[0];
                Field verificationDateField =
                        km.getClass().getDeclaredField("verificationDate");
                verificationDateField.setAccessible(true);
                verificationDateField.set(km, verifyingDate);
            }

            context.init(kms, tmf.getTrustManagers(), null);
        } else {
            context.init(null, tmf.getTrustManagers(), null);
        }

        return context;
    }
}
