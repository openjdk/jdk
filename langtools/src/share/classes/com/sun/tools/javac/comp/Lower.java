/*
 * Copyright (c) 1999, 2009, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;

import com.sun.tools.javac.code.*;
import com.sun.tools.javac.jvm.*;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.util.List;

import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.code.Type.*;

import com.sun.tools.javac.jvm.Target;

import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.code.Kinds.*;
import static com.sun.tools.javac.code.TypeTags.*;
import static com.sun.tools.javac.jvm.ByteCodes.*;

/** This pass translates away some syntactic sugar: inner classes,
 *  class literals, assertions, foreach loops, etc.
 *
 *  <p><b>This is NOT part of any API supported by Sun Microsystems.  If
 *  you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Lower extends TreeTranslator {
    protected static final Context.Key<Lower> lowerKey =
        new Context.Key<Lower>();

    public static Lower instance(Context context) {
        Lower instance = context.get(lowerKey);
        if (instance == null)
            instance = new Lower(context);
        return instance;
    }

    private Names names;
    private Log log;
    private Symtab syms;
    private Resolve rs;
    private Check chk;
    private Attr attr;
    private TreeMaker make;
    private DiagnosticPosition make_pos;
    private ClassWriter writer;
    private ClassReader reader;
    private ConstFold cfolder;
    private Target target;
    private Source source;
    private boolean allowEnums;
    private final Name dollarAssertionsDisabled;
    private final Name classDollar;
    private Types types;
    private boolean debugLower;

    protected Lower(Context context) {
        context.put(lowerKey, this);
        names = Names.instance(context);
        log = Log.instance(context);
        syms = Symtab.instance(context);
        rs = Resolve.instance(context);
        chk = Check.instance(context);
        attr = Attr.instance(context);
        make = TreeMaker.instance(context);
        writer = ClassWriter.instance(context);
        reader = ClassReader.instance(context);
        cfolder = ConstFold.instance(context);
        target = Target.instance(context);
        source = Source.instance(context);
        allowEnums = source.allowEnums();
        dollarAssertionsDisabled = names.
            fromString(target.syntheticNameChar() + "assertionsDisabled");
        classDollar = names.
            fromString("class" + target.syntheticNameChar());

        types = Types.instance(context);
        Options options = Options.instance(context);
        debugLower = options.get("debuglower") != null;
    }

    /** The currently enclosing class.
     */
    ClassSymbol currentClass;

    /** A queue of all translated classes.
     */
    ListBuffer<JCTree> translated;

    /** Environment for symbol lookup, set by translateTopLevelClass.
     */
    Env<AttrContext> attrEnv;

    /** A hash table mapping syntax trees to their ending source positions.
     */
    Map<JCTree, Integer> endPositions;

/**************************************************************************
 * Global mappings
 *************************************************************************/

    /** A hash table mapping local classes to their definitions.
     */
    Map<ClassSymbol, JCClassDecl> classdefs;

    /** A hash table mapping virtual accessed symbols in outer subclasses
     *  to the actually referred symbol in superclasses.
     */
    Map<Symbol,Symbol> actualSymbols;

    /** The current method definition.
     */
    JCMethodDecl currentMethodDef;

    /** The current method symbol.
     */
    MethodSymbol currentMethodSym;

    /** The currently enclosing outermost class definition.
     */
    JCClassDecl outermostClassDef;

    /** The currently enclosing outermost member definition.
     */
    JCTree outermostMemberDef;

    /** A navigator class for assembling a mapping from local class symbols
     *  to class definition trees.
     *  There is only one case; all other cases simply traverse down the tree.
     */
    class ClassMap extends TreeScanner {

        /** All encountered class defs are entered into classdefs table.
         */
        public void visitClassDef(JCClassDecl tree) {
            classdefs.put(tree.sym, tree);
            super.visitClassDef(tree);
        }
    }
    ClassMap classMap = new ClassMap();

    /** Map a class symbol to its definition.
     *  @param c    The class symbol of which we want to determine the definition.
     */
    JCClassDecl classDef(ClassSymbol c) {
        // First lookup the class in the classdefs table.
        JCClassDecl def = classdefs.get(c);
        if (def == null && outermostMemberDef != null) {
            // If this fails, traverse outermost member definition, entering all
            // local classes into classdefs, and try again.
            classMap.scan(outermostMemberDef);
            def = classdefs.get(c);
        }
        if (def == null) {
            // If this fails, traverse outermost class definition, entering all
            // local classes into classdefs, and try again.
            classMap.scan(outermostClassDef);
            def = classdefs.get(c);
        }
        return def;
    }

    /** A hash table mapping class symbols to lists of free variables.
     *  accessed by them. Only free variables of the method immediately containing
     *  a class are associated with that class.
     */
    Map<ClassSymbol,List<VarSymbol>> freevarCache;

    /** A navigator class for collecting the free variables accessed
     *  from a local class.
     *  There is only one case; all other cases simply traverse down the tree.
     */
    class FreeVarCollector extends TreeScanner {

        /** The owner of the local class.
         */
        Symbol owner;

        /** The local class.
         */
        ClassSymbol clazz;

        /** The list of owner's variables accessed from within the local class,
         *  without any duplicates.
         */
        List<VarSymbol> fvs;

        FreeVarCollector(ClassSymbol clazz) {
            this.clazz = clazz;
            this.owner = clazz.owner;
            this.fvs = List.nil();
        }

        /** Add free variable to fvs list unless it is already there.
         */
        private void addFreeVar(VarSymbol v) {
            for (List<VarSymbol> l = fvs; l.nonEmpty(); l = l.tail)
                if (l.head == v) return;
            fvs = fvs.prepend(v);
        }

        /** Add all free variables of class c to fvs list
         *  unless they are already there.
         */
        private void addFreeVars(ClassSymbol c) {
            List<VarSymbol> fvs = freevarCache.get(c);
            if (fvs != null) {
                for (List<VarSymbol> l = fvs; l.nonEmpty(); l = l.tail) {
                    addFreeVar(l.head);
                }
            }
        }

        /** If tree refers to a variable in owner of local class, add it to
         *  free variables list.
         */
        public void visitIdent(JCIdent tree) {
            result = tree;
            visitSymbol(tree.sym);
        }
        // where
        private void visitSymbol(Symbol _sym) {
            Symbol sym = _sym;
            if (sym.kind == VAR || sym.kind == MTH) {
                while (sym != null && sym.owner != owner)
                    sym = proxies.lookup(proxyName(sym.name)).sym;
                if (sym != null && sym.owner == owner) {
                    VarSymbol v = (VarSymbol)sym;
                    if (v.getConstValue() == null) {
                        addFreeVar(v);
                    }
                } else {
                    if (outerThisStack.head != null &&
                        outerThisStack.head != _sym)
                        visitSymbol(outerThisStack.head);
                }
            }
        }

        /** If tree refers to a class instance creation expression
         *  add all free variables of the freshly created class.
         */
        public void visitNewClass(JCNewClass tree) {
            ClassSymbol c = (ClassSymbol)tree.constructor.owner;
            addFreeVars(c);
            if (tree.encl == null &&
                c.hasOuterInstance() &&
                outerThisStack.head != null)
                visitSymbol(outerThisStack.head);
            super.visitNewClass(tree);
        }

        /** If tree refers to a qualified this or super expression
         *  for anything but the current class, add the outer this
         *  stack as a free variable.
         */
        public void visitSelect(JCFieldAccess tree) {
            if ((tree.name == names._this || tree.name == names._super) &&
                tree.selected.type.tsym != clazz &&
                outerThisStack.head != null)
                visitSymbol(outerThisStack.head);
            super.visitSelect(tree);
        }

        /** If tree refers to a superclass constructor call,
         *  add all free variables of the superclass.
         */
        public void visitApply(JCMethodInvocation tree) {
            if (TreeInfo.name(tree.meth) == names._super) {
                addFreeVars((ClassSymbol) TreeInfo.symbol(tree.meth).owner);
                Symbol constructor = TreeInfo.symbol(tree.meth);
                ClassSymbol c = (ClassSymbol)constructor.owner;
                if (c.hasOuterInstance() &&
                    tree.meth.getTag() != JCTree.SELECT &&
                    outerThisStack.head != null)
                    visitSymbol(outerThisStack.head);
            }
            super.visitApply(tree);
        }
    }

    /** Return the variables accessed from within a local class, which
     *  are declared in the local class' owner.
     *  (in reverse order of first access).
     */
    List<VarSymbol> freevars(ClassSymbol c)  {
        if ((c.owner.kind & (VAR | MTH)) != 0) {
            List<VarSymbol> fvs = freevarCache.get(c);
            if (fvs == null) {
                FreeVarCollector collector = new FreeVarCollector(c);
                collector.scan(classDef(c));
                fvs = collector.fvs;
                freevarCache.put(c, fvs);
            }
            return fvs;
        } else {
            return List.nil();
        }
    }

    Map<TypeSymbol,EnumMapping> enumSwitchMap = new LinkedHashMap<TypeSymbol,EnumMapping>();

    EnumMapping mapForEnum(DiagnosticPosition pos, TypeSymbol enumClass) {
        EnumMapping map = enumSwitchMap.get(enumClass);
        if (map == null)
            enumSwitchMap.put(enumClass, map = new EnumMapping(pos, enumClass));
        return map;
    }

    /** This map gives a translation table to be used for enum
     *  switches.
     *
     *  <p>For each enum that appears as the type of a switch
     *  expression, we maintain an EnumMapping to assist in the
     *  translation, as exemplified by the following example:
     *
     *  <p>we translate
     *  <pre>
     *          switch(colorExpression) {
     *          case red: stmt1;
     *          case green: stmt2;
     *          }
     *  </pre>
     *  into
     *  <pre>
     *          switch(Outer$0.$EnumMap$Color[colorExpression.ordinal()]) {
     *          case 1: stmt1;
     *          case 2: stmt2
     *          }
     *  </pre>
     *  with the auxiliary table initialized as follows:
     *  <pre>
     *          class Outer$0 {
     *              synthetic final int[] $EnumMap$Color = new int[Color.values().length];
     *              static {
     *                  try { $EnumMap$Color[red.ordinal()] = 1; } catch (NoSuchFieldError ex) {}
     *                  try { $EnumMap$Color[green.ordinal()] = 2; } catch (NoSuchFieldError ex) {}
     *              }
     *          }
     *  </pre>
     *  class EnumMapping provides mapping data and support methods for this translation.
     */
    class EnumMapping {
        EnumMapping(DiagnosticPosition pos, TypeSymbol forEnum) {
            this.forEnum = forEnum;
            this.values = new LinkedHashMap<VarSymbol,Integer>();
            this.pos = pos;
            Name varName = names
                .fromString(target.syntheticNameChar() +
                            "SwitchMap" +
                            target.syntheticNameChar() +
                            writer.xClassName(forEnum.type).toString()
                            .replace('/', '.')
                            .replace('.', target.syntheticNameChar()));
            ClassSymbol outerCacheClass = outerCacheClass();
            this.mapVar = new VarSymbol(STATIC | SYNTHETIC | FINAL,
                                        varName,
                                        new ArrayType(syms.intType, syms.arrayClass),
                                        outerCacheClass);
            enterSynthetic(pos, mapVar, outerCacheClass.members());
        }

        DiagnosticPosition pos = null;

        // the next value to use
        int next = 1; // 0 (unused map elements) go to the default label

        // the enum for which this is a map
        final TypeSymbol forEnum;

        // the field containing the map
        final VarSymbol mapVar;

        // the mapped values
        final Map<VarSymbol,Integer> values;

        JCLiteral forConstant(VarSymbol v) {
            Integer result = values.get(v);
            if (result == null)
                values.put(v, result = next++);
            return make.Literal(result);
        }

        // generate the field initializer for the map
        void translate() {
            make.at(pos.getStartPosition());
            JCClassDecl owner = classDef((ClassSymbol)mapVar.owner);

            // synthetic static final int[] $SwitchMap$Color = new int[Color.values().length];
            MethodSymbol valuesMethod = lookupMethod(pos,
                                                     names.values,
                                                     forEnum.type,
                                                     List.<Type>nil());
            JCExpression size = make // Color.values().length
                .Select(make.App(make.QualIdent(valuesMethod)),
                        syms.lengthVar);
            JCExpression mapVarInit = make
                .NewArray(make.Type(syms.intType), List.of(size), null)
                .setType(new ArrayType(syms.intType, syms.arrayClass));

            // try { $SwitchMap$Color[red.ordinal()] = 1; } catch (java.lang.NoSuchFieldError ex) {}
            ListBuffer<JCStatement> stmts = new ListBuffer<JCStatement>();
            Symbol ordinalMethod = lookupMethod(pos,
                                                names.ordinal,
                                                forEnum.type,
                                                List.<Type>nil());
            List<JCCatch> catcher = List.<JCCatch>nil()
                .prepend(make.Catch(make.VarDef(new VarSymbol(PARAMETER, names.ex,
                                                              syms.noSuchFieldErrorType,
                                                              syms.noSymbol),
                                                null),
                                    make.Block(0, List.<JCStatement>nil())));
            for (Map.Entry<VarSymbol,Integer> e : values.entrySet()) {
                VarSymbol enumerator = e.getKey();
                Integer mappedValue = e.getValue();
                JCExpression assign = make
                    .Assign(make.Indexed(mapVar,
                                         make.App(make.Select(make.QualIdent(enumerator),
                                                              ordinalMethod))),
                            make.Literal(mappedValue))
                    .setType(syms.intType);
                JCStatement exec = make.Exec(assign);
                JCStatement _try = make.Try(make.Block(0, List.of(exec)), catcher, null);
                stmts.append(_try);
            }

            owner.defs = owner.defs
                .prepend(make.Block(STATIC, stmts.toList()))
                .prepend(make.VarDef(mapVar, mapVarInit));
        }
    }


/**************************************************************************
 * Tree building blocks
 *************************************************************************/

    /** Equivalent to make.at(pos.getStartPosition()) with side effect of caching
     *  pos as make_pos, for use in diagnostics.
     **/
    TreeMaker make_at(DiagnosticPosition pos) {
        make_pos = pos;
        return make.at(pos);
    }

    /** Make an attributed tree representing a literal. This will be an
     *  Ident node in the case of boolean literals, a Literal node in all
     *  other cases.
     *  @param type       The literal's type.
     *  @param value      The literal's value.
     */
    JCExpression makeLit(Type type, Object value) {
        return make.Literal(type.tag, value).setType(type.constType(value));
    }

    /** Make an attributed tree representing null.
     */
    JCExpression makeNull() {
        return makeLit(syms.botType, null);
    }

    /** Make an attributed class instance creation expression.
     *  @param ctype    The class type.
     *  @param args     The constructor arguments.
     */
    JCNewClass makeNewClass(Type ctype, List<JCExpression> args) {
        JCNewClass tree = make.NewClass(null,
            null, make.QualIdent(ctype.tsym), args, null);
        tree.constructor = rs.resolveConstructor(
            make_pos, attrEnv, ctype, TreeInfo.types(args), null, false, false);
        tree.type = ctype;
        return tree;
    }

    /** Make an attributed unary expression.
     *  @param optag    The operators tree tag.
     *  @param arg      The operator's argument.
     */
    JCUnary makeUnary(int optag, JCExpression arg) {
        JCUnary tree = make.Unary(optag, arg);
        tree.operator = rs.resolveUnaryOperator(
            make_pos, optag, attrEnv, arg.type);
        tree.type = tree.operator.type.getReturnType();
        return tree;
    }

    /** Make an attributed binary expression.
     *  @param optag    The operators tree tag.
     *  @param lhs      The operator's left argument.
     *  @param rhs      The operator's right argument.
     */
    JCBinary makeBinary(int optag, JCExpression lhs, JCExpression rhs) {
        JCBinary tree = make.Binary(optag, lhs, rhs);
        tree.operator = rs.resolveBinaryOperator(
            make_pos, optag, attrEnv, lhs.type, rhs.type);
        tree.type = tree.operator.type.getReturnType();
        return tree;
    }

    /** Make an attributed assignop expression.
     *  @param optag    The operators tree tag.
     *  @param lhs      The operator's left argument.
     *  @param rhs      The operator's right argument.
     */
    JCAssignOp makeAssignop(int optag, JCTree lhs, JCTree rhs) {
        JCAssignOp tree = make.Assignop(optag, lhs, rhs);
        tree.operator = rs.resolveBinaryOperator(
            make_pos, tree.getTag() - JCTree.ASGOffset, attrEnv, lhs.type, rhs.type);
        tree.type = lhs.type;
        return tree;
    }

    /** Convert tree into string object, unless it has already a
     *  reference type..
     */
    JCExpression makeString(JCExpression tree) {
        if (tree.type.tag >= CLASS) {
            return tree;
        } else {
            Symbol valueOfSym = lookupMethod(tree.pos(),
                                             names.valueOf,
                                             syms.stringType,
                                             List.of(tree.type));
            return make.App(make.QualIdent(valueOfSym), List.of(tree));
        }
    }

    /** Create an empty anonymous class definition and enter and complete
     *  its symbol. Return the class definition's symbol.
     *  and create
     *  @param flags    The class symbol's flags
     *  @param owner    The class symbol's owner
     */
    ClassSymbol makeEmptyClass(long flags, ClassSymbol owner) {
        // Create class symbol.
        ClassSymbol c = reader.defineClass(names.empty, owner);
        c.flatname = chk.localClassName(c);
        c.sourcefile = owner.sourcefile;
        c.completer = null;
        c.members_field = new Scope(c);
        c.flags_field = flags;
        ClassType ctype = (ClassType) c.type;
        ctype.supertype_field = syms.objectType;
        ctype.interfaces_field = List.nil();

        JCClassDecl odef = classDef(owner);

        // Enter class symbol in owner scope and compiled table.
        enterSynthetic(odef.pos(), c, owner.members());
        chk.compiled.put(c.flatname, c);

        // Create class definition tree.
        JCClassDecl cdef = make.ClassDef(
            make.Modifiers(flags), names.empty,
            List.<JCTypeParameter>nil(),
            null, List.<JCExpression>nil(), List.<JCTree>nil());
        cdef.sym = c;
        cdef.type = c.type;

        // Append class definition tree to owner's definitions.
        odef.defs = odef.defs.prepend(cdef);

        return c;
    }

