/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jshell;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import static jdk.jshell.Util.REPL_CLASS_PREFIX;
import static jdk.jshell.Util.asLetters;

/**
 * A Snippet represents a snippet of Java source code as passed to
 * {@link jdk.jshell.JShell#eval}.  It is associated only with the
 * {@link jdk.jshell.JShell JShell} instance that created it.
 * An instance of Snippet (including its subclasses) is immutable: an access to
 * any of its methods will always return the same result.
 * For information about the current state of the snippet within the JShell
 * state engine, query <code>JShell</code> passing the Snippet.
 * <p>
 * Because it is immutable, <code>Snippet</code> (and subclasses) is thread-safe.
 * @author Robert Field
 * @see jdk.jshell.JShell#status
 */
public abstract class Snippet {

    /**
     * Describes the general kind of snippet.
     * The <code>Kind</code> is an immutable property of a Snippet.
     * It is accessed with {@link jdk.jshell.Snippet#kind()}.
     * The <code>Kind</code> can be used to determine which
     * subclass of Snippet it is. For example,
     * {@link jdk.jshell.JShell#eval eval("int three() { return 3; }")} will
     * return a snippet creation event.  The <code>Kind</code> of that Snippet
     * will be <code>METHOD</code>, from which you know that the subclass
     * of <code>Snippet</code> is <code>MethodSnippet</code> and it can be
     * cast as such.
     */
    public enum Kind {
        /**
         * An import declaration: <code>import</code> ...
         * The snippet is an instance of {@link jdk.jshell.ImportSnippet}.
         * An import can be a single type import
         * ({@link jdk.jshell.Snippet.SubKind#SINGLE_TYPE_IMPORT_SUBKIND}),
         * a static single import
         * ({@link jdk.jshell.Snippet.SubKind#SINGLE_STATIC_IMPORT_SUBKIND}),
         * an on-demand type import
         * ({@link jdk.jshell.Snippet.SubKind#TYPE_IMPORT_ON_DEMAND_SUBKIND}),
         * or a static on-demand type import
         * ({@link jdk.jshell.Snippet.SubKind#SINGLE_STATIC_IMPORT_SUBKIND}) --
         * use {@link jdk.jshell.Snippet#subKind()} to distinguish.
         * @jls 8.3: importDeclaration.
         */
        IMPORT(true),

        /**
         * A type declaration.
         * Which includes: NormalClassDeclaration, EnumDeclaration,
         * NormalInterfaceDeclaration, and AnnotationTypeDeclaration.
         * The snippet is an instance of {@link jdk.jshell.TypeDeclSnippet}.
         * A type declaration may be an interface
         * {@link jdk.jshell.Snippet.SubKind#INTERFACE_SUBKIND},
         * classes {@link jdk.jshell.Snippet.SubKind#CLASS_SUBKIND}, enums, and
         * annotation interfaces -- see {@link jdk.jshell.Snippet.SubKind} to
         * differentiate.
         * @jls 7.6: TypeDeclaration.
         */
        TYPE_DECL(true),

        /**
         * A method declaration.
         * The snippet is an instance of {@link jdk.jshell.MethodSnippet}.
         * @jls 8.4: MethodDeclaration.
         */
        METHOD(true),

        /**
         * One variable declaration.
         * Corresponding to one <i>VariableDeclarator</i>.
         * The snippet is an instance of {@link jdk.jshell.VarSnippet}.
         * The variable may be with or without initializer, or be a temporary
         * variable representing an expression -- see
         * {@link jdk.jshell.Snippet.SubKind}to differentiate.
         * @jls 8.3: FieldDeclaration.
         */
        VAR(true),

        /**
         * An expression, with or without side-effects.
         * The snippet is an instance of {@link jdk.jshell.ExpressionSnippet}.
         * The expression is currently either a simple named reference to a
         * variable ({@link jdk.jshell.Snippet.SubKind#VAR_VALUE_SUBKIND}) or an
         * assignment (both of which have natural referencing
         * names) -- see {@link jdk.jshell.Snippet.SubKind} to differentiate.
         * @jls 15: Expression.
         */
        EXPRESSION(false),

        /**
         * A statement.
         * The snippet is an instance of {@link jdk.jshell.StatementSnippet}.
         * @jls 14.5: Statement.
         */
        STATEMENT(false),

        /**
         * A syntactically incorrect input for which the specific
         * kind could not be determined.
         * The snippet is an instance of {@link jdk.jshell.ErroneousSnippet}.
         */
        ERRONEOUS(false);

