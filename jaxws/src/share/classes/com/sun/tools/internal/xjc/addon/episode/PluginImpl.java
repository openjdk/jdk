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

package com.sun.tools.internal.xjc.addon.episode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sun.tools.internal.xjc.BadCommandLineException;
import com.sun.tools.internal.xjc.Options;
import com.sun.tools.internal.xjc.Plugin;
import com.sun.tools.internal.xjc.outline.ClassOutline;
import com.sun.tools.internal.xjc.outline.Outline;
import com.sun.tools.internal.xjc.reader.Const;
import com.sun.xml.internal.txw2.TXW;
import com.sun.xml.internal.txw2.output.StreamSerializer;
import com.sun.xml.internal.xsom.XSAnnotation;
import com.sun.xml.internal.xsom.XSAttGroupDecl;
import com.sun.xml.internal.xsom.XSAttributeDecl;
import com.sun.xml.internal.xsom.XSAttributeUse;
import com.sun.xml.internal.xsom.XSComplexType;
import com.sun.xml.internal.xsom.XSComponent;
import com.sun.xml.internal.xsom.XSContentType;
import com.sun.xml.internal.xsom.XSDeclaration;
import com.sun.xml.internal.xsom.XSElementDecl;
import com.sun.xml.internal.xsom.XSFacet;
import com.sun.xml.internal.xsom.XSIdentityConstraint;
import com.sun.xml.internal.xsom.XSModelGroup;
import com.sun.xml.internal.xsom.XSModelGroupDecl;
import com.sun.xml.internal.xsom.XSNotation;
import com.sun.xml.internal.xsom.XSParticle;
import com.sun.xml.internal.xsom.XSSchema;
import com.sun.xml.internal.xsom.XSSimpleType;
import com.sun.xml.internal.xsom.XSWildcard;
import com.sun.xml.internal.xsom.XSXPath;
import com.sun.xml.internal.xsom.visitor.XSFunction;
import com.sun.xml.internal.bind.v2.schemagen.episode.Bindings;

import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Creates the episode file,
 *
 * @author Kohsuke Kawaguchi
 */
public class PluginImpl extends Plugin {

    private File episodeFile;

    public String getOptionName() {
        return "episode";
    }

    public String getUsage() {
        return "  -episode <FILE>    :  generate the episode file for separate compilation";
    }

    public int parseArgument(Options opt, String[] args, int i) throws BadCommandLineException, IOException {
        if(args[i].equals("-episode")) {
            episodeFile = new File(opt.requireArgument("-episode",args,++i));
            return 2;
        }
        return 0;
    }

    /**
     * Capture all the generated classes from global schema components
     * and generate them in an episode file.
     */
    public boolean run(Outline model, Options opt, ErrorHandler errorHandler) throws SAXException {
        try {
            // reorganize qualifying components by their namespaces to
            // generate the list nicely
            Map<XSSchema, List<ClassOutline>> perSchema = new HashMap<XSSchema,List<ClassOutline>>();
            boolean hasComponentInNoNamespace = false;

            for( ClassOutline co : model.getClasses() ) {
                XSComponent sc = co.target.getSchemaComponent();
                if(sc==null)        continue;
                if (!(sc instanceof XSDeclaration))
                    continue;
                XSDeclaration decl = (XSDeclaration) sc;
                if(decl.isLocal())
                    continue;   // local components cannot be referenced from outside, so no need to list.

                List<ClassOutline> list = perSchema.get(decl.getOwnerSchema());
                if(list==null) {
                    list = new ArrayList<ClassOutline>();
                    perSchema.put(decl.getOwnerSchema(),list);
                }

                list.add(co);

                if(decl.getTargetNamespace().equals(""))
                    hasComponentInNoNamespace = true;
            }

            OutputStream os = new FileOutputStream(episodeFile);
            Bindings bindings = TXW.create(Bindings.class, new StreamSerializer(os, "UTF-8"));
            if(hasComponentInNoNamespace) // otherwise jaxb binding NS should be the default namespace
                bindings._namespace(Const.JAXB_NSURI,"jaxb");
            else
                bindings._namespace(Const.JAXB_NSURI,"");
            bindings.version("2.1");
            bindings._comment("\n\n"+opt.getPrologComment()+"\n  ");

            // generate listing per schema
            for (Map.Entry<XSSchema,List<ClassOutline>> e : perSchema.entrySet()) {
                Bindings group = bindings.bindings();
                String tns = e.getKey().getTargetNamespace();
                if(!tns.equals(""))
                    group._namespace(tns,"tns");

                group.scd("x-schema::"+(tns.equals("")?"":"tns"));
                group.schemaBindings().map(false);

                for (ClassOutline co : e.getValue()) {
                    Bindings child = group.bindings();
                    child.scd(co.target.getSchemaComponent().apply(SCD));
                    child.klass().ref(co.implClass.fullName());
                }
                group.commit(true);
            }

            bindings.commit();

            return true;
        } catch (IOException e) {
            errorHandler.error(new SAXParseException("Failed to write to "+episodeFile,null,e));
            return false;
        }
    }

    /**
     * Computes SCD.
     * This is fairly limited as JAXB can only map a certain kind of components to classes.
     */
    private static final XSFunction<String> SCD = new XSFunction<String>() {
        private String name(XSDeclaration decl) {
            if(decl.getTargetNamespace().equals(""))
                return decl.getName();
            else
                return "tns:"+decl.getName();
        }

        public String complexType(XSComplexType type) {
            return "~"+name(type);
        }

        public String simpleType(XSSimpleType simpleType) {
            return "~"+name(simpleType);
        }

        public String elementDecl(XSElementDecl decl) {
            return name(decl);
        }

        // the rest is doing nothing
        public String annotation(XSAnnotation ann) {
            throw new UnsupportedOperationException();
        }

        public String attGroupDecl(XSAttGroupDecl decl) {
            throw new UnsupportedOperationException();
        }

        public String attributeDecl(XSAttributeDecl decl) {
            throw new UnsupportedOperationException();
        }

        public String attributeUse(XSAttributeUse use) {
            throw new UnsupportedOperationException();
        }

        public String schema(XSSchema schema) {
            throw new UnsupportedOperationException();
        }

        public String facet(XSFacet facet) {
            throw new UnsupportedOperationException();
        }

        public String notation(XSNotation notation) {
            throw new UnsupportedOperationException();
        }

        public String identityConstraint(XSIdentityConstraint decl) {
            throw new UnsupportedOperationException();
        }

        public String xpath(XSXPath xpath) {
            throw new UnsupportedOperationException();
        }

        public String particle(XSParticle particle) {
            throw new UnsupportedOperationException();
        }

        public String empty(XSContentType empty) {
            throw new UnsupportedOperationException();
        }

        public String wildcard(XSWildcard wc) {
            throw new UnsupportedOperationException();
        }

        public String modelGroupDecl(XSModelGroupDecl decl) {
            throw new UnsupportedOperationException();
        }

        public String modelGroup(XSModelGroup group) {
            throw new UnsupportedOperationException();
        }
    };
}
