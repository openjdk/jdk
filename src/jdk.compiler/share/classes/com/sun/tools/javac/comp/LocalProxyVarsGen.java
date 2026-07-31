/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.comp;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.tree.JCTree.JCAssign;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;

import static com.sun.tools.javac.code.Flags.FINAL;
import static com.sun.tools.javac.code.Flags.HASINIT;
import static com.sun.tools.javac.code.Flags.STRICT;
import static com.sun.tools.javac.code.Flags.SYNTHETIC;
import static com.sun.tools.javac.code.TypeTag.BOT;

import com.sun.tools.javac.jvm.Target;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Options;

/** This phase adds local variable proxies for fields that are read during the
 *  early construction phase (prologue)
 *
 *  Assignments to the affected instance fields will be rewritten as assignments to a
 *  local proxy variable. Fields will be assigned to with its corresponding local variable
 *  proxy just before the super invocation and after its arguments, if any, have been evaluated.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class LocalProxyVarsGen {

    protected static final Context.Key<LocalProxyVarsGen> localProxyVarsGenKey = new Context.Key<>();

    public static LocalProxyVarsGen instance(Context context) {
        LocalProxyVarsGen instance = context.get(localProxyVarsGenKey);
        if (instance == null)
            instance = new LocalProxyVarsGen(context);
        return instance;
    }

    private final Types types;
    private final Names names;
    private final Symtab syms;
    private final Target target;
    private TreeMaker make;
    private final Map<Symbol, Set<Symbol>> fieldsReadInPrologue = new HashMap<>();

    private final boolean noLocalProxyVars;

    @SuppressWarnings("this-escape")
    protected LocalProxyVarsGen(Context context) {
        context.put(localProxyVarsGenKey, this);
        make = TreeMaker.instance(context);
        types = Types.instance(context);
        names = Names.instance(context);
        syms = Symtab.instance(context);
        target = Target.instance(context);
        Options options = Options.instance(context);
        noLocalProxyVars = options.isSet("noLocalProxyVars");
    }

    public void addFieldReadInPrologue(Symbol owner, Symbol sym) {
        Assert.checkNonNull(sym, "parameter 'sym' is null");
        Set<Symbol> fieldSet = fieldsReadInPrologue.getOrDefault(owner, new LinkedHashSet<>());
        fieldSet.add(sym);
        fieldsReadInPrologue.put(owner, fieldSet);
    }

    public void patchConstructor(JCMethodDecl tree, TreeMaker make) {
        /* if some fields have initializers those probably were added using the enclosing class as their map key
         * we need to recover those now and add them to this constructor
         */
        Set<Symbol> earlyReads = null;
        if (fieldsReadInPrologue.get(tree.sym.owner) != null) {
            earlyReads = fieldsReadInPrologue.get(tree.sym.owner);
        }
        if (fieldsReadInPrologue.get(tree.sym) != null) {
            Set<Symbol> constructorEarlyReads = fieldsReadInPrologue.remove(tree.sym);
            if (constructorEarlyReads != null) {
                if (earlyReads == null) {
                    earlyReads = constructorEarlyReads;
                } else {
                    earlyReads.addAll(constructorEarlyReads);
                }
            }
        }
        if (earlyReads != null && !noLocalProxyVars) {
            addLocalProxiesFor(tree, earlyReads, make);
        }
    }

    public void allFieldNormalized(Symbol.ClassSymbol csym) {
        fieldsReadInPrologue.remove(csym);
    }

    void addLocalProxiesFor(JCMethodDecl constructor, Set<Symbol> fields, TreeMaker make) {
        ListBuffer<JCStatement> localDeclarations = new ListBuffer<>();
        Map<Symbol, Symbol> fieldToLocalMap = new LinkedHashMap<>();

        for (Symbol field : fields) {
            long flags = SYNTHETIC;
            VarSymbol proxy = new VarSymbol(flags, newLocalName(field.name.toString()), field.erasure(types), constructor.sym);
            fieldToLocalMap.put(field, proxy);
            JCVariableDecl localDecl;
            JCExpression initializer = null;
            if ((field.flags() & (HASINIT | FINAL | STRICT)) == 0) {
                initializer = field.type.isPrimitive() ?
                                    make.at(constructor.pos).Literal(0) :
                                    make.at(constructor.pos).Literal(BOT, null).setType(syms.botType);
            }
            localDecl = make.at(constructor.pos).VarDef(proxy, initializer);
            localDeclarations = localDeclarations.append(localDecl);
        }

        FieldRewriter fieldRewriter = new FieldRewriter(constructor, fieldToLocalMap);
        ListBuffer<JCStatement> newBody = new ListBuffer<>();
        for (JCStatement st : constructor.body.stats) {
            newBody = newBody.append(fieldRewriter.translate(st));
        }
        localDeclarations.addAll(newBody);
        ListBuffer<JCStatement> assigmentsBeforeSuper = new ListBuffer<>();
        for (Symbol vsym : fieldToLocalMap.keySet()) {
            Symbol local = fieldToLocalMap.get(vsym);
            assigmentsBeforeSuper.append(make.at(constructor.pos()).Assignment(vsym, make.at(constructor.pos()).Ident(local)));
        }
        constructor.body.stats = localDeclarations.toList();
        JCTree.JCMethodInvocation constructorCall = TreeInfo.findConstructorCall(constructor);
        if (constructorCall.args.isEmpty()) {
            // this is just a super invocation with no arguments we can set the fields just before the invocation
            // and let Gen do the rest
            TreeInfo.mapSuperCalls(constructor.body, supercall -> make.Block(0, assigmentsBeforeSuper.toList().append(supercall)));
        } else {
            // we need to generate fresh local variables to catch the values of the arguments, then
            // assign the proxy locals to the fields and finally invoke the super with the fresh local variables
            int argPosition = 0;
            ListBuffer<JCStatement> superArgsProxies = new ListBuffer<>();
            Symbol.MethodSymbol constructorCallSymbol = (Symbol.MethodSymbol) TreeInfo.symbolFor(constructorCall.meth);
            List<Type> allDeclaredArgs = constructorCallSymbol.externalType(types).getParameterTypes();
            for (JCExpression arg : constructorCall.args) {
                Type declaredType = allDeclaredArgs.head;
                long flags = SYNTHETIC | FINAL;
                VarSymbol proxyForArgSym = new VarSymbol(flags, newLocalName("" + argPosition), types.erasure(declaredType), constructor.sym);
                JCVariableDecl proxyForArgDecl = make.at(constructor.pos).VarDef(proxyForArgSym, arg);
                superArgsProxies = superArgsProxies.append(proxyForArgDecl);
                argPosition++;
                allDeclaredArgs = allDeclaredArgs.tail;
            }
            List<JCStatement> superArgsProxiesList = superArgsProxies.toList();
            ListBuffer<JCExpression> newArgs = new ListBuffer<>();
            for (JCStatement argProxy : superArgsProxies) {
                newArgs.add(make.at(argProxy.pos).Ident((JCVariableDecl) argProxy));
            }
            constructorCall.args = newArgs.toList();
            TreeInfo.mapSuperCalls(constructor.body,
                    supercall -> make.Block(0, superArgsProxiesList.appendList(assigmentsBeforeSuper.toList()).append(supercall)));
        }
    }

    private Name newLocalName(String name) {
        return names.fromString("local" + target.syntheticNameChar() + name);
    }

    class FieldRewriter extends TreeTranslator {
        JCMethodDecl md;
        Map<Symbol, Symbol> fieldToLocalMap;
        boolean ctorPrologue = true;

        public FieldRewriter(JCMethodDecl md, Map<Symbol, Symbol> fieldToLocalMap) {
            this.md = md;
            this.fieldToLocalMap = fieldToLocalMap;
        }

        @Override
        public void visitIdent(JCTree.JCIdent tree) {
            if (ctorPrologue && fieldToLocalMap.get(tree.sym) != null) {
                result = make.at(md).Ident(fieldToLocalMap.get(tree.sym));
            } else {
                result = tree;
            }
        }

        @Override
        public void visitSelect(JCTree.JCFieldAccess tree) {
            super.visitSelect(tree);
            if (ctorPrologue && fieldToLocalMap.get(tree.sym) != null) {
                result = make.at(md).Ident(fieldToLocalMap.get(tree.sym));
            } else {
                result = tree;
            }
        }

        @Override
        public void visitApply(JCTree.JCMethodInvocation tree) {
            super.visitApply(tree);
            if (TreeInfo.isConstructorCall(tree)) {
                ctorPrologue = false;
            }
        }
    }
}
