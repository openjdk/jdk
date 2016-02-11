/*
 * Copyright (c) 2003, 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.standard;

import java.util.Locale;
import java.util.Set;

import javax.lang.model.SourceVersion;

import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Reporter;
import jdk.javadoc.internal.doclets.formats.html.HtmlDoclet;

public class Standard implements Doclet {

    private final HtmlDoclet htmlDoclet;

    public Standard() {
        htmlDoclet = new HtmlDoclet();
    }

    public void init(Locale locale, Reporter reporter) {
        htmlDoclet.init(locale, reporter);
    }

    public String getName() {
        return "Standard";
    }

    public Set<Doclet.Option> getSupportedOptions() {
        return htmlDoclet.getSupportedOptions();
    }

    public SourceVersion getSupportedSourceVersion() {
        return htmlDoclet.sourceVersion();
    }

    public boolean run(DocletEnvironment root) {
        return htmlDoclet.run(root);
    }
}
