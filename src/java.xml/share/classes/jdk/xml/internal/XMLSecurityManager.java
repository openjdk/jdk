/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.xml.internal;


import com.sun.org.apache.xerces.internal.util.SecurityManager;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import javax.xml.catalog.CatalogManager;
import javax.xml.catalog.CatalogResolver;
import javax.xml.catalog.CatalogResolver.NotFoundAction;
import javax.xml.stream.XMLInputFactory;
import jdk.xml.internal.JdkProperty.State;
import jdk.xml.internal.JdkProperty.ImplPropMap;
import org.xml.sax.SAXException;

/**
 * This class manages standard and implementation-specific limitations.
 *
 */
public final class XMLSecurityManager {

    public static final String DTD_KEY = JdkConstants.DTD_PROPNAME;

    // Xerces Feature
    public static final String DISALLOW_DTD = "http://apache.org/xml/features/disallow-doctype-decl";
    public static final String LOAD_EXTERNAL_DTD = "http://apache.org/xml/features/nonvalidating/load-external-dtd";

    // StAX feature
    public static final String ZEPHYR_PROPERTY_PREFIX = "http://java.sun.com/xml/stream/properties/" ;
    public static final String IGNORE_EXTERNAL_DTD = ZEPHYR_PROPERTY_PREFIX + "ignore-external-dtd";

    // Valid values for the DTD property
    public static final String DTD_ALLOW = "allow";
    public static final String DTD_IGNORE = "ignore";
    public static final String DTD_DENY = "deny";
    static final Map<String, Integer> DTD_MAP;
    // Source Level JDK 8
    static {
        Map<String, Integer> map = new HashMap<>();
        map.put(DTD_ALLOW, 0);
        map.put(DTD_IGNORE, 1);
        map.put(DTD_DENY, 2);
        DTD_MAP = Collections.unmodifiableMap(map);
    }

    // Valid values for Catalog Resolve, and mappings between the string and
    // interger values
    static final Map<String, Integer> CR_MAP;
    // Source Level JDK 8
    static {
        Map<String, Integer> map = new HashMap<>();
        map.put("continue", 0);
        map.put("ignore", 1);
        map.put("strict", 2);
        CR_MAP = Collections.unmodifiableMap(map);
    }

    // Value converter for properties of type Boolean
    private static final BooleanMapper BOOLMAPPER = new BooleanMapper();

    // Value converter for properties of type Integer
    private static final IntegerMapper INTMAPPER = new IntegerMapper();

    // DTD value mapper
    private static final StringMapper DTDMAPPER = new StringMapper(DTD_MAP);

    // Catalog Resolve value mapper
    private static final StringMapper CRMAPPER = new StringMapper(CR_MAP);

