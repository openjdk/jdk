/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.internal.jfc.model;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import jdk.jfr.internal.jfc.JFC;

import static java.nio.charset.StandardCharsets.UTF_8;

// Holds the structure of a .jfc file similar to an XML DOM.
public final class JFCModel {
    private final Map<String, List<ControlElement>> controls = new LinkedHashMap<>();
    private final XmlConfiguration configuration;
    private final Consumer<String> logger;

    private JFCModel(XmlConfiguration configuration) throws JFCModelException {
        configuration.validate();
        this.configuration = configuration;
        this.logger = x -> { }; // Nothing to log.
    }

    public JFCModel(Reader reader,  Consumer<String> logger) throws IOException, JFCModelException, ParseException {
        this(Parser.parse(reader));
        addControls();
        wireConditions();
        wireSettings(logger);
    }

    public JFCModel(Consumer<String> logger) {
        this.configuration = new XmlConfiguration();
        this.configuration.setAttribute("version", "2.0");
        this.logger = logger;
    }

    public void parse(Path file) throws IOException, JFCModelException, ParseException {
        JFCModel model = JFCModel.create(file, logger);
        for (var entry : model.controls.entrySet()) {
            String name = entry.getKey();
            // Fail-fast checks that prevents an ambiguous file to be written later
            if (controls.containsKey(name)) {
                throw new JFCModelException("Control with '" + name + "' is declared in multiple files");
            }
            controls.put(name, entry.getValue());
        }
        for (XmlElement child : model.configuration.getChildren()) {
            this.configuration.addChild(child);
        }
    }

    public static JFCModel create(Path file, Consumer<String> logger) throws IOException, JFCModelException, ParseException{
        if (file.toString().equals("none")) {
            XmlConfiguration configuration = new XmlConfiguration();
            configuration.setAttribute("version", "2.0");
            configuration.setAttribute("label", "None");
            return new JFCModel(configuration);
        }
        try (Reader r = JFC.newReader(file)) {
            return new JFCModel(r, logger);
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

    public LinkedHashMap<String, String> getSettings() {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (XmlEvent event : configuration.getEvents()) {
            for (XmlSetting setting : event.getSettings()) {
                result.put(event.getName() + "#" + setting.getName(), setting.getContent());
            }
        }
        return result;
    }

    public void saveToFile(Path path) throws IOException {
        try (PrintWriter p = new PrintWriter(path.toFile(), UTF_8)) {
            PrettyPrinter pp = new PrettyPrinter(p);
            pp.print(configuration);
            if (p.checkError()) {
                throw new IOException("Error writing " + path);
            }
        }
    }

    private List<ControlElement> getControlElements(String name) {
        return controls.getOrDefault(name, Collections.emptyList());
    }

    private void addControls() {
        for (var controls : configuration.getControls()) {
            for (var control : controls.getControlElements()) {
                add(control);
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
        for (ControlElement ce : getControlElements(name)) {
            XmlElement control = (XmlElement) ce;
            control.addListener(test);
        }
    }

    private void wireSettings(Consumer<String> logger) {
        for (XmlEvent event : configuration.getEvents()) {
            for (XmlSetting setting : event.getSettings()) {
                var controlName = setting.getControl();
                if (controlName.isPresent()) {
                    List<ControlElement> controls = getControlElements(controlName.get());
                    if (controls.isEmpty()) {
                        logger.accept("Setting '" + setting.getFullName() + "' refers to missing control '" + controlName.get() + "'");
                    }
                    for (ControlElement ce : controls) {
                        XmlElement control = (XmlElement) ce;
                        control.addListener(setting);
                    }
                }
            }
        }
    }

    private void add(ControlElement control) {
        controls.computeIfAbsent(control.getName(), x -> new ArrayList<>()).add(control);
    }
}
