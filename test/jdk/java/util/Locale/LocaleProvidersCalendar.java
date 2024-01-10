/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8228465 8232871 8257964
 * @summary Test any Calendar Locale provider related issues
 * @library /test/lib
 * @build LocaleProviders
 * @modules java.base/sun.util.locale.provider
 * @run junit/othervm LocaleProvidersCalendar
 */

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.condition.OS.MAC;
import static org.junit.jupiter.api.condition.OS.WINDOWS;

public class LocaleProvidersCalendar {

    /*
     * 8228465 (Windows only): Ensure correct ERA display name under HOST Windows
     */
    @Test
    @EnabledOnOs(WINDOWS)
    public void gregCalEraHost() throws Throwable {
        LocaleProviders.test("HOST", "bug8228465Test");
    }

    /*
     * 8232871 (macOS only): Ensure correct Japanese calendar values under
     * HOST Mac.
     */
    @Test
    @EnabledOnOs(MAC)
    public void japaneseCalValuesHost() throws Throwable {
        LocaleProviders.test("HOST", "bug8232871Test");
    }

    /*
     * 8257964 (macOS/Windows only): Ensure correct Calendar::getMinimalDaysInFirstWeek
     * value under HOST Windows / Mac. Only run against machine with underlying
     * OS locale of en-GB.
     */
    @Test
    @EnabledOnOs({WINDOWS, MAC})
    @EnabledIfSystemProperty(named = "user.language", matches = "en")
    @EnabledIfSystemProperty(named = "user.country", matches = "GB")
    public void minDaysFirstWeekHost() throws Throwable {
        LocaleProviders.test("HOST", "bug8257964Test");
    }
}
