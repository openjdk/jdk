/*
 * Copyright (c) 2025, Red Hat, Inc.
 *
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

import java.io.*;
import java.lang.reflect.Method;
import java.security.Key;
import java.security.Provider;
import java.security.Security;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/*
 * @test
 * @bug 8345139
 * @run main/othervm/timeout=60 -enablesystemassertions ServicesConsistency
 */

public final class ServicesConsistency {

    private static final String separatorThin = "----------------------------";

    private static final String separatorThick = "============================";

    private static final String aliasPrefix = "Alg.Alias.";
    private static final String sT = "M";
    private static final String algL = "alg";
    private static final String alg2L = algL + "2";
    private static final String alg2U = alg2L.toUpperCase();
    private static final String algPropKeyL = sT + "." + algL;
    private static final String alg2PropKeyL = sT + "." + alg2L;
    private static final String algU = algL.toUpperCase();
    private static final String algPropKeyU = sT + "." + algU;
    private static final String aliasL = "alias";
    private static final String aliasPropKeyL = aliasPrefix + sT + "." + aliasL;
    private static final String aliasU = aliasL.toUpperCase();
    private static final String aliasPropKeyU = aliasPrefix + sT + "." + aliasU;
    private static final String attrL = "attr1";
    private static final String attrU = attrL.toUpperCase();
    private static final String attrLAlgPropKeyL = algPropKeyL + " " + attrL;
    private static final String attrLAlgPropKeyU = algPropKeyU + " " + attrL;
    private static final String attrUAlgPropKeyL = algPropKeyL + " " + attrU;
    private static final String attrUAlgPropKeyU = algPropKeyU + " " + attrU;
    private static final String class1 = "class1";
    private static final String class2 = "class2";
    private static final String currentClass = "currentClass";
    private static final String currentClass2 = currentClass + "2";
    private static final String legacyClass = "legacyClass";
    private static final String attrValue = "attrValue";
    private static final String attrValue2 = attrValue + "2";
    private static Provider.Service s, s2;

    private static int testsFailed = 0;
    private static int testsTotal = 0;

    private static final TestProvider p;

    static {
        TestProvider tmp = new TestProvider();
        for (Provider p : Security.getProviders()) {
            Security.removeProvider(p.getName());
        }
        Security.addProvider(tmp);
        p = tmp;
    }

    private static final class TestProvider extends Provider {
        @Serial
        private static final long serialVersionUID = -6399263285569001970L;

        static TestProvider serializationCopy() throws Throwable {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(p);
            ObjectInputStream ois = new ObjectInputStream(
                    new ByteArrayInputStream(baos.toByteArray()));
            return (TestProvider) ois.readObject();
        }

        TestProvider() {
            super("TestProvider", "1.0", "TestProvider info");
        }

        void putService(String type, String algorithm, String className,
                List<String> aliases, Map<String, String> attributes) {
            System.out.println("Provider.putService(new Service(TestProvider," +
                    " " + type + ", " + algorithm + ", " + className + ", " +
                    aliases + ", " + attributes + "))");
            super.putService(new Service(this, type, algorithm, className,
                    aliases, attributes));
        }

        @Override
        public void removeService(Provider.Service s) {
            System.out.println("Provider.removeService(" + s + ")");
            super.removeService(s);
        }

        @Override
        public Object put(Object k, Object v) {
            return put(k, v, true);
        }

        Object put(Object k, Object v, boolean print) {
            if (print) {
                System.out.println("Provider.put(" + k + ", " + v + ")");
            }
            return super.put(k, v);
        }

        void showProperties() {
            System.out.println();
            System.out.println("Properties map:");
            System.out.println(separatorThin);
            for (Map.Entry<Object, Object> e : entrySet()) {
                Object k = e.getKey();
                Object v = e.getValue();
                if (k instanceof String ks) {
                    if (ks.startsWith("Provider.")) {
                        continue;
                    }
                }
                System.out.println(k + ": " + v);
            }
            System.out.println();
        }
    }

    public static class TestSpi {
    }

    public static class TestSpi2 {
    }

    public static void main(String[] args) throws Throwable {
        for (Method m : ServicesConsistency.class.getDeclaredMethods()) {
            if (m.getName().startsWith("test")) {
                try {
                    printTestHeader(m.getName());
                    testsTotal += 1;
                    m.invoke(null);
                } catch (Throwable t) {
                    testsFailed += 1;
                    t.printStackTrace();
                } finally {
                    p.clear();
                }
            }
        }

        if (testsFailed > 0) {
            throw new Exception("TESTS FAILED: " + testsFailed + "/" +
                    testsTotal);
        } else {
            System.out.println("TESTS PASSED: " + testsTotal + "/" +
                    testsTotal);
        }
    }

    private static void printTestHeader(String testName) {
        System.out.println(separatorThin);
        System.out.println(testName);
        System.out.println(separatorThin);
        System.out.println();
    }

    private static void printThickHeader(String title) {
        System.out.println(title);
        System.out.println(separatorThick);
        System.out.println();
    }

    private static void assertThat(boolean condition, String errorMsg)
            throws Exception {
        if (!condition) {
            throw new Exception(errorMsg);
        }
    }

    private static void propValueAssertion(String propKey, String expectedValue,
            String valueDesc, TestProvider p) throws Exception {
        Object value = p.get(propKey);
        assertThat(expectedValue.equals(value), "A wrong " + valueDesc +
                " is assigned to the '" + propKey + "' provider property: " +
                "expecting '" + expectedValue + "' but was '" + value + "'.");
    }

