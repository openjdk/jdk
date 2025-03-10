/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.pkcs11;

import java.io.*;
import static java.io.StreamTokenizer.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;

import sun.security.util.PropertyExpander;

import sun.security.pkcs11.wrapper.*;
import static sun.security.pkcs11.wrapper.PKCS11Constants.*;
import static sun.security.pkcs11.wrapper.CK_ATTRIBUTE.*;

import static sun.security.pkcs11.TemplateManager.*;

/**
 * Configuration container and file parsing.
 *
 * @author  Andreas Sterbenz
 * @since   1.5
 */
final class Config {

    static final int ERR_HALT       = 1;
    static final int ERR_IGNORE_ALL = 2;
    static final int ERR_IGNORE_LIB = 3;

    // same as allowSingleThreadedModules but controlled via a system property
    // and applied to all providers. if set to false, no SunPKCS11 instances
    // will accept single threaded modules regardless of the setting in their
    // config files.
    private static final boolean staticAllowSingleThreadedModules;

    static {
        String allowSingleThreadedModules =
            System.getProperty(
                "sun.security.pkcs11.allowSingleThreadedModules", "true");
        if ("false".equalsIgnoreCase(allowSingleThreadedModules)) {
            staticAllowSingleThreadedModules = false;
        } else {
            staticAllowSingleThreadedModules = true;
        }
    }

    private static final boolean DEBUG = false;

    // file name containing this configuration
    private final String filename;

    // Reader and StringTokenizer used during parsing
    private Reader reader;

    private StreamTokenizer st;

    private Set<String> parsedKeywords;

    // name suffix of the provider
    private String name;

    // name of the PKCS#11 library
    private String library;

    // description to pass to the provider class
    private String description;

    // slotID of the slot to use
    private int slotID = -1;

    // slot to use, specified as index in the slotlist
    private int slotListIndex = -1;

    // set of enabled mechanisms (or null to use default)
    private Set<Long> enabledMechanisms;

    // set of disabled mechanisms
    private Set<Long> disabledMechanisms;

    // whether to print debug info during startup
    private boolean showInfo = false;

    // whether to allow legacy mechanisms
    private boolean allowLegacy = false;

    // template manager, initialized from parsed attributes
    private TemplateManager templateManager;

    // how to handle error during startup, one of ERR_
    private int handleStartupErrors = ERR_HALT;

    // flag indicating whether the P11KeyStore should
    // be more tolerant of input parameters
    private boolean keyStoreCompatibilityMode = true;

    // flag indicating whether we need to explicitly cancel operations
    // see Token
    private boolean explicitCancel = true;

    // how often to test for token insertion, if no token is present
    private int insertionCheckInterval = 2000;

    // short ms value to indicate how often native cleaner thread is called
    private int resourceCleanerShortInterval = 2_000;
    // long ms value to indicate how often native cleaner thread is called
    private int resourceCleanerLongInterval = 60_000;

    // should Token be destroyed after logout()
    private boolean destroyTokenAfterLogout;

    // flag indicating whether to omit the call to C_Initialize()
    // should be used only if we are running within a process that
    // has already called it (e.g. Plugin inside of Mozilla/NSS)
    private boolean omitInitialize = false;

    // whether to allow modules that only support single threaded access.
    // they cannot be used safely from multiple PKCS#11 consumers in the
    // same process, for example NSS and SunPKCS11
    private boolean allowSingleThreadedModules = true;

    // name of the C function that returns the PKCS#11 functionlist
    // This option primarily exists for the deprecated
    // Secmod.Module.getProvider() method.
    private String functionList = null;

    // CTS mode variant used by the token, as described in Addendum to NIST
    // Special Publication 800-38A, "Recommendation for Block Cipher Modes
    // of Operation: Three Variants of Ciphertext Stealing for CBC Mode".
    private Token.CTSVariant ctsVariant = null;

    // whether to use NSS secmod mode. Implicitly set if nssLibraryDirectory,
    // nssSecmodDirectory, or nssModule is specified.
    private boolean nssUseSecmod;

    // location of the NSS library files (libnss3.so, etc.)
    private String nssLibraryDirectory;

    // location of secmod.db
    private String nssSecmodDirectory;

    // which NSS module to use
    private String nssModule;

    private Secmod.DbMode nssDbMode = Secmod.DbMode.READ_WRITE;

