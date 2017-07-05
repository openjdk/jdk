/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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
public class TTYResources_zh_CN extends java.util.ListResourceBundle {


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
        {"** classes list **", "** \u7c7b\u5217\u8868 **\n{0}"},
        {"** fields list **", "** \u5b57\u6bb5\u5217\u8868 **\n{0}"},
        {"** methods list **", "** \u65b9\u6cd5\u5217\u8868 **\n{0}"},
        {"*** Reading commands from", "*** \u6b63\u5728\u4ece {0} \u4e2d\u8bfb\u53d6\u547d\u4ee4"},
        {"All threads resumed.", "\u6240\u6709\u7ebf\u7a0b\u5df2\u6062\u590d\u3002"},
        {"All threads suspended.", "\u6240\u6709\u7ebf\u7a0b\u5df2\u6682\u505c\u3002"},
        {"Argument is not defined for connector:", "\u6ca1\u6709\u4e3a\u8fde\u63a5\u5668\u5b9a\u4e49\u53c2\u6570 {0}\uff1a {1}"},
        {"Arguments match no method", "\u53c2\u6570\u4e0e\u65b9\u6cd5\u4e0d\u5339\u914d"},
        {"Array:", "\u6570\u7ec4\uff1a{0}"},
        {"Array element is not a method", "\u6570\u7ec4\u5143\u7d20\u4e0d\u662f\u65b9\u6cd5"},
        {"Array index must be a integer type", "\u6570\u7ec4\u7d22\u5f15\u5fc5\u987b\u4e3a\u6574\u6570\u7c7b\u578b"},
        {"base directory:", "\u57fa\u672c\u76ee\u5f55\uff1a{0}"},
        {"bootclasspath:", "\u5f15\u5bfc\u7c7b\u8def\u5f84\uff1a{0}"},
        {"Breakpoint hit:", "\u65ad\u70b9\u547d\u4e2d\uff1a "},
        {"breakpoint", "\u65ad\u70b9 {0}"},
        {"Breakpoints set:", "\u65ad\u70b9\u96c6\uff1a"},
        {"Breakpoints can be located only in classes.", "\u65ad\u70b9\u53ea\u80fd\u4f4d\u4e8e\u7c7b\u4e2d\u3002{0} \u662f\u63a5\u53e3\u6216\u6570\u7ec4\u3002"},
        {"Can only trace", "\u53ea\u80fd\u8ddf\u8e2a 'methods'\u3001'method exit' \u6216 'method exits'"},
        {"cannot redefine existing connection", "{0} \u65e0\u6cd5\u91cd\u65b0\u5b9a\u4e49\u73b0\u6709\u8fde\u63a5"},
        {"Cannot assign to a method invocation", "\u65e0\u6cd5\u6307\u5b9a\u7ed9\u65b9\u6cd5\u8c03\u7528"},
        {"Cannot specify command line with connector:", "\u65e0\u6cd5\u4f7f\u7528\u8fde\u63a5\u5668 {0} \u6307\u5b9a\u547d\u4ee4\u884c"},
        {"Cannot specify target vm arguments with connector:", "\u65e0\u6cd5\u4f7f\u7528\u8fde\u63a5\u5668 {0} \u6307\u5b9a\u76ee\u6807 VM \u53c2\u6570"},
        {"Class containing field must be specified.", "\u5fc5\u987b\u6307\u5b9a\u5305\u542b\u5b57\u6bb5\u7684\u7c7b\u3002"},
        {"Class:", "\u7c7b\uff1a{0}"},
        {"Classic VM no longer supported.", "\u4e0d\u518d\u652f\u6301 Classic VM\u3002"},
        {"classpath:", "\u7c7b\u8def\u5f84\uff1a{0}"},
        {"colon mark", ":"},
        {"colon space", ": "},
        {"Command is not supported on the target VM", "\u76ee\u6807 VM \u4e0d\u652f\u6301\u547d\u4ee4 \"{0}\""},
        {"Command is not supported on a read-only VM connection", "\u53ea\u8bfb VM \u8fde\u63a5\u4e0d\u652f\u6301\u547d\u4ee4 \"{0}\""},
        {"Command not valid until the VM is started with the run command", "\u4f7f\u7528 \"run\" \u547d\u4ee4\u542f\u52a8 VM \u4e4b\u540e\uff0c\u547d\u4ee4 \"{0}\" \u624d\u6709\u6548"},
        {"Condition must be boolean", "\u6761\u4ef6\u5fc5\u987b\u4e3a\u5e03\u5c14\u503c"},
        {"Connector and Transport name", "  \u8fde\u63a5\u5668\uff1a{0}  \u4f20\u9001\u5668\uff1a{1}"},
        {"Connector argument nodefault", "    \u53c2\u6570\uff1a{0}\uff08\u65e0\u9ed8\u8ba4\u503c\uff09"},
        {"Connector argument default", "    \u53c2\u6570\uff1a{0} \u9ed8\u8ba4\u503c\uff1a{1}"},
        {"Connector description", "    \u63cf\u8ff0\uff1a{0}"},
        {"Connector required argument nodefault", "    \u5fc5\u9700\u53c2\u6570\uff1a{0}\uff08\u65e0\u9ed8\u8ba4\u503c\uff09"},
        {"Connector required argument default", "    \u5fc5\u9700\u53c2\u6570\uff1a{0} \u9ed8\u8ba4\u503c\uff1a{1}"},
        {"Connectors available", "\u53ef\u7528\u7684\u8fde\u63a5\u5668\u5305\u62ec\uff1a"},
        {"Constant is not a method", "\u5e38\u91cf\u4e0d\u662f\u65b9\u6cd5"},
        {"Could not open:", "\u65e0\u6cd5\u6253\u5f00\uff1a{0}"},
        {"Current method is native", "\u5f53\u524d\u65b9\u6cd5\u662f\u672c\u673a\u65b9\u6cd5"},
        {"Current thread died. Execution continuing...", "\u5f53\u524d\u7ebf\u7a0b {0} \u5df2\u7ec8\u6b62\u3002\u6b63\u5728\u7ee7\u7eed\u6267\u884c..."},
        {"Current thread isnt suspended.", "\u5f53\u524d\u7ebf\u7a0b\u672a\u6682\u505c\u3002"},
        {"Current thread not set.", "\u5f53\u524d\u7ebf\u7a0b\u672a\u8bbe\u7f6e\u3002"},
        {"dbgtrace flag value must be an integer:", "dbgtrace \u6807\u5fd7\u503c\u5fc5\u987b\u4e3a\u6574\u6570\uff1a {0}"},
        {"Deferring.", "\u6b63\u5728\u5ef6\u8fdf {0}\u3002\n\u5c06\u5728\u88c5\u5165\u7c7b\u4e4b\u540e\u5bf9\u5176\u8fdb\u884c\u8bbe\u7f6e\u3002"},
        {"End of stack.", "\u5806\u6808\u7ed3\u5c3e\u3002"},
        {"Error popping frame", "\u5f39\u51fa\u5e27\u65f6\u51fa\u9519 - {0}"},
        {"Error reading file", "\u8bfb\u53d6 \"{0}\" \u65f6\u51fa\u9519 - {1}"},
        {"Error redefining class to file", "\u5c06 {0} \u91cd\u65b0\u5b9a\u4e49\u5230 {1} \u65f6\u51fa\u9519 - {2}"},
        {"exceptionSpec all", "\u6240\u6709 {0}"},
        {"exceptionSpec caught", "\u6355\u6349\u5230 {0}"},
        {"exceptionSpec uncaught", "\u672a\u6355\u6349\u5230 {0}"},
        {"Exception in expression:", "\u8868\u8fbe\u5f0f\u4e2d\u51fa\u73b0\u5f02\u5e38\uff1a{0}"},
        {"Exception occurred caught", "\u51fa\u73b0\u5f02\u5e38\uff1a{0}\uff08\u5728 {1} \u88ab\u6355\u6349\uff09"},
        {"Exception occurred uncaught", "\u51fa\u73b0\u5f02\u5e38\uff1a{0}\uff08\u672a\u6355\u6349\uff09"},
        {"Exceptions caught:", "\u51fa\u73b0\u8fd9\u4e9b\u5f02\u5e38\u65f6\u4e2d\u65ad\uff1a"},
        {"expr is null", "{0} = null"},
        {"expr is value", "{0} = {1}"},
        {"expr is value <collected>", "  {0} = {1} <\u5df2\u6536\u96c6>"},
        {"Expression cannot be void", "\u8868\u8fbe\u5f0f\u4e0d\u80fd\u6ca1\u6709\u8fd4\u56de\u503c"},
        {"Expression must evaluate to an object", "\u8868\u8fbe\u5f0f\u7684\u503c\u5fc5\u987b\u4e3a\u5bf9\u8c61"},
        {"extends:", "\u6269\u5c55\uff1a {0}"},
        {"Failed reading output", "\u8bfb\u53d6\u5b50 java \u89e3\u91ca\u7a0b\u5e8f\u7684\u8f93\u51fa\u5931\u8d25\u3002"},
        {"Fatal error", "\u81f4\u547d\u9519\u8bef\uff1a"},
        {"Field access encountered before after", "\u5b57\u6bb5 ({0}) \u4e3a {1}\uff0c\u5c06 {2}\uff1a "},
        {"Field access encountered", "\u9047\u5230\u5b57\u6bb5 ({0}) \u8bbf\u95ee\uff1a "},
        {"Field to unwatch not specified", "\u672a\u6307\u5b9a\u8981\u53d6\u6d88\u76d1\u89c6\u7684\u5b57\u6bb5\u3002"},
        {"Field to watch not specified", "\u672a\u6307\u5b9a\u8981\u76d1\u89c6\u7684\u5b57\u6bb5\u3002"},
        {"GC Disabled for", "\u5df2\u7981\u7528 {0} \u7684 GC\uff1a"},
        {"GC Enabled for", "\u5df2\u542f\u7528 {0} \u7684 GC\uff1a"},
        {"grouping begin character", "{"},
        {"grouping end character", "}"},
        {"Illegal Argument Exception", "\u975e\u6cd5\u53c2\u6570\u5f02\u5e38"},
        {"Illegal connector argument", "\u975e\u6cd5\u8fde\u63a5\u5668\u53c2\u6570\uff1a {0}"},
        {"implementor:", "\u5b9e\u73b0\u8005\uff1a {0}"},
        {"implements:", "\u5b9e\u73b0\uff1a {0}"},
        {"Initializing progname", "\u6b63\u5728\u521d\u59cb\u5316 {0}..."},
        {"Input stream closed.", "\u8f93\u5165\u6d41\u5df2\u7ed3\u675f\u3002"},
        {"Interface:", "\u63a5\u53e3\uff1a {0}"},
        {"Internal debugger error.", "\u5185\u90e8\u8c03\u8bd5\u5668\u9519\u8bef\u3002"},
        {"Internal error: null ThreadInfo created", "\u5185\u90e8\u9519\u8bef\uff1a\u521b\u5efa\u4e86\u7a7a\u7684 ThreadInfo"},
        {"Internal error; unable to set", "\u5185\u90e8\u9519\u8bef\uff1b\u65e0\u6cd5\u8bbe\u7f6e {0}"},
        {"Internal exception during operation:", "\u5728\u64cd\u4f5c\u8fc7\u7a0b\u4e2d\u51fa\u73b0\u5185\u90e8\u5f02\u5e38\uff1a\n    {0}"},
        {"Internal exception:", "\u5185\u90e8\u5f02\u5e38\uff1a"},
        {"Invalid argument type name", "\u53c2\u6570\u7c7b\u578b\u540d\u79f0\u65e0\u6548"},
        {"Invalid assignment syntax", "\u6307\u5b9a\u8bed\u6cd5\u65e0\u6548"},
        {"Invalid command syntax", "\u547d\u4ee4\u8bed\u6cd5\u65e0\u6548"},
        {"Invalid connect type", "\u8fde\u63a5\u7c7b\u578b\u65e0\u6548"},
        {"Invalid consecutive invocations", "\u8fde\u7eed\u8c03\u7528\u65e0\u6548"},
        {"Invalid exception object", "\u5f02\u5e38\u5bf9\u8c61\u65e0\u6548"},
        {"Invalid method specification:", "\u65e0\u6548\u7684\u65b9\u6cd5\u8bf4\u660e\uff1a {0}"},
        {"Invalid option on class command", "\u7c7b\u547d\u4ee4\u7684\u9009\u9879\u65e0\u6548"},
        {"invalid option", "\u65e0\u6548\u7684\u9009\u9879\uff1a {0}"},
        {"Invalid thread status.", "\u7ebf\u7a0b\u72b6\u6001\u65e0\u6548\u3002"},
        {"Invalid transport name:", "\u65e0\u6548\u7684\u4f20\u9001\u5668\u540d\u79f0\uff1a {0}"},
        {"I/O exception occurred:", "\u51fa\u73b0 I/O \u5f02\u5e38\uff1a {0}"},
        {"is an ambiguous method name in", "\"{0}\" \u5728 \"{1}\" \u4e2d\u662f\u4e0d\u660e\u786e\u7684\u65b9\u6cd5\u540d\u79f0"},
        {"is an invalid line number for",  "\u5bf9\u4e8e {1}\uff0c{0,number,integer} \u662f\u65e0\u6548\u7684\u884c\u53f7"},
        {"is not a valid class name", "\"{0}\" \u662f\u65e0\u6548\u7684\u7c7b\u540d\u3002"},
        {"is not a valid field name", "\"{0}\" \u662f\u65e0\u6548\u7684\u5b57\u6bb5\u540d\u3002"},
        {"is not a valid id or class name", "\"{0}\" \u662f\u65e0\u6548\u7684 ID \u6216\u7c7b\u540d\u3002"},
        {"is not a valid line number or method name for", "\u5bf9\u4e8e\u7c7b \"{1}\"\uff0c\"{0}\" \u662f\u65e0\u6548\u7684\u884c\u53f7\u6216\u65b9\u6cd5\u540d"},
        {"is not a valid method name", "\"{0}\" \u662f\u65e0\u6548\u7684\u65b9\u6cd5\u540d\u3002"},
        {"is not a valid thread id", "\"{0}\" \u662f\u65e0\u6548\u7684\u7ebf\u7a0b ID\u3002"},
        {"is not a valid threadgroup name", "\"{0}\" \u662f\u65e0\u6548\u7684\u7ebf\u7a0b\u7ec4\u540d\u79f0\u3002"},
        {"jdb prompt with no current thread", "> "},
        {"jdb prompt thread name and current stack frame", "{0}[{1,number,integer}] "},
        {"killed", "{0} \u5df2\u4e2d\u6b62"},
        {"killing thread:", "\u6b63\u5728\u4e2d\u6b62\u7ebf\u7a0b\uff1a {0}"},
        {"Line number information not available for", "\u6b64\u4f4d\u7f6e\u7684\u6e90\u884c\u53f7\u4e0d\u53ef\u7528\u3002"},
        {"line number", "\uff1a{0,number,integer}"},
        {"list field typename and name", "{0} {1}\n"},
        {"list field typename and name inherited", "{0} {1}\uff08\u4ece {2}\u7ee7\u627f\uff09\n"},
        {"list field typename and name hidden", "{0} {1} \uff08\u9690\u85cf\uff09\n"},
        {"Listening at address:", "\u6b63\u5728\u4ee5\u4e0b\u5730\u5740\u4fa6\u542c\uff1a {0}"},
        {"Local variable information not available.", "\u5c40\u90e8\u53d8\u91cf\u4fe1\u606f\u4e0d\u53ef\u7528\u3002\u4f7f\u7528 -g \u7f16\u8bd1\u4ee5\u751f\u6210\u53d8\u91cf\u4fe1\u606f"},
        {"Local variables:", "\u5c40\u90e8\u53d8\u91cf\uff1a"},
        {"<location unavailable>", "<\u4f4d\u7f6e\u4e0d\u53ef\u7528>"},
        {"location", "\"thread={0}\", {1}"},
        {"locationString", "{0}.{1}(), line={2,number,integer} bci={3,number,integer}"},
        {"Main class and arguments must be specified", "\u5fc5\u987b\u6307\u5b9a\u4e3b\u7c7b\u548c\u53c2\u6570"},
        {"Method arguments:", "\u65b9\u6cd5\u53c2\u6570\uff1a"},
        {"Method entered:", "\u65b9\u6cd5\u5df2\u8f93\u5165: "},
        {"Method exited:",  "\u65b9\u6cd5\u5df2\u9000\u51fa"},
        {"Method exitedValue:", "\u65b9\u6cd5\u5df2\u9000\u51fa: \u8fd4\u56de\u503c = {0}\uff0c"},
        {"Method is overloaded; specify arguments", "\u65b9\u6cd5 {0} \u5df2\u8fc7\u8f7d\uff1b\u6307\u5b9a\u53c2\u6570"},
        {"minus version", "\u8fd9\u662f {0} \u7248\u672c {1,number,integer}.{2,number,integer}\uff08J2SE \u7248\u672c {3}\uff09"},
        {"Monitor information for thread", "\u7ebf\u7a0b {0} \u7684\u76d1\u89c6\u5668\u4fe1\u606f\uff1a"},
        {"Monitor information for expr", "{0} ({1}) \u7684\u76d1\u89c6\u5668\u4fe1\u606f\uff1a"},
        {"More than one class named", "\u547d\u540d\u4e86\u591a\u4e2a\u7c7b\uff1a ''{0}''"},
        {"native method", "\u672c\u673a\u65b9\u6cd5"},
        {"nested:", "\u5d4c\u5957\uff1a {0}"},
        {"No attach address specified.", "\u672a\u6307\u5b9a\u8fde\u63a5\u5730\u5740\u3002"},
        {"No breakpoints set.", "\u672a\u8bbe\u7f6e\u65ad\u70b9\u3002"},
        {"No class named", "\u6ca1\u6709\u540d\u4e3a \"{0}\" \u7684\u7c7b"},
        {"No class specified.", "\u672a\u6307\u5b9a\u7c7b\u3002"},
        {"No classpath specified.", "\u672a\u6307\u5b9a\u7c7b\u8def\u5f84\u3002"},
        {"No code at line", "{1} \u4e2d\u7684\u7b2c {0,number,integer} \u884c\u6ca1\u6709\u4ee3\u7801"},
        {"No connect specification.", "\u6ca1\u6709\u8fde\u63a5\u8bf4\u660e\u3002"},
        {"No connector named:", "\u6ca1\u6709\u540d\u4e3a {0} \u7684\u8fde\u63a5\u5668"},
        {"No current thread", "\u6ca1\u6709\u5f53\u524d\u7ebf\u7a0b"},
        {"No default thread specified:", "\u672a\u6307\u5b9a\u9ed8\u8ba4\u7ebf\u7a0b\uff1a\u8bf7\u5148\u4f7f\u7528 \"thread\" \u547d\u4ee4\u3002"},
        {"No exception object specified.", "\u672a\u6307\u5b9a\u5f02\u5e38\u5bf9\u8c61\u3002"},
        {"No exceptions caught.", "\u672a\u6355\u6349\u5230\u5f02\u5e38\u3002"},
        {"No expression specified.", "\u672a\u6307\u5b9a\u8868\u8fbe\u5f0f\u3002"},
        {"No field in", "{1} \u4e2d\u6ca1\u6709\u5b57\u6bb5 {0}"},
        {"No frames on the current call stack", "\u5f53\u524d\u8c03\u7528\u5806\u6808\u4e2d\u6ca1\u6709\u5e27"},
        {"No linenumber information for", "\u6ca1\u6709 {0} \u7684\u884c\u53f7\u4fe1\u606f\u3002\u5c1d\u8bd5\u5728\u542f\u7528\u8c03\u8bd5\u65f6\u8fdb\u884c\u7f16\u8bd1\u3002"},
        {"No local variables", "\u65e0\u5c40\u90e8\u53d8\u91cf"},
        {"No method in", "{1} \u4e2d\u6ca1\u6709\u65b9\u6cd5 {0}"},
        {"No method specified.", "\u672a\u6307\u5b9a\u65b9\u6cd5\u3002"},
        {"No monitor numbered:", "\u6ca1\u6709\u7f16\u53f7\u7684\u76d1\u89c6\u5668\uff1a {0}"},
        {"No monitors owned", "  \u6ca1\u6709\u62e5\u6709\u7684\u76d1\u89c6\u5668"},
        {"No object specified.", "\u672a\u6307\u5b9a\u5bf9\u8c61\u3002"},
        {"No objects specified.", "\u672a\u6307\u5b9a\u5bf9\u8c61\u3002"},
        {"No save index specified.", "\u672a\u6307\u5b9a\u4fdd\u5b58\u7d22\u5f15\u3002"},
        {"No saved values", "\u6ca1\u6709\u4fdd\u5b58\u7684\u503c"},
        {"No source information available for:", "{0}\u6ca1\u6709\u53ef\u7528\u7684\u6e90\u4fe1\u606f"},
        {"No sourcedebugextension specified", "\u672a\u6307\u5b9a SourceDebugExtension"},
        {"No sourcepath specified.", "\u672a\u6307\u5b9a\u6e90\u8def\u5f84\u3002"},
        {"No thread specified.", "\u672a\u6307\u5b9a\u7ebf\u7a0b\u3002"},
        {"No VM connected", "\u672a\u8fde\u63a5 VM"},
        {"No waiters", "  \u6ca1\u6709\u7b49\u5f85\u8005"},
        {"not a class", "{0} \u4e0d\u662f\u7c7b"},
        {"Not a monitor number:", "\u4e0d\u662f\u76d1\u89c6\u5668\u7f16\u53f7\uff1a ''{0}''"},
        {"not found (try the full name)", "{0} \u672a\u627e\u5230\uff08\u8bf7\u5c1d\u8bd5\u4f7f\u7528\u5168\u540d\uff09"},
        {"Not found:", "\u672a\u627e\u5230\uff1a {0}"},
        {"not found", "{0} \u672a\u627e\u5230"},
        {"Not owned", "  \u4e0d\u62e5\u6709"},
        {"Not waiting for a monitor", "  \u4e0d\u7b49\u5f85\u76d1\u89c6\u5668"},
        {"Nothing suspended.", "\u672a\u6682\u505c\u4efb\u4f55\u5bf9\u8c61\u3002"},
        {"object description and hex id", "({0}){1}"},
        {"Operation is not supported on the target VM", "\u76ee\u6807 VM \u4e0d\u652f\u6301\u64cd\u4f5c"},
        {"operation not yet supported", "\u5c1a\u4e0d\u652f\u6301\u64cd\u4f5c"},
        {"Owned by:", "  \u62e5\u6709\u8005\uff1a{0}\uff0c\u6761\u76ee\u8ba1\u6570\uff1a{1,number,integer}"},
        {"Owned monitor:", "  \u62e5\u6709\u7684\u76d1\u89c6\u5668\uff1a {0}"},
        {"Parse exception:", "\u89e3\u6790\u5f02\u5e38\uff1a {0}"},
        {"printbreakpointcommandusage", "\u7528\u6cd5\uff1a{0} <\u7c7b>:<\u884c\u53f7> \u6216\n       {1} <\u7c7b>.<\u65b9\u6cd5\u540d>[(\u53c2\u6570\u7c7b\u578b,...)]"},
        {"Removed:", "\u5df2\u5220\u9664\uff1a {0}"},
        {"Requested stack frame is no longer active:", "\u8bf7\u6c42\u7684\u5806\u6808\u5e27\u4e0d\u518d\u5904\u4e8e\u6d3b\u52a8\u72b6\u6001\uff1a{0,number,integer}"},
        {"run <args> command is valid only with launched VMs", "\u201crun <\u53c2\u6570>\u201d\u547d\u4ee4\u4ec5\u5bf9\u5df2\u542f\u52a8\u7684 VM \u6709\u6548"},
        {"run", "\u8fd0\u884c {0}"},
        {"saved", "{0} \u5df2\u4fdd\u5b58"},
        {"Set deferred", "\u8bbe\u7f6e\u5ef6\u8fdf\u7684 {0}"},
        {"Set", "\u8bbe\u7f6e {0}"},
        {"Source file not found:", "\u627e\u4e0d\u5230\u6e90\u6587\u4ef6\uff1a {0}"},
        {"source line number and line", "{0,number,integer}    {1}"},
        {"source line number current line and line", "{0,number,integer} => {1}"},
        {"sourcedebugextension", "SourceDebugExtension- {0}"},
        {"Specify class and method", "\u6307\u5b9a\u7c7b\u548c\u65b9\u6cd5"},
        {"Specify classes to redefine", "\u6307\u5b9a\u8981\u91cd\u65b0\u5b9a\u4e49\u7684\u7c7b"},
        {"Specify file name for class", "\u6307\u5b9a\u7c7b {0} \u7684\u6587\u4ef6\u540d"},
        {"stack frame dump with pc", "  [{0,number,integer}] {1}.{2} ({3}), pc = {4}"},
        {"stack frame dump", "  [{0,number,integer}] {1}.{2} ({3})"},
        {"Step completed:", "\u5df2\u5b8c\u6210\u6b65\u9aa4\uff1a "},
        {"Stopping due to deferred breakpoint errors.", "\u7531\u4e8e\u5ef6\u8fdf\u7684\u65ad\u70b9\u9519\u8bef\u800c\u505c\u6b62\u3002\n"},
        {"subclass:", "\u5b50\u7c7b\uff1a {0}"},
        {"subinterface:", "\u5b50\u63a5\u53e3\uff1a {0}"},
        {"tab", "\t{0}"},
        {"Target VM failed to initialize.", "\u76ee\u6807 VM \u65e0\u6cd5\u521d\u59cb\u5316\u3002"},
        {"The application exited", "\u5e94\u7528\u7a0b\u5e8f\u5df2\u9000\u51fa"},
        {"The application has been disconnected", "\u5df2\u65ad\u5f00\u5e94\u7528\u7a0b\u5e8f\u7684\u8fde\u63a5"},
        {"The gc command is no longer necessary.", "\u4e0d\u518d\u9700\u8981 'gc' \u547d\u4ee4\u3002\n" +
"\u5982\u5e73\u5e38\u4e00\u6837\u5bf9\u6240\u6709\u5bf9\u8c61\u8fdb\u884c\u5783\u573e\u6536\u96c6\u3002\u4f7f\u7528 'enablegc' \u548c 'disablegc' \n" +
"\u547d\u4ee4\u6765\u63a7\u5236\u5404\u4e2a\u5bf9\u8c61\u7684\u5783\u573e\u6536\u96c6\u3002"},
        {"The load command is no longer supported.", "\u4e0d\u518d\u652f\u6301 \"load\" \u547d\u4ee4\u3002"},
        {"The memory command is no longer supported.", "\u4e0d\u518d\u652f\u6301 \"memory\" \u547d\u4ee4\u3002"},
        {"The VM does not use paths", "VM \u4e0d\u4f7f\u7528\u8def\u5f84"},
        {"Thread is not running (no stack).", "\u7ebf\u7a0b\u672a\u8fd0\u884c\uff08\u65e0\u5806\u6808\uff09\u3002"},
        {"Thread number not specified.", "\u672a\u6307\u5b9a\u7ebf\u7a0b\u53f7\u3002"},
        {"Thread:", "{0}:"},
        {"Thread Group:", "\u7ec4 {0}\uff1a"},
        {"Thread description name unknownStatus BP",  "  {0} {1} \u672a\u77e5\uff08\u5728\u65ad\u70b9\u5904\uff09"},
        {"Thread description name unknownStatus",     "  {0} {1} \u672a\u77e5"},
        {"Thread description name zombieStatus BP",   "  {0} {1} \u5904\u4e8e\u50f5\u72b6\u6001\uff08\u5728\u65ad\u70b9\u5904\uff09"},
        {"Thread description name zombieStatus",      "  {0} {1} \u5904\u4e8e\u50f5\u72b6\u6001"},
        {"Thread description name runningStatus BP",  "  {0} {1} \u6b63\u5728\u8fd0\u884c\uff08\u5728\u65ad\u70b9\u5904\uff09"},
        {"Thread description name runningStatus",     "  {0} {1} \u6b63\u5728\u8fd0\u884c"},
        {"Thread description name sleepingStatus BP", "  {0} {1} \u6b63\u5728\u4f11\u7720\uff08\u5728\u65ad\u70b9\u5904\uff09"},
        {"Thread description name sleepingStatus",    "  {0} {1} \u6b63\u5728\u4f11\u7720"},
        {"Thread description name waitingStatus BP",  "  {0} {1} \u6b63\u5728\u76d1\u89c6\u5668\u4e2d\u7b49\u5f85\uff08\u5728\u65ad\u70b9\u5904\uff09"},
        {"Thread description name waitingStatus",     "  {0} {1} \u6b63\u5728\u76d1\u89c6\u5668\u4e2d\u7b49\u5f85"},
        {"Thread description name condWaitstatus BP", "  {0} {1} \u6761\u4ef6\u6b63\u5728\u7b49\u5f85\uff08\u5728\u65ad\u70b9\u5904\uff09"},
        {"Thread description name condWaitstatus",    "  {0} {1} \u6761\u4ef6\u6b63\u5728\u7b49\u5f85"},
        {"Thread has been resumed", "\u7ebf\u7a0b\u5df2\u6062\u590d"},
        {"Thread not suspended", "\u7ebf\u7a0b\u672a\u6682\u505c"},
        {"thread group number description name", "{0,number,integer}\u3002 {1} {2}"},
        {"Threadgroup name not specified.", "\u672a\u6307\u5b9a\u7ebf\u7a0b\u7ec4\u540d\u79f0\u3002"},
        {"Threads must be suspended", "\u5fc5\u987b\u6682\u505c\u7ebf\u7a0b"},
        {"trace method exit in effect for", "\u5bf9 {0} \u6709\u6548\u8ddf\u8e2a\u65b9\u6cd5\u9000\u51fa"},
        {"trace method exits in effect", "\u6709\u6548\u8ddf\u8e2a\u65b9\u6cd5\u9000\u51fa"},
        {"trace methods in effect", "\u6709\u6548\u8ddf\u8e2a\u65b9\u6cd5"},
        {"trace go method exit in effect for", "\u5bf9 {0} \u6709\u6548\u8ddf\u8e2a go \u65b9\u6cd5\u9000\u51fa"},
        {"trace go method exits in effect", "\u6709\u6548\u8ddf\u8e2a go \u65b9\u6cd5\u9000\u51fa"},
        {"trace go methods in effect", "\u6709\u6548\u8ddf\u8e2a go \u65b9\u6cd5"},
        {"trace not in effect", "\u65e0\u6548\u8ddf\u8e2a"},
        {"Unable to attach to target VM.", "\u65e0\u6cd5\u8fde\u63a5\u5230\u76ee\u6807 VM\u3002"},
        {"Unable to display process output:", "\u65e0\u6cd5\u663e\u793a\u8fdb\u7a0b\u8f93\u51fa\uff1a {0}"},
        {"Unable to launch target VM.", "\u65e0\u6cd5\u542f\u52a8\u76ee\u6807 VM\u3002"},
        {"Unable to set deferred", "\u65e0\u6cd5\u8bbe\u7f6e\u5ef6\u8fdf\u7684 {0}\uff1a {1}"},
        {"Unable to set main class and arguments", "\u65e0\u6cd5\u8bbe\u7f6e\u4e3b\u7c7b\u548c\u53c2\u6570"},
        {"Unable to set", "\u65e0\u6cd5\u8bbe\u7f6e {0}\uff1a {1}"},
        {"Unexpected event type", "\u610f\u5916\u7684\u4e8b\u4ef6\u7c7b\u578b: {0}"},
        {"unknown", "\u672a\u77e5"},
        {"Unmonitoring", "\u672a\u76d1\u89c6 {0} "},
        {"Unrecognized command.  Try help...", "\u65e0\u6cd5\u8bc6\u522b\u7684\u547d\u4ee4\uff1a \"{0}\" \u3002  \u8bf7\u5c1d\u8bd5\u4f7f\u7528 help..."},
        {"Usage: catch exception", "\u7528\u6cd5\uff1acatch [uncaught|caught|all] <\u7c7b ID>|<\u7c7b\u6a21\u5f0f>"},
        {"Usage: ignore exception", "\u7528\u6cd5\uff1aignore [uncaught|caught|all] <\u7c7b ID>|<\u7c7b\u6a21\u5f0f>"},
        {"Usage: down [n frames]", "\u7528\u6cd5\uff1adown [n \u5e27]"},
        {"Usage: kill <thread id> <throwable>", "\u7528\u6cd5\uff1akill <\u7ebf\u7a0bID> <throwable>"},
        {"Usage: read <command-filename>", "\u7528\u6cd5\uff1aread <\u547d\u4ee4\u6587\u4ef6\u540d>"},
        {"Usage: unmonitor <monitor#>", "\u7528\u6cd5\uff1aunmonitor <\u76d1\u89c6\u5668\u53f7>"},
        {"Usage: up [n frames]", "\u7528\u6cd5\uff1aup [n \u5e27]"},
        {"Use java minus X to see", "\u4f7f\u7528 \"java -X\" \u53ef\u4ee5\u67e5\u770b\u53ef\u7528\u7684\u975e\u6807\u51c6\u9009\u9879"},
        {"Use stop at to set a breakpoint at a line number", "\u4f7f\u7528 \"stop at\" \u53ef\u4ee5\u5728\u67d0\u4e2a\u884c\u53f7\u5904\u8bbe\u7f6e\u65ad\u70b9"},
        {"VM already running. use cont to continue after events.", "VM \u5df2\u8fd0\u884c\u3002\u4f7f\u7528 \"cont\" \u53ef\u4ee5\u5728\u4e8b\u4ef6\u540e\u7ee7\u7eed\u3002"},
        {"VM Started:", "VM \u5df2\u542f\u52a8\uff1a "},
        {"vmstartexception", "VM \u542f\u52a8\u5f02\u5e38\uff1a {0}"},
        {"Waiting for monitor:", "   \u6b63\u5728\u7b49\u5f85\u76d1\u89c6\u5668\uff1a {0}"},
        {"Waiting thread:", " \u6b63\u5728\u7b49\u5f85\u7ebf\u7a0b\uff1a {0}"},
        {"watch accesses of", "\u76d1\u89c6 {0}.{1} \u7684\u8bbf\u95ee"},
        {"watch modification of", "\u76d1\u89c6 {0}.{1} \u7684\u4fee\u6539"},
        {"zz help text",
"** \u547d\u4ee4\u5217\u8868 **\n" +
"connectors                -- \u5217\u51fa\u6b64 VM \u4e2d\u53ef\u7528\u7684\u8fde\u63a5\u5668\u548c\u4f20\u8f93\u5668\n" +
             "\n" +
"run [\u7c7b [\u53c2\u6570]]        -- \u5f00\u59cb\u6267\u884c\u5e94\u7528\u7a0b\u5e8f\u7684\u4e3b\u7c7b\n" +
             "\n" +
"threads [\u7ebf\u7a0b\u7ec4]     -- \u5217\u51fa\u7ebf\u7a0b\n" +
"thread <\u7ebf\u7a0b ID>        -- \u8bbe\u7f6e\u9ed8\u8ba4\u7ebf\u7a0b\n" +
"suspend [\u7ebf\u7a0b ID]    -- \u6682\u505c\u7ebf\u7a0b\uff08\u9ed8\u8ba4\u503c\u4e3a all\uff09\n" +
"resume [\u7ebf\u7a0b ID]     -- \u6062\u590d\u7ebf\u7a0b\uff08\u9ed8\u8ba4\u503c\u4e3a all\uff09\n" +
"where [<\u7ebf\u7a0b ID> | all] -- \u8f6c\u50a8\u7ebf\u7a0b\u7684\u5806\u6808\n" +
"wherei [<\u7ebf\u7a0b ID> | all] -- \u8f6c\u50a8\u7ebf\u7a0b\u7684\u5806\u6808\u4ee5\u53ca pc \u4fe1\u606f\n" +
"up [n \u5e27]             -- \u5411\u4e0a\u79fb\u52a8\u7ebf\u7a0b\u7684\u5806\u6808\n" +
"down [n \u5e27]           -- \u5411\u4e0b\u79fb\u52a8\u7ebf\u7a0b\u7684\u5806\u6808\n" +
"kill <\u7ebf\u7a0b ID> <\u8868\u8fbe\u5f0f>   -- \u4e2d\u6b62\u5177\u6709\u7ed9\u5b9a\u7684\u5f02\u5e38\u5bf9\u8c61\u7684\u7ebf\u7a0b\n" +
"interrupt <\u7ebf\u7a0b ID>     -- \u4e2d\u65ad\u7ebf\u7a0b\n" +
             "\n" +
"print <\u8868\u8fbe\u5f0f>              -- \u8f93\u51fa\u8868\u8fbe\u5f0f\u7684\u503c\n" +
"dump <\u8868\u8fbe\u5f0f>               -- \u8f93\u51fa\u6240\u6709\u5bf9\u8c61\u4fe1\u606f\n" +
"eval <\u8868\u8fbe\u5f0f>               -- \u8ba1\u7b97\u8868\u8fbe\u5f0f\u7684\u503c\uff08\u4e0e print \u4f5c\u7528\u76f8\u540c\uff09\n" +
"set <lvalue> = <\u8868\u8fbe\u5f0f>     -- \u4e3a\u5b57\u6bb5/\u53d8\u91cf/\u6570\u7ec4\u5143\u7d20\u6307\u5b9a\u65b0\u503c\n" +
"locals                    -- \u8f93\u51fa\u5f53\u524d\u5806\u6808\u5e27\u4e2d\u7684\u6240\u6709\u672c\u5730\u53d8\u91cf\n" +
             "\n" +
"classes                   -- \u5217\u51fa\u5f53\u524d\u5df2\u77e5\u7684\u7c7b\n" +
"class <\u7c7b ID>          -- \u663e\u793a\u5df2\u547d\u540d\u7c7b\u7684\u8be6\u7ec6\u4fe1\u606f\n" +
"methods <\u7c7b ID>        -- \u5217\u51fa\u7c7b\u7684\u65b9\u6cd5\n" +
"fields <\u7c7b ID>         -- \u5217\u51fa\u7c7b\u7684\u5b57\u6bb5\n" +
             "\n" +
"threadgroups              -- \u5217\u51fa\u7ebf\u7a0b\u7ec4\n" +
"threadgroup <\u540d\u79f0>        -- \u8bbe\u7f6e\u5f53\u524d\u7ebf\u7a0b\u7ec4\n" +
             "\n" +
"stop in <\u7c7b ID>.<\u65b9\u6cd5>[(\u53c2\u6570\u7c7b\u578b,...)]\n" +
"                          -- \u5728\u65b9\u6cd5\u4e2d\u8bbe\u7f6e\u65ad\u70b9\n" +
"stop at <\u7c7b ID>:<\u884c> -- \u5728\u884c\u4e2d\u8bbe\u7f6e\u65ad\u70b9\n" +
"clear <\u7c7b ID>.<\u65b9\u6cd5>[(\u53c2\u6570\u7c7b\u578b,...)]\n" +
"                          -- \u6e05\u9664\u65b9\u6cd5\u4e2d\u7684\u65ad\u70b9\n" +
"clear <\u7c7b ID>:<\u884c>   -- \u6e05\u9664\u884c\u4e2d\u7684\u65ad\u70b9\n" +
"clear                     -- \u5217\u51fa\u65ad\u70b9\n" +
"catch [uncaught|caught|all] <\u7c7b ID>|<\u7c7b\u6a21\u5f0f>\n" +
"                          -- \u51fa\u73b0\u6307\u5b9a\u7684\u5f02\u5e38\u65f6\u4e2d\u65ad\n" +
"ignore [uncaught|caught|all] <\u7c7b ID>|<\u7c7b\u6a21\u5f0f>\n" +
"                          -- \u5bf9\u4e8e\u6307\u5b9a\u7684\u5f02\u5e38\uff0c\u53d6\u6d88 'catch'\n" +
"watch [access|all] <\u7c7b ID>.<\u5b57\u6bb5\u540d>\n" +
"                          -- \u76d1\u89c6\u5bf9\u5b57\u6bb5\u7684\u8bbf\u95ee/\u4fee\u6539\n" +
"unwatch [access|all] <\u7c7b ID>.<\u5b57\u6bb5\u540d>\n" +
"                          -- \u505c\u6b62\u76d1\u89c6\u5bf9\u5b57\u6bb5\u7684\u8bbf\u95ee/\u4fee\u6539\n" +
"trace [go] methods [thread]\n" +
"                          -- \u8ddf\u8e2a\u65b9\u6cd5\u7684\u8fdb\u5165\u548c\u9000\u51fa\u3002\n" +
"                          -- \u9664\u975e\u6307\u5b9a 'go'\uff0c\u5426\u5219\u6240\u6709\u7ebf\u7a0b\u90fd\u5c06\u6682\u505c\n" +
"trace [go] method exit | exits [thread]\n" +
"                          -- \u8ddf\u8e2a\u5f53\u524d\u65b9\u6cd5\u7684\u9000\u51fa\u6216\u6240\u6709\u65b9\u6cd5\u7684\u9000\u51fa\n" +
"                          -- \u9664\u975e\u6307\u5b9a 'go'\uff0c\u5426\u5219\u6240\u6709\u7ebf\u7a0b\u90fd\u5c06\u6682\u505c\n" +
"untrace [\u65b9\u6cd5]         -- \u505c\u6b62\u8ddf\u8e2a\u65b9\u6cd5\u7684\u8fdb\u5165\u548c/\u6216\u9000\u51fa\n" +
"step                      -- \u6267\u884c\u5f53\u524d\u884c\n" +
"step up                   -- \u6267\u884c\u5230\u5f53\u524d\u65b9\u6cd5\u8fd4\u56de\u5176\u8c03\u7528\u65b9\n" +
"stepi                     -- \u6267\u884c\u5f53\u524d\u6307\u4ee4\n" +
"next                      -- \u8df3\u8fc7\u4e00\u884c\uff08\u8de8\u8fc7\u8c03\u7528\uff09\n" +
"cont                      -- \u4ece\u65ad\u70b9\u5904\u7ee7\u7eed\u6267\u884c\n" +
             "\n" +
"list [line number|method] -- \u8f93\u51fa\u6e90\u4ee3\u7801\n" +
"use\uff08\u6216 sourcepath\uff09[\u6e90\u6587\u4ef6\u8def\u5f84]\n" +
"                          -- \u663e\u793a\u6216\u66f4\u6539\u6e90\u8def\u5f84\n" +
"exclude [<\u7c7b\u6a21\u5f0f>, ...|\u201c\u65e0\u201d]\n" +
"                          -- \u4e0d\u62a5\u544a\u6307\u5b9a\u7c7b\u7684\u6b65\u9aa4\u6216\u65b9\u6cd5\u4e8b\u4ef6\n" +
"classpath                 -- \u4ece\u76ee\u6807 VM \u8f93\u51fa\u7c7b\u8def\u5f84\u4fe1\u606f\n" +
             "\n" +
"monitor <\u547d\u4ee4>         -- \u6bcf\u6b21\u7a0b\u5e8f\u505c\u6b62\u65f6\u6267\u884c\u547d\u4ee4\n" +
"monitor                   -- \u5217\u51fa\u76d1\u89c6\u5668\n" +
"unmonitor <\u76d1\u89c6\u5668\u53f7>      -- \u5220\u9664\u67d0\u4e2a\u76d1\u89c6\u5668\n" +
"read <\u6587\u4ef6\u540d>           -- \u8bfb\u53d6\u5e76\u6267\u884c\u67d0\u4e2a\u547d\u4ee4\u6587\u4ef6\n" +
             "\n" +
"lock <\u8868\u8fbe\u5f0f>               -- \u8f93\u51fa\u5bf9\u8c61\u7684\u9501\u4fe1\u606f\n" +
"threadlocks [\u7ebf\u7a0b ID]   -- \u8f93\u51fa\u7ebf\u7a0b\u7684\u9501\u4fe1\u606f\n" +
             "\n" +
"pop                       -- \u5f39\u51fa\u6574\u4e2a\u5806\u6808\uff0c\u4e14\u5305\u542b\u5f53\u524d\u5e27\n" +
"reenter                   -- \u4e0e pop \u4f5c\u7528\u76f8\u540c\uff0c\u4f46\u91cd\u65b0\u8fdb\u5165\u5f53\u524d\u5e27\n" +
"redefine <\u7c7b ID> <\u7c7b\u6587\u4ef6\u540d>\n" +
"                          -- \u91cd\u65b0\u5b9a\u4e49\u7c7b\u4ee3\u7801\n" +
             "\n" +
"disablegc <\u8868\u8fbe\u5f0f>          -- \u7981\u6b62\u5bf9\u8c61\u7684\u5783\u573e\u56de\u6536\n" +
"enablegc <\u8868\u8fbe\u5f0f>           -- \u5141\u8bb8\u5bf9\u8c61\u7684\u5783\u573e\u56de\u6536\n" +
             "\n" +
"!!                        -- \u91cd\u590d\u6267\u884c\u6700\u540e\u4e00\u4e2a\u547d\u4ee4\n" +
"<n> <\u547d\u4ee4>             -- \u5c06\u547d\u4ee4\u91cd\u590d\u6267\u884c n \u6b21\n" +
"# <\u547d\u4ee4>               -- \u653e\u5f03\uff08\u4e0d\u6267\u884c\uff09\n" +
"help\uff08\u6216 ?\uff09               -- \u5217\u51fa\u547d\u4ee4\n" +
"version                   -- \u8f93\u51fa\u7248\u672c\u4fe1\u606f\n" +
"exit\uff08\u6216 quit\uff09            -- \u9000\u51fa\u8c03\u8bd5\u5668\n" +
             "\n" +
"<\u7c7b ID>: \u5e26\u6709\u8f6f\u4ef6\u5305\u9650\u5b9a\u7b26\u7684\u5b8c\u6574\u7c7b\u540d\n" +
"<\u7c7b\u6a21\u5f0f>: \u5e26\u6709\u524d\u5bfc\u6216\u540e\u7f00\u901a\u914d\u7b26 (*) \u7684\u7c7b\u540d\n" +
"<\u7ebf\u7a0b ID>: 'threads' \u547d\u4ee4\u4e2d\u6240\u62a5\u544a\u7684\u7ebf\u7a0b\u53f7\n" +
"<\u8868\u8fbe\u5f0f>: Java(TM) \u7f16\u7a0b\u8bed\u8a00\u8868\u8fbe\u5f0f\u3002\n" +
"\u652f\u6301\u5927\u591a\u6570\u5e38\u89c1\u8bed\u6cd5\u3002\n" +
             "\n" +
"\u53ef\u4ee5\u5c06\u542f\u52a8\u547d\u4ee4\u7f6e\u4e8e \"jdb.ini\" \u6216 \".jdbrc\" \u4e4b\u4e2d\n" +
"\uff08\u4e24\u8005\u4f4d\u4e8e user.home \u6216 user.dir \u4e2d\uff09"},
        {"zz usage text",
"\u7528\u6cd5:{0} <\u9009\u9879> <\u7c7b> <\u53c2\u6570>\n" +
             "\n" +
"\u5176\u4e2d\u9009\u9879\u5305\u62ec:\n" +
"    -help             \u8f93\u51fa\u6b64\u6d88\u606f\u5e76\u9000\u51fa\n" +
"    -sourcepath <\u4ee5 \"{1}\" \u5206\u9694\u7684\u76ee\u5f55>\n" +
"                      \u5728\u5176\u4e2d\u67e5\u627e\u6e90\u6587\u4ef6\u7684\u76ee\u5f55\n" +
"    -attach <\u5730\u5740>\n" +
"                      \u4f7f\u7528\u6807\u51c6\u8fde\u63a5\u5668\u8fde\u63a5\u5230\u6b63\u5728\u6307\u5b9a\u5730\u5740\u8fd0\u884c\u7684 VM\n" +
"    -listen <\u5730\u5740>\n" +
"                      \u7b49\u5f85\u6b63\u5728\u6307\u5b9a\u5730\u5740\u8fd0\u884c\u7684 VM \u4f7f\u7528\u6807\u51c6\u8fde\u63a5\u5668\u8fdb\u884c\u8fde\u63a5\n" +
"    -listenany\n" +
"                      \u7b49\u5f85\u6b63\u5728\u4efb\u610f\u53ef\u7528\u5730\u5740\u8fd0\u884c\u7684 VM \u4f7f\u7528\u6807\u51c6\u8fde\u63a5\u5668\u8fdb\u884c\u8fde\u63a5\n" +
"    -launch\n" +
"                      \u7acb\u5373\u542f\u52a8 VM\uff0c\u800c\u4e0d\u7b49\u5f85 ''run'' \u547d\u4ee4\n" +
"    -listconnectors   \u5217\u51fa\u6b64 VM \u4e2d\u53ef\u7528\u7684\u8fde\u63a5\u5668\n" +
"    -connect <\u8fde\u63a5\u5668\u540d\u79f0>:<\u540d\u79f0 1>=<\u503c 1>,...\n" +
"                      \u4f7f\u7528\u547d\u540d\u7684\u8fde\u63a5\u5668\u548c\u5217\u51fa\u7684\u53c2\u6570\u503c\u8fde\u63a5\u5230\u76ee\u6807 VM\n" +
"    -dbgtrace [\u6807\u5fd7] \u8f93\u51fa\u7528\u4e8e\u8c03\u8bd5 {0} \u7684\u4fe1\u606f\n" +
"    -tclient          \u5728 Hotspot(TM) Performance Engine\uff08\u5ba2\u6237\u673a\uff09\u4e2d\u8fd0\u884c\u5e94\u7528\u7a0b\u5e8f\n" +
"    -tserver          \u5728 Hotspot(TM) Performance Engine\uff08\u670d\u52a1\u5668\uff09\u4e2d\u8fd0\u884c\u5e94\u7528\u7a0b\u5e8f\n" +
             "\n" +
"\u8f6c\u53d1\u7ed9\u88ab\u8c03\u8bd5\u8fdb\u7a0b\u7684\u9009\u9879:\n" +
"    -v -verbose[:class|gc|jni]\n" +
"                      \u542f\u7528\u8be6\u7ec6\u6a21\u5f0f\n" +
"    -D<\u540d\u79f0>=<\u503c>  \u8bbe\u7f6e\u7cfb\u7edf\u5c5e\u6027\n" +
"    -classpath <\u4ee5 \"{1}\" \u5206\u9694\u7684\u76ee\u5f55>\n" +
"                      \u5217\u51fa\u8981\u5728\u5176\u4e2d\u67e5\u627e\u7c7b\u7684\u76ee\u5f55\n" +
"    -X<\u9009\u9879>        \u975e\u6807\u51c6\u76ee\u6807 VM \u9009\u9879\n" +
             "\n" +
"<\u7c7b> \u662f\u8981\u5f00\u59cb\u8c03\u8bd5\u7684\u7c7b\u7684\u540d\u79f0\n" +
"<\u53c2\u6570> \u662f\u4f20\u9012\u7ed9 <\u7c7b> \u7684 main() \u65b9\u6cd5\u7684\u53c2\u6570\n" +
             "\n" +
"\u8981\u83b7\u5f97\u547d\u4ee4\u5e2e\u52a9\uff0c\u8bf7\u5728 {0} \u63d0\u793a\u7b26\u4e0b\u952e\u5165 ''help''"},
        // END OF MATERIAL TO LOCALIZE
        };
    }
}