    private static void assertClassnamePropValue(String propKey,
            String expectedValue) throws Exception {
        assertClassnamePropValue(propKey, expectedValue, p);
    }

    private static void assertClassnamePropValue(String propKey,
            String expectedValue, TestProvider p) throws Exception {
        propValueAssertion(propKey, expectedValue, "class name", p);
    }

    private static void assertAliasPropValue(String propKey,
            String expectedValue) throws Exception {
        propValueAssertion(propKey, expectedValue, "algorithm", p);
    }

    private static void assertAttributePropValue(String propKey,
            String expectedValue) throws Exception {
        propValueAssertion(propKey, expectedValue, "attribute value", p);
    }

    private static void assertPropRemoved(String propKey) throws Exception {
        assertThat(p.get(propKey) == null, "Property '" + propKey + "' " +
                "expected to be removed but was not. Current value is '" +
                p.get(propKey) + "'.");
    }

    private static String getServiceDesc(Provider.Service svc) {
        return svc == null ? "null service" : "service with type '" +
                svc.getType() + "' and algorithm '" + svc.getAlgorithm() + "'";
    }

    private static void serviceAssertionCommon(Provider.Service svc,
            boolean isEqual, Provider.Service svc2, String errorMsg)
            throws Exception {
        String svc2Desc = getServiceDesc(svc2);
        if (isEqual) {
            assertThat(svc == svc2, errorMsg + " is not equal to a " +
                    svc2Desc + ", and was expected to be equal.");
        } else {
            assertThat(svc != svc2, errorMsg + " is equal to a " + svc2Desc +
                    ", and was not expected to be equal.");
        }
    }

    private static void lookupServiceAssertion(String type, String algorithm,
            boolean isEqual, Provider.Service svc2) throws Exception {
        serviceAssertionCommon(p.getService(type, algorithm), isEqual, svc2,
                "A service looked up by type '" + type + "' and algorithm '" +
                        algorithm + "'");
    }

    private static void assertServiceEqual(String type, String algorithm,
            Provider.Service svc2) throws Exception {
        lookupServiceAssertion(type, algorithm, true, svc2);
    }

    private static void assertServiceNotEqual(String type, String algorithm,
            Provider.Service svc2) throws Exception {
        lookupServiceAssertion(type, algorithm, false, svc2);
    }

    private static void serviceAssertion(Provider.Service svc, boolean isEqual,
            Provider.Service svc2) throws Exception {
        serviceAssertionCommon(svc, isEqual, svc2, "A " + getServiceDesc(svc));
    }

    private static void assertServiceEqual(Provider.Service svc,
            Provider.Service svc2) throws Exception {
        serviceAssertion(svc, true, svc2);
    }

    private static void assertServiceNotEqual(Provider.Service svc,
            Provider.Service svc2) throws Exception {
        serviceAssertion(svc, false, svc2);
    }

    private static void assertClassname(Provider.Service svc,
            String expectedClassName) throws Exception {
        assertServiceNotEqual(svc, null);
        String svcClassName = svc.getClassName();
        assertThat(expectedClassName.equals(svcClassName), "A " +
                getServiceDesc(svc) + " was expected to have a class name " +
                "equal to '" + expectedClassName + "' but is equal to '" +
                svcClassName + "'.");
    }

    private static void assertAttribute(Provider.Service svc, String attrName,
            String expectedAttrValue) throws Exception {
        assertServiceNotEqual(svc, null);
        String attrValue = svc.getAttribute(attrName);
        assertThat(Objects.equals(expectedAttrValue, attrValue), "A " +
                getServiceDesc(svc) + " was expected to have a '" + attrName +
                        "' attribute equal to '" + expectedAttrValue + "' but" +
                        " is equal to '" + attrValue + "'.");
    }

    private static void assertNotAttribute(Provider.Service svc,
            String attrName) throws Exception {
        assertAttribute(svc, attrName, null);
    }

