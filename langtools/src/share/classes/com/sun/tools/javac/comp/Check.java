/*
 * Copyright 1999-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.javac.comp;

import java.util.*;
import java.util.Set;

import com.sun.tools.javac.code.*;
import com.sun.tools.javac.jvm.*;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.util.List;

import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.code.Lint;
import com.sun.tools.javac.code.Lint.LintCategory;
import com.sun.tools.javac.code.Type.*;
import com.sun.tools.javac.code.Symbol.*;

import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.code.Kinds.*;
import static com.sun.tools.javac.code.TypeTags.*;

/** Type checking helper class for the attribution phase.
 *
 *  <p><b>This is NOT part of any API supported by Sun Microsystems.  If
 *  you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Check {
    protected static final Context.Key<Check> checkKey =
        new Context.Key<Check>();

    private final Names names;
    private final Log log;
    private final Symtab syms;
    private final Infer infer;
    private final Target target;
    private final Source source;
    private final Types types;
    private final JCDiagnostic.Factory diags;
    private final boolean skipAnnotations;
    private final TreeInfo treeinfo;

    // The set of lint options currently in effect. It is initialized
    // from the context, and then is set/reset as needed by Attr as it
    // visits all the various parts of the trees during attribution.
    private Lint lint;

    public static Check instance(Context context) {
        Check instance = context.get(checkKey);
        if (instance == null)
            instance = new Check(context);
        return instance;
    }

    protected Check(Context context) {
        context.put(checkKey, this);

        names = Names.instance(context);
        log = Log.instance(context);
        syms = Symtab.instance(context);
        infer = Infer.instance(context);
        this.types = Types.instance(context);
        diags = JCDiagnostic.Factory.instance(context);
        Options options = Options.instance(context);
        target = Target.instance(context);
        source = Source.instance(context);
        lint = Lint.instance(context);
        treeinfo = TreeInfo.instance(context);

        Source source = Source.instance(context);
        allowGenerics = source.allowGenerics();
        allowAnnotations = source.allowAnnotations();
        complexInference = options.get("-complexinference") != null;
        skipAnnotations = options.get("skipAnnotations") != null;

        boolean verboseDeprecated = lint.isEnabled(LintCategory.DEPRECATION);
        boolean verboseUnchecked = lint.isEnabled(LintCategory.UNCHECKED);
        boolean enforceMandatoryWarnings = source.enforceMandatoryWarnings();

        deprecationHandler = new MandatoryWarningHandler(log, verboseDeprecated,
                enforceMandatoryWarnings, "deprecated");
        uncheckedHandler = new MandatoryWarningHandler(log, verboseUnchecked,
                enforceMandatoryWarnings, "unchecked");
    }

    /** Switch: generics enabled?
     */
    boolean allowGenerics;

    /** Switch: annotations enabled?
     */
    boolean allowAnnotations;

    /** Switch: -complexinference option set?
     */
    boolean complexInference;

    /** A table mapping flat names of all compiled classes in this run to their
     *  symbols; maintained from outside.
     */
    public Map<Name,ClassSymbol> compiled = new HashMap<Name, ClassSymbol>();

    /** A handler for messages about deprecated usage.
     */
    private MandatoryWarningHandler deprecationHandler;

    /** A handler for messages about unchecked or unsafe usage.
     */
    private MandatoryWarningHandler uncheckedHandler;


/* *************************************************************************
 * Errors and Warnings
 **************************************************************************/

    Lint setLint(Lint newLint) {
        Lint prev = lint;
        lint = newLint;
        return prev;
    }

    /** Warn about deprecated symbol.
     *  @param pos        Position to be used for error reporting.
     *  @param sym        The deprecated symbol.
     */
    void warnDeprecated(DiagnosticPosition pos, Symbol sym) {
        if (!lint.isSuppressed(LintCategory.DEPRECATION))
            deprecationHandler.report(pos, "has.been.deprecated", sym, sym.location());
    }

    /** Warn about unchecked operation.
     *  @param pos        Position to be used for error reporting.
     *  @param msg        A string describing the problem.
     */
    public void warnUnchecked(DiagnosticPosition pos, String msg, Object... args) {
        if (!lint.isSuppressed(LintCategory.UNCHECKED))
            uncheckedHandler.report(pos, msg, args);
    }

    /**
     * Report any deferred diagnostics.
     */
    public void reportDeferredDiagnostics() {
        deprecationHandler.reportDeferredDiagnostic();
        uncheckedHandler.reportDeferredDiagnostic();
    }


    /** Report a failure to complete a class.
     *  @param pos        Position to be used for error reporting.
     *  @param ex         The failure to report.
     */
    public Type completionError(DiagnosticPosition pos, CompletionFailure ex) {
        log.error(pos, "cant.access", ex.sym, ex.getDetailValue());
        if (ex instanceof ClassReader.BadClassFile) throw new Abort();
        else return syms.errType;
    }

    /** Report a type error.
     *  @param pos        Position to be used for error reporting.
     *  @param problem    A string describing the error.
     *  @param found      The type that was found.
     *  @param req        The type that was required.
     */
    Type typeError(DiagnosticPosition pos, Object problem, Type found, Type req) {
        log.error(pos, "prob.found.req",
                  problem, found, req);
        return types.createErrorType(found);
    }

    Type typeError(DiagnosticPosition pos, String problem, Type found, Type req, Object explanation) {
        log.error(pos, "prob.found.req.1", problem, found, req, explanation);
        return types.createErrorType(found);
    }

    /** Report an error that wrong type tag was found.
     *  @param pos        Position to be used for error reporting.
     *  @param required   An internationalized string describing the type tag
     *                    required.
     *  @param found      The type that was found.
     */
    Type typeTagError(DiagnosticPosition pos, Object required, Object found) {
        // this error used to be raised by the parser,
        // but has been delayed to this point:
        if (found instanceof Type && ((Type)found).tag == VOID) {
            log.error(pos, "illegal.start.of.type");
            return syms.errType;
        }
        log.error(pos, "type.found.req", found, required);
        return types.createErrorType(found instanceof Type ? (Type)found : syms.errType);
    }

    /** Report an error that symbol cannot be referenced before super
     *  has been called.
     *  @param pos        Position to be used for error reporting.
     *  @param sym        The referenced symbol.
     */
    void earlyRefError(DiagnosticPosition pos, Symbol sym) {
        log.error(pos, "cant.ref.before.ctor.called", sym);
    }

    /** Report duplicate declaration error.
     */
    void duplicateError(DiagnosticPosition pos, Symbol sym) {
        if (!sym.type.isErroneous()) {
            log.error(pos, "already.defined", sym, sym.location());
        }
    }

    /** Report array/varargs duplicate declaration
     */
    void varargsDuplicateError(DiagnosticPosition pos, Symbol sym1, Symbol sym2) {
        if (!sym1.type.isErroneous() && !sym2.type.isErroneous()) {
            log.error(pos, "array.and.varargs", sym1, sym2, sym2.location());
        }
    }

/* ************************************************************************
 * duplicate declaration checking
 *************************************************************************/

    /** Check that variable does not hide variable with same name in
     *  immediately enclosing local scope.
     *  @param pos           Position for error reporting.
     *  @param v             The symbol.
     *  @param s             The scope.
     */
    void checkTransparentVar(DiagnosticPosition pos, VarSymbol v, Scope s) {
        if (s.next != null) {
            for (Scope.Entry e = s.next.lookup(v.name);
                 e.scope != null && e.sym.owner == v.owner;
                 e = e.next()) {
                if (e.sym.kind == VAR &&
                    (e.sym.owner.kind & (VAR | MTH)) != 0 &&
                    v.name != names.error) {
                    duplicateError(pos, e.sym);
                    return;
                }
            }
        }
    }

    /** Check that a class or interface does not hide a class or
     *  interface with same name in immediately enclosing local scope.
     *  @param pos           Position for error reporting.
     *  @param c             The symbol.
     *  @param s             The scope.
     */
    void checkTransparentClass(DiagnosticPosition pos, ClassSymbol c, Scope s) {
        if (s.next != null) {
            for (Scope.Entry e = s.next.lookup(c.name);
                 e.scope != null && e.sym.owner == c.owner;
                 e = e.next()) {
                if (e.sym.kind == TYP &&
                    (e.sym.owner.kind & (VAR | MTH)) != 0 &&
                    c.name != names.error) {
                    duplicateError(pos, e.sym);
                    return;
                }
            }
        }
    }

    /** Check that class does not have the same name as one of
     *  its enclosing classes, or as a class defined in its enclosing scope.
     *  return true if class is unique in its enclosing scope.
     *  @param pos           Position for error reporting.
     *  @param name          The class name.
     *  @param s             The enclosing scope.
     */
    boolean checkUniqueClassName(DiagnosticPosition pos, Name name, Scope s) {
        for (Scope.Entry e = s.lookup(name); e.scope == s; e = e.next()) {
            if (e.sym.kind == TYP && e.sym.name != names.error) {
                duplicateError(pos, e.sym);
                return false;
            }
        }
        for (Symbol sym = s.owner; sym != null; sym = sym.owner) {
            if (sym.kind == TYP && sym.name == name && sym.name != names.error) {
                duplicateError(pos, sym);
                return true;
            }
        }
        return true;
    }

/* *************************************************************************
 * Class name generation
 **************************************************************************/

    /** Return name of local class.
     *  This is of the form    <enclClass> $ n <classname>
     *  where
     *    enclClass is the flat name of the enclosing class,
     *    classname is the simple name of the local class
     */
    Name localClassName(ClassSymbol c) {
        for (int i=1; ; i++) {
            Name flatname = names.
                fromString("" + c.owner.enclClass().flatname +
                           target.syntheticNameChar() + i +
                           c.name);
            if (compiled.get(flatname) == null) return flatname;
        }
    }

