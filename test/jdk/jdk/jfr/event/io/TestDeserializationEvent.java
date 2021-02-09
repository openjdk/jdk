/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter.Status;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static java.lang.System.out;
import static org.testng.Assert.*;

/*
 * @test
 * @bug 8261160
 * @summary Add a deserialization JFR event
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib
 * @run testng/othervm jdk.jfr.event.io.TestDeserializationEvent
 */
public class TestDeserializationEvent {

    public record R() implements Serializable { }

    @DataProvider(name = "scenarios")
    public Object[][] scenarios() throws Exception {
        byte[] ba1 = serialize(new R());
        byte[] ba2 = serialize(new int[] { 56, 67, 58, 59, 60 });
        byte[] ba3 = serialize(new R[] { new R(), new R() });
        byte[] ba4 = serialize(new char[][] { new char[] {'a', 'b'}, new char[] {'c'} });

        // data provider columns- 1:id, 2:serialize-operation, 3:expected-event-checkers
        return new Object[][] {
            {   1,  // single stream object, R
                (Runnable)() -> deserialize(ba1),
                List.of(
                    Set.of(
                        assertStatus("n/a"),
                        assertClass("jdk.jfr.event.io.TestDeserializationEvent$R"),
                        assertArrayLength(-1),
                        assertTotalObjectRefs(1),
                        assertDepth(1),
                        assertHasBytesRead(),
                        assertException("n/a"))) },
            {   2,  // primitive int array
                (Runnable)() -> deserialize(ba2),
                List.of(
                    Set.of(  // TC_CLASS, for array class int[]
                        assertClass("[I"),
                        assertArrayLength(-1)),
                    Set.of(  // TC_ARRAY, actual array
                        assertClass("[I"),
                        assertArrayLength(5))) },
            {   3,  // reference array, R
                (Runnable)() -> deserialize(ba3),
                List.of(
                    Set.of(  // TC_CLASS, for array class R[]
                        assertClass("[Ljdk.jfr.event.io.TestDeserializationEvent$R;"),
                        assertArrayLength(-1)),
                    Set.of(  // TC_ARRAY, actual array
                        assertClass("[Ljdk.jfr.event.io.TestDeserializationEvent$R;"),
                        assertArrayLength(2)),
                    Set.of(  // TC_CLASS, for R
                        assertClass("jdk.jfr.event.io.TestDeserializationEvent$R"),
                        assertArrayLength(-1)),
                    Set.of(  // TC_REFERENCE, for TC_CLASS relating second stream obj
                        assertClass("null"),
                        assertArrayLength(-1))) },
            {  4,  // multi-dimensional prim char array
               (Runnable)() -> deserialize(ba4),
               List.of(
                    Set.of(  // TC_CLASS, for array class char[][]
                        assertClass("[[C"),
                        assertArrayLength(-1),
                        assertDepth(1)),
                    Set.of(  // TC_ARRAY, actual char[][] array
                        assertClass("[[C"),
                        assertArrayLength(2),
                        assertDepth(1)),
                    Set.of(  // TC_CLASS, for array class char[]
                        assertClass("[C"),
                        assertArrayLength(-1),
                        assertDepth(2)),
                    Set.of(  // TC_ARRAY, first char[] array
                        assertClass("[C"),
                        assertArrayLength(2),
                        assertDepth(2)),
                    Set.of(  // TC_REFERENCE, for TC_CLASS relating to second stream array
                        assertClass("null"),
                        assertArrayLength(-1),
                        assertDepth(2)),
                    Set.of(  // TC_ARRAY, second char[] array
                        assertClass("[C"),
                        assertArrayLength(1),
                        assertDepth(2))) }
        };
    }

    @Test(dataProvider = "scenarios")
    public void test(int id,
                     Runnable thunk,
                     List<Set<Consumer<RecordedEvent>>> expectedValuesChecker)
       throws IOException
    {
        try (Recording recording = new Recording()) {
            recording.enable(EventNames.Deserialization);
            recording.start();
            thunk.run();
            recording.stop();
            List<RecordedEvent> events = Events.fromRecording(recording);
            assertTrue(events.size() > 0);
            assertEventList(events, expectedValuesChecker);
        }
    }

    static final Class<InvalidClassException> ICE = InvalidClassException.class;

    @DataProvider(name = "filterDisallowValues")
    public Object[][] filterDisallowValues() {
        return new Object[][] {
                { Status.REJECTED,   "REJECTED" },
                { null,              "n/a"      }
        };
    }

    @Test(dataProvider = "filterDisallowValues")
    public void testFilterDisallow(Status filterStatus,
                                   String expectedValue)
        throws Exception
    {
        try (Recording recording = new Recording();
             var bais = new ByteArrayInputStream(serialize(new R()));
             var ois = new ObjectInputStream(bais)) {
            ois.setObjectInputFilter(fv -> filterStatus);
            recording.enable(EventNames.Deserialization);
            recording.start();
            assertThrows(ICE, () -> ois.readObject());
            recording.stop();
            List<RecordedEvent> events = Events.fromRecording(recording);
            assertTrue(events.size() > 0);
            events.stream()
                  .filter(e -> e.getEventType().getName().equals("jdk.Deserialization"))
                  .forEach(assertStatus(expectedValue));
        }
    }

    @DataProvider(name = "filterAllowedValues")
    public Object[][] filterAllowedValues() {
        return new Object[][] {
                { Status.ALLOWED,   "ALLOWED"   },
                { Status.UNDECIDED, "UNDECIDED" },
        };
    }