    /**
     * Limits managed by the security manager
     */
    @SuppressWarnings("deprecation")
    public static enum Limit {
        ENTITY_EXPANSION_LIMIT("EntityExpansionLimit", JdkConstants.JDK_ENTITY_EXPANSION_LIMIT,
            JdkConstants.SP_ENTITY_EXPANSION_LIMIT, JdkConstants.ENTITY_EXPANSION_LIMIT, 0, 64000, Processor.PARSER, INTMAPPER),
        MAX_OCCUR_NODE_LIMIT("MaxOccurLimit", JdkConstants.JDK_MAX_OCCUR_LIMIT,
            JdkConstants.SP_MAX_OCCUR_LIMIT, JdkConstants.MAX_OCCUR_LIMIT, 0, 5000, Processor.PARSER, INTMAPPER),
        ELEMENT_ATTRIBUTE_LIMIT("ElementAttributeLimit", JdkConstants.JDK_ELEMENT_ATTRIBUTE_LIMIT,
            JdkConstants.SP_ELEMENT_ATTRIBUTE_LIMIT, JdkConstants.ELEMENT_ATTRIBUTE_LIMIT, 0, 10000, Processor.PARSER, INTMAPPER),
        TOTAL_ENTITY_SIZE_LIMIT("TotalEntitySizeLimit", JdkConstants.JDK_TOTAL_ENTITY_SIZE_LIMIT,
            JdkConstants.SP_TOTAL_ENTITY_SIZE_LIMIT, null, 0, 50000000, Processor.PARSER, INTMAPPER),
        GENERAL_ENTITY_SIZE_LIMIT("MaxEntitySizeLimit", JdkConstants.JDK_GENERAL_ENTITY_SIZE_LIMIT,
            JdkConstants.SP_GENERAL_ENTITY_SIZE_LIMIT, null, 0, 0, Processor.PARSER, INTMAPPER),
        PARAMETER_ENTITY_SIZE_LIMIT("MaxEntitySizeLimit", JdkConstants.JDK_PARAMETER_ENTITY_SIZE_LIMIT,
            JdkConstants.SP_PARAMETER_ENTITY_SIZE_LIMIT, null, 0, 1000000, Processor.PARSER, INTMAPPER),
        MAX_ELEMENT_DEPTH_LIMIT("MaxElementDepthLimit", JdkConstants.JDK_MAX_ELEMENT_DEPTH,
            JdkConstants.SP_MAX_ELEMENT_DEPTH, null, 0, 0, Processor.PARSER, INTMAPPER),
        MAX_NAME_LIMIT("MaxXMLNameLimit", JdkConstants.JDK_XML_NAME_LIMIT,
            JdkConstants.SP_XML_NAME_LIMIT, null, 1000, 1000, Processor.PARSER, INTMAPPER),
        ENTITY_REPLACEMENT_LIMIT("EntityReplacementLimit", JdkConstants.JDK_ENTITY_REPLACEMENT_LIMIT,
            JdkConstants.SP_ENTITY_REPLACEMENT_LIMIT, null, 0, 3000000, Processor.PARSER, INTMAPPER),
        XPATH_GROUP_LIMIT("XPathGroupLimit", JdkConstants.XPATH_GROUP_LIMIT,
            JdkConstants.XPATH_GROUP_LIMIT, null, 10, 10, Processor.XPATH, INTMAPPER),
        XPATH_OP_LIMIT("XPathExprOpLimit", JdkConstants.XPATH_OP_LIMIT,
            JdkConstants.XPATH_OP_LIMIT, null, 100, 100, Processor.XPATH, INTMAPPER),
        XPATH_TOTALOP_LIMIT("XPathTotalOpLimit", JdkConstants.XPATH_TOTALOP_LIMIT,
            JdkConstants.XPATH_TOTALOP_LIMIT, null, 10000, 10000, Processor.XPATH, INTMAPPER),
        DTD("DTDProperty", JdkConstants.DTD_PROPNAME, JdkConstants.DTD_PROPNAME, null,
                JdkConstants.ALLOW, JdkConstants.ALLOW, Processor.PARSER, DTDMAPPER),
        XERCES_DISALLOW_DTD("disallowDTD", DISALLOW_DTD, null, null, 0, 0, Processor.PARSER, BOOLMAPPER),
        STAX_SUPPORT_DTD("supportDTD", XMLInputFactory.SUPPORT_DTD, null, null, 1, 1, Processor.PARSER, BOOLMAPPER),
        JDKCATALOG_RESOLVE("JDKCatalogResolve", JdkConstants.JDKCATALOG_RESOLVE, JdkConstants.JDKCATALOG_RESOLVE, null,
                JdkConstants.CONTINUE, JdkConstants.CONTINUE, Processor.PARSER, CRMAPPER),
        ;

        final String key;
        final String apiProperty;
        final String systemProperty;
        final String spOld;
        final int defaultValue;
        final int secureValue;
        final Processor processor;
        final ValueMapper mapper;

        Limit(String key, String apiProperty, String systemProperty, String spOld, int value,
                int secureValue, Processor processor, ValueMapper mapper) {
            this.key = key;
            this.apiProperty = apiProperty;
            this.systemProperty = systemProperty;
            this.spOld = spOld;
            this.defaultValue = value;
            this.secureValue = secureValue;
            this.processor = processor;
            this.mapper = mapper;
        }

        /**
         * Checks whether the specified name is a limit. Checks both the
         * property and System Property which is now the new property name.
         *
         * @param name the specified name
         * @return true if there is a match, false otherwise
         */
        public boolean is(String name) {
            // current spec: new property name == systemProperty
            return (systemProperty != null && systemProperty.equals(name)) ||
                   // current spec: apiProperty is legacy
                   (apiProperty.equals(name));
        }