    private static void testBasicLegacyAPIOps() throws Throwable {
        String attrLAliasPropKeyL = sT + "." + aliasL + " " + attrU;

        printThickHeader("Put an algorithm with two different cases:");
        p.put(algPropKeyL, class1);
        p.put(algPropKeyU, class2);
        p.showProperties();
        assertPropRemoved(algPropKeyL);
        assertClassnamePropValue(algPropKeyU, class2);
        assertClassname(p.getService(sT, algL), class2);
        assertClassname(p.getService(sT, algU), class2);
        p.clear();

        printThickHeader("Assign an alias with two different cases:");
        p.put(algPropKeyL, class1);
        p.put(aliasPropKeyL, algL);
        p.put(aliasPropKeyU, algL);
        s = p.getService(sT, algL);
        p.showProperties();
        assertPropRemoved(aliasPropKeyL);
        assertAliasPropValue(aliasPropKeyU, algL);
        assertServiceEqual(sT, aliasL, s);
        assertServiceEqual(sT, aliasU, s);
        assertClassname(s, class1);
        p.clear();

        printThickHeader("Put an attribute with different algorithm cases:");
        p.put(algPropKeyL, class1);
        p.put(attrLAlgPropKeyL, attrValue);
        p.put(attrLAlgPropKeyU, attrValue2);
        p.showProperties();
        assertPropRemoved(attrLAlgPropKeyL);
        assertAttributePropValue(attrLAlgPropKeyU, attrValue2);
        assertAttribute(p.getService(sT, algL), attrL, attrValue2);
        assertAttribute(p.getService(sT, algU), attrU, attrValue2);
        p.clear();

        printThickHeader("Put an attribute with different attr name case:");
        p.put(algPropKeyL, class1);
        p.put(attrLAlgPropKeyL, attrValue);
        p.put(attrUAlgPropKeyL, attrValue2);
        p.showProperties();
        assertPropRemoved(attrLAlgPropKeyL);
        assertAttributePropValue(attrUAlgPropKeyL, attrValue2);
        assertAttribute(p.getService(sT, algU), attrL, attrValue2);
        assertAttribute(p.getService(sT, algL), attrU, attrValue2);
        p.clear();

        printThickHeader("Replace attribute by alias:");
        p.put(algPropKeyL, class1);
        p.put(aliasPropKeyL, algL);
        p.put(attrLAlgPropKeyL, attrValue);
        p.put(attrLAliasPropKeyL, attrValue2);
        p.showProperties();
        assertPropRemoved(attrLAliasPropKeyL);
        assertAttributePropValue(attrLAlgPropKeyL, attrValue2);
        assertAttribute(p.getService(sT, algL), attrL, attrValue2);
        p.clear();

        printThickHeader("Remove service:");
        p.put(algPropKeyL, class1);
        p.put(aliasPropKeyL, algL);
        p.put(attrLAlgPropKeyL, attrValue);
        p.showProperties();
        p.remove(algPropKeyU);
        assertClassnamePropValue(algPropKeyL, class1);
        assertAliasPropValue(aliasPropKeyL, algL);
        assertAttributePropValue(attrLAlgPropKeyL, attrValue);
        assertClassname(p.getService(sT, algL), class1);
        assertClassname(p.getService(sT, aliasL), class1);
        assertAttribute(p.getService(sT, algL), attrL, attrValue);
        p.remove(algPropKeyL);
        assertPropRemoved(algPropKeyL);
        assertPropRemoved(aliasPropKeyL);
        assertPropRemoved(attrLAlgPropKeyL);
        assertServiceEqual(sT, algL, null);
        assertServiceEqual(sT, aliasL, null);
        p.clear();

        printThickHeader("Remove service alias:");
        p.put(algPropKeyL, class1);
        p.put(aliasPropKeyL, algL);
        p.remove(aliasPropKeyU);
        assertClassnamePropValue(algPropKeyL, class1);
        assertAliasPropValue(aliasPropKeyL, algL);
        assertServiceNotEqual(sT, aliasL, null);
        p.remove(aliasPropKeyL);
        assertPropRemoved(aliasPropKeyL);
        assertClassnamePropValue(algPropKeyL, class1);
        assertServiceEqual(sT, aliasL, null);
        assertClassname(p.getService(sT, algL), class1);
        p.clear();

        printThickHeader("Remove service attribute:");
        p.put(algPropKeyL, class1);
        p.put(attrLAlgPropKeyL, attrValue);
        p.remove(attrUAlgPropKeyL);
        assertAttributePropValue(attrLAlgPropKeyL, attrValue);
        assertAttribute(p.getService(sT, algL), attrL, attrValue);
        p.remove(attrLAlgPropKeyU);
        assertAttributePropValue(attrLAlgPropKeyL, attrValue);
        assertAttribute(p.getService(sT, algL), attrL, attrValue);
        p.remove(attrLAlgPropKeyL);
        assertPropRemoved(attrLAlgPropKeyL);
        assertClassnamePropValue(algPropKeyL, class1);
        assertNotAttribute(p.getService(sT, algL), attrL);
    }

    private static void testSerializationConsistencyBetweenAPIs()
            throws Throwable {
        printThickHeader("Before serialization:");
        p.putService(sT, algL, currentClass, null, null);
        p.put(algPropKeyL, legacyClass);
        p.showProperties();
        assertClassnamePropValue(algPropKeyL, currentClass);
        assertClassname(p.getService(sT, algL), currentClass);

        TestProvider serialP = TestProvider.serializationCopy();

        printThickHeader("After serialization:");
        serialP.showProperties();
        assertClassnamePropValue(algPropKeyL, currentClass, serialP);
        assertClassname(serialP.getService(sT, algL), currentClass);
    }

    private static void testComputeDoesNotThrowNPE() throws Throwable {
        p.put(algPropKeyL, class1);
        p.put(aliasPropKeyL, algL);
        p.compute(aliasPropKeyL, (key, oldV) -> null);
        assertPropRemoved(aliasPropKeyL);
    }

    private static void testMergeDoesNotThrowNPE() throws Throwable {
        p.put(algPropKeyL, class1);
        p.put(aliasPropKeyL, algL);
        p.merge(aliasPropKeyL, algL, (oldV, newV) -> null);
        assertPropRemoved(aliasPropKeyL);
    }

    private static void testLegacyAPIServicesOverride() throws Throwable {
        legacyAPIServicesOverride(false);
    }

    private static void testLegacyAPIServicesOverrideDifferentCase()
            throws Throwable {
        legacyAPIServicesOverride(true);
    }

