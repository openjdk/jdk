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
import jdk.test.lib.Pair;
import pool.PoolHelper;

import java.lang.reflect.Executable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * An abstract class that builds states by applying
 * commands one after another
 */
public abstract class AbstractCommandBuilder
        implements StateBuilder<CompileCommand> {
    protected static final List<Pair<Executable, Callable<?>>> METHODS
            = new PoolHelper().getAllMethods();
    protected final List<CompileCommand> compileCommands = new ArrayList<>();

    @Override
    public void add(CompileCommand command) {
        compileCommands.add(command);
    }

    @Override
    public Map<Executable, State> getStates() {
        Map<Executable, State> states = new HashMap<>();
        for (CompileCommand compileCommand : compileCommands) {
            if (compileCommand.isValid()) {
                CompileCommand cc = new CompileCommand(compileCommand.command,
                        compileCommand.methodDescriptor,
                        /* CompileCommand option and file doesn't support
                           compiler setting */
                        null,
                        compileCommand.type);
                MethodDescriptor md = cc.methodDescriptor;
                for (Pair<Executable, Callable<?>> pair: METHODS) {
                    Executable exec = pair.first;
                    State state = states.getOrDefault(exec, new State());
                    MethodDescriptor execDesc = new MethodDescriptor(exec);
                    // if executable matches regex then apply the state
                    if (execDesc.getCanonicalString().matches(md.getRegexp())) {
                        state.apply(cc);
                    } else {
                        if (cc.command == Command.COMPILEONLY) {
                            state.setC1Compilable(false);
                            state.setC2Compilable(false);
                        }
                    }
                    states.put(exec, state);
                }
            }
        }
        return states;
    }

    @Override
    public List<CompileCommand> getCompileCommands() {
        return Collections.unmodifiableList(compileCommands);
    }

    @Override
    public boolean isValid() {
        // CompileCommand ignores invalid items
        return true;
    }
}
