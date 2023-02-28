/*
 * Copyright (c) 2021, Google LLC. All rights reserved.
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
 * @test 8275233
 * @summary Incorrect line number reported in exception stack trace thrown from a lambda expression
 * @compile/ref=DeduplicationDebugInfo.out           -XDrawDiagnostics -XDdebug.dumpLambdaToMethodDeduplication -g:none        DeduplicationDebugInfo.java
 * @compile/ref=DeduplicationDebugInfo_none.out      -XDrawDiagnostics -XDdebug.dumpLambdaToMethodDeduplication                DeduplicationDebugInfo.java
 * @compile/ref=DeduplicationDebugInfo_none.out      -XDrawDiagnostics -XDdebug.dumpLambdaToMethodDeduplication -g:lines       DeduplicationDebugInfo.java
 * @compile/ref=DeduplicationDebugInfo_none.out      -XDrawDiagnostics -XDdebug.dumpLambdaToMethodDeduplication -g:vars        DeduplicationDebugInfo.java
 * @compile/ref=DeduplicationDebugInfo_none.out      -XDrawDiagnostics -XDdebug.dumpLambdaToMethodDeduplication -g:lines,vars  DeduplicationDebugInfo.java
 */

import java.util.function.Function;

class DeduplicationDebugInfoTest {
    void f() {
        Function<Object, Integer> f = x -> x.hashCode();
        Function<Object, Integer> g = x -> x.hashCode();
    }
}
