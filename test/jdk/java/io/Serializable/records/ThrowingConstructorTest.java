/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8246774
 * @summary Tests constructor invocation exceptions are handled appropriately
 * @run junit ThrowingConstructorTest
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import static java.lang.System.out;

import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * If the constructor invocation throws an exception, an
 * `InvalidObjectException` is thrown with that exception as its cause.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ThrowingConstructorTest {

    /** "big switch" that can be used to allow/disallow record construction
     * set to true after the data provider has constructed all record objects */
    private static volatile boolean firstDataSetCreated;

    record R1 () implements Serializable {
        public R1() {
            if (firstDataSetCreated)
                throw new NullPointerException("thrown from R1");
        }
    }

    record R2 (int x) implements Serializable {
        public R2(int x) {
            if (firstDataSetCreated)
                throw new IllegalArgumentException("thrown from R2");
            this.x = x;
        }
    }

    record R3 (int x, int y) implements Serializable {
        public R3(int x, int y) {
            if (firstDataSetCreated)
                throw new NumberFormatException("thrown from R3");
            this.x = x;
            this.y = y;
        }
    }

    static class C implements Serializable {
        final Object obj ;
        C(Object obj) { this.obj= obj; }
        @Override public String toString() { return "C[" + obj + "]"; }
    }

    static final Class<InvalidObjectException> IOE = InvalidObjectException.class;

    public Object[][] exceptionInstances() {
        Object[][] objs =  new Object[][] {
            new Object[] { new R1(),            NullPointerException.class,     "thrown from R1" },
            new Object[] { new R2(1),           IllegalArgumentException.class, "thrown from R2" },
            new Object[] { new R3(2, 3),        NumberFormatException .class,   "thrown from R3" },
            new Object[] { new C(new R1()),     NullPointerException.class,     "thrown from R1" },
            new Object[] { new C(new R2(4)),    IllegalArgumentException.class, "thrown from R2" },
            new Object[] { new C(new R3(5, 6)), NumberFormatException .class,   "thrown from R3" },
        };
        firstDataSetCreated = true;
        return  objs;
    }

    @ParameterizedTest
    @MethodSource("exceptionInstances")
    public void testExceptions(Object objectToSerialize,
                               Class<? extends Throwable> expectedExType,
                               String expectedExMessage)
        throws Exception
    {
        out.println("\n---");
        out.println("serializing: " + objectToSerialize);
        byte[] bytes = serialize(objectToSerialize);
        InvalidObjectException ioe = Assertions.assertThrows(IOE, () -> deserialize(bytes));
        out.println("caught expected IOE: " + ioe);
        Throwable t = ioe.getCause();
        assertTrue(t.getClass().equals(expectedExType),
                   "Expected:" + expectedExType + ", got:" + t);
        out.println("expected cause " + expectedExType +" : " + t);
        assertEquals(expectedExMessage, t.getMessage());
    }

    //  -- errors ( pass through unwrapped )

    private static volatile boolean secondDataSetCreated;

    record R4 () implements Serializable {
        public R4() {
            if (secondDataSetCreated)
                throw new OutOfMemoryError("thrown from R4"); }
    }

    record R5 (int x) implements Serializable {
        public R5(int x) {
            if (secondDataSetCreated)
                throw new StackOverflowError("thrown from R5");
            this.x = x;
        }
    }

    record R6 (int x, int y) implements Serializable {
        public R6(int x, int y) {
            if (secondDataSetCreated)
                throw new AssertionError("thrown from R6");
            this.x = x;
            this.y = y;
        }
    }

    public Object[][] errorInstances() {
        Object[][] objs =  new Object[][] {
            new Object[] { new R4(),              OutOfMemoryError.class,   "thrown from R4" },
            new Object[] { new R5(11),            StackOverflowError.class, "thrown from R5" },
            new Object[] { new R6(12, 13),        AssertionError .class,    "thrown from R6" },
            new Object[] { new C(new R4()),       OutOfMemoryError.class,   "thrown from R4" },
            new Object[] { new C(new R5(14)),     StackOverflowError.class, "thrown from R5" },
            new Object[] { new C(new R6(15, 16)), AssertionError .class,    "thrown from R6" },
        };
        secondDataSetCreated = true;
        return objs;
    }

    @ParameterizedTest
    @MethodSource("errorInstances")
    public void testErrors(Object objectToSerialize,
                           Class<? extends Throwable> expectedExType,
                           String expectedExMessage)
        throws Exception
    {
        out.println("\n---");
        out.println("serializing: " + objectToSerialize);
        byte[] bytes = serialize(objectToSerialize);
        Throwable t = Assertions.assertThrows(expectedExType, () -> deserialize(bytes));
        assertTrue(t.getClass().equals(expectedExType),
                   "Expected:" + expectedExType + ", got:" + t);
        out.println("caught expected " + expectedExType +" : " + t);
        assertEquals(expectedExMessage, t.getMessage());
    }

    // --- infra

    static <T> byte[] serialize(T obj) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(obj);
        oos.close();
        return baos.toByteArray();
    }

    @SuppressWarnings("unchecked")
    static <T> T deserialize(byte[] streamBytes)
        throws IOException, ClassNotFoundException
    {
        ByteArrayInputStream bais = new ByteArrayInputStream(streamBytes);
        ObjectInputStream ois  = new ObjectInputStream(bais);
        return (T) ois.readObject();
    }

    static <T> T serializeDeserialize(T obj)
        throws IOException, ClassNotFoundException
    {
        return deserialize(serialize(obj));
    }
}
