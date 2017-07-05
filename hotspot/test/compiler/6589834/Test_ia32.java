/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 6589834
 * @summary deoptimization problem with -XX:+DeoptimizeALot
 *
 * @run main Test_ia32
 */

/***************************************************************************************
NOTE: The bug shows up (with several "Bug!" message) even without the
      flag -XX:+DeoptimizeALot. In a debug build, you may want to try
      the flags -XX:+VerifyStack and -XX:+DeoptimizeALot to get more information.
****************************************************************************************/
import java.lang.reflect.Constructor;

public class Test_ia32 {

    public static int NUM_THREADS = 100;

    public static int CLONE_LENGTH = 1000;

    public static void main(String[] args) throws InterruptedException, ClassNotFoundException {

        Reflector[] threads = new Reflector[NUM_THREADS];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Reflector();
            threads[i].start();
        }

        System.out.println("Give Reflector.run() some time to compile...");
        Thread.sleep(5000);

        System.out.println("Load RMISecurityException causing run() deoptimization");
        ClassLoader.getSystemClassLoader().loadClass("java.rmi.RMISecurityException");

        for (Reflector thread : threads)
            thread.requestStop();

        for (Reflector thread : threads)
            try {
                thread.join();
            } catch (InterruptedException e) {
                System.out.println(e);
            }

    }

}

class Reflector extends Thread {

    volatile boolean _doSpin = true;

    Test_ia32[] _tests;

    Reflector() {
        _tests = new Test_ia32[Test_ia32.CLONE_LENGTH];
        for (int i = 0; i < _tests.length; i++) {
            _tests[i] = new Test_ia32();
        }
    }

    static int g(int i1, int i2, Test_ia32[] arr, int i3, int i4) {

        if (!(i1==1 && i2==2 && i3==3 && i4==4)) {
            System.out.println("Bug!");
        }

        return arr.length;
    }

    static int f(Test_ia32[] arr) {
        return g(1, 2, arr.clone(), 3, 4);
    }

    @Override
    public void run() {
        Constructor[] ctrs = null;
        Class<Test_ia32> klass = Test_ia32.class;
        try {
            ctrs = klass.getConstructors();
        } catch (SecurityException e) {
            System.out.println(e);
        }

        try {
            while (_doSpin) {
                if (f(_tests) < 0)
                    System.out.println("return value usage");
            }
        } catch (NullPointerException e) {
            e.printStackTrace();
        }

        System.out.println(this + " - stopped.");
    }

    public void requestStop() {
        System.out.println(this + " - stop requested.");
        _doSpin = false;
    }

}
