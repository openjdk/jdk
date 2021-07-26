/*
 * Copyright (c) 2021, Red Hat Inc. All rights reserved.
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
package org.openjdk.bench.vm.gc;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

// Test the raw allocation rate for long arrays. Allocate 1 megabyte
// of memory, in chunks of varying size. The resulting score is the
// allocation rate in megabytes per second.

@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 15, time = 1)
@Fork(value = 1)
@State(Scope.Thread)
public class RawAllocationRate {

    @Param({"32", "64", "256", "1024", "2048", "4096", "8192", "16384", "65536", "131072"})  // Object size in bytes.
    public int size;

    Object[] objects;

    private static final long megabyte = 1024 * 1024;
    private static final long bytesPerLong = 8;
    private static final int longsPerHeader = 2;

    @Setup(Level.Iteration)
    public void up() throws Throwable {
	if (size % bytesPerLong != 0) {
	    throw new RuntimeException("I'm sorry Dave, I can't do that");
	}
	objects = new Object[1_000_000];
    }

    @TearDown(Level.Iteration)
    public void down() throws Throwable {
	objects = null;
    }

    @Benchmark
    public Object[] test() {
        var arrays = objects;
	final int longElements = (int)(size/bytesPerLong) - longsPerHeader;
	int i, j;
	for (i = 0, j = 0; ; i += size, j++) {
	    if (i + size < megabyte) {
		arrays[j] = new long[longElements];
	    } else {
		long remaining = megabyte - i;
		int elements = (int)(remaining/bytesPerLong) - longsPerHeader;
		if (elements >= 0) {
		    arrays[j] = new long[elements];
		}
		break;
	    }
	}
        return arrays;
    }

    @Benchmark
    @Fork(jvmArgsAppend={"-XX:TieredStopAtLevel=1"})
    public Object[] test_C1() {
        return test();
    }
}
