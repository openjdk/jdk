/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

package java.nio;

import java.lang.ref.Cleaner;
import java.lang.ref.Cleaner.Cleanable;

/**
 * Handles buffer cleaners.
 */
class BufferCleaner {
    private static final Cleaner CLEANER = Cleaner.create();

    private BufferCleaner() {
        // No instantiation.
    }

    /**
     * Register a new cleanable for object and associated action.
     *
     * @param obj object to track
     * @param action cleanup action
     * @return associated cleanable
     */
    static Cleanable register(Object obj, Runnable action) {
        if (action != null) {
            return CLEANER.register(obj, action);
        } else {
            return null;
        }
    }

    /**
     * Sets up a new canary on the same cleaner. When canary is dead,
     * it is a signal that cleaner had acted.
     *
     * @return a canary
     */
    static Canary newCanary() {
        Canary canary = new Canary();
        register(new Object(), canary);
        return canary;
    }

    /**
     * A canary.
     */
    static class Canary implements Runnable {
        volatile boolean dead;

        @Override
        public void run() {
            dead = true;
        }

        public boolean isDead() {
            return dead;
        }
    }

}
