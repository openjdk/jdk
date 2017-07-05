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
// -D56351<daz> Update computation of RepositoryIDs to CORBA 2.3 (see spec.).
// -D59166<daz> Add escaped-id. info. to identifiers.
// -F60858.1<daz> Add support for -corba option, levels 2.2 and 2.3: accept 2.3
//   keywords as ids.; accept ids. that match keywords in letter, but not in case.
// -D62023<daz> Add support for -corba option, level 2.4: see keyword checking.

import java.io.EOFException;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;

import java.util.Enumeration;
import java.util.Stack;
import java.util.StringTokenizer;
import java.util.Vector;

/**
 *
 **/
class Scanner
{
  // <f46082.51> -Remove stateful feature.
  //Scanner (IncludeEntry file, String[] keywords, boolean vbose, boolean scanStateful, boolean emitAllIncludes) throws IOException
  // <f60858.1>
  //Scanner (IncludeEntry file, String[] keywords, boolean vbose, boolean emitAllIncludes) throws IOException
  /**
   *
   **/
  Scanner (IncludeEntry file, String[] keywords, boolean vbose,
      boolean emitAllIncludes, float cLevel, boolean debug) throws IOException
  {
    readFile (file);
    verbose  = vbose;
    // <f46082.51>
    //stateful = scanStateful;
    emitAll  = emitAllIncludes;
    sortKeywords (keywords);
    corbaLevel = cLevel;
    this.debug = debug ;
  } // ctor

  /**
   *
   **/
  void sortKeywords (String[] keywords)
  {
    for (int i = 0; i < keywords.length; ++i)
      if (wildcardAtEitherEnd (keywords[i]))
        this.openEndedKeywords.addElement (keywords[i]);
      else if (wildcardsInside (keywords[i]))
        this.wildcardKeywords.addElement (keywords[i]);
      else
        this.keywords.addElement (keywords[i]);
  } // sortKeywords

  /**
   *
   **/
  private boolean wildcardAtEitherEnd (String string)
  {
    return string.startsWith ("*") ||
           string.startsWith ("+") ||
           string.startsWith (".") ||
           string.endsWith ("*") ||
           string.endsWith ("+") ||
           string.endsWith (".");
  } // wildcardAtEitherEnd

  /**
   *
   **/
  private boolean wildcardsInside (String string)
  {
    return string.indexOf ("*") > 0 ||
           string.indexOf ("+") > 0 ||
           string.indexOf (".") > 0;
  } // wildcardsInside

  /**
   *
   **/
  void readFile (IncludeEntry file) throws IOException
  {
    String filename = file.name ();
    filename = filename.substring (1, filename.length () - 1);
    readFile (file, filename);
  } // readFile

  /**
   *
   **/
  void readFile (IncludeEntry file, String filename) throws IOException
  {
    data.fileEntry = file;
    data.filename = filename;
    // <f49747.1>
    //FileInputStream stream = new FileInputStream (data.filename);
    //data.fileBytes = new byte [stream.available ()];
    //stream.read (data.fileBytes);
    //stream.close (); <ajb>
    File idlFile = new File (data.filename);
    int len = (int)idlFile.length ();
    FileReader fileReader = new FileReader (idlFile);
    // <d41679> data.fileBytes = new char [len];
    final String EOL = System.getProperty ("line.separator");
    data.fileBytes = new char [len + EOL.length ()];

    fileReader.read (data.fileBytes, 0, len);
    fileReader.close ();

    // <d41679>
    for (int i = 0; i < EOL.length (); i++)
      data.fileBytes[len + i] = EOL.charAt (i);

    readChar ();
  } // readFile

