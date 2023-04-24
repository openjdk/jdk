/*
 * Copyright (c) 2007, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * VM Testbase keywords: [gc, stress, stressopt, nonconcurrent]
 */

// Run in Juggle3Quic.java @test id=1  @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp byteArr -ms low
/* @test id=2  @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp byteArr -ms medium */
/* @test id=3  @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp byteArr -ms high */
/* @test id=4  @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp booleanArr -ms low */
/* @test id=5  @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp booleanArr -ms medium */
/* @test id=6  @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp booleanArr -ms high */
/* @test id=7  @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp shortArr -ms low */
/* @test id=8  @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp shortArr -ms medium */
/* @test id=9  @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp shortArr -ms high */
/* @test id=10 @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp charArr -ms low */
/* @test id=11 @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp charArr -ms medium */
/* @test id=12 @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp charArr -ms high */
/* @test id=13 @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp intArr -ms low */
// Run in Juggle3Quic.java @test id=14 @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp intArr -ms medium
/* @test id=15 @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp intArr -ms high */
/* @test id=16 @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp longArr -ms low */
/* @test id=17 @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp longArr -ms medium */
/* @test id=18 @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp longArr -ms high */
/* @test id=19 @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp floatArr -ms low */
/* @test id=20 @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp floatArr -ms medium */
/* @test id=21 @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp floatArr -ms high */
// Run in Juggle3Quic.java @test id=22 @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp doubleArr -ms low
/* @test id=23 @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp doubleArr -ms medium */
/* @test id=24 @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp doubleArr -ms high */
/* @test id=25 @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp objectArr -ms low */
/* @test id=26 @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp objectArr -ms medium */
/* @test id=27 @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp objectArr -ms high */
/* @test id=28 @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp hashed(doubleArr) -ms low */
// Run in Juggle3Quic.java @test id=29 @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp hashed(doubleArr) -ms medium
/* @test id=30 @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp hashed(doubleArr) -ms high */
/* @test id=31 @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp hashed(objectArr) -ms low */
/* @test id=32 @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp hashed(objectArr) -ms medium */
/* @test id=33 @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp hashed(objectArr) -ms high */
// Run in Juggle3Quic.java @test id=34 @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp random(arrays) -ms high


package gc.ArrayJuggle;

import nsk.share.test.*;
import nsk.share.gc.*;
import nsk.share.gc.gp.*;

/**
 * This test randomly replaces elements of an array with new objects
 * using given garbage producer and memory strategy.  This class was
 * renamed from Juggle01 to Juggle3 to better distinguice it from
 * Juggle1.
 */
public class Juggle3 extends ThreadedGCTest implements GarbageProducerAware, MemoryStrategyAware {
        private GarbageProducer garbageProducer;
        private MemoryStrategy memoryStrategy;
        private Object[] array;
        private Object[] indexLocks;
        long objectSize;

        private class Juggler implements Runnable {
                public void run() {
                        int index = LocalRandom.nextInt(array.length);
                        // Synchronizing to prevent multiple object creation for the same index at the same time.
                        synchronized (indexLocks[index]) {
                                array[index] = null;
                                array[index] = garbageProducer.create(objectSize);
                        }
                }
        }

        protected Runnable createRunnable(int i) {
                return new Juggler();
        }

        public void run() {
                log.debug("Garbage producer: " + garbageProducer);
                log.debug("Memory strategy: " + memoryStrategy);
                long memory = runParams.getTestMemory();
                int objectCount = memoryStrategy.getCount(memory);
                objectSize = memoryStrategy.getSize(memory);
                log.debug("Object count: " + objectCount);
                log.debug("Object size: " + objectSize);
                array = new Object[objectCount - 1];
                indexLocks = new Object[objectCount - 1];
                for (int i = 0; i < indexLocks.length; i++) {
                    indexLocks[i] = new Object();
                }
                super.run();
        }

        public void setGarbageProducer(GarbageProducer garbageProducer) {
                this.garbageProducer = garbageProducer;
        }

        public void setMemoryStrategy(MemoryStrategy memoryStrategy) {
                this.memoryStrategy = memoryStrategy;
        }

        public static void main(String[] args) {
                GC.runTest(new Juggle3(), args);
        }
}
