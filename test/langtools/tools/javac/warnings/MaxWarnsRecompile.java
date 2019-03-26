/*
 * Copyright (c) 2019, Google LLC. All rights reserved.
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
 */

import java.util.List;

/*
 * @test
 * @summary suggest recompiling with -Xmaxwarns
 * @compile/ref=MaxWarnsRecompile.max1.out -XDrawDiagnostics -Xlint:all -Xmaxwarns 1 MaxWarnsRecompile.java
 * @compile/ref=MaxWarnsRecompile.all.out -XDrawDiagnostics -Xlint:all -Xmaxwarns 4 MaxWarnsRecompile.java
 */
public class MaxWarnsRecompile {

  List x1;
  List x2;
  List x3;
  List x4;
}
