/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.jfr.event.gc.detailed;

/**
 * @test
 * @key randomness
 * @summary Test allocates humongous objects with G1 GC. Objects
 * considered humongous when it allocates equals or more than one region. As
 * we're passing the size of byte array we need adjust it that entire structure
 * fits exactly to one region, if not - G1 will allocate another almost empty
 * region as a continue of humongous. Thus we will exhaust memory very fast and
 * test will fail with OOME.
 * @requires vm.hasJFR
 * @requires vm.gc == "null" | vm.gc == "G1"
 * @library /test/lib /test/jdk
 * @run main/othervm -XX:+UseG1GC -XX:MaxNewSize=5m -Xmx256m -XX:G1HeapRegionSize=1048576 jdk.jfr.event.gc.detailed.TestStressBigAllocationGCEventsWithG1 1048544
 */
public class TestStressBigAllocationGCEventsWithG1 {

    public static void main(String[] args) throws Exception {
        new StressAllocationGCEvents().run(args);
    }
}
