/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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


package micro.benchmarks;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Benchmarks cost of non-contended synchronization.
 */
public class SimpleSyncBenchmark extends BenchmarkBase {

    public static class Person {
        public int age;

        public Person(int age) {
            this.age = age;
        }

        public synchronized int getAge() {
            return age;
        }

        public synchronized void setAge(int age) {
            this.age = age;
        }

        public synchronized void setAgeIfNonZero(int age) {
            if (age != 0) {
                this.age = age;
            }
        }
    }

    @State(Scope.Benchmark)
    public static class ThreadState {
        Person person = new Person(22);
        int newAge = 45;
    }

    @Benchmark
    @Warmup(iterations = 20)
    public void setAgeCond(ThreadState state) {
        Person person = state.person;
        person.setAgeIfNonZero(state.newAge);
    }

    @Benchmark
    @Warmup(iterations = 20)
    public int getAge(ThreadState state) {
        Person person = state.person;
        return person.getAge();
    }

    @Benchmark
    @Warmup(iterations = 20)
    public int getAndIncAge(ThreadState state) {
        Person person = state.person;
        int oldAge = person.getAge();
        person.setAge(oldAge + 1);
        return oldAge;
    }
}
