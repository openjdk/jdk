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

/* @test id=byteArr_medium     @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp byteArr           -ms medium */
/* @test id=byteArr_high       @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp byteArr           -ms high */
/* @test id=booleanArr_low     @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp booleanArr        -ms low */
/* @test id=booleanArr_medium  @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp booleanArr        -ms medium */
/* @test id=booleanArr_high    @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp booleanArr        -ms high */
/* @test id=shortArr_low       @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp shortArr          -ms low */
/* @test id=shortArr_medium    @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp shortArr          -ms medium */
/* @test id=shortArr_high      @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp shortArr          -ms high */
/* @test id=charArr_low        @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp charArr           -ms low */
/* @test id=charArr_medium     @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp charArr           -ms medium */
/* @test id=charArr_high       @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp charArr           -ms high */
/* @test id=intArr_low         @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp intArr            -ms low */
/* @test id=intArr_high        @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp intArr            -ms high */
/* @test id=longArr_low        @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp longArr           -ms low */
/* @test id=longArr_medium     @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp longArr           -ms medium */
/* @test id=longArr_high       @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp longArr           -ms high */
/* @test id=floatArr_low       @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp floatArr          -ms low */
/* @test id=floatArr_medium    @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp floatArr          -ms medium */
/* @test id=floatArr_high      @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp floatArr          -ms high */
/* @test id=doubleArr_medium   @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp doubleArr         -ms medium */
/* @test id=doubleArr_high     @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp doubleArr         -ms high */
/* @test id=objectArr_low      @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp objectArr         -ms low */
/* @test id=objectArr_medium   @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp objectArr         -ms medium */
/* @test id=objectArr_high     @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp objectArr         -ms high */
/* @test id=h_doubleArr_low    @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp hashed(doubleArr) -ms low */
/* @test id=h_doubleArr_high   @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp hashed(doubleArr) -ms high */
/* @test id=h_objectArr_low    @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp hashed(objectArr) -ms low */
/* @test id=h_objectArr_medium @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp hashed(objectArr) -ms medium */
/* @test id=h_objectArr_high   @key stress randomness @library /vmTestbase /test/lib @run main/othervm -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc=debug:gc.log gc.ArrayJuggle.Juggle3 -gp hashed(objectArr) -ms high */

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
