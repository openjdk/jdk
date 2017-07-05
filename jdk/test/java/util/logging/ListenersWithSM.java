/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 7192275
 * @summary Basic test of addPropertyListener/removePropertyListener methods
 * @run main/othervm ListenersWithSM grant
 * @run main/othervm ListenersWithSM deny
 */

import java.nio.file.Paths;
import java.util.logging.LogManager;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;

public class ListenersWithSM {

    @SuppressWarnings("deprecation")
    public static void main(String[] args) throws Exception {
        boolean granted = args[0].equals("grant");

        // need to get reference to LogManager before setting SecurityManager
        LogManager logman = LogManager.getLogManager();

        // set policy and enable security manager
        if (granted) {
            String testSrc = System.getProperty("test.src");
            if (testSrc == null)
                testSrc = ".";
            System.setProperty("java.security.policy",
                Paths.get(testSrc).resolve("java.policy").toString());
        }
        System.setSecurityManager(new SecurityManager());

        PropertyChangeListener listener = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
            }
        };

        if (granted) {
            // permission granted, no security exception expected
            logman.addPropertyChangeListener(listener);
            logman.removePropertyChangeListener(listener);
        } else {
            // denied
            try {
                logman.addPropertyChangeListener(listener);
                throw new RuntimeException("SecurityException expected");
            } catch (SecurityException expected) { }
            try {
                logman.removePropertyChangeListener(listener);
                throw new RuntimeException("SecurityException expected");
            } catch (SecurityException expected) { }
        }
    }
}
