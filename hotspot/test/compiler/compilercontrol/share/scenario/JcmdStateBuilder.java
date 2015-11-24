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
import compiler.compilercontrol.share.method.MethodGenerator;
import pool.PoolHelper;
import jdk.test.lib.Pair;

import java.lang.reflect.Executable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

public class JcmdStateBuilder implements StateBuilder<JcmdCommand> {
    private static final List<Pair<Executable, Callable<?>>> METHODS
            = new PoolHelper().getAllMethods();
    private final Map<Executable, State> stateMap = new HashMap<>();
    private final DirectiveBuilder directiveBuilder;
    private Map<MethodDescriptor, List<CompileCommand>> matchBlocks
            = new LinkedHashMap<>();
    private boolean isFileValid = true;

    public JcmdStateBuilder(String fileName) {
        directiveBuilder = new DirectiveBuilder(fileName);
    }

    @Override
    public void add(JcmdCommand compileCommand) {
        switch (compileCommand.jcmdType) {
            case ADD:
                directiveBuilder.add(compileCommand);
                addCommand(compileCommand);
                break;
            case PRINT:
                // doesn't change the state
                break;
            case CLEAR:
                matchBlocks.clear();
                break;
            case REMOVE:
                removeDirective();
                break;
        }
    }

    private void addCommand(JcmdCommand compileCommand) {
        isFileValid &= compileCommand.isValid();
        for (MethodDescriptor md: matchBlocks.keySet()) {
            if (compileCommand.methodDescriptor.getCanonicalString()
                    .matches(md.getRegexp())) {
                matchBlocks.get(md).add(compileCommand);
            }
        }
        List<CompileCommand> commands = new ArrayList<>();
        commands.add(compileCommand);
        matchBlocks.put(compileCommand.methodDescriptor, commands);
    }

    private void removeDirective() {
        Iterator<MethodDescriptor> iterator = matchBlocks.keySet().iterator();
        if (iterator.hasNext()) {
            MethodDescriptor md = iterator.next();
            matchBlocks.remove(md);
        }
    }

    @Override
    public boolean isValid() {
        // VM skips invalid directive file added via jcmd command
        return true;
    }

    @Override
    public Map<Executable, State> getStates() {
        directiveBuilder.getStates();
        // Build states for each method according to match blocks
        for (Pair<Executable, Callable<?>> pair : METHODS) {
            State state = getState(pair);
            if (state != null) {
                stateMap.put(pair.first, state);
            }
        }
        if (isFileValid) {
            return stateMap;
        } else {
            // return empty map because invalid file doesn't change states
            return new HashMap<>();
        }
    }

    private State getState(Pair<Executable, Callable<?>> pair) {
        State state = null;
        MethodDescriptor execDesc = MethodGenerator.commandDescriptor(
                pair.first);
        boolean isMatchFound = false;

        if (stateMap.containsKey(pair.first)) {
            state = stateMap.get(pair.first);
        }
        for (MethodDescriptor matchDesc : matchBlocks.keySet()) {
            if (execDesc.getCanonicalString().matches(matchDesc.getRegexp())) {
                /*
                 * if executable matches regex
                 * then apply commands from this match to the state
                 */
                for (CompileCommand cc : matchBlocks.get(matchDesc)) {
                    state = new State();
                    if (!isMatchFound) {
                        // this is a first found match, apply all commands
                        state.apply(cc);
                    } else {
                        // apply only inline directives
                        switch (cc.command) {
                            case INLINE:
                            case DONTINLINE:
                                state.apply(cc);
                                break;
                        }
                    }
                }
                isMatchFound = true;
            }
        }
        return state;
    }

    @Override
    public List<String> getOptions() {
        return new ArrayList<>();
    }

    @Override
    public List<JcmdCommand> getCompileCommands() {
        if (isFileValid) {
            return matchBlocks.keySet().stream()
                    /* only method descriptor is required
                       to check print_directives */
                    .map(md -> new JcmdCommand(null, md, null, null,
                            Scenario.JcmdType.ADD))
                    .collect(Collectors.toList());
        } else {
            return new ArrayList<>();
        }
    }
}
