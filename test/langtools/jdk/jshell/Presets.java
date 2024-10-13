/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.net.InetAddress;
import java.util.*;

public class Presets {
    public static final String TEST_DEFAULT_EXECUTION;
    public static final String TEST_STANDARD_EXECUTION;

    static {
        String loopback = InetAddress.getLoopbackAddress().getHostAddress();

        TEST_DEFAULT_EXECUTION = "failover:0(jdi:hostname(" + loopback + "))," +
                                 "1(jdi:launch(true)), 2(jdi), 3(local)";
        TEST_STANDARD_EXECUTION = "failover:0(jdi:hostname(" + loopback + "))," +
                                  "1(jdi:launch(true)), 2(jdi)";
    }

    public static String[] addExecutionIfMissing(String[] args) {
        if (Arrays.stream(args).noneMatch(Presets::remoteRelatedOption)) {
            List<String> augmentedArgs = new ArrayList<>();

            augmentedArgs.add("--execution");
            augmentedArgs.add(Presets.TEST_DEFAULT_EXECUTION);
            augmentedArgs.addAll(List.of(args));

            return augmentedArgs.toArray(s -> new String[s]);
        }

        return args;
    }

    private static boolean remoteRelatedOption(String option) {
        return "--execution".equals(option) ||
               "--add-modules".equals(option) ||
               option.startsWith("-R");
    }
}
