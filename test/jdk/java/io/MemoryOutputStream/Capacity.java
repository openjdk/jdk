/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
package com.engebretson.memoryoutputstream;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.junit.Test;

/**
 * JUnit test whose purpose is to ensure that {@code MemoryOutputStream}'s max
 * capacity remains greater than {@code ByteArrayOutputStream}'s.
 *
 * @since 29
 * @author John Engebretson
 */
public class CapacityTest {

    private long testOutputStreamCapacity(OutputStream out) throws IOException {
        byte[] inputData = new byte[1024 * 1024];
        long totalBytesWritten = 0;

        try {
            while (true) {
                out.write(inputData);
                totalBytesWritten += inputData.length;
            }
        } catch (OutOfMemoryError e) {
            return totalBytesWritten;
        }
    }

    @Test
    public void compareCapacity() throws IOException {
        long mosCapacity = testOutputStreamCapacity(new MemoryOutputStream());
        System.out.println("MemoryOutputStream max capacity was " + (mosCapacity / 1024 / 1024) + "MB");
        long baosCapacity = testOutputStreamCapacity(new ByteArrayOutputStream());
        System.out.println("ByteArrayOutputStream max capacity was " + (baosCapacity / 1024 / 1024) + "MB");

        assertTrue(mosCapacity > baosCapacity);
    }
}
