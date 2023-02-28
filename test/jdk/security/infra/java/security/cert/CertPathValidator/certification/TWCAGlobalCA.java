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
 * @bug 8270377
 * @summary Interoperability tests with TWCA Global Root CA from TAIWAN-CA
 * @build ValidatePathWithParams
 * @run main/othervm -Djava.security.debug=certpath TWCAGlobalCA OCSP
 * @run main/othervm -Djava.security.debug=certpath TWCAGlobalCA CRL
 */

/*
 * Obtain TLS test artifacts for TWCA Global Root CA from:
 *
 * Valid TLS Certificates:
 * https://evssldemo6.twca.com.tw
 *
 * Revoked TLS Certificates:
 * https://evssldemo7.twca.com.tw
 */
public class TWCAGlobalCA {

    // Owner: CN=TWCA Global EVSSL Certification Authority, OU=Global EVSSL Sub-CA, O=TAIWAN-CA, C=TW
    // Issuer: CN=TWCA Global Root CA, OU=Root CA, O=TAIWAN-CA, C=TW
    // Serial number: 40013304f70000000000000cc042cd6d
    // Valid from: Thu Aug 23 02:53:30 PDT 2012 until: Fri Aug 23 08:59:59 PDT 2030
    private static final String INT = "-----BEGIN CERTIFICATE-----\n" +
            "MIIFdzCCA1+gAwIBAgIQQAEzBPcAAAAAAAAMwELNbTANBgkqhkiG9w0BAQsFADBR\n" +
            "MQswCQYDVQQGEwJUVzESMBAGA1UEChMJVEFJV0FOLUNBMRAwDgYDVQQLEwdSb290\n" +
            "IENBMRwwGgYDVQQDExNUV0NBIEdsb2JhbCBSb290IENBMB4XDTEyMDgyMzA5NTMz\n" +
            "MFoXDTMwMDgyMzE1NTk1OVowczELMAkGA1UEBhMCVFcxEjAQBgNVBAoTCVRBSVdB\n" +
            "Ti1DQTEcMBoGA1UECxMTR2xvYmFsIEVWU1NMIFN1Yi1DQTEyMDAGA1UEAxMpVFdD\n" +
            "QSBHbG9iYWwgRVZTU0wgQ2VydGlmaWNhdGlvbiBBdXRob3JpdHkwggEiMA0GCSqG\n" +
            "SIb3DQEBAQUAA4IBDwAwggEKAoIBAQC7MIaeq4wMnTjA5C2LsR6HJUj6rZbs8Nmq\n" +
            "sSqFoqu6LwjrMbzkAg274EL6913MQ6eOy6VUDRzqAfgBEYcwFofe/w8nC7Q6Nrzz\n" +
            "xTkl9lovXLJIm0CI44Qk2IhiCkoYaPlIoqexqnm3Fc2QRdRNeLk2pU/s86DpGrwT\n" +
            "BqRRRkziBlhcgo7K5Z9ihf+c82DT31iIUIi2nr0ES1eaRR7zpKrzJPZ8foNxRPwT\n" +
            "2D0tJWQJ4hNzbFGSKsSzshdwQ/p4JP9AEjK2eeXXbEePt0/JarwBjO2Lwign38/g\n" +
            "0ZiP3uE47bItxZhgXlnR5L/0bhJitE6U1xgVFbbrQnG2B2kZxVKxAgMBAAGjggEn\n" +
            "MIIBIzAfBgNVHSMEGDAWgBRI283ejulJclqI6LHYPQezuWtmUDAdBgNVHQ4EFgQU\n" +
            "br2hK87kwtUodFy92YxvBHIqBt4wDgYDVR0PAQH/BAQDAgEGMDgGA1UdIAQxMC8w\n" +
            "LQYEVR0gADAlMCMGCCsGAQUFBwIBFhdodHRwOi8vd3d3LnR3Y2EuY29tLnR3LzBJ\n" +
            "BgNVHR8EQjBAMD6gPKA6hjhodHRwOi8vUm9vdENBLnR3Y2EuY29tLnR3L1RXQ0FS\n" +
            "Q0EvZ2xvYmFsX3Jldm9rZV80MDk2LmNybDASBgNVHRMBAf8ECDAGAQH/AgEAMDgG\n" +
            "CCsGAQUFBwEBBCwwKjAoBggrBgEFBQcwAYYcaHR0cDovL1Jvb3RPY3NwLnR3Y2Eu\n" +
            "Y29tLnR3LzANBgkqhkiG9w0BAQsFAAOCAgEAaOmLaZ2+WN2EtB6feuSV5KnL88ck\n" +
            "I9jsUTB4YtKsv0ViORkeBMCQur5OoAgRE9VYdRVlWHN0zJAX232fdoZmnajl8gtj\n" +
            "u0AOOyDDJ7Vlh38rDMRlX/u+MS2DFcsq5Vd3EMwJsWWFR9D3Dcey+Tu9uEmEdqeB\n" +
            "+Erd4YjCeV9PyOW3SzPQ47RdW6XYmHArPh65/LcmSxTn/lxQy/NEBGGWqhm6s6n1\n" +
            "49mPq4MtQcMLo/NBI+8jv7BVjnThbbEh2edHHxMNiAd5kLZFDCyJuFkoezjWL4AH\n" +
            "ratXdoHtqvqtPoy97LyGrLrJeh+0hkO9u8QOt2gF7BEhNfid7o5dnsPRk+8l77Hn\n" +
            "T1dvBs++M0r0QG4AWMSMj9uUn6rhl4FGTvAsyB1fA8p/xCLoIEetIpKRP3BD+ve2\n" +
            "eYjWPorR/0W77iMTeoQEeuxDIxi2J/U9QLKKvzzqBy1TYrqqPe5YxqHLNAcfHZvo\n" +
            "BTPPbtP0WAiXrJiELTYcqFXETvQcGw0XjoUZNvJE8RD7vssSNT17RKU8iBRX7CbL\n" +
            "AB3T8gYykPMJTUqQSmdgEdVRBcqRMMdU+XRAEoU/Mz5oHAkm3ZNTDNwsEp2Dg1/b\n" +
            "qzfPMhg4/3/YyWzGrzNeCSWZkjYImAzLCvN0D5rbdVHEmFIrEJt+igocGozroq5x\n" +
            "DT5KhixlrqexzWE=\n" +
            "-----END CERTIFICATE-----";

