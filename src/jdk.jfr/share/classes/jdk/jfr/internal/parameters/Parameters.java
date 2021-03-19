/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.internal.parameters;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jdk.jfr.internal.SecuritySupport.SafePath;

public final class Parameters {
    private final Map<String, List<XmlVariable>> variables = new LinkedHashMap<>();
    private final XmlConfiguration configuration;

    public Parameters(SafePath file) throws ParseException, IOException {
        this.configuration = createConfiguration(file);
        this.configuration.validate();
        addVariables();
        wireConditions();
        wireSettings();
    }

    public Parameters(List<SafePath> files) throws IOException, ParseException {
        this.configuration = new XmlConfiguration();
        this.configuration.setAttribute("version", "2.0");
        for (SafePath file : files) {
            Parameters params = new Parameters(file);
            for (var entry : params.variables.entrySet()) {
                String name = entry.getKey();
                // Fail-fast checks that prevents an ambiguous file to be written later
                if (variables.containsKey(name)) {
                    throw new ParseException("Control with '" + name + "' is declared in multiple files", 0);
                }
                variables.put(name, entry.getValue());
            }
            for (XmlElement child : params.configuration.getChildren()) {
                this.configuration.addChild(child);
            }
        }
    }

    public void setLabel(String label) {
        configuration.setAttribute("label", label);
    }

    public void configure(String name, String value) {
        for (XmlInput i : getInputs()) {
            if (i.getName().equals(name)) {
                i.configure(value);
                return;
            }
        }
        boolean add = name.startsWith("+");
        if (add) {
            name = name.substring(1);
        }
        int index = name.indexOf("#");
        if (index < 1 || index == name.length() - 1) {
            throw new IllegalArgumentException("Option '" + name + "' doesn't exist in configuration");
        }
        XmlEvent event = configuration.getEvent(name.substring(0, index), add);
        String settingName = name.substring(index + 1);
        XmlSetting setting = event.getSetting(settingName, add);
        
        if (settingName.equals("period") || settingName.equals("threshold")) {
            try {
                value = Utilities.parseTimespan(value);
            } catch (IllegalArgumentException iae) {
                // OK, no validation to allow forward compatibility.
            }
        }
        
        setting.setContent(value);
    }

    public void configure(UserInterface ui) throws AbortException {
        for (XmlInput input : getInputs()) {
            input.configure(ui);
        }
    }

    public List<XmlInput> getInputs() {
        List<XmlInput> inputs = new ArrayList<>();
        for (XmlControl control : configuration.getControls()) {
            inputs.addAll(control.getInputs());
        }
        return inputs;
    }

    public XmlConfiguration getConfiguration() {
        return configuration;
    }

    public void saveToFile(SafePath path) throws IOException {
        try (PrintWriter p = new PrintWriter(path.toFile(), Charset.forName("UTF-8"))) {
            PrettyPrinter pp = new PrettyPrinter(p);
            pp.print(configuration);
            if (p.checkError()) {
                throw new IOException("Error writing " + path);
            }
        }
    }

    private List<XmlVariable> getVariables(String name) {
        return variables.getOrDefault(name, Collections.emptyList());
    }

    private void addVariables() {
        for (var control : configuration.getControls()) {
            for (var variable : control.getVariables()) {
                add(variable);
            }
        }
    }

    private void wireConditions() {
        for (XmlControl control : configuration.getControls()) {
            for (XmlCondition condition : control.getConditions()) {
                for (XmlElement element : condition.getChildren()) {
                    wireExpression(condition, element);
                }
            }
        }
    }

    private void wireExpression(XmlElement parent, XmlElement element) {
        element.addListener(parent);
        if (element instanceof XmlTest test) {
            wireTest(test);
        }
        if (element instanceof XmlExpression expression) {
            for (XmlExpression child : expression.getExpressions()) {
                wireExpression(expression, child);
            }
        }
    }

    private void wireTest(XmlTest test) {
        String name = test.getName();
        for (XmlVariable variable : getVariables(name)) {
            XmlElement producer = (XmlElement) variable;
            producer.addListener(test);
        }
    }

    private void wireSettings() {
        for (XmlEvent event : configuration.getEvents()) {
            for (XmlSetting setting : event.getSettings()) {
                var control = setting.getControl();
                if (control.isPresent()) {
                    List<XmlVariable> variables = getVariables(control.get());
                   
                    if (variables.isEmpty()) { // dangling reference
                        System.out.println("Warning! Setting '" + setting.getFullName() + "' refers to missing control '" + control.get() + "'");
                    }
                    
                    for (XmlVariable variable : variables) {
                        XmlElement producer = (XmlElement) variable;
                        producer.addListener(setting);
                    }
                }
            }
        }
    }

    private void add(XmlVariable variable) {
        variables.computeIfAbsent(variable.getName(), x -> new ArrayList<>()).add(variable);
    }

    private XmlConfiguration createConfiguration(SafePath file) throws ParseException, IOException {
        if (file.toString().equals("none")) {
            XmlConfiguration configuration = new XmlConfiguration();
            configuration.setAttribute("version", "2.0");
            configuration.setAttribute("label", "None");
            return configuration;
        }
        return Parser.parse(file.toPath());
    }
}
