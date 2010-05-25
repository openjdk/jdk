/*
 * Copyright (c) 2001, 2005, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package com.sun.tools.example.debug.tty;

/**
 * <p> This class represents the <code>ResourceBundle</code>
 * for the following package(s):
 *
 * <ol>
 * <li> com.sun.tools.example.debug.tty
 * </ol>
 *
 */
public class TTYResources_ja extends java.util.ListResourceBundle {


    /**
     * Returns the contents of this <code>ResourceBundle</code>.
     *
     * <p>
     *
     * @return the contents of this <code>ResourceBundle</code>.
     */
    public Object[][] getContents() {
        return new Object[][] {
        // NOTE: The value strings in this file containing "{0}" are
        //       processed by the java.text.MessageFormat class.  Any
        //       single quotes appearing in these strings need to be
        //       doubled up.
        //
        // LOCALIZE THIS
        {"** classes list **", "** \u30af\u30e9\u30b9\u30ea\u30b9\u30c8 **\n{0}"},
        {"** fields list **", "** \u30d5\u30a3\u30fc\u30eb\u30c9\u30ea\u30b9\u30c8 **\n{0}"},
        {"** methods list **", "** \u30e1\u30bd\u30c3\u30c9\u30ea\u30b9\u30c8 **\n{0}"},
        {"*** Reading commands from", "*** {0} \u304b\u3089\u30b3\u30de\u30f3\u30c9\u3092\u8aad\u307f\u8fbc\u3093\u3067\u3044\u307e\u3059"},
        {"All threads resumed.", "\u3059\u3079\u3066\u306e\u30b9\u30ec\u30c3\u30c9\u304c\u518d\u958b\u3055\u308c\u307e\u3057\u305f\u3002"},
        {"All threads suspended.", "\u3059\u3079\u3066\u306e\u30b9\u30ec\u30c3\u30c9\u304c\u4e2d\u65ad\u3055\u308c\u307e\u3057\u305f\u3002"},
        {"Argument is not defined for connector:", "\u30b3\u30cd\u30af\u30bf {1} \u3067\u5f15\u6570 {0} \u304c\u5b9a\u7fa9\u3055\u308c\u3066\u3044\u307e\u305b\u3093"},
        {"Arguments match no method", "\u5f15\u6570\u3068\u4e00\u81f4\u3059\u308b\u30e1\u30bd\u30c3\u30c9\u304c\u3042\u308a\u307e\u305b\u3093"},
        {"Array:", "\u914d\u5217: {0}"},
        {"Array element is not a method", "\u914d\u5217\u8981\u7d20\u306f\u3001\u30e1\u30bd\u30c3\u30c9\u3067\u306f\u3042\u308a\u307e\u305b\u3093"},
        {"Array index must be a integer type", "\u914d\u5217\u306e\u6dfb\u3048\u5b57\u306f\u6574\u6570\u578b\u306b\u3059\u308b\u5fc5\u8981\u304c\u3042\u308a\u307e\u3059"},
        {"base directory:", "\u30d9\u30fc\u30b9\u30c7\u30a3\u30ec\u30af\u30c8\u30ea: {0}"},
        {"bootclasspath:", "\u30d6\u30fc\u30c8\u30af\u30e9\u30b9\u30d1\u30b9: {0}"},
        {"Breakpoint hit:", "\u30d6\u30ec\u30fc\u30af\u30dd\u30a4\u30f3\u30c8\u306e\u30d2\u30c3\u30c8: "},
        {"breakpoint", "\u30d6\u30ec\u30fc\u30af\u30dd\u30a4\u30f3\u30c8 {0}"},
        {"Breakpoints set:", "\u30d6\u30ec\u30fc\u30af\u30dd\u30a4\u30f3\u30c8\u306e\u30bb\u30c3\u30c8:"},
        {"Breakpoints can be located only in classes.", "\u30d6\u30ec\u30fc\u30af\u30dd\u30a4\u30f3\u30c8\u306f\u3001\u30af\u30e9\u30b9\u306b\u306e\u307f\u5272\u308a\u5f53\u3066\u308b\u3053\u3068\u304c\u3067\u304d\u307e\u3059\u3002{0} \u306f\u30a4\u30f3\u30bf\u30d5\u30a7\u30fc\u30b9\u307e\u305f\u306f\u914d\u5217\u3067\u3059\u3002"},
        {"Can only trace", "'methods'\u3001'method exit' \u307e\u305f\u306f 'method exits' \u306e\u307f\u3092\u30c8\u30ec\u30fc\u30b9\u3067\u304d\u307e\u3059"},
        {"cannot redefine existing connection", "{0} \u306f\u3001\u65e2\u5b58\u306e\u63a5\u7d9a\u3092\u518d\u5b9a\u7fa9\u3067\u304d\u307e\u305b\u3093"},
        {"Cannot assign to a method invocation", "\u30e1\u30bd\u30c3\u30c9\u547c\u3073\u51fa\u3057\u306b\u306f\u5272\u308a\u5f53\u3066\u3089\u308c\u307e\u305b\u3093"},
        {"Cannot specify command line with connector:", "\u30b3\u30cd\u30af\u30bf\uff5b0} \u3067\u306f\u30b3\u30de\u30f3\u30c9\u884c\u3092\u6307\u5b9a\u3067\u304d\u307e\u305b\u3093"},
        {"Cannot specify target vm arguments with connector:", "\u30b3\u30cd\u30af\u30bf {0}\u3067\u306f\u3001\u30bf\u30fc\u30b2\u30c3\u30c8 VM \u5f15\u6570\u3092\u6307\u5b9a\u3067\u304d\u307e\u305b\u3093"},
        {"Class containing field must be specified.", "\u30d5\u30a3\u30fc\u30eb\u30c9\u3092\u542b\u3080\u30af\u30e9\u30b9\u3092\u6307\u5b9a\u3059\u308b\u5fc5\u8981\u304c\u3042\u308a\u307e\u3059\u3002"},
        {"Class:", "\u30af\u30e9\u30b9: {0}"},
        {"Classic VM no longer supported.", "Classic VM \u306f\u3082\u3046\u30b5\u30dd\u30fc\u30c8\u3055\u308c\u3066\u3044\u307e\u305b\u3093\u3002"},
        {"classpath:", "\u30af\u30e9\u30b9\u30d1\u30b9: {0}"},
        {"colon mark", ":"},
        {"colon space", ": "},
        {"Command is not supported on the target VM", "\u30bf\u30fc\u30b2\u30c3\u30c8 VM \u3067\u306f\u3001\u30b3\u30de\u30f3\u30c9 ''{0}'' \u304c\u30b5\u30dd\u30fc\u30c8\u3055\u308c\u3066\u3044\u307e\u305b\u3093"},
        {"Command is not supported on a read-only VM connection", "\u8aad\u307f\u53d6\u308a\u5c02\u7528 VM \u63a5\u7d9a\u3067\u306f\u3001\u30b3\u30de\u30f3\u30c9 ''{0}'' \u306f\u30b5\u30dd\u30fc\u30c8\u3055\u308c\u3066\u3044\u307e\u305b\u3093"},
        {"Command not valid until the VM is started with the run command", "\u30b3\u30de\u30f3\u30c9 ''{0}'' \u306f\u3001''run'' \u30b3\u30de\u30f3\u30c9\u3092\u4f7f\u3063\u3066 VM \u3092\u8d77\u52d5\u3059\u308b\u307e\u3067\u6709\u52b9\u3067\u306f\u3042\u308a\u307e\u305b\u3093"},
        {"Condition must be boolean", "\u6761\u4ef6\u306f boolean \u578b\u306b\u3059\u308b\u5fc5\u8981\u304c\u3042\u308a\u307e\u3059"},
        {"Connector and Transport name", "  \u30b3\u30cd\u30af\u30bf: {0}  \u30c8\u30e9\u30f3\u30b9\u30dd\u30fc\u30c8: {1}"},
        {"Connector argument nodefault", "    \u5f15\u6570: {0} (\u30c7\u30d5\u30a9\u30eb\u30c8\u306a\u3057)"},
        {"Connector argument default", "    \u5f15\u6570: {0} \u30c7\u30d5\u30a9\u30eb\u30c8\u5024: {1}"},
        {"Connector description", "    \u8aac\u660e: {0}"},
        {"Connector required argument nodefault", "    \u5fc5\u9808\u5f15\u6570: {0} (\u30c7\u30d5\u30a9\u30eb\u30c8\u306a\u3057)"},
        {"Connector required argument default", "    \u5fc5\u9808\u5f15\u6570: {0} \u30c7\u30d5\u30a9\u30eb\u30c8\u5024: {1}"},
        {"Connectors available", "\u5229\u7528\u53ef\u80fd\u306a\u30b3\u30cd\u30af\u30bf:"},
        {"Constant is not a method", "\u5b9a\u6570\u306f\u30e1\u30bd\u30c3\u30c9\u3067\u306f\u3042\u308a\u307e\u305b\u3093"},
        {"Could not open:", "{0} \u3092\u958b\u3051\u307e\u305b\u3093"},
        {"Current method is native", "\u73fe\u884c\u306e\u30e1\u30bd\u30c3\u30c9\u306f\u30cd\u30a4\u30c6\u30a3\u30d6\u3067\u3059"},
        {"Current thread died. Execution continuing...", "\u73fe\u5728\u306e\u30b9\u30ec\u30c3\u30c9 {0} \u304c\u4e2d\u65ad\u3055\u308c\u307e\u3057\u305f\u3002\u51e6\u7406\u3092\u7d9a\u884c\u3057\u3066\u3044\u307e\u3059..."},
        {"Current thread isnt suspended.", "\u73fe\u5728\u306e\u30b9\u30ec\u30c3\u30c9\u306f\u505c\u6b62\u3057\u3066\u3044\u307e\u305b\u3093\u3002"},
        {"Current thread not set.", "\u73fe\u5728\u306e\u30b9\u30ec\u30c3\u30c9\u306f\u8a2d\u5b9a\u3055\u308c\u3066\u3044\u307e\u305b\u3093\u3002"},
        {"dbgtrace flag value must be an integer:", "dbgtrace \u30d5\u30e9\u30b0\u5024\u306f\u3001\u6574\u6570\u306b\u3059\u308b\u5fc5\u8981\u304c\u3042\u308a\u307e\u3059: {0}"},
        {"Deferring.", "{0} \u3092\u4fdd\u7559\u3057\u3066\u3044\u307e\u3059\u3002\n\u30af\u30e9\u30b9\u304c\u30ed\u30fc\u30c9\u3055\u308c\u305f\u5f8c\u306b\u8a2d\u5b9a\u3055\u308c\u307e\u3059\u3002"},
        {"End of stack.", "\u30b9\u30bf\u30c3\u30af\u306e\u7d42\u308f\u308a\u3002"},
        {"Error popping frame", "\u30d5\u30ec\u30fc\u30e0\u306e\u30dd\u30c3\u30d7\u4e2d\u306b\u30a8\u30e9\u30fc\u304c\u767a\u751f\u3057\u307e\u3057\u305f - {0}"},
        {"Error reading file", "''{0}'' \u306e \u8aad\u307f\u8fbc\u307f\u4e2d\u306b\u30a8\u30e9\u30fc\u304c\u767a\u751f\u3057\u307e\u3057\u305f - {1}"},
        {"Error redefining class to file", "{0} \u306e {1} \u3078\u306e\u518d\u5b9a\u7fa9\u4e2d\u306b\u30a8\u30e9\u30fc\u304c\u767a\u751f\u3057\u307e\u3057\u305f - {2}"},
        {"exceptionSpec all", "all {0}"},
        {"exceptionSpec caught", "caught {0}"},
        {"exceptionSpec uncaught", "uncaught {0}"},
        {"Exception in expression:", "\u5f0f\u3067\u4f8b\u5916\u304c\u767a\u751f\u3057\u307e\u3057\u305f: {0}"},
        {"Exception occurred caught", "\u4f8b\u5916\u304c\u767a\u751f\u3057\u307e\u3057\u305f: {0} ({1}\u3067\u30ad\u30e3\u30c3\u30c1\u3055\u308c\u307e\u3057\u305f)"},
        {"Exception occurred uncaught", "\u4f8b\u5916\u304c\u767a\u751f\u3057\u307e\u3057\u305f: {0} (\u30ad\u30e3\u30c3\u30c1\u3055\u308c\u3066\u3044\u307e\u305b\u3093)"},
        {"Exceptions caught:", "\u4ee5\u4e0b\u306e\u4f8b\u5916\u304c\u767a\u751f\u3057\u305f\u6642\u306b\u505c\u6b62\u3057\u307e\u3059:"},
        {"expr is null", "{0} = null"},
        {"expr is value", "{0} = {1}"},
        {"expr is value <collected>", "  {0} = {1} <\u53ce\u96c6\u3055\u308c\u307e\u3057\u305f>"},
        {"Expression cannot be void", "\u5f0f\u3092 void \u578b\u306b\u3059\u308b\u3053\u3068\u306f\u3067\u304d\u307e\u305b\u3093"},
        {"Expression must evaluate to an object", "\u5f0f\u3067\u30aa\u30d6\u30b8\u30a7\u30af\u30c8\u3092\u8a55\u4fa1\u3059\u308b\u5fc5\u8981\u304c\u3042\u308a\u307e\u3059"},
        {"extends:", "\u62e1\u5f35: {0}"},
        {"Failed reading output", "\u5b50 java \u30a4\u30f3\u30bf\u30d7\u30ea\u30bf\u306e\u51fa\u529b\u306e\u8aad\u307f\u8fbc\u307f\u306b\u5931\u6557\u3057\u307e\u3057\u305f\u3002"},
        {"Fatal error", "\u81f4\u547d\u7684\u30a8\u30e9\u30fc:"},
        {"Field access encountered before after", "{1} \u306e\u30d5\u30a3\u30fc\u30eb\u30c9 ({0}) \u306f {2} \u306b\u306a\u308a\u307e\u3059: "},
        {"Field access encountered", "\u30d5\u30a3\u30fc\u30eb\u30c9 ({0}) \u3078\u306e\u30a2\u30af\u30bb\u30b9\u304c\u691c\u51fa\u3055\u308c\u307e\u3057\u305f: "},
        {"Field to unwatch not specified", "\u76e3\u8996\u3057\u306a\u3044\u30d5\u30a3\u30fc\u30eb\u30c9\u304c\u6307\u5b9a\u3055\u308c\u3066\u3044\u307e\u305b\u3093\u3002"},
        {"Field to watch not specified", "\u76e3\u8996\u3059\u308b\u30d5\u30a3\u30fc\u30eb\u30c9\u304c\u6307\u5b9a\u3055\u308c\u3066\u3044\u307e\u305b\u3093\u3002"},
        {"GC Disabled for", "{0} \u3067\u306f GC \u304c\u7121\u52b9\u3067\u3059:"},
        {"GC Enabled for", "{0} \u3067\u306f GC \u304c\u6709\u52b9\u3067\u3059:"},
        {"grouping begin character", "{"},
        {"grouping end character", "}"},
        {"Illegal Argument Exception", "\u4e0d\u6b63\u306a\u5f15\u6570\u4f8b\u5916\u3067\u3059"},
        {"Illegal connector argument", "\u4e0d\u6b63\u306a\u30b3\u30cd\u30af\u30bf\u5f15\u6570: {0}"},
        {"implementor:", "\u5b9f\u88c5\u8005: {0}"},
        {"implements:", "\u5b9f\u88c5\u3057\u307e\u3059: {0}"},
        {"Initializing progname", "{0} \u306e\u521d\u671f\u5316\u4e2d\u3067\u3059..."},
        {"Input stream closed.", "\u5165\u529b\u30b9\u30c8\u30ea\u30fc\u30e0\u3092\u7d42\u4e86\u3057\u307e\u3057\u305f\u3002"},
        {"Interface:", "\u30a4\u30f3\u30bf\u30d5\u30a7\u30fc\u30b9: {0}"},
        {"Internal debugger error.", "\u5185\u90e8\u30c7\u30d0\u30c3\u30ac\u30a8\u30e9\u30fc"},
        {"Internal error: null ThreadInfo created", "\u5185\u90e8\u30a8\u30e9\u30fc: null ThreadInfo \u304c\u4f5c\u6210\u3055\u308c\u307e\u3057\u305f"},
        {"Internal error; unable to set", "\u5185\u90e8\u30a8\u30e9\u30fc; {0} \u3092\u8a2d\u5b9a\u3067\u304d\u307e\u305b\u3093"},
        {"Internal exception during operation:", "\u51e6\u7406\u4e2d\u306b\u5185\u90e8\u4f8b\u5916\u304c\u767a\u751f\u3057\u307e\u3057\u305f:\n    {0}"},
        {"Internal exception:", "\u5185\u90e8\u4f8b\u5916:"},
        {"Invalid argument type name", "\u5f15\u6570\u306e\u578b\u540d\u304c\u7121\u52b9\u3067\u3059"},
        {"Invalid assignment syntax", "\u4ee3\u5165\u69cb\u6587\u304c\u7121\u52b9\u3067\u3059"},
        {"Invalid command syntax", "\u30b3\u30de\u30f3\u30c9\u69cb\u6587\u304c\u7121\u52b9\u3067\u3059"},
        {"Invalid connect type", "\u63a5\u7d9a\u306e\u7a2e\u985e\u304c\u7121\u52b9\u3067\u3059"},
        {"Invalid consecutive invocations", "\u9023\u7d9a\u547c\u3073\u51fa\u3057\u304c\u7121\u52b9\u3067\u3059"},
        {"Invalid exception object", "\u4f8b\u5916\u30aa\u30d6\u30b8\u30a7\u30af\u30c8\u304c\u7121\u52b9\u3067\u3059"},
        {"Invalid method specification:", "\u7121\u52b9\u306a\u30e1\u30bd\u30c3\u30c9\u3092\u6307\u5b9a\u3057\u307e\u3057\u305f: {0}"},
        {"Invalid option on class command", "\u30af\u30e9\u30b9\u30b3\u30de\u30f3\u30c9\u306e\u30aa\u30d7\u30b7\u30e7\u30f3\u304c\u7121\u52b9\u3067\u3059"},
        {"invalid option", "\u30aa\u30d7\u30b7\u30e7\u30f3\u304c\u7121\u52b9\u3067\u3059: {0}"},
        {"Invalid thread status.", "\u30b9\u30ec\u30c3\u30c9\u30b9\u30c6\u30fc\u30bf\u30b9\u304c\u7121\u52b9\u3067\u3059\u3002"},
        {"Invalid transport name:", "\u30c8\u30e9\u30f3\u30b9\u30dd\u30fc\u30c8\u540d\u304c\u7121\u52b9\u3067\u3059: {0}"},
        {"I/O exception occurred:", "\u5165\u51fa\u529b\u4f8b\u5916\u304c\u767a\u751f\u3057\u307e\u3057\u305f: {0}"},
        {"is an ambiguous method name in", "\"{0}\" \u306f\u3001\"{1}\" \u3067\u306f\u3042\u3044\u307e\u3044\u306a\u30e1\u30bd\u30c3\u30c9\u540d\u3067\u3059"},
        {"is an invalid line number for",  "{0,number,integer} \u306f\u3001{1} \u306e\u7121\u52b9\u306a\u884c\u756a\u53f7\u3067\u3059"},
        {"is not a valid class name", "\"{0}\" \u306f\u3001\u6709\u52b9\u306a\u30af\u30e9\u30b9\u540d\u3067\u306f\u3042\u308a\u307e\u305b\u3093\u3002"},
        {"is not a valid field name", "\"{0}\" \u306f\u3001\u6709\u52b9\u306a\u30d5\u30a3\u30fc\u30eb\u30c9\u540d\u3067\u306f\u3042\u308a\u307e\u305b\u3093\u3002"},
        {"is not a valid id or class name", "\"{0}\" \u306f\u3001\u6709\u52b9\u306a ID \u307e\u305f\u306f\u30af\u30e9\u30b9\u540d\u3067\u306f\u3042\u308a\u307e\u305b\u3093\u3002"},
        {"is not a valid line number or method name for", "\"{0}\" \u306f\u3001\u30af\u30e9\u30b9 \"{1}\" \u306e\u6709\u52b9\u306a\u884c\u756a\u53f7\u307e\u305f\u306f\u30e1\u30bd\u30c3\u30c9\u540d\u3067\u306f\u3042\u308a\u307e\u305b\u3093"},
        {"is not a valid method name", "\"{0}\" \u306f\u3001\u6709\u52b9\u306a\u30e1\u30bd\u30c3\u30c9\u540d\u3067\u306f\u3042\u308a\u307e\u305b\u3093\u3002"},
        {"is not a valid thread id", "\"{0}\" \u306f\u3001\u6709\u52b9\u306a\u30b9\u30ec\u30c3\u30c9 ID \u3067\u306f\u3042\u308a\u307e\u305b\u3093\u3002"},
        {"is not a valid threadgroup name", "\"{0}\" \u306f\u3001\u6709\u52b9\u306a\u30b9\u30ec\u30c3\u30c9\u30b0\u30eb\u30fc\u30d7\u540d\u3067\u306f\u3042\u308a\u307e\u305b\u3093\u3002"},
        {"jdb prompt with no current thread", "> "},
        {"jdb prompt thread name and current stack frame", "{0}[{1,number,integer}] "},
        {"killed", "{0} \u304c\u7d42\u4e86\u3057\u307e\u3057\u305f"},
        {"killing thread:", "\u6b21\u306e\u30b9\u30ec\u30c3\u30c9\u3092\u7d42\u4e86\u3057\u3066\u3044\u307e\u3059: {0}"},
        {"Line number information not available for", "\u30bd\u30fc\u30b9\u306e\u884c\u756a\u53f7\u306f\u3001\u3053\u3053\u3067\u306f\u4f7f\u7528\u3067\u304d\u307e\u305b\u3093\u3002"},
        {"line number", ":{0,number,integer}"},
        {"list field typename and name", "{0} {1}\n"},
        {"list field typename and name inherited", "{0} {1} ({2} \u304b\u3089\u7d99\u627f)\n"},
        {"list field typename and name hidden", "{0} {1} (\u975e\u8868\u793a)\n"},
        {"Listening at address:", "\u30a2\u30c9\u30ec\u30b9\u3067\u5f85\u6a5f: {0}"},
        {"Local variable information not available.", "\u30ed\u30fc\u30ab\u30eb\u5909\u6570\u60c5\u5831\u304c\u5229\u7528\u3067\u304d\u307e\u305b\u3093\u3002-g \u3092\u4f7f\u3063\u3066\u30b3\u30f3\u30d1\u30a4\u30eb\u3057\u3001\u5909\u6570\u60c5\u5831\u3092\u751f\u6210\u3057\u3066\u304f\u3060\u3055\u3044"},
        {"Local variables:", "\u30ed\u30fc\u30ab\u30eb\u5909\u6570:"},
        {"<location unavailable>", "<\u4f4d\u7f6e\u9078\u629e\u4e0d\u53ef>"},
        {"location", "\"\u30b9\u30ec\u30c3\u30c9={0}\", {1}"},
        {"locationString", "{0}.{1}(), line={2,number,integer} bci={3,number,integer}"},
        {"Main class and arguments must be specified", "\u30e1\u30a4\u30f3\u30af\u30e9\u30b9\u3068\u5f15\u6570\u3092\u6307\u5b9a\u3059\u308b\u5fc5\u8981\u304c\u3042\u308a\u307e\u3059"},
        {"Method arguments:", "\u30e1\u30bd\u30c3\u30c9\u5f15\u6570:"},
        {"Method entered:", "\u30e1\u30bd\u30c3\u30c9\u958b\u59cb: "},
        {"Method exited:",  "\u30e1\u30bd\u30c3\u30c9\u7d42\u4e86"},
        {"Method exitedValue:", "\u30e1\u30bd\u30c3\u30c9\u7d42\u4e86: \u623b\u308a\u5024 = {0}, "},
        {"Method is overloaded; specify arguments", "\u30e1\u30bd\u30c3\u30c9 {0} \u304c\u30aa\u30fc\u30d0\u30fc\u30ed\u30fc\u30c9\u3057\u3066\u3044\u307e\u3059\u3002\u5f15\u6570\u3092\u6307\u5b9a\u3057\u3066\u304f\u3060\u3055\u3044"},
        {"minus version", "\u3053\u308c\u306f {0} \u30d0\u30fc\u30b8\u30e7\u30f3 {1,number,integer}.{2,number,integer} (J2SE \u30d0\u30fc\u30b8\u30e7\u30f3 {3}) \u3067\u3059"},
        {"Monitor information for thread", "\u30b9\u30ec\u30c3\u30c9 {0} \u306e\u30e2\u30cb\u30bf\u60c5\u5831:"},
        {"Monitor information for expr", "{0} ({1}) \u306e\u30e2\u30cb\u30bf\u60c5\u5831:"},
        {"More than one class named", "\u8907\u6570\u306e\u30af\u30e9\u30b9\u306b\u6b21\u306e\u540d\u524d\u304c\u4ed8\u3044\u3066\u3044\u307e\u3059: ''{0}''"},
        {"native method", "\u30cd\u30a4\u30c6\u30a3\u30d6 \u30e1\u30bd\u30c3\u30c9"},
        {"nested:", "\u5165\u308c\u5b50: {0}"},
        {"No attach address specified.", "\u63a5\u7d9a\u5148\u306e\u30a2\u30c9\u30ec\u30b9\u304c\u6307\u5b9a\u3055\u308c\u3066\u3044\u307e\u305b\u3093\u3002"},
        {"No breakpoints set.", "\u30d6\u30ec\u30fc\u30af\u30dd\u30a4\u30f3\u30c8\u304c\u8a2d\u5b9a\u3055\u308c\u3066\u3044\u307e\u305b\u3093\u3002"},
        {"No class named", "''{0}'' \u3068\u3044\u3046\u540d\u524d\u306e\u30af\u30e9\u30b9\u304c\u3042\u308a\u307e\u305b\u3093"},
        {"No class specified.", "\u30af\u30e9\u30b9\u304c\u6307\u5b9a\u3055\u308c\u3066\u3044\u307e\u305b\u3093\u3002"},
        {"No classpath specified.", "\u30af\u30e9\u30b9\u30d1\u30b9\u304c\u6307\u5b9a\u3055\u308c\u3066\u3044\u307e\u305b\u3093\u3002"},
        {"No code at line", "{1} \u306e\u884c {0,number,integer} \u306b\u30b3\u30fc\u30c9\u304c\u3042\u308a\u307e\u305b\u3093"},
        {"No connect specification.", "\u63a5\u7d9a\u4ed5\u69d8\u304c\u3042\u308a\u307e\u305b\u3093\u3002"},
        {"No connector named:", "\u6b21\u306e\u540d\u524d\u306e\u30b3\u30cd\u30af\u30bf\u304c\u3042\u308a\u307e\u305b\u3093: {0}"},
        {"No current thread", "\u73fe\u884c\u30b9\u30ec\u30c3\u30c9\u304c\u3042\u308a\u307e\u305b\u3093"},
        {"No default thread specified:", "\u30c7\u30d5\u30a9\u30eb\u30c8\u30b9\u30ec\u30c3\u30c9\u304c\u6307\u5b9a\u3055\u308c\u3066\u3044\u307e\u305b\u3093\u3002\u6700\u521d\u306b \"thread\" \u30b3\u30de\u30f3\u30c9\u3092\u4f7f\u7528\u3057\u3066\u304f\u3060\u3055\u3044\u3002"},
        {"No exception object specified.", "\u4f8b\u5916\u30aa\u30d6\u30b8\u30a7\u30af\u30c8\u304c\u6307\u5b9a\u3055\u308c\u3066\u3044\u307e\u305b\u3093\u3002"},
        {"No exceptions caught.", "\u4f8b\u5916\u306f\u30ad\u30e3\u30c3\u30c1\u3055\u308c\u3066\u3044\u307e\u305b\u3093\u3002"},
        {"No expression specified.", "\u5f0f\u304c\u6307\u5b9a\u3055\u308c\u3066\u3044\u307e\u305b\u3093\u3002"},
        {"No field in", "{1} \u306b\u30d5\u30a3\u30fc\u30eb\u30c9 {0} \u304c\u3042\u308a\u307e\u305b\u3093"},
        {"No frames on the current call stack", "\u73fe\u884c\u306e\u547c\u3073\u51fa\u3057\u30b9\u30bf\u30c3\u30af\u306b\u306f\u30d5\u30ec\u30fc\u30e0\u304c\u3042\u308a\u307e\u305b\u3093"},
        {"No linenumber information for", "{0} \u306e\u884c\u756a\u53f7\u60c5\u5831\u304c\u3042\u308a\u307e\u305b\u3093\u3002\u30c7\u30d0\u30c3\u30b0\u6a5f\u80fd\u3092\u30aa\u30f3\u306b\u3057\u3066\u30b3\u30f3\u30d1\u30a4\u30eb\u3057\u3066\u307f\u3066\u304f\u3060\u3055\u3044\u3002"},
        {"No local variables", "\u30ed\u30fc\u30ab\u30eb\u5909\u6570\u304c\u3042\u308a\u307e\u305b\u3093"},
        {"No method in", "{1} \u306b\u30e1\u30bd\u30c3\u30c9 {0} \u304c\u3042\u308a\u307e\u305b\u3093"},
        {"No method specified.", "\u30e1\u30bd\u30c3\u30c9\u304c\u6307\u5b9a\u3055\u308c\u3066\u3044\u307e\u305b\u3093\u3002"},
        {"No monitor numbered:", "\u6b21\u306e\u756a\u53f7\u306e\u30e2\u30cb\u30bf\u304c\u3042\u308a\u307e\u305b\u3093: {0}"},
        {"No monitors owned", "  \u6240\u6709\u3059\u308b\u30e2\u30cb\u30bf\u304c\u3042\u308a\u307e\u305b\u3093"},
        {"No object specified.", "\u30aa\u30d6\u30b8\u30a7\u30af\u30c8\u304c\u6307\u5b9a\u3055\u308c\u3066\u3044\u307e\u305b\u3093\u3002"},
        {"No objects specified.", "\u30aa\u30d6\u30b8\u30a7\u30af\u30c8\u304c\u6307\u5b9a\u3055\u308c\u3066\u3044\u307e\u305b\u3093\u3002"},
        {"No save index specified.", "\u4fdd\u5b58\u30a4\u30f3\u30c7\u30c3\u30af\u30b9\u304c\u6307\u5b9a\u3055\u308c\u3066\u3044\u307e\u305b\u3093\u3002"},
        {"No saved values", "\u4fdd\u5b58\u3055\u308c\u305f\u5024\u304c\u3042\u308a\u307e\u305b\u3093\u3002"},
        {"No source information available for:", "{0} \u306e\u30bd\u30fc\u30b9\u60c5\u5831\u304c\u5229\u7528\u3067\u304d\u307e\u305b\u3093"},
        {"No sourcedebugextension specified", "SourceDebugExtension \u304c\u6307\u5b9a\u3055\u308c\u3066\u3044\u307e\u305b\u3093\u3002"},
        {"No sourcepath specified.", "sourcepath \u304c\u6307\u5b9a\u3055\u308c\u3066\u3044\u307e\u305b\u3093\u3002"},
        {"No thread specified.", "\u30b9\u30ec\u30c3\u30c9\u304c\u6307\u5b9a\u3055\u308c\u3066\u3044\u307e\u305b\u3093\u3002"},
        {"No VM connected", "VM \u304c\u63a5\u7d9a\u3055\u308c\u3066\u3044\u307e\u305b\u3093"},
        {"No waiters", " \u5f85\u6a5f\u8005\u306f\u3044\u307e\u305b\u3093"},
        {"not a class", "{0} \u306f\u30af\u30e9\u30b9\u3067\u306f\u3042\u308a\u307e\u305b\u3093"},
        {"Not a monitor number:", "\u30e2\u30cb\u30bf\u756a\u53f7\u3067\u306f\u3042\u308a\u307e\u305b\u3093: ''{0}''"},
        {"not found (try the full name)", "{0} \u304c\u898b\u3064\u304b\u308a\u307e\u305b\u3093 (\u30d5\u30eb\u30cd\u30fc\u30e0\u3092\u5165\u308c\u3066\u304f\u3060\u3055\u3044)"},
        {"Not found:", "\u898b\u3064\u304b\u308a\u307e\u305b\u3093: {0}"},
        {"not found", "{0} \u304c\u898b\u3064\u304b\u308a\u307e\u305b\u3093"},
        {"Not owned", "  \u6240\u6709\u3057\u3066\u3044\u307e\u305b\u3093"},
        {"Not waiting for a monitor", "  \u30e2\u30cb\u30bf\u3092\u5f85\u6a5f\u3057\u3066\u3044\u307e\u305b\u3093"},
        {"Nothing suspended.", "\u4f55\u3082\u4e2d\u65ad\u3055\u308c\u3066\u3044\u307e\u305b\u3093\u3002"},
        {"object description and hex id", "({0}){1}"},
        {"Operation is not supported on the target VM", "\u3053\u306e\u64cd\u4f5c\u306f\u3001\u30bf\u30fc\u30b2\u30c3\u30c8 VM \u3067\u306f\u30b5\u30dd\u30fc\u30c8\u3055\u308c\u3066\u3044\u307e\u305b\u3093"},
        {"operation not yet supported", "\u3053\u306e\u64cd\u4f5c\u306f\u30b5\u30dd\u30fc\u30c8\u3055\u308c\u3066\u3044\u307e\u305b\u3093"},
        {"Owned by:", "  \u6240\u6709\u8005: {0}\u3001\u30a8\u30f3\u30c8\u30ea\u30ab\u30a6\u30f3\u30c8: {1,number,integer}"},
        {"Owned monitor:", "  \u6240\u6709\u30e2\u30cb\u30bf: {0}"},
        {"Parse exception:", "\u69cb\u6587\u89e3\u6790\u4f8b\u5916: {0}"},
        {"printbreakpointcommandusage", "\u4f7f\u7528\u6cd5: {0} <class>:<line_number> \u307e\u305f\u306f\n       {1} <class>.<method_name>[(argument_type,...)]"},
        {"Removed:", "\u524a\u9664\u6e08\u307f: {0}"},
        {"Requested stack frame is no longer active:", "\u8981\u6c42\u3055\u308c\u305f\u30b9\u30bf\u30c3\u30af\u30d5\u30ec\u30fc\u30e0\u306f\u30a2\u30af\u30c6\u30a3\u30d6\u3067\u306f\u3042\u308a\u307e\u305b\u3093: {0,number,integer}"},
        {"run <args> command is valid only with launched VMs", "'run <args>' \u30b3\u30de\u30f3\u30c9\u306f\u3001VM \u306e\u8d77\u52d5\u6642\u306b\u306e\u307f\u6709\u52b9\u3067\u3059\u3002"},
        {"run", "{0} \u3092\u5b9f\u884c\u3057\u307e\u3059"},
        {"saved", "{0} \u3092\u4fdd\u5b58\u3057\u307e\u3057\u305f"},
        {"Set deferred", "\u4fdd\u7559\u3057\u305f {0} \u3092\u8a2d\u5b9a\u3057\u307e\u3057\u305f"},
        {"Set", "{0} \u3092\u8a2d\u5b9a\u3057\u307e\u3057\u305f"},
        {"Source file not found:", "\u30bd\u30fc\u30b9\u30d5\u30a1\u30a4\u30eb\u304c\u898b\u3064\u304b\u308a\u307e\u305b\u3093: {0}"},
        {"source line number and line", "{0,number,integer}    {1}"},
        {"source line number current line and line", "{0,number,integer} => {1}"},
        {"sourcedebugextension", "SourceDebugExtension -- {0}"},
        {"Specify class and method", "\u30af\u30e9\u30b9\u304a\u3088\u3073\u30e1\u30bd\u30c3\u30c9\u3092\u6307\u5b9a\u3057\u3066\u304f\u3060\u3055\u3044"},
        {"Specify classes to redefine", "\u518d\u5b9a\u7fa9\u3059\u308b\u30af\u30e9\u30b9\u3092\u6307\u5b9a\u3057\u3066\u304f\u3060\u3055\u3044"},
        {"Specify file name for class", "\u30af\u30e9\u30b9 {0} \u306e\u30d5\u30a1\u30a4\u30eb\u540d\u3092\u6307\u5b9a\u3057\u3066\u304f\u3060\u3055\u3044"},
        {"stack frame dump with pc", "  [{0,number,integer}] {1}.{2} ({3})\u3001pc = {4}"},
        {"stack frame dump", "  [{0,number,integer}] {1}.{2} ({3})"},
        {"Step completed:", "\u30b9\u30c6\u30c3\u30d7\u5b8c\u4e86: "},
        {"Stopping due to deferred breakpoint errors.", "\u4fdd\u7559\u3057\u305f\u30d6\u30ec\u30fc\u30af\u30dd\u30a4\u30f3\u30c8\u306e\u30a8\u30e9\u30fc\u304c\u539f\u56e0\u3067\u505c\u6b62\u3057\u307e\u3057\u305f\u3002\n"},
        {"subclass:", "\u30b5\u30d6\u30af\u30e9\u30b9: {0}"},
        {"subinterface:", "\u30b5\u30d6\u30a4\u30f3\u30bf\u30d5\u30a7\u30fc\u30b9: {0}"},
        {"tab", "\t{0}"},
        {"Target VM failed to initialize.", "\u30bf\u30fc\u30b2\u30c3\u30c8 VM \u306e\u521d\u671f\u5316\u306b\u5931\u6557\u3057\u307e\u3057\u305f\u3002"},
        {"The application exited", "\u30a2\u30d7\u30ea\u30b1\u30fc\u30b7\u30e7\u30f3\u306f\u7d42\u4e86\u3057\u307e\u3057\u305f"},
        {"The application has been disconnected", "\u30a2\u30d7\u30ea\u30b1\u30fc\u30b7\u30e7\u30f3\u306f\u5207\u65ad\u3055\u308c\u3066\u3044\u307e\u3059"},
        {"The gc command is no longer necessary.", "'gc' \u30b3\u30de\u30f3\u30c9\u306f\u73fe\u5728\u306f\u5fc5\u8981\u3042\u308a\u307e\u305b\u3093\u3002\n" +
         "\u901a\u5e38\u3069\u304a\u308a\u3001\u3059\u3079\u3066\u306e\u30aa\u30d6\u30b8\u30a7\u30af\u30c8\u304c\u30ac\u30d9\u30fc\u30b8\u30b3\u30ec\u30af\u30c8\u3055\u308c\u3066\u3044\u307e\u3059\u3002'enablegc' \u304a\u3088\u3073 'disablegc'\n" +
         "\u30b3\u30de\u30f3\u30c9\u3092\u4f7f\u7528\u3057\u3066\u3001\u30ac\u30d9\u30fc\u30b8\u30b3\u30ec\u30af\u30b7\u30e7\u30f3\u3092\u30aa\u30d6\u30b8\u30a7\u30af\u30c8\u3054\u3068\u306b\u5236\u5fa1\u3057\u3066\u304f\u3060\u3055\u3044\u3002"},
        {"The load command is no longer supported.", "'load' \u30b3\u30de\u30f3\u30c9\u306f\u30b5\u30dd\u30fc\u30c8\u3055\u308c\u3066\u3044\u307e\u305b\u3093\u3002"},
        {"The memory command is no longer supported.", "'memory' \u30b3\u30de\u30f3\u30c9\u306f\u30b5\u30dd\u30fc\u30c8\u3055\u308c\u3066\u3044\u307e\u305b\u3093\u3002"},
        {"The VM does not use paths", "VM \u3067\u306f\u30d1\u30b9\u3092\u4f7f\u7528\u3057\u307e\u305b\u3093"},
        {"Thread is not running (no stack).", "\u30b9\u30ec\u30c3\u30c9\u306f\u5b9f\u884c\u3055\u308c\u3066\u3044\u307e\u305b\u3093 (\u30b9\u30bf\u30c3\u30af\u306a\u3057)\u3002"},
        {"Thread number not specified.", "\u30b9\u30ec\u30c3\u30c9\u756a\u53f7\u304c\u6307\u5b9a\u3055\u308c\u3066\u3044\u307e\u305b\u3093\u3002"},
        {"Thread:", "{0}:"},
        {"Thread Group:", "\u30b0\u30eb\u30fc\u30d7 {0}:"},
        {"Thread description name unknownStatus BP",  "  {0} {1} \u4e0d\u660e (\u30d6\u30ec\u30fc\u30af\u30dd\u30a4\u30f3\u30c8)"},
        {"Thread description name unknownStatus",     "  {0} {1} \u4e0d\u660e"},
        {"Thread description name zombieStatus BP",   "  {0} {1} \u30be\u30f3\u30d3 (\u30d6\u30ec\u30fc\u30af\u30dd\u30a4\u30f3\u30c8)"},
        {"Thread description name zombieStatus",      "  {0} {1} \u30be\u30f3\u30d3"},
        {"Thread description name runningStatus BP",  "  {0} {1} \u5b9f\u884c\u4e2d (\u30d6\u30ec\u30fc\u30af\u30dd\u30a4\u30f3\u30c8)"},
        {"Thread description name runningStatus",     "  {0} {1} \u5b9f\u884c\u4e2d"},
        {"Thread description name sleepingStatus BP", "  {0} {1} \u4f11\u6b62\u4e2d (\u30d6\u30ec\u30fc\u30af\u30dd\u30a4\u30f3\u30c8)"},
        {"Thread description name sleepingStatus",    "  {0} {1} \u4f11\u6b62\u4e2d"},
        {"Thread description name waitingStatus BP",  "  {0} {1} \u30e2\u30cb\u30bf\u3067\u5f85\u6a5f\u4e2d (\u30d6\u30ec\u30fc\u30af\u30dd\u30a4\u30f3\u30c8)"},
        {"Thread description name waitingStatus",     "  {0} {1} \u30e2\u30cb\u30bf\u3067\u5f85\u6a5f\u4e2d"},
        {"Thread description name condWaitstatus BP", "  {0} {1} \u72b6\u6cc1\u5f85\u6a5f\u4e2d (\u30d6\u30ec\u30fc\u30af\u30dd\u30a4\u30f3\u30c8)"},
        {"Thread description name condWaitstatus",    "  {0} {1} \u72b6\u6cc1\u5f85\u6a5f\u4e2d"},
        {"Thread has been resumed", "\u30b9\u30ec\u30c3\u30c9\u304c\u518d\u958b\u3055\u308c\u307e\u3057\u305f"},
        {"Thread not suspended", "\u30b9\u30ec\u30c3\u30c9\u306f\u4e2d\u65ad\u3055\u308c\u3066\u3044\u307e\u305b\u3093"},
        {"thread group number description name", "{0,number,integer}\u3002 {1} {2}"},
        {"Threadgroup name not specified.", "Threadgroup \u540d\u304c\u6307\u5b9a\u3055\u308c\u3066\u3044\u307e\u305b\u3093\u3002"},
        {"Threads must be suspended", "\u30b9\u30ec\u30c3\u30c9\u3092\u4e2d\u65ad\u3059\u308b\u5fc5\u8981\u304c\u3042\u308a\u307e\u3059"},
        {"trace method exit in effect for", "{0} \u306b\u5bfe\u3059\u308b\u30e1\u30bd\u30c3\u30c9\u7d42\u4e86\u306e\u30c8\u30ec\u30fc\u30b9\u6709\u52b9"},
        {"trace method exits in effect", "\u30e1\u30bd\u30c3\u30c9\u7d42\u4e86\u306e\u30c8\u30ec\u30fc\u30b9\u6709\u52b9"},
        {"trace methods in effect", "\u30e1\u30bd\u30c3\u30c9\u306e\u30c8\u30ec\u30fc\u30b9\u6709\u52b9"},
        {"trace go method exit in effect for", "{0} \u306b\u5bfe\u3059\u308b\u30e1\u30bd\u30c3\u30c9\u7d42\u4e86\u306e\u30c8\u30ec\u30fc\u30b9 (go \u30aa\u30d7\u30b7\u30e7\u30f3\u4ed8\u304d) \u6709\u52b9"},
        {"trace go method exits in effect", "\u30e1\u30bd\u30c3\u30c9\u7d42\u4e86\u306e\u30c8\u30ec\u30fc\u30b9 (go \u30aa\u30d7\u30b7\u30e7\u30f3\u4ed8\u304d) \u6709\u52b9"},
        {"trace go methods in effect", "\u30e1\u30bd\u30c3\u30c9\u306e\u30c8\u30ec\u30fc\u30b9 (go \u30aa\u30d7\u30b7\u30e7\u30f3\u4ed8\u304d) \u6709\u52b9"},
        {"trace not in effect", "\u30c8\u30ec\u30fc\u30b9\u304c\u6709\u52b9\u3067\u306f\u3042\u308a\u307e\u305b\u3093"},
        {"Unable to attach to target VM.", "\u30bf\u30fc\u30b2\u30c3\u30c8 VM \u306b\u63a5\u7d9a\u3067\u304d\u307e\u305b\u3093"},
        {"Unable to display process output:", "\u30d7\u30ed\u30bb\u30b9\u51fa\u529b\u3092\u8868\u793a\u3067\u304d\u307e\u305b\u3093: {0}"},
        {"Unable to launch target VM.", "\u30bf\u30fc\u30b2\u30c3\u30c8 VM \u3092\u8d77\u52d5\u3067\u304d\u307e\u305b\u3093"},
        {"Unable to set deferred", "\u4fdd\u7559\u3057\u305f {0} \u3092\u8a2d\u5b9a\u3067\u304d\u307e\u305b\u3093: {1}"},
        {"Unable to set main class and arguments", "\u30e1\u30a4\u30f3\u30af\u30e9\u30b9\u304a\u3088\u3073\u5f15\u6570\u3092\u8a2d\u5b9a\u3067\u304d\u307e\u305b\u3093"},
        {"Unable to set", "{0} \u3092\u8a2d\u5b9a\u3067\u304d\u307e\u305b\u3093: {1}"},
        {"Unexpected event type", "\u4e88\u671f\u3057\u306a\u3044\u7a2e\u985e\u306e\u30a4\u30d9\u30f3\u30c8: {0}"},
        {"unknown", "\u4e0d\u660e"},
        {"Unmonitoring", "{0} \u306f\u76e3\u8996\u3057\u3066\u3044\u307e\u305b\u3093\u3002"},
        {"Unrecognized command.  Try help...", "\u8a8d\u8b58\u3067\u304d\u306a\u3044\u30b3\u30de\u30f3\u30c9: ''{0}''\u3002\u30d8\u30eb\u30d7\u3092\u53c2\u7167\u3057\u3066\u304f\u3060\u3055\u3044..."},
        {"Usage: catch exception", "\u4f7f\u7528\u6cd5: catch [uncaught|caught|all] <class id>|<class pattern>"},
        {"Usage: ignore exception", "\u4f7f\u7528\u6cd5: ignore [uncaught|caught|all] <class id>|<class pattern>"},
        {"Usage: down [n frames]", "\u4f7f\u7528\u6cd5: down [\u30d5\u30ec\u30fc\u30e0\u6570 n]"},
        {"Usage: kill <thread id> <throwable>", "\u4f7f\u7528\u6cd5: kill <thread id> <throwable>"},
        {"Usage: read <command-filename>", "\u4f7f\u7528\u6cd5: read <command-filename>"},
        {"Usage: unmonitor <monitor#>", "\u4f7f\u7528\u6cd5: unmonitor <monitor#>"},
        {"Usage: up [n frames]", "\u4f7f\u7528\u6cd5: up [\u30d5\u30ec\u30fc\u30e0\u6570 n]"},
        {"Use java minus X to see", "'java -X' \u3092\u4f7f\u7528\u3057\u3066\u3001\u4f7f\u7528\u53ef\u80fd\u306a\u6a19\u6e96\u4ee5\u5916\u306e\u30aa\u30d7\u30b7\u30e7\u30f3\u3092\u8868\u793a\u3057\u3066\u304f\u3060\u3055\u3044\u3002"},
        {"Use stop at to set a breakpoint at a line number", "'stop at' \u3092\u4f7f\u7528\u3057\u3066\u3001\u884c\u756a\u53f7\u306b\u30d6\u30ec\u30fc\u30af\u30dd\u30a4\u30f3\u30c8\u3092\u8a2d\u5b9a\u3057\u3066\u304f\u3060\u3055\u3044\u3002"},
        {"VM already running. use cont to continue after events.", "VM \u306f\u65e2\u306b\u5b9f\u884c\u4e2d\u3067\u3059\u3002'cont' \u3092\u4f7f\u7528\u3057\u3066\u30a4\u30d9\u30f3\u30c8\u306e\u5f8c\u3082\u7d9a\u884c\u3057\u3066\u304f\u3060\u3055\u3044\u3002"},
        {"VM Started:", "VM \u304c\u8d77\u52d5\u3057\u307e\u3057\u305f: "},
        {"vmstartexception", "VM \u8d77\u52d5\u4f8b\u5916: {0}"},
        {"Waiting for monitor:", "   \u30e2\u30cb\u30bf\u5f85\u6a5f\u4e2d: {0}"},
        {"Waiting thread:", " \u30b9\u30ec\u30c3\u30c9\u5f85\u6a5f\u4e2d: {0}"},
        {"watch accesses of", "{0}.{1} \u3078\u306e\u30a2\u30af\u30bb\u30b9\u3092\u76e3\u8996\u3057\u307e\u3059\u3002"},
        {"watch modification of", "{0}.{1} \u3078\u306e\u5909\u66f4\u3092\u76e3\u8996\u3057\u307e\u3059\u3002"},
        {"zz help text",
             "** command list **\n" +
             "connectors                -- \u3053\u306e VM \u5185\u3067\u4f7f\u7528\u53ef\u80fd\u306a\u30b3\u30cd\u30af\u30bf\u3068\u30c8\u30e9\u30f3\u30b9\u30dd\u30fc\u30c8\u3092\u30ea\u30b9\u30c8\u8868\u793a\u3057\u307e\u3059\n" +
             "\n" +
             "run [class [args]]        -- \u30a2\u30d7\u30ea\u30b1\u30fc\u30b7\u30e7\u30f3\u306e\u30e1\u30a4\u30f3\u30af\u30e9\u30b9\u306e\u5b9f\u884c\u3092\u958b\u59cb\u3057\u307e\u3059\u3002\n" +
             "\n" +
             "threads [threadgroup]     -- \u30b9\u30ec\u30c3\u30c9\u3092\u4e00\u89a7\u8868\u793a\u3057\u307e\u3059\n" +
             "thread <thread id>        -- \u30c7\u30d5\u30a9\u30eb\u30c8\u30b9\u30ec\u30c3\u30c9\u3092\u8a2d\u5b9a\u3057\u307e\u3059\n" +
             "suspend [thread id(s)]    -- \u30b9\u30ec\u30c3\u30c9\u3092\u4e2d\u65ad\u3057\u307e\u3059 (\u30c7\u30d5\u30a9\u30eb\u30c8: \u3059\u3079\u3066)\n" +
             "resume [thread id(s)]     -- \u30b9\u30ec\u30c3\u30c9\u3092\u518d\u958b\u3057\u307e\u3059 (\u30c7\u30d5\u30a9\u30eb\u30c8: \u3059\u3079\u3066)\n" +
             "where [<thread id> | all]   -- \u30b9\u30ec\u30c3\u30c9\u306e\u30b9\u30bf\u30c3\u30af\u3092\u30c0\u30f3\u30d7\u3057\u307e\u3059\n" +
             "wherei [<thread id> | all]  -- \u30b9\u30ec\u30c3\u30c9\u306e\u30b9\u30bf\u30c3\u30af\u3092 PC \u60c5\u5831\u3068\u3044\u3063\u3057\u3087\u306b\u30c0\u30f3\u30d7\u3057\u307e\u3059\n" +
             "up [n frames]             -- \u30b9\u30ec\u30c3\u30c9\u306e\u30b9\u30bf\u30c3\u30af\u3092\u4e0a\u3078\u79fb\u52d5\u3057\u307e\u3059\n" +
             "down [n frames]           -- \u30b9\u30ec\u30c3\u30c9\u306e\u30b9\u30bf\u30c3\u30af\u3092\u4e0b\u3078\u79fb\u52d5\u3057\u307e\u3059\n" +
             "kill <thread id> <expr>      -- \u6307\u5b9a\u3055\u308c\u305f\u4f8b\u5916\u30aa\u30d6\u30b8\u30a7\u30af\u30c8\u3067\u30b9\u30ec\u30c3\u30c9\u3092\u7d42\u4e86\u3057\u307e\u3059\n" +
             "interrupt <thread id>        -- \u30b9\u30ec\u30c3\u30c9\u306b\u5272\u308a\u8fbc\u307f\u307e\u3059\n" +
             "\n" +
             "print <expr>              -- \u5f0f\u306e\u5024\u3092\u51fa\u529b\u3057\u307e\u3059\n" +
             "dump <expr>               -- \u3059\u3079\u3066\u306e\u30aa\u30d6\u30b8\u30a7\u30af\u30c8\u60c5\u5831\u3092\u51fa\u529b\u3057\u307e\u3059\n" +
             "eval <expr>               -- \u5f0f\u3092\u8a55\u4fa1\u3057\u307e\u3059 (print \u3068\u540c\u3058)\n" +
             "set <lvalue> = <expr>     -- \u30d5\u30a3\u30fc\u30eb\u30c9/\u5909\u6570/\u914d\u5217\u8981\u7d20\u306b\u65b0\u3057\u3044\u5024\u3092\u5272\u308a\u5f53\u3066\u307e\u3059\n" +
             "locals                    -- \u73fe\u884c\u306e\u30b9\u30bf\u30c3\u30af\u30d5\u30ec\u30fc\u30e0\u306e\u3059\u3079\u3066\u306e\u30ed\u30fc\u30ab\u30eb\u5909\u6570\u3092\u51fa\u529b\u3057\u307e\u3059\n" +
             "\n" +
             "classes                   -- \u73fe\u6642\u70b9\u3067\u65e2\u77e5\u306e\u30af\u30e9\u30b9\u3092\u4e00\u89a7\u8868\u793a\u3057\u307e\u3059\n" +
             "class <class id>          -- \u540d\u524d\u4ed8\u304d\u30af\u30e9\u30b9\u306e\u8a73\u7d30\u3092\u8868\u793a\u3057\u307e\u3059\n" +
             "methods <class id>        -- \u30af\u30e9\u30b9\u306e\u30e1\u30bd\u30c3\u30c9\u3092\u4e00\u89a7\u8868\u793a\u3057\u307e\u3059\n" +
             "fields <class id>         -- \u30af\u30e9\u30b9\u306e\u30d5\u30a3\u30fc\u30eb\u30c9\u3092\u4e00\u89a7\u8868\u793a\u3057\u307e\u3059\n" +
             "\n" +
             "threadgroups              -- \u30b9\u30ec\u30c3\u30c9\u30b0\u30eb\u30fc\u30d7\u3092\u4e00\u89a7\u8868\u793a\u3057\u307e\u3059\n" +
             "threadgroup <name>        -- \u73fe\u5728\u306e\u30b9\u30ec\u30c3\u30c9\u30b0\u30eb\u30fc\u30d7\u3092\u8a2d\u5b9a\u3057\u307e\u3059\n" +
             "\n" +
             "stop in <class id>.<method>[(argument_type,...)]\n" +
             "                          -- \u30e1\u30bd\u30c3\u30c9\u306b\u30d6\u30ec\u30fc\u30af\u30dd\u30a4\u30f3\u30c8\u3092\u8a2d\u5b9a\u3057\u307e\u3059\n" +
             "stop at <class id>:<line> -- \u884c\u306b\u30d6\u30ec\u30fc\u30af\u30dd\u30a4\u30f3\u30c8\u3092\u8a2d\u5b9a\u3057\u307e\u3059\n" +
             "clear <class id>.<method>[(argument_type,...)]\n" +
             "                          -- \u30e1\u30bd\u30c3\u30c9\u306e\u30d6\u30ec\u30fc\u30af\u30dd\u30a4\u30f3\u30c8\u3092\u89e3\u9664\u3057\u307e\u3059\n" +
             "clear <class id>:<line>   -- \u884c\u306e\u30d6\u30ec\u30fc\u30af\u30dd\u30a4\u30f3\u30c8\u3092\u89e3\u9664\u3057\u307e\u3059\n" +
             "clear                     -- \u30d6\u30ec\u30fc\u30af\u30dd\u30a4\u30f3\u30c8\u3092\u4e00\u89a7\u8868\u793a\u3057\u307e\u3059\n" +
             "catch [uncaught|caught|all] <class id>|<class pattern>\n" +
             "                          -- \u6307\u5b9a\u3055\u308c\u305f\u4f8b\u5916\u304c\u767a\u751f\u3057\u305f\u3089\u505c\u6b62\u3057\u307e\u3059\n" +
             "ignore [uncaught|caught|all] <class id>|<class pattern>\n" +
             "                          -- \u6307\u5b9a\u3055\u308c\u305f\u4f8b\u5916\u306e 'catch' \u3092\u53d6\u308a\u6d88\u3057\u307e\u3059\n" +
             "watch [access|all] <class id>.<field name>\n" +
             "                          -- \u30d5\u30a3\u30fc\u30eb\u30c9\u3078\u306e\u30a2\u30af\u30bb\u30b9/\u4fee\u6b63\u3092\u76e3\u8996\u3057\u307e\u3059\n" +
             "unwatch [access|all] <class id>.<field name>\n" +
             "                          -- \u30d5\u30a3\u30fc\u30eb\u30c9\u3078\u306e\u30a2\u30af\u30bb\u30b9/\u4fee\u6b63\u306e\u76e3\u8996\u3092\u4e2d\u6b62\u3057\u307e\u3059\n" +
             "trace methods [thread]    -- \u30e1\u30bd\u30c3\u30c9\u306e\u958b\u59cb/\u7d42\u4e86\u3092\u8ffd\u8de1\u3057\u307e\u3059\n" +
             "untrace methods [thread]  -- \u30e1\u30bd\u30c3\u30c9\u306e\u958b\u59cb/\u7d42\u4e86\u306e\u8ffd\u8de1\u3092\u4e2d\u6b62\u3057\u307e\u3059\n" +
             "step                      -- \u73fe\u5728\u306e\u884c\u3092\u5b9f\u884c\u3057\u307e\u3059\n" +
             "step up                   -- \u73fe\u5728\u306e\u30e1\u30bd\u30c3\u30c9\u304c\u547c\u3073\u51fa\u3057\u5074\u306b\u623b\u308b\u307e\u3067\u5b9f\u884c\u3057\u307e\u3059\n" +
             "stepi                     -- \u73fe\u5728\u306e\u547d\u4ee4\u3092\u5b9f\u884c\u3057\u307e\u3059\n" +
             "next                      -- 1 \u884c\u30b9\u30c6\u30c3\u30d7\u5b9f\u884c\u3057\u307e\u3059 (\u547c\u3073\u51fa\u3057\u3092\u30b9\u30c6\u30c3\u30d7\u30aa\u30fc\u30d0\u30fc\u3057\u307e\u3059)\n" +
             "cont                      -- \u30d6\u30ec\u30fc\u30af\u30dd\u30a4\u30f3\u30c8\u304b\u3089\u7d99\u7d9a\u3057\u3066\u5b9f\u884c\u3057\u307e\u3059\n" +
             "\n" +
             "list [line number|method] -- \u30bd\u30fc\u30b9\u30b3\u30fc\u30c9\u3092\u51fa\u529b\u3057\u307e\u3059\n" +
             "use (or sourcepath) [source file path]\n" +
             "                          -- \u30bd\u30fc\u30b9\u30d1\u30b9\u3092\u8868\u793a\u307e\u305f\u306f\u5909\u66f4\u3057\u307e\u3059\n" +
             "exclude [<class pattern>, ... | \"none\"]\n" +
             "                          -- \u6307\u5b9a\u3057\u305f\u30af\u30e9\u30b9\u306e\u30b9\u30c6\u30c3\u30d7\u307e\u305f\u306f\u30e1\u30bd\u30c3\u30c9\u30a4\u30d9\u30f3\u30c8\u3092\u5831\u544a\u3057\u307e\u305b\u3093\n" +
             "classpath                 -- \u30bf\u30fc\u30b2\u30c3\u30c8 VM \u306e\u30af\u30e9\u30b9\u30d1\u30b9\u60c5\u5831\u3092\u51fa\u529b\u3057\u307e\u3059\n" +
             "\n" +
             "monitor <command>         -- \u30d7\u30ed\u30b0\u30e9\u30e0\u306e\u505c\u6b62\u6642\u306b\u6bce\u56de\u30b3\u30de\u30f3\u30c9\u3092\u5b9f\u884c\u3057\u307e\u3059\n" +
             "monitor                   -- \u30e2\u30cb\u30bf\u3092\u4e00\u89a7\u8868\u793a\u3057\u307e\u3059\n" +
             "unmonitor <monitor#>      -- \u30e2\u30cb\u30bf\u3092\u524a\u9664\u3057\u307e\u3059\n" +
             "read <filename>           -- \u30b3\u30de\u30f3\u30c9\u30d5\u30a1\u30a4\u30eb\u3092\u8aad\u307f\u8fbc\u307f\u3001\u5b9f\u884c\u3057\u307e\u3059\n" +
             "\n" +
             "lock <expr>               -- \u30aa\u30d6\u30b8\u30a7\u30af\u30c8\u306e\u30ed\u30c3\u30af\u60c5\u5831\u3092\u51fa\u529b\u3057\u307e\u3059\n" +
             "threadlocks [thread id]   -- \u30b9\u30ec\u30c3\u30c9\u306e\u30ed\u30c3\u30af\u60c5\u5831\u3092\u51fa\u529b\u3057\u307e\u3059\n" +
             "\n" +
             "pop                       -- \u73fe\u5728\u306e\u30d5\u30ec\u30fc\u30e0\u3092\u542b\u3080\u3059\u3079\u3066\u306e\u30b9\u30bf\u30c3\u30af\u3092\u30dd\u30c3\u30d7\u3057\u307e\u3059\n" +
             "reenter                   -- \u30dd\u30c3\u30d7\u3068\u540c\u3058\u3067\u3059\u304c\u3001\u73fe\u5728\u306e\u30d5\u30ec\u30fc\u30e0\u304c\u518d\u5165\u529b\u3055\u308c\u307e\u3059\n" +
             "redefine <class id> <class file name>\n" +
             "                          -- \u30af\u30e9\u30b9\u306e\u30b3\u30fc\u30c9\u3092\u518d\u5b9a\u7fa9\u3057\u307e\u3059\n" +
             "\n" +
             "disablegc <expr>          -- \u30aa\u30d6\u30b8\u30a7\u30af\u30c8\u306e\u30ac\u30d9\u30fc\u30b8\u30b3\u30ec\u30af\u30b7\u30e7\u30f3\u3092\u6291\u6b62\u3057\u307e\u3059\n" +
             "enablegc <expr>           -- \u30aa\u30d6\u30b8\u30a7\u30af\u30c8\u306e\u30ac\u30d9\u30fc\u30b8\u30b3\u30ec\u30af\u30b7\u30e7\u30f3\u3092\u8a31\u53ef\u3057\u307e\u3059\n" +
             "\n" +
             "!!                        -- \u6700\u5f8c\u306e\u30b3\u30de\u30f3\u30c9\u3092\u7e70\u308a\u8fd4\u3057\u307e\u3059\n" +
             "<n> <command>             -- \u30b3\u30de\u30f3\u30c9\u3092 n \u56de\u7e70\u308a\u8fd4\u3057\u307e\u3059\n" +
             "help (or ?)               -- \u30b3\u30de\u30f3\u30c9\u3092\u4e00\u89a7\u8868\u793a\u3057\u307e\u3059\n" +
             "version                   -- \u30d0\u30fc\u30b8\u30e7\u30f3\u60c5\u5831\u3092\u51fa\u529b\u3057\u307e\u3059\n" +
             "exit (or quit)            -- \u30c7\u30d0\u30c3\u30ac\u3092\u7d42\u4e86\u3057\u307e\u3059\n" +
             "\n" +
             "<class id>: \u30d1\u30c3\u30b1\u30fc\u30b8\u4fee\u98fe\u5b50\u3092\u542b\u3080\u5b8c\u5168\u306a\u30af\u30e9\u30b9\u540d\n" +
             "<class pattern>: \u5148\u982d\u304b\u672b\u5c3e\u306b\u30ef\u30a4\u30eb\u30c9\u30ab\u30fc\u30c9 ('*') \u306e\u4ed8\u3044\u305f\u30af\u30e9\u30b9\u540d\n" +
             "<thread id>: 'threads' \u30b3\u30de\u30f3\u30c9\u3067\u5831\u544a\u3055\u308c\u308b\u30b9\u30ec\u30c3\u30c9\u756a\u53f7\n" +
             "<expr>: Java(tm) \u30d7\u30ed\u30b0\u30e9\u30df\u30f3\u30b0\u8a00\u8a9e\u5f0f\n" +
             "\u3082\u3063\u3068\u3082\u4e00\u822c\u7684\u306a\u69cb\u6587\u304c\u30b5\u30dd\u30fc\u30c8\u3055\u308c\u307e\u3059\u3002\n" +
             "\n" +
             "user.home \u307e\u305f\u306f user.dir \u4e0b\u306e \"jdb.ini\" \u304b \".jdbrc\" \u306e\n" +
             "\u3044\u305a\u308c\u304b\u306b\u8d77\u52d5\u30b3\u30de\u30f3\u30c9\u3092\u8a2d\u5b9a\u3059\u308b\u3053\u3068\u304c\u3067\u304d\u307e\u3059\u3002"},
        {"zz usage text",
             "\u4f7f\u7528\u6cd5: {0} <options> <class> <arguments>\n" +
"\n" +
             "\u3053\u3053\u3067 options \u306f\u6b21\u306e\u3068\u304a\u308a\u3067\u3059\u3002\n" +
             "    -help             \u3053\u306e\u30e1\u30c3\u30bb\u30fc\u30b8\u3092\u51fa\u529b\u3057\u3066\u7d42\u4e86\u3057\u307e\u3059\n" +
             "    -sourcepath <\u30c7\u30a3\u30ec\u30af\u30c8\u30ea\u306f \"{1}\" \u3067\u533a\u5207\u308a\u307e\u3059>\n" +
             "                      \u30bd\u30fc\u30b9\u30d5\u30a1\u30a4\u30eb\u3092\u691c\u7d22\u3059\u308b\u30c7\u30a3\u30ec\u30af\u30c8\u30ea\u3067\u3059\n" +
             "    -attach <address>\n" +
             "                      \u6a19\u6e96\u7684\u306a\u30b3\u30cd\u30af\u30bf\u3092\u4f7f\u3063\u3066\u3001\u6307\u5b9a\u3057\u305f\u30a2\u30c9\u30ec\u30b9\u3067\u5b9f\u884c\u4e2d\u306e VM \u306b\u63a5\u7d9a\u3057\u307e\u3059\n" +
             "    -listen <address>\n" +
             "                      \u6a19\u6e96\u7684\u306a\u30b3\u30cd\u30af\u30bf\u3092\u4f7f\u3063\u3066\u3001\u6307\u5b9a\u3057\u305f\u30a2\u30c9\u30ec\u30b9\u3067\u63a5\u7d9a\u3059\u308b\u305f\u3081\u306b\u5b9f\u884c\u4e2d\u306e VM \u3092\u5f85\u6a5f\u3057\u307e\u3059\n" +
             "    -listenany\n" +
             "                      \u6a19\u6e96\u7684\u306a\u30b3\u30cd\u30af\u30bf\u3092\u4f7f\u3063\u3066\u3001\u4f7f\u7528\u53ef\u80fd\u306a\u4efb\u610f\u306e\u30a2\u30c9\u30ec\u30b9\u3067\u63a5\u7d9a\u3059\u308b\u305f\u3081\u306b\u5b9f\u884c\u4e2d\u306e VM \u3092\u5f85\u6a5f\u3057\u307e\u3059\n" +
             "    -launch\n" +
             "                      ''run'' \u30b3\u30de\u30f3\u30c9\u3092\u5f85\u305f\u305a\u306b VM \u3092\u305f\u3060\u3061\u306b\u8d77\u52d5\u3057\u307e\u3059\n" +
             "    -listconnectors   \u3053\u306e VM \u5185\u3067\u4f7f\u7528\u53ef\u80fd\u306a\u30b3\u30cd\u30af\u30bf\u3092\u30ea\u30b9\u30c8\u8868\u793a\u3057\u307e\u3059\n" +
             "    -connect <connector-name>:<name1>=<value1>,...\n" +
             "                      \u540d\u524d\u4ed8\u304d\u30b3\u30cd\u30af\u30bf\u3068\u4e00\u89a7\u8868\u793a\u3055\u308c\u305f\u5f15\u6570\u5024\u3092\u4f7f\u7528\u3057\u3066\u30bf\u30fc\u30b2\u30c3\u30c8 VM \u306b\u63a5\u7d9a\u3057\u307e\u3059\n" +
             "    -dbgtrace [flags] {0} \u3092\u30c7\u30d0\u30c3\u30b0\u3059\u308b\u305f\u3081\u306e\u51fa\u529b\u60c5\u5831\n" +
             "    -tclient          Hotspot(tm) Performance Engine (\u30af\u30e9\u30a4\u30a2\u30f3\u30c8) \u3067\u30a2\u30d7\u30ea\u30b1\u30fc\u30b7\u30e7\u30f3\u3092\u5b9f\u884c\u3057\u307e\u3059\n" +
             "    -tserver          Hotspot(tm) Performance Engine (\u30b5\u30fc\u30d0) \u3067\u30a2\u30d7\u30ea\u30b1\u30fc\u30b7\u30e7\u30f3\u3092\u5b9f\u884c\u3057\u307e\u3059\n" +
             "\n" +
             "\u30c7\u30d0\u30c3\u30b0\u30d7\u30ed\u30bb\u30b9\u306b\u8ee2\u9001\u3055\u308c\u305f\u30aa\u30d7\u30b7\u30e7\u30f3:\n" +
             "    -v -verbose[:class|gc|jni]\n" +
             "                      \u5197\u9577\u30e2\u30fc\u30c9\u3092\u30aa\u30f3\u306b\u3057\u307e\u3059\u3002\n" +
             "    -D<name>=<value>  \u30b7\u30b9\u30c6\u30e0\u30d7\u30ed\u30d1\u30c6\u30a3\u3092\u8a2d\u5b9a\u3057\u307e\u3059\n" +
             "    -classpath <\u30c7\u30a3\u30ec\u30af\u30c8\u30ea\u306f \"{1}\" \u3067\u533a\u5207\u308a\u307e\u3059>\n" +
             "                      \u30af\u30e9\u30b9\u306e\u691c\u7d22\u5148\u306e\u30c7\u30a3\u30ec\u30af\u30c8\u30ea\u3092\u4e00\u89a7\u8868\u793a\u3057\u307e\u3059\u3002\n" +
             "    -X<option>        \u6a19\u6e96\u4ee5\u5916\u306e\u30bf\u30fc\u30b2\u30c3\u30c8 VM \u30aa\u30d7\u30b7\u30e7\u30f3\n" +
             "\n" +
             "<class> \u306f\u3001\u30c7\u30d0\u30c3\u30b0\u3092\u958b\u59cb\u3059\u308b\u30af\u30e9\u30b9\u540d\u3067\u3059\u3002\n" +
             "<arguments> \u306f\u3001<class> \u306e main() \u30e1\u30bd\u30c3\u30c9\u306b\u6e21\u3059\u5f15\u6570\u3067\u3059\u3002\n" +
             "\n" +
             "\u30b3\u30de\u30f3\u30c9\u30d8\u30eb\u30d7\u3092\u8868\u793a\u3059\u308b\u306b\u306f\u3001{0} \u30d7\u30ed\u30f3\u30d7\u30c8\u306b ''help'' \u3068\u5165\u529b\u3057\u307e\u3059\u3002"},
        // END OF MATERIAL TO LOCALIZE
        };
    }
}