/* *************************************************************************
 * Type Checking
 **************************************************************************/

    /** Check that a given type is assignable to a given proto-type.
     *  If it is, return the type, otherwise return errType.
     *  @param pos        Position to be used for error reporting.
     *  @param found      The type that was found.
     *  @param req        The type that was required.
     */
    Type checkType(DiagnosticPosition pos, Type found, Type req) {
        if (req.tag == ERROR)
            return req;
        if (found.tag == FORALL)
            return instantiatePoly(pos, (ForAll)found, req, convertWarner(pos, found, req));
        if (req.tag == NONE)
            return found;
        if (types.isAssignable(found, req, convertWarner(pos, found, req)))
            return found;
        if (found.tag <= DOUBLE && req.tag <= DOUBLE)
            return typeError(pos, diags.fragment("possible.loss.of.precision"), found, req);
        if (found.isSuperBound()) {
            log.error(pos, "assignment.from.super-bound", found);
            return types.createErrorType(found);
        }
        if (req.isExtendsBound()) {
            log.error(pos, "assignment.to.extends-bound", req);
            return types.createErrorType(found);
        }
        return typeError(pos, diags.fragment("incompatible.types"), found, req);
    }

    /** Instantiate polymorphic type to some prototype, unless
     *  prototype is `anyPoly' in which case polymorphic type
     *  is returned unchanged.
     */
    Type instantiatePoly(DiagnosticPosition pos, ForAll t, Type pt, Warner warn) {
        if (pt == Infer.anyPoly && complexInference) {
            return t;
        } else if (pt == Infer.anyPoly || pt.tag == NONE) {
            Type newpt = t.qtype.tag <= VOID ? t.qtype : syms.objectType;
            return instantiatePoly(pos, t, newpt, warn);
        } else if (pt.tag == ERROR) {
            return pt;
        } else {
            try {
                return infer.instantiateExpr(t, pt, warn);
            } catch (Infer.NoInstanceException ex) {
                if (ex.isAmbiguous) {
                    JCDiagnostic d = ex.getDiagnostic();
                    log.error(pos,
                              "undetermined.type" + (d!=null ? ".1" : ""),
                              t, d);
                    return types.createErrorType(pt);
                } else {
                    JCDiagnostic d = ex.getDiagnostic();
                    return typeError(pos,
                                     diags.fragment("incompatible.types" + (d!=null ? ".1" : ""), d),
                                     t, pt);
                }
            }
        }
    }

    /** Check that a given type can be cast to a given target type.
     *  Return the result of the cast.
     *  @param pos        Position to be used for error reporting.
     *  @param found      The type that is being cast.
     *  @param req        The target type of the cast.
     */
    Type checkCastable(DiagnosticPosition pos, Type found, Type req) {
        if (found.tag == FORALL) {
            instantiatePoly(pos, (ForAll) found, req, castWarner(pos, found, req));
            return req;
        } else if (types.isCastable(found, req, castWarner(pos, found, req))) {
            return req;
        } else {
            return typeError(pos,
                             diags.fragment("inconvertible.types"),
                             found, req);
        }
    }
//where
        /** Is type a type variable, or a (possibly multi-dimensional) array of
         *  type variables?
         */
        boolean isTypeVar(Type t) {
            return t.tag == TYPEVAR || t.tag == ARRAY && isTypeVar(types.elemtype(t));
        }

    /** Check that a type is within some bounds.
     *
     *  Used in TypeApply to verify that, e.g., X in V<X> is a valid
     *  type argument.
     *  @param pos           Position to be used for error reporting.
     *  @param a             The type that should be bounded by bs.
     *  @param bs            The bound.
     */
    private void checkExtends(DiagnosticPosition pos, Type a, TypeVar bs) {
         if (a.isUnbound()) {
             return;
         } else if (a.tag != WILDCARD) {
             a = types.upperBound(a);
             for (List<Type> l = types.getBounds(bs); l.nonEmpty(); l = l.tail) {
                 if (!types.isSubtype(a, l.head)) {
                     log.error(pos, "not.within.bounds", a);
                     return;
                 }
             }
         } else if (a.isExtendsBound()) {
             if (!types.isCastable(bs.getUpperBound(), types.upperBound(a), Warner.noWarnings))
                 log.error(pos, "not.within.bounds", a);
         } else if (a.isSuperBound()) {
             if (types.notSoftSubtype(types.lowerBound(a), bs.getUpperBound()))
                 log.error(pos, "not.within.bounds", a);
         }
     }

    /** Check that a type is within some bounds.
     *
     *  Used in TypeApply to verify that, e.g., X in V<X> is a valid
     *  type argument.
     *  @param pos           Position to be used for error reporting.
     *  @param a             The type that should be bounded by bs.
     *  @param bs            The bound.
     */
    private void checkCapture(JCTypeApply tree) {
        List<JCExpression> args = tree.getTypeArguments();
        for (Type arg : types.capture(tree.type).getTypeArguments()) {
            if (arg.tag == TYPEVAR && arg.getUpperBound().isErroneous()) {
                log.error(args.head.pos, "not.within.bounds", args.head.type);
                break;
            }
            args = args.tail;
        }
     }

    /** Check that type is different from 'void'.
     *  @param pos           Position to be used for error reporting.
     *  @param t             The type to be checked.
     */
    Type checkNonVoid(DiagnosticPosition pos, Type t) {
        if (t.tag == VOID) {
            log.error(pos, "void.not.allowed.here");
            return types.createErrorType(t);
        } else {
            return t;
        }
    }

    /** Check that type is a class or interface type.
     *  @param pos           Position to be used for error reporting.
     *  @param t             The type to be checked.
     */
    Type checkClassType(DiagnosticPosition pos, Type t) {
        if (t.tag != CLASS && t.tag != ERROR)
            return typeTagError(pos,
                                diags.fragment("type.req.class"),
                                (t.tag == TYPEVAR)
                                ? diags.fragment("type.parameter", t)
                                : t);
        else
            return t;
    }

    /** Check that type is a class or interface type.
     *  @param pos           Position to be used for error reporting.
     *  @param t             The type to be checked.
     *  @param noBounds    True if type bounds are illegal here.
     */
    Type checkClassType(DiagnosticPosition pos, Type t, boolean noBounds) {
        t = checkClassType(pos, t);
        if (noBounds && t.isParameterized()) {
            List<Type> args = t.getTypeArguments();
            while (args.nonEmpty()) {
                if (args.head.tag == WILDCARD)
                    return typeTagError(pos,
                                        log.getLocalizedString("type.req.exact"),
                                        args.head);
                args = args.tail;
            }
        }
        return t;
    }

    /** Check that type is a reifiable class, interface or array type.
     *  @param pos           Position to be used for error reporting.
     *  @param t             The type to be checked.
     */
    Type checkReifiableReferenceType(DiagnosticPosition pos, Type t) {
        if (t.tag != CLASS && t.tag != ARRAY && t.tag != ERROR) {
            return typeTagError(pos,
                                diags.fragment("type.req.class.array"),
                                t);
        } else if (!types.isReifiable(t)) {
            log.error(pos, "illegal.generic.type.for.instof");
            return types.createErrorType(t);
        } else {
            return t;
        }
    }

    /** Check that type is a reference type, i.e. a class, interface or array type
     *  or a type variable.
     *  @param pos           Position to be used for error reporting.
     *  @param t             The type to be checked.
     */
    Type checkRefType(DiagnosticPosition pos, Type t) {
        switch (t.tag) {
        case CLASS:
        case ARRAY:
        case TYPEVAR:
        case WILDCARD:
        case ERROR:
            return t;
        default:
            return typeTagError(pos,
                                diags.fragment("type.req.ref"),
                                t);
        }
    }

    /** Check that each type is a reference type, i.e. a class, interface or array type
     *  or a type variable.
     *  @param trees         Original trees, used for error reporting.
     *  @param types         The types to be checked.
     */
    List<Type> checkRefTypes(List<JCExpression> trees, List<Type> types) {
        List<JCExpression> tl = trees;
        for (List<Type> l = types; l.nonEmpty(); l = l.tail) {
            l.head = checkRefType(tl.head.pos(), l.head);
            tl = tl.tail;
        }
        return types;
    }

    /** Check that type is a null or reference type.
     *  @param pos           Position to be used for error reporting.
     *  @param t             The type to be checked.
     */
    Type checkNullOrRefType(DiagnosticPosition pos, Type t) {
        switch (t.tag) {
        case CLASS:
        case ARRAY:
        case TYPEVAR:
        case WILDCARD:
        case BOT:
        case ERROR:
            return t;
        default:
            return typeTagError(pos,
                                diags.fragment("type.req.ref"),
                                t);
        }
    }

    /** Check that flag set does not contain elements of two conflicting sets. s
     *  Return true if it doesn't.
     *  @param pos           Position to be used for error reporting.
     *  @param flags         The set of flags to be checked.
     *  @param set1          Conflicting flags set #1.
     *  @param set2          Conflicting flags set #2.
     */
    boolean checkDisjoint(DiagnosticPosition pos, long flags, long set1, long set2) {
        if ((flags & set1) != 0 && (flags & set2) != 0) {
            log.error(pos,
                      "illegal.combination.of.modifiers",
                      asFlagSet(TreeInfo.firstFlag(flags & set1)),
                      asFlagSet(TreeInfo.firstFlag(flags & set2)));
            return false;
        } else
            return true;
    }

    /** Check that given modifiers are legal for given symbol and
     *  return modifiers together with any implicit modififiers for that symbol.
     *  Warning: we can't use flags() here since this method
     *  is called during class enter, when flags() would cause a premature
     *  completion.
     *  @param pos           Position to be used for error reporting.
     *  @param flags         The set of modifiers given in a definition.
     *  @param sym           The defined symbol.
     */
    long checkFlags(DiagnosticPosition pos, long flags, Symbol sym, JCTree tree) {
        long mask;
        long implicit = 0;
        switch (sym.kind) {
        case VAR:
            if (sym.owner.kind != TYP)
                mask = LocalVarFlags;
            else if ((sym.owner.flags_field & INTERFACE) != 0)
                mask = implicit = InterfaceVarFlags;
            else
                mask = VarFlags;
            break;
        case MTH:
            if (sym.name == names.init) {
                if ((sym.owner.flags_field & ENUM) != 0) {
                    // enum constructors cannot be declared public or
                    // protected and must be implicitly or explicitly
                    // private
                    implicit = PRIVATE;
                    mask = PRIVATE;
                } else
                    mask = ConstructorFlags;
            }  else if ((sym.owner.flags_field & INTERFACE) != 0)
                mask = implicit = InterfaceMethodFlags;
            else {
                mask = MethodFlags;
            }
            // Imply STRICTFP if owner has STRICTFP set.
            if (((flags|implicit) & Flags.ABSTRACT) == 0)
              implicit |= sym.owner.flags_field & STRICTFP;
            break;
        case TYP:
            if (sym.isLocal()) {
                mask = LocalClassFlags;
                if (sym.name.isEmpty()) { // Anonymous class
                    // Anonymous classes in static methods are themselves static;
                    // that's why we admit STATIC here.
                    mask |= STATIC;
                    // JLS: Anonymous classes are final.
                    implicit |= FINAL;
                }
                if ((sym.owner.flags_field & STATIC) == 0 &&
                    (flags & ENUM) != 0)
                    log.error(pos, "enums.must.be.static");
            } else if (sym.owner.kind == TYP) {
                mask = MemberClassFlags;
                if (sym.owner.owner.kind == PCK ||
                    (sym.owner.flags_field & STATIC) != 0)
                    mask |= STATIC;
                else if ((flags & ENUM) != 0)
                    log.error(pos, "enums.must.be.static");
                // Nested interfaces and enums are always STATIC (Spec ???)
                if ((flags & (INTERFACE | ENUM)) != 0 ) implicit = STATIC;
            } else {
                mask = ClassFlags;
            }
            // Interfaces are always ABSTRACT
            if ((flags & INTERFACE) != 0) implicit |= ABSTRACT;

            if ((flags & ENUM) != 0) {
                // enums can't be declared abstract or final
                mask &= ~(ABSTRACT | FINAL);
                implicit |= implicitEnumFinalFlag(tree);
            }
            // Imply STRICTFP if owner has STRICTFP set.
            implicit |= sym.owner.flags_field & STRICTFP;
            break;
        default:
            throw new AssertionError();
        }
        long illegal = flags & StandardFlags & ~mask;
        if (illegal != 0) {
            if ((illegal & INTERFACE) != 0) {
                log.error(pos, "intf.not.allowed.here");
                mask |= INTERFACE;
            }
            else {
                log.error(pos,
                          "mod.not.allowed.here", asFlagSet(illegal));
            }
        }
        else if ((sym.kind == TYP ||
                  // ISSUE: Disallowing abstract&private is no longer appropriate
                  // in the presence of inner classes. Should it be deleted here?
                  checkDisjoint(pos, flags,
                                ABSTRACT,
                                PRIVATE | STATIC))
                 &&
                 checkDisjoint(pos, flags,
                               ABSTRACT | INTERFACE,
                               FINAL | NATIVE | SYNCHRONIZED)
                 &&
                 checkDisjoint(pos, flags,
                               PUBLIC,
                               PRIVATE | PROTECTED)
                 &&
                 checkDisjoint(pos, flags,
                               PRIVATE,
                               PUBLIC | PROTECTED)
                 &&
                 checkDisjoint(pos, flags,
                               FINAL,
                               VOLATILE)
                 &&
                 (sym.kind == TYP ||
                  checkDisjoint(pos, flags,
                                ABSTRACT | NATIVE,
                                STRICTFP))) {
            // skip
        }
        return flags & (mask | ~StandardFlags) | implicit;
    }


    /** Determine if this enum should be implicitly final.
     *
     *  If the enum has no specialized enum contants, it is final.
     *
     *  If the enum does have specialized enum contants, it is
     *  <i>not</i> final.
     */
    private long implicitEnumFinalFlag(JCTree tree) {
        if (tree.getTag() != JCTree.CLASSDEF) return 0;
        class SpecialTreeVisitor extends JCTree.Visitor {
            boolean specialized;
            SpecialTreeVisitor() {
                this.specialized = false;
            };

            public void visitTree(JCTree tree) { /* no-op */ }

            public void visitVarDef(JCVariableDecl tree) {
                if ((tree.mods.flags & ENUM) != 0) {
                    if (tree.init instanceof JCNewClass &&
                        ((JCNewClass) tree.init).def != null) {
                        specialized = true;
                    }
                }
            }
        }

        SpecialTreeVisitor sts = new SpecialTreeVisitor();
        JCClassDecl cdef = (JCClassDecl) tree;
        for (JCTree defs: cdef.defs) {
            defs.accept(sts);
            if (sts.specialized) return 0;
        }
        return FINAL;
    }

