/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.internal.query;

import java.io.IOException;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jdk.jfr.internal.util.Tokenizer;

/**
 * Represents a configuration file that holds a set up queries and their
 * associated metadata, such as labels and descriptions.
 */
final class ViewFile {
    record ViewConfiguration(String name, String category, Map<String, String> properties) {
        public String query() {
            String form = get("form");
            if (form != null) {
                return form;
            }
            String table = get("table");
            if (table != null) {
                return table;
            }
            throw new IllegalStateException("Expected section to have form or table attribute");
        }

        public String getLabel() {
            return get("label");
        }

        public String getForm() {
            return get("form");
        }

        public String getTable() {
            return get("table");
        }

        private String get(String key) {
            return properties.get(key);
        }
    }

    private final List<ViewConfiguration> configurations;

    public ViewFile(String text) throws ParseException {
        this.configurations = parse(text);
    }

    public static ViewFile getDefault() {
        try {
            var is = ViewFile.class.getResourceAsStream("/jdk/jfr/internal/query/view.ini");
            byte[] bytes = is.readAllBytes();
            String query = new String(bytes, Charset.forName("UTF-8"));
            return new ViewFile(query);
        } catch (ParseException e) {
            throw new InternalError("Internal error, invalid view.ini", e);
        } catch (IOException e) {
            throw new InternalError("Internal error, could not read view.ini", e);
        }
    }

    public List<ViewConfiguration> getViewConfigurations() {
       return configurations;
    }

    private List<ViewConfiguration> parse(String text) throws ParseException {
        List<ViewConfiguration> views = new ArrayList<>();
        try (Tokenizer tokenizer = new Tokenizer(text, '[', ']', ';')) {
            while (tokenizer.hasNext()) {
                while (tokenizer.accept(";")) {
                    tokenizer.skipLine();
                }
                if (tokenizer.accept("[")) {
                    String fullName = tokenizer.next();
                    tokenizer.expect("]");
                    views.add(createView(fullName));
                }
                if (views.isEmpty()) {
                    throw new ParseException("Expected view file to begin with a section", tokenizer.getPosition());
                }
                String key = tokenizer.next();
                tokenizer.expect("=");
                String value = tokenizer.next();
                views.getLast().properties().put(key, value);
            }
        }
        return views;
    }

    private ViewConfiguration createView(String fullName) {
        int index = fullName.lastIndexOf(".");
        if (index == -1) {
            throw new InternalError("Missing name space for " + fullName);
        }
        String category = fullName.substring(0, index);
        String name = fullName.substring(index + 1);
        return new ViewConfiguration(name, category, new LinkedHashMap<>());
    }
}
