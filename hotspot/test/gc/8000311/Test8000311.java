/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @test Test8000311
 * @key gc
 * @bug 8000311
 * @summary G1: ParallelGCThreads==0 broken
 * @run main/othervm -XX:+UseG1GC -XX:ParallelGCThreads=0 -XX:+ResizePLAB -XX:+ExplicitGCInvokesConcurrent Test8000311
 * @author filipp.zhinkin@oracle.com
 */

import java.util.*;

public class Test8000311 {
  public static void main(String args[]) {
    for(int i = 0; i<100; i++) {
      byte[] garbage = new byte[1000];
      System.gc();
    }
  }
}
