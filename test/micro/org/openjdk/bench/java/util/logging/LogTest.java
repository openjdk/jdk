/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.java.util.logging;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class LogTest {
    private Logger logger;
    private System.Logger sysLogger;

    @Param({"1", "10"})
    private int numStrings;

    @Setup
    public void setup() {
        try {
            logger = Logger.getLogger("logger");
	    sysLogger = System.getLogger("sysLogger");
            logger.setLevel(Level.INFO);
            logger.setUseParentHandlers(false);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Benchmark
    public void testFine() {
       logger.log(Level.FINE, createMessage(numStrings));
    }

    @Benchmark
    public void testFineWithSupplier() {
       logger.log(Level.FINE, () -> createMessage(numStrings));
    }

    @Benchmark
    public void testInfo() {
       logger.log(Level.INFO, createMessage(numStrings));
    }


    @Benchmark
    public void testInfoWithSupplier() {
       logger.log(Level.INFO, () -> createMessage(numStrings));
    }

    @Benchmark
    public void testSysLoggerFine() {
       sysLogger.log(System.Logger.Level.DEBUG, createMessage(numStrings));
    }

    private static String createMessage(int numStrings) {
         return IntStream.range(0, numStrings).mapToObj( i -> { return "test" + i;}).collect(Collectors.toList()).toString();
    }
}
