/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.classfile.impl.verifier;

import java.util.Arrays;

/// From `stackMapFrame.cpp`.
class VerificationFrame {

    public static final int FLAG_THIS_UNINIT = 0x01;

    private int _offset;
    private int _locals_size, _stack_size;
    private int _stack_mark;
    private final int _max_locals, _max_stack;
    private int _flags;
    private final VerificationType[] _locals, _stack;
    private final VerifierImpl _verifier;

    public VerificationFrame(int offset, int flags, int locals_size, int stack_size, int max_locals, int max_stack, VerificationType[] locals, VerificationType[] stack, VerifierImpl v) {
        this._offset = offset;
        this._locals_size = locals_size;
        this._stack_size = stack_size;
        this._stack_mark = -1;
        this._max_locals = max_locals;
        this._max_stack = max_stack;
        this._flags = flags;
        this._locals = locals;
        this._stack = stack;
        this._verifier = v;
    }

    @Override
    public String toString() {
        return "frame @" + _offset + " with locals " + (_locals == null ? "[]" : Arrays.asList(_locals)) + " and stack " + (_stack == null ? "[]" : Arrays.asList(_stack));
    }

    void set_offset(int offset) {
        this._offset = offset;
    }

    void set_flags(int flags) {
        _flags = flags;
    }

    void set_locals_size(int locals_size) {
        _locals_size = locals_size;
    }

    void set_stack_size(int stack_size) {
        _stack_size = _stack_mark = stack_size;
    }

    int offset() {
        return _offset;
    }

    VerifierImpl verifier() {
        return _verifier;
    }

    int flags() {
        return _flags;
    }

    int locals_size() {
        return _locals_size;
    }

    VerificationType[] locals() {
        return _locals;
    }

    int stack_size() {
        return _stack_size;
    }

    VerificationType[] stack() {
        return _stack;
    }

    int max_locals() {
        return _max_locals;
    }

    boolean flag_this_uninit() {
        return (_flags & FLAG_THIS_UNINIT) == FLAG_THIS_UNINIT;
    }

    void reset() {
        for (int i = 0; i < _max_locals; i++) {
            _locals[i] = VerificationType.bogus_type;
        }
        for (int i = 0; i < _max_stack; i++) {
            _stack[i] = VerificationType.bogus_type;
        }
    }

    void set_mark() {
        if (_stack_mark != -1) {
            for (int i = _stack_mark - 1; i >= _stack_size; --i) {
                _stack[i] = VerificationType.bogus_type;
            }
            _stack_mark = _stack_size;
        }
    }

    void push_stack(VerificationType type) {
        if (type.is_check()) _verifier.verifyError("Must be a real type");
        if (_stack_size >= _max_stack) {
            _verifier.verifyError("Operand stack overflow");
        }
        _stack[_stack_size++] = type;
    }

    void push_stack_2(VerificationType type1, VerificationType type2) {
        if (!(type1.is_long() || type1.is_double())) _verifier.verifyError("must be long/double");
        if (!(type2.is_long2() || type2.is_double2())) _verifier.verifyError("must be long/double_2");
        if (_stack_size >= _max_stack - 1) {
            _verifier.verifyError("Operand stack overflow");
        }
        _stack[_stack_size++] = type1;
        _stack[_stack_size++] = type2;
    }

    VerificationType pop_stack() {
        if (_stack_size <= 0) {
            _verifier.verifyError("Operand stack underflow");
        }
        return _stack[--_stack_size];
    }

    VerificationType pop_stack(VerificationType type) {
        if (_stack_size != 0) {
            VerificationType top = _stack[_stack_size - 1];
            boolean subtype = type.is_assignable_from(top, verifier());
            if (subtype) {
                --_stack_size;
                return top;
            }
        }
        return pop_stack_ex(type);
    }

    void pop_stack_2(VerificationType type1, VerificationType type2) {
        if (!(type1.is_long2() || type1.is_double2())) _verifier.verifyError("must be long/double");
        if (!(type2.is_long() || type2.is_double())) _verifier.verifyError("must be long/double_2");
        if (_stack_size >= 2) {
            VerificationType top1 = _stack[_stack_size - 1];
            boolean subtype1 = type1.is_assignable_from(top1, verifier());
            VerificationType top2 = _stack[_stack_size - 2];
            boolean subtype2 = type2.is_assignable_from(top2, verifier());
            if (subtype1 && subtype2) {
                _stack_size -= 2;
                return;
            }
        }
        pop_stack_ex(type1);
        pop_stack_ex(type2);
    }

