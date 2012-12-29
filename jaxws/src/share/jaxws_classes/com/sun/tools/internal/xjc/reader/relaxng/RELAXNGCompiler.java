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

package com.sun.tools.internal.xjc.reader.relaxng;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.namespace.QName;

import com.sun.codemodel.internal.JCodeModel;
import com.sun.codemodel.internal.JPackage;
import com.sun.tools.internal.xjc.Options;
import com.sun.tools.internal.xjc.model.CBuiltinLeafInfo;
import com.sun.tools.internal.xjc.model.CClassInfo;
import com.sun.tools.internal.xjc.model.CClassInfoParent;
import com.sun.tools.internal.xjc.model.CEnumConstant;
import com.sun.tools.internal.xjc.model.CEnumLeafInfo;
import com.sun.tools.internal.xjc.model.CNonElement;
import com.sun.tools.internal.xjc.model.CTypeInfo;
import com.sun.tools.internal.xjc.model.Model;
import com.sun.tools.internal.xjc.model.TypeUse;
import com.sun.xml.internal.bind.api.impl.NameConverter;

import com.sun.xml.internal.rngom.digested.DChoicePattern;
import com.sun.xml.internal.rngom.digested.DDefine;
import com.sun.xml.internal.rngom.digested.DElementPattern;
import com.sun.xml.internal.rngom.digested.DPattern;
import com.sun.xml.internal.rngom.digested.DPatternWalker;
import com.sun.xml.internal.rngom.digested.DRefPattern;
import com.sun.xml.internal.rngom.digested.DValuePattern;
import com.sun.xml.internal.rngom.nc.NameClass;
import com.sun.xml.internal.rngom.xml.util.WellKnownNamespaces;

/**
 * @author Kohsuke Kawaguchi
 */
public final class RELAXNGCompiler {
    /**
     * Schema to compile.
     */
    final DPattern grammar;

    /**
     * All named patterns in this schema.
     */
    final Set<DDefine> defs;

    final Options opts;

    final Model model;

    /**
     * The package to which we generate the code into.
     */
    final JPackage pkg;

    final Map<String,DatatypeLib> datatypes = new HashMap<String, DatatypeLib>();

    /**
     * Patterns that are mapped to Java concepts.
     *
     * <p>
     * The value is an array because we map elements with finite names
     * to multiple classes.
     *
     * TODO: depending on the type of the key, the type of the values can be further
     * restricted. Make this into its own class to represent those constraints better.
     */
    final Map<DPattern,CTypeInfo[]> classes = new HashMap<DPattern,CTypeInfo[]>();

    /**
     * Classes that need to be bound.
     *
     * The value is the content model to be bound.
     */
    final Map<CClassInfo,DPattern> bindQueue = new HashMap<CClassInfo,DPattern>();

    final TypeUseBinder typeUseBinder = new TypeUseBinder(this);

    public static Model build(DPattern grammar, JCodeModel codeModel, Options opts ) {
        RELAXNGCompiler compiler = new RELAXNGCompiler(grammar, codeModel, opts);
        compiler.compile();
        return compiler.model;
    }

    public RELAXNGCompiler(DPattern grammar, JCodeModel codeModel, Options opts) {
        this.grammar = grammar;
        this.opts = opts;
        this.model = new Model(opts,codeModel, NameConverter.smart,opts.classNameAllocator,null);

        datatypes.put("",DatatypeLib.BUILTIN);
        datatypes.put(WellKnownNamespaces.XML_SCHEMA_DATATYPES,DatatypeLib.XMLSCHEMA);

        // find all defines
        DefineFinder deff = new DefineFinder();
        grammar.accept(deff);
        this.defs = deff.defs;

        if(opts.defaultPackage2!=null)
            pkg = codeModel._package(opts.defaultPackage2);
        else
        if(opts.defaultPackage!=null)
            pkg = codeModel._package(opts.defaultPackage);
        else
            pkg = codeModel.rootPackage();
    }

