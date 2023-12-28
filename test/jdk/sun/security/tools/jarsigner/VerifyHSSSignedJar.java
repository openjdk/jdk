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
 * @bug JDK-8298127
 * @summary verify JAR files signed with HSS/LMS
 * @library /test/lib
 * @run main VerifyHSSSignedJar
 */

import jdk.test.lib.SecurityTools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

public class VerifyHSSSignedJar {

    public static void main(String[] args) throws Exception {

        // This is a signed JAR file using the HSS/LMS signature algorithm.
        String jarFile = """
                UEsDBBQAAAAIAFKWqlbvY1zrjQAAAJAAAAAUABwATUVUQS1JTkYvTUFOSUZFU1Qu
                TUZVVAkAA7wfXGS8H1xkdXgLAAEE9QEAAAQAAAAA803My0xLLS7RDUstKs7Mz7NS
                MNQz4OVyLkpNLElN0XWqtFIwMtTNzCtJLcpLzFHQ8C9KTM5JVXDOLyrIL0osAerQ
                5OXi5fJLzE0FauXlCvZw1DUyNdN1yUwHmmqlEGgYnp1YkRcS7FdeFmbh5K7tb5ll
                6ObvX2rql+4YEOBlXhDm7WUQ6WfkHmoLMggAUEsDBBQAAAAIAFKWqlZLYPST8gAA
                ADIBAAASABwATUVUQS1JTkYvU0lHTkVSLlNGVVQJAAO8H1xkvB9cZHV4CwABBPUB
                AAAEAAAAAHWPTU+DMACG7yT8hx41pA6aCIPEw4ARNHxtC3Vyq66DZtBiKYL8eolH
                E2/P5X2evCdWc6JGSSGmcmCCe8B6MHUtkJQoeoH+tweQBRlXVHLSgrtcko+WgkDI
                Xkii1sW9rp3iHUSPNgxZTQcFU8LZdQUP3FJ5WdJJRM7Yz1m2ea3O0nTHzdzNoYU/
                m9byOzuc8CEQT/9qVmAc7pSS7H1UdPBAhUWPqRLJs2ENcYX8cCze/KSseLXdJ7Gu
                gam4Ruf0SA6Re1vNupaRjq7f/kY8gI9fxrjvm3qZm8FuqiJnRll29RLmLyZneOs4
                qHYRwjj9Ff0AUEsDBBQAAAAIAFKWqlbb/AXeEAsAABgMAAATABwATUVUQS1JTkYv
                U0lHTkVSLkRTQVVUCQADvB9cZLwfXGR1eAsAAQT1AQAABAAAAADFlWk41P0ax80Y
                MgYz9j1rSrb/2IlibJN9Xwcj+1ZE2creZF8qS5aMnawl+5KdcIhCE4mJqIg8YxCa
                03Ou5zo9T9d5f37Xdb+6v/f39+7zAWIZ2Gih53Fo3D4j6BQYH8tAA8QygMAgEBIO
                MNJCsTg0yJ0aAgbRUAGwX0EQPpZmFYilWQZi2vHUYBAYDKVKC9d2CniEM/95Bvsr
                B0VQMwPM/2mitoJQs9IGeXtedb8OCHIyysoBCkhAVlYBkFO054LLAgoKABKpBABy
                SkhF+/91ZPpbMfUlKogaFRUV6OfQ/ByIF6DK3t5Qu3Rxlup8XcHalIwmxwRA4y+U
                bCzo7Kz4nPjA/NrVh8Z1HzX9WjuHePv9fu+LpWGm+tuDkFlHqIt7Ang86pOfkC4A
                nPiL/Elek36P5dX4rvcqeztNeV0cJc5lf8o90Ak6eCxsWeN2jB3i/AKzXh/wL1bH
                ImzjlaV1ihovORjPVjKTX5NLx5A6UvMT7BAb+hvphUnIGCMvaQzdHpqtBFhI8pNX
                pv7xinTE+C8CaPKOWflwy7mxPYHuEVDbejz45SUc2Js3J2Azp+aywq1tqwge2qZ8
                SN6UP9izQlKfV5BbJ9Q41SrxlUX8CQ4fdKxAIlhFqjspQjA+eVKmAr5K6pkIWEZE
                lpCoW7roaMyiX0Ro36JKexrJBuosLLmEmre45adGcYcwz9BFxbRPY8RcDsH5V+6z
                ffB5Jx9CYGtmiYiLNLHn5xE5FuhMdjpctzXcu7jcanK5NS5RYLuE1DOmKX+rxI95
                jUo5vPygujjKv73nzCX7UW5h9dF3rhlq7x6PEsKb4A5o8YyvrQyFCt1WdoMRo+lM
                ukUITut4i40wlhHNKRfleUelTbmuWd0W2Da9ulHfgdFYIL/nTv0an1LeR+ExHzCR
                oFulMmsnezfKkOaleHnZ08g34hkuWzLn8RHFfEendTeZ6mKWLNbHdlk9+cyXcvbo
                nQ2TzqJWu89wxx05raZ+M30kns8XKao2kRL2fnfY/hmkSO6TwLG+LuRSqq5hV+UM
                ENapT9C5j2d9w1Ikbzx93LCobObUmdX2kdeycugKbYdUEhIf8lUhJydyApQrU+N4
                DWPKkQIHyknWZ/wF+tU8bG+l4t6CJ25af7rAmkjqmef6pP9Zu0Kq6VRCm+mc+Ek+
                XKx0diJfFxt6/32J5YPHcwodLEHO9/jbLZ+UMxyIEEOt+xUglE8b8opM9Px0jIW4
                LQkVRQ0ZINOSztlgV7siYWuduYb+7u75TbXwNOHIH6zfc5qmYSoznpjgaCSZscO/
                HkGK1tyYvTc3qQdld2/30Yubfr1U/IR8o6q9VYcYmFAPVovY0iuf4lL1yI1QmKUH
                Rdr4To53GfLviQDWCbuQ8h2pjb0Lpc3o0vIqrTjn7myTmkrut0TU59HWY1H6asc4
                mvF5sra30x+OKUli9OPDV+rdPtOVv0hQ8PZB8ekY3Fn8FjrdvYluhKwyVb8/Z3b8
                5NPtFjS/ZiBhbPAotoyNV+ujjxuDvfk+lp1qDtMX3bHwMMZTijlQy8fQz0Hcc4Gr
                XkBbi30st2AlMMHPngBzwRT5LYvZLTY2UJPVGwch/ABMBZcac3npOuKQOH3NCT4l
                nTW3Gupo/iJj08tpzMFFzuYly/5WHfwmlHD1zbfKGhk3P/e3M8BVThKW8fGiinar
                NG9WVimmErsQhv2ug0/lRAERN6pLSO+fSQysFFg8i5PPBTcUz0PvLMuEibSzOnu2
                WkFkJc4b6Y9XT6WkoWVSG4oQVLZ22lIXnUmWnLuuUGjf3Gn3OhT17irmq+GQ3qTv
                5o63bhQ4cHHqxzBrbvTuBgnccgex6WzX7V3Iq6Q5zDiMmfnQzx289qCiP4l+X/RP
                buV1Rhc7vCe7uYaUaYegYD1wffXkxaiiQ8GTx+xN+WVHRjUafpRzugJnQTnJn90H
                UHeLbOm0muv1yKVgI2ObQlHvWqyewdnBnsprzydV9vpQqlxB6cf749eROahOYG0+
                YBvNcTwikSySKya9nf+ZWnO25ZqJmJBQPa0Cb1Gwg+wPqQ3Y6ssZVqXGTNySx1x0
                fPhzqlTQPehyS2WNElF12QQZS0P6yfmdn0IATv8PKv+T+3/XBT4mF+D6rzGg1Mi/
                6wPg+7WhQcJ/0wEg9WsrjxT8+fE/q4t/M4zMrzgEKQwRZMqYq9kIEyPoV38V4Qi3
                k7b1wDhyyrn63jojfTth5CT9N/BDYmkQfzH/T5dA1liMxP0diEadWFaED/ej3ohY
                +cokjZNU/jclTnQs9yce8lx0lByAMqbvWJIPEk52FvsfpaWKLFAuTIXojNr7nCkq
                zrN+LWuKihpilnzDb2ab2VQzaQVaEYVFHH784ueYofYIoVntGN+7Y8pyefDZ7bU3
                gdUtHm9he0FaI87NcVl58NpNfr0v995JBIItD4IepPMM7OSny0NjtbgLlqIan5RR
                DgVOKflbVm6vXHbwD7zCFzRC8TTeT8HWJhvMb/dTSmklyO7G1rxX+zQc+8zTJJTU
                l9QPxViwQ59Ft60WsqfuCDvs3uw6l+ouacEz4TWYqnFD7ayHjUDPhkuia5NT4r0c
                UslKDf3OZrCY3K2F2ifaa7pTnecbvrInYZ1907nwNKWFT/t80TGZAqGlFTwMki5k
                ExmZgGhcSfLi8ugIKicnDOv7iEvruPyYikL7afGtoZ6AMmgElBdK7qsPPKecc3RJ
                aBDt4s2Htx1AtEZ/SNKoOHwJ13oY2w1SY/hQP/yyrrpMSJqn0J6WT5iFE1+8/jpK
                +SM9sCzdOMNf9bwmI9Lvsk9yhriDiI9w//Rmo9ZNBl2M491p6iW3tshSWqPx7+1g
                3ItjQp39SfNrzD73feilBX8L7Vy3wLfxADEo9EicUuz8x4/5rbDwhLmjMMKrsFuF
                s6YUQQr03KBJb5IG80NOELGa2KLDHpyZUZkpWNYIt+VtNHd91lt1rLgtXR0TAJos
                ITYfqUSNlHV7CbqzRRdX2rFPmM/D2xlatJ7fZ0jnEkp9HcLirPhKcjOA6fRjSWKK
                syle/2T0zviQI2+m0eneB5bfmyMI/bKENVOUlmbw7gz3d2Y6QrrqBOGL5gz+Cmx/
                5Qz6LUKPvSBcNRu3tMWQrvRHvXvzPaH9ivEIEwfizJ6ay9oWh6ls7eImJQbH6ObL
                hdbTNsTcWDZIOPomZ5eDWT+wO3E9KtgQbEu90VRL5d5vT0ysPEvbWT/69IcRzwvd
                Gqf888Kw9LCqjDz5hgV+pq9/eBqtSjIqOgf7mXVwllHPj69o9n9bUF9A7ttH4Mht
                +ILiyffyc4phpFN8npZWQfMBkT3D43qLtYzXJas63CqgrpL0p8K8Wn2ajSvpyHFr
                TKrohqSil6mOFWGJRQYEtxVVggEq1MWq6xVca8IIjim4p4l+ta+D425QNomZk8bY
                lNp27E2gUWZnzhKJ9VUjywjZb+QULurhNY4Q4ae6jtOwOhNVSNZGtpy68JVsb2WJ
                6jQxaYrsnAqYzSFuso1kvrcIvnVuXIJyexp2RM+SOGxmwXmVxVMiOsutdcbbwO4o
                RTktXSetM8F1QSNkWIcpXM6+rZZtTqvEnY8DYVkQuoy9SaEwYp9MTg65mkNnF2z9
                wfRajVybhi6kEafYCe2tRMl1ju4Jot3M7RevqCoDd8xOr6zFS3MP2FCLN331vKrl
                r1YR8v7R8Qp+M1sO3iuMmkU5AgN3kpQNauboa4y7olsApSEmTLCniN5FvApDz5/c
                jytYX3kgkC36Mfh5bL5Hh8P2c/bOml3lyDddiK7eOGux/zf3/w1QSwMECgAAAAAA
                eImqVlP8UWcCAAAAAgAAAAEAHAAxVVQJAAOUCFxklAhcZHV4CwABBPUBAAAEAAAA
                ADEKUEsBAh4DFAAAAAgAUpaqVu9jXOuNAAAAkAAAABQAGAAAAAAAAQAAAKSBAAAA
                AE1FVEEtSU5GL01BTklGRVNULk1GVVQFAAO8H1xkdXgLAAEE9QEAAAQAAAAAUEsB
                Ah4DFAAAAAgAUpaqVktg9JPyAAAAMgEAABIAGAAAAAAAAQAAAKSB2wAAAE1FVEEt
                SU5GL1NJR05FUi5TRlVUBQADvB9cZHV4CwABBPUBAAAEAAAAAFBLAQIeAxQAAAAI
                AFKWqlbb/AXeEAsAABgMAAATABgAAAAAAAAAAACkgRkCAABNRVRBLUlORi9TSUdO
                RVIuRFNBVVQFAAO8H1xkdXgLAAEE9QEAAAQAAAAAUEsBAh4DCgAAAAAAeImqVlP8
                UWcCAAAAAgAAAAEAGAAAAAAAAQAAAKSBdg0AADFVVAUAA5QIXGR1eAsAAQT1AQAA
                BAAAAABQSwUGAAAAAAQABABSAQAAsw0AAAAA
                """;
        Files.write(Path.of("x.jar"), Base64.getMimeDecoder().decode(jarFile));

        SecurityTools.jarsigner("-verify x.jar -verbose -debug")
                .shouldContain("jar verified.")
                .shouldContain("Signature algorithm: HSS/LMS,")
                .shouldHaveExitValue(0);

        Files.writeString(Paths.get("my.security"),
                "jdk.jar.disabledAlgorithms=HSS/LMS");

        SecurityTools.jarsigner(
                        "-J-Djava.security.properties=my.security " +
                        "-verify x.jar -verbose -debug")
                .shouldNotContain("jar verified.")
                .shouldContain("WARNING: The jar will be treated as unsigned")
                .shouldContain("Signature algorithm: HSS/LMS (disabled),")
                .shouldHaveExitValue(0);
    }
}
