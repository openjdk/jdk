/*
 * Copyright (c) 2019, Red Hat, Inc. All rights reserved.
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
 * @test id=default
 * @key randomness
 * @summary Test clone barriers work correctly
 * @library /test/lib
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:+UseShenandoahGC
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:+UseShenandoahGC
 *                   -Xint
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:+UseShenandoahGC
 *                   -XX:-TieredCompilation
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:+UseShenandoahGC
 *                   -XX:TieredStopAtLevel=1
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:+UseShenandoahGC
 *                   -XX:TieredStopAtLevel=4
 *                   TestClone
 */

/*
 * @test id=default-verify
 * @key randomness
 * @summary Test clone barriers work correctly
 * @library /test/lib
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:+UseShenandoahGC
 *                   -XX:+ShenandoahVerify
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:+UseShenandoahGC
 *                   -XX:+ShenandoahVerify
 *                   -Xint
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:+UseShenandoahGC
 *                   -XX:+ShenandoahVerify
 *                   -XX:-TieredCompilation
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:+UseShenandoahGC
 *                   -XX:+ShenandoahVerify
 *                   -XX:TieredStopAtLevel=1
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:+UseShenandoahGC
 *                   -XX:+ShenandoahVerify
 *                   -XX:TieredStopAtLevel=4
 *                   TestClone
 */

/*
 * @test id=passive
 * @key randomness
 * @summary Test clone barriers work correctly
 * @library /test/lib
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:+UseShenandoahGC
 *                   -XX:ShenandoahGCMode=passive -XX:+ShenandoahCloneBarrier
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:+UseShenandoahGC
 *                   -XX:ShenandoahGCMode=passive -XX:+ShenandoahCloneBarrier
 *                   -Xint
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:+UseShenandoahGC
 *                   -XX:ShenandoahGCMode=passive -XX:+ShenandoahCloneBarrier
 *                   -XX:-TieredCompilation
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:+UseShenandoahGC
 *                   -XX:ShenandoahGCMode=passive -XX:+ShenandoahCloneBarrier
 *                   -XX:TieredStopAtLevel=1
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:+UseShenandoahGC
 *                   -XX:ShenandoahGCMode=passive -XX:+ShenandoahCloneBarrier
 *                   -XX:TieredStopAtLevel=4
 *                   TestClone
 */

/*
 * @test id=passive-verify
 * @key randomness
 * @summary Test clone barriers work correctly
 * @library /test/lib
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:+UseShenandoahGC
 *                   -XX:ShenandoahGCMode=passive -XX:+ShenandoahCloneBarrier
 *                   -XX:+ShenandoahVerify
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:+UseShenandoahGC
 *                   -XX:ShenandoahGCMode=passive -XX:+ShenandoahCloneBarrier
 *                   -XX:+ShenandoahVerify
 *                   -Xint
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:+UseShenandoahGC
 *                   -XX:ShenandoahGCMode=passive -XX:+ShenandoahCloneBarrier
 *                   -XX:+ShenandoahVerify
 *                   -XX:-TieredCompilation
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:+UseShenandoahGC
 *                   -XX:ShenandoahGCMode=passive -XX:+ShenandoahCloneBarrier
 *                   -XX:+ShenandoahVerify
 *                   -XX:TieredStopAtLevel=1
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:+UseShenandoahGC
 *                   -XX:ShenandoahGCMode=passive -XX:+ShenandoahCloneBarrier
 *                   -XX:+ShenandoahVerify
 *                   -XX:TieredStopAtLevel=4
 *                   TestClone
 */

/*
 * @test id=aggressive
 * @key randomness
 * @summary Test clone barriers work correctly
 * @library /test/lib
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=aggressive
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=aggressive
 *                   -Xint
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=aggressive
 *                   -XX:-TieredCompilation
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=aggressive
 *                   -XX:TieredStopAtLevel=1
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=aggressive
 *                   -XX:TieredStopAtLevel=4
 *                   TestClone
 */

/*
 * @test id=aggressive-verify
 * @key randomness
 * @summary Test clone barriers work correctly
 * @library /test/lib
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=aggressive
 *                   -XX:+ShenandoahVerify
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=aggressive
 *                   -XX:+ShenandoahVerify
 *                   -Xint
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=aggressive
 *                   -XX:+ShenandoahVerify
 *                   -XX:-TieredCompilation
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=aggressive
 *                   -XX:+ShenandoahVerify
 *                   -XX:TieredStopAtLevel=1
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=aggressive
 *                   -XX:+ShenandoahVerify
 *                   -XX:TieredStopAtLevel=4
 *                   TestClone
 */

/*
 * @test id=no-coops
 * @key randomness
 * @summary Test clone barriers work correctly
 * @library /test/lib
 * @requires vm.gc.Shenandoah
 * @requires vm.bits == "64"
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:-UseCompressedOops
 *                   -XX:+UseShenandoahGC
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:-UseCompressedOops
 *                   -XX:+UseShenandoahGC
 *                   -Xint
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:-UseCompressedOops
 *                   -XX:+UseShenandoahGC
 *                   -XX:-TieredCompilation
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:-UseCompressedOops
 *                   -XX:+UseShenandoahGC
 *                   -XX:TieredStopAtLevel=1
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:-UseCompressedOops
 *                   -XX:+UseShenandoahGC
 *                   -XX:TieredStopAtLevel=4
 *                   TestClone
 */