/* *************************************************************************
 * Type Validation
 **************************************************************************/

    /** Validate a type expression. That is,
     *  check that all type arguments of a parametric type are within
     *  their bounds. This must be done in a second phase after type attributon
     *  since a class might have a subclass as type parameter bound. E.g:
     *
     *  class B<A extends C> { ... }
     *  class C extends B<C> { ... }
     *
     *  and we can't make sure that the bound is already attributed because
     *  of possible cycles.
     */
    private Validator validator = new Validator();

    /** Visitor method: Validate a type expression, if it is not null, catching
     *  and reporting any completion failures.
     */
    void validate(JCTree tree, Env<AttrContext> env) {
        try {
            if (tree != null) {
                validator.env = env;
                tree.accept(validator);
                checkRaw(tree, env);
            }
        } catch (CompletionFailure ex) {
            completionError(tree.pos(), ex);
        }
    }
    //where
    void checkRaw(JCTree tree, Env<AttrContext> env) {
        if (lint.isEnabled(Lint.LintCategory.RAW) &&
            tree.type.tag == CLASS &&
            !env.enclClass.name.isEmpty() &&  //anonymous or intersection
            tree.type.isRaw()) {
            log.warning(tree.pos(), "raw.class.use", tree.type, tree.type.tsym.type);
        }
    }

    /** Visitor method: Validate a list of type expressions.
     */
    void validate(List<? extends JCTree> trees, Env<AttrContext> env) {
        for (List<? extends JCTree> l = trees; l.nonEmpty(); l = l.tail)
            validate(l.head, env);
    }

    /** A visitor class for type validation.
     */
    class Validator extends JCTree.Visitor {

        public void visitTypeArray(JCArrayTypeTree tree) {
            validate(tree.elemtype, env);
        }

        public void visitTypeApply(JCTypeApply tree) {
            if (tree.type.tag == CLASS) {
                List<Type> formals = tree.type.tsym.type.allparams();
                List<Type> actuals = tree.type.allparams();
                List<JCExpression> args = tree.arguments;
                List<Type> forms = tree.type.tsym.type.getTypeArguments();
                ListBuffer<TypeVar> tvars_buf = new ListBuffer<TypeVar>();

                // For matching pairs of actual argument types `a' and
                // formal type parameters with declared bound `b' ...
                while (args.nonEmpty() && forms.nonEmpty()) {
                    validate(args.head, env);

                    // exact type arguments needs to know their
                    // bounds (for upper and lower bound
                    // calculations).  So we create new TypeVars with
                    // bounds substed with actuals.
                    tvars_buf.append(types.substBound(((TypeVar)forms.head),
                                                      formals,
                                                      actuals));

                    args = args.tail;
                    forms = forms.tail;
                }

                args = tree.arguments;
                List<Type> tvars_cap = types.substBounds(formals,
                                          formals,
                                          types.capture(tree.type).allparams());
                while (args.nonEmpty() && tvars_cap.nonEmpty()) {
                    // Let the actual arguments know their bound
                    args.head.type.withTypeVar((TypeVar)tvars_cap.head);
                    args = args.tail;
                    tvars_cap = tvars_cap.tail;
                }

                args = tree.arguments;
                List<TypeVar> tvars = tvars_buf.toList();

                while (args.nonEmpty() && tvars.nonEmpty()) {
                    checkExtends(args.head.pos(),
                                 args.head.type,
                                 tvars.head);
                    args = args.tail;
                    tvars = tvars.tail;
                }

                checkCapture(tree);

                // Check that this type is either fully parameterized, or
                // not parameterized at all.
                if (tree.type.getEnclosingType().isRaw())
                    log.error(tree.pos(), "improperly.formed.type.inner.raw.param");
                if (tree.clazz.getTag() == JCTree.SELECT)
                    visitSelectInternal((JCFieldAccess)tree.clazz);
            }
        }

        public void visitTypeParameter(JCTypeParameter tree) {
            validate(tree.bounds, env);
            checkClassBounds(tree.pos(), tree.type);
        }

        @Override
        public void visitWildcard(JCWildcard tree) {
            if (tree.inner != null)
                validate(tree.inner, env);
        }

        public void visitSelect(JCFieldAccess tree) {
            if (tree.type.tag == CLASS) {
                visitSelectInternal(tree);

                // Check that this type is either fully parameterized, or
                // not parameterized at all.
                if (tree.selected.type.isParameterized() && tree.type.tsym.type.getTypeArguments().nonEmpty())
                    log.error(tree.pos(), "improperly.formed.type.param.missing");
            }
        }
        public void visitSelectInternal(JCFieldAccess tree) {
            if (tree.type.tsym.isStatic() &&
                tree.selected.type.isParameterized()) {
                // The enclosing type is not a class, so we are
                // looking at a static member type.  However, the
                // qualifying expression is parameterized.
                log.error(tree.pos(), "cant.select.static.class.from.param.type");
            } else {
                // otherwise validate the rest of the expression
                tree.selected.accept(this);
            }
        }

        /** Default visitor method: do nothing.
         */
        public void visitTree(JCTree tree) {
        }

        Env<AttrContext> env;
    }

