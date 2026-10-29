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

package jdk.jfr.event.io;

import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @run main/othervm jdk.jfr.event.io.TestRandomAccessFileReadLine
 */
public class TestRandomAccessFileReadLine {

    public static void main(String[] args) throws Throwable {
        List<String> linesEnabled = test(true);
        List<String> linesDisabled = test(false);
        System.out.println("Enabled : " + linesEnabled);
        System.out.println("Disabled: " + linesDisabled);
        if (!linesEnabled.equals(linesDisabled)) {
            throw new Exception("Expected same result from RandomAccessFile::readLine with or without JFR");
        }
    }

    public static List<String> test(boolean enabled) throws Throwable {
        System.out.print("Testing with jdk.FileRead ");
        System.out.println(enabled ? "enabled" : "disabled");
        List<IOEvent> expectedEvents = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        try (Recording r = new Recording()) {
            if (enabled) {
                r.enable("jdk.FileRead").withoutThreshold();
            }
            r.start();
            // readLine() reads to the first:
            // - '\r\n'
            // - '\r'
            // - '\n'
            // - EOF
            // regardless of the operating system.

            // Reads: EOF
            lines.add(read(expectedEvents, "", 0, true));

            // Reads: '\n'
            lines.add(read(expectedEvents, "\n", 1, false));

            // Reads: '\r' EOF
            lines.add(read(expectedEvents, "\r", 1, true));

            // Reads: '\r' '\n'
            lines.add(read(expectedEvents, "\r\n", 2, false));

            // Reads: 'c' 'a' 't' EOF
            lines.add(read(expectedEvents, "cat", 3, true));

            // Reads: 'd' 'o' 'g' '\n'
            lines.add(read(expectedEvents, "dog\n", 4, false));

            // Reads: 'a' 'n' 't' '\r' EOF
            lines.add(read(expectedEvents, "ant\r", 4, true));

            // Reads: 'c' 'o' 'w' '\r' '\n'
            lines.add(read(expectedEvents, "cow\r\n", 5, false));

            // Reads: 'e' 'l' 'k' '\r' 'f'
            lines.add(read(expectedEvents, "elk\rfox", 5, false));
            r.stop();
            if (enabled) {
                List<RecordedEvent> events = Events.fromRecording(r);
                IOHelper.verifyEqualsInOrder(events, expectedEvents);
            }
        }
        return lines;
    }

    private static String read(List<IOEvent> expectedEvents,
                               String content,
                               long expectedBytes,
                               boolean expectedEOF) throws Throwable {
        Path path = Path.of("temp.txt");
        String line;
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "rw")) {
            raf.writeBytes(content);
            raf.seek(0);
            line = raf.readLine() + " file position: " + raf.getFilePointer();
            var event = IOEvent.createFileReadEvent(expectedBytes, path.toFile(), expectedEOF);
            expectedEvents.add(event);
        }
        Files.delete(path);
        return line;
    }
}
 