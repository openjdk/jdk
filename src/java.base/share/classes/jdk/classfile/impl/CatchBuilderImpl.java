package jdk.classfile.impl;

import jdk.classfile.CodeBuilder;
import jdk.classfile.Label;
import jdk.classfile.Opcode;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public final class CatchBuilderImpl implements CodeBuilder.CatchBuilder {
    final CodeBuilder b;
    final BlockCodeBuilder tryBlock;
    final Label tryCatchEnd;
    final Set<ConstantDesc> catchTypes;
    BlockCodeBuilder catchBlock;

    public CatchBuilderImpl(CodeBuilder b, BlockCodeBuilder tryBlock, Label tryCatchEnd) {
        this.b = b;
        this.tryBlock = tryBlock;
        this.tryCatchEnd = tryCatchEnd;
        this.catchTypes = new HashSet<>();
    }

    @Override
    public CodeBuilder.CatchBuilder catching(ClassDesc exceptionType, Consumer<CodeBuilder> catchHandler) {
        Objects.requireNonNull(catchHandler);

        if (catchBlock == null) {
            if (tryBlock.reachable()) {
                b.branchInstruction(Opcode.GOTO, tryCatchEnd);
            }
        }

        if (!catchTypes.add(exceptionType)) {
            throw new IllegalArgumentException("Existing catch block catches exception of type: " + exceptionType);
        }

        // Finish prior catch block
        if (catchBlock != null) {
            catchBlock.end();
            if (catchBlock.reachable()) {
                b.branchInstruction(Opcode.GOTO, tryCatchEnd);
            }
        }

        catchBlock = new BlockCodeBuilder(b);
        Label tryStart = tryBlock.startLabel();
        Label tryEnd = tryBlock.endLabel();
        catchBlock.start();
        if (exceptionType == null) {
            catchBlock.exceptionCatchAll(tryStart, tryEnd, catchBlock.startLabel());
        }
        else {
            catchBlock.exceptionCatch(tryStart, tryEnd, catchBlock.startLabel(), exceptionType);
        }
        catchHandler.accept(catchBlock);

        return this;
    }

    @Override
    public void catchingAll(Consumer<CodeBuilder> catchAllHandler) {
        catching(null, catchAllHandler);
    }

    public void finish() {
        if (catchBlock != null) {
            catchBlock.end();
            b.labelBinding(tryCatchEnd);
        }
    }
}
