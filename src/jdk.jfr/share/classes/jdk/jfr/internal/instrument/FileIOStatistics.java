/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.internal.instrument;

import java.util.concurrent.atomic.AtomicLong;

public final class FileIOStatistics {

    private static AtomicLong totalReadBytesForPeriod = new AtomicLong(0);
    private static AtomicLong totalReadDuration = new AtomicLong(0);
    private static AtomicLong totalReadBytesForProcess = new AtomicLong(0);  
    private static AtomicLong totalWriteBytesForProcess = new AtomicLong(0);
    private static AtomicLong totalWriteBytesForPeriod = new AtomicLong(0);
    private static AtomicLong totalWriteDuration = new AtomicLong(0);

    public static long getTotalReadBytesForProcess() {
        return totalReadBytesForProcess.get();
    } 

    public static long getTotalReadDuration() {
        return totalReadDuration.get();
    }

    public static long getTotalReadBytesForPeriod() {
        return totalReadBytesForPeriod.get();
    }
        
    public static void addTotalReadBytesForPeriod(long bytesRead, long duration) {
        totalReadDuration.addAndGet(duration);
        totalReadBytesForProcess.addAndGet(bytesRead);
        totalReadBytesForPeriod.addAndGet(bytesRead);
    }

    /**
     * Calculates and returns the read rate in bytes per second for the period mentioned in the jfc.
     *
     * This method computes the read rate by taking the total number of bytes read
     * and the total duration of the read operation, then calculates the rate as
     * bytes per second. If the interval is zero or negative, it returns 0.
     *
     * @return The read rate in bytes per second.
    */      
    public static long getAndResetReadRateForPeriod() {
        long result = getTotalReadBytesForPeriod();
        long interval = getTotalReadDuration();
        totalReadBytesForPeriod.addAndGet(-result);
        if (interval > 0) {
            totalReadDuration.addAndGet(-interval);
            double intervalInSec = (interval * 1.0 / 1_000_000_000);
            long rRate = (long) (result / intervalInSec);
            return rRate;
        }
        return 0;
    }

    public static long getTotalWriteBytesForProcess() {
        return totalWriteBytesForProcess.get();
    } 

    public static long getTotalWriteDuration() {
        return totalWriteDuration.get();
    }

    public static long getTotalWriteBytesForPeriod() {
        return totalWriteBytesForPeriod.get();
    }

    public static long addTotalWriteBytesForPeriod(long bytesWritten, long duration) {
        totalWriteDuration.addAndGet(duration);
        totalWriteBytesForProcess.addAndGet(bytesWritten);
        return totalWriteBytesForPeriod.addAndGet(bytesWritten);
    }

    /**
     * Calculates and returns the write rate in bytes per second for the period mentioned in the jfc.
     *
     * This method computes the write rate by taking the total number of bytes written
     * and the total duration of the write operation, then calculates the rate as
     * bytes per second. If the interval is zero or negative, it returns 0.
     *
     * @return The write rate in bytes per second.
    */     
    public static long getAndResetWriteRateForPeriod() {
        long result = getTotalWriteBytesForPeriod();
        long interval = getTotalWriteDuration();
        totalWriteBytesForPeriod.addAndGet(-result);       
        if (interval > 0) {
            totalWriteDuration.addAndGet(-interval);
            double intervalInSec = (interval * 1.0 / 1_000_000_000);           
            long wRate = (long) (result / intervalInSec);                  
            return wRate;
        }
        return 0;
    }
}