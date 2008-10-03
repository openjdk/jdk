/*
 * Copyright 2005-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @bug 4716807
 * @summary Test the ObjectName.apply(ObjectName) method
 *          with wildcards in the key properties value part.
 * @author Luis-Miguel Alventosa
 * @run clean ApplyWildcardTest
 * @compile -XDignore.symbol.file=true ApplyWildcardTest.java
 * @run build ApplyWildcardTest
 * @run main ApplyWildcardTest
 */

import com.sun.jmx.mbeanserver.Repository;
import com.sun.jmx.mbeanserver.Util;
import javax.management.ObjectName;

public class ApplyWildcardTest {

    private static final String positiveTests[][] = {
        { "d:k=*", "d:k=\"\"" },

        { "d:k=*", "d:k=" },
        { "d:k=*", "d:k=v" },
        { "d:k=a*b", "d:k=axyzb" },
        { "d:k=a*b,*", "d:k=axyzb,k2=v2" },
        { "d:*,k=a*b", "d:k=axyzb,k2=v2" },
        { "d:k=?", "d:k=v" },
        { "d:k=a?b", "d:k=axb" },
        { "d:k=a?b,*", "d:k=axb,k2=v2" },
        { "d:*,k=a?b", "d:k=axb,k2=v2" },
        { "d:k=?*", "d:k=axyzb" },
        { "d:k=a?bc*d", "d:k=axbcyzd" },
        { "d:k=a?bc*d,*", "d:k=axbcyzd,k2=v2" },
        { "d:*,k=a?bc*d", "d:k=axbcyzd,k2=v2" },
        { "d:k1=?,k2=*", "d:k1=a,k2=ab" },
        { "d:k1=a?b,k2=c*d", "d:k1=axb,k2=cyzd" },
        { "d:k1=a?b,k2=c*d,*", "d:k1=axb,k2=cyzd,k3=v3" },
        { "d:*,k1=a?b,k2=c*d", "d:k1=axb,k2=cyzd,k3=v3" },

        { "d:k=\"*\"", "d:k=\"\"" },
        { "d:k=\"*\"", "d:k=\"v\"" },
        { "d:k=\"a*b\"", "d:k=\"axyzb\"" },
        { "d:k=\"a*b\",*", "d:k=\"axyzb\",k2=\"v2\"" },
        { "d:*,k=\"a*b\"", "d:k=\"axyzb\",k2=\"v2\"" },
        { "d:k=\"?\"", "d:k=\"v\"" },
        { "d:k=\"a?b\"", "d:k=\"axb\"" },
        { "d:k=\"a?b\",*", "d:k=\"axb\",k2=\"v2\"" },
        { "d:*,k=\"a?b\"", "d:k=\"axb\",k2=\"v2\"" },
        { "d:k=\"?*\"", "d:k=\"axyzb\"" },
        { "d:k=\"a?bc*d\"", "d:k=\"axbcyzd\"" },
        { "d:k=\"a?bc*d\",*", "d:k=\"axbcyzd\",k2=\"v2\"" },
        { "d:*,k=\"a?bc*d\"", "d:k=\"axbcyzd\",k2=\"v2\"" },
        { "d:k1=\"?\",k2=\"*\"", "d:k1=\"a\",k2=\"ab\"" },
        { "d:k1=\"a?b\",k2=\"c*d\"", "d:k1=\"axb\",k2=\"cyzd\"" },
        { "d:k1=\"a?b\",k2=\"c*d\",*", "d:k1=\"axb\",k2=\"cyzd\",k3=\"v3\"" },
        { "d:*,k1=\"a?b\",k2=\"c*d\"", "d:k1=\"axb\",k2=\"cyzd\",k3=\"v3\"" },

        // with namespaces

        { "*//:*", "d//:k=v" },
        { "//?:*", "///:k=v" },
        { "z*x//:*", "zaxcx//:k=v" },
        { "*//:*", "d/xx/q//:k=v" },
        { "z*x//:*", "z/a/x/c/x//:k=v" },
        { "*x?//:*", "dbdbdxk//:k=v" },
        { "z*x?x//:*", "zaxcx//:k=v" },
        { "*x?f//:*", "d/xxf/qxbf//:k=v" },
        { "z*x?c*x//:*", "z/a/x/c/x//:k=v" },

        { "*//*:*", "d/c/v//x/vgh/:k=v" },
        { "z*x//z*x:*", "zaxcx//zaxcxcx:k=v" },
        { "//*//:*", "//d/xx/q//:k=v" },
        { "z*//*//:*", "z/x/x/z//z/a/x/c/x//:k=v" },
        { "*x?//blur?g*:*", "dbdbdxk//blurhgblurgh/x/:k=v" },
        { "z*x??x//??:*", "zaxcxccx///.:k=v" },
        { "*x?f//?:*", "d/xxf/qxbf///:k=v" },
        { "z*x?c*x//*//z????//g:*", "z/a/x/c/x//gloubs/././/zargh//g:k=v" },
        { "z*x?c*x//*//:*", "z/a/x/c/x//gloubs/././/:k=v"},
        { "*//*//:*", "aza//bzb//:k=v" },
        { "*//:*", "aza//:k=v" },

        // with or without namespaces, * can also match nothing
        { "x*z:*", "xz:k=v"},

        { "*//:*", "//:k=v" },
        { "z*x//:*", "zx//:k=v" },
        { "*x?//:*", "xk//:k=v" },
        { "z*x?x//:*", "zxcx//:k=v" },
        { "*x?f//:*", "xbf//:k=v" },
        { "z*x?c*x//:*", "zx/cx//:k=v" },

        { "*//*:*", "//:k=v" },
        { "z*x//z*x:*", "zx//zx:k=v" },
        { "//*//:*", "////:k=v" },
        { "z*//*//:*", "z////:k=v" },
        { "*x?//blur?g*:*", "xk//blurhg:k=v" },
        { "z*x??x//??:*", "zxccx///.:k=v" },
        { "*x?f//?:*", "xbf///:k=v" },
        { "z*x?c*x//*//z????//g:*", "zx/cx////zargh//g:k=v" },
        { "z*x?c*x//*//:*", "zx/cx////:k=v"},
        { "*//*//:*", "////:k=v" },
        { "*//:*", "//:k=v" },

        // recursive namespace meta-wildcard
        {"**//D:k=v", "a//D:k=v"},
        {"**//D:k=v", "a//b//c//D:k=v"},
        {"a//**//D:k=v", "a//b//c//D:k=v"},
        {"a//**//d//D:k=v", "a//b//c//d//D:k=v"},
        {"a//**//d//D:k=v", "a//b//c//d//d//D:k=v"},
        {"a//**//d//D:k=v", "a//a//b//c//d//d//D:k=v"},
        {"a//**//d//**//e//D:k=v", "a//a//b//d//c//d//e//D:k=v"},

        // special cases for names ending with //
        { "*:*", "d//:k=v" },
        { "z*x*:*", "zaxcx//:k=v" },
        { "*:*", "d/xx/q//:k=v" },
        { "z*x??:*", "z/a/x/c/x//:k=v" },
        { "*x???:*", "dbdbdxk//:k=v" },
        { "z*x?c*x*:*", "z/a/x/c/x//:k=v" },
        { "?/*/?:*", "d/xx/q//:k=v" },
        { "**//*:*", "a//b//jmx.rmi:k=v"},
        { "**//*:*", "a//b//jmx.rmi//:k=v"},
        { "*//*:*", "wombat//:type=Wombat" },
        { "**//*:*", "jmx.rmi//:k=v"},

    };

