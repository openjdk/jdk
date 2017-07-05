/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug 6750472 6752563
 * @summary Test CompositeDataSupport.toMap.
 * @author Eamonn McManus
 * @run main/othervm -ea CompositeDataToMapTest
 */

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;

public class CompositeDataToMapTest {
    private static class IdentityInvocationHandler implements InvocationHandler {
        private final Object wrapped;

        public IdentityInvocationHandler(Object wrapped) {
            this.wrapped = wrapped;
        }

        public Object invoke(Object proxy, Method m, Object[] args)
                throws Throwable {
            try {
                return m.invoke(wrapped, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    private static <T> T wrap(T x, Class<T> intf) {
        InvocationHandler ih = new IdentityInvocationHandler(x);
        return intf.cast(Proxy.newProxyInstance(
                intf.getClassLoader(), new Class<?>[] {intf}, ih));
    }

    public static void main(String[] args) throws Exception {
        if (!CompositeDataToMapTest.class.desiredAssertionStatus())
            throw new AssertionError("Must be run with -ea");

        CompositeType emptyCT = new CompositeType(
                "empty", "empty", new String[0], new String[0], new OpenType<?>[0]);
        CompositeData emptyCD = new CompositeDataSupport(
                emptyCT, Collections.<String, Object>emptyMap());
        assert CompositeDataSupport.toMap(emptyCD).isEmpty() :
            "Empty CD produces empty Map";

        CompositeData emptyCD2 = new CompositeDataSupport(
                emptyCT, new String[0], new Object[0]);
        assert emptyCD.equals(emptyCD2) : "Empty CD can be constructed two ways";

        CompositeType namedNumberCT = new CompositeType(
                "NamedNumber", "NamedNumber",
                new String[] {"name", "number"},
                new String[] {"name", "number"},
                new OpenType<?>[] {SimpleType.STRING, SimpleType.INTEGER});
        Map<String, Object> namedNumberMap = new HashMap<String, Object>();
        namedNumberMap.put("name", "Deich");
        namedNumberMap.put("number", 10);
        CompositeData namedNumberCD = new CompositeDataSupport(
                namedNumberCT, namedNumberMap);
        assert CompositeDataSupport.toMap(namedNumberCD).equals(namedNumberMap) :
            "Map survives passage through CompositeData";

        namedNumberCD = wrap(namedNumberCD, CompositeData.class);
        assert CompositeDataSupport.toMap(namedNumberCD).equals(namedNumberMap) :
            "Map survives passage through wrapped CompositeData";

        namedNumberMap = CompositeDataSupport.toMap(namedNumberCD);
        namedNumberMap.put("name", "Ceathar");
        namedNumberMap.put("number", 4);
        namedNumberCD = new CompositeDataSupport(namedNumberCT, namedNumberMap);
        assert CompositeDataSupport.toMap(namedNumberCD).equals(namedNumberMap) :
            "Modified Map survives passage through CompositeData";

        try {
            namedNumberMap = CompositeDataSupport.toMap(null);
            assert false : "Null toMap arg provokes exception";
        } catch (Exception e) {
            assert e instanceof IllegalArgumentException :
                "Exception for null toMap arg is IllegalArgumentException";
        }
    }
}
