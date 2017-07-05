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
package com.sun.tools.internal.xjc.reader.xmlschema.ct;

import com.sun.tools.internal.xjc.ErrorReceiver;
import com.sun.tools.internal.xjc.reader.Ring;
import com.sun.tools.internal.xjc.reader.xmlschema.BGMBuilder;
import com.sun.tools.internal.xjc.reader.xmlschema.BindGreen;
import com.sun.tools.internal.xjc.reader.xmlschema.ClassSelector;
import com.sun.tools.internal.xjc.reader.xmlschema.SimpleTypeBuilder;
import com.sun.xml.internal.xsom.XSComplexType;
import com.sun.xml.internal.xsom.XSSchemaSet;

/**
 * Builds a field expression from a complex type.
 *
 * Depending on a "kind" of complex type, the binding is
 * quite different. For example, how a complex type is bound
 * when it is extended from another complex type is very
 * different from how it's bound when it has, say, mixed content model.
 *
 * Each different algorithm of binding a complex type is implemented
 * as an implementation of this interface.
 *
 * @author
 *  Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
abstract class CTBuilder {
    /**
     * Returns true if this owner can handle the given complex type.
     */
    abstract boolean isApplicable(XSComplexType ct);

    /**
     * Binds the given complex type. This method will be called
     * only when the <code>isApplicable</code> method returns true.
     */
    abstract void build(XSComplexType ct);

    protected final ComplexTypeFieldBuilder builder = Ring.get(ComplexTypeFieldBuilder.class);
    protected final ClassSelector selector = Ring.get(ClassSelector.class);
    protected final SimpleTypeBuilder simpleTypeBuilder = Ring.get(SimpleTypeBuilder.class);
    protected final ErrorReceiver errorReceiver = Ring.get(ErrorReceiver.class);
    protected final BindGreen green = Ring.get(BindGreen.class);
    protected final XSSchemaSet schemas = Ring.get(XSSchemaSet.class);
    protected final BGMBuilder bgmBuilder = Ring.get(BGMBuilder.class);
}
