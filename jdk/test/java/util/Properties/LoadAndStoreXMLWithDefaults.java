/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Properties;


/**
 * @test
 * @bug 8016344
 * @summary checks that Properties.storeToXML only stores properties locally
 *          defined on the Properties object, excluding those that are inherited.
 * @author danielfuchs
 */
public class LoadAndStoreXMLWithDefaults {

    public static enum StoreMethod {
        // Note: this case will test the default provider when available,
        //       and the basic provider when it's not.
        PROPERTIES {
            @Override
            public String writeToXML(Properties p) throws IOException {
                final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                p.storeToXML(baos, "Test 8016344");
                return baos.toString();
            }
            @Override
            public Properties loadFromXML(String xml, Properties defaults)
                    throws IOException {
                final ByteArrayInputStream bais = new ByteArrayInputStream(xml.getBytes("UTF-8"));
                Properties p = new Properties(defaults);
                p.loadFromXML(bais);
                return p;
            }
        },
        // Note: this case always test the basic provider, which is always available.
        //       so sometimes it's just a dup with the previous case...
        BASICPROVIDER {
            @Override
            public String writeToXML(Properties p) throws IOException {
                final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                jdk.internal.util.xml.BasicXmlPropertiesProvider provider =
                        new  jdk.internal.util.xml.BasicXmlPropertiesProvider();
                provider.store(p, baos, "Test 8016344", "UTF-8");
                return baos.toString();
            }
            @Override
            public Properties loadFromXML(String xml, Properties defaults)
                    throws IOException {
                final ByteArrayInputStream bais = new ByteArrayInputStream(xml.getBytes("UTF-8"));
                Properties p = new Properties(defaults);
                jdk.internal.util.xml.BasicXmlPropertiesProvider provider =
                        new  jdk.internal.util.xml.BasicXmlPropertiesProvider();
                provider.load(p, bais);
                return p;
            }
        };
        public abstract String writeToXML(Properties p) throws IOException;
        public abstract Properties loadFromXML(String xml, Properties defaults)
                    throws IOException;
        public String displayName() {
            switch(this) {
                case PROPERTIES: return "Properties.storeToXML";
                case BASICPROVIDER: return "BasicXmlPropertiesProvider.store";
                default:
                    throw new UnsupportedOperationException(this.name());
            }
        }
    }

    static enum Objects { OBJ1, OBJ2, OBJ3 };

    public static void main(String[] args) throws IOException {
        Properties p1 = new Properties();
        p1.setProperty("p1.prop", "prop1-p1");
        p1.setProperty("p1.and.p2.prop", "prop2-p1");
        p1.setProperty("p1.and.p2.and.p3.prop", "prop3-p1");
        Properties p2 = new Properties(p1);
        p2.setProperty("p2.prop", "prop4-p2");
        p2.setProperty("p1.and.p2.prop", "prop5-p2");
        p2.setProperty("p1.and.p2.and.p3.prop", "prop6-p2");
        p2.setProperty("p2.and.p3.prop", "prop7-p2");
        Properties p3 = new Properties(p2);
        p3.setProperty("p3.prop", "prop8-p3");
        p3.setProperty("p1.and.p2.and.p3.prop", "prop9-p3");
        p3.setProperty("p2.and.p3.prop", "prop10-p3");

        for (StoreMethod m : StoreMethod.values()) {
            System.out.println("Testing with " + m.displayName());
            Properties P1 = m.loadFromXML(m.writeToXML(p1), null);
            Properties P2 = m.loadFromXML(m.writeToXML(p2), P1);
            Properties P3 = m.loadFromXML(m.writeToXML(p3), P2);

            testResults(m, p1, P1, p2, P2, p3, P3);

            // Now check that properties whose keys or values are objects
            // are skipped.

            System.out.println("Testing with " + m.displayName() + " and Objects");
            P1.put("p1.object.prop", Objects.OBJ1);
            P1.put(Objects.OBJ1, "p1.object.prop");
            P1.put("p2.object.prop", "p2.object.prop");
            P2.put("p2.object.prop", Objects.OBJ2);
            P2.put(Objects.OBJ2, "p2.object.prop");
            P3.put("p3.object.prop", Objects.OBJ3);
            P3.put(Objects.OBJ3, "p3.object.prop");

            Properties PP1 = m.loadFromXML(m.writeToXML(P1), null);
            Properties PP2 = m.loadFromXML(m.writeToXML(P2), PP1);
            Properties PP3 = m.loadFromXML(m.writeToXML(P3), PP2);

            p1.setProperty("p2.object.prop", "p2.object.prop");
            try {
                testResults(m, p1, PP1, p2, PP2, p3, PP3);
            } finally {
                p1.remove("p2.object.prop");
            }
        }
    }

    public static void testResults(StoreMethod m, Properties... pps) {
        for (int i=0 ; i < pps.length ; i += 2) {
            if (!pps[i].equals(pps[i+1])) {
                System.err.println(m.displayName() +": P" + (i/2+1)
                        + " Reloaded properties differ from original");
                System.err.println("\toriginal: " + pps[i]);
                System.err.println("\treloaded: " + pps[i+1]);
                throw new RuntimeException(m.displayName() +": P" + (i/2+1)
                        + " Reloaded properties differ from original");
            }
            if (!pps[i].keySet().equals(pps[i+1].keySet())) {
                System.err.println(m.displayName() +": P" + (i/2+1)
                        + " Reloaded property names differ from original");
                System.err.println("\toriginal: " + pps[i].keySet());
                System.err.println("\treloaded: " + pps[i+1].keySet());
                throw new RuntimeException(m.displayName() +": P" + (i/2+1)
                        + " Reloaded property names differ from original");
            }
            if (!pps[i].stringPropertyNames().equals(pps[i+1].stringPropertyNames())) {
                System.err.println(m.displayName() +": P" + (i/2+1)
                        + " Reloaded string property names differ from original");
                System.err.println("\toriginal: " + pps[i].stringPropertyNames());
                System.err.println("\treloaded: " + pps[i+1].stringPropertyNames());
                throw new RuntimeException(m.displayName() +": P" + (i/2+1)
                        + " Reloaded string property names differ from original");
            }
        }
    }

}