    private static void legacyAPIServicesOverride(boolean differentCase)
            throws Throwable {
        String aliasAsAlgPropKey = sT + "." + (differentCase ? aliasU : aliasL);
        String algAsAliasPropKey = aliasPrefix + sT + "." +
                (differentCase ? algU : algL);

        printThickHeader("A Legacy API service algorithm can override a " +
                "Legacy API service algorithm:");
        p.put(algPropKeyL, class1);
        p.put(aliasPropKeyL, algL);
        p.put(attrLAlgPropKeyL, attrValue);
        assertClassnamePropValue(algPropKeyL, class1);
        assertAliasPropValue(aliasPropKeyL, algL);
        assertAttributePropValue(attrLAlgPropKeyL, attrValue);
        s = p.getService(sT, algL);
        assertServiceEqual(sT, aliasL, s);
        assertClassname(s, class1);
        assertAttribute(s, attrL, attrValue);
        p.put(differentCase ? algPropKeyU : algPropKeyL, class2);
        p.showProperties();
        s2 = p.getService(sT, algL);
        assertClassnamePropValue(differentCase ? algPropKeyU : algPropKeyL,
                class2);
        if (differentCase) {
            assertPropRemoved(algPropKeyL);
        }
        assertAliasPropValue(aliasPropKeyL, algL);
        assertAliasPropValue(attrLAlgPropKeyL, attrValue);
        assertServiceEqual(sT, algU, s2);
        assertClassname(s2, class2);
        assertClassname(p.getService(sT, aliasL), class2);
        assertAttribute(s2, attrL, attrValue);
        p.clear();

        printThickHeader("A Legacy API service algorithm is a Legacy API " +
                "service alias already. Modify the existing service through " +
                "its alias:");
        p.put(algPropKeyL, class1);
        p.put(attrLAlgPropKeyL, attrValue);
        p.put(aliasPropKeyL, algL);
        p.put(aliasAsAlgPropKey, class2);
        p.showProperties();
        s = p.getService(sT, algL);
        assertClassnamePropValue(algPropKeyL, class2);
        assertPropRemoved(aliasAsAlgPropKey);
        assertAttributePropValue(attrLAlgPropKeyL, attrValue);
        assertAliasPropValue(aliasPropKeyL, algL);
        assertClassname(p.getService(sT, aliasL), class2);
        assertClassname(p.getService(sT, aliasU), class2);
        assertClassname(s, class2);
        assertAttribute(s, attrL, attrValue);
        p.clear();

        printThickHeader("A Legacy API service alias can override a Legacy " +
                "API service alias:");
        p.put(algPropKeyL, class1);
        p.put(aliasPropKeyL, algL);
        p.put(alg2PropKeyL, class2);
        p.put(differentCase ? aliasPropKeyU : aliasPropKeyL, alg2L);
        p.showProperties();
        s2 = p.getService(sT, alg2L);
        assertAliasPropValue(differentCase ? aliasPropKeyU : aliasPropKeyL,
                alg2L);
        if (differentCase) {
            assertPropRemoved(aliasPropKeyL);
        }
        assertServiceEqual(sT, aliasL, s2);
        assertServiceEqual(sT, aliasU, s2);
        assertClassname(p.getService(sT, algL), class1);
        assertClassname(s2, class2);
        p.clear();

        printThickHeader("A Legacy API service algorithm cannot be " +
                "overwritten by a Legacy API service alias:");
        p.put(algPropKeyL, class1);
        s = p.getService(sT, algL);
        p.put(alg2PropKeyL, class2);
        p.put(algAsAliasPropKey, alg2L);
        s2 = p.getService(sT, alg2L);
        p.showProperties();
        assertPropRemoved(algAsAliasPropKey);
        assertClassnamePropValue(algPropKeyL, class1);
        assertClassnamePropValue(alg2PropKeyL, class2);
        assertServiceEqual(sT, algL, s);
        assertServiceEqual(sT, algU, s);
        assertClassname(s, class1);
        assertClassname(s2, class2);
        p.clear();

        // Add a Current API service to test invalid overrides
        p.putService(sT, algL, currentClass, List.of(aliasL),
                Map.of(attrL, attrValue));
        s = p.getService(sT, algL);
        assertServiceNotEqual(s, null);
        assertServiceEqual(sT, aliasL, s);
        System.out.println();

        printThickHeader("A Legacy API service algorithm cannot overwrite a " +
                "Current API service algorithm:");
        p.put(differentCase ? algPropKeyU : algPropKeyL, legacyClass);
        p.showProperties();
        assertClassnamePropValue(algPropKeyL, currentClass);
        if (differentCase) {
            assertPropRemoved(algPropKeyU);
        }
        assertClassname(p.getService(sT, algL), currentClass);
        assertClassname(p.getService(sT, algU), currentClass);

        printThickHeader("A Legacy API service alias cannot overwrite a " +
                "Current API service alias:");
        p.put(differentCase ? aliasPropKeyU : aliasPropKeyL, alg2L);
        p.showProperties();
        assertAliasPropValue(aliasPropKeyL, algL);
        if (differentCase) {
            assertPropRemoved(aliasPropKeyU);
        }
        assertServiceEqual(sT, aliasL, s);
        assertClassname(p.getService(sT, aliasL), currentClass);
        assertClassname(p.getService(sT, aliasU), currentClass);

        printThickHeader("A Legacy API service cannot overwrite a Current API" +
                " service attribute:");
        p.put(differentCase ? attrUAlgPropKeyU : attrLAlgPropKeyL, attrValue2);
        p.showProperties();
        assertAttributePropValue(attrLAlgPropKeyL, attrValue);
        if (differentCase) {
            assertPropRemoved(attrUAlgPropKeyU);
        }
        assertAttribute(p.getService(sT, algL), attrL, attrValue);

        printThickHeader("A Legacy API service alias cannot overwrite a " +
                "Current API service algorithm:");
        p.put(algAsAliasPropKey, alg2L);
        p.showProperties();
        assertClassnamePropValue(algPropKeyL, currentClass);
        assertPropRemoved(algAsAliasPropKey);
        assertClassname(p.getService(sT, algL), currentClass);
        assertClassname(p.getService(sT, algU), currentClass);

        printThickHeader("A Legacy API service algorithm cannot overwrite a " +
                "Current API service alias:");
        p.put(aliasAsAlgPropKey, legacyClass);
        p.showProperties();
        assertAliasPropValue(aliasPropKeyL, algL);
        assertPropRemoved(aliasAsAlgPropKey);
        assertClassname(p.getService(sT, aliasL), currentClass);
        assertClassname(p.getService(sT, aliasU), currentClass);

        assertServiceEqual(p.getService(sT, algL), s);
        assertServiceEqual(p.getService(sT, aliasL), s);
    }

