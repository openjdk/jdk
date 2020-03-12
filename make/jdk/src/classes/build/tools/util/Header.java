/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

package build.tools.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Header {

    // template: a file inside make/template/
    // years: "2020," or "2000, 2020,"
    public static List<String> javaHeader(Path template, String years)
            throws IOException {
        List<String> result = new ArrayList<>();
        result.add("/*");
        int emptyLines = 0;
        for (String line :  Files.readAllLines(template)) {
            if (line.isEmpty()) {
                emptyLines++;
            } else {
                // Only add empty lines when they are not at the end
                for (int i = 0; i < emptyLines; i++) {
                    result.add(" *");
                }
                emptyLines = 0;
                result.add(" * " + line.replace("%YEARS%", years));
            }
        }
        result.add(" */");
        return result;
    }

    public static List<String> javaHeader(Path template)
            throws IOException {
        return javaHeader(template, LocalDate.now().getYear() + ",");
    }
}
