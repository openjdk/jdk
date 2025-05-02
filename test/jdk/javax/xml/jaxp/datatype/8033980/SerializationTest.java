/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8033980
 * @summary verify serialization compatibility for XMLGregorianCalendar and Duration
 * @run junit SerializationTest
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.stream.Stream;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
import javax.xml.datatype.XMLGregorianCalendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Verify serialization compatibility for XMLGregorianCalendar and Duration
 * @author huizhe.wang@oracle.com</a>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SerializationTest {

    final String EXPECTED_CAL = "0001-01-01T00:00:00.0000000-05:00";
    final String EXPECTED_DURATION = "P1Y1M1DT1H1M1S";
    static String[] JDK = {System.getProperty("java.version"), "JDK6", "JDK7", "JDK8", "JDK9"};

    // If needed to add serialized data of more JDK versions, serialized data source file can be generated using
    // GregorianCalAndDurSerDataUtil class.
    private GregorianCalendarAndDurationSerData[] gregorianCalendarAndDurationSerData = {null, new JDK6GregorianCalendarAndDurationSerData(),
    new JDK7GregorianCalendarAndDurationSerData(), new JDK8GregorianCalendarAndDurationSerData(), new JDK9GregorianCalendarAndDurationSerData()};

    /**
     * Create the serialized Bytes array and serialized bytes base64 string for GregorianCalender and Duration
     * with jdk under test.
     * @throws DatatypeConfigurationException Unexpected.
     * @throws IOException Unexpected.
     */
    @BeforeAll
    public void setup() throws DatatypeConfigurationException, IOException {
        DatatypeFactory dtf = DatatypeFactory.newInstance();
        XMLGregorianCalendar xmlGregorianCalendar = dtf.newXMLGregorianCalendar(EXPECTED_CAL);
        Duration duration = dtf.newDuration(EXPECTED_DURATION);
        try(ByteArrayOutputStream baos = new ByteArrayOutputStream(); ObjectOutputStream oos = new ObjectOutputStream(baos);
            ByteArrayOutputStream baos2 = new ByteArrayOutputStream(); ObjectOutputStream oos2 = new ObjectOutputStream(baos2)) {
            //Serialize the given xmlGregorianCalendar
            oos.writeObject(xmlGregorianCalendar);
            //Serialize the given xml Duration
            oos2.writeObject(duration);
            // Create the Data for JDK under test.
            gregorianCalendarAndDurationSerData[0] = new GregorianCalendarAndDurationSerData() {
                @Override
                public byte[] getGregorianCalendarByteArray() {
                    return baos.toByteArray();
                }

                @Override
                public byte[] getDurationBytes() {
                    return baos2.toByteArray();
                }
            };
        }
    }

    /**
     * Provide data for JDK version and Gregorian Calendar serialized bytes.
     * @return A Stream of arguments where each element is an array of size three. First element contain JDK version,
     * second element contain object reference to GregorianCalendarAndDurationSerData specific to JDK version
     * and third element contain expected Gregorian Calendar as string.
     */

    public Stream<Arguments> gregorianCalendarDataBytes() {
        return Stream.of(
                Arguments.of(JDK[0], gregorianCalendarAndDurationSerData[0], EXPECTED_CAL),
                Arguments.of(JDK[1], gregorianCalendarAndDurationSerData[1], EXPECTED_CAL),
                Arguments.of(JDK[2], gregorianCalendarAndDurationSerData[2], EXPECTED_CAL),
                Arguments.of(JDK[3], gregorianCalendarAndDurationSerData[3], EXPECTED_CAL),
                Arguments.of(JDK[4], gregorianCalendarAndDurationSerData[4], EXPECTED_CAL)
        );
    }

    /**
     * Provide data for JDK version and Duration serialized bytes.
     * @return A Stream of arguments where each element is an array of size three. First element contain JDK version,
     * second element contain object reference to GregorianCalendarAndDurationSerData specific to JDK version
     * and third element contain expected Duration as string.
     */

    public Stream<Arguments> durationData() {
        return Stream.of(Arguments.of(JDK[0], gregorianCalendarAndDurationSerData[0], EXPECTED_DURATION),
                Arguments.of(JDK[1], gregorianCalendarAndDurationSerData[1], EXPECTED_DURATION),
                Arguments.of(JDK[2], gregorianCalendarAndDurationSerData[2], EXPECTED_DURATION),
                Arguments.of(JDK[3], gregorianCalendarAndDurationSerData[3], EXPECTED_DURATION),
                Arguments.of(JDK[4], gregorianCalendarAndDurationSerData[4], EXPECTED_DURATION));
    }

    /**
     * Verify that GregorianCalendar serialized with different old JDK versions can be deserialized correctly with
     * JDK under test.
     * @param javaVersion JDK version used to GregorianCalendar serialization.
     * @param gcsd JDK version specific GregorianCalendarAndDurationSerData.
     * @param gregorianDate String representation of GregorianCalendar Date.
     * @throws IOException Unexpected.
     * @throws ClassNotFoundException Unexpected.
     */

    @ParameterizedTest
    @MethodSource("gregorianCalendarDataBytes")
    public void testReadCalBytes(String javaVersion, GregorianCalendarAndDurationSerData gcsd, String gregorianDate) throws IOException,
            ClassNotFoundException {
        final ByteArrayInputStream bais = new ByteArrayInputStream(gcsd.getGregorianCalendarByteArray());
        final ObjectInputStream ois = new ObjectInputStream(bais);
        final XMLGregorianCalendar xgc = (XMLGregorianCalendar) ois.readObject();
        assertEquals(gregorianDate, xgc.toString());
    }

    /**
     * Verify that Duration serialized with different old JDK versions can be deserialized correctly with
     * JDK under test.
     * @param javaVersion JDK version used to GregorianCalendar serialization.
     * @param gcsd JDK version specific GregorianCalendarAndDurationSerData.
     * @param duration String representation of Duration.
     * @throws IOException Unexpected.
     * @throws ClassNotFoundException Unexpected.
     */

    @ParameterizedTest
    @MethodSource("durationData")
    public void testReadDurationBytes(String javaVersion, GregorianCalendarAndDurationSerData gcsd, String duration) throws IOException,
            ClassNotFoundException {
        final ByteArrayInputStream bais = new ByteArrayInputStream(gcsd.getDurationBytes());
        final ObjectInputStream ois = new ObjectInputStream(bais);
        final Duration d1 = (Duration) ois.readObject();
        assertEquals(duration, d1.toString().toUpperCase());
    }
}