    // Owner: OID.2.5.4.17=100, STREET="10F.,NO.85,Yanping S. Rd.,Taipei City 100,Taiwan (R.O.C)",
    // SERIALNUMBER=70759028, OID.1.3.6.1.4.1.311.60.2.1.3=TW, OID.1.3.6.1.4.1.311.60.2.1.2=Taiwan,
    // OID.1.3.6.1.4.1.311.60.2.1.1=Taipei, OID.2.5.4.15=Private Organization,
    // CN=evssldemo6.twca.com.tw, OU=System, O=TAIWAN-CA INC., L=Taipei, ST=Taiwan, C=TW
    // Issuer: CN=TWCA Global EVSSL Certification Authority, OU=Global EVSSL Sub-CA,
    // O=TAIWAN-CA, C=TW
    // Serial number: 47e60000000f72367af31bd4f5e1ff68
    // Valid from: Mon Mar 14 00:56:49 PDT 2022 until: Fri Mar 31 08:59:59 PDT 2023
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIIATCCBumgAwIBAgIQR+YAAAAPcjZ68xvU9eH/aDANBgkqhkiG9w0BAQsFADBz\n" +
            "MQswCQYDVQQGEwJUVzESMBAGA1UEChMJVEFJV0FOLUNBMRwwGgYDVQQLExNHbG9i\n" +
            "YWwgRVZTU0wgU3ViLUNBMTIwMAYDVQQDEylUV0NBIEdsb2JhbCBFVlNTTCBDZXJ0\n" +
            "aWZpY2F0aW9uIEF1dGhvcml0eTAeFw0yMjAzMTQwNzU2NDlaFw0yMzAzMzExNTU5\n" +
            "NTlaMIIBRDELMAkGA1UEBhMCVFcxDzANBgNVBAgTBlRhaXdhbjEPMA0GA1UEBxMG\n" +
            "VGFpcGVpMRcwFQYDVQQKEw5UQUlXQU4tQ0EgSU5DLjEPMA0GA1UECxMGU3lzdGVt\n" +
            "MR8wHQYDVQQDExZldnNzbGRlbW82LnR3Y2EuY29tLnR3MR0wGwYDVQQPExRQcml2\n" +
            "YXRlIE9yZ2FuaXphdGlvbjEXMBUGCysGAQQBgjc8AgEBEwZUYWlwZWkxFzAVBgsr\n" +
            "BgEEAYI3PAIBAhMGVGFpd2FuMRMwEQYLKwYBBAGCNzwCAQMTAlRXMREwDwYDVQQF\n" +
            "Ewg3MDc1OTAyODFBMD8GA1UECRM4MTBGLixOTy44NSxZYW5waW5nIFMuIFJkLixU\n" +
            "YWlwZWkgQ2l0eSAxMDAsVGFpd2FuIChSLk8uQykxDDAKBgNVBBETAzEwMDCCASIw\n" +
            "DQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBANKX5Bg7Dv9JYtc0PFv1oXHK+8Ke\n" +
            "6DeARAPMckrXiH4oSnft3N8wtld3ARsRBrVYCfY525kfYNy22p5BUinHBlfveZnB\n" +
            "UikTXjsbbe1SXlVs9K0SReTruL02g873NAWGD5XMZlQN+3UTb4VYXD6aVudr7MEu\n" +
            "AAHWv68gsskTokCyiykSmZLvhJkhNIcMJCCsI1EoDNI2/WHkMtFbaKQb/Mi9RptD\n" +
            "gIhApP4jeoa9VEDumCrtwFPrKW0WS63tBVuJq97BVbvJNzD+PoOuaPTdyLbYWMLm\n" +
            "GQbValrXmD3TOlElo+MP2MRvKYet7mnvTwY1eL8DIGL4XsHEzDAnCSQK64UCAwEA\n" +
            "AaOCA7wwggO4MB8GA1UdIwQYMBaAFG69oSvO5MLVKHRcvdmMbwRyKgbeMB0GA1Ud\n" +
            "DgQWBBT1FKhICxXKpIWqKAcwCHEV19+vTTBTBgNVHR8ETDBKMEigRqBEhkJodHRw\n" +
            "Oi8vc3Nsc2VydmVyLnR3Y2EuY29tLnR3L3NzbHNlcnZlci9HbG9iYWxFVlNTTF9S\n" +
            "ZXZva2VfMjAxMi5jcmwwIQYDVR0RBBowGIIWZXZzc2xkZW1vNi50d2NhLmNvbS50\n" +
            "dzB/BggrBgEFBQcBAQRzMHEwRAYIKwYBBQUHMAKGOGh0dHA6Ly9zc2xzZXJ2ZXIu\n" +
            "dHdjYS5jb20udHcvY2FjZXJ0L0dsb2JhbEV2c3NsXzIwMTIucDdiMCkGCCsGAQUF\n" +
            "BzABhh1odHRwOi8vZXZzc2xvY3NwLnR3Y2EuY29tLnR3LzBIBgNVHSAEQTA/MDQG\n" +
            "DCsGAQQBgr8lAQEWAzAkMCIGCCsGAQUFBwIBFhZodHRwOi8vd3d3LnR3Y2EuY29t\n" +
            "LnR3MAcGBWeBDAEBMAkGA1UdEwQCMAAwDgYDVR0PAQH/BAQDAgWgMB0GA1UdJQQW\n" +
            "MBQGCCsGAQUFBwMBBggrBgEFBQcDAjCCAfcGCisGAQQB1nkCBAIEggHnBIIB4wHh\n" +
            "AHUAVYHUwhaQNgFK6gubVzxT8MDkOHhwJQgXL6OqHQcT0wwAAAF/h24NZQAABAMA\n" +
            "RjBEAiAP+MPmaeL6ZZ8HoivEmDyJ3laerZiyWZetyom4dT2BaAIgWI3+BZyBDtjd\n" +
            "dwJBAf232wFdCLGHw/7A+ikVwNqPfAgAdwDoPtDaPvUGNTLnVyi8iWvJA9PL0RFr\n" +
            "7Otp4Xd9bQa9bgAAAX+HbhAMAAAEAwBIMEYCIQCoTYKgiAZRZLwryWvlO7lRlBAO\n" +
            "bdQEQLjUyDqZCWDZPAIhAP//fV1aV7zNmpAHlzLO1oifmz65YvsIGM+mAIRRHyUN\n" +
            "AHcAb1N2rDHwMRnYmQCkURX/dxUcEdkCwQApBo2yCJo32RMAAAF/h24NcwAABAMA\n" +
            "SDBGAiEA5lTjsJik7023HfPdK65GYmU2U+l0sSr4M/OeQRsSWPACIQDivFeHgyo7\n" +
            "PUh52Zw+O696YpTtPsh2nXvluvvqbulk8AB2AHoyjFTYty22IOo44FIe6YQWcDIT\n" +
            "hU070ivBOlejUutSAAABf4duDbcAAAQDAEcwRQIgUGzB4Lp3qAQXmRPmeYLXEId6\n" +
            "lmNxAT2a45MGJk2XqJYCIQDPEESFZw7PSLOVv22GQNrAeSinpmblxd5msFyQwvBw\n" +
            "UzANBgkqhkiG9w0BAQsFAAOCAQEAk80iquNSqmC8bGx+hLZSbW/YP4cpvzfMKu52\n" +
            "ojx2RzLKI291h0Q5osUdIOTmkWhFSEdaRzzho11eOFwNoPhPejakX9HecYRCHtSY\n" +
            "AsqOJbeawGZmbDRbOfmcXAscOIOjiAZTkVQFT6TIR1l1R60gamhXYPMuLuCof836\n" +
            "xz+yFkP3nzTq3GriVCw0McsJmKZQwjYXgDvPeaHXwjFbTbRHMwzvLa7a1t1f/osQ\n" +
            "sLvVUJxxdG0Sm4/jWibWnqFe4PLImLFLfFl9TSPLEn/46O2HUPf2ZdW6skU+NfKY\n" +
            "tP2SVww5y+faBNfbgC0ZVCe1VJC4KEGj8cwvBy0xmlmjpiCnDg==\n" +
            "-----END CERTIFICATE-----";