  /**
   *
   **/
  Token getToken () throws IOException
  {
    //System.out.println ("Scanner.getToken char = |" + data.ch + "| (ASCII " + (int)data.ch + ").");

    // The token could be null if the next set of characters
    // is not a token:  white space, comments, ignored preprocessor
    // commands.
    Token token = null;
    String commentText = new String ("");

    while (token == null)
      try
      {
        data.oldIndex = data.fileIndex;
        data.oldLine  = data.line;
        if (data.ch <= ' ') {
          skipWhiteSpace ();
          continue;
        }

        // Special case for wchar and wstring literals.
        // The values are preceded by an L.
        //
        // Bug fix 4382578:  Can't compile a wchar literal.
        //
        // REVISIT.  This maps wchar/wstring literals to
        // our char/string literal types.  Eventually, we
        // need to write stronger checking to be spec
        // compliant in negative cases such as leaving the
        // L off of a wide string or putting it on a string.
        if (data.ch == 'L') {
            // Check to see if the next character is an
            // apostrophe.
            readChar();
            // Note:  This doesn't allow for space between
            // the L and the apostrophe or quote.
            if (data.ch == '\'') {
                // It was a wchar literal.  Get the value
                // and return the token.
                token = getCharacterToken(true);
                readChar();
                continue;
            } else
            if (data.ch == '"') {
                // It was a wstring literal.
                //
                // getUntil assumes we've already passed the
                // first quote.
                readChar ();
                token = new Token (Token.StringLiteral, getUntil ('"'), true);
                readChar ();
                continue;
            } else {
                // It must not have been a wchar literal.
                // Push the input back into the buffer, and
                // fall to the next if case.
                unread(data.ch);
                unread('L');
                readChar();
            }
        }

        if ((data.ch >= 'a' && data.ch <= 'z') ||
            (data.ch >= 'A' && data.ch <= 'Z') ||
            // <f46082.40> Escaped identifier; see data member comments.
            //(data.ch == '_' && underscoreOK)   || <daz>
            (data.ch == '_')   ||
            Character.isLetter (data.ch)) {
            token = getString ();
        } else
        if ((data.ch >= '0' && data.ch <= '9') || data.ch == '.') {
            token = getNumber ();
        } else {
          switch (data.ch)
          {
            case ';':
              token = new Token (Token.Semicolon);
              break;
            case '{':
              token = new Token (Token.LeftBrace);
              break;
            case '}':
              token = new Token (Token.RightBrace);
              break;
            case ':':
              readChar ();
              if (data.ch == ':')
                token = new Token (Token.DoubleColon);
              else
              {
                unread (data.ch);
                token = new Token (Token.Colon);
              }
              break;
            case ',':
              token = new Token (Token.Comma);
              break;
            case '=':
              readChar ();
              if (data.ch == '=')
                token = new Token (Token.DoubleEqual);
              else
              {
                unread (data.ch);
                token = new Token (Token.Equal);
              }
              break;
            case '+':
              token = new Token (Token.Plus);
              break;
            case '-':
              token = new Token (Token.Minus);
              break;
            case '(':
              token = new Token (Token.LeftParen);
              break;
            case ')':
              token = new Token (Token.RightParen);
              break;
            case '<':
              readChar ();
              if (data.ch == '<')
                token = new Token (Token.ShiftLeft);
              else if (data.ch == '=')
                token = new Token (Token.LessEqual);
              else
              {
                unread (data.ch);
                token = new Token (Token.LessThan);
              }
              break;
            case '>':
              readChar ();
              if (data.ch == '>')
                token = new Token (Token.ShiftRight);
              else if (data.ch == '=')
                token = new Token (Token.GreaterEqual);
              else
              {
                unread (data.ch);
                token = new Token (Token.GreaterThan);
              }
              break;
            case '[':
              token = new Token (Token.LeftBracket);
              break;
            case ']':
              token = new Token (Token.RightBracket);
              break;
            case '\'':
              token = getCharacterToken(false);
              break;
            case '"':
              readChar ();
              token = new Token (Token.StringLiteral, getUntil ('"', false, false, false));
              break;
            case '\\':
              readChar ();
              // If this is at the end of a line, then it is the
              // line continuation character - treat it as white space
              if (data.ch == '\n' || data.ch == '\r')
                token = null;
              else
                token = new Token (Token.Backslash);
              break;
            case '|':
              readChar ();
              if (data.ch == '|')
                token = new Token (Token.DoubleBar);
              else
              {
                unread (data.ch);
                token = new Token (Token.Bar);
              }
              break;
            case '^':
              token = new Token (Token.Carat);
              break;
            case '&':
              readChar ();
              if (data.ch == '&')
                token = new Token (Token.DoubleAmpersand);
              else
              {
                unread (data.ch);
                token = new Token (Token.Ampersand);
              }
              break;
            case '*':
              token = new Token (Token.Star);
              break;
            case '/':
              readChar ();
              // <21jul1997daz>  Extract comments rather than skipping them.
              // Preserve only the comment immediately preceding the next token.
              if (data.ch == '/')
                //skipLineComment ();
                commentText = getLineComment();
              else if (data.ch == '*')
                //skipBlockComment ();
                commentText = getBlockComment();
              else
              {
                unread (data.ch);
                token = new Token (Token.Slash);
              }
              break;
            case '%':
              token = new Token (Token.Percent);
              break;
            case '~':
              token = new Token (Token.Tilde);
              break;

            // The period token is recognized in getNumber.
            // The period is only valid in a floating ponit number.
            //case '.':
            //  token = new Token (Token.Period);
            //  break;

            case '#':
              token = getDirective ();
              break;
            case '!':
              readChar ();
              if (data.ch == '=')
                token = new Token (Token.NotEqual);
              else
              {
                unread (data.ch);
                token = new Token (Token.Exclamation);
              }
              break;
            case '?':
              try
              {
                token = replaceTrigraph ();
                break;
              }
              catch (InvalidCharacter e) {}
            default:
              throw new InvalidCharacter (data.filename, currentLine (), currentLineNumber (), currentLinePosition (), data.ch);
          }
          readChar ();
        }
      }
      catch (EOFException e)
      {
        token = new Token (Token.EOF);
      }

    // Transfer comment to parser via token.  <daz>21jul1997
    token.comment = new Comment( commentText );

    //System.out.println ("Scanner.getToken returning token.type = " + token.type);
    //if (token.type == Token.Identifier || token.type == Token.MacroIdentifier || (token.type >= Token.BooleanLiteral && token.type <= Token.StringLiteral))
    //  System.out.println ("Scanner.getToken returns token.name = " + token.name);

    if (debug)
        System.out.println( "Token: " + token ) ;

    return token;
  } // getToken

  /**
   *
   **/
  void scanString (String string)
  {
    dataStack.push (data);

    data = new ScannerData (data);

    data.fileIndex = 0;
    data.oldIndex  = 0;
    // <f49747.1> data.fileBytes = string.getBytes (); <ajb>
    int strLen = string.length();
    data.fileBytes = new char[strLen];
    string.getChars (0, strLen, data.fileBytes, 0);

    data.macrodata = true;

    try {readChar ();} catch (IOException e) {}
  } // scanString

