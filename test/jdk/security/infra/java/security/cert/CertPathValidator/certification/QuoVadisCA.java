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
 * @bug 8189131 8207059
 * @key intermittent
 * @summary Interoperability tests with QuoVadis Root CA1, CA2, and CA3 G3 CAs
 * @build ValidatePathWithParams
 * @run main/othervm -Djava.security.debug=certpath QuoVadisCA OCSP
 * @run main/othervm -Djava.security.debug=certpath QuoVadisCA CRL
 */

/*
 * Obtain TLS test artifacts for QuoVadis CAs from:
 *
 * https://www.quovadisglobal.com/download-roots-crl/
 *
 */
public class QuoVadisCA {

    public static void main(String[] args) throws Exception {

        ValidatePathWithParams pathValidator = new ValidatePathWithParams(null);

        if (args.length >= 1 && "CRL".equalsIgnoreCase(args[0])) {
            pathValidator.enableCRLCheck();
        } else {
            // OCSP check by default
            pathValidator.enableOCSPCheck();
        }

        new RootCA1G3().runTest(pathValidator);
        new RootCA2G3().runTest(pathValidator);
        new RootCA3G3().runTest(pathValidator);
    }
}

class RootCA1G3 {

    // Owner: CN=DigiCert QuoVadis TLS ICA QV Root CA 1 G3, O="DigiCert, Inc", C=US
    // Issuer: CN=QuoVadis Root CA 1 G3, O=QuoVadis Limited, C=BM
    // Serial number: 2837d5c3c2b57294becf99afe8bbdcd1bb0b20f1
    // Valid from: Wed Jan 06 12:50:51 PST 2021 until: Sat Jan 04 12:50:51 PST 2031
    private static final String INT = "-----BEGIN CERTIFICATE-----\n" +
            "MIIFgDCCA2igAwIBAgIUKDfVw8K1cpS+z5mv6Lvc0bsLIPEwDQYJKoZIhvcNAQEL\n" +
            "BQAwSDELMAkGA1UEBhMCQk0xGTAXBgNVBAoTEFF1b1ZhZGlzIExpbWl0ZWQxHjAc\n" +
            "BgNVBAMTFVF1b1ZhZGlzIFJvb3QgQ0EgMSBHMzAeFw0yMTAxMDYyMDUwNTFaFw0z\n" +
            "MTAxMDQyMDUwNTFaMFkxCzAJBgNVBAYTAlVTMRYwFAYDVQQKDA1EaWdpQ2VydCwg\n" +
            "SW5jMTIwMAYDVQQDDClEaWdpQ2VydCBRdW9WYWRpcyBUTFMgSUNBIFFWIFJvb3Qg\n" +
            "Q0EgMSBHMzCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBALMrbkb9kz/4\n" +
            "y00r7tfK+uDRomMNd5iCDVMWOvSx1VygKoBn3aavw7gq9Vfb2fIMIWkWG0GMxWbG\n" +
            "cx3wDHLWemd7yl9MxRUTGXkvH6/dNEavAQhUTL9TSf/N2e8f7q2dRDNYT7lXi/vR\n" +
            "fTBiYlY7BLNha8C3sPHsKduaJN32cjdjVFH51rFDRdhUXlo2hhOjgB6bqoqs75A3\n" +
            "Y3w88AdbMkapT63oGsCDO6N/uX2Mo9GSWREvlxHiXSMFf5qFw41vn5QIa5ADL1MP\n" +
            "CzlLmJSHXE138H1+cG5IutD7tIieKjo/t+66PGMo8xicj3yUd8rHEmBqClG4Ty3d\n" +
            "fF+bETFjLIUCAwEAAaOCAU8wggFLMBIGA1UdEwEB/wQIMAYBAf8CAQAwHwYDVR0j\n" +
            "BBgwFoAUo5fW816iEOGrRZ88F2Q87gFwnMwwdAYIKwYBBQUHAQEEaDBmMDgGCCsG\n" +
            "AQUFBzAChixodHRwOi8vdHJ1c3QucXVvdmFkaXNnbG9iYWwuY29tL3F2cmNhMWcz\n" +
            "LmNydDAqBggrBgEFBQcwAYYeaHR0cDovL29jc3AucXVvdmFkaXNnbG9iYWwuY29t\n" +
            "MBMGA1UdIAQMMAowCAYGZ4EMAQICMB0GA1UdJQQWMBQGCCsGAQUFBwMCBggrBgEF\n" +
            "BQcDATA7BgNVHR8ENDAyMDCgLqAshipodHRwOi8vY3JsLnF1b3ZhZGlzZ2xvYmFs\n" +
            "LmNvbS9xdnJjYTFnMy5jcmwwHQYDVR0OBBYEFJkRfemwrS1iWnDTPI2HIK3a2i5B\n" +
            "MA4GA1UdDwEB/wQEAwIBhjANBgkqhkiG9w0BAQsFAAOCAgEAb6tTptzzi4ssb+jA\n" +
            "n2O2vAjAo7ydlfN9v+QH0ZuGHlUc9bm8dpNpBo9yt6fWHIprGLJjVOF7HwVDQcJD\n" +
            "DhX4638Q7ETDrbTVQ4/edX6Yesq6C1G8Pza1LwStXD/jCQHFvWbPud86V0ikS4rS\n" +
            "qlmu3fzUrGZ2/Q+n5jrnRqM5IS8TXYcnzLD3azH1+aZjkwQt9HP4IuvAe/Bg9aWE\n" +
            "XeDmksbg0SqQInrWn+BVYtD+hCZNz8K0GnKKpx3Q9VxzRv+BMbO5e9iqK1Hcj5Wv\n" +
            "ZXvU45j2r5y9WML4fc8CvphzbF6ezr1e51i+yabNmfld33gRX48V5oNk16wX32ed\n" +
            "kQ83sKNomQm1dXURWK8aSDcZFAvJQ8vKTLIE9wiQmtjfSGoJzQhKLaN+egrp4L9y\n" +
            "fjpFIeK4zgAH39P4s4kaPWTdfXe2n6P5o7Xolp4R22SVkI76d8d+5Iv7Rtqd+mqI\n" +
            "y1hkwyTBbOBLtyF7yMtJQewkkZ0MWxkPvWg193RbYVRx8w1EycnxMgNwy2sJw7MR\n" +
            "XM6Mihkw910BkvlbsFUXw4uSvRkkRWSBWVrkM5hvZGtbIJkqrdnj55RSk4DLOOT/\n" +
            "LUyji/KpgD7YCi7emFA4tH6OpkNrjUJ3gdRnD4GwQj/87tYeoQWZ6uCl0MHDUCmw\n" +
            "73bpxSkjPrYbmKo9mGEAMhW1ZxY=\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=quovadis-root-ca-1-g3.chain-demos.digicert.com, O="DigiCert, Inc.",
    //        L=Lehi, ST=Utah, C=US
    // Issuer: CN=DigiCert QuoVadis TLS ICA QV Root CA 1 G3, O="DigiCert, Inc", C=US
    // Serial number: a94cc08600f5fe5d3f0659bfcfec6f0
    // Valid from: Fri Mar 04 16:00:00 PST 2022 until: Wed Apr 05 16:59:59 PDT 2023
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIG/DCCBeSgAwIBAgIQCpTMCGAPX+XT8GWb/P7G8DANBgkqhkiG9w0BAQsFADBZ\n" +
            "MQswCQYDVQQGEwJVUzEWMBQGA1UECgwNRGlnaUNlcnQsIEluYzEyMDAGA1UEAwwp\n" +
            "RGlnaUNlcnQgUXVvVmFkaXMgVExTIElDQSBRViBSb290IENBIDEgRzMwHhcNMjIw\n" +
            "MzA1MDAwMDAwWhcNMjMwNDA1MjM1OTU5WjB9MQswCQYDVQQGEwJVUzENMAsGA1UE\n" +
            "CBMEVXRhaDENMAsGA1UEBxMETGVoaTEXMBUGA1UEChMORGlnaUNlcnQsIEluYy4x\n" +
            "NzA1BgNVBAMTLnF1b3ZhZGlzLXJvb3QtY2EtMS1nMy5jaGFpbi1kZW1vcy5kaWdp\n" +
            "Y2VydC5jb20wggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQC3HrwaCagg\n" +
            "6bxmgEC+neLN/ShfNYuOMQ2Slk5q/zDUhQRpNQnh3nUwoRSWRvwGxDFsRj++LECF\n" +
            "TMdfzIu+0rlFzGqd3B5mlRsJrcycy/+ILwGNtIooUSU7pvJAVgLZ5N1SSVZoY+i3\n" +
            "bqLiMmv2/JfouT1SQB3U0tGmS+QKyBtVyKPVeuAhnLdyw90UiB7Gu9qXQpCawac8\n" +
            "pXPQLFzyEP7VJO0wDXanXvi6YPuIhh4m+j2YVCd9d2zI3y3kOrkuaUY5UCBvMG/b\n" +
            "Pc7/5pBsqf+E+7RHF24JAR2aqXzARWt2MzRiwpE/DJDfu097IUtR5aEdCRIKw/b4\n" +
            "GcHEbVaE3c8RAgMBAAGjggOaMIIDljAfBgNVHSMEGDAWgBSZEX3psK0tYlpw0zyN\n" +
            "hyCt2touQTAdBgNVHQ4EFgQUsG1/1d7ATEocqm82IRByZD/1qQIwOQYDVR0RBDIw\n" +
            "MIIucXVvdmFkaXMtcm9vdC1jYS0xLWczLmNoYWluLWRlbW9zLmRpZ2ljZXJ0LmNv\n" +
            "bTAOBgNVHQ8BAf8EBAMCBaAwHQYDVR0lBBYwFAYIKwYBBQUHAwEGCCsGAQUFBwMC\n" +
            "MIGXBgNVHR8EgY8wgYwwRKBCoECGPmh0dHA6Ly9jcmwzLmRpZ2ljZXJ0LmNvbS9E\n" +
            "aWdpQ2VydFF1b1ZhZGlzVExTSUNBUVZSb290Q0ExRzMuY3JsMESgQqBAhj5odHRw\n" +
            "Oi8vY3JsNC5kaWdpY2VydC5jb20vRGlnaUNlcnRRdW9WYWRpc1RMU0lDQVFWUm9v\n" +
            "dENBMUczLmNybDA+BgNVHSAENzA1MDMGBmeBDAECAjApMCcGCCsGAQUFBwIBFhto\n" +
            "dHRwOi8vd3d3LmRpZ2ljZXJ0LmNvbS9DUFMwgYMGCCsGAQUFBwEBBHcwdTAkBggr\n" +
            "BgEFBQcwAYYYaHR0cDovL29jc3AuZGlnaWNlcnQuY29tME0GCCsGAQUFBzAChkFo\n" +
            "dHRwOi8vY2FjZXJ0cy5kaWdpY2VydC5jb20vRGlnaUNlcnRRdW9WYWRpc1RMU0lD\n" +
            "QVFWUm9vdENBMUczLmNydDAJBgNVHRMEAjAAMIIBfQYKKwYBBAHWeQIEAgSCAW0E\n" +
            "ggFpAWcAdgCt9776fP8QyIudPZwePhhqtGcpXc+xDCTKhYY069yCigAAAX9cPNEg\n" +
            "AAAEAwBHMEUCIEcb3zz7lhKT26HkZpFPF9e7AsHY4HR3pO5LJ5+b2iDGAiEAjEHh\n" +
            "4H3Vl+j95X65uBdkODnqjlxRc6OrqCRor71nKTYAdQA1zxkbv7FsV78PrUxtQsu7\n" +
            "ticgJlHqP+Eq76gDwzvWTAAAAX9cPNEMAAAEAwBGMEQCIBbRZ9t9oUODHhZfa7n3\n" +
            "0lGGmEpnZP9dZw375SuVX6OjAiBbfpZesx7GgSNygEF+zkBAXx+AFJF5GoGiOjFX\n" +
            "0ykjDAB2ALNzdwfhhFD4Y4bWBancEQlKeS2xZwwLh9zwAw55NqWaAAABf1w80SoA\n" +
            "AAQDAEcwRQIgfSXjtjuKjFiVYwdlitFNgTTSc7uP9hyazlrCKO9GsaYCIQCKimXl\n" +
            "j4LjJ4BlG9H1J+V747tuf7ONnAzkCPsa2ymOuzANBgkqhkiG9w0BAQsFAAOCAQEA\n" +
            "b9havJS9egan+4dgMhI6gDt6rjdWRniyi7kXv7/vWJXOxR1xl2d/WYDLsfp3BbqW\n" +
            "YuKQwB5tTH1hEoNhQIyGnuE1Y1ZgtX24rSVfTCkU/3dnTZaIhaZgFHyftAum7xSI\n" +
            "Qzu7pwih+PXrGNXupsnZ+VUE7a7zHyRDajixhSp7dZS4zLoDTxeyKX0MDmo4e8Mi\n" +
            "HNYVASYcrdld90jVJaeI/V3EkJAX7/Eyo9JqzivEwGM0e0JhCLekcVSzhjGoAlbQ\n" +
            "tIzCIaeVUlWKKiNXSKr1WD4oCD3ky4Y5VekTGzyUf/0LYzV+Y7p8epc5vTWKwYx/\n" +
            "vQwJ4RsgFit+c84mSg4qug==\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=quovadis-root-ca-1-g3-revoked.chain-demos.digicert.com,
    //        O="DigiCert, Inc.", L=Lehi, ST=Utah, C=US
    // Issuer: CN=DigiCert QuoVadis TLS ICA QV Root CA 1 G3, O="DigiCert, Inc", C=US
    // Serial number: e7eff4cdd14ebed1daa7bb7e07300ed
    // Valid from: Fri Mar 04 16:00:00 PST 2022 until: Wed Apr 05 16:59:59 PDT 2023
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n" +
            "MIIHDjCCBfagAwIBAgIQDn7/TN0U6+0dqnu34HMA7TANBgkqhkiG9w0BAQsFADBZ\n" +
            "MQswCQYDVQQGEwJVUzEWMBQGA1UECgwNRGlnaUNlcnQsIEluYzEyMDAGA1UEAwwp\n" +
            "RGlnaUNlcnQgUXVvVmFkaXMgVExTIElDQSBRViBSb290IENBIDEgRzMwHhcNMjIw\n" +
            "MzA1MDAwMDAwWhcNMjMwNDA1MjM1OTU5WjCBhTELMAkGA1UEBhMCVVMxDTALBgNV\n" +
            "BAgTBFV0YWgxDTALBgNVBAcTBExlaGkxFzAVBgNVBAoTDkRpZ2lDZXJ0LCBJbmMu\n" +
            "MT8wPQYDVQQDEzZxdW92YWRpcy1yb290LWNhLTEtZzMtcmV2b2tlZC5jaGFpbi1k\n" +
            "ZW1vcy5kaWdpY2VydC5jb20wggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIB\n" +
            "AQDCQQ2S25TEDDGHa/zvFUex4mD7pUAS7g80g8mQVII2v9Cg6F2tIEbay/IDhV3D\n" +
            "NtxJcaqiMpT9oMA5jhMSOqcoq8QFzdqugtIvxQ3obrIZysxjjluB2b1T5UhlnND1\n" +
            "ShXlSWRhwkCN8qfO+VJ8wrpVH45mj+DsiSLWrY8Vw4q+gcJgoUV0Vj87m1H93JTf\n" +
            "pF68NjljUOOTTXZSzsvTRpDsnOizbVeyZoRawRP8D4UbxA8P28Q5W7a/uZSnUkfo\n" +
            "1U1QFDd/ii/PCt6TVGYCNUehb8eSrEyjAtIZ/ricIVkKxcqzQ3Tuq7HefH/KiAqD\n" +
            "GWr0NfO1JhX5ILmDZcosdsW1AgMBAAGjggOjMIIDnzAfBgNVHSMEGDAWgBSZEX3p\n" +
            "sK0tYlpw0zyNhyCt2touQTAdBgNVHQ4EFgQUK6amWfyhRxRpr+fT1tpYV14n2wgw\n" +
            "QQYDVR0RBDowOII2cXVvdmFkaXMtcm9vdC1jYS0xLWczLXJldm9rZWQuY2hhaW4t\n" +
            "ZGVtb3MuZGlnaWNlcnQuY29tMA4GA1UdDwEB/wQEAwIFoDAdBgNVHSUEFjAUBggr\n" +
            "BgEFBQcDAQYIKwYBBQUHAwIwgZcGA1UdHwSBjzCBjDBEoEKgQIY+aHR0cDovL2Ny\n" +
            "bDMuZGlnaWNlcnQuY29tL0RpZ2lDZXJ0UXVvVmFkaXNUTFNJQ0FRVlJvb3RDQTFH\n" +
            "My5jcmwwRKBCoECGPmh0dHA6Ly9jcmw0LmRpZ2ljZXJ0LmNvbS9EaWdpQ2VydFF1\n" +
            "b1ZhZGlzVExTSUNBUVZSb290Q0ExRzMuY3JsMD4GA1UdIAQ3MDUwMwYGZ4EMAQIC\n" +
            "MCkwJwYIKwYBBQUHAgEWG2h0dHA6Ly93d3cuZGlnaWNlcnQuY29tL0NQUzCBgwYI\n" +
            "KwYBBQUHAQEEdzB1MCQGCCsGAQUFBzABhhhodHRwOi8vb2NzcC5kaWdpY2VydC5j\n" +
            "b20wTQYIKwYBBQUHMAKGQWh0dHA6Ly9jYWNlcnRzLmRpZ2ljZXJ0LmNvbS9EaWdp\n" +
            "Q2VydFF1b1ZhZGlzVExTSUNBUVZSb290Q0ExRzMuY3J0MAkGA1UdEwQCMAAwggF+\n" +
            "BgorBgEEAdZ5AgQCBIIBbgSCAWoBaAB3AOg+0No+9QY1MudXKLyJa8kD08vREWvs\n" +
            "62nhd31tBr1uAAABf1xeMeAAAAQDAEgwRgIhALuEk3mDbnEEkboc95mrKMgibE0K\n" +
            "0QAWMu1gI/teH06xAiEA7dbuLv66ScQkOq0zbfnUM8ih1Bw+Wb29jQRyTEXCaxEA\n" +
            "dgA1zxkbv7FsV78PrUxtQsu7ticgJlHqP+Eq76gDwzvWTAAAAX9cXjIwAAAEAwBH\n" +
            "MEUCIBvEfG23Yewp6oXQJExXQ+Am7z4i0X5NqSz8ohAXT3NiAiEAhDjy2H2Z5CV5\n" +
            "gZ8TACTVgNyvEIH0cS4DjH6/ILknLDEAdQCzc3cH4YRQ+GOG1gWp3BEJSnktsWcM\n" +
            "C4fc8AMOeTalmgAAAX9cXjJBAAAEAwBGMEQCIGuxWoTPcFYQlVF9q/F1JbaZj/VT\n" +
            "O6Oa8ionxCC/8aqrAiAUCUoDcwphZ25ZFC+xGiP0kUiWgUwuQH7lBpTgoZp/BjAN\n" +
            "BgkqhkiG9w0BAQsFAAOCAQEAFrVjcQxq81PXEgHCf48+FOle8kUpJGxpH1n1Sp0p\n" +
            "V95wrXj47oT1Vt9WqXPrNDfDkxwAvvXrCMXjHEg2YN0FCEanVec8GciuRRRtXrOE\n" +
            "QOXAqGv5j+KG7bEvMNUFS90fesxfxVAQkr1zIT70nMAOKV1NOyQ/q8bZ+jehcRZB\n" +
            "wUKrCWAzvOw4DPytrDcQmflvQN+Bw92T3uDuoYT/oBcobpVfKpfuW/+ZxxXTIp4L\n" +
            "sixlx82SZNTo6e3LOqsgZnR6TFyRJ63sK65M+W0d55bHvleUAHRCOiGhhgqE/cby\n" +
            "z50hDzJMLnjskMSpkxMoeSeutAS2e7oIvA//7C37LrQccQ==\n" +
            "-----END CERTIFICATE-----";