/*
 * @test id=no-coops-verify
 * @key randomness
 * @summary Test clone barriers work correctly
 * @library /test/lib
 * @requires vm.gc.Shenandoah
 * @requires vm.bits == "64"
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:-UseCompressedOops
 *                   -XX:+UseShenandoahGC
 *                   -XX:+ShenandoahVerify
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:-UseCompressedOops
 *                   -XX:+UseShenandoahGC
 *                   -XX:+ShenandoahVerify
 *                   -Xint
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:-UseCompressedOops
 *                   -XX:+UseShenandoahGC
 *                   -XX:+ShenandoahVerify
 *                   -XX:-TieredCompilation
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:-UseCompressedOops
 *                   -XX:+UseShenandoahGC
 *                   -XX:+ShenandoahVerify
 *                   -XX:TieredStopAtLevel=1
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:-UseCompressedOops
 *                   -XX:+UseShenandoahGC
 *                   -XX:+ShenandoahVerify
 *                   -XX:TieredStopAtLevel=4
 *                   TestClone
 */

/*
 * @test id=no-coops-aggressive
 * @key randomness
 * @summary Test clone barriers work correctly
 * @library /test/lib
 * @requires vm.gc.Shenandoah
 * @requires vm.bits == "64"
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:-UseCompressedOops
 *                   -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=aggressive
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:-UseCompressedOops
 *                   -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=aggressive
 *                   -Xint
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:-UseCompressedOops
 *                   -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=aggressive
 *                   -XX:-TieredCompilation
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:-UseCompressedOops
 *                   -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=aggressive
 *                   -XX:TieredStopAtLevel=1
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:-UseCompressedOops
 *                   -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=aggressive
 *                   -XX:TieredStopAtLevel=4
 *                   TestClone
 */

/*
 * @test id=generational
 * @key randomness
 * @summary Test clone barriers work correctly
 * @library /test/lib
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational
 *                   -Xint
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational
 *                   -XX:-TieredCompilation
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational
 *                   -XX:TieredStopAtLevel=1
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational
 *                   -XX:TieredStopAtLevel=4
 *                   TestClone
 */

/*
 * @test id=generational-small-card-size
 * @key randomness
 * @summary Test clone barriers work correctly
 * @library /test/lib
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational -XX:GCCardSizeInBytes=128
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational -XX:GCCardSizeInBytes=128
 *                   -Xint
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational -XX:GCCardSizeInBytes=128
 *                   -XX:-TieredCompilation
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational -XX:GCCardSizeInBytes=128
 *                   -XX:TieredStopAtLevel=1
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational -XX:GCCardSizeInBytes=128
 *                   -XX:TieredStopAtLevel=4
 *                   TestClone
 */

/*
 * @test id=generational-verify
 * @key randomness
 * @summary Test clone barriers work correctly
 * @library /test/lib
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational
 *                   -XX:+ShenandoahVerify
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational
 *                   -XX:+ShenandoahVerify
 *                   -Xint
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational
 *                   -XX:+ShenandoahVerify
 *                   -XX:-TieredCompilation
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational
 *                   -XX:+ShenandoahVerify
 *                   -XX:TieredStopAtLevel=1
 *                   TestClone
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
 *                   -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational
 *                   -XX:+ShenandoahVerify
 *                   -XX:TieredStopAtLevel=4
 *                   TestClone
 */

 /*
  * @test id=generational-no-coops
  * @key randomness
  * @summary Test clone barriers work correctly
  * @library /test/lib
  * @requires vm.gc.Shenandoah
  * @requires vm.bits == "64"
  *
  * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
  *                   -XX:-UseCompressedOops
  *                   -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational
  *                   TestClone
  * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
  *                   -XX:-UseCompressedOops
  *                   -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational
  *                   -Xint
  *                   TestClone
  * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
  *                   -XX:-UseCompressedOops
  *                   -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational
  *                   -XX:-TieredCompilation
  *                   TestClone
  * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
  *                   -XX:-UseCompressedOops
  *                   -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational
  *                   -XX:TieredStopAtLevel=1
  *                   TestClone
  * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
  *                   -XX:-UseCompressedOops
  *                   -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational
  *                   -XX:TieredStopAtLevel=4
  *                   TestClone
  */

 /*
  * @test id=generational-no-coops-verify
  * @key randomness
  * @summary Test clone barriers work correctly
  * @library /test/lib
  * @requires vm.gc.Shenandoah
  * @requires vm.bits == "64"
  *
  * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
  *                   -XX:-UseCompressedOops
  *                   -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational
  *                   -XX:+ShenandoahVerify
  *                   TestClone
  * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
  *                   -XX:-UseCompressedOops
  *                   -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational
  *                   -XX:+ShenandoahVerify
  *                   -Xint
  *                   TestClone
  * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
  *                   -XX:-UseCompressedOops
  *                   -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational
  *                   -XX:+ShenandoahVerify
  *                   -XX:-TieredCompilation
  *                   TestClone
  * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
  *                   -XX:-UseCompressedOops
  *                   -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational
  *                   -XX:+ShenandoahVerify
  *                   -XX:TieredStopAtLevel=1
  *                   TestClone
  * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xms1g -Xmx1g
  *                   -XX:-UseCompressedOops
  *                   -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational
  *                   -XX:+ShenandoahVerify
  *                   -XX:TieredStopAtLevel=4
  *                   TestClone
  */

