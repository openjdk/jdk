/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package gc.epsilon;

/**
 * @test TestEnoughUnusedSpace
 * @requires vm.gc.Epsilon
 * @summary Epsilon should allocates object successfully if it has enough space.
 * @run main/othervm -Xms64M -Xmx128M -XX:+UnlockExperimentalVMOptions
 *                   -XX:+UseEpsilonGC gc.epsilon.TestEnoughUnusedSpace
 */

public class TestEnoughUnusedSpace {
    static volatile Object arr;

    public static void main(String[] args) {
        // Create an array about 90M. It should be created successfully
        // instead of throwing OOME, because 90M is smaller than 128M.
        arr = new byte[90 * 1024 * 1024];
    }
}
