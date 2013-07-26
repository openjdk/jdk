/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.test.framework;

/**
 * Configuration info for script tests.
 */
public interface TestConfig {
    // Test options inferred from various test @foo tags and passed to test factory.
    public static final String   OPTIONS_RUN                 = "run";
    public static final String   OPTIONS_EXPECT_COMPILE_FAIL = "expect-compile-fail";
    public static final String   OPTIONS_CHECK_COMPILE_MSG   = "check-compile-msg";
    public static final String   OPTIONS_EXPECT_RUN_FAIL     = "expect-run-fail";
    public static final String   OPTIONS_IGNORE_STD_ERROR    = "ignore-std-error";
    public static final String   OPTIONS_COMPARE             = "compare";
    public static final String   OPTIONS_FORK                = "fork";

    // System property names used for various test configurations

    // A list of test directories under which to look for the TEST_JS_INCLUDES
    // patterns
    static final String  TEST_JS_ROOTS                       = "test.js.roots";

    // A pattern of tests to include under the TEST_JS_ROOTS
    static final String TEST_JS_INCLUDES                    = "test.js.includes";

    // explicit list of tests specified to run
    static final String TEST_JS_LIST                        = "test.js.list";

    // framework script that runs before the test scripts
    static final String TEST_JS_FRAMEWORK                   = "test.js.framework";

    // test directory to skip
    static final String TEST_JS_EXCLUDE_DIR                 = "test.js.exclude.dir";

    // test directory where everything should be run
    static final String TEST_JS_UNCHECKED_DIR               = "test.js.unchecked.dir";

    // specific tests to skip
    static final String TEST_JS_EXCLUDE_LIST                = "test.js.exclude.list";

    // file containing list of tests to skip
    static final String TEST_JS_EXCLUDES_FILE               = "test.js.excludes.file";

    // strict mode or not
    static final String TEST_JS_ENABLE_STRICT_MODE          = "test.js.enable.strict.mode";

    // always fail test list
    static final String TEST_JS_FAIL_LIST                   = "test.js.fail.list";

    // shared context mode or not
    static final String TEST_JS_SHARED_CONTEXT              = "test.js.shared.context";

    static final String TEST_FORK_JVM_OPTIONS               = "test.fork.jvm.options";

    // file for storing last run's failed tests
    static final String TEST_FAILED_LIST_FILE = "test.failed.list.file";
}