    private static void testLegacyAPIAliasCannotBeAlgorithm() throws Throwable {
        p.put(aliasPropKeyL, aliasL);
        p.showProperties();
        assertPropRemoved(aliasPropKeyL);
        p.clear();

        p.put(sT + "." + aliasU, class1);
        p.put(aliasPropKeyL, aliasU);
        p.showProperties();
        assertClassnamePropValue(sT + "." + aliasU, class1);
        assertPropRemoved(aliasPropKeyL);
    }

    private static void testCurrentAPIServicesOverride() throws Throwable {
        currentAPIServicesOverride(false);
    }

    private static void testCurrentAPIServicesOverrideDifferentCase()
            throws Throwable {
        currentAPIServicesOverride(true);
    }

    private static void currentAPIServicesOverride(boolean differentCase)
            throws Throwable {
        printThickHeader("A Current API service overrides a Legacy API " +
                "service algorithm with its algorithm:");
        p.put(algPropKeyL, legacyClass);
        p.put(attrLAlgPropKeyL, attrValue);
        p.put(aliasPropKeyL, algL);
        s = p.getService(sT, algL);
        p.putService(sT, differentCase ? algU : algL, currentClass, List.of(),
                null);
        s2 = p.getService(sT, differentCase ? algU : algL);
        p.showProperties();
        if (differentCase) {
            assertPropRemoved(algPropKeyL);
        }
        assertPropRemoved(attrLAlgPropKeyL);
        assertPropRemoved(aliasPropKeyL);
        assertClassnamePropValue(differentCase ? algPropKeyU : algPropKeyL,
                currentClass);
        assertClassname(s, legacyClass);
        assertClassname(s2, currentClass);
        assertServiceEqual(sT, aliasL, null);
        assertNotAttribute(s2, attrL);
        p.clear();

        printThickHeader("A Current API service overrides a Legacy API " +
                "service alias with its algorithm:");
        p.put(algPropKeyL, legacyClass);
        p.put(aliasPropKeyL, algL);
        s = p.getService(sT, algL);
        assertServiceEqual(sT, aliasL, s);
        p.putService(sT, differentCase ? aliasU : aliasL, currentClass,
                List.of(), null);
        s2 = p.getService(sT, differentCase ? aliasU : aliasL);
        p.showProperties();
        assertClassnamePropValue(algPropKeyL, legacyClass);
        assertClassnamePropValue(sT + "." + (differentCase ? aliasU : aliasL),
                currentClass);
        assertPropRemoved(aliasPropKeyL);
        assertClassname(p.getService(sT, algL), legacyClass);
        assertClassname(s2, currentClass);
        p.clear();

        printThickHeader("A Current API service overrides a Legacy API " +
                "service algorithm with its alias:");
        p.put(algPropKeyL, legacyClass);
        p.put(aliasPropKeyL, algL);
        p.put(attrLAlgPropKeyL, attrValue);
        s = p.getService(sT, algL);
        assertServiceEqual(sT, aliasL, s);
        p.putService(sT, alg2L, currentClass,
                List.of(differentCase ? algU : algL), null);
        s2 = p.getService(sT, alg2L);
        p.showProperties();
        assertClassnamePropValue(alg2PropKeyL, currentClass);
        assertAliasPropValue(aliasPrefix + sT + "." + (differentCase ? algU :
                algL), alg2L);
        assertPropRemoved(algPropKeyL);
        assertPropRemoved(aliasPropKeyL);
        assertPropRemoved(attrLAlgPropKeyL);
        assertServiceEqual(sT, aliasL, null);
        assertServiceNotEqual(s, s2);
        assertServiceEqual(sT, algL, s2);
        assertClassname(s2, currentClass);
        assertNotAttribute(s2, attrL);
        p.clear();

        printThickHeader("A Current API service overrides a Legacy API alias" +
                " with its alias:");
        p.put(algPropKeyL, legacyClass);
        p.put(aliasPropKeyL, algL);
        p.put(attrLAlgPropKeyL, attrValue);
        s = p.getService(sT, algL);
        assertServiceEqual(sT, aliasL, s);
        p.putService(sT, alg2L, currentClass, List.of(differentCase ?
                aliasU : aliasL), null);
        s2 = p.getService(sT, alg2L);
        s = p.getService(sT, algL);
        p.showProperties();
        assertClassnamePropValue(algPropKeyL, legacyClass);
        assertAttributePropValue(attrLAlgPropKeyL, attrValue);
        assertClassnamePropValue(alg2PropKeyL, currentClass);
        assertAliasPropValue(differentCase ? aliasPropKeyU : aliasPropKeyL,
                alg2L);
        if (differentCase) {
            assertPropRemoved(aliasPropKeyL);
        }
        assertClassname(s, legacyClass);
        assertServiceEqual(sT, aliasL, s2);
        assertClassname(s2, currentClass);
        assertNotAttribute(s2, attrL);
        assertAttribute(s, attrL, attrValue);
        p.clear();

        printThickHeader("A Current API service overrides a Current API " +
                "service algorithm with its algorithm:");
        p.putService(sT, algL, currentClass, List.of(aliasL), Map.of(attrL,
                attrValue));
        s = p.getService(sT, algL);
        assertServiceEqual(sT, aliasL, s);
        assertClassname(s, currentClass);
        assertAttribute(s, attrL, attrValue);
        p.putService(sT, differentCase ? algU : algL, currentClass2, List.of(),
                null);
        s2 = p.getService(sT, differentCase ? algU : algL);
        p.showProperties();
        assertClassnamePropValue(differentCase ? algPropKeyU : algPropKeyL,
                currentClass2);
        if (differentCase) {
            assertPropRemoved(algPropKeyL);
        }
        assertPropRemoved(aliasPropKeyL);
        assertPropRemoved(attrLAlgPropKeyL);
        assertClassname(s2, currentClass2);
        assertNotAttribute(s2, attrL);
        assertServiceEqual(sT, aliasL, null);
        p.clear();

        printThickHeader("A Current API service overrides a Current API " +
                "service alias with its algorithm:");
        p.putService(sT, algL, currentClass, List.of(alg2L), Map.of(attrL,
                attrValue));
        assertServiceEqual(sT, alg2L, p.getService(sT, algL));
        p.putService(sT, differentCase ? alg2U : alg2L, currentClass2,
                List.of(), null);
        s = p.getService(sT, algL);
        s2 = p.getService(sT, differentCase ? alg2U : alg2L);
        p.showProperties();
        assertClassnamePropValue(algPropKeyL, currentClass);
        assertAttributePropValue(attrLAlgPropKeyL, attrValue);
        assertClassnamePropValue(sT + "." + (differentCase ? alg2U : alg2L),
                currentClass2);
        assertPropRemoved(aliasPrefix + alg2PropKeyL);
        assertClassname(s, currentClass);
        assertClassname(s2, currentClass2);
        assertAttribute(s, attrL, attrValue);
        assertNotAttribute(s2, attrL);
        p.removeService(s);
        assertPropRemoved(algPropKeyL);
        assertPropRemoved(attrLAlgPropKeyL);
        assertClassnamePropValue(sT + "." + (differentCase ? alg2U : alg2L),
                currentClass2);
        assertServiceEqual(sT, algL, null);
        assertServiceEqual(s2, p.getService(sT, differentCase ? alg2U : alg2L));
        p.clear();

        printThickHeader("A Current API service overrides a Current API " +
                "service algorithm with its alias:");
        p.putService(sT, algL, currentClass, List.of(aliasL), Map.of(attrL,
                attrValue));
        s = p.getService(sT, algL);
        assertServiceEqual(sT, aliasL, s);
        assertAttribute(s, attrL, attrValue);
        p.putService(sT, alg2L, currentClass2, List.of(differentCase ?
                algU : algL), null);
        s2 = p.getService(sT, alg2L);
        p.showProperties();
        assertClassnamePropValue(alg2PropKeyL, currentClass2);
        assertAliasPropValue(aliasPrefix + sT + "." + (differentCase ?
                algU : algL), alg2L);
        assertPropRemoved(algPropKeyL);
        assertPropRemoved(aliasPropKeyL);
        assertPropRemoved(attrLAlgPropKeyL);
        assertServiceEqual(sT, algL, s2);
        assertServiceEqual(sT, algU, s2);
        assertClassname(s2, currentClass2);
        assertNotAttribute(s2, attrL);
        assertServiceEqual(sT, aliasL, null);
        p.clear();

        printThickHeader("A Current API service overrides a Current API " +
                "service alias with its alias:");
        p.putService(sT, algL, currentClass, List.of(aliasL), Map.of(attrL,
                attrValue));
        s = p.getService(sT, algL);
        assertServiceEqual(sT, aliasL, s);
        assertAttribute(s, attrL, attrValue);
        p.putService(sT, alg2L, currentClass2, List.of(differentCase ?
                aliasU : aliasL), null);
        s2 = p.getService(sT, alg2L);
        p.showProperties();
        assertClassnamePropValue(algPropKeyL, currentClass);
        assertClassnamePropValue(alg2PropKeyL, currentClass2);
        assertAliasPropValue(differentCase ? aliasPropKeyU : aliasPropKeyL,
                alg2L);
        if (differentCase) {
            assertPropRemoved(aliasPropKeyL);
        }
        assertAttributePropValue(attrLAlgPropKeyL, attrValue);
        assertServiceEqual(sT, algL, s);
        assertServiceEqual(sT, aliasL, s2);
        assertServiceEqual(sT, aliasU, s2);
        p.removeService(s);
        assertPropRemoved(algPropKeyL);
        assertPropRemoved(attrLAlgPropKeyL);
        assertAliasPropValue(differentCase ? aliasPropKeyU : aliasPropKeyL,
                alg2L);
        assertServiceEqual(sT, algL, null);
        assertServiceEqual(sT, aliasL, s2);
        assertServiceEqual(sT, aliasU, s2);
    }

