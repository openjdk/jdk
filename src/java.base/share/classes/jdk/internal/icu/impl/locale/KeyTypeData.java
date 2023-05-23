// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 2014-2016, International Business Machines Corporation and
 * others. All Rights Reserved.
 *******************************************************************************
 */
package jdk.internal.icu.impl.locale;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Set;
import java.util.regex.Pattern;

import jdk.internal.icu.impl.ICUData;
import jdk.internal.icu.impl.ICUResourceBundle;
import jdk.internal.icu.util.Output;
import jdk.internal.icu.util.UResourceBundle;
import jdk.internal.icu.util.UResourceBundleIterator;

/**
 */
public class KeyTypeData {

    public enum ValueType {
        single, multiple, incremental, any
    }

    private static abstract class SpecialTypeHandler {
        abstract boolean isWellFormed(String value); // doesn't test validity, just whether it is well formed.
        String canonicalize(String value) {
            return AsciiUtil.toLowerString(value);
        }
    }

    private static class CodepointsTypeHandler extends SpecialTypeHandler {
        private static final Pattern pat = Pattern.compile("[0-9a-fA-F]{4,6}(-[0-9a-fA-F]{4,6})*");
        @Override
        boolean isWellFormed(String value) {
            return pat.matcher(value).matches();
        }
    }

    private static class ReorderCodeTypeHandler extends SpecialTypeHandler {
        private static final Pattern pat = Pattern.compile("[a-zA-Z]{3,8}(-[a-zA-Z]{3,8})*");
        @Override
        boolean isWellFormed(String value) {
            return pat.matcher(value).matches();
        }
    }

    private static class RgKeyValueTypeHandler extends SpecialTypeHandler {
        private static final Pattern pat = Pattern.compile("([a-zA-Z]{2}|[0-9]{3})[zZ]{4}");
        @Override
        boolean isWellFormed(String value) {
            return pat.matcher(value).matches();
        }
    }

    private static class ScriptCodeTypeHandler extends SpecialTypeHandler {
        private static final Pattern pat = Pattern.compile("[a-zA-Z]{4}(-[a-zA-Z]{4})*");
        @Override
        boolean isWellFormed(String value) {
            return pat.matcher(value).matches();
        }
    }

    private static class SubdivisionKeyValueTypeHandler extends SpecialTypeHandler {
        private static final Pattern pat = Pattern.compile("([a-zA-Z]{2}|[0-9]{3})");
        @Override
        boolean isWellFormed(String value) {
            return pat.matcher(value).matches();
        }
    }

    private static class PrivateUseKeyValueTypeHandler extends SpecialTypeHandler {
        private static final Pattern pat = Pattern.compile("[a-zA-Z0-9]{3,8}(-[a-zA-Z0-9]{3,8})*");
        @Override
        boolean isWellFormed(String value) {
            return pat.matcher(value).matches();
        }
    }

    private enum SpecialType {
        CODEPOINTS(new CodepointsTypeHandler()),
        REORDER_CODE(new ReorderCodeTypeHandler()),
        RG_KEY_VALUE(new RgKeyValueTypeHandler()),
        SCRIPT_CODE(new ScriptCodeTypeHandler()),
        SUBDIVISION_CODE(new SubdivisionKeyValueTypeHandler()),
        PRIVATE_USE(new PrivateUseKeyValueTypeHandler()),
        ;
        SpecialTypeHandler handler;
        SpecialType(SpecialTypeHandler handler) {
            this.handler = handler;
        }
    };

    private static class KeyData {
        String legacyId;
        String bcpId;
        Map<String, Type> typeMap;
        EnumSet<SpecialType> specialTypes;

        KeyData(String legacyId, String bcpId, Map<String, Type> typeMap,
                EnumSet<SpecialType> specialTypes) {
            this.legacyId = legacyId;
            this.bcpId = bcpId;
            this.typeMap = typeMap;
            this.specialTypes = specialTypes;
        }
    }

    private static class Type {
        String legacyId;
        String bcpId;

        Type(String legacyId, String bcpId) {
            this.legacyId = legacyId;
            this.bcpId = bcpId;
        }
    }

    public static String toBcpKey(String key) {
        key = AsciiUtil.toLowerString(key);
        KeyData keyData = KEYMAP.get(key);
        if (keyData != null) {
            return keyData.bcpId;
        }
        return null;
    }

