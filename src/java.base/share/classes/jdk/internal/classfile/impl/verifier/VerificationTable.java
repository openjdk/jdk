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

import java.util.ArrayList;
import java.util.List;

import static jdk.internal.classfile.impl.verifier.VerificationType.ITEM_Object;
import static jdk.internal.classfile.impl.verifier.VerificationType.ITEM_Uninitialized;
import static jdk.internal.classfile.impl.verifier.VerificationType.ITEM_UninitializedThis;

/// From `stackMapTable.cpp`.
class VerificationTable {

    private final int _code_length;
    private final int _frame_count;
    private final List<VerificationFrame> _frame_array;
    private final VerifierImpl _verifier;

    int get_frame_count() {
        return _frame_count;
    }

    int get_offset(int index) {
        return _frame_array.get(index).offset();
    }

    static class StackMapStream {

        private final byte[] _data;
        private int _index;
        private final VerifierImpl _verifier;

        StackMapStream(byte[] ah, VerifierImpl context) {
            _data = ah;
            _index = 0;
            _verifier = context;
        }

        int get_u1() {
            if (_data == null || _index >= _data.length) {
                _verifier.classError("access beyond the end of attribute");
            }
            return _data[_index++] & 0xff;
        }

        int get_u2() {
            int res = get_u1() << 8;
            return res | get_u1();
        }

        boolean at_end() {
            return (_data == null) || (_index == _data.length);
        }
    }

    VerificationTable(StackMapReader reader,
            VerificationWrapper.ConstantPoolWrapper cp, VerifierImpl v) {
        _verifier = v;
        _code_length = reader.code_length();
        int _frame_count = reader.get_frame_count();
        _frame_array = new ArrayList<>(_frame_count);
        if (_frame_count > 0) {
            while (!reader.at_end()) {
                VerificationFrame frame = reader.next();
                if (frame != null) {
                    _frame_array.add(frame);
                }
            }
        }
        reader.check_end();
        this._frame_count = _frame_array.size();
    }

    int get_index_from_offset(int offset) {
        int i = 0;
        for (; i < _frame_count; i++) {
            if (_frame_array.get(i).offset() == offset) {
                return i;
            }
        }
        return i;
    }

    boolean match_stackmap(VerificationFrame frame, int target, boolean match, boolean update) {
        int index = get_index_from_offset(target);
        return match_stackmap(frame, target, index, match, update);
    }

    boolean match_stackmap(VerificationFrame frame, int target, int frame_index, boolean match, boolean update) {
        if (frame_index < 0 || frame_index >= _frame_count) {
            _verifier.verifyError(String.format("Expecting a stackmap frame at branch target %d", target));
        }
        VerificationFrame stackmap_frame = _frame_array.get(frame_index);
        boolean result = true;
        if (match) {
            result = frame.is_assignable_to(stackmap_frame);
        }
        if (update) {
            int lsize = stackmap_frame.locals_size();
            int ssize = stackmap_frame.stack_size();
            if (frame.locals_size() > lsize || frame.stack_size() > ssize) {
                frame.reset();
            }
            frame.set_locals_size(lsize);
            frame.copy_locals(stackmap_frame);
            frame.set_stack_size(ssize);
            frame.copy_stack(stackmap_frame);
            frame.set_flags(stackmap_frame.flags());
        }
        return result;
    }

    void check_jump_target(VerificationFrame frame, int target) {
        boolean match = match_stackmap(frame, target, true, false);
        if (!match || (target < 0 || target >= _code_length)) {
            _verifier.verifyError(String.format("Inconsistent stackmap frames at branch target %d", target));
        }
    }

    static class StackMapReader {

        private final VerificationWrapper.ConstantPoolWrapper _cp;
        private final StackMapStream _stream;
        private final byte[] _code_data;
        private final int _code_length;
        private final int _frame_count;
        private int _parsed_frame_count;
        private VerificationFrame _prev_frame;
        char _max_locals, _max_stack;
        boolean _first;

        void check_verification_type_array_size(int size, int max_size) {
            if (size < 0 || size > max_size) {
                _verifier.classError("StackMapTable format error: bad type array size");
            }
        }

        private static final int
                        SAME_LOCALS_1_STACK_ITEM_EXTENDED = 247,
                        SAME_EXTENDED = 251,
                        FULL = 255;

        public int get_frame_count() {
            return _frame_count;
        }

        public VerificationFrame prev_frame() {
            return _prev_frame;
        }

        public byte[] code_data() {
            return _code_data;
        }

        public int code_length() {
            return _code_length;
        }

        public boolean at_end() {
            return _stream.at_end();
        }

        public VerificationFrame next() {
            _parsed_frame_count++;
            check_size();
            VerificationFrame frame = next_helper();
            if (frame != null) {
                check_offset(frame);
                _prev_frame = frame;
            }
            return frame;
        }

