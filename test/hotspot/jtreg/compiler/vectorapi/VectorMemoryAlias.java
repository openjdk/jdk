/*
 *  Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
 *  Copyright (c) 2021, Rado Smogura. All rights reserved.
 *
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *  Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

/*
 * @test
 * @summary Test if memory ordering is preserved
 *
 * @run main/othervm -XX:-TieredCompilation -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure
 *      -XX:CompileThreshold=100 -XX:CompileCommand=dontinline,compiler.vectorapi.VectorMemoryAlias::test
 *      compiler.vectorapi.VectorMemoryAlias
 * @modules jdk.incubator.vector
 */

package compiler.vectorapi;

import java.nio.ByteOrder;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorSpecies;

public class VectorMemoryAlias {
  public static final VectorSpecies<Byte> SPECIES = VectorSpecies.ofLargestShape(byte.class);
  public static void main(String[] args) {
    for (int i=0; i < 30000; i++) {
      if (test() != 1) {
        throw new AssertionError();
      }
    }
  }

  public static int test() {
    byte arr[] = new byte[256];
    final var ms = MemorySegment.ofArray(arr);
    final var ones = ByteVector.broadcast(SPECIES, 1);
    var res = ByteVector.zero(SPECIES);

    int result = 0;
    result += arr[2];
    res.add(ones).intoMemorySegment(ms, 0L, ByteOrder.nativeOrder());
    result += arr[2];

    return result;
  }
}
