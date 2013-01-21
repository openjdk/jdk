/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8003280
 * @summary Add lambda tests
 *  check nested case of overload resolution and lambda parameter inference
 * @author  Maurizio Cimadamore
 * @compile/fail/ref=TargetType01.out -XDrawDiagnostics TargetType01.java
 */

class TargetType01 {

    interface Func<A,B> {
        B call(A a);
    }

    interface F_I_I extends Func<Integer,Integer> {}
    interface F_S_S extends Func<String,String> {}

    static Integer M(F_I_I f){ return null; }
    static String M(F_S_S f){ return null; }

    static {
        M(x1 -> { return M( x2 -> { return x1 + x2; });}); //ambiguous
    }
}
