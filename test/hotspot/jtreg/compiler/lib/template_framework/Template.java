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
    private static final Pattern LOCAL_VARIABLE_PATTERN = Pattern.compile("\\$(\\w+)");
    private static final Pattern REPLACEMENT_PATTERN = Pattern.compile("\\$(\\w+)");

    private String templateString;

    public Template(String templateString) {
        this.templateString = templateString;
    }

    public String instantiate() {
        Matcher matcher = LOCAL_VARIABLE_PATTERN.matcher(templateString);

        while (matcher.find()) {
            String variableName = matcher.group(1);
            int start = matcher.start();
            int end = matcher.end();
            String extract = templateString.substring(start, end);
            System.out.println("Found local variable: " + variableName + " vs " + extract);
            //String replacement = variableName + uniqueId;
            //matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        // matcher.appendTail(result);

        return templateString;
    }
}