/* *************************************************************************
 * Exception checking
 **************************************************************************/

    /* The following methods treat classes as sets that contain
     * the class itself and all their subclasses
     */

    /** Is given type a subtype of some of the types in given list?
     */
    boolean subset(Type t, List<Type> ts) {
        for (List<Type> l = ts; l.nonEmpty(); l = l.tail)
            if (types.isSubtype(t, l.head)) return true;
        return false;
    }

    /** Is given type a subtype or supertype of
     *  some of the types in given list?
     */
    boolean intersects(Type t, List<Type> ts) {
        for (List<Type> l = ts; l.nonEmpty(); l = l.tail)
            if (types.isSubtype(t, l.head) || types.isSubtype(l.head, t)) return true;
        return false;
    }

    /** Add type set to given type list, unless it is a subclass of some class
     *  in the list.
     */
    List<Type> incl(Type t, List<Type> ts) {
        return subset(t, ts) ? ts : excl(t, ts).prepend(t);
    }

    /** Remove type set from type set list.
     */
    List<Type> excl(Type t, List<Type> ts) {
        if (ts.isEmpty()) {
            return ts;
        } else {
            List<Type> ts1 = excl(t, ts.tail);
            if (types.isSubtype(ts.head, t)) return ts1;
            else if (ts1 == ts.tail) return ts;
            else return ts1.prepend(ts.head);
        }
    }

    /** Form the union of two type set lists.
     */
    List<Type> union(List<Type> ts1, List<Type> ts2) {
        List<Type> ts = ts1;
        for (List<Type> l = ts2; l.nonEmpty(); l = l.tail)
            ts = incl(l.head, ts);
        return ts;
    }

    /** Form the difference of two type lists.
     */
    List<Type> diff(List<Type> ts1, List<Type> ts2) {
        List<Type> ts = ts1;
        for (List<Type> l = ts2; l.nonEmpty(); l = l.tail)
            ts = excl(l.head, ts);
        return ts;
    }

    /** Form the intersection of two type lists.
     */
    public List<Type> intersect(List<Type> ts1, List<Type> ts2) {
        List<Type> ts = List.nil();
        for (List<Type> l = ts1; l.nonEmpty(); l = l.tail)
            if (subset(l.head, ts2)) ts = incl(l.head, ts);
        for (List<Type> l = ts2; l.nonEmpty(); l = l.tail)
            if (subset(l.head, ts1)) ts = incl(l.head, ts);
        return ts;
    }

    /** Is exc an exception symbol that need not be declared?
     */
    boolean isUnchecked(ClassSymbol exc) {
        return
            exc.kind == ERR ||
            exc.isSubClass(syms.errorType.tsym, types) ||
            exc.isSubClass(syms.runtimeExceptionType.tsym, types);
    }

    /** Is exc an exception type that need not be declared?
     */
    boolean isUnchecked(Type exc) {
        return
            (exc.tag == TYPEVAR) ? isUnchecked(types.supertype(exc)) :
            (exc.tag == CLASS) ? isUnchecked((ClassSymbol)exc.tsym) :
            exc.tag == BOT;
    }

    /** Same, but handling completion failures.
     */
    boolean isUnchecked(DiagnosticPosition pos, Type exc) {
        try {
            return isUnchecked(exc);
        } catch (CompletionFailure ex) {
            completionError(pos, ex);
            return true;
        }
    }

    /** Is exc handled by given exception list?
     */
    boolean isHandled(Type exc, List<Type> handled) {
        return isUnchecked(exc) || subset(exc, handled);
    }

    /** Return all exceptions in thrown list that are not in handled list.
     *  @param thrown     The list of thrown exceptions.
     *  @param handled    The list of handled exceptions.
     */
    List<Type> unHandled(List<Type> thrown, List<Type> handled) {
        List<Type> unhandled = List.nil();
        for (List<Type> l = thrown; l.nonEmpty(); l = l.tail)
            if (!isHandled(l.head, handled)) unhandled = unhandled.prepend(l.head);
        return unhandled;
    }

