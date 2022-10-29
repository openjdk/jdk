/*
 * Copyright (c) 2021, Google LLC. All rights reserved.
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

import java.io.Serializable;

public class InnerClasses {

    public Class<?> localCapturesParameter(final int x) {
        class Local {
            public void f() {
                System.err.println(x);
            }
        }
        return Local.class;
    }

    public Class<?> localCapturesLocal() {
        final int x = 0;
        class Local {
            public void f() {
                System.err.println(x);
            }
        }
        return Local.class;
    }

    public Class<?> localCapturesEnclosing() {
        class Local {
            public void f() {
                System.err.println(InnerClasses.this);
            }
        }
        return Local.class;
    }

    public Class<?> anonCapturesParameter(final int x) {
        return new Object() {
            public void f() {
                System.err.println(x);
            }
        }.getClass();
    }

    public Class<?> anonCapturesLocal() {
        final int x = 0;
        return new Object() {
            public void f() {
                System.err.println(x);
            }
        }.getClass();
    }

    public Class<?> anonCapturesEnclosing() {
        return new Object() {
            public void f() {
                System.err.println(InnerClasses.this);
            }
        }.getClass();
    }

    public static class StaticMemberClass {}

    public class NonStaticMemberClass {}

    public class NonStaticMemberClassCapturesEnclosing {
        public void f() {
            System.err.println(InnerClasses.this);
        }
    }

    static class N0 {
        int x;

        class N1 {
            class N2 {
                class N3 {
                    void f() {
                        System.err.println(x);
                    }

                    class N4 {
                        class N5 {}
                    }
                }
            }
        }
    }

    class SerializableCapture implements Serializable {
      void f() {
        System.err.println(InnerClasses.this);
      }
    }

    class SerializableWithSerialVersionUID implements Serializable {
      private static final long serialVersionUID = 0;
    }

    class SerializableWithInvalidSerialVersionUIDType implements Serializable {
      private static final int serialVersionUID = 0;
    }

    class SerializableWithInvalidSerialVersionUIDNonFinal implements Serializable {
      private static long serialVersionUID = 0;
    }

    class SerializableWithInvalidSerialVersionUIDNonStatic implements Serializable {
      private final long serialVersionUID = 0;
    }
}