/**************************************************************************
 * Symbol manipulation utilities
 *************************************************************************/

    /** Enter a synthetic symbol in a given scope, but complain if there was already one there.
     *  @param pos           Position for error reporting.
     *  @param sym           The symbol.
     *  @param s             The scope.
     */
    private void enterSynthetic(DiagnosticPosition pos, Symbol sym, Scope s) {
        s.enter(sym);
    }

    /** Check whether synthetic symbols generated during lowering conflict
     *  with user-defined symbols.
     *
     *  @param translatedTrees lowered class trees
     */
    void checkConflicts(List<JCTree> translatedTrees) {
        for (JCTree t : translatedTrees) {
            t.accept(conflictsChecker);
        }
    }

    JCTree.Visitor conflictsChecker = new TreeScanner() {

        TypeSymbol currentClass;

        @Override
        public void visitMethodDef(JCMethodDecl that) {
            chk.checkConflicts(that.pos(), that.sym, currentClass);
            super.visitMethodDef(that);
        }

        @Override
        public void visitVarDef(JCVariableDecl that) {
            if (that.sym.owner.kind == TYP) {
                chk.checkConflicts(that.pos(), that.sym, currentClass);
            }
            super.visitVarDef(that);
        }

        @Override
        public void visitClassDef(JCClassDecl that) {
            TypeSymbol prevCurrentClass = currentClass;
            currentClass = that.sym;
            try {
                super.visitClassDef(that);
            }
            finally {
                currentClass = prevCurrentClass;
            }
        }
    };

    /** Look up a synthetic name in a given scope.
     *  @param scope        The scope.
     *  @param name         The name.
     */
    private Symbol lookupSynthetic(Name name, Scope s) {
        Symbol sym = s.lookup(name).sym;
        return (sym==null || (sym.flags()&SYNTHETIC)==0) ? null : sym;
    }

    /** Look up a method in a given scope.
     */
    private MethodSymbol lookupMethod(DiagnosticPosition pos, Name name, Type qual, List<Type> args) {
        return rs.resolveInternalMethod(pos, attrEnv, qual, name, args, null);
    }

    /** Look up a constructor.
     */
    private MethodSymbol lookupConstructor(DiagnosticPosition pos, Type qual, List<Type> args) {
        return rs.resolveInternalConstructor(pos, attrEnv, qual, args, null);
    }

    /** Look up a field.
     */
    private VarSymbol lookupField(DiagnosticPosition pos, Type qual, Name name) {
        return rs.resolveInternalField(pos, attrEnv, qual, name);
    }

/**************************************************************************
 * Access methods
 *************************************************************************/

    /** Access codes for dereferencing, assignment,
     *  and pre/post increment/decrement.
     *  Access codes for assignment operations are determined by method accessCode
     *  below.
     *
     *  All access codes for accesses to the current class are even.
     *  If a member of the superclass should be accessed instead (because
     *  access was via a qualified super), add one to the corresponding code
     *  for the current class, making the number odd.
     *  This numbering scheme is used by the backend to decide whether
     *  to issue an invokevirtual or invokespecial call.
     *
     *  @see Gen.visitSelect(Select tree)
     */
    private static final int
        DEREFcode = 0,
        ASSIGNcode = 2,
        PREINCcode = 4,
        PREDECcode = 6,
        POSTINCcode = 8,
        POSTDECcode = 10,
        FIRSTASGOPcode = 12;

    /** Number of access codes
     */
    private static final int NCODES = accessCode(ByteCodes.lushrl) + 2;

    /** A mapping from symbols to their access numbers.
     */
    private Map<Symbol,Integer> accessNums;

    /** A mapping from symbols to an array of access symbols, indexed by
     *  access code.
     */
    private Map<Symbol,MethodSymbol[]> accessSyms;

    /** A mapping from (constructor) symbols to access constructor symbols.
     */
    private Map<Symbol,MethodSymbol> accessConstrs;

    /** A queue for all accessed symbols.
     */
    private ListBuffer<Symbol> accessed;

    /** Map bytecode of binary operation to access code of corresponding
     *  assignment operation. This is always an even number.
     */
    private static int accessCode(int bytecode) {
        if (ByteCodes.iadd <= bytecode && bytecode <= ByteCodes.lxor)
            return (bytecode - iadd) * 2 + FIRSTASGOPcode;
        else if (bytecode == ByteCodes.string_add)
            return (ByteCodes.lxor + 1 - iadd) * 2 + FIRSTASGOPcode;
        else if (ByteCodes.ishll <= bytecode && bytecode <= ByteCodes.lushrl)
            return (bytecode - ishll + ByteCodes.lxor + 2 - iadd) * 2 + FIRSTASGOPcode;
        else
            return -1;
    }

    /** return access code for identifier,
     *  @param tree     The tree representing the identifier use.
     *  @param enclOp   The closest enclosing operation node of tree,
     *                  null if tree is not a subtree of an operation.
     */
    private static int accessCode(JCTree tree, JCTree enclOp) {
        if (enclOp == null)
            return DEREFcode;
        else if (enclOp.getTag() == JCTree.ASSIGN &&
                 tree == TreeInfo.skipParens(((JCAssign) enclOp).lhs))
            return ASSIGNcode;
        else if (JCTree.PREINC <= enclOp.getTag() && enclOp.getTag() <= JCTree.POSTDEC &&
                 tree == TreeInfo.skipParens(((JCUnary) enclOp).arg))
            return (enclOp.getTag() - JCTree.PREINC) * 2 + PREINCcode;
        else if (JCTree.BITOR_ASG <= enclOp.getTag() && enclOp.getTag() <= JCTree.MOD_ASG &&
                 tree == TreeInfo.skipParens(((JCAssignOp) enclOp).lhs))
            return accessCode(((OperatorSymbol) ((JCAssignOp) enclOp).operator).opcode);
        else
            return DEREFcode;
    }

    /** Return binary operator that corresponds to given access code.
     */
    private OperatorSymbol binaryAccessOperator(int acode) {
        for (Scope.Entry e = syms.predefClass.members().elems;
             e != null;
             e = e.sibling) {
            if (e.sym instanceof OperatorSymbol) {
                OperatorSymbol op = (OperatorSymbol)e.sym;
                if (accessCode(op.opcode) == acode) return op;
            }
        }
        return null;
    }

    /** Return tree tag for assignment operation corresponding
     *  to given binary operator.
     */
    private static int treeTag(OperatorSymbol operator) {
        switch (operator.opcode) {
        case ByteCodes.ior: case ByteCodes.lor:
            return JCTree.BITOR_ASG;
        case ByteCodes.ixor: case ByteCodes.lxor:
            return JCTree.BITXOR_ASG;
        case ByteCodes.iand: case ByteCodes.land:
            return JCTree.BITAND_ASG;
        case ByteCodes.ishl: case ByteCodes.lshl:
        case ByteCodes.ishll: case ByteCodes.lshll:
            return JCTree.SL_ASG;
        case ByteCodes.ishr: case ByteCodes.lshr:
        case ByteCodes.ishrl: case ByteCodes.lshrl:
            return JCTree.SR_ASG;
        case ByteCodes.iushr: case ByteCodes.lushr:
        case ByteCodes.iushrl: case ByteCodes.lushrl:
            return JCTree.USR_ASG;
        case ByteCodes.iadd: case ByteCodes.ladd:
        case ByteCodes.fadd: case ByteCodes.dadd:
        case ByteCodes.string_add:
            return JCTree.PLUS_ASG;
        case ByteCodes.isub: case ByteCodes.lsub:
        case ByteCodes.fsub: case ByteCodes.dsub:
            return JCTree.MINUS_ASG;
        case ByteCodes.imul: case ByteCodes.lmul:
        case ByteCodes.fmul: case ByteCodes.dmul:
            return JCTree.MUL_ASG;
        case ByteCodes.idiv: case ByteCodes.ldiv:
        case ByteCodes.fdiv: case ByteCodes.ddiv:
            return JCTree.DIV_ASG;
        case ByteCodes.imod: case ByteCodes.lmod:
        case ByteCodes.fmod: case ByteCodes.dmod:
            return JCTree.MOD_ASG;
        default:
            throw new AssertionError();
        }
    }

    /** The name of the access method with number `anum' and access code `acode'.
     */
    Name accessName(int anum, int acode) {
        return names.fromString(
            "access" + target.syntheticNameChar() + anum + acode / 10 + acode % 10);
    }

    /** Return access symbol for a private or protected symbol from an inner class.
     *  @param sym        The accessed private symbol.
     *  @param tree       The accessing tree.
     *  @param enclOp     The closest enclosing operation node of tree,
     *                    null if tree is not a subtree of an operation.
     *  @param protAccess Is access to a protected symbol in another
     *                    package?
     *  @param refSuper   Is access via a (qualified) C.super?
     */
    MethodSymbol accessSymbol(Symbol sym, JCTree tree, JCTree enclOp,
                              boolean protAccess, boolean refSuper) {
        ClassSymbol accOwner = refSuper && protAccess
            // For access via qualified super (T.super.x), place the
            // access symbol on T.
            ? (ClassSymbol)((JCFieldAccess) tree).selected.type.tsym
            // Otherwise pretend that the owner of an accessed
            // protected symbol is the enclosing class of the current
            // class which is a subclass of the symbol's owner.
            : accessClass(sym, protAccess, tree);

        Symbol vsym = sym;
        if (sym.owner != accOwner) {
            vsym = sym.clone(accOwner);
            actualSymbols.put(vsym, sym);
        }

        Integer anum              // The access number of the access method.
            = accessNums.get(vsym);
        if (anum == null) {
            anum = accessed.length();
            accessNums.put(vsym, anum);
            accessSyms.put(vsym, new MethodSymbol[NCODES]);
            accessed.append(vsym);
            // System.out.println("accessing " + vsym + " in " + vsym.location());
        }

        int acode;                // The access code of the access method.
        List<Type> argtypes;      // The argument types of the access method.
        Type restype;             // The result type of the access method.
        List<Type> thrown;        // The thrown exceptions of the access method.
        switch (vsym.kind) {
        case VAR:
            acode = accessCode(tree, enclOp);
            if (acode >= FIRSTASGOPcode) {
                OperatorSymbol operator = binaryAccessOperator(acode);
                if (operator.opcode == string_add)
                    argtypes = List.of(syms.objectType);
                else
                    argtypes = operator.type.getParameterTypes().tail;
            } else if (acode == ASSIGNcode)
                argtypes = List.of(vsym.erasure(types));
            else
                argtypes = List.nil();
            restype = vsym.erasure(types);
            thrown = List.nil();
            break;
        case MTH:
            acode = DEREFcode;
            argtypes = vsym.erasure(types).getParameterTypes();
            restype = vsym.erasure(types).getReturnType();
            thrown = vsym.type.getThrownTypes();
            break;
        default:
            throw new AssertionError();
        }

        // For references via qualified super, increment acode by one,
        // making it odd.
        if (protAccess && refSuper) acode++;

        // Instance access methods get instance as first parameter.
        // For protected symbols this needs to be the instance as a member
        // of the type containing the accessed symbol, not the class
        // containing the access method.
        if ((vsym.flags() & STATIC) == 0) {
            argtypes = argtypes.prepend(vsym.owner.erasure(types));
        }
        MethodSymbol[] accessors = accessSyms.get(vsym);
        MethodSymbol accessor = accessors[acode];
        if (accessor == null) {
            accessor = new MethodSymbol(
                STATIC | SYNTHETIC,
                accessName(anum.intValue(), acode),
                new MethodType(argtypes, restype, thrown, syms.methodClass),
                accOwner);
            enterSynthetic(tree.pos(), accessor, accOwner.members());
            accessors[acode] = accessor;
        }
        return accessor;
    }

    /** The qualifier to be used for accessing a symbol in an outer class.
     *  This is either C.sym or C.this.sym, depending on whether or not
     *  sym is static.
     *  @param sym   The accessed symbol.
     */
    JCExpression accessBase(DiagnosticPosition pos, Symbol sym) {
        return (sym.flags() & STATIC) != 0
            ? access(make.at(pos.getStartPosition()).QualIdent(sym.owner))
            : makeOwnerThis(pos, sym, true);
    }

    /** Do we need an access method to reference private symbol?
     */
    boolean needsPrivateAccess(Symbol sym) {
        if ((sym.flags() & PRIVATE) == 0 || sym.owner == currentClass) {
            return false;
        } else if (sym.name == names.init && (sym.owner.owner.kind & (VAR | MTH)) != 0) {
            // private constructor in local class: relax protection
            sym.flags_field &= ~PRIVATE;
            return false;
        } else {
            return true;
        }
    }

    /** Do we need an access method to reference symbol in other package?
     */
    boolean needsProtectedAccess(Symbol sym, JCTree tree) {
        if ((sym.flags() & PROTECTED) == 0 ||
            sym.owner.owner == currentClass.owner || // fast special case
            sym.packge() == currentClass.packge())
            return false;
        if (!currentClass.isSubClass(sym.owner, types))
            return true;
        if ((sym.flags() & STATIC) != 0 ||
            tree.getTag() != JCTree.SELECT ||
            TreeInfo.name(((JCFieldAccess) tree).selected) == names._super)
            return false;
        return !((JCFieldAccess) tree).selected.type.tsym.isSubClass(currentClass, types);
    }

    /** The class in which an access method for given symbol goes.
     *  @param sym        The access symbol
     *  @param protAccess Is access to a protected symbol in another
     *                    package?
     */
    ClassSymbol accessClass(Symbol sym, boolean protAccess, JCTree tree) {
        if (protAccess) {
            Symbol qualifier = null;
            ClassSymbol c = currentClass;
            if (tree.getTag() == JCTree.SELECT && (sym.flags() & STATIC) == 0) {
                qualifier = ((JCFieldAccess) tree).selected.type.tsym;
                while (!qualifier.isSubClass(c, types)) {
                    c = c.owner.enclClass();
                }
                return c;
            } else {
                while (!c.isSubClass(sym.owner, types)) {
                    c = c.owner.enclClass();
                }
            }
            return c;
        } else {
            // the symbol is private
            return sym.owner.enclClass();
        }
    }

    /** Ensure that identifier is accessible, return tree accessing the identifier.
     *  @param sym      The accessed symbol.
     *  @param tree     The tree referring to the symbol.
     *  @param enclOp   The closest enclosing operation node of tree,
     *                  null if tree is not a subtree of an operation.
     *  @param refSuper Is access via a (qualified) C.super?
     */
    JCExpression access(Symbol sym, JCExpression tree, JCExpression enclOp, boolean refSuper) {
        // Access a free variable via its proxy, or its proxy's proxy
        while (sym.kind == VAR && sym.owner.kind == MTH &&
            sym.owner.enclClass() != currentClass) {
            // A constant is replaced by its constant value.
            Object cv = ((VarSymbol)sym).getConstValue();
            if (cv != null) {
                make.at(tree.pos);
                return makeLit(sym.type, cv);
            }
            // Otherwise replace the variable by its proxy.
            sym = proxies.lookup(proxyName(sym.name)).sym;
            assert sym != null && (sym.flags_field & FINAL) != 0;
            tree = make.at(tree.pos).Ident(sym);
        }
        JCExpression base = (tree.getTag() == JCTree.SELECT) ? ((JCFieldAccess) tree).selected : null;
        switch (sym.kind) {
        case TYP:
            if (sym.owner.kind != PCK) {
                // Convert type idents to
                // <flat name> or <package name> . <flat name>
                Name flatname = Convert.shortName(sym.flatName());
                while (base != null &&
                       TreeInfo.symbol(base) != null &&
                       TreeInfo.symbol(base).kind != PCK) {
                    base = (base.getTag() == JCTree.SELECT)
                        ? ((JCFieldAccess) base).selected
                        : null;
                }
                if (tree.getTag() == JCTree.IDENT) {
                    ((JCIdent) tree).name = flatname;
                } else if (base == null) {
                    tree = make.at(tree.pos).Ident(sym);
                    ((JCIdent) tree).name = flatname;
                } else {
                    ((JCFieldAccess) tree).selected = base;
                    ((JCFieldAccess) tree).name = flatname;
                }
            }
            break;
        case MTH: case VAR:
            if (sym.owner.kind == TYP) {

                // Access methods are required for
                //  - private members,
                //  - protected members in a superclass of an
                //    enclosing class contained in another package.
                //  - all non-private members accessed via a qualified super.
                boolean protAccess = refSuper && !needsPrivateAccess(sym)
                    || needsProtectedAccess(sym, tree);
                boolean accReq = protAccess || needsPrivateAccess(sym);

                // A base has to be supplied for
                //  - simple identifiers accessing variables in outer classes.
                boolean baseReq =
                    base == null &&
                    sym.owner != syms.predefClass &&
                    !sym.isMemberOf(currentClass, types);

                if (accReq || baseReq) {
                    make.at(tree.pos);

                    // Constants are replaced by their constant value.
                    if (sym.kind == VAR) {
                        Object cv = ((VarSymbol)sym).getConstValue();
                        if (cv != null) return makeLit(sym.type, cv);
                    }

                    // Private variables and methods are replaced by calls
                    // to their access methods.
                    if (accReq) {
                        List<JCExpression> args = List.nil();
                        if ((sym.flags() & STATIC) == 0) {
                            // Instance access methods get instance
                            // as first parameter.
                            if (base == null)
                                base = makeOwnerThis(tree.pos(), sym, true);
                            args = args.prepend(base);
                            base = null;   // so we don't duplicate code
                        }
                        Symbol access = accessSymbol(sym, tree,
                                                     enclOp, protAccess,
                                                     refSuper);
                        JCExpression receiver = make.Select(
                            base != null ? base : make.QualIdent(access.owner),
                            access);
                        return make.App(receiver, args);

                    // Other accesses to members of outer classes get a
                    // qualifier.
                    } else if (baseReq) {
                        return make.at(tree.pos).Select(
                            accessBase(tree.pos(), sym), sym).setType(tree.type);
                    }
                }
            }
        }
        return tree;
    }

    /** Ensure that identifier is accessible, return tree accessing the identifier.
     *  @param tree     The identifier tree.
     */
    JCExpression access(JCExpression tree) {
        Symbol sym = TreeInfo.symbol(tree);
        return sym == null ? tree : access(sym, tree, null, false);
    }

    /** Return access constructor for a private constructor,
     *  or the constructor itself, if no access constructor is needed.
     *  @param pos       The position to report diagnostics, if any.
     *  @param constr    The private constructor.
     */
    Symbol accessConstructor(DiagnosticPosition pos, Symbol constr) {
        if (needsPrivateAccess(constr)) {
            ClassSymbol accOwner = constr.owner.enclClass();
            MethodSymbol aconstr = accessConstrs.get(constr);
            if (aconstr == null) {
                List<Type> argtypes = constr.type.getParameterTypes();
                if ((accOwner.flags_field & ENUM) != 0)
                    argtypes = argtypes
                        .prepend(syms.intType)
                        .prepend(syms.stringType);
                aconstr = new MethodSymbol(
                    SYNTHETIC,
                    names.init,
                    new MethodType(
                        argtypes.append(
                            accessConstructorTag().erasure(types)),
                        constr.type.getReturnType(),
                        constr.type.getThrownTypes(),
                        syms.methodClass),
                    accOwner);
                enterSynthetic(pos, aconstr, accOwner.members());
                accessConstrs.put(constr, aconstr);
                accessed.append(constr);
            }
            return aconstr;
        } else {
            return constr;
        }
    }

    /** Return an anonymous class nested in this toplevel class.
     */
    ClassSymbol accessConstructorTag() {
        ClassSymbol topClass = currentClass.outermostClass();
        Name flatname = names.fromString("" + topClass.getQualifiedName() +
                                         target.syntheticNameChar() +
                                         "1");
        ClassSymbol ctag = chk.compiled.get(flatname);
        if (ctag == null)
            ctag = makeEmptyClass(STATIC | SYNTHETIC, topClass);
        return ctag;
    }

    /** Add all required access methods for a private symbol to enclosing class.
     *  @param sym       The symbol.
     */
    void makeAccessible(Symbol sym) {
        JCClassDecl cdef = classDef(sym.owner.enclClass());
        assert cdef != null : "class def not found: " + sym + " in " + sym.owner;
        if (sym.name == names.init) {
            cdef.defs = cdef.defs.prepend(
                accessConstructorDef(cdef.pos, sym, accessConstrs.get(sym)));
        } else {
            MethodSymbol[] accessors = accessSyms.get(sym);
            for (int i = 0; i < NCODES; i++) {
                if (accessors[i] != null)
                    cdef.defs = cdef.defs.prepend(
                        accessDef(cdef.pos, sym, accessors[i], i));
            }
        }
    }

    /** Construct definition of an access method.
     *  @param pos        The source code position of the definition.
     *  @param vsym       The private or protected symbol.
     *  @param accessor   The access method for the symbol.
     *  @param acode      The access code.
     */
    JCTree accessDef(int pos, Symbol vsym, MethodSymbol accessor, int acode) {
//      System.err.println("access " + vsym + " with " + accessor);//DEBUG
        currentClass = vsym.owner.enclClass();
        make.at(pos);
        JCMethodDecl md = make.MethodDef(accessor, null);

        // Find actual symbol
        Symbol sym = actualSymbols.get(vsym);
        if (sym == null) sym = vsym;

        JCExpression ref;           // The tree referencing the private symbol.
        List<JCExpression> args;    // Any additional arguments to be passed along.
        if ((sym.flags() & STATIC) != 0) {
            ref = make.Ident(sym);
            args = make.Idents(md.params);
        } else {
            ref = make.Select(make.Ident(md.params.head), sym);
            args = make.Idents(md.params.tail);
        }
        JCStatement stat;          // The statement accessing the private symbol.
        if (sym.kind == VAR) {
            // Normalize out all odd access codes by taking floor modulo 2:
            int acode1 = acode - (acode & 1);

            JCExpression expr;      // The access method's return value.
            switch (acode1) {
            case DEREFcode:
                expr = ref;
                break;
            case ASSIGNcode:
                expr = make.Assign(ref, args.head);
                break;
            case PREINCcode: case POSTINCcode: case PREDECcode: case POSTDECcode:
                expr = makeUnary(
                    ((acode1 - PREINCcode) >> 1) + JCTree.PREINC, ref);
                break;
            default:
                expr = make.Assignop(
                    treeTag(binaryAccessOperator(acode1)), ref, args.head);
                ((JCAssignOp) expr).operator = binaryAccessOperator(acode1);
            }
            stat = make.Return(expr.setType(sym.type));
        } else {
            stat = make.Call(make.App(ref, args));
        }
        md.body = make.Block(0, List.of(stat));

        // Make sure all parameters, result types and thrown exceptions
        // are accessible.
        for (List<JCVariableDecl> l = md.params; l.nonEmpty(); l = l.tail)
            l.head.vartype = access(l.head.vartype);
        md.restype = access(md.restype);
        for (List<JCExpression> l = md.thrown; l.nonEmpty(); l = l.tail)
            l.head = access(l.head);

        return md;
    }

    /** Construct definition of an access constructor.
     *  @param pos        The source code position of the definition.
     *  @param constr     The private constructor.
     *  @param accessor   The access method for the constructor.
     */
    JCTree accessConstructorDef(int pos, Symbol constr, MethodSymbol accessor) {
        make.at(pos);
        JCMethodDecl md = make.MethodDef(accessor,
                                      accessor.externalType(types),
                                      null);
        JCIdent callee = make.Ident(names._this);
        callee.sym = constr;
        callee.type = constr.type;
        md.body =
            make.Block(0, List.<JCStatement>of(
                make.Call(
                    make.App(
                        callee,
                        make.Idents(md.params.reverse().tail.reverse())))));
        return md;
    }