  /**
   *
   **/
  void scanIncludedFile (IncludeEntry file, String filename, boolean includeIsImport) throws IOException
  {
    dataStack.push (data);
    data = new ScannerData ();
    data.indent = ((ScannerData)dataStack.peek ()).indent + ' ';
    data.includeIsImport = includeIsImport;
    try
    {
      readFile (file, filename);
      if (!emitAll && includeIsImport)
        SymtabEntry.enteringInclude ();
      // <d56351> As of CORBA 2.3, include files define new scope for Repository
      // ID prefixes. The previous Rep. ID is just below the top of the stack and
      // must be restored when the contents of this include file are parsed (see readCh()).
      Parser.enteringInclude ();

      if (verbose)
        System.out.println (data.indent + Util.getMessage ("Compile.parsing", filename));
    }
    catch (IOException e)
    {
      data = (ScannerData)dataStack.pop ();
      throw e;
    }
  } // scanIncludedFile

  /**
   *
   **/
  private void unread (char ch)
  {
    if (ch == '\n' && !data.macrodata) --data.line;
    --data.fileIndex;
  } // unread

  /**
   *
   **/
  void readChar () throws IOException
  {
    if (data.fileIndex >= data.fileBytes.length)
      if (dataStack.empty ())
        throw new EOFException ();
      else
      {
        // <d56351> Indicate end-of-scope for include file to parser.
        //Parser.exitingInclude ();

        // IBM.11666 - begin
        //if (!emitAll && data.includeIsImport && !data.macrodata)
        //{
        //SymtabEntry.exitingInclude ();
        //Parser.exitingInclude (); // <d59469>
        //}
        if (!data.macrodata)
        {
            if (!emitAll && data.includeIsImport)
                SymtabEntry.exitingInclude();
            Parser.exitingInclude();
        } // IBM.11666 - end

        if (verbose && !data.macrodata)
          System.out.println (data.indent + Util.getMessage ("Compile.parseDone", data.filename));
        data = (ScannerData)dataStack.pop ();
      }
    else
    {
      data.ch = (char)(data.fileBytes[data.fileIndex++] & 0x00ff);
      if (data.ch == '\n' && !data.macrodata) ++data.line;
    }
  } // readChar

  /**
   * Starting at a quote, reads a string with possible
   * unicode or octal values until an end quote.  Doesn't
   * handle line feeds or comments.
   */
  private String getWString() throws IOException
  {
      readChar();
      StringBuffer result = new StringBuffer();

      while (data.ch != '"') {
          if (data.ch == '\\') {
              // Could be a \ooo octal or
              // unicode hex
              readChar();
              if (data.ch == 'u') {
                  // Unicode hex
                  int num = getNDigitHexNumber(4);
                  System.out.println("Got num: " + num);
                  System.out.println("Which is: " + (int)(char)num);
                  result.append((char)num);
                  // result.append((char)getNDigitHexNumber(4));
                  // getNDigitHexNumber reads the next
                  // character, so loop without reading another
                  continue;
              } else
              if (data.ch >= '0' && data.ch <= '7') {
                  // Octal
                  result.append((char)get3DigitOctalNumber());
                  // get3DigitOctalNumber reads the next
                  // character, so loop without reading another
                  continue;
              } else {
                  // Wasn't either, so just append the
                  // slash and current character.
                  result.append('\\');
                  result.append(data.ch);
              }
          } else {
              // Just append the character
              result.append(data.ch);
          }

          // Advance to the next character
          readChar();
      }

      return result.toString();
  }

  /**
   *
   **/
  private Token getCharacterToken(boolean isWide) throws IOException
  {
    // The token name returned contains a string with two elements:
    // first the character appears, then the representation of the
    // character.  These are typically the same, but they CAN be
    // different, for example "O\117"
    Token token = null;
    readChar ();
    if ( data.ch == '\\' )
    {
      readChar ();
      if ((data.ch == 'x') || (data.ch == 'u'))
      {
        char charType = data.ch;
        int hexNum = getNDigitHexNumber ((charType == 'x') ? 2 : 4);
        return new Token (Token.CharacterLiteral,
            ((char)hexNum) + "\\" + charType + Integer.toString (hexNum, 16), isWide );
      }
      if ((data.ch >= '0') && (data.ch <= '7'))
      {
        int octNum = get3DigitOctalNumber ();
        return new Token (Token.CharacterLiteral,
            ((char)octNum) + "\\" + Integer.toString (octNum, 8), isWide );
      }
      return singleCharEscapeSequence (isWide);
    }
    token = new Token (Token.CharacterLiteral, "" + data.ch + data.ch, isWide );
    readChar ();
    return token;
  } // getCharacterToken

