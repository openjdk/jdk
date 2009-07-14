/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

import java.util.List;

/*
 * @test
 * @bug 6843077
 * @summary test that compiler doesn't warn about annotated redundant casts
 * @author Mahmood Ali
 * @compile/ref=LintCast.out -Xlint:cast -XDrawDiagnostics -source 1.7 LintCast.java
 */
class LintCast {
    void unparameterized() {
        String s = "m";
        String s1 = (String)s;
        String s2 = (@A String)s;
    }

    void parameterized() {
        List<String> l = null;
        List<String> l1 = (List<String>)l;
        List<String> l2 = (List<@A String>)l;
    }

    void array() {
        int @A [] a = null;
        int[] a1 = (int[])a;
        int[] a2 = (int @A [])a;
    }

    void sameAnnotations() {
        @A String annotated = null;
        String unannotated = null;

        // compiler ignore annotated casts even if redundant
        @A String anno1 = (@A String)annotated;

        // warn if redundant without an annotation
        String anno2 = (String)annotated;
        String unanno2 = (String)unannotated;
    }
}

@interface A { }
