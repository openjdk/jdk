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

/*
 * @test
 * @summary check if LookupPolicy.of correctly handles valid and illegal
 * combinations of characteristics bit mask flags.
 * @run testng LookupPolicyOfTest
 */

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.net.spi.InetAddressResolver.LookupPolicy;
import java.util.List;

import static java.net.spi.InetAddressResolver.LookupPolicy.IPV4;
import static java.net.spi.InetAddressResolver.LookupPolicy.IPV4_FIRST;
import static java.net.spi.InetAddressResolver.LookupPolicy.IPV6;
import static java.net.spi.InetAddressResolver.LookupPolicy.IPV6_FIRST;

public class LookupPolicyOfTest {

    @Test(dataProvider = "validCharacteristics")
    public void testValidCharacteristicCombinations(List<Integer> validCombination) {
        LookupPolicy.of(bitFlagsToCharacteristicsValue(validCombination));
    }

    @Test(dataProvider = "invalidCharacteristics", expectedExceptions = IllegalArgumentException.class)
    public void testInvalidCharacteristicCombinations(List<Integer> invalidCombination) {
        LookupPolicy.of(bitFlagsToCharacteristicsValue(invalidCombination));
    }

    @DataProvider(name = "validCharacteristics")
    public Object[][] validCharacteristicValue() {
        return new Object[][]{
                {List.of(IPV4)},
                {List.of(IPV4, IPV4_FIRST)},
                {List.of(IPV6)},
                {List.of(IPV6, IPV6_FIRST)},
                {List.of(IPV4, IPV6)},
                {List.of(IPV4, IPV6, IPV4_FIRST)},
                {List.of(IPV4, IPV6, IPV6_FIRST)},
                // Custom flag values alongside to address type flags
                // that could be used by custom providers
                {List.of(IPV4, IPV6, 0x10)},
                {List.of(IPV4, IPV6, 0x20)},
        };
    }

    @DataProvider(name = "invalidCharacteristics")
    public Object[][] illegalCharacteristicValue() {
        return new Object[][]{
                {List.of()},
                {List.of(IPV4_FIRST)},
                {List.of(IPV6_FIRST)},
                {List.of(IPV4_FIRST, IPV6_FIRST)},
                {List.of(IPV4, IPV6_FIRST)},
                {List.of(IPV6, IPV4_FIRST)},
                {List.of(IPV4, IPV6, IPV4_FIRST, IPV6_FIRST)},
        };
    }

    private static int bitFlagsToCharacteristicsValue(List<Integer> bitFlagsList) {
        return bitFlagsList.stream()
                .reduce(0, (flag1, flag2) -> flag1 | flag2);
    }
}
