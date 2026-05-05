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
 * @summary Test clone barriers work correctly
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
 * @summary Test clone barriers work correctly
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
 * @summary Test clone barriers work correctly
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
 * @summary Test clone barriers work correctly
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
 * @summary Test clone barriers work correctly
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
 * @test id=no-coops
 * @summary Test clone barriers work correctly
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
 * @summary Test clone barriers work correctly
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
 * @summary Test clone barriers work correctly
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
 * @summary Test clone barriers work correctly
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
 * @summary Test clone barriers work correctly
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
 * @summary Test clone barriers work correctly
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
  * @summary Test clone barriers work correctly
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
  * @summary Test clone barriers work correctly
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

public class TestClone {

    public static void main(String[] args) throws Exception {
        for (int i = 0; i < 10000; i++) {
            Object[] src = new Object[i];
            for (int c = 0; c < src.length; c++) {
                src[c] = new Object();
            }
            testWith(src);

            testWithObject(new SmallObject());
            testWithObject(new LargeObject());
        }
    }

    static void testWith(Object[] src) {
        Object[] dst = src.clone();
        int srcLen = src.length;
        int dstLen = dst.length;
        if (srcLen != dstLen) {
            throw new IllegalStateException("Lengths do not match: " + srcLen + " vs " + dstLen);
        }
        for (int c = 0; c < src.length; c++) {
            Object s = src[c];
            Object d = dst[c];
            if (s != d) {
                throw new IllegalStateException("Elements do not match at " + c + ": " + s + " vs " + d + ", len = " + srcLen);
            }
        }
    }

    static void testWithObject(SmallObject src) {
        SmallObject dst = src.clone();
        if (dst.x1 != src.x1 ||
            dst.x2 != src.x2 ||
            dst.x3 != src.x3 ||
            dst.x4 != src.x4) {
            throw new IllegalStateException("Contents do not match");
        }
    }

    static void testWithObject(LargeObject src) {
        LargeObject dst = src.clone();
        if (dst.x01 != src.x01 ||
            dst.x02 != src.x02 ||
            dst.x03 != src.x03 ||
            dst.x04 != src.x04 ||
            dst.x05 != src.x05 ||
            dst.x06 != src.x06 ||
            dst.x07 != src.x07 ||
            dst.x08 != src.x08 ||
            dst.x09 != src.x09 ||
            dst.x10 != src.x10 ||
            dst.x11 != src.x11 ||
            dst.x12 != src.x12 ||
            dst.x13 != src.x13 ||
            dst.x14 != src.x14 ||
            dst.x15 != src.x15 ||
            dst.x16 != src.x16) {
            throw new IllegalStateException("Contents do not match");
        }
    }

    static class SmallObject implements Cloneable {
        Object x1 = new Object();
        Object x2 = new Object();
        Object x3 = new Object();
        Object x4 = new Object();

        @Override
        public SmallObject clone() {
            try {
                return (SmallObject) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new AssertionError();
            }
        }
    }

    static class LargeObject implements Cloneable {
        Object x01 = new Object();
        Object x02 = new Object();
        Object x03 = new Object();
        Object x04 = new Object();
        Object x05 = new Object();
        Object x06 = new Object();
        Object x07 = new Object();
        Object x08 = new Object();
        Object x09 = new Object();
        Object x10 = new Object();
        Object x11 = new Object();
        Object x12 = new Object();
        Object x13 = new Object();
        Object x14 = new Object();
        Object x15 = new Object();
        Object x16 = new Object();

        @Override
        public LargeObject clone() {
            try {
                return (LargeObject) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new AssertionError();
            }
        }
    }
}
