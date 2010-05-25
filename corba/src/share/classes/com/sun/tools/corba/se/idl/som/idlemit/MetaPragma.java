/*
 * Copyright (c) 1999, Oracle and/or its affiliates. All rights reserved.
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
/*
 *  COMPONENT_NAME: shasta
 *
 *  ORIGINS: 27
 *
 * Licensed Materials - Property of IBM
 * 5639-D57 (C) COPYRIGHT International Business Machines Corp. 1997,1998,1999
 * RMI-IIOP v1.0
 *
 */

package com.sun.tools.corba.se.idl.som.idlemit;
import java.util.Vector;
import com.sun.tools.corba.se.idl.som.cff.Messages;
/**
 * This is an implementation that handles
 * #pragma meta scoped_name string
 * where
 * <UL>
 * <LI>    scoped_name ==  "::" separated scoped name
 * <LI>    string ==  separated identifiers, such as "localonly",
 *          "abstract", or "init".
 *         D59407: NOTE: any non-white-space is grouped
 *          as part of the identifier.
 * </UL>
 *
 * This pragma handler places a vector of Strings into the dynamicVariable()
 * part of the SymtabEntry. The key to access the dynamicVariable()
 * is com.sun.tools.corba.se.idl.som.idlemit.MetaPragma.metaKey
 *
 * It is possible to associate a meta pragma with a forward entry.
 * At some point after the parser has completed,
 * the method processForward(ForwardEntry entry) should be called
 * for each ForwardEntry so that the meta information can be folded from
 * the ForwardEntry into the corresponding InterfaceEntry.
 */
public class MetaPragma extends com.sun.tools.corba.se.idl.PragmaHandler {
    /* Class variables */

    /* key to access the Cached meta info in com.sun.tools.corba.se.idl.SymtabEntry */
    public static int metaKey = com.sun.tools.corba.se.idl.SymtabEntry.getVariableKey();


    /**
     * Main entry point for the MetaPragma handler
     * @param pragma string for pragma name
     * @param currentToken next token in the input stream.
     * @return true if this is a meta pragma.
     */
    public boolean process(String pragma, String currentToken) {
        if ( !pragma.equals("meta"))
            return false;

        com.sun.tools.corba.se.idl.SymtabEntry entry ;
        String msg;
        try {
            entry = scopedName();
            if ( entry == null){
                /* scoped name not found */
                parseException(Messages.msg("idlemit.MetaPragma.scopedNameNotFound"));
                skipToEOL();
            }
            else {
                msg = (currentToken()+ getStringToEOL());
// System.out.println(entry + ":  " + msg);
                Vector v;
                v = (Vector) entry.dynamicVariable(metaKey);
                if ( v== null){
                    v = new Vector();
                    entry.dynamicVariable(metaKey, v);
                }
                parseMsg(v, msg);
           }
        } catch(Exception e){
// System.out.println("exception in MetaPragma");
        }
        return true;
    }


    /**
     * Fold the meta info from the forward entry into its corresponding
     * interface entry.
     * @param forwardEntry the forward entry to process
     */
    static public void processForward(com.sun.tools.corba.se.idl.ForwardEntry forwardEntry){

        Vector forwardMeta;
        try {
            forwardMeta = (Vector)forwardEntry.dynamicVariable(metaKey);
        } catch (Exception e){
            forwardMeta = null;
        }
        com.sun.tools.corba.se.idl.SymtabEntry forwardInterface = forwardEntry.type();
        if (forwardMeta != null && forwardInterface!= null) {
            Vector interfaceMeta;
            try {
                 interfaceMeta= (Vector)forwardInterface.dynamicVariable(metaKey);
            } catch ( Exception e){
                 interfaceMeta = null;
            }

            if ( interfaceMeta == null) {
                /* set */
                try {
                    forwardInterface.dynamicVariable(MetaPragma.metaKey, forwardMeta);
                } catch(Exception e){};
            }
            else if (interfaceMeta != forwardMeta) {
                 /* The above check is needed because sometimes
                 a forward entry is processed more the once.
                 Not sure why */
                /* merge */
                for (int i=0; i < forwardMeta.size(); i++){
                    try {
                        Object obj = forwardMeta.elementAt(i);
                        interfaceMeta.addElement(obj);
                    } catch (Exception e){};
                }
            }
         }
    }

    /**
     * parse pragma message and place into vector v.
     * @param v: vector to add message
     * @param msg: string of comma separated message, perhaps with comment.
     * This is implemented as a state machine as follows:
     *
     *  State          token        next             action
     *  -----------------------------------------------------
     *   initial     whitespace     initial
     *   initial     SlashStar      comment
     *   initial     SlashSlash     final
     *   initial     no more        final
     *   initial     text           text             add to text buffer
     *   initial     StarSlash      initial
     *   comment     StarSlash      initial
     *   comment     SlashStar      comment
     *   comment     whitespace     comment
     *   comment     SlashSlash     comment
     *   comment     text           comment
     *   comment     no more        final
     *   text        text           text              add to buffer
     *   text        SlashStar      comment           put in vector
     *   text        whitespace     initial           put in vector
     *   text        SlashSlash     final             put in vector
     *   text        StarSlash      initial           put in vector
     *   text        no more        final             put in vector
     *
    */
    private static int initialState = 0;
    private static int commentState = 1;
    private static int textState = 2;
    private static int finalState =3;

    private void parseMsg(Vector v, String msg){
        int state = initialState;
        String text = "";
        int index = 0;
        while ( state != finalState ){
             boolean isNoMore = index >= msg.length();
             char ch = ' ';
             boolean isSlashStar = false;
             boolean isSlashSlash = false;
             boolean isWhiteSpace = false;
             boolean isStarSlash = false;
             boolean isText = false;
             if (!isNoMore ){
                 ch = msg.charAt(index);
                 if (ch == '/' && index+1 < msg.length()){
                     if (msg.charAt(index+1) == '/'){
                         isSlashSlash = true;
                          index++;
                     }
                     else if (msg.charAt(index+1) == '*'){
                         isSlashStar= true;
                         index++;
                     } else isText = true;
                 }
                 else if (ch == '*' && index+1 < msg.length() ){
                     if (msg.charAt(index+1) == '/'){
                         isStarSlash = true;
                         index++;
                     } else isText = true;
                 }
                 else if ( Character.isSpace(ch) || (ch == ',') // 59601
                              || (ch == ';') ) // 59683
                     isWhiteSpace = true;
                 else isText = true;
            }

            if (state == initialState){
                   if (isSlashStar){
                      state = commentState;
                   }
                   else if (isSlashSlash || isNoMore){
                      state = finalState;
                   }
                   else if (isText){
                       state = textState;
                       text = text+ ch;
                   }
             }
             else if (state == commentState){
                   if (isNoMore){
                        state = finalState;
                   }
                   else if ( isStarSlash){
                        state = initialState;
                   }
             }
             else if (state == textState){
                   if (isNoMore || isStarSlash || isSlashSlash ||
                       isSlashStar || isWhiteSpace ){
                       if (!text.equals("")) {
                           v.addElement(text);
// System.err.println("adding " + text);
                           text = "";
                       }
                       if (isNoMore)
                            state = finalState;
                       else if (isSlashStar)
                            state = commentState;
                       else state = initialState;
                   }
                   else if (isText){
                       text = text+ch;
                   }
             }
             index++;
        }
    }

}
