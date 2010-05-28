/*
 * Copyright (c) 1997, 2003, Oracle and/or its affiliates. All rights reserved.
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
import sun.tools.tree.*;
import sun.tools.asm.Assembler;

/**
 * A reference from one scope to another.
 *
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 *
 */

public
class UplevelReference implements Constants {
    /**
     * The class in which the reference occurs.
     */
    ClassDefinition client;

    /**
     * The field being referenced.
     * It is always a final argument or a final local variable.
     * (An uplevel reference to a field of a class C is fetched
     * through an implicit uplevel reference to C.this, which is
     * an argument.)
     */
    LocalMember target;

    /**
     * The local variable which bears a copy of the target's value,
     * for all methods of the client class.
     * Its name is "this$C" for <code>this.C</code> or
     * "val$x" for other target variables <code>x</code>.
     * <p>
     * This local variable is always a constructor argument,
     * and is therefore usable only in the constructor and in initializers.
     * All other methods use the local field.
     * @see #localField
     */
    LocalMember localArgument;

    /**
     * A private synthetic field of the client class which
     * bears a copy of the target's value.
     * The compiler tries to avoid creating it if possible.
     * The field has the same name and type as the localArgument.
     * @see #localArgument
     */
    MemberDefinition localField;

    /**
     * The next item on the references list of the client.
     */
    UplevelReference next;

    /**
     * constructor
     */
    public UplevelReference(ClassDefinition client, LocalMember target) {
        this.client = client;
        this.target = target;

        // Choose a name and build a variable declaration node.
        Identifier valName;
        if (target.getName().equals(idThis)) {
            ClassDefinition tc = target.getClassDefinition();
            // It should always be true that tc.enclosingClassOf(client).
            // If it were false, the numbering scheme would fail
            // to produce unique names, since we'd be trying
            // to number classes which were not in the sequence
            // of enclosing scopes.  The next paragraph of this
            // code robustly deals with that possibility, however,
            // by detecting name collisions and perturbing the names.
            int depth = 0;
            for (ClassDefinition pd = tc; !pd.isTopLevel(); pd = pd.getOuterClass()) {
                // The inner classes specification states that the name of
                // a private field containing a reference to the outermost
                // enclosing instance is named "this$0".  That outermost
                // enclosing instance is always the innermost toplevel class.
                depth += 1;
            }
            // In this example, T1,T2,T3 are all top-level (static),
            // while I4,I5,I6,I7 are all inner.  Each of the inner classes
            // will have a single up-level "this$N" reference to the next
            // class out.  Only the outermost "this$0" will refer to a
            // top-level class, T3.
            //
            // class T1 {
            //  static class T2 {
            //   static class T3 {
            //    class I4 {
            //     class I5 {
            //      class I6 {
            //       // at this point we have these fields in various places:
            //       // I4 this$0; I5 this$1; I6 this$2;
            //      }
            //     }
            //     class I7 {
            //       // I4 this$0; I7 this$1;
            //     }
            //    }
            //   }
            //  }
            // }
            valName = Identifier.lookup(prefixThis + depth);
        } else {
            valName = Identifier.lookup(prefixVal + target.getName());
        }

        // Make reasonably certain that valName is unique to this client.
        // (This check can be fooled by malicious naming of explicit
        // constructor arguments, or of inherited fields.)
        Identifier base = valName;
        int tick = 0;
        while (true) {
            boolean failed = (client.getFirstMatch(valName) != null);
            for (UplevelReference r = client.getReferences();
                    r != null; r = r.next) {
                if (r.target.getName().equals(valName)) {
                    failed = true;
                }
            }
            if (!failed) {
                break;
            }
            // try another name
            valName = Identifier.lookup(base + "$" + (++tick));
        }

        // Build the constructor argument.
        // Like "this", it wil be shared equally by all constructors of client.
        localArgument = new LocalMember(target.getWhere(),
                                       client,
                                       M_FINAL | M_SYNTHETIC,
                                       target.getType(),
                                       valName);
    }

    /**
     * Insert self into a list of references.
     * Maintain "isEarlierThan" as an invariant of the list.
     * This is important (a) to maximize stability of signatures,
     * and (b) to allow uplevel "this" parameters to come at the
     * front of every argument list they appear in.
     */
    public UplevelReference insertInto(UplevelReference references) {
        if (references == null || isEarlierThan(references)) {
            next = references;
            return this;
        } else {
            UplevelReference prev = references;
            while (!(prev.next == null || isEarlierThan(prev.next))) {
                prev = prev.next;
            }
            next = prev.next;
            prev.next = this;
            return references;
        }
    }

    /**
     * Tells if self precedes the other in the canonical ordering.
     */
    public final boolean isEarlierThan(UplevelReference other) {
        // Outer fields always come first.
        if (isClientOuterField()) {
            return true;
        } else if (other.isClientOuterField()) {
            return false;
        }

        // Now it doesn't matter what the order is; use string comparison.
        LocalMember target2 = other.target;
        Identifier name = target.getName();
        Identifier name2 = target2.getName();
        int cmp = name.toString().compareTo(name2.toString());
        if (cmp != 0) {
            return cmp < 0;
        }
        Identifier cname = target.getClassDefinition().getName();
        Identifier cname2 = target2.getClassDefinition().getName();
        int ccmp = cname.toString().compareTo(cname2.toString());
        return ccmp < 0;
    }

