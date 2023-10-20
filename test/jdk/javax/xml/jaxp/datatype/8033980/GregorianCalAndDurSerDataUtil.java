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

/**
 * @test
 * @summary utility to generate Gregorian Calendar and Duration serialized data java classes.
 * @run junit/manual GregorianCalAndDurSerDataUtil
 */

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Formatter;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
import javax.xml.datatype.XMLGregorianCalendar;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Utility to generate the java source file for Gregorian Calendar and Duration serialized data
 * for specific version of JDK to be added in SerializationTest. Execute this test with desired version
 * of JDK to generate the java source file.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class GregorianCalAndDurSerDataUtil {
    static String JDK = "JDK" + System.getProperty("java.version");
    static String testsrc = System.getProperty("test.src");
    final static String EXPECTED_CAL = "0001-01-01T00:00:00.0000000-05:00";
    final static String EXPECTED_DURATION = "P1Y1M1DT1H1M1S";
    String srcFilePrefix = JDK.toUpperCase().replace("-", "_");


    /**
     * Create the serialized Bytes array and serialized bytes base64 string for GregorianCalender and Duration
     * with jdk under test and generate the java source file.
     * @throws DatatypeConfigurationException Unexpected.
     * @throws IOException Unexpected.
     */
    @BeforeAll
    public void setup() throws DatatypeConfigurationException, IOException {
        DatatypeFactory dtf = DatatypeFactory.newInstance();
        XMLGregorianCalendar xmlGregorianCalendar = dtf.newXMLGregorianCalendar(EXPECTED_CAL);
        Duration duration = dtf.newDuration(EXPECTED_DURATION);
        String copyRightStr = GregorianCalAndDurSerDataTemplate.ORACLE_COPY_RIGHT;
        String classStr = GregorianCalAndDurSerDataTemplate.GREGO_CAL_DUR_SER_CLASS;
        try(ByteArrayOutputStream baos = new ByteArrayOutputStream(); ObjectOutputStream oos = new ObjectOutputStream(baos);
            ByteArrayOutputStream baos2 = new ByteArrayOutputStream(); ObjectOutputStream oos2 = new ObjectOutputStream(baos2)) {
            //Serialize the given xmlGregorianCalendar
            oos.writeObject(xmlGregorianCalendar);
            //Serialize the given xml Duration
            oos2.writeObject(duration);
            Files.deleteIfExists(Path.of(testsrc,srcFilePrefix+"GregorianCalendarAndDurationSerData.java"));

            copyRightStr = String.format(copyRightStr, LocalDate.now().getYear(), JDK);
            classStr = String.format(classStr, srcFilePrefix, generatePseudoCodeForGregCalSerBytes(baos),
                    generatePseudoCodeForDurationSerBytes(baos2));
            String srcStr = copyRightStr + "\n" + classStr;
            Files.writeString(Path.of(testsrc,srcFilePrefix+"GregorianCalendarAndDurationSerData.java"), srcStr);
        }
    }

    /**
     * Verify that Java source file is created.
     */
    @Test
    void testFileCreated() {
        assertTrue(Files.exists(Path.of(testsrc,srcFilePrefix+"GregorianCalendarAndDurationSerData.java")));
    }

    /**
     * Generates the Java Pseudo code for serialized Gregorian Calendar byte array.
     * @param baos Serialized GregorianCalendar ByteArrayOutputStream.
     * @return pseudocode String for serialized Gregorian Calendar byte array.
     */
    public static String generatePseudoCodeForGregCalSerBytes(ByteArrayOutputStream baos) {
        byte [] bytes = baos.toByteArray();
        StringBuilder sb = new StringBuilder(bytes.length * 5);
        sb.append("private final byte[] gregorianCalendarBytes = {");
        return generatePseudoCode(sb, bytes);
    }

    /**
     * Generates the Java Pseudo code for serialized Duration byte array.
     * @param baos Serialized Duration ByteArrayOutputStream.
     * @return pseudocode String for serialized Duration byte array.
     */
    public static String generatePseudoCodeForDurationSerBytes(ByteArrayOutputStream baos) {
        byte [] bytesdur = baos.toByteArray();
        StringBuilder sb = new StringBuilder(bytesdur.length * 5);
        sb.append("private final byte[] durationBytes = {");
        return generatePseudoCode(sb, bytesdur);
    }

    private static String generatePseudoCode(StringBuilder sb, byte [] bytes) {
        final int linelen = 8;
//        HexFormat hex = HexFormat.of().withPrefix(" (byte) 0x").withSuffix(",");
//        for (int i = 0; i < bytes.length; i += linelen) {
//            sb.append("\n");
//            sb.append(hex.formatHex(bytes, i, Math.min(i + linelen, bytes.length)));
//        }
//        sb.append("};");
        Formatter fmt = new Formatter(sb);
        for (int i = 0; i <bytes.length; i++) {
            if (i % linelen == 0) {
                fmt.format("%n           ");
            }
            fmt.format(" (byte) 0x%x,", bytes[i] & 0xff);
        }
        fmt.format("%n    };%n");
        return sb.toString();
    }
}
