/*
 * Copyright (c) 2008, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6843077
 * @summary new type annotation location: receivers
 * @author Mahmood Ali
 * @compile -source 1.7 Receivers.java
 */
class DefaultUnmodified {
  void plain() @A { }
  <T> void generic() @A { }
  void withException() @A throws Exception { }
  String nonVoid() @A { return null; }
  <T extends Runnable> void accept(T r) @A throws Exception { }
}

class PublicModified {
  public final void plain() @A { }
  public final <T> void generic() @A { }
  public final void withException() @A throws Exception { }
  public final String nonVoid() @A { return null; }
  public final <T extends Runnable> void accept(T r) @A throws Exception { }
}

class WithValue {
  void plain() @B("m") { }
  <T> void generic() @B("m") { }
  void withException() @B("m") throws Exception { }
  String nonVoid() @B("m") { return null; }
  <T extends Runnable> void accept(T r) @B("m") throws Exception { }
}

@interface A {}
@interface B { String value(); }
