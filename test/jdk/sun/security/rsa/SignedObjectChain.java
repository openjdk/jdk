/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=MD2withRSA
 * @bug 8050374 8146293
 * @library /test/lib
 * @build jdk.test.lib.SigTestUtil
 * @compile ../../../java/security/SignedObject/Chain.java
 * @run main/othervm -DSigAlg=MD2withRSA SignedObjectChain
 * @summary Verify a chain of signed objects
 */

/*
 * @test id=MD5withRSA
 * @bug 8050374 8146293
 * @library /test/lib
 * @build jdk.test.lib.SigTestUtil
 * @compile ../../../java/security/SignedObject/Chain.java
 * @run main/othervm -DSigAlg=MD5withRSA SignedObjectChain
 * @summary Verify a chain of signed objects
 */

/*
 * @test id=SHA1withRSA
 * @bug 8050374 8146293
 * @library /test/lib
 * @build jdk.test.lib.SigTestUtil
 * @compile ../../../java/security/SignedObject/Chain.java
 * @run main/othervm -DSigAlg=SHA1withRSA SignedObjectChain
 * @summary Verify a chain of signed objects
 */

/*
 * @test id=SHA224withRSA
 * @bug 8050374 8146293
 * @library /test/lib
 * @build jdk.test.lib.SigTestUtil
 * @compile ../../../java/security/SignedObject/Chain.java
 * @run main/othervm -DSigAlg=SHA224withRSA SignedObjectChain
 * @summary Verify a chain of signed objects
 */

/*
 * @test id=SHA256withRSA
 * @bug 8050374 8146293
 * @library /test/lib
 * @build jdk.test.lib.SigTestUtil
 * @compile ../../../java/security/SignedObject/Chain.java
 * @run main/othervm -DSigAlg=SHA256withRSA SignedObjectChain
 * @summary Verify a chain of signed objects
 */

/*
 * @test id=SHA384withRSA
 * @bug 8050374 8146293
 * @library /test/lib
 * @build jdk.test.lib.SigTestUtil
 * @compile ../../../java/security/SignedObject/Chain.java
 * @run main/othervm -DSigAlg=SHA384withRSA SignedObjectChain
 * @summary Verify a chain of signed objects
 */

/*
 * @test id=SHA512withRSA
 * @bug 8050374 8146293
 * @library /test/lib
 * @build jdk.test.lib.SigTestUtil
 * @compile ../../../java/security/SignedObject/Chain.java
 * @run main/othervm -DSigAlg=SHA512withRSA SignedObjectChain
 * @summary Verify a chain of signed objects
 */

/*
 * @test id=SHA512_224withRSA
 * @bug 8050374 8146293
 * @library /test/lib
 * @build jdk.test.lib.SigTestUtil
 * @compile ../../../java/security/SignedObject/Chain.java
 * @run main/othervm -DSigAlg=SHA512_224withRSA SignedObjectChain
 * @summary Verify a chain of signed objects
 */

/*
 * @test id=SHA512_256withRSA
 * @bug 8050374 8146293
 * @library /test/lib
 * @build jdk.test.lib.SigTestUtil
 * @compile ../../../java/security/SignedObject/Chain.java
 * @run main/othervm -DSigAlg=SHA512_256withRSA SignedObjectChain
 * @summary Verify a chain of signed objects
 */
public class SignedObjectChain {
    private static class Test extends Chain.Test {

        public Test(Chain.SigAlg sigAlg) {
            super(sigAlg, Chain.KeyAlg.RSA, Chain.Provider.SunRsaSign);
        }
    }


    public static void main(String argv[]) {
        boolean resutl = Chain.runTest(new Test(Chain.SigAlg.valueOf(System.getProperty("SigAlg"))));

        if(resutl) {
            System.out.println("All tests passed");
        } else {
            throw new RuntimeException("Some tests failed");
        }
    }
}