  /**
   *
   **/
  private Token singleCharEscapeSequence (boolean isWide) throws IOException
  {
    Token token;
    if (data.ch == 'n')
      // newline
      token = new Token (Token.CharacterLiteral, "\n\\n", isWide);
    else if (data.ch == 't')
      // horizontal tab
      token = new Token (Token.CharacterLiteral, "\t\\t", isWide);
    else if (data.ch == 'v')
      // vertical tab
      token = new Token (Token.CharacterLiteral, "\013\\v", isWide);
    else if (data.ch == 'b')
      // backspace
      token = new Token (Token.CharacterLiteral, "\b\\b", isWide);
    else if (data.ch == 'r')
      // carriage return
      token = new Token (Token.CharacterLiteral, "\r\\r", isWide);
    else if (data.ch == 'f')
      // form feed
      token = new Token (Token.CharacterLiteral, "\f\\f", isWide);
    else if (data.ch == 'a')
      // alert
      token = new Token (Token.CharacterLiteral, "\007\\a", isWide);
    else if (data.ch == '\\')
      // backslash
      token = new Token (Token.CharacterLiteral, "\\\\\\", isWide);
    else if (data.ch == '?')
      // question mark
      token = new Token (Token.CharacterLiteral, "?\\?", isWide);
    else if (data.ch == '\'')
      // single quote
      token = new Token (Token.CharacterLiteral, "'\\'", isWide);
    else if (data.ch == '"')
      // double quote
      token = new Token (Token.CharacterLiteral, "\"\\\"", isWide);
    else
      throw new InvalidCharacter (data.filename, currentLine (), currentLineNumber (), currentLinePosition (), data.ch);
    readChar ();
    return token;
  } // singleCharEscapeSequence

  private Token getString () throws IOException
  {
    StringBuffer sbuf = new StringBuffer() ;
    boolean escaped = false;  // <d59166>
    boolean[] collidesWithKeyword = { false } ;  // <d62023>

    // <f46082.40> An escaped id. begins with '_', which is followed by a normal
    // identifier.  Disallow prefixes of '_' having length > 1.
    if (data.ch == '_') {
        sbuf.append( data.ch ) ;
        readChar ();
        if (escaped = escapedOK)
            if (data.ch == '_')
                throw new InvalidCharacter (data.filename, currentLine (),
                    currentLineNumber (), currentLinePosition (), data.ch);
    }

    // Build up the string of valid characters until a non-string
    // character is encountered.
    while (Character.isLetterOrDigit( data.ch ) || (data.ch == '_')) {
        sbuf.append( data.ch ) ;
        readChar() ;
    }

    String string = sbuf.toString() ;

    // <f46082.40> Escaped identifiers - If identifier has '_' prefix, ignore
    // keyword check and strip '_'; otherwise, perform keyword check.

    if (!escaped) { // Escaped id ==> ignore keyword check
        Token result = Token.makeKeywordToken( string, corbaLevel, escapedOK,
            collidesWithKeyword ) ;
        if (result != null)
            return result ;
    }

    // At this point the string is an identifier.  If it is a
    // string which is also a Java keyword, prepend an underscore
    // so that it doesn't generate a compiler error.
    string = getIdentifier (string);

    // If a left paren immediately follows, this could be a
    // macro definition, return a MacroIdentifier
    if (data.ch == '(') {
        readChar ();
        return new Token (Token.MacroIdentifier, string, escaped,
            collidesWithKeyword[0], false);
    } else
        return new Token (Token.Identifier, string, escaped,
            collidesWithKeyword[0], false);
  }

  // Wildcard values
  static final int Star = 0, Plus = 1, Dot = 2, None = 3;

  /**
   *
   **/
  private boolean matchesClosedWildKeyword (String string)
  {
    boolean     found     = true;
    String      tmpString = string;
    Enumeration e         = wildcardKeywords.elements ();
    while (e.hasMoreElements ())
    {
      int             wildcard = None;
      StringTokenizer tokens   = new StringTokenizer ((String)e.nextElement (), "*+.", true);
      if (tokens.hasMoreTokens ())
      {
        String token = tokens.nextToken ();
        if (tmpString.startsWith (token))
        {
          tmpString = tmpString.substring (token.length ());
          while (tokens.hasMoreTokens () && found)
          {
            token = tokens.nextToken ();
            if (token.equals ("*"))
              wildcard = Star;
            else if (token.equals ("+"))
              wildcard = Plus;
            else if (token.equals ("."))
              wildcard = Dot;
            else if (wildcard == Star)
            {
              int index = tmpString.indexOf (token);
              if (index >= 0)
                tmpString = tmpString.substring (index + token.length ());
              else
                found = false;
            }
            else if (wildcard == Plus)
            {
              int index = tmpString.indexOf (token);
              if (index > 0)
                tmpString = tmpString.substring (index + token.length ());
              else
                found = false;
            }
            else if (wildcard == Dot)
            {
              int index = tmpString.indexOf (token);
              if (index == 1)
                tmpString = tmpString.substring (1 + token.length ());
              else
                found = false;
            }
          }
          if (found && tmpString.equals (""))
            break;
        }
      }
    }
    return found && tmpString.equals ("");
  } // matchesClosedWildKeyword

