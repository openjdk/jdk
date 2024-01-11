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
 * @bug 7198834 8001440 8013086 8013903 8027289 8232860
 * @summary Test any java.text.Format Locale provider related issues
 * @library /test/lib
 * @build LocaleProviders
 *        providersrc.spi.src.tznp
 *        providersrc.spi.src.tznp8013086
 * @modules java.base/sun.util.locale.provider
 * @run junit/othervm LocaleProvidersFormat
 */

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;

import static org.junit.jupiter.api.condition.OS.MAC;
import static org.junit.jupiter.api.condition.OS.WINDOWS;

public class LocaleProvidersFormat {

    /*
     * 7198834: Ensure under Windows/HOST, adapter does not append an extra space for date patterns.
     */
    @Test
    @EnabledOnOs(WINDOWS)
    public void dateFormatExtraSpace() throws Throwable {
        LocaleProviders.test("HOST", "bug7198834Test");
    }

    /*
     * 8001440: Ensure under CLDR, when number extension of the language
     * tag is invalid, test program does not throw exception when calling
     * NumberFormat::format.
     */
    @Test
    public void formatWithInvalidLocaleExtension() throws Throwable {
        LocaleProviders.test("CLDR", "bug8001440Test");
    }

    /*
     * 8013086: Ensure a custom TimeZoneNameProvider does not cause an NPE
     * in simpleDateFormat, as SimpleDateFormat::matchZoneString expects the
     * name array is fully filled with non-null names.
     */
    @Test
    public void simpleDateFormatWithTZNProvider() throws Throwable {
        LocaleProviders.test("JRE,SPI", "bug8013086Test", "ja", "JP");
        LocaleProviders.test("COMPAT,SPI", "bug8013086Test", "ja", "JP");
    }

    /*
     * 8013903 (Windows only): Ensure HOST adapter with Japanese locale produces
     * the correct Japanese era, month, day names.
     */
    @Test
    @EnabledOnOs(WINDOWS)
    public void windowsJapaneseDateFields() throws Throwable {
        LocaleProviders.test("HOST,JRE", "bug8013903Test");
        LocaleProviders.test("HOST", "bug8013903Test");
        LocaleProviders.test("HOST,COMPAT", "bug8013903Test");
    }

    /*
     * 8027289: Ensure if underlying system format locale is zh_CN, the Window's currency
     * symbol under HOST provider is \u00A5, the yen (yuan) sign.
     */
    @Test
    @EnabledOnOs(WINDOWS)
    @EnabledIfSystemProperty(named = "user.language", matches = "zh")
    @EnabledIfSystemProperty(named = "user.country", matches = "CN")
    public void windowsChineseCurrencySymbol() throws Throwable {
        LocaleProviders.test("JRE,HOST", "bug8027289Test", "FFE5");
        LocaleProviders.test("COMPAT,HOST", "bug8027289Test", "FFE5");
        LocaleProviders.test("HOST", "bug8027289Test", "00A5");
    }

    /*
     * 8232860 (macOS/Windows only): Ensure the Host adapter returns the number
     * pattern for number/integer instances, which require optional fraction digits.
     */
    @Test
    @EnabledOnOs({WINDOWS, MAC})
    public void hostOptionalFracDigits() throws Throwable {
        LocaleProviders.test("HOST", "bug8232860Test");
    }
}
