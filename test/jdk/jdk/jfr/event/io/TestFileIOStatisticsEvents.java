/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.test.lib.Asserts.assertEquals;
import static jdk.test.lib.Asserts.assertNotEquals;
import static jdk.test.lib.Asserts.assertGreaterThanOrEqual;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.time.Duration;
import java.util.List;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;
import jdk.test.lib.Utils;

/**
 * @test
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @run main/othervm jdk.jfr.event.io.TestFileIOStatisticsEvents
 */
public class TestFileIOStatisticsEvents {
    private static final int writeInt = 'A';
    private static final byte[] writeBuf = { 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P',
            'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z' };
    private static int accWriteExpected = 0;
    private static int accReadExpected = 0;
    public static void main(String[] args) throws Throwable {
        Recording recording = new Recording();
        File tmp = Utils.createTempFile("TestFileIOStatistics", ".tmp").toFile();
        recording.enable(EventNames.FileReadIOStatistics);
        recording.enable(EventNames.FileWriteIOStatistics);

        recording.start();
        useFileChannel(tmp);
        useRandomAccessFile(tmp);
        useFileStream(tmp);
        recording.stop(); 

        checkForDisabledFileEvents(recording);      
        checkForEnabledFileIOStatistics(recording);       
    }

    private static void useFileChannel(File tmp) throws Throwable {
        tmp.delete();
        try (RandomAccessFile rf = new RandomAccessFile(tmp, "rw"); FileChannel ch = rf.getChannel()) {
            final String bufContent = "0123456789";
            final int bufSize = bufContent.length();
            ByteBuffer writeBuf = ByteBuffer.allocateDirect(bufSize);
            writeBuf.put(bufContent.getBytes());
            writeBuf.flip();
            int writeSize = ch.write(writeBuf);
            accWriteExpected = accWriteExpected + writeSize;

            ch.position(0);
            ByteBuffer readBuf = ByteBuffer.allocateDirect(bufSize);
            int readSize = ch.read(readBuf);
            accReadExpected = accReadExpected + readSize;
            assertEquals(accWriteExpected, accReadExpected, "Total Read bytes != Total write bytes in the useFileChannel");
        }
    }

    private static void useRandomAccessFile(File tmp) throws Throwable {
        tmp.delete();
        try (RandomAccessFile ras = new RandomAccessFile(tmp, "rw")) {
            ras.write(writeInt);
            ras.write(writeBuf);
            ras.seek(0);
            accWriteExpected = accWriteExpected + writeBuf.length + 1;
            int readInt = ras.read();
            byte[] readBuf = new byte[writeBuf.length];
            int readSize = ras.read(readBuf);
            accReadExpected = accReadExpected + readSize + 1;
            // Try to read more which should generate EOF.
            readInt = ras.read();
            assertEquals(readInt, -1, "Wrong readInt after EOF");
        }
        assertEquals(accWriteExpected, accReadExpected, "Total Read bytes != Total write bytes in the RandomAccessFile");
    } 

    private static void useFileStream(File tmp) throws Throwable {
        tmp.delete();
        try (FileOutputStream fos = new FileOutputStream(tmp)) {
            fos.write(writeInt);
            fos.write(writeBuf);
            accWriteExpected = accWriteExpected + writeBuf.length + 1;
        }
        try(FileInputStream fis = new FileInputStream(tmp)){
            int readInt = fis.read();
            byte[] readBuf = new byte[writeBuf.length];
            int readSize = fis.read(readBuf);
            accReadExpected = accReadExpected + readSize + 1;    
            readInt = fis.read();
            assertEquals(readInt, -1, "Wrong readInt after EOF");             
        }
        assertEquals(accWriteExpected, accReadExpected, "Total Read bytes != Total write bytes in the FileStream");  
       
    }

    private static void checkForDisabledFileEvents(Recording recording) throws Throwable {
        for (RecordedEvent event : Events.fromRecording(recording)) {
            final String eventName = event.getEventType().getName();
            System.out.println("Got eventName:" + eventName);
            assertNotEquals(eventName, IOEvent.EVENT_FILE_READ, "Got disabled read event");
            assertNotEquals(eventName, IOEvent.EVENT_FILE_WRITE, "Got disabled write event"); 
            assertNotEquals(eventName, IOEvent.EVENT_FILE_FORCE, "Got disabled force event");            
        }
    }

    private static void checkForEnabledFileIOStatistics(Recording recording) throws Throwable {
        boolean hasNonZeroReadRate = false;
        boolean hasNonZeroWriteRate = false;
        String accWrite = null;
        String accRead = null;

        //JFR EventAttributes 
        final String WRITE_RATE = "writeRate";
        final String ACC_WRITE_BYTES = "accWrite";
        final String READ_RATE = "readRate";
        final String ACC_READ_BYTES = "accRead";

        List<RecordedEvent> events = Events.fromRecording(recording);

        Events.hasEvents(events);
        Events.hasEvent(events, EventNames.FileReadIOStatistics);
        Events.hasEvent(events, EventNames.FileWriteIOStatistics);

        for (RecordedEvent event : events) {            
            if (event.getEventType().getName().toString().equals(EventNames.FileWriteIOStatistics)) {
                String writeRateVal = Events.assertField(event, WRITE_RATE).getValue().toString();
                accWrite = Events.assertField(event, ACC_WRITE_BYTES).getValue().toString();
                if (Double.parseDouble(writeRateVal) > 0) {
                    hasNonZeroWriteRate = true;
                }
            } else if (event.getEventType().getName().toString().equals(EventNames.FileReadIOStatistics)) {
                String readRateVal = Events.assertField(event, READ_RATE).getValue().toString();
                accRead = Events.assertField(event, ACC_READ_BYTES).getValue().toString();
                if (Double.parseDouble(readRateVal) > 0) {
                    hasNonZeroReadRate = true;
                }
            }
        }
        assertEquals(hasNonZeroWriteRate, true);
        assertEquals(hasNonZeroReadRate, true);
       
        assertGreaterThanOrEqual(Integer.parseInt(accRead), accReadExpected, "The accumulated read bytes:" + accRead + " should be equal to expected Read Bytes:" + accReadExpected);
        assertGreaterThanOrEqual(Integer.parseInt(accWrite), accWriteExpected,
                "The accumulated write bytes:" + accWrite + " should be equal or more that expected length:" + accWriteExpected);
    }
}