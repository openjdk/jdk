/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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


package com.sun.xml.internal.dtdparser;

import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Set;
import java.util.Vector;

/**
 * This implements parsing of XML 1.0 DTDs.
 * <p/>
 * This conforms to the portion of the XML 1.0 specification related
 * to the external DTD subset.
 * <p/>
 * For multi-language applications (such as web servers using XML
 * processing to create dynamic content), a method supports choosing
 * a locale for parser diagnostics which is both understood by the
 * message recipient and supported by the parser.
 * <p/>
 * This parser produces a stream of parse events.  It supports some
 * features (exposing comments, CDATA sections, and entity references)
 * which are not required to be reported by conformant XML processors.
 *
 * @author David Brownell
 * @author Janet Koenig
 * @author Kohsuke KAWAGUCHI
 * @version $Id: DTDParser.java,v 1.1 2005/05/31 22:28:54 kohsuke Exp $
 */
public class DTDParser {
    public final static String TYPE_CDATA = "CDATA";
    public final static String TYPE_ID = "ID";
    public final static String TYPE_IDREF = "IDREF";
    public final static String TYPE_IDREFS = "IDREFS";
    public final static String TYPE_ENTITY = "ENTITY";
    public final static String TYPE_ENTITIES = "ENTITIES";
    public final static String TYPE_NMTOKEN = "NMTOKEN";
    public final static String TYPE_NMTOKENS = "NMTOKENS";
    public final static String TYPE_NOTATION = "NOTATION";
    public final static String TYPE_ENUMERATION = "ENUMERATION";


    // stack of input entities being merged
    private InputEntity in;

    // temporaries reused during parsing
    private StringBuffer strTmp;
    private char nameTmp [];
    private NameCache nameCache;
    private char charTmp [] = new char[2];

    // temporary DTD parsing state
    private boolean doLexicalPE;

    // DTD state, used during parsing
//    private SimpleHashtable    elements = new SimpleHashtable (47);
    protected final Set declaredElements = new java.util.HashSet();
    private SimpleHashtable params = new SimpleHashtable(7);

    // exposed to package-private subclass
    Hashtable notations = new Hashtable(7);
    SimpleHashtable entities = new SimpleHashtable(17);

    private SimpleHashtable ids = new SimpleHashtable();

    // listeners for DTD parsing events
    private DTDEventListener dtdHandler;

    private EntityResolver resolver;
    private Locale locale;

    // string constants -- use these copies so "==" works
    // package private
    static final String strANY = "ANY";
    static final String strEMPTY = "EMPTY";

    /**
     * Used by applications to request locale for diagnostics.
     *
     * @param l The locale to use, or null to use system defaults
     *          (which may include only message IDs).
     */
    public void setLocale(Locale l) throws SAXException {

        if (l != null && !messages.isLocaleSupported(l.toString())) {
            throw new SAXException(messages.getMessage(locale,
                    "P-078", new Object[]{l}));
        }
        locale = l;
    }

    /**
     * Returns the diagnostic locale.
     */
    public Locale getLocale() {
        return locale;
    }

    /**
     * Chooses a client locale to use for diagnostics, using the first
     * language specified in the list that is supported by this parser.
     * That locale is then set using <a href="#setLocale(java.util.Locale)">
     * setLocale()</a>.  Such a list could be provided by a variety of user
     * preference mechanisms, including the HTTP <em>Accept-Language</em>
     * header field.
     *
     * @param languages Array of language specifiers, ordered with the most
     *                  preferable one at the front.  For example, "en-ca" then "fr-ca",
     *                  followed by "zh_CN".  Both RFC 1766 and Java styles are supported.
     * @return The chosen locale, or null.
     * @see MessageCatalog
     */
    public Locale chooseLocale(String languages [])
            throws SAXException {

        Locale l = messages.chooseLocale(languages);

        if (l != null) {
            setLocale(l);
        }
        return l;
    }

    /**
     * Lets applications control entity resolution.
     */
    public void setEntityResolver(EntityResolver r) {

        resolver = r;
    }

    /**
     * Returns the object used to resolve entities
     */
    public EntityResolver getEntityResolver() {

        return resolver;
    }

    /**
     * Used by applications to set handling of DTD parsing events.
     */
    public void setDtdHandler(DTDEventListener handler) {
        dtdHandler = handler;
        if (handler != null)
            handler.setDocumentLocator(new Locator() {
                public String getPublicId() {
                    return DTDParser.this.getPublicId();
                }

                public String getSystemId() {
                    return DTDParser.this.getSystemId();
                }

                public int getLineNumber() {
                    return DTDParser.this.getLineNumber();
                }

                public int getColumnNumber() {
                    return DTDParser.this.getColumnNumber();
                }
            });
    }

    /**
     * Returns the handler used to for DTD parsing events.
     */
    public DTDEventListener getDtdHandler() {
        return dtdHandler;
    }

    /**
     * Parse a DTD.
     */
    public void parse(InputSource in)
            throws IOException, SAXException {
        init();
        parseInternal(in);
    }

    /**
     * Parse a DTD.
     */
    public void parse(String uri)
            throws IOException, SAXException {
        InputSource in;

        init();
        // System.out.println ("parse (\"" + uri + "\")");
        in = resolver.resolveEntity(null, uri);

        // If custom resolver punts resolution to parser, handle it ...
        if (in == null) {
            in = Resolver.createInputSource(new java.net.URL(uri), false);

            // ... or if custom resolver doesn't correctly construct the
            // input entity, patch it up enough so relative URIs work, and
            // issue a warning to minimize later confusion.
        } else if (in.getSystemId() == null) {
            warning("P-065", null);
            in.setSystemId(uri);
        }

        parseInternal(in);
    }

    // makes sure the parser is reset to "before a document"
    private void init() {
        in = null;

        // alloc temporary data used in parsing
        strTmp = new StringBuffer();
        nameTmp = new char[20];
        nameCache = new NameCache();

        // reset doc info
//        isInAttribute = false;

        doLexicalPE = false;

        entities.clear();
        notations.clear();
        params.clear();
        //    elements.clear ();
        declaredElements.clear();

        // initialize predefined references ... re-interpreted later
        builtin("amp", "&#38;");
        builtin("lt", "&#60;");
        builtin("gt", ">");
        builtin("quot", "\"");
        builtin("apos", "'");

        if (locale == null)
            locale = Locale.getDefault();
        if (resolver == null)
            resolver = new Resolver();
        if (dtdHandler == null)
            dtdHandler = new DTDHandlerBase();
    }

    private void builtin(String entityName, String entityValue) {
        InternalEntity entity;
        entity = new InternalEntity(entityName, entityValue.toCharArray());
        entities.put(entityName, entity);
    }


    ////////////////////////////////////////////////////////////////
    //
    // parsing is by recursive descent, code roughly
    // following the BNF rules except tweaked for simple
    // lookahead.  rules are more or less in numeric order,
    // except where code sharing suggests other structures.
    //
    // a classic benefit of recursive descent parsers:  it's
    // relatively easy to get diagnostics that make sense.
    //
    ////////////////////////////////////////////////////////////////


    private void parseInternal(InputSource input)
            throws IOException, SAXException {

        if (input == null)
            fatal("P-000");

        try {
            in = InputEntity.getInputEntity(dtdHandler, locale);
            in.init(input, null, null, false);

            dtdHandler.startDTD(in);

            // [30] extSubset ::= TextDecl? extSubsetDecl
            // [31] extSubsetDecl ::= ( markupdecl | conditionalSect
            //        | PEReference | S )*
            //    ... same as [79] extPE, which is where the code is

            ExternalEntity externalSubset = new ExternalEntity(in);
            externalParameterEntity(externalSubset);

            if (!in.isEOF()) {
                fatal("P-001", new Object[]
                {Integer.toHexString(((int) getc()))});
            }
            afterRoot();
            dtdHandler.endDTD();

        } catch (EndOfInputException e) {
            if (!in.isDocument()) {
                String name = in.getName();
                do {    // force a relevant URI and line number
                    in = in.pop();
                } while (in.isInternal());
                fatal("P-002", new Object[]{name});
            } else {
                fatal("P-003", null);
            }
        } catch (RuntimeException e) {
            // Don't discard location that triggered the exception
            // ## Should properly wrap exception
            System.err.print("Internal DTD parser error: "); // ##
            e.printStackTrace();
            throw new SAXParseException(e.getMessage() != null
                    ? e.getMessage() : e.getClass().getName(),
                    getPublicId(), getSystemId(),
                    getLineNumber(), getColumnNumber());

        } finally {
            // recycle temporary data used during parsing
            strTmp = null;
            nameTmp = null;
            nameCache = null;

            // ditto input sources etc
            if (in != null) {
                in.close();
                in = null;
            }

            // get rid of all DTD info ... some of it would be
            // useful for editors etc, investigate later.

            params.clear();
            entities.clear();
            notations.clear();
            declaredElements.clear();
//        elements.clear();
            ids.clear();
        }
    }

