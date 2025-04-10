/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8214031 8214114 8236546 8353565
 * @summary Verify switch expressions embedded in various statements work properly.
 * @compile ExpressionSwitchEmbedding.java
 * @run main ExpressionSwitchEmbedding
 */

public class ExpressionSwitchEmbedding {
    public static void main(String... args) {
        new ExpressionSwitchEmbedding().run();
        new ExpressionSwitchEmbedding().runStackMapMergingTest();
    }

    private void run() {
        {
            int i = 6;
            int o = 0;
            while (switch (i) {
                case 1: i = 0; yield true;
                case 2: i = 1; yield true;
                case 3, 4: i--;
                    if (i == 2 || i == 4) {
                        yield switch (i) {
                            case 2 -> true;
                            case 4 -> false;
                            default -> throw new IllegalStateException();
                        };
                    } else {
                        yield true;
                    }
                default: i--; yield switch (i) {
                    case -1 -> false;
                    case 3 -> true;
                    default -> true;
                };
            }) {
                o++;
            }
            if (o != 6 && i >= 0) {
                throw new IllegalStateException();
            }
        }
        {
            int i = 6;
            int o = 0;
            while (switch (i) {
                case 1: try { new ExpressionSwitchEmbedding().throwException(); } catch (Throwable t) { i = 0; yield true; }
                case 2: try { new ExpressionSwitchEmbedding().throwException(); } catch (Throwable t) { i = 1; yield true; }
                case 3, 4:
                    try {
                        new ExpressionSwitchEmbedding().throwException();
                    } catch (Throwable t) {
                        i--;
                        if (i == 2 || i == 4) {
                            try {
                                yield switch (i) {
                                    case 2 -> throw new ResultException(true);
                                    case 4 -> false;
                                    default -> throw new IllegalStateException();
                                };
                            } catch (ResultException ex) {
                                yield ex.result;
                            }
                        } else {
                            yield true;
                        }
                    }
                default:
                    try {
                        new ExpressionSwitchEmbedding().throwException();
                    } catch (Throwable t) {
                        i--;
                        yield switch (i) {
                            case -1 -> false;
                            case 3 -> true;
                            default -> true;
                        };
                    }
                    throw new AssertionError();
            }) {
                o++;
            }
            if (o != 6 && i >= 0) {
                throw new IllegalStateException();
            }
        }
        {
            int i = 6;
            int o = 0;
            if (switch (i) {
                case 1: i = 0; yield true;
                case 2: i = 1; yield true;
                case 3, 4: i--;
                    if (i == 2 || i == 4) {
                        yield (switch (i) {
                            case 2 -> 3;
                            case 4 -> 5;
                            default -> throw new IllegalStateException();
                        }) == i + 1;
                    } else {
                        yield true;
                    }
                default: i--; yield switch (i) {
                    case -1 -> false;
                    case 3 -> true;
                    default -> true;
                };
            }) {
                o++;
            }
            if (o != 1 && i != 5) {
                throw new IllegalStateException();
            }
        }
        {
            int i = 6;
            int o = 0;
            if (switch (i) {
                case 1: try { new ExpressionSwitchEmbedding().throwException(); } catch (Throwable t) { i = 0; yield true; }
                case 2: try { new ExpressionSwitchEmbedding().throwException(); } catch (Throwable t) { i = 1; yield true; }
                case 3, 4:
                    try {
                        new ExpressionSwitchEmbedding().throwException();
                    } catch (Throwable t) {
                        i--;
                        if (i == 2 || i == 4) {
                            try {
                                yield switch (i) {
                                    case 2 -> throw new ResultException(true);
                                    case 4 -> false;
                                    default -> throw new IllegalStateException();
                                };
                            } catch (ResultException ex) {
                                yield ex.result;
                            }
                        } else {
                            yield true;
                        }
                    }
                default:
                    try {
                        new ExpressionSwitchEmbedding().throwException();
                    } catch (Throwable t) {
                        i--;
                        yield switch (i) {
                            case -1 -> false;
                            case 3 -> true;
                            default -> true;
                        };
                    }
                    throw new AssertionError();
            }) {
                o++;
            }
            if (o != 1 && i != 5) {
                throw new IllegalStateException();
            }
        }
        {
            int o = 0;
            for (int i = 6; (switch (i) {
                case 1: i = 0; yield true;
                case 2: i = 1; yield true;
                case 3, 4: i--;
                    if (i == 2 || i == 4) {
                        yield switch (i) {
                            case 2 -> true;
                            case 4 -> false;
                            default -> throw new IllegalStateException();
                        };
                    } else {
                        yield true;
                    }
                default: i--; yield switch (i) {
                    case -1 -> false;
                    case 3 -> true;
                    default -> true;
                };
            }); ) {
                o++;
            }
            if (o != 6) {
                throw new IllegalStateException();
            }
        }
        {
            int o = 0;
            for (int i = 6; (switch (i) {
                case 1: try { new ExpressionSwitchEmbedding().throwException(); } catch (Throwable t) { i = 0; yield true; }
                case 2: try { new ExpressionSwitchEmbedding().throwException(); } catch (Throwable t) { i = 1; yield true; }
                case 3, 4:
                    try {
                        new ExpressionSwitchEmbedding().throwException();
                    } catch (Throwable t) {
                        i--;
                        if (i == 2 || i == 4) {
                            try {
                                yield switch (i) {
                                    case 2 -> throw new ResultException(true);
                                    case 4 -> false;
                                    default -> throw new IllegalStateException();
                                };
                            } catch (ResultException ex) {
                                yield ex.result;
                            }
                        } else {
                            yield true;
                        }
                    }
                default:
                    try {
                        new ExpressionSwitchEmbedding().throwException();
                    } catch (Throwable t) {
                        i--;
                        yield switch (i) {
                            case -1 -> false;
                            case 3 -> true;
                            default -> true;
                        };
                    }
                    throw new AssertionError();
            }); ) {
                o++;
            }
            if (o != 6) {
                throw new IllegalStateException();
            }
        }
        {
            int i = 6;
            int o = 0;
            do {
                o++;
            } while (switch (i) {
                case 1: i = 0; yield true;
                case 2: i = 1; yield true;
                case 3, 4: i--;
                    if (i == 2 || i == 4) {
                        yield switch (i) {
                            case 2 -> true;
                            case 4 -> false;
                            default -> throw new IllegalStateException();
                        };
                    } else {
                        yield true;
                    }
                default: i--; yield switch (i) {
                    case -1 -> false;
                    case 3 -> true;
                    default -> true;
                };
            });
            if (o != 6 && i >= 0) {
                throw new IllegalStateException();
            }
        }
        {
            int i = 6;
            int o = 0;
            do {
                o++;
            } while (switch (i) {
                case 1: try { new ExpressionSwitchEmbedding().throwException(); } catch (Throwable t) { i = 0; yield true; }
                case 2: try { new ExpressionSwitchEmbedding().throwException(); } catch (Throwable t) { i = 1; yield true; }
                case 3, 4:
                    try {
                        new ExpressionSwitchEmbedding().throwException();
                    } catch (Throwable t) {
                        i--;
                        if (i == 2 || i == 4) {
                            try {
                                yield switch (i) {
                                    case 2 -> throw new ResultException(true);
                                    case 4 -> false;
                                    default -> throw new IllegalStateException();
                                };
                            } catch (ResultException ex) {
                                yield ex.result;
                            }
                        } else {
                            yield true;
                        }
                    }
                default:
                    try {
                        new ExpressionSwitchEmbedding().throwException();
                    } catch (Throwable t) {
                        i--;
                        yield switch (i) {
                            case -1 -> false;
                            case 3 -> true;
                            default -> true;
                        };
                    }
                    throw new AssertionError();
            });
            if (o != 6 && i >= 0) {
                throw new IllegalStateException();
            }
        }
        {
            String s = "";
            Object o = switch (s) { default -> s != null && s == s; };
            if (!(Boolean) o) {
                throw new IllegalStateException();
            }
        }
    }

