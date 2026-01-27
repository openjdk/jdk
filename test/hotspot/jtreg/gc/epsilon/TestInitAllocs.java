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

package gc.epsilon;

/**
 * @test TestInitAllocs
 * @requires vm.gc.Epsilon
 * @summary Test that allocation path taken in early JVM phases works
 *
 * @run main/othervm -Xmx256m
 *                   -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC
 *                   gc.epsilon.TestInitAllocs
 *
 * @run main/othervm -Xmx256m
 *                   -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC
 *                   -XX:+UseTLAB
 *                   -XX:+UseCompressedOops
 *                   -XX:EpsilonMinHeapExpand=1024
 *                   -XX:EpsilonUpdateCountersStep=1
 *                   -XX:EpsilonPrintHeapSteps=1000000
 *                   gc.epsilon.TestInitAllocs
 *
 * @run main/othervm -Xmx256m
 *                   -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC
 *                   -XX:+UseTLAB
 *                   -XX:-UseCompressedOops
 *                   -XX:EpsilonMinHeapExpand=1024
 *                   -XX:EpsilonUpdateCountersStep=1
 *                   -XX:EpsilonPrintHeapSteps=1000000
 *                   gc.epsilon.TestInitAllocs
 *
 * @run main/othervm -Xmx256m
 *                   -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC
 *                   -XX:-UseTLAB
 *                   -XX:+UseCompressedOops
 *                   -XX:EpsilonMinHeapExpand=1024
 *                   -XX:EpsilonUpdateCountersStep=1
 *                   -XX:EpsilonPrintHeapSteps=1000000
 *                   gc.epsilon.TestInitAllocs
 *
 * @run main/othervm -Xmx256m
 *                   -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC
 *                   -XX:-UseTLAB
 *                   -XX:-UseCompressedOops
 *                   -XX:EpsilonMinHeapExpand=1024
 *                   -XX:EpsilonUpdateCountersStep=1
 *                   -XX:EpsilonPrintHeapSteps=1000000
 *                   gc.epsilon.TestInitAllocs
 */

public class TestInitAllocs {
  public static void main(String[] args) throws Exception {
    System.out.println("Hello World");
  }
}
