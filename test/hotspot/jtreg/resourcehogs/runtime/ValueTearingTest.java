/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @library /test/lib
 * @modules java.base/jdk.internal.vm.annotation java.base/jdk.internal.value
 * @enablePreview
 * @run main/othervm/timeout=2000 ValueTearingTest
 */


import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import jdk.internal.value.ValueClass;
import jdk.test.lib.Asserts;

public class ValueTearingTest {

    static final int N_SCENARIO = 5;
    static final int N_PRECOMPUTED = 100;
    static final int[] INCREMENTS = {1, 2, 3, 5, 7, 11, 13, 17, 23, 29, 31, 37, 41, 43,
                                     47, 53, 59, 61, 67, 71, 73, 79, 83, 89, 97, 101, 103,
                                     107, 109, 113, 127, 131};

    static int N_WRITERS;
    static int N_READERS;
    static final int DURATION_MILLISECONDS = 1 * 60 * 1000;
    static final Object[] objectArray = new Object[1];
    static AtomicLong[] scenarioFailures = new AtomicLong[N_SCENARIO];

    static CyclicBarrier barrier;

    static {
        int nCpu = Runtime.getRuntime().availableProcessors();
        N_WRITERS = Math.min((nCpu / 2), INCREMENTS.length);
        N_READERS = nCpu - N_WRITERS;
        Asserts.assertTrue(N_WRITERS > 0, "At least one writer thread is required");
        Asserts.assertTrue(N_READERS > 0, "At least one reader thread is required");
        barrier = new CyclicBarrier(nCpu + 1);
        objectArray[0] = new Value(1);
        for (int i = 0; i < N_SCENARIO; i++) {
            scenarioFailures[i] = new AtomicLong();
        }
    }

    static value class Value {
        int i;
        byte b;

        static Value[] precomputedValues = new Value[100];
        static Value sharedInstance = new Value(0);


        static {
            for (int i = 0; i < N_PRECOMPUTED; i++) {
                precomputedValues[i] = new Value(i);
            }
        }

        Value(int i) {
            this.i = i;
            b = (byte)(i/3);
        }

        public boolean existInPredefinedValueSet() {
            for (Value precomputedValue : precomputedValues) {
                if (this == precomputedValue) return true;
            }
            return false;
        }
    }

    static class Container {
        static Container sharedInstance = new Container();
        static Container[] precomputedContainers = new Container[N_PRECOMPUTED];
        static Value[] sharedArray;

        static {
            for (int i = 0; i < N_PRECOMPUTED; i++) {
                precomputedContainers[i] = new Container(i);
            }
            sharedArray = new Value[0];
            Asserts.assertTrue(ValueClass.isFlatArray(sharedArray));
        }

        Value val;

        Container() {
            this(0);
        }

        Container(int i) {
            val  = new Value(i);
        }
    }

    static class Scenario {
      static final int BATCH_SIZE = 10_000;

      static int incrementIndex(int idx, int n) {
          idx += INCREMENTS[n];
          if (idx >= N_PRECOMPUTED) idx -= N_PRECOMPUTED;
          return idx;
      }

      static void scenario0_writer(int n) {
          // Flat array -> buffered -> reference array [System.arraycopy]
          final var end = Instant.now().plusMillis(DURATION_MILLISECONDS);
          int idx = 0;
          while (Instant.now().isBefore(end)) {
              for (int i = 0; i < BATCH_SIZE; i++) {
                  System.arraycopy(Value.precomputedValues, idx, objectArray, 0, 1);
                  idx = incrementIndex(idx, n);
              }
          }
      }

      static void scenario0_reader() {
          // flat array -> buffered -> reference array [System.arraycopy]
          final var end0 = Instant.now().plusMillis(DURATION_MILLISECONDS);
          int failures = 0;
          while (Instant.now().isBefore(end0)) {
              Value v = (Value)objectArray[0];
              if (!v.existInPredefinedValueSet()) {
                  failures++;
              }
          }
          if (failures > 0) {
              scenarioFailures[0].getAndAdd(failures);
          }
      }

      static void scenario1_writer(int n) {
          // Flat array -> buffered -> reference array [Not System.arraycopy]
          final var end = Instant.now().plusMillis(DURATION_MILLISECONDS);
          int idx = 0;
          while (Instant.now().isBefore(end)) {
              for (int i = 0; i < BATCH_SIZE; i++) {
                  objectArray[0] = Value.precomputedValues[idx];
                  idx = incrementIndex(idx, n);
              }
          }
      }

      static void scenario1_reader() {
          // flat array -> buffered -> reference array [Not System.arraycopy]
          final var end0 = Instant.now().plusMillis(DURATION_MILLISECONDS);
          int failures = 0;
          while (Instant.now().isBefore(end0)) {
              Value v = (Value)objectArray[0];
              if (!v.existInPredefinedValueSet()) {
                  failures++;
              }
          }
          if (failures > 0) {
              scenarioFailures[1].getAndAdd(failures);
          }
      }

