/*
 * Copyright 2003-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @summary Test that SnmpTimeTicks wraps around when it is passed a long
 *          value
 * @bug     4955105
 * @build   TimeTicksWrapping
 * @run     main TimeTicksWrapping
 */
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class TimeTicksWrapping {
    // We use an SnmpTimeticksBuilder in order to adapt this test case to a
    // configuration where the SNMP packages are not present in rt.jar.
    //
    public static final class SnmpTimeticksBuilder {
        public static final long   MAX_VALUE = 0x0ffffffffL;
        public static final String SNMP_TIME_TICKS_CLASS_NAME =
            "com.sun.jmx.snmp.SnmpTimeticks";
        private static final Class<?> SNMP_TIME_TICKS_CLASS;
        private static final Constructor<?> SNMP_long_CTOR;
        private static final Constructor<?> SNMP_LONG_CTOR;
        private static final Method SNMP_LONG_VALUE;
        static {
            Class<?> snmpTimeTicksClass;
            try {
                snmpTimeTicksClass =
                    Class.forName(SNMP_TIME_TICKS_CLASS_NAME, true, null);
            } catch (ClassNotFoundException x) {
                snmpTimeTicksClass = null;
                System.err.println("WARNING: can't load "+
                        SNMP_TIME_TICKS_CLASS_NAME);
            } catch (NoClassDefFoundError x) {
                snmpTimeTicksClass = null;
                System.err.println("WARNING: can't load "+
                        SNMP_TIME_TICKS_CLASS_NAME);
            }
            SNMP_TIME_TICKS_CLASS = snmpTimeTicksClass;
            if (SNMP_TIME_TICKS_CLASS != null) {
                try {
                  SNMP_long_CTOR =
                          SNMP_TIME_TICKS_CLASS.getConstructor(long.class);
                } catch (Exception x) {
                    throw new ExceptionInInitializerError(x);
                }
            } else {
                SNMP_long_CTOR = null;
            }
            if (SNMP_TIME_TICKS_CLASS != null) {
                try {
                  SNMP_LONG_CTOR =
                          SNMP_TIME_TICKS_CLASS.getConstructor(Long.class);
                } catch (Exception x) {
                    throw new ExceptionInInitializerError(x);
                }
            } else {
                SNMP_LONG_CTOR = null;
            }
            if (SNMP_TIME_TICKS_CLASS != null) {
                try {
                  SNMP_LONG_VALUE =
                          SNMP_TIME_TICKS_CLASS.getMethod("longValue");
                } catch (Exception x) {
                    throw new ExceptionInInitializerError(x);
                }
            } else {
                SNMP_LONG_VALUE = null;
            }

        }

        private final Object timeticks;

        public SnmpTimeticksBuilder(long ticks) throws Exception {
            timeticks = newSnmpTimeticks(ticks);
        }
        public SnmpTimeticksBuilder(Long ticks) throws Exception {
            timeticks = newSnmpTimeticks(ticks);
        }
        public long longValue() throws Exception {
            return longValue(timeticks);
        }

        public static boolean isSnmpPresent() {
            System.out.println(TimeTicksWrapping.class.getName()+
                    ": Testing for SNMP Packages...");
            return SNMP_TIME_TICKS_CLASS != null;
        }

        private static Object newSnmpTimeticks(long time)
                throws Exception {
            try {
                return SNMP_long_CTOR.newInstance(time);
            } catch (InvocationTargetException x) {
                final Throwable cause = x.getCause();
                if (cause instanceof Exception) throw (Exception) cause;
                if (cause instanceof Error) throw (Error) cause;
                throw x;
            }
        }

        private static Object newSnmpTimeticks(Long time)
            throws Exception {
            try {
                return SNMP_LONG_CTOR.newInstance(time);
            } catch (InvocationTargetException x) {
                final Throwable cause = x.getCause();
                if (cause instanceof Exception) throw (Exception) cause;
                if (cause instanceof Error) throw (Error) cause;
                throw x;
            }
        }

        private static long longValue(Object o)
                throws Exception {
            try {
                return ((Long)SNMP_LONG_VALUE.invoke(o)).longValue();
            } catch (InvocationTargetException x) {
                final Throwable cause = x.getCause();
                if (cause instanceof Exception) throw (Exception) cause;
                if (cause instanceof Error) throw (Error) cause;
                throw x;
            }
        }

    }

    public static final long[] oks = {
        0L, 1L, (long)Integer.MAX_VALUE, (long)Integer.MAX_VALUE*2,
        (long)Integer.MAX_VALUE*2+1L, (long)Integer.MAX_VALUE*2+2L,
        (long)Integer.MAX_VALUE*3,
        SnmpTimeticksBuilder.MAX_VALUE, SnmpTimeticksBuilder.MAX_VALUE+1L,
        SnmpTimeticksBuilder.MAX_VALUE*3-1L, Long.MAX_VALUE
    };

    public static final long[] kos = {
        -1L, (long)Integer.MIN_VALUE, (long)Integer.MIN_VALUE*2,
        (long)Integer.MIN_VALUE*2-1L, (long)Integer.MIN_VALUE*3,
        -SnmpTimeticksBuilder.MAX_VALUE, -(SnmpTimeticksBuilder.MAX_VALUE+1L),
        -(SnmpTimeticksBuilder.MAX_VALUE*3-1L), Long.MIN_VALUE
    };


    public static void main(String args[]) {
        if (!SnmpTimeticksBuilder.isSnmpPresent()) {
            System.err.println("WARNING: "+
                    SnmpTimeticksBuilder.SNMP_TIME_TICKS_CLASS_NAME+
                    " not present.");
            System.err.println(TimeTicksWrapping.class.getName()+
                    ": test skipped.");
            return;
        }
        try {
            SnmpTimeticksBuilder t = null;

            for (int i=0;i<oks.length;i++) {
                final long t1,t2,t3;
                t1 = (new SnmpTimeticksBuilder(oks[i])).longValue();
                t2 = (new SnmpTimeticksBuilder(new Long(oks[i]))).longValue();
                t3 = oks[i]%0x0100000000L;
                if (t1 != t3)
                    throw new Exception("Value should have wrapped: " +
                                        oks[i] + " expected: " + t3);
                if (t2 != t3)
                    throw new Exception("Value should have wrapped: " +
                                        "Long("+oks[i]+") expected: " + t3);

                if (t1 > SnmpTimeticksBuilder.MAX_VALUE)
                    throw new Exception("Value should have wrapped " +
                                        "for " + oks[i] + ": " +
                                        t1 + " exceeds max: " +
                                        SnmpTimeticksBuilder.MAX_VALUE);
                if (t2 > SnmpTimeticksBuilder.MAX_VALUE)
                    throw new Exception("Value should have wrapped " +
                                        "for " + oks[i] + ": " +
                                        t2 + " exceeds max: " +
                                        SnmpTimeticksBuilder.MAX_VALUE);

                if (t1 < 0)
                    throw new Exception("Value should have wrapped: " +
                                        "for " + oks[i] + ": " +
                                        t1 + " is negative");
                if (t2 < 0)
                    throw new Exception("Value should have wrapped: " +
                                        "for " + oks[i] + ": " +
                                        t2 + " is negative");

                System.out.println("TimeTicks[" + oks[i] +
                                   "] rightfully accepted: " + t3);
            }

            for (int i=0;i<kos.length;i++) {
                try {
                    t = new SnmpTimeticksBuilder(kos[i]);
                    throw new Exception("Value should have been rejected: " +
                                        kos[i]);
                } catch (IllegalArgumentException x) {
                    // OK!
                }
                try {
                    t = new SnmpTimeticksBuilder(new Long(kos[i]));
                    throw new Exception("Value should have been rejected: " +
                                        "Long("+kos[i]+")");
                } catch (IllegalArgumentException x) {
                    // OK!
                }

                System.out.println("TimeTicks[" + kos[i] +
                                   "] rightfully rejected.");
            }

        } catch(Exception x) {
            x.printStackTrace();
            System.exit(1);
        }
    }
}
