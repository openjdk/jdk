/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.valueclass;

import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.api.BasicJavacTask;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Preview;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Context;

/**
 * A javac plugin that transforms classes annotated with
 * {@code @jdk.test.valueclass.AsValueClass} into value classes when
 * {@code --enable-preview} is active.
 *
 * <p>The plugin hooks into the PARSE phase. After a compilation unit is
 * parsed it walks the AST looking for class declarations whose modifier list
 * contains an annotation whose simple name ends with {@code "ValueClass"}.
 * For each such class it adds the internal {@code VALUE_CLASS} modifier flag
 * and clears the {@code IDENTITY_TYPE} flag (which is set by default on all
 * classes), causing the compiler to treat the class as a value class for all
 * subsequent phases (Enter, Attr, Gen, …).
 *
 * <p>If {@code --enable-preview} is <em>not</em> active the plugin is a
 * no-op: the annotated classes remain ordinary identity classes, allowing
 * the same test source to run in both preview and non-preview environments.
 *
 * <p>Activated via {@code -Xplugin:ValueClassPlugin} in the javac options.
 * Must be paired with {@code --enable-preview} for the transformation to
 * take effect.
 */
public class ValueClassPlugin implements Plugin {

    @Override
    public String getName() {
        return "ValueClassPlugin";
    }

    @Override
    public void init(JavacTask task, String... args) {
        Context ctx = ((BasicJavacTask) task).getContext();
        task.addTaskListener(new TaskListener() {
            @Override
            public void finished(TaskEvent e) {
                if (e.getKind() != TaskEvent.Kind.PARSE) return;

                Preview preview = Preview.instance(ctx);
                if (!preview.isEnabled()) return;

                new TreeScanner() {
                    @Override
                    public void visitClassDef(JCClassDecl tree) {
                        boolean hasAnnotation = tree.mods.annotations.stream()
                                .anyMatch(a -> a.annotationType.toString()
                                        .equals("AsValueClass"));
                        if (hasAnnotation) {
                            tree.mods.flags |= Flags.VALUE_CLASS;
                            tree.mods.flags &= ~Flags.IDENTITY_TYPE;
                            // Mark the source file as using a preview feature so
                            // the class file gets minor version 0xFFFF, which the
                            // JVM requires to recognize the class as a value class.
                            preview.markUsesPreview(null);
                        }
                        super.visitClassDef(tree);
                    }
                }.scan((JCTree) e.getCompilationUnit());
            }
        });
    }
}