    public void runTest(ValidatePathWithParams pathValidator)
            throws Exception {
        // Validate valid
        pathValidator.validate(new String[]{VALID, INT},
                ValidatePathWithParams.Status.GOOD, null, System.out);

        // Validate Revoked
        pathValidator.validate(new String[]{REVOKED, INT},
                ValidatePathWithParams.Status.REVOKED,
                "Tue Mar 08 11:22:28 PST 2022", System.out);
    }
}

class RootCA2G3 {

    // Owner: CN=DigiCert QV EV TLS ICA G1, O="DigiCert, Inc.", C=US
    // Issuer: CN=QuoVadis Root CA 2 G3, O=QuoVadis Limited, C=BM
    // Serial number: 65e9bcd53e791df22dffeb5ecc2bc7a5588d0883
    // Valid from: Mon Mar 16 12:39:42 PDT 2020 until: Thu Mar 14 12:39:42 PDT 2030
    private static final String INT = "-----BEGIN CERTIFICATE-----\n" +
            "MIIFbzCCA1egAwIBAgIUZem81T55HfIt/+tezCvHpViNCIMwDQYJKoZIhvcNAQEL\n" +
            "BQAwSDELMAkGA1UEBhMCQk0xGTAXBgNVBAoTEFF1b1ZhZGlzIExpbWl0ZWQxHjAc\n" +
            "BgNVBAMTFVF1b1ZhZGlzIFJvb3QgQ0EgMiBHMzAeFw0yMDAzMTYxOTM5NDJaFw0z\n" +
            "MDAzMTQxOTM5NDJaMEoxCzAJBgNVBAYTAlVTMRcwFQYDVQQKDA5EaWdpQ2VydCwg\n" +
            "SW5jLjEiMCAGA1UEAwwZRGlnaUNlcnQgUVYgRVYgVExTIElDQSBHMTCCASIwDQYJ\n" +
            "KoZIhvcNAQEBBQADggEPADCCAQoCggEBAMhwn6I+pGrJsnisnzP7EU5cFN9UT5XF\n" +
            "auA13F3jHeUUZmBOcMSOJEhx/e7oeVScTnmKpe7t7uey7lIIC9DWFmP8klbtLBgL\n" +
            "0jY4MPlCkVyxUIhZ73EHCPqDCX9bo+rMB6C758/tKZOPcoWRixQypPwoC4cXNOOk\n" +
            "ntqFPRxFSZoBdTDNlAmkAQJCRsXGCEC5pZ0JqzGcAA0/Pw1fB8lSPAti3trubYmd\n" +
            "aaPFAKzGK7vsexxpuSUKO0opNkFWbLdHZ8jkr86R80oo1vhURJXWNeMS74ws5nbt\n" +
            "Ll9sJTDW33MQPS0/JO3xYI7bQcW3K1sPSERa4BahqgOJvEXMk1eWRcUCAwEAAaOC\n" +
            "AU0wggFJMBIGA1UdEwEB/wQIMAYBAf8CAQAwHwYDVR0jBBgwFoAU7edvdlq/YOxJ\n" +
            "W8ald7tyFnGbxD0wOgYIKwYBBQUHAQEELjAsMCoGCCsGAQUFBzABhh5odHRwOi8v\n" +
            "b2NzcC5xdW92YWRpc2dsb2JhbC5jb20wSwYDVR0gBEQwQjAHBgVngQwBATA3Bglg\n" +
            "hkgBhv1sAgEwKjAoBggrBgEFBQcCARYcaHR0cHM6Ly93d3cuZGlnaWNlcnQuY29t\n" +
            "L0NQUzAdBgNVHSUEFjAUBggrBgEFBQcDAgYIKwYBBQUHAwEwOwYDVR0fBDQwMjAw\n" +
            "oC6gLIYqaHR0cDovL2NybC5xdW92YWRpc2dsb2JhbC5jb20vcXZyY2EyZzMuY3Js\n" +
            "MB0GA1UdDgQWBBQTL6fobnFR9uIMmEeDnn+deHk08zAOBgNVHQ8BAf8EBAMCAYYw\n" +
            "DQYJKoZIhvcNAQELBQADggIBAEoOxze3kgnR39LX8M63EjiNxx0LThZHROqYqev6\n" +
            "5ox/c5NNitk8/ODA8osdPpvnUBAlmE0+gqBvnTBRPVrJFd9bOr5BK8z6Os9/U0ed\n" +
            "c3UINkWLS05B7ChC9s6Zw1Vd/WlW08TQJ80GpvAIbEKcg8EO/DXPniHxC4cMtv1T\n" +
            "jtNeh98XiVgQXHL1FY+u/l413J8C4utKi4ZOQeCJDqvlSDzRsOi+tHsXrCJxnMWN\n" +
            "2QBgMGgdPW37zwf0EffoH0Gee3pTgg7I5SzmvBq0t5xRDfv4N0OdM/sN1mc5f3o7\n" +
            "0YCd9WXhyDCV5W2O8QIbrd42CK5k1rlM6gXwOyDmYY5CVAl1QeXEeRfDk/zNjU/1\n" +
            "+LnH/Dv88VcZhODYq+VGbyM8bpNr0v95PY3yaH4kzpWGqWAN5i9LosfcaqRPmyL4\n" +
            "PcKTQwcA9AVTjITExFua/QtGrXLPvMVxR248G9IQpJMxP3JEGkjlKCenmc29r2u1\n" +
            "KE4TeCs2xxjR1PusTfX91bBW3YAoAPDTRQKZjolegLUY44j3uKSzAdhMEbZQhovH\n" +
            "Lraqx1WjTayTuq1Vuakcia5shmgFVSNcE+NVgLEIe32oTOm/G6Kd1lcm9C4Ph1Cg\n" +
            "nfDuqohZrk76kJTk8poAY5aFCQHhVzbpSw3zooMGjjvWnkG+/DC6SZM8rKoOdKiB\n" +
            "cy+N\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=quovadis-root-ca-2-g3.chain-demos.digicert.com, O="DigiCert,
    //        Inc.", L=Lehi, ST=Utah, C=US, SERIALNUMBER=5299537-0142, OID.1.3.6.1.4
    //        1.311.60.2.1.2=Utah, OID.1.3.6.1.4.1.311.60.2.1.3=US,
    //        OID.2.5.4 .15=Private Organization
    // Issuer: CN=DigiCert QV EV TLS ICA G1, O="DigiCert, Inc.", C=US
    // Serial number: 9c5e9d5f169d3a59e64db208d3e849d
    // Valid from: Wed Feb 02 16:00:00 PST 2022 until: Mon Mar 06 15:59:59 PST 2023
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIHNDCCBhygAwIBAgIQCcXp1fFp06WeZNsgjT6EnTANBgkqhkiG9w0BAQsFADBK\n" +
            "MQswCQYDVQQGEwJVUzEXMBUGA1UECgwORGlnaUNlcnQsIEluYy4xIjAgBgNVBAMM\n" +
            "GURpZ2lDZXJ0IFFWIEVWIFRMUyBJQ0EgRzEwHhcNMjIwMjAzMDAwMDAwWhcNMjMw\n" +
            "MzA2MjM1OTU5WjCB3zEdMBsGA1UEDwwUUHJpdmF0ZSBPcmdhbml6YXRpb24xEzAR\n" +
            "BgsrBgEEAYI3PAIBAxMCVVMxFTATBgsrBgEEAYI3PAIBAhMEVXRhaDEVMBMGA1UE\n" +
            "BRMMNTI5OTUzNy0wMTQyMQswCQYDVQQGEwJVUzENMAsGA1UECBMEVXRhaDENMAsG\n" +
            "A1UEBxMETGVoaTEXMBUGA1UEChMORGlnaUNlcnQsIEluYy4xNzA1BgNVBAMTLnF1\n" +
            "b3ZhZGlzLXJvb3QtY2EtMi1nMy5jaGFpbi1kZW1vcy5kaWdpY2VydC5jb20wggEi\n" +
            "MA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDfknHK7boXh9ysZ0FLDQKEyT2x\n" +
            "Swtyecb5kkVgJU75XpXccV724mntCu5hpZ7Yt4tOmDZvbpYcqWbhIJgxGFNLyPdB\n" +
            "Fn8jgZ4N0WoD7u295HI9izEmbM0XrO2rvHUc6ZhFFyx0jhvJPf/k9QbQB4TwKZri\n" +
            "Iuf1E1Ek70DkTWAg6OrPHMe2ER3aSz2S2rNkMSopURvZuabzPovsGaz+XEZNfE4N\n" +
            "UfkBLa0DUjFCamOMZKIfkzxpH/NhQcigGnZgxiyUb6KRhu9ydpWeOvOHwPWwR/fV\n" +
            "7WT+X1DUHojoXeCk2RtIRMihDWPd+lqiUppM8IlEW/gxWbK1wP41qioiK9j5AgMB\n" +
            "AAGjggN+MIIDejAfBgNVHSMEGDAWgBQTL6fobnFR9uIMmEeDnn+deHk08zAdBgNV\n" +
            "HQ4EFgQUtAEN4g3bzwES6MoOINihiZQrt+owOQYDVR0RBDIwMIIucXVvdmFkaXMt\n" +
            "cm9vdC1jYS0yLWczLmNoYWluLWRlbW9zLmRpZ2ljZXJ0LmNvbTAOBgNVHQ8BAf8E\n" +
            "BAMCBaAwHQYDVR0lBBYwFAYIKwYBBQUHAwEGCCsGAQUFBwMCMHsGA1UdHwR0MHIw\n" +
            "N6A1oDOGMWh0dHA6Ly9jcmwzLmRpZ2ljZXJ0LmNvbS9EaWdpQ2VydFFWRVZUTFNJ\n" +
            "Q0FHMS5jcmwwN6A1oDOGMWh0dHA6Ly9jcmw0LmRpZ2ljZXJ0LmNvbS9EaWdpQ2Vy\n" +
            "dFFWRVZUTFNJQ0FHMS5jcmwwSgYDVR0gBEMwQTALBglghkgBhv1sAgEwMgYFZ4EM\n" +
            "AQEwKTAnBggrBgEFBQcCARYbaHR0cDovL3d3dy5kaWdpY2VydC5jb20vQ1BTMHYG\n" +
            "CCsGAQUFBwEBBGowaDAkBggrBgEFBQcwAYYYaHR0cDovL29jc3AuZGlnaWNlcnQu\n" +
            "Y29tMEAGCCsGAQUFBzAChjRodHRwOi8vY2FjZXJ0cy5kaWdpY2VydC5jb20vRGln\n" +
            "aUNlcnRRVkVWVExTSUNBRzEuY3J0MAwGA1UdEwEB/wQCMAAwggF9BgorBgEEAdZ5\n" +
            "AgQCBIIBbQSCAWkBZwB2AOg+0No+9QY1MudXKLyJa8kD08vREWvs62nhd31tBr1u\n" +
            "AAABfsHcGW0AAAQDAEcwRQIgSMSWvB5/8sf6CAZYojDI+t3bmcVHtIJT3T+Z3TcZ\n" +
            "MFMCIQD5Qyb6jwHOAscsPeID156bUZIw+PeB652u+Q8gTU8C5gB1ADXPGRu/sWxX\n" +
            "vw+tTG1Cy7u2JyAmUeo/4SrvqAPDO9ZMAAABfsHcGUcAAAQDAEYwRAIgL68Riq9a\n" +
            "l17hobjQopbfzvcQi4KT1+DlqO2dAeCuF80CIAy19t3bAxcJRmbXWo9J2dGc7WuE\n" +
            "r+bLfnQoerq9KB1bAHYAs3N3B+GEUPhjhtYFqdwRCUp5LbFnDAuH3PADDnk2pZoA\n" +
            "AAF+wdwZZAAABAMARzBFAiEA4vYazXAaD1BfJ8MqEmrfxeTIDQ6LZkmqfh8xEnVz\n" +
            "8VYCIF/RgfyBhOeH40wfgwpFTa+Y+t7EWg0PtjC4IaIFTKYKMA0GCSqGSIb3DQEB\n" +
            "CwUAA4IBAQC5KLlms/+5XcCIEFBpQSwT7VoRcqnrVWlhya+9ClA98LYuDUeHcHt6\n" +
            "lHvfjEEmy2s2GoKHK/JxXzftBau5LbDWlvQ6EF+22fnaVDsKIwNgYwbhJb+6zr8t\n" +
            "LOFS6Y51YSlRrDUvy94S3PE7N8D3wyKq18IhXOI1WUeR0bKHLlXtl+ZjKMIMkd/l\n" +
            "YtLnnskRCQa0P/HLwQYLUpgiNGVZJQbjrWsVzcw12mR/gza1KjR02STJRGZad7L0\n" +
            "Oz48CRhm94iaEjFcVKT3vcDUrtCKpkmhBACcdA3NNqDq10i/SLspOeDLSESkkJKF\n" +
            "w8w3YCqXjZn5JyV3sVHYNezNKtLdCxn4\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=quovadis-root-ca-2-g3-revoked.chain-demos.digicert.com,
    //           O="DigiCert, Inc.", L=Lehi, ST=Utah, C=US, SERIALNUMBER=5299537-0142,
    //           OID.2.5.4.15=Private Organization, OID.1.3.6.1.4.1.311.60.2.1.2=Utah,
    //           OID.1.3.6.1.4.1.311.60.2.1.3=US
    // Issuer: CN=DigiCert QV EV TLS ICA G1, O="DigiCert, Inc.", C=US
    // Serial number: 3f84605850df3ac98fcc15adec269f8
    // Valid from: Sun Apr 17 17:00:00 PDT 2022 until: Fri May 19 16:59:59 PDT 2023
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n" +
            "MIIHQDCCBiigAwIBAgIQA/hGBYUN86yY/MFa3sJp+DANBgkqhkiG9w0BAQsFADBK\n" +
            "MQswCQYDVQQGEwJVUzEXMBUGA1UECgwORGlnaUNlcnQsIEluYy4xIjAgBgNVBAMM\n" +
            "GURpZ2lDZXJ0IFFWIEVWIFRMUyBJQ0EgRzEwHhcNMjIwNDE4MDAwMDAwWhcNMjMw\n" +
            "NTE5MjM1OTU5WjCB5zETMBEGCysGAQQBgjc8AgEDEwJVUzEVMBMGCysGAQQBgjc8\n" +
            "AgECEwRVdGFoMR0wGwYDVQQPDBRQcml2YXRlIE9yZ2FuaXphdGlvbjEVMBMGA1UE\n" +
            "BRMMNTI5OTUzNy0wMTQyMQswCQYDVQQGEwJVUzENMAsGA1UECBMEVXRhaDENMAsG\n" +
            "A1UEBxMETGVoaTEXMBUGA1UEChMORGlnaUNlcnQsIEluYy4xPzA9BgNVBAMTNnF1\n" +
            "b3ZhZGlzLXJvb3QtY2EtMi1nMy1yZXZva2VkLmNoYWluLWRlbW9zLmRpZ2ljZXJ0\n" +
            "LmNvbTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBANfUkoe8l/AFhMH5\n" +
            "NtRR2Ztx4xVINz1celdjQE7xgjyHoQY6EMhuI+tvTpwJr9wJEFl7YBiIUFUgJZo6\n" +
            "lCLZtXI5t6rN0PhI+F03vGj5ukOkBBcsNVuKPJjud78sHL7u4w7RL3agrQIG7sff\n" +
            "bQK4qieUDPxiE8TO8mIzUKnIvYeNA8aJe4zxWf6Mn64WvnudsxYFgMDL4L0ryYKy\n" +
            "Ls53Co0OweOl4qnNSne8eIGfb6UaUBQvWbnVfRSHzf+skrF1qstWlFhUsqR07HtF\n" +
            "6BqVrAsRA8tmXisyXrMp9jTcIsG7LXVLOqxN07mAvpateExZs3WWRhfQl4Z+HpHD\n" +
            "80WbTI0CAwEAAaOCA4IwggN+MB8GA1UdIwQYMBaAFBMvp+hucVH24gyYR4Oef514\n" +
            "eTTzMB0GA1UdDgQWBBSTXYbD9dwCDxIH/aN5vIr02uLz5DBBBgNVHREEOjA4gjZx\n" +
            "dW92YWRpcy1yb290LWNhLTItZzMtcmV2b2tlZC5jaGFpbi1kZW1vcy5kaWdpY2Vy\n" +
            "dC5jb20wDgYDVR0PAQH/BAQDAgWgMB0GA1UdJQQWMBQGCCsGAQUFBwMBBggrBgEF\n" +
            "BQcDAjB7BgNVHR8EdDByMDegNaAzhjFodHRwOi8vY3JsMy5kaWdpY2VydC5jb20v\n" +
            "RGlnaUNlcnRRVkVWVExTSUNBRzEuY3JsMDegNaAzhjFodHRwOi8vY3JsNC5kaWdp\n" +
            "Y2VydC5jb20vRGlnaUNlcnRRVkVWVExTSUNBRzEuY3JsMEoGA1UdIARDMEEwCwYJ\n" +
            "YIZIAYb9bAIBMDIGBWeBDAEBMCkwJwYIKwYBBQUHAgEWG2h0dHA6Ly93d3cuZGln\n" +
            "aWNlcnQuY29tL0NQUzB2BggrBgEFBQcBAQRqMGgwJAYIKwYBBQUHMAGGGGh0dHA6\n" +
            "Ly9vY3NwLmRpZ2ljZXJ0LmNvbTBABggrBgEFBQcwAoY0aHR0cDovL2NhY2VydHMu\n" +
            "ZGlnaWNlcnQuY29tL0RpZ2lDZXJ0UVZFVlRMU0lDQUcxLmNydDAJBgNVHRMEAjAA\n" +
            "MIIBfAYKKwYBBAHWeQIEAgSCAWwEggFoAWYAdQDoPtDaPvUGNTLnVyi8iWvJA9PL\n" +
            "0RFr7Otp4Xd9bQa9bgAAAYA+bejFAAAEAwBGMEQCIFDhmaB4BXmOw2SKONPFBU8t\n" +
            "qXb7DXeG6JHGcONDqITjAiAqozEj7/1ULu6t/uzfwOSgC7xEmUsLGzQVnaOF9m3s\n" +
            "swB1ADXPGRu/sWxXvw+tTG1Cy7u2JyAmUeo/4SrvqAPDO9ZMAAABgD5t6QkAAAQD\n" +
            "AEYwRAIgfVEs7Ph+wOpoCGl4woa3aUWH1COGx1SwvHZ8lH21xfsCIBI1IpR6goya\n" +
            "iz47tT/Uz+26RnkHiAApYsdMOPyevkzhAHYAs3N3B+GEUPhjhtYFqdwRCUp5LbFn\n" +
            "DAuH3PADDnk2pZoAAAGAPm3pPgAABAMARzBFAiAKBon1PVoqJAF49jMQd2c222TK\n" +
            "sWkL5sLFqLVZj2vOugIhAODd/OUy236+9alC2U5nxl1oej9fOF4por2OZMFQfpFF\n" +
            "MA0GCSqGSIb3DQEBCwUAA4IBAQAyrJzyOiRAETfoYddTmRmbnFNuHx4YAkkdxn2d\n" +
            "BXdy4jPn0kTtDo4592KnbTdieSCWghmEmcEY1sQXdX6iqKwzmp408jfUDohl5evV\n" +
            "oZrum3P3zgLRz1qswFM5a2HteWzCWWi/n6d6nKXj6PGGVAMQfk1s6PaWhYBuiaag\n" +
            "myYss/LTPzaLGUfFzlt/HfomiD+BNuBOVa+pPrmTWhex+e02z95n6RPYCiazuZNZ\n" +
            "xiarN83pRNu/fIjVXw2jENg7+kaC1wwLqET0x6/EJa6YI3Xa7Aumb8Pp2r2UZ5Tr\n" +
            "7BUhmiRLkvw/9SI8ceXNSwuTTGK2fKHm2/CWqI0cS3zWk3dC\n" +
            "-----END CERTIFICATE-----";

