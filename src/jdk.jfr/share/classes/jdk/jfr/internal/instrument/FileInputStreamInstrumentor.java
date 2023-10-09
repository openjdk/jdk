/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;

import jdk.jfr.events.EventConfigurations;
import jdk.jfr.events.FileReadEvent;
import jdk.jfr.internal.event.EventConfiguration;

/**
 * See {@link JITracer} for an explanation of this code.
 */
@JIInstrumentationTarget("java.io.FileInputStream")
final class FileInputStreamInstrumentor {

    private FileInputStreamInstrumentor() {
    }

    private String path;

    @JIInstrumentationMethod
    public int read() throws IOException {
        EventConfiguration fileReadEventConfiguration = EventConfigurations.FILE_READ;
        EventConfiguration fileReadIOStatisticsEventConfiguration = EventConfigurations.FILE_READ_IO_STATISTICS;
        if (!fileReadEventConfiguration.isEnabled() && !fileReadIOStatisticsEventConfiguration.isEnabled()) {
            return read();
        }
        int result = 0;
        boolean endOfFile = false;
        long bytesRead = 0;
        long start = 0;
        long duration = 0;
        try {
            start = EventConfiguration.timestamp();
            result = read();
            if (result < 0) {
                endOfFile = true;
            } else {
                bytesRead = 1;
            }
        } finally {
            duration = EventConfiguration.timestamp() - start;
            if (fileReadEventConfiguration.shouldCommit(duration)) {
                FileReadEvent.commit(start, duration, path, bytesRead, endOfFile);
            }
        }
        if(fileReadIOStatisticsEventConfiguration.isEnabled()){            
            FileIOStatistics.addTotalReadBytesForPeriod(bytesRead, duration);
        }
        return result;
    }

    @JIInstrumentationMethod
    public int read(byte b[]) throws IOException {
        EventConfiguration fileReadEventConfiguration = EventConfigurations.FILE_READ;
        EventConfiguration fileReadIOStatisticsEventConfiguration = EventConfigurations.FILE_READ_IO_STATISTICS;
        if (!fileReadEventConfiguration.isEnabled() && !fileReadIOStatisticsEventConfiguration.isEnabled()) {
            return read(b);
        }
        int bytesRead = 0;
        long start = 0;
        long duration = 0;
        try {
            start = EventConfiguration.timestamp();
            bytesRead = read(b);
        } finally {
            duration = EventConfiguration.timestamp() - start;
            if (fileReadEventConfiguration.shouldCommit(duration)) {
                if (bytesRead < 0) {
                    FileReadEvent.commit(start, duration, path, 0L, true);
                } else {
                    FileReadEvent.commit(start, duration, path, bytesRead, false);
                }
            }
        }
        if(fileReadIOStatisticsEventConfiguration.isEnabled()){            
            FileIOStatistics.addTotalReadBytesForPeriod(((bytesRead < 0) ? 0 : bytesRead), duration);
        }
        return bytesRead;
    }

    @JIInstrumentationMethod
    public int read(byte b[], int off, int len) throws IOException {
        EventConfiguration fileReadEventConfiguration = EventConfigurations.FILE_READ;
        EventConfiguration fileReadIOStatisticsEventConfiguration = EventConfigurations.FILE_READ_IO_STATISTICS;
        if (!fileReadEventConfiguration.isEnabled() && !fileReadIOStatisticsEventConfiguration.isEnabled()) {
            return read(b, off, len);
        }
        int bytesRead = 0;
        long start = 0;
        long duration = 0;
        try {
            start = EventConfiguration.timestamp();
            bytesRead = read(b, off, len);
        } finally {
            duration = EventConfiguration.timestamp() - start;
            if (fileReadEventConfiguration.shouldCommit(duration)) {
                if (bytesRead < 0) {
                    FileReadEvent.commit(start, duration, path, 0L, true);
                } else {
                    FileReadEvent.commit(start, duration, path, bytesRead, false);
                }
            }
        }
        if(fileReadIOStatisticsEventConfiguration.isEnabled()){            
            FileIOStatistics.addTotalReadBytesForPeriod(((bytesRead < 0) ? 0 : bytesRead), duration);
        }
        return bytesRead;
    }
}