    private static void testInvalidServiceNotReturned() throws Throwable {
        p.put(aliasPropKeyL, algL);
        assertServiceEqual(sT, algL, null);
        assertServiceEqual(sT, aliasL, null);
    }

    private static void testInvalidCachedHasKeyAttributes() throws Throwable {
        invalidCachedSupportedKeyFormats(true);
    }

    private static void testInvalidCachedSupportedKeyFormats()
            throws Throwable {
        invalidCachedSupportedKeyFormats(false);
    }

    private static void invalidCachedSupportedKeyFormats(
            boolean targetingHasAttributes) throws Throwable {
        String sT = "Cipher";
        String algPropKeyL = sT + "." + algL;
        String attrPropKey = algPropKeyL + " SupportedKeyFormats";
        String format1 = "format1";
        String format2 = "format2";
        boolean supportsKeyFormat, supportsKeyFormat2;
        Key key = new Key() {
            @Serial
            private static final long serialVersionUID = 5040566397999588441L;
            @Override
            public String getAlgorithm() {
                return null;
            }
            @Override
            public String getFormat() {
                return format2;
            }
            @Override
            public byte[] getEncoded() {
                return new byte[0];
            }
        };

        p.put(algPropKeyL, TestSpi.class.getName());
        if (!targetingHasAttributes) {
            p.put(attrPropKey, format2);
        }
        supportsKeyFormat = p.getService(sT, algL).supportsParameter(key);
        p.put(attrPropKey, format1);
        supportsKeyFormat2 = p.getService(sT, algL).supportsParameter(key);
        p.showProperties();

        assertClassnamePropValue(algPropKeyL, TestSpi.class.getName());
        assertAttributePropValue(attrPropKey, format1);
        assertThat(supportsKeyFormat && !supportsKeyFormat2,
                "supportsKeyFormat expected to be 'true' (was '" +
                supportsKeyFormat + "'), and supportsKeyFormat2 expected to " +
                "be 'false' (was '" + supportsKeyFormat2 + "').");
    }

