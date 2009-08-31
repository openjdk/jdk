/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.tools.internal.jxc.apt;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import javax.xml.bind.SchemaOutputResolver;
import javax.xml.namespace.QName;

import com.sun.mirror.apt.AnnotationProcessor;
import com.sun.mirror.apt.AnnotationProcessorEnvironment;
import com.sun.mirror.declaration.AnnotationTypeDeclaration;
import com.sun.tools.internal.jxc.ConfigReader;
import com.sun.tools.internal.xjc.ErrorReceiver;
import com.sun.tools.internal.xjc.api.J2SJAXBModel;
import com.sun.tools.internal.xjc.api.Reference;
import com.sun.tools.internal.xjc.api.XJC;

import org.xml.sax.SAXException;



/**
 * This class behaves as a JAXB Annotation Processor,
 * It reads the user specified typeDeclarations
 * and the config files
 * It also reads config files
 *
 * @author Bhakti Mehta (bhakti.mehta@sun.com)
 */
final class AnnotationParser implements AnnotationProcessor  {

    /**
     * This is the environment available to the annotationProcessor
     */
    private final AnnotationProcessorEnvironment env;

    private ErrorReceiver errorListener;

    public AnnotationProcessorEnvironment getEnv() {
        return env;
    }


    AnnotationParser(Set<AnnotationTypeDeclaration> atds, AnnotationProcessorEnvironment env) {
        this.env = env;
        errorListener = new ErrorReceiverImpl(env.getMessager(),env.getOptions().containsKey(Const.DEBUG_OPTION));
    }

    public void process() {
        for( Map.Entry<String,String> me : env.getOptions().entrySet() ) {
            String key =  me.getKey();
            if (key.startsWith(Const.CONFIG_FILE_OPTION+'=')) {
                // somehow the values are passed as a part of the key in APT.
                // this is ugly
                String value = key.substring(Const.CONFIG_FILE_OPTION.length()+1);

                // For multiple config files we are following the format
                // -Aconfig=foo.config:bar.config where : is the pathSeparatorChar
                StringTokenizer st = new StringTokenizer(value,File.pathSeparator);
                if(!st.hasMoreTokens()) {
                    errorListener.error(null,Messages.OPERAND_MISSING.format(Const.CONFIG_FILE_OPTION));
                    continue;
                }

                while (st.hasMoreTokens())   {
                    File configFile = new File(st.nextToken());
                    if(!configFile.exists()) {
                        errorListener.error(null,Messages.NON_EXISTENT_FILE.format());
                        continue;
                    }

                    try {
                        ConfigReader configReader = new ConfigReader(env,env.getTypeDeclarations(),configFile,errorListener);

                        Collection<Reference> classesToBeIncluded = configReader.getClassesToBeIncluded();
                        J2SJAXBModel model = XJC.createJavaCompiler().bind(
                                classesToBeIncluded,Collections.<QName,Reference>emptyMap(),null,env);

                        SchemaOutputResolver schemaOutputResolver = configReader.getSchemaOutputResolver();

                        model.generateSchema(schemaOutputResolver,errorListener);
                    } catch (IOException e) {
                        errorListener.error(e.getMessage(),e);
                    } catch (SAXException e) {
                        // the error should have already been reported
                    }
                }
            }
        }
    }
}
