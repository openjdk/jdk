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
 * @run testng SerializationTest
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Base64;
import java.util.Formatter;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
import javax.xml.datatype.XMLGregorianCalendar;

import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Verify serialization compatibility for XMLGregorianCalendar and Duration
 * @author huizhe.wang@oracle.com</a>
 */
public class SerializationTest {

    final String EXPECTED_CAL = "0001-01-01T00:00:00.0000000-05:00";
    final String EXPECTED_DURATION = "P1Y1M1DT1H1M1S";
    static String[] JDK = {"JDK6", "JDK7", "JDK8", "JDK9"};

    private GregorianCalendarAndDurationSerData[] gregorianCalendarAndDurationSerData = {null, new JDK6GregorianCalendarAndDurationSerData(),
    new JDK7GregorianCalendarAndDurationSerData(), new JDK8GregorianCalendarAndDurationSerData(), new JDK9GregorianCalendarAndDurationSerData()};

    /**
     * Create the Serialization Bytes using JDK under test.
     * @throws DatatypeConfigurationException
     * @throws IOException
     * @throws ClassNotFoundException
     */
    @BeforeClass
    public void setup() throws DatatypeConfigurationException, IOException, ClassNotFoundException {
        DatatypeFactory dtf = DatatypeFactory.newInstance();
        XMLGregorianCalendar xmlGregorianCalendar = dtf.newXMLGregorianCalendar(EXPECTED_CAL);

        //Serialize the given xmlGregorianCalendar

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(xmlGregorianCalendar);
        oos.flush();
        oos.close();

        // Now get a Base64 string representation of the serialized bytes.
        final String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());

        // Generates the Java Pseudo code for base64 encoded string that can be cut & pasted into the test
        final StringBuilder sb = new StringBuilder();
        sb.append("    /**").append('\n');
        sb.append("     * Base64 encoded string for XMLGregorianCalendar object.").append('\n');
        sb.append("     * Java version: ").append(System.getProperty("java.version")).append('\n');
        sb.append("     **/").append('\n');
        sb.append("    private final String gregorianCalendarBase64 = ").append("\n          ");
        final int last = base64.length() - 1;
        for (int i=0; i<base64.length();i++) {
            if (i%64 == 0) sb.append("\"");
            sb.append(base64.charAt(i));
            if (i%64 == 63 || i == last) {
                sb.append("\"");
                if (i == last) sb.append(";\n");
                else sb.append("\n        + ");
            }
        }
        System.out.println(sb);

        // Generates the Java Pseudo code for byte array that can be cut & pasted into the test
        byte [] bytes = baos.toByteArray();
        StringBuilder sb2 = new StringBuilder(bytes.length * 5);
        Formatter fmt = new Formatter(sb2);
        fmt.format("    private final byte[] %s = {", "gregorianCalendarBytes");
        final int linelen = 8;
        for (int i = 0; i <bytes.length; i++) {
            if (i % linelen == 0) {
                fmt.format("%n        ");
            }
            fmt.format(" (byte) 0x%x,", bytes[i] & 0xff);
        }
        fmt.format("%n    };%n");

        System.out.println(sb2);

        Duration duration = dtf.newDuration(EXPECTED_DURATION);

        //Serialize the given xml Duration

        final ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
        final ObjectOutputStream oos2 = new ObjectOutputStream(baos2);
        oos2.writeObject(duration);
        oos2.flush();
        oos2.close();

        // Now get a Base64 string representation of the serialized bytes.
        final String base64dur = Base64.getEncoder().encodeToString(baos2.toByteArray());

        // Generates the Java Pseudo code for Duration serialized bytes  Base64 encoded string  that can be cut &
        // pasted into the test

        final StringBuilder sbdur = new StringBuilder();
        sbdur.append("    /**").append('\n');
        sbdur.append("     * Base64 encoded string for Duration object.").append('\n');
        sbdur.append("     * Java version: ").append(System.getProperty("java.version")).append('\n');
        sbdur.append("     **/").append('\n');
        sbdur.append("    private final String durationBase64 = ").append("\n          ");
        final int lastdur = base64dur.length() - 1;
        for (int i=0; i<base64dur.length();i++) {
            if (i%64 == 0) sbdur.append("\"");
            sbdur.append(base64dur.charAt(i));
            if (i%64 == 63 || i == lastdur) {
                sbdur.append("\"");
                if (i == lastdur) sbdur.append(";\n");
                else sbdur.append("\n        + ");
            }
        }

        System.out.println(sbdur);

        // Generates the Java Pseudo code for Duration serialized byte array that can be cut & pasted into the test
        byte [] bytesdur = baos2.toByteArray();
        StringBuilder sbdur2 = new StringBuilder(bytesdur.length * 5);
        Formatter fmt2 = new Formatter(sbdur2);
        fmt2.format("    private final byte[] %s = {", "durationBytes");
        final int linelen2 = 8;
        for (int i = 0; i <bytesdur.length; i++) {
            if (i % linelen2 == 0) {
                fmt2.format("%n        ");
            }
            fmt2.format(" (byte) 0x%x,", bytesdur[i] & 0xff);
        }
        fmt2.format("%n    };%n");

        System.out.println(sbdur2);

