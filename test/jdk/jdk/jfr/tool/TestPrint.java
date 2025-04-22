/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.tool;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import jdk.jfr.Recording;
import jdk.jfr.Event;
import jdk.jfr.Percentage;
import jdk.jfr.Timestamp;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.Timespan;
import jdk.jfr.DataAmount;
import jdk.jfr.Frequency;
import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;

/**
 * @test
 * @summary Test jfr print
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @run main/othervm jdk.jfr.tool.TestPrint
 */
public class TestPrint {

    static class ExactEvent extends Event {
        @DataAmount(DataAmount.BITS)
        long oneBit;

        @DataAmount(DataAmount.BITS)
        long bits;

        @Frequency
        @DataAmount(DataAmount.BITS)
        long oneBitPerSecond;

        @Frequency
        @DataAmount(DataAmount.BITS)
        long bitsPerSecond;

        @DataAmount(DataAmount.BYTES)
        long oneByte;

        @DataAmount(DataAmount.BYTES)
        long bytes;

        @Frequency
        @DataAmount(DataAmount.BYTES)
        long oneBytePerSecond;

        @Frequency
        @DataAmount(DataAmount.BYTES)
        long bytesPerSecond;

        @Percentage
        double percentage;

        @Timestamp(Timestamp.MILLISECONDS_SINCE_EPOCH)
        long timestamp;

        @Timespan(Timespan.NANOSECONDS)
        long timespan;
    }

    public static void main(String[] args) throws Throwable {
        testNoFile();
        testMissingFile();
        testIncorrectOption();
        testExact();
    }

    private static void testNoFile() throws Throwable {
        OutputAnalyzer output = ExecuteHelper.jfr("print");
        output.shouldContain("missing file");
    }

    private static void testMissingFile() throws Throwable {
        OutputAnalyzer output = ExecuteHelper.jfr("print", "missing.jfr");
        output.shouldContain("could not open file ");
    }

    private static void testIncorrectOption() throws Throwable {
        Path file = Utils.createTempFile("faked-print-file", ".jfr");
        FileWriter fw = new FileWriter(file.toFile());
        fw.write('d');
        fw.close();
        OutputAnalyzer output = ExecuteHelper.jfr("print", "--wrongOption", file.toAbsolutePath().toString());
        output.shouldContain("unknown option");
        Files.delete(file);
    }

    private static void testExact() throws Throwable{
        try (Recording r = new Recording()) {
            r.start();
            ExactEvent e = new ExactEvent();
            e.begin();
            e.oneBit            = 1L;
            e.bits              = 222_222_222L;
            e.oneBitPerSecond   = 1L;
            e.bitsPerSecond     = 333_333_333L;
            e.oneByte           = 1L;
            e.bytes             = 444_444_444L;
            e.oneBytePerSecond  = 1L;
            e.bytesPerSecond    = 555_555_555L;
            e.percentage        = 0.666_666_666_66;
            e.timestamp         = 777;
            e.timespan          = 888_888_888L;
            e.commit();
            r.stop();
            Path file = Path.of("exact.jfr");
            r.dump(file);
            OutputAnalyzer output = ExecuteHelper.jfr("print", "--exact", file.toAbsolutePath().toString());
            output.shouldContain("oneBit = 1 bit");
            output.shouldContain("bits = 222222222 bits");
            output.shouldContain("oneBitPerSecond = 1 bit/s");
            output.shouldContain("bitsPerSecond = 333333333 bits/s");
            output.shouldContain("oneByte = 1 byte");
            output.shouldContain("bytes = 444444444 bytes");
            output.shouldContain("oneBytePerSecond = 1 byte/s");
            output.shouldContain("bytesPerSecond = 555555555 bytes/s");
            output.shouldContain(String.valueOf(100 * e.percentage) + "%");
            output.shouldContain("00.777000000 (19");
            output.shouldContain(String.valueOf(e.timespan) + " s");
            Files.delete(file);
        }
    }
}
