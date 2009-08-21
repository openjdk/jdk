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

import com.sun.tools.internal.xjc.outline.FieldOutline;
import com.sun.tools.internal.xjc.outline.ClassOutline;
import com.sun.tools.internal.xjc.outline.FieldAccessor;
import com.sun.tools.internal.xjc.outline.Aspect;
import com.sun.tools.internal.xjc.outline.Outline;
import com.sun.tools.internal.xjc.model.CPropertyInfo;
import com.sun.tools.internal.xjc.model.CElementInfo;
import com.sun.tools.internal.xjc.model.CReferencePropertyInfo;
import com.sun.codemodel.internal.JType;
import com.sun.codemodel.internal.JExpression;
import com.sun.codemodel.internal.JBlock;
import com.sun.codemodel.internal.JVar;
import com.sun.codemodel.internal.JConditional;
import com.sun.codemodel.internal.JExpr;
import com.sun.codemodel.internal.JCodeModel;
import com.sun.codemodel.internal.JInvocation;

/**
 * {@link FieldOutline} that wraps another {@link FieldOutline}
 * and allows JAX-WS to access values without using about
 * {@link JAXBElement}.
 *
 * <p>
 * That means if a value is requested, we unwrap JAXBElement
 * and give it to them. If a value is set, we wrap that into
 * JAXBElement, etc.
 *
 * <p>
 * This can be used only with {@link CReferencePropertyInfo}
 * (or else it won't be {@link JAXBElement),
 * with one {@link CElementInfo} (or else we can't infer the tag name.)
 *
 * @author Kohsuke Kawaguchi
 */
abstract class ElementAdapter implements FieldOutline {
    protected final FieldOutline core;

    /**
     * The only one {@link CElementInfo} that can be in the property.
     */
    protected final CElementInfo ei;

    public ElementAdapter(FieldOutline core, CElementInfo ei) {
        this.core = core;
        this.ei = ei;
    }

    public ClassOutline parent() {
        return core.parent();
    }

    public CPropertyInfo getPropertyInfo() {
        return core.getPropertyInfo();
    }

    protected final Outline outline() {
        return core.parent().parent();
    }

    protected final JCodeModel codeModel() {
        return outline().getCodeModel();
    }

    protected abstract class FieldAccessorImpl implements FieldAccessor {
        final FieldAccessor acc;

        public FieldAccessorImpl(JExpression target) {
            acc = core.create(target);
        }

        public void unsetValues(JBlock body) {
            acc.unsetValues(body);
        }

        public JExpression hasSetValue() {
            return acc.hasSetValue();
        }

        public FieldOutline owner() {
            return ElementAdapter.this;
        }

        public CPropertyInfo getPropertyInfo() {
            return core.getPropertyInfo();
        }

        /**
         * Wraps a type value into a {@link JAXBElement}.
         */
        protected final JInvocation createJAXBElement(JExpression $var) {
            JCodeModel cm = codeModel();

            return JExpr._new(cm.ref(JAXBElement.class))
                .arg(JExpr._new(cm.ref(QName.class))
                    .arg(ei.getElementName().getNamespaceURI())
                    .arg(ei.getElementName().getLocalPart()))
                .arg(getRawType().boxify().erasure().dotclass())
                .arg($var);
        }
    }
}
