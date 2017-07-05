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
 * $Id: MethodGenerator.java,v 1.2.4.1 2005/09/05 11:16:47 pvedula Exp $
 */

package com.sun.org.apache.xalan.internal.xsltc.compiler.util;

import java.util.Hashtable;

import com.sun.org.apache.bcel.internal.generic.ALOAD;
import com.sun.org.apache.bcel.internal.generic.ASTORE;
import com.sun.org.apache.bcel.internal.generic.ConstantPoolGen;
import com.sun.org.apache.bcel.internal.generic.ICONST;
import com.sun.org.apache.bcel.internal.generic.ILOAD;
import com.sun.org.apache.bcel.internal.generic.INVOKEINTERFACE;
import com.sun.org.apache.bcel.internal.generic.ISTORE;
import com.sun.org.apache.bcel.internal.generic.Instruction;
import com.sun.org.apache.bcel.internal.generic.InstructionHandle;
import com.sun.org.apache.bcel.internal.generic.InstructionList;
import com.sun.org.apache.bcel.internal.generic.LocalVariableGen;
import com.sun.org.apache.bcel.internal.generic.MethodGen;
import com.sun.org.apache.bcel.internal.generic.Type;
import com.sun.org.apache.xalan.internal.xsltc.compiler.Pattern;

/**
 * @author Jacek Ambroziak
 * @author Santiago Pericas-Geertsen
 */