        /**
         * True if this kind of snippet adds a declaration or declarations
         * which are visible to subsequent evaluations.
         */
        public final boolean isPersistent;

        Kind(boolean isPersistent) {
            this.isPersistent = isPersistent;
        }
    }

    /**
     * The detailed variety of a snippet.  This is a sub-classification of the
     * Kind.  The Kind of a SubKind is accessible with
     * {@link jdk.jshell.Snippet.SubKind#kind}.
     */
    public enum SubKind {

        /**
         * Single-Type-Import Declaration.
         * An import declaration of a single type.
         * @jls 7.5.1 SingleTypeImportDeclaration.
         */
        SINGLE_TYPE_IMPORT_SUBKIND(Kind.IMPORT),

        /**
         * Type-Import-on-Demand Declaration.
         * A non-static "star" import.
         * @jls 7.5.2. TypeImportOnDemandDeclaration.
         */
        TYPE_IMPORT_ON_DEMAND_SUBKIND(Kind.IMPORT),

        /**
         * Single-Static-Import Declaration.
         * An import of a static member.
         * @jls 7.5.3 Single-Static-Import.
         */
        SINGLE_STATIC_IMPORT_SUBKIND(Kind.IMPORT),

        /**
         * Static-Import-on-Demand Declaration.
         * A static "star" import of all static members of a named type.
         * @jls 7.5.4. Static-Import-on-Demand Static "star" import.
         */
        STATIC_IMPORT_ON_DEMAND_SUBKIND(Kind.IMPORT),

        /**
         * A class declaration.
         * A <code>SubKind</code> of {@link Kind#TYPE_DECL}.
         * @jls 8.1. NormalClassDeclaration.
         */
        CLASS_SUBKIND(Kind.TYPE_DECL),

        /**
         * An interface declaration.
         * A <code>SubKind</code> of {@link Kind#TYPE_DECL}.
         * @jls 9.1. NormalInterfaceDeclaration.
         */
        INTERFACE_SUBKIND(Kind.TYPE_DECL),

        /**
         * An enum declaration.
         * A <code>SubKind</code> of {@link Kind#TYPE_DECL}.
         * @jls 8.9. EnumDeclaration.
         */
        ENUM_SUBKIND(Kind.TYPE_DECL),

        /**
         * An annotation interface declaration. A <code>SubKind</code> of
         * {@link Kind#TYPE_DECL}.
         * @jls 9.6. AnnotationTypeDeclaration.
         */
        ANNOTATION_TYPE_SUBKIND(Kind.TYPE_DECL),

        /**
         * A method. The only <code>SubKind</code> for {@link Kind#METHOD}.
         * @jls 8.4. MethodDeclaration.
         */
        METHOD_SUBKIND(Kind.METHOD),

        /**
         * A variable declaration without initializer.
         * A <code>SubKind</code> of {@link Kind#VAR}.
         * @jls 8.3. VariableDeclarator without VariableInitializer in
         * FieldDeclaration.
         */
        VAR_DECLARATION_SUBKIND(Kind.VAR),

        /**
         * A variable declaration with an initializer expression. A
         * <code>SubKind</code> of {@link Kind#VAR}.
         * @jls 8.3. VariableDeclarator with VariableInitializer in
         * FieldDeclaration.
         */
        VAR_DECLARATION_WITH_INITIALIZER_SUBKIND(Kind.VAR, true, true),

        /**
         * An expression whose value has been stored in a temporary variable. A
         * <code>SubKind</code> of {@link Kind#VAR}.
         * @jls 15. Primary.
         */
        TEMP_VAR_EXPRESSION_SUBKIND(Kind.VAR, true, true),

        /**
         * A simple variable reference expression. A <code>SubKind</code> of
         * {@link Kind#EXPRESSION}.
         * @jls 15.11. Field Access as 3.8. Identifier.
         */
        VAR_VALUE_SUBKIND(Kind.EXPRESSION, true, true),

        /**
         * An assignment expression. A <code>SubKind</code> of
         * {@link Kind#EXPRESSION}.
         * @jls 15.26. Assignment.
         */
        ASSIGNMENT_SUBKIND(Kind.EXPRESSION, true, true),

        /**
         * An expression which has not been wrapped in a temporary variable
         * (reserved). A <code>SubKind</code> of {@link Kind#EXPRESSION}.
         */
        OTHER_EXPRESSION_SUBKIND(Kind.EXPRESSION, true, true),

