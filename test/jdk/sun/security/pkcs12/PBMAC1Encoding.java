/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8343232
 * @summary PKCS#12 KeyStore support for RFC 9579: Use of Password-Based Message Authentication Code 1 (PBMAC1)
 */

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.util.Base64;

public class PBMAC1Encoding {
    // A.1.  Valid PKCS #12 File with SHA-256 HMAC and PRF
    static final String A1 =
        "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" +
        "3QQITk9UIFVTRUQCAQE=";

    // A.2.  Valid PKCS #12 File with SHA-256 HMAC and SHA-512 PRF
    static final String A2 =
        "MIIKigIBAzCCCgUGCSqGSIb3DQEHAaCCCfYEggnyMIIJ7jCCBGIGCSqGSIb3DQEH" +
        "BqCCBFMwggRPAgEAMIIESAYJKoZIhvcNAQcBMFcGCSqGSIb3DQEFDTBKMCkGCSqG" +
        "SIb3DQEFDDAcBAi4j6UBBY2iOgICCAAwDAYIKoZIhvcNAgkFADAdBglghkgBZQME" +
        "ASoEEFpHSS5zrk/9pkDo1JRbtE6AggPgtbMLGoFd5KLpVXMdcxLrT129L7/vCr0B" +
        "0I2tnhPPA7aFtRjjuGbwooCMQwxw9qzuCX1eH4xK2LUw6Gbd2H47WimSOWJMaiUb" +
        "wy4alIWELYufe74kXPmKPCyH92lN1hqu8s0EGhIl7nBhWbFzow1+qpIc9/lpujJo" +
        "wodSY+pNBD8oBeoU1m6DgOjgc62apL7m0nwavDUqEt7HAqtTBxKxu/3lpb1q8nbl" +
        "XLTqROax5feXErf+GQAqs24hUJIPg3O1eCMDVzH0h5pgZyRN9ZSIP0HC1i+d1lnb" +
        "JwHyrAhZv8GMdAVKaXHETbq8zTpxT3UE/LmH1gyZGOG2B21D2dvNDKa712sHOS/t" +
        "3XkFngHDLx+a9pVftt6p7Nh6jqI581tb7fyc7HBV9VUc/+xGgPgHZouaZw+I3PUz" +
        "fjHboyLQer22ndBz+l1/S2GhhZ4xLXg4l0ozkgn7DX92S/UlbmcZam1apjGwkGY/" +
        "7ktA8BarNW211mJF+Z+hci+BeDiM7eyEguLCYRdH+/UBiUuYjG1hi5Ki3+42pRZD" +
        "FZkTHGOrcG6qE2KJDsENj+RkGiylG98v7flm4iWFVAB78AlAogT38Bod40evR7Ok" +
        "c48sOIW05eCH/GLSO0MHKcttYUQNMqIDiG1TLzP1czFghhG97AxiTzYkKLx2cYfs" +
        "pgg5PE9drq1fNzBZMUmC2bSwRhGRb5PDu6meD8uqvjxoIIZQAEV53xmD63umlUH1" +
        "jhVXfcWSmhU/+vV/IWStZgQbwhF7DmH2q6S8itCkz7J7Byp5xcDiUOZ5Gpf9RJnk" +
        "DTZoOYM5iA8kte6KCwA+jnmCgstI5EbRbnsNcjNvAT3q/X776VdmnehW0VeL+6k4" +
        "z+GvQkr+D2sxPpldIb5hrb+1rcp9nOQgtpBnbXaT16Lc1HdTNe5kx4ScujXOWwfd" +
        "Iy6bR6H0QFq2SLKAAC0qw4E8h1j3WPxll9e0FXNtoRKdsRuX3jzyqDBrQ6oGskkL" +
        "wnyMtVjSX+3c9xbFc4vyJPFMPwb3Ng3syjUDrOpU5RxaMEAWt4josadWKEeyIC2F" +
        "wrS1dzFn/5wv1g7E7xWq+nLq4zdppsyYOljzNUbhOEtJ2lhme3NJ45fxnxXmrPku" +
        "gBda1lLf29inVuzuTjwtLjQwGk+usHJm9R/K0hTaSNRgepXnjY0cIgS+0gEY1/BW" +
        "k3+Y4GE2JXds2cQToe5rCSYH3QG0QTyUAGvwX6hAlhrRRgUG3vxtYSixQ3UUuwzs" +
        "eQW2SUFLl1611lJ7cQwFSPyr0sL0p81vdxWiigwjkfPtgljZ2QpmzR5rX2xiqItH" +
        "Dy4E+iVigIYwggWEBgkqhkiG9w0BBwGgggV1BIIFcTCCBW0wggVpBgsqhkiG9w0B" +
        "DAoBAqCCBTEwggUtMFcGCSqGSIb3DQEFDTBKMCkGCSqGSIb3DQEFDDAcBAhDiwsh" +
        "4wt3aAICCAAwDAYIKoZIhvcNAgkFADAdBglghkgBZQMEASoEELNFnEpJT65wsXwd" +
        "fZ1g56cEggTQRo04bP/fWfPPZrTEczq1qO1HHV86j76Sgxau2WQ9OQAG998HFtNq" +
        "NxO8R66en6QFhqpWCI73tSJD+oA29qOsT+Xt2bR2z5+K7D4QoiXuLa3gXv62VkjB" +
        "0DLCHAS7Mu+hkp5OKCpXCS7fo0OnAiQjM4EluAsiwwLrHu7z1E16UwpmlgKQnaC1" +
        "S44fV9znS9TxofRTnuCq1lupdn2qQjSydOU6inQeKLBflKRiLrJHOobaFmjWwp1U" +
        "OQAMuZrALhHyIbOFXMPYk3mmU/1UPuRGcbcV5v2Ut2UME+WYExXSCOYR3/R4UfVk" +
        "IfEzeRPFs2slJMIDS2fmMyFkEEElBckhKO9IzhQV3koeKUBdM066ufyax/uIyXPm" +
        "MiB9fAqbQQ4jkQTT80bKkBAP1Bvyg2L8BssstR5iCoZgWnfA9Uz4RI5GbRqbCz7H" +
        "iSkuOIowEqOox3IWbXty5VdWBXNjZBHpbE0CyMLSH/4QdGVw8R0DiCAC0mmaMaZq" +
        "32yrBR32E472N+2KaicvX31MwB/LkZN46c34TGanL5LJZx0DR6ITjdNgP8TlSSrp" +
        "7y2mqi7VbKp/C/28Cj5r+m++Gk6EOUpLHsZ2d2hthrr7xqoPzUAEkkyYWedHJaoQ" +
        "TkoIisZb0MGlXb9thjQ8Ee429ekfjv7CQfSDS6KTE/+mhuJ33mPz1ZcIacHjdHhE" +
        "6rbrKhjSrLbgmrGa8i7ezd89T4EONu0wkG9KW0wM2cn5Gb12PF6rxjTfzypG7a50" +
        "yc1IJ2Wrm0B7gGuYpVoCeIohr7IlxPYdeQGRO/SlzTd0xYaJVm9FzJaMNK0ZqnZo" +
        "QMEPaeq8PC3kMjpa8eAiHXk9K3DWdOWYviGVCPVYIZK6Cpwe+EwfXs+2hZgZlYzc" +
        "vpUWg60md1PD4UsyLQagaj37ubR6K4C4mzlhFx5NovV/C/KD+LgekMbjCtwEQeWy" +
        "agev2l9KUEz73/BT4TgQFM5K2qZpVamwmsOmldPpekGPiUCu5YxYg/y4jUKvAqj1" +
        "S9t4wUAScCJx8OvXUfgpmS2+mhFPBiFps0M4O3nWG91Q6mKMqbNHPUcFDn9P7cUh" +
        "s1xu3NRLyJ+QIfVfba3YBTV8A6WBYEmL9lxf1uL1WS2Bx6+Crh0keyNUPo9cRjpx" +
        "1oj/xkInoc2HQODEkvuK9DD7VrLr7sDhfmJvr1mUfJMQ5/THk7Z+E+NAuMdMtkM2" +
        "yKXxghZAbBrQkU3mIW150i7PsjlUw0o0/LJvQwJIsh6yeJDHY8mby9mIdeP3LQAF" +
        "clYKzNwmgwbdtmVAXmQxLuhmEpXfstIzkBrNJzChzb2onNSfa+r5L6XEHNHl7wCw" +
        "TuuV/JWldNuYXLfVfuv3msfSjSWkv6aRtRWIvmOv0Qba2o05LlwFMd1PzKM5uN4D" +
        "DYtsS9A6yQOXEsvUkWcLOJnCs8SkJRdXhJTxdmzeBqM1JttKwLbgGMbpjbxlg3ns" +
        "N+Z+sEFox+2ZWOglgnBHj0mCZOiAC8wqUu+sxsLT4WndaPWKVqoRQChvDaZaNOaN" +
        "qHciF9HPUcfZow+fH8TnSHneiQcDe6XcMhSaQ2MtpY8/jrgNKguZt22yH9gw/VpT" +
        "3/QOB7FBgKFIEbvUaf3nVjFIlryIheg+LeiBd2isoMNNXaBwcg2YXukxJTAjBgkq" +
        "hkiG9w0BCRUxFgQUwWO5DorvVWYF3BWUmAw0rUEajScwfDBtMEkGCSqGSIb3DQEF" +
        "DjA8MCwGCSqGSIb3DQEFDDAfBAgUr2yP+/DBrgICCAACASAwDAYIKoZIhvcNAgsF" +
        "ADAMBggqhkiG9w0CCQUABCA5zFL93jw8ItGlcbHKhqkNwbgpp6layuOuxSju4/Vd" +
        "6QQITk9UIFVTRUQCAQE=";