    private static void testInvalidCachedClass() throws Throwable {
        Object testSpi, testSpi2;
        p.put(algPropKeyL, TestSpi.class.getName());
        s = p.getService(sT, algL);
        testSpi = s.newInstance(null);
        assertClassname(s, TestSpi.class.getName());
        p.put(algPropKeyL, TestSpi2.class.getName());
        s2 = p.getService(sT, algL);
        testSpi2 = s2.newInstance(null);
        assertClassname(s2, TestSpi2.class.getName());
        p.showProperties();

        assertClassnamePropValue(algPropKeyL, TestSpi2.class.getName());
        assertThat(testSpi.getClass() == TestSpi.class && testSpi2.getClass()
                == TestSpi2.class, "testSpi expected to be an instance of '" +
                TestSpi.class.getSimpleName() + "' (was an instance of '" +
                testSpi.getClass().getSimpleName() + "'), and testSpi2 " +
                "expected to be an instance of '" +
                TestSpi2.class.getSimpleName() + "' (was an instance of '" +
                testSpi2.getClass().getSimpleName() + "').");
    }

    private static void testLegacyAPIAddIsRemove() throws Throwable {
        Object obj = new Object();
        p.put(algPropKeyL, class1);
        p.put(algPropKeyU, obj);
        assertClassnamePropValue(algPropKeyL, class1);
        assertServiceNotEqual(sT, algL, null);
        p.showProperties();
        p.put(algPropKeyL, obj);
        p.showProperties();
        assertThat(p.get(algPropKeyL) == obj, "Entry value " +
                "expected to be the Object instance added.");
        assertThat(p.get(algPropKeyU) == obj, "Entry value " +
                "expected to be the Object instance added.");
        assertServiceEqual(sT, algL, null);
    }

    private static void testReplaceAllIsAtomic() throws Throwable {
        concurrentReadWrite((Map<String, String> aliases) -> {
                    p.put(alg2PropKeyL, class2);
                    p.putAll(aliases);
                },
                (Map<String, String> aliases) ->
                    p.replaceAll((k, v) -> {
                        if (((String)k).startsWith(aliasPrefix) &&
                                algL.equals(v)) {
                            return alg2L;
                        }
                        return v;
                    }),
                (String sT, String firstAlias, String lastAlias) -> {
            Provider.Service s1 = p.getService(sT, firstAlias);
            Provider.Service s2 = p.getService(sT, lastAlias);
            if (s1 != null && s1.getClassName().equals(class2)) {
                if (s2 == null) {
                    throw new Exception("First service found, " +
                            "last service not found.");
                }
                return true;
            }
            return false;
        }, "Provider::replaceAll is not atomic.");
    }

