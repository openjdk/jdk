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

package jdk.nashorn.ant;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.BuildException;

/**
 * This class implements an ant task to evaluate nashorn scripts
 * from ant projects.
 */
public final class NashornTask extends Task {
    // Underlying nashorn script engine
    private final ScriptEngine engine;
    // the current ant project
    private Project project;
    // the script evaluated by this task
    private String script;

    public NashornTask() {
        final ScriptEngineManager m = new ScriptEngineManager();
        this.engine = m.getEngineByName("nashorn");
    }

    @Override
    public void setProject(Project proj) {
        this.project = proj;
    }

    // set the script to be evaluated
    public void addText(String text) {
        this.script = text;
    }

    @Override
    public void execute() {
        // expose project as "project" variable
        engine.put("project", project);
        // expose this task as "self" variable
        engine.put("self", this);

        // evaluate specified script
        try {
            engine.eval(script);
        } catch (final ScriptException se) {
            throw new BuildException(se);
        }
    }
}