    void afterRoot() throws SAXException {
        // Make sure all IDREFs match declared ID attributes.  We scan
        // after the document element is parsed, since XML allows forward
        // references, and only now can we know if they're all resolved.

        for (Enumeration e = ids.keys();
             e.hasMoreElements();
                ) {
            String id = (String) e.nextElement();
            Boolean value = (Boolean) ids.get(id);
            if (Boolean.FALSE == value)
                error("V-024", new Object[]{id});
        }
    }


    // role is for diagnostics
    private void whitespace(String roleId)
            throws IOException, SAXException {

        // [3] S ::= (#x20 | #x9 | #xd | #xa)+
        if (!maybeWhitespace()) {
            fatal("P-004", new Object[]
            {messages.getMessage(locale, roleId)});
        }
    }

    // S?
    private boolean maybeWhitespace()
            throws IOException, SAXException {

        if (!doLexicalPE)
            return in.maybeWhitespace();

        // see getc() for the PE logic -- this lets us splice
        // expansions of PEs in "anywhere".  getc() has smarts,
        // so for external PEs we don't bypass it.

        // XXX we can marginally speed PE handling, and certainly
        // be cleaner (hence potentially more correct), by using
        // the observations that expanded PEs only start and stop
        // where whitespace is allowed.  getc wouldn't need any
        // "lexical" PE expansion logic, and no other method needs
        // to handle termination of PEs.  (parsing of literals would
        // still need to pop entities, but not parsing of references
        // in content.)

        char c = getc();
        boolean saw = false;

        while (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
            saw = true;

            // this gracefully ends things when we stop playing
            // with internal parameters.  caller should have a
            // grammar rule allowing whitespace at end of entity.
            if (in.isEOF() && !in.isInternal())
                return saw;
            c = getc();
        }
        ungetc();
        return saw;
    }

    private String maybeGetName()
            throws IOException, SAXException {

        NameCacheEntry entry = maybeGetNameCacheEntry();
        return (entry == null) ? null : entry.name;
    }

    private NameCacheEntry maybeGetNameCacheEntry()
            throws IOException, SAXException {

        // [5] Name ::= (Letter|'_'|':') (Namechar)*
        char c = getc();

        if (!XmlChars.isLetter(c) && c != ':' && c != '_') {
            ungetc();
            return null;
        }
        return nameCharString(c);
    }

    // Used when parsing enumerations
    private String getNmtoken()
            throws IOException, SAXException {

        // [7] Nmtoken ::= (Namechar)+
        char c = getc();
        if (!XmlChars.isNameChar(c))
            fatal("P-006", new Object[]{new Character(c)});
        return nameCharString(c).name;
    }

    // n.b. this gets used when parsing attribute values (for
    // internal references) so we can't use strTmp; it's also
    // a hotspot for CPU and memory in the parser (called at least
    // once for each element) so this has been optimized a bit.

    private NameCacheEntry nameCharString(char c)
            throws IOException, SAXException {

        int i = 1;

        nameTmp[0] = c;
        for (; ;) {
            if ((c = in.getNameChar()) == 0)
                break;
            if (i >= nameTmp.length) {
                char tmp [] = new char[nameTmp.length + 10];
                System.arraycopy(nameTmp, 0, tmp, 0, nameTmp.length);
                nameTmp = tmp;
            }
            nameTmp[i++] = c;
        }
        return nameCache.lookupEntry(nameTmp, i);
    }