  /**
   *
   **/
  private String matchesOpenWildcard (String string)
  {
    Enumeration e = openEndedKeywords.elements ();
    String prepend = "";
    while (e.hasMoreElements ())
    {
      int             wildcard  = None;
      boolean         found     = true;
      String          tmpString = string;
      StringTokenizer tokens    = new StringTokenizer ((String)e.nextElement (), "*+.", true);
      while (tokens.hasMoreTokens () && found)
      {
        String token = tokens.nextToken ();
        if (token.equals ("*"))
          wildcard = Star;
        else if (token.equals ("+"))
          wildcard = Plus;
        else if (token.equals ("."))
          wildcard = Dot;
        else if (wildcard == Star)
        {
          wildcard = None;
          int index = tmpString.lastIndexOf (token);
          if (index >= 0)
            tmpString = blankOutMatch (tmpString, index, token.length ());
          else
            found = false;
        }
        else if (wildcard == Plus)
        {
          wildcard = None;
          int index = tmpString.lastIndexOf (token);
          if (index > 0)
            tmpString = blankOutMatch (tmpString, index, token.length ());
          else
            found = false;
        }
        else if (wildcard == Dot)
        {
          wildcard = None;
          int index = tmpString.lastIndexOf (token);
          if (index == 1)
            tmpString = blankOutMatch (tmpString, 1, token.length ());
          else
            found = false;
        }
        else if (wildcard == None)
          if (tmpString.startsWith (token))
            tmpString = blankOutMatch (tmpString, 0, token.length ());
          else
            found = false;
      }

      // Make sure that, if the last character of the keyword is a
      // wildcard, that the string matches what the wildcard
      // requires.
      if (found)
      {
        if (wildcard == Star)
          ;
        else if (wildcard == Plus && tmpString.lastIndexOf (' ') != tmpString.length () - 1)
          ;
        else if (wildcard == Dot && tmpString.lastIndexOf (' ') == tmpString.length () - 2)
          ;
        else if (wildcard == None && tmpString.lastIndexOf (' ') == tmpString.length () - 1)
          ;
        else
          found = false;
      }
      // If found, then prepend an underscore.  But also try matching
      // again after leading and trailing blanks are removed from
      // tmpString.  This isn't quite right, but it solves a problem
      // which surfaced in the Java mapping.  For example:
      // openEndedKeywords = {"+Helper", "+Holder", "+Package"};
      // string            = fooHelperPackage.
      // Given the mechanics of the Java mapping, _fooHelperPackage
      // COULD have a conflict, so for each occurance of a keyword,
      // an underscore is added, so this would cause two underscores:
      // __fooHelperPackage.  To accomplish this, the first time thru
      // tmpString is "fooHelper       " at this point, strip off the
      // trailing blanks and try matching "fooHelper".  This also
      // matches, so two underscores are prepended.
      if (found)
      {
        prepend = prepend + "_" + matchesOpenWildcard (tmpString.trim ());
        break;
      }
    }
    return prepend;
  } // matchesOpenWildcard

  /**
   *
   **/
  private String blankOutMatch (String string, int start, int length)
  {
    char[] blanks = new char [length];
    for (int i = 0; i < length; ++i)
      blanks[i] = ' ';
    return string.substring (0, start) + new String (blanks) + string.substring (start + length);
  } // blankOutMatch

  /**
   *
   **/
  private String getIdentifier (String string)
  {
    if (keywords.contains (string))
      // string matches a non-wildcard keyword
      string = '_' + string;
    else
    {
      // Check to see if string matches any wildcard keywords that
      // aren't open ended (don't have a wildcard as the first or
      // last character.
      String prepend = "";
      if (matchesClosedWildKeyword (string))
        prepend = "_";
      else
        // string did not match any closed wildcard keywords (that
        // is, keywords with wildcards anywhere but at the beginning
        // or end of the word).
        // Now check for * + or . at the beginning or end.
        // These require special handling because they could match
        // more than one keyword.  prepend an underscore for each
        // matched keyword.
        prepend = matchesOpenWildcard (string);
      string = prepend + string;
    }
    return string;
  } // getIdentifier

  /**
   *
   **/
  private Token getDirective () throws IOException
  {
    readChar ();
    String string = new String ();
    while ((data.ch >= 'a' && data.ch <= 'z') || (data.ch >= 'A' && data.ch <= 'Z'))
    {
      string = string + data.ch;
      readChar ();
    }
    unread (data.ch);
    for (int i = 0; i < Token.Directives.length; ++i)
      if (string.equals (Token.Directives[i]))
        return new Token (Token.FirstDirective + i);
    // If it got this far, it is an unknown preprocessor directive.
    return new Token (Token.Unknown, string);
  } // getDirective

  /**
   *
   **/
  private Token getNumber () throws IOException
  {
    if (data.ch == '.')
      return getFractionNoInteger ();
    else if (data.ch == '0')
      return isItHex ();
    else // the only other possibliities are 1..9
      return getInteger ();
  } // getNumber

  /**
   *
   **/
  private Token getFractionNoInteger () throws IOException
  {
    readChar ();
    if (data.ch >= '0' && data.ch <= '9')
      return getFraction (".");
    else
      return new Token (Token.Period);
  } // getFractionNoInteger

  /**
   *
   **/
  private Token getFraction (String string) throws IOException
  {
    while (data.ch >= '0' && data.ch <= '9')
    {
      string = string + data.ch;
      readChar ();
    }
    if (data.ch == 'e' || data.ch == 'E')
      return getExponent (string + 'E');
    else
      return new Token (Token.FloatingPointLiteral, string);
  } // getFraction

  /**
   *
   **/
  private Token getExponent (String string) throws IOException
  {
    readChar ();
    if (data.ch == '+' || data.ch == '-')
    {
      string = string + data.ch;
      readChar ();
    }
    else if (data.ch < '0' || data.ch > '9')
      throw new InvalidCharacter (data.filename, currentLine (), currentLineNumber (), currentLinePosition (), data.ch);
    while (data.ch >= '0' && data.ch <= '9')
    {
      string = string + data.ch;
      readChar ();
    }
    return new Token (Token.FloatingPointLiteral, string);
  } // getExponent

