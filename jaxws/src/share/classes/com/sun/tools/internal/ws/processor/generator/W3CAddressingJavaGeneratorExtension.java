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

package com.sun.tools.internal.ws.processor.generator;

import com.sun.codemodel.internal.JAnnotationArrayMember;
import com.sun.codemodel.internal.JAnnotationUse;
import com.sun.codemodel.internal.JClass;
import com.sun.codemodel.internal.JMethod;
import com.sun.tools.internal.ws.api.TJavaGeneratorExtension;
import com.sun.tools.internal.ws.api.wsdl.TWSDLOperation;
import com.sun.tools.internal.ws.wsdl.document.Fault;
import com.sun.tools.internal.ws.wsdl.document.Operation;

import javax.xml.ws.Action;
import javax.xml.ws.FaultAction;
import java.util.Map;

/**
 * @author Arun Gupta
 */
public class W3CAddressingJavaGeneratorExtension extends TJavaGeneratorExtension {
    @Override
    public void writeMethodAnnotations(TWSDLOperation two, JMethod jMethod) {
        JAnnotationUse actionAnn = null;

        if (!(two instanceof Operation))
            return;

        Operation o = ((Operation)two);

        // explicit input action
        if (o.getInput().getAction() != null && !o.getInput().getAction().equals("")) {
            // explicitly specified
            actionAnn = jMethod.annotate(Action.class);
            actionAnn.param("input", o.getInput().getAction());
        }

        // explicit output action
        if (o.getOutput() != null && o.getOutput().getAction() != null && !o.getOutput().getAction().equals("")) {
            // explicitly specified
            if (actionAnn == null)
                actionAnn = jMethod.annotate(Action.class);

            actionAnn.param("output", o.getOutput().getAction());
        }

        // explicit fault action
        if (o.getFaults() != null && o.getFaults().size() > 0) {
            Map<String, JClass> map = o.getFaults();
            JAnnotationArrayMember jam = null;

            for (Fault f : o.faults()) {
                if (f.getAction() == null)
                    continue;

                if (f.getAction().equals(""))
                    continue;

                if (actionAnn == null) {
                    actionAnn = jMethod.annotate(Action.class);
                }
                if (jam == null) {
                    jam = actionAnn.paramArray("fault");
                }
                final JAnnotationUse faAnn = jam.annotate(FaultAction.class);
                faAnn.param("className", map.get(f.getName()));
                faAnn.param("value", f.getAction());
            }
        }
    }
}
