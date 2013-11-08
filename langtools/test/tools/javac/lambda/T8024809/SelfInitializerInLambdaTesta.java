/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8024809
 * @summary javac, some lambda programs are rejected by flow analysis
 * @compile/fail/ref=SelfInitializerInLambdaTesta.out -XDrawDiagnostics SelfInitializerInLambdaTesta.java
 */

public class SelfInitializerInLambdaTesta {

    final Runnable r1 = ()->System.out.println(r1);

    final Object lock = new Object();

    final Runnable r2 = ()->{
        System.out.println(r2);
        synchronized (lock){}
    };

    final Runnable r3 = ()->{
        synchronized (lock){
            System.out.println(r3);
        }
    };

    final Runnable r4 = ()->{
        System.out.println(r4);
    };

    interface SAM {
        int m(String s);
    }

    final SAM s1 = (String s)->{
        System.out.println(s + s1.toString());
        return 0;
    };

    final SAM s2 = (s)->{
        System.out.println(s + s2.toString());
        return 0;
    };
}