    public static String toLegacyKey(String key) {
        key = AsciiUtil.toLowerString(key);
        KeyData keyData = KEYMAP.get(key);
        if (keyData != null) {
            return keyData.legacyId;
        }
        return null;
    }

    public static String toBcpType(String key, String type,
            Output<Boolean> isKnownKey, Output<Boolean> isSpecialType) {

        if (isKnownKey != null) {
            isKnownKey.value = false;
        }
        if (isSpecialType != null) {
            isSpecialType.value = false;
        }

        key = AsciiUtil.toLowerString(key);
        type = AsciiUtil.toLowerString(type);

        KeyData keyData = KEYMAP.get(key);
        if (keyData != null) {
            if (isKnownKey != null) {
                isKnownKey.value = Boolean.TRUE;
            }
            Type t = keyData.typeMap.get(type);
            if (t != null) {
                return t.bcpId;
            }
            if (keyData.specialTypes != null) {
                for (SpecialType st : keyData.specialTypes) {
                    if (st.handler.isWellFormed(type)) {
                        if (isSpecialType != null) {
                            isSpecialType.value = true;
                        }
                        return st.handler.canonicalize(type);
                    }
                }
            }
        }
        return null;
    }


    public static String toLegacyType(String key, String type,
            Output<Boolean> isKnownKey, Output<Boolean> isSpecialType) {

        if (isKnownKey != null) {
            isKnownKey.value = false;
        }
        if (isSpecialType != null) {
            isSpecialType.value = false;
        }

        key = AsciiUtil.toLowerString(key);
        type = AsciiUtil.toLowerString(type);

        KeyData keyData = KEYMAP.get(key);
        if (keyData != null) {
            if (isKnownKey != null) {
                isKnownKey.value = Boolean.TRUE;
            }
            Type t = keyData.typeMap.get(type);
            if (t != null) {
                return t.legacyId;
            }
            if (keyData.specialTypes != null) {
                for (SpecialType st : keyData.specialTypes) {
                    if (st.handler.isWellFormed(type)) {
                        if (isSpecialType != null) {
                            isSpecialType.value = true;
                        }
                        return st.handler.canonicalize(type);
                    }
                }
            }
        }
        return null;
    }