    // A.3.  Valid PKCS #12 File with SHA-512 HMAC and PRF
    static final String A3 =
        "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";

    // Invalid PKCS #12 File with Incorrect Iteration Count
    static final String A4 =
        "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" +
        "3QQITk9UIFVTRUQCAggA";

    // A.5. Invalid PKCS #12 File with Incorrect Salt
    static final String A5 =
        "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" +
        "3QQIb0c8OLAuMXMCAQE=";

    // A.6.  Invalid PKCS #12 File with Missing Key Length
    static final String A6 =
        "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" +
        "b0c8OLAuMXMCAggA";

    static String password = "1234";

    public static void main(String[] args) throws Exception {
        KeyStore ks;
        FileOutputStream fos;
        FileInputStream fis;

        ks = KeyStore.getInstance("PKCS12");
        ks.load(new ByteArrayInputStream(Base64.getDecoder().decode(A1)),
                password.toCharArray());

        fos = new FileOutputStream("pbmac1KeyStore.p12");
        ks.store(fos, password.toCharArray());
        fos.close();

        // read keystore we just wrote
        fis = new FileInputStream("pbmac1KeyStore.p12");
        ks.load(fis, password.toCharArray());
        fis.close();
        System.out.println("A.1 pass");

        // Unsupported: key length must be same as Hmac output length.
        try {
            ks = KeyStore.getInstance("PKCS12");
            ks.load(new ByteArrayInputStream(Base64.getDecoder().decode(A2)),
                    password.toCharArray());
        } catch (IOException e) {
            System.out.println("A.2 pass");
        }

        ks = KeyStore.getInstance("PKCS12");
        ks.load(new ByteArrayInputStream(Base64.getDecoder().decode(A3)),
                password.toCharArray());

        fos = new FileOutputStream("pbmac1KeyStore.p12");
        ks.store(fos, password.toCharArray());
        fos.close();

        // read keystore we just wrote
        fis = new FileInputStream("pbmac1KeyStore.p12");
        ks.load(fis, password.toCharArray());
        fis.close();
        System.out.println("A.3 pass");

        try {
            ks = KeyStore.getInstance("PKCS12");
            ks.load(new ByteArrayInputStream(Base64.getDecoder().decode(A4)),
                    password.toCharArray());
        } catch (IOException e) {
            System.out.println("A.4 pass");
        }

        try {
            ks = KeyStore.getInstance("PKCS12");
            ks.load(new ByteArrayInputStream(Base64.getDecoder().decode(A5)),
                    password.toCharArray());

        } catch (IOException e) {
            System.out.println("A.5 pass");
        }

        try {
            ks = KeyStore.getInstance("PKCS12");
            ks.load(new ByteArrayInputStream(Base64.getDecoder().decode(A6)),
                    password.toCharArray());
        } catch (IOException e) {
            System.out.println("A.6 pass");
        }
    }
}
