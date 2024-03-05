/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8008577 8138613 8174269
 * @summary Check whether CLDR locale provider adapter is enabled by default
 * @compile -XDignore.symbol.file ExpectedAdapterTypes.java
 * @modules java.base/sun.util.locale.provider
 * @run junit ExpectedAdapterTypes
 */

import java.util.Arrays;
import java.util.List;
import sun.util.locale.provider.LocaleProviderAdapter;

import org.junit.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExpectedAdapterTypes {

    static final LocaleProviderAdapter.Type[] expected = {
        LocaleProviderAdapter.Type.CLDR,
        LocaleProviderAdapter.Type.FALLBACK,
    };

    /**
     * This test ensures LocaleProviderAdapter.getAdapterPreference() returns
     * the correct preferred adapter types. This test should fail whenever a change is made
     * to the implementation and the expected list is not updated accordingly.
     */
    @Test
    public void correctAdapterListTest() {
        List<LocaleProviderAdapter.Type> actualTypes = LocaleProviderAdapter.getAdapterPreference();
        List<LocaleProviderAdapter.Type> expectedTypes = Arrays.asList(expected);
        assertEquals(actualTypes, expectedTypes, String.format("getAdapterPreference() " +
                "returns: %s, but the expected adapter list returns: %s", actualTypes, expectedTypes));
    }
}
