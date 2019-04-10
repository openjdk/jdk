/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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

package gc.survivorAlignment;

/**
 * @test gc.survivorAlignment.TestPromotionLABLargeSurvivorAlignment
 * @bug 8060463
 * @summary Verify that objects promoted from eden space to survivor space
 *          with large values for SurvivorAlignmentInBytes succeed.
 * @requires vm.opt.ExplicitGCInvokesConcurrent != true
 * @run main/othervm -Xmx128m
 *                   -XX:+UnlockExperimentalVMOptions
 *                   -XX:SurvivorAlignmentInBytes=8 -XX:SurvivorRatio=1
 *                   -XX:-ExplicitGCInvokesConcurrent -XX:-ResizePLAB
 *                   gc.survivorAlignment.TestPromotionLABLargeSurvivorAlignment
 * @run main/othervm -Xmx128m
 *                   -XX:+UnlockExperimentalVMOptions
 *                   -XX:SurvivorAlignmentInBytes=16 -XX:SurvivorRatio=1
 *                   -XX:-ExplicitGCInvokesConcurrent -XX:-ResizePLAB
 *                   gc.survivorAlignment.TestPromotionLABLargeSurvivorAlignment
 * @run main/othervm -Xmx128m
 *                   -XX:+UnlockExperimentalVMOptions
 *                   -XX:SurvivorAlignmentInBytes=512 -XX:SurvivorRatio=1
 *                   -XX:-ExplicitGCInvokesConcurrent -XX:-ResizePLAB
 *                   gc.survivorAlignment.TestPromotionLABLargeSurvivorAlignment
 * @run main/othervm -Xmx128m
 *                   -XX:+UnlockExperimentalVMOptions
 *                   -XX:SurvivorAlignmentInBytes=1k -XX:SurvivorRatio=1
 *                   -XX:-ExplicitGCInvokesConcurrent -XX:-ResizePLAB
 *                   gc.survivorAlignment.TestPromotionLABLargeSurvivorAlignment
 * @run main/othervm -Xmx128m
 *                   -XX:+UnlockExperimentalVMOptions
 *                   -XX:SurvivorAlignmentInBytes=4k -XX:SurvivorRatio=1
 *                   -XX:-ExplicitGCInvokesConcurrent -XX:-ResizePLAB
 *                   gc.survivorAlignment.TestPromotionLABLargeSurvivorAlignment
 * @run main/othervm -Xmx128m
 *                   -XX:+UnlockExperimentalVMOptions
 *                   -XX:SurvivorAlignmentInBytes=16k -XX:SurvivorRatio=1
 *                   -XX:-ExplicitGCInvokesConcurrent -XX:-ResizePLAB
 *                   gc.survivorAlignment.TestPromotionLABLargeSurvivorAlignment
 */
public class TestPromotionLABLargeSurvivorAlignment {
    public static void main(String args[]) {
        Object garbage[] = new Object[1000000];
        for (int i = 0; i < garbage.length; i++) {
            garbage[i] = new byte[0];
        }
        for (int i = 0; i < 2; i++) {
            System.gc();
        }
    }
}

