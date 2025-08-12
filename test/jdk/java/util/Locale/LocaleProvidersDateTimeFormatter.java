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
 * @bug 8248695
 * @summary Test any java.time.DateTimeFormatter Locale provider related issues
 * @library /test/lib
 * @build LocaleProviders
 * @modules java.base/sun.util.locale.provider
 * @run junit/othervm LocaleProvidersDateTimeFormatter
 */

import org.junit.jupiter.api.Test;

public class LocaleProvidersDateTimeFormatter {

    /*
     * 8248695: Ensure DateTimeFormatter::ofLocalizedDate does not throw exception
     * under HOST (date only pattern leaks time field)
     */
    @Test
    public void dateOnlyJavaTimePattern() throws Throwable {
        LocaleProviders.test("HOST", "bug8248695Test");
    }
}