import java.util.Random;
import jdk.test.lib.Utils;

public class TestClone {

    private static final int ENTRIES = 10_000;
    private static final int ITERS = 1_000_000;
    private static final int ARRAY_MAX_SIZE = 128;

    public static void main(String[] args) throws Exception {
        SmallObject[] small = new SmallObject[ENTRIES];
        LargeObject[] large = new LargeObject[ENTRIES];
        Ref[][] array = new Ref[ENTRIES][];

        for (int i = 0; i < ENTRIES; i++) {
            small[i] = new SmallObject(i);
            large[i] = new LargeObject(i);
            array[i] = newArray(i);
        }

        Random rand = Utils.getRandomInstance();
        for (int i = 0; i < ITERS; i++) {
            int r = rand.nextInt(ENTRIES);
            small[r] = (SmallObject) small[r].clone();
            large[r] = (LargeObject) large[r].clone();
            array[r] = array[r].clone();

            r = rand.nextInt(ENTRIES);
            verify(small[r], r);
            verify(large[r], r);
            verify(array[r], r);
        }
    }

    static Ref[] newArray(int id) {
        int size = id % ARRAY_MAX_SIZE;
        Ref[] arr = new Ref[size];
        for (int i = 0; i < size; i++) {
          arr[i] = new Ref(id * 1_000 + i);
        }
        return arr;
    }

    static void verify(SmallObject src, int id) {
        assertEquals(src.x1.x, id++);
        assertEquals(src.x2.x, id++);
        assertEquals(src.x3.x, id++);
        assertEquals(src.x4.x, id++);
    }

    static void verify(LargeObject src, int id) {
        assertEquals(src.x01.x, id++);
        assertEquals(src.x02.x, id++);
        assertEquals(src.x03.x, id++);
        assertEquals(src.x04.x, id++);
        assertEquals(src.x05.x, id++);
        assertEquals(src.x06.x, id++);
        assertEquals(src.x07.x, id++);
        assertEquals(src.x08.x, id++);
        assertEquals(src.x09.x, id++);
        assertEquals(src.x10.x, id++);
        assertEquals(src.x11.x, id++);
        assertEquals(src.x12.x, id++);
        assertEquals(src.x13.x, id++);
        assertEquals(src.x14.x, id++);
        assertEquals(src.x15.x, id++);
        assertEquals(src.x16.x, id++);
    }

    static void assertEquals(int actual, int expected) {
        if (actual != expected) {
            throw new IllegalStateException("Mismatch: expected=" + expected + ", actual=" + actual);
        }
    }

    static void verify(Ref[] src, int id) {
        int expectedLen = id % ARRAY_MAX_SIZE;
        int srcLen = src.length;
        if (srcLen != expectedLen) {
            throw new IllegalStateException("Lengths do not match: " + srcLen + " vs " + expectedLen);
        }
        for (int i = 0; i < src.length; i++) {
            int expectedVal = id * 1_000 + i;
            int val = src[i].x;
            if (val != expectedVal) {
                throw new IllegalStateException("Elements do not match at " + i + ": " + val + " vs " + expectedVal + ", len = " + srcLen);
            }
        }
    }

    static class Ref {
        int x;

        Ref(int x) {
            this.x = x;
        }
    }

    static abstract class DefaultClone<T> implements Cloneable {
        @Override
        @SuppressWarnings("unchecked")
        public T clone() {
            try {
                return (T) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new AssertionError(e);
            }
        }
    }

    static class SmallObject extends DefaultClone {
        Ref x1, x2, x3, x4;

        SmallObject(int x) {
            x1 = new Ref(x++);
            x2 = new Ref(x++);
            x3 = new Ref(x++);
            x4 = new Ref(x++);
        }
    }

    static class LargeObject extends DefaultClone {
        Ref x01, x02, x03, x04, x05, x06, x07, x08;
        Ref x09, x10, x11, x12, x13, x14, x15, x16;

        LargeObject(int x) {
            x01 = new Ref(x++);
            x02 = new Ref(x++);
            x03 = new Ref(x++);
            x04 = new Ref(x++);
            x05 = new Ref(x++);
            x06 = new Ref(x++);
            x07 = new Ref(x++);
            x08 = new Ref(x++);
            x09 = new Ref(x++);
            x10 = new Ref(x++);
            x11 = new Ref(x++);
            x12 = new Ref(x++);
            x13 = new Ref(x++);
            x14 = new Ref(x++);
            x15 = new Ref(x++);
            x16 = new Ref(x++);
        }
    }
}