    private static void testPutAllIsAtomic() throws Throwable {
        concurrentReadWrite(null, p::putAll,
                (String sT, String firstAlias, String lastAlias) -> {
            Provider.Service s1 = p.getService(sT, firstAlias);
            Provider.Service s2 = p.getService(sT, lastAlias);
            if (s1 != null) {
                if (s2 == null) {
                    throw new Exception("First service found, " +
                            "last service not found.");
                }
                return true;
            }
            return false;
        }, "Provider::putAll is not atomic.");
    }

    private static void testConcurrentServiceModification() throws Throwable {
        concurrentReadWrite(null, (Map<String, String> aliases) -> {
            for (Map.Entry<String, String> aliasEntry : aliases.entrySet()) {
                p.put(aliasEntry.getKey(), aliasEntry.getValue(), false);
            }
        }, (String sT, String firstAlias, String lastAlias) -> {
            Provider.Service s1 = p.getService(sT, firstAlias);
            if (s1 != null) {
                s1.toString();
            }
            return p.getService(sT, lastAlias) != null;
        }, "Concurrent modification of a service compromised integrity.");
    }

    @FunctionalInterface
    private interface ConcurrentWriteCallback {
        void apply(Map<String, String> aliases);
    }

    @FunctionalInterface
    private interface ConcurrentReadCallback {
        boolean apply(String sT, String firstAlias, String lastAlias)
                throws Throwable;
    }

    private static void concurrentReadWrite(ConcurrentWriteCallback preWriteCB,
            ConcurrentWriteCallback writerCB, ConcurrentReadCallback readerCB,
            String errorMsg) throws Throwable {
        int lastAlias = 500;
        int numberOfExperiments = 10;
        String firstAliasL = aliasL + 0;
        String lastAliasL = aliasL + lastAlias;
        Map<String, String> aliasesMap = new LinkedHashMap<>(lastAlias + 1);
        for (int i = 0; i <= lastAlias; i++) {
            aliasesMap.put(aliasPropKeyL + i, algL);
        }
        AtomicReference<Throwable> exceptionRef = new AtomicReference<>(null);
        Runnable writerR = () -> writerCB.apply(aliasesMap);
        Runnable readerR = () -> {
            try {
                while (!readerCB.apply(sT, firstAliasL, lastAliasL));
            } catch (Throwable t) {
                exceptionRef.set(t);
            }
        };
        for (int i = 0; i < numberOfExperiments; i++) {
            p.clear();
            p.put(algPropKeyL, class1);
            Thread writerT = new Thread(writerR);
            Thread readerT = new Thread(readerR);
            if (preWriteCB != null) {
                preWriteCB.apply(aliasesMap);
            }
            readerT.start();
            writerT.start();
            writerT.join();
            readerT.join();
            if (exceptionRef.get() != null) {
                throw new Exception(errorMsg, exceptionRef.get());
            }
        }
    }

    private static void testInvalidGetServiceRemoval() throws Throwable {
        invalidServiceRemoval(true);
    }

    private static void testInvalidGetServicesRemoval() throws Throwable {
        invalidServiceRemoval(false);
    }

    private static void invalidServiceRemoval(boolean getService)
            throws Throwable {
        p.put(aliasPropKeyL, algL);
        if (getService) {
            p.getService(sT, aliasL);
        } else {
            p.getServices();
        }
        p.put(algPropKeyL, class1);
        p.showProperties();

        assertAliasPropValue(aliasPropKeyL, algL);
        assertClassnamePropValue(algPropKeyL, class1);
        s = p.getService(sT, aliasL);
        assertClassname(s, class1);
        assertServiceEqual(sT, algL, s);
    }

    private static void testSerializationClassnameConsistency()
            throws Throwable {
        printThickHeader("Before serialization:");
        p.put(algPropKeyU, class1);
        p.put(algPropKeyL, class2);
        s = p.getService(sT, algL);
        s2 = p.getService(sT, algU);
        p.showProperties();
        assertClassname(s, class2);
        assertClassname(s2, class2);

        TestProvider serialP = TestProvider.serializationCopy();

        printThickHeader("After serialization:");
        serialP.showProperties();
        s = serialP.getService(sT, algL);
        s2 = serialP.getService(sT, algU);
        p.showProperties();
        assertClassname(s, class2);
        assertClassname(s2, class2);
    }

    private static void testCreateServiceByAlias() throws Throwable {
        printThickHeader("Create service by alias and set classname by alias:");
        p.put(aliasPropKeyL, algL);
        assertServiceEqual(sT, algL, null);
        assertServiceEqual(sT, aliasL, null);
        p.put(sT + "." + aliasL, class1);
        p.showProperties();
        assertPropRemoved(sT + "." + aliasL);
        assertAliasPropValue(aliasPropKeyL, algL);
        assertClassnamePropValue(algPropKeyL, class1);
        s = p.getService(sT, algL);
        assertServiceEqual(sT, aliasL, s);
        assertClassname(s, class1);
    }

    private static void testCreateServiceByAttr() throws Throwable {
        printThickHeader("Create a service by attribute:");
        p.put(attrLAlgPropKeyL, attrValue);
        assertServiceEqual(sT, algL, null);
        p.put(algPropKeyL, class1);
        p.showProperties();
        assertAttributePropValue(attrLAlgPropKeyL, attrValue);
        assertClassnamePropValue(algPropKeyL, class1);
        assertAttribute(p.getService(sT, algL), attrL, attrValue);
    }
}
