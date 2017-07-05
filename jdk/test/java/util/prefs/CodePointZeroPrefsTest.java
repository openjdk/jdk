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
import java.lang.reflect.Constructor;
import java.util.prefs.Preferences;
import java.util.prefs.PreferencesFactory;

/*
 * @test
 * @bug 8068373
 * @summary Ensure writing a code point U+0000 null control character is detected.
 */
public class CodePointZeroPrefsTest
{
    public static void main(String[] args) throws Exception
    {
        int failures = 0;

        // Deliberately reflect so you can reproduce it on any platform.
        Constructor<? extends PreferencesFactory> constructor =
            Class.forName("java.util.prefs.FileSystemPreferencesFactory").asSubclass(PreferencesFactory.class).getDeclaredConstructor();
        constructor.setAccessible(true);
        PreferencesFactory factory = constructor.newInstance();

        Preferences node = factory.userRoot().node("com/acme/testing");

        // legal key and value
        try {
            node.put("a", "1");
        } catch (IllegalArgumentException iae) {
            System.err.println("Unexpected IllegalArgumentException for legal key");
            failures++;
        }

        // illegal key only
        int numIAEs = 0;
        try {
            node.put("a\u0000b", "1");
            System.err.println("IllegalArgumentException not thrown for illegal key");
            failures++;
        } catch (IllegalArgumentException iae) {
            // do nothing
        }

        // illegal value only
        numIAEs = 0;
        try {
            node.put("ab", "2\u00003");
            System.err.println("IllegalArgumentException not thrown for illegal value");
            failures++;
        } catch (IllegalArgumentException iae) {
            // do nothing
        }

        // illegal key and value
        numIAEs = 0;
        try {
            node.put("a\u0000b", "2\u00003");
            System.err.println("IllegalArgumentException not thrown for illegal entry");
            failures++;
        } catch (IllegalArgumentException iae) {
            // do nothing
        }

        if (failures != 0) {
            throw new RuntimeException("CodePointZeroPrefsTest failed with "
                + failures + " errors!");
        }
    }
}