    //
    // much similarity between parsing entity values in DTD
    // and attribute values (in DTD or content) ... both follow
    // literal parsing rules, newline canonicalization, etc
    //
    // leaves value in 'strTmp' ... either a "replacement text" (4.5),
    // or else partially normalized attribute value (the first bit
    // of 3.3.3's spec, without the "if not CDATA" bits).
    //
    private void parseLiteral(boolean isEntityValue)
            throws IOException, SAXException {

        // [9] EntityValue ::=
        //    '"' ([^"&%] | Reference | PEReference)* '"'
        //    |    "'" ([^'&%] | Reference | PEReference)* "'"
        // [10] AttValue ::=
        //    '"' ([^"&]  | Reference             )* '"'
        //    |    "'" ([^'&]  | Reference             )* "'"
        char quote = getc();
        char c;
        InputEntity source = in;

        if (quote != '\'' && quote != '"') {
            fatal("P-007");
        }

        // don't report entity expansions within attributes,
        // they're reported "fully expanded" via SAX
//    isInAttribute = !isEntityValue;

        // get value into strTmp
        strTmp = new StringBuffer();

        // scan, allowing entity push/pop wherever ...
        // expanded entities can't terminate the literal!
        for (; ;) {
            if (in != source && in.isEOF()) {
                // we don't report end of parsed entities
                // within attributes (no SAX hooks)
                in = in.pop();
                continue;
            }
            if ((c = getc()) == quote && in == source) {
                break;
            }

            //
            // Basically the "reference in attribute value"
            // row of the chart in section 4.4 of the spec
            //
            if (c == '&') {
                String entityName = maybeGetName();

                if (entityName != null) {
                    nextChar(';', "F-020", entityName);

                    // 4.4 says:  bypass these here ... we'll catch
                    // forbidden refs to unparsed entities on use
                    if (isEntityValue) {
                        strTmp.append('&');
                        strTmp.append(entityName);
                        strTmp.append(';');
                        continue;
                    }
                    expandEntityInLiteral(entityName, entities, isEntityValue);


                    // character references are always included immediately
                } else if ((c = getc()) == '#') {
                    int tmp = parseCharNumber();

                    if (tmp > 0xffff) {
                        tmp = surrogatesToCharTmp(tmp);
                        strTmp.append(charTmp[0]);
                        if (tmp == 2)
                            strTmp.append(charTmp[1]);
                    } else
                        strTmp.append((char) tmp);
                } else
                    fatal("P-009");
                continue;

            }

            // expand parameter entities only within entity value literals
            if (c == '%' && isEntityValue) {
                String entityName = maybeGetName();

                if (entityName != null) {
                    nextChar(';', "F-021", entityName);
                    expandEntityInLiteral(entityName, params, isEntityValue);
                    continue;
                } else
                    fatal("P-011");
            }

            // For attribute values ...
            if (!isEntityValue) {
                // 3.3.3 says whitespace normalizes to space...
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    strTmp.append(' ');
                    continue;
                }

                // "<" not legal in parsed literals ...
                if (c == '<')
                    fatal("P-012");
            }

            strTmp.append(c);
        }
//    isInAttribute = false;
    }

    // does a SINGLE expansion of the entity (often reparsed later)
    private void expandEntityInLiteral(String name, SimpleHashtable table,
                                       boolean isEntityValue)
            throws IOException, SAXException {

        Object entity = table.get(name);

        if (entity instanceof InternalEntity) {
            InternalEntity value = (InternalEntity) entity;
            pushReader(value.buf, name, !value.isPE);

        } else if (entity instanceof ExternalEntity) {
            if (!isEntityValue)    // must be a PE ...
                fatal("P-013", new Object[]{name});
            // XXX if this returns false ...
            pushReader((ExternalEntity) entity);

        } else if (entity == null) {
            //
            // Note:  much confusion about whether spec requires such
            // errors to be fatal in many cases, but none about whether
            // it allows "normal" errors to be unrecoverable!
            //
            fatal((table == params) ? "V-022" : "P-014",
                    new Object[]{name});
        }
    }

    // [11] SystemLiteral ::= ('"' [^"]* '"') | ("'" [^']* "'")
    // for PUBLIC and SYSTEM literals, also "<?xml ...type='literal'?>'

    // NOTE:  XML spec should explicitly say that PE ref syntax is
    // ignored in PIs, comments, SystemLiterals, and Pubid Literal
    // values ... can't process the XML spec's own DTD without doing
    // that for comments.

    private String getQuotedString(String type, String extra)
            throws IOException, SAXException {

        // use in.getc to bypass PE processing
        char quote = in.getc();

        if (quote != '\'' && quote != '"')
            fatal("P-015", new Object[]{
                messages.getMessage(locale, type, new Object[]{extra})
            });

        char c;

        strTmp = new StringBuffer();
        while ((c = in.getc()) != quote)
            strTmp.append((char) c);
        return strTmp.toString();
    }


    private String parsePublicId() throws IOException, SAXException {

        // [12] PubidLiteral ::= ('"' PubidChar* '"') | ("'" PubidChar* "'")
        // [13] PubidChar ::= #x20|#xd|#xa|[a-zA-Z0-9]|[-'()+,./:=?;!*#@$_%]
        String retval = getQuotedString("F-033", null);
        for (int i = 0; i < retval.length(); i++) {
            char c = retval.charAt(i);
            if (" \r\n-'()+,./:=?;!*#@$_%0123456789".indexOf(c) == -1
                    && !(c >= 'A' && c <= 'Z')
                    && !(c >= 'a' && c <= 'z'))
                fatal("P-016", new Object[]{new Character(c)});
        }
        strTmp = new StringBuffer();
        strTmp.append(retval);
        return normalize(false);
    }

    // [14] CharData ::= [^<&]* - ([^<&]* ']]>' [^<&]*)
    // handled by:  InputEntity.parsedContent()

    private boolean maybeComment(boolean skipStart)
            throws IOException, SAXException {

        // [15] Comment ::= '<!--'
        //        ( (Char - '-') | ('-' (Char - '-'))*
        //        '-->'
        if (!in.peek(skipStart ? "!--" : "<!--", null))
            return false;

        boolean savedLexicalPE = doLexicalPE;
        boolean saveCommentText;

        doLexicalPE = false;
        saveCommentText = false;
        if (saveCommentText)
            strTmp = new StringBuffer();

        oneComment:
        for (; ;) {
            try {
                // bypass PE expansion, but permit PEs
                // to complete ... valid docs won't care.
                for (; ;) {
                    int c = getc();
                    if (c == '-') {
                        c = getc();
                        if (c != '-') {
                            if (saveCommentText)
                                strTmp.append('-');
                            ungetc();
                            continue;
                        }
                        nextChar('>', "F-022", null);
                        break oneComment;
                    }
                    if (saveCommentText)
                        strTmp.append((char) c);
                }
            } catch (EndOfInputException e) {
                //
                // This is fatal EXCEPT when we're processing a PE...
                // in which case a validating processor reports an error.
                // External PEs are easy to detect; internal ones we
                // infer by being an internal entity outside an element.
                //
                if (in.isInternal()) {
                    error("V-021", null);
                }
                fatal("P-017");
            }
        }
        doLexicalPE = savedLexicalPE;
        if (saveCommentText)
            dtdHandler.comment(strTmp.toString());
        return true;
    }

    private boolean maybePI(boolean skipStart)
            throws IOException, SAXException {

        // [16] PI ::= '<?' PITarget
        //        (S (Char* - (Char* '?>' Char*)))?
        //        '?>'
        // [17] PITarget ::= Name - (('X'|'x')('M'|'m')('L'|'l')
        boolean savedLexicalPE = doLexicalPE;

        if (!in.peek(skipStart ? "?" : "<?", null))
            return false;
        doLexicalPE = false;

        String target = maybeGetName();

        if (target == null) {
            fatal("P-018");
        }
        if ("xml".equals(target)) {
            fatal("P-019");
        }
        if ("xml".equalsIgnoreCase(target)) {
            fatal("P-020", new Object[]{target});
        }

        if (maybeWhitespace()) {
            strTmp = new StringBuffer();
            try {
                for (; ;) {
                    // use in.getc to bypass PE processing
                    char c = in.getc();
                    //Reached the end of PI.
                    if (c == '?' && in.peekc('>'))
                        break;
                    strTmp.append(c);
                }
            } catch (EndOfInputException e) {
                fatal("P-021");
            }
            dtdHandler.processingInstruction(target, strTmp.toString());
        } else {
            if (!in.peek("?>", null)) {
                fatal("P-022");
            }
            dtdHandler.processingInstruction(target, "");
        }

        doLexicalPE = savedLexicalPE;
        return true;
    }

    // [18] CDSect ::= CDStart CData CDEnd
    // [19] CDStart ::= '<![CDATA['
    // [20] CData ::= (Char* - (Char* ']]>' Char*))
    // [21] CDEnd ::= ']]>'
    //
    //    ... handled by InputEntity.unparsedContent()

    // collapsing several rules together ...
    // simpler than attribute literals -- no reference parsing!
    private String maybeReadAttribute(String name, boolean must)
            throws IOException, SAXException {

        // [24] VersionInfo ::= S 'version' Eq \'|\" versionNum \'|\"
        // [80] EncodingDecl ::= S 'encoding' Eq \'|\" EncName \'|\"
        // [32] SDDecl ::=  S 'standalone' Eq \'|\" ... \'|\"
        if (!maybeWhitespace()) {
            if (!must) {
                return null;
            }
            fatal("P-024", new Object[]{name});
            // NOTREACHED
        }

        if (!peek(name)) {
            if (must) {
                fatal("P-024", new Object[]{name});
            } else {
                // To ensure that the whitespace is there so that when we
                // check for the next attribute we assure that the
                // whitespace still exists.
                ungetc();
                return null;
            }
        }

        // [25] Eq ::= S? '=' S?
        maybeWhitespace();
        nextChar('=', "F-023", null);
        maybeWhitespace();

        return getQuotedString("F-035", name);
    }

    private void readVersion(boolean must, String versionNum)
            throws IOException, SAXException {

        String value = maybeReadAttribute("version", must);

        // [26] versionNum ::= ([a-zA-Z0-9_.:]| '-')+

        if (must && value == null)
            fatal("P-025", new Object[]{versionNum});
        if (value != null) {
            int length = value.length();
            for (int i = 0; i < length; i++) {
                char c = value.charAt(i);
                if (!((c >= '0' && c <= '9')
                        || c == '_' || c == '.'
                        || (c >= 'a' && c <= 'z')
                        || (c >= 'A' && c <= 'Z')
                        || c == ':' || c == '-')
                )
                    fatal("P-026", new Object[]{value});
            }
        }
        if (value != null && !value.equals(versionNum))
            error("P-027", new Object[]{versionNum, value});
    }

    // common code used by most markup declarations
    // ... S (Q)Name ...
    private String getMarkupDeclname(String roleId, boolean qname)
            throws IOException, SAXException {

        String name;

        whitespace(roleId);
        name = maybeGetName();
        if (name == null)
            fatal("P-005", new Object[]
            {messages.getMessage(locale, roleId)});
        return name;
    }

    private boolean maybeMarkupDecl()
            throws IOException, SAXException {

        // [29] markupdecl ::= elementdecl | Attlistdecl
        //           | EntityDecl | NotationDecl | PI | Comment
        return maybeElementDecl()
                || maybeAttlistDecl()
                || maybeEntityDecl()
                || maybeNotationDecl()
                || maybePI(false)
                || maybeComment(false);
    }

    private static final String XmlLang = "xml:lang";

    private boolean isXmlLang(String value) {

        // [33] LanguageId ::= Langcode ('-' Subcode)*
        // [34] Langcode ::= ISO639Code | IanaCode | UserCode
        // [35] ISO639Code ::= [a-zA-Z] [a-zA-Z]
        // [36] IanaCode ::= [iI] '-' SubCode
        // [37] UserCode ::= [xX] '-' SubCode
        // [38] SubCode ::= [a-zA-Z]+

        // the ISO and IANA codes (and subcodes) are registered,
        // but that's neither a WF nor a validity constraint.

        int nextSuffix;
        char c;

        if (value.length() < 2)
            return false;
        c = value.charAt(1);
        if (c == '-') {        // IANA, or user, code
            c = value.charAt(0);
            if (!(c == 'i' || c == 'I' || c == 'x' || c == 'X'))
                return false;
            nextSuffix = 1;
        } else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
            // 2 letter ISO code, or error
            c = value.charAt(0);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')))
                return false;
            nextSuffix = 2;
        } else
            return false;

        // here "suffix" ::= '-' [a-zA-Z]+ suffix*
        while (nextSuffix < value.length()) {
            c = value.charAt(nextSuffix);
            if (c != '-')
                break;
            while (++nextSuffix < value.length()) {
                c = value.charAt(nextSuffix);
                if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')))
                    break;
            }
        }
        return value.length() == nextSuffix && c != '-';
    }


    //
    // CHAPTER 3:  Logical Structures
    //

    /**
     * To validate, subclassers should at this time make sure that
     * values are of the declared types:<UL>
     * <LI> ID and IDREF(S) values are Names
     * <LI> NMTOKEN(S) are Nmtokens
     * <LI> ENUMERATION values match one of the tokens
     * <LI> NOTATION values match a notation name
     * <LI> ENTITIY(IES) values match an unparsed external entity
     * </UL>
     * <p/>
     * <P> Separately, make sure IDREF values match some ID
     * provided in the document (in the afterRoot method).
     */