        /**
         * Returns the state of a property name. By the specification as of JDK 17,
         * the "jdk.xml." prefixed System property name is also the current API
         * name. The URI-based qName is legacy.
         *
         * @param name the property name
         * @return the state of the property name, null if no match
         */
        public State getState(String name) {
            if (systemProperty != null && systemProperty.equals(name)) {
                return State.APIPROPERTY;
            } else if (apiProperty.equals(name)) {
                //the URI-style qName is legacy
                return State.LEGACY_APIPROPERTY;
            }
            return null;
        }

        public String key() {
            return key;
        }

        public String apiProperty() {
            return apiProperty;
        }

        public String systemProperty() {
            return systemProperty;
        }

        // returns legacy System Property
        public String spOld() {
            return spOld;
        }

        public int defaultValue() {
            return defaultValue;
        }

        public boolean isSupported(Processor p) {
            return processor == p;
        }

        int secureValue() {
            return secureValue;
        }

        public ValueMapper mapper() {
            return mapper;
        }
    }

    /**
     * Supported processors
     */
    public static enum Processor {
        ANY,
        PARSER,
        XPATH,
    }

    private static final int NO_LIMIT = 0;

    /**
     * Values of the properties
     */
    private final int[] values;

    /**
     * States of the settings for each property
     */
    private State[] states;

    /**
     * Flag indicating if secure processing is set
     */
    boolean secureProcessing;

    /**
     * States that determine if properties are set explicitly
     */
    private boolean[] isSet;


    /**
     * Index of the special entityCountInfo property
     */
    private final int indexEntityCountInfo = 10000;
    private String printEntityCountInfo = "";

    /**
     * Default constructor. Establishes default values for known security
     * vulnerabilities.
     */
    public XMLSecurityManager() {
        this(false);
    }

    /**
     * Instantiate Security Manager in accordance with the status of
     * secure processing
     * @param secureProcessing
     */
    public XMLSecurityManager(boolean secureProcessing) {
        values = new int[Limit.values().length];
        states = new State[Limit.values().length];
        isSet = new boolean[Limit.values().length];
        this.secureProcessing = secureProcessing;
        for (Limit limit : Limit.values()) {
            if (secureProcessing) {
                values[limit.ordinal()] = limit.secureValue;
                states[limit.ordinal()] = State.FSP;
            } else {
                values[limit.ordinal()] = limit.defaultValue();
                states[limit.ordinal()] = State.DEFAULT;
            }
        }

        //read system properties or the config file (jaxp.properties by default)
        readSystemProperties();
        // prepare the JDK Catalog
        prepareCatalog();
    }

    /**
     * Flag indicating whether the JDK Catalog has been initialized
     */
    static volatile boolean jdkcatalogInitialized = false;
    private final Object lock = new Object();

    private void prepareCatalog() {
        if (!jdkcatalogInitialized) {
            synchronized (lock) {
                if (!jdkcatalogInitialized) {
                    jdkcatalogInitialized = true;
                    String resolve = getLimitValueAsString(Limit.JDKCATALOG_RESOLVE);
                    JdkCatalog.init(resolve);
                }
            }
        }
    }

    /**
     * Returns the JDKCatalogResolver with the current setting of the RESOLVE
     * property.
     *
     * @return the JDKCatalogResolver
     */
    public CatalogResolver getJDKCatalogResolver() {
        String resolve = getLimitValueAsString(Limit.JDKCATALOG_RESOLVE);
        return CatalogManager.catalogResolver(JdkCatalog.catalog, toActionType(resolve));
    }