/* *************************************************************************
 * Overriding/Implementation checking
 **************************************************************************/

    /** The level of access protection given by a flag set,
     *  where PRIVATE is highest and PUBLIC is lowest.
     */
    static int protection(long flags) {
        switch ((short)(flags & AccessFlags)) {
        case PRIVATE: return 3;
        case PROTECTED: return 1;
        default:
        case PUBLIC: return 0;
        case 0: return 2;
        }
    }

    /** A customized "cannot override" error message.
     *  @param m      The overriding method.
     *  @param other  The overridden method.
     *  @return       An internationalized string.
     */
    Object cannotOverride(MethodSymbol m, MethodSymbol other) {
        String key;
        if ((other.owner.flags() & INTERFACE) == 0)
            key = "cant.override";
        else if ((m.owner.flags() & INTERFACE) == 0)
            key = "cant.implement";
        else
            key = "clashes.with";
        return diags.fragment(key, m, m.location(), other, other.location());
    }

    /** A customized "override" warning message.
     *  @param m      The overriding method.
     *  @param other  The overridden method.
     *  @return       An internationalized string.
     */
    Object uncheckedOverrides(MethodSymbol m, MethodSymbol other) {
        String key;
        if ((other.owner.flags() & INTERFACE) == 0)
            key = "unchecked.override";
        else if ((m.owner.flags() & INTERFACE) == 0)
            key = "unchecked.implement";
        else
            key = "unchecked.clash.with";
        return diags.fragment(key, m, m.location(), other, other.location());
    }

    /** A customized "override" warning message.
     *  @param m      The overriding method.
     *  @param other  The overridden method.
     *  @return       An internationalized string.
     */
    Object varargsOverrides(MethodSymbol m, MethodSymbol other) {
        String key;
        if ((other.owner.flags() & INTERFACE) == 0)
            key = "varargs.override";
        else  if ((m.owner.flags() & INTERFACE) == 0)
            key = "varargs.implement";
        else
            key = "varargs.clash.with";
        return diags.fragment(key, m, m.location(), other, other.location());
    }

    /** Check that this method conforms with overridden method 'other'.
     *  where `origin' is the class where checking started.
     *  Complications:
     *  (1) Do not check overriding of synthetic methods
     *      (reason: they might be final).
     *      todo: check whether this is still necessary.
     *  (2) Admit the case where an interface proxy throws fewer exceptions
     *      than the method it implements. Augment the proxy methods with the
     *      undeclared exceptions in this case.
     *  (3) When generics are enabled, admit the case where an interface proxy
     *      has a result type
     *      extended by the result type of the method it implements.
     *      Change the proxies result type to the smaller type in this case.
     *
     *  @param tree         The tree from which positions
     *                      are extracted for errors.
     *  @param m            The overriding method.
     *  @param other        The overridden method.
     *  @param origin       The class of which the overriding method
     *                      is a member.
     */
    void checkOverride(JCTree tree,
                       MethodSymbol m,
                       MethodSymbol other,
                       ClassSymbol origin) {
        // Don't check overriding of synthetic methods or by bridge methods.
        if ((m.flags() & (SYNTHETIC|BRIDGE)) != 0 || (other.flags() & SYNTHETIC) != 0) {
            return;
        }

        // Error if static method overrides instance method (JLS 8.4.6.2).
        if ((m.flags() & STATIC) != 0 &&
                   (other.flags() & STATIC) == 0) {
            log.error(TreeInfo.diagnosticPositionFor(m, tree), "override.static",
                      cannotOverride(m, other));
            return;
        }

        // Error if instance method overrides static or final
        // method (JLS 8.4.6.1).
        if ((other.flags() & FINAL) != 0 ||
                 (m.flags() & STATIC) == 0 &&
                 (other.flags() & STATIC) != 0) {
            log.error(TreeInfo.diagnosticPositionFor(m, tree), "override.meth",
                      cannotOverride(m, other),
                      asFlagSet(other.flags() & (FINAL | STATIC)));
            return;
        }

        if ((m.owner.flags() & ANNOTATION) != 0) {
            // handled in validateAnnotationMethod
            return;
        }

        // Error if overriding method has weaker access (JLS 8.4.6.3).
        if ((origin.flags() & INTERFACE) == 0 &&
                 protection(m.flags()) > protection(other.flags())) {
            log.error(TreeInfo.diagnosticPositionFor(m, tree), "override.weaker.access",
                      cannotOverride(m, other),
                      other.flags() == 0 ?
                          Flag.PACKAGE :
                          asFlagSet(other.flags() & AccessFlags));
            return;
        }

        Type mt = types.memberType(origin.type, m);
        Type ot = types.memberType(origin.type, other);
        // Error if overriding result type is different
        // (or, in the case of generics mode, not a subtype) of
        // overridden result type. We have to rename any type parameters
        // before comparing types.
        List<Type> mtvars = mt.getTypeArguments();
        List<Type> otvars = ot.getTypeArguments();
        Type mtres = mt.getReturnType();
        Type otres = types.subst(ot.getReturnType(), otvars, mtvars);

        overrideWarner.warned = false;
        boolean resultTypesOK =
            types.returnTypeSubstitutable(mt, ot, otres, overrideWarner);
        if (!resultTypesOK) {
            if (!source.allowCovariantReturns() &&
                m.owner != origin &&
                m.owner.isSubClass(other.owner, types)) {
                // allow limited interoperability with covariant returns
            } else {
                typeError(TreeInfo.diagnosticPositionFor(m, tree),
                          diags.fragment("override.incompatible.ret",
                                         cannotOverride(m, other)),
                          mtres, otres);
                return;
            }
        } else if (overrideWarner.warned) {
            warnUnchecked(TreeInfo.diagnosticPositionFor(m, tree),
                          "prob.found.req",
                          diags.fragment("override.unchecked.ret",
                                              uncheckedOverrides(m, other)),
                          mtres, otres);
        }

        // Error if overriding method throws an exception not reported
        // by overridden method.
        List<Type> otthrown = types.subst(ot.getThrownTypes(), otvars, mtvars);
        List<Type> unhandled = unHandled(mt.getThrownTypes(), otthrown);
        if (unhandled.nonEmpty()) {
            log.error(TreeInfo.diagnosticPositionFor(m, tree),
                      "override.meth.doesnt.throw",
                      cannotOverride(m, other),
                      unhandled.head);
            return;
        }

        // Optional warning if varargs don't agree
        if ((((m.flags() ^ other.flags()) & Flags.VARARGS) != 0)
            && lint.isEnabled(Lint.LintCategory.OVERRIDES)) {
            log.warning(TreeInfo.diagnosticPositionFor(m, tree),
                        ((m.flags() & Flags.VARARGS) != 0)
                        ? "override.varargs.missing"
                        : "override.varargs.extra",
                        varargsOverrides(m, other));
        }

        // Warn if instance method overrides bridge method (compiler spec ??)
        if ((other.flags() & BRIDGE) != 0) {
            log.warning(TreeInfo.diagnosticPositionFor(m, tree), "override.bridge",
                        uncheckedOverrides(m, other));
        }

        // Warn if a deprecated method overridden by a non-deprecated one.
        if ((other.flags() & DEPRECATED) != 0
            && (m.flags() & DEPRECATED) == 0
            && m.outermostClass() != other.outermostClass()
            && !isDeprecatedOverrideIgnorable(other, origin)) {
            warnDeprecated(TreeInfo.diagnosticPositionFor(m, tree), other);
        }
    }
    // where
        private boolean isDeprecatedOverrideIgnorable(MethodSymbol m, ClassSymbol origin) {
            // If the method, m, is defined in an interface, then ignore the issue if the method
            // is only inherited via a supertype and also implemented in the supertype,
            // because in that case, we will rediscover the issue when examining the method
            // in the supertype.
            // If the method, m, is not defined in an interface, then the only time we need to
            // address the issue is when the method is the supertype implemementation: any other
            // case, we will have dealt with when examining the supertype classes
            ClassSymbol mc = m.enclClass();
            Type st = types.supertype(origin.type);
            if (st.tag != CLASS)
                return true;
            MethodSymbol stimpl = m.implementation((ClassSymbol)st.tsym, types, false);

            if (mc != null && ((mc.flags() & INTERFACE) != 0)) {
                List<Type> intfs = types.interfaces(origin.type);
                return (intfs.contains(mc.type) ? false : (stimpl != null));
            }
            else
                return (stimpl != m);
        }


    // used to check if there were any unchecked conversions
    Warner overrideWarner = new Warner();

    /** Check that a class does not inherit two concrete methods
     *  with the same signature.
     *  @param pos          Position to be used for error reporting.
     *  @param site         The class type to be checked.
     */
    public void checkCompatibleConcretes(DiagnosticPosition pos, Type site) {
        Type sup = types.supertype(site);
        if (sup.tag != CLASS) return;

        for (Type t1 = sup;
             t1.tsym.type.isParameterized();
             t1 = types.supertype(t1)) {
            for (Scope.Entry e1 = t1.tsym.members().elems;
                 e1 != null;
                 e1 = e1.sibling) {
                Symbol s1 = e1.sym;
                if (s1.kind != MTH ||
                    (s1.flags() & (STATIC|SYNTHETIC|BRIDGE)) != 0 ||
                    !s1.isInheritedIn(site.tsym, types) ||
                    ((MethodSymbol)s1).implementation(site.tsym,
                                                      types,
                                                      true) != s1)
                    continue;
                Type st1 = types.memberType(t1, s1);
                int s1ArgsLength = st1.getParameterTypes().length();
                if (st1 == s1.type) continue;

                for (Type t2 = sup;
                     t2.tag == CLASS;
                     t2 = types.supertype(t2)) {
                    for (Scope.Entry e2 = t2.tsym.members().lookup(s1.name);
                         e2.scope != null;
                         e2 = e2.next()) {
                        Symbol s2 = e2.sym;
                        if (s2 == s1 ||
                            s2.kind != MTH ||
                            (s2.flags() & (STATIC|SYNTHETIC|BRIDGE)) != 0 ||
                            s2.type.getParameterTypes().length() != s1ArgsLength ||
                            !s2.isInheritedIn(site.tsym, types) ||
                            ((MethodSymbol)s2).implementation(site.tsym,
                                                              types,
                                                              true) != s2)
                            continue;
                        Type st2 = types.memberType(t2, s2);
                        if (types.overrideEquivalent(st1, st2))
                            log.error(pos, "concrete.inheritance.conflict",
                                      s1, t1, s2, t2, sup);
                    }
                }
            }
        }
    }

    /** Check that classes (or interfaces) do not each define an abstract
     *  method with same name and arguments but incompatible return types.
     *  @param pos          Position to be used for error reporting.
     *  @param t1           The first argument type.
     *  @param t2           The second argument type.
     */
    public boolean checkCompatibleAbstracts(DiagnosticPosition pos,
                                            Type t1,
                                            Type t2) {
        return checkCompatibleAbstracts(pos, t1, t2,
                                        types.makeCompoundType(t1, t2));
    }

    public boolean checkCompatibleAbstracts(DiagnosticPosition pos,
                                            Type t1,
                                            Type t2,
                                            Type site) {
        Symbol sym = firstIncompatibility(t1, t2, site);
        if (sym != null) {
            log.error(pos, "types.incompatible.diff.ret",
                      t1, t2, sym.name +
                      "(" + types.memberType(t2, sym).getParameterTypes() + ")");
            return false;
        }
        return true;
    }

    /** Return the first method which is defined with same args
     *  but different return types in two given interfaces, or null if none
     *  exists.
     *  @param t1     The first type.
     *  @param t2     The second type.
     *  @param site   The most derived type.
     *  @returns symbol from t2 that conflicts with one in t1.
     */
    private Symbol firstIncompatibility(Type t1, Type t2, Type site) {
        Map<TypeSymbol,Type> interfaces1 = new HashMap<TypeSymbol,Type>();
        closure(t1, interfaces1);
        Map<TypeSymbol,Type> interfaces2;
        if (t1 == t2)
            interfaces2 = interfaces1;
        else
            closure(t2, interfaces1, interfaces2 = new HashMap<TypeSymbol,Type>());

        for (Type t3 : interfaces1.values()) {
            for (Type t4 : interfaces2.values()) {
                Symbol s = firstDirectIncompatibility(t3, t4, site);
                if (s != null) return s;
            }
        }
        return null;
    }

    /** Compute all the supertypes of t, indexed by type symbol. */
    private void closure(Type t, Map<TypeSymbol,Type> typeMap) {
        if (t.tag != CLASS) return;
        if (typeMap.put(t.tsym, t) == null) {
            closure(types.supertype(t), typeMap);
            for (Type i : types.interfaces(t))
                closure(i, typeMap);
        }
    }

    /** Compute all the supertypes of t, indexed by type symbol (except thise in typesSkip). */
    private void closure(Type t, Map<TypeSymbol,Type> typesSkip, Map<TypeSymbol,Type> typeMap) {
        if (t.tag != CLASS) return;
        if (typesSkip.get(t.tsym) != null) return;
        if (typeMap.put(t.tsym, t) == null) {
            closure(types.supertype(t), typesSkip, typeMap);
            for (Type i : types.interfaces(t))
                closure(i, typesSkip, typeMap);
        }
    }

    /** Return the first method in t2 that conflicts with a method from t1. */
    private Symbol firstDirectIncompatibility(Type t1, Type t2, Type site) {
        for (Scope.Entry e1 = t1.tsym.members().elems; e1 != null; e1 = e1.sibling) {
            Symbol s1 = e1.sym;
            Type st1 = null;
            if (s1.kind != MTH || !s1.isInheritedIn(site.tsym, types)) continue;
            Symbol impl = ((MethodSymbol)s1).implementation(site.tsym, types, false);
            if (impl != null && (impl.flags() & ABSTRACT) == 0) continue;
            for (Scope.Entry e2 = t2.tsym.members().lookup(s1.name); e2.scope != null; e2 = e2.next()) {
                Symbol s2 = e2.sym;
                if (s1 == s2) continue;
                if (s2.kind != MTH || !s2.isInheritedIn(site.tsym, types)) continue;
                if (st1 == null) st1 = types.memberType(t1, s1);
                Type st2 = types.memberType(t2, s2);
                if (types.overrideEquivalent(st1, st2)) {
                    List<Type> tvars1 = st1.getTypeArguments();
                    List<Type> tvars2 = st2.getTypeArguments();
                    Type rt1 = st1.getReturnType();
                    Type rt2 = types.subst(st2.getReturnType(), tvars2, tvars1);
                    boolean compat =
                        types.isSameType(rt1, rt2) ||
                        rt1.tag >= CLASS && rt2.tag >= CLASS &&
                        (types.covariantReturnType(rt1, rt2, Warner.noWarnings) ||
                         types.covariantReturnType(rt2, rt1, Warner.noWarnings)) ||
                         checkCommonOverriderIn(s1,s2,site);
                    if (!compat) return s2;
                }
            }
        }
        return null;
    }
    //WHERE
    boolean checkCommonOverriderIn(Symbol s1, Symbol s2, Type site) {
        Map<TypeSymbol,Type> supertypes = new HashMap<TypeSymbol,Type>();
        Type st1 = types.memberType(site, s1);
        Type st2 = types.memberType(site, s2);
        closure(site, supertypes);
        for (Type t : supertypes.values()) {
            for (Scope.Entry e = t.tsym.members().lookup(s1.name); e.scope != null; e = e.next()) {
                Symbol s3 = e.sym;
                if (s3 == s1 || s3 == s2 || s3.kind != MTH || (s3.flags() & (BRIDGE|SYNTHETIC)) != 0) continue;
                Type st3 = types.memberType(site,s3);
                if (types.overrideEquivalent(st3, st1) && types.overrideEquivalent(st3, st2)) {
                    if (s3.owner == site.tsym) {
                        return true;
                    }
                    List<Type> tvars1 = st1.getTypeArguments();
                    List<Type> tvars2 = st2.getTypeArguments();
                    List<Type> tvars3 = st3.getTypeArguments();
                    Type rt1 = st1.getReturnType();
                    Type rt2 = st2.getReturnType();
                    Type rt13 = types.subst(st3.getReturnType(), tvars3, tvars1);
                    Type rt23 = types.subst(st3.getReturnType(), tvars3, tvars2);
                    boolean compat =
                        rt13.tag >= CLASS && rt23.tag >= CLASS &&
                        (types.covariantReturnType(rt13, rt1, Warner.noWarnings) &&
                         types.covariantReturnType(rt23, rt2, Warner.noWarnings));
                    if (compat)
                        return true;
                }
            }
        }
        return false;
    }

    /** Check that a given method conforms with any method it overrides.
     *  @param tree         The tree from which positions are extracted
     *                      for errors.
     *  @param m            The overriding method.
     */
    void checkOverride(JCTree tree, MethodSymbol m) {
        ClassSymbol origin = (ClassSymbol)m.owner;
        if ((origin.flags() & ENUM) != 0 && names.finalize.equals(m.name))
            if (m.overrides(syms.enumFinalFinalize, origin, types, false)) {
                log.error(tree.pos(), "enum.no.finalize");
                return;
            }
        for (Type t = types.supertype(origin.type); t.tag == CLASS;
             t = types.supertype(t)) {
            TypeSymbol c = t.tsym;
            Scope.Entry e = c.members().lookup(m.name);
            while (e.scope != null) {
                if (m.overrides(e.sym, origin, types, false))
                    checkOverride(tree, m, (MethodSymbol)e.sym, origin);
                else if (e.sym.kind == MTH &&
                        e.sym.isInheritedIn(origin, types) &&
                        (e.sym.flags() & SYNTHETIC) == 0 &&
                        !m.isConstructor()) {
                    Type er1 = m.erasure(types);
                    Type er2 = e.sym.erasure(types);
                    if (types.isSameTypes(er1.getParameterTypes(),
                            er2.getParameterTypes())) {
                            log.error(TreeInfo.diagnosticPositionFor(m, tree),
                                    "name.clash.same.erasure.no.override",
                                    m, m.location(),
                                    e.sym, e.sym.location());
                    }
                }
                e = e.next();
            }
        }
    }

    /** Check that all abstract members of given class have definitions.
     *  @param pos          Position to be used for error reporting.
     *  @param c            The class.
     */
    void checkAllDefined(DiagnosticPosition pos, ClassSymbol c) {
        try {
            MethodSymbol undef = firstUndef(c, c);
            if (undef != null) {
                if ((c.flags() & ENUM) != 0 &&
                    types.supertype(c.type).tsym == syms.enumSym &&
                    (c.flags() & FINAL) == 0) {
                    // add the ABSTRACT flag to an enum
                    c.flags_field |= ABSTRACT;
                } else {
                    MethodSymbol undef1 =
                        new MethodSymbol(undef.flags(), undef.name,
                                         types.memberType(c.type, undef), undef.owner);
                    log.error(pos, "does.not.override.abstract",
                              c, undef1, undef1.location());
                }
            }
        } catch (CompletionFailure ex) {
            completionError(pos, ex);
        }
    }
