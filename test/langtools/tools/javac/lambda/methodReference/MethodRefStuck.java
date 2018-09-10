/*
 * Copyright (c) 2018, Google LLC. All rights reserved.
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
 * @test
 * @bug 8210483
 * @summary AssertionError in DeferredAttr at setOverloadKind caused by JDK-8203679
 * @compile/fail/ref=MethodRefStuck.out -XDrawDiagnostics MethodRefStuck.java
 */

import java.util.Optional;
import java.util.stream.Stream;

public abstract class MethodRefStuck {
  public static void main(Stream<String> xs, Optional<String> x) {
    xs.map(
        c -> {
          return new I(x.map(c::equals));
        });
  }

  static class I {
    I(boolean i) {}
  }
}