    // convert the string value of the RESOLVE property to the corresponding
    // action type
    private NotFoundAction toActionType(String resolve) {
        for (NotFoundAction type : NotFoundAction.values()) {
            if (type.toString().equals(resolve)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Setting FEATURE_SECURE_PROCESSING explicitly
     */
    public void setSecureProcessing(boolean secure) {
        secureProcessing = secure;
        for (Limit limit : Limit.values()) {
            if (secure) {
                setLimit(limit.ordinal(), State.FSP, limit.secureValue());
            } else {
                setLimit(limit.ordinal(), State.FSP, limit.defaultValue());
            }
        }
    }

    /**
     * Return the state of secure processing
     * @return the state of secure processing
     */
    public boolean isSecureProcessing() {
        return secureProcessing;
    }

    /**
     * Finds a limit's new name with the given property name.
     * @param propertyName the property name specified
     * @return the limit's new name if found, null otherwise
     */
    public String find(String propertyName) {
        for (Limit limit : Limit.values()) {
            if (limit.is(propertyName)) {
                // current spec: new property name == systemProperty
                return limit.systemProperty();
            }
        }
        //ENTITYCOUNT's new name is qName
        if (ImplPropMap.ENTITYCOUNT.is(propertyName)) {
            return ImplPropMap.ENTITYCOUNT.qName();
        }
        return null;
    }

    /**
     * Set limit by property name and state
     * @param propertyName property name
     * @param state the state of the property
     * @param value the value of the property
     * @return true if the property is managed by the security manager; false
     *              if otherwise.
     */
    public boolean setLimit(String propertyName, State state, Object value) {
        // special property to return entity count info
        if (ImplPropMap.ENTITYCOUNT.is(propertyName)) {
            printEntityCountInfo = (String)value;
            return true;
        }

        Limit limit = getEnumValue(propertyName);
        if (limit != null) {
            State pState = state;
            if (state == State.APIPROPERTY) {
                // ordinal is the index of the value array
                pState = (Limit.values()[limit.ordinal()]).getState(propertyName);
            }
            setLimit(limit, pState, value);
            return true;
        }
        return false;
    }

    /**
     * Set the value for a specific limit.
     *
     * @param limit the limit
     * @param state the state of the property
     * @param value the value of the property
     */
    public void setLimit(Limit limit, State state, int value) {
        setLimit(limit.ordinal(), state, value);
    }

    /**
     * Sets the value of a property by its enum name
     *
     * @param limit the limit
     * @param state the state of the property
     * @param value the value of the property
     */
    public void setLimit(Limit limit, State state, Object value) {
        int intValue = limit.mapper().toInt(value);
        if (intValue < 0) {
            intValue = 0;
        }

        setLimit(limit.ordinal(), state, intValue);
    }

    /**
     * Set the value of a property by its index
     *
     * @param index the index of the property
     * @param state the state of the property
     * @param value the value of the property
     */
    public void setLimit(int index, State state, int value) {
        if (index == indexEntityCountInfo) {
            //if it's explicitly set, it's treated as yes no matter the value
            printEntityCountInfo = JdkConstants.JDK_YES;
        } else {
            //only update if it shall override
            if (state.compareTo(states[index]) >= 0) {
                values[index] = value;
                states[index] = state;
                isSet[index] = true;
            }
        }
    }

    /**
     * Return the value of the specified property
     *
     * @param propertyName the property name
     * @return the value of the property as a string. If a property is managed
     * by this manager, its value shall not be null.
     */
    public String getLimitAsString(String propertyName) {
        int index = getIndex(propertyName);
        if (index > -1) {
            return getLimitValueByIndex(index);
        }

        return null;
    }
    /**
     * Return the value of the specified property
     *
     * @param limit the property
     * @return the value of the property
     */
    public int getLimit(Limit limit) {
        return values[limit.ordinal()];
    }

    /**
     * Return the value of a property by its ordinal
     *
     * @param limit the property
     * @return value of a property
     */
    public String getLimitValueAsString(Limit limit) {
        return limit.mapper().toString(values[limit.ordinal()]);
    }

    /**
     * Return the value of a property by its ordinal
     *
     * @param index the index of a property
     * @return limit of a property as a string
     */
    public String getLimitValueByIndex(int index) {
        if (index == indexEntityCountInfo) {
            return printEntityCountInfo;
        }

        Limit limit = Limit.values()[index];
        return limit.mapper().toString(values[index]);
    }

    /**
     * Return the state of the limit property
     *
     * @param limit the limit
     * @return the state of the limit property
     */
    public State getState(Limit limit) {
        return states[limit.ordinal()];
    }

    /**
     * Return the state of the limit property
     *
     * @param limit the limit
     * @return the state of the limit property
     */
    public String getStateLiteral(Limit limit) {
        return states[limit.ordinal()].literal();
    }

    /**
     * Returns the enum value by its property name.
     *
     * @param propertyName property name
     * @return the enum value if found; null otherwise
     */
    public Limit getEnumValue(String propertyName) {
        for (Limit limit : Limit.values()) {
            if (limit.is(propertyName)) {
                return limit;
            }
        }

        return null;
    }

    /**
     * Get the index by property name
     *
     * @param propertyName property name
     * @return the index of the property if found; return -1 if not
     */
    public int getIndex(String propertyName) {
        for (Limit limit : Limit.values()) {
            // see JDK-8265248, accept both the URL and jdk.xml as prefix
            if (limit.is(propertyName)) {
                //internally, ordinal is used as index
                return limit.ordinal();
            }
        }
        //special property to return entity count info
        if (ImplPropMap.ENTITYCOUNT.is(propertyName)) {
            return indexEntityCountInfo;
        }
        return -1;
    }

    /**
     * Check if there's no limit defined by the Security Manager
     * @param limit
     * @return
     */
    public boolean isNoLimit(int limit) {
        return limit == NO_LIMIT;
    }
    /**
     * Check if the size (length or count) of the specified limit property is
     * over the limit
     *
     * @param limit the type of the limit property
     * @param entityName the name of the entity
     * @param size the size (count or length) of the entity
     * @return true if the size is over the limit, false otherwise
     */
    public boolean isOverLimit(Limit limit, String entityName, int size,
            XMLLimitAnalyzer limitAnalyzer) {
        return isOverLimit(limit.ordinal(), entityName, size, limitAnalyzer);
    }

    /**
     * Check if the value (length or count) of the specified limit property is
     * over the limit
     *
     * @param index the index of the limit property
     * @param entityName the name of the entity
     * @param size the size (count or length) of the entity
     * @return true if the size is over the limit, false otherwise
     */
    public boolean isOverLimit(int index, String entityName, int size,
            XMLLimitAnalyzer limitAnalyzer) {
        if (values[index] == NO_LIMIT) {
            return false;
        }
        if (size > values[index]) {
            limitAnalyzer.addValue(index, entityName, size);
            return true;
        }
        return false;
    }

    /**
     * Check against cumulated value
     *
     * @param limit the type of the limit property
     * @param size the size (count or length) of the entity
     * @return true if the size is over the limit, false otherwise
     */
    public boolean isOverLimit(Limit limit, XMLLimitAnalyzer limitAnalyzer) {
        return isOverLimit(limit.ordinal(), limitAnalyzer);
    }

    public boolean isOverLimit(int index, XMLLimitAnalyzer limitAnalyzer) {
        if (values[index] == NO_LIMIT) {
            return false;
        }

        if (index == Limit.ELEMENT_ATTRIBUTE_LIMIT.ordinal() ||
                index == Limit.ENTITY_EXPANSION_LIMIT.ordinal() ||
                index == Limit.TOTAL_ENTITY_SIZE_LIMIT.ordinal() ||
                index == Limit.ENTITY_REPLACEMENT_LIMIT.ordinal() ||
                index == Limit.MAX_ELEMENT_DEPTH_LIMIT.ordinal() ||
                index == Limit.MAX_NAME_LIMIT.ordinal()
                ) {
            return (limitAnalyzer.getTotalValue(index) > values[index]);
        } else {
            return (limitAnalyzer.getValue(index) > values[index]);
        }
    }

    public void debugPrint(XMLLimitAnalyzer limitAnalyzer) {
        if (printEntityCountInfo.equals(JdkConstants.JDK_YES)) {
            limitAnalyzer.debugPrint(this);
        }
    }


    /**
     * Indicate if a property is set explicitly
     * @param limit the limit
     * @return true if the limit is set, false otherwise
     */
    public boolean isSet(Limit limit) {
        return isSet[limit.ordinal()];
    }

    /**
     * Checks whether the specified {@link Limit} is set and the value is
     * as specified.
     *
     * @param limit the {@link Limit}
     * @param value the value
     * @return true if the {@code Limit} is set and the values match
     */
    public boolean is(Limit limit, int value) {
        return getLimit(limit) == value;
    }

    /**
     * Checks whether the specified {@link Limit} is set and the value is
     * 1 (true for a property of boolean type).
     *
     * @param limit the {@link Limit}
     *
     * @return true if the {@code Limit} is set and the value is 1
     */
    public boolean is(Limit limit) {
        return getLimit(limit) == 1;
    }

    public boolean printEntityCountInfo() {
        return printEntityCountInfo.equals(JdkConstants.JDK_YES);
    }

    /**
     * Read system properties, or the configuration file
     */
    public void readSystemProperties() {
        for (Limit limit : Limit.values()) {
            if (State.SYSTEMPROPERTY.compareTo(states[limit.ordinal()]) >= 0 &&
                    limit.systemProperty() != null) {
                // attempts to read both the current and old system propery
                if (!getSystemProperty(limit, limit.systemProperty())
                        && (!getSystemProperty(limit, limit.spOld()))) {
                    //if system property is not found, try the config file
                    if (State.JAXPDOTPROPERTIES.compareTo(states[limit.ordinal()]) >= 0) {
                        getPropertyConfig(limit, limit.systemProperty());
                    }
                }
            }
        }
    }

    // Array list to store printed warnings for each SAX parser used
    private static final CopyOnWriteArrayList<String> printedWarnings = new CopyOnWriteArrayList<>();

    /**
     * Prints out warnings if a parser does not support the specified feature/property.
     *
     * @param parserClassName the name of the parser class
     * @param propertyName the property name
     * @param exception the exception thrown by the parser
     */
    public static void printWarning(String parserClassName, String propertyName, SAXException exception) {
        String key = parserClassName+":"+propertyName;
        if (printedWarnings.addIfAbsent(key)) {
            System.err.println( "Warning: "+parserClassName+": "+exception.getMessage());
        }
    }

    /**
     * Reads a system property, sets value and state if found.
     *
     * @param limit the limit property
     * @param sysPropertyName the name of system property
     */
    private boolean getSystemProperty(Limit limit, String sysPropertyName) {
        if (sysPropertyName == null) return false;

        try {
            String value = SecuritySupport.getSystemProperty(sysPropertyName);
            if (value != null && !value.equals("")) {
                setLimit(limit, State.SYSTEMPROPERTY, value);
                return true;
            }
        } catch (NumberFormatException e) {
            //invalid setting
            throw new NumberFormatException("Invalid setting for system property: " + limit.systemProperty());
        }
        return false;
    }

    /**
     * Reads a property from a configuration file, if any.
     *
     * @param limit the limit property
     * @param sysPropertyName the name of system property
     * @return
     */
    private boolean getPropertyConfig(Limit limit, String sysPropertyName) {
        try {
            String value = SecuritySupport.readConfig(sysPropertyName);
            if (value != null && !value.equals("")) {
                setLimit(limit, State.JAXPDOTPROPERTIES, value);
                return true;
            }
        } catch (NumberFormatException e) {
            //invalid setting
            throw new NumberFormatException("Invalid setting for system property: " + limit.systemProperty());
        }
        return false;
    }

    /**
     * Convert a value set through setProperty to XMLSecurityManager.
     * If the value is an instance of XMLSecurityManager, use it to override the default;
     * If the value is an old SecurityManager, convert to the new XMLSecurityManager.
     *
     * @param value user specified security manager
     * @param securityManager an instance of XMLSecurityManager
     * @return an instance of the new security manager XMLSecurityManager
     */
    public static XMLSecurityManager convert(Object value, XMLSecurityManager securityManager) {
        if (value == null) {
            if (securityManager == null) {
                securityManager = new XMLSecurityManager(true);
            }
            return securityManager;
        }
        if (value instanceof XMLSecurityManager) {
            return (XMLSecurityManager)value;
        } else {
            if (securityManager == null) {
                securityManager = new XMLSecurityManager(true);
            }
            if (value instanceof SecurityManager) {
                SecurityManager origSM = (SecurityManager)value;
                securityManager.setLimit(Limit.MAX_OCCUR_NODE_LIMIT, State.APIPROPERTY, origSM.getMaxOccurNodeLimit());
                securityManager.setLimit(Limit.ENTITY_EXPANSION_LIMIT, State.APIPROPERTY, origSM.getEntityExpansionLimit());
                securityManager.setLimit(Limit.ELEMENT_ATTRIBUTE_LIMIT, State.APIPROPERTY, origSM.getElementAttrLimit());
            }
            return securityManager;
        }
    }

    /**
     * Represents a mapper for properties of type String. The input is expected
     * to be a String or Object. If there is a map, the mappings are between the
     * keys and values within the map.
     */
    public static class StringMapper extends ValueMapper {
        private final Map<String, Integer> map;
        private final Map<Integer, String> reverseMap;

        public StringMapper(Map<String, Integer> map) {
            this.map = map;
            if (map != null) {
                reverseMap = map.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
            } else {
                reverseMap = null;
            }
        }

        /**
         * Finds the mapping int value with the specified property value. This
         * method will try to convert the provided value to an integer if no
         * mapping is found.
         * @param value the property value
         * @return the mapping int value if found, null otherwise
         */
        @Override
        public int toInt(Object value) {
            Objects.requireNonNull(value);
            Integer iVal;
            if (map != null) {
                iVal = map.get(((String)value).toLowerCase());
                iVal = (iVal == null) ? 0 : iVal;
            } else {
                try {
                    iVal = (int)Double.parseDouble((String)value);
                } catch (NumberFormatException e) {
                    // Note: this is the currently expected behavior. It may be
                    // appropriate for the setter to catch it.
                    throw new NumberFormatException("Invalid setting " + value
                            + " for a property of Integer type.");
                }
            }
            return iVal;
        }

        @Override
        public String toObject(int value) {
            if (reverseMap != null) {
                return reverseMap.get(value);
            }
            return Integer.toString(value);
        }

        @Override
        public String toString(int value) {
            return toObject(value);
        }
    }

    /**
     * Represents a mapper for properties of type Integer. The input is expected
     * to be either an Integer or String.
     */
    public static class IntegerMapper extends ValueMapper {
        @Override
        public int toInt(Object value) {
            Objects.requireNonNull(value);

            Integer iVal;
            if (value instanceof Integer) {
                iVal = (Integer)value;
            } else {
                try {
                    iVal = Integer.parseInt((String)value);
                } catch (NumberFormatException e) {
                    // Note: this is the currently expected behavior. It may be
                    // appropriate for the setter to catch it.
                    throw new NumberFormatException("Invalid setting " + value
                            + " for a property of Integer type.");
                }
            }

            return iVal;
        }

        @Override
        public Integer toObject(int value) {
            return value;
        }

        @Override
        public String toString(int value) {
            return Integer.toString(value);
        }
    }

    /**
     * Represents a mapper for properties of type Boolean. The input is expected
     * to be either a Boolean or String.
     */
    public static class BooleanMapper extends ValueMapper {
        @Override
        public int toInt(Object value) {
            Objects.requireNonNull(value);

            Boolean bVal;
            if (value instanceof Boolean) {
                bVal = (Boolean)value;
            } else {
                bVal = ((String)value).equalsIgnoreCase("true");
            }

            return bVal ? 1 : 0;
        }

        @Override
        public Boolean toObject(int value) {
            return value != 0;
        }

        @Override
        public String toString(int value) {
            return Boolean.toString(value != 0);
        }
    }

    /**
     * Represents a mapper of property values between int and other types, such as
     * Boolean, String, and Object.
     */
    public static abstract class ValueMapper {
        // converts to an int value from that of the specified type
        public abstract int toInt(Object value);
        // converts the int value back to the original type
        public abstract Object toObject(int value);
        // converts the int value of a property to a String representation
        public abstract String toString(int value);

    }

    /**
     * Represents a mapper of property values between int and other types, such as
     * Boolean, String, and Object.
     *
     * @param <T> the value type to be mapped with an int value
     */
    public abstract class ValueMapper1<T> {
        // converts to an int value from that of the specified type
        public abstract int toInt(T value);
        // converts the int value back to the original type
        public abstract T toObject(int value);
        // converts the int value of a property to a String representation
        public abstract String toString(int value);

    }
}