    private static final String negativeTests[][] = {
        { "d:k=\"*\"", "d:k=" },

        { "d:k=*", "d:k=,k2=" },
        { "d:k=*", "d:k=v,k2=v2" },
        { "d:k=a*b", "d:k=axyzbc" },
        { "d:k=a*b,*", "d:k=axyzbc,k2=v2" },
        { "d:*,k=a*b", "d:k=axyzbc,k2=v2" },
        { "d:k=?", "d:k=xyz" },
        { "d:k=a?b", "d:k=ab" },
        { "d:k=a?b,*", "d:k=ab,k2=v2" },
        { "d:*,k=a?b", "d:k=ab,k2=v2" },
        { "d:k=?*", "d:k=axyzb,k2=v2" },
        { "d:k=a?bc*d", "d:k=abcd" },
        { "d:k=a?bc*d,*", "d:k=abcd,k2=v2" },
        { "d:*,k=a?bc*d", "d:k=abcd,k2=v2" },
        { "d:k1=?,k2=*", "d:k1=ab,k2=ab" },
        { "d:k1=a?b,k2=c*d", "d:k1=ab,k2=cd" },
        { "d:k1=a?b,k2=c*d,*", "d:k1=ab,k2=cd,k3=v3" },
        { "d:*,k1=a?b,k2=c*d", "d:k1=ab,k2=cd,k3=v3" },

        { "d:k=\"*\"", "d:k=\"\",k2=\"\"" },
        { "d:k=\"*\"", "d:k=\"v\",k2=\"v2\"" },
        { "d:k=\"a*b\"", "d:k=\"axyzbc\"" },
        { "d:k=\"a*b\",*", "d:k=\"axyzbc\",k2=\"v2\"" },
        { "d:*,k=\"a*b\"", "d:k=\"axyzbc\",k2=\"v2\"" },
        { "d:k=\"?\"", "d:k=\"xyz\"" },
        { "d:k=\"a?b\"", "d:k=\"ab\"" },
        { "d:k=\"a?b\",*", "d:k=\"ab\",k2=\"v2\"" },
        { "d:*,k=\"a?b\"", "d:k=\"ab\",k2=\"v2\"" },
        { "d:k=\"?*\"", "d:k=\"axyzb\",k2=\"v2\"" },
        { "d:k=\"a?bc*d\"", "d:k=\"abcd\"" },
        { "d:k=\"a?bc*d\",*", "d:k=\"abcd\",k2=\"v2\"" },
        { "d:*,k=\"a?bc*d\"", "d:k=\"abcd\",k2=\"v2\"" },
        { "d:k1=\"?\",k2=\"*\"", "d:k1=\"ab\",k2=\"ab\"" },
        { "d:k1=\"a?b\",k2=\"c*d\"", "d:k1=\"ab\",k2=\"cd\"" },
        { "d:k1=\"a?b\",k2=\"c*d\",*", "d:k1=\"ab\",k2=\"cd\",k3=\"v3\"" },
        { "d:*,k1=\"a?b\",k2=\"c*d\"", "d:k1=\"ab\",k2=\"cd\",k3=\"v3\"" },

        // with namespaces

        { "z*x?x*:*", "zaxcx//blougs:k=v" },
        { "*x?f??rata:*", "d/xxf/qxbf//rata:k=v" },
        { "z*x?c*x*b*:*", "z/a/x/c/x//b//:k=v" },

        { "*:*", "d/c/v//x/vgh/:k=v" },
        { "z*x??z*x:*", "zaxcx//zaxcxcx:k=v" },
        { "?/*/?:*", "//d/xx/q//:k=v" },
        { "z*/?*/?:*", "z/x/x/z//z/a/x/c/x//:k=v" },
        { "*x?/?blur?g*:*", "dbdbdxk//blurhgblurgh/x/:k=v" },
        { "z*x??x/???:*", "zaxcxccx///.:k=v" },
        { "*x?f?/?:*", "d/xxf/qxbf///:k=v" },
        { "z*x?c*x/?*z????*g:*", "z/a/x/c/x//gloubs/././/zargh//g:k=v" },

        // recursive namespace meta-wildcard
        {"**//D:k=v", "D:k=v"},
        {"b//**//D:k=v", "a//b//c//D:k=v"},
        {"a//**//D:k=v", "a//D:k=v"},
        {"a//**//d//D:k=v", "a//b//c//d//e//D:k=v"},
        {"a//**//d//D:k=v", "a//b//c//D:k=v"},
        {"a//**//d//D:k=v", "a//b//c//d//d//e//D:k=v"},
        {"a//**//d//**//e//D:k=v", "a//a//b//c//d//e//D:k=v"},
        {"a//**//d//**//e//D:k=v", "a//a//b//c//e//D:k=v"},
        { "**//*:*", "jmx.rmi:k=v"},

    };