        /**
         * A statement. The only <code>SubKind</code> for {@link Kind#STATEMENT}.
         * @jls 14.5. Statement.
         */
        STATEMENT_SUBKIND(Kind.STATEMENT, true, false),

        /**
         * An unknown snippet. The only <code>SubKind</code> for
         * {@link Kind#ERRONEOUS}.
         */
        UNKNOWN_SUBKIND(Kind.ERRONEOUS, false, false);

        private final boolean isExecutable;
        private final boolean hasValue;
        private final Kind kind;

        SubKind(Kind kind) {
            this.kind = kind;
            this.isExecutable = false;
            this.hasValue = false;
        }

        SubKind(Kind kind, boolean isExecutable, boolean hasValue) {
            this.kind = kind;
            this.isExecutable = isExecutable;
            this.hasValue = hasValue;
        }

        /**
         * Is this <code>SubKind</code> executable?
         *
         * @return true if this <code>SubKind</code> can be executed.
         */
        public boolean isExecutable() {
            return isExecutable;
        }

        /**
         * Is this <code>SubKind</code> executable and is non-<code>void</code>.
         *
         * @return true if this <code>SubKind</code> has a value.
         */
        public boolean hasValue() {
            return hasValue;
        }

        /**
         * The {@link Snippet.Kind} that corresponds to this <code>SubKind</code>.
         *
         * @return the fixed <code>Kind</code> for this <code>SubKind</code>
         */
        public Kind kind() {
            return kind;
        }
    }

    /**
     * Describes the current state of a Snippet.
     * This is a dynamic property of a Snippet within the JShell state --
     * thus is retrieved with a {@linkplain
     * jdk.jshell.JShell#status(jdk.jshell.Snippet) query on <code>JShell</code>}.
     * <p>
     * The <code>Status</code> changes as the state changes.
     * For example, creation of another snippet with
     * {@link jdk.jshell.JShell#eval(java.lang.String) eval}
     * may resolve dependencies of this Snippet (or invalidate those dependencies), or
     * {@linkplain jdk.jshell.Snippet.Status#OVERWRITTEN overwrite}
     * this Snippet changing its
     * <code>Status</code>.
     * <p>
     * Important properties associated with <code>Status</code> are:
     * {@link jdk.jshell.Snippet.Status#isDefined}, if it is visible to other
     * existing and new snippets; and
     * {@link jdk.jshell.Snippet.Status#isActive}, if, as the
     * JShell state changes, the snippet will update, possibly
     * changing <code>Status</code>.
     * An executable Snippet can only be executed if it is in the the
     * {@link jdk.jshell.Snippet.Status#VALID} <code>Status</code>.
     * @see JShell#status(jdk.jshell.Snippet)
     */
    public enum Status {
        /**
         * The snippet is a valid snippet
         * (in the context of current <code>JShell</code> state).
         * Only snippets with <code>VALID</code>
         * <code>Status</code> can be executed (though not all
         * <code>VALID</code> snippets have executable code).
         * If the snippet is a declaration or import, it is visible to other
         * snippets ({@link Status#isDefined isDefined} <code> == true</code>).
         * <p>
         * The snippet will update as dependents change
         * ({@link Status#isActive isActive} <code> == true</code>), its
         * status could become <code>RECOVERABLE_DEFINED</code>, <code>RECOVERABLE_NOT_DEFINED</code>,
         * <code>DROPPED</code>, or <code>OVERWRITTEN</code>.
         */
        VALID(true, true),

        /**
         * The snippet is a declaration snippet with potentially recoverable
         * unresolved references or other issues in its body
         * (in the context of current <code>JShell</code> state).
         * Only a {@link jdk.jshell.DeclarationSnippet} can have this
         * <code>Status</code>.
         * The snippet has a valid signature and it is visible to other
         * snippets ({@link Status#isDefined isDefined} <code> == true</code>)
         * and thus can be referenced in existing or new snippets
         * but the snippet cannot be executed.
         * An {@link UnresolvedReferenceException} will be thrown on an attempt
         * to execute it.
         * <p>
         * The snippet will update as dependents change
         * ({@link Status#isActive isActive} <code> == true</code>), its
         * status could become <code>VALID</code>, <code>RECOVERABLE_NOT_DEFINED</code>,
         * <code>DROPPED</code>, or <code>OVERWRITTEN</code>.
         * <p>
         * Note: both <code>RECOVERABLE_DEFINED</code> and <code>RECOVERABLE_NOT_DEFINED</code>
         * indicate potentially recoverable errors, they differ in that, for
         * <code>RECOVERABLE_DEFINED</code>, the snippet is
         * {@linkplain Status#isDefined defined}.
         */
        RECOVERABLE_DEFINED(true, true),