/**************************************************************************
 * Free variables proxies and this$n
 *************************************************************************/

    /** A scope containing all free variable proxies for currently translated
     *  class, as well as its this$n symbol (if needed).
     *  Proxy scopes are nested in the same way classes are.
     *  Inside a constructor, proxies and any this$n symbol are duplicated
     *  in an additional innermost scope, where they represent the constructor
     *  parameters.
     */
    Scope proxies;

    /** A stack containing the this$n field of the currently translated
     *  classes (if needed) in innermost first order.
     *  Inside a constructor, proxies and any this$n symbol are duplicated
     *  in an additional innermost scope, where they represent the constructor
     *  parameters.
     */
    List<VarSymbol> outerThisStack;

    /** The name of a free variable proxy.
     */
    Name proxyName(Name name) {
        return names.fromString("val" + target.syntheticNameChar() + name);
    }

    /** Proxy definitions for all free variables in given list, in reverse order.
     *  @param pos        The source code position of the definition.
     *  @param freevars   The free variables.
     *  @param owner      The class in which the definitions go.
     */
    List<JCVariableDecl> freevarDefs(int pos, List<VarSymbol> freevars, Symbol owner) {
        long flags = FINAL | SYNTHETIC;
        if (owner.kind == TYP &&
            target.usePrivateSyntheticFields())
            flags |= PRIVATE;
        List<JCVariableDecl> defs = List.nil();
        for (List<VarSymbol> l = freevars; l.nonEmpty(); l = l.tail) {
            VarSymbol v = l.head;
            VarSymbol proxy = new VarSymbol(
                flags, proxyName(v.name), v.erasure(types), owner);
            proxies.enter(proxy);
            JCVariableDecl vd = make.at(pos).VarDef(proxy, null);
            vd.vartype = access(vd.vartype);
            defs = defs.prepend(vd);
        }
        return defs;
    }

    /** The name of a this$n field
     *  @param type   The class referenced by the this$n field
     */
    Name outerThisName(Type type, Symbol owner) {
        Type t = type.getEnclosingType();
        int nestingLevel = 0;
        while (t.tag == CLASS) {
            t = t.getEnclosingType();
            nestingLevel++;
        }
        Name result = names.fromString("this" + target.syntheticNameChar() + nestingLevel);
        while (owner.kind == TYP && ((ClassSymbol)owner).members().lookup(result).scope != null)
            result = names.fromString(result.toString() + target.syntheticNameChar());
        return result;
    }

    /** Definition for this$n field.
     *  @param pos        The source code position of the definition.
     *  @param owner      The class in which the definition goes.
     */
    JCVariableDecl outerThisDef(int pos, Symbol owner) {
        long flags = FINAL | SYNTHETIC;
        if (owner.kind == TYP &&
            target.usePrivateSyntheticFields())
            flags |= PRIVATE;
        Type target = types.erasure(owner.enclClass().type.getEnclosingType());
        VarSymbol outerThis = new VarSymbol(
            flags, outerThisName(target, owner), target, owner);
        outerThisStack = outerThisStack.prepend(outerThis);
        JCVariableDecl vd = make.at(pos).VarDef(outerThis, null);
        vd.vartype = access(vd.vartype);
        return vd;
    }

    /** Return a list of trees that load the free variables in given list,
     *  in reverse order.
     *  @param pos          The source code position to be used for the trees.
     *  @param freevars     The list of free variables.
     */
    List<JCExpression> loadFreevars(DiagnosticPosition pos, List<VarSymbol> freevars) {
        List<JCExpression> args = List.nil();
        for (List<VarSymbol> l = freevars; l.nonEmpty(); l = l.tail)
            args = args.prepend(loadFreevar(pos, l.head));
        return args;
    }
//where
        JCExpression loadFreevar(DiagnosticPosition pos, VarSymbol v) {
            return access(v, make.at(pos).Ident(v), null, false);
        }

    /** Construct a tree simulating the expression <C.this>.
     *  @param pos           The source code position to be used for the tree.
     *  @param c             The qualifier class.
     */
    JCExpression makeThis(DiagnosticPosition pos, TypeSymbol c) {
        if (currentClass == c) {
            // in this case, `this' works fine
            return make.at(pos).This(c.erasure(types));
        } else {
            // need to go via this$n
            return makeOuterThis(pos, c);
        }
    }

    /** Construct a tree that represents the outer instance
     *  <C.this>. Never pick the current `this'.
     *  @param pos           The source code position to be used for the tree.
     *  @param c             The qualifier class.
     */
    JCExpression makeOuterThis(DiagnosticPosition pos, TypeSymbol c) {
        List<VarSymbol> ots = outerThisStack;
        if (ots.isEmpty()) {
            log.error(pos, "no.encl.instance.of.type.in.scope", c);
            assert false;
            return makeNull();
        }
        VarSymbol ot = ots.head;
        JCExpression tree = access(make.at(pos).Ident(ot));
        TypeSymbol otc = ot.type.tsym;
        while (otc != c) {
            do {
                ots = ots.tail;
                if (ots.isEmpty()) {
                    log.error(pos,
                              "no.encl.instance.of.type.in.scope",
                              c);
                    assert false; // should have been caught in Attr
                    return tree;
                }
                ot = ots.head;
            } while (ot.owner != otc);
            if (otc.owner.kind != PCK && !otc.hasOuterInstance()) {
                chk.earlyRefError(pos, c);
                assert false; // should have been caught in Attr
                return makeNull();
            }
            tree = access(make.at(pos).Select(tree, ot));
            otc = ot.type.tsym;
        }
        return tree;
    }

    /** Construct a tree that represents the closest outer instance
     *  <C.this> such that the given symbol is a member of C.
     *  @param pos           The source code position to be used for the tree.
     *  @param sym           The accessed symbol.
     *  @param preciseMatch  should we accept a type that is a subtype of
     *                       sym's owner, even if it doesn't contain sym
     *                       due to hiding, overriding, or non-inheritance
     *                       due to protection?
     */
    JCExpression makeOwnerThis(DiagnosticPosition pos, Symbol sym, boolean preciseMatch) {
        Symbol c = sym.owner;
        if (preciseMatch ? sym.isMemberOf(currentClass, types)
                         : currentClass.isSubClass(sym.owner, types)) {
            // in this case, `this' works fine
            return make.at(pos).This(c.erasure(types));
        } else {
            // need to go via this$n
            return makeOwnerThisN(pos, sym, preciseMatch);
        }
    }

    /**
     * Similar to makeOwnerThis but will never pick "this".
     */
    JCExpression makeOwnerThisN(DiagnosticPosition pos, Symbol sym, boolean preciseMatch) {
        Symbol c = sym.owner;
        List<VarSymbol> ots = outerThisStack;
        if (ots.isEmpty()) {
            log.error(pos, "no.encl.instance.of.type.in.scope", c);
            assert false;
            return makeNull();
        }
        VarSymbol ot = ots.head;
        JCExpression tree = access(make.at(pos).Ident(ot));
        TypeSymbol otc = ot.type.tsym;
        while (!(preciseMatch ? sym.isMemberOf(otc, types) : otc.isSubClass(sym.owner, types))) {
            do {
                ots = ots.tail;
                if (ots.isEmpty()) {
                    log.error(pos,
                        "no.encl.instance.of.type.in.scope",
                        c);
                    assert false;
                    return tree;
                }
                ot = ots.head;
            } while (ot.owner != otc);
            tree = access(make.at(pos).Select(tree, ot));
            otc = ot.type.tsym;
        }
        return tree;
    }

    /** Return tree simulating the assignment <this.name = name>, where
     *  name is the name of a free variable.
     */
    JCStatement initField(int pos, Name name) {
        Scope.Entry e = proxies.lookup(name);
        Symbol rhs = e.sym;
        assert rhs.owner.kind == MTH;
        Symbol lhs = e.next().sym;
        assert rhs.owner.owner == lhs.owner;
        make.at(pos);
        return
            make.Exec(
                make.Assign(
                    make.Select(make.This(lhs.owner.erasure(types)), lhs),
                    make.Ident(rhs)).setType(lhs.erasure(types)));
    }

    /** Return tree simulating the assignment <this.this$n = this$n>.
     */
    JCStatement initOuterThis(int pos) {
        VarSymbol rhs = outerThisStack.head;
        assert rhs.owner.kind == MTH;
        VarSymbol lhs = outerThisStack.tail.head;
        assert rhs.owner.owner == lhs.owner;
        make.at(pos);
        return
            make.Exec(
                make.Assign(
                    make.Select(make.This(lhs.owner.erasure(types)), lhs),
                    make.Ident(rhs)).setType(lhs.erasure(types)));
    }

