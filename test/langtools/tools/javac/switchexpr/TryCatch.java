/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8214114
 * @summary Verify try-catch inside a switch expression works properly.
 * @compile --enable-preview -source 13 TryCatch.java
 * @run main/othervm --enable-preview TryCatch
 */
public class TryCatch {
    public static void main(String[] args) {
        {
            int val = 3;
            for (int p : new int[] {0, 1, 2}) {
                int res = 1 + new TryCatch().id(switch(p) {
                    case 0 -> switch (p + 1) {
                        case 1:
                            try {
                                new TryCatch().throwException();
                                break -1;
                            } catch(Throwable ex) {
                                break val;
                            }
                        default: break -1;
                    };
                    case 1 -> {
                        try {
                            break new TryCatch().id(switch (p + 1) {
                                case 2:
                                    try {
                                        new TryCatch().throwException();
                                        break -1;
                                    } catch(Throwable ex) {
                                        throw ex;
                                    }
                                default: break -1;
                            });
                        } catch(Throwable ex) {
                            break val;
                        }
                    }
                    default -> {
                        try {
                            new TryCatch().throwException();
                            break -1;
                        } catch(Throwable ex) {
                            break val;
                        }
                    }
                } - 1);
                if (res != 3) {
                    throw new AssertionError("Unexpected result: " + res);
                }
            }
        }
        {
            int val = 3;
            for (int p : new int[] {0, 1, 2}) {
                int x;
                int res = new TryCatch().id(val == 3 && switch(p) {
                    case 0 -> switch (p + 1) {
                        case 1:
                            try {
                                new TryCatch().throwException();
                                break false;
                            } catch(Throwable ex) {
                                break true;
                            }
                        default: break false;
                    };
                    case 1 -> {
                        try {
                            break new TryCatch().id(switch (p + 1) {
                                case 2:
                                    try {
                                        new TryCatch().throwException();
                                        break false;
                                    } catch(Throwable ex) {
                                        throw ex;
                                    }
                                default: break false;
                            });
                        } catch(Throwable ex) {
                            break true;
                        }
                    }
                    default -> {
                        try {
                            new TryCatch().throwException();
                            break false;
                        } catch(Throwable ex) {
                            break true;
                        }
                    }
                } && (x = 1) == 1 && x == 1 ? val : -1);
                if (res != 3) {
                    throw new AssertionError("Unexpected result: " + res);
                }
            }
        }
        {
            int val = 3;
            for (E e : new E[] {E.A, E.B, E.C}) {
                int res = 1 + new TryCatch().id(switch(e) {
                    case A -> switch (e.next()) {
                        case B:
                            try {
                                new TryCatch().throwException();
                                break -1;
                            } catch(Throwable ex) {
                                break val;
                            }
                        default: break -1;
                    };
                    case B -> {
                        try {
                            break new TryCatch().id(switch (e.next()) {
                                case C:
                                    try {
                                        new TryCatch().throwException();
                                        break -1;
                                    } catch(Throwable ex) {
                                        throw ex;
                                    }
                                default: break -1;
                            });
                        } catch(Throwable ex) {
                            break val;
                        }
                    }
                    default -> {
                        try {
                            new TryCatch().throwException();
                            break -1;
                        } catch(Throwable ex) {
                            break val;
                        }
                    }
                } - 1);
                if (res != 3) {
                    throw new AssertionError("Unexpected result: " + res);
                }
            }
        }
        {
            int val = 3;
            for (E e : new E[] {E.A, E.B, E.C}) {
                int x;
                int res = new TryCatch().id(val == 3 && switch(e) {
                    case A -> switch (e.next()) {
                        case B:
                            try {
                                new TryCatch().throwException();
                                break false;
                            } catch(Throwable ex) {
                                break true;
                            }
                        default: break false;
                    };
                    case B -> {
                        try {
                            break new TryCatch().id(switch (e.next()) {
                                case C:
                                    try {
                                        new TryCatch().throwException();
                                        break false;
                                    } catch(Throwable ex) {
                                        throw ex;
                                    }
                                default: break false;
                            });
                        } catch(Throwable ex) {
                            break true;
                        }
                    }
                    default -> {
                        try {
                            new TryCatch().throwException();
                            break false;
                        } catch(Throwable ex) {
                            break true;
                        }
                    }
                } && (x = 1) == 1 && x == 1 ? val : -1);
                if (res != 3) {
                    throw new AssertionError("Unexpected result: " + res);
                }
            }
        }
        {
            int val = 3;
            for (String s : new String[] {"", "a", "b"}) {
                int res = 1 + new TryCatch().id(switch(s) {
                    case "" -> switch (s + "c") {
                        case "c":
                            try {
                                new TryCatch().throwException();
                                break -1;
                            } catch(Throwable ex) {
                                break val;
                            }
                        default: break -1;
                    };
                    case "a" -> {
                        try {
                            break new TryCatch().id(switch (s + "c") {
                                case "ac":
                                    try {
                                        new TryCatch().throwException();
                                        break -1;
                                    } catch(Throwable ex) {
                                        throw ex;
                                    }
                                default: break -1;
                            });
                        } catch(Throwable ex) {
                            break val;
                        }
                    }
                    default -> {
                        try {
                            new TryCatch().throwException();
                            break -1;
                        } catch(Throwable ex) {
                            break val;
                        }
                    }
                } - 1);
                if (res != 3) {
                    throw new AssertionError("Unexpected result: " + res);
                }
            }
        }
        {
            int val = 3;
            for (String s : new String[] {"", "a", "b"}) {
                int x;
                int res = new TryCatch().id(val == 3 && switch(s) {
                    case "" -> switch (s + "c") {
                        case "c":
                            try {
                                new TryCatch().throwException();
                                break false;
                            } catch(Throwable ex) {
                                break true;
                            }
                        default: break false;
                    };
                    case "a" -> {
                        try {
                            break new TryCatch().id(switch (s + "c") {
                                case "ac":
                                    try {
                                        new TryCatch().throwException();
                                        break false;
                                    } catch(Throwable ex) {
                                        throw ex;
                                    }
                                default: break false;
                            });
                        } catch(Throwable ex) {
                            break true;
                        }
                    }
                    default -> {
                        try {
                            new TryCatch().throwException();
                            break false;
                        } catch(Throwable ex) {
                            break true;
                        }
                    }
                } && (x = 1) == 1 && x == 1 ? val : -1);
                if (res != 3) {
                    throw new AssertionError("Unexpected result: " + res);
                }
            }
        }

        {
            int res = new FieldHolder().intTest;

            if (res != 3) {
                throw new AssertionError("Unexpected result: " + res);
            }
        }
        {
            int res = FieldHolder.intStaticTest;

            if (res != 3) {
                throw new AssertionError("Unexpected result: " + res);
            }
        }
        {
            boolean res = new FieldHolder().booleanTest;

            if (!res) {
                throw new AssertionError("Unexpected result: " + res);
            }
        }
        {
            boolean res = FieldHolder.booleanStaticTest;

            if (!res) {
                throw new AssertionError("Unexpected result: " + res);
            }
        }
    }

