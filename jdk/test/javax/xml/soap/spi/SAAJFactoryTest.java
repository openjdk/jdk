/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import javax.xml.soap.MessageFactory;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/*
 * @test
 * @bug 8131334
 * @summary SAAJ Plugability Layer: using java.util.ServiceLoader
 *
 * There are unsafe scenarios not to be run within jdk build (relying on global jdk confguration)
 *
 * unsafe; not running:
 *
 * run main/othervm SAAJFactoryTest saaj.factory.Valid -
 *      scenario1 javax.xml.soap.MessageFactory=saaj.factory.Valid -
 * run main/othervm SAAJFactoryTest - javax.xml.soap.SOAPException
 *      scenario3 javax.xml.soap.MessageFactory=non.existing.FactoryClass -
 * run main/othervm SAAJFactoryTest - javax.xml.soap.SOAPException
 *      scenario4 javax.xml.soap.MessageFactory=saaj.factory.Invalid -
 * run main/othervm -Djavax.xml.soap.MessageFactory=saaj.factory.Valid3 SAAJFactoryTest saaj.factory.Valid3 -
 *      scenario13 javax.xml.soap.MessageFactory=saaj.factory.Valid saaj.factory.Valid2
 * run main/othervm SAAJFactoryTest saaj.factory.Valid -
 *      scenario14 javax.xml.soap.MessageFactory=saaj.factory.Valid saaj.factory.Valid2 -
 *
 * @build saaj.factory.*
 *
 * @run main/othervm SAAJFactoryTest com.sun.xml.internal.messaging.saaj.soap.ver1_1.SOAPMessageFactory1_1Impl -
 *      scenario2 - -
 * @run main/othervm -Djavax.xml.soap.MessageFactory=saaj.factory.Valid SAAJFactoryTest saaj.factory.Valid -
 *      scenario5 - -
 * @run main/othervm -Djavax.xml.soap.MessageFactory=saaj.factory.NonExisting SAAJFactoryTest
 *      - javax.xml.soap.SOAPException
 *      scenario6 - -
 * @run main/othervm -Djavax.xml.soap.MessageFactory=saaj.factory.Invalid SAAJFactoryTest - javax.xml.soap.SOAPException
 *      scenario7 - -
 * @run main/othervm SAAJFactoryTest saaj.factory.Valid -
 *      scenario8 - saaj.factory.Valid
 * @run main/othervm SAAJFactoryTest saaj.factory.Valid -
 *      scenario9 - saaj.factory.Valid
 * @run main/othervm SAAJFactoryTest - javax.xml.soap.SOAPException
 *      scenario10 - saaj.factory.NonExisting
 * @run main/othervm SAAJFactoryTest - javax.xml.soap.SOAPException
 *      scenario11 - saaj.factory.Invalid
 * @run main/othervm SAAJFactoryTest com.sun.xml.internal.messaging.saaj.soap.ver1_1.SOAPMessageFactory1_1Impl -
 *      scenario12 - -
 * @run main/othervm SAAJFactoryTest saaj.factory.Valid -
 *      scenario15 - saaj.factory.Valid
 */
public class SAAJFactoryTest {

    // scenario name - just for logging
    static String scenario;

    // configuration to be created by the test
    static Path providersDir = Paths.get(System.getProperty("test.classes"), "META-INF", "services");
    static Path providersFile = providersDir.resolve("javax.xml.soap.MessageFactory");

    // configuration to be created by the test
    static Path jdkDir = Paths.get(System.getProperty("java.home"), "conf");
    static Path jdkFile = jdkDir.resolve("jaxm.properties");

    // java policy file for testing w/security manager
    static String policy = System.getProperty("test.src", ".") + File.separator + "test.policy";


    protected MessageFactory factory() throws Throwable {
        try {
            MessageFactory factory = MessageFactory.newInstance();
            System.out.println("     TEST: factory class = [" + factory.getClass().getName() + "]\n");
            return factory;
        } catch (Throwable t) {
            System.out.println("     TEST: Throwable [" + t.getClass().getName() + "] thrown.\n");
            t.printStackTrace();
            throw t;
        }
    }

    protected void test(String[] args) {
        if (args.length < 5) throw new IllegalArgumentException("Incorrect test setup. Required 5 arguments: \n" +
                "   1. expected factory class (if any)\n" +
                "   2. expected \n" +
                "   3. scenario name\n" +
                "   4. jdk/conf configured provider class name\n" +
                "   5. service loader provider class name");

        scenario = args[2]; // scenario name
        prepare(args[3], args[4]); // jdk/conf class, service loader class

        try {
            MessageFactory factory = factory();
            assertTrue(factory != null, "No factory found.");
            String className = factory.getClass().getName();
            assertTrue(args[0].equals(className), "Incorrect factory: [" + className +
                    "], Expected: [" + args[0] + "]");

        } catch (Throwable throwable) {
            String expectedExceptionClass = args[1];
            String throwableClass = throwable.getClass().getName();
            boolean correctException = throwableClass.equals(expectedExceptionClass);
            if (!correctException) {
                throwable.printStackTrace();
            }
            assertTrue(correctException, "Got unexpected exception: [" +
                    throwableClass + "], expected: [" + expectedExceptionClass + "]");
        } finally {
            cleanResource(providersFile);
            cleanResource(providersDir);

            // unsafe; not running:
            // cleanResource(jdkFile);
        }
    }

    private void cleanResource(Path resource) {
        try {
            Files.deleteIfExists(resource);
        } catch (IOException ignored) {
            ignored.printStackTrace();
        }
    }

    private void prepare(String propertiesClassName, String providerClassName) {

        try {
            log("providerClassName = " + providerClassName);
            log("propertiesClassName = " + propertiesClassName);

            setupFile(providersFile, providersDir, providerClassName);

            // unsafe; not running:
            //setupFile(jdkFile, jdkDir, propertiesClassName);

            log(" SETUP OK.");

        } catch (IOException e) {
            log(" SETUP FAILED.");
            e.printStackTrace();
        }
    }

    private void setupFile(Path file, Path dir, String value) throws IOException {
        cleanResource(file);
        if (!"-".equals(value)) {
            log("writing configuration [" + value + "] into file [" + file.toAbsolutePath() + "]");
            Files.createDirectories(dir);
            Files.write(
                    file,
                    value.getBytes(),
                    StandardOpenOption.CREATE);
        }
    }

    private static void assertTrue(boolean condition, String msg) {
        if (!condition) {
            log(" FAILED -  ERROR: " + msg);
            throw new RuntimeException("[" + scenario + "] " + msg);
        } else {
            log(" PASSED.");
        }
    }

    private static void log(String msg) {
        System.out.println("[" + scenario + "] " + msg);
    }


    public static void main(String[] args) {
        // no security manager
        new SAAJFactoryTest().test(args);

        System.out.println("Policy file: " + policy);
        System.setProperty("java.security.policy", policy);

        System.out.println("Install security manager...");
        System.setSecurityManager(new SecurityManager());

        // security manager enabled
        new SAAJFactoryTest().test(args);
    }

}

