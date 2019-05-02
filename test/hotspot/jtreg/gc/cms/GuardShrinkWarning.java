/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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

package gc.cms;

/**
 * @test GuardShrinkWarning
 * @key gc regression
 * @summary Remove warning about CMS generation shrinking.
 * @bug 8012111
 * @requires vm.gc.ConcMarkSweep & !vm.graal.enabled
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run main/othervm gc.cms.GuardShrinkWarning
 * @author jon.masamitsu@oracle.com
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class GuardShrinkWarning {
  public static void main(String args[]) throws Exception {

    ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
      "-showversion",
      "-XX:+UseConcMarkSweepGC",
      "-XX:+ExplicitGCInvokesConcurrent",
      SystemGCCaller.class.getName()
      );

    OutputAnalyzer output = new OutputAnalyzer(pb.start());

    output.shouldNotContain("Shrinking of CMS not yet implemented");

    output.shouldNotContain("error");

    output.shouldHaveExitValue(0);
  }
  static class SystemGCCaller {
    public static void main(String [] args) {
      System.gc();
    }
  }
}
