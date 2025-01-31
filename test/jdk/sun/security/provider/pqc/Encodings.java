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
 * @summary ensure ML-KEM and ML-DSA encodings consistent with
 *      draft-ietf-lamps-kyber-certificates-07
 *      and draft-ietf-lamps-dilithium-certificates-05
 */

import jdk.test.lib.Asserts;
import jdk.test.lib.security.FixedSecureRandom;

import javax.crypto.KEM;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.spec.NamedParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class Encodings {
    public static void main(String[] args) throws Exception {

        var seed = new byte[64];
        for (var i = 0; i < seed.length; i++) {
            seed[i] = (byte) i;
        }

        KeyPairGenerator g;
        KeyPair kp;
        // Data from https://datatracker.ietf.org/doc/html/draft-ietf-lamps-dilithium-certificates-06
        g = KeyPairGenerator.getInstance("ML-DSA");
        g.initialize(NamedParameterSpec.ML_DSA_44, new FixedSecureRandom(seed));
        kp = g.generateKeyPair();
        var dsa44 = kp.getPublic();
        testKeys("""
                MDICAQAwCwYJYIZIAWUDBAMRBCAAAQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRob
                HB0eHw==
                """, """
                MIIFMjALBglghkgBZQMEAxEDggUhANeytHJUquDbReeTDUqY0sl9jxOX0Xidr6Fw
                JLMW6b7JT8mUbULxm3mnQTu6oz5xSctC7VEVaTrAQfrLmIretf4OHYYxGEmVtZLD
                l9IpTi4U+QqkFLo4JomaxD9MzKy8JumoMrlRGNXLQzy++WYLABOOCBf2HnYsonTD
                atVU6yKqwRYuSrAay6HjjE79j4C2WzM9D3LlXf5xzpweu5iJ58VhBsD9c4A6Kuz+
                r97XqjyyztpU0SvYzTanjPl1lDtHq9JeiArEUuV0LtHo0agq+oblkMdYwVrk0oQN
                kryhpQkPQElll/yn2LlRPxob2m6VCqqY3kZ1B9Sk9aTwWZIWWCw1cvYu2okFqzWB
                ZwxKAnd6M+DKcpX9j0/20aCjp2g9ZfX19/xg2gI+gmxfkhRMAvfRuhB1mHVT6pNn
                /NdtmQt/qZzUWv24g21D5Fn1GH3wWEeXCaAepoNZNfpwRgmQzT3BukAbqUurHd5B
                rGerMxncrKBgSNTE7vJ+4TqcF9BTj0MPLWQtwkFWYN54h32NirxyUjl4wELkKF9D
                GYRsRBJiQpdoRMEOVWuiFbWnGeWdDGsqltOYWQcf3MLN51JKe+2uVOhbMY6FTo/i
                svPt+slxkSgnCq/R5QRMOk/a/Z/zH5B4S46ORZYUSg2vWGUR09mWK56pWvGXtOX8
                YPKx7RXeOlvvX4m9x52RBR2bKBbnT6VFMe/cHL501EiFf0drzVjyHAtlOzt2pOB2
                plWaMCcYVVzGP3SFmqurkl8COGHKjND3utsocfZ9VTJtdFETWtRfShumkRj7ssij
                DuyTku8/l3Bmya3VxxDMZHsVFNIX2VjHAXw+kP0gwE5nS5BIbpNwoxoAHTL0c5ee
                SQZ0nn5Hf6C3RQj4pfI3gxK4PCW9OIygsP/3R4uvQrcWZ+2qyXxGsSlkPlhuWwVa
                DCEZRtTzbmdb7Vhg+gQqMV2YJhZNapI3w1pfv0lUkKW9TfJIuVxKrneEtgVnMWas
                QkW1tLCCoJ6TI+YvIHjFt2eDRG3v1zatOjcC1JsImESQCmGDM5e8RBmzDXqXoLOH
                wZEUdMTUG1PjKpd6y28Op122W7OeWecB52lX3vby1EVZwxp3EitSBOO1whnxaIsU
                7QvAuAGz5ugtzUPpwOn0F0TNmBW9G8iCDYuxI/BPrNGxtoXdWisbjbvz7ZM2cPCV
                oYC08ZLQixC4+rvfzCskUY4y7qCl4MkEyoRHgAg/OwzS0Li2r2e8NVuUlAJdx7Cn
                j6gOOi2/61EyiFHWB4GY6Uk2Ua54fsAlH5Irow6fUd9iptcnhM890gU5MXbfoySl
                Er2Ulwo23TSlFKhnkfDrNvAUWwmrZGUbSgMTsplhGiocSIkWJ1mHaKMRQGC6RENI
                bfUVIqHOiLMJhcIW+ObtF43VZ7MEoNTK+6iCooNC8XqaomrljbYwCD0sNY/fVmw/
                XWKkKFZ7yeqM6VyqDzVHSwv6jzOaJQq0388gg76O77wQVeGP4VNw7ssmBWbYP/Br
                IRquxDyim1TM0A+IFaJGXvC0ZRXMfkHzEk8J7/9zkwmrWLKaFFmgC85QOOk4yWeP
                cusOTuX9quZtn4Vz/Jf8QrSVn0v4th14Qz6GsDNdbpGRxNi/SHs5BcEIz9asJLDO
                t9y3z1H4TQ7Wh7lerrHFM8BvDZcCPZKnCCWDe1m6bLfU5WsKh8IDhiro8xW6WSXo
                7e+meTaaIgJ2YVHxapZfn4Hs52zAcLVYaeTbl4TPBcgwsyQsgxI=
                """, kp);
        testCert("""
                -----BEGIN CERTIFICATE-----
                MIIPlDCCBgqgAwIBAgIUFZ/+byL9XMQsUk32/V4o0N44804wCwYJYIZIAWUDBAMR
                MCIxDTALBgNVBAoTBElFVEYxETAPBgNVBAMTCExBTVBTIFdHMB4XDTIwMDIwMzA0
                MzIxMFoXDTQwMDEyOTA0MzIxMFowIjENMAsGA1UEChMESUVURjERMA8GA1UEAxMI
                TEFNUFMgV0cwggUyMAsGCWCGSAFlAwQDEQOCBSEA17K0clSq4NtF55MNSpjSyX2P
                E5fReJ2voXAksxbpvslPyZRtQvGbeadBO7qjPnFJy0LtURVpOsBB+suYit61/g4d
                hjEYSZW1ksOX0ilOLhT5CqQUujgmiZrEP0zMrLwm6agyuVEY1ctDPL75ZgsAE44I
                F/YediyidMNq1VTrIqrBFi5KsBrLoeOMTv2PgLZbMz0PcuVd/nHOnB67mInnxWEG
                wP1zgDoq7P6v3teqPLLO2lTRK9jNNqeM+XWUO0er0l6ICsRS5XQu0ejRqCr6huWQ
                x1jBWuTShA2SvKGlCQ9ASWWX/KfYuVE/GhvabpUKqpjeRnUH1KT1pPBZkhZYLDVy
                9i7aiQWrNYFnDEoCd3oz4Mpylf2PT/bRoKOnaD1l9fX3/GDaAj6CbF+SFEwC99G6
                EHWYdVPqk2f8122ZC3+pnNRa/biDbUPkWfUYffBYR5cJoB6mg1k1+nBGCZDNPcG6
                QBupS6sd3kGsZ6szGdysoGBI1MTu8n7hOpwX0FOPQw8tZC3CQVZg3niHfY2KvHJS
                OXjAQuQoX0MZhGxEEmJCl2hEwQ5Va6IVtacZ5Z0MayqW05hZBx/cws3nUkp77a5U
                6FsxjoVOj+Ky8+36yXGRKCcKr9HlBEw6T9r9n/MfkHhLjo5FlhRKDa9YZRHT2ZYr
                nqla8Ze05fxg8rHtFd46W+9fib3HnZEFHZsoFudPpUUx79wcvnTUSIV/R2vNWPIc
                C2U7O3ak4HamVZowJxhVXMY/dIWaq6uSXwI4YcqM0Pe62yhx9n1VMm10URNa1F9K
                G6aRGPuyyKMO7JOS7z+XcGbJrdXHEMxkexUU0hfZWMcBfD6Q/SDATmdLkEhuk3Cj
                GgAdMvRzl55JBnSefkd/oLdFCPil8jeDErg8Jb04jKCw//dHi69CtxZn7arJfEax
                KWQ+WG5bBVoMIRlG1PNuZ1vtWGD6BCoxXZgmFk1qkjfDWl+/SVSQpb1N8ki5XEqu
                d4S2BWcxZqxCRbW0sIKgnpMj5i8geMW3Z4NEbe/XNq06NwLUmwiYRJAKYYMzl7xE
                GbMNepegs4fBkRR0xNQbU+Mql3rLbw6nXbZbs55Z5wHnaVfe9vLURVnDGncSK1IE
                47XCGfFoixTtC8C4AbPm6C3NQ+nA6fQXRM2YFb0byIINi7Ej8E+s0bG2hd1aKxuN
                u/PtkzZw8JWhgLTxktCLELj6u9/MKyRRjjLuoKXgyQTKhEeACD87DNLQuLavZ7w1
                W5SUAl3HsKePqA46Lb/rUTKIUdYHgZjpSTZRrnh+wCUfkiujDp9R32Km1yeEzz3S
                BTkxdt+jJKUSvZSXCjbdNKUUqGeR8Os28BRbCatkZRtKAxOymWEaKhxIiRYnWYdo
                oxFAYLpEQ0ht9RUioc6IswmFwhb45u0XjdVnswSg1Mr7qIKig0LxepqiauWNtjAI
                PSw1j99WbD9dYqQoVnvJ6ozpXKoPNUdLC/qPM5olCrTfzyCDvo7vvBBV4Y/hU3Du
                yyYFZtg/8GshGq7EPKKbVMzQD4gVokZe8LRlFcx+QfMSTwnv/3OTCatYspoUWaAL
                zlA46TjJZ49y6w5O5f2q5m2fhXP8l/xCtJWfS/i2HXhDPoawM11ukZHE2L9IezkF
                wQjP1qwksM633LfPUfhNDtaHuV6uscUzwG8NlwI9kqcIJYN7Wbpst9TlawqHwgOG
                KujzFbpZJejt76Z5NpoiAnZhUfFqll+fgeznbMBwtVhp5NuXhM8FyDCzJCyDEqNC
                MEAwDgYDVR0PAQH/BAQDAgEGMA8GA1UdEwEB/wQFMAMBAf8wHQYDVR0OBBYEFDKa
                B7H6u0j1KjCfEaGJj4SOIyL/MAsGCWCGSAFlAwQDEQOCCXUA/LEt78ExnP4sas0M
                wv/5uOJwUaNNpxfdLJ4BvQt2ocbrYG/4Z4K75x7KbP9w2lzUnkTeihk1hrPTK+ok
                QfG/KnOADj+SKVyGRvQecSunv+Ze77Um/UJRY+IrMsbwKhtG9hoX+sPEbQZwF4YP
                Kgb6HBontM0zMs7eGWWgca0WBRgZyK2LwfZhRO3lR5xxutrN/nF9wKta2mRg1J8j
                GuwZ7H6E3lol0NJD2TFc5CcO2EA7NV+hVb4/8W9ILm71WIwiLApn0YKI7RjG81Io
                zPr3TjgKTvRTkxdjrVjKmelj8R47u/UMe1Ga7uiSQoIudYQziXPpexmvBKSUywF7
                yU+0cMVTIuqN6iTIbeTx0rzWMwMo4BTF33iroteo+/fhE0x0vkIPynPqW0IXluD4
                awiwZkZNyML+8GuHCPzcSGkKG63YB/jmiPWQppUs4PKWbjX+Wsr2pbvsUT0h2b2x
                LB0K/7jLqEcWzDZvEYCshKNiQ6hhXhFyxDquGmLzrYtN5zdJqZSRU2hxfYutem6i
                WvPL/cisDHzIY3ATkdABLwLq2FUMw6OGbLx2NllYRRV1vkgvHIpOb6PiUtk1yh7P
                yAYDSE1UxjLFeYmjlCABYWElShY0kNzM2ktlkJszcJSY7e4RPlnldSeSGJ6VTTWy
                y6XEp1f/0xqYDnJ7HdBAkNE2t+V4+iNMPhap3+MDtSNt+NPNLrH27j+7SdcCtTKL
                KZdk8VcTB/IIj1eJnNwtLmf4w3Fs5tvOYxbwpEpSgOFhahYD17++K6d7u7mykFd5
                gZN7ydA4YRB/fFOJS8msLjm5ta8TYWDuU1t9RLFBd7S6qNSOg/GDNJ5CyyUAv/fD
                2+gtfiuVWtV7IoUNymNNK8Wrht2f5LlvWrGJQAhKm/JcUjwwAdENoZgFtUCVHW9W
                UuWt3li4Ta69qXKVn1EdzNuPAdq5AbZyQmEl5GHyT+uv5VV9edGK6G5dASwATca0
                NOWL/kNWb6T4xkpvBgj5yI2+SvmfHZyiUo+s8hj3Ikt+U+m6zLKp4xB1x37CFdPy
                J/7+yOcmelsZfNjr9Dps0Nxnijvu+fvhI0KkoVvzHG9yR6h7YXWn/VOezt8XL4vE
                BcQcuCCj+Bz55rclX1gCGJED6ZhiQO5xvHDgptMzbbYkmuK6PZJzCMzWtFcYrrOi
                jNHLBhtxnjgG71PygYGmoKRVG0fLS4MAcXLAl906fRDl9seI92Y3/JgCI5VgL/A3
                3hcFv/fbYo2wBycYPYmpvZqCI4594sas92nZwyKlqlcIeYVhlu1gbX9HKUZQkH8x
                8J6h7l208b3cA59RcB7BYHlOriftCzyvq1JPcORSEvsk3vHX7iZA8DmKU73rpGQ8
                iAS+4bbEmCAfKEkKss2ub5uFkfip+x3C34/btT/msKOr/t0sTTwtSI4hpmln5OJJ
                P0kwDwd93vJO9jUiztF8f6a8etCg/NqA9sdRz+7BGPviwAZwDOa+jZ0VvsoJkSax
                ir5Z81E6rR949ZzKery208QzJD8emoXHwkWngyrZzqPguYvPB1fNDNLjdkZl3AnR
                DL/0ahygOIRrEyrEdHLDPPSzoZYg7s7iW40E1Fs61/EMNeGDxWWmcXwSeRsJv1ut
                pKxkjnofHFkc9i9RJhhYx5JPBn9JNBUG9obrwLnFTF+DUdEJwRbyMwLEWHzZobU8
                pvYXusxLgcd+0gn0s+siQNJAAlRniKsi0YutSR27k8nQyL0s8Jgl7bybSh/KbNKG
                T/643zfGrBMfOtGo5z28CsXszpDlxhEipYS8iaoidwnjURrndCQgR7ukcYv105ul
                K1v8V3S5msyWb4UTndzK5yK6owqRfstmUA6S6HczJ+qkowDpb2t3nMGqQ5+BTzww
                pozM/bnTVSmLFMGNic+7NltWzRySbxGzc3dxSHHfly4kSocmr53vEewK2DpixrM3
                LJMYZYivUREMaHd4BCoUzjbK9aFs3EXcm6/Q8tbFzd4k9qDahopVccqiTmaJ4HGb
                szeTqpm5iGJK9okvLy5w4j83oNhHPd/J6vw8ZqtfYYbME4GYXVtFRb07GpnPQa6E
                aErkAgtVTKptOQyJaO09YaLEbHnAgYFhZ1wQeN0Negn1YqkBC4FBe1yBMdMOre7l
                xV0FhkpMaO2wU0gPl9IrCe9m8aLRT4zcihIdJLVARPsudeLv6Oim3ao259gcum0y
                P1KD1dxwUC/j6zAtz2Qjt2Q11dLfxqKPA67Go2Dng+/uaUvbZI0lsPrx2kX0ADsT
                Nf5KoipXLssLwyooz3Ga0dcwjpTv0NE43wT8wx/OKuAC3cHAS/s+7Pk+xkypbFXI
                ba8Z6Au7Dq4r03k0fPpgL3KHqpDBWNuxujH9+RN7oGGAjppcGfq4I5tf7i5mNjZQ
                W+SBG7pgsVWibDidCbf7CfWwY6oWi718wtShOYFALIzsYAqeXqdi7raMQGl1T43A
                WiyjbXxh0adG3sqmwVhjtvesFSwZ9v0fMG0CMDQBl3fP6FXmMMaV1O0GWb16zxVK
                4QtIFRQfQGkhXWNAvBPm4F2xb6F2uJUWHnOs9g8Wuhfk3pBtjtfFWJwkaOAOsvp2
                d/1UtsYop10zwW1EMyYxiUZ16TwvSb3Om4q0mGxHQEY1VkCMouBY680bWxMfw6C4
                rRv98itFqOAHP1Mc1tKCCtUOI/a1MDQrnI0oLjsxxTnOX2uUzrx2DnuWhPEx3/w1
                +BxKXZFcDHamuWl6nbPWwrOxbNorC6arGMQSy6h40f6dfixlsCpEh+lBjucRG74S
                TJl8sM82z6akqE8JDOf3yU/sItuzOLfvNXICbkoMG0EBxMPa6TaxPNtco4UiKOuY
                nkzyIOnH5LGNOQNQCExkxHFRyoZr8TMBEoIcICnZruw1r8gYtB+kFgivIsqKptXC
                tAMhQF/vdss4YqwYcCV5bDVs90+IMlSalrfuecV0pdwUzvXn6QHVm+4SkKlMEwuY
                wyQiiwPav40S9rIO5tE5gR/BBndPfo02pGvxXR9/vZjhRVRkJr3ZaC+lVHSqtsjc
                bVfTh/nK6lkLYJ/AwcAK/3d4SKSIjHmTucj2KIDws4i3QSQ1qh1AABpugNV4zJgm
                J88qqA7oLmNFkyILQW0g0COb500DDBcjPkZkb3p7i5yjrbzEx/L4BQkKGhstdnmO
                nsLY8P4FBxAZKTJFRlSGipeipr3HGicsMTpNZ3eKqbK4wsTR3fAAAAAAAAAAAAAA
                AAAAABMhMUI=
                -----END CERTIFICATE-----""", kp.getPublic(), dsa44);

        g.initialize(NamedParameterSpec.ML_DSA_65, new FixedSecureRandom(seed));
        kp = g.generateKeyPair();
        var dsa65 = kp.getPublic();
        testKeys("""
                MDICAQAwCwYJYIZIAWUDBAMSBCAAAQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRob
                HB0eHw==
                """, """
                MIIHsjALBglghkgBZQMEAxIDggehAEhoPZGXjjHrPd24sEc0gtK4il9iWUn9j1il
                YeaWvUwn0Fs427Lt8B5mTv2Bvh6ok2iM5oqi1RxZWPi7xutOie5n0sAyCVTVchLK
                xyKf8dbq8DkovVFRH42I2EdzbH3icw1ZeOVBBxMWCXiGdxG/VTmgv8TDUMK+Vyuv
                DuLi+xbM/qCAKNmaxJrrt1k33c4RHNq2L/886ouiIz0eVvvFxaHnJt5j+t0q8Bax
                GRd/o9lxotkncXP85VtndFrwt8IdWX2+uT5qMvNBxJpai+noJQiNHyqkUVXWyK4V
                Nn5OsAO4/feFEHGUlzn5//CQI+r0UQTSqEpFkG7tRnGkTcKNJ5h7tV32np6FYfYa
                gKcmmVA4Zf7Zt+5yqOF6GcQIFE9LKa/vcDHDpthXFhC0LJ9CEkWojxl+FoErAxFZ
                tluWh+Wz6TTFIlrpinm6c9Kzmdc1EO/60Z5TuEUPC6j84QEv2Y0mCnSqqhP64kmg
                BrHDT1uguILyY3giL7NvIoPCQ/D/618btBSgpw1V49QKVrbLyIrh8Dt7KILZje6i
                jhRcne39jq8c7y7ZSosFD4lk9G0eoNDCpD4N2mGCrb9PbtF1tnQiV4Wb8i86QX7P
                H52JMXteU51YevFrnhMT4EUU/6ZLqLP/K4Mh+IEcs/sCLI9kTnCkuAovv+5gSrtz
                eQkeqObFx038AoNma0DAeThwAoIEoTa/XalWjreY00kDi9sMEeA0ReeEfLUGnHXP
                KKxgHHeZ2VghDdvLIm5Rr++fHeR7Bzhz1tP5dFa+3ghQgudKKYss1I9LMJMVXzZs
                j6YBxq+FjfoywISRsqKYh/kDNZSaXW7apnmIKjqV1r9tlwoiH0udPYy/OEr4GqyV
                4rMpTgR4msg3J6XcBFWflq9B2KBTUW/u7rxSdG62qygZ4JEIcQ2DXwEfpjBlhyrT
                NNXN/7KyMQUH6S/Jk64xfal/TzCc2vD2ftmdkCFVdgg4SflTskbX/ts/22dnmFCl
                rUBOZBR/t89Pau3dBa+0uDSWjR/ogBSWDc5dlCI2Um4SpHjWnl++aXAxCzCMBoRQ
                GM/HsqtDChOmsax7sCzMuz2RGsLxEGhhP74Cm/3OAs9c04lQ7XLIOUTt+8dWFa+H
                +GTAUfPFVFbFQShjpAwG0dq1Yr3/BXG408ORe70wCIC7pemYI5uV+pG31kFtTzmL
                OtvNMJg+01krTZ731CNv0A9Q2YqlOiNaxBcnIPd9lhcmcpgM/o/3pacCeD7cK6Mb
                IlkBWhEvx/RoqcL5RkA5AC0w72eLTLeYvBFiFr96mnwYugO3tY/QdRXTEVBJ02FL
                56B+dEMAdQ3x0sWHUziQWer8PXhczdMcB2SL7cA6XDuK1G0GTVnBPVc3Ryn8TilT
                YuKlGRIEUwQovBUir6KP9f4WVeMEylvIwnrQ4MajndTfKJVsFLOMyTaCzv5AK71e
                gtKcRk5E6103tI/FaN/gzG6OFrrqBeUTVZDxkpTnPoNnsCFtu4FQMLneVZE/CAOc
                QjUcWeVRXdWvjgiaFeYl6Pbe5jk4bEZJfXomMoh3TeWBp96WKbQbRCQUH5ePuDMS
                CO/ew8bg3jm8VwY/Pc1sRwNzwIiR6inLx8xtZIO4iJCDrOhqp7UbHCz+birRjZfO
                NvvFbqQvrpfmp6wRSGRHjDZt8eux57EakJhQT9WXW98fSdxwACtjwXOanSY/utQH
                P2qfbCuK9LTDMqEDoM/6Xe6y0GLKPCFf02ACa+fFFk9KRCTvdJSIBNZvRkh3Msgg
                LHlUeGR7TqcdYnwIYCTMo1SkHwh3s48Zs3dK0glcjaU7Bp4hx2ri0gB+FnGe1ACA
                0zT32lLp9aWZBDnK8IOpW4M/Aq0QoIwabQ8mDAByhb1KL0dwOlrvRlKH0lOxisIl
                FDFiEP9WaBSxD4eik9bxmdPDlZmQ0MEmi09Q1fn877vyN70MKLgBgtZll0HxTxC/
                uyG7oSq2IKojlvVsBoa06pAXmQIkIWsv6K12xKkUju+ahqNjWmqne8Hc+2+6Wad9
                /am3Uw3AyoZIyNlzc44Burjwi0kF6EqkZBvWAkEM2XUgJl8vIx8rNeFesvoE0r2U
                1ad6uvHg4WEBCpkAh/W0bqmIsrwFEv2g+pI9rdbEXFMB0JSDZzJltasuEPS6Ug9r
                utVkpcPV4nvbCA99IOEylqMYGVTDnGSclD6+F99cH3quCo/hJsR3WFpdTWSKDQCL
                avXozTG+aakpbU8/0l7YbyIeS5P2X1kplnUzYkuSNXUMMHB1ULWFNtEJpxMcWlu+
                SlcVVnwSU0rsdmB2Huu5+uKJHHdFibgOVmrVV93vc2cZa3In6phw7wnd/seda5MZ
                poebUgXXa/erpazzOvtZ0X/FTmg4PWvloI6bZtpT3N4Ai7KUuFgr0TLNzEmVn9vC
                HlJyGIDIrQNSx58DpDu9hMTN/cbFKQBeHnzZo0mnFoo1Vpul3qgYlo1akUZr1uZO
                IL9iQXGYr8ToHCjdd+1AKCMjmLUvvehryE9HW5AWcQziqrwRoGtNuskB7BbPNlyj
                8tU4E5SKaToPk+ecRspdWm3KPSjKUK0YvRP8pVBZ3ZsYX3n5xHGWpOgbIQS8RgoF
                HgLy6ERP
                """, kp);

        g.initialize(NamedParameterSpec.ML_DSA_87, new FixedSecureRandom(seed));
        kp = g.generateKeyPair();
        var dsa87 = kp.getPublic();
        testKeys("""
                MDICAQAwCwYJYIZIAWUDBAMTBCAAAQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRob
                HB0eHw==
                """, """
                MIIKMjALBglghkgBZQMEAxMDggohAJeSvOwvJDBoaoL8zzwvX/Zl53HXq0G5AljP
                p+kOyXEkpzsyO5uiGrZNdnxDP1pSHv/hj4bkahiJUsRGfgSLcp5/xNEV5+SNoYlt
                X+EZsQ3N3vYssweVQHS0IzblKDbeYdqUH4036misgQb6vhkHBnmvYAhTcSD3B5O4
                6pzA5ue3tMmlx0IcYPJEUboekz2xou4Wx5VZ8hs9G4MFhQqkKvuxPx9NW59INfnY
                ffzrFi0O9Kf9xMuhdDzRyHu0ln2hbMh2S2Vp347lvcv/6aTgV0jm/fIlr55O63dz
                ti6Phfm1a1SJRVUYRPvYmAakrDab7S0lYQD2iKatXgpwmCbcREnpHiPFUG5kI2Hv
                WjE3EvebxLMYaGHKhaS6sX5/lD0bijM6o6584WtEDWAY+eBNr1clx/GpP60aWie2
                eJW9JJqpFoXeIK8yyLfiaMf5aHfQyFABE1pPCo8bgmT6br5aNJ2K7K0aFimczy/Z
                x7hbrOLO06oSdrph7njtflyltnzdRYqTVAMOaru6v1agojFv7J26g7UdQv0xZ/Hg
                +QhV1cZlCbIQJl3B5U7ES0O6fPmu8Ri0TYCRLOdRZqZlHhFs6+SSKacGLAmTH3Gr
                0ik/dvfvwyFbqXgAA35Y5HC9u7Q8GwQ56vecVNk7RKrJ7+n74VGHTPsqZMvuKMxM
                D+d3Xl2HDxwC5bLjxQBMmV8kybd5y3U6J30Ocf1CXra8LKVs4SnbUfcHQPMeY5dr
                UMcxLpeX14xbGsJKX6NHzJFuCoP1w7Z1zTC4Hj+hC5NETgc5dXHM6Yso2lHbkFa8
                coxbCxGB4vvTh7THmrGl/v7ONxZ693LdrRTrTDmC2lpZ0OnrFz7GMVCRFwAno6te
                9qoSnLhYVye5NYooUB1xOnLz8dsxcUKG+bZAgBOvBgRddVkvwLfdR8c+2cdbEenX
                xp98rfwygKkGLFJzxDvhw0+HRIhkzqe1yX1tMvWb1fJThGU7tcT6pFvqi4lAKEPm
                Rba5Jp4r2YjdrLAzMo/7BgRQ998IAFPmlpslHodezsMs/FkoQNaatpp14Gs3nFNd
                lSZrCC9PCckxYrM7DZ9zB6TqqlIQRDf+1m+O4+q71F1nslqBM/SWRotSuv/b+tk+
                7xqYGLXkLscieIo9jTUp/Hd9K6VwgB364B7IgwKDfB+54DVXJ2Re4QRsP5Ffaugt
                rU+2sDVqRlGP/INBVcO0/m2vpsyKXM9TxzoISdjUT33PcnVOcOG337RHu070nRpx
                j2Fxu84gCVDgzpJhBrFRo+hx1c5JcxvWZQqbDKly2hxfE21Egg6mODwI87OEzyM4
                54nFE/YYzFaUpvDO4QRRHh7XxfI6Hr/YoNuEJFUyQBVtv2IoMbDGQ9HFUbbz96mN
                KbhcLeBaZfphXu4WSVvZBzdnIRW1PpHF2QAozz8ak5U6FT3lO0QITpzP9rc2aTkm
                2u/rstd6pa1om5LzFoZmnfFtFxXMWPeiz7ct0aUekvglmTp0Aivn6etgVGVEVwlN
                FJKPICFeeyIqxWtRrb7I2L22mDl5p+OiG0S10VGMqX0LUZX1HtaiQ1DIl0fh7epR
                tEjj6RRwVM6SeHPJDbOU2GiI4H3/F3WT1veeFSMCIErrA74jhq8+JAeL0CixaJ9e
                FHyfRSyM6wLsWcydtjoDV2zur+mCOQI4l9oCNmMKU8Def0NaGYaXkvqzbnueY1dg
                8JBp5kMucAA1rCoCh5//Ch4b7FIgRxk9lOtd8e/VPuoRRMp4lAhS9eyXJ5BLNm7e
                T14tMx+tX8KC6ixH6SMUJ3HD3XWoc1dIfe+Z5fGOnZ7WI8F10CiIxR+CwHqA1UcW
                s8PCvb4unwqbuq6+tNUpNodkBvXADo5LvQpewFeX5iB8WrbIjxpohCG9BaEU9Nfe
                KsJB+g6L7f9H92Ldy+qpEAT40x6FCVyBBUmUrTgm40S6lgQIEPwLKtHeSM+t4ALG
                LlpJoHMas4NEvBY23xa/YH1WhV5W1oQAPHGOS62eWgmZefzd7rHEp3ds03o0F8sO
                GE4p75vA6HR1umY74J4Aq1Yut8D3Fl+WmptCQUGYzPG/8qLI1omkFOznZiknZlaJ
                6U25YeuuxWFcvBp4lcaFGslhQy/xEY1GB9Mu+dxzLVEzO+S00OMN3qeE7Ki+R+dB
                vpwZYx3EcKUu9NwTpPNjP9Q014fBcJd7QX31mOHQ3eUGu3HW8LwX7HDjsDzcGWXL
                Npk/YzsEcuUNCSOsbGb98dPmRZzBIfD1+U0J6dvPXWkOIyM4OKC6y3xjjRsmUKQw
                jNFxtoVRJtHaZypu2FqNeMKG+1b0qz0hSXUoBFxjJiyKQq8vmALFO3u4vijnj+C1
                zkX7t6GvGjsoqNlLeJDjyILjm8mOnwrXYCW/DdLwApjnFBoiaz187kFPYE0eC6VN
                EdX+WLzOpq13rS6MHKrPMkWQFLe5EAGx76itFypSP7jjZbV3Ehv5/Yiixgwh6CHX
                tqy0elqZXkDKztXCI7j+beXhjp0uWJOu/rt6rn/xoUYmDi8RDpOVKCE6ACWjjsea
                q8hhsl68UJpGdMEyqqy34BRvFO/RHPyvTKpPd1pxbOMl4KQ1pNNJ1yC88TdFCvxF
                BG/Bofg6nTKXd6cITkqtrnEizpcAWTBSjrPH9/ESmzcoh6NxFVo7ogGiXL8dy2Tn
                ze4JLDFB+1VQ/j0N2C6HDleLK0ZQCBgRO49laXc8Z3OFtppCt33Lp6z/2V/URS4j
                qqHTfh2iFR6mWNQKNZayesn4Ep3GzwZDdyYktZ9PRhIw30ccomCHw5QtXGaH32CC
                g1k1o/h8t2Kww7HQ3aSmUzllvvG3uCkuJUwBTQkP7YV8RMGDnGlMCmTj+tkKEfU0
                citu4VdPLhSdVddE3kiHAk4IURQxwGJ1DhbHSrnzJC8ts/+xKo1hB/qiKdb2NzsH
                8205MrO9sEwZ3WTq3X+Tw8Vkw1ihyB3PHJwx5bBlaPl1RMF9wVaYxcs4mDqa/EJ4
                P6p3OlLJ2CYGkL6eMVaqW8FQneo/aVh2lc1v8XK6g+am2KfWu+u7zaNnJzGYP4m8
                WDHcN8PzxcVvrMaX88sgvV2629cC5UhErC9iaQH+FZ25Pf1Hc9j+c1YrhGwfyFbR
                gCdihA68cteYi951y8pw0xnTLODMAlO7KtRVcj7gx/RzbObmZlxayjKkgcU4Obwl
                kWewE9BCM5Xuuaqu4yBhSafVUNZ/xf3+SopcNdJRC2ZDeauPcoVaKvR6vOKmMgSO
                r4nly0qI3rxTpZUQOszk8c/xis/wev4etXFqoeQLYxNMOjrpV5+of1Fb4JPC0p22
                1rZck2YeAGNrWScE0JPMZxbCNC6xhT1IyFxjrIooVEYse3fn470erFvKKP+qALXT
                SfilR62HW5aowrKRDJMBMJo/kTilaTER9Vs8AJypR8Od/ILZjrHKpKnL6IX3hvqG
                5VvgYiIvi6kKl0BzMmsxISrs4KNKYA==
                """, kp);

        // Data from https://datatracker.ietf.org/doc/html/draft-ietf-lamps-kyber-certificates-07
        g = KeyPairGenerator.getInstance("ML-KEM");
        g.initialize(NamedParameterSpec.ML_KEM_512, new FixedSecureRandom(seed));
        kp = g.generateKeyPair();
        testKeys("""
                MFICAQAwCwYJYIZIAWUDBAQBBEAAAQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRob
                HB0eHyAhIiMkJSYnKCkqKywtLi8wMTIzNDU2Nzg5Ojs8PT4/
                """, """
                MIIDMjALBglghkgBZQMEBAEDggMhADmVgV5ZfRBDVc8pqlMzyTJRhp1bzb5IcST2
                Ari2pmwWxHYWSK12XPXYAGtRXpBafwrAdrDGLvoygVPnylcBaZ8TBfHmvG+QsOSb
                aTUSts6ZKouAFt38GmYsfj+WGcvYad13GvMIlszVkYrGy3dGbF53mZbWf/mqvJdQ
                Pyx7fi0ADYZFD7GAfKTKvaRlgloxx4mht6SRqzhydl0yDQtxkg+iE8lAk0Frg7gS
                Tmn2XmLLUADcw3qpoP/3OXDEdy81fSQYnKb1MFVowOI3ajdipoxgXlY8XSCVcuD8
                dTLKKUcpU1VntfxBPF6HktJGRTbMgI+YrddGZPFBVm+QFqkKVBgpqYoEZM5BqLtE
                wtT6PCwglGByjvFKGnxMm5jRIgO0zDUpFgqasteDj3/2tTrgWqMafWRrevpsRZMl
                JqPDdVYZvplMIRwqMcBbNEeDbLIVC+GCna5rBMVTXP9Ubjkrp5dBFyD5JPSQpaxU
                lfITVtVQt4KmTBaItrZVvMeEIZekNML2Vjtbfwmni8xIgjJ4NWHRb0y6tnVUAAUH
                gVcMZmBLgXrRJSKUc26LAYYaS1p0UZuLb+UUiaUHI5Llh2JscTd2V10zgGocjicy
                r5fCaA9RZmMxxOuLvAQxxPloMtrxs8RVKPuhU/bHixwZhwKUfM0zdyekb7U7oR3l
                y0GRNGhZUWy2rXJADzzyCbI2rvNaWArIfrPjD6/WaXPKin3SZ1r0H3oXthQzzRr4
                D3cIhp9mVIhJeYCxrBCgzctjagDthoGzXkKRJMqANQcluF+DperDpKPMFgCQPmUp
                NWC5szblrw1SnawaBIEZMCy3qbzBELlIUb8CEX8ZncSFqFK3Rz8JuDGmgx1bVMC3
                kNIlz2u5LZRiomzbM92lEjx6rw4moLg2Ve6ii/OoB0clAY/WuuS2Ac9huqtxp6PT
                UZejQ+dLSicsEl1UCJZCbYW3lY07OKa6mH7DciXHtEzbEt3kU5tKsII2NoPwS/eg
                nMXEHf6DChsWLgsyQzQ2LwhKFEZ3IzRLrdAA+NjFN8SPmY8FMHzr0e3guBw7xZoG
                WhttY7Js
                """, kp);
        testCert("""
                -----BEGIN CERTIFICATE-----
                MIINpDCCBBqgAwIBAgIUFZ/+byL9XMQsUk32/V4o0N44808wCwYJYIZIAWUDBAMR
                MCIxDTALBgNVBAoTBElFVEYxETAPBgNVBAMTCExBTVBTIFdHMB4XDTIwMDIwMzA0
                MzIxMFoXDTQwMDEyOTA0MzIxMFowIjENMAsGA1UEChMESUVURjERMA8GA1UEAxMI
                TEFNUFMgV0cwggMyMAsGCWCGSAFlAwQEAQOCAyEAOZWBXll9EENVzymqUzPJMlGG
                nVvNvkhxJPYCuLambBbEdhZIrXZc9dgAa1FekFp/CsB2sMYu+jKBU+fKVwFpnxMF
                8ea8b5Cw5JtpNRK2zpkqi4AW3fwaZix+P5YZy9hp3Xca8wiWzNWRisbLd0ZsXneZ
                ltZ/+aq8l1A/LHt+LQANhkUPsYB8pMq9pGWCWjHHiaG3pJGrOHJ2XTINC3GSD6IT
                yUCTQWuDuBJOafZeYstQANzDeqmg//c5cMR3LzV9JBicpvUwVWjA4jdqN2KmjGBe
                VjxdIJVy4Px1MsopRylTVWe1/EE8XoeS0kZFNsyAj5it10Zk8UFWb5AWqQpUGCmp
                igRkzkGou0TC1Po8LCCUYHKO8UoafEybmNEiA7TMNSkWCpqy14OPf/a1OuBaoxp9
                ZGt6+mxFkyUmo8N1Vhm+mUwhHCoxwFs0R4NsshUL4YKdrmsExVNc/1RuOSunl0EX
                IPkk9JClrFSV8hNW1VC3gqZMFoi2tlW8x4Qhl6Q0wvZWO1t/CaeLzEiCMng1YdFv
                TLq2dVQABQeBVwxmYEuBetElIpRzbosBhhpLWnRRm4tv5RSJpQcjkuWHYmxxN3ZX
                XTOAahyOJzKvl8JoD1FmYzHE64u8BDHE+Wgy2vGzxFUo+6FT9seLHBmHApR8zTN3
                J6RvtTuhHeXLQZE0aFlRbLatckAPPPIJsjau81pYCsh+s+MPr9Zpc8qKfdJnWvQf
                ehe2FDPNGvgPdwiGn2ZUiEl5gLGsEKDNy2NqAO2GgbNeQpEkyoA1ByW4X4Ol6sOk
                o8wWAJA+ZSk1YLmzNuWvDVKdrBoEgRkwLLepvMEQuUhRvwIRfxmdxIWoUrdHPwm4
                MaaDHVtUwLeQ0iXPa7ktlGKibNsz3aUSPHqvDiaguDZV7qKL86gHRyUBj9a65LYB
                z2G6q3Gno9NRl6ND50tKJywSXVQIlkJthbeVjTs4prqYfsNyJce0TNsS3eRTm0qw
                gjY2g/BL96CcxcQd/oMKGxYuCzJDNDYvCEoURncjNEut0AD42MU3xI+ZjwUwfOvR
                7eC4HDvFmgZaG21jsmyjUjBQMA4GA1UdDwEB/wQEAwIFIDAdBgNVHQ4EFgQUDsWS
                pZcefo2geKhuRnTy+xH26NcwHwYDVR0jBBgwFoAUMpoHsfq7SPUqMJ8RoYmPhI4j
                Iv8wCwYJYIZIAWUDBAMRA4IJdQDcV8LA/De8Ss6UL3tMcHXKc0iTXaBPPLyoCimW
                KG/BhZ299qdyg6Qv/hWMxXfuQLvBIJUiE9boIUvDJH1Bv5q+wBXDM4Pcb585a972
                fB7Lj7rTYwGezp4QRGsn4bMOUHtOS/9MaD9LAw8XlEDSl69KgN+jN+Cak+PS1Q3O
                u+TpeM2fo304+3vTfHlNiePSNOqkd1pzs2nwVIbQGIWctpF1rIHC7NJ/XOO3ZsN3
                Cr758OLyAotCdGCRnj16Fhxh1rJ976b6y+Yo96CDMgl22lYPJoihlBekuKc4ugkE
                g4vJEwAtPlMoaogn7XJcWkKIhGKp1M7nG9KvgQxCRvIfRURuDyHaiOAkOayK+Hp6
                4AV02pbYX/w1X9bW1KOeId42EUQpF2iFu3ilOJi1JmMFyMP8lZZYq/8fPv3KGZPF
                YJpd6yaA7ReIQaNiFgCMqx7nw/Zti7sa2a5dor3YqYRjZ8UlJUuYUKxNDde/u46W
                mIEGSYcynpOiEYbyeWmXW4ye7qhT1Q7bmFPV8Mjzn3rXytzUzUZfrK8j9cHxAozY
                sF7RDuBmauliYfV1jaroCcHrohVTnSSiSMQKV4q6HjKPIpf4qENs4SVh9xkWXdbB
                OaiGgFhsI+sxlDGPRwbKrj6gVcbyFuJIPRL1LylJ2qFXzpzHyfAS3fHFvgv+S0AJ
                DnfNk3OcT7G9jQhESQOkTXA4LqxPI+0c6asvauXlICnN8RdOjraY4+DQL8cYidEi
                SAnXsOKNSzj+b225zdPvfBB/4eJTtV7VdnQOhETJErofxEWbpA8zobl/+bu2smdY
                Pg1a83hwVo+HxfkSz1iHW9WT9+iwhnm28RqzLdmmzZGJSfgEFkADriwXUEr+LIkX
                0xeMGvyXxdxv9S6Y6y+n0Al0ql0tzGviVoDqA0xNLU+Mupou5ftDTJj7U1oxIUHj
                HlFeE06+JRoTPbDcl+cBil31SlxuZ1u7cOE33nbPOw0jWDXeA8M5uE3aMQah5VRf
                tZXmdijH4zEN1/++Q5oJAF1SCTsnTkZ0lk3ZlIfpO0H1sJpINzLlBO04dLlQx2Nc
                NFIExuPsVO7kW1rDLqkh8srBKrdUa/8ngD3kppXW7iaBhSnUE0N6lrwi5g/fJbNU
                H0W7r0b31u0KDQ8cNKlK8PZL5pu/ulJTGZ5Dz4HORwVt2aXQojZfGQ0rashKxes8
                F+Ewgse7NUAt3HqX94+0SWpfpNCVlZknK5XfhZJV08XVZ2TkTDoJ6aBLqua/a5Xg
                jWTwroAJuB84jx2B1eCeYxjt+3cEaB274XU++H6m5kP/1QtJ3L1r545NaRQAylZF
                MwCtCTVyAavhrTcrQwhl8rVGAKOlXaCfHSln8y9u26qMHeL9BIP7JeMeZxCYQQ5b
                QxN0WvGmK11W6XG2CTc0qQ0RdUOvfrXTfl5A+I6DS4T2Z26APgkoq2JSQihO3JEg
                S7zknl2NoAummhweGU/qSPzX+4/KlxwcCCs8mD8ZkkwhdB5poU4uTES/eCO+rrm3
                wxLmiIcv2RwNdN8bRkxm35SQCCfc6riit4AxkaRKz5b27FWedfkH9bOgQaQGxm/v
                5IwGHsFGeQFJyV1pNvo0aB9vvMTL3VZOsoXooxrdlc0kv7jJ9Q6eF8ZAFYXvxnaS
                D+/OsH1b1+6WCVZIDRzRsMauvaifYUZNMQQ/CKSkDkFPjBDY5Xca9yZkGl+S+Pzz
                7ODu6y3lvvUk+V6sPKEAS4ejZOocriV75SPfz0WlRZoljJXOm3tKCo6L2e56ntVs
                hRiIBaLG5stQf2EihTSZUf21zNjb15E7KcdbTtr8TE0iJAuVYxBtNRWsVhExOMO/
                QqXWnHL015pv8Dubwt6iDr8ObCDNOItPtszlNjCz4yN51aGTrHGZ0CJcbcUWqxOm
                W1wrQmnYWUaz1eDahmbnowXshqI8RcGqvzUlZ0/g6nEbAJZgbk7jozC1VlwOKMM4
                erhkw5mrrpicX3cvP3wl3JyhB6vbAfK4XQH3CfrnK12BhpgG0+9V5DKxTL02f+5m
                ckJI9cZqSYx8rhlDlNbR33kSOY0Ba2RwvmMxhdypd38l5S8oSwTRu5eJ4VrrSeeM
                wiW3gIxLA+o+SD2iFKyafsWLeu+Axx5/HlIVB+g82dGKkZrrESEvO9LpdlaS+AMW
                9BccbDD2SGE2UZKlK4zx2QwYvnFG/ZDRjmvQV0dQOxiy0j2l7WHmbedlTTUUd5FU
                0cfSG+cJHnToa/VRU4mDHvFpnV+AF0dA1s0oemhN5vOqhDzHnKasFFpUDH88mS7K
                gbXELYiHTQEB/s/Hr0crjwVQQCbJFe4bBJzhcnwuOcdNUKLmF7MidvoyKYYu20oE
                P6F0/RoDwS2FW3RyrKeSzlLWnuarfTq84iMaPgKrOl8XNfaSgGRsG3kxGe0s3rVs
                iwzaO8THoCLp6WpEebfucmSCMXtKfVG/28u/dvQkz1D0oqTcWqhQiDLqZI3HjdDr
                io44DARVGKAsEvq75Jq91GXP+1R8yejpP1lZU4onX1i0E8DMuVEU85JN+kFXbS83
                6nZHmYhgwj93IvetNiK5cJs2M19LnJj5GrONmPMizoXCIBjzDx0MO/3CoRF5achF
                p598lYloyvlS1VYhwmLrpFmz0BB9OEepvdq0ZX11XM532I6WIF4lAUh0YEx1FInO
                XJ74LC2uMxa92W6nceJAjiraJKhi4VnURhPa7MUt/2oA5WY8zzmVGn94UlPsEmPj
                /nl7vXBVLb9Nojt9AkIO637bT+1wszCvOH8nelnzNDsCBi9B8+mdgzizEN08UKSk
                dCaNbCB86LVeo+umyY5abmgr2NOI7XaSTqWMs7ezemR5AkIUka35LgVIKvZw2WEz
                G3KxZImSviV+XMsakqGTdXof7k1usEcmbJ/EJLi9ecaxMZKuLjT9sFtNo8uvE/m1
                1pf4bGnGXgBERGpZsqnm+JNxDDTbD1WntdPpyeF8/6iXd/eNiHboV830Olj0dXJ4
                YbTrQBcWbfUeZ8+8gGJ0bgshMtPCrOdYVMAfWfcu7DyFi0tQdtS1pmo5Co+OwLxe
                IyKgwlIYOghCE3r6SBCrx0+sTP0sixV5Refu2JIBkjoywPavmK3+109l1F0BkzST
                fQ1pAwENGx0oLVFdZHB1f4CSlZaiq8Te7AtOfX6Qtba4w8bP1+j2FSVCWGt4goSv
                s7TAwcrR1drv9BRiaH2qytnr8PcAAAAAAAAAAAAAAAAAAAAAFSM2QA==
                -----END CERTIFICATE-----""", kp.getPublic(), dsa44);

        g.initialize(NamedParameterSpec.ML_KEM_768, new FixedSecureRandom(seed));
        kp = g.generateKeyPair();
        testKeys("""
                MFICAQAwCwYJYIZIAWUDBAQCBEAAAQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRob
                HB0eHyAhIiMkJSYnKCkqKywtLi8wMTIzNDU2Nzg5Ojs8PT4/
                """, """
                MIIEsjALBglghkgBZQMEBAIDggShACmKoQ1CPI3aBp0CvFnmzfA6CWuLPaTKubgM
                pKFJB2cszvHsT68jSgvFt+nUc/KzEzs7JqHRdctnp4BZGWmcAvdlMbmcX4kYBwS7
                TKRTXFuJcmecZgoHxeUUuHAJyGLrj1FXaV77P8QKne9rgcHMAqJJrk8JStDZvTSF
                wcHGgIBSCnyMYyAyzuc4FU5cUXbAfaVgJHdqQw/nbqz2ZaP3uDIQIhW8gvEJOcg1
                VwQzao+sHYHkuwSFql18dNa1m75cXpcqDYusQRtVtdVVfNaAoaj3G064a8SMmgUJ
                cxpUvZ1ykLJ5Y+Q3Lcmxmc/crAsBrNKKYjlREuTENkjWIsSMgjTQFEDozDdskn8j
                pa/JrAR0xmInTkJFJchVLs47P+JlFt6QG8fVFb3olVjmJslcgLkzQvgBAATznmxs
                lIccXjRMqzlmyDX5qWpZr9McQChrOLHBp4RwurlHUYk0RTzoZzapGfH1ptUQqG9U
                VPw5gMtcdlvSvV97NrFBDWY1yM60fE3aDXaijqyTnHHDAkgEhmxxYmZYRCFjwsIh
                F+UKzvzmN4qYVlIwKk7wws4Mxxa3eW4ray43d9+hrD2iWaMbWptTD4y2OKgaYqww
                GEmrr5WnMBvaMAaJCb/bfmfbzLs4pVUaJbGjoPaFdIrVdT2IgPABbGJ0hhZjhMVX
                H+I2WQA2TQODEeLYdds2ZoaTK17GAkMKNp6Hpu9cM4eGZXglvUwFes65I+sJNeaQ
                XmO0ztf4CFenc91ksVDSZhLqmsEgUtsgF78YQ8y0sygbaQ3HKK36hcACgbjjwJKH
                M1+Fa0/CiS9povV5Ia2gGRTECYhmLVd2lmKnhjUbm2ZJPat5WU2YbeIQDWW6D/Tq
                WLgVONJKRDWiWPrCVASqf0H2WLE4UGXhWNy2ARVzJyD0BFmqrBXkBpU6kKxSmX0c
                zQcAYO/GXbnmUzVEZ/rVbscTyG51QMQjrPJmn1L6b0rGiI2HHvPoR8ApqKr7uS4X
                skqgebH0GbphdbRCr7EZCdSla3CgM1soc5IYqnyTSOLDwvPrPRWkHmQXwN2Uv+sh
                QZsxGnuxOhgLvoMyGKmmsXRHzIXyJYWVh6cwdwSay8/UTQ8CVDjhXRU4Jw1Ybhv4
                MZKpRZz2PA6XL4UpdnmDHs8SFQmFHLg0D28Qew+hoO/Rs2qBibwIXE9ct4TlU/Qb
                kY+AOXzhlW94W+43fKmqi+aZitowwmt8PYxrVSVMyWIDsgxCruCsTh67QI5JqeP4
                edCrB4XrcCVCXRMFoimcAV4SDRY7DhlJTOVyU9AkbRgnRcuBl6t0OLPBu3lyvsWj
                BuujVnhVwBRpn+9lrlTHcKDYXBhADPZCrtxmB3e6SxOFAr1aeBL2IfhKSClrmN1D
                IrbxWCi4qPDgCoukSlPDqLFDVxsHQKvVZ9rxzenHnCBLbV4lnRdmoxu7y05qBc9F
                AhdrMBwcL0Ekd1AVe87IXoCbMKTWDXdHzdD1uZqoyCaYdRd5OqqAgKCxJKhVjfcr
                vje3X07btr6CFtbGM/srIoDiURPYaV5DSBw+6zl+sZJQUim2eiAeqJPD4ssy2ovD
                QvpN6gV4
                """, kp);
        testCert("""
                -----BEGIN CERTIFICATE-----
                MIISnTCCBZqgAwIBAgIUFZ/+byL9XMQsUk32/V4o0N44808wCwYJYIZIAWUDBAMS
                MCIxDTALBgNVBAoTBElFVEYxETAPBgNVBAMTCExBTVBTIFdHMB4XDTIwMDIwMzA0
                MzIxMFoXDTQwMDEyOTA0MzIxMFowIjENMAsGA1UEChMESUVURjERMA8GA1UEAxMI
                TEFNUFMgV0cwggSyMAsGCWCGSAFlAwQEAgOCBKEAKYqhDUI8jdoGnQK8WebN8DoJ
                a4s9pMq5uAykoUkHZyzO8exPryNKC8W36dRz8rMTOzsmodF1y2engFkZaZwC92Ux
                uZxfiRgHBLtMpFNcW4lyZ5xmCgfF5RS4cAnIYuuPUVdpXvs/xAqd72uBwcwCokmu
                TwlK0Nm9NIXBwcaAgFIKfIxjIDLO5zgVTlxRdsB9pWAkd2pDD+durPZlo/e4MhAi
                FbyC8Qk5yDVXBDNqj6wdgeS7BIWqXXx01rWbvlxelyoNi6xBG1W11VV81oChqPcb
                TrhrxIyaBQlzGlS9nXKQsnlj5DctybGZz9ysCwGs0opiOVES5MQ2SNYixIyCNNAU
                QOjMN2ySfyOlr8msBHTGYidOQkUlyFUuzjs/4mUW3pAbx9UVveiVWOYmyVyAuTNC
                +AEABPOebGyUhxxeNEyrOWbINfmpalmv0xxAKGs4scGnhHC6uUdRiTRFPOhnNqkZ
                8fWm1RCob1RU/DmAy1x2W9K9X3s2sUENZjXIzrR8TdoNdqKOrJOcccMCSASGbHFi
                ZlhEIWPCwiEX5QrO/OY3iphWUjAqTvDCzgzHFrd5bitrLjd336GsPaJZoxtam1MP
                jLY4qBpirDAYSauvlacwG9owBokJv9t+Z9vMuzilVRolsaOg9oV0itV1PYiA8AFs
                YnSGFmOExVcf4jZZADZNA4MR4th12zZmhpMrXsYCQwo2noem71wzh4ZleCW9TAV6
                zrkj6wk15pBeY7TO1/gIV6dz3WSxUNJmEuqawSBS2yAXvxhDzLSzKBtpDccorfqF
                wAKBuOPAkoczX4VrT8KJL2mi9XkhraAZFMQJiGYtV3aWYqeGNRubZkk9q3lZTZht
                4hANZboP9OpYuBU40kpENaJY+sJUBKp/QfZYsThQZeFY3LYBFXMnIPQEWaqsFeQG
                lTqQrFKZfRzNBwBg78ZdueZTNURn+tVuxxPIbnVAxCOs8mafUvpvSsaIjYce8+hH
                wCmoqvu5LheySqB5sfQZumF1tEKvsRkJ1KVrcKAzWyhzkhiqfJNI4sPC8+s9FaQe
                ZBfA3ZS/6yFBmzEae7E6GAu+gzIYqaaxdEfMhfIlhZWHpzB3BJrLz9RNDwJUOOFd
                FTgnDVhuG/gxkqlFnPY8DpcvhSl2eYMezxIVCYUcuDQPbxB7D6Gg79GzaoGJvAhc
                T1y3hOVT9BuRj4A5fOGVb3hb7jd8qaqL5pmK2jDCa3w9jGtVJUzJYgOyDEKu4KxO
                HrtAjkmp4/h50KsHhetwJUJdEwWiKZwBXhINFjsOGUlM5XJT0CRtGCdFy4GXq3Q4
                s8G7eXK+xaMG66NWeFXAFGmf72WuVMdwoNhcGEAM9kKu3GYHd7pLE4UCvVp4EvYh
                +EpIKWuY3UMitvFYKLio8OAKi6RKU8OosUNXGwdAq9Vn2vHN6cecIEttXiWdF2aj
                G7vLTmoFz0UCF2swHBwvQSR3UBV7zshegJswpNYNd0fN0PW5mqjIJph1F3k6qoCA
                oLEkqFWN9yu+N7dfTtu2voIW1sYz+ysigOJRE9hpXkNIHD7rOX6xklBSKbZ6IB6o
                k8PiyzLai8NC+k3qBXijUjBQMA4GA1UdDwEB/wQEAwIFIDAdBgNVHQ4EFgQUQry1
                oWf6MwRJYS29gYcFanUY94cwHwYDVR0jBBgwFoAUGwVj480zRhScjJ688jsKTlqQ
                DuowCwYJYIZIAWUDBAMSA4IM7gDya3x1P7gnc/43+gwI1bbPyLFhkbPTUdbp8wrj
                S6y1IBreYKD5+OSNsHx1sQ+vThL20hYZunwSyzM3ud/UFZJcpTYE3hLIqWYYlFfD
                KXc9OUYfL4xYtwY9L7NuV9GitoPOZqXGxC8uFBcCPtgXnKKm+2VcUcp3WAdgnW6T
                ohOKPc1JMN1ElgywyAeUKGyVu26WhQxltO/tD9NyWjjx88GJQB0EAhd+CUx2gJoG
                71QWYaHKKKY2Ap66VvNY8EwfG8xHfd1agWXl+dR7OldlYHAflSrZyczt/m97CBfT
                gz0q59YrtpgFC6A8f27DOns49/pcvFrFvnqbrB6olgn4g95w9a+zTjK+0LEOLuZ7
                coxK7G52UM4+zm89rgiV6Lf57E+gq6PIg6VJQzWeNlii8vK2c4D9+ru9DWxrQYIp
                lO011cW7q37cw1UenD7ouG6zd0Rgq5LIaoeQgwngLFoAEGl213xGJ7nFmPKweq6m
                jEWArh8WFdQS8xaArVxh16Qhijpk9aIMRXP8kv7x8ORXIOQkfE2zVQnnjMt7zTO7
                YbKY0ujPJwEga8UsP95V3ApLLNc4S9EIm/URSL9i1eA5Yf0/7qZub4512LN3tH9f
                QGr96wtIGKmMmD/M/ON86GXWRMvQW8w3DSgi73RuM5WH+IVZ8kRgdwx6ff/Flbd3
                PXXmxziQd6JdOIDn2JeTaEfZd6MxJ8juknEQTotIzOhSNJ08zcQqkCu0OQIcNMaK
                vzbzEDP+VbiIGxL6n7Y3JRnp+ACA2pWbB5lUl7Ex2OMCO9zrGAL5f98+5RFId7Mz
                2gQOah/y2FFHVw72TB3XFzyPuThiTSeXW/sQUMkvGXcb6cgUA25Umuq+tvKuktLt
                H7Rrj13+g+cSgkDMKpHPx2aVTaZ3hchDqQhplLu8adVkjaXldrrU/le3JYUwZCsL
                4ZCbWfEZeRgq7rVirSSEm8U1psE5mFZ0LqewLz87FKIYmTFVY25Xew+T4O/BC35P
                k3xp5pP99ShC+0o0YyStQziC2PmNNzjm6xHGYAYas7gyfpqVz93ooN5lg9uMTnLs
                SdAD/jsumB9nLGFPJ9tNYmL6AbnlBZiBwg2oSuIlSUBTCMFmbt+4QvsgeqjHx7nQ
                Z+oc8x7D3tSiVcf+sTICFRO6br2FF2PHDlTvKudW6ziFLsYWkkNK4K68p4GO983H
                R8pd0uXyhICMHSgriODpHmbTvyV2Vzh9+AKCt8PLiixeKzBL0Q6A2lquMk+cJP8f
                Q4QJL/TbUJ1B0yy1GVy6oToID+zM7ZUwI85VEqBnwWqA/UU3pggJg1CjItGrgM9x
                fGkPVjPZ9IjadgB0tgfHZ97gW6YiocaXmu6rrYF6rxYkWDaww9Uq8CQsrv7YRb2Q
                OeLCem1jyo/98YeMxVxBXZtAqMfgbAd2f0pa9Y3u84OBvdLNIyHXDWgmIhHG4uy1
                6JO6OxdU9qoEyw3s/8hCAQbQZfEHTsTTbR+ij35PCZHfYOZiFUZozMCSslHSrbIc
                +hmjd5slvDnbuxwCnhJX5dOnWRQtWzbUg4kJFwSven+MCQ6d8CS6RZbEHOwvCD4B
                qIHUaR1+lT9bW8kynPMZk6GdKCvyAEVnf9ka4mIiJrzycqBwwdOTlfKsESviE2yd
                9YyBF3adS6eOKiuE71HJ7h1gnpxQJLtrC0q4y4Rmh9arwDb5nQ7QrF4mG+jUMFLL
                sR8jd+/QHGmpZ5qhUfxyti2qQOteGjDlXtA2guahqCSX71GUpXLTY3VYisnWzoM/
                xdoMhKy+maEJ1mOeyrPnmOXh/mxLWpwcN42QH3u+iktGa66LKNwk5P4+1aSjV62k
                6jWvWAF6bSgr7hhffyt8Nr70HklYQg3NZpo5ivpzYzCJ6r5dm0yuL6pxJg098RYu
                3CfyjyOHB/FVhx+e9ADQ1I/NbkGyDvIj/AqD0TLbG9AyXU968SP3AEmedi3IZLGO
                EtA373hLW/rnVCa15+3rcLcQACfJwv8VwbIpeZSBh7fZ26KcR2Rj0vV7Qn786ZbK
                6aG9SlHpRCsV6hiQdsCYr1k+X0a7wrRr80fHrCd07vqG/hl4dbFu/IhMeQ243K6n
                3FTnHclYDoKaUQCmlOfgp9/3djAb/rOVwiPMoXkVS8JAJPa3gazejnITG+W209T1
                ukA+AYvpAR2qd1ysBjZnZxbEswAWKk2z6O/056/F1AQaIVRgKBIYzuwE1lLNLNV4
                OgLUZ791oEfjVx/1QqhgLBd3pY/U3535OlM8lCURjdMo0EuxsrIY3AxDQHdnSTsw
                EzE6ZDFLCFEKEEw/iVJul8qKUtFuoqsQMX51A2L1AosbaPzawY6RU2/BWFqew2A4
                K5Wm5YDwilHYlpBy3+F1ByNUI5+ayXMFwQi0dqpD6QXpuRm38Ze+qy2YKtaAljeJ
                xfcJjdIrx2LiAvKGHO6yMb+JVGliBZr38wS5fJX3sZY1gWE3uG82qMo9ft5ovmoE
                ZMMb4GSBfX8WTyncPmO/t7/wv+JbVP/Hx0yv/7WWVY1pPoC6boEtY4YrIHve7lxv
                S8NSixJ8ESLzffJZTGc9D/tDM6FRHobUZItSoFZwHpGGbfOrOD1Q8mWaVj2OxXh7
                nlWrKX+WSZX59sR+Ez4eHejnNXFT2FGWrUfK05+0YooTn/4jZE/u8X9tSf/HJkKb
                NyKoDeJ9lwf60iJFbQNf1zXVc0U3I9y833CvUz3V1XKZoZ6AQXcc5NW+lNpj0CPD
                3Z3tjwYGIdpQopZW6qYk66yektO780fYKdqG3W+0QvFmV25DjKx0DcNXDgs6AXn8
                Dehq70ogiRaqisQuXE0+Qy9MdXwx/9ytN6m3Th25dNg7PPKuPugbFAg3ev+RuPv0
                a3BwLozRyAIp5VGuG7Iu0E80kAXQixkN3YQpcWhXTsJBfsrFyUVJLejYgX0Xmkj+
                +2pf4+9IRf2nAwqcYRZylt1N0/x2/vVy7pz57NIoWGsQ9Vy8HcgK/rus1PWRhN36
                ic5IoCgko/ctVpKZfX3Rhhm4qjWXEgzsiMj8/RhbKC2m/MobcCNCQUK26fwetMri
                Sq62x3XTyaI4HU5kCQUdXcuaa13UvmFxNKqhKqJSYopCOk+2tP49qewc4dPKebbc
                qYF8kVhpJB5cwifB3ieaRjU66PaTX2AwZNa0k3XrXmql9pQ6h6K7QJ+DucAJn1n0
                FH0XElKBX2ebUC9luqUjHRKeJW/FDZEijj9ez8ssGMD4Elcut/qM1hNh1GB0hDN1
                x8yE3KNwHJfs9bQxphoRYnw78rINuwUU9Yild15XLEa9CzUvwmOcwQXku/X4aVPv
                0qsUnF414LGeySk/8XUcJewV/u9EdIm1XvL77iifRaV9CeRu4yEYPn737QCW7j+F
                Ex4WrWbokI54n+SeBuvZ6Jfs/12lPjFVIsD9MM+YaIVA2846cVJ0Idc+o7MGXK5e
                6p/2PjlRktXrYPVHrIRP3Ouc2js0IBEK6STubJFbSnAHTSRQqmcxph1BXLf6A1dd
                7dt7R7tKbepBxWKYq5liC9Rqq2oatrbMARH59EWscoEAzZP0L0rio1KPknvM0ZBI
                ibiszAb7sqkh7Hq7EoicirdXTjItOitSQWshGiuiKVqCE0jANM7lFhfO63XsFo7G
                GuOuqQKDJTx+8F5qHs2s7yC4uZDDmMx+pZ36J6Mae5CcyeXVQDgkBZdU47tVCeB0
                7WqaXFAdbJTKVwEkG3PSg9qp8SoDL6c9eQye/Hk1Z/vmf1tYHoPg8iJpx0iD/dEk
                /73iGZEAr7U7NM/ldcDxCXO1mfBNSmixq6zp5jJEH9TCo+usT0dQKGW0N1zPyDrH
                0qHWt1xSO0G6FPK4zTyEY/84z+ecXFvxxynXLYYCm5kEhK06PYiVY5OKOaBe9vma
                qS66MzHNpfjNblJfG9O/HeiJLJ3vV7/F3U/kfxs3PStrMgoXMRt1KBrmIBB3F1xE
                5WCaEONmuYSmJMZPbdkB+7rEsbC4v1cnyE0800BAGNYpVyPyTYbfPBthNEmYsBIV
                KSYuVQ1259Ju69UE22dqnXnorsCZCXWEpmcmRO8/Gvb0Y7OYFWltDeGLFJRbJ4av
                5dtNm2ZH53uLPi3aYsZU9cyfxh7AcbKSfQlRSVKCj6o0BQ3ZvmBPPOvcsUbUU5oo
                FgCPOse60fvnKhEEO9zEnuU3RObcQPkDQRmMQ3OhibiGzOEOaU6PCEVJ3P+N+lJm
                /0M2lNaYgaks0kmKoYdEmpLdmdGSCCB6HJ+nIIlwodrM0wK9SZUqkd+kFoGvGf7+
                XkFvmlJbGn4UCaaHOUaDZsFBMiAcMAAcPv9FIM+A9NIjbC2imd0TJf+tLf6tLA6P
                gFHtzTF9yuL8FSI+bbLr9go0PG2SnqPM4RQha4s2OoOvtNkQI2Smvu0AAAAAAAAA
                AAAAAAAAAAAAAAAFDBUZHyU=
                -----END CERTIFICATE-----""", kp.getPublic(), dsa65);

        g.initialize(NamedParameterSpec.ML_KEM_1024, new FixedSecureRandom(seed));
        kp = g.generateKeyPair();
        testKeys("""
                MFICAQAwCwYJYIZIAWUDBAQDBEAAAQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRob
                HB0eHyAhIiMkJSYnKCkqKywtLi8wMTIzNDU2Nzg5Ojs8PT4/
                """, """
                MIIGMjALBglghkgBZQMEBAMDggYhAEuUwpRQERGRgjs1FMmsHqPZglzLhjk6LfsE
                ZU+iGS03v60cSXxlAu7lyoCnO/zguvWlSohYWkATl6PSMvQmp6+wgrwhpEMXCQ6q
                x1ksLqiKZTxEkeoZOTEzX1LpiaPEzFbZxVNzLVfEcPtBq3WbZdLQREU4L82cTjRK
                ESj6nhHgQ1jhku0BSyMjKn7isi4jcX9EER7jNXU5nDdkbamBPsmyEq/pTl3FwjMK
                cpTMH0I0ptP7tPFoWriJLASssXzRwXDXsGEbanF2x5TMjGf1X8kjwq0gMQDzZZkY
                gsMCQ9d4E4Q7XsfJZAMiY3BgkuzwDHUWvmTkWYykImwGm7XmfkF1zyKGyN1cSIps
                WGHzG6oL0CaUcOi1Ud07zTjIbBL5zbF2x33ItsAqcB9HiQLIVT9pTA2CcntMSlws
                EEEhKqEnSAi4IRGzd+x1IU6bGXj3YATUE52YYT9LjpjSCve1NAc6UJqVm3p1ZPm0
                DKIYv2GCkyCoUCAXlU0yjXrGx2nsKXAHVuewaFs0DV4RgFlQSkmppQoQGY6xCleE
                Z460J9e0uruVUpM7BiiXlz4TGOrwoOrDdYSmVAGxcD4EKszYN1MUg/JBytzRwdN4
                EZ5pRCnbGZrIkeTFNDdXCFuzrng2ZzUMRFjZdnLoYegLHSZ5UQ6jpvI2DHekaULH
                oGpVTSKAgMhLR67xTbF2IMsWwGqzChvkzacIK+n4fpwhHEaRY0mluo6qUgHHKUo8
                CIW1O2V0UhCIJexkbJCgRhIyTufQMa/lNDEyy+9ntu+xpewoCbdzU4znez2LBOsL
                PCJWAR5McWwZqLoHUr9xSSEXZJ8GFcMpD8KaRv3kvVLbkobWAziCRCWcFaesK2QK
                YMwDN2pYQaP7ikc1aPqbGiZyFfNMAWl7Dw5icXXXIQW3cHwpueYUvcM6b2yBipU3
                C0J4gte0dnlqnsbrmTJ0zZsjkagrpF4zk9Lprpchyp1sG5iLWCdxP5CmWF3pQzUo
                wCsDzhC7X3IBOND7tMMMEma5GOUpJd/hezf5XSK8pU9HWRmshZCYwPDQisWHXvKb
                Vv0UHm7xX3AKC2bzlZXFiBdzc8RmmyG8Bx5MOqXwtKMbYljzXaJKw80px/IJJBDF
                B4NVsTj7U6a5rm4LnAgkPnuqRcRzduuMfxPUz1Gqc2+jFUDJJB83DaVEv5+cKNml
                fi8qfKlaTktGbmQas7zHat8ROdVnpvErUvOmXn7AquJryqjFWDOwTlmZjryaGTD7
                ttIjPFPSwfi5UY48Lec6Gd7ms4Clsylxz2ThKf1sH6bnXUojRQHpZt06VAr1yPTz
                SmtKJT7ihJJWbV5nxvVYVfywUG+wbBVnRNmgOjGib6lMrRTxV7fzA9B6acdzdo/L
                TQecCQWXA6DDqU3kuZ6jovFlg9D5Fwo5UNsHtPC8MIApJ/n3lhtiWYkmNqlQKicF
                MDY3eZ3TRNpFHBz3v2eEDOsweauMa4wZJ/ZAU8YSRQxFyeYDvBZmbllrNHHhA7bx
                VEdCTRcCIEgRH/vTfhxnD2TxS4p7MrlMGkm0XdL8OM1SidkQrWNgLPXhMELGSsZ5
                e4n7VRrQjgWpLSAMzLfnEu8jyTEss1DwKatTfihzR/0wdawQkGp4PxxsB8y4j0Ei
                jEvhxkD3kLXDpdXTynkklddLxGFWJljAesYAJ2uSSrW8m+HwSUy3b4L0YKdICXJm
                M4HhaZlgYdeZhZ7FTU9cpcQRwB2xWXsWWXdmneE6koo0r7rCWP6oxHZCOclCHcMR
                m/W0dpkgaXgyexxTRe90anmDhB8FbiU0EAqyTU6au9CxfGqVvUw8DkD2nhYSrO6y
                i5kIbJURbnIEJziTOQv0a4mbNihrDr8ZR7uYhPcyyifagrGbXcDMf4iFcUkQiIsj
                EMT5MZ1BCzTmQzuQA+IXa7mVJXRWEG6JUhY7i6WSUwzFqgrrQ605j+npe6pSPXpE
                MWd8PTrwcZ5HXbhcqVr1CJvqvrBbL6q0iWumD4HIhHKle0aoKIJqDN+0RvgYkYLS
                v16sTsHMXer1mcihPkgjVAbRf/3cg0S2xmmEqGiqkvoCInoIaVDrDIcB7VjcYod2
                uYOILhF1
                """, kp);
        testCert("""
                -----BEGIN CERTIFICATE-----
                MIIZQzCCBxqgAwIBAgIUFZ/+byL9XMQsUk32/V4o0N44808wCwYJYIZIAWUDBAMT
                MCIxDTALBgNVBAoTBElFVEYxETAPBgNVBAMTCExBTVBTIFdHMB4XDTIwMDIwMzA0
                MzIxMFoXDTQwMDEyOTA0MzIxMFowIjENMAsGA1UEChMESUVURjERMA8GA1UEAxMI
                TEFNUFMgV0cwggYyMAsGCWCGSAFlAwQEAwOCBiEAS5TClFAREZGCOzUUyaweo9mC
                XMuGOTot+wRlT6IZLTe/rRxJfGUC7uXKgKc7/OC69aVKiFhaQBOXo9Iy9Canr7CC
                vCGkQxcJDqrHWSwuqIplPESR6hk5MTNfUumJo8TMVtnFU3MtV8Rw+0GrdZtl0tBE
                RTgvzZxONEoRKPqeEeBDWOGS7QFLIyMqfuKyLiNxf0QRHuM1dTmcN2RtqYE+ybIS
                r+lOXcXCMwpylMwfQjSm0/u08WhauIksBKyxfNHBcNewYRtqcXbHlMyMZ/VfySPC
                rSAxAPNlmRiCwwJD13gThDtex8lkAyJjcGCS7PAMdRa+ZORZjKQibAabteZ+QXXP
                IobI3VxIimxYYfMbqgvQJpRw6LVR3TvNOMhsEvnNsXbHfci2wCpwH0eJAshVP2lM
                DYJye0xKXCwQQSEqoSdICLghEbN37HUhTpsZePdgBNQTnZhhP0uOmNIK97U0BzpQ
                mpWbenVk+bQMohi/YYKTIKhQIBeVTTKNesbHaewpcAdW57BoWzQNXhGAWVBKSaml
                ChAZjrEKV4RnjrQn17S6u5VSkzsGKJeXPhMY6vCg6sN1hKZUAbFwPgQqzNg3UxSD
                8kHK3NHB03gRnmlEKdsZmsiR5MU0N1cIW7OueDZnNQxEWNl2cuhh6AsdJnlRDqOm
                8jYMd6RpQsegalVNIoCAyEtHrvFNsXYgyxbAarMKG+TNpwgr6fh+nCEcRpFjSaW6
                jqpSAccpSjwIhbU7ZXRSEIgl7GRskKBGEjJO59Axr+U0MTLL72e277Gl7CgJt3NT
                jOd7PYsE6ws8IlYBHkxxbBmougdSv3FJIRdknwYVwykPwppG/eS9UtuShtYDOIJE
                JZwVp6wrZApgzAM3alhBo/uKRzVo+psaJnIV80wBaXsPDmJxddchBbdwfCm55hS9
                wzpvbIGKlTcLQniC17R2eWqexuuZMnTNmyORqCukXjOT0umulyHKnWwbmItYJ3E/
                kKZYXelDNSjAKwPOELtfcgE40Pu0wwwSZrkY5Skl3+F7N/ldIrylT0dZGayFkJjA
                8NCKxYde8ptW/RQebvFfcAoLZvOVlcWIF3NzxGabIbwHHkw6pfC0oxtiWPNdokrD
                zSnH8gkkEMUHg1WxOPtTprmubgucCCQ+e6pFxHN264x/E9TPUapzb6MVQMkkHzcN
                pUS/n5wo2aV+Lyp8qVpOS0ZuZBqzvMdq3xE51Wem8StS86ZefsCq4mvKqMVYM7BO
                WZmOvJoZMPu20iM8U9LB+LlRjjwt5zoZ3uazgKWzKXHPZOEp/WwfpuddSiNFAelm
                3TpUCvXI9PNKa0olPuKEklZtXmfG9VhV/LBQb7BsFWdE2aA6MaJvqUytFPFXt/MD
                0Hppx3N2j8tNB5wJBZcDoMOpTeS5nqOi8WWD0PkXCjlQ2we08LwwgCkn+feWG2JZ
                iSY2qVAqJwUwNjd5ndNE2kUcHPe/Z4QM6zB5q4xrjBkn9kBTxhJFDEXJ5gO8FmZu
                WWs0ceEDtvFUR0JNFwIgSBEf+9N+HGcPZPFLinsyuUwaSbRd0vw4zVKJ2RCtY2As
                9eEwQsZKxnl7iftVGtCOBaktIAzMt+cS7yPJMSyzUPApq1N+KHNH/TB1rBCQang/
                HGwHzLiPQSKMS+HGQPeQtcOl1dPKeSSV10vEYVYmWMB6xgAna5JKtbyb4fBJTLdv
                gvRgp0gJcmYzgeFpmWBh15mFnsVNT1ylxBHAHbFZexZZd2ad4TqSijSvusJY/qjE
                dkI5yUIdwxGb9bR2mSBpeDJ7HFNF73RqeYOEHwVuJTQQCrJNTpq70LF8apW9TDwO
                QPaeFhKs7rKLmQhslRFucgQnOJM5C/RriZs2KGsOvxlHu5iE9zLKJ9qCsZtdwMx/
                iIVxSRCIiyMQxPkxnUELNOZDO5AD4hdruZUldFYQbolSFjuLpZJTDMWqCutDrTmP
                6el7qlI9ekQxZ3w9OvBxnkdduFypWvUIm+q+sFsvqrSJa6YPgciEcqV7RqgogmoM
                37RG+BiRgtK/XqxOwcxd6vWZyKE+SCNUBtF//dyDRLbGaYSoaKqS+gIieghpUOsM
                hwHtWNxih3a5g4guEXWjUjBQMA4GA1UdDwEB/wQEAwIFIDAdBgNVHQ4EFgQU2oIY
                LDnr2zUNkE7kvFB7cgQ/+iMwHwYDVR0jBBgwFoAUiYhnULV8JNs/wBLmHt5ZdTM3
                N08wCwYJYIZIAWUDBAMTA4ISFAB0Ilvfx69mChnV48hOgGE9RRQLmMKyjFn4sKDx
                FO8grAAsxKw9hdEkv+TKqayLkCkxeDnhL/HIOnDRXxZ9iVUMcCUrhcerYIIZiUeu
                CJYYHAk0Wv/eQF+qzT3UNREKdljBD7rlem7wRC7oT6vf304BFsDOQmL3yL3gh8hI
                ycxU5SMh3dH6Gj1wSug91LVBV/QhLebDixXuKOe/q5dyNQRk1lI4im5ysGCkGzdq
                UZuanqBYvvE0c1dvvgeG9+qV9ARQOxmOaKYQMENVVA9HbzGV66GUrR19jK9z1bRI
                OSzFCba83oGHKyC9bHCLfvtXFXRxNVlDHGk7dRm2dAOds/iWJL4cu/M2O8rWaxIt
                ypfeieyKbr6CQjGzWqQ5lNYC3piMO9Byl6QxvZqBPhFeLbXYc3ZFhk250oz7m+LF
                DpHX0+uf4SROW51EDoo3gN3hQPp9usgYQcfprP/SpxGmxJ03GaHv/tFF/pEwCAT+
                sGPjYGsT14KVNG//guI4cHs9pE6s5Y8lslD1AUjFg8VQlIqF2JCPnaOGyagdEem3
                mazLJ0y2KCnFMhqp3oGaVWXC2LSwyOLe0XKeJWRbuvXQ4Wl81OItyLX86fjol8bO
                nCG83V3w4L3Omizd9SdnBtd6uv+1S6oxEvNcs7+pw6TN/6EuUaRPhi/jYr8Zpplq
                JfsCOUoLs6hJLjrD5QMmCCxYCrV76ea6Moyyr1/0mfElOkkTLMLzKN5p4vqPEdAd
                N5vDAT8g4Yn0MsRPqqK0pXyUA7Ax9ISGuQebeF9rBEtoEIG+bq4wXBWxmG2gQ3Ki
                ctNDS5LUZS23n85pZ8t002IX6fXD3JYtn4UMJEjbSh3+s6WY3A1qG00bLJL4chIq
                +G8mBAZm0/e0Kxb+H7Y1tWZnTe+pi08fKwRcPTEdHXLKU8bS53e3A851y8cNrGs0
                dNHaDQHjcboFgDhXS4geBY6iwzHGdmfDKcA5mxURP+XUgG6HBLuCYCmx0S5OzP+F
                ZY+bChnR7z0j8bTl4YOOIiaHyh2CW8frGsIlw1tBINezLWa7sr+4rx6C1CK0F2J/
                IdYIdEMLiL8Yx85wL0q0EufDoc/HPQRe3hDDtYsex3RMr83osZI+okf+3vtMoLv3
                CJxyZIp8Di65SuZRHZ5KNW/DGFWGAobRHbS6Va37KTjzysg1VsdM6wqcIYFvOMV/
                mvUVJ2MbXSawQuwKVMjYeibT8n55S9iL7mcfnivLgl7QNO86vaks8ZRpnZEA+FVS
                QiS0K9eZnBTI7L4bzJKZHgTg0tcd13qZXZtUpQdXxquS63o0lDZs7k5iKx7Xt3Pz
                T1f2y5ADQIrSPJ9Ytw71TubGotB39vkiqwvrF2fl7n/Ia8aEHp3k6x1OUbOcQ7G7
                PW+sE2mdgy+2FcSlyomFXDent9ayH135V2k87/YYwtJjt2rFMSRogut01AtKJ/On
                C1E2X5s5U9FXmeuy1ss/U6zHZ+VEiSSZlBu1ej6/yrsCAsu03/HepXMfbh4NuB4X
                yUTGRYg4rF12nH8ah9Er33b4iYM6zf5JVPRPba+6oDjQHYAjvD+gRF9D5t64PcaQ
                JAA381HRYqtigLpS1NaAD2bUvg2JYsZEkymXs1w+iG8aLBcakJpqmwKazFczcpZJ
                nAfhVAopjRQTyGxyslH+01Kd4ZUiP4LKZCkNrQjsNspIHIaAPMp0kL/FA03tfGwe
                sZvcvlnJYD7PIrwxCWdIFW24A6yaGKg4xE1NO9oJQWLRNDDY6IyOYf9jw4YNlcG5
                wsJ5IsbUcUckGOPHiRx9IHSiOFewb5KWjQUN79wA9/w1SWToG2fUSrfUSNhEvsV5
                F+As9EcQvgVGtINulzWWHxfCGbfVHZ8EO35xQG077xcEGMhMz9eNWQR8GdQOLy2k
                QjNlZV9U9pKa5CcVjkBRHPpfsFOMT4qHW6Arv6VoNcTwUuobFtl6DYWTeU/qrmN3
                e5gM176CKneRS8IoDF8nZeCDCeHAD17g4V9UUKNaeHaVQZ4elvvVwPhZvdrTGoIp
                +VZrYIJqltUCZwvBvsxy6ILzZHCGTLTQwWaHSiaRLVKUPVymXVBnzj2cReDb4pk8
                /bQu/03ZSquOub6PTV/8U7ejb4fXXa6TEWQa2Sao7ziqYIUTfwoPzNfvz4eLFMPw
                j7USnBXe8mV+MOgL2ncK7aobOIyfPwal5IEAA5ovPmY63T1JQGdAoumKTO7NOVb5
                hR/fXq25OrWf77Df3vlNdi5n1GC7UFXN2FdJ4wJl3X8my5L3sVOtzAWKMAqBLbqN
                cKFKxMvbYI6gBT79Vm9f4LgwGEf9lFQUk3ysP/uQFwURGGglzPN4GmIrNHPNx5yB
                bUU74kQ8d5KOYmP09S6gyxVd17nau6i4BkxwA69HnIS7RDXfg7kFnrnNvk0ySHFb
                a8YmLTK4n5HEO2KRSoayIjMq5j7CvTZZag/emL3dSdFsNsnqJclUl5RImlXg5xnv
                nf5x+lXcx7IZ3fBau3yE001C4W+ljlh9EzaRqTt0vT2JuJ/Mn4iRws/a7CYdX3+L
                FINsrgkOJwbgUOFZGG/LShXe1OjPxbVnE0TMl35QqC6tYyY+57lqb1cBc3+ZPmTc
                Q7yOeHfGAhdI7aYRV8Gqt2nx8ZwuhCJRuuxWGYjbpx9StbbVeSmQyQODoUUeXvBR
                7DjFqKVRz3CXFW0j8SMRJiXCk8pQb3J+cbyA2AuXJkBlkIYswLVgH2NT3onbnhO6
                0YbkUiv7d8AARktu1VHDpJWr5JgMSQ05k5b2rqKD0CPHWphapFFyEDBESeLLmnUH
                WXf0aNl7VrYrXYRzEXzUGDf61yUJbBw9gTLMDC8WGHl/NPth57aZ1Ao/IB8Ir3z2
                vXABqKz3Byk8klGzEa37tist+sZjN87DhKGjAUcolgoOn8F9p+SAwnLVLMhBo+Yi
                Fpu5hwAIggzYhC+fgH17Oz8m8SEL+o6LUoAtleMZPQCgbSb88CvBZPHBPa3l6+qF
                cORCrafkR7eKWUBCcJejSzUvap2ViqDSnerLHl0cppKvL0B9Jf++DO5RARKhTLdL
                BKCHsfGVWJh+cpePHdMM0Kzax5K46RjbKrK0v7qD5oHfHQOI6RV3oJ/SXuZr5HRq
                jHgy6quxwksp5w1il324kdoQ+VzaVHNbd7Oyngk8hM1RC2/HVyE/8xJjlZUxMolx
                /D460FpuXdxyuYg7Z46sHNv1o3O7sRiOFXJfOH9wVb6H4PAo3T8kK1HASaA4fXq1
                lj4NGV4eSD0bxDNJv+7uywbUTTKzy5ObF4swVgkfQHtRkGoXZwSTkIGnGw+bwOwO
                GIz2W0T4YZVwbHs6gChn7cCQnqUmrFH+wZn54qY5FDX9ZyGsP2qxeb5zh7GtZx4T
                WjcEkEok2O2YwvteSxYUPM/5lkol5edy9e5kua8YKEEFue04CghZv37ROQnh5+/s
                NFZooNTzP7iPDcYuPMYSCpbowrVaRRxu7A3+IK37n9gkB9NMXT4xXizv79ey3gO9
                xrk+2aa8GTC4JEXM3EUjiLIhlQ/GFLk6xPi0y9/dX4txmRzGi6DEyi6yfpog2xho
                56zUqHZ2qcKBmEyrKzd99JmDe3Riw9C0Lci3SzKP1DvNQktDerm5TkyhJbOQl5Y5
                fjkksJjUdEvWOGysJHx7GlUZRGPytXgTuXKEZ6oMObXt6+/lQFdB4117dsamPdl+
                IXyc9FxgwMCyaECP72CuvJwCNRrPEIxlRJAaMPYhalgltqGGFm8vDhyKgfbAyhIv
                OrkH6/7oOY8V/9SS6XtRIZD8WpLsxIKhB+spvtFSA3mkgLOw+Vx46CtV+91f5rJd
                HcDAqOMl/KebHbt0gTKiIncx4ICUS3OcTmF5MEhSxwBHqTGeF2u6w62h9jlpp+JD
                m34hh9A1gH3OwsnBGcBMxb6H23iXNGYZYyWyneIluQTvRT0CnKra8hgm8ONjXK6F
                N8BZepxBL1Bu7TQIH1iYUW5LnQzIEm6eIf/iaUz6S4RRT042Cek8YWWpkhAf4ko0
                0syLPVpPPxSZMpj2rUKmyOiPxLtHeVhE1QHeUS9YqkjEH9W31g68lzI/1OwIAPmX
                8/0W2ehncAXZzcvaqKn3sVF0ntfY6zexcvkWKnQntyrVik6feikCRDym5CguxGzv
                leBp4PVF9kMJ+lbRTCgvu+rAu70sm7HRYkbtvUQzdAkdIQYNGYa5Ah9+y/oI0vy1
                C4Yz5c5D4XLN6lomHL/N/e2A6RPwCa4i5BdVDButLBAiXg8QLeicikPLxmnzVJdV
                hat/2VgWDPmrW2hOfHgka+S4muOUcxHkLLKz4vIy4H6aUztSnjod5P/03JrQOm8q
                iBzhOYA9tzOKxNOn8SxlWlJHhT8vb7KX3pT9dKmWqfTPn5gYlnT8rexudJkcX0pY
                Qm9cLNKThdRAwP/t7Yk9evt6qh7g///JMZjKMIHtPE+mL5m/xiBjGNiA1JkV5/vl
                55tWqRGoJMv0qgcPvM9IKvUMk65x2gjH5os1fuV52BgVOpcwhbLJEmHG4wd/IEo9
                GrW7rFFGL4vyUNhxxXsmAsfhYsoSRR/s3GlX1FwPDxqUw+VS2duVCHYvKDBsZaLP
                Ergt6fDalHKZVTnI2tVGNH3fFpAmBC5V8Iq8thzK4fRK2yF8nGP4HYSWNqQc2P5o
                hB8wvEofpGjitBdNqlujkBMcNsLPPk9ZnUmQ3/erzFw34b0jTMUBrsfleaG2Kf1S
                9CG6YUiULoMoRh8cPSSrvaGCxfNx9M/WkaI8JvDsEL19ASBYqu3bOV2bCutPgbfP
                Bd1C6N8fNNzJ7hPSVAqz980TtfmgK+dj4NqhEw5AaVxy4+9IVGt6JhYAT8F//ATK
                xfAe44nD1Bj8UGN+seYwEk7dKaCd703yP6CNu9447k/3xkvtwcwtL40Kqmza6913
                B64HvQ2GjSaOdIAkaPq1ACy+2OI+S1kIvOTKBemHF3KMJf02+1ZdAhwJ4uJSnGDi
                uVT8svHM779FgIUMZjOmdE8dI7jpRKsw3czgucG2r/EPYRVa1B8cQd9iq8Xw1/Ce
                7CbgROAqmfboMupDgA+QEV9Nf2aAwqQTEs6yG5saOtoNiCULXwNmh18RPWhZhKqm
                voXPxnZyZ2VsN3jlcFB2WG5lngf+r//d32QX8ptGQHmETXxIvMmRG2p2TS7PAthx
                T45SNsbL5jNQFysjJQWTlGGYGjNGQJHtqhmiIwpUICoJNymGfYEkrg84QKo7+NdX
                xZFd7HAAw9MdSl1tvkLX+uiFzl+2d/d+SvAxHD3qDitg/90tUDLAoAxmaYO3lmFy
                kTuJUMVJLhkavp3LC2Q5K+mgevqlnw4h+sw2lY0a7RVLLnHc6/FVi/sC/Smu1u8u
                019R3unx8faluUtqsRvlxAjtH1feQdIApy5FFp5m8t+Ixpe1QipBTN3Aa+g3bph0
                hWw7u9JgPOja0lIJDDyGwWhyv4iCsII1OSKhHdLn3U34BCQ8nTY2DPqvojpRKg7u
                PVnSPpbAdLnfSU3Z+x4eQZiZLKQ8LwcOnU6+J8S2Mneboj4t8chpblbFqXEX2GDy
                jE6JffIAEtZan8bJyuD9lNJgr4raeyt2rqRLmpoY1Emk5HSioIjsgUTu92FeMp/b
                YWP6Fc/rXHoYl5xR5kUW4BtiB+592H/XdJzPHJQx2kjzS4gh1NH5s0yENMOWYTar
                0HJecZth4BF3SNDzElWcOvGWnMQj/fpkHgAq+aqXa2UCd4P/FaEXVUOuxy+vnHwe
                qqigp/mWD19+DiTyv7WEe+o/AomHctLyigGFlR2zs3yLXSwNnDJ6YANpgMlEspwS
                3ToM7PbcVC9vDfjKhGdAhvdVT1lr7IU0fYeMVppE6HkoKS6tbsokb9qtbvtvWCfz
                I6342qm7BW6/SiZEx/Sl/DzF8qA3eLHM0xFR2kvHsn+5AB5ucy2ZOJF2W9XuwYSU
                BPoRrmdIWKQYC8/MD5PtZMqUoEGvHl6jFpfbO6+RP6NakpA+q4Tl4xuDNyeKqOdD
                9+XdE3acWR/r+JseircGaBDDkpjBElcYgZuLfqKrx1+G5i6t6gWopcNtLmVcuAWv
                HVT854OIkNIUoqfnESODrczb3C5kjJ230df4V156qMbJBwwcJFtzf5ObyO3ycnd/
                kNggIp4AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQIDxcdKS4x
                -----END CERTIFICATE-----""", kp.getPublic(), dsa87);

    }

    // Ensures `certs` is a valid cert, contains public key `pk`,
    // and is signed by `signer`.
    private static void testCert(String certs, PublicKey pk, PublicKey signer)
            throws Exception {
        var cert = CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(
                        certs.getBytes(StandardCharsets.UTF_8)));
        Asserts.assertEquals(cert.getPublicKey(), pk);
        cert.verify(signer);
    }

    // Ensures encodings of keys in `kp` is the same as `sks` and `pks`,
    // and keys generated from the encodings are equivalent to original keys.
    static void testKeys(String sks, String pks, KeyPair kp) throws Exception {
        var sk = Base64.getMimeDecoder().decode(sks);
        var pk = Base64.getMimeDecoder().decode(pks);
        Asserts.assertEqualsByteArray(sk, kp.getPrivate().getEncoded());
        Asserts.assertEqualsByteArray(pk, kp.getPublic().getEncoded());
        var kf = KeyFactory.getInstance(kp.getPublic().getAlgorithm());
        assertEquivalence(kp, new KeyPair(
                kf.generatePublic(new X509EncodedKeySpec(pk)),
                kf.generatePrivate(new PKCS8EncodedKeySpec(sk))));
    }

    // Ensures 2 key pairs are equivalent
    static void assertEquivalence(KeyPair kp1, KeyPair kp2) throws Exception {
        Asserts.assertEqualsByteArray(kp1.getPrivate().getEncoded(),
                kp2.getPrivate().getEncoded());
        Asserts.assertEqualsByteArray(kp1.getPublic().getEncoded(),
                kp2.getPublic().getEncoded());
        checkInterop(kp1.getPublic(), kp1.getPrivate());
        checkInterop(kp1.getPublic(), kp2.getPrivate());
        checkInterop(kp2.getPublic(), kp1.getPrivate());
        checkInterop(kp2.getPublic(), kp2.getPrivate());
    }

    // Ensures pk and sk interop with each other
    static void checkInterop(PublicKey pk, PrivateKey sk) throws Exception {
        if (pk.getAlgorithm().startsWith("ML-KEM")) {
            var kem = KEM.getInstance("ML-KEM");
            var enc = kem.newEncapsulator(pk).encapsulate();
            var k = kem.newDecapsulator(sk).decapsulate(enc.encapsulation());
            Asserts.assertEqualsByteArray(k.getEncoded(), enc.key().getEncoded());
        } else {
            var msg = "hello".getBytes(StandardCharsets.UTF_8);
            var s = Signature.getInstance("ML-DSA");
            s.initSign(sk);
            s.update(msg);
            var sig = s.sign();
            s.initVerify(pk);
            s.update(msg);
            Asserts.assertTrue(s.verify(sig));
        }
    }
}