    private void compile() {
        // decide which patterns to map to classes
        promoteElementDefsToClasses();
        promoteTypeSafeEnums();
        // TODO: promote patterns with <jaxb:class> to classes
        // TODO: promote 'type' patterns to classes
        promoteTypePatternsToClasses();

        for (Map.Entry<CClassInfo,DPattern> e : bindQueue.entrySet())
            bindContentModel(e.getKey(),e.getValue());
    }

    private void bindContentModel(CClassInfo clazz, DPattern pattern) {
        // first we decide which patterns in it map to properties
        // then we process each of them by using RawTypeSetBuilder.
        // much like DefaultParticleBinder in XSD
        pattern.accept(new ContentModelBinder(this,clazz));
    }

    private void promoteTypeSafeEnums() {
        // we'll be trying a lot of choices,
        // and most of them will not be type-safe enum.
        // using the same list improves the memory efficiency.
        List<CEnumConstant> members = new ArrayList<CEnumConstant>();

        OUTER:
        for( DDefine def : defs ) {
            DPattern p = def.getPattern();
            if (p instanceof DChoicePattern) {
                DChoicePattern cp = (DChoicePattern) p;

                members.clear();

                // check if the choice consists of all value patterns
                // and that they are of the same datatype
                DValuePattern vp = null;

                for( DPattern child : cp ) {
                    if(child instanceof DValuePattern) {
                        DValuePattern c = (DValuePattern) child;
                        if(vp==null)
                            vp=c;
                        else {
                            if(!vp.getDatatypeLibrary().equals(c.getDatatypeLibrary())
                            || !vp.getType().equals(c.getType()) )
                                continue OUTER; // different type name
                        }

                        members.add(new CEnumConstant(
                            model.getNameConverter().toConstantName(c.getValue()),
                            null, c.getValue(), null, null/*TODO*/, c.getLocation()
                        ));
                    } else
                        continue OUTER; // not a value
                }

                if(members.isEmpty())
                    continue;   // empty choice

                CNonElement base = CBuiltinLeafInfo.STRING;

                DatatypeLib lib = datatypes.get(vp.getNs());
                if(lib!=null) {
                    TypeUse use = lib.get(vp.getType());
                    if(use instanceof CNonElement)
                        base = (CNonElement)use;
                }

                CEnumLeafInfo xducer = new CEnumLeafInfo(model, null,
                        new CClassInfoParent.Package(pkg), def.getName(), base,
                        new ArrayList<CEnumConstant>(members),
                        null, null/*TODO*/, cp.getLocation());

                classes.put(cp,new CTypeInfo[]{xducer});
            }
        }
    }


    private void promoteElementDefsToClasses() {
        // look for elements among named patterns
        for( DDefine def : defs ) {
            DPattern p = def.getPattern();
            if (p instanceof DElementPattern) {
                DElementPattern ep = (DElementPattern) p;

                mapToClass(ep);
            }
        }

        // also look for root elements
        grammar.accept(new DPatternWalker() {
            public Void onRef(DRefPattern p) {
                return null;    // stop recursion
            }

            public Void onElement(DElementPattern p) {
                mapToClass(p);
                return null;
            }
        });
    }

    private void mapToClass(DElementPattern p) {
        NameClass nc = p.getName();
        if(nc.isOpen())
            return;   // infinite name. can't map to a class.

        Set<QName> names = nc.listNames();

        CClassInfo[] types = new CClassInfo[names.size()];
        int i=0;
        for( QName n : names ) {
            // TODO: read class names from customization
            String name = model.getNameConverter().toClassName(n.getLocalPart());

            bindQueue.put(
                types[i++] = new CClassInfo(model,pkg,name,p.getLocation(),null,n,null,null/*TODO*/),
                p.getChild() );
        }

        classes.put(p,types);
    }

    /**
     * Looks for named patterns that are not bound to classes so far,
     * but that can be bound to classes.
     */
    private void promoteTypePatternsToClasses() {

//        for( DDefine def : defs ) {
//        ;
//
//        def.getPattern().accept(new InheritanceChecker());
//        }
    }
}
