/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8005698
 * @summary Check operation of jdk.map.useRandomSeed property
 * @run main CheckRandomHashSeed
 * @run main/othervm -Djdk.map.useRandomSeed=false CheckRandomHashSeed
 * @run main/othervm -Djdk.map.useRandomSeed=bogus CheckRandomHashSeed
 * @run main/othervm -Djdk.map.useRandomSeed=true CheckRandomHashSeed true
 * @author Brent Christian
 */
import java.lang.reflect.Field;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Hashtable;
import java.util.WeakHashMap;

public class CheckRandomHashSeed {
    private final static String PROP_NAME = "jdk.map.useRandomSeed";
    static boolean expectRandom = false;

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("true")) {
            expectRandom = true;
        }
        String hashSeedProp = System.getProperty(PROP_NAME);
        boolean propSet = (null != hashSeedProp)
                ? Boolean.parseBoolean(hashSeedProp) : false;
        if (expectRandom != propSet) {
            throw new Error("Error in test setup: " + (expectRandom ? "" : "not " ) + "expecting random hashSeed, but " + PROP_NAME + " is " + (propSet ? "" : "not ") + "enabled");
        }

        testMap(new WeakHashMap());
        testMap(new Hashtable());
    }

    private static void testMap(Map map) {
        int hashSeed = getHashSeed(map);
        boolean hashSeedIsZero = (hashSeed == 0);

        if (expectRandom != hashSeedIsZero) {
            System.out.println("Test passed for " + map.getClass().getSimpleName() + " - expectRandom: " + expectRandom + ", hashSeed: " + hashSeed);
        } else {
            throw new Error ("Test FAILED for " + map.getClass().getSimpleName() + " -  expectRandom: " + expectRandom + ", hashSeed: " + hashSeed);
        }
    }

    private static int getHashSeed(Map map) {
        try {
            if (map instanceof HashMap || map instanceof LinkedHashMap) {
                map.put("Key", "Value");
                Field hashSeedField = HashMap.class.getDeclaredField("hashSeed");
                hashSeedField.setAccessible(true);
                int hashSeed = hashSeedField.getInt(map);
                return hashSeed;
            } else {
                map.put("Key", "Value");
                Field hashSeedField = map.getClass().getDeclaredField("hashSeed");
                hashSeedField.setAccessible(true);
                int hashSeed = hashSeedField.getInt(map);
                return hashSeed;
            }
        } catch(Exception e) {
            e.printStackTrace();
            throw new Error(e);
        }
    }
}