    private static int runPositiveTests() {
        int error = 0;
        for (int i = 0; i < positiveTests.length; i++) {
            System.out.println("----------------------------------------------");
            try {
                ObjectName on1 = ObjectName.getInstance(positiveTests[i][0]);
                ObjectName on2 = ObjectName.getInstance(positiveTests[i][1]);
                System.out.println("\"" + on1 + "\".apply(\"" + on2 + "\")");
                boolean result = on1.apply(on2);
                System.out.println("Result = " + result);
                if (result == false) {
                    error++;
                    System.out.println("Test failed!");
                    throw new Error("test failed for "+
                            "\"" + on1 + "\".apply(\"" + on2 + "\")");
                } else {
                    System.out.println("Test passed!");
                }
            } catch (Exception e) {
                error++;
                System.out.println("Got Unexpected Exception = " + e.toString());
                System.out.println("Test failed!");
            }
            System.out.println("----------------------------------------------");
        }
        return error;
    }

    private static int runNegativeTests() {
        int error = 0;
        for (int i = 0; i < negativeTests.length; i++) {
            System.out.println("----------------------------------------------");
            try {
                ObjectName on1 = ObjectName.getInstance(negativeTests[i][0]);
                ObjectName on2 = ObjectName.getInstance(negativeTests[i][1]);
                System.out.println("\"" + on1 + "\".apply(\"" + on2 + "\")");
                boolean result = on1.apply(on2);
                System.out.println("Result = " + result);
                if (result == true) {
                    error++;
                    System.out.println("Test failed!");
                } else {
                    System.out.println("Test passed!");
                }
            } catch (Exception e) {
                error++;
                System.out.println("Got Unexpected Exception = " + e.toString());
                System.out.println("Test failed!");
            }
            System.out.println("----------------------------------------------");
        }
        return error;
    }

