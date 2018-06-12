/*
 * Copyright (c) 2002-2016, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package jdk.internal.jline.internal;

import java.io.IOException;
import java.io.Writer;
import java.util.Stack;

/**
 * Curses helper methods.
 *
 * @author <a href="mailto:gnodet@gmail.com">Guillaume Nodet</a>
 */
public class Curses {

    private static Object[] sv = new Object[26];
    private static Object[] dv = new Object[26];

    private static final int IFTE_NONE = 0;
    private static final int IFTE_IF = 1;
    private static final int IFTE_THEN = 2;
    private static final int IFTE_ELSE = 3;

    /**
     * Print the given terminal capabilities
     *
     * @param out the output stream
     * @param str the capability to output
     * @param params optional parameters
     * @throws IOException if an error occurs
     */
    public static void tputs(Writer out, String str, Object... params) throws IOException {
        int index = 0;
        int length = str.length();
        int ifte = IFTE_NONE;
        boolean exec = true;
        Stack<Object> stack = new Stack<Object>();
        while (index < length) {
            char ch = str.charAt(index++);
            switch (ch) {
                case '\\':
                    ch = str.charAt(index++);
                    if (ch >= '0' && ch <= '9') {
                        throw new UnsupportedOperationException(); // todo
                    } else {
                        switch (ch) {
                            case 'e':
                            case 'E':
                                if (exec) {
                                    out.write(27); // escape
                                }
                                break;
                            case 'n':
                                out.write('\n');
                                break;
//                        case 'l':
//                            rawPrint('\l');
//                            break;
                            case 'r':
                                if (exec) {
                                    out.write('\r');
                                }
                                break;
                            case 't':
                                if (exec) {
                                    out.write('\t');
                                }
                                break;
                            case 'b':
                                if (exec) {
                                    out.write('\b');
                                }
                                break;
                            case 'f':
                                if (exec) {
                                    out.write('\f');
                                }
                                break;
                            case 's':
                                if (exec) {
                                    out.write(' ');
                                }
                                break;
                            case ':':
                            case '^':
                            case '\\':
                                if (exec) {
                                    out.write(ch);
                                }
                                break;
                            default:
                                throw new IllegalArgumentException();
                        }
                    }
                    break;
                case '^':
                    ch = str.charAt(index++);
                    if (exec) {
                        out.write(ch - '@');
                    }
                    break;
                case '%':
                    ch = str.charAt(index++);
                    switch (ch) {
                        case '%':
                            if (exec) {
                                out.write('%');
                            }
                            break;
                        case 'p':
                            ch = str.charAt(index++);
                            if (exec) {
                                stack.push(params[ch - '1']);
                            }
                            break;
                        case 'P':
                            ch = str.charAt(index++);
                            if (ch >= 'a' && ch <= 'z') {
                                if (exec) {
                                    dv[ch - 'a'] = stack.pop();
                                }
                            } else if (ch >= 'A' && ch <= 'Z') {
                                if (exec) {
                                    sv[ch - 'A'] = stack.pop();
                                }
                            } else {
                                throw new IllegalArgumentException();
                            }
                            break;
                        case 'g':
                            ch = str.charAt(index++);
                            if (ch >= 'a' && ch <= 'z') {
                                if (exec) {
                                    stack.push(dv[ch - 'a']);
                                }
                            } else if (ch >= 'A' && ch <= 'Z') {
                                if (exec) {
                                    stack.push(sv[ch - 'A']);
                                }
                            } else {
                                throw new IllegalArgumentException();
                            }
                            break;
                        case '\'':
                            ch = str.charAt(index++);
                            if (exec) {
                                stack.push((int) ch);
                            }
                            ch = str.charAt(index++);
                            if (ch != '\'') {
                                throw new IllegalArgumentException();
                            }
                            break;
                        case '{':
                            int start = index;
                            while (str.charAt(index++) != '}') ;
                            if (exec) {
                                int v = Integer.valueOf(str.substring(start, index - 1));
                                stack.push(v);
                            }
                            break;
                        case 'l':
                            if (exec) {
                                stack.push(stack.pop().toString().length());
                            }
                            break;
                        case '+':
                            if (exec) {
                                int v2 = toInteger(stack.pop());
                                int v1 = toInteger(stack.pop());
                                stack.push(v1 + v2);
                            }
                            break;
                        case '-':
                            if (exec) {
                                int v2 = toInteger(stack.pop());
                                int v1 = toInteger(stack.pop());
                                stack.push(v1 - v2);
                            }
                            break;
                        case '*':
                            if (exec) {
                                int v2 = toInteger(stack.pop());
                                int v1 = toInteger(stack.pop());
                                stack.push(v1 * v2);
                            }
                            break;
                        case '/':
                            if (exec) {
                                int v2 = toInteger(stack.pop());
                                int v1 = toInteger(stack.pop());
                                stack.push(v1 / v2);
                            }
                            break;
                        case 'm':
                            if (exec) {
                                int v2 = toInteger(stack.pop());
                                int v1 = toInteger(stack.pop());
                                stack.push(v1 % v2);
                            }
                            break;
                        case '&':
                            if (exec) {
                                int v2 = toInteger(stack.pop());
                                int v1 = toInteger(stack.pop());
                                stack.push(v1 & v2);
                            }
                            break;
                        case '|':
                            if (exec) {
                                int v2 = toInteger(stack.pop());
                                int v1 = toInteger(stack.pop());
                                stack.push(v1 | v2);
                            }
                            break;
                        case '^':
                            if (exec) {
                                int v2 = toInteger(stack.pop());
                                int v1 = toInteger(stack.pop());
                                stack.push(v1 ^ v2);
                            }
                            break;
                        case '=':
                            if (exec) {
                                int v2 = toInteger(stack.pop());
                                int v1 = toInteger(stack.pop());
                                stack.push(v1 == v2);
                            }
                            break;
                        case '>':
                            if (exec) {
                                int v2 = toInteger(stack.pop());
                                int v1 = toInteger(stack.pop());
                                stack.push(v1 > v2);
                            }
                            break;
                        case '<':
                            if (exec) {
                                int v2 = toInteger(stack.pop());
                                int v1 = toInteger(stack.pop());
                                stack.push(v1 < v2);
                            }
                            break;
                        case 'A':
                            if (exec) {
                                int v2 = toInteger(stack.pop());
                                int v1 = toInteger(stack.pop());
                                stack.push(v1 != 0 && v2 != 0);
                            }
                            break;
                        case '!':
                            if (exec) {
                                int v1 = toInteger(stack.pop());
                                stack.push(v1 == 0);
                            }
                            break;
                        case '~':
                            if (exec) {
                                int v1 = toInteger(stack.pop());
                                stack.push(~v1);
                            }
                            break;
                        case 'O':
                            if (exec) {
                                int v2 = toInteger(stack.pop());
                                int v1 = toInteger(stack.pop());
                                stack.push(v1 != 0 || v2 != 0);
                            }
                            break;
                        case '?':
                            if (ifte != IFTE_NONE) {
                                throw new IllegalArgumentException();
                            } else {
                                ifte = IFTE_IF;
                            }
                            break;
                        case 't':
                            if (ifte != IFTE_IF && ifte != IFTE_ELSE) {
                                throw new IllegalArgumentException();
                            } else {
                                ifte = IFTE_THEN;
                            }
                            exec = toInteger(stack.pop()) != 0;
                            break;
                        case 'e':
                            if (ifte != IFTE_THEN) {
                                throw new IllegalArgumentException();
                            } else {
                                ifte = IFTE_ELSE;
                            }
                            exec = !exec;
                            break;
                        case ';':
                            if (ifte == IFTE_NONE || ifte == IFTE_IF) {
                                throw new IllegalArgumentException();
                            } else {
                                ifte = IFTE_NONE;
                            }
                            exec = true;
                            break;
                        case 'i':
                            if (params.length >= 1) {
                                params[0] = toInteger(params[0]) + 1;
                            }
                            if (params.length >= 2) {
                                params[1] = toInteger(params[1]) + 1;
                            }
                            break;
                        case 'd':
                            out.write(Integer.toString(toInteger(stack.pop())));
                            break;
                        default:
                            throw new UnsupportedOperationException();
                    }
                    break;
                default:
                    if (exec) {
                        out.write(ch);
                    }
                    break;
            }
        }
    }

    private static int toInteger(Object pop) {
        if (pop instanceof Number) {
            return ((Number) pop).intValue();
        } else if (pop instanceof Boolean) {
            return (Boolean) pop ? 1 : 0;
        } else {
            return Integer.valueOf(pop.toString());
        }
    }

}