    private static void initFromResourceBundle() {
        UResourceBundle keyTypeDataRes = ICUResourceBundle.getBundleInstance(
                ICUData.ICU_BASE_NAME,
                "keyTypeData",
                ICUResourceBundle.ICU_DATA_CLASS_LOADER,
                ICUResourceBundle.OpenType.DIRECT);

        getKeyInfo(keyTypeDataRes.get("keyInfo"));
        getTypeInfo(keyTypeDataRes.get("typeInfo"));

        UResourceBundle keyMapRes = keyTypeDataRes.get("keyMap");
        UResourceBundle typeMapRes = keyTypeDataRes.get("typeMap");

        // alias data is optional
        UResourceBundle typeAliasRes = null;
        UResourceBundle bcpTypeAliasRes = null;

        try {
            typeAliasRes = keyTypeDataRes.get("typeAlias");
        } catch (MissingResourceException e) {
            // fall through
        }

        try {
            bcpTypeAliasRes = keyTypeDataRes.get("bcpTypeAlias");
        } catch (MissingResourceException e) {
            // fall through
        }

        // iterate through keyMap resource
        UResourceBundleIterator keyMapItr = keyMapRes.getIterator();
        Map<String,Set<String>> _Bcp47Keys = new LinkedHashMap<String,Set<String>>();

        while (keyMapItr.hasNext()) {
            UResourceBundle keyMapEntry = keyMapItr.next();
            String legacyKeyId = keyMapEntry.getKey();
            String bcpKeyId = keyMapEntry.getString();

            boolean hasSameKey = false;
            if (bcpKeyId.length() == 0) {
                // Empty value indicates that BCP key is same with the legacy key.
                bcpKeyId = legacyKeyId;
                hasSameKey = true;
            }
            final LinkedHashSet<String> _bcp47Types = new LinkedHashSet<String>();
            _Bcp47Keys.put(bcpKeyId, Collections.unmodifiableSet(_bcp47Types));

            boolean isTZ = legacyKeyId.equals("timezone");

            // reverse type alias map
            Map<String, Set<String>> typeAliasMap = null;
            if (typeAliasRes != null) {
                UResourceBundle typeAliasResByKey = null;
                try {
                    typeAliasResByKey = typeAliasRes.get(legacyKeyId);
                } catch (MissingResourceException e) {
                    // fall through
                }
                if (typeAliasResByKey != null) {
                    typeAliasMap = new HashMap<String, Set<String>>();
                    UResourceBundleIterator typeAliasResItr = typeAliasResByKey.getIterator();
                    while (typeAliasResItr.hasNext()) {
                        UResourceBundle typeAliasDataEntry = typeAliasResItr.next();
                        String from = typeAliasDataEntry.getKey();
                        String to = typeAliasDataEntry.getString();
                        if (isTZ) {
                            from = from.replace(':', '/');
                        }
                        Set<String> aliasSet = typeAliasMap.get(to);
                        if (aliasSet == null) {
                            aliasSet = new HashSet<String>();
                            typeAliasMap.put(to, aliasSet);
                        }
                        aliasSet.add(from);
                    }
                }
            }

            // reverse bcp type alias map
            Map<String, Set<String>> bcpTypeAliasMap = null;
            if (bcpTypeAliasRes != null) {
                UResourceBundle bcpTypeAliasResByKey = null;
                try {
                    bcpTypeAliasResByKey = bcpTypeAliasRes.get(bcpKeyId);
                } catch (MissingResourceException e) {
                    // fall through
                }
                if (bcpTypeAliasResByKey != null) {
                    bcpTypeAliasMap = new HashMap<String, Set<String>>();
                    UResourceBundleIterator bcpTypeAliasResItr = bcpTypeAliasResByKey.getIterator();
                    while (bcpTypeAliasResItr.hasNext()) {
                        UResourceBundle bcpTypeAliasDataEntry = bcpTypeAliasResItr.next();
                        String from = bcpTypeAliasDataEntry.getKey();
                        String to = bcpTypeAliasDataEntry.getString();
                        Set<String> aliasSet = bcpTypeAliasMap.get(to);
                        if (aliasSet == null) {
                            aliasSet = new HashSet<String>();
                            bcpTypeAliasMap.put(to, aliasSet);
                        }
                        aliasSet.add(from);
                    }
                }
            }

            Map<String, Type> typeDataMap = new HashMap<String, Type>();
            EnumSet<SpecialType> specialTypeSet = null;

            // look up type map for the key, and walk through the mapping data
            UResourceBundle typeMapResByKey = null;
            try {
                typeMapResByKey = typeMapRes.get(legacyKeyId);
            } catch (MissingResourceException e) {
                // type map for each key must exist
                assert false;
            }
            if (typeMapResByKey != null) {
                UResourceBundleIterator typeMapResByKeyItr = typeMapResByKey.getIterator();
                while (typeMapResByKeyItr.hasNext()) {
                    UResourceBundle typeMapEntry = typeMapResByKeyItr.next();
                    String legacyTypeId = typeMapEntry.getKey();
                    String bcpTypeId = typeMapEntry.getString();

                    // special types
                    final char first = legacyTypeId.charAt(0);
                    final boolean isSpecialType = '9' < first && first < 'a' && bcpTypeId.length() == 0;
                    if (isSpecialType) {
                        if (specialTypeSet == null) {
                            specialTypeSet = EnumSet.noneOf(SpecialType.class);
                        }
                        specialTypeSet.add(SpecialType.valueOf(legacyTypeId));
                        _bcp47Types.add(legacyTypeId);
                        continue;
                    }

                    if (isTZ) {
                        // a timezone key uses a colon instead of a slash in the resource.
                        // e.g. America:Los_Angeles
                        legacyTypeId = legacyTypeId.replace(':', '/');
                    }

                    boolean hasSameType = false;
                    if (bcpTypeId.length() == 0) {
                        // Empty value indicates that BCP type is same with the legacy type.
                        bcpTypeId = legacyTypeId;
                        hasSameType = true;
                    }
                    _bcp47Types.add(bcpTypeId);

                    // Note: legacy type value should never be
                    // equivalent to bcp type value of a different
                    // type under the same key. So we use a single
                    // map for lookup.
                    Type t = new Type(legacyTypeId, bcpTypeId);
                    typeDataMap.put(AsciiUtil.toLowerString(legacyTypeId), t);
                    if (!hasSameType) {
                        typeDataMap.put(AsciiUtil.toLowerString(bcpTypeId), t);
                    }

                    // Also put aliases in the map
                    if (typeAliasMap != null) {
                        Set<String> typeAliasSet = typeAliasMap.get(legacyTypeId);
                        if (typeAliasSet != null) {
                            for (String alias : typeAliasSet) {
                                typeDataMap.put(AsciiUtil.toLowerString(alias), t);
                            }
                        }
                    }
                    if (bcpTypeAliasMap != null) {
                        Set<String> bcpTypeAliasSet = bcpTypeAliasMap.get(bcpTypeId);
                        if (bcpTypeAliasSet != null) {
                            for (String alias : bcpTypeAliasSet) {
                                typeDataMap.put(AsciiUtil.toLowerString(alias), t);
                            }
                        }
                    }
                }
            }

            KeyData keyData = new KeyData(legacyKeyId, bcpKeyId, typeDataMap, specialTypeSet);

            KEYMAP.put(AsciiUtil.toLowerString(legacyKeyId), keyData);
            if (!hasSameKey) {
                KEYMAP.put(AsciiUtil.toLowerString(bcpKeyId), keyData);
            }
        }
        BCP47_KEYS = Collections.unmodifiableMap(_Bcp47Keys);
    }

