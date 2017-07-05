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

import java.io.*;
import java.util.*;
import java.util.logging.*;

/*
 * @test
 * @bug 8026027
 * @summary Test Level.parse to look up custom levels by name and its
 *          localized name
 *
 * @run main/othervm CustomLevel
 */

public class CustomLevel extends Level {
    public CustomLevel(String name, int value, String resourceBundleName) {
        super(name, value, resourceBundleName);
    }

    private static final List<Level> levels = new ArrayList<>();
    private static final String RB_NAME = "myresource";
    public static void main(String[] args) throws Exception {
        setupCustomLevels();

        // Level.parse will return the custom Level instance
        ResourceBundle rb = ResourceBundle.getBundle(RB_NAME);
        for (Level level : levels) {
            String name = level.getName();
            if (!name.equals("WARNING") && !name.equals("INFO")) {
                // custom level whose name doesn't conflict with any standard one
                checkCustomLevel(Level.parse(name), level);
            }
            String localizedName = rb.getString(level.getName());
            Level l = Level.parse(localizedName);
            if (l != level) {
                throw new RuntimeException("Unexpected level " + l + " " + l.getClass());
            }
        }
    }

    private static void setupCustomLevels() throws IOException {
        levels.add(new CustomLevel("EMERGENCY", 1090, RB_NAME));
        levels.add(new CustomLevel("ALERT", 1060, RB_NAME));
        levels.add(new CustomLevel("CRITICAL", 1030, RB_NAME));
        levels.add(new CustomLevel("WARNING", 1010, RB_NAME));
        levels.add(new CustomLevel("INFO", 1000, RB_NAME));
    }
    static void checkCustomLevel(Level level, Level expected) {
        // Level value must be the same
        if (!level.equals(expected)) {
            throw new RuntimeException(formatLevel(level) + " != " + formatLevel(expected));
        }

        if (!level.getName().equals(expected.getName())) {
            throw new RuntimeException(formatLevel(level) + " != " + formatLevel(expected));
        }

        // Level.parse is expected to return the custom Level
        if (level != expected) {
            throw new RuntimeException(formatLevel(level) + " != " + formatLevel(expected));
        }

        ResourceBundle rb = ResourceBundle.getBundle(RB_NAME);
        String name = rb.getString(level.getName());
        if (!level.getLocalizedName().equals(name)) {
            // must have the same localized name
            throw new RuntimeException(level.getLocalizedName() + " != " + name);
        }
    }

    static String formatLevel(Level l) {
        return l + ":" + l.intValue() + ":" + l.getClass().getName();
    }
}