    private void runStackMapMergingTest() {
        //JDK-8353565: verify that two types neither of which is a subtype of the other
        //can be merged while computing StackMaps.
        if (!(computeTypeAtMergePoint1(E.A, E.A) instanceof Impl1a)) {
            throw new AssertionError("Unexpected result");
        }
        if (runMethodForInterfaceTypeAtMergePoint1(E.A, E.A) != 1) {
            throw new AssertionError("Unexpected result");
        }
        if (runMethodForInterfaceTypeAtMergePoint2(E.A, E.A) != 2) {
            throw new AssertionError("Unexpected result");
        }
    }

    private Root computeTypeAtMergePoint1(E e1, E e2) {
        return (Root) switch (e1) {
            case A -> switch (e2) {
                case A -> new Impl1a();
                case B -> new Impl1b();
                case C -> new Impl1c();
            };
            case B -> switch (e2) {
                case A -> new Impl2a();
                case B -> new Impl2b();
                case C -> new Impl2c();
            };
            case C -> switch (e2) {
                case A -> new Impl3a();
                case B -> new Impl3b();
                case C -> new Impl3c();
            };
        };
    }

    private int runMethodForInterfaceTypeAtMergePoint1(E e1, E e2) {
        return (switch (e1) {
            case A -> switch (e2) {
                case A -> new C1();
                case B -> new C1();
                case C -> new C1();
            };
            case B -> switch (e2) {
                case A -> new C2();
                case B -> new C2();
                case C -> new C2();
            };
            case C -> switch (e2) {
                case A -> new C3();
                case B -> new C3();
                case C -> new C3();
            };
        }).test1();
    }

    private int runMethodForInterfaceTypeAtMergePoint2(E e1, E e2) {
        return (switch (e1) {
            case A -> switch (e2) {
                case A -> new C1();
                case B -> new C1();
                case C -> new C1();
            };
            case B -> switch (e2) {
                case A -> new C2();
                case B -> new C2();
                case C -> new C2();
            };
            case C -> switch (e2) {
                case A -> new C3();
                case B -> new C3();
                case C -> new C3();
            };
        }).test2();
    }

    private static class Root {}
    private static class Base1 extends Root {}
    private static class Impl1a extends Base1 {}
    private static class Impl1b extends Base1 {}
    private static class Impl1c extends Base1 {}
    private static class Base2 extends Root {}
    private static class Impl2a extends Base2 {}
    private static class Impl2b extends Base2 {}
    private static class Impl2c extends Base2 {}
    private static class Base3 extends Root {}
    private static class Impl3a extends Base3 {}
    private static class Impl3b extends Base3 {}
    private static class Impl3c extends Base3 {}

    private static interface RootInterface1 {
        public default int test1() {
            return 1;
        }
    }
    private static interface RootInterface2 {
        public default int test2() {
            return 2;
        }
    }
    private static class C1 implements RootInterface1, RootInterface2 {}
    private static class C2 implements RootInterface1, RootInterface2 {}
    private static class C3 implements RootInterface1, RootInterface2 {}

    enum E {A, B, C;}

    private void throwException() {
        throw new RuntimeException();
    }

    private static final class ResultException extends RuntimeException {
        public final boolean result;
        public ResultException(boolean result) {
            this.result = result;
        }
    }
}
