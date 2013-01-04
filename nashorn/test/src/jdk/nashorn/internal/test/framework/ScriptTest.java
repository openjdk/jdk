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

import static jdk.nashorn.internal.test.framework.TestConfig.TEST_JS_INCLUDES;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import jdk.nashorn.internal.test.framework.TestFinder.TestFactory;
import org.testng.ITest;
import org.testng.annotations.Factory;
import org.testng.annotations.Listeners;

/**
 * Simple test suite Factory for the JavaScript tests - runs tests in the name order.
 */
@Listeners({ TestReorderInterceptor.class })
public final class ScriptTest {
    /**
     * Creates a test factory for the set of .js source tests.
     *
     * @return a Object[] of test objects.
     */
    @Factory
    public Object[] suite() throws Exception {
        Locale.setDefault(new Locale(""));

        final List<ITest> tests = new ArrayList<>();
        final Set<String> orphans = new TreeSet<>();

        final TestFactory<ITest> testFactory = new TestFactory<ITest>() {
            @Override
            public ITest createTest(final String framework, final File testFile, final List<String> engineOptions, final Map<String, String> testOptions, final List<String> scriptArguments) {
                return new ScriptRunnable(framework, testFile, engineOptions, testOptions,  scriptArguments);
            }

            @Override
            public void log(String msg) {
                org.testng.Reporter.log(msg, true);
            }
        };

        TestFinder.findAllTests(tests, orphans, testFactory);

        if (System.getProperty(TEST_JS_INCLUDES) == null) {
            tests.add(new OrphanTestFinder(orphans));
        }

        return tests.toArray();
    }
}
