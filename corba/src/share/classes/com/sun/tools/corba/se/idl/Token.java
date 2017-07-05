/*
 * Copyright (c) 1999, 2001, Oracle and/or its affiliates. All rights reserved.
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
 * COMPONENT_NAME: idl.parser
 *
 * ORIGINS: 27
 *
 * Licensed Materials - Property of IBM
 * 5639-D57 (C) COPYRIGHT International Business Machines Corp. 1997, 1999
 * RMI-IIOP v1.0
 *
 */

package com.sun.tools.corba.se.idl;

// NOTES:
// -F46082.51<daz> Remove -stateful feature.
// -D59166<daz> Add support for keyword/identifier collision detection.  This
//  feature is implemented here, rather than class Scanner, to allow the Parser
//  to handle the problem.
// -F60858.1<daz> Support -corba option, level <= 2.2: identify 2.3 keywords.
// -D62023<daz> Support -corba option, level <= 2.3, identify 2.4 keywords.
// KMC Support -corba, level <= 3.0.  Added 3.0 keywords.
//
// Should escaped Identifier should be a type rather than an attribute?
//

/**
 * Class Token represents a lexeme appearing within an IDL source.  Every
 * Token has a type.  Depending on its type and on the supported version
 * of IDL, a Token will have other associated attributes, such as a name
 * (identifier, e.g.), and whether it is escaped, deprecated, or is a type
 * that is known to be in a future version of IDL.
 **/
class Token
{
  ///////////////
  // Available types

  static final int                // Keywords
      Any                  =   0, // 2.2
      Attribute            =   1, // |
      Boolean              =   2, // .
      Case                 =   3, // .
      Char                 =   4, // .
      Const                =   5,
      Context              =   6,
      Default              =   7,
      Double               =   8,
      Enum                 =   9,
      Exception            =  10,
      FALSE                =  11,
      Fixed                =  12, // New addition
      Float                =  13,
      In                   =  14,
      Inout                =  15,
      Interface            =  16,
      Long                 =  17,
      Module               =  18,
      Native               =  19, // New addition
      Object               =  20,
      Octet                =  21,
      Oneway               =  22,
      Out                  =  23,
      Raises               =  24,
      Readonly             =  25,
      Sequence             =  26,
      Short                =  27,
      String               =  28,
      Struct               =  29,
      Switch               =  30,
      TRUE                 =  31,
      Typedef              =  32,
      Unsigned             =  33, // .
      Union                =  34, // .
      Void                 =  35, // .
      Wchar                =  36, // |
      Wstring              =  37, // 2.2
      // <f46082.40> New OBV keywords...
      // <d62023> In 2.4rtf, "factory" is synonymous to "init" in 2.3
      Init                 =  38, // 2.3 only
      Abstract             =  39, // 2.3        2.4rtf
      Custom               =  40, // |          |
      Private              =  41, // |          |
      Public               =  42, // |          |
      Supports             =  43, // |          |
      Truncatable          =  44, // |          |
      ValueBase            =  45, // |          |
      Valuetype            =  46, // 2.3        2.4rtf
      Factory              =  47, //            2.4rtf only

      // Keywords in CORBA 3.0
      Component            =  48,
      Consumes             =  49,
      Emits                =  50,
      Finder               =  51,
      GetRaises            =  52,
      Home                 =  53,
      Import               =  54,
      Local                =  55,
      Manages              =  56,
      Multiple             =  57,
      PrimaryKey           =  58,
      Provides             =  59,
      Publishes            =  60,
      SetRaises            =  61,
      TypeId               =  62,
      TypePrefix           =  63,
      Uses                 =  64,

      Identifier           =  80, // Identifier
      MacroIdentifier      =  81, // Macro Identifier