    /**
     * the target of this reference
     */
    public final LocalMember getTarget() {
        return target;
    }

    /**
     * the local argument for this reference
     */
    public final LocalMember getLocalArgument() {
        return localArgument;
    }

    /**
     * the field allocated in the client for this reference
     */
    public final MemberDefinition getLocalField() {
        return localField;
    }

    /**
     * Get the local field, creating one if necessary.
     * The client class must not be frozen.
     */
    public final MemberDefinition getLocalField(Environment env) {
        if (localField == null) {
            makeLocalField(env);
        }
        return localField;
    }

    /**
     * the client class
     */
    public final ClassDefinition getClient() {
        return client;
    }

    /**
     * the next reference in the client's list
     */
    public final UplevelReference getNext() {
        return next;
    }

    /**
     * Tell if this uplevel reference is the up-level "this" pointer
     * of an inner class.  Such references are treated differently
     * than others, because they affect constructor calls across
     * compilation units.
     */
    public boolean isClientOuterField() {
        MemberDefinition outerf = client.findOuterMember();
        return (outerf != null) && (localField == outerf);
    }

    /**
     * Tell if my local argument is directly available in this context.
     * If not, the uplevel reference will have to be via a class field.
     * <p>
     * This must be called in a context which is local
     * to the client of the uplevel reference.
     */
    public boolean localArgumentAvailable(Environment env, Context ctx) {
        MemberDefinition reff = ctx.field;
        if (reff.getClassDefinition() != client) {
            throw new CompilerError("localArgumentAvailable");
        }
        return (   reff.isConstructor()
                || reff.isVariable()
                || reff.isInitializer() );
    }

    /**
     * Process an uplevel reference.
     * The only decision to make at this point is whether
     * to build a "localField" instance variable, which
     * is done (lazily) when localArgumentAvailable() proves false.
     */
    public void noteReference(Environment env, Context ctx) {
        if (localField == null && !localArgumentAvailable(env, ctx)) {
            // We need an instance variable unless client is a constructor.
            makeLocalField(env);
        }
    }

    private void makeLocalField(Environment env) {
        // Cannot alter decisions like this one at a late date.
        client.referencesMustNotBeFrozen();
        int mod = M_PRIVATE | M_FINAL | M_SYNTHETIC;
        localField = env.makeMemberDefinition(env,
                                             localArgument.getWhere(),
                                             client, null,
                                             mod,
                                             localArgument.getType(),
                                             localArgument.getName(),
                                             null, null, null);
    }

    /**
     * Assuming noteReference() is all taken care of,
     * build an uplevel reference.
     * <p>
     * This must be called in a context which is local
     * to the client of the uplevel reference.
     */
    public Expression makeLocalReference(Environment env, Context ctx) {
        if (ctx.field.getClassDefinition() != client) {
            throw new CompilerError("makeLocalReference");
        }
        if (localArgumentAvailable(env, ctx)) {
            return new IdentifierExpression(0, localArgument);
        } else {
            return makeFieldReference(env, ctx);
        }
    }

    /**
     * As with makeLocalReference(), build a locally-usable reference.
     * Ignore the availability of local arguments; always use a class field.
     */
    public Expression makeFieldReference(Environment env, Context ctx) {
        Expression e = ctx.findOuterLink(env, 0, localField);
        return new FieldExpression(0, e, localField);
    }

    /**
     * During the inline phase, call this on a list of references
     * for which the code phase will later emit arguments.
     * It will make sure that any "double-uplevel" values
     * needed by the callee are also present at the call site.
     * <p>
     * If any reference is a "ClientOuterField", it is skipped
     * by this method (and by willCodeArguments).  This is because
     */
    public void willCodeArguments(Environment env, Context ctx) {
        if (!isClientOuterField()) {
            ctx.noteReference(env, target);
        }

        if (next != null) {
            next.willCodeArguments(env, ctx);
        }
    }

    /**
     * Code is being generated for a call to a constructor of
     * the client class.  Push an argument for the constructor.
     */
    public void codeArguments(Environment env, Context ctx, Assembler asm,
                              long where, MemberDefinition conField) {
        if (!isClientOuterField()) {
            Expression e = ctx.makeReference(env, target);
            e.codeValue(env, ctx, asm);
        }

        if (next != null) {
            next.codeArguments(env, ctx, asm, where, conField);
        }
    }

    /**
     * Code is being generated for a constructor of the client class.
     * Emit code which initializes the instance.
     */
    public void codeInitialization(Environment env, Context ctx, Assembler asm,
                                   long where, MemberDefinition conField) {
        // If the reference is a clientOuterField, then the initialization
        // code is generated in MethodExpression.makeVarInits().
        // (Fix for bug 4075063.)
        if (localField != null && !isClientOuterField()) {
            Expression e = ctx.makeReference(env, target);
            Expression f = makeFieldReference(env, ctx);
            e = new AssignExpression(e.getWhere(), f, e);
            e.type = localField.getType();
            e.code(env, ctx, asm);
        }

        if (next != null) {
            next.codeInitialization(env, ctx, asm, where, conField);
        }
    }

    public String toString() {
        return "[" + localArgument + " in " + client + "]";
    }
}
