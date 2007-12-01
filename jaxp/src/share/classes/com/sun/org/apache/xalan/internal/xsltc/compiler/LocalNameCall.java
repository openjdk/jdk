/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 2001-2004 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * $Id: LocalNameCall.java,v 1.2.4.1 2005/09/01 16:00:21 pvedula Exp $
 */

package com.sun.org.apache.xalan.internal.xsltc.compiler;

import java.util.Vector;

import com.sun.org.apache.bcel.internal.generic.ConstantPoolGen;
import com.sun.org.apache.bcel.internal.generic.INVOKEINTERFACE;
import com.sun.org.apache.bcel.internal.generic.INVOKESTATIC;
import com.sun.org.apache.bcel.internal.generic.InstructionList;
import com.sun.org.apache.xalan.internal.xsltc.compiler.util.ClassGenerator;
import com.sun.org.apache.xalan.internal.xsltc.compiler.util.MethodGenerator;

/**
 * @author Morten Jorgensen
 */
final class LocalNameCall extends NameBase {

    /**
     * Handles calls with no parameter (current node is implicit parameter).
     */
    public LocalNameCall(QName fname) {
        super(fname);
    }

    /**
     * Handles calls with one parameter (either node or node-set).
     */
    public LocalNameCall(QName fname, Vector arguments) {
        super(fname, arguments);
    }

    /**
     * This method is called when the constructor is compiled in
     * Stylesheet.compileConstructor() and not as the syntax tree is traversed.
     */
    public void translate(ClassGenerator classGen,
                          MethodGenerator methodGen) {
        final ConstantPoolGen cpg = classGen.getConstantPool();
        final InstructionList il = methodGen.getInstructionList();

        // Returns the name of a node in the DOM
        final int getNodeName = cpg.addInterfaceMethodref(DOM_INTF,
                                                          "getNodeName",
                                                          "(I)"+STRING_SIG);

        final int getLocalName = cpg.addMethodref(BASIS_LIBRARY_CLASS,
                                                  "getLocalName",
                                                  "(Ljava/lang/String;)"+
                                                  "Ljava/lang/String;");
        super.translate(classGen, methodGen);
        il.append(new INVOKEINTERFACE(getNodeName, 2));
        il.append(new INVOKESTATIC(getLocalName));
    }
}