//where
        /** Return first abstract member of class `c' that is not defined
         *  in `impl', null if there is none.
         */
        private MethodSymbol firstUndef(ClassSymbol impl, ClassSymbol c) {
            MethodSymbol undef = null;
            // Do not bother to search in classes that are not abstract,
            // since they cannot have abstract members.
            if (c == impl || (c.flags() & (ABSTRACT | INTERFACE)) != 0) {
                Scope s = c.members();
                for (Scope.Entry e = s.elems;
                     undef == null && e != null;
                     e = e.sibling) {
                    if (e.sym.kind == MTH &&
                        (e.sym.flags() & (ABSTRACT|IPROXY)) == ABSTRACT) {
                        MethodSymbol absmeth = (MethodSymbol)e.sym;
                        MethodSymbol implmeth = absmeth.implementation(impl, types, true);
                        if (implmeth == null || implmeth == absmeth)
                            undef = absmeth;
                    }
                }
                if (undef == null) {
                    Type st = types.supertype(c.type);
                    if (st.tag == CLASS)
                        undef = firstUndef(impl, (ClassSymbol)st.tsym);
                }
                for (List<Type> l = types.interfaces(c.type);
                     undef == null && l.nonEmpty();
                     l = l.tail) {
                    undef = firstUndef(impl, (ClassSymbol)l.head.tsym);
                }
            }
            return undef;
        }

    /** Check for cyclic references. Issue an error if the
     *  symbol of the type referred to has a LOCKED flag set.
     *
     *  @param pos      Position to be used for error reporting.
     *  @param t        The type referred to.
     */
    void checkNonCyclic(DiagnosticPosition pos, Type t) {
        checkNonCyclicInternal(pos, t);
    }


    void checkNonCyclic(DiagnosticPosition pos, TypeVar t) {
        checkNonCyclic1(pos, t, List.<TypeVar>nil());
    }

    private void checkNonCyclic1(DiagnosticPosition pos, Type t, List<TypeVar> seen) {
        final TypeVar tv;
        if  (t.tag == TYPEVAR && (t.tsym.flags() & UNATTRIBUTED) != 0)
            return;
        if (seen.contains(t)) {
            tv = (TypeVar)t;
            tv.bound = types.createErrorType(t);
            log.error(pos, "cyclic.inheritance", t);
        } else if (t.tag == TYPEVAR) {
            tv = (TypeVar)t;
            seen = seen.prepend(tv);
            for (Type b : types.getBounds(tv))
                checkNonCyclic1(pos, b, seen);
        }
    }

    /** Check for cyclic references. Issue an error if the
     *  symbol of the type referred to has a LOCKED flag set.
     *
     *  @param pos      Position to be used for error reporting.
     *  @param t        The type referred to.
     *  @returns        True if the check completed on all attributed classes
     */
    private boolean checkNonCyclicInternal(DiagnosticPosition pos, Type t) {
        boolean complete = true; // was the check complete?
        //- System.err.println("checkNonCyclicInternal("+t+");");//DEBUG
        Symbol c = t.tsym;
        if ((c.flags_field & ACYCLIC) != 0) return true;

        if ((c.flags_field & LOCKED) != 0) {
            noteCyclic(pos, (ClassSymbol)c);
        } else if (!c.type.isErroneous()) {
            try {
                c.flags_field |= LOCKED;
                if (c.type.tag == CLASS) {
                    ClassType clazz = (ClassType)c.type;
                    if (clazz.interfaces_field != null)
                        for (List<Type> l=clazz.interfaces_field; l.nonEmpty(); l=l.tail)
                            complete &= checkNonCyclicInternal(pos, l.head);
                    if (clazz.supertype_field != null) {
                        Type st = clazz.supertype_field;
                        if (st != null && st.tag == CLASS)
                            complete &= checkNonCyclicInternal(pos, st);
                    }
                    if (c.owner.kind == TYP)
                        complete &= checkNonCyclicInternal(pos, c.owner.type);
                }
            } finally {
                c.flags_field &= ~LOCKED;
            }
        }
        if (complete)
            complete = ((c.flags_field & UNATTRIBUTED) == 0) && c.completer == null;
        if (complete) c.flags_field |= ACYCLIC;
        return complete;
    }

    /** Note that we found an inheritance cycle. */
    private void noteCyclic(DiagnosticPosition pos, ClassSymbol c) {
        log.error(pos, "cyclic.inheritance", c);
        for (List<Type> l=types.interfaces(c.type); l.nonEmpty(); l=l.tail)
            l.head = types.createErrorType((ClassSymbol)l.head.tsym, Type.noType);
        Type st = types.supertype(c.type);
        if (st.tag == CLASS)
            ((ClassType)c.type).supertype_field = types.createErrorType((ClassSymbol)st.tsym, Type.noType);
        c.type = types.createErrorType(c, c.type);
        c.flags_field |= ACYCLIC;
    }

    /** Check that all methods which implement some
     *  method conform to the method they implement.
     *  @param tree         The class definition whose members are checked.
     */
    void checkImplementations(JCClassDecl tree) {
        checkImplementations(tree, tree.sym);
    }
//where
        /** Check that all methods which implement some
         *  method in `ic' conform to the method they implement.
         */
        void checkImplementations(JCClassDecl tree, ClassSymbol ic) {
            ClassSymbol origin = tree.sym;
            for (List<Type> l = types.closure(ic.type); l.nonEmpty(); l = l.tail) {
                ClassSymbol lc = (ClassSymbol)l.head.tsym;
                if ((allowGenerics || origin != lc) && (lc.flags() & ABSTRACT) != 0) {
                    for (Scope.Entry e=lc.members().elems; e != null; e=e.sibling) {
                        if (e.sym.kind == MTH &&
                            (e.sym.flags() & (STATIC|ABSTRACT)) == ABSTRACT) {
                            MethodSymbol absmeth = (MethodSymbol)e.sym;
                            MethodSymbol implmeth = absmeth.implementation(origin, types, false);
                            if (implmeth != null && implmeth != absmeth &&
                                (implmeth.owner.flags() & INTERFACE) ==
                                (origin.flags() & INTERFACE)) {
                                // don't check if implmeth is in a class, yet
                                // origin is an interface. This case arises only
                                // if implmeth is declared in Object. The reason is
                                // that interfaces really don't inherit from
                                // Object it's just that the compiler represents
                                // things that way.
                                checkOverride(tree, implmeth, absmeth, origin);
                            }
                        }
                    }
                }
            }
        }

    /** Check that all abstract methods implemented by a class are
     *  mutually compatible.
     *  @param pos          Position to be used for error reporting.
     *  @param c            The class whose interfaces are checked.
     */
    void checkCompatibleSupertypes(DiagnosticPosition pos, Type c) {
        List<Type> supertypes = types.interfaces(c);
        Type supertype = types.supertype(c);
        if (supertype.tag == CLASS &&
            (supertype.tsym.flags() & ABSTRACT) != 0)
            supertypes = supertypes.prepend(supertype);
        for (List<Type> l = supertypes; l.nonEmpty(); l = l.tail) {
            if (allowGenerics && !l.head.getTypeArguments().isEmpty() &&
                !checkCompatibleAbstracts(pos, l.head, l.head, c))
                return;
            for (List<Type> m = supertypes; m != l; m = m.tail)
                if (!checkCompatibleAbstracts(pos, l.head, m.head, c))
                    return;
        }
        checkCompatibleConcretes(pos, c);
    }

    /** Check that class c does not implement directly or indirectly
     *  the same parameterized interface with two different argument lists.
     *  @param pos          Position to be used for error reporting.
     *  @param type         The type whose interfaces are checked.
     */
    void checkClassBounds(DiagnosticPosition pos, Type type) {
        checkClassBounds(pos, new HashMap<TypeSymbol,Type>(), type);
    }
