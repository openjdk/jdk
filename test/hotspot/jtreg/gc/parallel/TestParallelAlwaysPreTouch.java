/*
 * Copyright (c) Ampere Computing and/or its affiliates. All rights reserved.
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
 * @test TestParallelAlwaysPreTouch
 * @bug 8315923
 * @requires vm.gc.Parallel & os.family == "linux" & os.maxMemory > 30G
 * @summary Check if parallel pretouch performs normally with and without THP.
 * @comment The test is not ParallelGC-specific, but a multi-threaded GC is
 *          required. So ParallelGC is used here.
 *
 * @run main/othervm -XX:-UseTransparentHugePages
 *                   -XX:+UseParallelGC -XX:ParallelGCThreads=${os.processors}
 *                   -Xlog:startuptime,pagesize,gc+heap=debug
 *                   -Xms24G -Xmx24G -XX:+AlwaysPreTouch
 *                   gc.parallel.TestParallelAlwaysPreTouch
 *
 * @run main/othervm -XX:+UseTransparentHugePages
 *                   -XX:+UseParallelGC -XX:ParallelGCThreads=${os.processors}
 *                   -Xlog:startuptime,pagesize,gc+heap=debug
 *                   -Xms24G -Xmx24G -XX:+AlwaysPreTouch
 *                   gc.parallel.TestParallelAlwaysPreTouch
 */

package gc.parallel;

public class TestParallelAlwaysPreTouch {
  public static void main(String[] args) throws Exception {
    // everything should happen before entry point
  }
}