  /**
   *
   **/
  private Token isItHex () throws IOException
  {
    readChar ();
    if (data.ch == '.')
    {
      readChar ();
      return getFraction ("0.");
    }
    else if (data.ch == 'x' || data.ch == 'X')
      return getHexNumber ("0x");
    else if (data.ch == '8' || data.ch == '9')
      throw new InvalidCharacter (data.filename, currentLine (), currentLineNumber (), currentLinePosition (), data.ch);
    else if (data.ch >= '0' && data.ch <= '7')
      return getOctalNumber ();
    else if (data.ch == 'e' || data.ch == 'E')
      return getExponent ("0E");
    else
      return new Token (Token.IntegerLiteral, "0");
  } // isItHex

  /**
   *
   **/
  private Token getOctalNumber () throws IOException
  {
    String string = "0" + data.ch;
    readChar ();
    while ((data.ch >= '0' && data.ch <= '9'))
    {
      if (data.ch == '8' || data.ch == '9')
        throw new InvalidCharacter (data.filename, currentLine (), currentLineNumber (), currentLinePosition (), data.ch);
      string = string + data.ch;
      readChar ();
    }
    return new Token (Token.IntegerLiteral, string);
  } // getOctalNumber

  /**
   *
   **/
  private Token getHexNumber (String string) throws IOException
  {
    readChar ();
    if ((data.ch < '0' || data.ch > '9') && (data.ch < 'a' || data.ch > 'f') && (data.ch < 'A' || data.ch > 'F'))
      throw new InvalidCharacter (data.filename, currentLine (), currentLineNumber (), currentLinePosition (), data.ch);
    else
      while ((data.ch >= '0' && data.ch <= '9') || (data.ch >= 'a' && data.ch <= 'f') || (data.ch >= 'A' && data.ch <= 'F'))
      {
        string = string + data.ch;
        readChar ();
      }
    return new Token (Token.IntegerLiteral, string);
  } // getHexNumber

  /**
   *
   **/
  private int getNDigitHexNumber (int n) throws IOException
  {
    readChar ();
    if (!isHexChar (data.ch))
      throw new InvalidCharacter (data.filename, currentLine (),
          currentLineNumber (), currentLinePosition (), data.ch);
    String string = "" + data.ch;
    readChar ();
    for (int i = 2; i <= n; i++)
    {
      if (!isHexChar( data.ch))
        break;
      string += data.ch;
      readChar ();
    }
    try
    {
      return Integer.parseInt (string, 16);
    }
    catch (NumberFormatException e)
    {
    }
    return 0;
  } // getNDigitHexNumber

  /**
   *
   **/
  private boolean isHexChar ( char hex )
  {
    return ((data.ch >= '0') && (data.ch <= '9')) ||
        ((data.ch >= 'a') && (data.ch <= 'f')) ||
        ((data.ch >= 'A') && (data.ch <= 'F'));
  }

  /**
   *
   **/
  private int get3DigitOctalNumber () throws IOException
  {
    char firstDigit = data.ch;
    String string = "" + data.ch;
    readChar ();
    if (data.ch >= '0' && data.ch <= '7')
    {
      string = string + data.ch;
      readChar ();
      if (data.ch >= '0' && data.ch <= '7')
      {
        string = string + data.ch;
        if (firstDigit > '3')
          // This is a 3-digit number bigger than 377
          throw new InvalidCharacter (data.filename, currentLine (), currentLineNumber (), currentLinePosition (), firstDigit);
        readChar ();
      }
    }
    int ret = 0;
    try
    {
      ret = Integer.parseInt (string, 8);
    }
    catch (NumberFormatException e)
    {
      throw new InvalidCharacter (data.filename, currentLine (), currentLineNumber (), currentLinePosition (), string.charAt (0));
    }
    return ret;
  } // get3DigitOctalNumber

  /**
   *
   **/
  private Token getInteger () throws IOException
  {
    String string = "" + data.ch;
    readChar ();
    if (data.ch == '.')
    {
      readChar ();
      return getFraction (string + '.');
    }
    else  if (data.ch == 'e' || data.ch == 'E')
      return getExponent (string + 'E');
    else if (data.ch >= '0' && data.ch <= '9')
      while (data.ch >= '0' && data.ch <= '9')
      {
        string = string + data.ch;
        readChar ();
        if (data.ch == '.')
        {
          readChar ();
          return getFraction (string + '.');
        }
      }
    return new Token (Token.IntegerLiteral, string);
  } // getInteger

  /**
   *
   **/
  private Token replaceTrigraph () throws IOException
  {
    readChar ();
    if (data.ch == '?')
    {
      readChar ();
      if (data.ch == '=')
        data.ch = '#';
      else if (data.ch == '/')
        data.ch = '\\';
      else if (data.ch == '\'')
        data.ch = '^';
      else if (data.ch == '(')
        data.ch = '[';
      else if (data.ch == ')')
        data.ch = ']';
      else if (data.ch == '!')
        data.ch = '|';
      else if (data.ch == '<')
        data.ch = '{';
      else if (data.ch == '>')
        data.ch = '}';
      else if (data.ch == '-')
        data.ch = '~';
      else
      {
        unread (data.ch);
        unread ('?');
        throw new InvalidCharacter (data.filename, currentLine (), currentLineNumber (), currentLinePosition (), data.ch);
      }
      return getToken ();
    }
    else
    {
      unread ('?');
      throw new InvalidCharacter (data.filename, currentLine (), currentLineNumber (), currentLinePosition (), data.ch);
    }
  } // replaceTrigraph

