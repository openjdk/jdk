/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

package crules;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.tree.TreeScanner;

import static com.sun.source.util.TaskEvent.Kind;
import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.tree.JCTree.JCVariableDecl;

public class MutableFieldsAnalyzer extends AbstractCodingRulesAnalyzer {

    public MutableFieldsAnalyzer() {
        treeVisitor = new MutableFieldsVisitor();
        eventKind = Kind.ANALYZE;
    }

    public String getName() {
        return "mutable_fields_analyzer";
    }

    private boolean ignoreField(String className, String field) {
        List<String> currentFieldsToIgnore =
                classFieldsToIgnoreMap.get(className);
        if (currentFieldsToIgnore != null) {
            for (String fieldToIgnore : currentFieldsToIgnore) {
                if (field.equals(fieldToIgnore)) {
                    return true;
                }
            }
        }
        return false;
    }

    class MutableFieldsVisitor extends TreeScanner {

        @Override
        public void visitVarDef(JCVariableDecl tree) {
            boolean isJavacPack = tree.sym.outermostClass().fullname.toString()
                    .contains(packageToCheck);
            if (isJavacPack &&
                (tree.sym.flags() & SYNTHETIC) == 0 &&
                tree.sym.owner.kind == Kinds.TYP) {
                if (!ignoreField(tree.sym.owner.flatName().toString(),
                        tree.getName().toString())) {
                    boolean enumClass = (tree.sym.owner.flags() & ENUM) != 0;
                    boolean nonFinalStaticEnumField =
                            (tree.sym.flags() & (ENUM | FINAL)) == 0;
                    boolean nonFinalStaticField =
                            (tree.sym.flags() & STATIC) != 0 &&
                            (tree.sym.flags() & FINAL) == 0;
                    if (enumClass ? nonFinalStaticEnumField : nonFinalStaticField) {
                        messages.error(tree, "crules.err.var.must.be.final", tree);
                    }
                }
            }
            super.visitVarDef(tree);
        }

    }

    private static final String packageToCheck = "com.sun.tools.javac";

    private static final Map<String, List<String>> classFieldsToIgnoreMap =
                new HashMap<String, List<String>>();

    static {
        classFieldsToIgnoreMap.
                put("com.sun.tools.javac.util.JCDiagnostic",
                    Arrays.asList("fragmentFormatter"));
        classFieldsToIgnoreMap.
                put("com.sun.tools.javac.util.JavacMessages",
                    Arrays.asList("defaultBundle", "defaultMessages"));
        classFieldsToIgnoreMap.
                put("com.sun.tools.javac.file.ZipFileIndexCache",
                    Arrays.asList("sharedInstance"));
        classFieldsToIgnoreMap.
                put("com.sun.tools.javac.main.JavaCompiler",
                    Arrays.asList("versionRB"));
        classFieldsToIgnoreMap.
                put("com.sun.tools.javac.code.Type",
                    Arrays.asList("moreInfo"));
        classFieldsToIgnoreMap.
                put("com.sun.tools.javac.util.SharedNameTable",
                    Arrays.asList("freelist"));
        classFieldsToIgnoreMap.
                put("com.sun.tools.javac.util.Log",
                    Arrays.asList("useRawMessages"));
    }

}
