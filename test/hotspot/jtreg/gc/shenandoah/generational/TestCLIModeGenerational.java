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

package gc.shenandoah.generational;

import jdk.test.whitebox.WhiteBox;

/*
 * @test TestCLIModeGenerational
 * @requires vm.gc.Shenandoah
 * @summary Test argument processing for -XX:+ShenandoahGCMode=generational.
 * @library /testlibrary /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:.
 *      -XX:+IgnoreUnrecognizedVMOptions
 *      -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *      -XX:+UnlockExperimentalVMOptions
 *      -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational
 *      gc.shenandoah.generational.TestCLIModeGenerational
 */

public class TestCLIModeGenerational {

  private static WhiteBox wb = WhiteBox.getWhiteBox();

  public static void main(String args[]) throws Exception {
    Boolean using_shenandoah = wb.getBooleanVMFlag("UseShenandoahGC");
    String gc_mode = wb.getStringVMFlag("ShenandoahGCMode");
    if (!using_shenandoah || !gc_mode.equals("generational"))
      throw new IllegalStateException("Command-line options not honored!");
  }
}

