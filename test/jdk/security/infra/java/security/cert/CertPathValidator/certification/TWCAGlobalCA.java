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
 * @bug 8305975
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
    // CN=evssldemo6.twca.com.tw, O=TAIWAN-CA INC., L=Taipei, ST=Taiwan, C=TW
    // Issuer: CN=TWCA Global EVSSL Certification Authority, OU=Global EVSSL Sub-CA,
    // O=TAIWAN-CA, C=TW
    // Serial number: 47e70000001258ff71d89af7f0353fef
    // Valid from: Thu Mar 02 00:49:56 PST 2023 until: Sun Mar 31 08:59:59 PDT 2024
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIH7zCCBtegAwIBAgIQR+cAAAASWP9x2Jr38DU/7zANBgkqhkiG9w0BAQsFADBz\n" +
            "MQswCQYDVQQGEwJUVzESMBAGA1UEChMJVEFJV0FOLUNBMRwwGgYDVQQLExNHbG9i\n" +
            "YWwgRVZTU0wgU3ViLUNBMTIwMAYDVQQDEylUV0NBIEdsb2JhbCBFVlNTTCBDZXJ0\n" +
            "aWZpY2F0aW9uIEF1dGhvcml0eTAeFw0yMzAzMDIwODQ5NTZaFw0yNDAzMzExNTU5\n" +
            "NTlaMIIBMzELMAkGA1UEBhMCVFcxDzANBgNVBAgTBlRhaXdhbjEPMA0GA1UEBxMG\n" +
            "VGFpcGVpMRcwFQYDVQQKEw5UQUlXQU4tQ0EgSU5DLjEfMB0GA1UEAxMWZXZzc2xk\n" +
            "ZW1vNi50d2NhLmNvbS50dzEdMBsGA1UEDxMUUHJpdmF0ZSBPcmdhbml6YXRpb24x\n" +
            "FzAVBgsrBgEEAYI3PAIBARMGVGFpcGVpMRcwFQYLKwYBBAGCNzwCAQITBlRhaXdh\n" +
            "bjETMBEGCysGAQQBgjc8AgEDEwJUVzERMA8GA1UEBRMINzA3NTkwMjgxQTA/BgNV\n" +
            "BAkTODEwRi4sTk8uODUsWWFucGluZyBTLiBSZC4sVGFpcGVpIENpdHkgMTAwLFRh\n" +
            "aXdhbiAoUi5PLkMpMQwwCgYDVQQREwMxMDAwggEiMA0GCSqGSIb3DQEBAQUAA4IB\n" +
            "DwAwggEKAoIBAQDEgj/jtcAtGPkiBilLajzHIqfiAxpwwnKhdHwyOnqfcqur1p2R\n" +
            "Cxl0Q8jYGmY8ZUq7716XnIGN3bn3Wu10BvmHi07h8f54/G/K7xBKjkasAh44zW1P\n" +
            "hgdaxH0huRvoQOoSRCitew8YpMN4B++uOQ8yu2pWDGDdQHW4VaWt/e+QtZbQtp/b\n" +
            "7vUWgcuhxDStj97B8Dcb5PY+sbLy6dfDiXnTaSpuWhjKmEcpknagGyn4uCFBSppZ\n" +
            "/PYcTsg+Nk8Ae/SDMpc7XWBCjmxMG2GI0IVW4un9UOuElYgWVjMWnBAiGMDkVMEQ\n" +
            "jLRxEYOh+NJ3izMyD/ufLrA/YwJMI1LgFcOJAgMBAAGjggO7MIIDtzAfBgNVHSME\n" +
            "GDAWgBRuvaErzuTC1Sh0XL3ZjG8EcioG3jAdBgNVHQ4EFgQUg4msPcTFvDjwluRf\n" +
            "inEn9qMC7OYwUwYDVR0fBEwwSjBIoEagRIZCaHR0cDovL3NzbHNlcnZlci50d2Nh\n" +
            "LmNvbS50dy9zc2xzZXJ2ZXIvR2xvYmFsRVZTU0xfUmV2b2tlXzIwMTIuY3JsMCEG\n" +
            "A1UdEQQaMBiCFmV2c3NsZGVtbzYudHdjYS5jb20udHcwfwYIKwYBBQUHAQEEczBx\n" +
            "MEQGCCsGAQUFBzAChjhodHRwOi8vc3Nsc2VydmVyLnR3Y2EuY29tLnR3L2NhY2Vy\n" +
            "dC9HbG9iYWxFdnNzbF8yMDEyLnA3YjApBggrBgEFBQcwAYYdaHR0cDovL2V2c3Ns\n" +
            "b2NzcC50d2NhLmNvbS50dy8wSAYDVR0gBEEwPzA0BgwrBgEEAYK/JQEBFgMwJDAi\n" +
            "BggrBgEFBQcCARYWaHR0cDovL3d3dy50d2NhLmNvbS50dzAHBgVngQwBATAJBgNV\n" +
            "HRMEAjAAMA4GA1UdDwEB/wQEAwIFoDAdBgNVHSUEFjAUBggrBgEFBQcDAQYIKwYB\n" +
            "BQUHAwIwggH2BgorBgEEAdZ5AgQCBIIB5gSCAeIB4AB2AEiw42vapkc0D+VqAvqd\n" +
            "MOscUgHLVt0sgdm7v6s52IRzAAABhqGDiCYAAAQDAEcwRQIgd7uqvHdSTSXqNPWs\n" +
            "OQeCeT2vuKY3vj8jRcoJ9IIohqgCIQCtQfZ0lfZ1Y1GmwCTDc5NM++5mgp+ZpNWu\n" +
            "F9OKsWoCPQB2AFWB1MIWkDYBSuoLm1c8U/DA5Dh4cCUIFy+jqh0HE9MMAAABhqGD\n" +
            "iJYAAAQDAEcwRQIgIHKa+XeYyDURUq9AVYEntGS5oJitKyWZjSOlpD+udZgCIQC/\n" +
            "oVPtjJpcXP4OScYFsNWMPKUtZOO5mY5y7V65S84DrQB2ADtTd3U+LbmAToswWwb+\n" +
            "QDtn2E/D9Me9AA0tcm/h+tQXAAABhqGDh8YAAAQDAEcwRQIgYT7aPr9YCtF5TCTp\n" +
            "NICK9c5eiL6Ku/y9wM6ARgG2k1UCIQDomqlwGur+AMI4YIc1SNqyNVCyxgP1DxXP\n" +
            "FYkX6BX17gB2AO7N0GTV2xrOxVy3nbTNE6Iyh0Z8vOzew1FIWUZxH7WbAAABhqGD\n" +
            "iKkAAAQDAEcwRQIhAKTMliyTn48vvP9hN8jucD6rGZwRCqQI6suE6ADpN7bNAiB3\n" +
            "zFZFdH8eJRn3RXjD/mzbmF201sNLitp9SOYAazubljANBgkqhkiG9w0BAQsFAAOC\n" +
            "AQEAOOtzqtRFvxlJro61O0dEkDottToFh88vib3N3AofS5uW0nDpoS0L27XR8IDd\n" +
            "2NfN+2XKAQXdz2BqHnjW1nAMXUx4TAMi4jG8XpOkvpSDXbjghD5EB10FyAzCuGmv\n" +
            "mKxkVOU1DzL0kSLLQjLaJ57WUYsoE97f5O6rY9jlJpid32o1WgM1oZsBjPhO8Kiy\n" +
            "KJ5zZHppolGPtuFYMUcatiqv//pH/5piwtlYSkbwMj5nYidSrSBciBzO53HFk1pE\n" +
            "TABXFcoK3gmhWM04lysmJMwAzRUbNQVizpGDICbRjCOVnwCbutnSnka8pDHkq4Zy\n" +
            "BrUeZe2xJe8jWvukwqvNzIIvwg==\n" +
            "-----END CERTIFICATE-----";

    // Owner: OID.2.5.4.17=100, STREET="10F.,NO.85,Yanping S. Rd.,Taipei City 100,Taiwan (R.O.C)",
    // SERIALNUMBER=70759028, OID.1.3.6.1.4.1.311.60.2.1.3=TW, OID.1.3.6.1.4.1.311.60.2.1.2=Taiwan,
    // OID.1.3.6.1.4.1.311.60.2.1.1=Taipei, OID.2.5.4.15=Private Organization,
    // CN=evssldemo7.twca.com.tw, O=TAIWAN-CA INC., L=Taipei, ST=Taiwan, C=TW
    // Issuer: CN=TWCA Global EVSSL Certification Authority, OU=Global EVSSL Sub-CA,
    // O=TAIWAN-CA, C=TW
    // Serial number: 47e70000001258f036a5b513091ccb2e
    // Valid from: Tue Feb 07 02:03:08 PST 2023 until: Thu Mar 07 07:59:59 PST 2024
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n" +
            "MIIHdjCCBl6gAwIBAgIQR+cAAAASWPA2pbUTCRzLLjANBgkqhkiG9w0BAQsFADBz\n" +
            "MQswCQYDVQQGEwJUVzESMBAGA1UEChMJVEFJV0FOLUNBMRwwGgYDVQQLExNHbG9i\n" +
            "YWwgRVZTU0wgU3ViLUNBMTIwMAYDVQQDEylUV0NBIEdsb2JhbCBFVlNTTCBDZXJ0\n" +
            "aWZpY2F0aW9uIEF1dGhvcml0eTAeFw0yMzAyMDcxMDAzMDhaFw0yNDAzMDcxNTU5\n" +
            "NTlaMIIBMzELMAkGA1UEBhMCVFcxDzANBgNVBAgTBlRhaXdhbjEPMA0GA1UEBxMG\n" +
            "VGFpcGVpMRcwFQYDVQQKEw5UQUlXQU4tQ0EgSU5DLjEfMB0GA1UEAxMWZXZzc2xk\n" +
            "ZW1vNy50d2NhLmNvbS50dzEdMBsGA1UEDxMUUHJpdmF0ZSBPcmdhbml6YXRpb24x\n" +
            "FzAVBgsrBgEEAYI3PAIBARMGVGFpcGVpMRcwFQYLKwYBBAGCNzwCAQITBlRhaXdh\n" +
            "bjETMBEGCysGAQQBgjc8AgEDEwJUVzERMA8GA1UEBRMINzA3NTkwMjgxQTA/BgNV\n" +
            "BAkTODEwRi4sTk8uODUsWWFucGluZyBTLiBSZC4sVGFpcGVpIENpdHkgMTAwLFRh\n" +
            "aXdhbiAoUi5PLkMpMQwwCgYDVQQREwMxMDAwggEiMA0GCSqGSIb3DQEBAQUAA4IB\n" +
            "DwAwggEKAoIBAQDSX3co7XUdwxv8OEj7Mipq0Ot+1w+VYTFlPvdnryrv9st7ERLb\n" +
            "+xJPJo7swgqbHeHKWlwYu4lkzJq6s3nAOkuYIP/O3uVmGDiilLSAVkukz9MooyjB\n" +
            "466eArXY1VT9vpXVNmSLunAp5RU8H+2WWOUMmtJx/oYojqEbtWqnltlErvEjb2TM\n" +
            "vR16d/vXI6QtMc+IV3nZ0SVdetH2E7ZvpP5mZqVSHNnOnVjqdd69hAJ4SJgG9lCM\n" +
            "87ysm6UaJxQbEGxc6YkwrUNVet1tx2hBWltTyRw3oOBCBUwrPUTx7/pFh7yhci6p\n" +
            "AhHp1j0OzAmZHOFTM+qO1L1vlmguO8zW0zWtAgMBAAGjggNCMIIDPjAfBgNVHSME\n" +
            "GDAWgBRuvaErzuTC1Sh0XL3ZjG8EcioG3jAdBgNVHQ4EFgQUvvbgZHRNPdmGlxQS\n" +
            "fcTzM2A14EkwUwYDVR0fBEwwSjBIoEagRIZCaHR0cDovL3NzbHNlcnZlci50d2Nh\n" +
            "LmNvbS50dy9zc2xzZXJ2ZXIvR2xvYmFsRVZTU0xfUmV2b2tlXzIwMTIuY3JsMCEG\n" +
            "A1UdEQQaMBiCFmV2c3NsZGVtbzcudHdjYS5jb20udHcwfwYIKwYBBQUHAQEEczBx\n" +
            "MEQGCCsGAQUFBzAChjhodHRwOi8vc3Nsc2VydmVyLnR3Y2EuY29tLnR3L2NhY2Vy\n" +
            "dC9HbG9iYWxFdnNzbF8yMDEyLnA3YjApBggrBgEFBQcwAYYdaHR0cDovL2V2c3Ns\n" +
            "b2NzcC50d2NhLmNvbS50dy8wSAYDVR0gBEEwPzA0BgwrBgEEAYK/JQEBFgMwJDAi\n" +
            "BggrBgEFBQcCARYWaHR0cDovL3d3dy50d2NhLmNvbS50dzAHBgVngQwBATAJBgNV\n" +
            "HRMEAjAAMA4GA1UdDwEB/wQEAwIFoDAdBgNVHSUEFjAUBggrBgEFBQcDAQYIKwYB\n" +
            "BQUHAwIwggF9BgorBgEEAdZ5AgQCBIIBbQSCAWkBZwB2AFWB1MIWkDYBSuoLm1c8\n" +
            "U/DA5Dh4cCUIFy+jqh0HE9MMAAABhitUR1YAAAQDAEcwRQIhANv7DhQm67R1Ilmg\n" +
            "k5StrFQ1dqyELzZTAT3on84g0G/vAiAttP+EWWmztK2luQ7SxvQsmExDh/qGiZHq\n" +
            "NAd2a8dUIgB1AO7N0GTV2xrOxVy3nbTNE6Iyh0Z8vOzew1FIWUZxH7WbAAABhitU\n" +
            "RycAAAQDAEYwRAIgcU5n4DJaGWvTr3wZug59ItynMgCZ5z0ZVrZr2KwV70wCIHEv\n" +
            "DAwNBLGsdj5IX/4E5hnzJvS7WroSLnRB6OW931JbAHYAdv+IPwq2+5VRwmHM9Ye6\n" +
            "NLSkzbsp3GhCCp/mZ0xaOnQAAAGGK1RKDwAABAMARzBFAiBvlIvOnE8PhYJQueMh\n" +
            "AOCwgREvnAsk3Edt59lcuqPrrQIhAOSRb3UmBYkHQ6k5pUJva0Mgk0GmnLR0de0s\n" +
            "VxW3TTASMA0GCSqGSIb3DQEBCwUAA4IBAQAQB7oaouXBI6VpLzL+kzOZXSTbSClv\n" +
            "LS33DTEBI3A8LTXHbFq6c4/ZdqieUzy42Kd0i9e3hI1hwQYPgEwxpROOcldX72r0\n" +
            "EUTh0L+XrxN3YEgod6aCsjIiJlWYy6J2ZXVURnk/iWYAwYLa0JmmBGuWFjEnq4lO\n" +
            "xL1C3M2mYAEC+Beb7Xyq1rcu97p4P8igJYM+VfwXNwYYRCXUr9f4ESD7t5vXlYoE\n" +
            "c4m5KiBQD9XtZS77QRon9JCQklxTvMkxuLwWvSdzicEUzWeFp+kN/fcXL2SVsb17\n" +
            "xDPMMsMMh7L/f+uMWDYZ+wH17LYQxOLi7VXT3fv8nl2X2iD3d4CCh0Tu\n" +
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
                "Thu Mar 23 17:30:19 PDT 2023", System.out);
    }
}
