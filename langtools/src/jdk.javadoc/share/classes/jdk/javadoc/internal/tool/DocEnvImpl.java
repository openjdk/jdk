/*
 * Copyright (c) 1997, 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.tool;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaFileManager;

import com.sun.source.util.DocTrees;
import com.sun.tools.javac.code.Source;
import jdk.javadoc.doclet.DocletEnvironment;

/**
 * This class holds the information from one run of javadoc.
 * Particularly the packages, classes and options specified
 * by the user.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Robert Field
 * @author Atul M Dambalkar
 * @author Neal Gafter (rewrite)
 */
public class DocEnvImpl implements DocletEnvironment {

    public final ElementsTable etable;

    public final ToolEnvironment toolEnv;

    /**
     * Construct a doclet environment.
     *
     * @param toolEnv the tool environment
     * @param etable the includes table, providing all the information
     * with respect to specified, included/selected elements.
     */
    public DocEnvImpl(ToolEnvironment toolEnv, ElementsTable etable) {
        this.toolEnv = toolEnv;
        this.etable = etable;
    }

    @Override
    public Set<ModuleElement> getIncludedModuleElements() {
        return etable.getIncludedModuleElements();
    }

    @Override
    public Set<PackageElement> getIncludedPackageElements() {
        return etable.getIncludedPackageElements();
    }

    /**
     * Return all TypeElements (including those inside
     * packages) to be documented.
     */
    @Override
    public Set<TypeElement> getIncludedTypeElements() {
        return etable.getIncludedTypeElements();
    }

    @Override
    public boolean isIncluded(Element e) {
        return etable.isIncluded(e);
    }

    @Override
    public DocTrees getDocTrees() {
        return toolEnv.docTrees;
    }

    @Override
    public Elements getElementUtils() {
        return toolEnv.elements;
    }

    @Override
    public List<Element> getSelectedElements(List<? extends Element> elements) {
        return elements.stream()
                .filter(e -> isIncluded(e))
                .collect(Collectors.<Element>toList());
    }

    @Override
    public Set<Element> getSpecifiedElements() {
        Set<Element> out = new LinkedHashSet<>();
        out.addAll(etable.getSpecifiedModuleElements());
        out.addAll(etable.getSpecifiedPackageElements());
        out.addAll(etable.getSpecifiedTypeElements());
        return out;
    }

    @Override
    public Types getTypeUtils() {
        return toolEnv.typeutils;
    }

    @Override
    public JavaFileManager getJavaFileManager() {
        return toolEnv.fileManager;
    }

    @Override
    public SourceVersion getSourceVersion() {
        return Source.toSourceVersion(toolEnv.source);
    }

    @Override
    public ModuleMode getModuleMode() {
        return etable.getModuleMode();
    }
}