        /**
         * The snippet is a declaration snippet with potentially recoverable
         * unresolved references or other issues
         * (in the context of current <code>JShell</code> state).
         * Only a {@link jdk.jshell.DeclarationSnippet} can have this
         * <code>Status</code>.
         * The snippet has an invalid signature or the implementation is
         * otherwise unable to define it.
         * The snippet it is not visible to other snippets
         * ({@link Status#isDefined isDefined} <code> == false</code>)
         * and thus cannot be referenced or executed.
         * <p>
         * The snippet will update as dependents change
         * ({@link Status#isActive isActive} <code> == true</code>), its
         * status could become <code>VALID</code>, <code>RECOVERABLE_DEFINED</code>,
         * <code>DROPPED</code>, or <code>OVERWRITTEN</code>.
         * <p>
         * Note: both <code>RECOVERABLE_DEFINED</code> and <code>RECOVERABLE_NOT_DEFINED</code>
         * indicate potentially recoverable errors, they differ in that, for
         * <code>RECOVERABLE_DEFINED</code>, the snippet is
         * {@linkplain Status#isDefined defined}.
         */
        RECOVERABLE_NOT_DEFINED(true, false),

        /**
         * The snippet is inactive because of an explicit call to
         * the {@link JShell#drop(jdk.jshell.PersistentSnippet)}.
         * Only a {@link jdk.jshell.PersistentSnippet} can have this
         * <code>Status</code>.
         * The snippet is not visible to other snippets
         * ({@link Status#isDefined isDefined} <code> == false</code>)
         * and thus cannot be referenced or executed.
         * <p>
         * The snippet will not update as dependents change
         * ({@link Status#isActive isActive} <code> == false</code>), its
         * <code>Status</code> will never change again.
         */
        DROPPED(false, false),

        /**
         * The snippet is inactive because it has been replaced by a new
         * snippet.  This occurs when the new snippet added with
         * {@link jdk.jshell.JShell#eval} matches a previous snippet.
         * A <code>TypeDeclSnippet</code> will match another
         * <code>TypeDeclSnippet</code> if the names match.
         * For example <code>class X { }</code> will overwrite
         * <code>class X { int ii; }</code> or
         * <code>interface X { }</code>.
         * A <code>MethodSnippet</code> will match another
         * <code>MethodSnippet</code> if the names and parameter types
         * match.
         * For example <code>void m(int a) { }</code> will overwrite
         * <code>int m(int a) { return a+a; }</code>.
         * A <code>VarSnippet</code> will match another
         * <code>VarSnippet</code> if the names match.
         * For example <code>double z;</code> will overwrite
         * <code>long z = 2L;</code>.
         * Only a {@link jdk.jshell.PersistentSnippet} can have this
         * <code>Status</code>.
         * The snippet is not visible to other snippets
         * ({@link Status#isDefined isDefined} <code> == false</code>)
         * and thus cannot be referenced or executed.
         * <p>
         * The snippet will not update as dependents change
         * ({@link Status#isActive isActive} <code> == false</code>), its
         * <code>Status</code> will never change again.
         */
        OVERWRITTEN(false, false),

        /**
         * The snippet is inactive because it failed compilation on initial
         * evaluation and it is not capable of becoming valid with further
         * changes to the JShell state.
         * The snippet is not visible to other snippets
         * ({@link Status#isDefined isDefined} <code> == false</code>)
         * and thus cannot be referenced or executed.
         * <p>
         * The snippet will not update as dependents change
         * ({@link Status#isActive isActive} <code> == false</code>), its
         * <code>Status</code> will never change again.
         */
        REJECTED(false, false),

        /**
         * The snippet is inactive because it does not yet exist.
         * Used only in {@link SnippetEvent#previousStatus} for new
         * snippets.
         * {@link jdk.jshell.JShell#status(jdk.jshell.Snippet) JShell.status(Snippet)}
         * will never return this <code>Status</code>.
         * Vacuously, {@link Status#isDefined isDefined} and
         * {@link Status#isActive isActive} are both defined <code>false</code>.
         */
        NONEXISTENT(false, false);

