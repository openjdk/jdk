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
package compiler.lib.test_generator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class Template {
    // Atomic counter to ensure thread-safe unique number generation
    private static final AtomicInteger uniqueCounter = new AtomicInteger(1);

    // Pattern to identify placeholders in the format $variableName
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$(\\w+)");

    /**
     * Processes a template string to prevent variable name conflicts by appending a unique identifier.
     *
     * <p>This method scans the input template for placeholders, which are denoted by a '$' followed by a word
     * representing the variable name. Each detected placeholder is replaced with the variable name concatenated
     * with a unique number, ensuring that variable names remain distinct and do not clash within the processed string.
     *
     * @param template The template string containing placeholders to be processed.
     * @return The processed string with unique identifiers appended to variable names.
     */
    public static String avoidConflict(String template) {
        int uniqueId = uniqueCounter.getAndIncrement();
        StringBuilder result = new StringBuilder();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);

        while (matcher.find()) {
            String variableName = matcher.group(1);
            String replacement = variableName + uniqueId;
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }


    /**
     * Abstract method to retrieve the template string for a given variable.
     *
     * @param variable The name of the variable whose template is to be retrieved.
     * @return The template string associated with the specified variable.
     */
    public abstract String getTemplate(String variable);
}
