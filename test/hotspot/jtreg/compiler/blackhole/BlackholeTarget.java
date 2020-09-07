/*
 * Copyright (c) 2020, Red Hat, Inc. All rights reserved.
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

package compiler.blackhole;

import java.lang.reflect.*;

public class BlackholeTarget {
    private static String entered;

    private static void registerEntered(String label) {
        if (entered == null) {
           entered = label;
        } else if (!entered.equals(label)) {
           throw new IllegalStateException("Trying to register enter with overwrite: " + entered + " -> " + label);
        }
    }

    public static void clear() {
        entered = null;
    }

    public static void shouldBeEntered() {
        if (entered == null) {
            throw new IllegalStateException("Should have been entered");
        }
    }

    public static void shouldNotBeEntered() {
        if (entered != null) {
            throw new IllegalStateException("Should not have been entered: " + entered);
        }
    }

    public static void    bh_s_boolean(boolean v)  { registerEntered("bh_s_boolean"); }
    public static void    bh_s_byte(byte v)        { registerEntered("bh_s_byte");    }
    public static void    bh_s_short(short v)      { registerEntered("bh_s_short");   }
    public static void    bh_s_char(char v)        { registerEntered("bh_s_char");    }
    public static void    bh_s_int(int v)          { registerEntered("bh_s_int");     }
    public static void    bh_s_float(float v)      { registerEntered("bh_s_float");   }
    public static void    bh_s_long(long v)        { registerEntered("bh_s_long");    }
    public static void    bh_s_double(double v)    { registerEntered("bh_s_double");  }
    public static void    bh_s_Object(Object v)    { registerEntered("bh_s_Object");  }

    public        void    bh_i_boolean(boolean v)  { registerEntered("bh_i_boolean"); }
    public        void    bh_i_byte(byte v)        { registerEntered("bh_i_byte");    }
    public        void    bh_i_short(short v)      { registerEntered("bh_i_short");   }
    public        void    bh_i_char(char v)        { registerEntered("bh_i_char");    }
    public        void    bh_i_int(int v)          { registerEntered("bh_i_int");     }
    public        void    bh_i_float(float v)      { registerEntered("bh_i_float");   }
    public        void    bh_i_long(long v)        { registerEntered("bh_i_long");    }
    public        void    bh_i_double(double v)    { registerEntered("bh_i_double");  }
    public        void    bh_i_Object(Object v)    { registerEntered("bh_i_Object");  }

    public static boolean bh_sr_boolean(boolean v) { registerEntered("bh_sr_boolean"); return false; }
    public static byte    bh_sr_byte(byte v)       { registerEntered("bh_sr_byte");    return 0;     }
    public static short   bh_sr_short(short v)     { registerEntered("bh_sr_short");   return 0;     }
    public static char    bh_sr_char(char v)       { registerEntered("bh_sr_char");    return 0;     }
    public static int     bh_sr_int(int v)         { registerEntered("bh_sr_int");     return 0;     }
    public static float   bh_sr_float(float v)     { registerEntered("bh_sr_float");   return 0;     }
    public static long    bh_sr_long(long v)       { registerEntered("bh_sr_long");    return 0;     }
    public static double  bh_sr_double(double v)   { registerEntered("bh_sr_double");  return 0;     }
    public static Object  bh_sr_Object(Object v)   { registerEntered("bh_sr_Object");  return null;  }

    public        boolean bh_ir_boolean(boolean v) { registerEntered("bh_ir_boolean"); return false; }
    public        byte    bh_ir_byte(byte v)       { registerEntered("bh_ir_byte");    return 0;     }
    public        short   bh_ir_short(short v)     { registerEntered("bh_ir_short");   return 0;     }
    public        char    bh_ir_char(char v)       { registerEntered("bh_ir_char");    return 0;     }
    public        int     bh_ir_int(int v)         { registerEntered("bh_ir_int");     return 0;     }
    public        float   bh_ir_float(float v)     { registerEntered("bh_ir_float");   return 0;     }
    public        long    bh_ir_long(long v)       { registerEntered("bh_ir_long");    return 0;     }
    public        double  bh_ir_double(double v)   { registerEntered("bh_ir_double");  return 0;     }
    public        Object  bh_ir_Object(Object v)   { registerEntered("bh_ir_Object");  return null;  }
}
