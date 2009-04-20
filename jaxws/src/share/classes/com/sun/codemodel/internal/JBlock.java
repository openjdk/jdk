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

package com.sun.codemodel.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

/**
 * A block of Java code, which may contain statements and local declarations.
 *
 * <p>
 * {@link JBlock} contains a large number of factory methods that creates new
 * statements/declarations. Those newly created statements/declarations are
 * inserted into the {@link #pos() "current position"}. The position advances
 * one every time you add a new instruction.
 */
public final class JBlock implements JGenerable, JStatement {

    /**
     * Declarations and statements contained in this block.
     * Either {@link JStatement} or {@link JDeclaration}.
     */
    private final List<Object> content = new ArrayList<Object>();

    /**
     * Whether or not this block must be braced and indented
     */
    private boolean bracesRequired = true;
    private boolean indentRequired = true;

    /**
     * Current position.
     */
    private int pos;

    public JBlock() {
        this(true,true);
    }

    public JBlock(boolean bracesRequired, boolean indentRequired) {
        this.bracesRequired = bracesRequired;
        this.indentRequired = indentRequired;
    }

    /**
     * Returns a read-only view of {@link JStatement}s and {@link JDeclaration}
     * in this block.
     */
    public List<Object> getContents() {
        return Collections.unmodifiableList(content);
    }

    private <T> T insert( T statementOrDeclaration ) {
        content.add(pos,statementOrDeclaration);
        pos++;
        return statementOrDeclaration;
    }

    /**
     * Gets the current position to which new statements will be inserted.
     *
     * For example if the value is 0, newly created instructions will be
     * inserted at the very beginning of the block.
     *
     * @see #pos(int)
     */
    public int pos() {
        return pos;
    }

    /**
     * Sets the current position.
     *
     * @return
     *      the old value of the current position.
     * @throws IllegalArgumentException
     *      if the new position value is illegal.
     *
     * @see #pos()
     */
    public int pos(int newPos) {
        int r = pos;
        if(newPos>content.size() || newPos<0)
            throw new IllegalArgumentException();
        pos = newPos;

        return r;
    }


    /**
     * Adds a local variable declaration to this block
     *
     * @param type
     *        JType of the variable
     *
     * @param name
     *        Name of the variable
     *
     * @return Newly generated JVar
     */
    public JVar decl(JType type, String name) {
        return decl(JMod.NONE, type, name, null);
    }

    /**
     * Adds a local variable declaration to this block
     *
     * @param type
     *        JType of the variable
     *
     * @param name
     *        Name of the variable
     *
     * @param init
     *        Initialization expression for this variable.  May be null.
     *
     * @return Newly generated JVar
     */
    public JVar decl(JType type, String name, JExpression init) {
        return decl(JMod.NONE, type, name, init);
    }

    /**
     * Adds a local variable declaration to this block
     *
     * @param mods
     *        Modifiers for the variable
     *
     * @param type
     *        JType of the variable
     *
     * @param name
     *        Name of the variable
     *
     * @param init
     *        Initialization expression for this variable.  May be null.
     *
     * @return Newly generated JVar
     */
    public JVar decl(int mods, JType type, String name, JExpression init) {
        JVar v = new JVar(JMods.forVar(mods), type, name, init);
        insert(v);
        bracesRequired = true;
        indentRequired = true;
        return v;
    }

    /**
     * Creates an assignment statement and adds it to this block.
     *
     * @param lhs
     *        Assignable variable or field for left hand side of expression
     *
     * @param exp
     *        Right hand side expression
     */
    public JBlock assign(JAssignmentTarget lhs, JExpression exp) {
        insert(new JAssignment(lhs, exp));
        return this;
    }

    public JBlock assignPlus(JAssignmentTarget lhs, JExpression exp) {
        insert(new JAssignment(lhs, exp, "+"));
        return this;
    }

    /**
     * Creates an invocation statement and adds it to this block.
     *
     * @param expr
     *        JExpression evaluating to the class or object upon which
     *        the named method will be invoked
     *
     * @param method
     *        Name of method to invoke
     *
     * @return Newly generated JInvocation
     */
    public JInvocation invoke(JExpression expr, String method) {
        JInvocation i = new JInvocation(expr, method);
        insert(i);
        return i;
    }

    /**
     * Creates an invocation statement and adds it to this block.
     *
     * @param expr
     *        JExpression evaluating to the class or object upon which
     *        the method will be invoked
     *
     * @param method
     *        JMethod to invoke
     *
     * @return Newly generated JInvocation
     */
    public JInvocation invoke(JExpression expr, JMethod method) {
        return insert(new JInvocation(expr, method));
    }