    VerificationFrame(int max_locals, int max_stack, VerifierImpl verifier) {
        _offset = 0;
        _locals_size = 0;
        _stack_size = 0;
        _stack_mark = 0;
        _max_locals = max_locals;
        _max_stack = max_stack;
        _flags = 0;
        _verifier = verifier;
        _locals = new VerificationType[max_locals];
        _stack = new VerificationType[max_stack];
        for (int i = 0; i < max_locals; i++) {
            _locals[i] = VerificationType.bogus_type;
        }
        for (int i = 0; i < max_stack; i++) {
            _stack[i] = VerificationType.bogus_type;
        }
    }

    VerificationFrame frame_in_exception_handler(int flags) {
        return new VerificationFrame(_offset, flags, _locals_size, 0,
                _max_locals, _max_stack, _locals, new VerificationType[1],
                _verifier);
    }

    void initialize_object(VerificationType old_object, VerificationType new_object) {
        int i;
        for (i = 0; i < _max_locals; i++) {
            if (_locals[i].equals(old_object)) {
                _locals[i] = new_object;
            }
        }
        for (i = 0; i < _stack_size; i++) {
            if (_stack[i].equals(old_object)) {
                _stack[i] = new_object;
            }
        }
        if (old_object.is_uninitialized_this(_verifier)) {
            _flags = 0;
        }
    }

    VerificationType  set_locals_from_arg(VerificationWrapper.MethodWrapper m, VerificationType thisKlass) {
        var ss = new VerificationSignature(m.descriptor(), true, _verifier);
        int init_local_num = 0;
        if (!m.isStatic()) {
            init_local_num++;
            if (VerifierImpl.object_initializer_name.equals(m.name()) && !VerifierImpl.java_lang_Object.equals(thisKlass.name())) {
                _locals[0] = VerificationType.uninitialized_this_type;
                _flags |= FLAG_THIS_UNINIT;
            } else {
                _locals[0] = thisKlass;
            }
        }
        while (!ss.atReturnType()) {
            init_local_num += _verifier.change_sig_to_verificationType(ss, _locals, init_local_num);
            ss.next();
        }
        _locals_size = init_local_num;
        switch (ss.type()) {
            case T_OBJECT:
            case T_ARRAY:
            {
                String sig = ss.asSymbol();
                return VerificationType.reference_type(sig);
            }
            case T_INT:         return VerificationType.integer_type;
            case T_BYTE:        return VerificationType.byte_type;
            case T_CHAR:        return VerificationType.char_type;
            case T_SHORT:     return VerificationType.short_type;
            case T_BOOLEAN: return VerificationType.boolean_type;
            case T_FLOAT:     return VerificationType.float_type;
            case T_DOUBLE:    return VerificationType.double_type;
            case T_LONG:        return VerificationType.long_type;
            case T_VOID:        return VerificationType.bogus_type;
            default:
                _verifier.verifyError("Should not reach here");
                return VerificationType.bogus_type;
        }
    }

    void copy_locals(VerificationFrame src) {
        int len = src.locals_size() < _locals_size ? src.locals_size() : _locals_size;
        if (len > 0) System.arraycopy(src.locals(), 0, _locals, 0, len);
    }

    void copy_stack(VerificationFrame src) {
        int len = src.stack_size() < _stack_size ? src.stack_size() : _stack_size;
        if (len > 0) System.arraycopy(src.stack(), 0, _stack, 0, len);
    }

    private int is_assignable_to(VerificationType[] from, VerificationType[] to, int len) {
        int i = 0;
        for (; i < len; i++) {
            if (!to[i].is_assignable_from(from[i], verifier())) {
                break;
            }
        }
        return i;
    }

