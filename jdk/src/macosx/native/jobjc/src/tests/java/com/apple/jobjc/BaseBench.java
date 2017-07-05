/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.apple.jobjc;

import sun.misc.Unsafe;

public class BaseBench extends PooledTestCase {
    protected final static JObjCRuntime RUNTIME = JObjCRuntime.getInstance();
    protected final static JObjC JOBJC = JObjC.getInstance();
    protected final static Unsafe UNSAFE = JObjCRuntime.getInstance().unsafe;
    protected final static NativeArgumentBuffer ARGS = JObjCRuntime.getInstance().getThreadLocalState();

    public abstract static class Task{
        final String name;
        public Task(String name){ this.name = name; }
        public abstract void run();
    }

    public void bench(final String title, final long warmup, final long runs, final long iterations, final Task... tasks){
        final long[] runtimes = new long[tasks.length];

        for(int t = 0; t < tasks.length; ++t){
            long runtime = 0;
            for(int i = 0; i < warmup; ++i)
                singleBench(iterations, tasks[t]);
            for(int i = 0; i < runs; ++i)
                runtime = runtime + singleBench(iterations, tasks[t]);
            runtimes[t] = runtime;
        }

        final float[] relatives = new float[tasks.length];

        for(int t = 0; t < tasks.length; ++t)
            relatives[t] = ((float) runtimes[t] / (float) runs);

        float min = relatives[0];
        for(float t : relatives)
            if(t < min)
                min = t;

        for(int t = 0; t < tasks.length; ++t)
            relatives[t] = relatives[t] / min;

        System.out.format("\n* %1$s\n", title);
        for(int t = 0; t < tasks.length; ++t)
            System.out.format("%1$60s : %2$.1f\n", tasks[t].name, relatives[t]);
    }

    public long singleBench(final long iterations, final Task task){
        long start = System.currentTimeMillis();
        for(long i = 0; i < iterations; ++i)
            task.run();
        long end = System.currentTimeMillis();
        return end - start;
    }
}