    private static int runRepositoryPositiveTests() {
        int error = 0;
        for (int i = 0; i < positiveTests.length; i++) {
            try {
                ObjectName on1 = ObjectName.getInstance(positiveTests[i][0]);
                ObjectName on2 = ObjectName.getInstance(positiveTests[i][1]);
                if (on1.isPropertyPattern()) {
                    if (!on1.getKeyPropertyListString().equals("")) continue;
                } else if (!on1.getCanonicalKeyPropertyListString()
                            .equals(on2.getCanonicalKeyPropertyListString())) {
                    continue;
                }
                System.out.println("Repository Positive Match Test ---------------");
                final String dom1 = on1.getDomain();
                final String dom2 = on2.getDomain();
                System.out.println("Util.wildpathmatch(\"" + dom2 + "\",\"" + dom1 + "\")");
                boolean result =
                        Util.wildpathmatch(dom2,dom1);
                System.out.println("Result = " + result);
                if (result == false) {
                    error++;
                    System.out.println("Test failed!");
                } else {
                    System.out.println("Test passed!");
                }
            } catch (Exception e) {
                error++;
                System.out.println("Got Unexpected Exception = " + e.toString());
                System.out.println("Test failed!");
            }
            System.out.println("----------------------------------------------");
        }
        return error;
    }

