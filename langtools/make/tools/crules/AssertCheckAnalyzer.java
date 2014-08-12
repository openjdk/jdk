/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.Tag;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Assert;

public class AssertCheckAnalyzer extends AbstractCodingRulesAnalyzer {

    public AssertCheckAnalyzer(JavacTask task) {
        super(task);
        treeVisitor = new AssertCheckVisitor();
        eventKind = Kind.ANALYZE;
    }

    class AssertCheckVisitor extends TreeScanner {

        @Override
        public void visitApply(JCMethodInvocation tree) {
            Symbol method = TreeInfo.symbolFor(tree);
            if (method != null &&
                method.owner.getQualifiedName().contentEquals(Assert.class.getName()) &&
                !method.name.contentEquals("error")) {
                JCExpression lastParam = tree.args.last();
                if (lastParam != null &&
                    lastParam.type.tsym == syms.stringType.tsym &&
                    lastParam.hasTag(Tag.PLUS)) {
                    messages.error(tree, "crules.should.not.use.string.concatenation");
                }
            }

            super.visitApply(tree);
        }

    }
}