    // Whether the P11KeyStore should specify the CKA_NETSCAPE_DB attribute
    // when creating private keys. Only valid if nssUseSecmod is true.
    private boolean nssNetscapeDbWorkaround = true;

    // Special init argument string for the NSS softtoken.
    // This is used when using the NSS softtoken directly without secmod mode.
    private String nssArgs;

    // whether to use NSS trust attributes for the KeyStore of this provider
    // this option is for internal use by the SunPKCS11 code only and
    // works only for NSS providers created via the Secmod API
    private boolean nssUseSecmodTrust = false;

    // Flag to indicate whether the X9.63 encoding for EC points shall be used
    // (true) or whether that encoding shall be wrapped in an ASN.1 OctetString
    // (false).
    private boolean useEcX963Encoding = false;

    // Flag to indicate whether NSS should favour performance (false) or
    // memory footprint (true).
    private boolean nssOptimizeSpace = false;

    Config(String fn) throws IOException {
        this.filename = fn;
        if (filename.startsWith("--")) {
            // inline config
            String config = filename.substring(2).replace("\\n", "\n");
            reader = new StringReader(config);
        } else {
            reader = new BufferedReader(new InputStreamReader
                (new FileInputStream(expand(filename)),
                    StandardCharsets.ISO_8859_1));
        }
        parsedKeywords = new HashSet<String>();
        st = new StreamTokenizer(reader);
        setupTokenizer();
        parse();
    }

    String getFileName() {
        return filename;
    }

    String getName() {
        return name;
    }

    String getLibrary() {
        return library;
    }

    String getDescription() {
        if (description != null) {
            return description;
        }
        return "SunPKCS11-" + name + " using library " + library;
    }

    int getSlotID() {
        return slotID;
    }

    int getSlotListIndex() {
        if ((slotID == -1) && (slotListIndex == -1)) {
            // if neither is set, default to first slot
            return 0;
        } else {
            return slotListIndex;
        }
    }

    boolean getShowInfo() {
        return (SunPKCS11.debug != null) || showInfo;
    }

    boolean getAllowLegacy() {
        return allowLegacy;
    }

    TemplateManager getTemplateManager() {
        if (templateManager == null) {
            templateManager = new TemplateManager();
        }
        return templateManager;
    }

    boolean isEnabled(long m) {
        if (enabledMechanisms != null) {
            return enabledMechanisms.contains(Long.valueOf(m));
        }
        if (disabledMechanisms != null) {
            return !disabledMechanisms.contains(Long.valueOf(m));
        }
        return true;
    }

    int getHandleStartupErrors() {
        return handleStartupErrors;
    }

    boolean getKeyStoreCompatibilityMode() {
        return keyStoreCompatibilityMode;
    }

    boolean getExplicitCancel() {
        return explicitCancel;
    }

    boolean getDestroyTokenAfterLogout() {
        return destroyTokenAfterLogout;
    }

    int getResourceCleanerShortInterval() {
        return resourceCleanerShortInterval;
    }

    int getResourceCleanerLongInterval() {
        return resourceCleanerLongInterval;
    }

    int getInsertionCheckInterval() {
        return insertionCheckInterval;
    }

    boolean getOmitInitialize() {
        return omitInitialize;
    }

    boolean getAllowSingleThreadedModules() {
        return staticAllowSingleThreadedModules && allowSingleThreadedModules;
    }

    String getFunctionList() {
        if (functionList == null) {
            // defaults to "C_GetFunctionList" for NSS secmod
            if (nssUseSecmod || nssUseSecmodTrust) {
                return "C_GetFunctionList";
            }
        }
        return functionList;
    }

    Token.CTSVariant getCTSVariant() {
        return ctsVariant;
    }

    boolean getNssUseSecmod() {
        return nssUseSecmod;
    }

    String getNssLibraryDirectory() {
        return nssLibraryDirectory;
    }

    String getNssSecmodDirectory() {
        return nssSecmodDirectory;
    }

    String getNssModule() {
        return nssModule;
    }

    Secmod.DbMode getNssDbMode() {
        return nssDbMode;
    }

    public boolean getNssNetscapeDbWorkaround() {
        return nssUseSecmod && nssNetscapeDbWorkaround;
    }

    String getNssArgs() {
        return nssArgs;
    }

    boolean getNssUseSecmodTrust() {
        return nssUseSecmodTrust;
    }

    boolean getUseEcX963Encoding() {
        return useEcX963Encoding;
    }