        public void check_end() {
            if (_frame_count != _parsed_frame_count) {
                _verifier.verifyError("wrong attribute size");
            }
        }

        private final VerifierImpl _verifier;

        public StackMapReader(byte[] stackmapData, byte[] code_data, int code_len,
                              VerificationFrame init_frame, char max_locals, char max_stack,
                              VerificationWrapper.ConstantPoolWrapper cp, VerifierImpl context) {
            this._verifier = context;
            _stream = new StackMapStream(stackmapData, _verifier);
            _code_data = code_data;
            _code_length = code_len;
            _parsed_frame_count = 0;
            _prev_frame = init_frame;
            _max_locals = max_locals;
            _max_stack = max_stack;
            _first = true;
            if (stackmapData != null) {
                _cp = cp;
                _frame_count = _stream.get_u2();
            } else {
                _cp = null;
                _frame_count = 0;
            }
        }

        void check_offset(VerificationFrame frame) {
            int offset = frame.offset();
            if (offset >= _code_length || _code_data[offset] == 0) {
                _verifier.verifyError("StackMapTable error: bad offset");
            }
        }

        void check_size() {
            if (_frame_count < _parsed_frame_count) {
                _verifier.verifyError("wrong attribute size");
            }
        }

        int chop(VerificationType[] locals, int length, int chops) {
            if (locals == null) return -1;
            int pos = length - 1;
            for (int i=0; i<chops; i++) {
                if (locals[pos].is_category2_2nd()) {
                    pos -= 2;
                } else {
                    pos--;
                }
                if (pos<0 && i<(chops-1)) return -1;
            }
            return pos+1;
        }

        VerificationType parse_verification_type(int[] flags) {
            int tag = _stream.get_u1();
            if (tag < ITEM_UninitializedThis) {
                return VerificationType.from_tag(tag, _verifier);
            }
            if (tag == ITEM_Object) {
                int class_index = _stream.get_u2();
                int nconstants = _cp.entryCount();
                if (class_index <= 0 || class_index >= nconstants || _cp.tagAt(class_index) != VerifierImpl.JVM_CONSTANT_Class) {
                    _verifier.classError("bad class index");
                }
                return VerificationType.reference_type(_cp.classNameAt(class_index));
            }
            if (tag == ITEM_UninitializedThis) {
                if (flags != null) {
                    flags[0] |= VerificationFrame.FLAG_THIS_UNINIT;
                }
                return VerificationType.uninitialized_this_type;
            }
            if (tag == ITEM_Uninitialized) {
                int offset = _stream.get_u2();
                if (offset >= _code_length || _code_data[offset] != VerifierImpl.NEW_OFFSET) {
                    _verifier.classError("StackMapTable format error: bad offset for Uninitialized");
                }
                return VerificationType.uninitialized_type(offset);
            }
            _verifier.classError("bad verification type");
            return VerificationType.bogus_type;
        }