  /**
   *
   **/
  void skipWhiteSpace () throws IOException
  {
    while (data.ch <= ' ')
      readChar ();
  } // skipWhiteSpace

  /**
   *
   **/
  private void skipBlockComment () throws IOException
  {
    try
    {
      boolean done = false;
      readChar ();
      while (!done)
      {
        while (data.ch != '*')
          readChar ();
        readChar ();
        if (data.ch == '/')
          done = true;
      }
    }
    catch (EOFException e)
    {
      ParseException.unclosedComment (data.filename);
      throw e;
    }
  } // skipBlockComment

  /**
   *
   **/
  void skipLineComment () throws IOException
  {
    while (data.ch != '\n')
      readChar ();
  } // skipLineComment

  // The following two routines added to extract comments rather
  // than ignore them.

  /**
   * Extract a line comment from the input buffer.
   **/
  private String getLineComment () throws IOException
  {
    StringBuffer sb = new StringBuffer( "/" );
    while (data.ch != '\n')
    {
      if (data.ch != '\r')
        sb.append (data.ch);
      readChar ();
    }
    return sb.toString();
  } // getLineComment

  /**
   * Extract a block comment from the input buffer.
   **/
  private String getBlockComment () throws IOException
  {
    StringBuffer sb = new StringBuffer ("/*");
    try
    {
      boolean done = false;
      readChar ();
      sb.append (data.ch);
      while (!done)
      {
        while (data.ch != '*')
        {
          readChar ();
          sb.append (data.ch);
        }
        readChar ();
        sb.append (data.ch);
        if (data.ch == '/')
          done = true;
      }
    }
    catch (EOFException e)
    {
      ParseException.unclosedComment (data.filename);
      throw e;
    }
    return sb.toString ();
  } // getBlockComment

  /**
   *
   **/
  Token skipUntil (char c) throws IOException
  {
    while (data.ch != c)
    {
      if (data.ch == '/')
      {
        readChar ();
        if (data.ch == '/')
        {
          skipLineComment ();
          // If this is skipping until the newline, skipLineComment
          // reads past the newline, so it won't be seen by the
          // while loop conditional check.
          if (c == '\n') break;
        }
        else if (data.ch == '*')
          skipBlockComment ();
      }
      else
        readChar ();
    }
    return getToken ();
  } // skipUntil

  // getUntil is used for macro definitions and to get quoted
  // strings, so characters within "("...")" and '"'...'"' are
  // ignored.  Ie getUntil ',' on (,,,,),X will return (,,,,)

  String getUntil (char c) throws IOException
  {
      return getUntil (c, true, true, true);
  }

  String getUntil (char c, boolean allowQuote, boolean allowCharLit, boolean allowComment) throws IOException
  {
    String string = "";
    while (data.ch != c)
      string = appendToString (string, allowQuote, allowCharLit, allowComment);
    return string;
  } // getUntil

  /**
   *
   **/
  String getUntil (char c1, char c2) throws IOException
  {
    String string = "";
    while (data.ch != c1 && data.ch != c2)
      string = appendToString (string, false, false, false);
    return string;
  } // getUntil

  /**
   *
   **/
  private String appendToString (String string, boolean allowQuote, boolean allowCharLit, boolean allowComment) throws IOException
  {
    // Ignore any comments if they are allowed
    if (allowComment && data.ch == '/')
    {
      readChar ();
      if (data.ch == '/')
        skipLineComment ();
      else if (data.ch == '*')
        skipBlockComment ();
      else
        string = string + '/';
    }
    // Handle line continuation character
    else if (data.ch == '\\')
    {
      readChar ();
      if (data.ch == '\n')
        readChar ();
      else if (data.ch == '\r')
      {
        readChar ();
        if (data.ch == '\n')
          readChar ();
      }
      else
      {
        string = string + '\\' + data.ch;
        readChar ();
      }
    }
    // characters within "("...")" and '"'...'"' are ignored.
    // Ie getUntil ',' on (,,,,),X will return (,,,)
    else
    {
      if (allowCharLit && data.ch == '"')
      {
        readChar ();
        string = string + '"';
        while (data.ch != '"')
          string = appendToString (string, true, false, allowComment);
      }
      else if (allowQuote && allowCharLit && data.ch == '(')
      {
        readChar ();
        string = string + '(';
        while (data.ch != ')')
          string = appendToString (string, false, false, allowComment);
      }
      else if (allowQuote && data.ch == '\'')
      {
        readChar ();
        string = string + "'";
        while (data.ch != '\'')
          string = appendToString (string, false, true, allowComment);
      }
      string = string + data.ch;
      readChar ();
    }
    return string;
  } // appendToString

  /**
   *
   **/
  String getStringToEOL () throws IOException
  {
    String string = new String ();
    while (data.ch != '\n')
    {
      if (data.ch == '\\')
      {
        readChar ();
        if (data.ch == '\n')
          readChar ();
        else if (data.ch == '\r')
        {
          readChar ();
          if (data.ch == '\n')
            readChar ();
        }
        else
        {
          string = string + data.ch;
          readChar ();
        }
      }
      else
      {
        string = string + data.ch;
        readChar ();
      }
    }
    return string;
  } // getStringToEOL

