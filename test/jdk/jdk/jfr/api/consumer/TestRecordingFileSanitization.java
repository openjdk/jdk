/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.api.consumer;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import jdk.jfr.Name;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import jdk.jfr.Event;

/**
 * @test
 * @summary Verifies that all traces of sensitive data is removed
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm jdk.jfr.api.consumer.TestRecordingFileSanitization
 */
public class TestRecordingFileSanitization {
    // Less than 16 characters, stored in event
    private final static String SHORT_PASSWORD = "abcde123";
    // More than 16 characters, stored in constant pool
    private final static String LONG_PASSWORD = "abcdefghijklmnopqrstuvxyz1234567890";

    @Name("Sensitive")
    public static class SensitiveEvent extends Event {
        String shortPassword;
        String longPassword;
    }

    public static void main(String[] args) throws Throwable {
        Path sensitive = Path.of("sensitive.jfr");
        Path sanitized = Path.of("sanitized.jfr");
        try (Recording r = new Recording()) {
            r.start();
            SensitiveEvent e = new SensitiveEvent();
            e.shortPassword = SHORT_PASSWORD;
            e.longPassword = LONG_PASSWORD;
            e.commit();
            r.stop();
            r.dump(sensitive);
        }
        try (RecordingFile r = new RecordingFile(sensitive)) {
            r.write(sanitized, e -> !e.getEventType().getName().equals("Sensitive"));
        }

        expect(sensitive, SHORT_PASSWORD);
        expect(sensitive, LONG_PASSWORD);
        missing(sanitized, SHORT_PASSWORD);
        missing(sanitized, LONG_PASSWORD);
    }

    private static void expect(Path file, String text) throws IOException {
        if (!find(file, text)) {
            throw new AssertionError("Expected to find '" + text +"' in " + file);
        }
        System.out.println("OK, found '" + text + "' in " + file );
    }

    private static void missing(Path file, String text) throws IOException {
        if (find(file, text)) {
            throw new AssertionError("Didn't expect to find '" + text +"' in " + file);
        }
        System.out.println("OK, missing '" + text + "' in " + file);
    }

    private static boolean find(Path file, String text) throws IOException {
        byte[] textBytes = stringToBytes(text);
        byte[] fileBytes = Files.readAllBytes(file);
        for (int i = 0; i < fileBytes.length - textBytes.length; i++) {
            if (find(fileBytes, i, textBytes)) {
                return true;
            }
        }
        return false;
    }

    private static boolean find(byte[] haystack, int start, byte[] needle) {
        for (int i = 0; i < needle.length; i++) {
            if (haystack[start + i] != needle[i]) {
                return false;
            }
        }
        return true;
    }

    private static byte[] stringToBytes(String text) {
        byte[] bytes = new byte[text.length()];
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) > 127) {
                throw new Error("Test only allows characters that becomes one byte with LEB128");
            }
            bytes[i] = (byte)(text.charAt(i));
        }
        return bytes;
    }
}
