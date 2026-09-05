/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @test /nodynamiccopyright/
 * @summary Inference must not widen wildcard type arguments to Object
 * @compile/fail WildcardChainedInference.java
 */

class Pair<A,B>{
    A left;
    B right;
    static <X,Y> Pair<X,Y> change(Pair<X,Y> a){ return a;}
    static <A> Pair<A,?> left(Pair<A,Integer> p){return p;}

    public static void main(String[] args){
        Pair<Object,Integer> p1 = new Pair<Object,Integer>();
        p1.left = "1";
        p1.right = 2;
        Pair<Object,Object> p = change(left(p1));
        p.right = p.left;
        System.out.println(p1.right + p1.right);
    }
}