    static Set<String> DEPRECATED_KEYS = Collections.emptySet(); // default for no resources
    static Map<String, ValueType> VALUE_TYPES = Collections.emptyMap(); // default for no resources
    static Map<String, Set<String>> DEPRECATED_KEY_TYPES = Collections.emptyMap(); // default for no resources

    private enum KeyInfoType {deprecated, valueType}
    private enum TypeInfoType {deprecated}

    /** Reads
keyInfo{
    deprecated{
        kh{"true"}
        vt{"true"}
    }
    valueType{
        ca{"incremental"}
        h0{"single"}
        kr{"multiple"}
        vt{"multiple"}
        x0{"any"}
    }
}
     */
    private static void getKeyInfo(UResourceBundle keyInfoRes) {
        Set<String> _deprecatedKeys = new LinkedHashSet<String>();
        Map<String, ValueType> _valueTypes = new LinkedHashMap<String, ValueType>();
        for (UResourceBundleIterator keyInfoIt = keyInfoRes.getIterator(); keyInfoIt.hasNext();) {
            UResourceBundle keyInfoEntry = keyInfoIt.next();
            String key = keyInfoEntry.getKey();
            KeyInfoType keyInfo = KeyInfoType.valueOf(key);
            for (UResourceBundleIterator keyInfoIt2 = keyInfoEntry.getIterator(); keyInfoIt2.hasNext();) {
                UResourceBundle keyInfoEntry2 = keyInfoIt2.next();
                String key2 = keyInfoEntry2.getKey();
                String value2 = keyInfoEntry2.getString();
                switch (keyInfo) {
                case deprecated:
                    _deprecatedKeys.add(key2);
                    break;
                case valueType:
                    _valueTypes.put(key2, ValueType.valueOf(value2));
                    break;
                }
            }
        }
        DEPRECATED_KEYS = Collections.unmodifiableSet(_deprecatedKeys);
        VALUE_TYPES = Collections.unmodifiableMap(_valueTypes);
    }

