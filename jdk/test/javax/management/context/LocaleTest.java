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
 * @test LocaleTest.java
 * @bug 5072267
 * @summary Test client locales.
 * @author Eamonn McManus
 */

import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.ListResourceBundle;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;
import javax.management.ClientContext;
import java.util.Arrays;
import javax.management.MBeanServer;
import javax.management.ObjectName;

public class LocaleTest {
    private static String failure;

    public static void main(String[] args) throws Exception {

        // Test the translation String -> Locale

        Locale[] locales = Locale.getAvailableLocales();
        System.out.println("Testing String->Locale for " + locales.length +
                " locales");
        for (Locale loc : locales) {
            Map<String, String> ctx = Collections.singletonMap(
                    ClientContext.LOCALE_KEY, loc.toString());
            Locale loc2 = ClientContext.doWithContext(
                    ctx, new Callable<Locale>() {
                public Locale call() {
                    return ClientContext.getLocale();
                }
            });
            assertEquals(loc, loc2);
        }

        // Test that a locale-sensitive attribute works

        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        mbs = ClientContext.newContextForwarder(mbs, null);
        ObjectName name = new ObjectName("a:type=LocaleSensitive");
        mbs.registerMBean(new LocaleSensitive(), name);
        Locale.setDefault(Locale.US);

        assertEquals("spectacular failure",
                mbs.getAttribute(name, "LastProblemDescription"));

        MBeanServer frmbs = ClientContext.withContext(
                mbs, ClientContext.LOCALE_KEY, Locale.FRANCE.toString());
        assertEquals("\u00e9chec r\u00e9tentissant",
                frmbs.getAttribute(name, "LastProblemDescription"));

        if (failure == null)
            System.out.println("TEST PASSED");
        else
            throw new Exception("TEST FAILED: " + failure);
    }

    public static interface LocaleSensitiveMBean {
        public String getLastProblemDescription();
    }

    public static class LocaleSensitive implements LocaleSensitiveMBean {
        public String getLastProblemDescription() {
            Locale loc = ClientContext.getLocale();
            ResourceBundle rb = ResourceBundle.getBundle(
                    MyResources.class.getName(), loc);
            return rb.getString("spectacular");
        }
    }

    public static class MyResources extends ListResourceBundle {
        protected Object[][] getContents() {
            return new Object[][] {
                {"spectacular", "spectacular failure"},
            };
        }
    }

    public static class MyResources_fr extends ListResourceBundle {
        protected Object[][] getContents() {
            return new Object[][] {
                {"spectacular", "\u00e9chec r\u00e9tentissant"},
            };
        }
    }

    private static void assertEquals(Object x, Object y) {
        if (!equal(x, y))
            failed("expected " + string(x) + "; got " + string(y));
    }

    private static boolean equal(Object x, Object y) {
        if (x == y)
            return true;
        if (x == null || y == null)
            return false;
        if (x.getClass().isArray())
            return Arrays.deepEquals(new Object[] {x}, new Object[] {y});
        return x.equals(y);
    }

    private static String string(Object x) {
        String s = Arrays.deepToString(new Object[] {x});
        return s.substring(1, s.length() - 1);
    }

    private static void failed(String why) {
        failure = why;
        new Throwable("FAILED: " + why).printStackTrace(System.out);
    }
}
