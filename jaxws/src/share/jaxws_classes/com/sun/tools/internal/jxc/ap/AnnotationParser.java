/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.jxc.ap;

import com.sun.tools.internal.jxc.ConfigReader;
import com.sun.tools.internal.jxc.api.JXC;
import com.sun.tools.internal.xjc.ErrorReceiver;
import com.sun.tools.internal.xjc.api.J2SJAXBModel;
import com.sun.tools.internal.xjc.api.Reference;
import org.xml.sax.SAXException;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.xml.bind.SchemaOutputResolver;
import javax.xml.namespace.QName;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * This class behaves as a JAXB Annotation Processor,
 * It reads the user specified typeDeclarations
 * and the config files
 * It also reads config files
 *
 * Used in unit tests
 *
 * @author Bhakti Mehta (bhakti.mehta@sun.com)
 */
@SupportedAnnotationTypes("javax.xml.bind.annotation.*")
@SupportedOptions("jaxb.config")
public final class AnnotationParser extends AbstractProcessor {

    private ErrorReceiver errorListener;

    @Override
    public void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.processingEnv = processingEnv;
        errorListener = new ErrorReceiverImpl(
                processingEnv.getMessager(),
                processingEnv.getOptions().containsKey(Const.DEBUG_OPTION.getValue())
        );
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (processingEnv.getOptions().containsKey(Const.CONFIG_FILE_OPTION.getValue())) {
            String value = processingEnv.getOptions().get(Const.CONFIG_FILE_OPTION.getValue());

            // For multiple config files we are following the format
            // -Aconfig=foo.config:bar.config where : is the pathSeparatorChar
            StringTokenizer st = new StringTokenizer(value, File.pathSeparator);
            if (!st.hasMoreTokens()) {
                errorListener.error(null, Messages.OPERAND_MISSING.format(Const.CONFIG_FILE_OPTION.getValue()));
                return true;
            }

            while (st.hasMoreTokens()) {
                File configFile = new File(st.nextToken());
                if (!configFile.exists()) {
                    errorListener.error(null, Messages.NON_EXISTENT_FILE.format());
                    continue;
                }

                try {
                    Collection<TypeElement> rootElements = new ArrayList<TypeElement>();
                    filterClass(rootElements, roundEnv.getRootElements());
                    ConfigReader configReader = new ConfigReader(
                            processingEnv,
                            rootElements,
                            configFile,
                            errorListener
                    );

                    Collection<Reference> classesToBeIncluded = configReader.getClassesToBeIncluded();
                    J2SJAXBModel model = JXC.createJavaCompiler().bind(
                            classesToBeIncluded, Collections.<QName, Reference>emptyMap(), null, processingEnv);

                    SchemaOutputResolver schemaOutputResolver = configReader.getSchemaOutputResolver();

                    model.generateSchema(schemaOutputResolver, errorListener);
                } catch (IOException e) {
                    errorListener.error(e.getMessage(), e);
                } catch (SAXException e) {
                    // the error should have already been reported
                }
            }
        }
        return true;
    }

    private void filterClass(Collection<TypeElement> rootElements, Collection<? extends Element> elements) {
        for (Element element : elements) {
            if (element.getKind().equals(ElementKind.CLASS) || element.getKind().equals(ElementKind.INTERFACE) ||
                    element.getKind().equals(ElementKind.ENUM)) {
                rootElements.add((TypeElement) element);
                filterClass(rootElements, ElementFilter.typesIn(element.getEnclosedElements()));
            }
        }
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        if (SourceVersion.latest().compareTo(SourceVersion.RELEASE_6) > 0)
            return SourceVersion.valueOf("RELEASE_7");
        else
            return SourceVersion.RELEASE_6;
    }
}
