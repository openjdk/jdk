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
 * $Id: NodeCounterGenerator.java,v 1.2.4.1 2005/09/05 11:20:48 pvedula Exp $
 */

package com.sun.org.apache.xalan.internal.xsltc.compiler.util;

import com.sun.org.apache.bcel.internal.generic.ALOAD;
import com.sun.org.apache.bcel.internal.generic.Instruction;
import com.sun.org.apache.xalan.internal.xsltc.compiler.Stylesheet;

/**
 * This class implements auxiliary classes needed to compile
 * patterns in <tt>xsl:number</tt>. These classes inherit from
 * {Any,Single,Multiple}NodeCounter and override the
 * <tt>matchFrom</tt> and <tt>matchCount</tt> methods.
 * @author Jacek Ambroziak
 * @author Santiago Pericas-Geertsen
 */
public final class NodeCounterGenerator extends ClassGenerator {
    private Instruction _aloadTranslet;

    public NodeCounterGenerator(String className,
                                String superClassName,
                                String fileName,
                                int accessFlags,
                                String[] interfaces,
                                Stylesheet stylesheet) {
        super(className, superClassName, fileName,
              accessFlags, interfaces, stylesheet);
    }

    /**
     * Set the index of the register where "this" (the pointer to
     * the translet) is stored.
     */
    public void setTransletIndex(int index) {
        _aloadTranslet = new ALOAD(index);
    }

    /**
     * The index of the translet pointer within the execution of
     * matchFrom or matchCount.
     * Overridden from ClassGenerator.
     */
    public Instruction loadTranslet() {
        return _aloadTranslet;
    }

    /**
     * Returns <tt>true</tt> since this class is external to the
     * translet.
     */
    public boolean isExternal() {
        return true;
    }
}
