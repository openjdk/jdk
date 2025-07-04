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
 * @bug 8347938 8347941
 * @library /test/lib
 * @modules java.base/com.sun.crypto.provider
 *          java.base/sun.security.pkcs
 *          java.base/sun.security.provider
 *          java.base/sun.security.util
 * @summary ensure bad keys can be detected
 * @run main/othervm BadPrivateKeys
 */

import com.sun.crypto.provider.ML_KEM_Impls;
import jdk.test.lib.Asserts;
import sun.security.pkcs.NamedPKCS8Key;
import sun.security.provider.ML_DSA_Impls;

import javax.crypto.KEM;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.stream.Collectors;

public class BadPrivateKeys {

    public static void main(String[] args) throws Exception {
        badkem();
        baddsa();
    }

    static void badkem() throws Exception {
        var kf = KeyFactory.getInstance("ML-KEM");

        // The first ML-KEM-512-PrivateKey example includes the both CHOICE,
        // i.e., both seed and expandedKey are included. The seed and expanded
        // values can be checked for inconsistencies.
        Asserts.assertThrows(InvalidKeySpecException.class,
                () -> readKey(kf, BAD_KEM_1));

        // The second ML-KEM-512-PrivateKey example includes only expandedKey.
        // The expanded private key has a mutated s_0 and a valid public key hash,
        // but a pairwise consistency check would find that the public key
        // fails to match private.
        var k2 = readKey(kf, BAD_KEM_2);
        var pk2 = ML_KEM_Impls.privKeyToPubKey((NamedPKCS8Key) k2);
        var enc = KEM.getInstance("ML-KEM").newEncapsulator(pk2).encapsulate();
        var dk = KEM.getInstance("ML-KEM").newDecapsulator(k2).decapsulate(enc.encapsulation());
        Asserts.assertNotEqualsByteArray(enc.key().getEncoded(), dk.getEncoded());

        // The third ML-KEM-512-PrivateKey example includes only expandedKey.
        // The expanded private key has a mutated H(ek); both a public key
        // digest check and a pairwise consistency check should fail.
        var k3 = readKey(kf, BAD_KEM_3);
        Asserts.assertThrows(InvalidKeyException.class,
                () -> KEM.getInstance("ML-KEM").newDecapsulator(k3));

        // The fourth ML-KEM-512-PrivateKey example includes the both CHOICE,
        // i.e., both seed and expandedKey are included. There is mismatch
        // of the seed and expanded private key in only the z implicit rejection
        // secret; here the private and public vectors match and the pairwise
        // consistency check passes, but z is different.
        Asserts.assertThrows(InvalidKeySpecException.class,
                () -> readKey(kf, BAD_KEM_4));
    }

    static void baddsa() throws Exception {
        var kf = KeyFactory.getInstance("ML-DSA");

        // The first ML-DSA-PrivateKey example includes the both CHOICE, i.e.,
        // both seed and expandedKey are included. The seed and expanded values
        // can be checked for inconsistencies.
        Asserts.assertThrows(InvalidKeySpecException.class,
                () -> readKey(kf, BAD_DSA_1));

        // The second ML-DSA-PrivateKey example includes only expandedKey.
        // The public key fails to match the tr hash value in the private key.
        var k2 = readKey(kf, BAD_DSA_2);
        Asserts.assertThrows(IllegalArgumentException.class,
                () -> ML_DSA_Impls.privKeyToPubKey((NamedPKCS8Key) k2));

        // The third ML-DSA-PrivateKey example also includes only expandedKey.
        // The private s_1 and s_2 vectors imply a t vector whose private low
        // bits do not match the t_0 vector portion of the private key
        // (its high bits t_1 are the primary content of the public key).
        var k3 = readKey(kf, BAD_DSA_3);
        Asserts.assertThrows(IllegalArgumentException.class,
                () -> ML_DSA_Impls.privKeyToPubKey((NamedPKCS8Key) k3));
    }