    public void runTest(ValidatePathWithParams pathValidator)
            throws Exception {
        // Validate valid
        pathValidator.validate(new String[]{VALID, INT},
                ValidatePathWithParams.Status.GOOD, null, System.out);

        // Validate Revoked
        pathValidator.validate(new String[]{REVOKED, INT},
                ValidatePathWithParams.Status.REVOKED,
                "Mon Apr 18 14:07:46 PDT 2022", System.out);
    }
}

class RootCA3G3 {

    // Owner: CN=DigiCert QuoVadis TLS ICA QV Root CA 3 G3, O="DigiCert, Inc", C=US
    // Issuer: CN=QuoVadis Root CA 3 G3, O=QuoVadis Limited, C=BM
    // Serial number: 427dd33a8ff51d8152e813c7dec93ba76312a7d8
    // Valid from: Wed Jan 06 12:55:40 PST 2021 until: Sat Jan 04 12:55:40 PST 2031
    private static final String INT = "-----BEGIN CERTIFICATE-----\n" +
            "MIIFgDCCA2igAwIBAgIUQn3TOo/1HYFS6BPH3sk7p2MSp9gwDQYJKoZIhvcNAQEL\n" +
            "BQAwSDELMAkGA1UEBhMCQk0xGTAXBgNVBAoTEFF1b1ZhZGlzIExpbWl0ZWQxHjAc\n" +
            "BgNVBAMTFVF1b1ZhZGlzIFJvb3QgQ0EgMyBHMzAeFw0yMTAxMDYyMDU1NDBaFw0z\n" +
            "MTAxMDQyMDU1NDBaMFkxCzAJBgNVBAYTAlVTMRYwFAYDVQQKDA1EaWdpQ2VydCwg\n" +
            "SW5jMTIwMAYDVQQDDClEaWdpQ2VydCBRdW9WYWRpcyBUTFMgSUNBIFFWIFJvb3Qg\n" +
            "Q0EgMyBHMzCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBALxNTdqnFD+A\n" +
            "MhketYfVfVUWQKPkVEuyYj7Y2uwXBMRP4RStO4CoQih+hX/h94vRlObOIsqcNnyC\n" +
            "ElwBnLbmusaWYLYnDEWoROL8uN0pkWk0asfhhEsXTkAJ6FLHUD85WBkED4gIVWPi\n" +
            "Sp4AOwiA+/zpbwgVAgdjJTO3jjMsp4F1lBrdViYSwoPRACH1ZMjJG572oXTpZkQX\n" +
            "uWmEKLUOnik1i5cbqGLnwXiDvTAhxit7aBlj/C5IDvONWVQL34ZTYppvo8S3Hhy9\n" +
            "xX0S4HCpTpeBe3mas7VOrjsXNlEoFvejrxcQ+fB/gUf6fLUPxUhcPtm8keBPQuxc\n" +
            "qP12/+KG0WECAwEAAaOCAU8wggFLMBIGA1UdEwEB/wQIMAYBAf8CAQAwHwYDVR0j\n" +
            "BBgwFoAUxhfQvKjqAkPyGwaZXSuQILnXnOQwdAYIKwYBBQUHAQEEaDBmMDgGCCsG\n" +
            "AQUFBzAChixodHRwOi8vdHJ1c3QucXVvdmFkaXNnbG9iYWwuY29tL3F2cmNhM2cz\n" +
            "LmNydDAqBggrBgEFBQcwAYYeaHR0cDovL29jc3AucXVvdmFkaXNnbG9iYWwuY29t\n" +
            "MBMGA1UdIAQMMAowCAYGZ4EMAQICMB0GA1UdJQQWMBQGCCsGAQUFBwMCBggrBgEF\n" +
            "BQcDATA7BgNVHR8ENDAyMDCgLqAshipodHRwOi8vY3JsLnF1b3ZhZGlzZ2xvYmFs\n" +
            "LmNvbS9xdnJjYTNnMy5jcmwwHQYDVR0OBBYEFDNm+y+RBcyzYlLvzTz1fhzOpxeW\n" +
            "MA4GA1UdDwEB/wQEAwIBhjANBgkqhkiG9w0BAQsFAAOCAgEAY0ZuDgyM4I4MO9ll\n" +
            "D8qFUPQ8xtcGOuJgSRhDS2onIJ0M8yOGOYJCobIEGIgqyx94kI/n/1Xw+Wvsnhwb\n" +
            "OYOtVedx6VGDu6IuSKTVgPPhzwKP5ZA7wtmgKR8+W4E3DM1VerA9Po9ycDK9qCdl\n" +
            "K4tuF37grKEzlQKovG+kn0z+Zi0D/E1kN1Q8YmX35HHRenJWKEnAL9QROh0X9jFi\n" +
            "SlsHPrxWC3adOdAW+B+kVG0cM2nurd0Ic2YkiLKOOaSd5hbCQY/fCZwohtest+ZU\n" +
            "Ajyd+FVzSNvEFrwPzZwKfcdemvD4kew8lx5sG6BUL4GkFWnotxSr+F9Huwgj4pC+\n" +
            "cxE2841a/9r/gliuwDM/8jkt16epFAdw0fXemyM8FdHJDnB++3d8SyjOOQ8j+VHW\n" +
            "31NWx27sORa5CgRchlldXWDzIIEwbc82a1OAfGUmNAsdEHjMl1HMcZHbjCmdSdsw\n" +
            "fmyldZrj2YmvOI5ZlE9z4vzi35KyqlxWCtu9O/SJq/rBvYS0TPmm8HbhJQbeMe6p\n" +
            "vJGrxcb1muSBANn9T9wvukjiNNw32ciSDCjZ0h4N+CGxbzoZtgIAQ29IunYdnJix\n" +
            "ZiP+ED6xvwgVRBkDSgWD2W/hex/+z4fNmGQJDcri51/tZCqHHv2Y7XReuf4Fk+nP\n" +
            "l8Sd/Kpqwde/sJkoqwDcBSJygh0=\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=quovadis-root-ca-3-g3.chain-demos.digicert.com, O="DigiCert, Inc.",
    //        L=Lehi, ST=Utah, C=US
    // Issuer: CN=DigiCert QuoVadis TLS ICA QV Root CA 3 G3, O="DigiCert, Inc", C=US
    // Serial number: f27ee3fad1d754ae78d7866da0a4f6f
    // Valid from: Fri Mar 04 16:00:00 PST 2022 until: Wed Apr 05 16:59:59 PDT 2023
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIG/jCCBeagAwIBAgIQDyfuP60ddUrnjXhm2gpPbzANBgkqhkiG9w0BAQsFADBZ\n" +
            "MQswCQYDVQQGEwJVUzEWMBQGA1UECgwNRGlnaUNlcnQsIEluYzEyMDAGA1UEAwwp\n" +
            "RGlnaUNlcnQgUXVvVmFkaXMgVExTIElDQSBRViBSb290IENBIDMgRzMwHhcNMjIw\n" +
            "MzA1MDAwMDAwWhcNMjMwNDA1MjM1OTU5WjB9MQswCQYDVQQGEwJVUzENMAsGA1UE\n" +
            "CBMEVXRhaDENMAsGA1UEBxMETGVoaTEXMBUGA1UEChMORGlnaUNlcnQsIEluYy4x\n" +
            "NzA1BgNVBAMTLnF1b3ZhZGlzLXJvb3QtY2EtMy1nMy5jaGFpbi1kZW1vcy5kaWdp\n" +
            "Y2VydC5jb20wggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDOZpBBS8yo\n" +
            "ioVgFUQDCVcnkHTL4/PfaPKGK1owE0+mKz1AXmYX1rzFfp6gFqjbZeclhWCKoINE\n" +
            "OrZ2+1mGp75+nCP89NgoGzPgjYLVsM97gN2Y36/jXu8TwsZdYfBw9gxL+YApvq2r\n" +
            "NbPfxXaYfWdq8bz0RzqXRgS8BqKi1q8tKyahx5EJ3fCpozY9NPvCnipwbWXL9evF\n" +
            "Oak3c5Ip2YME4mHh8PujrznCVBte7KGLDn2KwbOUbh5SKKBL32vzTPOERWEDMbAu\n" +
            "3XqQh/cc4LTp32Lf/XkfnUOSbzNh+Te8ZjeDzI+SYNg9bleKpPxLSkBZyurs4mCD\n" +
            "92L8BXPlMaGjAgMBAAGjggOcMIIDmDAfBgNVHSMEGDAWgBQzZvsvkQXMs2JS7808\n" +
            "9X4czqcXljAdBgNVHQ4EFgQUnf71SuL2Z73DAgGKgO7UVFDBIkgwOQYDVR0RBDIw\n" +
            "MIIucXVvdmFkaXMtcm9vdC1jYS0zLWczLmNoYWluLWRlbW9zLmRpZ2ljZXJ0LmNv\n" +
            "bTAOBgNVHQ8BAf8EBAMCBaAwHQYDVR0lBBYwFAYIKwYBBQUHAwEGCCsGAQUFBwMC\n" +
            "MIGXBgNVHR8EgY8wgYwwRKBCoECGPmh0dHA6Ly9jcmwzLmRpZ2ljZXJ0LmNvbS9E\n" +
            "aWdpQ2VydFF1b1ZhZGlzVExTSUNBUVZSb290Q0EzRzMuY3JsMESgQqBAhj5odHRw\n" +
            "Oi8vY3JsNC5kaWdpY2VydC5jb20vRGlnaUNlcnRRdW9WYWRpc1RMU0lDQVFWUm9v\n" +
            "dENBM0czLmNybDA+BgNVHSAENzA1MDMGBmeBDAECAjApMCcGCCsGAQUFBwIBFhto\n" +
            "dHRwOi8vd3d3LmRpZ2ljZXJ0LmNvbS9DUFMwgYMGCCsGAQUFBwEBBHcwdTAkBggr\n" +
            "BgEFBQcwAYYYaHR0cDovL29jc3AuZGlnaWNlcnQuY29tME0GCCsGAQUFBzAChkFo\n" +
            "dHRwOi8vY2FjZXJ0cy5kaWdpY2VydC5jb20vRGlnaUNlcnRRdW9WYWRpc1RMU0lD\n" +
            "QVFWUm9vdENBM0czLmNydDAJBgNVHRMEAjAAMIIBfwYKKwYBBAHWeQIEAgSCAW8E\n" +
            "ggFrAWkAdwDoPtDaPvUGNTLnVyi8iWvJA9PL0RFr7Otp4Xd9bQa9bgAAAX9cGyUR\n" +
            "AAAEAwBIMEYCIQDjpwE/uiXodkY8Cx3ecooM7gxZp+Qi3aQSIi3SWam6YwIhAPqz\n" +
            "8AdaOw+FTZApiEiO2PXww8Y98YtivwXay8v/ZFxrAHYANc8ZG7+xbFe/D61MbULL\n" +
            "u7YnICZR6j/hKu+oA8M71kwAAAF/XBsk5gAABAMARzBFAiEA4v9FfzFKPr8hPM1O\n" +
            "jPSlboD96ufdyFBy9KmD8pFcI6ECIBY6pcURmWtsE/G2jQgC+qvueJqSycNP2qTM\n" +
            "iJ3pO/U1AHYAs3N3B+GEUPhjhtYFqdwRCUp5LbFnDAuH3PADDnk2pZoAAAF/XBsl\n" +
            "BwAABAMARzBFAiEAsHzOaXv9OIo4RvaKUEscoLpnM98C+4hc6v4Z26d41aICIC2o\n" +
            "aTrc5JsqgDhJXp7UArQPziUqDso967W2mrLa0nLdMA0GCSqGSIb3DQEBCwUAA4IB\n" +
            "AQC2CaUwlIb+uKsELGw5U2KV0q8uMp/nBIyFaW/HNOJUf8j1keaf31WWBAFfUQVY\n" +
            "pzFRUnRmNTtGxCvzyY1YhoQSwswGghz8ZCSQPWCST/Tl8kKuVFas8wSUXaEV23t4\n" +
            "G0pfIlXL2oIuJwREjzv54SK7xsQ4whco0nw8DvLt+/5us4t96u8r1EuBKkF45ngz\n" +
            "t77MTqpa0nvWUT7q9POT7xwQNui7P0j5t7prVX/fBKm5EfK1Jdi1Toj9+VxTIWYk\n" +
            "splUCXw7zxaA3nlrncAmnHxZEY8sQjpGY1OGY0udd+m5bldJNbRTA1Q+VoPVMiU6\n" +
            "osdBQGUbbWrqm1fnoFW1VvUt\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=quovadis-root-ca-3-g3-revoked.chain-demos.digicert.com,
    //        O="DigiCert, Inc.", L=Lehi, ST=Utah, C=US
    // Issuer: CN=DigiCert QuoVadis TLS ICA QV Root CA 3 G3, O="DigiCert, Inc", C=US
    // Serial number: aafa7cafda91796626f5fc8bcb38702
    // Valid from: Fri Mar 04 16:00:00 PST 2022 until: Wed Apr 05 16:59:59 PDT 2023
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n" +
            "MIIHDTCCBfWgAwIBAgIQCq+nyv2pF5Zib1/IvLOHAjANBgkqhkiG9w0BAQsFADBZ\n" +
            "MQswCQYDVQQGEwJVUzEWMBQGA1UECgwNRGlnaUNlcnQsIEluYzEyMDAGA1UEAwwp\n" +
            "RGlnaUNlcnQgUXVvVmFkaXMgVExTIElDQSBRViBSb290IENBIDMgRzMwHhcNMjIw\n" +
            "MzA1MDAwMDAwWhcNMjMwNDA1MjM1OTU5WjCBhTELMAkGA1UEBhMCVVMxDTALBgNV\n" +
            "BAgTBFV0YWgxDTALBgNVBAcTBExlaGkxFzAVBgNVBAoTDkRpZ2lDZXJ0LCBJbmMu\n" +
            "MT8wPQYDVQQDEzZxdW92YWRpcy1yb290LWNhLTMtZzMtcmV2b2tlZC5jaGFpbi1k\n" +
            "ZW1vcy5kaWdpY2VydC5jb20wggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIB\n" +
            "AQDofDJ1xHHWMhbWwU7e4cY3u2NjvE4ur/A0Y13UK53zoH8qDunV6ORAXQ+zSpev\n" +
            "kPlnIbdjYOK1v5RJn2ZRgCafj8Bc/9GnfQ1uE7P9dRkC9ZQwvb6Eh6f4RT7gaOPX\n" +
            "UXSXwtr96xdXDvtlJqWx13YQPnSGXUNNT1NH8bs2Myr9j+I5bUcUGsKsGheZoib3\n" +
            "6IFINss+ouOhZ+HP6ganS5cQVsUGk5u6BT6oH9VgwfVMjpDqmRkwc6UJmiij/Nz4\n" +
            "NOLOx2tivUjhk0eTPUaErUqYipGBSuwww6Linc/0IAIxGJ2k0J3Qz9PthJzG0P47\n" +
            "J5U5ej6FimnRS6Rrk5Ywk2HNAgMBAAGjggOiMIIDnjAfBgNVHSMEGDAWgBQzZvsv\n" +
            "kQXMs2JS78089X4czqcXljAdBgNVHQ4EFgQU9qXify+xtHlQIniZABL1pv7gcb4w\n" +
            "QQYDVR0RBDowOII2cXVvdmFkaXMtcm9vdC1jYS0zLWczLXJldm9rZWQuY2hhaW4t\n" +
            "ZGVtb3MuZGlnaWNlcnQuY29tMA4GA1UdDwEB/wQEAwIFoDAdBgNVHSUEFjAUBggr\n" +
            "BgEFBQcDAQYIKwYBBQUHAwIwgZcGA1UdHwSBjzCBjDBEoEKgQIY+aHR0cDovL2Ny\n" +
            "bDMuZGlnaWNlcnQuY29tL0RpZ2lDZXJ0UXVvVmFkaXNUTFNJQ0FRVlJvb3RDQTNH\n" +
            "My5jcmwwRKBCoECGPmh0dHA6Ly9jcmw0LmRpZ2ljZXJ0LmNvbS9EaWdpQ2VydFF1\n" +
            "b1ZhZGlzVExTSUNBUVZSb290Q0EzRzMuY3JsMD4GA1UdIAQ3MDUwMwYGZ4EMAQIC\n" +
            "MCkwJwYIKwYBBQUHAgEWG2h0dHA6Ly93d3cuZGlnaWNlcnQuY29tL0NQUzCBgwYI\n" +
            "KwYBBQUHAQEEdzB1MCQGCCsGAQUFBzABhhhodHRwOi8vb2NzcC5kaWdpY2VydC5j\n" +
            "b20wTQYIKwYBBQUHMAKGQWh0dHA6Ly9jYWNlcnRzLmRpZ2ljZXJ0LmNvbS9EaWdp\n" +
            "Q2VydFF1b1ZhZGlzVExTSUNBUVZSb290Q0EzRzMuY3J0MAkGA1UdEwQCMAAwggF9\n" +
            "BgorBgEEAdZ5AgQCBIIBbQSCAWkBZwB1AOg+0No+9QY1MudXKLyJa8kD08vREWvs\n" +
            "62nhd31tBr1uAAABf1wc0LIAAAQDAEYwRAIgdmF6UFe2jgbM3FjYRMmcNaXfpleT\n" +
            "E8hmYfmAVy5lSoUCIDPCV27IP9wpdGoxnnCMBwuekg6E4SB0lj49+o9OHHjDAHUA\n" +
            "Nc8ZG7+xbFe/D61MbULLu7YnICZR6j/hKu+oA8M71kwAAAF/XBzQ0QAABAMARjBE\n" +
            "AiBO6vYHFci7OWvqDHRlgTn+Q6zNG/LysZEOlrO4W8ZZ2gIgDY5+qjlar3esPN0b\n" +
            "JUR5vfITl7UiZoqINJSm1gZ4Nm4AdwCzc3cH4YRQ+GOG1gWp3BEJSnktsWcMC4fc\n" +
            "8AMOeTalmgAAAX9cHNDdAAAEAwBIMEYCIQCB52OPhdnYybsWzmkdSGSbgQVmS0V7\n" +
            "ZumbThJSJwpuiwIhAP+JRx+Eu3MYRp5iyLb+xlWqghMnDnF9aCfm1VuW4aDuMA0G\n" +
            "CSqGSIb3DQEBCwUAA4IBAQBO/4LljBpMGYYxBang12UIQ+FIjxAfKqqIklSa+du2\n" +
            "ea0VHqaRrdfh/aTxzb0WaU++bgQN+MeHmQdvwYSgAyU/lY7mIvDTNxFOO6IG2vfR\n" +
            "+JAUnS9iVUQ1rXHU72cxUsne5aRyLQ0W/2Zayx85O6/C9gIUJgJVRuk0dTPZ6tnq\n" +
            "FoW1S4GwqEpzTuJU8rP5IvMYoYo8jItpjzS0W90gtDvev/XBRs1ig28Ky7ZS5AtQ\n" +
            "S2Q6Ikg9YzegE9YNj2wqdZnEneoce0G1InysM/geY1BZ57G9RAUZkzWVTJRLJgbg\n" +
            "2nWSqpQJ765gg9JdsRo+zqj1kUBbUYoTSlaAJG6ucrlB\n" +
            "-----END CERTIFICATE-----";

    public void runTest(ValidatePathWithParams pathValidator)
            throws Exception {
        // Validate valid
        pathValidator.validate(new String[]{VALID, INT},
                ValidatePathWithParams.Status.GOOD, null, System.out);

        // Validate Revoked
        pathValidator.validate(new String[]{REVOKED, INT},
                ValidatePathWithParams.Status.REVOKED,
                "Tue Mar 08 11:23:06 PST 2022", System.out);
    }
}
