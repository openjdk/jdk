/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8145039
 * @summary Check that marshalling of xjc generated class doesn't throw
 *          ClassCast exception.
 * @modules javax.xml.bind
 * @library /lib/testlibrary
 * @run testng/othervm JaxbMarshallTest
 */

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import java.util.Arrays;
import java.util.List;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import jdk.testlibrary.JDKToolLauncher;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class JaxbMarshallTest {

    @BeforeTest
    public void setUp() throws IOException {
        // Create test directory inside scratch
        testWorkDir = Paths.get(System.getProperty("user.dir", "."));
        // Save its URL
        testWorkDirUrl = testWorkDir.toUri().toURL();
        // Get test source directory path
        testSrcDir = Paths.get(System.getProperty("test.src", "."));
        // Get path of xjc result folder
        xjcResultDir = testWorkDir.resolve(TEST_PACKAGE);
        // Copy schema document file to scratch directory
        Files.copy(testSrcDir.resolve(XSD_FILENAME), testWorkDir.resolve(XSD_FILENAME), REPLACE_EXISTING);
    }


    /*
     * Test does the following steps to reproduce problem reported by 8145039:
     * 1. Copy test schema to JTREG scratch folder
     * 2. Run xjc on test schema file
     * 3. Compile generated java files with test javac
     * 4. Marshall the new list instance to ensure that
     *    ClassCastException is not thrown
     */
    @Test
    public void marshallClassCastExceptionTest() throws Exception {
        JAXBContext jaxbContext;
        Marshaller marshaller;
        URLClassLoader jaxbContextClassLoader;
        // Generate java classes by xjc
        runXjc(XSD_FILENAME);
        // Compile xjc generated java files
        compileXjcGeneratedClasses();

        // Create JAXB context based on xjc generated package.
        // Need to create URL class loader ot make compiled classes discoverable
        // by JAXB context
        jaxbContextClassLoader = URLClassLoader.newInstance(new URL[] {testWorkDirUrl});
        jaxbContext = JAXBContext.newInstance( TEST_PACKAGE, jaxbContextClassLoader);

        // Create instance of Xjc generated data type.
        // Java classes were compiled during the test execution hence reflection
        // is needed here
        Class classLongListClass = jaxbContextClassLoader.loadClass(TEST_CLASS);
        Object objectLongListClass = classLongListClass.newInstance();
        // Get 'getIn' method object
        Method getInMethod = classLongListClass.getMethod( GET_LIST_METHOD, (Class [])null );
        // Invoke 'getIn' method
        List<Long> inList = (List<Long>)getInMethod.invoke(objectLongListClass);
        // Add values into the jaxb object list
        inList.add(Long.valueOf(0));
        inList.add(Long.valueOf(43));
        inList.add(Long.valueOf(1000000123));

        // Marshall constructed complex type variable to standard output.
        // In case of failure the ClassCastException will be thrown
        marshaller = jaxbContext.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        marshaller.marshal(objectLongListClass, System.out);
    }

    // Compile schema file into java classes definitions
    void runXjc(String xsdFileName) throws Exception {
        // Prepare process builder to run schemagen tool and save its output
        JDKToolLauncher xjcLauncher = JDKToolLauncher.createUsingTestJDK("xjc");
        xjcLauncher.addToolArg(xsdFileName);
        System.out.println("Executing xjc command: " + Arrays.asList(xjcLauncher.getCommand()));
        ProcessBuilder pb = new ProcessBuilder(xjcLauncher.getCommand());
        // Set xjc work directory with the input java file
        pb.directory(testWorkDir.toFile());
        pb.inheritIO();
        Process p = pb.start();
        p.waitFor();
        p.destroy();
    }

    // Compile java classes with javac tool
    void compileXjcGeneratedClasses() throws Exception {
        JDKToolLauncher javacLauncher = JDKToolLauncher.createUsingTestJDK("javac");
        javacLauncher.addToolArg(xjcResultDir.resolve("ObjectFactory.java").toString());
        javacLauncher.addToolArg(xjcResultDir.resolve("TypesLongList.java").toString());
        javacLauncher.addToolArg(xjcResultDir.resolve("package-info.java").toString());
        System.out.println("Compiling xjc generated classes: " + Arrays.asList(javacLauncher.getCommand()));
        ProcessBuilder pb = new ProcessBuilder(javacLauncher.getCommand());
        pb.inheritIO();
        pb.directory(testWorkDir.toFile());
        Process p = pb.start();
        p.waitFor();
        p.destroy();
    }

    // Test schema filename
    static final String XSD_FILENAME = "testSchema.xsd";
    // Package of java classes generated by xjc
    static final String TEST_PACKAGE = "testns_package";
    // Name of generated java class
    static final String TEST_CLASS = TEST_PACKAGE+".TypesLongList";
    // Method to get the list from xjc generated class
    static final String GET_LIST_METHOD = "getIn";
    // Test working directory
    Path testWorkDir;
    // Test working directory URL
    URL testWorkDirUrl;
    // Directory with test src
    Path testSrcDir;
    // Directory with java files generated by xjc
    Path xjcResultDir;
}
