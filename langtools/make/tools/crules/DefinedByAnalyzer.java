/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent.Kind;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.DefinedBy;
import com.sun.tools.javac.util.DefinedBy.Api;

/**This analyzer ensures that all method that implement a public supported API method are marked with
 * {@link DefinedBy} annotation, and that methods that don't implement a public API are not marked
 * using the annotation.
 */
public class DefinedByAnalyzer extends AbstractCodingRulesAnalyzer {

    public DefinedByAnalyzer(JavacTask task) {
        super(task);
        treeVisitor = new DefinedByVisitor();
        eventKind = Kind.ANALYZE;
    }

    class DefinedByVisitor extends TreeScanner {
        @Override
        public void visitMethodDef(JCMethodDecl tree) {
            if (!isAPIPackage(packageName(tree.sym))) {
                boolean seenAPIPackage = false;

                for (MethodSymbol overridden : types.getOverriddenMethods(tree.sym)) {
                    String overriddenPackage = packageName(overridden);

                    if (!isAPIPackage(overriddenPackage))
                        continue;

                    seenAPIPackage = true;

                    DefinedBy definedBy = tree.sym.getAnnotation(DefinedBy.class);

                    if (definedBy != null) {
                        String packageRoot = definedBy.value().packageRoot;
                        if (!overriddenPackage.startsWith(packageRoot)) {
                            messages.error(tree, "crules.wrong.defined.by");
                        }
                        continue;
                    }

                    messages.error(tree, "crules.no.defined.by");
                }

                if (!seenAPIPackage && tree.sym.getAnnotation(DefinedBy.class) != null) {
                    messages.error(tree, "crules.defined.by.no.api");
                }
            }

            super.visitMethodDef(tree);
        }

        private boolean isAPIPackage(String pack) {
            for (Api api : Api.values()) {
                if (pack.startsWith(api.packageRoot))
                    return true;
            }

            return false;
        }

        private String packageName(Symbol sym) {
            return elements.getPackageOf(sym).getQualifiedName().toString();
        }
    }
}
