/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8390296
 * @summary Verifies MultiUIDefaults#containsKey and
 *          MultiUIDefaults#keySet().contains(key) produce same result
 * @run main MultiUIDefaultsContainsKeyTest
 */

import javax.swing.UIManager;
import javax.swing.UIDefaults;
import javax.swing.LookAndFeel;

public class MultiUIDefaultsContainsKeyTest {
    private static final String KEY = "TabbedPane.isTabRollover";

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel(new KeyProvidingLookAndFeel());

        boolean containsKey = UIManager.getDefaults().containsKey(KEY);
        boolean keySetContains = UIManager.getDefaults().keySet().contains(KEY);

        System.out.println("key: " + KEY);
        System.out.println("defaults class: "
                + UIManager.getDefaults().getClass().getName());
        System.out.println("containsKey: " + containsKey);
        System.out.println("keySet().contains: " + keySetContains);

        if (containsKey != keySetContains) {
            throw new RuntimeException("containsKey and keySet().contains disagree");
        }

        System.out.println("No inconsistency observed.");
    }

    private static final class KeyProvidingLookAndFeel extends LookAndFeel {
        @Override
        public String getName() {
            return "auxiliary Look and Feel";
        }

        @Override
        public String getID() {
            return "Auxiliary";
        }

        @Override
        public String getDescription() {
            return "Adds a defaults key for the MultiUIDefaults reproducer";
        }

        @Override
        public boolean isNativeLookAndFeel() {
            return false;
        }

        @Override
        public boolean isSupportedLookAndFeel() {
            return true;
        }

        @Override
        public UIDefaults getDefaults() {
            UIDefaults defaults = new UIDefaults();
            defaults.put(KEY, Boolean.TRUE);
            return defaults;
        }
    }
}
