/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javadoc;

import com.sun.tools.javac.util.*;

import com.sun.javadoc.*;

/**
 * Represents a see also documentation tag.
 * The @see tag can be plain text, or reference a class or member.
 *
 * @author Kaiyang Liu (original)
 * @author Robert Field (rewrite)
 * @author Atul M Dambalkar
 *
 */
class SeeTagImpl extends TagImpl implements SeeTag, LayoutCharacters {

    //### TODO: Searching for classes, fields, and methods
    //### should follow the normal rules applied by the compiler.

    /**
     * where of  where#what - i.e. the class name (may be empty)
     */
    private String where;

    /**
     * what of  where#what - i.e. the member (may be null)
     */
    private String what;

    private PackageDoc referencedPackage;
    private ClassDoc referencedClass;
    private MemberDoc referencedMember;

    String label = "";

    SeeTagImpl(DocImpl holder, String name, String text) {
        super(holder, name, text);
        parseSeeString();
        if (where != null) {
            ClassDocImpl container = null;
            if (holder instanceof MemberDoc) {
                container =
                  (ClassDocImpl)((ProgramElementDoc)holder).containingClass();
            } else if (holder instanceof ClassDoc) {
                container = (ClassDocImpl)holder;
            }
            findReferenced(container);
        }
    }

    /**
     * get the class name part of @see, For instance,
     * if the comment is @see String#startsWith(java.lang.String) .
     *      This function returns String.
     * Returns null if format was not that of java reference.
     * Return empty string if class name was not specified..
     */
    public String referencedClassName() {
        return where;
    }

    /**
     * get the package referenced by  @see. For instance,
     * if the comment is @see java.lang
     *      This function returns a PackageDocImpl for java.lang
     * Returns null if no known package found.
     */
    public PackageDoc referencedPackage() {
        return referencedPackage;
    }

    /**
     * get the class referenced by the class name part of @see, For instance,
     * if the comment is @see String#startsWith(java.lang.String) .
     *      This function returns a ClassDocImpl for java.lang.String.
     * Returns null if class is not a class specified on the javadoc command line..
     */
    public ClassDoc referencedClass() {
        return referencedClass;
    }

    /**
     * get the name of the member referenced by the prototype part of @see,
     * For instance,
     * if the comment is @see String#startsWith(java.lang.String) .
     *      This function returns "startsWith(java.lang.String)"
     * Returns null if format was not that of java reference.
     * Return empty string if member name was not specified..
     */
    public String referencedMemberName() {
        return what;
    }

    /**
     * get the member referenced by the prototype part of @see,
     * For instance,
     * if the comment is @see String#startsWith(java.lang.String) .
     *      This function returns a MethodDocImpl for startsWith.
     * Returns null if member could not be determined.
     */
    public MemberDoc referencedMember() {
        return referencedMember;
    }


