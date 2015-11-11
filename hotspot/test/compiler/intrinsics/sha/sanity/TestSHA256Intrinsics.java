/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

import sha.predicate.IntrinsicPredicates;

/**
 * @test
 * @bug 8035968
 * @summary Verify that SHA-256 intrinsic is actually used.
 * @library /testlibrary /test/lib /compiler/testlibrary ../
 * @modules java.base/sun.misc
 *          java.management
 * @build TestSHA intrinsics.Verifier TestSHA256Intrinsics
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -Xbatch -XX:CompileThreshold=500
 *                   -XX:Tier4InvocationThreshold=500
 *                   -XX:+LogCompilation -XX:LogFile=positive_224.log
 *                   -XX:CompileOnly=sun/security/provider/DigestBase
 *                   -XX:CompileOnly=sun/security/provider/SHA
 *                   -XX:+UseSHA256Intrinsics
 *                   -Dalgorithm=SHA-224 TestSHA256Intrinsics
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -Xbatch -XX:CompileThreshold=500
 *                   -XX:Tier4InvocationThreshold=500
 *                   -XX:+LogCompilation -XX:LogFile=negative_224.log
 *                   -XX:CompileOnly=sun/security/provider/DigestBase
 *                   -XX:CompileOnly=sun/security/provider/SHA
 *                   -XX:-UseSHA256Intrinsics
 *                   -Dalgorithm=SHA-224 TestSHA256Intrinsics
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -Xbatch -XX:CompileThreshold=500
 *                   -XX:Tier4InvocationThreshold=500
 *                   -XX:+LogCompilation -XX:LogFile=positive_256.log
 *                   -XX:CompileOnly=sun/security/provider/DigestBase
 *                   -XX:CompileOnly=sun/security/provider/SHA
 *                   -XX:+UseSHA256Intrinsics
 *                   -Dalgorithm=SHA-256 TestSHA256Intrinsics
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -Xbatch -XX:CompileThreshold=500
 *                   -XX:Tier4InvocationThreshold=500
 *                   -XX:+LogCompilation -XX:LogFile=negative_256.log
 *                   -XX:CompileOnly=sun/security/provider/DigestBase
 *                   -XX:CompileOnly=sun/security/provider/SHA
 *                   -XX:-UseSHA256Intrinsics
 *                   -Dalgorithm=SHA-256 TestSHA256Intrinsics
 * @run main/othervm -DverificationStrategy=VERIFY_INTRINSIC_USAGE
 *                    intrinsics.Verifier positive_224.log positive_256.log
 *                    negative_224.log negative_256.log
 */
public class TestSHA256Intrinsics {
    public static void main(String args[]) throws Exception {
        new SHASanityTestBase(IntrinsicPredicates.SHA256_INTRINSICS_AVAILABLE,
                SHASanityTestBase.SHA256_INTRINSIC_ID).test();
    }
}