/**************************************************************************
 * Code for .class
 *************************************************************************/

    /** Return the symbol of a class to contain a cache of
     *  compiler-generated statics such as class$ and the
     *  $assertionsDisabled flag.  We create an anonymous nested class
     *  (unless one already exists) and return its symbol.  However,
     *  for backward compatibility in 1.4 and earlier we use the
     *  top-level class itself.
     */
    private ClassSymbol outerCacheClass() {
        ClassSymbol clazz = outermostClassDef.sym;
        if ((clazz.flags() & INTERFACE) == 0 &&
            !target.useInnerCacheClass()) return clazz;
        Scope s = clazz.members();
        for (Scope.Entry e = s.elems; e != null; e = e.sibling)
            if (e.sym.kind == TYP &&
                e.sym.name == names.empty &&
                (e.sym.flags() & INTERFACE) == 0) return (ClassSymbol) e.sym;
        return makeEmptyClass(STATIC | SYNTHETIC, clazz);
    }

    /** Return symbol for "class$" method. If there is no method definition
     *  for class$, construct one as follows:
     *
     *    class class$(String x0) {
     *      try {
     *        return Class.forName(x0);
     *      } catch (ClassNotFoundException x1) {
     *        throw new NoClassDefFoundError(x1.getMessage());
     *      }
     *    }
     */
    private MethodSymbol classDollarSym(DiagnosticPosition pos) {
        ClassSymbol outerCacheClass = outerCacheClass();
        MethodSymbol classDollarSym =
            (MethodSymbol)lookupSynthetic(classDollar,
                                          outerCacheClass.members());
        if (classDollarSym == null) {
            classDollarSym = new MethodSymbol(
                STATIC | SYNTHETIC,
                classDollar,
                new MethodType(
                    List.of(syms.stringType),
                    types.erasure(syms.classType),
                    List.<Type>nil(),
                    syms.methodClass),
                outerCacheClass);
            enterSynthetic(pos, classDollarSym, outerCacheClass.members());

            JCMethodDecl md = make.MethodDef(classDollarSym, null);
            try {
                md.body = classDollarSymBody(pos, md);
            } catch (CompletionFailure ex) {
                md.body = make.Block(0, List.<JCStatement>nil());
                chk.completionError(pos, ex);
            }
            JCClassDecl outerCacheClassDef = classDef(outerCacheClass);
            outerCacheClassDef.defs = outerCacheClassDef.defs.prepend(md);
        }
        return classDollarSym;
    }

    /** Generate code for class$(String name). */
    JCBlock classDollarSymBody(DiagnosticPosition pos, JCMethodDecl md) {
        MethodSymbol classDollarSym = md.sym;
        ClassSymbol outerCacheClass = (ClassSymbol)classDollarSym.owner;

        JCBlock returnResult;

        // in 1.4.2 and above, we use
        // Class.forName(String name, boolean init, ClassLoader loader);
        // which requires we cache the current loader in cl$
        if (target.classLiteralsNoInit()) {
            // clsym = "private static ClassLoader cl$"
            VarSymbol clsym = new VarSymbol(STATIC|SYNTHETIC,
                                            names.fromString("cl" + target.syntheticNameChar()),
                                            syms.classLoaderType,
                                            outerCacheClass);
            enterSynthetic(pos, clsym, outerCacheClass.members());

            // emit "private static ClassLoader cl$;"
            JCVariableDecl cldef = make.VarDef(clsym, null);
            JCClassDecl outerCacheClassDef = classDef(outerCacheClass);
            outerCacheClassDef.defs = outerCacheClassDef.defs.prepend(cldef);

            // newcache := "new cache$1[0]"
            JCNewArray newcache = make.
                NewArray(make.Type(outerCacheClass.type),
                         List.<JCExpression>of(make.Literal(INT, 0).setType(syms.intType)),
                         null);
            newcache.type = new ArrayType(types.erasure(outerCacheClass.type),
                                          syms.arrayClass);

            // forNameSym := java.lang.Class.forName(
            //     String s,boolean init,ClassLoader loader)
            Symbol forNameSym = lookupMethod(make_pos, names.forName,
                                             types.erasure(syms.classType),
                                             List.of(syms.stringType,
                                                     syms.booleanType,
                                                     syms.classLoaderType));
            // clvalue := "(cl$ == null) ?
            // $newcache.getClass().getComponentType().getClassLoader() : cl$"
            JCExpression clvalue =
                make.Conditional(
                    makeBinary(JCTree.EQ, make.Ident(clsym), makeNull()),
                    make.Assign(
                        make.Ident(clsym),
                        makeCall(
                            makeCall(makeCall(newcache,
                                              names.getClass,
                                              List.<JCExpression>nil()),
                                     names.getComponentType,
                                     List.<JCExpression>nil()),
                            names.getClassLoader,
                            List.<JCExpression>nil())).setType(syms.classLoaderType),
                    make.Ident(clsym)).setType(syms.classLoaderType);

            // returnResult := "{ return Class.forName(param1, false, cl$); }"
            List<JCExpression> args = List.of(make.Ident(md.params.head.sym),
                                              makeLit(syms.booleanType, 0),
                                              clvalue);
            returnResult = make.
                Block(0, List.<JCStatement>of(make.
                              Call(make. // return
                                   App(make.
                                       Ident(forNameSym), args))));
        } else {
            // forNameSym := java.lang.Class.forName(String s)
            Symbol forNameSym = lookupMethod(make_pos,
                                             names.forName,
                                             types.erasure(syms.classType),
                                             List.of(syms.stringType));
            // returnResult := "{ return Class.forName(param1); }"
            returnResult = make.
                Block(0, List.of(make.
                          Call(make. // return
                              App(make.
                                  QualIdent(forNameSym),
                                  List.<JCExpression>of(make.
                                                        Ident(md.params.
                                                              head.sym))))));
        }

        // catchParam := ClassNotFoundException e1
        VarSymbol catchParam =
            new VarSymbol(0, make.paramName(1),
                          syms.classNotFoundExceptionType,
                          classDollarSym);

        JCStatement rethrow;
        if (target.hasInitCause()) {
            // rethrow = "throw new NoClassDefFoundError().initCause(e);
            JCTree throwExpr =
                makeCall(makeNewClass(syms.noClassDefFoundErrorType,
                                      List.<JCExpression>nil()),
                         names.initCause,
                         List.<JCExpression>of(make.Ident(catchParam)));
            rethrow = make.Throw(throwExpr);
        } else {
            // getMessageSym := ClassNotFoundException.getMessage()
            Symbol getMessageSym = lookupMethod(make_pos,
                                                names.getMessage,
                                                syms.classNotFoundExceptionType,
                                                List.<Type>nil());
            // rethrow = "throw new NoClassDefFoundError(e.getMessage());"
            rethrow = make.
                Throw(makeNewClass(syms.noClassDefFoundErrorType,
                          List.<JCExpression>of(make.App(make.Select(make.Ident(catchParam),
                                                                     getMessageSym),
                                                         List.<JCExpression>nil()))));
        }

        // rethrowStmt := "( $rethrow )"
        JCBlock rethrowStmt = make.Block(0, List.of(rethrow));

        // catchBlock := "catch ($catchParam) $rethrowStmt"
        JCCatch catchBlock = make.Catch(make.VarDef(catchParam, null),
                                      rethrowStmt);

        // tryCatch := "try $returnResult $catchBlock"
        JCStatement tryCatch = make.Try(returnResult,
                                        List.of(catchBlock), null);

        return make.Block(0, List.of(tryCatch));
    }
    // where
        /** Create an attributed tree of the form left.name(). */
        private JCMethodInvocation makeCall(JCExpression left, Name name, List<JCExpression> args) {
            assert left.type != null;
            Symbol funcsym = lookupMethod(make_pos, name, left.type,
                                          TreeInfo.types(args));
            return make.App(make.Select(left, funcsym), args);
        }

    /** The Name Of The variable to cache T.class values.
     *  @param sig      The signature of type T.
     */
    private Name cacheName(String sig) {
        StringBuffer buf = new StringBuffer();
        if (sig.startsWith("[")) {
            buf = buf.append("array");
            while (sig.startsWith("[")) {
                buf = buf.append(target.syntheticNameChar());
                sig = sig.substring(1);
            }
            if (sig.startsWith("L")) {
                sig = sig.substring(0, sig.length() - 1);
            }
        } else {
            buf = buf.append("class" + target.syntheticNameChar());
        }
        buf = buf.append(sig.replace('.', target.syntheticNameChar()));
        return names.fromString(buf.toString());
    }

    /** The variable symbol that caches T.class values.
     *  If none exists yet, create a definition.
     *  @param sig      The signature of type T.
     *  @param pos      The position to report diagnostics, if any.
     */
    private VarSymbol cacheSym(DiagnosticPosition pos, String sig) {
        ClassSymbol outerCacheClass = outerCacheClass();
        Name cname = cacheName(sig);
        VarSymbol cacheSym =
            (VarSymbol)lookupSynthetic(cname, outerCacheClass.members());
        if (cacheSym == null) {
            cacheSym = new VarSymbol(
                STATIC | SYNTHETIC, cname, types.erasure(syms.classType), outerCacheClass);
            enterSynthetic(pos, cacheSym, outerCacheClass.members());

            JCVariableDecl cacheDef = make.VarDef(cacheSym, null);
            JCClassDecl outerCacheClassDef = classDef(outerCacheClass);
            outerCacheClassDef.defs = outerCacheClassDef.defs.prepend(cacheDef);
        }
        return cacheSym;
    }

    /** The tree simulating a T.class expression.
     *  @param clazz      The tree identifying type T.
     */
    private JCExpression classOf(JCTree clazz) {
        return classOfType(clazz.type, clazz.pos());
    }

    private JCExpression classOfType(Type type, DiagnosticPosition pos) {
        switch (type.tag) {
        case BYTE: case SHORT: case CHAR: case INT: case LONG: case FLOAT:
        case DOUBLE: case BOOLEAN: case VOID:
            // replace with <BoxedClass>.TYPE
            ClassSymbol c = types.boxedClass(type);
            Symbol typeSym =
                rs.access(
                    rs.findIdentInType(attrEnv, c.type, names.TYPE, VAR),
                    pos, c.type, names.TYPE, true);
            if (typeSym.kind == VAR)
                ((VarSymbol)typeSym).getConstValue(); // ensure initializer is evaluated
            return make.QualIdent(typeSym);
        case CLASS: case ARRAY:
            if (target.hasClassLiterals()) {
                VarSymbol sym = new VarSymbol(
                        STATIC | PUBLIC | FINAL, names._class,
                        syms.classType, type.tsym);
                return make_at(pos).Select(make.Type(type), sym);
            }
            // replace with <cache == null ? cache = class$(tsig) : cache>
            // where
            //  - <tsig>  is the type signature of T,
            //  - <cache> is the cache variable for tsig.
            String sig =
                writer.xClassName(type).toString().replace('/', '.');
            Symbol cs = cacheSym(pos, sig);
            return make_at(pos).Conditional(
                makeBinary(JCTree.EQ, make.Ident(cs), makeNull()),
                make.Assign(
                    make.Ident(cs),
                    make.App(
                        make.Ident(classDollarSym(pos)),
                        List.<JCExpression>of(make.Literal(CLASS, sig)
                                              .setType(syms.stringType))))
                .setType(types.erasure(syms.classType)),
                make.Ident(cs)).setType(types.erasure(syms.classType));
        default:
            throw new AssertionError();
        }
    }

/**************************************************************************
 * Code for enabling/disabling assertions.
 *************************************************************************/

    // This code is not particularly robust if the user has
    // previously declared a member named '$assertionsDisabled'.
    // The same faulty idiom also appears in the translation of
    // class literals above.  We should report an error if a
    // previous declaration is not synthetic.

    private JCExpression assertFlagTest(DiagnosticPosition pos) {
        // Outermost class may be either true class or an interface.
        ClassSymbol outermostClass = outermostClassDef.sym;

        // note that this is a class, as an interface can't contain a statement.
        ClassSymbol container = currentClass;

        VarSymbol assertDisabledSym =
            (VarSymbol)lookupSynthetic(dollarAssertionsDisabled,
                                       container.members());
        if (assertDisabledSym == null) {
            assertDisabledSym =
                new VarSymbol(STATIC | FINAL | SYNTHETIC,
                              dollarAssertionsDisabled,
                              syms.booleanType,
                              container);
            enterSynthetic(pos, assertDisabledSym, container.members());
            Symbol desiredAssertionStatusSym = lookupMethod(pos,
                                                            names.desiredAssertionStatus,
                                                            types.erasure(syms.classType),
                                                            List.<Type>nil());
            JCClassDecl containerDef = classDef(container);
            make_at(containerDef.pos());
            JCExpression notStatus = makeUnary(JCTree.NOT, make.App(make.Select(
                    classOfType(types.erasure(outermostClass.type),
                                containerDef.pos()),
                    desiredAssertionStatusSym)));
            JCVariableDecl assertDisabledDef = make.VarDef(assertDisabledSym,
                                                   notStatus);
            containerDef.defs = containerDef.defs.prepend(assertDisabledDef);
        }
        make_at(pos);
        return makeUnary(JCTree.NOT, make.Ident(assertDisabledSym));
    }


/**************************************************************************
 * Building blocks for let expressions
 *************************************************************************/

    interface TreeBuilder {
        JCTree build(JCTree arg);
    }

    /** Construct an expression using the builder, with the given rval
     *  expression as an argument to the builder.  However, the rval
     *  expression must be computed only once, even if used multiple
     *  times in the result of the builder.  We do that by
     *  constructing a "let" expression that saves the rvalue into a
     *  temporary variable and then uses the temporary variable in
     *  place of the expression built by the builder.  The complete
     *  resulting expression is of the form
     *  <pre>
     *    (let <b>TYPE</b> <b>TEMP</b> = <b>RVAL</b>;
     *     in (<b>BUILDER</b>(<b>TEMP</b>)))
     *  </pre>
     *  where <code><b>TEMP</b></code> is a newly declared variable
     *  in the let expression.
     */
    JCTree abstractRval(JCTree rval, Type type, TreeBuilder builder) {
        rval = TreeInfo.skipParens(rval);
        switch (rval.getTag()) {
        case JCTree.LITERAL:
            return builder.build(rval);
        case JCTree.IDENT:
            JCIdent id = (JCIdent) rval;
            if ((id.sym.flags() & FINAL) != 0 && id.sym.owner.kind == MTH)
                return builder.build(rval);
        }
        VarSymbol var =
            new VarSymbol(FINAL|SYNTHETIC,
                          names.fromString(
                                          target.syntheticNameChar()
                                          + "" + rval.hashCode()),
                                      type,
                                      currentMethodSym);
        rval = convert(rval,type);
        JCVariableDecl def = make.VarDef(var, (JCExpression)rval); // XXX cast
        JCTree built = builder.build(make.Ident(var));
        JCTree res = make.LetExpr(def, built);
        res.type = built.type;
        return res;
    }

    // same as above, with the type of the temporary variable computed
    JCTree abstractRval(JCTree rval, TreeBuilder builder) {
        return abstractRval(rval, rval.type, builder);
    }

    // same as above, but for an expression that may be used as either
    // an rvalue or an lvalue.  This requires special handling for
    // Select expressions, where we place the left-hand-side of the
    // select in a temporary, and for Indexed expressions, where we
    // place both the indexed expression and the index value in temps.
    JCTree abstractLval(JCTree lval, final TreeBuilder builder) {
        lval = TreeInfo.skipParens(lval);
        switch (lval.getTag()) {
        case JCTree.IDENT:
            return builder.build(lval);
        case JCTree.SELECT: {
            final JCFieldAccess s = (JCFieldAccess)lval;
            JCTree selected = TreeInfo.skipParens(s.selected);
            Symbol lid = TreeInfo.symbol(s.selected);
            if (lid != null && lid.kind == TYP) return builder.build(lval);
            return abstractRval(s.selected, new TreeBuilder() {
                    public JCTree build(final JCTree selected) {
                        return builder.build(make.Select((JCExpression)selected, s.sym));
                    }
                });
        }
        case JCTree.INDEXED: {
            final JCArrayAccess i = (JCArrayAccess)lval;
            return abstractRval(i.indexed, new TreeBuilder() {
                    public JCTree build(final JCTree indexed) {
                        return abstractRval(i.index, syms.intType, new TreeBuilder() {
                                public JCTree build(final JCTree index) {
                                    JCTree newLval = make.Indexed((JCExpression)indexed,
                                                                (JCExpression)index);
                                    newLval.setType(i.type);
                                    return builder.build(newLval);
                                }
                            });
                    }
                });
        }
        case JCTree.TYPECAST: {
            return abstractLval(((JCTypeCast)lval).expr, builder);
        }
        }
        throw new AssertionError(lval);
    }

    // evaluate and discard the first expression, then evaluate the second.
    JCTree makeComma(final JCTree expr1, final JCTree expr2) {
        return abstractRval(expr1, new TreeBuilder() {
                public JCTree build(final JCTree discarded) {
                    return expr2;
                }
            });
    }

