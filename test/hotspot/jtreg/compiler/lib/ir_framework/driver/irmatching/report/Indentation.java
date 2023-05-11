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

package compiler.lib.ir_framework.driver.irmatching.report;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Class used by {@link FailureMessageBuilder} to handle indentations in the failure message. Each indentation level
 * equals 2 whitespaces.
 */
class Indentation {
    private static final int LEVEL_SIZE = 2;
    private int indentation;

    public Indentation(int initialIndentation) {
        this.indentation = initialIndentation;
    }

    public void add() {
        indentation += LEVEL_SIZE;
    }

    public void sub() {
        indentation -= LEVEL_SIZE;
    }

    public List<String> prependForLines(List<String> lines) {
        return lines.stream()
                    .map(s -> s.replaceAll(System.lineSeparator(), System.lineSeparator() + this))
                    .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return " ".repeat(indentation);
    }
}
