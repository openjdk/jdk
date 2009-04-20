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

import com.sun.codemodel.internal.ClassType;
import com.sun.codemodel.internal.JAnnotationUse;
import com.sun.codemodel.internal.JBlock;
import com.sun.codemodel.internal.JClass;
import com.sun.codemodel.internal.JClassAlreadyExistsException;
import com.sun.codemodel.internal.JDefinedClass;
import com.sun.codemodel.internal.JDocComment;
import com.sun.codemodel.internal.JExpr;
import com.sun.codemodel.internal.JFieldRef;
import com.sun.codemodel.internal.JFieldVar;
import com.sun.codemodel.internal.JMethod;
import com.sun.codemodel.internal.JMod;
import com.sun.codemodel.internal.JType;
import com.sun.codemodel.internal.JVar;
import com.sun.tools.internal.ws.processor.model.Fault;
import com.sun.tools.internal.ws.processor.model.Model;
import com.sun.tools.internal.ws.wscompile.ErrorReceiver;
import com.sun.tools.internal.ws.wscompile.WsimportOptions;

import javax.xml.ws.WebFault;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author WS Development Team
 */
public class CustomExceptionGenerator extends GeneratorBase {
    private Map<String, JClass> faults = new HashMap<String, JClass>();

    public static void generate(Model model,
        WsimportOptions options,
        ErrorReceiver receiver){
        CustomExceptionGenerator exceptionGen = new CustomExceptionGenerator(model, options, receiver);
        exceptionGen.doGeneration();
    }
    private CustomExceptionGenerator(
        Model model,
        WsimportOptions options,
        ErrorReceiver receiver) {
        super(model, options, receiver);
    }

    public GeneratorBase getGenerator(Model model, WsimportOptions options, ErrorReceiver receiver) {
        return new CustomExceptionGenerator(model, options, receiver);
    }

    @Override
    public void visit(Fault fault) throws Exception {
        if (isRegistered(fault))
            return;
        registerFault(fault);
    }

    private boolean isRegistered(Fault fault) {
        if(faults.keySet().contains(fault.getJavaException().getName())){
            fault.setExceptionClass(faults.get(fault.getJavaException().getName()));
            return true;
        }
        return false;
    }

    private void registerFault(Fault fault) {
         try {
            write(fault);
            faults.put(fault.getJavaException().getName(), fault.getExceptionClass());
        } catch (JClassAlreadyExistsException e) {
            throw new GeneratorException("generator.nestedGeneratorError",e);
        }
    }

    private void write(Fault fault) throws JClassAlreadyExistsException {
        String className = Names.customExceptionClassName(fault);

        JDefinedClass cls = cm._class(className, ClassType.CLASS);
        JDocComment comment = cls.javadoc();
        if(fault.getJavaDoc() != null){
            comment.add(fault.getJavaDoc());
            comment.add("\n\n");
        }

        for (String doc : getJAXWSClassComment()) {
            comment.add(doc);
        }

        cls._extends(java.lang.Exception.class);

        //@WebFault
        JAnnotationUse faultAnn = cls.annotate(WebFault.class);
        faultAnn.param("name", fault.getBlock().getName().getLocalPart());
        faultAnn.param("targetNamespace", fault.getBlock().getName().getNamespaceURI());

        JType faultBean = fault.getBlock().getType().getJavaType().getType().getType();

        //faultInfo filed
        JFieldVar fi = cls.field(JMod.PRIVATE, faultBean, "faultInfo");

        //add jaxb annotations
        fault.getBlock().getType().getJavaType().getType().annotate(fi);

        fi.javadoc().add("Java type that goes as soapenv:Fault detail element.");
        JFieldRef fr = JExpr.ref(JExpr._this(), fi);

        //Constructor
        JMethod constrc1 = cls.constructor(JMod.PUBLIC);
        JVar var1 = constrc1.param(String.class, "message");
        JVar var2 = constrc1.param(faultBean, "faultInfo");
        constrc1.javadoc().addParam(var1);
        constrc1.javadoc().addParam(var2);
        JBlock cb1 = constrc1.body();
        cb1.invoke("super").arg(var1);

        cb1.assign(fr, var2);

        //constructor with Throwable
        JMethod constrc2 = cls.constructor(JMod.PUBLIC);
        var1 = constrc2.param(String.class, "message");
        var2 = constrc2.param(faultBean, "faultInfo");
        JVar var3 = constrc2.param(Throwable.class, "cause");
        constrc2.javadoc().addParam(var1);
        constrc2.javadoc().addParam(var2);
        constrc2.javadoc().addParam(var3);
        JBlock cb2 = constrc2.body();
        cb2.invoke("super").arg(var1).arg(var3);
        cb2.assign(fr, var2);


        //getFaultInfo() method
        JMethod fim = cls.method(JMod.PUBLIC, faultBean, "getFaultInfo");
        fim.javadoc().addReturn().add("returns fault bean: "+faultBean.fullName());
        JBlock fib = fim.body();
        fib._return(fi);
        fault.setExceptionClass(cls);

    }
}