    /**
     * parse @see part of comment. Determine 'where' and 'what'
     */
    private void parseSeeString() {
        int len = text.length();
        if (len == 0) {
            return;
        }
        switch (text.charAt(0)) {
            case '<':
                if (text.charAt(len-1) != '>') {
                    docenv().warning(holder,
                                     "tag.see.no_close_bracket_on_url",
                                     name, text);
                }
                return;
            case '"':
                if (len == 1 || text.charAt(len-1) != '"') {
                    docenv().warning(holder,
                                     "tag.see.no_close_quote",
                                     name, text);
                } else {
//                    text = text.substring(1,len-1); // strip quotes
                }
                return;
        }

        // check that the text is one word, with possible parentheses
        // this part of code doesn't allow
        // @see <a href=.....>asfd</a>
        // comment it.

        // the code assumes that there is no initial white space.
        int parens = 0;
        int commentstart = 0;
        int start = 0;
        int cp;
        for (int i = start; i < len ; i += Character.charCount(cp)) {
            cp = text.codePointAt(i);
            switch (cp) {
                case '(': parens++; break;
                case ')': parens--; break;
                case '[': case ']': case '.': case '#': break;
                case ',':
                    if (parens <= 0) {
                        docenv().warning(holder,
                                         "tag.see.malformed_see_tag",
                                         name, text);
                        return;
                    }
                    break;
                case ' ': case '\t': case '\n': case CR:
                    if (parens == 0) { //here onwards the comment starts.
                        commentstart = i;
                        i = len;
                    }
                    break;
                default:
                    if (!Character.isJavaIdentifierPart(cp)) {
                        docenv().warning(holder,
                                         "tag.see.illegal_character",
                                         name, ""+cp, text);
                    }
                    break;
            }
        }
        if (parens != 0) {
            docenv().warning(holder,
                             "tag.see.malformed_see_tag",
                             name, text);
            return;
        }

        String seetext = "";
        String labeltext = "";

        if (commentstart > 0) {
            seetext = text.substring(start, commentstart);
            labeltext = text.substring(commentstart + 1);
            // strip off the white space which can be between seetext and the
            // actual label.
            for (int i = 0; i < labeltext.length(); i++) {
                char ch2 = labeltext.charAt(i);
                if (!(ch2 == ' ' || ch2 == '\t' || ch2 == '\n')) {
                    label = labeltext.substring(i);
                    break;
                }
            }
        } else {
            seetext = text;
            label = "";
        }

        int sharp = seetext.indexOf('#');
        if (sharp >= 0) {
            // class#member
            where = seetext.substring(0, sharp);
            what = seetext.substring(sharp + 1);
        } else {
            if (seetext.indexOf('(') >= 0) {
                docenv().warning(holder,
                                 "tag.see.missing_sharp",
                                 name, text);
                where = "";
                what = seetext;
            }
            else {
                // no member specified, text names class
                where = seetext;
                what = null;
            }
        }
    }

    /**
     * Find what is referenced by the see also.  If possible, sets
     * referencedClass and referencedMember.
     *
     * @param containingClass the class containing the comment containing
     * the tag. May be null, if, for example, it is a package comment.
     */
    private void findReferenced(ClassDocImpl containingClass) {
        if (where.length() > 0) {
            if (containingClass != null) {
                referencedClass = containingClass.findClass(where);
            } else {
                referencedClass = docenv().lookupClass(where);
            }
            if (referencedClass == null && holder() instanceof ProgramElementDoc) {
                referencedClass = docenv().lookupClass(
                    ((ProgramElementDoc) holder()).containingPackage().name() + "." + where);
            }

            if (referencedClass == null) { /* may just not be in this run */
//                docenv().warning(holder, "tag.see.class_not_found",
//                                 where, text);
                // check if it's a package name
                referencedPackage = docenv().lookupPackage(where);
                return;
            }
        } else {
            if (containingClass == null) {
                docenv().warning(holder,
                                 "tag.see.class_not_specified",
                                 name, text);
                return;
            } else {
                referencedClass = containingClass;
            }
        }
        where = referencedClass.qualifiedName();

        if (what == null) {
            return;
        } else {
            int paren = what.indexOf('(');
            String memName = (paren >= 0 ? what.substring(0, paren) : what);
            String[] paramarr;
            if (paren > 0) {
                // has parameter list -- should be method or constructor
                paramarr = new ParameterParseMachine(what.
                        substring(paren, what.length())).parseParameters();
                if (paramarr != null) {
                    referencedMember = findExecutableMember(memName, paramarr,
                                                            referencedClass);
                } else {
                    referencedMember = null;
                }
            } else {
                // no parameter list -- should be field
                referencedMember = findExecutableMember(memName, null,
                                                        referencedClass);
                FieldDoc fd = ((ClassDocImpl)referencedClass).
                                                            findField(memName);
                // when no args given, prefer fields over methods
                if (referencedMember == null ||
                    (fd != null &&
                     fd.containingClass()
                         .subclassOf(referencedMember.containingClass()))) {
                    referencedMember = fd;
                }
            }
            if (referencedMember == null) {
                docenv().warning(holder,
                                 "tag.see.can_not_find_member",
                                 name, what, where);
            }
        }
    }

    private MemberDoc findReferencedMethod(String memName, String[] paramarr,
                                           ClassDoc referencedClass) {
        MemberDoc meth = findExecutableMember(memName, paramarr, referencedClass);
        ClassDoc[] nestedclasses = referencedClass.innerClasses();
        if (meth == null) {
            for (int i = 0; i < nestedclasses.length; i++) {
                meth = findReferencedMethod(memName, paramarr, nestedclasses[i]);
                if (meth != null) {
                    return meth;
                }
            }
        }
        return null;
    }

