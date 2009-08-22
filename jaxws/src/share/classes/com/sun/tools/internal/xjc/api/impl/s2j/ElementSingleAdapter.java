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

package com.sun.tools.internal.xjc.api.impl.s2j;

import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;

import com.sun.codemodel.internal.JType;
import com.sun.codemodel.internal.JBlock;
import com.sun.codemodel.internal.JVar;
import com.sun.codemodel.internal.JConditional;
import com.sun.codemodel.internal.JExpr;
import com.sun.codemodel.internal.JExpression;
import com.sun.codemodel.internal.JCodeModel;
import com.sun.codemodel.internal.JInvocation;
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
