/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.BeforeClass;
import org.junit.experimental.categories.Category;
import junit.framework.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import static org.junit.Assert.*;

@RunWith(value = Parameterized.class)
public class TestLogCompilation {

    String logFile;

    public TestLogCompilation(String logFile) {
        this.logFile = logFile;
    }

    @Parameters
    public static Collection data() {
        Object[][] data = new Object[][]{
            // Simply running this jar with jdk-9 no args,
            // no file (just prints the help)
            {"./src/test/resources/hotspot_pid23756.log"},
            // LogCompilation output of running on above file
            {"./src/test/resources/hotspot_pid25109.log"}

        };
        return Arrays.asList(data);
    }

    @Test
    public void testDashi() throws Exception {
        String[] args = {"-i",
            logFile
        };

        LogCompilation.main(args);
    }

    @Test
    public void testDashiDasht() throws Exception {
        String[] args = {"-i",
            "-t",
            logFile
        };

        LogCompilation.main(args);
    }

    @Test
    public void testDefault() throws Exception {
        String[] args = {
            logFile
        };

        LogCompilation.main(args);
    }

    @Test
    public void testDashS() throws Exception {
        String[] args = {"-S",
            logFile
        };

        LogCompilation.main(args);
    }

    @Test
    public void testDashU() throws Exception {
        String[] args = {"-U",
            logFile
        };

        LogCompilation.main(args);
    }

    @Test
    public void testDashe() throws Exception {
        String[] args = {"-e",
            logFile
        };

        LogCompilation.main(args);
    }

    @Test
    public void testDashn() throws Exception {
        String[] args = {"-n",
            logFile
        };

        LogCompilation.main(args);
    }
}
