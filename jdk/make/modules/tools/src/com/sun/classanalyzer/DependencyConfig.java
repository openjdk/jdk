/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
package com.sun.classanalyzer;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

/**
 * Config file specifying additional dependency
 * Each line consists of:
 * <tag> <classname> -> <value>
 * where <tag> can be:
 *    @ClassForName and <value> is its dependency
 *    @Provider and <value> is the service name
 *    @Providers and <value> is the list of the service names
 *
 * @author Mandy Chung
 */
public class DependencyConfig {
    private DependencyConfig() {
    }

    static void parse(List<String> configs) throws IOException {
        for (String s : configs) {
            parse(s);
        }
    }

    private static void parse(String config) throws IOException {
        // parse configuration file
        FileInputStream in = new FileInputStream(config);
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line;
            int lineNumber = 0;
            String type = null;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                if (line.length() == 0 || line.charAt(0) == '#') {
                    continue;
                }
                if (line.charAt(0) == '@') {
                    if (AnnotatedDependency.isValidType(line)) {
                        type = line;
                        continue;
                    } else {
                        throw new RuntimeException(config + ", line " +
                            lineNumber + ", invalid annotation type.");
                    }
                }
                String[] s = line.split("\\s+");
                if (s.length < 3 || !s[1].equals("->")) {
                    throw new RuntimeException(config + ", line " +
                            lineNumber + ", is malformed");
                }
                String classname = s[0].trim();
                String value = s[2].trim();

                Klass k = Klass.findKlass(classname);
                if (k == null) {
                    // System.out.println("Warning: " + classname + " cannot be found");
                    continue;
                }
                AnnotatedDependency dep = AnnotatedDependency.newAnnotatedDependency(type, value, k);
                if (dep == null) {
                    throw new RuntimeException(config + ", line " +
                            lineNumber + ", is malformed. Fail to construct the dependency.");
                }
            }

        } finally {
            in.close();
        }
    }
}
