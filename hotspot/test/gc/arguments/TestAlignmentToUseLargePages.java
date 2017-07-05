/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @test TestAlignmentToUseLargePages
 * @summary All parallel GC variants may use large pages without the requirement that the
 * heap alignment is large page aligned. Other collectors also need to start up with odd sized heaps.
 * @bug 8024396
 * @key gc
 * @key regression
 * @requires vm.gc=="null"
 * @run main/othervm -Xms71M -Xmx91M -XX:+UseParallelGC -XX:-UseParallelOldGC -XX:+UseLargePages TestAlignmentToUseLargePages
 * @run main/othervm -Xms71M -Xmx91M -XX:+UseParallelGC -XX:-UseParallelOldGC -XX:-UseLargePages TestAlignmentToUseLargePages
 * @run main/othervm -Xms71M -Xmx91M -XX:+UseParallelGC -XX:+UseParallelOldGC -XX:+UseLargePages TestAlignmentToUseLargePages
 * @run main/othervm -Xms71M -Xmx91M -XX:+UseParallelGC -XX:+UseParallelOldGC -XX:-UseLargePages TestAlignmentToUseLargePages
 * @run main/othervm -Xms71M -Xmx91M -XX:+UseSerialGC -XX:+UseLargePages TestAlignmentToUseLargePages
 * @run main/othervm -Xms71M -Xmx91M -XX:+UseSerialGC -XX:-UseLargePages TestAlignmentToUseLargePages
 * @run main/othervm -Xms71M -Xmx91M -XX:+UseConcMarkSweepGC -XX:+UseLargePages TestAlignmentToUseLargePages
 * @run main/othervm -Xms71M -Xmx91M -XX:+UseConcMarkSweepGC -XX:-UseLargePages TestAlignmentToUseLargePages
 * @run main/othervm -Xms71M -Xmx91M -XX:+UseG1GC -XX:+UseLargePages TestAlignmentToUseLargePages
 * @run main/othervm -Xms71M -Xmx91M -XX:+UseG1GC -XX:-UseLargePages TestAlignmentToUseLargePages
 */

public class TestAlignmentToUseLargePages {
  public static void main(String args[]) throws Exception {
    // nothing to do
  }
}
