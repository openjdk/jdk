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

/*
 * @test
 * @bug 7192275
 * @summary Basic test of addPropertyListener/removePropertyListener methods
 * @run main/othervm Listeners
 */

import java.util.logging.LogManager;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;

public class Listeners {

    static void assertTrue(boolean result, String msg) {
        if (!result)
            throw new RuntimeException(msg);
    }

    /**
     * A {@code PropertyChangeListener} that counts the number of times that
     * the {@code propertyChange} method is fired, and also checks that the
     * event source is the expected (fixed) object.
     */
    static class Listener implements PropertyChangeListener {
        private final Object expectedSource;
        private int fireCount;

        Listener(Object expectedSource) {
            this.expectedSource = expectedSource;
        }

        int fireCount() {
            return fireCount;
        }

        Listener reset() {
            fireCount = 0;
            return this;
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            assertTrue(evt.getSource() == expectedSource, "Unexpected source");
            fireCount++;
        }
    }

    /**
     * Tests that the given listeners are invoked the expected number of
     * times.
     */
    static void test(Listener[] listeners, int... expected) throws Exception {
        // reset counts
        for (Listener listener : listeners) {
            listener.reset();
        }

        // re-reading configuration causes events to be fired
        LogManager.getLogManager().readConfiguration();

        // check event listeners invoked as expected
        for (int i = 0; i < expected.length; i++) {
            assertTrue(listeners[i].fireCount() == expected[i],
                "Unexpected event count");
        }
    }

    @SuppressWarnings("deprecation")
    public static void main(String[] args) throws Exception {
        LogManager logman = LogManager.getLogManager();

        Listener[] listeners = new Listener[2];
        Listener listener1 = listeners[0] = new Listener(LogManager.class);
        Listener listener2 = listeners[1] = new Listener(LogManager.class);

        // add listeners
        logman.addPropertyChangeListener(listener1);
        test(listeners, 1, 0);

        logman.addPropertyChangeListener(listener1);
        test(listeners, 2, 0);

        logman.addPropertyChangeListener(listener2);
        test(listeners, 2, 1);

        // null handling to check for impact on the existing registrations
        try {
            logman.addPropertyChangeListener(null);
            assertTrue(false, "NullPointerException expected");
        } catch (NullPointerException expected) { }
        test(listeners, 2, 1);

        logman.removePropertyChangeListener(null);  // no-op
        test(listeners, 2, 1);

        // remove listeners
        logman.removePropertyChangeListener(listener1);
        test(listeners, 1, 1);

        logman.removePropertyChangeListener(listener1);
        test(listeners, 0, 1);

        logman.removePropertyChangeListener(listener1);  // no-op
        test(listeners, 0, 1);

        logman.removePropertyChangeListener(listener2);
        test(listeners, 0, 0);

        logman.removePropertyChangeListener(listener2);  // no-op
        test(listeners, 0, 0);
    }
}