    static class FieldHolder {
        private final int intTest = switch (0) {
            case -1: break -1;
            default:
                try {
                    break new TryCatch().id(switch (2) {
                        case 2:
                            try {
                                new TryCatch().throwException();
                                break -1;
                            } catch(Throwable ex) {
                                throw ex;
                            }
                        default: break -1;
                    });
                } catch(Throwable ex) {
                    break 3;
                }
        };
        private static final int intStaticTest = switch (0) {
            case -1: break -1;
            default:
                try {
                    break new TryCatch().id(switch (2) {
                        case 2:
                            try {
                                new TryCatch().throwException();
                                break -1;
                            } catch(Throwable ex) {
                                throw ex;
                            }
                        default: break -1;
                    });
                } catch(Throwable ex) {
                    break 3;
                }
        };
        private final boolean booleanTest = switch (0) {
            case -1: break false;
            default:
                try {
                    break new TryCatch().id(switch (2) {
                        case 2:
                            try {
                                new TryCatch().throwException();
                                break false;
                            } catch(Throwable ex) {
                                throw ex;
                            }
                        default: break false;
                    });
                } catch(Throwable ex) {
                    break true;
                }
        };
        private static final boolean booleanStaticTest = switch (0) {
            case -1: break false;
            default:
                try {
                    break new TryCatch().id(switch (2) {
                        case 2:
                            try {
                                new TryCatch().throwException();
                                break false;
                            } catch(Throwable ex) {
                                throw ex;
                            }
                        default: break false;
                    });
                } catch(Throwable ex) {
                    break true;
                }
        };
    }

    private int id(int i) {
        return i;
    }

    private boolean id(boolean b) {
        return b;
    }

    private void throwException() {
        throw new RuntimeException();
    }
    enum E {
        A, B, C;
        public E next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }
}