        VerificationFrame next_helper() {
            VerificationFrame frame;
            int offset;
            VerificationType[] locals = null;
            int frame_type = _stream.get_u1();
            if (frame_type < 64) {
                if (_first) {
                    offset = frame_type;
                    if (_prev_frame.locals_size() > 0) {
                        locals = new VerificationType[_prev_frame.locals_size()];
                    }
                } else {
                    offset = _prev_frame.offset() + frame_type + 1;
                    locals = _prev_frame.locals();
                }
                frame = new VerificationFrame(offset, _prev_frame.flags(), _prev_frame.locals_size(), 0, _max_locals, _max_stack, locals, null, _verifier);
                if (_first && locals != null) {
                    frame.copy_locals(_prev_frame);
                }
                _first = false;
                return frame;
            }
            if (frame_type < 128) {
                if (_first) {
                    offset = frame_type - 64;
                    if (_prev_frame.locals_size() > 0) {
                        locals = new VerificationType[_prev_frame.locals_size()];
                    }
                } else {
                    offset = _prev_frame.offset() + frame_type - 63;
                    locals = _prev_frame.locals();
                }
                VerificationType[] stack = new VerificationType[2];
                int stack_size = 1;
                stack[0] = parse_verification_type(null);
                if (stack[0].is_category2()) {
                    stack[1] = stack[0].to_category2_2nd(_verifier);
                    stack_size = 2;
                }
                check_verification_type_array_size(stack_size, _max_stack);
                frame = new VerificationFrame(offset, _prev_frame.flags(), _prev_frame.locals_size(), stack_size, _max_locals, _max_stack, locals, stack, _verifier);
                if (_first && locals != null) {
                    frame.copy_locals(_prev_frame);
                }
                _first = false;
                return frame;
            }
            int offset_delta = _stream.get_u2();
            if (frame_type < SAME_LOCALS_1_STACK_ITEM_EXTENDED) {
                _verifier.classError("reserved frame type");
            }
            if (frame_type == SAME_LOCALS_1_STACK_ITEM_EXTENDED) {
                if (_first) {
                    offset = offset_delta;
                    if (_prev_frame.locals_size() > 0) {
                        locals = new VerificationType[_prev_frame.locals_size()];
                    }
                } else {
                    offset = _prev_frame.offset() + offset_delta + 1;
                    locals = _prev_frame.locals();
                }
                VerificationType[] stack = new VerificationType[2];
                int stack_size = 1;
                stack[0] = parse_verification_type(null);
                if (stack[0].is_category2()) {
                    stack[1] = stack[0].to_category2_2nd(_verifier);
                    stack_size = 2;
                }
                check_verification_type_array_size(stack_size, _max_stack);
                frame = new VerificationFrame(offset, _prev_frame.flags(), _prev_frame.locals_size(), stack_size, _max_locals, _max_stack, locals, stack, _verifier);
                if (_first && locals != null) {
                    frame.copy_locals(_prev_frame);
                }
                _first = false;
                return frame;
            }
            if (frame_type <= SAME_EXTENDED) {
                locals = _prev_frame.locals();
                int length = _prev_frame.locals_size();
                int chops = SAME_EXTENDED - frame_type;
                int new_length = length;
                int flags = _prev_frame.flags();
                if (chops != 0) {
                    new_length = chop(locals, length, chops);
                    check_verification_type_array_size(new_length, _max_locals);
                    flags = 0;
                    for (int i=0; i<new_length; i++) {
                        if (locals[i].is_uninitialized_this(_verifier)) {
                            flags |= VerificationFrame.FLAG_THIS_UNINIT;
                            break;
                        }
                    }
                }
                if (_first) {
                    offset = offset_delta;
                    if (new_length > 0) {
                        locals = new VerificationType[new_length];
                    } else {
                        locals = null;
                    }
                } else {
                    offset = _prev_frame.offset() + offset_delta + 1;
                }
                frame = new VerificationFrame(offset, flags, new_length, 0, _max_locals, _max_stack, locals, null, _verifier);
                if (_first && locals != null) {
                    frame.copy_locals(_prev_frame);
                }
                _first = false;
                return frame;
            } else if (frame_type < SAME_EXTENDED + 4) {
                int appends = frame_type - SAME_EXTENDED;
                int real_length = _prev_frame.locals_size();
                int new_length = real_length + appends*2;
                locals = new VerificationType[new_length];
                VerificationType[] pre_locals = _prev_frame.locals();
                int i;
                for (i=0; i< _prev_frame.locals_size(); i++) {
                    locals[i] = pre_locals[i];
                }
                int[] flags = new int[]{_prev_frame.flags()};
                for (i=0; i<appends; i++) {
                    locals[real_length] = parse_verification_type(flags);
                    if (locals[real_length].is_category2()) {
                        locals[real_length + 1] = locals[real_length].to_category2_2nd(_verifier);
                        ++real_length;
                    }
                    ++real_length;
                }
                check_verification_type_array_size(real_length, _max_locals);
                if (_first) {
                    offset = offset_delta;
                } else {
                    offset = _prev_frame.offset() + offset_delta + 1;
                }
                frame = new VerificationFrame(offset, flags[0], real_length, 0, _max_locals, _max_stack, locals, null, _verifier);
                _first = false;
                return frame;
            }
            if (frame_type == FULL) {
                int flags[] = new int[]{0};
                int locals_size = _stream.get_u2();
                int real_locals_size = 0;
                if (locals_size > 0) {
                    locals = new VerificationType[locals_size*2];
                }
                int i;
                for (i=0; i<locals_size; i++) {
                    locals[real_locals_size] = parse_verification_type(flags);
                    if (locals[real_locals_size].is_category2()) {
                        locals[real_locals_size + 1] =
                                locals[real_locals_size].to_category2_2nd(_verifier);
                        ++real_locals_size;
                    }
                    ++real_locals_size;
                }
                check_verification_type_array_size(real_locals_size, _max_locals);
                int stack_size = _stream.get_u2();
                int real_stack_size = 0;
                VerificationType[] stack = null;
                if (stack_size > 0) {
                    stack = new VerificationType[stack_size*2];
                }
                for (i=0; i<stack_size; i++) {
                    stack[real_stack_size] = parse_verification_type(null);
                    if (stack[real_stack_size].is_category2()) {
                        stack[real_stack_size + 1] = stack[real_stack_size].to_category2_2nd(_verifier);
                        ++real_stack_size;
                    }
                    ++real_stack_size;
                }
                check_verification_type_array_size(real_stack_size, _max_stack);
                if (_first) {
                    offset = offset_delta;
                } else {
                    offset = _prev_frame.offset() + offset_delta + 1;
                }
                frame = new VerificationFrame(offset, flags[0], real_locals_size, real_stack_size, _max_locals, _max_stack, locals, stack, _verifier);
                _first = false;
                return frame;
            }
            _verifier.classError("reserved frame type");
            return null;
        }
    }
}
