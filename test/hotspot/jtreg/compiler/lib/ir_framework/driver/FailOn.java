/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.ir_framework.driver;

import compiler.lib.ir_framework.IR;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class representing a failOn attribute of an IR rule.
 *
 * @see IR#failOn()
 */
class FailOn extends CheckAttribute {
    public Pattern quickPattern;
    public List<String> nodes;

    public FailOn(List<String> nodes) {
        this.nodes = nodes;
        this.quickPattern = Pattern.compile(String.join("|", nodes));
    }

    @Override
    public List<? extends Failure> apply(String compilation) {
        Matcher matcher = quickPattern.matcher(compilation);
        if (matcher.find()) {
            return getFailureResults(compilation);
        }
        return Failure.NO_FAILURE;
    }

    private List<FailOnFailure> getFailureResults(String compilation) {
        List<FailOnFailure> failures = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            String node = nodes.get(i);
            Pattern p = Pattern.compile(node);
            Matcher m = p.matcher(compilation);
            if (m.find()) {
                List<String> matches = getMatchedNodes(m);
                failures.add(new FailOnFailure(node, i + 1, matches));
            }
        }
        return failures;
    }
}
