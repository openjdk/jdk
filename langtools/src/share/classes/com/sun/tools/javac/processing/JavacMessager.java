/*
 * Copyright (c) 2005, 2008, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.processing;

import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.*;
import javax.lang.model.element.*;
import javax.tools.JavaFileObject;
import javax.tools.Diagnostic;
import javax.annotation.processing.*;

/**
 * An implementation of the Messager built on top of log.
 *
 * <p><b>This is NOT part of any API supported by Sun Microsystems.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.</b>
 */
public class JavacMessager implements Messager {
    Log log;
    JavacProcessingEnvironment processingEnv;
    int errorCount = 0;

    JavacMessager(Context context, JavacProcessingEnvironment processingEnv) {
        log = Log.instance(context);
        this.processingEnv = processingEnv;
    }

    // processingEnv.getElementUtils()

    public void printMessage(Diagnostic.Kind kind, CharSequence msg) {
        printMessage(kind, msg, null, null, null);
    }

    public void printMessage(Diagnostic.Kind kind, CharSequence msg,
                      Element e) {
        printMessage(kind, msg, e, null, null);
    }

    /**
     * Prints a message of the specified kind at the location of the
     * annotation mirror of the annotated element.
     *
     * @param kind the kind of message
     * @param msg  the message, or an empty string if none
     * @param e    the annotated element
     * @param a    the annotation to use as a position hint
     */
    public void printMessage(Diagnostic.Kind kind, CharSequence msg,
                      Element e, AnnotationMirror a) {
        printMessage(kind, msg, e, a, null);
    }

    /**
     * Prints a message of the specified kind at the location of the
     * annotation value inside the annotation mirror of the annotated
     * element.
     *
     * @param kind the kind of message
     * @param msg  the message, or an empty string if none
     * @param e    the annotated element
     * @param a    the annotation containing the annotaiton value
     * @param v    the annotation value to use as a position hint
     */
    public void printMessage(Diagnostic.Kind kind, CharSequence msg,
                      Element e, AnnotationMirror a, AnnotationValue v) {
        JavaFileObject oldSource = null;
        JavaFileObject newSource = null;
        JCDiagnostic.DiagnosticPosition pos = null;
        JavacElements elemUtils = processingEnv.getElementUtils();
        Pair<JCTree, JCCompilationUnit> treeTop = elemUtils.getTreeAndTopLevel(e, a, v);
        if (treeTop != null) {
            newSource = treeTop.snd.sourcefile;
            if (newSource != null) {
                oldSource = log.useSource(newSource);
                pos = treeTop.fst.pos();
            }
        }
        try {
            switch (kind) {
            case ERROR:
                errorCount++;
                boolean prev = log.multipleErrors;
                log.multipleErrors = true;
                try {
                    log.error(pos, "proc.messager", msg.toString());
                } finally {
                    log.multipleErrors = prev;
                }
                break;

            case WARNING:
                log.warning(pos, "proc.messager", msg.toString());
                break;

            case MANDATORY_WARNING:
                log.mandatoryWarning(pos, "proc.messager", msg.toString());
                break;

            default:
                log.note(pos, "proc.messager", msg.toString());
                break;
            }
        } finally {
            if (oldSource != null)
                log.useSource(oldSource);
        }
    }

    /**
     * Prints an error message.
     * Equivalent to {@code printError(null, msg)}.
     * @param msg  the message, or an empty string if none
     */
    public void printError(String msg) {
        printMessage(Diagnostic.Kind.ERROR, msg);
    }

    /**
     * Prints a warning message.
     * Equivalent to {@code printWarning(null, msg)}.
     * @param msg  the message, or an empty string if none
     */
    public void printWarning(String msg) {
        printMessage(Diagnostic.Kind.WARNING, msg);
    }

    /**
     * Prints a notice.
     * @param msg  the message, or an empty string if none
     */
    public void printNotice(String msg) {
        printMessage(Diagnostic.Kind.NOTE, msg);
    }

    public boolean errorRaised() {
        return errorCount > 0;
    }

    public int errorCount() {
        return errorCount;
    }

    public void newRound(Context context) {
        log = Log.instance(context);
        errorCount = 0;
    }

    public String toString() {
        return "javac Messager";
    }
}
