/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Preview;
import com.sun.tools.javac.code.Scope.WriteableScope;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.Completer;
import com.sun.tools.javac.code.Symbol.DynamicMethodSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.*;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.jvm.PoolConstant.LoadableConstant;
import com.sun.tools.javac.jvm.Target;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.util.JCDiagnostic.SimpleDiagnosticPosition;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.stream.Collectors;

import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.code.Scope.LookupKind.NON_RECURSIVE;
import static com.sun.tools.javac.tree.JCTree.Tag.*;

/** This pass translates constructed literals (string templates, ...) to conventional Java.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public final class TransLiterals extends TreeTranslator {
    /**
     * The context key for the TransTypes phase.
     */
    protected static final Context.Key<TransLiterals> transLiteralsKey = new Context.Key<>();
    private final char SYNTHETIC_NAME_CHAR;

    /**
     * Get the instance for this context.
     */
    public static TransLiterals instance(Context context) {
        TransLiterals instance = context.get(transLiteralsKey);
        if (instance == null)
            instance = new TransLiterals(context);
        return instance;
    }

    private final Symtab syms;
    private final Attr attr;
    private final Resolve rs;
    private final Types types;
    private final Check chk;
    private final Operators operators;
    private final Names names;
    private final Target target;
    private final Preview preview;
    private TreeMaker make = null;
    private Env<AttrContext> env = null;
    private ClassSymbol currentClass = null;
    private MethodSymbol currentMethodSym = null;

    protected TransLiterals(Context context) {
        context.put(transLiteralsKey, this);
        syms = Symtab.instance(context);
        attr = Attr.instance(context);
        rs = Resolve.instance(context);
        make = TreeMaker.instance(context);
        types = Types.instance(context);
        chk = Check.instance(context);
        operators = Operators.instance(context);
        names = Names.instance(context);
        target = Target.instance(context);
        preview = Preview.instance(context);

        SYNTHETIC_NAME_CHAR = target.syntheticNameChar();
    }

    JCExpression makeLit(Type type, Object value) {
        return make.Literal(type.getTag(), value).setType(type.constType(value));
    }

    JCExpression makeString(String string) {
        return makeLit(syms.stringType, string);
    }

    List<JCExpression> makeStringList(List<String> strings) {
        List<JCExpression> exprs = List.nil();
        for (String string : strings) {
            exprs = exprs.append(makeString(string));
        }
        return exprs;
    }

    Type makeListType(Type elemType) {
         return new ClassType(syms.listType.getEnclosingType(), List.of(elemType), syms.listType.tsym);
    }

    JCBinary makeBinary(JCTree.Tag optag, JCExpression lhs, JCExpression rhs) {
        JCBinary tree = make.Binary(optag, lhs, rhs);
        tree.operator = operators.resolveBinary(tree, optag, lhs.type, rhs.type);
        tree.type = tree.operator.type.getReturnType();
        return tree;
    }

    JCVariableDecl makeField(JCClassDecl cls, long flags, Name name, Type type, JCExpression init) {
        VarSymbol sym = new VarSymbol(flags | FINAL | SYNTHETIC, name, type, cls.sym);
        JCVariableDecl var = make.VarDef(sym, init);
        cls.defs = cls.defs.append(var);
        cls.sym.members().enter(var.sym);

        return var;
    }

    MethodType makeMethodType(Type returnType, List<Type> argTypes) {
        return new MethodType(argTypes, returnType, List.nil(), syms.methodClass);
    }

    JCFieldAccess makeThisFieldSelect(Type owner, JCVariableDecl field) {
        JCFieldAccess select = make.Select(make.This(owner), field.name);
        select.type = field.type;
        select.sym = field.sym;
        return select;
    }

    JCIdent makeParamIdent(List<JCVariableDecl> params, Name name) {
        VarSymbol param = params.stream()
                .filter(p -> p.name == name)
                .findFirst()
                .get().sym;
        JCIdent ident = make.Ident(name);
        ident.type = param.type;
        ident.sym = param;
        return ident;
    }

    JCFieldAccess makeSelect(Symbol sym, Name name) {
        return make.Select(make.QualIdent(sym), name);
    }

    JCMethodInvocation makeApply(JCFieldAccess method, List<JCExpression> args) {
        return make.Apply(List.nil(), method, args);
    }

    Symbol findMember(ClassSymbol classSym, Name name) {
        return classSym.members().getSymbolsByName(name, NON_RECURSIVE).iterator().next();
    }

    JCFieldAccess makeFieldAccess(JCClassDecl owner, Name name) {
        Symbol sym = findMember(owner.sym, name);
        JCFieldAccess access = makeSelect(owner.sym, name);
        access.type = sym.type;
        access.sym = sym;
        return access;
    }

    MethodSymbol lookupMethod(DiagnosticPosition pos, Name name, Type qual, List<Type> args) {
        return rs.resolveInternalMethod(pos, env, qual, name, args, List.nil());
    }

    @Override
    public void visitClassDef(JCClassDecl tree) {
        ClassSymbol prevCurrentClass = currentClass;
        try {
            currentClass = tree.sym;
            super.visitClassDef(tree);
        } finally {
            currentClass = prevCurrentClass;
        }
    }

    @Override
    public void visitMethodDef(JCMethodDecl tree) {
        MethodSymbol prevMethodSym = currentMethodSym;
        try {
            currentMethodSym = tree.sym;
            super.visitMethodDef(tree);
        } finally {
            currentMethodSym = prevMethodSym;
        }
    }

    record MethodInfo(MethodType type, MethodSymbol sym, JCMethodDecl decl) {
        void addStatement(JCStatement statement) {
            JCBlock body = decl.body;
            body.stats = body.stats.append(statement);
        }
    }

    class TransStringTemplate {
        JCStringTemplate tree;
        JCExpression processor;
        List<String> fragments;
        List<JCExpression> expressions;
        List<Type> expressionTypes;
        boolean useValuesList;
        JCClassDecl stringTemplateClass;
        JCVariableDecl fragmentsVar;
        JCVariableDecl valuesVar;
        List<JCVariableDecl> fields;
        MethodInfo interpolateMethod;

        TransStringTemplate(JCStringTemplate tree) {
            this.tree = tree;
            this.processor = tree.processor;
            this.fragments = tree.fragments;
            this.expressions = translate(tree.expressions);
            this.expressionTypes = expressions.stream()
                    .map(arg -> arg.type == syms.botType ? syms.objectType : arg.type)
                    .collect(List.collector());
            int slots = expressionTypes.stream()
                    .mapToInt(t -> types.isSameType(t, syms.longType) ||
                            types.isSameType(t, syms.doubleType) ? 2 : 1).sum();
            this.useValuesList = 200 < slots; // StringConcatFactory.MAX_INDY_CONCAT_ARG_SLOTS
            this.stringTemplateClass = null;
            this.fragmentsVar = null;
            this.valuesVar = null;
            this.fields = List.nil();
            this.interpolateMethod = null;
        }

        JCExpression concatExpression(List<String> fragments, List<JCExpression> expressions) {
            JCExpression expr = null;
            Iterator<JCExpression> iterator = expressions.iterator();
            for (String fragment : fragments) {
                expr = expr == null ? makeString(fragment)
                        : makeBinary(PLUS, expr, makeString(fragment));
                if (iterator.hasNext()) {
                    JCExpression expression = iterator.next();
                    Type expressionType = expression.type;
                    expr = makeBinary(PLUS, expr, expression.setType(expressionType));
                }
            }
            return expr;
        }

        JCMethodInvocation createApply(Type owner, Name name, List<JCExpression> args, JCFieldAccess method) {
            List<Type> argTypes = TreeInfo.types(args);
            MethodSymbol methodSym = lookupMethod(tree.pos(), name, owner, argTypes);
            method.sym = methodSym;
            method.type = types.erasure(methodSym.type);
            JCMethodInvocation process = makeApply(method, args);
            process.type = methodSym.getReturnType();
            return process;
        }

        JCMethodInvocation createApply(Type owner, Name name, List<JCExpression> args) {
            JCFieldAccess method = makeSelect(owner.tsym, name);
            return createApply(owner, name, args, method);
        }

        JCMethodInvocation createApply(Symbol receiver, Name name, List<JCExpression> args) {
            Type owner = receiver.type;
            JCFieldAccess method = makeSelect(receiver, name);
            return createApply(owner, name, args, method);
        }

        JCMethodInvocation createApplyToList(List<JCExpression> list, Type argType) {
            Type listType = makeListType(argType == null ? syms.objectType : argType);
            JCMethodInvocation toListApplied = createApply(syms.templateRuntimeType, names.toList, list)
                    .setType(listType);
            toListApplied.varargsElement = argType;
            return toListApplied;
        }

        JCMethodInvocation createApplyExprMethod(JCMethodDecl exprMethod) {
            return createApply(stringTemplateClass.sym, exprMethod.name, List.nil());
        }

        MethodInfo createMethod(long flags, Name name, Type returnType, List<Type> argTypes, JCClassDecl owner) {
            MethodType type = makeMethodType(returnType, argTypes);
            MethodSymbol sym = new MethodSymbol(flags, name, type, owner.sym);
            JCMethodDecl decl = make.MethodDef(sym, type, make.Block(0, List.nil()));
            owner.defs = owner.defs.append(decl);
            owner.sym.members().enter(sym);
            return new MethodInfo(type, sym, decl);
        }

        void createFields() {
            int i = 0;
            for (JCExpression expression : expressions) {


                Type type = expression.type == syms.botType ? syms.objectType : expression.type;
                JCVariableDecl fieldVar = makeField(stringTemplateClass, PRIVATE, make.paramName(i++),
                        type, null);
                fields = fields.append(fieldVar);
            }
        }

        void createFragmentsListAndMethod() {
            List<JCExpression> fragmentArgs = makeStringList(fragments);
            Type stringListType = makeListType(syms.stringType);
            fragmentsVar = makeField(stringTemplateClass, PRIVATE | STATIC,
                    names.fragmentsUpper, stringListType,
                    createApplyToList(fragmentArgs, syms.stringType));
            TransLiterals.MethodInfo method = createMethod(SYNTHETIC | PUBLIC, names.fragments,
                    stringListType, List.nil(), stringTemplateClass);
            method.addStatement(make.Return(make.QualIdent(fragmentsVar.sym)));
        }

        void createValuesListAndMethod() {
            Type listType = makeListType(syms.objectType);
            valuesVar = makeField(stringTemplateClass, SYNTHETIC | PRIVATE, names.valuesUpper,
                    listType, null);
            MethodInfo method = createMethod(SYNTHETIC | PUBLIC, names.values,
                    listType, List.nil(), stringTemplateClass);
            JCExpression returnValues = make.QualIdent(valuesVar.sym);
            returnValues.type = listType;
            method.addStatement(make.Return(returnValues));
        }

        List<JCExpression> createAccessors() {
            return fields.stream()
                    .map(f -> make.QualIdent(f.sym))
                    .collect(List.collector());
        }

        void createInitMethod() {
            long flags = useValuesList ? PUBLIC | VARARGS
                                       : PUBLIC;
            List<Type> types = useValuesList ?
                    List.of(new ArrayType(syms.objectType, syms.arrayClass)) : expressionTypes;
            MethodInfo method = createMethod(flags, names.init,
                    syms.voidType, types, stringTemplateClass);
            JCIdent superIdent = make.Ident(names._super);
            superIdent.sym = lookupMethod(tree.pos(), names.init, syms.objectType, List.nil());
            superIdent.type = superIdent.sym.type;
            JCMethodInvocation superApply = make.Apply(List.nil(), translate(superIdent), List.nil());
            superApply.type = syms.voidType;
            method.addStatement(make.Exec(superApply));
            List<JCVariableDecl> params = method.decl.params;

            if (useValuesList) {
                JCFieldAccess select = makeThisFieldSelect(stringTemplateClass.type, valuesVar);
                JCIdent ident = makeParamIdent(params, params.head.name);
                JCAssign assign = make.Assign(select, createApplyToList(List.of(ident), null));
                assign.type = ident.type;
                method.addStatement(make.Exec(assign));
            } else {
                for (JCVariableDecl field : fields) {
                    JCFieldAccess select = makeThisFieldSelect(stringTemplateClass.type, field);
                    JCIdent ident = makeParamIdent(params, field.name);
                    JCAssign assign = make.Assign(select, ident);
                    assign.type = ident.type;
                    method.addStatement(make.Exec(assign));
                }
            }
        }

        void createValuesMethod() {
            Type objectListType = makeListType(syms.objectType);
            MethodInfo method = createMethod(SYNTHETIC | PUBLIC, names.values,
                    objectListType, List.nil(), stringTemplateClass);
            method.addStatement(make.Return(createApplyToList(createAccessors(), syms.objectType)));
        }

        void createInterpolateMethod() {
            interpolateMethod = createMethod(SYNTHETIC | PUBLIC, names.interpolate,
                    syms.stringType, List.nil(), stringTemplateClass);
            interpolateMethod.addStatement(make.Return(concatExpression(fragments, createAccessors())));
        }

        void createToStringMethod() {
            MethodInfo toStringMethod = createMethod(PUBLIC, names.toString,
                    syms.stringType, List.nil(), stringTemplateClass);
            JCExpression applytoString = this.createApply(syms.stringTemplateType, names.toString,
                    List.of(make.This(stringTemplateClass.type)));
            toStringMethod.addStatement(make.Return(applytoString));
        }

        private JCClassDecl newStringTemplateClass() {
            long flags = PUBLIC | FINAL | SYNTHETIC;

            if (currentMethodSym.isStatic()) {
                flags |= NOOUTERTHIS;
            }

            Name name = chk.localClassName(syms.defineClass(names.empty, currentClass));
            JCClassDecl cDecl =  make.ClassDef(make.Modifiers(flags), name, List.nil(), null, List.nil(), List.nil());
            ClassSymbol cSym = syms.defineClass(name, currentMethodSym);
            cSym.sourcefile = currentClass.sourcefile;
            cSym.completer = Completer.NULL_COMPLETER;
            cSym.members_field = WriteableScope.create(cSym);
            cSym.flags_field = flags;
            ClassType cType = (ClassType)cSym.type;
            cType.supertype_field = syms.objectType;
            cType.interfaces_field = List.of(syms.stringTemplateType);
            cType.all_interfaces_field = List.of(syms.stringTemplateType);
            cType.setEnclosingType(currentClass.type);
            cDecl.sym = cSym;
            cDecl.type = cType;
            cSym.complete();

            return cDecl;
        }

        void createStringTemplateClass() {
            ClassSymbol saveCurrentClass = currentClass;

            try {
                Type stringTemplateType = syms.stringTemplateType;
                stringTemplateClass = newStringTemplateClass();
                currentClass = stringTemplateClass.sym;
                createFragmentsListAndMethod();
                createToStringMethod();

                if (useValuesList) {
                    createValuesListAndMethod();
                } else {
                    createFields();
                    createValuesMethod();
                    createInterpolateMethod();
                }

                createInitMethod();
                saveCurrentClass.members().enter(stringTemplateClass.sym);
            } finally {
                currentClass = saveCurrentClass;
            }
        }

        JCExpression createBSMProcessorPerformMethodCall() {
            List<JCExpression> args = expressions.prepend(processor);
            List<Type> argTypes = expressionTypes.prepend(processor.type);
            Name bootstrapName = names.stringTemplateBSM;
            Name methodName = names.process;
            VarSymbol processorSym = (VarSymbol)TreeInfo.symbol(processor);
            List<LoadableConstant> staticArgValues = List.of(processorSym.asMethodHandle(true));
            List<Type> staticArgsTypes =
                    List.of(syms.methodHandleLookupType, syms.stringType,
                            syms.methodTypeType, syms.methodHandleType);
            for (String fragment : fragments) {
                staticArgValues = staticArgValues.append(LoadableConstant.String(fragment));
                staticArgsTypes = staticArgsTypes.append(syms.stringType);
            }
            Symbol bsm = rs.resolveQualifiedMethod(tree.pos(), env,
                    syms.templateRuntimeType, bootstrapName, staticArgsTypes, List.nil());
            MethodType indyType = new MethodType(argTypes, tree.type, List.nil(), syms.methodClass);
            DynamicMethodSymbol dynSym = new DynamicMethodSymbol(
                    methodName,
                    syms.noSymbol,
                    ((MethodSymbol)bsm).asHandle(),
                    indyType,
                    staticArgValues.toArray(new LoadableConstant[0])
            );
            JCFieldAccess qualifier = make.Select(make.Type(syms.templateProcessorType), dynSym.name);
            qualifier.sym = dynSym;
            qualifier.type = tree.type;
            JCExpression process = make.Apply(List.nil(), qualifier, args);
            process.type = tree.type;
            return process;
        }

        JCExpression createProcessorPerformMethodCall(JCExpression stringTemplate) {
            MethodSymbol appyMeth = lookupMethod(tree.pos(), names.process,
                    syms.templateProcessorType, List.of(syms.stringTemplateType));
            JCExpression applySelect = make.Select(processor, appyMeth);
            JCExpression process = make.Apply(null, applySelect, List.of(stringTemplate))
                    .setType(syms.objectType);
            JCTypeCast cast = make.TypeCast(tree.type, process);
            return cast;
        }

        JCExpression newStringTemplate() {
            createStringTemplateClass();
            List<JCExpression> args = expressions;
            List<Type> argTypes = expressionTypes;
            JCExpression encl = currentMethodSym.isStatic() ? null :
                    make.This(currentMethodSym.owner.type);
            JCNewClass newClass = make.NewClass(encl,
                    null, make.QualIdent(stringTemplateClass.type.tsym), args, stringTemplateClass);
            newClass.constructor = rs.resolveConstructor(
                    new SimpleDiagnosticPosition(make.pos), env, stringTemplateClass.type, argTypes, List.nil());
            newClass.type = stringTemplateClass.type;
            newClass.varargsElement = useValuesList ? syms.objectType : null;

            return newClass;
        }

        boolean isProcessor(Name name) {
            if (processor instanceof JCIdent ident && ident.sym instanceof VarSymbol varSym) {
                if (varSym.flags() == (PUBLIC | FINAL | STATIC) &&
                        varSym.name == name &&
                        types.isSameType(varSym.owner.type, syms.stringTemplateType)) {
                    return true;
                }
            }
            return false;
        }

        boolean isSTRProcessor() {
            if (processor instanceof JCIdent ident && ident.sym instanceof VarSymbol varSym) {
                if (varSym.flags() == (PUBLIC | FINAL | STATIC) &&
                        varSym.name == names.str &&
                        types.isSameType(varSym.owner.type, syms.stringTemplateType)) {
                    return true;
                }
            }
            return false;
        }

        boolean isLinkageProcessor() {
            return processor != null &&
                   !useValuesList &&
                   types.isSubtype(processor.type, syms.processorLinkage) &&
                   processor.type.isFinal() &&
                   TreeInfo.symbol(processor) instanceof VarSymbol varSymbol &&
                   varSymbol.isStatic() &&
                   varSymbol.isFinal();
        }

        JCExpression visit() {
            JCExpression result;
            make.at(tree.pos);

            if (processor == null) {
                result = newStringTemplate();
            } else if (isSTRProcessor()) {
                result = concatExpression(fragments, expressions);
            } else if (isLinkageProcessor()) {
                result = createBSMProcessorPerformMethodCall();
            } else {
                result = createProcessorPerformMethodCall(newStringTemplate());
            }

            return result;
        }
    }

    public void visitStringTemplate(JCStringTemplate tree) {
        int prevPos = make.pos;
        try {
            tree.processor = translate(tree.processor);
            tree.expressions = translate(tree.expressions);

            TransStringTemplate transStringTemplate = new TransStringTemplate(tree);

            result = transStringTemplate.visit();
        } catch (Throwable ex) {
            ex.printStackTrace();
            throw ex;
        } finally {
            make.at(prevPos);
        }
    }

    public void visitVarDef(JCVariableDecl tree) {
        MethodSymbol prevMethodSym = currentMethodSym;
        try {
            tree.mods = translate(tree.mods);
            tree.vartype = translate(tree.vartype);
            if (currentMethodSym == null) {
                // A class or instance field initializer.
                currentMethodSym =
                        new MethodSymbol((tree.mods.flags& Flags.STATIC) | Flags.BLOCK,
                                names.empty, null,
                                currentClass);
            }
            if (tree.init != null) tree.init = translate(tree.init);
            result = tree;
        } finally {
            currentMethodSym = prevMethodSym;
        }
    }

    public JCTree translateTopLevelClass(Env<AttrContext> env, JCTree cdef, TreeMaker make) {
        try {
            this.make = make;
            this.env = env;
            translate(cdef);
        } finally {
            this.make = null;
            this.env = null;
        }

        return cdef;
    }

}