//where
        /** Enter all interfaces of type `type' into the hash table `seensofar'
         *  with their class symbol as key and their type as value. Make
         *  sure no class is entered with two different types.
         */
        void checkClassBounds(DiagnosticPosition pos,
                              Map<TypeSymbol,Type> seensofar,
                              Type type) {
            if (type.isErroneous()) return;
            for (List<Type> l = types.interfaces(type); l.nonEmpty(); l = l.tail) {
                Type it = l.head;
                Type oldit = seensofar.put(it.tsym, it);
                if (oldit != null) {
                    List<Type> oldparams = oldit.allparams();
                    List<Type> newparams = it.allparams();
                    if (!types.containsTypeEquivalent(oldparams, newparams))
                        log.error(pos, "cant.inherit.diff.arg",
                                  it.tsym, Type.toString(oldparams),
                                  Type.toString(newparams));
                }
                checkClassBounds(pos, seensofar, it);
            }
            Type st = types.supertype(type);
            if (st != null) checkClassBounds(pos, seensofar, st);
        }

    /** Enter interface into into set.
     *  If it existed already, issue a "repeated interface" error.
     */
    void checkNotRepeated(DiagnosticPosition pos, Type it, Set<Type> its) {
        if (its.contains(it))
            log.error(pos, "repeated.interface");
        else {
            its.add(it);
        }
    }

/* *************************************************************************
 * Check annotations
 **************************************************************************/

    /** Annotation types are restricted to primitives, String, an
     *  enum, an annotation, Class, Class<?>, Class<? extends
     *  Anything>, arrays of the preceding.
     */
    void validateAnnotationType(JCTree restype) {
        // restype may be null if an error occurred, so don't bother validating it
        if (restype != null) {
            validateAnnotationType(restype.pos(), restype.type);
        }
    }

    void validateAnnotationType(DiagnosticPosition pos, Type type) {
        if (type.isPrimitive()) return;
        if (types.isSameType(type, syms.stringType)) return;
        if ((type.tsym.flags() & Flags.ENUM) != 0) return;
        if ((type.tsym.flags() & Flags.ANNOTATION) != 0) return;
        if (types.lowerBound(type).tsym == syms.classType.tsym) return;
        if (types.isArray(type) && !types.isArray(types.elemtype(type))) {
            validateAnnotationType(pos, types.elemtype(type));
            return;
        }
        log.error(pos, "invalid.annotation.member.type");
    }

    /**
     * "It is also a compile-time error if any method declared in an
     * annotation type has a signature that is override-equivalent to
     * that of any public or protected method declared in class Object
     * or in the interface annotation.Annotation."
     *
     * @jls3 9.6 Annotation Types
     */
    void validateAnnotationMethod(DiagnosticPosition pos, MethodSymbol m) {
        for (Type sup = syms.annotationType; sup.tag == CLASS; sup = types.supertype(sup)) {
            Scope s = sup.tsym.members();
            for (Scope.Entry e = s.lookup(m.name); e.scope != null; e = e.next()) {
                if (e.sym.kind == MTH &&
                    (e.sym.flags() & (PUBLIC | PROTECTED)) != 0 &&
                    types.overrideEquivalent(m.type, e.sym.type))
                    log.error(pos, "intf.annotation.member.clash", e.sym, sup);
            }
        }
    }

    /** Check the annotations of a symbol.
     */
    public void validateAnnotations(List<JCAnnotation> annotations, Symbol s) {
        if (skipAnnotations) return;
        for (JCAnnotation a : annotations)
            validateAnnotation(a, s);
    }

    /** Check an annotation of a symbol.
     */
    public void validateAnnotation(JCAnnotation a, Symbol s) {
        validateAnnotation(a);

        if (!annotationApplicable(a, s))
            log.error(a.pos(), "annotation.type.not.applicable");

        if (a.annotationType.type.tsym == syms.overrideType.tsym) {
            if (!isOverrider(s))
                log.error(a.pos(), "method.does.not.override.superclass");
        }
    }

    /** Is s a method symbol that overrides a method in a superclass? */
    boolean isOverrider(Symbol s) {
        if (s.kind != MTH || s.isStatic())
            return false;
        MethodSymbol m = (MethodSymbol)s;
        TypeSymbol owner = (TypeSymbol)m.owner;
        for (Type sup : types.closure(owner.type)) {
            if (sup == owner.type)
                continue; // skip "this"
            Scope scope = sup.tsym.members();
            for (Scope.Entry e = scope.lookup(m.name); e.scope != null; e = e.next()) {
                if (!e.sym.isStatic() && m.overrides(e.sym, owner, types, true))
                    return true;
            }
        }
        return false;
    }

    /** Is the annotation applicable to the symbol? */
    boolean annotationApplicable(JCAnnotation a, Symbol s) {
        Attribute.Compound atTarget =
            a.annotationType.type.tsym.attribute(syms.annotationTargetType.tsym);
        if (atTarget == null) return true;
        Attribute atValue = atTarget.member(names.value);
        if (!(atValue instanceof Attribute.Array)) return true; // error recovery
        Attribute.Array arr = (Attribute.Array) atValue;
        for (Attribute app : arr.values) {
            if (!(app instanceof Attribute.Enum)) return true; // recovery
            Attribute.Enum e = (Attribute.Enum) app;
            if (e.value.name == names.TYPE)
                { if (s.kind == TYP) return true; }
            else if (e.value.name == names.FIELD)
                { if (s.kind == VAR && s.owner.kind != MTH) return true; }
            else if (e.value.name == names.METHOD)
                { if (s.kind == MTH && !s.isConstructor()) return true; }
            else if (e.value.name == names.PARAMETER)
                { if (s.kind == VAR &&
                      s.owner.kind == MTH &&
                      (s.flags() & PARAMETER) != 0)
                    return true;
                }
            else if (e.value.name == names.CONSTRUCTOR)
                { if (s.kind == MTH && s.isConstructor()) return true; }
            else if (e.value.name == names.LOCAL_VARIABLE)
                { if (s.kind == VAR && s.owner.kind == MTH &&
                      (s.flags() & PARAMETER) == 0)
                    return true;
                }
            else if (e.value.name == names.ANNOTATION_TYPE)
                { if (s.kind == TYP && (s.flags() & ANNOTATION) != 0)
                    return true;
                }
            else if (e.value.name == names.PACKAGE)
                { if (s.kind == PCK) return true; }
            else
                return true; // recovery
        }
        return false;
    }

    /** Check an annotation value.
     */
    public void validateAnnotation(JCAnnotation a) {
        if (a.type.isErroneous()) return;

        // collect an inventory of the members
        Set<MethodSymbol> members = new HashSet<MethodSymbol>();
        for (Scope.Entry e = a.annotationType.type.tsym.members().elems;
             e != null;
             e = e.sibling)
            if (e.sym.kind == MTH)
                members.add((MethodSymbol) e.sym);

        // count them off as they're annotated
        for (JCTree arg : a.args) {
            if (arg.getTag() != JCTree.ASSIGN) continue; // recovery
            JCAssign assign = (JCAssign) arg;
            Symbol m = TreeInfo.symbol(assign.lhs);
            if (m == null || m.type.isErroneous()) continue;
            if (!members.remove(m))
                log.error(arg.pos(), "duplicate.annotation.member.value",
                          m.name, a.type);
            if (assign.rhs.getTag() == ANNOTATION)
                validateAnnotation((JCAnnotation)assign.rhs);
        }

        // all the remaining ones better have default values
        for (MethodSymbol m : members)
            if (m.defaultValue == null && !m.type.isErroneous())
                log.error(a.pos(), "annotation.missing.default.value",
                          a.type, m.name);

        // special case: java.lang.annotation.Target must not have
        // repeated values in its value member
        if (a.annotationType.type.tsym != syms.annotationTargetType.tsym ||
            a.args.tail == null)
            return;

        if (a.args.head.getTag() != JCTree.ASSIGN) return; // error recovery
        JCAssign assign = (JCAssign) a.args.head;
        Symbol m = TreeInfo.symbol(assign.lhs);
        if (m.name != names.value) return;
        JCTree rhs = assign.rhs;
        if (rhs.getTag() != JCTree.NEWARRAY) return;
        JCNewArray na = (JCNewArray) rhs;
        Set<Symbol> targets = new HashSet<Symbol>();
        for (JCTree elem : na.elems) {
            if (!targets.add(TreeInfo.symbol(elem))) {
                log.error(elem.pos(), "repeated.annotation.target");
            }
        }
    }

    void checkDeprecatedAnnotation(DiagnosticPosition pos, Symbol s) {
        if (allowAnnotations &&
            lint.isEnabled(Lint.LintCategory.DEP_ANN) &&
            (s.flags() & DEPRECATED) != 0 &&
            !syms.deprecatedType.isErroneous() &&
            s.attribute(syms.deprecatedType.tsym) == null) {
            log.warning(pos, "missing.deprecated.annotation");
        }
    }

