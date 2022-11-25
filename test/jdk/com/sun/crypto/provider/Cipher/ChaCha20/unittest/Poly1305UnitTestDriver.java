/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8153029
 * @modules java.base/com.sun.crypto.provider
 * @run main java.base/com.sun.crypto.provider.Poly1305UnitTest
 * @summary Unit test for com.sun.crypto.provider.Poly1305.
 */

/*
 * @test
 * @key randomness
 * @modules java.base/com.sun.crypto.provider
 * @run main java.base/com.sun.crypto.provider.Poly1305IntrinsicFuzzTest
 * @summary Unit test for com.sun.crypto.provider.Poly1305.
 */

/*
 * @test
 * @modules java.base/com.sun.crypto.provider
 * @run main java.base/com.sun.crypto.provider.Poly1305KAT
 * @summary Unit test for com.sun.crypto.provider.Poly1305.
 */

/*
 * @test
 * @key randomness
 * @modules java.base/com.sun.crypto.provider
 * @summary Unit test for IntrinsicCandidate in com.sun.crypto.provider.Poly1305.
 * @run main/othervm -Xcomp -XX:-TieredCompilation -XX:+UnlockDiagnosticVMOptions -XX:+ForceUnreachable java.base/com.sun.crypto.provider.Poly1305IntrinsicFuzzTest
 */

/*
 * @test
 * @modules java.base/com.sun.crypto.provider
 * @summary Unit test for IntrinsicCandidate in com.sun.crypto.provider.Poly1305.
 * @run main/othervm -Xcomp -XX:-TieredCompilation -XX:+UnlockDiagnosticVMOptions -XX:+ForceUnreachable java.base/com.sun.crypto.provider.Poly1305KAT
 */

package com.sun.crypto.provider.Cipher.ChaCha20;

public class Poly1305UnitTestDriver {
    static public void main(String[] args) {
        System.out.println("Passed");
    }
}
