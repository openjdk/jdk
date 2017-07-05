/*
 * Copyright 2002-2007 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
package org.jdesktop.synthdesigner.generator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.Map;

/**
 * TemplateWriter - Class for writing Java Source files using a src template and variable subsitution
 *
 * @author  Richard Bair
 * @author  Jasper Potts
 */
public class TemplateWriter {

    /**
     * Write a Java source file by taking a template file and applying variable substitution and writing to the output
     * file.
     *
     * @param template  The template to use
     * @param variables Map of the variable names and values to substitute in the template
     * @param output    The file to write to
     * @throws IOException If there was a problem writing the Java source file
     */
    static void writeSrcFile(String template, Map<String, String> variables, File output) throws IOException {
        TemplateReader in = new TemplateReader(variables, template);
        PrintWriter out =
                new PrintWriter(new FileWriter(output));

        String line = in.readLine();
        while (line != null) {
            out.println(line);
            line = in.readLine();
        }

        out.close();
    }

    /**
     * Read a template file into a string
     *
     * @param name The template file path relative to Generator class
     * @return The contents of the template file as string
     * @throws IOException If there was a problem reading the template file
     */
    static String read(String name) throws IOException {
        InputStream in = Generator.class.getResourceAsStream(name);
        if (in==null) throw new IOException("Could not find template ["+name+
                "] relative to class ["+Generator.class.getName()+"]");
        byte[] data = new byte[4096];
        int length = -1;
        StringBuilder buffer = new StringBuilder();
        while ((length = in.read(data)) != -1) {
            buffer.append(new String(data, 0, length));
        }
        return buffer.toString();
    }

    /** A BufferedReader implementation that automatically performs string replacements as needed. */
    private static final class TemplateReader extends BufferedReader {
        private Map<String, String> variables;

        TemplateReader(Map<String, String> variables, String template) {
            super(new StringReader(template));
            this.variables = variables;
        }

        /**
         * @return a line of text from the template but with variables substituted. Other methods will return the text
         *         sans substitution. Call this method.
         * @throws java.io.IOException
         */
        public String readLine() throws IOException {
            return substituteVariables(super.readLine());
        }

        private String substituteVariables(String input) {
            if (input == null) return null;
            for (Map.Entry<String, String> variable : variables.entrySet()) {
                input = input.replace("${" + variable.getKey() + "}", variable.getValue());
            }
            return input;
        }
    }

}
