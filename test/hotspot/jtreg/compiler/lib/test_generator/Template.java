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
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static compiler.lib.test_generator.InputTemplate.doReplacements;

public abstract class Template {
    public Template() {}

    public static String reassemble(String dec, String cod) {
        String assembe_template = """
                \\{declare}
        \\{code}
               \s""";
        Map<String, String> replacement_code = Map.ofEntries(
                Map.entry("declare", dec),
                Map.entry("code", cod)
        );
        return doReplacements(assembe_template, replacement_code);
    }

    public static String avoid_conflict(String temp,int num){
        StringBuffer result = new StringBuffer();
        String regex="\\$(\\w+)";
        Pattern pat = Pattern.compile(regex);
        Matcher mat = pat.matcher(temp);
        while(mat.find()){
            String replacement = mat.group(1)+num;
            mat.appendReplacement(result, replacement);
        }
        mat.appendTail(result);
        return result.toString();
    }

    public String getTemplate(List<String> values){
        return "";
    }
}
