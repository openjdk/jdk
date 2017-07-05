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
package com.sun.xml.internal.rngom.nc;

import com.sun.xml.internal.rngom.ast.builder.Annotations;
import com.sun.xml.internal.rngom.ast.builder.BuildException;
import com.sun.xml.internal.rngom.ast.builder.CommentList;
import com.sun.xml.internal.rngom.ast.builder.NameClassBuilder;
import com.sun.xml.internal.rngom.ast.om.Location;
import com.sun.xml.internal.rngom.ast.om.ParsedElementAnnotation;

import java.util.List;


/**
 *
 * @author
 *      Kohsuke Kawaguchi (kk@kohsuke.org)
 */
public class NameClassBuilderImpl<
    E extends ParsedElementAnnotation,
    L extends Location,
    A extends Annotations<E,L,CL>,
    CL extends CommentList<L>> implements NameClassBuilder<NameClass,E,L,A,CL> {

    public NameClass makeChoice(List<NameClass> nameClasses, L loc, A anno) {
      NameClass result = nameClasses.get(0);
      for (int i = 1; i < nameClasses.size(); i++)
        result = new ChoiceNameClass(result, nameClasses.get(i));
      return result;
    }

    public NameClass makeName(String ns, String localName, String prefix, L loc, A anno) {
      return new SimpleNameClass(ns, localName);
    }

    public NameClass makeNsName(String ns, L loc, A anno) {
      return new NsNameClass(ns);
    }

    public NameClass makeNsName(String ns, NameClass except, L loc, A anno) {
      return new NsNameExceptNameClass(ns, except);
    }

    public NameClass makeAnyName(L loc, A anno) {
      return NameClass.ANY;
    }

    public NameClass makeAnyName(NameClass except, L loc, A anno) {
      return new AnyNameExceptNameClass(except);
    }

    public NameClass makeErrorNameClass() {
        return NameClass.NULL;
    }

    public NameClass annotate(NameClass nc, A anno) throws BuildException {
      return nc;
    }

    public NameClass annotateAfter(NameClass nc, E e) throws BuildException {
      return nc;
    }

    public NameClass commentAfter(NameClass nc, CL comments) throws BuildException {
      return nc;
    }

}
