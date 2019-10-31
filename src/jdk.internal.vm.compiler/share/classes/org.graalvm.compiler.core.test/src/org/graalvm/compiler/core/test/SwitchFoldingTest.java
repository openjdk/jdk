/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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



package org.graalvm.compiler.core.test;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.junit.Test;

public class SwitchFoldingTest extends GraalCompilerTest {

    private static final String REFERENCE_SNIPPET = "referenceSnippet";
    private static final String REFERENCE_SNIPPET_2 = "reference2Snippet";
    private static final String REFERENCE_SNIPPET_3 = "reference3Snippet";
    private static final String REFERENCE_SNIPPET_4 = "reference4Snippet";
    private static final String REFERENCE_SNIPPET_5 = "reference5Snippet";

    public static int referenceSnippet(int a) {
        switch (a) {
            case 0:
                return 10;
            case 1:
                return 5;
            case 2:
                return 3;
            case 3:
                return 11;
            case 4:
                return 14;
            case 5:
                return 2;
            case 6:
                return 1;
            case 7:
                return 0;
            case 8:
                return 7;
            default:
                return 6;
        }
    }

    public static int reference2Snippet(int a) {
        switch (a) {
            case 0:
                return 4;
            case 1:
            case 2:
                return 1;
            case 3:
                return 6;
            default:
                return 7;
        }
    }

    public static int reference3Snippet(int a) {
        switch (a) {
            case 0:
                return 4;
            case 1:
            case 2:
            case 4:
                return 6;
            case 6:
            case 7:
            default:
                return 7;
        }
    }

    public static int test1Snippet(int a) {
        if (a == 0) {
            return 10;
        } else if (a == 1) {
            return 5;
        } else if (a == 2) {
            return 3;
        } else if (a == 3) {
            return 11;
        } else if (a == 4) {
            return 14;
        } else if (a == 5) {
            return 2;
        } else if (a == 6) {
            return 1;
        } else if (a == 7) {
            return 0;
        } else if (a == 8) {
            return 7;
        } else {
            return 6;
        }
    }

    @Test
    public void test1() {
        test1("test1Snippet");
    }

    public static int test2Snippet(int a) {
        switch (a) {
            case 0:
                return 10;
            case 1:
                return 5;
            case 2:
                return 3;
            case 3:
                return 11;
            case 4:
                return 14;
            default:
                switch (a) {
                    case 5:
                        return 2;
                    case 6:
                        return 1;
                    case 7:
                        return 0;
                    case 8:
                        return 7;
                    default:
                        return 6;
                }
        }
    }

    @Test
    public void test2() {
        test1("test2Snippet");
    }

    public static int test3Snippet(int a) {
        switch (a) {
            case 0:
                return 10;
            default:
                switch (a) {
                    case 1:
                        return 5;
                    default:
                        switch (a) {
                            case 2:
                                return 3;
                            default:
                                switch (a) {
                                    case 3:
                                        return 11;
                                    default:
                                        switch (a) {
                                            case 4:
                                                return 14;
                                            default:
                                                switch (a) {
                                                    case 5:
                                                        return 2;
                                                    default:
                                                        switch (a) {
                                                            case 6:
                                                                return 1;
                                                            default:
                                                                switch (a) {
                                                                    case 7:
                                                                        return 0;
                                                                    default:
                                                                        switch (a) {
                                                                            case 8:
                                                                                return 7;
                                                                            default:
                                                                                return 6;
                                                                        }
                                                                }
                                                        }
                                                }
                                        }
                                }
                        }
                }
        }
    }

    @Test
    public void test3() {
        test1("test3Snippet");
    }

    public static int test4Snippet(int a) {
        switch (a) {
            case 0:
                return 10;
            case 1:
                return 5;
            case 2:
                return 3;
            case 3:
                return 11;
            case 4:
                return 14;
            case 5:
                return 2;
            case 6:
                return 1;
            default:
                if (a == 7) {
                    return 0;
                } else if (a == 8) {
                    return 7;
                } else {
                    return 6;
                }
        }
    }

    @Test
    public void test4() {
        test1("test4Snippet");
    }

