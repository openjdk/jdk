/*
* Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
* @summary Tests humongous stack-chunk handling
* @requires vm.continuations
* @modules java.base/jdk.internal.vm
*
* @requires vm.gc.G1
* @run main/othervm --enable-preview -XX:+IgnoreUnrecognizedVMOptions -XX:-VerifyContinuations -Xms2g -Xmx2g -XX:+UseG1GC -XX:G1HeapRegionSize=1m -Xss10m -Xint HumongousStack 5000
* @run main/othervm --enable-preview -XX:+IgnoreUnrecognizedVMOptions -XX:-VerifyContinuations -Xms2g -Xmx2g -XX:+UseG1GC -XX:G1HeapRegionSize=1m -Xss10m -Xcomp -XX:TieredStopAtLevel=3 -XX:CompileOnly=jdk/internal/vm/Continuation,HumongousStack HumongousStack 10000
* @run main/othervm --enable-preview -XX:+IgnoreUnrecognizedVMOptions -XX:-VerifyContinuations -Xms2g -Xmx2g -XX:+UseG1GC -XX:G1HeapRegionSize=1m -Xss10m -Xcomp -XX:-TieredCompilation -XX:CompileOnly=jdk/internal/vm/Continuation,HumongousStack HumongousStack 10000
*/

import jdk.internal.vm.Continuation;
import jdk.internal.vm.ContinuationScope;

public class HumongousStack implements Runnable {
    static final ContinuationScope FOO = new ContinuationScope() {};
    private final int DEPTH;

    static Object[] fillYoungGen(int depth) {
        Object[] x = new Object[100];
        if (depth > 0) for (int i=0; i<x.length; i++) x[i] = fillYoungGen(depth-1);
        return x;
    }

    public static void main(String[] args) {
        int depth = Integer.parseInt(args[0]);
        System.out.println("start depth: " + depth);

        Continuation cont = new Continuation(FOO, new HumongousStack(depth));

        // remove the try/catch when adding support for humongous stacks
        try {
            Object[] x = null;
            while (!cont.isDone()) {
                cont.run();
                x = fillYoungGen(3);
            }

            System.out.println("done; x:" + java.util.Arrays.hashCode(x));
        } catch (StackOverflowError e) {
        }
    }

    public HumongousStack(int depth) { this.DEPTH = depth; }

    @Override
    public void run() { // we don't use a lambda so that the compilation command compiles everything
        String res = deep(DEPTH, new String("x"));
        System.out.println("done: " + res);
    }


    static String deep(int depth, String x) {
        if (depth > 0) {
            assert x != null;
            var r = deep(depth-1, x); // deep(depth-1, depth + "-" + x) + "-" + depth; //
            assert r != null;
            return r;
        }

        System.out.println("-- Yielding!");
        boolean res = Continuation.yield(FOO);
        assert res;
        System.out.println("-- Resumed!");

        return "" + depth;
    }
}