      Semicolon            = 100, // Symbols
      LeftBrace            = 101,
      RightBrace           = 102,
      Colon                = 103,
      Comma                = 104,
      Equal                = 105,
      Plus                 = 106,
      Minus                = 107,
      LeftParen            = 108,
      RightParen           = 109,
      LessThan             = 110,
      GreaterThan          = 111,
      LeftBracket          = 112,
      RightBracket         = 113,
      Apostrophe           = 114,
      Quote                = 115,
      Backslash            = 116,
      Bar                  = 117,
      Carat                = 118,
      Ampersand            = 119,
      Star                 = 120,
      Slash                = 121,
      Percent              = 122,
      Tilde                = 123,
      DoubleColon          = 124,
      ShiftLeft            = 125,
      ShiftRight           = 126,
      Period               = 127,
      Hash                 = 128,
      Exclamation          = 129,
      DoubleEqual          = 130,
      NotEqual             = 131,
      GreaterEqual         = 132,
      LessEqual            = 133,
      DoubleBar            = 134,
      DoubleAmpersand      = 135,

      BooleanLiteral       = 200, // Literals
      CharacterLiteral     = 201,
      IntegerLiteral       = 202,
      FloatingPointLiteral = 203,
      StringLiteral        = 204,
      Literal              = 205,

      Define               = 300, // Directives
      Undef                = 301,
      If                   = 302,
      Ifdef                = 303,
      Ifndef               = 304,
      Else                 = 305,
      Elif                 = 306,
      Include              = 307,
      Endif                = 308,
      Line                 = 309,
      Error                = 310,
      Pragma               = 311,
      Null                 = 312,
      Unknown              = 313,

      Defined              = 400,

      // <f46082.40> Keyword identifiers.
      //Abstract             = 500,
      //Custom               = 501,
      //Init                 = 502,
      //Private2             = 503,
      //Public2              = 504,
      //Supports             = 505,
      //Truncatable          = 506,
      //ValueBase            = 507,
      //Valuetype            = 508,

      EOF                  = 999; // End of Input

  // Available types
  ///////////////
  // Keywords

  static final String [] Keywords = {
      "any",         "attribute",    "boolean",
      "case",        "char",         "const",
      "context",     "default",      "double",
      "enum",        "exception",    "FALSE",      "fixed",
      "float",       "in",           "inout",
      "interface",   "long",         "module",     "native",
      "Object",      "octet",        "oneway",
      "out",         "raises",       "readonly",
      "sequence",    "short",        "string",
      "struct",      "switch",       "TRUE",
      "typedef",     "unsigned",     "union",
      "void",        "wchar",        "wstring",
      "init", // In 2.3 only
      "abstract",     "custom",      "private",      // 2.3 and 2.4rtf
      "public",       "supports",    "truncatable",
      "ValueBase",    "valuetype",
      "factory",  // In 2.4rtf only
      // CORBA 3.0 keywords
      "component",      "consumes",     "emits",
      "finder",         "getRaises",    "home",
      "import",         "local",        "manages",
      "multiple",       "primaryKey",   "provides",
      "publishes",      "setRaises",    "supports",
      "typeId",         "typePrefix",   "uses" } ;

  // <f46082.40> Remove keyword identifiers.
  //static final int
  //    FirstKeywordIdentifier = 500,
  //    LastKeywordIdentifier  = Valuetype;
  //
  //static final String[] KeywordIdentifiers = {
  //    "abstract",    "custom",    "init",
  //    "private",     "public",    "supports",
  //    "truncatable", "valueBase", "valuetype"};

  /**
   * Determine whether this token is a keyword.
   * @return true iff this token is a keyword.
   **/
  boolean isKeyword ()
  {
    return type >= FirstKeyword && type <= LastKeyword;
  } // isKeyword

  private static final int
      FirstKeyword = Any, // 0
      LastKeyword  = Uses;

  // <f60858.1> Keywords in CORBA 2.2 that we support.
  private static final int
      First22Keyword = Any, // 0
      Last22Keyword  = Wstring;

  // <f60858.1> New keywords in CORBA 2.3 (preliminary) that we support.
  private static final int
      First23Keyword = Init,
      Last23Keyword  = Valuetype;

  // <d62023> New keywords in CORBA 2.4rtf (accepted 2.3) that we support.
  // Note that "factory" replaces "init".  Scanner must account for this in
  // keyword scan.
  private static final int
      First24rtfKeyword = Abstract,
      Last24rtfKeyword  = Factory;

