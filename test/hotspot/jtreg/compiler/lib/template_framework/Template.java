/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package compiler.lib.template_framework;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TODO
 */
public final class Template {
    // Match local variables:
    //   $name
    private static final String LOCAL_VARIABLE_PATTERN = "(\\$\\w+)";

    // Match replacements:
    //   \{name}
    //   \{name:generator}
    //   \{name:generator(arg1,arg2)}
    //   \{:generator}
    private static final String REPLACEMENT_CHARS = "\\w:\\(\\),";
    private static final String REPLACEMENT_PATTERN = "(\\\\\\{[" + REPLACEMENT_CHARS + "]+\\})";

    // Match either local variable or replacement.
    private static final String ALL_PATTERNS = "" +
                                               LOCAL_VARIABLE_PATTERN +
                                               "|" +
                                               REPLACEMENT_PATTERN +
                                               "";
    private static final Pattern PATTERNS = Pattern.compile(ALL_PATTERNS);

    private String templateString;

    public Template(String templateString) {
        this.templateString = templateString;
    }

    public String instantiate() {
        Matcher matcher = PATTERNS.matcher(templateString);

        while (matcher.find()) {
            System.out.println("group: " + matcher.group());
            int start = matcher.start();
            int end = matcher.end();
            String extract = templateString.substring(start, end);
            System.out.println("Found: " + extract);
        }

        return templateString;
    }
}
