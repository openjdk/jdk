/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8235474
 * @summary Tests for evalution of records
 * @modules jdk.jshell
 * @build KullaTesting TestingInputStream ExpectedDiagnostic
 * @run testng RecordsTest
 */

import org.testng.annotations.Test;

import javax.lang.model.SourceVersion;
import static org.testng.Assert.assertEquals;
import org.testng.annotations.BeforeMethod;

@Test
public class RecordsTest extends KullaTesting {

    public void testRecordClass() {
        assertEval("record R(String s, int i) { }");
        assertEquals(varKey(assertEval("R r = new R(\"r\", 42);")).name(), "r");
        assertEval("r.s()", "\"r\"");
        assertEval("r.i()", "42");
    }

    public void testRecordField() {
        assertEquals(varKey(assertEval("String record = \"\";")).name(), "record");
        assertEval("record.length()", "0");
    }

    public void testRecordMethod() {
        assertEquals(methodKey(assertEval("String record(String record) { return record + record; }")).name(), "record");
        assertEval("record(\"r\")", "\"rr\"");
        assertEval("record(\"r\").length()", "2");
    }

    @BeforeMethod
    public void setUp() {
        setUp(b -> b.compilerOptions("--enable-preview", "-source", String.valueOf(SourceVersion.latest().ordinal()))
                    .remoteVMOptions("--enable-preview"));
    }
}