  // New keywords in CORBA 3.0 (from CORBA components v. 1)
  private static final int
      First30Keyword    = Component,
      Last30Keyword     = Uses;

  // Current valid CORBA levels:
  // 2.2 (or <2.3): the default: no OBV support
  // 2.3: add OBV with init
  // >2.3: OBV with init replcaed by factory
  // 3.0: adds components, attr exceptions, local interfaces, type repository
  //      decls.

  private static final int CORBA_LEVEL_22 = 0 ;
  private static final int CORBA_LEVEL_23 = 1 ;
  private static final int CORBA_LEVEL_24RTF = 2 ;
  private static final int CORBA_LEVEL_30 = 3 ;

  // Do the conversion from a floating point CORBA level to an int
  private static int getLevel( float cLevel )
  {
    if (cLevel < 2.3f)
        return CORBA_LEVEL_22 ;
    if (Util.absDelta( cLevel, 2.3f ) < 0.001f)
        return CORBA_LEVEL_23 ;
    if (cLevel < 3.0f)
        return CORBA_LEVEL_24RTF ;
    return CORBA_LEVEL_30 ;
  }

  // Return the last keyword corresponding to a particular CORBA level
  private static int getLastKeyword( int level )
  {
    if (level == CORBA_LEVEL_22)
        return Last22Keyword ;
    if (level == CORBA_LEVEL_23)
        return Last23Keyword ;
    if (level == CORBA_LEVEL_24RTF)
        return Last24rtfKeyword ;
    return Last30Keyword ;
  }

  /** Create a keyword token from a string.
  * Determines whether the string is an IDL keyword based on the corbaLevel.
  * Strings that are keywords at higher CORBA levels than the corbaLevel
  * argument create identifier tokens that are marked as "collidesWithKeyword", unless
  * escapedOK is FALSE, which is the case only when preprocessing is taking place.
  * In the case of the "init" keyword, which was only defined in CORBA 2.3, init is
  * marked deprecated in CORBA 2.3 since it is not supported in higher levels.
  * @param String string The string we are converting to a token.
  * @param float corbaLevel The CORBA level, currently in the interval [2.2, 3.0].
  * @param boolean escapedOK Flag set true if _ is used to escape an IDL keyword for use
  * as an identifier.
  * @param boolean[] collidesWithKeyword is an array containing one value: a flag
  * representing whether this string is an identifier that collides with a keyword.
  * This is set by this method.
  * @returns Token The resulting Token corresponding to string.
  */
  public static Token makeKeywordToken(
    String string, float corbaLevel, boolean escapedOK, boolean[] collision )
  {
    int level = getLevel( corbaLevel ) ;
    int lastKeyword = getLastKeyword( level ) ;
    boolean deprecated = false ;
    collision[0] = false ;

    // If the string is a keyword token, return that token
    for (int i = Token.FirstKeyword; i <= Token.LastKeyword; ++i) {
        if (string.equals (Token.Keywords[i])) {
            // <f60858.1><d62023> Return identifier if lexeme is a keyword in a
            // greater CORBA level; collect attributes indicating future keyword/
            // identifier collision and deprecations.

            // Init is really a funny case.  I don't want to mark it as
            // a keyword collision in the 2.2 case, since it was only
            // defined to be a keyword briefly in 2.3.
            if (i == Token.Init) {
                if (level == CORBA_LEVEL_23)
                    deprecated = true ;
                else
                    break ;
            }

            if (i > lastKeyword) {
                collision[0] |= escapedOK; // escapedOK true iff not preprocessing
                break ;
            }

            if (string.equals ("TRUE") || string.equals ("FALSE"))
                return new Token (Token.BooleanLiteral, string) ;
            else
                return new Token (i, deprecated);
        } else if (string.equalsIgnoreCase (Token.Keywords[i])) {
            // <d62023> PU!  This will go away in a future release, because
            // case-insensitive keyword checking will be standard.  For now,
            // indicate that a keyword collision has occurred.
            collision[0] |= true;
            break;
        }
    } // for i <= lastKeyword

    return null ;
  } // makeKeywordToken

