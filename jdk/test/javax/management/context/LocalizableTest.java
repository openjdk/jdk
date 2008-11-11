/*
 * Copyright 2007 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test LocalizableTest
 * @bug 5072267 6635499
 * @summary Test localizable MBeanInfo using LocalizableMBeanFactory.
 * @author Eamonn McManus
 */

import java.lang.management.ManagementFactory;
import java.util.Locale;
import java.util.ResourceBundle;
import javax.management.ClientContext;
import javax.management.Description;
import javax.management.JMX;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanConstructorInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import localizable.MBeanDescriptions_fr;
import localizable.Whatsit;

import static localizable.WhatsitMBean.*;

public class LocalizableTest {
    // If you change the order of the array elements or their number then
    // you must also change these constants.
    private static final int
            MBEAN = 0, ATTR = 1, OPER = 2, PARAM = 3, CONSTR = 4,
            CONSTR_PARAM = 5;
    private static final String[] englishDescriptions = {
        englishMBeanDescription, englishAttrDescription, englishOperDescription,
        englishParamDescription, englishConstrDescription,
        englishConstrParamDescription,
    };
    private static final String[] defaultDescriptions = englishDescriptions.clone();
    static {
        defaultDescriptions[MBEAN] = defaultMBeanDescription;
    }
    private static final String[] frenchDescriptions = {
        frenchMBeanDescription, frenchAttrDescription, frenchOperDescription,
        frenchParamDescription, frenchConstrDescription,
        frenchConstrParamDescription,
    };

    private static String failure;

    @Description(unlocalizedMBeanDescription)
    public static interface UnlocalizedMBean {}
    public static class Unlocalized implements UnlocalizedMBean {}

    public static void main(String[] args) throws Exception {
        ResourceBundle frenchBundle = new MBeanDescriptions_fr();
        // The purpose of the previous line is to force that class to be compiled
        // when the test is run so it will be available for reflection.
        // Yes, we could do this with a @build tag.

        MBeanServer plainMBS = ManagementFactory.getPlatformMBeanServer();
        MBeanServer unlocalizedMBS =
                ClientContext.newContextForwarder(plainMBS, null);
        MBeanServer localizedMBS =
                ClientContext.newLocalizeMBeanInfoForwarder(plainMBS);
        localizedMBS = ClientContext.newContextForwarder(localizedMBS, null);
        ObjectName name = new ObjectName("a:b=c");

        Whatsit whatsit = new Whatsit();
        Object[][] locales = {
            {null, englishDescriptions},
            {"en", englishDescriptions},
            {"fr", frenchDescriptions},
        };

        for (Object[] localePair : locales) {
            String locale = (String) localePair[0];
            String[] localizedDescriptions = (String[]) localePair[1];
            System.out.println("===Testing locale " + locale + "===");
            for (boolean localized : new boolean[] {false, true}) {
                String[] descriptions = localized ?
                    localizedDescriptions : defaultDescriptions;
                MBeanServer mbs = localized ? localizedMBS : unlocalizedMBS;
                System.out.println("Testing MBean " + whatsit + " with " +
                        "localized=" + localized);
                mbs.registerMBean(whatsit, name);
                System.out.println(mbs.getMBeanInfo(name));
                try {
                    test(mbs, name, locale, descriptions);
                } catch (Exception e) {
                    fail("Caught exception: " + e);
                } finally {
                    mbs.unregisterMBean(name);
                }
            }
        }

        System.out.println("===Testing unlocalizable MBean===");
        Object mbean = new Unlocalized();
        localizedMBS.registerMBean(mbean, name);
        try {
            MBeanInfo mbi = localizedMBS.getMBeanInfo(name);
            assertEquals("MBean description", unlocalizedMBeanDescription,
                    mbi.getDescription());
        } finally {
            localizedMBS.unregisterMBean(name);
        }

        System.out.println("===Testing MBeanInfo.localizeDescriptions===");
        plainMBS.registerMBean(whatsit, name);
        MBeanInfo mbi = plainMBS.getMBeanInfo(name);
        Locale french = new Locale("fr");
        mbi = mbi.localizeDescriptions(french, whatsit.getClass().getClassLoader());
        checkDescriptions(mbi, frenchDescriptions);

        if (failure == null)
            System.out.println("TEST PASSED");
        else
            throw new Exception("TEST FAILED: Last failure: " + failure);
    }

    private static void test(MBeanServer mbs, ObjectName name, String locale,
                             String[] expectedDescriptions)
    throws Exception {
        if (locale != null)
            mbs = ClientContext.withLocale(mbs, new Locale(locale));
        MBeanInfo mbi = mbs.getMBeanInfo(name);
        checkDescriptions(mbi, expectedDescriptions);
    }

    private static void checkDescriptions(MBeanInfo mbi,
                                          String[] expectedDescriptions) {
        assertEquals("MBean description",
                     expectedDescriptions[MBEAN], mbi.getDescription());
        MBeanAttributeInfo mbai = mbi.getAttributes()[0];
        assertEquals("Attribute description",
                     expectedDescriptions[ATTR], mbai.getDescription());
        MBeanOperationInfo mboi = mbi.getOperations()[0];
        assertEquals("Operation description",
                     expectedDescriptions[OPER], mboi.getDescription());
        MBeanParameterInfo mbpi = mboi.getSignature()[0];
        assertEquals("Parameter description",
                     expectedDescriptions[PARAM], mbpi.getDescription());
        MBeanConstructorInfo[] mbcis = mbi.getConstructors();
        assertEquals("Number of constructors", 2, mbcis.length);
        for (MBeanConstructorInfo mbci : mbcis) {
            MBeanParameterInfo[] mbcpis = mbci.getSignature();
            String constrName = mbcpis.length + "-arg constructor";
            assertEquals(constrName + " description",
                    expectedDescriptions[CONSTR], mbci.getDescription());
            if (mbcpis.length > 0) {
                assertEquals(constrName + " parameter description",
                        expectedDescriptions[CONSTR_PARAM],
                        mbcpis[0].getDescription());
            }
        }
    }

    private static void assertEquals(String what, Object expect, Object actual) {
        if (expect.equals(actual))
            System.out.println("...OK: " + what + " = " + expect);
        else
            fail(what + " should be " + expect + ", was " + actual);
    }

    private static void fail(String why) {
        System.out.println("FAIL: " + why);
        failure = why;
    }
}