    boolean getNssOptimizeSpace() {
        return nssOptimizeSpace;
    }

    private static String expand(final String s) throws IOException {
        try {
            return PropertyExpander.expand(s);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private void setupTokenizer() {
        st.resetSyntax();
        st.wordChars('a', 'z');
        st.wordChars('A', 'Z');
        st.wordChars('0', '9');
        st.wordChars(':', ':');
        st.wordChars('.', '.');
        st.wordChars('_', '_');
        st.wordChars('-', '-');
        st.wordChars('/', '/');
        st.wordChars('\\', '\\');
        st.wordChars('$', '$');
        st.wordChars('{', '{'); // need {} for property subst
        st.wordChars('}', '}');
        st.wordChars('*', '*');
        st.wordChars('+', '+');
        st.wordChars('~', '~');
        // XXX check ASCII table and add all other characters except special

        // special: #="(),
        st.whitespaceChars(0, ' ');
        st.commentChar('#');
        st.eolIsSignificant(true);
        st.quoteChar('\"');
    }

    private ConfigurationException excToken(String msg) {
        return new ConfigurationException(msg + " " + st);
    }

    private ConfigurationException excLine(String msg) {
        return new ConfigurationException(msg + ", line " + st.lineno());
    }

    private ConfigurationException excLine(String msg, Throwable e) {
        return new ConfigurationException(msg + ", line " + st.lineno(), e);
    }

    private void parse() throws IOException {
        while (true) {
            int token = nextToken();
            if (token == TT_EOF) {
                break;
            }
            if (token == TT_EOL) {
                continue;
            }
            if (token != TT_WORD) {
                throw excToken("Unexpected token:");
            }
            switch (st.sval) {
            case "name"->
                name = parseStringEntry(st.sval);
            case "library"->
                library = parseLibrary(st.sval);
            case "description"->
                parseDescription(st.sval);
            case "slot"->
                parseSlotID(st.sval);
            case "slotListIndex"->
                parseSlotListIndex(st.sval);
            case "enabledMechanisms"->
                parseEnabledMechanisms(st.sval);
            case "disabledMechanisms"->
                parseDisabledMechanisms(st.sval);
            case "attributes"->
                parseAttributes(st.sval);
            case "handleStartupErrors"->
                parseHandleStartupErrors(st.sval);
            case "insertionCheckInterval"-> {
                insertionCheckInterval = parseIntegerEntry(st.sval);
                if (insertionCheckInterval < 100) {
                    throw excLine(st.sval + " must be at least 100 ms");
                }
            }
            case "cleaner.shortInterval"-> {
                resourceCleanerShortInterval = parseIntegerEntry(st.sval);
                if (resourceCleanerShortInterval < 1_000) {
                    throw excLine(st.sval + " must be at least 1000 ms");
                }
            }
            case "cleaner.longInterval"-> {
                resourceCleanerLongInterval = parseIntegerEntry(st.sval);
                if (resourceCleanerLongInterval < 1_000) {
                    throw excLine(st.sval + " must be at least 1000 ms");
                }
            }
            case "destroyTokenAfterLogout"->
                destroyTokenAfterLogout = parseBooleanEntry(st.sval);
            case "showInfo"->
                showInfo = parseBooleanEntry(st.sval);
            case "allowLegacy"->
                allowLegacy = parseBooleanEntry(st.sval);
            case "keyStoreCompatibilityMode"->
                keyStoreCompatibilityMode = parseBooleanEntry(st.sval);
            case "explicitCancel"->
                explicitCancel = parseBooleanEntry(st.sval);
            case "omitInitialize"->
                omitInitialize = parseBooleanEntry(st.sval);
            case "allowSingleThreadedModules"->
                allowSingleThreadedModules = parseBooleanEntry(st.sval);
            case "functionList"->
                functionList = parseStringEntry(st.sval);
            case "cipherTextStealingVariant"->
                ctsVariant = parseEnumEntry(Token.CTSVariant.class, st.sval);
            case "nssUseSecmod"->
                nssUseSecmod = parseBooleanEntry(st.sval);
            case "nssLibraryDirectory"-> {
                nssLibraryDirectory = parseLibrary(st.sval);
                nssUseSecmod = true;
            }
            case "nssSecmodDirectory"-> {
                nssSecmodDirectory = expand(parseStringEntry(st.sval));
                nssUseSecmod = true;
            }
            case "nssModule"-> {
                nssModule = parseStringEntry(st.sval);
                nssUseSecmod = true;
            }
            case "nssDbMode"-> {
                String mode = parseStringEntry(st.sval);
                nssDbMode = switch (mode) {
                    case "readWrite" -> Secmod.DbMode.READ_WRITE;
                    case "readOnly" -> Secmod.DbMode.READ_ONLY;
                    case "noDb" -> Secmod.DbMode.NO_DB;
                    default -> throw excToken("nssDbMode must be one of readWrite, readOnly, and noDb:");
                };
                nssUseSecmod = true;
            }
            case "nssNetscapeDbWorkaround"-> {
                nssNetscapeDbWorkaround = parseBooleanEntry(st.sval);
                nssUseSecmod = true;
            }
            case "nssArgs"->
                parseNSSArgs(st.sval);
            case "nssUseSecmodTrust"->
                nssUseSecmodTrust = parseBooleanEntry(st.sval);
            case "useEcX963Encoding"->
                useEcX963Encoding = parseBooleanEntry(st.sval);
            case "nssOptimizeSpace"->
                nssOptimizeSpace = parseBooleanEntry(st.sval);
            default->
                throw new ConfigurationException
                        ("Unknown keyword '" + st.sval + "', line " +
                        st.lineno());
            }
            parsedKeywords.add(st.sval);
        }
        reader.close();
        reader = null;
        st = null;
        parsedKeywords = null;
        if (name == null) {
            throw new ConfigurationException("name must be specified");
        }
        if (!nssUseSecmod) {
            if (library == null) {
                throw new ConfigurationException("library must be specified");
            }
        } else {
            if (library != null) {
                throw new ConfigurationException
                    ("library must not be specified in NSS mode");
            }
            if ((slotID != -1) || (slotListIndex != -1)) {
                throw new ConfigurationException
                    ("slot and slotListIndex must not be specified in NSS mode");
            }
            if (nssArgs != null) {
                throw new ConfigurationException
                    ("nssArgs must not be specified in NSS mode");
            }
            if (nssUseSecmodTrust) {
                throw new ConfigurationException("nssUseSecmodTrust is an "
                    + "internal option and must not be specified in NSS mode");
            }
        }
    }

    //
    // Parsing helper methods
    //

    private int nextToken() throws IOException {
        int token = st.nextToken();
        if (DEBUG)  {
            System.out.println(st);
        }
        return token;
    }

    private void parseEquals() throws IOException {
        int token = nextToken();
        if (token != '=') {
            throw excToken("Expected '=', read");
        }
    }

    private void parseOpenBraces() throws IOException {
        while (true) {
            int token = nextToken();
            if (token == TT_EOL) {
                continue;
            }
            if ((token == TT_WORD) && st.sval.equals("{")) {
                return;
            }
            throw excToken("Expected '{', read");
        }
    }

    private boolean isCloseBraces(int token) {
        return (token == TT_WORD) && st.sval.equals("}");
    }

    private String parseWord() throws IOException {
        int token = nextToken();
        if (token != TT_WORD) {
            throw excToken("Unexpected value:");
        }
        return st.sval;
    }

    private String parseStringEntry(String keyword) throws IOException {
        checkDup(keyword);
        parseEquals();

        int token = nextToken();
        if (token != TT_WORD && token != '\"') {
            // not a word token nor a string enclosed by double quotes
            throw excToken("Unexpected value:");
        }
        String value = st.sval;

        if (DEBUG) {
            System.out.println(keyword + ": " + value);
        }
        return value;
    }

    private boolean parseBooleanEntry(String keyword) throws IOException {
        checkDup(keyword);
        parseEquals();
        boolean value = parseBoolean();
        if (DEBUG) {
            System.out.println(keyword + ": " + value);
        }
        return value;
    }

    private int parseIntegerEntry(String keyword) throws IOException {
        checkDup(keyword);
        parseEquals();
        int value = decodeNumber(parseWord());
        if (DEBUG) {
            System.out.println(keyword + ": " + value);
        }
        return value;
    }

    private <E extends Enum<E>> E parseEnumEntry(Class<E> enumClass,
            String keyword) throws IOException {
        String value = parseStringEntry(keyword);
        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException ignored) {
            throw excToken(keyword + " must be one of " +
                    Arrays.toString(enumClass.getEnumConstants()) + ", read:");
        }
    }

    private boolean parseBoolean() throws IOException {
        String val = parseWord();
        return switch (val) {
            case "true" -> true;
            case "false" -> false;
            default -> throw excToken("Expected boolean value, read:");
        };
    }

    private String parseLine() throws IOException {
        // allow quoted string as part of line
        String s = null;
        while (true) {
            int token = nextToken();
            if ((token == TT_EOL) || (token == TT_EOF)) {
                break;
            }
            if (token != TT_WORD && token != '\"') {
                throw excToken("Unexpected value");
            }
            if (s == null) {
                s = st.sval;
            } else {
                s = s + " " + st.sval;
            }
        }
        if (s == null) {
            throw excToken("Unexpected empty line");
        }
        return s;
    }

    private int decodeNumber(String str) throws IOException {
        try {
            if (str.startsWith("0x") || str.startsWith("0X")) {
                return Integer.parseInt(str.substring(2), 16);
            } else {
                return Integer.parseInt(str);
            }
        } catch (NumberFormatException e) {
            throw excToken("Expected number, read");
        }
    }

    private static boolean isNumber(String s) {
        if (s.length() == 0) {
            return false;
        }
        char ch = s.charAt(0);
        return ((ch >= '0') && (ch <= '9'));
    }

    private void parseComma() throws IOException {
        int token = nextToken();
        if (token != ',') {
            throw excToken("Expected ',', read");
        }
    }

    private static boolean isByteArray(String val) {
        return val.startsWith("0h");
    }

    private byte[] decodeByteArray(String str) throws IOException {
        if (!str.startsWith("0h")) {
            throw excToken("Expected byte array value, read");
        }
        str = str.substring(2);
        // XXX proper hex parsing
        try {
            return new BigInteger(str, 16).toByteArray();
        } catch (NumberFormatException e) {
            throw excToken("Expected byte array value, read");
        }
    }

    private void checkDup(String keyword) throws IOException {
        if (parsedKeywords.contains(keyword)) {
            throw excLine(keyword + " must only be specified once");
        }
    }

    //
    // individual entry parsing methods
    //

    private String parseLibrary(String keyword) throws IOException {
        checkDup(keyword);
        parseEquals();
        String lib = parseLine();
        lib = expand(lib);
        int i = lib.indexOf("/$ISA/");
        if (i != -1) {
            // replace "/$ISA/" with "/"
            String prefix = lib.substring(0, i);
            String suffix = lib.substring(i + 5);
            lib = prefix + suffix;
        }
        if (DEBUG) {
            System.out.println(keyword + ": " + lib);
        }

        // Check to see if full path is specified to prevent the DLL
        // preloading attack
        if (!(new File(lib)).isAbsolute()) {
            throw new ConfigurationException(
                "Absolute path required for library value: " + lib);
        }
        return lib;
    }

    private void parseDescription(String keyword) throws IOException {
        checkDup(keyword);
        parseEquals();
        description = parseLine();
        if (DEBUG) {
            System.out.println("description: " + description);
        }
    }

    private void parseSlotID(String keyword) throws IOException {
        if (slotID >= 0) {
            throw excLine("Duplicate slot definition");
        }
        if (slotListIndex >= 0) {
            throw excLine
                ("Only one of slot and slotListIndex must be specified");
        }
        parseEquals();
        String slotString = parseWord();
        slotID = decodeNumber(slotString);
        if (DEBUG) {
            System.out.println("slot: " + slotID);
        }
    }

    private void parseSlotListIndex(String keyword) throws IOException {
        if (slotListIndex >= 0) {
            throw excLine("Duplicate slotListIndex definition");
        }
        if (slotID >= 0) {
            throw excLine
                ("Only one of slot and slotListIndex must be specified");
        }
        parseEquals();
        String slotString = parseWord();
        slotListIndex = decodeNumber(slotString);
        if (DEBUG) {
            System.out.println("slotListIndex: " + slotListIndex);
        }
    }

    private void parseEnabledMechanisms(String keyword) throws IOException {
        enabledMechanisms = parseMechanisms(keyword);
    }

    private void parseDisabledMechanisms(String keyword) throws IOException {
        disabledMechanisms = parseMechanisms(keyword);
    }

    private Set<Long> parseMechanisms(String keyword) throws IOException {
        checkDup(keyword);
        Set<Long> mechs = new HashSet<Long>();
        parseEquals();
        parseOpenBraces();
        while (true) {
            int token = nextToken();
            if (isCloseBraces(token)) {
                break;
            }
            if (token == TT_EOL) {
                continue;
            }
            if (token != TT_WORD) {
                throw excToken("Expected mechanism, read");
            }
            long mech = parseMechanism(st.sval);
            mechs.add(Long.valueOf(mech));
        }
        if (DEBUG) {
            System.out.print("mechanisms: [");
            for (Long mech : mechs) {
                System.out.print(Functions.getMechanismName(mech));
                System.out.print(", ");
            }
            System.out.println("]");
        }
        return mechs;
    }

    private long parseMechanism(String mech) throws IOException {
        if (isNumber(mech)) {
            return decodeNumber(mech);
        } else {
            try {
                return Functions.getMechanismId(mech);
            } catch (IllegalArgumentException e) {
                throw excLine("Unknown mechanism: " + mech, e);
            }
        }
    }

    private void parseAttributes(String keyword) throws IOException {
        if (templateManager == null) {
            templateManager = new TemplateManager();
        }
        int token = nextToken();
        if (token == '=') {
            String s = parseWord();
            if (!s.equals("compatibility")) {
                throw excLine("Expected 'compatibility', read " + s);
            }
            setCompatibilityAttributes();
            return;
        }
        if (token != '(') {
            throw excToken("Expected '(' or '=', read");
        }
        String op = parseOperation();
        parseComma();
        long objectClass = parseObjectClass();
        parseComma();
        long keyAlg = parseKeyAlgorithm();
        token = nextToken();
        if (token != ')') {
            throw excToken("Expected ')', read");
        }
        parseEquals();
        parseOpenBraces();
        List<CK_ATTRIBUTE> attributes = new ArrayList<CK_ATTRIBUTE>();
        while (true) {
            token = nextToken();
            if (isCloseBraces(token)) {
                break;
            }
            if (token == TT_EOL) {
                continue;
            }
            if (token != TT_WORD) {
                throw excToken("Expected mechanism, read");
            }
            String attributeName = st.sval;
            long attributeId = decodeAttributeName(attributeName);
            parseEquals();
            String attributeValue = parseWord();
            attributes.add(decodeAttributeValue(attributeId, attributeValue));
        }
        templateManager.addTemplate
                (op, objectClass, keyAlg, attributes.toArray(CK_A0));
    }

    private void setCompatibilityAttributes() {
        // all secret keys
        templateManager.addTemplate(O_ANY, CKO_SECRET_KEY, PCKK_ANY,
        new CK_ATTRIBUTE[] {
            TOKEN_FALSE,
            SENSITIVE_FALSE,
            EXTRACTABLE_TRUE,
            ENCRYPT_TRUE,
            DECRYPT_TRUE,
            WRAP_TRUE,
            UNWRAP_TRUE,
        });

        // generic secret keys are special
        // They are used as MAC keys plus for the SSL/TLS (pre)master secrets
        templateManager.addTemplate(O_ANY, CKO_SECRET_KEY, CKK_GENERIC_SECRET,
        new CK_ATTRIBUTE[] {
            SIGN_TRUE,
            VERIFY_TRUE,
            ENCRYPT_NULL,
            DECRYPT_NULL,
            WRAP_NULL,
            UNWRAP_NULL,
            DERIVE_TRUE,
        });

        // all private and public keys
        templateManager.addTemplate(O_ANY, CKO_PRIVATE_KEY, PCKK_ANY,
        new CK_ATTRIBUTE[] {
            TOKEN_FALSE,
            SENSITIVE_FALSE,
            EXTRACTABLE_TRUE,
        });
        templateManager.addTemplate(O_ANY, CKO_PUBLIC_KEY, PCKK_ANY,
        new CK_ATTRIBUTE[] {
            TOKEN_FALSE,
        });

        // additional attributes for RSA private keys
        templateManager.addTemplate(O_ANY, CKO_PRIVATE_KEY, CKK_RSA,
        new CK_ATTRIBUTE[] {
            DECRYPT_TRUE,
            SIGN_TRUE,
            SIGN_RECOVER_TRUE,
            UNWRAP_TRUE,
        });
        // additional attributes for RSA public keys
        templateManager.addTemplate(O_ANY, CKO_PUBLIC_KEY, CKK_RSA,
        new CK_ATTRIBUTE[] {
            ENCRYPT_TRUE,
            VERIFY_TRUE,
            VERIFY_RECOVER_TRUE,
            WRAP_TRUE,
        });

        // additional attributes for DSA private keys
        templateManager.addTemplate(O_ANY, CKO_PRIVATE_KEY, CKK_DSA,
        new CK_ATTRIBUTE[] {
            SIGN_TRUE,
        });
        // additional attributes for DSA public keys
        templateManager.addTemplate(O_ANY, CKO_PUBLIC_KEY, CKK_DSA,
        new CK_ATTRIBUTE[] {
            VERIFY_TRUE,
        });

        // additional attributes for DH private keys
        templateManager.addTemplate(O_ANY, CKO_PRIVATE_KEY, CKK_DH,
        new CK_ATTRIBUTE[] {
            DERIVE_TRUE,
        });

        // additional attributes for EC private keys
        templateManager.addTemplate(O_ANY, CKO_PRIVATE_KEY, CKK_EC,
        new CK_ATTRIBUTE[] {
            SIGN_TRUE,
            DERIVE_TRUE,
        });
        // additional attributes for EC public keys
        templateManager.addTemplate(O_ANY, CKO_PUBLIC_KEY, CKK_EC,
        new CK_ATTRIBUTE[] {
            VERIFY_TRUE,
        });
    }

    private static final CK_ATTRIBUTE[] CK_A0 = new CK_ATTRIBUTE[0];

    private String parseOperation() throws IOException {
        String op = parseWord();
        return switch (op) {
            case "*" -> TemplateManager.O_ANY;
            case "generate" -> TemplateManager.O_GENERATE;
            case "import" -> TemplateManager.O_IMPORT;
            default -> throw excLine("Unknown operation " + op);
        };
    }

    private long parseObjectClass() throws IOException {
        String name = parseWord();
        try {
            return Functions.getObjectClassId(name);
        } catch (IllegalArgumentException e) {
            throw excLine("Unknown object class " + name, e);
        }
    }

    private long parseKeyAlgorithm() throws IOException {
        String name = parseWord();
        if (isNumber(name)) {
            return decodeNumber(name);
        } else {
            try {
                return Functions.getKeyId(name);
            } catch (IllegalArgumentException e) {
                throw excLine("Unknown key algorithm " + name, e);
            }
        }
    }

    private long decodeAttributeName(String name) throws IOException {
        if (isNumber(name)) {
            return decodeNumber(name);
        } else {
            try {
                return Functions.getAttributeId(name);
            } catch (IllegalArgumentException e) {
                throw excLine("Unknown attribute name " + name, e);
            }
        }
    }

    private CK_ATTRIBUTE decodeAttributeValue(long id, String value)
            throws IOException {
        if (value.equals("null")) {
            return new CK_ATTRIBUTE(id);
        } else if (value.equals("true")) {
            return new CK_ATTRIBUTE(id, true);
        } else if (value.equals("false")) {
            return new CK_ATTRIBUTE(id, false);
        } else if (isByteArray(value)) {
            return new CK_ATTRIBUTE(id, decodeByteArray(value));
        } else if (isNumber(value)) {
            return new CK_ATTRIBUTE(id, Integer.valueOf(decodeNumber(value)));
        } else {
            throw excLine("Unknown attribute value " + value);
        }
    }

    private void parseNSSArgs(String keyword) throws IOException {
        checkDup(keyword);
        parseEquals();
        int token = nextToken();
        if (token != '"') {
            throw excToken("Expected quoted string");
        }
        nssArgs = expand(st.sval);
        if (DEBUG) {
            System.out.println("nssArgs: " + nssArgs);
        }
    }

    private void parseHandleStartupErrors(String keyword) throws IOException {
        checkDup(keyword);
        parseEquals();
        String val = parseWord();
        handleStartupErrors = switch (val) {
            case "ignoreAll" -> ERR_IGNORE_ALL;
            case "ignoreMissingLibrary" -> ERR_IGNORE_LIB;
            case "halt" -> ERR_HALT;
            default -> throw excToken("Invalid value for handleStartupErrors:");
        };
        if (DEBUG) {
            System.out.println("handleStartupErrors: " + handleStartupErrors);
        }
    }

}

class ConfigurationException extends IOException {
    @Serial
    private static final long serialVersionUID = 254492758807673194L;
    ConfigurationException(String msg) {
        super(msg);
    }

    ConfigurationException(String msg, Throwable e) {
        super(msg, e);
    }
}
