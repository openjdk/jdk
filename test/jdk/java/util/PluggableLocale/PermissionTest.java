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
/*
 *
 */
public class PermissionTest{

    //  Make sure provider impls can be instantiated under a security manager.ZZ
    com.foo.BreakIteratorProviderImpl breakIP = new com.foo.BreakIteratorProviderImpl();
    com.foo.CollatorProviderImpl collatorP = new com.foo.CollatorProviderImpl();
    com.foo.DateFormatProviderImpl dateFP = new com.foo.DateFormatProviderImpl();
    com.foo.DateFormatSymbolsProviderImpl dateFSP = new com.foo.DateFormatSymbolsProviderImpl();
    com.foo.DecimalFormatSymbolsProviderImpl decimalFSP = new com.foo.DecimalFormatSymbolsProviderImpl();
    com.foo.NumberFormatProviderImpl numberFP = new com.foo.NumberFormatProviderImpl();
    com.bar.CurrencyNameProviderImpl currencyNP = new com.bar.CurrencyNameProviderImpl();
    com.bar.CurrencyNameProviderImpl2 currencyNP2 = new com.bar.CurrencyNameProviderImpl2();
    com.bar.LocaleNameProviderImpl localeNP = new com.bar.LocaleNameProviderImpl();
    com.bar.TimeZoneNameProviderImpl tzNP = new com.bar.TimeZoneNameProviderImpl();
    com.bar.GenericTimeZoneNameProviderImpl tzGenNP = new com.bar.GenericTimeZoneNameProviderImpl();
    com.bar.CalendarDataProviderImpl calDataP = new com.bar.CalendarDataProviderImpl();
    com.bar.CalendarNameProviderImpl calNameP = new com.bar.CalendarNameProviderImpl();

    public static void main(String[] s) {
        new PermissionTest();
    }
}