/**************************************************************************
 * Translation methods
 *************************************************************************/

    /** Visitor argument: enclosing operator node.
     */
    private JCExpression enclOp;

    /** Visitor method: Translate a single node.
     *  Attach the source position from the old tree to its replacement tree.
     */
    public <T extends JCTree> T translate(T tree) {
        if (tree == null) {
            return null;
        } else {
            make_at(tree.pos());
            T result = super.translate(tree);
            if (endPositions != null && result != tree) {
                Integer endPos = endPositions.remove(tree);
                if (endPos != null) endPositions.put(result, endPos);
            }
            return result;
        }
    }

    /** Visitor method: Translate a single node, boxing or unboxing if needed.
     */
    public <T extends JCTree> T translate(T tree, Type type) {
        return (tree == null) ? null : boxIfNeeded(translate(tree), type);
    }

    /** Visitor method: Translate tree.
     */
    public <T extends JCTree> T translate(T tree, JCExpression enclOp) {
        JCExpression prevEnclOp = this.enclOp;
        this.enclOp = enclOp;
        T res = translate(tree);
        this.enclOp = prevEnclOp;
        return res;
    }

    /** Visitor method: Translate list of trees.
     */
    public <T extends JCTree> List<T> translate(List<T> trees, JCExpression enclOp) {
        JCExpression prevEnclOp = this.enclOp;
        this.enclOp = enclOp;
        List<T> res = translate(trees);
        this.enclOp = prevEnclOp;
        return res;
    }

    /** Visitor method: Translate list of trees.
     */
    public <T extends JCTree> List<T> translate(List<T> trees, Type type) {
        if (trees == null) return null;
        for (List<T> l = trees; l.nonEmpty(); l = l.tail)
            l.head = translate(l.head, type);
        return trees;
    }

    public void visitTopLevel(JCCompilationUnit tree) {
        if (tree.packageAnnotations.nonEmpty()) {
            Name name = names.package_info;
            long flags = Flags.ABSTRACT | Flags.INTERFACE;
            if (target.isPackageInfoSynthetic())
                // package-info is marked SYNTHETIC in JDK 1.6 and later releases
                flags = flags | Flags.SYNTHETIC;
            JCClassDecl packageAnnotationsClass
                = make.ClassDef(make.Modifiers(flags,
                                               tree.packageAnnotations),
                                name, List.<JCTypeParameter>nil(),
                                null, List.<JCExpression>nil(), List.<JCTree>nil());
            ClassSymbol c = tree.packge.package_info;
            c.flags_field |= flags;
            c.attributes_field = tree.packge.attributes_field;
            ClassType ctype = (ClassType) c.type;
            ctype.supertype_field = syms.objectType;
            ctype.interfaces_field = List.nil();
            packageAnnotationsClass.sym = c;

            translated.append(packageAnnotationsClass);
        }
    }

    public void visitClassDef(JCClassDecl tree) {
        ClassSymbol currentClassPrev = currentClass;
        MethodSymbol currentMethodSymPrev = currentMethodSym;
        currentClass = tree.sym;
        currentMethodSym = null;
        classdefs.put(currentClass, tree);

        proxies = proxies.dup(currentClass);
        List<VarSymbol> prevOuterThisStack = outerThisStack;

        // If this is an enum definition
        if ((tree.mods.flags & ENUM) != 0 &&
            (types.supertype(currentClass.type).tsym.flags() & ENUM) == 0)
            visitEnumDef(tree);

        // If this is a nested class, define a this$n field for
        // it and add to proxies.
        JCVariableDecl otdef = null;
        if (currentClass.hasOuterInstance())
            otdef = outerThisDef(tree.pos, currentClass);

        // If this is a local class, define proxies for all its free variables.
        List<JCVariableDecl> fvdefs = freevarDefs(
            tree.pos, freevars(currentClass), currentClass);

        // Recursively translate superclass, interfaces.
        tree.extending = translate(tree.extending);
        tree.implementing = translate(tree.implementing);

        // Recursively translate members, taking into account that new members
        // might be created during the translation and prepended to the member
        // list `tree.defs'.
        List<JCTree> seen = List.nil();
        while (tree.defs != seen) {
            List<JCTree> unseen = tree.defs;
            for (List<JCTree> l = unseen; l.nonEmpty() && l != seen; l = l.tail) {
                JCTree outermostMemberDefPrev = outermostMemberDef;
                if (outermostMemberDefPrev == null) outermostMemberDef = l.head;
                l.head = translate(l.head);
                outermostMemberDef = outermostMemberDefPrev;
            }
            seen = unseen;
        }

        // Convert a protected modifier to public, mask static modifier.
        if ((tree.mods.flags & PROTECTED) != 0) tree.mods.flags |= PUBLIC;
        tree.mods.flags &= ClassFlags;

        // Convert name to flat representation, replacing '.' by '$'.
        tree.name = Convert.shortName(currentClass.flatName());

        // Add this$n and free variables proxy definitions to class.
        for (List<JCVariableDecl> l = fvdefs; l.nonEmpty(); l = l.tail) {
            tree.defs = tree.defs.prepend(l.head);
            enterSynthetic(tree.pos(), l.head.sym, currentClass.members());
        }
        if (currentClass.hasOuterInstance()) {
            tree.defs = tree.defs.prepend(otdef);
            enterSynthetic(tree.pos(), otdef.sym, currentClass.members());
        }

        proxies = proxies.leave();
        outerThisStack = prevOuterThisStack;

        // Append translated tree to `translated' queue.
        translated.append(tree);

        currentClass = currentClassPrev;
        currentMethodSym = currentMethodSymPrev;

        // Return empty block {} as a placeholder for an inner class.
        result = make_at(tree.pos()).Block(0, List.<JCStatement>nil());
    }

    /** Translate an enum class. */
    private void visitEnumDef(JCClassDecl tree) {
        make_at(tree.pos());

        // add the supertype, if needed
        if (tree.extending == null)
            tree.extending = make.Type(types.supertype(tree.type));

        // classOfType adds a cache field to tree.defs unless
        // target.hasClassLiterals().
        JCExpression e_class = classOfType(tree.sym.type, tree.pos()).
            setType(types.erasure(syms.classType));

        // process each enumeration constant, adding implicit constructor parameters
        int nextOrdinal = 0;
        ListBuffer<JCExpression> values = new ListBuffer<JCExpression>();
        ListBuffer<JCTree> enumDefs = new ListBuffer<JCTree>();
        ListBuffer<JCTree> otherDefs = new ListBuffer<JCTree>();
        for (List<JCTree> defs = tree.defs;
             defs.nonEmpty();
             defs=defs.tail) {
            if (defs.head.getTag() == JCTree.VARDEF && (((JCVariableDecl) defs.head).mods.flags & ENUM) != 0) {
                JCVariableDecl var = (JCVariableDecl)defs.head;
                visitEnumConstantDef(var, nextOrdinal++);
                values.append(make.QualIdent(var.sym));
                enumDefs.append(var);
            } else {
                otherDefs.append(defs.head);
            }
        }

        // private static final T[] #VALUES = { a, b, c };
        Name valuesName = names.fromString(target.syntheticNameChar() + "VALUES");
        while (tree.sym.members().lookup(valuesName).scope != null) // avoid name clash
            valuesName = names.fromString(valuesName + "" + target.syntheticNameChar());
        Type arrayType = new ArrayType(types.erasure(tree.type), syms.arrayClass);
        VarSymbol valuesVar = new VarSymbol(PRIVATE|FINAL|STATIC|SYNTHETIC,
                                            valuesName,
                                            arrayType,
                                            tree.type.tsym);
        JCNewArray newArray = make.NewArray(make.Type(types.erasure(tree.type)),
                                          List.<JCExpression>nil(),
                                          values.toList());
        newArray.type = arrayType;
        enumDefs.append(make.VarDef(valuesVar, newArray));
        tree.sym.members().enter(valuesVar);

        Symbol valuesSym = lookupMethod(tree.pos(), names.values,
                                        tree.type, List.<Type>nil());
        List<JCStatement> valuesBody;
        if (useClone()) {
            // return (T[]) $VALUES.clone();
            JCTypeCast valuesResult =
                make.TypeCast(valuesSym.type.getReturnType(),
                              make.App(make.Select(make.Ident(valuesVar),
                                                   syms.arrayCloneMethod)));
            valuesBody = List.<JCStatement>of(make.Return(valuesResult));
        } else {
            // template: T[] $result = new T[$values.length];
            Name resultName = names.fromString(target.syntheticNameChar() + "result");
            while (tree.sym.members().lookup(resultName).scope != null) // avoid name clash
                resultName = names.fromString(resultName + "" + target.syntheticNameChar());
            VarSymbol resultVar = new VarSymbol(FINAL|SYNTHETIC,
                                                resultName,
                                                arrayType,
                                                valuesSym);
            JCNewArray resultArray = make.NewArray(make.Type(types.erasure(tree.type)),
                                  List.of(make.Select(make.Ident(valuesVar), syms.lengthVar)),
                                  null);
            resultArray.type = arrayType;
            JCVariableDecl decl = make.VarDef(resultVar, resultArray);

            // template: System.arraycopy($VALUES, 0, $result, 0, $VALUES.length);
            if (systemArraycopyMethod == null) {
                systemArraycopyMethod =
                    new MethodSymbol(PUBLIC | STATIC,
                                     names.fromString("arraycopy"),
                                     new MethodType(List.<Type>of(syms.objectType,
                                                            syms.intType,
                                                            syms.objectType,
                                                            syms.intType,
                                                            syms.intType),
                                                    syms.voidType,
                                                    List.<Type>nil(),
                                                    syms.methodClass),
                                     syms.systemType.tsym);
            }
            JCStatement copy =
                make.Exec(make.App(make.Select(make.Ident(syms.systemType.tsym),
                                               systemArraycopyMethod),
                          List.of(make.Ident(valuesVar), make.Literal(0),
                                  make.Ident(resultVar), make.Literal(0),
                                  make.Select(make.Ident(valuesVar), syms.lengthVar))));

            // template: return $result;
            JCStatement ret = make.Return(make.Ident(resultVar));
            valuesBody = List.<JCStatement>of(decl, copy, ret);
        }

        JCMethodDecl valuesDef =
             make.MethodDef((MethodSymbol)valuesSym, make.Block(0, valuesBody));

        enumDefs.append(valuesDef);

        if (debugLower)
            System.err.println(tree.sym + ".valuesDef = " + valuesDef);

        /** The template for the following code is:
         *
         *     public static E valueOf(String name) {
         *         return (E)Enum.valueOf(E.class, name);
         *     }
         *
         *  where E is tree.sym
         */
        MethodSymbol valueOfSym = lookupMethod(tree.pos(),
                         names.valueOf,
                         tree.sym.type,
                         List.of(syms.stringType));
        assert (valueOfSym.flags() & STATIC) != 0;
        VarSymbol nameArgSym = valueOfSym.params.head;
        JCIdent nameVal = make.Ident(nameArgSym);
        JCStatement enum_ValueOf =
            make.Return(make.TypeCast(tree.sym.type,
                                      makeCall(make.Ident(syms.enumSym),
                                               names.valueOf,
                                               List.of(e_class, nameVal))));
        JCMethodDecl valueOf = make.MethodDef(valueOfSym,
                                           make.Block(0, List.of(enum_ValueOf)));
        nameVal.sym = valueOf.params.head.sym;
        if (debugLower)
            System.err.println(tree.sym + ".valueOf = " + valueOf);
        enumDefs.append(valueOf);

        enumDefs.appendList(otherDefs.toList());
        tree.defs = enumDefs.toList();

        // Add the necessary members for the EnumCompatibleMode
        if (target.compilerBootstrap(tree.sym)) {
            addEnumCompatibleMembers(tree);
        }
    }
        // where
        private MethodSymbol systemArraycopyMethod;
        private boolean useClone() {
            try {
                Scope.Entry e = syms.objectType.tsym.members().lookup(names.clone);
                return (e.sym != null);
            }
            catch (CompletionFailure e) {
                return false;
            }
        }

    /** Translate an enumeration constant and its initializer. */
    private void visitEnumConstantDef(JCVariableDecl var, int ordinal) {
        JCNewClass varDef = (JCNewClass)var.init;
        varDef.args = varDef.args.
            prepend(makeLit(syms.intType, ordinal)).
            prepend(makeLit(syms.stringType, var.name.toString()));
    }

    public void visitMethodDef(JCMethodDecl tree) {
        if (tree.name == names.init && (currentClass.flags_field&ENUM) != 0) {
            // Add "String $enum$name, int $enum$ordinal" to the beginning of the
            // argument list for each constructor of an enum.
            JCVariableDecl nameParam = make_at(tree.pos()).
                Param(names.fromString(target.syntheticNameChar() +
                                       "enum" + target.syntheticNameChar() + "name"),
                      syms.stringType, tree.sym);
            nameParam.mods.flags |= SYNTHETIC; nameParam.sym.flags_field |= SYNTHETIC;

            JCVariableDecl ordParam = make.
                Param(names.fromString(target.syntheticNameChar() +
                                       "enum" + target.syntheticNameChar() +
                                       "ordinal"),
                      syms.intType, tree.sym);
            ordParam.mods.flags |= SYNTHETIC; ordParam.sym.flags_field |= SYNTHETIC;

            tree.params = tree.params.prepend(ordParam).prepend(nameParam);

            MethodSymbol m = tree.sym;
            Type olderasure = m.erasure(types);
            m.erasure_field = new MethodType(
                olderasure.getParameterTypes().prepend(syms.intType).prepend(syms.stringType),
                olderasure.getReturnType(),
                olderasure.getThrownTypes(),
                syms.methodClass);

            if (target.compilerBootstrap(m.owner)) {
                // Initialize synthetic name field
                Symbol nameVarSym = lookupSynthetic(names.fromString("$name"),
                                                    tree.sym.owner.members());
                JCIdent nameIdent = make.Ident(nameParam.sym);
                JCIdent id1 = make.Ident(nameVarSym);
                JCAssign newAssign = make.Assign(id1, nameIdent);
                newAssign.type = id1.type;
                JCExpressionStatement nameAssign = make.Exec(newAssign);
                nameAssign.type = id1.type;
                tree.body.stats = tree.body.stats.prepend(nameAssign);

                // Initialize synthetic ordinal field
                Symbol ordinalVarSym = lookupSynthetic(names.fromString("$ordinal"),
                                                       tree.sym.owner.members());
                JCIdent ordIdent = make.Ident(ordParam.sym);
                id1 = make.Ident(ordinalVarSym);
                newAssign = make.Assign(id1, ordIdent);
                newAssign.type = id1.type;
                JCExpressionStatement ordinalAssign = make.Exec(newAssign);
                ordinalAssign.type = id1.type;
                tree.body.stats = tree.body.stats.prepend(ordinalAssign);
            }
        }

        JCMethodDecl prevMethodDef = currentMethodDef;
        MethodSymbol prevMethodSym = currentMethodSym;
        try {
            currentMethodDef = tree;
            currentMethodSym = tree.sym;
            visitMethodDefInternal(tree);
        } finally {
            currentMethodDef = prevMethodDef;
            currentMethodSym = prevMethodSym;
        }
    }
    //where
    private void visitMethodDefInternal(JCMethodDecl tree) {
        if (tree.name == names.init &&
            (currentClass.isInner() ||
             (currentClass.owner.kind & (VAR | MTH)) != 0)) {
            // We are seeing a constructor of an inner class.
            MethodSymbol m = tree.sym;

            // Push a new proxy scope for constructor parameters.
            // and create definitions for any this$n and proxy parameters.
            proxies = proxies.dup(m);
            List<VarSymbol> prevOuterThisStack = outerThisStack;
            List<VarSymbol> fvs = freevars(currentClass);
            JCVariableDecl otdef = null;
            if (currentClass.hasOuterInstance())
                otdef = outerThisDef(tree.pos, m);
            List<JCVariableDecl> fvdefs = freevarDefs(tree.pos, fvs, m);

            // Recursively translate result type, parameters and thrown list.
            tree.restype = translate(tree.restype);
            tree.params = translateVarDefs(tree.params);
            tree.thrown = translate(tree.thrown);

            // when compiling stubs, don't process body
            if (tree.body == null) {
                result = tree;
                return;
            }

            // Add this$n (if needed) in front of and free variables behind
            // constructor parameter list.
            tree.params = tree.params.appendList(fvdefs);
            if (currentClass.hasOuterInstance())
                tree.params = tree.params.prepend(otdef);

            // If this is an initial constructor, i.e., it does not start with
            // this(...), insert initializers for this$n and proxies
            // before (pre-1.4, after) the call to superclass constructor.
            JCStatement selfCall = translate(tree.body.stats.head);

            List<JCStatement> added = List.nil();
            if (fvs.nonEmpty()) {
                List<Type> addedargtypes = List.nil();
                for (List<VarSymbol> l = fvs; l.nonEmpty(); l = l.tail) {
                    if (TreeInfo.isInitialConstructor(tree))
                        added = added.prepend(
                            initField(tree.body.pos, proxyName(l.head.name)));
                    addedargtypes = addedargtypes.prepend(l.head.erasure(types));
                }
                Type olderasure = m.erasure(types);
                m.erasure_field = new MethodType(
                    olderasure.getParameterTypes().appendList(addedargtypes),
                    olderasure.getReturnType(),
                    olderasure.getThrownTypes(),
                    syms.methodClass);
            }
            if (currentClass.hasOuterInstance() &&
                TreeInfo.isInitialConstructor(tree))
            {
                added = added.prepend(initOuterThis(tree.body.pos));
            }

            // pop local variables from proxy stack
            proxies = proxies.leave();

            // recursively translate following local statements and
            // combine with this- or super-call
            List<JCStatement> stats = translate(tree.body.stats.tail);
            if (target.initializeFieldsBeforeSuper())
                tree.body.stats = stats.prepend(selfCall).prependList(added);
            else
                tree.body.stats = stats.prependList(added).prepend(selfCall);

            outerThisStack = prevOuterThisStack;
        } else {
            super.visitMethodDef(tree);
        }
        result = tree;
    }

    public void visitAnnotatedType(JCAnnotatedType tree) {
        tree.underlyingType = translate(tree.underlyingType);
        result = tree.underlyingType;
    }

    public void visitTypeCast(JCTypeCast tree) {
        tree.clazz = translate(tree.clazz);
        if (tree.type.isPrimitive() != tree.expr.type.isPrimitive())
            tree.expr = translate(tree.expr, tree.type);
        else
            tree.expr = translate(tree.expr);
        result = tree;
    }

    public void visitNewClass(JCNewClass tree) {
        ClassSymbol c = (ClassSymbol)tree.constructor.owner;

        // Box arguments, if necessary
        boolean isEnum = (tree.constructor.owner.flags() & ENUM) != 0;
        List<Type> argTypes = tree.constructor.type.getParameterTypes();
        if (isEnum) argTypes = argTypes.prepend(syms.intType).prepend(syms.stringType);
        tree.args = boxArgs(argTypes, tree.args, tree.varargsElement);
        tree.varargsElement = null;

        // If created class is local, add free variables after
        // explicit constructor arguments.
        if ((c.owner.kind & (VAR | MTH)) != 0) {
            tree.args = tree.args.appendList(loadFreevars(tree.pos(), freevars(c)));
        }

        // If an access constructor is used, append null as a last argument.
        Symbol constructor = accessConstructor(tree.pos(), tree.constructor);
        if (constructor != tree.constructor) {
            tree.args = tree.args.append(makeNull());
            tree.constructor = constructor;
        }

        // If created class has an outer instance, and new is qualified, pass
        // qualifier as first argument. If new is not qualified, pass the
        // correct outer instance as first argument.
        if (c.hasOuterInstance()) {
            JCExpression thisArg;
            if (tree.encl != null) {
                thisArg = attr.makeNullCheck(translate(tree.encl));
                thisArg.type = tree.encl.type;
            } else if ((c.owner.kind & (MTH | VAR)) != 0) {
                // local class
                thisArg = makeThis(tree.pos(), c.type.getEnclosingType().tsym);
            } else {
                // nested class
                thisArg = makeOwnerThis(tree.pos(), c, false);
            }
            tree.args = tree.args.prepend(thisArg);
        }
        tree.encl = null;

        // If we have an anonymous class, create its flat version, rather
        // than the class or interface following new.
        if (tree.def != null) {
            translate(tree.def);
            tree.clazz = access(make_at(tree.clazz.pos()).Ident(tree.def.sym));
            tree.def = null;
        } else {
            tree.clazz = access(c, tree.clazz, enclOp, false);
        }
        result = tree;
    }

    // Simplify conditionals with known constant controlling expressions.
    // This allows us to avoid generating supporting declarations for
    // the dead code, which will not be eliminated during code generation.
    // Note that Flow.isFalse and Flow.isTrue only return true
    // for constant expressions in the sense of JLS 15.27, which
    // are guaranteed to have no side-effects.  More aggressive
    // constant propagation would require that we take care to
    // preserve possible side-effects in the condition expression.

    /** Visitor method for conditional expressions.
     */
    public void visitConditional(JCConditional tree) {
        JCTree cond = tree.cond = translate(tree.cond, syms.booleanType);
        if (cond.type.isTrue()) {
            result = convert(translate(tree.truepart, tree.type), tree.type);
        } else if (cond.type.isFalse()) {
            result = convert(translate(tree.falsepart, tree.type), tree.type);
        } else {
            // Condition is not a compile-time constant.
            tree.truepart = translate(tree.truepart, tree.type);
            tree.falsepart = translate(tree.falsepart, tree.type);
            result = tree;
        }
    }
