/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4316678
 * @summary test that Calendar's Serialization works correctly.
 * @run junit bug4316678
 */

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class bug4316678 {

    private static final String serializedData = "bug4316678.ser";

    // Save JVM default TimeZone
    private static final TimeZone savedTz = TimeZone.getDefault();

    // Set custom JVM default TimeZone
    @BeforeAll
    static void initAll() {
        TimeZone.setDefault(TimeZone.getTimeZone("PST"));
    }

    // Restore JVM default Locale and TimeZone
    @AfterAll
    static void tearDownAll() {
        TimeZone.setDefault(savedTz);
    }

    // Test that a serialized GregorianCalendar has the expected values
    @Test
    public void serializationTest() throws IOException, ClassNotFoundException {
        GregorianCalendar gc1 = new GregorianCalendar(2000, Calendar.OCTOBER, 10);
        GregorianCalendar gc2;
        try (ObjectOutputStream out
                = new ObjectOutputStream(new FileOutputStream(serializedData))) {
            out.writeObject(gc1);
        }

        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(serializedData))) {
            gc2 = (GregorianCalendar)in.readObject();
        }

        gc1.set(Calendar.DATE, 16);
        gc2.set(Calendar.DATE, 16);
        assertEquals(gc2.getTime(), gc1.getTime(),
                "Times should be equal after serialization");
    }
}