  // Keywords
  ///////////////
  // Symbols

  static final int
      FirstSymbol = 100,
      LastSymbol  = 199;

  static final String [] Symbols = {
      ";",  "{",  "}",  ":", ",", "=", "+",  "-",
      "(",  ")",  "<",  ">", "[", "]", "'",  "\"",
      "\\", "|",  "^",  "&", "*", "/", "%",  "~",
      "::", "<<", ">>", ".", "#", "!", "==", "!=",
      ">=", "<=", "||", "&&"};

  // Symbols
  ///////////////
  // Literals

  static final int
      FirstLiteral = 200,
      LastLiteral  = 299;

  static final String [] Literals = {
      Util.getMessage ("Token.boolLit"),
      Util.getMessage ("Token.charLit"),
      Util.getMessage ("Token.intLit"),
      Util.getMessage ("Token.floatLit"),
      Util.getMessage ("Token.stringLit"),
      Util.getMessage ("Token.literal")};

  // Literals
  ///////////////
  // Directives

  /**
   * Determine whether this token is a preprocessor directive.
   * @return true iff this token is a preprocessor directive.
   **/
  boolean isDirective ()
  {
    return type >= FirstDirective && type <= LastDirective;
  } // isDirective

  static final int
      FirstDirective = 300,
      LastDirective  = 399;

  static final String [] Directives = {
      "define", "undef",  "if",
      "ifdef",  "ifndef", "else",
      "elif",   "include","endif",
      "line",   "error",  "pragma",
      ""};

  // Directives
  ///////////////
  // Specials

  static final int
      FirstSpecial = 400,
      LastSpecial  = 499;

  static final String [] Special = {
      "defined"};

  // Specials
  ///////////////

  /**
   * Constructor.
   * @return a Token of the supplied type.
   **/
  Token (int tokenType)
  {
    type = tokenType;
  } // ctor

  // <d62023>
  /**
   * Constructor.
   * @return a Token having the supplied attributes.
   **/
  Token (int tokenType, boolean deprecated)
  {
    this.type = tokenType;
    this.isDeprecated = deprecated;
  } // ctor

  /**
   * Constructor.
   * @return a Token having the supplied attributes.
   **/
  Token (int tokenType, String tokenName)
  {
    type = tokenType;
    name = tokenName;
  } // ctor

  /**
   * Constructor.
   * @return a Token having the supplied attribtues.
   *  having
   **/
  Token (int tokenType, String tokenName, boolean isWide)
  {
    this (tokenType, tokenName);
    this.isWide = isWide;
  } // ctor


  // <d62023>
  /**
   * Constructor.
   * @return a Token having the supplied attributes.
   **/
  Token (int tokenType, String tokenName, boolean escaped,
      boolean collision, boolean deprecated)
  {
    this (tokenType, tokenName);
    this.isEscaped = escaped;
    this.collidesWithKeyword = collision;
    this.isDeprecated = deprecated;
  } // ctor

  // <f46082.40> Remove keyword identifiers.
  ///**
  // * Constructor.
  // * @return a Token having the supplied attributes.
  // **/
  //Token (int tokenType, int tokenSubType, String tokenName)
  //{
  //  type    = tokenType;
  //  subType = tokenSubType;
  //  name    = tokenName;
  //} // ctor

  /**
   * Get the String representation of this Token.
   * @return a String containing representation of this Token.
   **/
  public String toString ()
  {
    if (type == Identifier)
      return name;
    if (type == MacroIdentifier)
      return name + '(';
    return Token.toString (type);
  } // toString