        /**
         * Is the Snippet active, that is, will the snippet
         * be re-evaluated when a new
         * {@link JShell#eval(java.lang.String) JShell.eval(String)} or
         * {@link JShell#drop(jdk.jshell.PersistentSnippet)
         * JShell.drop(PersistentSnippet)} that could change
         * its status is invoked?  This is more broad than
         * {@link Status#isDefined} since a Snippet which is
         * {@link Status#RECOVERABLE_NOT_DEFINED}
         * will be updated.
         */
        public final boolean isActive;

        /**
         * Is the snippet currently part of the defined state of the JShell?
         * Is it visible to compilation of other snippets?
         */
        public final boolean isDefined;

        Status(boolean isActive, boolean isDefined) {
            this.isActive = isActive;
            this.isDefined = isDefined;
        }
    }

    private final Key key;
    private final String source;
    private final Wrap guts;
    final String unitName;
    private final SubKind subkind;

    private int seq;
    private String className;
    private String id;
    private OuterWrap outer;
    private Status status;
    private List<String> unresolved;
    private DiagList diagnostics;

    Snippet(Key key, String userSource, Wrap guts, String unitName, SubKind subkind) {
        this.key = key;
        this.source = userSource;
        this.guts = guts;
        this.unitName = unitName;
        this.subkind = subkind;
        this.status = Status.NONEXISTENT;
        setSequenceNumber(0);
    }

    /**** public access ****/

    /**
     * The unique identifier for the snippet. No two active snippets will have
     * the same id().  Value of id has no prescribed meaning.  The details of
     * how the id is generated and the mechanism to change it is documented in
     * {@link JShell.Builder#idGenerator()}.
     * @return the snippet id string.
     */
    public String id() {
        return id;
    }

    /**
     * The {@link jdk.jshell.Snippet.Kind} for the snippet.
     * Indicates the subclass of Snippet.
     * @return the Kind of the snippet
     * @see Snippet.Kind
     */
    public Kind kind() {
        return subkind.kind();
    }

    /**
     * Return the {@link SubKind} of snippet.
     * The SubKind is useful for feedback to users.
     * @return the SubKind corresponding to this snippet
     */
    public SubKind subKind() {
        return subkind;
    }

    /**
     * Return the source code of the snippet.
     * @return the source code corresponding to this snippet
     */
    public String source() {
        return source;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Snippet:");
        if (key() != null) {
            sb.append(key().toString());
        }
        sb.append('-');
        sb.append(source);
        return sb.toString();
    }

    //**** internal access ****

    String name() {
        return unitName;
    }

    Key key() {
        return key;
    }

    List<String> unresolved() {
        return Collections.unmodifiableList(unresolved);
    }

    DiagList diagnostics() {
        return diagnostics;
    }

    /**
     * @return the corralled guts
     */
    Wrap corralled() {
        return null;
    }

    Collection<String> declareReferences() {
        return null;
    }

    Collection<String> bodyReferences() {
        return null;
    }

    String importLine(JShell state) {
        return "";
    }

    void setId(String id) {
        this.id = id;
    }

    final void setSequenceNumber(int seq) {
        this.seq = seq;
        this.className = REPL_CLASS_PREFIX + key().index() + asLetters(seq);
    }

    void setOuterWrap(OuterWrap outer) {
        this.outer = outer;
    }

    void setCompilationStatus(Status status, List<String> unresolved, DiagList diagnostics) {
        this.status = status;
        this.unresolved = unresolved;
        this.diagnostics = diagnostics;
    }

    void setDiagnostics(DiagList diagnostics) {
        this.diagnostics = diagnostics;
    }

    void setFailed(DiagList diagnostics) {
        this.seq = -1;
        this.outer = null;
        this.status = Status.REJECTED;
        this.unresolved = Collections.emptyList();
        this.diagnostics = diagnostics;
    }

    void setDropped() {
        this.status = Status.DROPPED;
    }

    void setOverwritten() {
        this.status = Status.OVERWRITTEN;
    }

    Status status() {
        return status;
    }

    String className() {
        return className;
    }

    /**
     * Top-level wrap
     * @return
     */
    OuterWrap outerWrap() {
        return outer;
    }

    /**
     * Basically, class version for this Key.
     * @return int
     */
    int sequenceNumber() {
        return seq;
    }

    Wrap guts() {
        return guts;
    }

    boolean isExecutable() {
        return subkind.isExecutable();
    }

}
