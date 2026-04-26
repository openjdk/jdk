/*
 * Copyright (c) 2013, 2026, Oracle and/or its affiliates. All rights reserved.
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
package java.util.stream;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

/**
 * LoggingTestCase
 *
 */
public class LoggingTestCase {
    private final Map<String, Object> context = new HashMap<>();

    protected void setContext(String key, Object value) {
        context.put(key, value);
    }

    protected void clearContext(String key) {
        context.remove(key);
    }

    public static class Extension implements BeforeEachCallback, TestWatcher {

        @Override
        public void beforeEach(ExtensionContext ctx) {
            getInstance(ctx).ifPresent(l -> l.context.clear());
        }

        @Override
        public void testFailed(ExtensionContext ctx, Throwable cause) {
            getInstance(ctx).ifPresent(l ->
                    System.err.printf("[FAILED] %s | context: %s%n",
                            ctx.getDisplayName(), l.context));
        }

        private Optional<LoggingTestCase> getInstance(ExtensionContext ctx) {
            return ctx.getTestInstance()
                    .filter(i -> i instanceof LoggingTestCase)
                    .map(i -> (LoggingTestCase) i);
        }
    }
}