    private static PrivateKey readKey(KeyFactory kf, String input) throws Exception {
        var pem = input.lines()
                .filter(s -> !s.contains("-----"))
                .collect(Collectors.joining());
        return kf.generatePrivate(
                new PKCS8EncodedKeySpec(Base64.getMimeDecoder().decode(pem)));
    }

    // https://datatracker.ietf.org/doc/html/draft-ietf-lamps-kyber-certificates-10#name-examples-of-bad-private-key
    static final String BAD_KEM_1 = """
            -----BEGIN PRIVATE KEY-----
            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
            P0A=
            -----END PRIVATE KEY-----""";

    static final String BAD_KEM_2 = """
            -----BEGIN PRIVATE KEY-----
            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
            JCUmJygpKissLS4vMDEyMzQ1Njc4OTo7PD0+Pw==
            -----END PRIVATE KEY-----""";

    static final String BAD_KEM_3 = """
            -----BEGIN PRIVATE KEY-----
            MIIGeAIBADALBglghkgBZQMEBAEEggZkBIIGYHBVT9Q2NE8nhbGzsbrBhLZnkAMz
            bCbxWn3oeMSCXGvgPzxKSA91t0hqrTHToAUYYj/SB6tSjdYnIUlYNa4AYsNnt0px
            uvEKrQ6KKQIHa+MTSL6xXMwJV83rtK/yJnVrvGAbZWireErLrrNHAvD4aiYgIRiy
            KyP4NVh3bHnBTbqYM3nIA+DcwxYKEXVwMOacaRl5jYHraYqaRIOpnlpcssMcmmYX
            mfPMiceQcG6gQWKQRdQqg67YiGDjlMaRh+IQXSjMFOw5NZLWfdAKpD/otOrkQUAC
            hmtccTxqjX0Wz3i4GdbxLp5adCM5CPCxXjxLqDKcXN2lXISSjjqoBj5aqWdkA/kX
            NbEQEMf1kwkTZNyGRFvIBIQKmiFyQhJGn4p7DOCsaY64bK05p/SCTZpRY6rCHuaA
            iwU8ij+ssLZ0S1Jiu8smpD9mTIcytkz8es8JlgX0HHlgYJdqxDODP+ADQ/sYKDAK
            QkdBEW5LRbsnbqgRKaDbTG5gvOYREB6MYlR0kl4CImeTCKPncI0Zcqe0I+sjKFHD
            bS7VPT7Tu3UAY3BhpdwikvocRmwHNUaDMovsLB7Sy1yZt47KCWkDjPfDTdEYck4x
            yuCGIGs0MCtSD10Xet7Vs8zgKszoCOomvMByYl/bk/F0WKX8HU2jlDgKH1fpzGYQ
            lDigdfDSgT/MShmcx22zgj8nCwBhWUGSlAQRo3/7r64sFQFlzsXGv3PFlfuSzRUx
            JgfaBwd4ZSvZlEvEi8fRpTQzi60LrWZWxdUCznhQqxWHJE7rWPQ5q14IV0pxjIqs
            PXfHmLuhVCczvnNEjyP7cMDlNTonyIMixSGEk6+7OAhkNNbWCla6iH3UmMOrJqCH
            CZOBWqakCXXyGK3KFYLWT/yGUvuzqab7wwT5GUX6Sq7yh4/XFd9wET0jefRIhvgS
            yD/ytxmmnh7HSuSxWszTrtWlPOdqewmCRxYzuXPLQKGgAV0KQk+hGkecAjAXQ20q
            KQDpk+taCgZ0AMf0qt8gH8T6MSZKY7rpXMjWXDmVgV5ZfRBDVc8pqlMzyTJRhp1b
            zb5IcST2Ari2pmwWxHYWSK12XPXYAGtRXpBafwrAdrDGLvoygVPnylcBaZ8TBfHm
            vG+QsOSbaTUSts6ZKouAFt38GmYsfj+WGcvYad13GvMIlszVkYrGy3dGbF53mZbW
            f/mqvJdQPyx7fi0ADYZFD7GAfKTKvaRlgloxx4mht6SRqzhydl0yDQtxkg+iE8lA
            k0Frg7gSTmn2XmLLUADcw3qpoP/3OXDEdy81fSQYnKb1MFVowOI3ajdipoxgXlY8
            XSCVcuD8dTLKKUcpU1VntfxBPF6HktJGRTbMgI+YrddGZPFBVm+QFqkKVBgpqYoE
            ZM5BqLtEwtT6PCwglGByjvFKGnxMm5jRIgO0zDUpFgqasteDj3/2tTrgWqMafWRr
            evpsRZMlJqPDdVYZvplMIRwqMcBbNEeDbLIVC+GCna5rBMVTXP9Ubjkrp5dBFyD5
            JPSQpaxUlfITVtVQt4KmTBaItrZVvMeEIZekNML2Vjtbfwmni8xIgjJ4NWHRb0y6
            tnVUAAUHgVcMZmBLgXrRJSKUc26LAYYaS1p0UZuLb+UUiaUHI5Llh2JscTd2V10z
            gGocjicyr5fCaA9RZmMxxOuLvAQxxPloMtrxs8RVKPuhU/bHixwZhwKUfM0zdyek
            b7U7oR3ly0GRNGhZUWy2rXJADzzyCbI2rvNaWArIfrPjD6/WaXPKin3SZ1r0H3oX
            thQzzRr4D3cIhp9mVIhJeYCxrBCgzctjagDthoGzXkKRJMqANQcluF+DperDpKPM
            FgCQPmUpNWC5szblrw1SnawaBIEZMCy3qbzBELlIUb8CEX8ZncSFqFK3Rz8JuDGm
            gx1bVMC3kNIlz2u5LZRiomzbM92lEjx6rw4moLg2Ve6ii/OoB0clAY/WuuS2Ac9h
            uqtxp6PTUZejQ+dLSicsEl1UCJZCbYW3lY07OKa6mH7DciXHtEzbEt3kU5tKsII2
            NoPwS/egnMXEHf6DChsWLgsyQzQ2LwhKFEZ3IzRLrdAA+NjFN8SPmY8FMHzr0e3g
            uBw7xZoGWhttY7Jsg/EB/2SAY7N24rtsW3RV9lWlDC/q2t4VDvoODm82WuogISIj
            JCUmJygpKissLS4vMDEyMzQ1Njc4OTo7PD0+Pw==
            -----END PRIVATE KEY-----""";