//where
        private JCTree convert(JCTree tree, Type pt) {
            if (tree.type == pt) return tree;
            JCTree result = make_at(tree.pos()).TypeCast(make.Type(pt), (JCExpression)tree);
            result.type = (tree.type.constValue() != null) ? cfolder.coerce(tree.type, pt)
                                                           : pt;
            return result;
        }

    /** Visitor method for if statements.
     */
    public void visitIf(JCIf tree) {
        JCTree cond = tree.cond = translate(tree.cond, syms.booleanType);
        if (cond.type.isTrue()) {
            result = translate(tree.thenpart);
        } else if (cond.type.isFalse()) {
            if (tree.elsepart != null) {
                result = translate(tree.elsepart);
            } else {
                result = make.Skip();
            }
        } else {
            // Condition is not a compile-time constant.
            tree.thenpart = translate(tree.thenpart);
            tree.elsepart = translate(tree.elsepart);
            result = tree;
        }
    }

    /** Visitor method for assert statements. Translate them away.
     */
    public void visitAssert(JCAssert tree) {
        DiagnosticPosition detailPos = (tree.detail == null) ? tree.pos() : tree.detail.pos();
        tree.cond = translate(tree.cond, syms.booleanType);
        if (!tree.cond.type.isTrue()) {
            JCExpression cond = assertFlagTest(tree.pos());
            List<JCExpression> exnArgs = (tree.detail == null) ?
                List.<JCExpression>nil() : List.of(translate(tree.detail));
            if (!tree.cond.type.isFalse()) {
                cond = makeBinary
                    (JCTree.AND,
                     cond,
                     makeUnary(JCTree.NOT, tree.cond));
            }
            result =
                make.If(cond,
                        make_at(detailPos).
                           Throw(makeNewClass(syms.assertionErrorType, exnArgs)),
                        null);
        } else {
            result = make.Skip();
        }
    }

    public void visitApply(JCMethodInvocation tree) {
        Symbol meth = TreeInfo.symbol(tree.meth);
        List<Type> argtypes = meth.type.getParameterTypes();
        if (allowEnums &&
            meth.name==names.init &&
            meth.owner == syms.enumSym)
            argtypes = argtypes.tail.tail;
        tree.args = boxArgs(argtypes, tree.args, tree.varargsElement);
        tree.varargsElement = null;
        Name methName = TreeInfo.name(tree.meth);
        if (meth.name==names.init) {
            // We are seeing a this(...) or super(...) constructor call.
            // If an access constructor is used, append null as a last argument.
            Symbol constructor = accessConstructor(tree.pos(), meth);
            if (constructor != meth) {
                tree.args = tree.args.append(makeNull());
                TreeInfo.setSymbol(tree.meth, constructor);
            }

            // If we are calling a constructor of a local class, add
            // free variables after explicit constructor arguments.
            ClassSymbol c = (ClassSymbol)constructor.owner;
            if ((c.owner.kind & (VAR | MTH)) != 0) {
                tree.args = tree.args.appendList(loadFreevars(tree.pos(), freevars(c)));
            }

            // If we are calling a constructor of an enum class, pass
            // along the name and ordinal arguments
            if ((c.flags_field&ENUM) != 0 || c.getQualifiedName() == names.java_lang_Enum) {
                List<JCVariableDecl> params = currentMethodDef.params;
                if (currentMethodSym.owner.hasOuterInstance())
                    params = params.tail; // drop this$n
                tree.args = tree.args
                    .prepend(make_at(tree.pos()).Ident(params.tail.head.sym)) // ordinal
                    .prepend(make.Ident(params.head.sym)); // name
            }

            // If we are calling a constructor of a class with an outer
            // instance, and the call
            // is qualified, pass qualifier as first argument in front of
            // the explicit constructor arguments. If the call
            // is not qualified, pass the correct outer instance as
            // first argument.
            if (c.hasOuterInstance()) {
                JCExpression thisArg;
                if (tree.meth.getTag() == JCTree.SELECT) {
                    thisArg = attr.
                        makeNullCheck(translate(((JCFieldAccess) tree.meth).selected));
                    tree.meth = make.Ident(constructor);
                    ((JCIdent) tree.meth).name = methName;
                } else if ((c.owner.kind & (MTH | VAR)) != 0 || methName == names._this){
                    // local class or this() call
                    thisArg = makeThis(tree.meth.pos(), c.type.getEnclosingType().tsym);
                } else {
                    // super() call of nested class
                    thisArg = makeOwnerThis(tree.meth.pos(), c, false);
                }
                tree.args = tree.args.prepend(thisArg);
            }
        } else {
            // We are seeing a normal method invocation; translate this as usual.
            tree.meth = translate(tree.meth);

            // If the translated method itself is an Apply tree, we are
            // seeing an access method invocation. In this case, append
            // the method arguments to the arguments of the access method.
            if (tree.meth.getTag() == JCTree.APPLY) {
                JCMethodInvocation app = (JCMethodInvocation)tree.meth;
                app.args = tree.args.prependList(app.args);
                result = app;
                return;
            }
        }
        result = tree;
    }

    List<JCExpression> boxArgs(List<Type> parameters, List<JCExpression> _args, Type varargsElement) {
        List<JCExpression> args = _args;
        if (parameters.isEmpty()) return args;
        boolean anyChanges = false;
        ListBuffer<JCExpression> result = new ListBuffer<JCExpression>();
        while (parameters.tail.nonEmpty()) {
            JCExpression arg = translate(args.head, parameters.head);
            anyChanges |= (arg != args.head);
            result.append(arg);
            args = args.tail;
            parameters = parameters.tail;
        }
        Type parameter = parameters.head;
        if (varargsElement != null) {
            anyChanges = true;
            ListBuffer<JCExpression> elems = new ListBuffer<JCExpression>();
            while (args.nonEmpty()) {
                JCExpression arg = translate(args.head, varargsElement);
                elems.append(arg);
                args = args.tail;
            }
            JCNewArray boxedArgs = make.NewArray(make.Type(varargsElement),
                                               List.<JCExpression>nil(),
                                               elems.toList());
            boxedArgs.type = new ArrayType(varargsElement, syms.arrayClass);
            result.append(boxedArgs);
        } else {
            if (args.length() != 1) throw new AssertionError(args);
            JCExpression arg = translate(args.head, parameter);
            anyChanges |= (arg != args.head);
            result.append(arg);
            if (!anyChanges) return _args;
        }
        return result.toList();
    }

    /** Expand a boxing or unboxing conversion if needed. */
    @SuppressWarnings("unchecked") // XXX unchecked
    <T extends JCTree> T boxIfNeeded(T tree, Type type) {
        boolean havePrimitive = tree.type.isPrimitive();
        if (havePrimitive == type.isPrimitive())
            return tree;
        if (havePrimitive) {
            Type unboxedTarget = types.unboxedType(type);
            if (unboxedTarget.tag != NONE) {
                if (!types.isSubtype(tree.type, unboxedTarget)) //e.g. Character c = 89;
                    tree.type = unboxedTarget.constType(tree.type.constValue());
                return (T)boxPrimitive((JCExpression)tree, type);
            } else {
                tree = (T)boxPrimitive((JCExpression)tree);
            }
        } else {
            tree = (T)unbox((JCExpression)tree, type);
        }
        return tree;
    }

    /** Box up a single primitive expression. */
    JCExpression boxPrimitive(JCExpression tree) {
        return boxPrimitive(tree, types.boxedClass(tree.type).type);
    }

    /** Box up a single primitive expression. */
    JCExpression boxPrimitive(JCExpression tree, Type box) {
        make_at(tree.pos());
        if (target.boxWithConstructors()) {
            Symbol ctor = lookupConstructor(tree.pos(),
                                            box,
                                            List.<Type>nil()
                                            .prepend(tree.type));
            return make.Create(ctor, List.of(tree));
        } else {
            Symbol valueOfSym = lookupMethod(tree.pos(),
                                             names.valueOf,
                                             box,
                                             List.<Type>nil()
                                             .prepend(tree.type));
            return make.App(make.QualIdent(valueOfSym), List.of(tree));
        }
    }

    /** Unbox an object to a primitive value. */
    JCExpression unbox(JCExpression tree, Type primitive) {
        Type unboxedType = types.unboxedType(tree.type);
        // note: the "primitive" parameter is not used.  There muse be
        // a conversion from unboxedType to primitive.
        make_at(tree.pos());
        Symbol valueSym = lookupMethod(tree.pos(),
                                       unboxedType.tsym.name.append(names.Value), // x.intValue()
                                       tree.type,
                                       List.<Type>nil());
        return make.App(make.Select(tree, valueSym));
    }

    /** Visitor method for parenthesized expressions.
     *  If the subexpression has changed, omit the parens.
     */
    public void visitParens(JCParens tree) {
        JCTree expr = translate(tree.expr);
        result = ((expr == tree.expr) ? tree : expr);
    }

    public void visitIndexed(JCArrayAccess tree) {
        tree.indexed = translate(tree.indexed);
        tree.index = translate(tree.index, syms.intType);
        result = tree;
    }

    public void visitAssign(JCAssign tree) {
        tree.lhs = translate(tree.lhs, tree);
        tree.rhs = translate(tree.rhs, tree.lhs.type);

        // If translated left hand side is an Apply, we are
        // seeing an access method invocation. In this case, append
        // right hand side as last argument of the access method.
        if (tree.lhs.getTag() == JCTree.APPLY) {
            JCMethodInvocation app = (JCMethodInvocation)tree.lhs;
            app.args = List.of(tree.rhs).prependList(app.args);
            result = app;
        } else {
            result = tree;
        }
    }

    public void visitAssignop(final JCAssignOp tree) {
        if (!tree.lhs.type.isPrimitive() &&
            tree.operator.type.getReturnType().isPrimitive()) {
            // boxing required; need to rewrite as x = (unbox typeof x)(x op y);
            // or if x == (typeof x)z then z = (unbox typeof x)((typeof x)z op y)
            // (but without recomputing x)
            JCTree newTree = abstractLval(tree.lhs, new TreeBuilder() {
                    public JCTree build(final JCTree lhs) {
                        int newTag = tree.getTag() - JCTree.ASGOffset;
                        // Erasure (TransTypes) can change the type of
                        // tree.lhs.  However, we can still get the
                        // unerased type of tree.lhs as it is stored
                        // in tree.type in Attr.
                        Symbol newOperator = rs.resolveBinaryOperator(tree.pos(),
                                                                      newTag,
                                                                      attrEnv,
                                                                      tree.type,
                                                                      tree.rhs.type);
                        JCExpression expr = (JCExpression)lhs;
                        if (expr.type != tree.type)
                            expr = make.TypeCast(tree.type, expr);
                        JCBinary opResult = make.Binary(newTag, expr, tree.rhs);
                        opResult.operator = newOperator;
                        opResult.type = newOperator.type.getReturnType();
                        JCTypeCast newRhs = make.TypeCast(types.unboxedType(tree.type),
                                                          opResult);
                        return make.Assign((JCExpression)lhs, newRhs).setType(tree.type);
                    }
                });
            result = translate(newTree);
            return;
        }
        tree.lhs = translate(tree.lhs, tree);
        tree.rhs = translate(tree.rhs, tree.operator.type.getParameterTypes().tail.head);

        // If translated left hand side is an Apply, we are
        // seeing an access method invocation. In this case, append
        // right hand side as last argument of the access method.
        if (tree.lhs.getTag() == JCTree.APPLY) {
            JCMethodInvocation app = (JCMethodInvocation)tree.lhs;
            // if operation is a += on strings,
            // make sure to convert argument to string
            JCExpression rhs = (((OperatorSymbol)tree.operator).opcode == string_add)
              ? makeString(tree.rhs)
              : tree.rhs;
            app.args = List.of(rhs).prependList(app.args);
            result = app;
        } else {
            result = tree;
        }
    }

    /** Lower a tree of the form e++ or e-- where e is an object type */
    JCTree lowerBoxedPostop(final JCUnary tree) {
        // translate to tmp1=lval(e); tmp2=tmp1; tmp1 OP 1; tmp2
        // or
        // translate to tmp1=lval(e); tmp2=tmp1; (typeof tree)tmp1 OP 1; tmp2
        // where OP is += or -=
        final boolean cast = TreeInfo.skipParens(tree.arg).getTag() == JCTree.TYPECAST;
        return abstractLval(tree.arg, new TreeBuilder() {
                public JCTree build(final JCTree tmp1) {
                    return abstractRval(tmp1, tree.arg.type, new TreeBuilder() {
                            public JCTree build(final JCTree tmp2) {
                                int opcode = (tree.getTag() == JCTree.POSTINC)
                                    ? JCTree.PLUS_ASG : JCTree.MINUS_ASG;
                                JCTree lhs = cast
                                    ? make.TypeCast(tree.arg.type, (JCExpression)tmp1)
                                    : tmp1;
                                JCTree update = makeAssignop(opcode,
                                                             lhs,
                                                             make.Literal(1));
                                return makeComma(update, tmp2);
                            }
                        });
                }
            });
    }

    public void visitUnary(JCUnary tree) {
        boolean isUpdateOperator =
            JCTree.PREINC <= tree.getTag() && tree.getTag() <= JCTree.POSTDEC;
        if (isUpdateOperator && !tree.arg.type.isPrimitive()) {
            switch(tree.getTag()) {
            case JCTree.PREINC:            // ++ e
                    // translate to e += 1
            case JCTree.PREDEC:            // -- e
                    // translate to e -= 1
                {
                    int opcode = (tree.getTag() == JCTree.PREINC)
                        ? JCTree.PLUS_ASG : JCTree.MINUS_ASG;
                    JCAssignOp newTree = makeAssignop(opcode,
                                                    tree.arg,
                                                    make.Literal(1));
                    result = translate(newTree, tree.type);
                    return;
                }
            case JCTree.POSTINC:           // e ++
            case JCTree.POSTDEC:           // e --
                {
                    result = translate(lowerBoxedPostop(tree), tree.type);
                    return;
                }
            }
            throw new AssertionError(tree);
        }

        tree.arg = boxIfNeeded(translate(tree.arg, tree), tree.type);

        if (tree.getTag() == JCTree.NOT && tree.arg.type.constValue() != null) {
            tree.type = cfolder.fold1(bool_not, tree.arg.type);
        }

        // If translated left hand side is an Apply, we are
        // seeing an access method invocation. In this case, return
        // that access method invocation as result.
        if (isUpdateOperator && tree.arg.getTag() == JCTree.APPLY) {
            result = tree.arg;
        } else {
            result = tree;
        }
    }

    public void visitBinary(JCBinary tree) {
        List<Type> formals = tree.operator.type.getParameterTypes();
        JCTree lhs = tree.lhs = translate(tree.lhs, formals.head);
        switch (tree.getTag()) {
        case JCTree.OR:
            if (lhs.type.isTrue()) {
                result = lhs;
                return;
            }
            if (lhs.type.isFalse()) {
                result = translate(tree.rhs, formals.tail.head);
                return;
            }
            break;
        case JCTree.AND:
            if (lhs.type.isFalse()) {
                result = lhs;
                return;
            }
            if (lhs.type.isTrue()) {
                result = translate(tree.rhs, formals.tail.head);
                return;
            }
            break;
        }
        tree.rhs = translate(tree.rhs, formals.tail.head);
        result = tree;
    }

    public void visitIdent(JCIdent tree) {
        result = access(tree.sym, tree, enclOp, false);
    }

    /** Translate away the foreach loop.  */
    public void visitForeachLoop(JCEnhancedForLoop tree) {
        if (types.elemtype(tree.expr.type) == null)
            visitIterableForeachLoop(tree);
        else
            visitArrayForeachLoop(tree);
    }
        // where
        /**
         * A statement of the form
         *
         * <pre>
         *     for ( T v : arrayexpr ) stmt;
         * </pre>
         *
         * (where arrayexpr is of an array type) gets translated to
         *
         * <pre>
         *     for ( { arraytype #arr = arrayexpr;
         *             int #len = array.length;
         *             int #i = 0; };
         *           #i < #len; i$++ ) {
         *         T v = arr$[#i];
         *         stmt;
         *     }
         * </pre>
         *
         * where #arr, #len, and #i are freshly named synthetic local variables.
         */
        private void visitArrayForeachLoop(JCEnhancedForLoop tree) {
            make_at(tree.expr.pos());
            VarSymbol arraycache = new VarSymbol(0,
                                                 names.fromString("arr" + target.syntheticNameChar()),
                                                 tree.expr.type,
                                                 currentMethodSym);
            JCStatement arraycachedef = make.VarDef(arraycache, tree.expr);
            VarSymbol lencache = new VarSymbol(0,
                                               names.fromString("len" + target.syntheticNameChar()),
                                               syms.intType,
                                               currentMethodSym);
            JCStatement lencachedef = make.
                VarDef(lencache, make.Select(make.Ident(arraycache), syms.lengthVar));
            VarSymbol index = new VarSymbol(0,
                                            names.fromString("i" + target.syntheticNameChar()),
                                            syms.intType,
                                            currentMethodSym);

            JCVariableDecl indexdef = make.VarDef(index, make.Literal(INT, 0));
            indexdef.init.type = indexdef.type = syms.intType.constType(0);

            List<JCStatement> loopinit = List.of(arraycachedef, lencachedef, indexdef);
            JCBinary cond = makeBinary(JCTree.LT, make.Ident(index), make.Ident(lencache));

            JCExpressionStatement step = make.Exec(makeUnary(JCTree.PREINC, make.Ident(index)));

            Type elemtype = types.elemtype(tree.expr.type);
            JCExpression loopvarinit = make.Indexed(make.Ident(arraycache),
                                                    make.Ident(index)).setType(elemtype);
            JCVariableDecl loopvardef = (JCVariableDecl)make.VarDef(tree.var.mods,
                                                  tree.var.name,
                                                  tree.var.vartype,
                                                  loopvarinit).setType(tree.var.type);
            loopvardef.sym = tree.var.sym;
            JCBlock body = make.
                Block(0, List.of(loopvardef, tree.body));

            result = translate(make.
                               ForLoop(loopinit,
                                       cond,
                                       List.of(step),
                                       body));
            patchTargets(body, tree, result);
        }
        /** Patch up break and continue targets. */
        private void patchTargets(JCTree body, final JCTree src, final JCTree dest) {
            class Patcher extends TreeScanner {
                public void visitBreak(JCBreak tree) {
                    if (tree.target == src)
                        tree.target = dest;
                }
                public void visitContinue(JCContinue tree) {
                    if (tree.target == src)
                        tree.target = dest;
                }
                public void visitClassDef(JCClassDecl tree) {}
            }
            new Patcher().scan(body);
        }
        /**
         * A statement of the form
         *
         * <pre>
         *     for ( T v : coll ) stmt ;
         * </pre>
         *
         * (where coll implements Iterable<? extends T>) gets translated to
         *
         * <pre>
         *     for ( Iterator<? extends T> #i = coll.iterator(); #i.hasNext(); ) {
         *         T v = (T) #i.next();
         *         stmt;
         *     }
         * </pre>
         *
         * where #i is a freshly named synthetic local variable.
         */
        private void visitIterableForeachLoop(JCEnhancedForLoop tree) {
            make_at(tree.expr.pos());
            Type iteratorTarget = syms.objectType;
            Type iterableType = types.asSuper(types.upperBound(tree.expr.type),
                                              syms.iterableType.tsym);
            if (iterableType.getTypeArguments().nonEmpty())
                iteratorTarget = types.erasure(iterableType.getTypeArguments().head);
            Type eType = tree.expr.type;
            tree.expr.type = types.erasure(eType);
            if (eType.tag == TYPEVAR && eType.getUpperBound().isCompound())
                tree.expr = make.TypeCast(types.erasure(iterableType), tree.expr);
            Symbol iterator = lookupMethod(tree.expr.pos(),
                                           names.iterator,
                                           types.erasure(syms.iterableType),
                                           List.<Type>nil());
            VarSymbol itvar = new VarSymbol(0, names.fromString("i" + target.syntheticNameChar()),
                                            types.erasure(iterator.type.getReturnType()),
                                            currentMethodSym);
            JCStatement init = make.
                VarDef(itvar,
                       make.App(make.Select(tree.expr, iterator)));
            Symbol hasNext = lookupMethod(tree.expr.pos(),
                                          names.hasNext,
                                          itvar.type,
                                          List.<Type>nil());
            JCMethodInvocation cond = make.App(make.Select(make.Ident(itvar), hasNext));
            Symbol next = lookupMethod(tree.expr.pos(),
                                       names.next,
                                       itvar.type,
                                       List.<Type>nil());
            JCExpression vardefinit = make.App(make.Select(make.Ident(itvar), next));
            if (tree.var.type.isPrimitive())
                vardefinit = make.TypeCast(types.upperBound(iteratorTarget), vardefinit);
            else
                vardefinit = make.TypeCast(tree.var.type, vardefinit);
            JCVariableDecl indexDef = (JCVariableDecl)make.VarDef(tree.var.mods,
                                                  tree.var.name,
                                                  tree.var.vartype,
                                                  vardefinit).setType(tree.var.type);
            indexDef.sym = tree.var.sym;
            JCBlock body = make.Block(0, List.of(indexDef, tree.body));
            body.endpos = TreeInfo.endPos(tree.body);
            result = translate(make.
                ForLoop(List.of(init),
                        cond,
                        List.<JCExpressionStatement>nil(),
                        body));
            patchTargets(body, tree, result);
        }

    public void visitVarDef(JCVariableDecl tree) {
        MethodSymbol oldMethodSym = currentMethodSym;
        tree.mods = translate(tree.mods);
        tree.vartype = translate(tree.vartype);
        if (currentMethodSym == null) {
            // A class or instance field initializer.
            currentMethodSym =
                new MethodSymbol((tree.mods.flags&STATIC) | BLOCK,
                                 names.empty, null,
                                 currentClass);
        }
        if (tree.init != null) tree.init = translate(tree.init, tree.type);
        result = tree;
        currentMethodSym = oldMethodSym;
    }

    public void visitBlock(JCBlock tree) {
        MethodSymbol oldMethodSym = currentMethodSym;
        if (currentMethodSym == null) {
            // Block is a static or instance initializer.
            currentMethodSym =
                new MethodSymbol(tree.flags | BLOCK,
                                 names.empty, null,
                                 currentClass);
        }
        super.visitBlock(tree);
        currentMethodSym = oldMethodSym;
    }

    public void visitDoLoop(JCDoWhileLoop tree) {
        tree.body = translate(tree.body);
        tree.cond = translate(tree.cond, syms.booleanType);
        result = tree;
    }

    public void visitWhileLoop(JCWhileLoop tree) {
        tree.cond = translate(tree.cond, syms.booleanType);
        tree.body = translate(tree.body);
        result = tree;
    }

    public void visitForLoop(JCForLoop tree) {
        tree.init = translate(tree.init);
        if (tree.cond != null)
            tree.cond = translate(tree.cond, syms.booleanType);
        tree.step = translate(tree.step);
        tree.body = translate(tree.body);
        result = tree;
    }

    public void visitReturn(JCReturn tree) {
        if (tree.expr != null)
            tree.expr = translate(tree.expr,
                                  types.erasure(currentMethodDef
                                                .restype.type));
        result = tree;
    }

    public void visitSwitch(JCSwitch tree) {
        Type selsuper = types.supertype(tree.selector.type);
        boolean enumSwitch = selsuper != null &&
            (tree.selector.type.tsym.flags() & ENUM) != 0;
        boolean stringSwitch = selsuper != null &&
            types.isSameType(tree.selector.type, syms.stringType);
        Type target = enumSwitch ? tree.selector.type :
            (stringSwitch? syms.stringType : syms.intType);
        tree.selector = translate(tree.selector, target);
        tree.cases = translateCases(tree.cases);
        if (enumSwitch) {
            result = visitEnumSwitch(tree);
        } else if (stringSwitch) {
            result = visitStringSwitch(tree);
        } else {
            result = tree;
        }
    }

    public JCTree visitEnumSwitch(JCSwitch tree) {
        TypeSymbol enumSym = tree.selector.type.tsym;
        EnumMapping map = mapForEnum(tree.pos(), enumSym);
        make_at(tree.pos());
        Symbol ordinalMethod = lookupMethod(tree.pos(),
                                            names.ordinal,
                                            tree.selector.type,
                                            List.<Type>nil());
        JCArrayAccess selector = make.Indexed(map.mapVar,
                                        make.App(make.Select(tree.selector,
                                                             ordinalMethod)));
        ListBuffer<JCCase> cases = new ListBuffer<JCCase>();
        for (JCCase c : tree.cases) {
            if (c.pat != null) {
                VarSymbol label = (VarSymbol)TreeInfo.symbol(c.pat);
                JCLiteral pat = map.forConstant(label);
                cases.append(make.Case(pat, c.stats));
            } else {
                cases.append(c);
            }
        }
        JCSwitch enumSwitch = make.Switch(selector, cases.toList());
        patchTargets(enumSwitch, tree, enumSwitch);
        return enumSwitch;
    }

    public JCTree visitStringSwitch(JCSwitch tree) {
        List<JCCase> caseList = tree.getCases();
        int alternatives = caseList.size();

        if (alternatives == 0) { // Strange but legal possibility
            return make.at(tree.pos()).Exec(attr.makeNullCheck(tree.getExpression()));
        } else {
            /*
             * The general approach used is to translate a single
             * string switch statement into a series of two chained
             * switch statements: the first a synthesized statement
             * switching on the argument string's hash value and
             * computing a string's position in the list of original
             * case labels, if any, followed by a second switch on the
             * computed integer value.  The second switch has the same
             * code structure as the original string switch statement
             * except that the string case labels are replaced with
             * positional integer constants starting at 0.
             *
             * The first switch statement can be thought of as an
             * inlined map from strings to their position in the case
             * label list.  An alternate implementation would use an
             * actual Map for this purpose, as done for enum switches.
             *
             * With some additional effort, it would be possible to
             * use a single switch statement on the hash code of the
             * argument, but care would need to be taken to preserve
             * the proper control flow in the presence of hash
             * collisions and other complications, such as
             * fallthroughs.  Switch statements with one or two
             * alternatives could also be specially translated into
             * if-then statements to omit the computation of the hash
             * code.
             *
             * The generated code assumes that the hashing algorithm
             * of String is the same in the compilation environment as
             * in the environment the code will run in.  The string
             * hashing algorithm in the SE JDK has been unchanged
             * since at least JDK 1.2.  Since the algorithm has been
             * specified since that release as well, it is very
             * unlikely to be changed in the future.
             *
             * Different hashing algorithms, such as the length of the
             * strings or a perfect hashing algorithm over the
             * particular set of case labels, could potentially be
             * used instead of String.hashCode.
             */

            ListBuffer<JCStatement> stmtList = new ListBuffer<JCStatement>();

            // Map from String case labels to their original position in
            // the list of case labels.
            Map<String, Integer> caseLabelToPosition =
                new LinkedHashMap<String, Integer>(alternatives + 1, 1.0f);

            // Map of hash codes to the string case labels having that hashCode.
            Map<Integer, Set<String>> hashToString =
                new LinkedHashMap<Integer, Set<String>>(alternatives + 1, 1.0f);

            int casePosition = 0;
            for(JCCase oneCase : caseList) {
                JCExpression expression = oneCase.getExpression();

                if (expression != null) { // expression for a "default" case is null
                    String labelExpr = (String) expression.type.constValue();
                    Integer mapping = caseLabelToPosition.put(labelExpr, casePosition);
                    assert mapping == null;
                    int hashCode = labelExpr.hashCode();

                    Set<String> stringSet = hashToString.get(hashCode);
                    if (stringSet == null) {
                        stringSet = new LinkedHashSet<String>(1, 1.0f);
                        stringSet.add(labelExpr);
                        hashToString.put(hashCode, stringSet);
                    } else {
                        boolean added = stringSet.add(labelExpr);
                        assert added;
                    }
                }
                casePosition++;
            }

            // Synthesize a switch statement that has the effect of
            // mapping from a string to the integer position of that
            // string in the list of case labels.  This is done by
            // switching on the hashCode of the string followed by an
            // if-then-else chain comparing the input for equality
            // with all the case labels having that hash value.

            /*
             * s$ = top of stack;
             * tmp$ = -1;
             * switch($s.hashCode()) {
             *     case caseLabel.hashCode:
             *         if (s$.equals("caseLabel_1")
             *           tmp$ = caseLabelToPosition("caseLabel_1");
             *         else if (s$.equals("caseLabel_2"))
             *           tmp$ = caseLabelToPosition("caseLabel_2");
             *         ...
             *         break;
             * ...
             * }
             */

            VarSymbol dollar_s = new VarSymbol(FINAL|SYNTHETIC,
                                               names.fromString("s" + tree.pos + target.syntheticNameChar()),
                                               syms.stringType,
                                               currentMethodSym);
            stmtList.append(make.at(tree.pos()).VarDef(dollar_s, tree.getExpression()).setType(dollar_s.type));

            VarSymbol dollar_tmp = new VarSymbol(SYNTHETIC,
                                                 names.fromString("tmp" + tree.pos + target.syntheticNameChar()),
                                                 syms.intType,
                                                 currentMethodSym);
            JCVariableDecl dollar_tmp_def =
                (JCVariableDecl)make.VarDef(dollar_tmp, make.Literal(INT, -1)).setType(dollar_tmp.type);
            dollar_tmp_def.init.type = dollar_tmp.type = syms.intType;
            stmtList.append(dollar_tmp_def);
            ListBuffer<JCCase> caseBuffer = ListBuffer.lb();
            // hashCode will trigger nullcheck on original switch expression
            JCMethodInvocation hashCodeCall = makeCall(make.Ident(dollar_s),
                                                       names.hashCode,
                                                       List.<JCExpression>nil()).setType(syms.intType);
            JCSwitch switch1 = make.Switch(hashCodeCall,
                                        caseBuffer.toList());
            for(Map.Entry<Integer, Set<String>> entry : hashToString.entrySet()) {
                int hashCode = entry.getKey();
                Set<String> stringsWithHashCode = entry.getValue();
                assert stringsWithHashCode.size() >= 1;

                JCStatement elsepart = null;
                for(String caseLabel : stringsWithHashCode ) {
                    JCMethodInvocation stringEqualsCall = makeCall(make.Ident(dollar_s),
                                                                   names.equals,
                                                                   List.<JCExpression>of(make.Literal(caseLabel)));
                    elsepart = make.If(stringEqualsCall,
                                       make.Exec(make.Assign(make.Ident(dollar_tmp),
                                                             make.Literal(caseLabelToPosition.get(caseLabel))).
                                                 setType(dollar_tmp.type)),
                                       elsepart);
                }

                ListBuffer<JCStatement> lb = ListBuffer.lb();
                JCBreak breakStmt = make.Break(null);
                breakStmt.target = switch1;
                lb.append(elsepart).append(breakStmt);

                caseBuffer.append(make.Case(make.Literal(hashCode), lb.toList()));
            }

            switch1.cases = caseBuffer.toList();
            stmtList.append(switch1);

            // Make isomorphic switch tree replacing string labels
            // with corresponding integer ones from the label to
            // position map.

            ListBuffer<JCCase> lb = ListBuffer.lb();
            JCSwitch switch2 = make.Switch(make.Ident(dollar_tmp), lb.toList());
            for(JCCase oneCase : caseList ) {
                // Rewire up old unlabeled break statements to the
                // replacement switch being created.
                patchTargets(oneCase, tree, switch2);

                boolean isDefault = (oneCase.getExpression() == null);
                JCExpression caseExpr;
                if (isDefault)
                    caseExpr = null;
                else {
                    caseExpr = make.Literal(caseLabelToPosition.get((String)oneCase.
                                                                    getExpression().
                                                                    type.constValue()));
                }

                lb.append(make.Case(caseExpr,
                                    oneCase.getStatements()));
            }

            switch2.cases = lb.toList();
            stmtList.append(switch2);

            return make.Block(0L, stmtList.toList());
        }
    }

    public void visitNewArray(JCNewArray tree) {
        tree.elemtype = translate(tree.elemtype);
        for (List<JCExpression> t = tree.dims; t.tail != null; t = t.tail)
            if (t.head != null) t.head = translate(t.head, syms.intType);
        tree.elems = translate(tree.elems, types.elemtype(tree.type));
        result = tree;
    }

    public void visitSelect(JCFieldAccess tree) {
        // need to special case-access of the form C.super.x
        // these will always need an access method.
        boolean qualifiedSuperAccess =
            tree.selected.getTag() == JCTree.SELECT &&
            TreeInfo.name(tree.selected) == names._super;
        tree.selected = translate(tree.selected);
        if (tree.name == names._class)
            result = classOf(tree.selected);
        else if (tree.name == names._this || tree.name == names._super)
            result = makeThis(tree.pos(), tree.selected.type.tsym);
        else
            result = access(tree.sym, tree, enclOp, qualifiedSuperAccess);
    }

    public void visitLetExpr(LetExpr tree) {
        tree.defs = translateVarDefs(tree.defs);
        tree.expr = translate(tree.expr, tree.type);
        result = tree;
    }

    // There ought to be nothing to rewrite here;
    // we don't generate code.
    public void visitAnnotation(JCAnnotation tree) {
        result = tree;
    }

