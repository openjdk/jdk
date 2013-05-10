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

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;

import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.Trees;
import com.sun.tools.javac.api.BasicJavacTask;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Log;

import static com.sun.source.util.TaskEvent.Kind;

public abstract class AbstractCodingRulesAnalyzer implements Plugin {

    protected Log log;
    protected Trees trees;
    protected TreeScanner treeVisitor;
    protected Kind eventKind;
    protected Messages messages;

    public void init(JavacTask task, String... args) {
        BasicJavacTask impl = (BasicJavacTask)task;
        Context context = impl.getContext();
        log = Log.instance(context);
        trees = Trees.instance(task);
        messages = new Messages();
        task.addTaskListener(new PostAnalyzeTaskListener());
    }

    public class PostAnalyzeTaskListener implements TaskListener {

        @Override
        public void started(TaskEvent taskEvent) {}

        @Override
        public void finished(TaskEvent taskEvent) {
            if (taskEvent.getKind().equals(eventKind)) {
                TypeElement typeElem = taskEvent.getTypeElement();
                Tree tree = trees.getTree(typeElem);
                if (tree != null) {
                    JavaFileObject prevSource = log.currentSourceFile();
                    try {
                        log.useSource(taskEvent.getCompilationUnit().getSourceFile());
                        treeVisitor.scan((JCTree)tree);
                    } finally {
                        log.useSource(prevSource);
                    }
                }
            }
        }
    }

    class Messages {
        ResourceBundle bundle;

        Messages() {
            String name = getClass().getPackage().getName() + ".resources.crules";
            bundle = ResourceBundle.getBundle(name, Locale.ENGLISH);
        }

        public void error(JCTree tree, String code, Object... args) {
            String msg = (code == null) ? (String) args[0] : localize(code, args);
            log.error(tree, "proc.messager", msg.toString());
        }

        private String localize(String code, Object... args) {
            String msg = bundle.getString(code);
            if (msg == null) {
                StringBuilder sb = new StringBuilder();
                sb.append("message file broken: code=").append(code);
                if (args.length > 0) {
                    sb.append(" arguments={0}");
                    for (int i = 1; i < args.length; i++) {
                        sb.append(", {").append(i).append("}");
                    }
                }
                msg = sb.toString();
            }
            return MessageFormat.format(msg, args);
        }
    }

}
