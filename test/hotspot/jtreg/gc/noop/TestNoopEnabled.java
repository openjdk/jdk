/*
 * Copyright (c) 2022, Red Hat, Inc. All rights reserved.
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

package gc.noop;

/**
 * @test TestNoopEnabled
 * @summary Basic sanity test for Noop
 * @library /test/lib
 *
 * @run main/othervm -Xmx256m
 *                   -XX:+UnlockExperimentalVMOptions -XX:+UseNoopGC
 *                   gc.noop.TestNoopEnabled
 */

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;

public class TestNoopEnabled {
  public static void main(String[] args) throws Exception {
    if (!isNoopEnabled()) {
      throw new IllegalStateException("Debug builds should have Noop enabled");
    }
  }

  public static boolean isNoopEnabled() {
    for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
      if (bean.getName().contains("Noop")) {
        return true;
      }
    }
    return false;
  }
}