    public static int test5Snippet(int a) {
        switch (a) {
            case 0:
                return 10;
            default:
                switch (a) {
                    case 1:
                        return 5;
                    default:
                        switch (a) {
                            case 2:
                                return 3;
                            default:
                                switch (a) {
                                    case 3:
                                        return 11;
                                    default:
                                        switch (a) {
                                            case 4:
                                                return 14;
                                            default:
                                                switch (a) {
                                                    case 5:
                                                        return 2;
                                                    default:
                                                        switch (a) {
                                                            case 6:
                                                                return 1;
                                                            default:
                                                                if (a == 7) {
                                                                    return 0;
                                                                } else if (a == 8) {
                                                                    return 7;
                                                                } else {
                                                                    return 6;
                                                                }
                                                        }
                                                }
                                        }
                                }
                        }
                }
        }
    }

    @Test
    public void test5() {
        test1("test5Snippet");
    }

    public static int test6Snippet(int a) {
        if (a == 0) {
            return 10;
        } else {
            switch (a) {
                case 1:
                    return 5;
                default:
                    if (a == 2) {
                        return 3;
                    } else if (a == 3) {
                        return 11;
                    } else {
                        switch (a) {
                            case 4:
                                return 14;
                            case 5:
                                return 2;
                            case 6:
                                return 1;
                            default:
                                if (a == 7) {
                                    return 0;
                                } else if (a == 8) {
                                    return 7;
                                } else {
                                    return 6;
                                }
                        }
                    }

            }
        }
    }

    @Test
    public void test6() {
        test1("test6Snippet");
    }

    public static int test7Snippet(int a) {
        if (a == 0) {
            return 4;
        } else {
            switch (a) {
                case 1:
                case 2:
                    return 1;
                case 3:
                    return 6;
                default:
                    return 7;
            }
        }
    }

    @Test
    public void test7() {
        test2("test7Snippet");
    }

    public static int test8Snippet(int a) {
        switch (a) {
            case 0:
                return 4;
            case 1:
            case 2:
            case 7:
            default:
                switch (a) {
                    case 2:
                    case 6:
                    default:
                        switch (a) {
                            case 1:
                            case 2:
                            case 4:
                                return 6;
                            default:
                                return 7;
                        }
                }
        }
    }

    @Test
    public void test8() {
        test3("test8Snippet");
    }

    public static int reference4Snippet(int a) {
        switch (a) {
            case 0:
                return 4;
            case 1:
            case 2:
            case 4:
                return 6;
            case 6:
                return 7;
            case 7:
                return 7;
            default:
                return 7;
        }
    }

    public static int test9Snippet(int a) {
        switch (a) {
            case 0:
                return 4;
            case 1:
            case 2:
            case 4:
                return 6;
            case 6:
            case 7:
            default:
                if (a == 6) {
                    return 7;
                } else if (a == 7) {
                    return 7;
                } else {
                    return 7;
                }
        }
    }

    @Test
    public void test9() {
        test4("test9Snippet");
    }

    public static int reference5Snippet(int a) {
        switch (a) {
            case 0:
                return 4;
            case 1:
                return 1;
            case 2:
                return 1;
            case 3:
                return 6;
            default:
                return 7;
        }
    }

    public static int test10Snippet(int a) {
        if (a == 0) {
            return 4;
        } else {
            if (a == 1 || a == 2) {
                return 1;
            } else {
                switch (a) {
                    case 3:
                        return 6;
                    default:
                        return 7;
                }
            }
        }
    }

    @Test
    public void test10() {
        test5("test10Snippet");
    }

    private void test1(String snippet) {
        test(snippet, REFERENCE_SNIPPET);
    }

    private void test2(String snippet) {
        test(snippet, REFERENCE_SNIPPET_2);
    }

    private void test3(String snippet) {
        test(snippet, REFERENCE_SNIPPET_3);
    }

    private void test4(String snippet) {
        test(snippet, REFERENCE_SNIPPET_4);
    }

    private void test5(String snippet) {
        test(snippet, REFERENCE_SNIPPET_5);
    }

    private void test(String snippet, String ref) {
        StructuredGraph graph = parseEager(snippet, StructuredGraph.AllowAssumptions.YES);
        DebugContext debug = graph.getDebug();
        debug.dump(DebugContext.BASIC_LEVEL, graph, "Graph");
        createCanonicalizerPhase().apply(graph, getProviders());
        StructuredGraph referenceGraph = parseEager(ref, StructuredGraph.AllowAssumptions.YES);
        assertEquals(referenceGraph, graph);
    }
}
