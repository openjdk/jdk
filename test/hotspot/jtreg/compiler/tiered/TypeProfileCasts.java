/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @test TypeProfileCasts
 * @summary Check that turning of TypeProfileCasts is tolerated
 * @requires vm.debug == true
 * @modules java.base/jdk.internal.misc
 *          java.management
 *
 * @run main/othervm -XX:+TieredCompilation -XX:-BackgroundCompilation -XX:-TypeProfileCasts
 *                   -XX:CompileCommand=compileonly,compiler.tiered.TypeProfileCasts::test_instanceof
 *                   compiler.tiered.TypeProfileCasts
 * @run main/othervm -XX:+TieredCompilation -XX:-BackgroundCompilation -XX:+TypeProfileCasts
 *                   -XX:CompileCommand=compileonly,compiler.tiered.TypeProfileCasts::test_instanceof
 *                   compiler.tiered.TypeProfileCasts
 * @run main/othervm -XX:+TieredCompilation -XX:-BackgroundCompilation -XX:-TypeProfileCasts
 *                   -XX:CompileCommand=compileonly,compiler.tiered.TypeProfileCasts::test_checkcast
 *                   compiler.tiered.TypeProfileCasts
 * @run main/othervm -XX:+TieredCompilation -XX:-BackgroundCompilation -XX:+TypeProfileCasts
 *                   -XX:CompileCommand=compileonly,compiler.tiered.TypeProfileCasts::test_checkcast
 *                   compiler.tiered.TypeProfileCasts
 * @run main/othervm -XX:+TieredCompilation -XX:-BackgroundCompilation -XX:-TypeProfileCasts
 *                   -XX:CompileCommand=compileonly,compiler.tiered.TypeProfileCasts::test_array_store
 *                   compiler.tiered.TypeProfileCasts
 * @run main/othervm -XX:+TieredCompilation -XX:-BackgroundCompilation -XX:+TypeProfileCasts
 *                   -XX:CompileCommand=compileonly,compiler.tiered.TypeProfileCasts::test_array_store
 *                   compiler.tiered.TypeProfileCasts
 */

package compiler.tiered;

public class TypeProfileCasts {
    static class Foo { }
    public static int sideEffect = 0;

    private static void test_instanceof(Object o) {
      // instanceof
      if (o instanceof Foo) {
        sideEffect++;
      }
    }

    private static void test_checkcast(Object o) {
      // checkcast
      Foo f = (Foo) o;

      sideEffect++;
    }

    private static void test_array_store(Object o) {
      // array store type check
      Foo[] fs = new Foo[1];
      Object[] os = fs;
      os[0] = o;

      sideEffect++;
    }

    public static void main(String... args) {
      for (int i = 0; i < 100_000; i++) {
        test_instanceof(new Foo());
        test_checkcast(new Foo());
        test_array_store(new Foo());
      }
    }
}