      static void scenario2_writer(int n) {
          // Flat field -> buffered -> reference array
          final var end = Instant.now().plusMillis(DURATION_MILLISECONDS);
          int idx = 0;
          while (Instant.now().isBefore(end)) {
              for (int i = 0; i < BATCH_SIZE; i++) {
                  objectArray[0] = Container.precomputedContainers[idx].val;
                  idx = incrementIndex(idx, n);
              }
          }
      }

      static void scenario2_reader() {
          // Flat field -> buffered -> reference array
          final var end = Instant.now().plusMillis(DURATION_MILLISECONDS);
          int failures = 0;
          while (Instant.now().isBefore(end)) {
              Value v = (Value)objectArray[0];
              if (!v.existInPredefinedValueSet()) {
                  failures++;
              }
          }
          if (failures > 0) {
              scenarioFailures[2].getAndAdd(failures);
          }
      }

      static void scenario3_writer(int n) {
          // Flat field -> buffered -> reference field
          final var end = Instant.now().plusMillis(DURATION_MILLISECONDS);
          int idx = 0;
          while (Instant.now().isBefore(end)) {
              for (int i = 0; i < BATCH_SIZE; i++) {
                  Value.sharedInstance = Container.precomputedContainers[idx].val;
                  idx = incrementIndex(idx, n);
              }
          }
      }

      static void scenario3_reader() {
          // Flat field -> buffered -> reference field
          final var end = Instant.now().plusMillis(DURATION_MILLISECONDS);
          int failures = 0;
          while (Instant.now().isBefore(end)) {
              Value v = Value.sharedInstance;
              if (!v.existInPredefinedValueSet()) {
                  failures++;
              }
          }
          if (failures > 0) {
              scenarioFailures[3].getAndAdd(failures);
          }
      }

      static void scenario4_writer(int n) {
          // Flat array -> buffered -> reference field
          final var end = Instant.now().plusMillis(DURATION_MILLISECONDS);
          int idx = 0;
          while (Instant.now().isBefore(end)) {
              for (int i = 0; i < BATCH_SIZE; i++) {
                  Value.sharedInstance = Value.precomputedValues[idx];
                  idx = incrementIndex(idx, n);
              }
          }
      }

      static void scenario4_reader() {
          // Flat array -> buffered -> reference field
          final var end = Instant.now().plusMillis(DURATION_MILLISECONDS);
          int failures = 0;
          while (Instant.now().isBefore(end)) {
              Value v = Value.sharedInstance;
              if (!v.existInPredefinedValueSet()) {
                  failures++;
              }
          }
          if (failures > 0) {
              scenarioFailures[4].getAndAdd(failures);
          }
      }
    }

    static class Writer implements Runnable {
        final int id;

        Writer(int i) {
            id = i;
        }

        @Override
        public void run() {
            try {
                // Reflection could be used to invoke methods, but keep it simple for now
                barrier.await();
                Scenario.scenario0_writer(id);
                barrier.await();
                Scenario.scenario1_writer(id);
                barrier.await();
                Scenario.scenario2_writer(id);
                barrier.await();
                Scenario.scenario3_writer(id);
                barrier.await();
                Scenario.scenario4_writer(id);
                barrier.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (BrokenBarrierException e) {
                throw new RuntimeException(e);
            }
        }
    }

    static class Reader implements Runnable {

        @Override
        public void run() {
            int scenario = 0;
            try {
                barrier.await();
                Scenario.scenario0_reader();
                barrier.await();
                Scenario.scenario1_reader();
                barrier.await();
                Scenario.scenario2_reader();
                barrier.await();
                Scenario.scenario3_reader();
                barrier.await();
                Scenario.scenario4_reader();
                barrier.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (BrokenBarrierException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("N_WRITERS = " + N_WRITERS + " N_READERS = " + N_READERS);
        Thread[] writers = new Thread[N_WRITERS];
        Thread[] readers = new Thread[N_READERS];

        for (int i = 0; i < N_WRITERS; i++) {
            writers[i] = new Thread(new Writer(i), "Writer-" + i);
            writers[i].start();
        }
        for (int i = 0; i < N_READERS; i++) {
            readers[i] = new Thread(new Reader(), "Reader-" + i);
            readers[i].start();
        }

        try {
            System.out.println("Waiting");
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) { }
            barrier.await();
            boolean failed = false;
            for (int i = 0; i < N_SCENARIO; i++) {
                System.out.println("Running scenario " + i);
                barrier.await();
                if (scenarioFailures[i].get() != 0) {
                    failed = true;
                    System.out.println("Scenario " + i + " failures: " + scenarioFailures[i].get());
                }
            }
            if (failed) {
                throw new RuntimeException("Value tearing detected");
            } else {
                System.out.println("Done");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (BrokenBarrierException e) {
            throw new RuntimeException(e);
        }
    }
}