    /** Reads:
typeInfo{
    deprecated{
        co{
            direct{"true"}
        }
        tz{
            camtr{"true"}
        }
    }
}
     */
    private static void getTypeInfo(UResourceBundle typeInfoRes) {
        Map<String,Set<String>> _deprecatedKeyTypes = new LinkedHashMap<String,Set<String>>();
        for (UResourceBundleIterator keyInfoIt = typeInfoRes.getIterator(); keyInfoIt.hasNext();) {
            UResourceBundle keyInfoEntry = keyInfoIt.next();
            String key = keyInfoEntry.getKey();
            TypeInfoType typeInfo = TypeInfoType.valueOf(key);
            for (UResourceBundleIterator keyInfoIt2 = keyInfoEntry.getIterator(); keyInfoIt2.hasNext();) {
                UResourceBundle keyInfoEntry2 = keyInfoIt2.next();
                String key2 = keyInfoEntry2.getKey();
                Set<String> _deprecatedTypes = new LinkedHashSet<String>();
                for (UResourceBundleIterator keyInfoIt3 = keyInfoEntry2.getIterator(); keyInfoIt3.hasNext();) {
                    UResourceBundle keyInfoEntry3 = keyInfoIt3.next();
                    String key3 = keyInfoEntry3.getKey();
                    switch (typeInfo) { // allow for expansion
                    case deprecated:
                        _deprecatedTypes.add(key3);
                        break;
                    }
                }
                _deprecatedKeyTypes.put(key2, Collections.unmodifiableSet(_deprecatedTypes));
            }
        }
        DEPRECATED_KEY_TYPES = Collections.unmodifiableMap(_deprecatedKeyTypes);
    }

    //
    // Note:    The key-type data is currently read from ICU resource bundle keyTypeData.res.
    //          In future, we may import the data into code like below directly from CLDR to
    //          avoid cyclic dependency between ULocale and UResourceBundle. For now, the code
    //          below is just for proof of concept, and commented out.
    //

    //    private static final String[][] TYPE_DATA_CA = {
    //     // {<legacy type>, <bcp type - if different>},
    //        {"buddhist", null},
    //        {"chinese", null},
    //        {"coptic", null},
    //        {"dangi", null},
    //        {"ethiopic", null},
    //        {"ethiopic-amete-alem", "ethioaa"},
    //        {"gregorian", "gregory"},
    //        {"hebrew", null},
    //        {"indian", null},
    //        {"islamic", null},
    //        {"islamic-civil", null},
    //        {"islamic-rgsa", null},
    //        {"islamic-tbla", null},
    //        {"islamic-umalqura", null},
    //        {"iso8601", null},
    //        {"japanese", null},
    //        {"persian", null},
    //        {"roc", null},
    //    };
    //
    //    private static final String[][] TYPE_DATA_KS = {
    //     // {<legacy type>, <bcp type - if different>},
    //        {"identical", "identic"},
    //        {"primary", "level1"},
    //        {"quaternary", "level4"},
    //        {"secondary", "level2"},
    //        {"tertiary", "level3"},
    //    };
    //
    //    private static final String[][] TYPE_ALIAS_KS = {
    //     // {<legacy alias>, <legacy canonical>},
    //        {"quarternary", "quaternary"},
    //    };
    //
    //    private static final String[][] BCP_TYPE_ALIAS_CA = {
    //     // {<bcp deprecated>, <bcp preferred>
    //        {"islamicc", "islamic-civil"},
    //    };
    //
    //    private static final Object[][] KEY_DATA = {
    //     // {<legacy key>, <bcp key - if different>, <type map>, <type alias>, <bcp type alias>},
    //        {"calendar", "ca", TYPE_DATA_CA, null, BCP_TYPE_ALIAS_CA},
    //        {"colstrength", "ks", TYPE_DATA_KS, TYPE_ALIAS_KS, null},
    //    };

    private static final Object[][] KEY_DATA = {};