  /**
   * Get the String representation of a supplied Token type.
   * @return A String containing the name of the supplied Token type.
   **/
  static String toString (int type)
  {
    if (type <= LastKeyword)
      return Keywords[type];
    // <f46082.40> Remove keyword identifiers.
    //if ( (type >= FirstKeywordIdentifier) && (type <= LastKeywordIdentifier) )
    //  return KeywordIdentifiers[ type - FirstKeywordIdentifier ];
    if (type == Identifier || type == MacroIdentifier)
      return Util.getMessage ("Token.identifier");
    if (type <= LastSymbol)
      return Symbols[type - FirstSymbol];
    if (type <= LastLiteral)
      return Literals[type - FirstLiteral];
    if (type <= LastDirective)
      return Directives[type - FirstDirective];
    if (type <= LastSpecial)
      return Special[type - FirstSpecial];
    if (type == EOF)
      return Util.getMessage ("Token.endOfFile");
    return Util.getMessage ("Token.unknown");
  } // toString

  ///////////////
  // Accessors and Predicates

  /**
   * Determine whether this token equals a supplied token.
   * @return true iff the types and names of this and the supplied
   * Token are equal.
   **/
  boolean equals (Token that)
  {
    if (this.type == that.type)
      if (this.name == null)
        return that.name == null;
      else
        return this.name.equals (that.name);
    return false;
  } // equals

  /**
   * Determine whether the this token is of a supplied type.
   * @return true iff the type of this Token equals that supplied.
   **/
  boolean equals (int type)
  {
    return this.type == type;
  } // equals

  /**
   * Determine whether this identifier has the supplied name.
   * @return true iff this Token is an identifier having the supplied name.
   **/
  boolean equals (String name)
  {
    return (this.type == Identifier && this.name.equals (name));
  } // equals

  // Although isEscaped is an independent attribute, it may be true only
  // when type is Identifer.
  /**
   * Accessor.
   * @return true iff this token is an escaped identifier.
   **/
  public boolean isEscaped ()
  {
    return type == Identifier && isEscaped;
  } // isEscaped

  // <d62023>
  /**
   * Accessor.
   * @return true iff this token is an identifier having a name matching
   * a keyword in a version of CORBA greater than the specified CORBA level,
   * or iff it matches a keyword in letter, but note case.
   **/
  public boolean collidesWithKeyword ()
  {
    return collidesWithKeyword;
  } // collidesWithKeyword

  // <d62023> Storing deprecation information in a token seems a natural
  // means to notify the parser about deprecated types.
  /**
   * Accessor.
   * @return true iff this token is a deprecated lexeme or lexical type with
   * respect to the specified CORBA level.
   **/
  public boolean isDeprecated ()
  {
    return isDeprecated;
  }
  // isDeprecated

  public boolean isWide()
  {
      return isWide ;
  }

  // <d59166><d62023> It's more efficient if Scanner determines this attribute.
  /**
   * Determine whether this token collides with an IDL keyword.
   **/
  //public boolean collidesWithKeyword ()
  //{
  //  if (name != null && type == Identifier && !isEscaped)
  //  {
  //    String lcName = name.toLowerCase ();
  //    for (int i = FirstKeyword; i <= LastKeyword; ++i)
  //      if (lcName.equals (Token.Keywords [i].toLowerCase ()))
  //        return true;
  //  }
  //  return false;
  //} // collidesWithKeyword

  // Accessors and Predicates
  ///////////////

  /**
   * Code identifying the lexical class to which this token belongs, e.g.,
   * Keyword, Identifier, ...
   **/
  int type;
  /**
   * Lexeme extracted from the source for this token.
   **/
  String name = null;
  /**
   * Source comment associated with this token.
   **/
  Comment comment = null;
  /**
   * True iff this token is an escaped identifier.
   **/
  boolean isEscaped = false; // <d59165>
  /**
   * True iff this token is an identifier that is known to be a keyword
   * in another version of CORBA or matches a keyword in letter, but not case.
   **/
  boolean collidesWithKeyword = false;  // <d62023>
  /**
   * True iff this token is deprecated.
   **/
  boolean isDeprecated = false;  // <d62023>
  // <f46082.40> Remove keyword identifier implementation.
  ///**
  // * Non-zero only when type = [Macro]Identifier
  // **/
  //int subType = 0;

  boolean isWide = false ;  // Only for string and char literals: indicates that this is
                            // a wide string or char.
} // class Token
