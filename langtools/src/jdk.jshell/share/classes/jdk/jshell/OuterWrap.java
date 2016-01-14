/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jshell;

import jdk.jshell.Wrap.CompoundWrap;
import static jdk.jshell.Util.*;
import java.util.Locale;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import jdk.jshell.MemoryFileManager.SourceMemoryJavaFileObject;

/**
 *
 * @author Robert Field
 */
final class OuterWrap implements GeneralWrap {

    private final String packageName;
    private final String className;
    private final String userSource;
    private final GeneralWrap w;
    private final Wrap guts;

    public static OuterWrap wrapInClass(String packageName, String className,
             String imports, String userSource, Wrap guts) {
        GeneralWrap kw = new CompoundWrap(
                imports
                + "class " + className + " {\n",
                guts,
                "}\n");
        return new OuterWrap(packageName, className, userSource, kw, guts);
    }

    public static OuterWrap wrapImport(String userSource, Wrap guts) {
        return new OuterWrap("", "", userSource, guts, guts);
    }

    private OuterWrap(String packageName, String className, String userSource,
            GeneralWrap w, Wrap guts) {
        this.packageName = packageName;
        this.className = className;
        this.userSource = userSource;
        this.w = w;
        this.guts = guts;
    }

    @Override
    public final String wrapped() {
        return w.wrapped();
    }

    @Override
    public int snippetIndexToWrapIndex(int ui) {
        return w.snippetIndexToWrapIndex(ui);
    }

    @Override
    public int wrapIndexToSnippetIndex(int si) {
        return w.wrapIndexToSnippetIndex(si);
    }

    @Override
    public int firstSnippetIndex() {
        return w.firstSnippetIndex();
    }

    @Override
    public int lastSnippetIndex() {
        return w.lastSnippetIndex();
    }

    @Override
    public int snippetLineToWrapLine(int snline) {
        return w.snippetLineToWrapLine(snline);
    }

    @Override
    public int wrapLineToSnippetLine(int wline) {
        return w.wrapLineToSnippetLine(wline);
    }

    @Override
    public int firstSnippetLine() {
        return w.firstSnippetLine();
    }

    @Override
    public int lastSnippetLine() {
        return w.lastSnippetLine();
    }

    public String className() {
        return className;
    }

    public String classFullName() {
        return packageName + "." + className;
    }

    public String getUserSource() {
        return userSource;
    }

    Wrap guts() {
        return guts;
    }

    Diag wrapDiag(Diagnostic<? extends JavaFileObject> d) {
        return new WrappedDiagnostic(d);
    }

    class WrappedDiagnostic extends Diag {

        private final Diagnostic<? extends JavaFileObject> diag;

        WrappedDiagnostic(Diagnostic<? extends JavaFileObject> diag) {
            this.diag = diag;
        }

        @Override
        public boolean isError() {
            return diag.getKind() == Diagnostic.Kind.ERROR;
        }

        @Override
        public long getPosition() {
            return wrapIndexToSnippetIndex(diag.getPosition());
        }

        @Override
        public long getStartPosition() {
            return wrapIndexToSnippetIndex(diag.getStartPosition());
        }

        @Override
        public long getEndPosition() {
            return wrapIndexToSnippetIndex(diag.getEndPosition());
        }

        @Override
        public String getCode() {
            return diag.getCode();
        }

        @Override
        public String getMessage(Locale locale) {
            return expunge(diag.getMessage(locale));
        }

        @Override
        Unit unitOrNull() {
            JavaFileObject fo = diag.getSource();
            if (fo instanceof SourceMemoryJavaFileObject) {
                SourceMemoryJavaFileObject sfo = (SourceMemoryJavaFileObject) fo;
                if (sfo.getOrigin() instanceof Unit) {
                    return (Unit) sfo.getOrigin();
                }
            }
            return null;
        }

        @Override
        public String toString() {
            return "WrappedDiagnostic(" + getMessage(null) + ":" + getPosition() + ")";
        }
    }
}