    static final String BAD_KEM_4 = """
            -----BEGIN PRIVATE KEY-----
            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
            Pj4=
            -----END PRIVATE KEY-----""";

    // https://datatracker.ietf.org/doc/html/draft-ietf-lamps-dilithium-certificates#name-example-inconsistent-privat
    static final String BAD_DSA_1 = """
            -----BEGIN PRIVATE KEY-----
            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
            -----END PRIVATE KEY-----""";

    static final String BAD_DSA_2 = """
            -----BEGIN PRIVATE KEY-----
            MIIKGAIBADALBglghkgBZQMEAxEEggoEBIIKANeytHJUquDbReeTDUqY0sl9jxOX
            0Xidr6FwJLMW6b7JOc4Pf3f421ZE3No2a/5HNL2V9DX/mmE6pUqkHCxpTAQzmgex
            +rtI9SownxGhiY+EjiMi/+Yj7IENs77jNoWFSogmnaMg1RIL/P6JoY4w9xFNg6pA
            SmRrbJlziYYNElIu4ABuI4SBkYZhmyYNEYZk1KYoIhhEgkAomBRhSKZhTEJIoZII
            wjgpUSRICKElwggxCMRxIBQJFINsGKeAhBBuycBwIrVkCLBhDAcEmBJEUYhpWQBG
            IpMgQQYuQrZMARZJFChMQahRgEYKURZRWgggAiJE3JhJ0TJR4TBl08CFkqhREqFk
            ADkiCUZiHMcM2Qht0AYmUkCFgEQwkQYsUMgJJMWEGpZtSpgsmQZtpEQyIKdkWjJu
            EbVwIJJhJBOOBIUsCkhyyKBR0wgqmSCAWCQgJAdOWRSIEKRkYMBt4LKNGxkJIDQi
            wCRBCUNxCiEgYaIBUiJSG4CAmjQAE5NN0zIpIhcKmJJpGhRRICchnMAgYqKBSBhp
            GoVNg0RpWyBBAxJCyxhGAakNDAIxg7AhWiJKyJIF2ZBpBDBqSwZK0rIBHEBAgUIy
            UjJyVKZAWhgQDDISksKAUhJiXIIoC7RsA0KNUxAMFAEO4TZSiIQkkQIKY0YmIAYp
            EcIo0CBIArNsojYJWoZIy7Rhi0ZixECCGokJEAJNJLJFIBIlJMkFiCiMycBNWUgi
            CiduwTRkTJBgW0RQgoZJQ4gEQ7KMYDCAoogthKRtjKYp0MaEQgZGiYhRAKmNAUmN
            5DgNpAaN05RxQrJsGoRhG6MoQrQoCKBxGsUx4KBMATdlJChiFCiQCRBh2UAiGzNg
            CQKS0CSBIAQISRhEoyItXIhEFJgIpEZhAZVkCzkKDJRQykBq0rIgwDgBgjCOE7kI
            kYCEFIgpwBiREjUNoCQi4gQG2cKFBCgSHMmJGAJy0kApwggS2AYqmZRxm7hoI4Qp
            GiKJFEUR3IJEUJZFDESEwLIEmqYFQ4YsRDJuiEQhIKhMmjBw47gtYyaIAyVJA0OM
            SKgJyhRyUzROEkMIG6cEWTAi2ZSA4jQigUISnDAqlDQmYQRFJCYoE0YJSjJtESgJ
            GLglYigRE0ENQbIRkIRMixISosaIycAwIgYG0hiOhIYwkERSEogx2SBxE8UoQwYO
            AzBgzKaEWCZSTIgBHvclYshf+kOs+kkhfysXLXu8FGIObZgKcaq73wxF6aIG7LFC
            P+4V3swXYBMAFJ2SI81ubG4fqOQfx8ZJOKtokF/T3NpQ2HCC59DXHRvJsrhMhVI8
            qP5srSlK34O+FbEI/3IdDMh7w906dZAYSw6EVmOpH8nhw8U6YdhnQgsE8JI1V1O8
            ZaBjaP1BKV/QmSQTLG+R9nlkwUJnSnJcNDkUxM7PWMB0vK9FWMl795EeB6ptCTjy
            7iuzwajFldY16ENC/eoB3CSyEa0vwoHPd+WREMerxUvwyG1IC5vidkcdydYDzumM
            /as+n8+3A3k1YFSepEUPp7M/uRacRLTSX7nEV/SXkc09oD6slglYE8EFEyzNpOY+
            SSKM0j2KHzeFbxQtk7kNsJ+Cr4kljGOquAR6gMA2yTV+ogRvjcY1TwxSlfNCu0F9
            PP6wsf0zYiwp4Uy72S4TY8ZevUUEt1EjKblnDjLhssZ6VOfxpV+Ln56gToyjpwXm
            KjxeY3N0r7eutt3qYSzeKPAaIC16pONHItJ90/m4mJTQGf1dTXEZ7+NyO7oQTLi7
            CYHgdN46/iANqq6tgmzEXyRNv0Ma+rNO+994JHTS/VcRj2RiFJNO2Zy6OwA+jWej
            g29vGfxBkQzlFj7jrpnrhNUU63YeY2hOpW+XkdLdSqxuYWi5SMgX91oiKssOjNwD
            zEr+j2cVfho2O3+u/58XK5iRNnfFod0IXp7kwiBSwa9YGTEWZz3NO/xfNLhV3MbH
            eIVknp5x9D1K6g9Lcsp+2gV4uhPTGmWNLQYKmmb/ae0b55l6L7HScj04+b+r4Y+O
            ezzakG5Om16ULI6uspYHDr/TZJR6lAzJeL7Wazd0nm1dzXvoxJREDiuEzs/vuYwL
            7fs8QeM1nSzXGX++cgxIqmxrZGXB7mPjVpwq3HREkTcLf3gm/gt3odGdZBAdAyuR
            gQa0LS73N0flYB/kulDyPt5SHwMagX0VKUpDci6DeHhLbbDPG6norpEdkgG5zpzD
            AZxvXCfLmNomFEtkIlp8kysw92Hnii1Zodi4PsY0Si9t1H52VwbQC/SnmmqSbDup
            HYEsjyx5erF5Zwnl0WhWd4KTUp8ChtAVw7U5lhlkKjM+nlk9bj9TU5lCCOnmozKF
            HX9lJSKpKLkX4n4tbUITff4uv6b7HGeybAJUUoaF9+vb4xWmjqotp2noqfQtPmAA
            fHEzCSaywAEtg+rU5P0e2HLM0ZciAdKwJ/NUWsLTDNeLwddA/sy8b8KgRGxuMOrF
            H1ppCYqi1EfyCFtOTkuSzMJpIdLeR4UYzQkM4meuotJ62lf9iLSXbYn7hDzcz0mn
            bKJnnmgBv6f7AxiW+1BilwS5kjk2u13ThTERIcrfsRmV5ZtzA0z2ftA6uBOGdkjQ
            JYKAh+lJqa/Ra5XXLZmx7coleqwTL/t6Bwmu1anA/wX7Dyu/KECe7XtfWAG+lkzt
            AZ4ct4UdOFHxApBnThn/sAizAcSs9kGiuxQhbh1pyr9Ste8idJaw8weZqFXRF/rT
            dEpvozUD6nmLUt3X7lQmYJ2/zT8ME7Fk1sBR9+1KEZcZpxLjiNMoQCCB/xNUtVTS
            wjev7TsVHEuo6fS964SZowZuJrvGnorwid7HFzHR3FKeqxfvc3RzTA/kdUlMg4Nr
            3TSgO5vImRRxYGG/uY7G5hw+1EOO3K8lJDxkcIa56nAYsNmooLAM7LAKveJJjWnC
            M2EBp3LL5PVxUj9RvQWILN81i4ScwUCqH68iQjoShRzg4z/UiXWklZ+lxf5BjJOQ
            gZGrbnQbd7/gLL1pjueVxGbWFWGeZEE4LG6sAYNO6atzzqgLviNceNqRvXm2+C+J
            l4XWhwDTk+Z1wiJNa3oa0hMgSVZ5ra7XAWe1CGZxOlMQnbe299gTBOzf2Dsxmx7y
            SDBrRa0p593Mhj2sVgSLXWnqF1AR92FMAKhqhjzeGHKokyh4uax+GsW9pJl7cgZP
            DNdfTIFOA03hGsuQE89+qSa05+qs4HDHuiGI760uQx4SI9Rd0FxNhAPC5FzuZBPs
            vnUn6HPkVcTmEKYYOarMC9VtJIPnjymLZqR46y9VjLr8qGvoR7rrAsWyFsjNiP6k
            3ySbCeZwogcDq6wksKkavEpWRmAUQroQvs/TCZOIAFHQf1agWpN556jmvv7j8i+q
            EGOY93BgBuQum+HvidJcJy8RqVCVxYfXE3MihN6dvTxyF7BoniHY6w/2lmg=
            -----END PRIVATE KEY-----""";

    static final String BAD_DSA_3 = """
            -----BEGIN PRIVATE KEY-----
            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
            -----END PRIVATE KEY-----""";
}