/* *************************************************************************
 * Check for recursive annotation elements.
 **************************************************************************/

    /** Check for cycles in the graph of annotation elements.
     */
    void checkNonCyclicElements(JCClassDecl tree) {
        if ((tree.sym.flags_field & ANNOTATION) == 0) return;
        assert (tree.sym.flags_field & LOCKED) == 0;
        try {
            tree.sym.flags_field |= LOCKED;
            for (JCTree def : tree.defs) {
                if (def.getTag() != JCTree.METHODDEF) continue;
                JCMethodDecl meth = (JCMethodDecl)def;
                checkAnnotationResType(meth.pos(), meth.restype.type);
            }
        } finally {
            tree.sym.flags_field &= ~LOCKED;
            tree.sym.flags_field |= ACYCLIC_ANN;
        }
    }

    void checkNonCyclicElementsInternal(DiagnosticPosition pos, TypeSymbol tsym) {
        if ((tsym.flags_field & ACYCLIC_ANN) != 0)
            return;
        if ((tsym.flags_field & LOCKED) != 0) {
            log.error(pos, "cyclic.annotation.element");
            return;
        }
        try {
            tsym.flags_field |= LOCKED;
            for (Scope.Entry e = tsym.members().elems; e != null; e = e.sibling) {
                Symbol s = e.sym;
                if (s.kind != Kinds.MTH)
                    continue;
                checkAnnotationResType(pos, ((MethodSymbol)s).type.getReturnType());
            }
        } finally {
            tsym.flags_field &= ~LOCKED;
            tsym.flags_field |= ACYCLIC_ANN;
        }
    }

    void checkAnnotationResType(DiagnosticPosition pos, Type type) {
        switch (type.tag) {
        case TypeTags.CLASS:
            if ((type.tsym.flags() & ANNOTATION) != 0)
                checkNonCyclicElementsInternal(pos, type.tsym);
            break;
        case TypeTags.ARRAY:
            checkAnnotationResType(pos, types.elemtype(type));
            break;
        default:
            break; // int etc
        }
    }

/* *************************************************************************
 * Check for cycles in the constructor call graph.
 **************************************************************************/

    /** Check for cycles in the graph of constructors calling other
     *  constructors.
     */
    void checkCyclicConstructors(JCClassDecl tree) {
        Map<Symbol,Symbol> callMap = new HashMap<Symbol, Symbol>();

        // enter each constructor this-call into the map
        for (List<JCTree> l = tree.defs; l.nonEmpty(); l = l.tail) {
            JCMethodInvocation app = TreeInfo.firstConstructorCall(l.head);
            if (app == null) continue;
            JCMethodDecl meth = (JCMethodDecl) l.head;
            if (TreeInfo.name(app.meth) == names._this) {
                callMap.put(meth.sym, TreeInfo.symbol(app.meth));
            } else {
                meth.sym.flags_field |= ACYCLIC;
            }
        }

        // Check for cycles in the map
        Symbol[] ctors = new Symbol[0];
        ctors = callMap.keySet().toArray(ctors);
        for (Symbol caller : ctors) {
            checkCyclicConstructor(tree, caller, callMap);
        }
    }

    /** Look in the map to see if the given constructor is part of a
     *  call cycle.
     */
    private void checkCyclicConstructor(JCClassDecl tree, Symbol ctor,
                                        Map<Symbol,Symbol> callMap) {
        if (ctor != null && (ctor.flags_field & ACYCLIC) == 0) {
            if ((ctor.flags_field & LOCKED) != 0) {
                log.error(TreeInfo.diagnosticPositionFor(ctor, tree),
                          "recursive.ctor.invocation");
            } else {
                ctor.flags_field |= LOCKED;
                checkCyclicConstructor(tree, callMap.remove(ctor), callMap);
                ctor.flags_field &= ~LOCKED;
            }
            ctor.flags_field |= ACYCLIC;
        }
    }

/* *************************************************************************
 * Miscellaneous
 **************************************************************************/

    /**
     * Return the opcode of the operator but emit an error if it is an
     * error.
     * @param pos        position for error reporting.
     * @param operator   an operator
     * @param tag        a tree tag
     * @param left       type of left hand side
     * @param right      type of right hand side
     */
    int checkOperator(DiagnosticPosition pos,
                       OperatorSymbol operator,
                       int tag,
                       Type left,
                       Type right) {
        if (operator.opcode == ByteCodes.error) {
            log.error(pos,
                      "operator.cant.be.applied",
                      treeinfo.operatorName(tag),
                      List.of(left, right));
        }
        return operator.opcode;
    }


    /**
     *  Check for division by integer constant zero
     *  @param pos           Position for error reporting.
     *  @param operator      The operator for the expression
     *  @param operand       The right hand operand for the expression
     */
    void checkDivZero(DiagnosticPosition pos, Symbol operator, Type operand) {
        if (operand.constValue() != null
            && lint.isEnabled(Lint.LintCategory.DIVZERO)
            && operand.tag <= LONG
            && ((Number) (operand.constValue())).longValue() == 0) {
            int opc = ((OperatorSymbol)operator).opcode;
            if (opc == ByteCodes.idiv || opc == ByteCodes.imod
                || opc == ByteCodes.ldiv || opc == ByteCodes.lmod) {
                log.warning(pos, "div.zero");
            }
        }
    }

    /**
     * Check for empty statements after if
     */
    void checkEmptyIf(JCIf tree) {
        if (tree.thenpart.getTag() == JCTree.SKIP && tree.elsepart == null && lint.isEnabled(Lint.LintCategory.EMPTY))
            log.warning(tree.thenpart.pos(), "empty.if");
    }

    /** Check that symbol is unique in given scope.
     *  @param pos           Position for error reporting.
     *  @param sym           The symbol.
     *  @param s             The scope.
     */
    boolean checkUnique(DiagnosticPosition pos, Symbol sym, Scope s) {
        if (sym.type.isErroneous())
            return true;
        if (sym.owner.name == names.any) return false;
        for (Scope.Entry e = s.lookup(sym.name); e.scope == s; e = e.next()) {
            if (sym != e.sym &&
                sym.kind == e.sym.kind &&
                sym.name != names.error &&
                (sym.kind != MTH || types.hasSameArgs(types.erasure(sym.type), types.erasure(e.sym.type)))) {
                if ((sym.flags() & VARARGS) != (e.sym.flags() & VARARGS))
                    varargsDuplicateError(pos, sym, e.sym);
                else if (sym.kind == MTH && !types.overrideEquivalent(sym.type, e.sym.type))
                    duplicateErasureError(pos, sym, e.sym);
                else
                    duplicateError(pos, e.sym);
                return false;
            }
        }
        return true;
    }
    //where
    /** Report duplicate declaration error.
     */
    void duplicateErasureError(DiagnosticPosition pos, Symbol sym1, Symbol sym2) {
        if (!sym1.type.isErroneous() && !sym2.type.isErroneous()) {
            log.error(pos, "name.clash.same.erasure", sym1, sym2);
        }
    }

    /** Check that single-type import is not already imported or top-level defined,
     *  but make an exception for two single-type imports which denote the same type.
     *  @param pos           Position for error reporting.
     *  @param sym           The symbol.
     *  @param s             The scope
     */
    boolean checkUniqueImport(DiagnosticPosition pos, Symbol sym, Scope s) {
        return checkUniqueImport(pos, sym, s, false);
    }

    /** Check that static single-type import is not already imported or top-level defined,
     *  but make an exception for two single-type imports which denote the same type.
     *  @param pos           Position for error reporting.
     *  @param sym           The symbol.
     *  @param s             The scope
     *  @param staticImport  Whether or not this was a static import
     */
    boolean checkUniqueStaticImport(DiagnosticPosition pos, Symbol sym, Scope s) {
        return checkUniqueImport(pos, sym, s, true);
    }

    /** Check that single-type import is not already imported or top-level defined,
     *  but make an exception for two single-type imports which denote the same type.
     *  @param pos           Position for error reporting.
     *  @param sym           The symbol.
     *  @param s             The scope.
     *  @param staticImport  Whether or not this was a static import
     */
    private boolean checkUniqueImport(DiagnosticPosition pos, Symbol sym, Scope s, boolean staticImport) {
        for (Scope.Entry e = s.lookup(sym.name); e.scope != null; e = e.next()) {
            // is encountered class entered via a class declaration?
            boolean isClassDecl = e.scope == s;
            if ((isClassDecl || sym != e.sym) &&
                sym.kind == e.sym.kind &&
                sym.name != names.error) {
                if (!e.sym.type.isErroneous()) {
                    String what = e.sym.toString();
                    if (!isClassDecl) {
                        if (staticImport)
                            log.error(pos, "already.defined.static.single.import", what);
                        else
                            log.error(pos, "already.defined.single.import", what);
                    }
                    else if (sym != e.sym)
                        log.error(pos, "already.defined.this.unit", what);
                }
                return false;
            }
        }
        return true;
    }

    /** Check that a qualified name is in canonical form (for import decls).
     */
    public void checkCanonical(JCTree tree) {
        if (!isCanonical(tree))
            log.error(tree.pos(), "import.requires.canonical",
                      TreeInfo.symbol(tree));
    }
        // where
        private boolean isCanonical(JCTree tree) {
            while (tree.getTag() == JCTree.SELECT) {
                JCFieldAccess s = (JCFieldAccess) tree;
                if (s.sym.owner != TreeInfo.symbol(s.selected))
                    return false;
                tree = s.selected;
            }
            return true;
        }

    private class ConversionWarner extends Warner {
        final String key;
        final Type found;
        final Type expected;
        public ConversionWarner(DiagnosticPosition pos, String key, Type found, Type expected) {
            super(pos);
            this.key = key;
            this.found = found;
            this.expected = expected;
        }

        public void warnUnchecked() {
            boolean warned = this.warned;
            super.warnUnchecked();
            if (warned) return; // suppress redundant diagnostics
            Object problem = diags.fragment(key);
            Check.this.warnUnchecked(pos(), "prob.found.req", problem, found, expected);
        }
    }

    public Warner castWarner(DiagnosticPosition pos, Type found, Type expected) {
        return new ConversionWarner(pos, "unchecked.cast.to.type", found, expected);
    }

    public Warner convertWarner(DiagnosticPosition pos, Type found, Type expected) {
        return new ConversionWarner(pos, "unchecked.assign", found, expected);
    }
}
