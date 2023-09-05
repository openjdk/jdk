/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 *
 */
package com.sun.hotspot.tools.compiler;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@RunWith(value = Parameterized.class)
public class TestPrebuiltLogs {
    private String logFile;
    static List<String> tests = new ArrayList<String>();

    // BeforeClass is invoked after @Parameters, so we use static block to evaluate tests first.
    static {
        File file = new File("src/test/resources");
        File[] testData = file.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".xml");
            }
        });

        for (File f: testData) {
            tests.add(f.toString());
        }
    }

    @Parameters
    public static Collection data() {
        return tests;
    }

    public TestPrebuiltLogs(String logFile) {
        this.logFile = logFile;
    }

    void doItOrFail(String[] args) {
        try {
            LogCompilation.main(args);
        } catch (Throwable e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testDashi() throws Exception {
        String[] args = {"-i", logFile};
        doItOrFail(args);
    }

    @Test
    public void testNone() throws Exception {
        String[] args = {logFile};
        doItOrFail(args);
    }
}
