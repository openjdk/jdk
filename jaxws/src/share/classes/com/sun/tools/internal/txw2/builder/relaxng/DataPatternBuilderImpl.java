/*
 * Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.internal.txw2.builder.relaxng;

import com.sun.codemodel.JType;
import com.sun.tools.internal.txw2.model.Data;
import com.sun.tools.internal.txw2.model.Leaf;
import org.kohsuke.rngom.ast.builder.BuildException;
import org.kohsuke.rngom.ast.builder.DataPatternBuilder;
import org.kohsuke.rngom.ast.om.ParsedElementAnnotation;
import org.kohsuke.rngom.ast.util.LocatorImpl;
import org.kohsuke.rngom.parse.Context;

/**
 * @author Kohsuke Kawaguchi
 */
final class DataPatternBuilderImpl implements DataPatternBuilder<Leaf,ParsedElementAnnotation,LocatorImpl,AnnotationsImpl,CommentListImpl> {
    final JType type;

    public DataPatternBuilderImpl(JType type) {
        this.type = type;
    }

    public Leaf makePattern(LocatorImpl locator, AnnotationsImpl annotations) throws BuildException {
        return new Data(locator,type);
    }

    public void addParam(String name, String value, Context context, String ns, LocatorImpl locator, AnnotationsImpl annotations) throws BuildException {
    }

    public void annotation(ParsedElementAnnotation parsedElementAnnotation) {
    }

    public Leaf makePattern(Leaf except, LocatorImpl locator, AnnotationsImpl annotations) throws BuildException {
        return makePattern(locator,annotations);
    }
}
