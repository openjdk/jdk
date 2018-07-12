/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.mxtool.junit;

import org.junit.runner.*;
import org.junit.runner.notification.*;

/**
 * Color support for JUnit test output using ANSI escapes codes.
 */
class AnsiTerminalDecorator extends MxRunListenerDecorator {

    /** Foreground black. */
    public static final String BLACK = "\u001b[30m";
    /** Foreground red. */
    public static final String RED = "\u001b[31m";
    /** Foreground green. */
    public static final String GREEN = "\u001b[32m";
    /** Foreground yellow. */
    public static final String YELLOW = "\u001b[33m";
    /** Foreground blue. */
    public static final String BLUE = "\u001b[34m";
    /** Foreground magenta. */
    public static final String MAGENTA = "\u001b[35m";
    /** Foreground cyan. */
    public static final String CYAN = "\u001b[36m";
    /** Foreground white. */
    public static final String WHITE = "\u001b[37m";

    /** Foreground bold black. */
    public static final String BOLD_BLACK = "\u001b[30;1m";
    /** Foreground bold red. */
    public static final String BOLD_RED = "\u001b[31;1m";
    /** Foreground bold green. */
    public static final String BOLD_GREEN = "\u001b[32;1m";
    /** Foreground bold yellow. */
    public static final String BOLD_YELLOW = "\u001b[33;1m";
    /** Foreground bold blue. */
    public static final String BOLD_BLUE = "\u001b[34;1m";
    /** Foreground bold magenta. */
    public static final String BOLD_MAGENTA = "\u001b[35;1m";
    /** Foreground bold cyan. */
    public static final String BOLD_CYAN = "\u001b[36;1m";
    /** Foreground bold white. */
    public static final String BOLD_WHITE = "\u001b[37;1m";

    /** Background black. */
    public static final String BG_BLACK = "\u001b[40m";
    /** Background red. */
    public static final String BG_RED = "\u001b[41m";
    /** Background green. */
    public static final String BG_GREEN = "\u001b[42m";
    /** Background yellow. */
    public static final String BG_YELLOW = "\u001b[43m";
    /** Background blue. */
    public static final String BG_BLUE = "\u001b[44m";
    /** Background magenta. */
    public static final String BG_MAGENTA = "\u001b[45m";
    /** Background cyan. */
    public static final String BG_CYAN = "\u001b[46m";
    /** Background white. */
    public static final String BG_WHITE = "\u001b[47m";

    /** Reset. */
    public static final String RESET = "\u001b[0m";
    /** Underline. */
    public static final String UNDERLINED = "\u001b[4m";

    AnsiTerminalDecorator(MxRunListener l) {
        super(l);
    }

    @Override
    public void testSucceeded(Description description) {
        getWriter().print(GREEN);
        super.testSucceeded(description);
        getWriter().print(RESET);
    }

    @Override
    public void testAssumptionFailure(Failure failure) {
        getWriter().print(BLUE);
        super.testAssumptionFailure(failure);
        getWriter().print(RESET);
    }

    @Override
    public void testFailed(Failure failure) {
        getWriter().print(RED);
        super.testFailed(failure);
        getWriter().print(RESET);
    }

    @Override
    public void testIgnored(Description description) {
        getWriter().print(MAGENTA);
        super.testIgnored(description);
        getWriter().print(RESET);
    }
}
