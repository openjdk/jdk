/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.xjc.api.impl.s2j;

import javax.xml.bind.JAXBElement;

import com.sun.codemodel.internal.JType;
import com.sun.codemodel.internal.JBlock;
import com.sun.codemodel.internal.JVar;
import com.sun.codemodel.internal.JConditional;
import com.sun.codemodel.internal.JExpr;
import com.sun.codemodel.internal.JExpression;
import com.sun.tools.internal.xjc.outline.Aspect;
import com.sun.tools.internal.xjc.outline.FieldOutline;
import com.sun.tools.internal.xjc.outline.FieldAccessor;
import com.sun.tools.internal.xjc.model.CElementInfo;

/**
 * {@link ElementAdapter} that works with a single {@link JAXBElement}.
 *
 * @author Kohsuke Kawaguchi
 */
final class ElementSingleAdapter extends ElementAdapter {
    public ElementSingleAdapter(FieldOutline core, CElementInfo ei) {
        super(core, ei);
    }

    public JType getRawType() {
        return ei.getContentInMemoryType().toType(outline(), Aspect.EXPOSED);
    }

    public FieldAccessor create(JExpression targetObject) {
        return new FieldAccessorImpl(targetObject);
    }

    final class FieldAccessorImpl extends ElementAdapter.FieldAccessorImpl {
        public FieldAccessorImpl(JExpression target) {
            super(target);
        }

        public void toRawValue(JBlock block, JVar $var) {
            // [RESULT]
            // if([core.hasSetValue])
            //   $var = [core.toRawValue].getValue();
            // else
            //   $var = null;

            JConditional cond = block._if(acc.hasSetValue());
            JVar $v = cond._then().decl(core.getRawType(), "v" + hashCode());// TODO: unique value control
            acc.toRawValue(cond._then(),$v);
            cond._then().assign($var,$v.invoke("getValue"));
            cond._else().assign($var, JExpr._null());
        }

        public void fromRawValue(JBlock block, String uniqueName, JExpression $var) {
            // [RESULT]
            // [core.fromRawValue](new JAXBElement(tagName, TYPE, $var));

            acc.fromRawValue(block,uniqueName, createJAXBElement($var));
        }
    }
}
