/*
 * Copyright (c) 2005, 2010, Oracle and/or its affiliates. All rights reserved.
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
/*
 * Copyright (C) 2004-2012
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.sun.xml.internal.rngom.digested;

import com.sun.xml.internal.rngom.ast.builder.Annotations;
import com.sun.xml.internal.rngom.ast.builder.BuildException;
import com.sun.xml.internal.rngom.ast.builder.DataPatternBuilder;
import com.sun.xml.internal.rngom.ast.om.Location;
import com.sun.xml.internal.rngom.ast.om.ParsedElementAnnotation;
import com.sun.xml.internal.rngom.ast.om.ParsedPattern;
import com.sun.xml.internal.rngom.parse.Context;
import org.xml.sax.Locator;

/**
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */
final class DataPatternBuilderImpl implements DataPatternBuilder {

    private final DDataPattern p;

    public DataPatternBuilderImpl(String datatypeLibrary, String type, Location loc) {
        p = new DDataPattern();
        p.location = (Locator)loc;
        p.datatypeLibrary = datatypeLibrary;
        p.type = type;
    }

    public void addParam(String name, String value, Context context, String ns, Location loc, Annotations anno) throws BuildException {
        p.params.add(p.new Param(name,value,context.copy(),ns,loc,(Annotation)anno));
    }

    public void annotation(ParsedElementAnnotation ea) {
        // TODO
    }

    public ParsedPattern makePattern(Location loc, Annotations anno) throws BuildException {
        return makePattern(null,loc,anno);
    }

    public ParsedPattern makePattern(ParsedPattern except, Location loc, Annotations anno) throws BuildException {
        p.except = (DPattern)except;
        if (anno!=null) {
            p.annotation = ((Annotation)anno).getResult();
        }
        return p;
    }
}
