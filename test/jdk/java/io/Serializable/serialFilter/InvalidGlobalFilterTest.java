/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.util.Map;

/*
 * @test
 * @bug 8278087
 * @summary Test that an invalid pattern value for the jdk.serialFilter system property causes an
 * exception to be thrown when an attempt is made to use the filter or deserialize.
 * A subset of invalid filter patterns is tested.
 * @run testng/othervm -Djdk.serialFilter=.* InvalidGlobalFilterTest
 * @run testng/othervm -Djdk.serialFilter=! InvalidGlobalFilterTest
 * @run testng/othervm -Djdk.serialFilter=/ InvalidGlobalFilterTest
 *
 */
@Test
public class InvalidGlobalFilterTest {
    private static final String serialPropName = "jdk.serialFilter";
    private static final String serialFilter = System.getProperty(serialPropName);

    static {
        // Enable logging
        System.setProperty("java.util.logging.config.file",
                System.getProperty("test.src", ".") + "/logging.properties");
    }

    /**
     * Map of invalid patterns to the expected exception message.
     */
    private static final Map<String, String> invalidMessages =
            Map.of(".*", "Invalid jdk.serialFilter: package missing in: \".*\"",
                    ".**", "Invalid jdk.serialFilter: package missing in: \".**\"",
                    "!", "Invalid jdk.serialFilter: class or package missing in: \"!\"",
                    "/java.util.Hashtable", "Invalid jdk.serialFilter: module name is missing in: \"/java.util.Hashtable\"",
                    "java.base/", "Invalid jdk.serialFilter: class or package missing in: \"java.base/\"",
                    "/", "Invalid jdk.serialFilter: module name is missing in: \"/\"");

    @DataProvider(name = "MethodsToCall")
    private Object[][] cases() {
        return new Object[][] {
                {serialFilter, "getSerialFilter", (Assert.ThrowingRunnable) () -> ObjectInputFilter.Config.getSerialFilter()},
                {serialFilter, "setSerialFilter", (Assert.ThrowingRunnable) () -> ObjectInputFilter.Config.setSerialFilter(new NoopFilter())},
                {serialFilter, "new ObjectInputStream(is)", (Assert.ThrowingRunnable) () -> new ObjectInputStream(new ByteArrayInputStream(new byte[0]))},
                {serialFilter, "new OISSubclass()", (Assert.ThrowingRunnable) () -> new OISSubclass()},
        };
    }

    /**
     * Test each method that should throw IllegalStateException based on
     * the invalid arguments it was launched with.
     */
    @Test(dataProvider = "MethodsToCall")
    public void initFaultTest(String pattern, String method, Assert.ThrowingRunnable runnable) {

        IllegalStateException ex = Assert.expectThrows(IllegalStateException.class,
                runnable);

        String expected = invalidMessages.get(serialFilter);
        if (expected == null) {
            Assert.fail("No expected message for filter: " + serialFilter);
        }
        System.out.println(ex.getMessage());
        Assert.assertEquals(ex.getMessage(), expected, "wrong message");
    }

    private static class NoopFilter implements ObjectInputFilter {
        /**
         * Returns UNDECIDED.
         *
         * @param filter the FilterInfo
         * @return Status.UNDECIDED
         */
        public ObjectInputFilter.Status checkInput(FilterInfo filter) {
             return ObjectInputFilter.Status.UNDECIDED;
        }

        public String toString() {
            return "NoopFilter";
        }
    }

    /**
     * Subclass of ObjectInputStream to test subclassing constructor.
     */
    private static class OISSubclass extends ObjectInputStream {

        protected OISSubclass() throws IOException {
        }
    }

}
