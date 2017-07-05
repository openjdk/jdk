/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6989440
 * @summary Verify ConcurrentModificationException is not thrown with multiple
 *     thread accesses.
 * @compile -XDignore.symbol.file=true Bug6989440.java
 * @run main Bug6989440
 */
import java.text.spi.DateFormatProvider;
import java.util.spi.LocaleNameProvider;
import java.util.spi.LocaleServiceProvider;
import java.util.spi.TimeZoneNameProvider;

import sun.util.LocaleServiceProviderPool;

public class Bug6989440 {
    public static void main(String[] args) {
        TestThread t1 = new TestThread(LocaleNameProvider.class);
        TestThread t2 = new TestThread(TimeZoneNameProvider.class);
        TestThread t3 = new TestThread(DateFormatProvider.class);

        t1.start();
        t2.start();
        t3.start();
    }

    static class TestThread extends Thread {
        private Class<? extends LocaleServiceProvider> cls;

        public TestThread(Class<? extends LocaleServiceProvider> providerClass) {
            cls = providerClass;
        }

        public void run() {
            LocaleServiceProviderPool pool = LocaleServiceProviderPool.getPool(cls);
            pool.getAvailableLocales();
        }
    }
}
