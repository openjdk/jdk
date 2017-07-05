/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.xjc.generator.bean;

import javax.xml.bind.JAXBContext;

import com.sun.codemodel.internal.JClass;
import com.sun.codemodel.internal.JPackage;
import com.sun.codemodel.internal.fmt.JPropertyFile;
import com.sun.tools.internal.xjc.model.CElementInfo;
import com.sun.tools.internal.xjc.model.Model;
import com.sun.tools.internal.xjc.outline.Aspect;
import com.sun.tools.internal.xjc.runtime.JAXBContextFactory;

/**
 * Generates private ObjectFactory.
 *
 * <p>
 * This class also puts a copy of {@link JAXBContextFactory}
 * to the impl package.
 *
 * @author Kohsuke Kawaguchi
 */
final class PrivateObjectFactoryGenerator extends ObjectFactoryGeneratorImpl {
    public PrivateObjectFactoryGenerator(BeanGenerator outline, Model model, JPackage targetPackage) {
        super(outline, model, targetPackage.subPackage("impl"));

        JPackage implPkg = targetPackage.subPackage("impl");

        // put JAXBContextFactory into the impl package
        JClass factory = outline.generateStaticClass(JAXBContextFactory.class,implPkg);

        // and then put jaxb.properties to point to it
        JPropertyFile jaxbProperties = new JPropertyFile("jaxb.properties");
        targetPackage.addResourceFile(jaxbProperties);
        jaxbProperties.add(
            JAXBContext.JAXB_CONTEXT_FACTORY,
            factory.fullName());
    }

    void populate(CElementInfo ei) {
        populate(ei,Aspect.IMPLEMENTATION,Aspect.IMPLEMENTATION);
    }

    void populate(ClassOutlineImpl cc) {
        populate(cc,cc.implRef);
    }
}
