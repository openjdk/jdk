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

package loggerfinder;

import java.lang.*;
import java.util.*;

public class SimpleLoggerFinder extends System.LoggerFinder {

    static {
        try {
            long sleep = new Random().nextLong(1000L) + 1L;
            System.out.println("Logger finder service load sleep value: " + sleep);
            // simulate a slow load service
            Thread.sleep(sleep);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
     @Override
     public System.Logger getLogger(String name, Module module) {
         return new SimpleLogger(name);
     }

    private static class SimpleLogger implements System.Logger {
        private final String name;

        public SimpleLogger(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isLoggable(Level level) {
            return true;
        }

        @Override
        public void log(Level level, ResourceBundle bundle, String msg, Throwable thrown) {
            System.out.println("TEST LOGGER: " + msg);
        }

        @Override
        public void log(Level level, ResourceBundle bundle, String format, Object... params) {
            System.out.println("TEST LOGGER: " + Arrays.asList(params));

        }
    }
}
