/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package javax.swing;

import sun.swing.AccessibleAnnounceProvider;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleAnnouncer;

/**
 * This class provides the ability to speak a given string using screen readers.
 */
public class JAccessibleAnnouncer implements AccessibleAnnouncer {

    /**
     * This method checks if an announcing implementation for this platform is available in this build
     *
     * @return announcer instance ore null
     */
    public static JAccessibleAnnouncer getJAccessibleAnnouncer() {
        if (AccessibleAnnounceProvider.isAnnounceExists()) {
            return new JAccessibleAnnouncer();
        }
        return null;
    }

    private JAccessibleAnnouncer() {}

    /**
     * This method makes an announcement with the specified priority from an accessible to which the announcing relates
     *
     * @param a      an accessible to which the announcing relates
     * @param str      string for announcing
     * @param priority priority for announcing
     */
    @Override
    public void announce(Accessible a, String str, int priority) {
        try {
            AccessibleAnnounceProvider.announce(a, str, priority);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
