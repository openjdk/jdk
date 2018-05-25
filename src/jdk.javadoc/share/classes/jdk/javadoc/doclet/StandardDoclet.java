/*
 * Copyright (c) 2003, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.doclet;

import java.util.Locale;
import java.util.Set;

import javax.lang.model.SourceVersion;

import jdk.javadoc.internal.doclets.formats.html.HtmlDoclet;

/**
 * This doclet generates HTML-formatted documentation for the specified modules,
 * packages and types.
 *
 * @see <a href="{@docRoot}/../specs/doc-comment-spec.html">
 *      Documentation Comment Specification for the Standard Doclet</a>
 */
public class StandardDoclet implements Doclet {

    private final HtmlDoclet htmlDoclet;

    public StandardDoclet() {
        htmlDoclet = new HtmlDoclet(this);
    }

    @Override
    public void init(Locale locale, Reporter reporter) {
        htmlDoclet.init(locale, reporter);
    }

    @Override
    public String getName() {
        return "Standard";
    }

    @Override
    public Set<Doclet.Option> getSupportedOptions() {
        return htmlDoclet.getSupportedOptions();
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return htmlDoclet.getSupportedSourceVersion();
    }

    @Override
    public boolean run(DocletEnvironment docEnv) {
        return htmlDoclet.run(docEnv);
    }
}
