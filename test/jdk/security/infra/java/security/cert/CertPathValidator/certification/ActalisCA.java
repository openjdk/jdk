/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8189131
 * @summary Interoperability tests with Actalis CA
 * @build ValidatePathWithParams
 * @run main/othervm/timeout=180 -Djava.security.debug=certpath ActalisCA OCSP
 * @run main/othervm/timeout=180 -Djava.security.debug=certpath ActalisCA CRL
 */

 /*
 * Obtain test artifacts for Actalis CA from:
 *
 * Test website with *active* TLS Server certificate:
 * https://ssltest-active.actalis.it/
 *
 * Test website with *revoked* TLS Server certificate:
 * https://ssltest-revoked.actalis.it/
 *
 * Test website with *expired* TLS Server certificate:
 * https://ssltest-expired.actalis.it/
 */
public class ActalisCA {

    // Owner: CN=Actalis Organization Validated Server CA G3, O=Actalis S.p.A.,
    //        L=Ponte San Pietro, ST=Bergamo, C=IT
    // Issuer: CN=Actalis Authentication Root CA, O=Actalis S.p.A ./03358520967,
    //         L=Milan, C=IT
    // Serial number: 5c3b3f37adfc28fe0fcfd3abf83f8551
    // Valid from: Mon Jul 06 00:20:55 PDT 2020 until: Sun Sep 22 04:22:02 PDT 2030
    private static final String INT = "-----BEGIN CERTIFICATE-----\n" +
            "MIIHdTCCBV2gAwIBAgIQXDs/N638KP4Pz9Or+D+FUTANBgkqhkiG9w0BAQsFADBr\n" +
            "MQswCQYDVQQGEwJJVDEOMAwGA1UEBwwFTWlsYW4xIzAhBgNVBAoMGkFjdGFsaXMg\n" +
            "Uy5wLkEuLzAzMzU4NTIwOTY3MScwJQYDVQQDDB5BY3RhbGlzIEF1dGhlbnRpY2F0\n" +
            "aW9uIFJvb3QgQ0EwHhcNMjAwNzA2MDcyMDU1WhcNMzAwOTIyMTEyMjAyWjCBiTEL\n" +
            "MAkGA1UEBhMCSVQxEDAOBgNVBAgMB0JlcmdhbW8xGTAXBgNVBAcMEFBvbnRlIFNh\n" +
            "biBQaWV0cm8xFzAVBgNVBAoMDkFjdGFsaXMgUy5wLkEuMTQwMgYDVQQDDCtBY3Rh\n" +
            "bGlzIE9yZ2FuaXphdGlvbiBWYWxpZGF0ZWQgU2VydmVyIENBIEczMIICIjANBgkq\n" +
            "hkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAs73Ch+t2owm3ayTkyqy0OPuCTiybxTyS\n" +
            "4cU4y0t2RGSwCNjLh/rcutO0yoriZxVtPrNMcIRQ544BQhHFt/ypW7e+t8wWKrHa\n" +
            "r3BkKwSUbqNwpDWP1bXs7IJTVhHXWGAm7Ak1FhrrBmtXk8QtdzTzDDuxfFBK7sCL\n" +
            "N0Jdqoqb1V1z3wsWqAvr4KlSCFW05Nh4baWm/kXOmb8U+XR6kUmuoVvia3iBhotR\n" +
            "TzAHTO9SWWkgjTcir/nhBvyL2RoqkgYyP/k50bznaVOGFnFWzfl0XnrM/salfCBh\n" +
            "O0/1vNaoU8elR6AtbdCFAupgQy95GuFIRVS8n/cF0QupfPjUl+kGSLzvGAc+6oNE\n" +
            "alpAhKIS/+P0uODzRrS9Eq0WX1iSj6KHtQMNN4ZKsS4nsuvYCahnAc0QwQyoduAW\n" +
            "iU/ynhU9WTIEe1VIoEDE79NPOI2/80RqbZqdpAKUaf0FvuqVXhEcjiJJu+d0w9YN\n" +
            "b7gurd6xkaSXemW/fP4idBiNkd8aCVAdshGQYn6yh+na0Lu5IG88Z2kSIFcXDtwy\n" +
            "zjcxkW86pwkO6GekEomVBNKcv0Cey2Smf8uhpZk15TSCeyFDrZBWH9OsDst/Tnhz\n" +
            "pN156Huw3M3RRdEegt33fcyPykgt0HThxrEv9DwOzhs6lCQ5RNQJO7ZvZF1ZiqgT\n" +
            "FOJ6vs1xMqECAwEAAaOCAfQwggHwMA8GA1UdEwEB/wQFMAMBAf8wHwYDVR0jBBgw\n" +
            "FoAUUtiIOsifeGbtifN7OHCUyQICNtAwQQYIKwYBBQUHAQEENTAzMDEGCCsGAQUF\n" +
            "BzABhiVodHRwOi8vb2NzcDA1LmFjdGFsaXMuaXQvVkEvQVVUSC1ST09UMEUGA1Ud\n" +
            "IAQ+MDwwOgYEVR0gADAyMDAGCCsGAQUFBwIBFiRodHRwczovL3d3dy5hY3RhbGlz\n" +
            "Lml0L2FyZWEtZG93bmxvYWQwHQYDVR0lBBYwFAYIKwYBBQUHAwIGCCsGAQUFBwMB\n" +
            "MIHjBgNVHR8EgdswgdgwgZaggZOggZCGgY1sZGFwOi8vbGRhcDA1LmFjdGFsaXMu\n" +
            "aXQvY24lM2RBY3RhbGlzJTIwQXV0aGVudGljYXRpb24lMjBSb290JTIwQ0EsbyUz\n" +
            "ZEFjdGFsaXMlMjBTLnAuQS4lMmYwMzM1ODUyMDk2NyxjJTNkSVQ/Y2VydGlmaWNh\n" +
            "dGVSZXZvY2F0aW9uTGlzdDtiaW5hcnkwPaA7oDmGN2h0dHA6Ly9jcmwwNS5hY3Rh\n" +
            "bGlzLml0L1JlcG9zaXRvcnkvQVVUSC1ST09UL2dldExhc3RDUkwwHQYDVR0OBBYE\n" +
            "FJ+KsbXxsd6C9Cd8vojN3qlDgaNLMA4GA1UdDwEB/wQEAwIBBjANBgkqhkiG9w0B\n" +
            "AQsFAAOCAgEAJbygMnKJ5M6byr5Ectq05ODqwNMtky8TEF3O55g6RHhxblf6OegZ\n" +
            "4ui4+ElHNOIXjycbeuUGuFA4LScCC9fnI1Rnn8TI2Q7OP5YWifEfnrdp99t/tJzQ\n" +
            "hfdi7ZTdRRZZGV9x+grfR/RtjT2C3Lt9X4lcbuSxTea3PHAwwi0A3bYRR1L5ciPm\n" +
            "eAnYtG9kpat8/RuC22oxiZZ5FdjU6wrRWkASRLiIwNcFIYfvpUbMWElaCUhqaB2y\n" +
            "YvWF8o02pnaYb4bvTCg4cVabVnojUuuXH81LeQhhsSXLwcdwSdew0NL4zCiNCn2Q\n" +
            "iDZpz2biCWDggibmWxsUUF6AbqMHnwsdS8vsKXiFQJHeAdNAhA+kwpqYAdhUiCdj\n" +
            "RTUdtRNUucLvZEN1OAvVYyog9xYCfhtkqgXQROMANP+Z/+yaZahaP/Vgak/V00se\n" +
            "Hdh7F+B6h5HVdwdh+17E2jl+aMTfyvBFcg2H/9Qjyl4TY8NW/6v0DPK52sVt8a35\n" +
            "I+7xLGLPohAl4z6pEf2OxgjMNfXXCXS33smRgz1dLQFo8UpAb3rf84zkXaqEI6Qi\n" +
            "2P+5pibVFQigRbn4RcE+K2a/nm2M/o+WZTSio+E+YXacnNk71VcO82biOof+jBKT\n" +
            "iC3Xi7rAlypmme+QFBw9F1J89ig3smV/HaN8tO0lfTpvm7Zvzd5TkMs=\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=ssltest-active.actalis.it, O=Actalis S.p.A., L=Ponte San Pietro,
    //        ST=Bergamo, C=IT
    // Issuer: CN=Actalis Organization Validated Server CA G3, O=Actalis S.p A.,
    //         L=Ponte San Pietro, ST=Bergamo, C=IT
    // Serial number: 4a49e2afcd448af3b7f5f14e1cd5954
    // Valid from: Tue Mar 08 08:00:57 PST 2022 until: Wed Mar 08 08:00:57 PST 2023
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIH0jCCBbqgAwIBAgIQBKSeKvzUSK87f18U4c1ZVDANBgkqhkiG9w0BAQsFADCB\n" +
            "iTELMAkGA1UEBhMCSVQxEDAOBgNVBAgMB0JlcmdhbW8xGTAXBgNVBAcMEFBvbnRl\n" +
            "IFNhbiBQaWV0cm8xFzAVBgNVBAoMDkFjdGFsaXMgUy5wLkEuMTQwMgYDVQQDDCtB\n" +
            "Y3RhbGlzIE9yZ2FuaXphdGlvbiBWYWxpZGF0ZWQgU2VydmVyIENBIEczMB4XDTIy\n" +
            "MDMwODE2MDA1N1oXDTIzMDMwODE2MDA1N1owdzELMAkGA1UEBhMCSVQxEDAOBgNV\n" +
            "BAgMB0JlcmdhbW8xGTAXBgNVBAcMEFBvbnRlIFNhbiBQaWV0cm8xFzAVBgNVBAoM\n" +
            "DkFjdGFsaXMgUy5wLkEuMSIwIAYDVQQDDBlzc2x0ZXN0LWFjdGl2ZS5hY3RhbGlz\n" +
            "Lml0MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAsJnlOatNNth7gfqZ\n" +
            "WN8HMfp9qlkDf/YW8ReNXyTtqFEy2xZrVVmAV2XIqL1lJDYJz86mdVsz3AqIMTzo\n" +
            "GxPlmn/oEnF0YeRYQ1coKRdwP7hWSwqyMMhh+C7r5zMA9gQQVXV5wWR5U+bgvt23\n" +
            "Y/55DOqk3Fp5Odt6Lyu6xA45MwHrj2Gr/nMKe8L7f8UYPWT98MJa1+TXB24yllOw\n" +
            "rZE8gZByLBCVzDkVwRwTgu+HgY6zm5sJTvBT4tyJy4QD8u2xLWoZ5sXodrU0Z3Nf\n" +
            "xU9keMFp6CIh1t+akqFgpW81b/HWkfUO0+L6PH4hgaSPtiwp2dVFsF9v5p4on9qA\n" +
            "2j1d9QIDAQABo4IDRTCCA0EwDAYDVR0TAQH/BAIwADAfBgNVHSMEGDAWgBSfirG1\n" +
            "8bHegvQnfL6Izd6pQ4GjSzB+BggrBgEFBQcBAQRyMHAwOwYIKwYBBQUHMAKGL2h0\n" +
            "dHA6Ly9jYWNlcnQuYWN0YWxpcy5pdC9jZXJ0cy9hY3RhbGlzLWF1dGhvdmczMDEG\n" +
            "CCsGAQUFBzABhiVodHRwOi8vb2NzcDA5LmFjdGFsaXMuaXQvVkEvQVVUSE9WLUcz\n" +
            "MCQGA1UdEQQdMBuCGXNzbHRlc3QtYWN0aXZlLmFjdGFsaXMuaXQwUQYDVR0gBEow\n" +
            "SDA8BgYrgR8BFAEwMjAwBggrBgEFBQcCARYkaHR0cHM6Ly93d3cuYWN0YWxpcy5p\n" +
            "dC9hcmVhLWRvd25sb2FkMAgGBmeBDAECAjAdBgNVHSUEFjAUBggrBgEFBQcDAgYI\n" +
            "KwYBBQUHAwEwSAYDVR0fBEEwPzA9oDugOYY3aHR0cDovL2NybDA5LmFjdGFsaXMu\n" +
            "aXQvUmVwb3NpdG9yeS9BVVRIT1YtRzMvZ2V0TGFzdENSTDAdBgNVHQ4EFgQUIbcm\n" +
            "54DVM6gC8DYhvnZg8ILaLrAwDgYDVR0PAQH/BAQDAgWgMIIBfQYKKwYBBAHWeQIE\n" +
            "AgSCAW0EggFpAWcAdQCt9776fP8QyIudPZwePhhqtGcpXc+xDCTKhYY069yCigAA\n" +
            "AX9qTFEkAAAEAwBGMEQCIFB4RW+Fca/jj96sFg9JtZVe/CAQq74HAezTi2AD07qL\n" +
            "AiBej8APns5uKmaHNYbU6lel6kdowIaUY/+iqX82e2KhrAB2AOg+0No+9QY1MudX\n" +
            "KLyJa8kD08vREWvs62nhd31tBr1uAAABf2pMUVMAAAQDAEcwRQIgcopYpSUDiQ2C\n" +
            "7j06vgbfsn3ux4REvpbrbWatifLtfVMCIQCi96i+4EhAUOw4dumA7hJwlG+qD/+5\n" +
            "uSL3aKB9KR7apAB2AG9Tdqwx8DEZ2JkApFEV/3cVHBHZAsEAKQaNsgiaN9kTAAAB\n" +
            "f2pMUYEAAAQDAEcwRQIgdCNjaV7nQcCiVefX28u1vtQMy+rqT4F4i9EVJ2xbqbQC\n" +
            "IQCrpcYqt53tX/rSMoGnjFhDGnMhnYyc2AqzpokfhmdcVTANBgkqhkiG9w0BAQsF\n" +
            "AAOCAgEAfXISBKP1dZQv1kkWZVDXiVY/fv+068DKq2e8hgBcsN6b9a2rlVfBU2iq\n" +
            "W9KqFNET5GDWf1wjM71Itjau8b1A3+apcNdEGQk3eqIOymK5kVtVvAI2ahp4926x\n" +
            "Kkt/sexmi1pJGA+eLfTixkCoaESh5P8U7HDW/vUFXm2AtLQih+oT5OVoYt5e9pXr\n" +
            "hr8oadm/ZDJxiyDL1vcTIsl2TM4/Fpo2IWxYzUC+YshnuLiRwWI840maJmWFx/lJ\n" +
            "Pzdik3P51Uef7VsCSBhTxER09/B4IrEUMDAhVgG5QNbcFSHvnmpV8JLrNuBKUROU\n" +
            "xnDsWieKlb5YO6S6PjGOncOrd+k4RCIYRaekSnx52WBKkpqxMEv/rjY1Glx4Cota\n" +
            "mpNiYDvZHGzrRQtY2eH17XhFatBxEEbJMA+0QPbFksHcKxAxJgMDncqag4TDq5fT\n" +
            "I2NUxqiB51F5w0x+++lyLnUZ+z4BJFZ73VdtfoJ2fsuRhemOoZjHPi/V2exXpAfb\n" +
            "pomha3KCrTcuFv1lj8mPx5L4ciNPxuDFgjeXEaTGjS8IvdNoJIrgdHdahMwkwS/y\n" +
            "wei7FJ1Ey0maqRUpUlAY6sIQPQ/KDltTuKX/C94C5pYLI0JXCScr5xg6C+r2ckbA\n" +
            "rjhpn3C/NptVyZgT8bL4XT5ITrAjwPciBj0yxYzUkrLZO1wKQSQ=\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=ssltest-revoked.actalis.it, O=Actalis S.p.A., L=Ponte San Pietro,
    //        ST=Bergamo, C=IT
    // Issuer: CN=Actalis Organization Validated Server CA G3, O=Actalis S.p .A.,
    //         L=Ponte San Pietro, ST=Bergamo, C=IT
    // Serial number: 3dbdba0fefe7c6bd978220de52ffe3b2
    // Valid from: Fri Oct 08 02:23:49 PDT 2021 until: Sat Oct 08 02:23:49 PDT 2022
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n" +
            "MIIH1zCCBb+gAwIBAgIQPb26D+/nxr2XgiDeUv/jsjANBgkqhkiG9w0BAQsFADCB\n" +
            "iTELMAkGA1UEBhMCSVQxEDAOBgNVBAgMB0JlcmdhbW8xGTAXBgNVBAcMEFBvbnRl\n" +
            "IFNhbiBQaWV0cm8xFzAVBgNVBAoMDkFjdGFsaXMgUy5wLkEuMTQwMgYDVQQDDCtB\n" +
            "Y3RhbGlzIE9yZ2FuaXphdGlvbiBWYWxpZGF0ZWQgU2VydmVyIENBIEczMB4XDTIx\n" +
            "MTAwODA5MjM0OVoXDTIyMTAwODA5MjM0OVoweDELMAkGA1UEBhMCSVQxEDAOBgNV\n" +
            "BAgMB0JlcmdhbW8xGTAXBgNVBAcMEFBvbnRlIFNhbiBQaWV0cm8xFzAVBgNVBAoM\n" +
            "DkFjdGFsaXMgUy5wLkEuMSMwIQYDVQQDDBpzc2x0ZXN0LXJldm9rZWQuYWN0YWxp\n" +
            "cy5pdDCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAPPgHusiIPuBvyPF\n" +
            "5lvUnfRraGomDTTJ4FBHomWJgbyTcfjJ6WqP5p6dwcRTyXT1U/odp5bxSYGBpj31\n" +
            "1zi1tqtJGxuXauTytKUPL1pOXOD4V9JpOL9VetDZS5Lvo3bnAjGLJA/Bqr7VRmMY\n" +
            "l9LiGjIlJcSCQWCDxcHDkJA/4Vrmek6z1Pwzz/OjkBYRJ3T75qlWtTh/8ZhvnKxs\n" +
            "WAeHD/n0hLshMbqke2CuGHGC1+tAUlb8ZzIZjdVKoWL4VrQPN0NzgQF2jX6AS/ru\n" +
            "NNO3UrvwjD2Us9YUrDxxQLCw0LT/TkchhYWp675mY/e1EjoX+vnXpO3J3CMEfCv5\n" +
            "aVtXzusCAwEAAaOCA0kwggNFMAwGA1UdEwEB/wQCMAAwHwYDVR0jBBgwFoAUn4qx\n" +
            "tfGx3oL0J3y+iM3eqUOBo0swfgYIKwYBBQUHAQEEcjBwMDsGCCsGAQUFBzAChi9o\n" +
            "dHRwOi8vY2FjZXJ0LmFjdGFsaXMuaXQvY2VydHMvYWN0YWxpcy1hdXRob3ZnMzAx\n" +
            "BggrBgEFBQcwAYYlaHR0cDovL29jc3AwOS5hY3RhbGlzLml0L1ZBL0FVVEhPVi1H\n" +
            "MzAlBgNVHREEHjAcghpzc2x0ZXN0LXJldm9rZWQuYWN0YWxpcy5pdDBRBgNVHSAE\n" +
            "SjBIMDwGBiuBHwEUATAyMDAGCCsGAQUFBwIBFiRodHRwczovL3d3dy5hY3RhbGlz\n" +
            "Lml0L2FyZWEtZG93bmxvYWQwCAYGZ4EMAQICMB0GA1UdJQQWMBQGCCsGAQUFBwMC\n" +
            "BggrBgEFBQcDATBIBgNVHR8EQTA/MD2gO6A5hjdodHRwOi8vY3JsMDkuYWN0YWxp\n" +
            "cy5pdC9SZXBvc2l0b3J5L0FVVEhPVi1HMy9nZXRMYXN0Q1JMMB0GA1UdDgQWBBTe\n" +
            "jnmMDJmWf46bs3abftL18WTLPzAOBgNVHQ8BAf8EBAMCBaAwggGABgorBgEEAdZ5\n" +
            "AgQCBIIBcASCAWwBagB2AFWB1MIWkDYBSuoLm1c8U/DA5Dh4cCUIFy+jqh0HE9MM\n" +
            "AAABfF9AbyoAAAQDAEcwRQIgb4G8Pbdfmo9KKxA1AXSB6MGNWb5SzDbKK12xR6/d\n" +
            "gvQCIQDlIsOyHxCDIPGFIRgKrsKH1nHj2DQ++7J1V5g9r/JNwwB3AFGjsPX9AXmc\n" +
            "Vm24N3iPDKR6zBsny/eeiEKaDf7UiwXlAAABfF9Aby0AAAQDAEgwRgIhAO7xBWad\n" +
            "yewERj1dP5Uebb4K4AFI/1CDhVljDDJ/hejTAiEAzQH3I6FyCBfT92HeMB3AonNC\n" +
            "HaNEXSzmwksP6umHAzYAdwBGpVXrdfqRIDC1oolp9PN9ESxBdL79SbiFq/L8cP5t\n" +
            "RwAAAXxfQG70AAAEAwBIMEYCIQCCkh1gHDflb+PNRy3T7AEBvHf/ZlFKpZcMMz2N\n" +
            "4RV04QIhAJHrg7WiSAOW0qN3Xx5FCqm+GBomtByHlY8qg9F4VTEmMA0GCSqGSIb3\n" +
            "DQEBCwUAA4ICAQBfsH+q/ZIUIrIz4JsN8Ac8Rfbr0p1jqWc7WNyPKgDzxiI92T5O\n" +
            "IjJty1sshsoWct4hLmCk0nqTt2/Pvk76RUSvpneVh9lrmmnxOUeu/PxykYzOQoYq\n" +
            "gGvXQrDPc0R1Q/gt2Q8Orcow/TTNpbWjZYhlOmT8JHUCpUgfKm00xCWayWRVMyZH\n" +
            "lFFOHcM7+1cngXC7rkGESB0pmdkJ3Zh0fhYYdhjltZJOScO3wGCH5UtlvgbcZkYh\n" +
            "h1NMdp/yveucVwNajHGIzJ56KyFcxHrXlqIhV8HslyrSvFHQklETLyAJBt8uLoVi\n" +
            "W4ytCS11qHFcSv9D5btlGqqpub6XicRwM1jGRvD/odrTuRetT8Wi4qB0XeDGmaG8\n" +
            "al9Z6HEZoxTQ5+Pb0xIu5FOF7rC5p/BjqDxGlKBCFWyhEwR7T17javCLMNQGCQMc\n" +
            "LsLG8iUjPSQB/wiJ8RtruU7kEQrEoxRJjJLIfkLt9ti8C3yK7T7SCPBdoeM6CyI/\n" +
            "V5p7FDcvBXtDPukWTnPQ2DtUUGw8244QLWavFoHbvFekzlm2GWZYTPEwlaNAcwmg\n" +
            "0ZpxphiuMoouwud1Oaa1xzToPn+iyIjun8+wkB+rbaenIMSpqwWk8s3jtmconGKP\n" +
            "JCJvmsfAxAXrAy4Iizums4Z1kPN1ApfNIoPprTfu5cSza3xeOMq0txkERQ==\n" +
            "-----END CERTIFICATE-----";

    public static void main(String[] args) throws Exception {

        ValidatePathWithParams pathValidator = new ValidatePathWithParams(null);
        boolean ocspEnabled = false;

        if (args.length >= 1 && "CRL".equalsIgnoreCase(args[0])) {
            pathValidator.enableCRLCheck();
        } else {
            // OCSP check by default
            pathValidator.enableOCSPCheck();
            ocspEnabled = true;
        }

        // Validate valid
        pathValidator.validate(new String[]{VALID, INT},
                ValidatePathWithParams.Status.GOOD, null, System.out);

        if (ocspEnabled) {
            // Revoked test certificate will expire in Oct 2022
            pathValidator.setValidationDate("June 01, 2022");
        }

        // Validate Revoked
        pathValidator.validate(new String[]{REVOKED, INT},
                ValidatePathWithParams.Status.REVOKED,
                "Mon Mar 07 06:11:11 PST 2022", System.out);
    }
}
