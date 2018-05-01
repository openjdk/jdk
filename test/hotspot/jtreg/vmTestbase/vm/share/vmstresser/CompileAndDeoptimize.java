/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
package vm.share.vmstresser;

public class CompileAndDeoptimize implements Runnable {

    public static int v = 0;

    private abstract static class A {
        public abstract void incv();
    }

    private static class B extends A {
        public void incv() {
            v++;
        }
    }

    public static class C extends A {
        public void incv() {
            v += (new int[1][1][1][1][1][1][1][1]).length;
        }
    }

    private volatile boolean done = false;
    public volatile A a = new B();

    private void incv() {
        a.incv();
    }

    private void inc() {
        while ( ! done ) {
            incv();
        }
        //while ( ! done ) {
        //      incv();
        //}
        //while ( ! done ) {
        //      incv();
        //}
    }

    public void run() {
        try {
            Thread t = new Thread(new Runnable() { @Override public void run() { inc(); } });
            t.start();
            Thread.sleep(100);
            a = (A) CompileAndDeoptimize.class.getClassLoader().loadClass(B.class.getName().replaceAll("B$", "C")).getConstructors()[0].newInstance(new Object[0]);
            //Thread.sleep(1000);
            //done = true;
            //t.join();

        } catch ( Throwable t ) {
            t.printStackTrace();
        }
    }

}