    private static int runRepositoryNegativeTests() {
        int error = 0;
        for (int i = 0; i < negativeTests.length; i++) {
            try {
                ObjectName on1 = ObjectName.getInstance(negativeTests[i][0]);
                ObjectName on2 = ObjectName.getInstance(negativeTests[i][1]);
                if (on1.isPropertyPattern()) {
                    if (!on1.getKeyPropertyListString().equals("")) continue;
                } else if (!on1.getCanonicalKeyPropertyListString()
                            .equals(on2.getCanonicalKeyPropertyListString())) {
                    continue;
                }
                System.out.println("Repository Negative Match Test ---------------");
                final String dom1 = on1.getDomain();
                final String dom2 = on2.getDomain();
                System.out.println("Util.wildpathmatch(\"" + dom2 + "\",\"" + dom1 + "\")");
                boolean result =
                        Util.wildpathmatch(dom2,dom1);
                System.out.println("Result = " + result);
                if (result == true) {
                    error++;
                    System.out.println("Test failed!");
                } else {
                    System.out.println("Test passed!");
                }
            } catch (Exception e) {
                error++;
                System.out.println("Got Unexpected Exception = " + e.toString());
                System.out.println("Test failed!");
            }
            System.out.println("----------------------------------------------");
        }
        return error;
    }

    public static void main(String[] args) throws Exception {


        int error = 0;

        if (!(new ObjectName("z*x*:*").apply(new ObjectName("zaxcx//:k=v"))))
                throw new Exception();


        // Check null values
        //
        System.out.println("----------------------------------------------");
        System.out.println("Test ObjectName.apply(null)");
        try {
            new ObjectName("d:k=v").apply(null);
            error++;
            System.out.println("Didn't get expected NullPointerException!");
            System.out.println("Test failed!");
        } catch (NullPointerException e) {
            System.out.println("Got expected exception '" + e.toString() + "'");
            System.out.println("Test passed!");
        } catch (Exception e) {
            error++;
            System.out.println("Got unexpected exception '" + e.toString() + "'");
            System.out.println("Test failed!");
        }
        System.out.println("----------------------------------------------");

        // Check domain pattern values
        //
        System.out.println("----------------------------------------------");
        System.out.println("Test ObjectName.apply(domain_pattern)");
        try {
            if (new ObjectName("d:k=v").apply(new ObjectName("*:k=v"))) {
                error++;
                System.out.println("Got 'true' expecting 'false'");
                System.out.println("Test failed!");
            } else {
                System.out.println("Got expected return value 'false'");
                System.out.println("Test passed!");
            }
        } catch (Exception e) {
            error++;
            System.out.println("Got unexpected exception = " + e.toString());
            System.out.println("Test failed!");
        }
        System.out.println("----------------------------------------------");

        // Check key property list pattern values
        //
        System.out.println("----------------------------------------------");
        System.out.println("Test ObjectName.apply(key_property_list_pattern)");
        try {
            if (new ObjectName("d:k=v").apply(new ObjectName("d:k=v,*"))) {
                error++;
                System.out.println("Got 'true' expecting 'false'");
                System.out.println("Test failed!");
            } else {
                System.out.println("Got expected return value 'false'");
                System.out.println("Test passed!");
            }
        } catch (Exception e) {
            error++;
            System.out.println("Got unexpected exception = " + e.toString());
            System.out.println("Test failed!");
        }
        System.out.println("----------------------------------------------");

        // Check key property value pattern values
        //
        System.out.println("----------------------------------------------");
        System.out.println("Test ObjectName.apply(key_property_value_pattern)");
        try {
            if (new ObjectName("d:k=v").apply(new ObjectName("d:k=*"))) {
                error++;
                System.out.println("Got 'true' expecting 'false'");
                System.out.println("Test failed!");
            } else {
                System.out.println("Got expected return value 'false'");
                System.out.println("Test passed!");
            }
        } catch (Exception e) {
            error++;
            System.out.println("Got unexpected exception = " + e.toString());
            System.out.println("Test failed!");
        }
        System.out.println("----------------------------------------------");

        error += runPositiveTests();
        error += runNegativeTests();
        System.out.println("----------------------------------------------");
        error += runRepositoryPositiveTests();
        System.out.println("----------------------------------------------");
        error += runRepositoryNegativeTests();

        if (error > 0) {
            final String msg = "Test FAILED! Got " + error + " error(s)";
            System.out.println(msg);
            throw new IllegalArgumentException(msg);
        } else {
            System.out.println("Test PASSED!");
        }
    }
}
