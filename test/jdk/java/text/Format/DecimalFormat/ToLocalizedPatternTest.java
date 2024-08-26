/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8282929 8326908
 * @summary Verify DecimalFormat::toLocalizedPattern correctness.
 * @run junit ToLocalizedPatternTest
 */

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class ToLocalizedPatternTest {
    private static final char MONETARY_GROUPING = 'g';
    private static final char MONETARY_DECIMAL = 'd';

    // Verifies that the toLocalizedPattern() method correctly returns
    // monetary symbols for a currency formatter.
    @Test
    public void monetarySymbolsTest() {
        var dfs = new DecimalFormatSymbols(Locale.US);

        // Customize the decimal format symbols
        dfs.setMonetaryGroupingSeparator(MONETARY_GROUPING);
        dfs.setMonetaryDecimalSeparator(MONETARY_DECIMAL);

        // create a currency formatter
        var cf = (DecimalFormat)DecimalFormat.getCurrencyInstance(Locale.US);
        cf.setDecimalFormatSymbols(dfs);

        // check
        assertEquals(cf.toLocalizedPattern(),
            cf.toPattern()
               .replace(',', MONETARY_GROUPING)
               .replace('.', MONETARY_DECIMAL));
    }

    // Verify some common symbols are enforced with the DFS
    @Test
    public void useDFSWhenLocalizedTest() {
        DecimalFormatSymbols dfs = new DecimalFormatSymbols();
        dfs.setGroupingSeparator('a');
        dfs.setDecimalSeparator('b');
        dfs.setDigit('c');
        dfs.setZeroDigit('d');
        // Create a DecimalFormat that utilizes the above symbols
        DecimalFormat dFmt = new DecimalFormat("###,##0.###", dfs);
        assertEquals("caccdbccc", dFmt.toLocalizedPattern());
    }
}