public class MethodGenerator extends MethodGen
    implements com.sun.org.apache.xalan.internal.xsltc.compiler.Constants {
    protected static final int INVALID_INDEX   = -1;

    private static final String START_ELEMENT_SIG
        = "(" + STRING_SIG + ")V";
    private static final String END_ELEMENT_SIG
        = START_ELEMENT_SIG;

    private InstructionList _mapTypeSub;

    private static final int DOM_INDEX       = 1;
    private static final int ITERATOR_INDEX  = 2;
    private static final int HANDLER_INDEX   = 3;

    private Instruction       _iloadCurrent;
    private Instruction       _istoreCurrent;
    private final Instruction _astoreHandler;
    private final Instruction _aloadHandler;
    private final Instruction _astoreIterator;
    private final Instruction _aloadIterator;
    private final Instruction _aloadDom;
    private final Instruction _astoreDom;

    private final Instruction _startElement;
    private final Instruction _endElement;
    private final Instruction _startDocument;
    private final Instruction _endDocument;
    private final Instruction _attribute;
    private final Instruction _uniqueAttribute;
    private final Instruction _namespace;

    private final Instruction _setStartNode;
    private final Instruction _reset;
    private final Instruction _nextNode;

    private SlotAllocator _slotAllocator;
    private boolean _allocatorInit = false;
        /**
                 * A mapping between patterns and instruction lists used by
                 * test sequences to avoid compiling the same pattern multiple
                 * times. Note that patterns whose kernels are "*", "node()"
                 * and "@*" can between shared by test sequences.
                 */
        private Hashtable _preCompiled = new Hashtable();


    public MethodGenerator(int access_flags, Type return_type,
                           Type[] arg_types, String[] arg_names,
                           String method_name, String class_name,
                           InstructionList il, ConstantPoolGen cpg) {
        super(access_flags, return_type, arg_types, arg_names, method_name,
              class_name, il, cpg);

        _astoreHandler  = new ASTORE(HANDLER_INDEX);
        _aloadHandler   = new ALOAD(HANDLER_INDEX);
        _astoreIterator = new ASTORE(ITERATOR_INDEX);
        _aloadIterator  = new ALOAD(ITERATOR_INDEX);
        _aloadDom       = new ALOAD(DOM_INDEX);
        _astoreDom      = new ASTORE(DOM_INDEX);

        final int startElement =
            cpg.addInterfaceMethodref(TRANSLET_OUTPUT_INTERFACE,
                                      "startElement",
                                      START_ELEMENT_SIG);
        _startElement = new INVOKEINTERFACE(startElement, 2);

        final int endElement =
            cpg.addInterfaceMethodref(TRANSLET_OUTPUT_INTERFACE,
                                      "endElement",
                                      END_ELEMENT_SIG);
        _endElement = new INVOKEINTERFACE(endElement, 2);

        final int attribute =
            cpg.addInterfaceMethodref(TRANSLET_OUTPUT_INTERFACE,
                                      "addAttribute",
                                      "("
                                      + STRING_SIG
                                      + STRING_SIG
                                      + ")V");
        _attribute = new INVOKEINTERFACE(attribute, 3);

        final int uniqueAttribute =
            cpg.addInterfaceMethodref(TRANSLET_OUTPUT_INTERFACE,
                                      "addUniqueAttribute",
                                      "("
                                      + STRING_SIG
                                      + STRING_SIG
                                      + "I)V");
        _uniqueAttribute = new INVOKEINTERFACE(uniqueAttribute, 4);

        final int namespace =
            cpg.addInterfaceMethodref(TRANSLET_OUTPUT_INTERFACE,
                                      "namespaceAfterStartElement",
                                      "("
                                      + STRING_SIG
                                      + STRING_SIG
                                      + ")V");
        _namespace = new INVOKEINTERFACE(namespace, 3);

        int index = cpg.addInterfaceMethodref(TRANSLET_OUTPUT_INTERFACE,
                                              "startDocument",
                                              "()V");
        _startDocument = new INVOKEINTERFACE(index, 1);

        index = cpg.addInterfaceMethodref(TRANSLET_OUTPUT_INTERFACE,
                                          "endDocument",
                                          "()V");
        _endDocument = new INVOKEINTERFACE(index, 1);


        index = cpg.addInterfaceMethodref(NODE_ITERATOR,
                                          SET_START_NODE,
                                          SET_START_NODE_SIG);
        _setStartNode = new INVOKEINTERFACE(index, 2);

        index = cpg.addInterfaceMethodref(NODE_ITERATOR,
                                          "reset", "()"+NODE_ITERATOR_SIG);
        _reset = new INVOKEINTERFACE(index, 1);

        index = cpg.addInterfaceMethodref(NODE_ITERATOR, NEXT, NEXT_SIG);
        _nextNode = new INVOKEINTERFACE(index, 1);

        _slotAllocator = new SlotAllocator();
        _slotAllocator.initialize(getLocalVariables());
        _allocatorInit = true;
    }

    /**
     * Allocates a local variable. If the slot allocator has already been
     * initialized, then call addLocalVariable2() so that the new variable
     * is known to the allocator. Failing to do this may cause the allocator
     * to return a slot that is already in use.
     */
    public LocalVariableGen addLocalVariable(String name, Type type,
                                             InstructionHandle start,
                                             InstructionHandle end)
    {
        return (_allocatorInit) ? addLocalVariable2(name, type, start)
            : super.addLocalVariable(name, type, start, end);
    }

    public LocalVariableGen addLocalVariable2(String name, Type type,
                                              InstructionHandle start)
    {
        return super.addLocalVariable(name, type,
                                      _slotAllocator.allocateSlot(type),
                                      start, null);
    }

    public void removeLocalVariable(LocalVariableGen lvg) {
        _slotAllocator.releaseSlot(lvg);
        super.removeLocalVariable(lvg);
    }

    public Instruction loadDOM() {
        return _aloadDom;
    }

    public Instruction storeDOM() {
        return _astoreDom;
    }

    public Instruction storeHandler() {
        return _astoreHandler;
    }

    public Instruction loadHandler() {
        return _aloadHandler;
    }

    public Instruction storeIterator() {
        return _astoreIterator;
    }

    public Instruction loadIterator() {
        return _aloadIterator;
    }

    public final Instruction setStartNode() {
        return _setStartNode;
    }

    public final Instruction reset() {
        return _reset;
    }

    public final Instruction nextNode() {
        return _nextNode;
    }

    public final Instruction startElement() {
        return _startElement;
    }

    public final Instruction endElement() {
        return _endElement;
    }

    public final Instruction startDocument() {
        return _startDocument;
    }

    public final Instruction endDocument() {
        return _endDocument;
    }

    public final Instruction attribute() {
        return _attribute;
    }

    public final Instruction uniqueAttribute() {
        return _uniqueAttribute;
    }

    public final Instruction namespace() {
        return _namespace;
    }

    public Instruction loadCurrentNode() {
        if (_iloadCurrent == null) {
            int idx = getLocalIndex("current");
            if (idx > 0)
                _iloadCurrent = new ILOAD(idx);
            else
                _iloadCurrent = new ICONST(0);
        }
        return _iloadCurrent;
    }

    public Instruction storeCurrentNode() {
        return _istoreCurrent != null
            ? _istoreCurrent
            : (_istoreCurrent = new ISTORE(getLocalIndex("current")));
    }

    /** by default context node is the same as current node. MK437 */
    public Instruction loadContextNode() {
        return loadCurrentNode();
    }

    public Instruction storeContextNode() {
        return storeCurrentNode();
    }

    public int getLocalIndex(String name) {
        return getLocalVariable(name).getIndex();
    }

    public LocalVariableGen getLocalVariable(String name) {
        final LocalVariableGen[] vars = getLocalVariables();
        for (int i = 0; i < vars.length; i++)
            if (vars[i].getName().equals(name))
                return vars[i];
        return null;
    }

    public void setMaxLocals() {

        // Get the current number of local variable slots
        int maxLocals = super.getMaxLocals();
        int prevLocals = maxLocals;

        // Get numer of actual variables
        final LocalVariableGen[] localVars = super.getLocalVariables();
        if (localVars != null) {
            if (localVars.length > maxLocals)
                maxLocals = localVars.length;
        }

        // We want at least 5 local variable slots (for parameters)
        if (maxLocals < 5) maxLocals = 5;

        super.setMaxLocals(maxLocals);
    }

        /**
         * Add a pre-compiled pattern to this mode.
         */
        public void addInstructionList(Pattern pattern,
        InstructionList ilist)
        {
        _preCompiled.put(pattern, ilist);
        }

        /**
         * Get the instruction list for a pre-compiled pattern. Used by
         * test sequences to avoid compiling patterns more than once.
         */
        public InstructionList getInstructionList(Pattern pattern) {
        return (InstructionList) _preCompiled.get(pattern);
        }

}
