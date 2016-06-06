/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.jshell.jdi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import jdk.internal.jshell.debug.InternalDebugControl;
import jdk.jshell.JShellException;
import jdk.jshell.spi.ExecutionControl;
import jdk.jshell.spi.ExecutionEnv;

/**
 * A meta implementation of ExecutionControl which cycles through the specified
 * ExecutionControl instances until it finds one that starts.
 */
public class FailOverExecutionControl implements ExecutionControl {

    private final List<ExecutionControl> ecl = new ArrayList<>();
    private ExecutionControl active = null;
    private final List<Exception> thrown = new ArrayList<>();

    /**
     * Create the ExecutionControl instance with at least one actual
     * ExecutionControl instance.
     *
     * @param ec0 the first instance to try
     * @param ecs the second and on instance to try
     */
    public FailOverExecutionControl(ExecutionControl ec0, ExecutionControl... ecs) {
        ecl.add(ec0);
        for (ExecutionControl ec : ecs) {
            ecl.add(ec);
        }
    }

    @Override
    public void start(ExecutionEnv env) throws Exception {
        for (ExecutionControl ec : ecl) {
            try {
                ec.start(env);
                // Success! This is our active ExecutionControl
                active = ec;
                return;
            } catch (Exception ex) {
                thrown.add(ex);
            } catch (Throwable ex) {
                thrown.add(new RuntimeException(ex));
            }
            InternalDebugControl.debug(env.state(), env.userErr(),
                    thrown.get(thrown.size() - 1), "failed one in FailOverExecutionControl");
        }
        // They have all failed -- rethrow the first exception we encountered
        throw thrown.get(0);
    }

    @Override
    public void close() {
        active.close();
    }

    @Override
    public boolean addToClasspath(String path) {
        return active.addToClasspath(path);
    }

    @Override
    public String invoke(String classname, String methodname) throws JShellException {
        return active.invoke(classname, methodname);
    }

    @Override
    public boolean load(Collection<String> classes) {
        return active.load(classes);
    }

    @Override
    public boolean redefine(Collection<String> classes) {
        return active.redefine(classes);
    }

    @Override
    public ClassStatus getClassStatus(String classname) {
        return active.getClassStatus(classname);
    }

    @Override
    public void stop() {
        active.stop();
    }

    @Override
    public String varValue(String classname, String varname) {
        return active.varValue(classname, varname);
    }

}
