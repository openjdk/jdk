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

package compiler.compilercontrol.share.scenario;

import compiler.compilercontrol.share.method.MethodDescriptor;
import compiler.compilercontrol.share.processors.CommandProcessor;
import compiler.compilercontrol.share.processors.LogProcessor;
import compiler.compilercontrol.share.processors.PrintDirectivesProcessor;
import compiler.compilercontrol.share.processors.PrintProcessor;
import jdk.test.lib.Asserts;
import jdk.test.lib.OutputAnalyzer;
import jdk.test.lib.Pair;
import pool.PoolHelper;

import java.lang.reflect.Executable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Test scenario
 */
public final class Scenario {
    private final boolean isValid;
    private final Map<Executable, State> states;
    private final List<Consumer<OutputAnalyzer>> processors;
    private final Executor executor;
    private final Consumer<List<OutputAnalyzer>> jcmdProcessor;

    private Scenario(boolean isValid,
                     List<String> vmopts,
                     Map<Executable, State> states,
                     List<CompileCommand> compileCommands,
                     List<JcmdCommand> jcmdCommands,
                     List<CompileCommand> directives) {
        this.isValid = isValid;
        this.states = states;
        processors = new ArrayList<>();
        processors.add(new LogProcessor(states));
        processors.add(new PrintProcessor(states));
        List<CompileCommand> nonQuieted = new ArrayList<>();
        List<CompileCommand> quieted = new ArrayList<>();
        boolean metQuiet = false;
        for (CompileCommand cc : compileCommands) {
            metQuiet |= cc.command == Command.QUIET;
            if (metQuiet) {
                quieted.add(cc);
            } else {
                nonQuieted.add(cc);
            }
        }
        processors.add(new CommandProcessor(nonQuieted, quieted));
        List<String> jcmdExecCommands = new ArrayList<>();
        boolean addCommandMet = false;
        boolean printCommandMet = false;
        for (JcmdCommand cmd : jcmdCommands) {
            switch (cmd.jcmdType) {
                case ADD:
                    if (!addCommandMet) {
                        jcmdExecCommands.add(JcmdType.ADD.command);
                    }
                    addCommandMet = true;
                    break;
                case PRINT:
                    printCommandMet = true;
                    break;
                default:
                    jcmdExecCommands.add(cmd.jcmdType.command);
                    break;
            }
        }
        // Add print command only in the end to get directives printed
        if (printCommandMet) {
            jcmdExecCommands.add(JcmdType.PRINT.command);
        }
        jcmdProcessor = new PrintDirectivesProcessor(directives);
        executor = new Executor(isValid, vmopts, states, jcmdExecCommands);
    }

    /**
     * Executes scenario
     */
    public void execute() {
        List<OutputAnalyzer> outputList = executor.execute();
        // The first one contains output from the test VM
        OutputAnalyzer mainOuput = outputList.get(0);
        if (isValid) {
            mainOuput.shouldHaveExitValue(0);
            processors.forEach(processor -> processor.accept(mainOuput));
            // only the last output contains directives got from print command
            List<OutputAnalyzer> last = new ArrayList<>();
            last.add(outputList.get(outputList.size() - 1));
            jcmdProcessor.accept(last);
        } else {
            Asserts.assertNE(mainOuput.getExitValue(), 0, "VM should exit with "
                    + "error for incorrect directives");
            mainOuput.shouldContain("Parsing of compiler directives failed");
        }
    }

    /**
     * Gets states of methods for this scenario
     *
     * @return pairs of executable and its state
     */
    public Map<Executable, State> getStates() {
        return states;
    }

    public static enum Compiler {
        C1("c1"),
        C2("c2");

        public final String name;

        Compiler(String name) {
            this.name = name;
        }
    }

    /**
     * Type of diagnostic (jcmd) command
     */
    public static enum JcmdType {
        ADD("Compiler.directives_add " + Type.JCMD.fileName),
        PRINT("Compiler.directives_print"),
        CLEAR("Compiler.directives_clear"),
        REMOVE("Compiler.directives_remove");

        public final String command;
        private JcmdType(String command) {
            this.command = command;
        }
    }

    /**
     * Type of the compile command
     */
    public static enum Type {
        OPTION(""),
        FILE("command_file"),
        DIRECTIVE("directives.json"),
        JCMD("jcmd_directives.json") {
            @Override
            public CompileCommand createCompileCommand(Command command,
                    MethodDescriptor md, Compiler compiler) {
                return new JcmdCommand(command, md, compiler, this,
                        JcmdType.ADD);
            }
        };

