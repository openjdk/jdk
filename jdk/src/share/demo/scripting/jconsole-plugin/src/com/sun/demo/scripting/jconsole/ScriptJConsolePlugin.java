/*
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */


package com.sun.demo.scripting.jconsole;

import com.sun.tools.jconsole.*;
import java.io.*;
import java.util.concurrent.CountDownLatch;
import javax.script.*;
import javax.swing.*;
import java.util.*;

/**
 * This is script console plugin. This class uses javax.script API to create
 * interactive read-eval-print script shell within the jconsole GUI.
 */
public class ScriptJConsolePlugin extends JConsolePlugin
                     implements ScriptShellPanel.CommandProcessor {
    // Panel for our tab
    private volatile ScriptShellPanel window;
    // Tabs that we add to jconsole GUI
    private Map<String, JPanel> tabs;

    // Script engine that evaluates scripts
    private volatile ScriptEngine engine;

    // script engine initialization occurs in background.
    // This latch is used to coorrdinate engine init and eval.
    private CountDownLatch engineReady = new CountDownLatch(1);

    // File extension used for scripts of chosen language.
    // For eg. ".js" for JavaScript, ".bsh" for BeanShell.
    private String extension;

    // Prompt to print in the read-eval-print loop. This is
    // derived from the script file extension.
    private volatile String prompt;

    /**
     * Constructor to create this plugin
     */
    public ScriptJConsolePlugin() {
    }

    @Override public Map<String, JPanel> getTabs() {
        // create ScriptEngine
        createScriptEngine();

        // create panel for tab
        window = new ScriptShellPanel(this);

        // add tab to tabs map
        tabs = new HashMap<String, JPanel>();
        tabs.put("Script Shell", window);

        new Thread(new Runnable() {
            @Override
            public void run() {
                // initialize the script engine
                initScriptEngine();
                engineReady.countDown();
            }
        }).start();
        return tabs;
    }

    @Override public SwingWorker<?,?> newSwingWorker() {
        return null;
    }

    @Override public void dispose() {
        window.dispose();
    }

    @Override
    public String getPrompt() {
        return prompt;
    }

    @Override
    public String executeCommand(String cmd) {
        String res;
        try {
           engineReady.await();
           Object tmp = engine.eval(cmd);
           res = (tmp == null)? null : tmp.toString();
        } catch (InterruptedException ie) {
           res = ie.getMessage();
        } catch (ScriptException se) {
           res = se.getMessage();
        }
        return res;
    }

    //-- Internals only below this point
    private void createScriptEngine() {
        ScriptEngineManager manager = new ScriptEngineManager();
        String language = getScriptLanguage();
        engine = manager.getEngineByName(language);
        if (engine == null) {
            throw new RuntimeException("cannot load " + language + " engine");
        }
        extension = engine.getFactory().getExtensions().get(0);
        prompt = extension + ">";
        engine.setBindings(createBindings(), ScriptContext.ENGINE_SCOPE);
    }

    // Name of the System property used to select scripting language
    private static final String LANGUAGE_KEY = "com.sun.demo.jconsole.console.language";

    private String getScriptLanguage() {
        // check whether explicit System property is set
        String lang = System.getProperty(LANGUAGE_KEY);
        if (lang == null) {
            // default is JavaScript
            lang = "JavaScript";
        }
        return lang;
    }

    // create Bindings that is backed by a synchronized HashMap
    private Bindings createBindings() {
        Map<String, Object> map =
                Collections.synchronizedMap(new HashMap<String, Object>());
        return new SimpleBindings(map);
    }

    // create and initialize script engine
    private void initScriptEngine() {
        // set pre-defined global variables
        setGlobals();
        // load pre-defined initialization file
        loadInitFile();
        // load current user's initialization file
        loadUserInitFile();
    }

    // set pre-defined global variables for script
    private void setGlobals() {
        engine.put("engine", engine);
        engine.put("window", window);
        engine.put("plugin", this);
    }

    // load initial script file (jconsole.<extension>)
    private void loadInitFile() {
        String oldFilename = (String) engine.get(ScriptEngine.FILENAME);
        engine.put(ScriptEngine.FILENAME, "<built-in jconsole." + extension + ">");
        try {
            Class<? extends ScriptJConsolePlugin> myClass = this.getClass();
            InputStream stream = myClass.getResourceAsStream("/resources/jconsole." +
                                       extension);
            if (stream != null) {
                engine.eval(new InputStreamReader(new BufferedInputStream(stream)));
            }
        } catch (Exception exp) {
            exp.printStackTrace();
            // FIXME: What else I can do here??
        } finally {
            engine.put(ScriptEngine.FILENAME, oldFilename);
        }
    }

    // load user's initial script file (~/jconsole.<extension>)
    private void loadUserInitFile() {
        String oldFilename = (String) engine.get(ScriptEngine.FILENAME);
        String home = System.getProperty("user.home");
        if (home == null) {
            // no user.home?? should not happen??
            return;
        }
        String fileName = home + File.separator + "jconsole." + extension;
        if (! (new File(fileName).exists())) {
            // user does not have ~/jconsole.<extension>
            return;
        }
        engine.put(ScriptEngine.FILENAME, fileName);
        try {
            engine.eval(new FileReader(fileName));
        } catch (Exception exp) {
            exp.printStackTrace();
            // FIXME: What else I can do here??
        } finally {
            engine.put(ScriptEngine.FILENAME, oldFilename);
        }
    }
}
