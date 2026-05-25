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

    private static final int ENTRIES = 1_000;
    private static final int ITERS = 50_000;
    private static final int ARRAY_MAX_SIZE = 128;
    private static final Random RAND = Utils.getRandomInstance();
    private static final SmallObject[] SMALL = new SmallObject[ENTRIES];
    private static final LargeObject[] LARGE = new LargeObject[ENTRIES];
    private static final Ref[][] ARRAY = new Ref[ENTRIES][];

    public static void main(String[] args) throws Exception {
        // Seed
        for (int i = 0; i < ENTRIES; i++) {
            SMALL[i] = new SmallObject(i);
            LARGE[i] = new LargeObject(i);
            ARRAY[i] = newArray(i);
        }

        // Random clone and verify
        for (int i = 0; i < ITERS; i++) {
            cloneAndVerify();
        }

        // Verify everything
        for (int i = 0; i < ENTRIES; i++) {
            verify(SMALL[i], i);
            verify(LARGE[i], i);
            verify(ARRAY[i], i);
        }
    }

    static void cloneAndVerify() {
        int r = RAND.nextInt(ENTRIES);
        SMALL[r] = SMALL[r].clone();
        LARGE[r] = LARGE[r].clone();
        ARRAY[r] = ARRAY[r].clone();

        // Verify will trigger LRB.
        // We don't want LRB to heal refs in the clone source or target,
        // so we verify a different random location.
        r = RAND.nextInt(ENTRIES);
        verify(SMALL[r], r);
        verify(LARGE[r], r);
        verify(ARRAY[r], r);
    }

    static Ref[] newArray(int id) {
        int size = id % ARRAY_MAX_SIZE;
        Ref[] arr = new Ref[size];
        for (int i = 0; i < size; i++) {
            arr[i] = new Ref(elementValue(id, i));
        }
        return arr;
    }

    static void verify(SmallObject src, int id) {
        assertEquals(elementValue(id, 0), src.x1.x);
        assertEquals(elementValue(id, 1), src.x2.x);
        assertEquals(elementValue(id, 2), src.x3.x);
        assertEquals(elementValue(id, 3), src.x4.x);
    }

    static void verify(LargeObject src, int id) {
        assertEquals(elementValue(id, 0), src.x01.x);
        assertEquals(elementValue(id, 1), src.x02.x);
        assertEquals(elementValue(id, 2), src.x03.x);
        assertEquals(elementValue(id, 3), src.x04.x);
        assertEquals(elementValue(id, 4), src.x05.x);
        assertEquals(elementValue(id, 5), src.x06.x);
        assertEquals(elementValue(id, 6), src.x07.x);
        assertEquals(elementValue(id, 7), src.x08.x);
        assertEquals(elementValue(id, 8), src.x09.x);
        assertEquals(elementValue(id, 9), src.x10.x);
        assertEquals(elementValue(id, 10), src.x11.x);
        assertEquals(elementValue(id, 11), src.x12.x);
        assertEquals(elementValue(id, 12), src.x13.x);
        assertEquals(elementValue(id, 13), src.x14.x);
        assertEquals(elementValue(id, 14), src.x15.x);
        assertEquals(elementValue(id, 15), src.x16.x);
    }

    static void assertEquals(int expected, int actual) {
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
            int expectedVal = elementValue(id, i);
            int val = src[i].x;
            if (val != expectedVal) {
                throw new IllegalStateException("Elements do not match at " + i + ": " + val + " vs " + expectedVal + ", len = " + srcLen);
            }
        }
    }

    static int elementValue(int id, int offset) {
        // Globally unique (per type).
        return ENTRIES * id + offset;
    }

    static class Ref {
        int x;

        Ref(int x) {
            this.x = x;
        }
    }

    abstract static class DefaultClone<T> implements Cloneable {
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

    static class SmallObject extends DefaultClone<SmallObject> {
        Ref x1, x2, x3, x4;

        SmallObject(int x) {
            x1 = new Ref(elementValue(x, 0));
            x2 = new Ref(elementValue(x, 1));
            x3 = new Ref(elementValue(x, 2));
            x4 = new Ref(elementValue(x, 3));
        }
    }

    static class LargeObject extends DefaultClone<LargeObject> {
        Ref x01, x02, x03, x04, x05, x06, x07, x08;
        Ref x09, x10, x11, x12, x13, x14, x15, x16;

        LargeObject(int x) {
            x01 = new Ref(elementValue(x, 0));
            x02 = new Ref(elementValue(x, 1));
            x03 = new Ref(elementValue(x, 2));
            x04 = new Ref(elementValue(x, 3));
            x05 = new Ref(elementValue(x, 4));
            x06 = new Ref(elementValue(x, 5));
            x07 = new Ref(elementValue(x, 6));
            x08 = new Ref(elementValue(x, 7));
            x09 = new Ref(elementValue(x, 8));
            x10 = new Ref(elementValue(x, 9));
            x11 = new Ref(elementValue(x, 10));
            x12 = new Ref(elementValue(x, 11));
            x13 = new Ref(elementValue(x, 12));
            x14 = new Ref(elementValue(x, 13));
            x15 = new Ref(elementValue(x, 14));
            x16 = new Ref(elementValue(x, 15));
        }
    }
}
