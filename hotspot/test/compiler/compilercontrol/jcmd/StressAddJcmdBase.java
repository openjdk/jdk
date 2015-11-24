/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package compiler.compilercontrol.jcmd;

import compiler.compilercontrol.parser.HugeDirectiveUtil;
import compiler.compilercontrol.share.AbstractTestBase;
import compiler.compilercontrol.share.method.MethodDescriptor;
import compiler.compilercontrol.share.scenario.Executor;
import jdk.test.lib.OutputAnalyzer;
import jdk.test.lib.TimeLimitedRunner;
import jdk.test.lib.Utils;
import pool.PoolHelper;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class StressAddJcmdBase {
    private static final int DIRECTIVES_AMOUNT = Integer.getInteger(
            "compiler.compilercontrol.jcmd.StressAddJcmdBase.directivesAmount",
            1000);
    private static final int DIRECTIVE_FILES = Integer.getInteger(
            "compiler.compilercontrol.jcmd.StressAddJcmdBase.directiveFiles",
            5);
    private static final List<MethodDescriptor> DESCRIPTORS = new PoolHelper()
            .getAllMethods().stream()
                    .map(pair -> AbstractTestBase
                            .getValidMethodDescriptor(pair.first))
                    .collect(Collectors.toList());

    /**
     * Performs test
     */
    public void test() {
        List<String> commands = prepareCommands();
        Executor executor = new TimeLimitedExecutor(commands);
        List<OutputAnalyzer> outputAnalyzers = executor.execute();
        outputAnalyzers.get(0).shouldHaveExitValue(0);
    }

    /**
     * Makes connection to the test VM
     *
     * @param pid      a pid of the VM under test
     * @param commands a list of jcmd commands to be executed
     * @return true if the test should continue invocation of this method
     */
    protected abstract boolean makeConnection(int pid, List<String> commands);

    /**
     * Finish test executions
     */
    protected void finish() { }

    private List<String> prepareCommands() {
        String[] files = new String[DIRECTIVE_FILES];
        for (int i = 0; i < DIRECTIVE_FILES; i++) {
            files[i] = "directives" + i + ".json";
            HugeDirectiveUtil.createHugeFile(DESCRIPTORS, files[i],
                    DIRECTIVES_AMOUNT);
        }
        return Stream.of(files)
                .map(file -> "Compiler.directives_add " + file)
                .collect(Collectors.toList());
    }

    private class TimeLimitedExecutor extends Executor {
        private final List<String> jcmdCommands;

        public TimeLimitedExecutor(List<String> jcmdCommands) {
            /* There are no need to check the state */
            super(true, null, null, jcmdCommands);
            this.jcmdCommands = jcmdCommands;
        }

        @Override
        protected OutputAnalyzer[] executeJCMD(int pid) {
            TimeLimitedRunner runner = new TimeLimitedRunner(
                    Utils.DEFAULT_TEST_TIMEOUT,
                    Utils.TIMEOUT_FACTOR,
                    () -> makeConnection(pid, jcmdCommands));
            try {
                runner.call();
            } catch (Exception e) {
                throw new Error("Exception during the execution: " + e, e);
            }
            finish();
            return new OutputAnalyzer[0];
        }
    }
}
