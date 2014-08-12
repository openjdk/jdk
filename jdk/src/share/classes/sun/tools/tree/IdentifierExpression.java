/*
 * Copyright (c) 1994, 2003, Oracle and/or its affiliates. All rights reserved.
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

package sun.tools.tree;

import sun.tools.java.*;
import sun.tools.asm.Assembler;
import sun.tools.asm.LocalVariable;
import java.io.PrintStream;
import java.util.Hashtable;

/**
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
public
class IdentifierExpression extends Expression {
    Identifier id;
    MemberDefinition field;
    Expression implementation;

    /**
     * Constructor
     */
    public IdentifierExpression(long where, Identifier id) {
        super(IDENT, where, Type.tError);
        this.id = id;
    }
    public IdentifierExpression(IdentifierToken id) {
        this(id.getWhere(), id.getName());
    }
    public IdentifierExpression(long where, MemberDefinition field) {
        super(IDENT, where, field.getType());
        this.id = field.getName();
        this.field = field;
    }

    public Expression getImplementation() {
        if (implementation != null)
            return implementation;
        return this;
    }

    /**
     * Check if the expression is equal to a value
     */
    public boolean equals(Identifier id) {
        return this.id.equals(id);
    }


    /**
     * Assign a value to this identifier.  [It must already be "bound"]
     */
    private Vset assign(Environment env, Context ctx, Vset vset) {
        if (field.isLocal()) {
            LocalMember local = (LocalMember)field;
            if (local.scopeNumber < ctx.frameNumber) {
                env.error(where, "assign.to.uplevel", id);
            }
            if (local.isFinal()) {
                // allow definite single assignment of blank finals
                if (!local.isBlankFinal()) {
                    env.error(where, "assign.to.final", id);
                } else if (!vset.testVarUnassigned(local.number)) {
                    env.error(where, "assign.to.blank.final", id);
                }
            }
            vset.addVar(local.number);
            local.writecount++;
        } else if (field.isFinal()) {
            vset = FieldExpression.checkFinalAssign(env, ctx, vset,
                                                    where, field);
        }
        return vset;
    }

    /**
     * Get the value of this identifier.  [ It must already be "bound"]
     */
    private Vset get(Environment env, Context ctx, Vset vset) {
        if (field.isLocal()) {
            LocalMember local = (LocalMember)field;
            if (local.scopeNumber < ctx.frameNumber && !local.isFinal()) {
                env.error(where, "invalid.uplevel", id);
            }
            if (!vset.testVar(local.number)) {
                env.error(where, "var.not.initialized", id);
                vset.addVar(local.number);
            }
            local.readcount++;
        } else {
            if (!field.isStatic()) {
                if (!vset.testVar(ctx.getThisNumber())) {
                    env.error(where, "access.inst.before.super", id);
                    implementation = null;
                }
            }
            if (field.isBlankFinal()) {
                int number = ctx.getFieldNumber(field);
                if (number >= 0 && !vset.testVar(number)) {
                    env.error(where, "var.not.initialized", id);
                }
            }
        }
        return vset;
    }

    /**
     * Bind to a field
     */
    boolean bind(Environment env, Context ctx) {
        try {
            field = ctx.getField(env, id);
            if (field == null) {
                for (ClassDefinition cdef = ctx.field.getClassDefinition();
                     cdef != null; cdef = cdef.getOuterClass()) {
                    if (cdef.findAnyMethod(env, id) != null) {
                        env.error(where, "invalid.var", id,
                                  ctx.field.getClassDeclaration());
                        return false;
                    }
                }
                env.error(where, "undef.var", id);
                return false;
            }

            type = field.getType();

            // Check access permission
            if (!ctx.field.getClassDefinition().canAccess(env, field)) {
                env.error(where, "no.field.access",
                          id, field.getClassDeclaration(),
                          ctx.field.getClassDeclaration());
                return false;
            }

            // Find out how to access this variable.
            if (field.isLocal()) {
                LocalMember local = (LocalMember)field;
                if (local.scopeNumber < ctx.frameNumber) {
                    // get a "val$x" copy via the current object
                    implementation = ctx.makeReference(env, local);
                }
            } else {
                MemberDefinition f = field;

                if (f.reportDeprecated(env)) {
                    env.error(where, "warn.field.is.deprecated",
                              id, f.getClassDefinition());
                }

                ClassDefinition fclass = f.getClassDefinition();
                if (fclass != ctx.field.getClassDefinition()) {
                    // Maybe an inherited field hides an apparent variable.
                    MemberDefinition f2 = ctx.getApparentField(env, id);
                    if (f2 != null && f2 != f) {
                        ClassDefinition c = ctx.findScope(env, fclass);
                        if (c == null)  c = f.getClassDefinition();
                        if (f2.isLocal()) {
                            env.error(where, "inherited.hides.local",
                                      id, c.getClassDeclaration());
                        } else {
                            env.error(where, "inherited.hides.field",
                                      id, c.getClassDeclaration(),
                                      f2.getClassDeclaration());
                        }
                    }
                }

                // Rewrite as a FieldExpression.
                // Access methods for private fields, if needed, will be added
                // during subsequent processing of the FieldExpression.  See
                // method 'FieldExpression.checkCommon'. This division of labor
                // is somewhat awkward, as most further processing of a
                // FieldExpression during the checking phase is suppressed when
                // the referenced field is pre-set as it is here.

                if (f.isStatic()) {
                    Expression base = new TypeExpression(where,
                                        f.getClassDeclaration().getType());
                    implementation = new FieldExpression(where, null, f);
                } else {
                    Expression base = ctx.findOuterLink(env, where, f);
                    if (base != null) {
                        implementation = new FieldExpression(where, base, f);
                    }
                }
            }

            // Check forward reference
            if (!ctx.canReach(env, field)) {
                env.error(where, "forward.ref",
                          id, field.getClassDeclaration());
                return false;
            }
            return true;
        } catch (ClassNotFound e) {
            env.error(where, "class.not.found", e.name, ctx.field);
        } catch (AmbiguousMember e) {
            env.error(where, "ambig.field", id,
                      e.field1.getClassDeclaration(),
                      e.field2.getClassDeclaration());
        }
        return false;
    }

    /**
     * Check expression
     */
    public Vset checkValue(Environment env, Context ctx, Vset vset, Hashtable<Object, Object> exp) {
        if (field != null) {
            // An internally pre-set field, such as an argument copying
            // an uplevel value.  Do not re-check it.
            return vset;
        }
        if (bind(env, ctx)) {
            vset = get(env, ctx, vset);
            ctx.field.getClassDefinition().addDependency(field.getClassDeclaration());
            if (implementation != null)
                vset = implementation.checkValue(env, ctx, vset, exp);
        }
        return vset;
    }

    /**
     * Check the expression if it appears on the LHS of an assignment
     */
    public Vset checkLHS(Environment env, Context ctx,
                         Vset vset, Hashtable<Object, Object> exp) {
        if (!bind(env, ctx))
            return vset;
        vset = assign(env, ctx, vset);
        if (implementation != null)
            vset = implementation.checkValue(env, ctx, vset, exp);
        return vset;
    }

    /**
     * Check the expression if it appears on the LHS of an op= expression
     */
    public Vset checkAssignOp(Environment env, Context ctx,
                              Vset vset, Hashtable<Object, Object> exp, Expression outside) {
        if (!bind(env, ctx))
            return vset;
        vset = assign(env, ctx, get(env, ctx, vset));
        if (implementation != null)
            vset = implementation.checkValue(env, ctx, vset, exp);
        return vset;
    }

    /**
     * Return an accessor if one is needed for assignments to this expression.
     */
    public FieldUpdater getAssigner(Environment env, Context ctx) {
        if (implementation != null)
            return implementation.getAssigner(env, ctx);
        return null;
    }

    /**
     * Return an updater if one is needed for assignments to this expression.
     */
    public FieldUpdater getUpdater(Environment env, Context ctx) {
        if (implementation != null)
            return implementation.getUpdater(env, ctx);
        return null;
    }

    /**
     * Check if the present name is part of a scoping prefix.
     */
    public Vset checkAmbigName(Environment env, Context ctx, Vset vset, Hashtable<Object, Object> exp,
                               UnaryExpression loc) {
        try {
            if (ctx.getField(env, id) != null) {
                // if this is a local field, there's nothing more to do.
                return checkValue(env, ctx, vset, exp);
            }
        } catch (ClassNotFound ee) {
        } catch (AmbiguousMember ee) {
        }
        // Can this be interpreted as a type?
        ClassDefinition c = toResolvedType(env, ctx, true);
        // Is it a real type??
        if (c != null) {
            loc.right = new TypeExpression(where, c.getType());
            return vset;
        }
        // We hope it is a package prefix.  Let the caller decide.
        type = Type.tPackage;
        return vset;
    }

    /**
     * Convert an identifier to a known type, or null.
     */
    private ClassDefinition toResolvedType(Environment env, Context ctx,
                                           boolean pkgOK) {
        Identifier rid = ctx.resolveName(env, id);
        Type t = Type.tClass(rid);
        if (pkgOK && !env.classExists(t)) {
            return null;
        }
        if (env.resolve(where, ctx.field.getClassDefinition(), t)) {
            try {
                ClassDefinition c = env.getClassDefinition(t);

                // Maybe an inherited class hides an apparent class.
                if (c.isMember()) {
                    ClassDefinition sc = ctx.findScope(env, c.getOuterClass());
                    if (sc != c.getOuterClass()) {
                        Identifier rid2 = ctx.getApparentClassName(env, id);
                        if (!rid2.equals(idNull) && !rid2.equals(rid)) {
                            env.error(where, "inherited.hides.type",
                                      id, sc.getClassDeclaration());
                        }
                    }
                }

                if (!c.getLocalName().equals(id.getFlatName().getName())) {
                    env.error(where, "illegal.mangled.name", id, c);
                }

                return c;
            } catch (ClassNotFound ee) {
            }
        }
        return null;
    }

    /**
     * Convert an identifier to a type.
     * If one is not known, use the current package as a qualifier.
     */
    Type toType(Environment env, Context ctx) {
        ClassDefinition c = toResolvedType(env, ctx, false);
        if (c != null) {
            return c.getType();
        }
        return Type.tError;
    }

    /**
     * Convert an expresion to a type in a context where a qualified
     * type name is expected, e.g., in the prefix of a qualified type
     * name. We do not necessarily know where the package prefix ends,
     * so we operate similarly to 'checkAmbiguousName'.  This is the
     * base case -- the first component of the qualified name.
     */
    /*-------------------------------------------------------*
    Type toQualifiedType(Environment env, Context ctx) {
        // We do not look for non-type fields.  Is this correct?
        ClassDefinition c = toResolvedType(env, ctx, true);
        // Is it a real type?
        if (c != null) {
            return c.getType();
        }
        // We hope it is a package prefix.  Let the caller decide.
        return Type.tPackage;
    }
    *-------------------------------------------------------*/

    /**
     * Check if constant:  Will it inline away?
     */
    public boolean isConstant() {
        if (implementation != null)
            return implementation.isConstant();
        if (field != null) {
            return field.isConstant();
        }
        return false;
    }

    /**
     * Inline
     */
    public Expression inline(Environment env, Context ctx) {
        return null;
    }
    public Expression inlineValue(Environment env, Context ctx) {
        if (implementation != null)
            return implementation.inlineValue(env, ctx);
        if (field == null) {
            return this;
        }
        try {
            if (field.isLocal()) {
                if (field.isInlineable(env, false)) {
                    Expression e = (Expression)field.getValue(env);
                    return (e == null) ? this : e.inlineValue(env, ctx);
                }
                return this;
            }
            return this;
        } catch (ClassNotFound e) {
            throw new CompilerError(e);
        }
    }
    public Expression inlineLHS(Environment env, Context ctx) {
        if (implementation != null)
            return implementation.inlineLHS(env, ctx);
        return this;
    }

    public Expression copyInline(Context ctx) {
        if (implementation != null)
            return implementation.copyInline(ctx);
        IdentifierExpression e =
            (IdentifierExpression)super.copyInline(ctx);
        if (field != null && field.isLocal()) {
            e.field = ((LocalMember)field).getCurrentInlineCopy(ctx);
        }
        return e;
    }

    public int costInline(int thresh, Environment env, Context ctx) {
        if (implementation != null)
            return implementation.costInline(thresh, env, ctx);
        return super.costInline(thresh, env, ctx);
    }

    /**
     * Code local vars (object fields have been inlined away)
     */
    int codeLValue(Environment env, Context ctx, Assembler asm) {
        return 0;
    }
    void codeLoad(Environment env, Context ctx, Assembler asm) {
        asm.add(where, opc_iload + type.getTypeCodeOffset(),
                ((LocalMember)field).number);
    }
    void codeStore(Environment env, Context ctx, Assembler asm) {
        LocalMember local = (LocalMember)field;
        asm.add(where, opc_istore + type.getTypeCodeOffset(),
                new LocalVariable(local, local.number));
    }
    public void codeValue(Environment env, Context ctx, Assembler asm) {
        codeLValue(env, ctx, asm);
        codeLoad(env, ctx, asm);
    }

    /**
     * Print
     */
    public void print(PrintStream out) {
        out.print(id + "#" + ((field != null) ? field.hashCode() : 0));
        if (implementation != null) {
            out.print("/IMPL=");
            implementation.print(out);
        }
    }
}
