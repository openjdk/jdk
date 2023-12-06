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


/**
 * Class responsible for printing and formatting the contents of the first row in a table,
 * as a form.
 */
import jdk.jfr.internal.util.Output;

final class FormRenderer {
    private static final String LABEL_SUFFIX = ": ";
    private final Table table;
    private final Output out;
    private final Configuration configuration;
    private final int width;

    public FormRenderer(Configuration configuration, Table table) {
        this.table = table;
        this.out = configuration.output;
        this.configuration = configuration;
        this.width = determineWidth(configuration);
    }

    private static int determineWidth(Configuration configuration) {
        if (configuration.width == 0) {
            return Configuration.PREFERRED_WIDTH;
        } else {
            return configuration.width;
        }
    }

    public void render() {
        if (table.isEmpty()) {
            if (configuration.title != null) {
                out.println();
                out.println("No events found for '" + configuration.title +"'.");
            }
            return;
        }

        int maxWidth = 0;
        for (Field field : table.getFields()) {
            String label = field.label + LABEL_SUFFIX;
            maxWidth = Math.max(label.length() + 1, maxWidth);
        }
        out.println();
        if (maxWidth + 2 > width) {
            out.println("Columns are too wide to fit width " + configuration.width + ".");
            return;
        }
        if (configuration.title != null) {
            out.println(configuration.title);
            out.println("-".repeat(configuration.title.length()));
        }
        if (table.isEmpty()) {
            return;
        }
        for (Field field : table.getFields()) {
            if (field.visible) {
                out.println();
                renderField(field);
            }
        }
    }

    private void renderField(Field field) {
        Row row = table.getRows().getFirst();
        String label = field.label + LABEL_SUFFIX;
        Object value = row.getValue(field.index);
        String text = FieldFormatter.format(field, value);
        boolean newLine = false;
        out.print(label);
        long p = width - label.length() - 1;
        for (int i = 0; i < text.length(); i++) {
            if (newLine) {
                out.print(" ".repeat(label.length()));
                newLine = false;
            }
            out.print(text.charAt(i));
            if (i % p == p - 1) {
                newLine = true;
                out.println();
            }
        }
        out.println();
    }

    public int getWidth() {
        return width;
    }
}