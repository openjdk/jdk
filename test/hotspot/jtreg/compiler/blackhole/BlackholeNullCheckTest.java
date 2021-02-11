/*
 * Copyright (c) 2020, Red Hat, Inc. All rights reserved.
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

/**
 * @test id=c1
 * @build compiler.blackhole.BlackholeTarget
 *
 * @run main/othervm
 *      -Xmx1g
 *      -Xbatch -XX:TieredStopAtLevel=1
 *      -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure
 *      -XX:CompileCommand=blackhole,compiler/blackhole/BlackholeTarget.bh_*
 *      compiler.blackhole.BlackholeNullCheckTest
 */

/**
 * @test id=c2
 * @build compiler.blackhole.BlackholeTarget
 *
 * @run main/othervm
 *      -Xmx1g
 *      -Xbatch -XX:-TieredCompilation
 *      -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure
 *      -XX:CompileCommand=blackhole,compiler/blackhole/BlackholeTarget.bh_*
 *      compiler.blackhole.BlackholeNullCheckTest
 */

/**
 * @test id=c1-no-coops
 * @requires vm.bits == "64"
 * @build compiler.blackhole.BlackholeTarget
 *
 * @run main/othervm
 *      -Xmx1g -XX:-UseCompressedOops
 *      -Xbatch -XX:TieredStopAtLevel=1
 *      -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure
 *      -XX:CompileCommand=blackhole,compiler/blackhole/BlackholeTarget.bh_*
 *      compiler.blackhole.BlackholeNullCheckTest
 */

/**
 * @test id=c2-no-coops
 * @requires vm.bits == "64"
 * @build compiler.blackhole.BlackholeTarget
 *
 * @run main/othervm
 *      -Xmx1g -XX:-UseCompressedOops
 *      -Xbatch -XX:-TieredCompilation
 *      -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure
 *      -XX:CompileCommand=blackhole,compiler/blackhole/BlackholeTarget.bh_*
 *      compiler.blackhole.BlackholeNullCheckTest
 */

package compiler.blackhole;

public class BlackholeNullCheckTest {

    public static void main(String[] args) {
        BlackholeNullCheckTest t = new BlackholeNullCheckTest();
        runTries(t::test_local_sf);
        runTries(t::test_local_s);
        runTries(t::test_local);
        runTries(t::test_field_sf);
        runTries(t::test_field_s);
        runTries(t::test_field);
    }

    private static final int CYCLES = 1_000_000;
    private static final int TRIES = 10;

    public static void runTries(Runnable r) {
        for (int t = 0; t < TRIES; t++) {
            r.run();
        }
    }

    static final BlackholeTarget BH_SF_TARGET = null;
    static       BlackholeTarget BH_S_TARGET = null;
                 BlackholeTarget BH_TARGET = null;

    private void test_local_sf() {
        test_with(BH_SF_TARGET);
    }

    private void test_local_s() {
        test_with(BH_S_TARGET);
    }

    private void test_local() {
        test_with(BH_TARGET);
    }

    private void test_with(BlackholeTarget t) {
        try {
            t.bh_i_boolean_1(false);
            throw new IllegalStateException("Expected NPE");
        } catch (NullPointerException npe) {
        }

        try {
            t.call_for_null_check();
            throw new IllegalStateException("Expected NPE");
        } catch (NullPointerException npe) {
            // Expected
        }
    }

    private void test_field_sf() {
        try {
            BH_SF_TARGET.bh_i_boolean_1(false);
            throw new IllegalStateException("Expected NPE");
        } catch (NullPointerException npe) {
        }

        try {
            BH_SF_TARGET.call_for_null_check();
            throw new IllegalStateException("Expected NPE");
        } catch (NullPointerException npe) {
            // Expected
        }
    }

    private void test_field_s() {
        try {
            BH_S_TARGET.bh_i_boolean_1(false);
            throw new IllegalStateException("Expected NPE");
        } catch (NullPointerException npe) {
        }

        try {
            BH_S_TARGET.call_for_null_check();
            throw new IllegalStateException("Expected NPE");
        } catch (NullPointerException npe) {
            // Expected
        }
    }

    private void test_field() {
        try {
            BH_TARGET.bh_i_boolean_1(false);
            throw new IllegalStateException("Expected NPE");
        } catch (NullPointerException npe) {
        }

        try {
            BH_TARGET.call_for_null_check();
            throw new IllegalStateException("Expected NPE");
        } catch (NullPointerException npe) {
            // Expected
        }
    }

}
