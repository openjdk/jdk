/*
 * Copyright (c) 2022, Red Hat, Inc. All rights reserved.
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

package gc.noop;

/**
 * @test TestAlignment
 * @summary Check Noop runs fine with standard alignments
 *
 * @run main/othervm -Xmx64m -XX:+UseTLAB
 *                   -XX:+UnlockExperimentalVMOptions -XX:+UseNoopGC
 *                   gc.noop.TestAlignment
 *
 * @run main/othervm -Xmx64m -XX:-UseTLAB
 *                   -XX:+UnlockExperimentalVMOptions -XX:+UseNoopGC
 *                   gc.noop.TestAlignment
 */

/**
 * @test TestAlignment
 * @requires vm.bits == "64"
 * @summary Check Noop TLAB options with unusual object alignment
 * @run main/othervm -Xmx64m -XX:+UseTLAB
 *                   -XX:+UnlockExperimentalVMOptions -XX:+UseNoopGC
 *                   -XX:ObjectAlignmentInBytes=16
 *                   gc.noop.TestAlignment
 *
 * @run main/othervm -Xmx64m -XX:-UseTLAB
 *                   -XX:+UnlockExperimentalVMOptions -XX:+UseNoopGC
 *                   -XX:ObjectAlignmentInBytes=16
 *                   gc.noop.TestAlignment
 */

public class TestAlignment {
    static Object sink;

    public static void main(String[] args) throws Exception {
        for (int c = 0; c < 1000; c++) {
            sink = new byte[c];
        }
    }
}