        // Create the Data for JDK under test.
        gregorianCalendarAndDurationSerData[0] = new GregorianCalendarAndDurationSerData() {
            @Override
            public byte[] getGregorianCalendarByteArray() {
                return baos.toByteArray();
            }

            @Override
            public String getGregorianCalendarBase64() {
                return base64;
            }

            @Override
            public byte[] getDurationBytes() {
                return baos2.toByteArray();
            }

            @Override
            public String getDurationBase64() {
                return base64dur;
            }
        };
    }

    /**
     *Provide data for JDK version and Gregorian Calendar serialized bytes
     */
    @DataProvider(name = "GregorianCalendarData")
    public Object [][] gregorianCalendarDataBytes() {
        return new Object [][] {{System.getProperty("java.version"), gregorianCalendarAndDurationSerData[0], EXPECTED_CAL},
                {JDK[0], gregorianCalendarAndDurationSerData[1], EXPECTED_CAL},
                {JDK[1], gregorianCalendarAndDurationSerData[2], EXPECTED_CAL},
                {JDK[2], gregorianCalendarAndDurationSerData[3], EXPECTED_CAL},
                {JDK[3], gregorianCalendarAndDurationSerData[4], EXPECTED_CAL}};
    }

    /**
     *Provide data for JDK version and serialized Gregorian Calendar Base64 encoded string
     */
    @DataProvider(name = "GregorianCalendarDataBase64")
    public Object [][] gregorianCalendarDataBase64() {
        return new Object [][] {{System.getProperty("java.version"), gregorianCalendarAndDurationSerData[0], EXPECTED_CAL},
                {JDK[2], gregorianCalendarAndDurationSerData[3], EXPECTED_CAL},
                {JDK[3], gregorianCalendarAndDurationSerData[4], EXPECTED_CAL}};
    }

    /**
     *Provide data for JDK version and Duration serialized bytes
     */
    @DataProvider(name = "DurationData")
    public Object [][] DurationData() {
        return new Object [][] {{System.getProperty("java.version"), gregorianCalendarAndDurationSerData[0], EXPECTED_DURATION},
                {JDK[0], gregorianCalendarAndDurationSerData[1], EXPECTED_DURATION},
                {JDK[1], gregorianCalendarAndDurationSerData[2], EXPECTED_DURATION},
                {JDK[2], gregorianCalendarAndDurationSerData[3], EXPECTED_DURATION},
                {JDK[3], gregorianCalendarAndDurationSerData[4], EXPECTED_DURATION}};
    }

    /**
     *Provide data for JDK version and Duration Base64 encode serialized bytes string
     */
    @DataProvider(name = "DurationDataBase64")
    public Object [][] DurationDataBase64() {
        return new Object [][] {{System.getProperty("java.version"), gregorianCalendarAndDurationSerData[0], EXPECTED_DURATION},
                {JDK[2], gregorianCalendarAndDurationSerData[3], EXPECTED_DURATION},
                {JDK[3], gregorianCalendarAndDurationSerData[4], EXPECTED_DURATION}};
    }

    /**
     * verify serialization compatibility for XMLGregorianCalendar
     * @param javaVersion
     * @param gcsd
     * @param gregorianDate
     * @throws IOException
     * @throws ClassNotFoundException
     */
    @Test(dataProvider="GregorianCalendarData")
    public void testReadCalBytes(String javaVersion, GregorianCalendarAndDurationSerData gcsd, String gregorianDate) throws IOException,
            ClassNotFoundException {
        final ByteArrayInputStream bais = new ByteArrayInputStream(gcsd.getGregorianCalendarByteArray());
        final ObjectInputStream ois = new ObjectInputStream(bais);
        final XMLGregorianCalendar xgc = (XMLGregorianCalendar) ois.readObject();
        Assert.assertEquals(xgc.toString(), gregorianDate);
    }

    /**
     * verify serialization compatibility for XMLGregorianCalendar
     * @param javaVersion
     * @param gcsd
     * @param gregorianDate
     * @throws IOException
     * @throws ClassNotFoundException
     */
    @Test(dataProvider="GregorianCalendarDataBase64")
    public void testReadCalBase64(String javaVersion, GregorianCalendarAndDurationSerData gcsd, String gregorianDate) throws IOException,
            ClassNotFoundException {
        final ByteArrayInputStream bais = new ByteArrayInputStream(Base64.getDecoder().decode(gcsd.getGregorianCalendarBase64()));
        final ObjectInputStream ois = new ObjectInputStream(bais);
        final XMLGregorianCalendar xgc = (XMLGregorianCalendar) ois.readObject();
        Assert.assertEquals(xgc.toString(), gregorianDate);
    }

    /**
     * verify serialization compatibility for Duration
     * @param javaVersion
     * @param gcsd
     * @param duration
     * @throws IOException
     * @throws ClassNotFoundException
     */
    @Test(dataProvider = "DurationData")
    public void testReadDurationBytes(String javaVersion, GregorianCalendarAndDurationSerData gcsd, String duration) throws IOException,
            ClassNotFoundException {
        final ByteArrayInputStream bais = new ByteArrayInputStream(gcsd.getDurationBytes());
        final ObjectInputStream ois = new ObjectInputStream(bais);
        final Duration d1 = (Duration) ois.readObject();
        Assert.assertEquals(d1.toString().toUpperCase(), duration);
    }

    /**
     * verify serialization compatibility for Duration
     * @param javaVersion
     * @param gcsd
     * @param duration
     * @throws IOException
     * @throws ClassNotFoundException
     */
    @Test(dataProvider = "DurationDataBase64")
    public void testReadDurationBase64(String javaVersion, GregorianCalendarAndDurationSerData gcsd, String duration) throws IOException,
            ClassNotFoundException {
        final ByteArrayInputStream bais = new ByteArrayInputStream(Base64.getDecoder().decode(gcsd.getDurationBase64()));
        final ObjectInputStream ois = new ObjectInputStream(bais);
        final Duration d1 = (Duration) ois.readObject();
        Assert.assertEquals(d1.toString().toUpperCase(), duration);
    }
}
