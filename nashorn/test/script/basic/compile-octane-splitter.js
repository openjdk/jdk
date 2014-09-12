/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * Make sure that we run with the class cache off to so that every
 * run produces compile time and with optimistic type info caching
 * and persistent code store off, for the same reasons. These last two
 * are currently default, but this is not guaranteed to be the case
 * forever, so make this test future safe, we specify them explicitly
 *
 * @test
 * @fork
 * @option -Dnashorn.compiler.splitter.threshold=1000
 * @fork
 * @runif external.octane
 * @option -scripting
 * @option -Dnashorn.typeInfo.disabled=true
 * @option --class-cache-size=0
 * @option --persistent-code-cache=false
 */

var fn  = __DIR__ + 'compile-octane.js';
var url = new java.io.File(fn).toURL();
loadWithNewGlobal(url);
