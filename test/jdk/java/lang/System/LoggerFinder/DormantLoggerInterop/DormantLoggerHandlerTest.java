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
 * @summary Dormant Logger removes its handlers on OFF and bypasses JUL for direct output
 * @modules java.base/jdk.internal.access
 *          java.base/jdk.internal.logger.dynamic
 * @run main/othervm DormantLoggerHandlerTest lifecycle
 * @run main/othervm DormantLoggerHandlerTest stdout
 * @run main/othervm DormantLoggerHandlerTest stderr
 */

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MutableCallSite;
import java.lang.ref.Reference;
import java.util.Arrays;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import jdk.internal.access.SharedSecrets;
import jdk.internal.logger.dynamic.DormantLogger;

public class DormantLoggerHandlerTest {
    public static void main(String[] args) throws Throwable {
        if (args[0].equals("lifecycle")) {
            lifecycle();
        } else {
            delayedJUL(args[0]);
        }
    }

    private static void lifecycle() throws Throwable {
        Logger parent = Logger.getLogger("dormant.handler.test");
        parent.setUseParentHandlers(false);
        ApplicationHandler inherited = new ApplicationHandler();
        inherited.setLevel(Level.INFO);
        parent.addHandler(inherited);

        String name = parent.getName() + ".child";
        Logger jul = Logger.getLogger(name);
        ApplicationHandler application = new ApplicationHandler();
        application.setLevel(Level.INFO);
        Filter filter = record -> true;
        var formatter = new SimpleFormatter();
        application.setFilter(filter);
        application.setFormatter(formatter);
        jul.addHandler(application);
        MutableCallSite gate = new MutableCallSite(MethodHandles.constant(boolean.class, false));
        System.Logger dormant = register(name, gate);

        for (String output : new String[] { "logger", "stdout", "stderr" }) {
            // A fresh direct-output activation must preserve JUL configuration,
            // including when stored dormant levels are reloaded.
            if (!output.equals("logger")) {
                jul.setLevel(Level.WARNING);
                DormantLogger.setDormantLoggerLevel(name, "DEBUG", output);
                DormantLogger.reloadLevels();
                check(jul.getLevel() == Level.WARNING, "Direct output changed JUL level");
                check(Arrays.equals(jul.getHandlers(), new Handler[] { application }),
                        "Direct output installed a handler");
            }

            DormantLogger.setDormantLoggerLevel(name, "DEBUG", "logger");
            check((boolean) gate.dynamicInvoker().invokeExact(), "Activation did not enable gate");
            check(jul.getHandlers().length == 2, "Expected one diagnostic handler");
            DormantLogger.setDormantLoggerLevel(name, "DEBUG", "logger");
            check(jul.getHandlers().length == 2, "Repeated activation added a handler");

            if (!output.equals("logger")) {
                DormantLogger.setDormantLoggerLevel(name, "DEBUG", output);
                check(Arrays.equals(jul.getHandlers(), new Handler[] { application }),
                        "Switch to direct output retained a diagnostic handler");
                check(jul.getLevel() == Level.FINE, "Switch to direct output changed JUL level");
            }
            DormantLogger.setDormantLoggerLevel(name, "OFF", output);
            check(!(boolean) gate.dynamicInvoker().invokeExact(), "OFF did not disable gate");
            check(Arrays.equals(jul.getHandlers(), new Handler[] { application }),
                    "OFF did not restore application handlers");
            check(Arrays.equals(parent.getHandlers(), new Handler[] { inherited }),
                    "Parent handlers changed");
            check(jul.getUseParentHandlers(), "Parent handler routing changed");
            check(application.getLevel() == Level.INFO && inherited.getLevel() == Level.INFO,
                    "Application handler levels changed");
            check(application.getFilter() == filter && application.getFormatter() == formatter,
                    "Application filter or formatter changed");
            check(!application.closed && !inherited.closed, "Application handler was closed");
        }

        // OFF with a direct output argument must also clean up a preceding
        // logger-output activation, without an intermediate output switch.
        for (String output : new String[] { "stdout", "stderr" }) {
            DormantLogger.setDormantLoggerLevel(name, "DEBUG", "logger");
            DormantLogger.setDormantLoggerLevel(name, "OFF", output);
            check(Arrays.equals(jul.getHandlers(), new Handler[] { application }),
                    "Direct OFF retained a diagnostic handler");
            check(!(boolean) gate.dynamicInvoker().invokeExact(), "Direct OFF did not disable gate");
        }
        Reference.reachabilityFence(dormant);
    }

    private static void delayedJUL(String output) {
        String name = "dormant.handler.delayed." + output;
        MutableCallSite gate = new MutableCallSite(MethodHandles.constant(boolean.class, false));
        System.Logger dormant = register(name, gate);
        check(SharedSecrets.getJavaUtilLoggingAccess() == null, "JUL initialized too early");
        DormantLogger.setDormantLoggerLevel(name, "DEBUG", output);
        check(SharedSecrets.getJavaUtilLoggingAccess() == null, "Direct output initialized JUL");

        Logger jul = Logger.getLogger(name);
        check(jul.getHandlers().length == 0, "Delayed JUL initialization installed a handler");
        // Lazy logger replacement preserves the initial OFF level. Reloading
        // must not apply the direct-output DEBUG level to the JUL backend.
        check(jul.getLevel() == Level.OFF, "Delayed JUL initialization applied direct-output level");
        DormantLogger.setDormantLoggerLevel(name, "OFF", output);
        check(jul.getHandlers().length == 0 && jul.getLevel() == Level.OFF,
                "Direct OFF changed JUL configuration");
        Reference.reachabilityFence(dormant);
    }

    private static System.Logger register(String name, MutableCallSite gate) {
        return DormantLogger.of(name, Object.class.getModule(), gate,
                MethodHandles.constant(boolean.class, true));
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static class ApplicationHandler extends Handler {
        boolean closed;

        @Override public void publish(LogRecord record) {}
        @Override public void flush() {}
        @Override public void close() { closed = true; }
    }
}