    boolean is_assignable_to(VerificationFrame target) {
        if (_max_locals != target.max_locals()) {
            _verifier.verifyError("Locals size mismatch", this, target);
        }
        if (_stack_size != target.stack_size()) {
            _verifier.verifyError("Stack size mismatch", this, target);
        }
        int mismatch_loc;
        mismatch_loc = is_assignable_to(_locals, target.locals(), target.locals_size());
        if (mismatch_loc != target.locals_size()) {
            _verifier.verifyError("Bad type", this, target);
        }
        mismatch_loc = is_assignable_to(_stack, target.stack(), _stack_size);
        if (mismatch_loc != _stack_size) {
            _verifier.verifyError("Bad type", this, target);
        }

        if ((_flags | target.flags()) == target.flags()) {
            return true;
        } else {
            _verifier.verifyError("Bad flags", this, target);
        }
        return false;
    }

    VerificationType pop_stack_ex(VerificationType type) {
        if (_stack_size <= 0) {
            _verifier.verifyError("Operand stack underflow");
        }
        VerificationType top = _stack[--_stack_size];
        boolean subtype = type.is_assignable_from(top, verifier());
        if (!subtype) {
            _verifier.verifyError("Bad type on operand stack");
        }
        return top;
    }

    VerificationType get_local(int index, VerificationType type) {
        if (index >= _max_locals) {
            _verifier.verifyError("Local variable table overflow");
        }
        boolean subtype = type.is_assignable_from(_locals[index],
            verifier());
        if (!subtype) {
            _verifier.verifyError("Bad local variable type");
        }
        if(index >= _locals_size) { _locals_size = index + 1; }
        return _locals[index];
    }

    void get_local_2(int index, VerificationType type1, VerificationType type2) {
        if (!(type1.is_long() || type1.is_double())) _verifier.verifyError("must be long/double");
        if (!(type2.is_long2() || type2.is_double2())) _verifier.verifyError("must be long/double_2");
        if (index >= _locals_size - 1) {
            _verifier.verifyError("get long/double overflows locals");
        }
        boolean subtype = type1.is_assignable_from(_locals[index], verifier());
        if (!subtype) {
            _verifier.verifyError("Bad local variable type");
        } else {
            subtype = type2.is_assignable_from(_locals[index + 1], verifier());
            if (!subtype) {
                _verifier.verifyError("Bad local variable type");
            }
        }
    }

    void set_local(int index, VerificationType type) {
        if (type.is_check()) _verifier.verifyError("Must be a real type");
        if (index >= _max_locals) {
            _verifier.verifyError("Local variable table overflow");
        }
        if (_locals[index].is_double() || _locals[index].is_long()) {
            if ((index + 1) >= _locals_size) _verifier.verifyError("Local variable table overflow");
            _locals[index + 1] = VerificationType.bogus_type;
        }
        if (_locals[index].is_double2() || _locals[index].is_long2()) {
            if (index < 1) _verifier.verifyError("Local variable table underflow");
            _locals[index - 1] = VerificationType.bogus_type;
        }
        _locals[index] = type;
        if (index >= _locals_size) {
            for (int i=_locals_size; i<index; i++) {
                if (_locals[i] != VerificationType.bogus_type) _verifier.verifyError("holes must be bogus type");
            }
            _locals_size = index + 1;
        }
    }

    void set_local_2(int index, VerificationType type1, VerificationType type2) {
        if (!(type1.is_long() || type1.is_double())) _verifier.verifyError("must be long/double");
        if (!(type2.is_long2() || type2.is_double2())) _verifier.verifyError("must be long/double_2");
        if (index >= _max_locals - 1) {
            _verifier.verifyError("Local variable table overflow");
        }
        if (_locals[index+1].is_double() || _locals[index+1].is_long()) {
            if ((index + 2) >= _locals_size) _verifier.verifyError("Local variable table overflow");
            _locals[index + 2] = VerificationType.bogus_type;
        }
        if (_locals[index].is_double2() || _locals[index].is_long2()) {
            if (index < 1) _verifier.verifyError("Local variable table underflow");
            _locals[index - 1] = VerificationType.bogus_type;
        }
        _locals[index] = type1;
        _locals[index+1] = type2;
        if (index >= _locals_size - 1) {
            for (int i=_locals_size; i<index; i++) {
                if (_locals[i] != VerificationType.bogus_type) _verifier.verifyError("holes must be bogus type");
            }
            _locals_size = index + 2;
        }
    }
}