    private MemberDoc findExecutableMember(String memName, String[] paramarr,
                                           ClassDoc referencedClass) {
        if (memName.equals(referencedClass.name())) {
            return ((ClassDocImpl)referencedClass).findConstructor(memName,
                                                                   paramarr);
        } else {   // it's a method.
            return ((ClassDocImpl)referencedClass).findMethod(memName,
                                                              paramarr);
        }
    }

    // separate "int, String" from "(int, String)"
    // (int i, String s) ==> [0] = "int",  [1] = String
    // (int[][], String[]) ==> [0] = "int[][]" // [1] = "String[]"
    class ParameterParseMachine {
        static final int START = 0;
        static final int TYPE = 1;
        static final int NAME = 2;
        static final int TNSPACE = 3;  // space between type and name
        static final int ARRAYDECORATION = 4;
        static final int ARRAYSPACE = 5;

        String parameters;

        StringBuilder typeId;

        ListBuffer<String> paramList;

        ParameterParseMachine(String parameters) {
            this.parameters = parameters;
            this.paramList = new ListBuffer<String>();
            typeId = new StringBuilder();
        }

        public String[] parseParameters() {
            if (parameters.equals("()")) {
                return new String[0];
            }   // now strip off '(' and ')'
            int state = START;
            int prevstate = START;
            parameters = parameters.substring(1, parameters.length() - 1);
            int cp;
            for (int index = 0; index < parameters.length(); index += Character.charCount(cp)) {
                cp = parameters.codePointAt(index);
                switch (state) {
                    case START:
                        if (Character.isJavaIdentifierStart(cp)) {
                            typeId.append(Character.toChars(cp));
                            state = TYPE;
                        }
                        prevstate = START;
                        break;
                    case TYPE:
                        if (Character.isJavaIdentifierPart(cp) || cp == '.') {
                            typeId.append(Character.toChars(cp));
                        } else if (cp == '[') {
                            typeId.append('[');
                            state = ARRAYDECORATION;
                        } else if (Character.isWhitespace(cp)) {
                            state = TNSPACE;
                        } else if (cp == ',') {  // no name, just type
                            addTypeToParamList();
                            state = START;
                        }
                        prevstate = TYPE;
                        break;
                    case TNSPACE:
                        if (Character.isJavaIdentifierStart(cp)) { // name
                            if (prevstate == ARRAYDECORATION) {
                                docenv().warning(holder,
                                                 "tag.missing_comma_space",
                                                 name,
                                                 "(" + parameters + ")");
                                return (String[])null;
                            }
                            addTypeToParamList();
                            state = NAME;
                        } else if (cp == '[') {
                            typeId.append('[');
                            state = ARRAYDECORATION;
                        } else if (cp == ',') {   // just the type
                            addTypeToParamList();
                            state = START;
                        } // consume rest all
                        prevstate = TNSPACE;
                        break;
                    case ARRAYDECORATION:
                        if (cp == ']') {
                            typeId.append(']');
                            state = TNSPACE;
                        } else if (!Character.isWhitespace(cp)) {
                            docenv().warning(holder,
                                             "tag.illegal_char_in_arr_dim",
                                             name,
                                             "(" + parameters + ")");
                            return (String[])null;
                        }
                        prevstate = ARRAYDECORATION;
                        break;
                    case NAME:
                        if (cp == ',') {  // just consume everything till ','
                            state = START;
                        }
                        prevstate = NAME;
                        break;
                }
            }
            if (state == ARRAYDECORATION ||
                (state == START && prevstate == TNSPACE)) {
                docenv().warning(holder,
                                 "tag.illegal_see_tag",
                                 "(" + parameters + ")");
            }
            if (typeId.length() > 0) {
                paramList.append(typeId.toString());
            }
            return paramList.toArray(new String[paramList.length()]);
        }

        void addTypeToParamList() {
            if (typeId.length() > 0) {
                paramList.append(typeId.toString());
                typeId.setLength(0);
            }
        }
    }

    /**
     * Return the kind of this tag.
     */
    @Override
    public String kind() {
        return "@see";
    }

    /**
     * Return the label of the see tag.
     */
    public String label() {
        return label;
    }
}
