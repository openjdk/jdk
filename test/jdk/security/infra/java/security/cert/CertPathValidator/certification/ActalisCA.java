/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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

    // Owner: CN=ssltest-revoked.actalis.it, O=Actalis S.p.A., L=Ponte San Pietro, ST=Bergamo, C=IT
    // Issuer: CN=Actalis Organization Validated Server CA G3, O=Actalis S.p.A.,
    // L=Ponte San Pietro, ST=Bergamo, C=IT
    // Serial number: 320955171b78d49507508910da2c5bc4
    // Valid from: Tue Sep 27 03:40:43 PDT 2022 until: Wed Sep 27 03:40:43 PDT 2023
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n" +
            "MIIH1TCCBb2gAwIBAgIQMglVFxt41JUHUIkQ2ixbxDANBgkqhkiG9w0BAQsFADCB\n" +
            "iTELMAkGA1UEBhMCSVQxEDAOBgNVBAgMB0JlcmdhbW8xGTAXBgNVBAcMEFBvbnRl\n" +
            "IFNhbiBQaWV0cm8xFzAVBgNVBAoMDkFjdGFsaXMgUy5wLkEuMTQwMgYDVQQDDCtB\n" +
            "Y3RhbGlzIE9yZ2FuaXphdGlvbiBWYWxpZGF0ZWQgU2VydmVyIENBIEczMB4XDTIy\n" +
            "MDkyNzEwNDA0M1oXDTIzMDkyNzEwNDA0M1oweDELMAkGA1UEBhMCSVQxEDAOBgNV\n" +
            "BAgMB0JlcmdhbW8xGTAXBgNVBAcMEFBvbnRlIFNhbiBQaWV0cm8xFzAVBgNVBAoM\n" +
            "DkFjdGFsaXMgUy5wLkEuMSMwIQYDVQQDDBpzc2x0ZXN0LXJldm9rZWQuYWN0YWxp\n" +
            "cy5pdDCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAKdBnbeFtw/Ejp1U\n" +
            "gr86BQ5rqgGXWWXb7fsOhPb5On9RXTojg6oaeIV4GxHsMZhEDKQdcZ6JWAo2dbtp\n" +
            "/7ereFEDWG/YJahLHFZ/ihXG4AmfObYEhoGbKitW75fOs/aWC7Veck/sXsw7cjLW\n" +
            "GY623ybcF9DBExg3S4uLRaSkv5hXUDu/CzphUgwiEd5YNBZjcryOiS8+Y5EQ+2q+\n" +
            "g+tdRG9m5G5YxeHWgQz2HDDwLDsJhWkb8/RsUurU/I+avHPhYk13K5Ysf311gww8\n" +
            "bAsplfdJ2gdn8Is+EAEH4GJHqMybC95YDh1w5dY7dk/lIoNX4hYUIQimirIr3OW8\n" +
            "Svkj1G8CAwEAAaOCA0cwggNDMAwGA1UdEwEB/wQCMAAwHwYDVR0jBBgwFoAUn4qx\n" +
            "tfGx3oL0J3y+iM3eqUOBo0swfgYIKwYBBQUHAQEEcjBwMDsGCCsGAQUFBzAChi9o\n" +
            "dHRwOi8vY2FjZXJ0LmFjdGFsaXMuaXQvY2VydHMvYWN0YWxpcy1hdXRob3ZnMzAx\n" +
            "BggrBgEFBQcwAYYlaHR0cDovL29jc3AwOS5hY3RhbGlzLml0L1ZBL0FVVEhPVi1H\n" +
            "MzAlBgNVHREEHjAcghpzc2x0ZXN0LXJldm9rZWQuYWN0YWxpcy5pdDBRBgNVHSAE\n" +
            "SjBIMDwGBiuBHwEUATAyMDAGCCsGAQUFBwIBFiRodHRwczovL3d3dy5hY3RhbGlz\n" +
            "Lml0L2FyZWEtZG93bmxvYWQwCAYGZ4EMAQICMB0GA1UdJQQWMBQGCCsGAQUFBwMC\n" +
            "BggrBgEFBQcDATBIBgNVHR8EQTA/MD2gO6A5hjdodHRwOi8vY3JsMDkuYWN0YWxp\n" +
            "cy5pdC9SZXBvc2l0b3J5L0FVVEhPVi1HMy9nZXRMYXN0Q1JMMB0GA1UdDgQWBBS6\n" +
            "o8qJpg3ixoyA2QBayptaTfc+5DAOBgNVHQ8BAf8EBAMCBaAwggF+BgorBgEEAdZ5\n" +
            "AgQCBIIBbgSCAWoBaAB2AK33vvp8/xDIi509nB4+GGq0Zyldz7EMJMqFhjTr3IKK\n" +
            "AAABg36SGRYAAAQDAEcwRQIgDXxSCQGfcIYroxNiDJg08IX38Y9+r5CC6T4NeW14\n" +
            "FzgCIQDdEhEYsGIWpwyrnTLr4RFB5CMEq+84dByNT07UYkiVwwB2AHoyjFTYty22\n" +
            "IOo44FIe6YQWcDIThU070ivBOlejUutSAAABg36SGTUAAAQDAEcwRQIgL2ig9RrM\n" +
            "FPWESGRYGJJJYRHdcayHev66jawrf98saN8CIQD/CInlI3Vo7SBzzN/4uykjYsFZ\n" +
            "u9RypT6AYv6AHPlNdQB2AG9Tdqwx8DEZ2JkApFEV/3cVHBHZAsEAKQaNsgiaN9kT\n" +
            "AAABg36SGU0AAAQDAEcwRQIhAOCD/dOs4HjyC+GQaQRh4U+/mUwWyu+CnlHdebmD\n" +
            "hAvFAiAvBE0rbxgm8TpZLG2TaMk3dqZj7Q6FFdLlqTsvwhKa3jANBgkqhkiG9w0B\n" +
            "AQsFAAOCAgEAEnPALMVp1pySJgHhugLWAUgiD6stpDWCKfaBxPr+jf34A5wS+m5r\n" +
            "2VhYyNQpOwIQB76K2RSJQrdpg7Dg2L6EiUnbbClSTrOkZ4XX5ggBIjldDEx4ZxhI\n" +
            "zwSw4KB6+DDAVMwsCL0q0E7AAPOMaZ0RDLteusqQYIYm08TXfJPWD8LjQPt/8Uie\n" +
            "LOqm1eLUuwJc+eHFWV+Xr8Uea6SFwqNEj7qPHb2MElctET/MhSIIUKI1ObmrFwyB\n" +
            "ElKEPaUh9L0HXpnuD8IWc7tw2mdvnWJhuGG8G6JkasTGvtZ4gKIDBdTrJcuj7MCS\n" +
            "amz3ZBCY47tP1ohgImjqwg4ITYjX6UQXgj/nBVDdu+nXkEhx16uPJkTYWaun9Nio\n" +
            "8RjYIOxXmDD39QbGUElP0Epsr2wcVT9tIFYMGzUpIO51mCk3Aq1AmiQZwZZhqOIN\n" +
            "RDx7lGESPj3IgdVfJi9Ing/OUNtS46Ug9DSuDcGqdY7KnTYEUdWGsUJNtnpjd4lS\n" +
            "U6oIAeW1aKuOve6iNg1vsFAN57aJNh1ih3BOup58J9ve42bNlAYWN8wiNxM+Aeba\n" +
            "ArUSTnH/QEYCyMRD0XqIREVR9VhNODgSZbL3XedYBAW9wImi1whp+u+8aReXd7lC\n" +
            "Q3kD9KRyfZ9Kk05Glf3DsZMWvp1N2ZZWaU2Ms5U3ijUheCiBrqrs8a8=\n" +
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
                "Tue Sep 27 03:52:40 PDT 2022", System.out);
    }
}
