/*
 * Copyright (c) 2010, 2018, Oracle and/or its affiliates. All rights reserved.
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
package xmlkit; // -*- mode: java; indent-tabs-mode: nil -*-

import java.util.*;
/*
 * @author jrose
 */
public class CommandLineParser {

    public CommandLineParser(String optionString) {
        setOptionMap(optionString);
    }
    TreeMap<String, String[]> optionMap;

    public void setOptionMap(String options) {
        // Convert options string into optLines dictionary.
        TreeMap<String, String[]> optmap = new TreeMap<String, String[]>();
        loadOptmap:
        for (String optline : options.split("\n")) {
            String[] words = optline.split("\\p{Space}+");
            if (words.length == 0) {
                continue loadOptmap;
            }
            String opt = words[0];
            words[0] = "";  // initial word is not a spec
            if (opt.length() == 0 && words.length >= 1) {
                opt = words[1];  // initial "word" is empty due to leading ' '
                words[1] = "";
            }
            if (opt.length() == 0) {
                continue loadOptmap;
            }
            String[] prevWords = optmap.put(opt, words);
            if (prevWords != null) {
                throw new RuntimeException("duplicate option: "
                        + optline.trim());
            }
        }
        optionMap = optmap;
    }

    public String getOptionMap() {
        TreeMap<String, String[]> optmap = optionMap;
        StringBuffer sb = new StringBuffer();
        for (String opt : optmap.keySet()) {
            sb.append(opt);
            for (String spec : optmap.get(opt)) {
                sb.append(' ').append(spec);
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * Remove a set of command-line options from args,
     * storing them in the properties map in a canonicalized form.
     */
    public String parse(List<String> args, Map<String, String> properties) {
        //System.out.println(args+" // "+properties);

        String resultString = null;
        TreeMap<String, String[]> optmap = optionMap;

        // State machine for parsing a command line.
        ListIterator<String> argp = args.listIterator();
        ListIterator<String> pbp = new ArrayList<String>().listIterator();
        doArgs:
        for (;;) {
            // One trip through this loop per argument.
            // Multiple trips per option only if several options per argument.
            String arg;
            if (pbp.hasPrevious()) {
                arg = pbp.previous();
                pbp.remove();
            } else if (argp.hasNext()) {
                arg = argp.next();
            } else {
                // No more arguments at all.
                break doArgs;
            }
            tryOpt:
            for (int optlen = arg.length();; optlen--) {
                // One time through this loop for each matching arg prefix.
                String opt;
                // Match some prefix of the argument to a key in optmap.
                findOpt:
                for (;;) {
                    opt = arg.substring(0, optlen);
                    if (optmap.containsKey(opt)) {
                        break findOpt;
                    }
                    if (optlen == 0) {
                        break tryOpt;
                    }
                    // Decide on a smaller prefix to search for.
                    SortedMap<String, String[]> pfxmap = optmap.headMap(opt);
                    // pfxmap.lastKey is no shorter than any prefix in optmap.
                    int len = pfxmap.isEmpty() ? 0 : pfxmap.lastKey().length();
                    optlen = Math.min(len, optlen - 1);
                    opt = arg.substring(0, optlen);
                    // (Note:  We could cut opt down to its common prefix with
                    // pfxmap.lastKey, but that wouldn't save many cycles.)
                }
                opt = opt.intern();
                assert (arg.startsWith(opt));
                assert (opt.length() == optlen);
                String val = arg.substring(optlen);  // arg == opt+val

                // Execute the option processing specs for this opt.
                // If no actions are taken, then look for a shorter prefix.
                boolean didAction = false;
                boolean isError = false;

                int pbpMark = pbp.nextIndex();  // in case of backtracking
                String[] specs = optmap.get(opt);
                eachSpec:
                for (String spec : specs) {
                    if (spec.length() == 0) {
                        continue eachSpec;
                    }
                    if (spec.startsWith("#")) {
                        break eachSpec;
                    }
                    int sidx = 0;
                    char specop = spec.charAt(sidx++);

                    // Deal with '+'/'*' prefixes (spec conditions).
                    boolean ok;
                    switch (specop) {
                        case '+':
                            // + means we want an non-empty val suffix.
                            ok = (val.length() != 0);
                            specop = spec.charAt(sidx++);
                            break;
                        case '*':
                            // * means we accept empty or non-empty
                            ok = true;
                            specop = spec.charAt(sidx++);
                            break;
                        default:
                            // No condition prefix means we require an exact
                            // match, as indicated by an empty val suffix.
                            ok = (val.length() == 0);
                            break;
                    }
                    if (!ok) {
                        continue eachSpec;
                    }

                    String specarg = spec.substring(sidx);
                    switch (specop) {
                        case '.':  // terminate the option sequence
                            resultString = (specarg.length() != 0) ? specarg.intern() : opt;
                            break doArgs;
                        case '?':  // abort the option sequence
                            resultString = (specarg.length() != 0) ? specarg.intern() : arg;
                            isError = true;
                            break eachSpec;
                        case '@':  // change the effective opt name
                            opt = specarg.intern();
                            break;
                        case '>':  // shift remaining arg val to next arg
                            pbp.add(specarg + val);  // push a new argument
                            val = "";
                            break;
                        case '!':  // negation option
                            String negopt = (specarg.length() != 0) ? specarg.intern() : opt;
                            properties.remove(negopt);
                            properties.put(negopt, null);  // leave placeholder
                            didAction = true;
                            break;
                        case '$':  // normal "boolean" option
                            String boolval;
                            if (specarg.length() != 0) {
                                // If there is a given spec token, store it.
                                boolval = specarg;
                            } else {
                                String old = properties.get(opt);
                                if (old == null || old.length() == 0) {
                                    boolval = "1";
                                } else {
                                    // Increment any previous value as a numeral.
                                    boolval = "" + (1 + Integer.parseInt(old));
                                }
                            }
                            properties.put(opt, boolval);
                            didAction = true;
                            break;
                        case '=':  // "string" option
                        case '&':  // "collection" option
                            // Read an option.
                            boolean append = (specop == '&');
                            String strval;
                            if (pbp.hasPrevious()) {
                                strval = pbp.previous();
                                pbp.remove();
                            } else if (argp.hasNext()) {
                                strval = argp.next();
                            } else {
                                resultString = arg + " ?";
                                isError = true;
                                break eachSpec;
                            }
                            if (append) {
                                String old = properties.get(opt);
                                if (old != null) {
                                    // Append new val to old with embedded delim.
                                    String delim = specarg;
                                    if (delim.length() == 0) {
                                        delim = " ";
                                    }
                                    strval = old + specarg + strval;
                                }
                            }
                            properties.put(opt, strval);
                            didAction = true;
                            break;
                        default:
                            throw new RuntimeException("bad spec for "
                                    + opt + ": " + spec);
                    }
                }

                // Done processing specs.
                if (didAction && !isError) {
                    continue doArgs;
                }

                // The specs should have done something, but did not.
                while (pbp.nextIndex() > pbpMark) {
                    // Remove anything pushed during these specs.
                    pbp.previous();
                    pbp.remove();
                }

                if (isError) {
                    throw new IllegalArgumentException(resultString);
                }

                if (optlen == 0) {
                    // We cannot try a shorter matching option.
                    break tryOpt;
                }
            }

            // If we come here, there was no matching option.
            // So, push back the argument, and return to caller.
            pbp.add(arg);
            break doArgs;
        }
        // Report number of arguments consumed.
        args.subList(0, argp.nextIndex()).clear();
        // Report any unconsumed partial argument.
        while (pbp.hasPrevious()) {
            args.add(0, pbp.previous());
        }
        //System.out.println(args+" // "+properties+" -> "+resultString);
        return resultString;
    }
}
