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

/*
 * @test
 * @bug 8343580
 * @summary Type error with inner classes of generic classes in functions generic by outer
 * @compile T8343580.java
 */

class T8343580 {
   static abstract class Getters<T> {
      abstract class Getter {
         abstract T get();
      }
   }

   static class Usage1<T, G extends Getters<T>> {
      public T test(G.Getter getter) {
         return getter.get();
      }
   }

   static class Usage2<T, U extends Getters<T>, G extends U> {
      public T test(G.Getter getter) {
         return getter.get();
      }
   }

   static class Usage3<T, U extends T, G extends Getters<T>> {
      public T test(G.Getter getter) {
         return getter.get();
      }
   }

   class G2<K> extends Getters<K> {}
   static class Usage4<M, L extends G2<M>> {
      M test(L.Getter getter) {
         return getter.get();
      }
   }
}
