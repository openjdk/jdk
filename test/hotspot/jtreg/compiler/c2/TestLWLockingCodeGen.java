/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 * @summary Tests correct code generation of lightweight locking when using -XX:+ShowMessageBoxOnError; times-out on failure
 * @bug 8329726
 * @run main/othervm -XX:+ShowMessageBoxOnError -Xcomp -XX:-TieredCompilation -XX:CompileOnly=TestLWLockingCodeGen::sync TestLWLockingCodeGen
 */
public class TestLWLockingCodeGen {
    private static int val = 0;
    public static void main(String[] args) {
        sync();
    }
    private static synchronized void sync() {
        val = val + (int)(Math.random() * 42);
    }
}