  /**
   *
   **/
  String filename ()
  {
    return data.filename;
  } // filename

  /**
   *
   **/
  IncludeEntry fileEntry ()
  {
    return data.fileEntry;
  } // fileEntry

  /**
   *
   **/
  int currentLineNumber ()
  {
    return data.line;
  } // currentLineNumber

  /**
   *
   **/
  int lastTokenLineNumber ()
  {
    return data.oldLine;
  } // lastTokenLineNumber

  private int BOL; // Beginning Of Line

  /**
   *
   **/
  String currentLine ()
  {
    BOL = data.fileIndex - 1;
    try
    {
      // If the current position is at the end of the line,
      // set BOL to before the end of the line so the whole
      // line is returned.
      if (data.fileBytes[BOL - 1] == '\r' && data.fileBytes[BOL] == '\n')
        BOL -= 2;
      else if (data.fileBytes[BOL] == '\n')
        --BOL;
      while (data.fileBytes[BOL] != '\n')
        --BOL;
    }
    catch (ArrayIndexOutOfBoundsException e)
    {
      BOL = -1;
    }
    ++BOL; // Go to the first character AFTER the newline
    int EOL = data.fileIndex - 1;
    try
    {
      while (data.fileBytes[EOL] != '\n' && data.fileBytes[EOL] != '\r')
        ++EOL;
    }
    catch (ArrayIndexOutOfBoundsException e)
    {
      EOL = data.fileBytes.length;
    }
    if (BOL < EOL)
      return new String (data.fileBytes, BOL, EOL - BOL);
    else
      return "";
  } // currentLine

  /**
   *
   **/
  String lastTokenLine ()
  {
    int saveFileIndex = data.fileIndex;
    data.fileIndex = data.oldIndex;
    String ret = currentLine ();
    data.fileIndex = saveFileIndex;
    return ret;
  } // lastTokenLine

  /**
   *
   **/
  int currentLinePosition ()
  {
    return data.fileIndex - BOL;
  } // currentLinePosition

  /**
   *
   **/
  int lastTokenLinePosition ()
  {
    return data.oldIndex - BOL;
  } // lastTokenLinePosition

  // The scanner data is moved to a separate class so that all of the
  // data can easily be pushed and popped to a stack.

  // The data must be stackable for macros and #included files.  When
  // a macro is encountered:  the current stack data is reserved on
  // the stack; the stack is loaded with the macro info; processing
  // proceeds with this data.  The same is true for #included files.

  // It may seem that the entire Scanner should be put on a stack in
  // the Parser since all the scanner data is stackable.  But that
  // would mean instantiating a new scanner.  The scanner must
  // continue from where it left off; when certain things cross file
  // boundaries, they must be handled by the scanner, not the parser,
  // things like:  block comments, quoted strings, tokens.
  private ScannerData data              = new ScannerData ();
  private Stack       dataStack         = new Stack ();
  private Vector      keywords          = new Vector ();
  private Vector      openEndedKeywords = new Vector ();
  private Vector      wildcardKeywords  = new Vector ();
  private boolean     verbose;
  // <f46082.40> Identifiers starting with '_' are considered "Escaped",
  // except when scanned during preprocessing.  Class Preprocessor is
  // responsible to modify the escapedOK flag accordingly.  Since preceding
  // underscores are now legal when scanning identifiers as well as
  // macro identifier, underscoreOK is obsolete.
  //
  //        boolean     underscoreOK      = false;
          boolean     escapedOK         = true;
  // <f46082.51> Remove -stateful feature.
  //        boolean     stateful;
  private boolean     emitAll;
  private float       corbaLevel;
  private boolean     debug ;
} // class Scanner

// This is a dumb class, really just a struct.  It contains all of the
// scanner class's data in one place so that that data can be easily
// pushed and popped to a stack.

/**
 *
 **/
class ScannerData
{
  /**
   *
   **/
  public ScannerData ()
  {
  } // ctor

  /**
   *
   **/
  public ScannerData (ScannerData that)
  {
    indent          = that.indent;
    fileEntry       = that.fileEntry;
    filename        = that.filename;
    fileBytes       = that.fileBytes;
    fileIndex       = that.fileIndex;
    oldIndex        = that.oldIndex;
    ch              = that.ch;
    line            = that.line;
    oldLine         = that.oldLine;
    macrodata       = that.macrodata;
    includeIsImport = that.includeIsImport;
  } // copy ctor

  String       indent          = "";
  IncludeEntry fileEntry       = null;
  String       filename        = "";

  // fileBytes is a byte array rather than a char array.  This is
  // safe because OMG IDL is specified to be ISO Latin-1 whose high-
  // order byte is always 0x0.  <f49747.1> Converted from byte[] to char[]
  // to employ Reader classes, which have Character encoding features. <ajb>
  //byte[]       fileBytes       = null;
  char[]       fileBytes       = null;
  int          fileIndex       = 0;
  int          oldIndex        = 0;
  char         ch;
  int          line            = 1;
  int          oldLine         = 1;
  boolean      macrodata       = false;
  boolean      includeIsImport = false;
} // class ScannerData
