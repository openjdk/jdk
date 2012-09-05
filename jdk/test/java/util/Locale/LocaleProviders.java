/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
import java.text.spi.DateFormatProvider;
import java.util.Locale;
import sun.util.locale.provider.LocaleProviderAdapter;

public class LocaleProviders {

    public static void main(String[] args) {
        String expected = args[0];
        Locale testLocale = new Locale(args[1], args[2]);
        String preference = System.getProperty("java.locale.providers", "");
        LocaleProviderAdapter lda = LocaleProviderAdapter.getAdapter(DateFormatProvider.class, testLocale);
        LocaleProviderAdapter.Type type = lda.getAdapterType();
        System.out.printf("testLocale: %s, got: %s, expected: %s\n", testLocale, type, expected);
        if (!type.toString().equals(expected)) {
            throw new RuntimeException("Returned locale data adapter is not correct.");
        }
    }
}
