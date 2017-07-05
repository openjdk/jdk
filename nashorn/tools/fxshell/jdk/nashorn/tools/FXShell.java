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

package jdk.nashorn.tools;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javafx.application.Application;
import javafx.stage.Stage;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;

/**
 * This shell is designed to launch a JavaFX application written in Nashorn JavaScript.
 */
public class FXShell extends Application {
    /**
     * Script engine manager to search.
     */
    private ScriptEngineManager manager;
    /**
     * Nashorn script engine factory.
     */
    private NashornScriptEngineFactory factory;
    /**
     * Main instance of Nashorn script engine.
     */
    private ScriptEngine engine;

    /**
     * Needed so that the FX launcher can create an instance of this class.
     */
    public FXShell() {
    }

    /**
     * Main entry point. Never actually used.
     * @param args Command lien arguments.
     */
    public static void main(String[] args) {
        launch(args);
    }

    /*
     * Application overrides.
     */

    @Override
    public void init() throws Exception {
        // Script engine manager to search.
        this.manager = new ScriptEngineManager();

        // Locate the Nashorn script engine factory.  Needed for passing arguments.
        for (ScriptEngineFactory engineFactory : this.manager.getEngineFactories()) {
             if (engineFactory.getEngineName().equals("Oracle Nashorn") && engineFactory instanceof NashornScriptEngineFactory) {
                this.factory = (NashornScriptEngineFactory)engineFactory;
            }
        }

        // If none located.
        if (this.factory == null) {
            System.err.println("Nashorn script engine not available");
            System.exit(1);
        }

        // Get the command line and JNLP parameters.
        final Parameters parameters = getParameters();

        // To collect the script paths and command line arguments.
        final List<String> paths = new ArrayList<>();
        final List<String> args = new ArrayList<>();

        // Pull out relevant JNLP named parameters.
        final Map<String, String> named = parameters.getNamed();
        for (Map.Entry<String, String> entry : named.entrySet()) {
            final String key = entry.getKey();
            final String value = entry.getValue();

            if ((key.equals("cp") || key.equals("classpath")) && value != null) {
                args.add("-classpath");
                args.add(value);
            } else if (key.equals("source") && value != null && value.toLowerCase().endsWith(".js")) {
                paths.add(value);
            }
        }

        // Pull out relevant command line arguments.
        boolean addNextArg = false;
        boolean addAllArgs = false;
        for (String parameter : parameters.getUnnamed()) {
            if (addAllArgs || addNextArg) {
                args.add(parameter);
                addNextArg = false;
            } else if (parameter.equals("--")) {
                args.add(parameter);
                addAllArgs = true;
            } else if (parameter.startsWith("-")) {
                args.add(parameter);
                addNextArg = parameter.equals("-cp") || parameter.equals("-classpath");
            } else if (parameter.toLowerCase().endsWith(".js")) {
                paths.add(parameter);
            }
        }

        // Create a Nashorn script engine with specified arguments.
        engine = factory.getScriptEngine(args.toArray(new String[args.size()]));

        // Load initial scripts.
        for (String path : paths) {
            load(path);
        }

        // Invoke users JavaScript init function if present.
        try {
            ((Invocable) engine).invokeFunction("init");
        } catch (NoSuchMethodException ex) {
            // Presence of init is optional.
        }
    }

    @Override
    public void start(Stage stage) throws Exception {
        // Invoke users JavaScript start function if present.
        try {
            ((Invocable) engine).invokeFunction("start", stage);
        } catch (NoSuchMethodException ex) {
            // Presence of start is optional.
        }
    }

    @Override
    public void stop() throws Exception {
        // Invoke users JavaScript stop function if present.
        try {
            ((Invocable) engine).invokeFunction("stop");
        } catch (NoSuchMethodException ex) {
            // Presence of stop is optional.
        }
    }

    /**
     * Load and evaluate the specified JavaScript file.
     *
     * @param path Path to UTF-8 encoded JavaScript file.
     *
     * @return Last evaluation result (discarded.)
     */
    @SuppressWarnings("resource")
    private Object load(String path) {
        try {
            FileInputStream file = new FileInputStream(path);
            InputStreamReader input = new InputStreamReader(file, "UTF-8");
            return engine.eval(input);
        } catch (FileNotFoundException | UnsupportedEncodingException | ScriptException ex) {
            ex.printStackTrace();
        }

        return null;
    }
}
