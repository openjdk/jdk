/*
 * Copyright (c) 2008, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * See OldMappingTest.sh
 */

import java.lang.reflect.*;
import java.util.*;

public class OldIDMappingTest {
    private static final String MAPPING_PROPERTY_NAME = "sun.timezone.ids.oldmapping";
    private static final Map<String, String> newmap = new HashMap<String, String>();
    static {
        // Add known new mappings
        newmap.put("EST", "EST");
        newmap.put("MST", "MST");
        newmap.put("HST", "HST");
    }

    public static void main(String[] args) {
        boolean useOldMapping = true;
        String arg = args[0];
        if (arg.equals("-new")) {
            useOldMapping = false;
        } else if (arg.equals("-old")) {
            useOldMapping = true;
        } else {
            throw new RuntimeException("-old or -new must be specified; got " + arg);
        }

        Map<String, String> oldmap = TzIDOldMapping.MAP;
        String prop = System.getProperty(MAPPING_PROPERTY_NAME);
        System.out.println(MAPPING_PROPERTY_NAME + "=" + prop);

        // Try the test multiple times with modifying TimeZones to
        // make sure TimeZone instances for the old mapping are
        // properly copied (defensive copy).
        for (int count = 0; count < 3; count++) {
            for (String id : oldmap.keySet()) {
                TimeZone tzAlias = TimeZone.getTimeZone(id);
                TimeZone tz = TimeZone.getTimeZone(oldmap.get(id));
                if (useOldMapping) {
                    if (!tzAlias.hasSameRules(tz)) {
                        throw new RuntimeException("OLDMAP: " + MAPPING_PROPERTY_NAME + "=" + prop + ": "
                                                   + id + " isn't an alias of " + oldmap.get(id));
                    }
                    if (count == 0) {
                        System.out.println("    " + id + " => " + oldmap.get(id));
                    }
                    tzAlias.setRawOffset(tzAlias.getRawOffset() * count);
                } else {
                    if (!newmap.containsKey(id)) {
                        // ignore ids not contained in the new map
                        if (count == 0) {
                            System.out.println("    " + id + " => " + oldmap.get(id));
                        }
                        tzAlias.setRawOffset(tzAlias.getRawOffset() * count);
                        continue;
                    }
                    if (tzAlias.hasSameRules(tz)) {
                        throw new RuntimeException("NEWMAP: " + MAPPING_PROPERTY_NAME + "=" + prop + ": "
                                                   + id + " is an alias of " + oldmap.get(id));
                    }
                    tz = TimeZone.getTimeZone(newmap.get(id));
                    if (!tzAlias.hasSameRules(tz)) {
                        throw new RuntimeException("NEWMAP: " + MAPPING_PROPERTY_NAME + "=" + prop + ": "
                                                   + id + " isn't an alias of " + newmap.get(id));
                    }
                    if (count == 0) {
                        System.out.println("    " + id + " => " + newmap.get(id));
                    }
                    tzAlias.setRawOffset(tzAlias.getRawOffset() * count);
                }
            }
        }
    }
}
