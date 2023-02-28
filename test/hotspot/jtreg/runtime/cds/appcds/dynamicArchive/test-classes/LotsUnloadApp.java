/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

import java.lang.invoke.MethodType;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import static java.lang.invoke.MethodHandles.Lookup.ClassOption.*;

public class LotsUnloadApp implements Runnable {
    static byte[] classdata;
    static int exitAfterNumClasses = 1024;

    public static void main(String args[]) throws Throwable {
        String resname = DefinedAsHiddenKlass.class.getName() + ".class";
        classdata = LotsUnloadApp.class.getClassLoader().getResourceAsStream(resname).readAllBytes();

        int numThreads = 4;
        try {
            numThreads = Integer.parseInt(args[0]);
        } catch (Throwable t) {}

        try {
            exitAfterNumClasses = Integer.parseInt(args[1]);
        } catch (Throwable t) {}

        for (int i = 0; i < numThreads; i++) {
            Thread t = new Thread(new LotsUnloadApp());
            t.start();
        }
    }

    public void run() {
        while (true) {
            try {
                Lookup lookup = MethodHandles.lookup();
                Class<?> cl = lookup.defineHiddenClass(classdata, false, NESTMATE).lookupClass();
                cl.newInstance();
                add();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    static int n;
    static synchronized void add() {
        n++;
        if (n >= exitAfterNumClasses) {
            System.exit(0);
        }
    }
}

class DefinedAsHiddenKlass {
    // ZGC region size is always a multiple of 2MB on x64.
    // Make this slightly smaller than that.
    static byte[] array = new byte[2 * 1024 * 1024 - 8 * 1024];
    static String x;
    public DefinedAsHiddenKlass() {
        // This will generate some lambda forms hidden classes for string concat.
        x = "array size is "  + array.length + " bytes ";
    }
}