    @SuppressWarnings("unused")
    private static void initFromTables() {
        for (Object[] keyDataEntry : KEY_DATA) {
            String legacyKeyId = (String)keyDataEntry[0];
            String bcpKeyId = (String)keyDataEntry[1];
            String[][] typeData = (String[][])keyDataEntry[2];
            String[][] typeAliasData = (String[][])keyDataEntry[3];
            String[][] bcpTypeAliasData = (String[][])keyDataEntry[4];

            boolean hasSameKey = false;
            if (bcpKeyId == null) {
                bcpKeyId = legacyKeyId;
                hasSameKey = true;
            }

            // reverse type alias map
            Map<String, Set<String>> typeAliasMap = null;
            if (typeAliasData != null) {
                typeAliasMap = new HashMap<String, Set<String>>();
                for (String[] typeAliasDataEntry : typeAliasData) {
                    String from = typeAliasDataEntry[0];
                    String to = typeAliasDataEntry[1];
                    Set<String> aliasSet = typeAliasMap.get(to);
                    if (aliasSet == null) {
                        aliasSet = new HashSet<String>();
                        typeAliasMap.put(to, aliasSet);
                    }
                    aliasSet.add(from);
                }
            }

            // BCP type alias map data
            Map<String, Set<String>> bcpTypeAliasMap = null;
            if (bcpTypeAliasData != null) {
                bcpTypeAliasMap = new HashMap<String, Set<String>>();
                for (String[] bcpTypeAliasDataEntry : bcpTypeAliasData) {
                    String from = bcpTypeAliasDataEntry[0];
                    String to = bcpTypeAliasDataEntry[1];
                    Set<String> aliasSet = bcpTypeAliasMap.get(to);
                    if (aliasSet == null) {
                        aliasSet = new HashSet<String>();
                        bcpTypeAliasMap.put(to, aliasSet);
                    }
                    aliasSet.add(from);
                }
            }

            // Type map data
            assert typeData != null;
            Map<String, Type> typeDataMap = new HashMap<String, Type>();
            Set<SpecialType> specialTypeSet = null;

            for (String[] typeDataEntry : typeData) {
                String legacyTypeId = typeDataEntry[0];
                String bcpTypeId = typeDataEntry[1];

                // special types
                boolean isSpecialType = false;
                for (SpecialType st : SpecialType.values()) {
                    if (legacyTypeId.equals(st.toString())) {
                        isSpecialType = true;
                        if (specialTypeSet == null) {
                            specialTypeSet = new HashSet<SpecialType>();
                        }
                        specialTypeSet.add(st);
                        break;
                    }
                }
                if (isSpecialType) {
                    continue;
                }

                boolean hasSameType = false;
                if (bcpTypeId == null) {
                    bcpTypeId = legacyTypeId;
                    hasSameType = true;
                }

                // Note: legacy type value should never be
                // equivalent to bcp type value of a different
                // type under the same key. So we use a single
                // map for lookup.
                Type t = new Type(legacyTypeId, bcpTypeId);
                typeDataMap.put(AsciiUtil.toLowerString(legacyTypeId), t);
                if (!hasSameType) {
                    typeDataMap.put(AsciiUtil.toLowerString(bcpTypeId), t);
                }

                // Also put aliases in the index
                Set<String> typeAliasSet = typeAliasMap.get(legacyTypeId);
                if (typeAliasSet != null) {
                    for (String alias : typeAliasSet) {
                        typeDataMap.put(AsciiUtil.toLowerString(alias), t);
                    }
                }
                Set<String> bcpTypeAliasSet = bcpTypeAliasMap.get(bcpTypeId);
                if (bcpTypeAliasSet != null) {
                    for (String alias : bcpTypeAliasSet) {
                        typeDataMap.put(AsciiUtil.toLowerString(alias), t);
                    }
                }
            }

            EnumSet<SpecialType> specialTypes = null;
            if (specialTypeSet != null) {
                specialTypes = EnumSet.copyOf(specialTypeSet);
            }

            KeyData keyData = new KeyData(legacyKeyId, bcpKeyId, typeDataMap, specialTypes);

            KEYMAP.put(AsciiUtil.toLowerString(legacyKeyId), keyData);
            if (!hasSameKey) {
                KEYMAP.put(AsciiUtil.toLowerString(bcpKeyId), keyData);
            }
        }
    }

    private static final Map<String, KeyData> KEYMAP = new HashMap<String, KeyData>();
    private static Map<String, Set<String>> BCP47_KEYS;

    static {
        // initFromTables();
        initFromResourceBundle();
    }

    public static Set<String> getBcp47Keys() {
        return BCP47_KEYS.keySet();
    };

    public static Set<String> getBcp47KeyTypes(String key) {
        return BCP47_KEYS.get(key);
    };

    public static boolean isDeprecated(String key) {
        return DEPRECATED_KEYS.contains(key);
    }

    public static boolean isDeprecated(String key, String type) {
        Set<String> deprecatedTypes = DEPRECATED_KEY_TYPES.get(key);
        if (deprecatedTypes == null) {
            return false;
        }
        return deprecatedTypes.contains(type);
    }

    public static ValueType getValueType(String key) {
        ValueType type = VALUE_TYPES.get(key);
        return type == null ? ValueType.single : type;
    }
}