    /**
     * Creates a static invocation statement.
     */
    public JInvocation staticInvoke(JClass type, String method) {
        return insert(new JInvocation(type, method));
    }

    /**
     * Creates an invocation statement and adds it to this block.
     *
     * @param method
     *        Name of method to invoke
     *
     * @return Newly generated JInvocation
     */
    public JInvocation invoke(String method) {
        return insert(new JInvocation((JExpression)null, method));
    }

    /**
     * Creates an invocation statement and adds it to this block.
     *
     * @param method
     *        JMethod to invoke
     *
     * @return Newly generated JInvocation
     */
    public JInvocation invoke(JMethod method) {
        return insert(new JInvocation((JExpression)null, method));
    }

    /**
     * Adds a statement to this block
     *
     * @param s
     *        JStatement to be added
     *
     * @return This block
     */
    public JBlock add(JStatement s) { // ## Needed?
        insert(s);
        return this;
    }

    /**
     * Create an If statement and add it to this block
     *
     * @param expr
     *        JExpression to be tested to determine branching
     *
     * @return Newly generated conditional statement
     */
    public JConditional _if(JExpression expr) {
        return insert(new JConditional(expr));
    }

    /**
     * Create a For statement and add it to this block
     *
     * @return Newly generated For statement
     */
    public JForLoop _for() {
        return insert(new JForLoop());
    }

    /**
     * Create a While statement and add it to this block
     *
     * @return Newly generated While statement
     */
    public JWhileLoop _while(JExpression test) {
        return insert(new JWhileLoop(test));
    }

    /**
     * Create a switch/case statement and add it to this block
     */
    public JSwitch _switch(JExpression test) {
        return insert(new JSwitch(test));
    }

    /**
     * Create a Do statement and add it to this block
     *
     * @return Newly generated Do statement
     */
    public JDoLoop _do(JExpression test) {
        return insert(new JDoLoop(test));
    }

    /**
     * Create a Try statement and add it to this block
     *
     * @return Newly generated Try statement
     */
    public JTryBlock _try() {
        return insert(new JTryBlock());
    }

    /**
     * Create a return statement and add it to this block
     */
    public void _return() {
        insert(new JReturn(null));
    }

    /**
     * Create a return statement and add it to this block
     */
    public void _return(JExpression exp) {
        insert(new JReturn(exp));
    }

    /**
     * Create a throw statement and add it to this block
     */
    public void _throw(JExpression exp) {
        insert(new JThrow(exp));
    }

    /**
     * Create a break statement and add it to this block
     */
    public void _break() {
        _break(null);
    }

    public void _break(JLabel label) {
        insert(new JBreak(label));
    }

    /**
     * Create a label, which can be referenced from
     * <code>continue</code> and <code>break</code> statements.
     */
    public JLabel label(String name) {
        JLabel l = new JLabel(name);
        insert(l);
        return l;
    }

    /**
     * Create a continue statement and add it to this block
     */
    public void _continue(JLabel label) {
        insert(new JContinue(label));
    }

    public void _continue() {
        _continue(null);
    }

    /**
     * Create a sub-block and add it to this block
     */
    public JBlock block() {
        JBlock b = new JBlock();
        b.bracesRequired = false;
        b.indentRequired = false;
        return insert(b);
    }

    /**
     * Creates a "literal" statement directly.
     *
     * <p>
     * Specified string is printed as-is.
     * This is useful as a short-cut.
     *
     * <p>
     * For example, you can invoke this method as:
     * <code>directStatement("a=b+c;")</code>.
     */
    public JStatement directStatement(final String source) {
        JStatement s = new JStatement() {
            public void state(JFormatter f) {
                f.p(source).nl();
            }
        };
        add(s);
        return s;
    }

    public void generate(JFormatter f) {
        if (bracesRequired)
            f.p('{').nl();
        if (indentRequired)
            f.i();
        generateBody(f);
        if (indentRequired)
            f.o();
        if (bracesRequired)
            f.p('}');
    }

    void generateBody(JFormatter f) {
        for (Object o : content) {
            if (o instanceof JDeclaration)
                f.d((JDeclaration) o);
            else
                f.s((JStatement) o);
        }
    }

    /**
     * Creates an enhanced For statement based on j2se 1.5 JLS
     * and add it to this block
     *
     * @return Newly generated enhanced For statement per j2se 1.5
     * specification
    */
    public JForEach forEach(JType varType, String name, JExpression collection) {
        return insert(new JForEach( varType, name, collection));

    }
    public void state(JFormatter f) {
        f.g(this);
        if (bracesRequired)
            f.nl();
    }

}
