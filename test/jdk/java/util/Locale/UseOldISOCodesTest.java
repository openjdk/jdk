/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8295232
 * @summary Ensures java.locale.useOldISOCodes is statically initialized
 * @library /test/lib
 * @run junit UseOldISOCodesTest
 */

import java.util.Locale;
import jdk.test.lib.process.ProcessTools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UseOldISOCodesTest {

    // Ensure java.locale.useOldISOCodes is only interpreted at runtime startup
    @Test
    public void staticInitializationTest() throws Exception {
        ProcessTools.executeTestJava("-Djava.locale.useOldISOCodes=true", "UseOldISOCodesTest$Runner")
                .outputTo(System.out)
                .errorTo(System.err)
                .shouldHaveExitValue(0);
    }

    static class Runner {
        private static final String obsoleteCode = "iw";
        private static final String newCode = "he";

        public static void main(String[] args) {
            // Should have no effect
            System.setProperty("java.locale.useOldISOCodes", "false");
            Locale locale = Locale.of(newCode);
            assertEquals(obsoleteCode, locale.getLanguage(),
                    "newCode 'he' was not mapped to 'iw' with useOldISOCodes=true");
        }
    }
}
