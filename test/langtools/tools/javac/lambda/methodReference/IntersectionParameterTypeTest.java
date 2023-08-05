/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 8292975
 * @summary Javac produces code that crashes with LambdaConversionException
 * @run main IntersectionParameterTypeTest
 */

import java.util.function.BiFunction;

public class IntersectionParameterTypeTest {

    sealed interface Term {
        record Lit() implements Term {}
        record Lam(String x, Term a) implements Term {}
    }

    public static <U, T> void call(BiFunction<U, T, T> op, U x, T t) {
      op.apply(x, t);
    }

    public static void main(String[] args) {
      // this code works
      call(Term.Lam::new, "x", (Term) new Term.Lit());

      // this does not
      call(Term.Lam::new, "x", new Term.Lit());
  }
}