    @Test(dataProvider = "filterAllowedValues")
    public void testFilterAllowed(Status filterStatus,
                                  String expectedValue) throws Exception {
        try (Recording recording = new Recording();
             var bais = new ByteArrayInputStream(serialize(new R()));
             var ois = new ObjectInputStream(bais)) {
            ois.setObjectInputFilter(fv -> filterStatus);
            recording.enable(EventNames.Deserialization);
            recording.start();
            ois.readObject();
            recording.stop();
            List<RecordedEvent> events = Events.fromRecording(recording);
            assertTrue(events.size() > 0);
            events.stream()
                  .filter(e -> e.getEventType().getName().equals("jdk.Deserialization"))
                  .forEach(assertStatus(expectedValue));
        }
    }

    static class XYZException extends RuntimeException { }

    @Test
    public void testException() throws Exception {
        try (Recording recording = new Recording();
             var bais = new ByteArrayInputStream(serialize(new R()));
             var ois = new ObjectInputStream(bais)) {
            ois.setObjectInputFilter(fv -> { throw new XYZException(); });
            recording.enable(EventNames.Deserialization);
            recording.start();
            InvalidClassException ice = expectThrows(ICE, () -> ois.readObject());
            recording.stop();
            out.println("caught: " + ice);
            List<RecordedEvent> events = Events.fromRecording(recording);
            assertTrue(events.size() > 0);
            events.stream()
                    .filter(e -> e.getEventType().getName().equals("jdk.Deserialization"))
                    .forEach(assertException("jdk.jfr.event.io.TestDeserializationEvent$XYZException"));
        }
    }

    static void assertEventList(List<RecordedEvent> actualEvents,
                                List<Set<Consumer<RecordedEvent>>> expectedValuesChecker) {
        int found = 0;
        for (RecordedEvent recordedEvent : actualEvents) {
            if (!recordedEvent.getEventType().getName().equals("jdk.Deserialization"))
                continue;

            out.println("Checking recorded event:" + recordedEvent);
            Set<Consumer<RecordedEvent>> checkers = expectedValuesChecker.get(found);
            for (Consumer<RecordedEvent> checker : checkers) {
                out.println("  checking:" + checker);
                checker.accept(recordedEvent);
            }
            assertException("n/a"); // no exception expected
            found++;
        }
        assertEquals(found, expectedValuesChecker.size());
    }

    static Consumer<RecordedEvent> assertStatus(String expectedValue) {
        return new Consumer<>() {
            @Override public void accept(RecordedEvent recordedEvent) {
                assertTrue(recordedEvent.hasField("status"));
                assertEquals(recordedEvent.getValue("status"), expectedValue);
            }
            @Override public String toString() {
                return "assertStatus, expectedValue=" + expectedValue;
            }
        };
    }

    static Consumer<RecordedEvent> assertClass(String expectedValue) {
        return new Consumer<>() {
            @Override public void accept(RecordedEvent recordedEvent) {
                assertTrue(recordedEvent.hasField("clazz"));
                assertEquals(recordedEvent.getValue("clazz"), expectedValue);
            }
            @Override public String toString() {
                return "assertClass, expectedValue=" + expectedValue;
            }
        };
    }

    static Consumer<RecordedEvent> assertArrayLength(int expectedValue) {
        return new Consumer<>() {
            @Override public void accept(RecordedEvent recordedEvent) {
                assertTrue(recordedEvent.hasField("arrayLength"));
                assertEquals((int)recordedEvent.getValue("arrayLength"), expectedValue);
            }
            @Override public String toString() {
                return "assertArrayLength, expectedValue=" + expectedValue;
            }
        };
    }

    static Consumer<RecordedEvent> assertTotalObjectRefs(long expectedValue) {
        return new Consumer<>() {
            @Override public void accept(RecordedEvent recordedEvent) {
                assertTrue(recordedEvent.hasField("totalObjectRefs"));
                assertEquals((long)recordedEvent.getValue("totalObjectRefs"), expectedValue);
            }
            @Override public String toString() {
                return "assertTotalObjectRefs, expectedValue=" + expectedValue;
            }
        };
    }

    static Consumer<RecordedEvent> assertDepth(long expectedValue) {
        return new Consumer<>() {
            @Override public void accept(RecordedEvent recordedEvent) {
                assertTrue(recordedEvent.hasField("depth"));
                assertEquals((long)recordedEvent.getValue("depth"), expectedValue);
            }
            @Override public String toString() {
                return "assertDepth, expectedValue=" + expectedValue;
            }
        };
    }

    static Consumer<RecordedEvent> assertHasBytesRead() {
        return new Consumer<>() {
            @Override public void accept(RecordedEvent recordedEvent) {
                assertTrue(recordedEvent.hasField("bytesRead"));
            }
            @Override public String toString() {
                return "assertHasBytesRead,";
            }
        };
    }

    static Consumer<RecordedEvent> assertBytesRead(long expectedValue) {
        return new Consumer<>() {
            @Override public void accept(RecordedEvent recordedEvent) {
                assertHasBytesRead().accept(recordedEvent);
                assertEquals((long)recordedEvent.getValue("bytesRead"), expectedValue);
            }
            @Override public String toString() {
                return "assertBytesRead, expectedValue=" + expectedValue;
            }
        };
    }

    static Consumer<RecordedEvent> assertException(String expectedValue) {
        return new Consumer<>() {
            @Override public void accept(RecordedEvent recordedEvent) {
                assertHasBytesRead().accept(recordedEvent);
                assertEquals(recordedEvent.getValue("exception"), expectedValue);
            }
            @Override public String toString() {
                return "assertException, expectedValue=" + expectedValue;
            }
        };
    }

    static <T> byte[] serialize(T obj) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(obj);
        oos.close();
        return baos.toByteArray();
    }

    @SuppressWarnings("unchecked")
    static <T> T deserialize(byte[] streamBytes) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(streamBytes);
            ObjectInputStream ois  = new ObjectInputStream(bais);
            return (T) ois.readObject();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
