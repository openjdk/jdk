/*
 * Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8046026: CompiledFunction.relinkComposableInvoker assert is being hit
 * JDK-8044770: crash with jdk9-dev/nashorn during global object initialization from MT test
 * JDK-8047770: NPE in deoptimizing recompilation in multithreaded
 *
 * @test
 * @run
 */

(function() {
var n = 1 << 25;
var ThreadLocalRandom = java.util.concurrent.ThreadLocalRandom;
var m = java.util.stream.IntStream.range(0, n)
 .parallel() // this is the essence of this test. We must trigger parallel execution
 .filter(function() {
     var tlr = ThreadLocalRandom.current();

     var x = tlr.nextDouble(-1.0, 1.0);
     var y = tlr.nextDouble(-1.0, 1.0);

     return x * x + y * y <= 1.0;
 })
 .count();
var pi = (4.0 * m) / n;
print(pi.toFixed(2));
})()