    // Owner: OID.2.5.4.17=100, STREET="10F.,NO.85,Yanping S. Rd.,Taipei City 100,Taiwan (R.O.C)",
    // SERIALNUMBER=70759028, OID.1.3.6.1.4.1.311.60.2.1.3=TW, OID.1.3.6.1.4.1.311.60.2.1.2=Taiwan,
    // OID.1.3.6.1.4.1.311.60.2.1.1=Taipei, OID.2.5.4.15=Private Organization,
    // CN=evssldemo7.twca.com.tw, OU=System, O=TAIWAN-CA INC., L=Taipei, ST=Taiwan, C=TW
    // Issuer: CN=TWCA Global EVSSL Certification Authority, OU=Global EVSSL Sub-CA,
    // O=TAIWAN-CA, C=TW
    // Serial number: 47e60000000f723876b42e5ce57c1e7e
    // Valid from: Mon Mar 14 01:05:17 PDT 2022 until: Fri Mar 31 08:59:59 PDT 2023
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n" +
            "MIIIAzCCBuugAwIBAgIQR+YAAAAPcjh2tC5c5XwefjANBgkqhkiG9w0BAQsFADBz\n" +
            "MQswCQYDVQQGEwJUVzESMBAGA1UEChMJVEFJV0FOLUNBMRwwGgYDVQQLExNHbG9i\n" +
            "YWwgRVZTU0wgU3ViLUNBMTIwMAYDVQQDEylUV0NBIEdsb2JhbCBFVlNTTCBDZXJ0\n" +
            "aWZpY2F0aW9uIEF1dGhvcml0eTAeFw0yMjAzMTQwODA1MTdaFw0yMzAzMzExNTU5\n" +
            "NTlaMIIBRDELMAkGA1UEBhMCVFcxDzANBgNVBAgTBlRhaXdhbjEPMA0GA1UEBxMG\n" +
            "VGFpcGVpMRcwFQYDVQQKEw5UQUlXQU4tQ0EgSU5DLjEPMA0GA1UECxMGU3lzdGVt\n" +
            "MR8wHQYDVQQDExZldnNzbGRlbW83LnR3Y2EuY29tLnR3MR0wGwYDVQQPExRQcml2\n" +
            "YXRlIE9yZ2FuaXphdGlvbjEXMBUGCysGAQQBgjc8AgEBEwZUYWlwZWkxFzAVBgsr\n" +
            "BgEEAYI3PAIBAhMGVGFpd2FuMRMwEQYLKwYBBAGCNzwCAQMTAlRXMREwDwYDVQQF\n" +
            "Ewg3MDc1OTAyODFBMD8GA1UECRM4MTBGLixOTy44NSxZYW5waW5nIFMuIFJkLixU\n" +
            "YWlwZWkgQ2l0eSAxMDAsVGFpd2FuIChSLk8uQykxDDAKBgNVBBETAzEwMDCCASIw\n" +
            "DQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBANfU7CMw3cQgjT1A184pQzz1ID6o\n" +
            "JzaDmSTdb7FwxYCMPjKyDTWa+Mq0bRlYDeArsc/Ow+bhobPAzZ/6PH0wp+42QrkU\n" +
            "9nmKOWUhbfUpxQlLvVbddRMgVlEMRyXO8tc8WnKqXvkBt4xWA1BThAGu9S1e1LIQ\n" +
            "PjhJexfqfmjbvl73RZdPZeFXjI2Se8zGjad1cSmyJXb2yccc/qK39zMQ/Bd11c6W\n" +
            "c6/ICU7cXqkSzMWdXGpQT+jvlEwhaymPpcpIK6EvpeMEo9nzGvuXD3E4b0rRRdN8\n" +
            "ngtZCOCX9DpcHi1aG0mdCVY0i+oiX0Rucj54IlOaAr+aGF71tbYBJvs6rkUCAwEA\n" +
            "AaOCA74wggO6MB8GA1UdIwQYMBaAFG69oSvO5MLVKHRcvdmMbwRyKgbeMB0GA1Ud\n" +
            "DgQWBBR+DcOTcur7/M6SSzyalRINdSJGjDBTBgNVHR8ETDBKMEigRqBEhkJodHRw\n" +
            "Oi8vc3Nsc2VydmVyLnR3Y2EuY29tLnR3L3NzbHNlcnZlci9HbG9iYWxFVlNTTF9S\n" +
            "ZXZva2VfMjAxMi5jcmwwIQYDVR0RBBowGIIWZXZzc2xkZW1vNy50d2NhLmNvbS50\n" +
            "dzB/BggrBgEFBQcBAQRzMHEwRAYIKwYBBQUHMAKGOGh0dHA6Ly9zc2xzZXJ2ZXIu\n" +
            "dHdjYS5jb20udHcvY2FjZXJ0L0dsb2JhbEV2c3NsXzIwMTIucDdiMCkGCCsGAQUF\n" +
            "BzABhh1odHRwOi8vZXZzc2xvY3NwLnR3Y2EuY29tLnR3LzBIBgNVHSAEQTA/MDQG\n" +
            "DCsGAQQBgr8lAQEWAzAkMCIGCCsGAQUFBwIBFhZodHRwOi8vd3d3LnR3Y2EuY29t\n" +
            "LnR3MAcGBWeBDAEBMAkGA1UdEwQCMAAwDgYDVR0PAQH/BAQDAgWgMB0GA1UdJQQW\n" +
            "MBQGCCsGAQUFBwMBBggrBgEFBQcDAjCCAfkGCisGAQQB1nkCBAIEggHpBIIB5QHj\n" +
            "AHcA6D7Q2j71BjUy51covIlryQPTy9ERa+zraeF3fW0GvW4AAAF/h3XNCQAABAMA\n" +
            "SDBGAiEA2rhyp8eqM/ZYawrvRqcto7JgdRIj/34AOf2uNzrmf44CIQDhzigDJEsD\n" +
            "n+rlusMFDaB4P9h2alWOF2TKX28fo9ri0AB3AFWB1MIWkDYBSuoLm1c8U/DA5Dh4\n" +
            "cCUIFy+jqh0HE9MMAAABf4d1za8AAAQDAEgwRgIhAP/rmf4AUO3HlDRxduKAC/UH\n" +
            "skuc6Fwx8DsmqTF6dBBFAiEAwZl5x2yJy8jXhjwZSF6x4lBDk7OqRcrfp2uerCZg\n" +
            "GrQAdgBvU3asMfAxGdiZAKRRFf93FRwR2QLBACkGjbIImjfZEwAAAX+Hdc3QAAAE\n" +
            "AwBHMEUCIDBLlx4UshvohU7bIHGb/fdhkMvdijPXK0kuTK5h5fQQAiEA4Pb0+d8Q\n" +
            "KSjqILxTkDHfUY4YVRC/9bdLsO1lLEpKqtoAdwB6MoxU2LcttiDqOOBSHumEFnAy\n" +
            "E4VNO9IrwTpXo1LrUgAAAX+Hdcq6AAAEAwBIMEYCIQCJC2lYS+M8xIvJd2mTlp+1\n" +
            "gZX9yEjWtQFbRqBLLcJHmwIhAMsXNTv4fYe3w2uq4feY5kjYVCMLoO/pHX96FILM\n" +
            "DV6qMA0GCSqGSIb3DQEBCwUAA4IBAQCps3c4qJaRh7XQ4CO30XA/JaLWeyVmQfyM\n" +
            "hzrRyIL64HtmbY0YLwNoulD8AKCyc5wnkyZiT8UDAvmpKdT7OUjncrtQk1cGlZwR\n" +
            "cIxg6JDlpVFggv3h0an1ZYeWjU/Dxp6T2P991Rp1Tidrnlelt4TNirekqtVhaGJw\n" +
            "RZH0fLS8swIlghsAO7Da94JHQB2gRpTgsyMEuWktfxE1QbdiywclmN6MUvTYjVeb\n" +
            "eDYPlPmpBQrVHuBa1fK+b6zCpx9QY53AFzo5P6I0L965QQU11K9AA6EeW6myTzVh\n" +
            "4me7onSYouqT/+96KO5D3s/pW4jz+lqrLYMcz01OsXgpFlftJuuI\n" +
            "-----END CERTIFICATE-----";

    public static void main(String[] args) throws Exception {

        ValidatePathWithParams pathValidator = new ValidatePathWithParams(null);

        if (args.length >= 1 && "CRL".equalsIgnoreCase(args[0])) {
            pathValidator.enableCRLCheck();
        } else {
            // OCSP check by default
            pathValidator.enableOCSPCheck();
        }

        // Validate valid
        pathValidator.validate(new String[]{VALID, INT},
                ValidatePathWithParams.Status.GOOD, null, System.out);

        // Validate Revoked
        pathValidator.validate(new String[]{REVOKED, INT},
                ValidatePathWithParams.Status.REVOKED,
                "Mon Mar 14 02:12:14 PDT 2022", System.out);
    }
}