        public final String fileName;

        public CompileCommand createCompileCommand(Command command,
                MethodDescriptor md, Compiler compiler) {
            return new CompileCommand(command, md, compiler, this);
        }

        private Type(String fileName) {
            this.fileName = fileName;
        }
    }

    public static Builder getBuilder() {
        return new Builder();
    }

    public static class Builder {
        private final Set<String> vmopts = new LinkedHashSet<>();
        private final Map<Type, StateBuilder<CompileCommand>> builders
                = new HashMap<>();
        private final JcmdStateBuilder jcmdStateBuilder;
        private final List<JcmdCommand> jcmdCommands = new ArrayList<>();

        public Builder() {
            addFlag("-Xmixed");
            builders.put(Type.FILE, new CommandFileBuilder(Type.FILE.fileName));
            builders.put(Type.OPTION, new CommandOptionsBuilder());
            builders.put(Type.DIRECTIVE, new DirectiveBuilder(
                    Type.DIRECTIVE.fileName));
            jcmdStateBuilder = new JcmdStateBuilder(Type.JCMD.fileName);
        }

        public void add(CompileCommand compileCommand) {
            String[] vmOptions = compileCommand.command.vmOpts;
            Collections.addAll(vmopts, vmOptions);
            if (compileCommand.type == Type.JCMD) {
                jcmdStateBuilder.add((JcmdCommand) compileCommand);
                jcmdCommands.add((JcmdCommand) compileCommand);
            } else {
                StateBuilder<CompileCommand> builder = builders.get(
                        compileCommand.type);
                if (builder == null) {
                    throw new Error("TESTBUG: Missing builder for the type: "
                            + compileCommand.type);
                }
                builder.add(compileCommand);
            }
        }

        public Scenario build() {
            boolean isValid = true;

            // Get states from each of the state builders
            Map<Executable, State> commandFileStates
                    = builders.get(Type.FILE).getStates();
            Map<Executable, State> commandOptionStates
                    = builders.get(Type.OPTION).getStates();
            Map<Executable, State> directiveFileStates
                    = builders.get(Type.DIRECTIVE).getStates();

            // check if directives stack was cleared by jcmd
            boolean isClearedState = false;
            if (jcmdContainsCommand(JcmdType.CLEAR)) {
                isClearedState = true;
            }

            // Merge states
            List<Pair<Executable, Callable<?>>> methods = new PoolHelper()
                    .getAllMethods();
            Map<Executable, State> finalStates = new HashMap<>();
            Map<Executable, State> jcmdStates = jcmdStateBuilder.getStates();
            for (Pair<Executable, Callable<?>> pair : methods) {
                Executable x = pair.first;
                State commandOptionState = commandOptionStates.get(x);
                State commandFileState = commandFileStates.get(x);
                State st = State.merge(commandOptionState, commandFileState);
                if (!isClearedState) {
                    State directiveState = directiveFileStates.get(x);
                    State jcmdState = jcmdStates.get(x);
                    if (jcmdState != null) {
                        st = State.merge(st, jcmdState);
                    } else if (directiveState != null) {
                        st = State.merge(st, directiveState);
                    }
                }
                finalStates.put(x, st);
            }

            /*
             * Create a list of commands from options and file
             * to handle quiet command
             */
            List<CompileCommand> ccList = new ArrayList<>();
            ccList.addAll(builders.get(Type.OPTION).getCompileCommands());
            ccList.addAll(builders.get(Type.FILE).getCompileCommands());

            // Create a list of directives to check which one was printed
            List<CompileCommand> directives = new ArrayList<>();
            if (jcmdContainsCommand(JcmdType.PRINT)) {
                if (!isClearedState) {
                    directives.addAll(builders.get(Type.DIRECTIVE)
                            .getCompileCommands());
                }
                directives.addAll(jcmdStateBuilder.getCompileCommands());
            }

            // Get all VM options after we build all states and files
            List<String> options = new ArrayList<>();
            options.addAll(vmopts);
            for (StateBuilder<?> builder : builders.values()) {
                options.addAll(builder.getOptions());
                isValid &= builder.isValid();
            }
            options.addAll(jcmdStateBuilder.getOptions());
            return new Scenario(isValid, options, finalStates, ccList,
                    jcmdCommands, directives);
        }

        // shows if jcmd have passed a specified jcmd command type
        private boolean jcmdContainsCommand(JcmdType type) {
            for (JcmdCommand jcmdCommand : jcmdCommands) {
                if (jcmdCommand.jcmdType == type) {
                    return true;
                }
            }
            return false;
        }
    }
}
