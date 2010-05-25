/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 5044157
 * @summary type system loophole in wildcard substitution
 * @author Gilad Bracha
 *
 * @compile/fail CaptureInSubtype.java
 */

import java.util.List;
import java.util.ArrayList;

public class CaptureInSubtype {

    static class SuperOfFlaw<S>{
        S s;
        S get() { return s;}
        void put(S a) { s = a;}

        SuperOfFlaw(S a) { s = a;}
    }

    static class Flaw<T> extends SuperOfFlaw<List<T>> {
        List<T> fetch(){return s;}

        Flaw(T t){super(new ArrayList<T>()); s.add(t);}
    }

    static class SuperOfShowFlaw {

        SuperOfFlaw<List<?>> m(){return null;}
    }


    public static class ShowFlaw extends SuperOfShowFlaw {
        static Flaw<Number> fn =  new Flaw<Number>(new Integer(3));

        Flaw<?> m(){return fn;}
    }

    public static void main(String[] args) {
        SuperOfShowFlaw sosf = new ShowFlaw();
        SuperOfFlaw<List<?>> sof = sosf.m();
        List<String> ls = new ArrayList<String>();
        ls.add("Smalltalk rules!");
        sof.put(ls);
        Number n = ShowFlaw.fn.get().get(0);
    }

}
