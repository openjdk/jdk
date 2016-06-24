/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8131029
 * @summary Test that fail-over works for FailOverExecutionControl
 * @modules jdk.jshell/jdk.internal.jshell.jdi
 *          jdk.jshell/jdk.jshell.spi
 * @build KullaTesting ExecutionControlTestBase
 * @run testng FailOverExecutionControlTest
 */


import java.util.Collection;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeMethod;
import jdk.internal.jshell.jdi.FailOverExecutionControl;
import jdk.internal.jshell.jdi.JDIExecutionControl;
import jdk.jshell.JShellException;
import jdk.jshell.spi.ExecutionControl;
import jdk.jshell.spi.ExecutionEnv;

@Test
public class FailOverExecutionControlTest extends ExecutionControlTestBase {

    @BeforeMethod
    @Override
    public void setUp() {
        setUp(new FailOverExecutionControl(
                new AlwaysFailingExecutionControl(),
                new AlwaysFailingExecutionControl(),
                new JDIExecutionControl()));
    }

    class AlwaysFailingExecutionControl implements ExecutionControl {

        @Override
        public void start(ExecutionEnv env) throws Exception {
            throw new UnsupportedOperationException("This operation intentionally broken.");
        }

        @Override
        public void close() {
            throw new UnsupportedOperationException("This operation intentionally broken.");
        }

        @Override
        public boolean addToClasspath(String path) {
            throw new UnsupportedOperationException("This operation intentionally broken.");
        }

        @Override
        public String invoke(String classname, String methodname) throws JShellException {
            throw new UnsupportedOperationException("This operation intentionally broken.");
        }

        @Override
        public boolean load(Collection<String> classes) {
            throw new UnsupportedOperationException("This operation intentionally broken.");
        }

        @Override
        public boolean redefine(Collection<String> classes) {
            throw new UnsupportedOperationException("This operation intentionally broken.");
        }

        @Override
        public ClassStatus getClassStatus(String classname) {
            throw new UnsupportedOperationException("This operation intentionally broken.");
        }

        @Override
        public void stop() {
            throw new UnsupportedOperationException("This operation intentionally broken.");
        }

        @Override
        public String varValue(String classname, String varname) {
            throw new UnsupportedOperationException("This operation intentionally broken.");
        }

    }
}
