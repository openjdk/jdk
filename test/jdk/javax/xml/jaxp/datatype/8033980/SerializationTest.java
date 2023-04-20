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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
import javax.xml.datatype.XMLGregorianCalendar;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Verify serialization compatibility for XMLGregorianCalendar and Duration
 * @author huizhe.wang@oracle.com</a>
 */
public class SerializationTest {

    final String FILENAME_CAL = "_XMLGregorianCalendar.ser";
    final String FILENAME_DURATION = "_Duration.ser";
    String filePath;

    {
        filePath = System.getProperty("test.src");
        if (filePath == null) {
            //current directory
            filePath = System.getProperty("user.dir");
        }
        filePath += File.separator;
    }
    final String EXPECTED_CAL = "0001-01-01T00:00:00.0000000-05:00";
    final String EXPECTED_DURATION = "P1Y1M1DT1H1M1S";
    static String[] JDK = {"JDK6", "JDK7", "JDK8", "JDK9"};

    /**
     * Create the Data files.
     * @throws DatatypeConfigurationException
     * @throws IOException
     */
    @BeforeClass
    public void setup() throws DatatypeConfigurationException, IOException {
        DatatypeFactory dtf = DatatypeFactory.newInstance();
        for(String jdkVersion : JDK) {
            if(!Files.exists(Path.of(filePath, jdkVersion+FILENAME_CAL ))) {
                XMLGregorianCalendar c = dtf.newXMLGregorianCalendar(EXPECTED_CAL);
                toFile((Serializable) c, filePath + jdkVersion + FILENAME_CAL);

            }
            if(!Files.exists(Path.of(filePath, jdkVersion+"t"+FILENAME_DURATION ))) {
                Duration d = dtf.newDuration(EXPECTED_DURATION);
                toFile((Serializable) d, filePath + jdkVersion + FILENAME_DURATION);
            }
        }
    }

    /**
     *Delete the data files.
     * @throws IOException
     */
    @AfterClass
    public void cleanup() throws IOException {
        for(String jdkVersion : JDK) {
            Files.deleteIfExists(Path.of(filePath, jdkVersion+FILENAME_CAL ));
            Files.deleteIfExists(Path.of(filePath, jdkVersion+FILENAME_DURATION ));
        }
    }


    /**
     *Provide data for JDK version and Gregorian Calender
     */
    @DataProvider(name = "GregorianCalendar")
    public Object [][]gregorianCalendarData() {
        return new Object [][] {{JDK[0], EXPECTED_CAL}, {JDK[1], EXPECTED_CAL},
                {JDK[2], EXPECTED_CAL}, {JDK[3], EXPECTED_CAL}};
    }

    /**
     * Provide data for JDK version and Duration
     */
    @DataProvider(name = "GregorianDuration")
    public Object [][]gregorianDurationData() {
        return new Object [][] {{JDK[0], EXPECTED_DURATION}, {JDK[1], EXPECTED_DURATION},
                {JDK[2], EXPECTED_DURATION}, {JDK[3], EXPECTED_DURATION}};
    }

    /**
     * verify serialization compatibility for XMLGregorianCalendar
     * @param javaVersion
     * @param gregorianDate
     * @throws IOException
     * @throws ClassNotFoundException
     */
    @Test(dataProvider="GregorianCalendar")
    public void testReadCal(String javaVersion, String gregorianDate) throws IOException, ClassNotFoundException {
        XMLGregorianCalendar d1 = (XMLGregorianCalendar) fromFile(javaVersion + FILENAME_CAL);
        Assert.assertEquals(d1.toString(), gregorianDate);
    }

    /**
     * verify serialization compatibility for Duration
     * @param javaVersion
     * @param gregorianDuration
     * @throws IOException
     * @throws ClassNotFoundException
     */
    @Test(dataProvider = "GregorianDuration")
    public void testReadDuration(String javaVersion, String gregorianDuration) throws IOException, ClassNotFoundException {
        Duration d1 = (Duration) fromFile(javaVersion + FILENAME_DURATION);
        Assert.assertEquals(d1.toString().toUpperCase(), gregorianDuration);
    }

    /**
     * Read the object from a file.
     */
    private static Object fromFile(String filePath) throws IOException,
            ClassNotFoundException {
        InputStream streamIn = SerializationTest.class.getResourceAsStream(
            filePath);
        ObjectInputStream objectinputstream = new ObjectInputStream(streamIn);
        Object o = objectinputstream.readObject();
        objectinputstream.close();
        streamIn.close();
        return o;
    }

    /**
     * Write the object to a file.
     */
    private static void toFile(Serializable o, String filePath) throws IOException {
        FileOutputStream fout = new FileOutputStream(filePath, true);
        ObjectOutputStream oos = new ObjectOutputStream(fout);
        oos.writeObject(o);
        oos.close();
        fout.close();
    }
}
