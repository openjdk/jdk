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
 * $Id: FormatNumberCall.java,v 1.2.4.1 2005/09/01 15:26:46 pvedula Exp $
 */

package com.sun.org.apache.xalan.internal.xsltc.compiler;

import java.util.Vector;

import com.sun.org.apache.bcel.internal.generic.ConstantPoolGen;
import com.sun.org.apache.bcel.internal.generic.INVOKESTATIC;
import com.sun.org.apache.bcel.internal.generic.INVOKEVIRTUAL;
import com.sun.org.apache.bcel.internal.generic.InstructionList;
import com.sun.org.apache.bcel.internal.generic.PUSH;
import com.sun.org.apache.xalan.internal.xsltc.compiler.util.ClassGenerator;
import com.sun.org.apache.xalan.internal.xsltc.compiler.util.MethodGenerator;
import com.sun.org.apache.xalan.internal.xsltc.compiler.util.RealType;
import com.sun.org.apache.xalan.internal.xsltc.compiler.util.StringType;
import com.sun.org.apache.xalan.internal.xsltc.compiler.util.Type;
import com.sun.org.apache.xalan.internal.xsltc.compiler.util.TypeCheckError;

/**
 * @author Jacek Ambroziak
 * @author Santiago Pericas-Geertsen
 * @author Morten Jorgensen
 */
final class FormatNumberCall extends FunctionCall {
    private Expression _value;
    private Expression _format;
    private Expression _name;
    private QName      _resolvedQName = null;

    public FormatNumberCall(QName fname, Vector arguments) {
        super(fname, arguments);
        _value = argument(0);
        _format = argument(1);
        _name = argumentCount() == 3 ? argument(2) : null;
    }

    public Type typeCheck(SymbolTable stable) throws TypeCheckError {

        // Inform stylesheet to instantiate a DecimalFormat object
        getStylesheet().numberFormattingUsed();

        final Type tvalue = _value.typeCheck(stable);
        if (tvalue instanceof RealType == false) {
            _value = new CastExpr(_value, Type.Real);
        }
        final Type tformat = _format.typeCheck(stable);
        if (tformat instanceof StringType == false) {
            _format = new CastExpr(_format, Type.String);
        }
        if (argumentCount() == 3) {
            final Type tname = _name.typeCheck(stable);

            if (_name instanceof LiteralExpr) {
                final LiteralExpr literal = (LiteralExpr) _name;
                _resolvedQName =
                    getParser().getQNameIgnoreDefaultNs(literal.getValue());
            }
            else if (tname instanceof StringType == false) {
                _name = new CastExpr(_name, Type.String);
            }
        }
        return _type = Type.String;
    }

    public void translate(ClassGenerator classGen, MethodGenerator methodGen) {
        final ConstantPoolGen cpg = classGen.getConstantPool();
        final InstructionList il = methodGen.getInstructionList();

        _value.translate(classGen, methodGen);
        _format.translate(classGen, methodGen);

        final int fn3arg = cpg.addMethodref(BASIS_LIBRARY_CLASS,
                                            "formatNumber",
                                            "(DLjava/lang/String;"+
                                            "Ljava/text/DecimalFormat;)"+
                                            "Ljava/lang/String;");
        final int get = cpg.addMethodref(TRANSLET_CLASS,
                                         "getDecimalFormat",
                                         "(Ljava/lang/String;)"+
                                         "Ljava/text/DecimalFormat;");

        il.append(classGen.loadTranslet());
        if (_name == null) {
            il.append(new PUSH(cpg, EMPTYSTRING));
        }
        else if (_resolvedQName != null) {
            il.append(new PUSH(cpg, _resolvedQName.toString()));
        }
        else {
            _name.translate(classGen, methodGen);
        }
        il.append(new INVOKEVIRTUAL(get));
        il.append(new INVOKESTATIC(fn3arg));
    }
}