/*    void validateAttributeSyntax (Attribute attr, String value)
         throws DTDParseException {
        // ID, IDREF(S) ... values are Names
        if (Attribute.ID == attr.type()) {
            if (!XmlNames.isName (value))
                error ("V-025", new Object [] { value });

            Boolean             b = (Boolean) ids.getNonInterned (value);
            if (b == null || b.equals (Boolean.FALSE))
                ids.put (value.intern (), Boolean.TRUE);
            else
                error ("V-026", new Object [] { value });

        } else if (Attribute.IDREF == attr.type()) {
            if (!XmlNames.isName (value))
                error ("V-027", new Object [] { value });

            Boolean             b = (Boolean) ids.getNonInterned (value);
            if (b == null)
                ids.put (value.intern (), Boolean.FALSE);

        } else if (Attribute.IDREFS == attr.type()) {
            StringTokenizer     tokenizer = new StringTokenizer (value);
            Boolean             b;
            boolean             sawValue = false;

            while (tokenizer.hasMoreTokens ()) {
                value = tokenizer.nextToken ();
                if (!XmlNames.isName (value))
                    error ("V-027", new Object [] { value });
                b = (Boolean) ids.getNonInterned (value);
                if (b == null)
                    ids.put (value.intern (), Boolean.FALSE);
                sawValue = true;
            }
            if (!sawValue)
                error ("V-039", null);


        // NMTOKEN(S) ... values are Nmtoken(s)
        } else if (Attribute.NMTOKEN == attr.type()) {
            if (!XmlNames.isNmtoken (value))
                error ("V-028", new Object [] { value });

        } else if (Attribute.NMTOKENS == attr.type()) {
            StringTokenizer     tokenizer = new StringTokenizer (value);
            boolean             sawValue = false;

            while (tokenizer.hasMoreTokens ()) {
                value = tokenizer.nextToken ();
                if (!XmlNames.isNmtoken (value))
                    error ("V-028", new Object [] { value });
                sawValue = true;
            }
            if (!sawValue)
                error ("V-032", null);

        // ENUMERATION ... values match one of the tokens
        } else if (Attribute.ENUMERATION == attr.type()) {
            for (int i = 0; i < attr.values().length; i++)
                if (value.equals (attr.values()[i]))
                    return;
            error ("V-029", new Object [] { value });

        // NOTATION values match a notation name
        } else if (Attribute.NOTATION == attr.type()) {
            //
            // XXX XML 1.0 spec should probably list references to
            // externally defined notations in standalone docs as
            // validity errors.  Ditto externally defined unparsed
            // entities; neither should show up in attributes, else
            // one needs to read the external declarations in order
            // to make sense of the document (exactly what tagging
            // a doc as "standalone" intends you won't need to do).
            //
            for (int i = 0; i < attr.values().length; i++)
                if (value.equals (attr.values()[i]))
                    return;
            error ("V-030", new Object [] { value });

        // ENTITY(IES) values match an unparsed entity(ies)
        } else if (Attribute.ENTITY == attr.type()) {
            // see note above re standalone
            if (!isUnparsedEntity (value))
                error ("V-031", new Object [] { value });

        } else if (Attribute.ENTITIES == attr.type()) {
            StringTokenizer     tokenizer = new StringTokenizer (value);
            boolean             sawValue = false;

            while (tokenizer.hasMoreTokens ()) {
                value = tokenizer.nextToken ();
                // see note above re standalone
                if (!isUnparsedEntity (value))
                    error ("V-031", new Object [] { value });
                sawValue = true;
            }
            if (!sawValue)
                error ("V-040", null);

        } else if (Attribute.CDATA != attr.type())
            throw new InternalError (attr.type());
    }
*/
/*
    private boolean isUnparsedEntity (String name)
    {
        Object e = entities.getNonInterned (name);
        if (e == null || !(e instanceof ExternalEntity))
            return false;
        return ((ExternalEntity)e).notation != null;
    }
*/
    private boolean maybeElementDecl()
            throws IOException, SAXException {

        // [45] elementDecl ::= '<!ELEMENT' S Name S contentspec S? '>'
        // [46] contentspec ::= 'EMPTY' | 'ANY' | Mixed | children
        InputEntity start = peekDeclaration("!ELEMENT");

        if (start == null)
            return false;

        // n.b. for content models where inter-element whitespace is
        // ignorable, we mark that fact here.
        String name = getMarkupDeclname("F-015", true);
//    Element        element = (Element) elements.get (name);
//    boolean        declEffective = false;

/*
    if (element != null) {
        if (element.contentModel() != null) {
            error ("V-012", new Object [] { name });
        } // else <!ATTLIST name ...> came first
    } else {
        element = new Element(name);
        elements.put (element.name(), element);
        declEffective = true;
    }
*/
        if (declaredElements.contains(name))
            error("V-012", new Object[]{name});
        else {
            declaredElements.add(name);
//        declEffective = true;
        }

        short modelType;
        whitespace("F-000");
        if (peek(strEMPTY)) {
///        // leave element.contentModel as null for this case.
            dtdHandler.startContentModel(name, modelType = DTDEventListener.CONTENT_MODEL_EMPTY);
        } else if (peek(strANY)) {
///        element.setContentModel(new StringModel(StringModelType.ANY));
            dtdHandler.startContentModel(name, modelType = DTDEventListener.CONTENT_MODEL_ANY);
        } else {
            modelType = getMixedOrChildren(name);
        }

        dtdHandler.endContentModel(name, modelType);

        maybeWhitespace();
        char c = getc();
        if (c != '>')
            fatal("P-036", new Object[]{name, new Character(c)});
        if (start != in)
            error("V-013", null);

///        dtdHandler.elementDecl(element);

        return true;
    }

    // We're leaving the content model as a regular expression;
    // it's an efficient natural way to express such things, and
    // libraries often interpret them.  No whitespace in the
    // model we store, though!

    /**
     * returns content model type.
     */
    private short getMixedOrChildren(String elementName/*Element element*/)
            throws IOException, SAXException {

        InputEntity start;

        // [47] children ::= (choice|seq) ('?'|'*'|'+')?
        strTmp = new StringBuffer();

        nextChar('(', "F-028", elementName);
        start = in;
        maybeWhitespace();
        strTmp.append('(');

        short modelType;
        if (peek("#PCDATA")) {
            strTmp.append("#PCDATA");
            dtdHandler.startContentModel(elementName, modelType = DTDEventListener.CONTENT_MODEL_MIXED);
            getMixed(elementName, start);
        } else {
            dtdHandler.startContentModel(elementName, modelType = DTDEventListener.CONTENT_MODEL_CHILDREN);
            getcps(elementName, start);
        }

        return modelType;
    }

    // '(' S? already consumed
    // matching ')' must be in "start" entity if validating
    private void getcps(/*Element element,*/String elementName, InputEntity start)
            throws IOException, SAXException {

        // [48] cp ::= (Name|choice|seq) ('?'|'*'|'+')?
        // [49] choice ::= '(' S? cp (S? '|' S? cp)* S? ')'
        // [50] seq    ::= '(' S? cp (S? ',' S? cp)* S? ')'
        boolean decided = false;
        char type = 0;
//        ContentModel       retval, temp, current;

//        retval = temp = current = null;

        dtdHandler.startModelGroup();

        do {
            String tag;

            tag = maybeGetName();
            if (tag != null) {
                strTmp.append(tag);
//                temp = new ElementModel(tag);
//                getFrequency((RepeatableContent)temp);
///->
                dtdHandler.childElement(tag, getFrequency());
///<-
            } else if (peek("(")) {
                InputEntity next = in;
                strTmp.append('(');
                maybeWhitespace();
//                temp = getcps(element, next);
//                getFrequency(temp);
///->
                getcps(elementName, next);
///                getFrequency();        <- this looks like a bug
///<-
            } else
                fatal((type == 0) ? "P-039" :
                        ((type == ',') ? "P-037" : "P-038"),
                        new Object[]{new Character(getc())});

            maybeWhitespace();
            if (decided) {
                char c = getc();

//                if (current != null) {
//                    current.addChild(temp);
//                }
                if (c == type) {
                    strTmp.append(type);
                    maybeWhitespace();
                    reportConnector(type);
                    continue;
                } else if (c == '\u0029') {    // rparen
                    ungetc();
                    continue;
                } else {
                    fatal((type == 0) ? "P-041" : "P-040",
                            new Object[]{
                                new Character(c),
                                new Character(type)
                            });
                }
            } else {
                type = getc();
                switch (type) {
                case '|':
                case ',':
                    reportConnector(type);
                    break;
                default:
//                        retval = temp;
                    ungetc();
                    continue;
                }
//                retval = (ContentModel)current;
                decided = true;
//                current.addChild(temp);
                strTmp.append(type);
            }
            maybeWhitespace();
        } while (!peek(")"));

        if (in != start)
            error("V-014", new Object[]{elementName});
        strTmp.append(')');

        dtdHandler.endModelGroup(getFrequency());
//        return retval;
    }

    private void reportConnector(char type) throws SAXException {
        switch (type) {
        case '|':
            dtdHandler.connector(DTDEventListener.CHOICE);    ///<-
            return;
        case ',':
            dtdHandler.connector(DTDEventListener.SEQUENCE); ///<-
            return;
        default:
            throw new Error();    //assertion failed.
        }
    }

    private short getFrequency()
            throws IOException, SAXException {

        final char c = getc();

        if (c == '?') {
            strTmp.append(c);
            return DTDEventListener.OCCURENCE_ZERO_OR_ONE;
            //        original.setRepeat(Repeat.ZERO_OR_ONE);
        } else if (c == '+') {
            strTmp.append(c);
            return DTDEventListener.OCCURENCE_ONE_OR_MORE;
            //        original.setRepeat(Repeat.ONE_OR_MORE);
        } else if (c == '*') {
            strTmp.append(c);
            return DTDEventListener.OCCURENCE_ZERO_OR_MORE;
            //        original.setRepeat(Repeat.ZERO_OR_MORE);
        } else {
            ungetc();
            return DTDEventListener.OCCURENCE_ONCE;
        }
    }

    // '(' S? '#PCDATA' already consumed
    // matching ')' must be in "start" entity if validating
    private void getMixed(String elementName, /*Element element,*/ InputEntity start)
            throws IOException, SAXException {

        // [51] Mixed ::= '(' S? '#PCDATA' (S? '|' S? Name)* S? ')*'
        //        | '(' S? '#PCDATA'                   S? ')'
        maybeWhitespace();
        if (peek("\u0029*") || peek("\u0029")) {
            if (in != start)
                error("V-014", new Object[]{elementName});
            strTmp.append(')');
//            element.setContentModel(new StringModel(StringModelType.PCDATA));
            return;
        }

        ArrayList l = new ArrayList();
//    l.add(new StringModel(StringModelType.PCDATA));


        while (peek("|")) {
            String name;

            strTmp.append('|');
            maybeWhitespace();

            doLexicalPE = true;
            name = maybeGetName();
            if (name == null)
                fatal("P-042", new Object[]
                {elementName, Integer.toHexString(getc())});
            if (l.contains(name)) {
                error("V-015", new Object[]{name});
            } else {
                l.add(name);
                dtdHandler.mixedElement(name);
            }
            strTmp.append(name);
            maybeWhitespace();
        }

        if (!peek("\u0029*"))    // right paren
            fatal("P-043", new Object[]
            {elementName, new Character(getc())});
        if (in != start)
            error("V-014", new Object[]{elementName});
        strTmp.append(')');
//        ChoiceModel cm = new ChoiceModel((Collection)l);
//    cm.setRepeat(Repeat.ZERO_OR_MORE);
//       element.setContentModel(cm);
    }

    private boolean maybeAttlistDecl()
            throws IOException, SAXException {

        // [52] AttlistDecl ::= '<!ATTLIST' S Name AttDef* S? '>'
        InputEntity start = peekDeclaration("!ATTLIST");

        if (start == null)
            return false;

        String elementName = getMarkupDeclname("F-016", true);
//    Element    element = (Element) elements.get (name);

//    if (element == null) {
//        // not yet declared -- no problem.
//        element = new Element(name);
//        elements.put(name, element);
//    }

        while (!peek(">")) {

            // [53] AttDef ::= S Name S AttType S DefaultDecl
            // [54] AttType ::= StringType | TokenizedType | EnumeratedType

            // look for global attribute definitions, don't expand for now...
            maybeWhitespace();
            char c = getc();
            if (c == '%') {
                String entityName = maybeGetName();
                if (entityName != null) {
                    nextChar(';', "F-021", entityName);
                    whitespace("F-021");
                    continue;
                } else
                    fatal("P-011");
            }

            ungetc();
            // look for attribute name otherwise
            String attName = maybeGetName();
            if (attName == null) {
                fatal("P-044", new Object[]{new Character(getc())});
            }
            whitespace("F-001");

///        Attribute    a = new Attribute (name);

            String typeName;
            Vector values = null;    // notation/enumeration values

            // Note:  use the type constants from Attribute
            // so that "==" may be used (faster)

            // [55] StringType ::= 'CDATA'
            if (peek(TYPE_CDATA))
///            a.setType(Attribute.CDATA);
                typeName = TYPE_CDATA;

            // [56] TokenizedType ::= 'ID' | 'IDREF' | 'IDREFS'
            //        | 'ENTITY' | 'ENTITIES'
            //        | 'NMTOKEN' | 'NMTOKENS'
            // n.b. if "IDREFS" is there, both "ID" and "IDREF"
            // match peekahead ... so this order matters!
            else if (peek(TYPE_IDREFS))
                typeName = TYPE_IDREFS;
            else if (peek(TYPE_IDREF))
                typeName = TYPE_IDREF;
            else if (peek(TYPE_ID)) {
                typeName = TYPE_ID;
// TODO: should implement this error check?
///        if (element.id() != null) {
///                    error ("V-016", new Object [] { element.id() });
///        } else
///            element.setId(name);
            } else if (peek(TYPE_ENTITY))
                typeName = TYPE_ENTITY;
            else if (peek(TYPE_ENTITIES))
                typeName = TYPE_ENTITIES;
            else if (peek(TYPE_NMTOKENS))
                typeName = TYPE_NMTOKENS;
            else if (peek(TYPE_NMTOKEN))
                typeName = TYPE_NMTOKEN;

            // [57] EnumeratedType ::= NotationType | Enumeration
            // [58] NotationType ::= 'NOTATION' S '(' S? Name
            //        (S? '|' S? Name)* S? ')'
            else if (peek(TYPE_NOTATION)) {
                typeName = TYPE_NOTATION;
                whitespace("F-002");
                nextChar('(', "F-029", null);
                maybeWhitespace();

                values = new Vector();
                do {
                    String name;
                    if ((name = maybeGetName()) == null)
                        fatal("P-068");
                    // permit deferred declarations
                    if (notations.get(name) == null)
                        notations.put(name, name);
                    values.addElement(name);
                    maybeWhitespace();
                    if (peek("|"))
                        maybeWhitespace();
                } while (!peek(")"));
///            a.setValues(new String [v.size ()]);
///            for (int i = 0; i < v.size (); i++)
///                a.setValue(i, (String)v.elementAt(i));

                // [59] Enumeration ::= '(' S? Nmtoken (S? '|' Nmtoken)* S? ')'
            } else if (peek("(")) {
///            a.setType(Attribute.ENUMERATION);
                typeName = TYPE_ENUMERATION;

                maybeWhitespace();

///            Vector v = new Vector ();
                values = new Vector();
                do {
                    String name = getNmtoken();
///                v.addElement (name);
                    values.addElement(name);
                    maybeWhitespace();
                    if (peek("|"))
                        maybeWhitespace();
                } while (!peek(")"));
///            a.setValues(new String [v.size ()]);
///            for (int i = 0; i < v.size (); i++)
///                a.setValue(i, (String)v.elementAt(i));
            } else {
                fatal("P-045",
                        new Object[]{attName, new Character(getc())});
                typeName = null;
            }

            short attributeUse;
            String defaultValue = null;

            // [60] DefaultDecl ::= '#REQUIRED' | '#IMPLIED'
            //        | (('#FIXED' S)? AttValue)
            whitespace("F-003");
            if (peek("#REQUIRED"))
                attributeUse = DTDEventListener.USE_REQUIRED;
///            a.setIsRequired(true);
            else if (peek("#FIXED")) {
///            if (a.type() == Attribute.ID)
                if (typeName == TYPE_ID)
                    error("V-017", new Object[]{attName});
///            a.setIsFixed(true);
                attributeUse = DTDEventListener.USE_FIXED;
                whitespace("F-004");
                parseLiteral(false);
///            if (a.type() != Attribute.CDATA)
///                a.setDefaultValue(normalize(false));
///            else
///                a.setDefaultValue(strTmp.toString());

                if (typeName == TYPE_CDATA)
                    defaultValue = normalize(false);
                else
                    defaultValue = strTmp.toString();

// TODO: implement this check
///            if (a.type() != Attribute.CDATA)
///                validateAttributeSyntax (a, a.defaultValue());
            } else if (!peek("#IMPLIED")) {
                attributeUse = DTDEventListener.USE_IMPLIED;

///            if (a.type() == Attribute.ID)
                if (typeName == TYPE_ID)
                    error("V-018", new Object[]{attName});
                parseLiteral(false);
///            if (a.type() != Attribute.CDATA)
///                a.setDefaultValue(normalize(false));
///            else
///                a.setDefaultValue(strTmp.toString());
                if (typeName == TYPE_CDATA)
                    defaultValue = normalize(false);
                else
                    defaultValue = strTmp.toString();

// TODO: implement this check
///            if (a.type() != Attribute.CDATA)
///                validateAttributeSyntax (a, a.defaultValue());
            } else {
                // TODO: this looks like an fatal error.
                attributeUse = DTDEventListener.USE_NORMAL;
            }

            if (XmlLang.equals(attName)
                    && defaultValue/* a.defaultValue()*/ != null
                    && !isXmlLang(defaultValue/*a.defaultValue()*/))
                error("P-033", new Object[]{defaultValue /*a.defaultValue()*/});

// TODO: isn't it an error to specify the same attribute twice?
///        if (!element.attributes().contains(a)) {
///            element.addAttribute(a);
///            dtdHandler.attributeDecl(a);
///        }

            String[] v = (values != null) ? (String[]) values.toArray(new String[0]) : null;
            dtdHandler.attributeDecl(elementName, attName, typeName, v, attributeUse, defaultValue);
            maybeWhitespace();
        }
        if (start != in)
            error("V-013", null);
        return true;
    }

    // used when parsing literal attribute values,
    // or public identifiers.
    //
    // input in strTmp
    private String normalize(boolean invalidIfNeeded) {

        // this can allocate an extra string...

        String s = strTmp.toString();
        String s2 = s.trim();
        boolean didStrip = false;

        if (s != s2) {
            s = s2;
            s2 = null;
            didStrip = true;
        }
        strTmp = new StringBuffer();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!XmlChars.isSpace(c)) {
                strTmp.append(c);
                continue;
            }
            strTmp.append(' ');
            while (++i < s.length() && XmlChars.isSpace(s.charAt(i)))
                didStrip = true;
            i--;
        }
        if (didStrip)
            return strTmp.toString();
        else
            return s;
    }

    private boolean maybeConditionalSect()
            throws IOException, SAXException {

        // [61] conditionalSect ::= includeSect | ignoreSect

        if (!peek("<!["))
            return false;

        String keyword;
        InputEntity start = in;

        maybeWhitespace();

        if ((keyword = maybeGetName()) == null)
            fatal("P-046");
        maybeWhitespace();
        nextChar('[', "F-030", null);

        // [62] includeSect ::= '<![' S? 'INCLUDE' S? '['
        //                extSubsetDecl ']]>'
        if ("INCLUDE".equals(keyword)) {
            for (; ;) {
                while (in.isEOF() && in != start)
                    in = in.pop();
                if (in.isEOF()) {
                    error("V-020", null);
                }
                if (peek("]]>"))
                    break;

                doLexicalPE = false;
                if (maybeWhitespace())
                    continue;
                if (maybePEReference())
                    continue;
                doLexicalPE = true;
                if (maybeMarkupDecl() || maybeConditionalSect())
                    continue;

                fatal("P-047");
            }

            // [63] ignoreSect ::= '<![' S? 'IGNORE' S? '['
            //            ignoreSectcontents ']]>'
            // [64] ignoreSectcontents ::= Ignore ('<!['
            //            ignoreSectcontents ']]>' Ignore)*
            // [65] Ignore ::= Char* - (Char* ('<![' | ']]>') Char*)
        } else if ("IGNORE".equals(keyword)) {
            int nestlevel = 1;
            // ignoreSectcontents
            doLexicalPE = false;
            while (nestlevel > 0) {
                char c = getc();    // will pop input entities
                if (c == '<') {
                    if (peek("!["))
                        nestlevel++;
                } else if (c == ']') {
                    if (peek("]>"))
                        nestlevel--;
                } else
                    continue;
            }
        } else
            fatal("P-048", new Object[]{keyword});
        return true;
    }


    //
    // CHAPTER 4:  Physical Structures
    //

    // parse decimal or hex numeric character reference
    private int parseCharNumber()
            throws IOException, SAXException {

        char c;
        int retval = 0;

        // n.b. we ignore overflow ...
        if (getc() != 'x') {
            ungetc();
            for (; ;) {
                c = getc();
                if (c >= '0' && c <= '9') {
                    retval *= 10;
                    retval += (c - '0');
                    continue;
                }
                if (c == ';')
                    return retval;
                fatal("P-049");
            }
        } else
            for (; ;) {
                c = getc();
                if (c >= '0' && c <= '9') {
                    retval <<= 4;
                    retval += (c - '0');
                    continue;
                }
                if (c >= 'a' && c <= 'f') {
                    retval <<= 4;
                    retval += 10 + (c - 'a');
                    continue;
                }
                if (c >= 'A' && c <= 'F') {
                    retval <<= 4;
                    retval += 10 + (c - 'A');
                    continue;
                }
                if (c == ';')
                    return retval;
                fatal("P-050");
            }
    }

    // parameter is a UCS-4 character ... i.e. not just 16 bit UNICODE,
    // though still subject to the 'Char' construct in XML
    private int surrogatesToCharTmp(int ucs4)
            throws SAXException {

        if (ucs4 <= 0xffff) {
            if (XmlChars.isChar(ucs4)) {
                charTmp[0] = (char) ucs4;
                return 1;
            }
        } else if (ucs4 <= 0x0010ffff) {
            // we represent these as UNICODE surrogate pairs
            ucs4 -= 0x10000;
            charTmp[0] = (char) (0xd800 | ((ucs4 >> 10) & 0x03ff));
            charTmp[1] = (char) (0xdc00 | (ucs4 & 0x03ff));
            return 2;
        }
        fatal("P-051", new Object[]{Integer.toHexString(ucs4)});
        // NOTREACHED
        return -1;
    }

    private boolean maybePEReference()
            throws IOException, SAXException {

        // This is the SYNTACTIC version of this construct.
        // When processing external entities, there is also
        // a LEXICAL version; see getc() and doLexicalPE.

        // [69] PEReference ::= '%' Name ';'
        if (!in.peekc('%'))
            return false;

        String name = maybeGetName();
        Object entity;

        if (name == null)
            fatal("P-011");
        nextChar(';', "F-021", name);
        entity = params.get(name);

        if (entity instanceof InternalEntity) {
            InternalEntity value = (InternalEntity) entity;
            pushReader(value.buf, name, false);

        } else if (entity instanceof ExternalEntity) {
            pushReader((ExternalEntity) entity);
            externalParameterEntity((ExternalEntity) entity);

        } else if (entity == null) {
            error("V-022", new Object[]{name});
        }
        return true;
    }

    private boolean maybeEntityDecl()
            throws IOException, SAXException {

        // [70] EntityDecl ::= GEDecl | PEDecl
        // [71] GEDecl ::= '<!ENTITY' S       Name S EntityDef S? '>'
        // [72] PEDecl ::= '<!ENTITY' S '%' S Name S PEDEF     S? '>'
        // [73] EntityDef ::= EntityValue | (ExternalID NDataDecl?)
        // [74] PEDef     ::= EntityValue |  ExternalID
        //
        InputEntity start = peekDeclaration("!ENTITY");

        if (start == null)
            return false;

        String entityName;
        SimpleHashtable defns;
        ExternalEntity externalId;
        boolean doStore;

        // PE expansion gets selectively turned off several places:
        // in ENTITY declarations (here), in comments, in PIs.

        // Here, we allow PE entities to be declared, and allows
        // literals to include PE refs without the added spaces
        // required with their expansion in markup decls.

        doLexicalPE = false;
        whitespace("F-005");
        if (in.peekc('%')) {
            whitespace("F-006");
            defns = params;
        } else
            defns = entities;

        ungetc();    // leave some whitespace
        doLexicalPE = true;
        entityName = getMarkupDeclname("F-017", false);
        whitespace("F-007");
        externalId = maybeExternalID();

        //
        // first definition sticks ... e.g. internal subset PEs are used
        // to override DTD defaults.  It's also an "error" to incorrectly
        // redefine builtin internal entities, but since reporting such
        // errors is optional we only give warnings ("just in case") for
        // non-parameter entities.
        //
        doStore = (defns.get(entityName) == null);
        if (!doStore && defns == entities)
            warning("P-054", new Object[]{entityName});

        // internal entities
        if (externalId == null) {
            char value [];
            InternalEntity entity;

            doLexicalPE = false;        // "ab%bar;cd" -maybe-> "abcd"
            parseLiteral(true);
            doLexicalPE = true;
            if (doStore) {
                value = new char[strTmp.length()];
                if (value.length != 0)
                    strTmp.getChars(0, value.length, value, 0);
                entity = new InternalEntity(entityName, value);
                entity.isPE = (defns == params);
                entity.isFromInternalSubset = false;
                defns.put(entityName, entity);
                if (defns == entities)
                    dtdHandler.internalGeneralEntityDecl(entityName,
                            new String(value));
            }

            // external entities (including unparsed)
        } else {
            // [76] NDataDecl ::= S 'NDATA' S Name
            if (defns == entities && maybeWhitespace()
                    && peek("NDATA")) {
                externalId.notation = getMarkupDeclname("F-018", false);

                // flag undeclared notation for checking after
                // the DTD is fully processed
                if (notations.get(externalId.notation) == null)
                    notations.put(externalId.notation, Boolean.TRUE);
            }
            externalId.name = entityName;
            externalId.isPE = (defns == params);
            externalId.isFromInternalSubset = false;
            if (doStore) {
                defns.put(entityName, externalId);
                if (externalId.notation != null)
                    dtdHandler.unparsedEntityDecl(entityName,
                            externalId.publicId, externalId.systemId,
                            externalId.notation);
                else if (defns == entities)
                    dtdHandler.externalGeneralEntityDecl(entityName,
                            externalId.publicId, externalId.systemId);
            }
        }
        maybeWhitespace();
        nextChar('>', "F-031", entityName);
        if (start != in)
            error("V-013", null);
        return true;
    }

    private ExternalEntity maybeExternalID()
            throws IOException, SAXException {

        // [75] ExternalID ::= 'SYSTEM' S SystemLiteral
        //        | 'PUBLIC' S' PubidLiteral S Systemliteral
        String temp = null;
        ExternalEntity retval;

        if (peek("PUBLIC")) {
            whitespace("F-009");
            temp = parsePublicId();
        } else if (!peek("SYSTEM"))
            return null;

        retval = new ExternalEntity(in);
        retval.publicId = temp;
        whitespace("F-008");
        retval.systemId = parseSystemId();
        return retval;
    }

    private String parseSystemId()
            throws IOException, SAXException {

        String uri = getQuotedString("F-034", null);
        int temp = uri.indexOf(':');

        // resolve relative URIs ... must do it here since
        // it's relative to the source file holding the URI!

        // "new java.net.URL (URL, string)" conforms to RFC 1630,
        // but we can't use that except when the URI is a URL.
        // The entity resolver is allowed to handle URIs that are
        // not URLs, so we pass URIs through with scheme intact
        if (temp == -1 || uri.indexOf('/') < temp) {
            String baseURI;

            baseURI = in.getSystemId();
            if (baseURI == null)
                fatal("P-055", new Object[]{uri});
            if (uri.length() == 0)
                uri = ".";
            baseURI = baseURI.substring(0, baseURI.lastIndexOf('/') + 1);
            if (uri.charAt(0) != '/')
                uri = baseURI + uri;
            else {
                // XXX slashes at the beginning of a relative URI are
                // a special case we don't handle.
                throw new InternalError();
            }

            // letting other code map any "/xxx/../" or "/./" to "/",
            // since all URIs must handle it the same.
        }
        // check for fragment ID in URI
        if (uri.indexOf('#') != -1)
            error("P-056", new Object[]{uri});
        return uri;
    }

    private void maybeTextDecl()
            throws IOException, SAXException {

        // [77] TextDecl ::= '<?xml' VersionInfo? EncodingDecl S? '?>'
        if (peek("<?xml")) {
            readVersion(false, "1.0");
            readEncoding(true);
            maybeWhitespace();
            if (!peek("?>"))
                fatal("P-057");
        }
    }

    private void externalParameterEntity(ExternalEntity next)
            throws IOException, SAXException {

        //
        // Reap the intended benefits of standalone declarations:
        // don't deal with external parameter entities, except to
        // validate the standalone declaration.
        //

        // n.b. "in external parameter entities" (and external
        // DTD subset, same grammar) parameter references can
        // occur "within" markup declarations ... expansions can
        // cross syntax rules.  Flagged here; affects getc().

        // [79] ExtPE ::= TextDecl? extSubsetDecl
        // [31] extSubsetDecl ::= ( markupdecl | conditionalSect
        //        | PEReference | S )*
        InputEntity pe;

        // XXX if this returns false ...

        pe = in;
        maybeTextDecl();
        while (!pe.isEOF()) {
            // pop internal PEs (and whitespace before/after)
            if (in.isEOF()) {
                in = in.pop();
                continue;
            }
            doLexicalPE = false;
            if (maybeWhitespace())
                continue;
            if (maybePEReference())
                continue;
            doLexicalPE = true;
            if (maybeMarkupDecl() || maybeConditionalSect())
                continue;
            break;
        }
        // if (in != pe) throw new InternalError("who popped my PE?");
        if (!pe.isEOF())
            fatal("P-059", new Object[]{in.getName()});
    }

    private void readEncoding(boolean must)
            throws IOException, SAXException {

        // [81] EncName ::= [A-Za-z] ([A-Za-z0-9._] | '-')*
        String name = maybeReadAttribute("encoding", must);

        if (name == null)
            return;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if ((c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z'))
                continue;
            if (i != 0
                    && ((c >= '0' && c <= '9')
                    || c == '-'
                    || c == '_'
                    || c == '.'
                    ))
                continue;
            fatal("P-060", new Object[]{new Character(c)});
        }

        //
        // This should be the encoding in use, and it's even an error for
        // it to be anything else (in certain cases that are impractical to
        // to test, and may even be insufficient).  So, we do the best we
        // can, and warn if things look suspicious.  Note that Java doesn't
        // uniformly expose the encodings, and that the names it uses
        // internally are nonstandard.  Also, that the XML spec allows
        // such "errors" not to be reported at all.
        //
        String currentEncoding = in.getEncoding();

        if (currentEncoding != null
                && !name.equalsIgnoreCase(currentEncoding))
            warning("P-061", new Object[]{name, currentEncoding});
    }

    private boolean maybeNotationDecl()
            throws IOException, SAXException {

        // [82] NotationDecl ::= '<!NOTATION' S Name S
        //        (ExternalID | PublicID) S? '>'
        // [83] PublicID ::= 'PUBLIC' S PubidLiteral
        InputEntity start = peekDeclaration("!NOTATION");

        if (start == null)
            return false;

        String name = getMarkupDeclname("F-019", false);
        ExternalEntity entity = new ExternalEntity(in);

        whitespace("F-011");
        if (peek("PUBLIC")) {
            whitespace("F-009");
            entity.publicId = parsePublicId();
            if (maybeWhitespace()) {
                if (!peek(">"))
                    entity.systemId = parseSystemId();
                else
                    ungetc();
            }
        } else if (peek("SYSTEM")) {
            whitespace("F-008");
            entity.systemId = parseSystemId();
        } else
            fatal("P-062");
        maybeWhitespace();
        nextChar('>', "F-032", name);
        if (start != in)
            error("V-013", null);
        if (entity.systemId != null && entity.systemId.indexOf('#') != -1)
            error("P-056", new Object[]{entity.systemId});

        Object value = notations.get(name);
        if (value != null && value instanceof ExternalEntity)
            warning("P-063", new Object[]{name});

        else {
            notations.put(name, entity);
            dtdHandler.notationDecl(name, entity.publicId,
                    entity.systemId);
        }
        return true;
    }


    ////////////////////////////////////////////////////////////////
    //
    //    UTILITIES
    //
    ////////////////////////////////////////////////////////////////

    private char getc() throws IOException, SAXException {

        if (!doLexicalPE) {
            char c = in.getc();
            return c;
        }

        //
        // External parameter entities get funky processing of '%param;'
        // references.  It's not clearly defined in the XML spec; but it
        // boils down to having those refs be _lexical_ in most cases to
        // include partial syntax productions.  It also needs selective
        // enabling; "<!ENTITY % foo ...>" must work, for example, and
        // if "bar" is an empty string PE, "ab%bar;cd" becomes "abcd"
        // if it's expanded in a literal, else "ab  cd".  PEs also do
        // not expand within comments or PIs, and external PEs are only
        // allowed to have markup decls (and so aren't handled lexically).
        //
        // This PE handling should be merged into maybeWhitespace, where
        // it can be dealt with more consistently.
        //
        // Also, there are some validity constraints in this area.
        //
        char c;

        while (in.isEOF()) {
            if (in.isInternal() || (doLexicalPE && !in.isDocument()))
                in = in.pop();
            else {
                fatal("P-064", new Object[]{in.getName()});
            }
        }
        if ((c = in.getc()) == '%' && doLexicalPE) {
            // PE ref ::= '%' name ';'
            String name = maybeGetName();
            Object entity;

            if (name == null)
                fatal("P-011");
            nextChar(';', "F-021", name);
            entity = params.get(name);

            // push a magic "entity" before and after the
            // real one, so ungetc() behaves uniformly
            pushReader(" ".toCharArray(), null, false);
            if (entity instanceof InternalEntity)
                pushReader(((InternalEntity) entity).buf, name, false);
            else if (entity instanceof ExternalEntity)
            // PEs can't be unparsed!
            // XXX if this returns false ...
                pushReader((ExternalEntity) entity);
            else if (entity == null)
            // see note in maybePEReference re making this be nonfatal.
                fatal("V-022");
            else
                throw new InternalError();
            pushReader(" ".toCharArray(), null, false);
            return in.getc();
        }
        return c;
    }

    private void ungetc() {

        in.ungetc();
    }

    private boolean peek(String s)
            throws IOException, SAXException {

        return in.peek(s, null);
    }

    // Return the entity starting the specified declaration
    // (for validating declaration nesting) else null.

    private InputEntity peekDeclaration(String s)
            throws IOException, SAXException {

        InputEntity start;

        if (!in.peekc('<'))
            return null;
        start = in;
        if (in.peek(s, null))
            return start;
        in.ungetc();
        return null;
    }

    private void nextChar(char c, String location, String near)
            throws IOException, SAXException {

        while (in.isEOF() && !in.isDocument())
            in = in.pop();
        if (!in.peekc(c))
            fatal("P-008", new Object[]
            {new Character(c),
             messages.getMessage(locale, location),
             (near == null ? "" : ('"' + near + '"'))});
    }


    private void pushReader(char buf [], String name, boolean isGeneral)
            throws SAXException {

        InputEntity r = InputEntity.getInputEntity(dtdHandler, locale);
        r.init(buf, name, in, !isGeneral);
        in = r;
    }

    private boolean pushReader(ExternalEntity next)
            throws IOException, SAXException {

        InputEntity r = InputEntity.getInputEntity(dtdHandler, locale);
        InputSource s;
        try {
            s = next.getInputSource(resolver);
        } catch (IOException e) {
            String msg =
                    "unable to open the external entity from :" + next.systemId;
            if (next.publicId != null)
                msg += " (public id:" + next.publicId + ")";

            SAXParseException spe = new SAXParseException(msg,
                    getPublicId(), getSystemId(), getLineNumber(), getColumnNumber(), e);
            dtdHandler.fatalError(spe);
            throw e;
        }

        r.init(s, next.name, in, next.isPE);
        in = r;
        return true;
    }

    public String getPublicId() {

        return (in == null) ? null : in.getPublicId();
    }

    public String getSystemId() {

        return (in == null) ? null : in.getSystemId();
    }

    public int getLineNumber() {

        return (in == null) ? -1 : in.getLineNumber();
    }

    public int getColumnNumber() {

        return (in == null) ? -1 : in.getColumnNumber();
    }

    // error handling convenience routines

    private void warning(String messageId, Object parameters [])
            throws SAXException {

        SAXParseException e = new SAXParseException(messages.getMessage(locale, messageId, parameters),
                getPublicId(), getSystemId(), getLineNumber(), getColumnNumber());

        dtdHandler.warning(e);
    }

    void error(String messageId, Object parameters [])
            throws SAXException {

        SAXParseException e = new SAXParseException(messages.getMessage(locale, messageId, parameters),
                getPublicId(), getSystemId(), getLineNumber(), getColumnNumber());

        dtdHandler.error(e);
    }

    private void fatal(String messageId) throws SAXException {

        fatal(messageId, null);
    }

    private void fatal(String messageId, Object parameters [])
            throws SAXException {

        SAXParseException e = new SAXParseException(messages.getMessage(locale, messageId, parameters),
                getPublicId(), getSystemId(), getLineNumber(), getColumnNumber());

        dtdHandler.fatalError(e);

        throw e;
    }

    //
    // Map char arrays to strings ... cuts down both on memory and
    // CPU usage for element/attribute/other names that are reused.
    //
    // Documents typically repeat names a lot, so we more or less
    // intern all the strings within the document; since some strings
    // are repeated in multiple documents (e.g. stylesheets) we go
    // a bit further, and intern globally.
    //
    static class NameCache {
        //
        // Unless we auto-grow this, the default size should be a
        // reasonable bit larger than needed for most XML files
        // we've yet seen (and be prime).  If it's too small, the
        // penalty is just excess cache collisions.
        //
        NameCacheEntry hashtable [] = new NameCacheEntry[541];

        //
        // Usually we just want to get the 'symbol' for these chars
        //
        String lookup(char value [], int len) {

            return lookupEntry(value, len).name;
        }

        //
        // Sometimes we need to scan the chars in the resulting
        // string, so there's an accessor which exposes them.
        // (Mostly for element end tags.)
        //
        NameCacheEntry lookupEntry(char value [], int len) {

            int index = 0;
            NameCacheEntry entry;

            // hashing to get index
            for (int i = 0; i < len; i++)
                index = index * 31 + value[i];
            index &= 0x7fffffff;
            index %= hashtable.length;

            // return entry if one's there ...
            for (entry = hashtable[index];
                 entry != null;
                 entry = entry.next) {
                if (entry.matches(value, len))
                    return entry;
            }

            // else create new one
            entry = new NameCacheEntry();
            entry.chars = new char[len];
            System.arraycopy(value, 0, entry.chars, 0, len);
            entry.name = new String(entry.chars);
            //
            // NOTE:  JDK 1.1 has a fixed size string intern table,
            // with non-GC'd entries.  It can panic here; that's a
            // JDK problem, use 1.2 or later with many identifiers.
            //
            entry.name = entry.name.intern();        // "global" intern
            entry.next = hashtable[index];
            hashtable[index] = entry;
            return entry;
        }
    }

    static class NameCacheEntry {

        String name;
        char chars [];
        NameCacheEntry next;

        boolean matches(char value [], int len) {

            if (chars.length != len)
                return false;
            for (int i = 0; i < len; i++)
                if (value[i] != chars[i])
                    return false;
            return true;
        }
    }

    //
    // Message catalog for diagnostics.
    //
    static final Catalog messages = new Catalog();

    static final class Catalog extends MessageCatalog {

        Catalog() {
            super(DTDParser.class);
        }
    }

}
