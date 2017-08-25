/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.stubs;

import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_VMCONFIG;
import static org.graalvm.compiler.hotspot.nodes.JumpToExceptionHandlerNode.jumpToExceptionHandler;
import static org.graalvm.compiler.hotspot.nodes.PatchReturnAddressNode.patchReturnAddress;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.readExceptionOop;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.readExceptionPc;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.registerAsWord;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.writeExceptionOop;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.writeExceptionPc;
import static org.graalvm.compiler.hotspot.stubs.StubUtil.cAssertionsEnabled;
import static org.graalvm.compiler.hotspot.stubs.StubUtil.decipher;
import static org.graalvm.compiler.hotspot.stubs.StubUtil.fatal;
import static org.graalvm.compiler.hotspot.stubs.StubUtil.newDescriptor;
import static org.graalvm.compiler.hotspot.stubs.StubUtil.printf;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.Fold.InjectedParameter;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.debug.Assertions;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotBackend;
import org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.nodes.StubForeignCallNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.word.Word;
import org.graalvm.word.WordFactory;

import jdk.vm.ci.code.Register;

/**
 * Stub called by the {@linkplain GraalHotSpotVMConfig#MARKID_EXCEPTION_HANDLER_ENTRY exception
 * handler entry point} in a compiled method. This entry point is used when returning to a method to
 * handle an exception thrown by a callee. It is not used for routing implicit exceptions.
 * Therefore, it does not need to save any registers as HotSpot uses a caller save convention.
 * <p>
 * The descriptor for a call to this stub is {@link HotSpotBackend#EXCEPTION_HANDLER}.
 */
public class ExceptionHandlerStub extends SnippetStub {

    public ExceptionHandlerStub(OptionValues options, HotSpotProviders providers, HotSpotForeignCallLinkage linkage) {
        super("exceptionHandler", options, providers, linkage);
    }

    /**
     * This stub is called when returning to a method to handle an exception thrown by a callee. It
     * is not used for routing implicit exceptions. Therefore, it does not need to save any
     * registers as HotSpot uses a caller save convention.
     */
    @Override
    public boolean preservesRegisters() {
        return false;
    }

    @Override
    protected Object getConstantParameterValue(int index, String name) {
        if (index == 2) {
            return providers.getRegisters().getThreadRegister();
        }
        assert index == 3;
        return options;
    }

    @Snippet
    private static void exceptionHandler(Object exception, Word exceptionPc, @ConstantParameter Register threadRegister, @ConstantParameter OptionValues options) {
        Word thread = registerAsWord(threadRegister);
        checkNoExceptionInThread(thread, assertionsEnabled(INJECTED_VMCONFIG));
        checkExceptionNotNull(assertionsEnabled(INJECTED_VMCONFIG), exception);
        writeExceptionOop(thread, exception);
        writeExceptionPc(thread, exceptionPc);
        if (logging(options)) {
            printf("handling exception %p (", Word.objectToTrackedPointer(exception).rawValue());
            decipher(Word.objectToTrackedPointer(exception).rawValue());
            printf(") at %p (", exceptionPc.rawValue());
            decipher(exceptionPc.rawValue());
            printf(")\n");
        }

        // patch throwing pc into return address so that deoptimization finds the right debug info
        patchReturnAddress(exceptionPc);

        Word handlerPc = exceptionHandlerForPc(EXCEPTION_HANDLER_FOR_PC, thread);

        if (logging(options)) {
            printf("handler for exception %p at %p is at %p (", Word.objectToTrackedPointer(exception).rawValue(), exceptionPc.rawValue(), handlerPc.rawValue());
            decipher(handlerPc.rawValue());
            printf(")\n");
        }

        // patch the return address so that this stub returns to the exception handler
        jumpToExceptionHandler(handlerPc);
    }

    static void checkNoExceptionInThread(Word thread, boolean enabled) {
        if (enabled) {
            Object currentException = readExceptionOop(thread);
            if (currentException != null) {
                fatal("exception object in thread must be null, not %p", Word.objectToTrackedPointer(currentException).rawValue());
            }
            if (cAssertionsEnabled(INJECTED_VMCONFIG)) {
                // This thread-local is only cleared in DEBUG builds of the VM
                // (see OptoRuntime::generate_exception_blob)
                Word currentExceptionPc = readExceptionPc(thread);
                if (currentExceptionPc.notEqual(WordFactory.zero())) {
                    fatal("exception PC in thread must be zero, not %p", currentExceptionPc.rawValue());
                }
            }
        }
    }

    static void checkExceptionNotNull(boolean enabled, Object exception) {
        if (enabled && exception == null) {
            fatal("exception must not be null");
        }
    }

    @Fold
    static boolean logging(OptionValues options) {
        return StubOptions.TraceExceptionHandlerStub.getValue(options);
    }

    /**
     * Determines if either Java assertions are enabled for Graal or if this is a HotSpot build
     * where the ASSERT mechanism is enabled.
     */
    @Fold
    @SuppressWarnings("all")
    static boolean assertionsEnabled(@InjectedParameter GraalHotSpotVMConfig config) {
        return Assertions.assertionsEnabled() || cAssertionsEnabled(config);
    }

    public static final ForeignCallDescriptor EXCEPTION_HANDLER_FOR_PC = newDescriptor(ExceptionHandlerStub.class, "exceptionHandlerForPc", Word.class, Word.class);

    @NodeIntrinsic(value = StubForeignCallNode.class)
    public static native Word exceptionHandlerForPc(@ConstantNodeParameter ForeignCallDescriptor exceptionHandlerForPc, Word thread);
}