/**************************************************************************
 * main method
 *************************************************************************/

    /** Translate a toplevel class and return a list consisting of
     *  the translated class and translated versions of all inner classes.
     *  @param env   The attribution environment current at the class definition.
     *               We need this for resolving some additional symbols.
     *  @param cdef  The tree representing the class definition.
     */
    public List<JCTree> translateTopLevelClass(Env<AttrContext> env, JCTree cdef, TreeMaker make) {
        ListBuffer<JCTree> translated = null;
        try {
            attrEnv = env;
            this.make = make;
            endPositions = env.toplevel.endPositions;
            currentClass = null;
            currentMethodDef = null;
            outermostClassDef = (cdef.getTag() == JCTree.CLASSDEF) ? (JCClassDecl)cdef : null;
            outermostMemberDef = null;
            this.translated = new ListBuffer<JCTree>();
            classdefs = new HashMap<ClassSymbol,JCClassDecl>();
            actualSymbols = new HashMap<Symbol,Symbol>();
            freevarCache = new HashMap<ClassSymbol,List<VarSymbol>>();
            proxies = new Scope(syms.noSymbol);
            outerThisStack = List.nil();
            accessNums = new HashMap<Symbol,Integer>();
            accessSyms = new HashMap<Symbol,MethodSymbol[]>();
            accessConstrs = new HashMap<Symbol,MethodSymbol>();
            accessed = new ListBuffer<Symbol>();
            translate(cdef, (JCExpression)null);
            for (List<Symbol> l = accessed.toList(); l.nonEmpty(); l = l.tail)
                makeAccessible(l.head);
            for (EnumMapping map : enumSwitchMap.values())
                map.translate();
            checkConflicts(this.translated.toList());
            translated = this.translated;
        } finally {
            // note that recursive invocations of this method fail hard
            attrEnv = null;
            this.make = null;
            endPositions = null;
            currentClass = null;
            currentMethodDef = null;
            outermostClassDef = null;
            outermostMemberDef = null;
            this.translated = null;
            classdefs = null;
            actualSymbols = null;
            freevarCache = null;
            proxies = null;
            outerThisStack = null;
            accessNums = null;
            accessSyms = null;
            accessConstrs = null;
            accessed = null;
            enumSwitchMap.clear();
        }
        return translated.toList();
    }

    //////////////////////////////////////////////////////////////
    // The following contributed by Borland for bootstrapping purposes
    //////////////////////////////////////////////////////////////
    private void addEnumCompatibleMembers(JCClassDecl cdef) {
        make_at(null);

        // Add the special enum fields
        VarSymbol ordinalFieldSym = addEnumOrdinalField(cdef);
        VarSymbol nameFieldSym = addEnumNameField(cdef);

        // Add the accessor methods for name and ordinal
        MethodSymbol ordinalMethodSym = addEnumFieldOrdinalMethod(cdef, ordinalFieldSym);
        MethodSymbol nameMethodSym = addEnumFieldNameMethod(cdef, nameFieldSym);

        // Add the toString method
        addEnumToString(cdef, nameFieldSym);

        // Add the compareTo method
        addEnumCompareTo(cdef, ordinalFieldSym);
    }

    private VarSymbol addEnumOrdinalField(JCClassDecl cdef) {
        VarSymbol ordinal = new VarSymbol(PRIVATE|FINAL|SYNTHETIC,
                                          names.fromString("$ordinal"),
                                          syms.intType,
                                          cdef.sym);
        cdef.sym.members().enter(ordinal);
        cdef.defs = cdef.defs.prepend(make.VarDef(ordinal, null));
        return ordinal;
    }

    private VarSymbol addEnumNameField(JCClassDecl cdef) {
        VarSymbol name = new VarSymbol(PRIVATE|FINAL|SYNTHETIC,
                                          names.fromString("$name"),
                                          syms.stringType,
                                          cdef.sym);
        cdef.sym.members().enter(name);
        cdef.defs = cdef.defs.prepend(make.VarDef(name, null));
        return name;
    }

    private MethodSymbol addEnumFieldOrdinalMethod(JCClassDecl cdef, VarSymbol ordinalSymbol) {
        // Add the accessor methods for ordinal
        Symbol ordinalSym = lookupMethod(cdef.pos(),
                                         names.ordinal,
                                         cdef.type,
                                         List.<Type>nil());

        assert(ordinalSym != null);
        assert(ordinalSym instanceof MethodSymbol);

        JCStatement ret = make.Return(make.Ident(ordinalSymbol));
        cdef.defs = cdef.defs.append(make.MethodDef((MethodSymbol)ordinalSym,
                                                    make.Block(0L, List.of(ret))));

        return (MethodSymbol)ordinalSym;
    }

    private MethodSymbol addEnumFieldNameMethod(JCClassDecl cdef, VarSymbol nameSymbol) {
        // Add the accessor methods for name
        Symbol nameSym = lookupMethod(cdef.pos(),
                                   names._name,
                                   cdef.type,
                                   List.<Type>nil());

        assert(nameSym != null);
        assert(nameSym instanceof MethodSymbol);

        JCStatement ret = make.Return(make.Ident(nameSymbol));

        cdef.defs = cdef.defs.append(make.MethodDef((MethodSymbol)nameSym,
                                                    make.Block(0L, List.of(ret))));

        return (MethodSymbol)nameSym;
    }

    private MethodSymbol addEnumToString(JCClassDecl cdef,
                                         VarSymbol nameSymbol) {
        Symbol toStringSym = lookupMethod(cdef.pos(),
                                          names.toString,
                                          cdef.type,
                                          List.<Type>nil());

        JCTree toStringDecl = null;
        if (toStringSym != null)
            toStringDecl = TreeInfo.declarationFor(toStringSym, cdef);

        if (toStringDecl != null)
            return (MethodSymbol)toStringSym;

        JCStatement ret = make.Return(make.Ident(nameSymbol));

        JCTree resTypeTree = make.Type(syms.stringType);

        MethodType toStringType = new MethodType(List.<Type>nil(),
                                                 syms.stringType,
                                                 List.<Type>nil(),
                                                 cdef.sym);
        toStringSym = new MethodSymbol(PUBLIC,
                                       names.toString,
                                       toStringType,
                                       cdef.type.tsym);
        toStringDecl = make.MethodDef((MethodSymbol)toStringSym,
                                      make.Block(0L, List.of(ret)));

        cdef.defs = cdef.defs.prepend(toStringDecl);
        cdef.sym.members().enter(toStringSym);

        return (MethodSymbol)toStringSym;
    }

    private MethodSymbol addEnumCompareTo(JCClassDecl cdef, VarSymbol ordinalSymbol) {
        Symbol compareToSym = lookupMethod(cdef.pos(),
                                   names.compareTo,
                                   cdef.type,
                                   List.of(cdef.sym.type));

        assert(compareToSym != null);
        assert(compareToSym instanceof MethodSymbol);

        JCMethodDecl compareToDecl = (JCMethodDecl) TreeInfo.declarationFor(compareToSym, cdef);

        ListBuffer<JCStatement> blockStatements = new ListBuffer<JCStatement>();

        JCModifiers mod1 = make.Modifiers(0L);
        Name oName = names.fromString("o");
        JCVariableDecl par1 = make.Param(oName, cdef.type, compareToSym);

        JCIdent paramId1 = make.Ident(names.java_lang_Object);
        paramId1.type = cdef.type;
        paramId1.sym = par1.sym;

        ((MethodSymbol)compareToSym).params = List.of(par1.sym);

        JCIdent par1UsageId = make.Ident(par1.sym);
        JCIdent castTargetIdent = make.Ident(cdef.sym);
        JCTypeCast cast = make.TypeCast(castTargetIdent, par1UsageId);
        cast.setType(castTargetIdent.type);

        Name otherName = names.fromString("other");

        VarSymbol otherVarSym = new VarSymbol(mod1.flags,
                                              otherName,
                                              cdef.type,
                                              compareToSym);
        JCVariableDecl otherVar = make.VarDef(otherVarSym, cast);
        blockStatements.append(otherVar);

        JCIdent id1 = make.Ident(ordinalSymbol);

        JCIdent fLocUsageId = make.Ident(otherVarSym);
        JCExpression sel = make.Select(fLocUsageId, ordinalSymbol);
        JCBinary bin = makeBinary(JCTree.MINUS, id1, sel);
        JCReturn ret = make.Return(bin);
        blockStatements.append(ret);
        JCMethodDecl compareToMethod = make.MethodDef((MethodSymbol)compareToSym,
                                                   make.Block(0L,
                                                              blockStatements.toList()));
        compareToMethod.params = List.of(par1);
        cdef.defs = cdef.defs.append(compareToMethod);

        return (MethodSymbol)compareToSym;
    }
    //////////////////////////////////////////////////////////////
    // The above contributed by Borland for bootstrapping purposes
    //////////////////////////////////////////////////////////////
}
