# Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.

import os
import sys
import platform
import random
import re
import subprocess

OBJDUMP = "objdump"
X86_AS = "as"
X86_OBJCOPY = "objcopy"
SEED = 1327
TEST_DEMOTION = True

random.seed(SEED)

cond_to_suffix = {
    'overflow': 'o',
    'noOverflow': 'no',
    'below': 'b',
    'aboveEqual': 'ae',
    'zero': 'z',
    'notZero': 'nz',
    'belowEqual': 'be',
    'above': 'a',
    'negative': 's',
    'positive': 'ns',
    'parity': 'p',
    'noParity': 'np',
    'less': 'l',
    'greaterEqual': 'ge',
    'lessEqual': 'le',
    'greater': 'g',
}

shift_rot_ops = {'sarl', 'sarq', 'sall', 'salq', 'shll', 'shlq', 'shrl', 'shrq', 'shrdl', 'shrdq', 'shldl', 'shldq', 'rcrq', 'rorl', 'rorq', 'roll', 'rolq', 'rcll', 'rclq',
                 'esarl', 'esarq', 'esall', 'esalq', 'eshll', 'eshlq', 'eshrl', 'eshrq', 'eshrdl', 'eshrdq', 'eshldl', 'eshldq', 'ercrq', 'erorl', 'erorq', 'eroll', 'erolq', 'ercll', 'erclq'}

registers_mapping = {
    # skip rax, rsi, rdi, rsp, rbp as they have special encodings
    'rax': {64: 'rax', 32: 'eax', 16: 'ax', 8: 'al'},
    'rcx': {64: 'rcx', 32: 'ecx', 16: 'cx', 8: 'cl'},
    'rdx': {64: 'rdx', 32: 'edx', 16: 'dx', 8: 'dl'},
    'rbx': {64: 'rbx', 32: 'ebx', 16: 'bx', 8: 'bl'},
    # 'rsp': {64: 'rsp', 32: 'esp', 16: 'sp', 8: 'spl'},
    # 'rbp': {64: 'rbp', 32: 'ebp', 16: 'bp', 8: 'bpl'},
    # 'rsi': {64: 'rsi', 32: 'esi', 16: 'si', 8: 'sil'},
    # 'rdi': {64: 'rdi', 32: 'edi', 16: 'di', 8: 'dil'},
    'r8': {64: 'r8', 32: 'r8d', 16: 'r8w', 8: 'r8b'},
    'r9': {64: 'r9', 32: 'r9d', 16: 'r9w', 8: 'r9b'},
    'r10': {64: 'r10', 32: 'r10d', 16: 'r10w', 8: 'r10b'},
    'r11': {64: 'r11', 32: 'r11d', 16: 'r11w', 8: 'r11b'},
    'r12': {64: 'r12', 32: 'r12d', 16: 'r12w', 8: 'r12b'},
    'r13': {64: 'r13', 32: 'r13d', 16: 'r13w', 8: 'r13b'},
    'r14': {64: 'r14', 32: 'r14d', 16: 'r14w', 8: 'r14b'},
    'r15': {64: 'r15', 32: 'r15d', 16: 'r15w', 8: 'r15b'},
    'r16': {64: 'r16', 32: 'r16d', 16: 'r16w', 8: 'r16b'},
    'r17': {64: 'r17', 32: 'r17d', 16: 'r17w', 8: 'r17b'},
    'r18': {64: 'r18', 32: 'r18d', 16: 'r18w', 8: 'r18b'},
    'r19': {64: 'r19', 32: 'r19d', 16: 'r19w', 8: 'r19b'},
    'r20': {64: 'r20', 32: 'r20d', 16: 'r20w', 8: 'r20b'},
    'r21': {64: 'r21', 32: 'r21d', 16: 'r21w', 8: 'r21b'},
    'r22': {64: 'r22', 32: 'r22d', 16: 'r22w', 8: 'r22b'},
    'r23': {64: 'r23', 32: 'r23d', 16: 'r23w', 8: 'r23b'},
    'r24': {64: 'r24', 32: 'r24d', 16: 'r24w', 8: 'r24b'},
    'r25': {64: 'r25', 32: 'r25d', 16: 'r25w', 8: 'r25b'},
    'r26': {64: 'r26', 32: 'r26d', 16: 'r26w', 8: 'r26b'},
    'r27': {64: 'r27', 32: 'r27d', 16: 'r27w', 8: 'r27b'},
    'r28': {64: 'r28', 32: 'r28d', 16: 'r28w', 8: 'r28b'},
    'r29': {64: 'r29', 32: 'r29d', 16: 'r29w', 8: 'r29b'},
    'r30': {64: 'r30', 32: 'r30d', 16: 'r30w', 8: 'r30b'},
    'r31': {64: 'r31', 32: 'r31d', 16: 'r31w', 8: 'r31b'},
}

class Operand(object):
    def generate(self):
        return self

class Register(Operand):
    def generate(self, reg, width):
        self.reg = reg
        self.areg = registers_mapping.get(reg, {}).get(width, reg)
        return self

    def cstr(self):
        return self.reg

    def astr(self):
        return self.areg

class Immediate(Operand):
    def generate(self, value):
        self._value = value
        return self

    def cstr(self):
        return str(self._value)

    def astr(self):
        return str(self._value)

class Address(Operand):
    width_to_ptr = {
        8: "byte ptr",
        16: "word ptr",
        32: "dword ptr",
        64: "qword ptr"
    }

    def generate(self, base, index, width):
        self.base = Register().generate(base, 64)
        self.index = Register().generate(index, 64)
        self._width = width
        self._scale_factor = random.choice([-1, 0, 1, 2, 3])
        self._disp = random.randint(-2**31, 2**31 - 1)
        return self

    def cstr(self):
        disp_str = "{0:+#x}".format(self._disp)
        if self._scale_factor == -1:
            return f"Address({self.base.cstr()}, {disp_str})"
        else:
            return f"Address({self.base.cstr()}, {self.index.cstr()}, (Address::ScaleFactor){self._scale_factor}, {disp_str})"

    def astr(self):
        ptr_str = self.width_to_ptr.get(self._width, "qword ptr")
        disp_str = "{0:+#x}".format(self._disp)
        if self._scale_factor == -1:
            return f"{ptr_str} [{self.base.cstr() + disp_str}]"
        else:
            return f"{ptr_str} [{self.base.cstr()}+{self.index.cstr()}*{2 ** self._scale_factor}{disp_str}]"

class Instruction(object):
    def __init__(self, name, aname):
        self._name = name
        self._aname = aname

    def generate_operands(self, *operands):
        self.operands = [operand for operand in operands]

    def cstr(self):
        return f'__ {self._name}(' + ', '.join([op.cstr() for op in self.operands]) + ');'

    def astr(self):
        # JDK assembler uses 'cl' for shift instructions with one operand by default
        cl_str = (', cl' if self._name in shift_rot_ops and len(self.operands) == 1 else '')
        return f'{self._aname} ' + ', '.join([op.astr() for op in self.operands]) + cl_str

class NFInstruction(Instruction):
    def __init__(self, name, aname, no_flag):
        super().__init__(name, aname)
        self.no_flag = no_flag
    
    def cstr(self):
        return f'__ {self._name}(' + ', '.join([op.cstr() for op in self.operands]) + (f', {str(self.no_flag).lower()}' if self.no_flag is not None else '') + ');'
    
    def astr(self):
        # JDK assembler uses 'cl' for shift instructions with one operand by default
        cl_str = (', cl' if self._name in shift_rot_ops and len(self.operands) == 2 else '')
        # special case for shift instructions with three operands
        if (self._name == 'eshldl' or self._name == 'eshldq' or self._name == 'eshrdl' or self._name == 'eshrdq') and all([isinstance(op, Register) for op in self.operands]):
            cl_str = ', cl'
        return ('{NF}' if self.no_flag else '{EVEX}') + f'{self._aname} ' + ', '.join([op.astr() for op in self.operands]) + cl_str

class RegInstruction(Instruction):
    def __init__(self, name, aname, width, reg):
        super().__init__(name, aname)
        self.reg = Register().generate(reg, width)
        self.generate_operands(self.reg)

class MemInstruction(Instruction):
    def __init__(self, name, aname, width, mem_base, mem_idx):
        super().__init__(name, aname)
        self.mem = Address().generate(mem_base, mem_idx, width)
        self.generate_operands(self.mem)

class TwoRegInstruction(Instruction):
    def __init__(self, name, aname, width, reg1, reg2):
        super().__init__(name, aname)
        self.reg1 = Register().generate(reg1, width)
        self.reg2 = Register().generate(reg2, width)
        self.generate_operands(self.reg1, self.reg2)

    def astr(self):
        return f'{{load}}' + super().astr()

class MemRegInstruction(Instruction):
    def __init__(self, name, aname, width, reg, mem_base, mem_idx):
        super().__init__(name, aname)
        self.mem = Address().generate(mem_base, mem_idx, width)
        self.reg = Register().generate(reg, width)
        self.generate_operands(self.mem, self.reg)

class RegMemInstruction(Instruction):
    def __init__(self, name, aname, width, reg, mem_base, mem_idx):
        super().__init__(name, aname)
        self.reg = Register().generate(reg, width)
        self.mem = Address().generate(mem_base, mem_idx, width)
        self.generate_operands(self.reg, self.mem)

class RegImmInstruction(Instruction):
    def __init__(self, name, aname, width, reg, imm):
        super().__init__(name, aname)
        self.reg = Register().generate(reg, width)
        self.imm = Immediate().generate(imm)
        self.generate_operands(self.reg, self.imm)

class MemImmInstruction(Instruction):
    def __init__(self, name, aname, width, imm, mem_base, mem_idx):
        super().__init__(name, aname)
        self.mem = Address().generate(mem_base, mem_idx, width)
        self.imm = Immediate().generate(imm)
        self.generate_operands(self.mem, self.imm)

class RegRegImmInstruction(Instruction):
    def __init__(self, name, aname, width, reg1, reg2, imm):
        super().__init__(name, aname)
        self.reg1 = Register().generate(reg1, width)
        self.reg2 = Register().generate(reg2, width)
        self.imm = Immediate().generate(imm)
        self.generate_operands(self.reg1, self.reg2, self.imm)

class RegMemImmInstruction(Instruction):
    def __init__(self, name, aname, width, reg, imm, mem_base, mem_idx):
        super().__init__(name, aname)
        self.reg = Register().generate(reg, width)
        self.mem = Address().generate(mem_base, mem_idx, width)
        self.imm = Immediate().generate(imm)
        self.generate_operands(self.reg, self.mem, self.imm)

class Pop2Instruction(TwoRegInstruction):
    def __init__(self, name, aname, width, reg1, reg2):
        super().__init__(name, aname, width, reg1, reg2)

    def cstr(self):
        # reverse to match the order in OpenJDK
        return f'__ {self._name}(' + ', '.join([reg.cstr() for reg in reversed(self.operands)]) + ');'

class Push2Instruction(TwoRegInstruction):
    def __init__(self, name, aname, width, reg1, reg2):
        super().__init__(name, aname, width, reg1, reg2)

    def cstr(self):
        # reverse to match the order in OpenJDK
        return f'__ {self._name}(' + ', '.join([reg.cstr() for reg in reversed(self.operands)]) + ');'

class CmpxchgInstruction(MemRegInstruction):
    def __init__(self, name, aname, width, reg, mem_base, mem_idx):
        super().__init__(name, aname, width, reg, mem_base, mem_idx)

    def cstr(self):
        # reverse to match the order in OpenJDK
        return f'__ {self._name}(' + ', '.join([reg.cstr() for reg in reversed(self.operands)]) + ');'

class CondRegMemInstruction(RegMemInstruction):
    def __init__(self, name, aname, width, cond, reg, mem_base, mem_idx):
        super().__init__(name, aname, width, reg, mem_base, mem_idx)
        self.cond = cond

    def cstr(self):
        return f'__ {self._name}(' + 'Assembler::Condition::' + self.cond + ', ' + ', '.join([self.reg.cstr(), self.mem.cstr()]) + ');'

    def astr(self):
        return f'{self._aname}' + cond_to_suffix[self.cond] + ' ' + ', '.join([self.reg.astr(), self.mem.astr()])

class CondRegInstruction(RegInstruction):
    def __init__(self, name, aname, width, cond, reg):
        super().__init__(name, aname, width, reg)
        self.cond = cond

    def cstr(self):
        return f'__ {self._name}b(' + 'Assembler::Condition::' + self.cond + ', ' + self.reg.cstr() + ');'

    def astr(self):
        return f'{self._aname}' + cond_to_suffix[self.cond] + ' ' + self.reg.astr()

class CondRegRegRegInstruction(Instruction):
    def __init__(self, name, aname, width, cond, reg1, reg2, reg3):
        super().__init__(name, aname)
        self.reg1 = Register().generate(reg1, width)
        self.reg2 = Register().generate(reg2, width)
        self.reg3 = Register().generate(reg3, width)
        self.cond = cond
        self.generate_operands(self.reg1, self.reg2, self.reg3)
        self.demote = True
    
    def cstr(self):
        return f'__ {self._name} (' + 'Assembler::Condition::' + self.cond + ', ' + ', '.join([reg.cstr() for reg in self.operands]) + ');'
    
    def astr(self):
        operands = self.operands
        if self.demote:
            ops = [op.cstr() for op in self.operands]
            if ops[0] == ops[1]:
                operands = operands[1:]
        return f'{self._aname}' + cond_to_suffix[self.cond] + ' ' + ', '.join([reg.astr() for reg in operands])

class CondRegRegMemInstruction(Instruction):
    def __init__(self, name, aname, width, cond, reg1, reg2, mem_base, mem_idx):
        super().__init__(name, aname)
        self.reg1 = Register().generate(reg1, width)
        self.reg2 = Register().generate(reg2, width)
        self.mem = Address().generate(mem_base, mem_idx, width)
        self.cond = cond
        self.generate_operands(self.reg1, self.reg2, self.mem)
        self.demote = True
    
    def cstr(self):
        return f'__ {self._name} (' + 'Assembler::Condition::' + self.cond + ', ' + ', '.join([reg.cstr() for reg in self.operands]) + ');'
    
    def astr(self):
        operands = self.operands
        if self.demote:
            ops = [op.cstr() for op in self.operands]
            if ops[0] == ops[1]:
                operands = operands[1:]
        return f'{self._aname}' + cond_to_suffix[self.cond] + ' ' + ', '.join([reg.astr() for reg in operands])

class MoveRegMemInstruction(Instruction):
    def __init__(self, name, aname, width, mem_width, reg, mem_base, mem_idx):
        super().__init__(name, aname)
        self.reg = Register().generate(reg, width)
        self.mem = Address().generate(mem_base, mem_idx, mem_width)
        self.generate_operands(self.reg, self.mem)

class MoveRegRegInstruction(Instruction):
    def __init__(self, name, aname, width, reg_width, reg1, reg2):
        super().__init__(name, aname)
        self.reg1 = Register().generate(reg1, width)
        self.reg2 = Register().generate(reg2, reg_width)
        self.generate_operands(self.reg1, self.reg2)

class RegNddInstruction(NFInstruction):
    def __init__(self, name, aname, width, no_flag, reg):
        super().__init__(name, aname, no_flag)
        self.reg = Register().generate(reg, width)
        self.generate_operands(self.reg)

class MemNddInstruction(NFInstruction):
    def __init__(self, name, aname, width, no_flag, mem_base, mem_idx):
        super().__init__(name, aname, no_flag)
        self.mem = Address().generate(mem_base, mem_idx, width)
        self.generate_operands(self.mem)

class RegRegNddInstruction(NFInstruction):
    def __init__(self, name, aname, width, no_flag, reg1, reg2):
        super().__init__(name, aname, no_flag)
        self.reg1 = Register().generate(reg1, width)
        self.reg2 = Register().generate(reg2, width)
        self.generate_operands(self.reg1, self.reg2)
        self.demote = True

    def astr(self):
        if self.demote and self._aname not in ['popcnt', 'lzcnt', 'tzcnt']:
            ops = [op.cstr() for op in self.operands]
            if ops[0] == ops[1] and (not self.no_flag):
                cl_str = (', cl' if self._name in shift_rot_ops and len(self.operands) == 2 else '')
                return  f'{self._aname} ' + ', '.join([op.astr() for op in self.operands[1:]]) + cl_str
        return super().astr()

class RegMemNddInstruction(NFInstruction):
    def __init__(self, name, aname, width, no_flag, reg, mem_base, mem_idx):
        super().__init__(name, aname, no_flag)
        self.reg = Register().generate(reg, width)
        self.mem = Address().generate(mem_base, mem_idx, width)
        self.generate_operands(self.reg, self.mem)

class RegMemImmNddInstruction(NFInstruction):
    def __init__(self, name, aname, width, no_flag, reg, imm, mem_base, mem_idx):
        super().__init__(name, aname, no_flag)
        self.reg = Register().generate(reg, width)
        self.mem = Address().generate(mem_base, mem_idx, width)
        self.imm = Immediate().generate(imm)
        self.generate_operands(self.reg, self.mem, self.imm)

class RegMemRegNddInstruction(NFInstruction):
    def __init__(self, name, aname, width, no_flag, reg1, mem_base, mem_idx, reg2):
        super().__init__(name, aname, no_flag)
        self.reg1 = Register().generate(reg1, width)
        self.mem = Address().generate(mem_base, mem_idx, width)
        self.reg2 = Register().generate(reg2, width)
        self.generate_operands(self.reg1, self.mem, self.reg2)

class RegRegImmNddInstruction(NFInstruction):
    def __init__(self, name, aname, width, no_flag, reg1, reg2, imm):
        super().__init__(name, aname, no_flag)
        self.reg1 = Register().generate(reg1, width)
        self.reg2 = Register().generate(reg2, width)
        self.imm = Immediate().generate(imm)
        self.generate_operands(self.reg1, self.reg2, self.imm)
        self.demote = True

    def astr(self):
        if self.demote and self._aname not in ['imul']:
            ops = [op.cstr() for op in self.operands]
            if ops[0] == ops[1] and (not self.no_flag):
                return  f'{self._aname} ' + ', '.join([op.astr() for op in self.operands[1:]])
        return super().astr()

class RegRegMemNddInstruction(NFInstruction):
    def __init__(self, name, aname, width, no_flag, reg1, reg2, mem_base, mem_idx):
        super().__init__(name, aname, no_flag)
        self.reg1 = Register().generate(reg1, width)
        self.reg2 = Register().generate(reg2, width)
        self.mem = Address().generate(mem_base, mem_idx, width)
        self.generate_operands(self.reg1, self.reg2, self.mem)
        self.demote = True

    def astr(self):
        if self.demote:
            ops = [op.cstr() for op in self.operands]
            if ops[0] == ops[1] and (not self.no_flag):
                return  f'{self._aname} ' + ', '.join([op.astr() for op in self.operands[1:]])
        return super().astr()

class RegRegRegNddInstruction(NFInstruction):
    def __init__(self, name, aname, width, no_flag, reg1, reg2, reg3):
        super().__init__(name, aname, no_flag)
        self.reg1 = Register().generate(reg1, width)
        self.reg2 = Register().generate(reg2, width)
        self.reg3 = Register().generate(reg3, width)
        self.generate_operands(self.reg1, self.reg2, self.reg3)
        self.demote = True

    def astr(self):
        hdr = f'{{load}}'
        if self.demote:
            ops = [op.cstr() for op in self.operands]
            if ops[0] == ops[1] and (not self.no_flag):
                return  hdr + f'{self._aname} ' + ', '.join([op.astr() for op in self.operands[1:]])
        return hdr + super().astr()

class RegRegRegImmNddInstruction(NFInstruction):
    def __init__(self, name, aname, width, no_flag, reg1, reg2, reg3, imm):
        super().__init__(name, aname, no_flag)
        self.reg1 = Register().generate(reg1, width)
        self.reg2 = Register().generate(reg2, width)
        self.reg3 = Register().generate(reg3, width)
        self.imm = Immediate().generate(imm)
        self.generate_operands(self.reg1, self.reg2, self.reg3, self.imm)
        self.demote = True

    def astr(self):
        if self.demote:
            ops = [op.cstr() for op in self.operands]
            if ops[0] == ops[1] and (not self.no_flag):
                return (f'{self._aname} ' + ', '.join([op.astr() for op in self.operands[1:]]))
        return super().astr()


test_regs = [key for key in registers_mapping.keys() if key != 'rax']

immediates32 = [2 ** i for i in range(0, 32, 4)]
immediates16 = [2 ** i for i in range(0, 16, 2)]
immediates8 = [2 ** i for i in range(0, 8, 2)]
immediates5 = [2 ** i for i in range(0, 5, 1)]
immediate_values_8_to_16_bit = [2 ** i for i in range(8, 16, 2)]
immediate_values_16_to_32_bit = [2 ** i for i in range(16, 32, 2)]
immediate_values_32_to_64_bit = [2 ** i for i in range(32, 64, 2)]
negative_immediates32 = [-2 ** i for i in range(0, 32, 4)]

immediate_map = {
    8: immediates8,
    16: immediates16,
    32: immediates32,
    64: immediates32
}

def is_64_reg(reg):
    return reg in {'r8', 'r9', 'r10', 'r11', 'r12', 'r13', 'r14', 'r15', 'r16', 'r17', 'r18', 'r19', 'r20', 'r21', 'r22', 'r23', 'r24', 'r25', 'r26', 'r27', 'r28', 'r29', 'r30', 'r31'}

def print_instruction(instr, lp64_flag, print_lp64_flag):
    cstr = instr.cstr()
    astr = instr.astr()
    print("    %-75s //    %s    IID%s" % (cstr, astr, len(ifdef_flags)))
    ifdef_flags.append(lp64_flag or not print_lp64_flag)
    insns_strs.append(cstr)
    outfile.write(f"    {astr}\n")

def handle_lp64_flag(lp64_flag, print_lp64_flag, *regs):
    for reg in regs:
        if is_64_reg(reg):
            if not lp64_flag and print_lp64_flag:
                print("#ifdef _LP64")
            return True
    if lp64_flag and print_lp64_flag:
        print("#endif // _LP64")
        return False
    return lp64_flag

def get_immediate_list(op_name, width):
    # special cases
    word_imm_ops = {'addw', 'cmpw'}
    dword_imm_ops = {'subl_imm32', 'subq_imm32', 'orq_imm32', 'cmpl_imm32', 'esubl_imm32', 'esubq_imm32', 'eorq_imm32', 'testl'}
    qword_imm_ops = {'mov64'}
    neg_imm_ops = {'testq'}
    bt_ops = {'btq'}

    if op_name in shift_rot_ops:
        return immediates5
    elif op_name in bt_ops:
        return immediate_map[8]
    elif op_name in word_imm_ops:
        return immediate_values_8_to_16_bit
    elif op_name in dword_imm_ops:
        return immediate_values_16_to_32_bit
    elif op_name in qword_imm_ops:
        return immediate_values_32_to_64_bit
    elif op_name in neg_imm_ops:
        return negative_immediates32
    else:
        return immediate_map[width]

lp64_flag = False
def generate(RegOp, ops, print_lp64_flag=True, full_set=False):
    global lp64_flag
    for op in ops:
        op_name = op[0]
        width = op[2]

        if RegOp in [RegInstruction, CondRegInstruction, RegNddInstruction]:
            if full_set:
                for i in range(len(test_regs)):
                    test_reg = test_regs[i]
                    lp64_flag = handle_lp64_flag(lp64_flag, print_lp64_flag, test_reg)
                    instr = RegOp(*op, reg=test_reg)
                    print_instruction(instr, lp64_flag, print_lp64_flag)
            else:
                test_reg = random.choice(test_regs)
                lp64_flag = handle_lp64_flag(lp64_flag, print_lp64_flag, test_reg)
                instr = RegOp(*op, reg=test_reg)
                print_instruction(instr, lp64_flag, print_lp64_flag)

        elif RegOp in [TwoRegInstruction, MoveRegRegInstruction, RegRegNddInstruction]:
            demote_options = [False, True] if TEST_DEMOTION and RegOp in [RegRegNddInstruction]  else [False]
            for demote in demote_options:
                for i in range(len(test_regs) if full_set else 1):
                    test_reg1 = test_regs[i] if full_set else random.choice(test_regs)
                    test_reg2 = test_reg1 if demote \
                                          else test_regs[(i + 1) % len(test_regs)] if full_set \
                                          else random.choice(test_regs)
                    lp64_flag = handle_lp64_flag(lp64_flag, print_lp64_flag, test_reg1, test_reg2)
                    instr = RegOp(*op, reg1=test_reg1, reg2=test_reg2)
                    print_instruction(instr, lp64_flag, print_lp64_flag)

        elif RegOp in [RegRegRegNddInstruction, CondRegRegRegInstruction]:
            for demote in [False, True] if TEST_DEMOTION else [False]:
                for i in range(len(test_regs) if full_set else 1):
                    test_reg1 = test_regs[i] if full_set else random.choice(test_regs)
                    test_reg2 = test_reg1 if demote \
                                          else test_regs[(i + 1) % len(test_regs)] if full_set \
                                          else random.choice(test_regs)
                    test_reg3 = test_regs[(i + 2) % len(test_regs)] if full_set else random.choice(test_regs)
                    lp64_flag = handle_lp64_flag(lp64_flag, print_lp64_flag, test_reg1, test_reg2, test_reg3)
                    instr = RegOp(*op, reg1=test_reg1, reg2=test_reg2, reg3=test_reg3)
                    print_instruction(instr, lp64_flag, print_lp64_flag)

        elif RegOp in [MemRegInstruction, RegMemInstruction, MoveRegMemInstruction, CmpxchgInstruction, CondRegMemInstruction, RegMemNddInstruction]:
            if full_set:
                for i in range(len(test_regs)):
                    test_reg = test_regs[i]
                    test_mem_base = test_regs[(i + 1) % len(test_regs)]
                    test_mem_idx = test_regs[(i + 2) % len(test_regs)]
                    if test_mem_idx == 'rsp':
                        continue
                    lp64_flag = handle_lp64_flag(lp64_flag, print_lp64_flag, test_reg, test_mem_base, test_mem_idx)
                    instr = RegOp(*op, reg=test_reg, mem_base=test_mem_base, mem_idx=test_mem_idx)
                    print_instruction(instr, lp64_flag, print_lp64_flag)
            else:
                filtered_regs = [reg for reg in test_regs if reg != 'rsp']
                test_reg = random.choice(test_regs)
                test_mem_base = random.choice(test_regs)
                test_mem_idx = random.choice(filtered_regs)
                lp64_flag = handle_lp64_flag(lp64_flag, print_lp64_flag, test_reg, test_mem_base, test_mem_idx)
                instr = RegOp(*op, reg=test_reg, mem_base=test_mem_base, mem_idx=test_mem_idx)
                print_instruction(instr, lp64_flag, print_lp64_flag)

        elif RegOp in [RegImmInstruction]:
            if full_set:
                imm_list = get_immediate_list(op_name, width)
                for i in range(len(test_regs)):
                    test_reg = test_regs[i]
                    lp64_flag = handle_lp64_flag(lp64_flag, print_lp64_flag, test_reg)
                    for imm in imm_list:
                        instr = RegOp(*op, reg=test_reg, imm=imm)
                        print_instruction(instr, lp64_flag, print_lp64_flag)
            else:
                test_reg = random.choice(test_regs)
                imm = random.choice(get_immediate_list(op_name, width))
                lp64_flag = handle_lp64_flag(lp64_flag, print_lp64_flag, test_reg)
                instr = RegOp(*op, reg=test_reg, imm=imm)
                print_instruction(instr, lp64_flag, print_lp64_flag)

        elif RegOp in [MemImmInstruction]:
            if full_set:
                imm_list = get_immediate_list(op_name, width)
                for imm in imm_list:
                    for i in range(len(test_regs)):
                        test_mem_base = test_regs[i]
                        test_mem_idx = test_regs[(i + 1) % len(test_regs)]
                        if test_mem_idx == 'rsp':
                            continue
                        lp64_flag = handle_lp64_flag(lp64_flag, print_lp64_flag, test_mem_base, test_mem_idx)
                        instr = RegOp(*op, imm=imm, mem_base=test_mem_base, mem_idx=test_mem_idx)
                        print_instruction(instr, lp64_flag, print_lp64_flag)

            else:
                filtered_regs = [reg for reg in test_regs if reg != 'rsp']
                imm = random.choice(get_immediate_list(op_name, width))
                test_mem_base = random.choice(test_regs)
                test_mem_idx = random.choice(filtered_regs)
                lp64_flag = handle_lp64_flag(lp64_flag, print_lp64_flag, test_mem_base, test_mem_idx)
                instr = RegOp(*op, imm=imm, mem_base=test_mem_base, mem_idx=test_mem_idx)
                print_instruction(instr, lp64_flag, print_lp64_flag)

        elif RegOp in [MemInstruction, MemNddInstruction]:
            if full_set:
                for i in range(len(test_regs)):
                    test_mem_base = test_regs[i]
                    test_mem_idx = test_regs[(i + 1) % len(test_regs)]
                    if test_mem_idx == 'rsp':
                        continue
                    lp64_flag = handle_lp64_flag(lp64_flag, print_lp64_flag, test_mem_base, test_mem_idx)
                    instr = RegOp(*op, mem_base=test_mem_base, mem_idx=test_mem_idx)
                    print_instruction(instr, lp64_flag, print_lp64_flag)
            else:
                filtered_regs = [reg for reg in test_regs if reg != 'rsp']
                test_mem_base = random.choice(test_regs)
                test_mem_idx = random.choice(filtered_regs)
                lp64_flag = handle_lp64_flag(lp64_flag, print_lp64_flag, test_mem_base, test_mem_idx)
                instr = RegOp(*op, mem_base=test_mem_base, mem_idx=test_mem_idx)
                print_instruction(instr, lp64_flag, print_lp64_flag)

        elif RegOp in [RegRegImmInstruction, RegRegImmNddInstruction]:
            demote_options = [False, True] if TEST_DEMOTION and RegOp in [RegRegImmNddInstruction] else [False]
            for demote in demote_options:
                imm_list = get_immediate_list(op_name, width)
                if not full_set:
                    imm_list = [random.choice(imm_list)]
                for i in range(len(test_regs) if full_set else 1):
                    test_reg1 = test_regs[i] if full_set else random.choice(test_regs)
                    test_reg2 = test_reg1 if demote \
                                          else test_regs[(i + 1) % len(test_regs)] if full_set \
                                          else random.choice(test_regs)
                    lp64_flag = handle_lp64_flag(lp64_flag, print_lp64_flag, test_reg1, test_reg2)
                    for imm in imm_list:
                        instr = RegOp(*op, reg1=test_reg1, reg2=test_reg2, imm=imm)
                        print_instruction(instr, lp64_flag, print_lp64_flag)

                    # additional tests with rax as destination
                    if RegOp in [RegRegImmNddInstruction] and not demote and not full_set:
                        test_reg1 = 'rax'
                        test_reg2 = random.choice(test_regs)
                        lp64_flag = handle_lp64_flag(lp64_flag, print_lp64_flag, test_reg1, test_reg2)
                        instr = RegOp(*op, reg1=test_reg1, reg2=test_reg2, imm=imm)
                        print_instruction(instr, lp64_flag, print_lp64_flag)

        elif RegOp in [RegMemImmInstruction, RegMemImmNddInstruction]:
            if full_set:
                imm_list = get_immediate_list(op_name, width)
                for i in range(len(test_regs)):
                    test_reg = test_regs[i]
                    test_mem_base = test_regs[(i + 1) % len(test_regs)]
                    test_mem_idx = test_regs[(i + 2) % len(test_regs)]
                    if test_mem_idx == 'rsp':
                        continue
                    lp64_flag = handle_lp64_flag(lp64_flag, print_lp64_flag, test_reg, test_mem_base, test_mem_idx)
                    for imm in imm_list:
                        instr = RegOp(*op, reg=test_reg, mem_base=test_mem_base, mem_idx=test_mem_idx, imm=imm)
                        print_instruction(instr, lp64_flag, print_lp64_flag)
            else:
                imm = random.choice(get_immediate_list(op_name, width))
                filtered_regs = [reg for reg in test_regs if reg != 'rsp']
                test_reg = random.choice(test_regs)
                test_mem_base = random.choice(test_regs)
                test_mem_idx = random.choice(filtered_regs)
                lp64_flag = handle_lp64_flag(lp64_flag, print_lp64_flag, test_reg, test_mem_base, test_mem_idx)
                instr = RegOp(*op, reg=test_reg, imm=imm, mem_base=test_mem_base, mem_idx=test_mem_idx)
                print_instruction(instr, lp64_flag, print_lp64_flag)

        elif RegOp in [RegMemRegNddInstruction, RegRegMemNddInstruction, CondRegRegMemInstruction]:
            demote_options = [False] if TEST_DEMOTION and RegOp not in [RegMemRegNddInstruction] else [False, True]
            for demote in demote_options:
                for i in range(len(test_regs) if full_set else 1):
                    test_reg1 = test_regs[i] if full_set else random.choice(test_regs)
                    test_mem_base = test_regs[(i + 1) % len(test_regs)] if full_set else random.choice(test_regs)
                    test_mem_idx = test_regs[(i + 2) % len(test_regs)] if full_set \
                                   else random.choice([reg for reg in test_regs if reg != 'rsp'])
                    test_reg2 = test_reg1 if demote \
                                          else test_regs[(i + 3) % len(test_regs)] if full_set \
                                          else random.choice(test_regs)
                    if test_mem_idx == 'rsp':
                        continue
                    lp64_flag = handle_lp64_flag(lp64_flag, print_lp64_flag, test_reg1, test_mem_base, test_mem_idx, test_reg2)
                    instr = RegOp(*op, reg1=test_reg1, mem_base=test_mem_base, mem_idx=test_mem_idx, reg2=test_reg2)
                    print_instruction(instr, lp64_flag, print_lp64_flag)

        elif RegOp in [RegRegRegImmNddInstruction]:
            for demote in [False, True] if TEST_DEMOTION else [False]:
                imm_list = get_immediate_list(op_name, width)
                if not full_set:
                    imm_list = [random.choice(imm_list)]
                for i in range(len(test_regs) if full_set else 1):
                    test_reg1 = test_regs[i] if full_set else random.choice(test_regs)
                    test_reg2 = test_reg1 if demote \
                                          else test_regs[(i + 1) % len(test_regs)] if full_set \
                                          else random.choice(test_regs)
                    test_reg3 = test_regs[(i + 2) % len(test_regs)] if full_set else random.choice(test_regs)
                    lp64_flag = handle_lp64_flag(lp64_flag, print_lp64_flag, test_reg1, test_reg2, test_reg3)
                    for imm in imm_list:
                        instr = RegOp(*op, reg1=test_reg1, reg2=test_reg2, reg3=test_reg3, imm=imm)
                        print_instruction(instr, lp64_flag, print_lp64_flag)

        elif RegOp in [Push2Instruction, Pop2Instruction]:
            if full_set:
                for i in range(len(test_regs)):
                    test_reg1 = test_regs[i]
                    test_reg2 = test_regs[(i + 1) % len(test_regs)]
                    lp64_flag = handle_lp64_flag(lp64_flag, print_lp64_flag, test_reg1, test_reg2)
                    if test_reg1 == 'rsp' or test_reg2 == 'rsp':
                        continue
                    instr = RegOp(*op, reg1=test_reg1, reg2=test_reg2)
                    print_instruction(instr, lp64_flag, print_lp64_flag)
            else:
                filtered_regs = [reg for reg in test_regs if reg != 'rsp']
                test_reg1, test_reg2 = random.sample(filtered_regs, 2)
                lp64_flag = handle_lp64_flag(lp64_flag, print_lp64_flag, test_reg1, test_reg2)
                instr = RegOp(*op, reg1=test_reg1, reg2=test_reg2)
                print_instruction(instr, lp64_flag, print_lp64_flag)

        else:
            raise ValueError(f"Unsupported instruction type: {RegOp}")

def print_with_ifdef(ifdef_flags, items, item_formatter, width):
    under_defined = False
    iid = 0
    for idx, item in enumerate(items):
        if ifdef_flags[idx]:
            if not under_defined:
                print("#ifdef _LP64")
                under_defined = True
        else:
            if under_defined:
                print("#endif // _LP64")
                under_defined = False
        print("    %-*s // IID%s" % (width, item_formatter(item) + ",", iid))
        iid += 1
    if under_defined:
        print("#endif // _LP64")

instruction_set = {
    TwoRegInstruction: [
        ('shldl', 'shld', 32),
        ('shrdl', 'shrd', 32),
        ('adcl', 'adc', 32),
        ('cmpl', 'cmp', 32),
        ('imull', 'imul', 32),
        ('popcntl', 'popcnt', 32),
        ('sbbl', 'sbb', 32),
        ('subl', 'sub', 32),
        ('tzcntl', 'tzcnt', 32),
        ('lzcntl', 'lzcnt', 32),
        ('addl', 'add', 32),
        ('andl', 'and', 32),
        ('orl', 'or', 32),
        ('xorl', 'xor', 32),
        ('movl', 'mov', 32),
        ('bsfl', 'bsf', 32),
        ('bsrl', 'bsr', 32),
        ('xchgl', 'xchg', 32),
        ('testl', 'test', 32),
    ],
    MemRegInstruction: [
        ('addb', 'add', 8),
        ('addw', 'add', 16),
        ('addl', 'add', 32),
        ('adcl', 'adc', 32),
        ('andb', 'and', 8),
        ('andl', 'and', 32),
        ('cmpb', 'cmp', 8),
        ('cmpw', 'cmp', 16),
        ('cmpl', 'cmp', 32),
        ('orb', 'or', 8),
        ('orl', 'or', 32),
        ('xorb', 'xor', 8),
        ('xorl', 'xor', 32),
        ('subl', 'sub', 32),
        ('movb', 'mov', 8),
        ('movl', 'mov', 32),
        ('xaddb', 'xadd', 8),
        ('xaddw', 'xadd', 16),
        ('xaddl', 'xadd', 32),
    ],
    MemImmInstruction: [
        ('adcl', 'adc', 32),
        ('andl', 'and', 32),
        ('addb', 'add', 8),
        ('addw', 'add', 16),
        ('addl', 'add', 32),
        ('cmpb', 'cmp', 8),
        ('cmpw', 'cmp', 16),
        ('cmpl', 'cmp', 32),
        ('sarl', 'sar', 32),
        ('sall', 'sal', 32),
        ('sbbl', 'sbb', 32),
        ('shrl', 'shr', 32),
        ('subl', 'sub', 32),
        ('xorl', 'xor', 32),
        ('orb', 'or', 8),
        ('orl', 'or', 32),
        ('movb', 'mov', 8),
        ('movl', 'mov', 32),
        ('testb', 'test', 8),
        ('testl', 'test', 32),
        ('cmpl_imm32', 'cmp', 32),
    ],
    RegMemInstruction: [
        ('addl', 'add', 32),
        ('andl', 'and', 32),
        ('cmpb', 'cmp', 8),
        ('cmpl', 'cmp', 32),
        ('lzcntl', 'lzcnt', 32),
        ('orl', 'or', 32),
        ('adcl', 'adc', 32),
        ('imull', 'imul', 32),
        ('popcntl', 'popcnt', 32),
        ('sbbl', 'sbb', 32),
        ('subl', 'sub', 32),
        ('tzcntl', 'tzcnt', 32),
        ('xorb', 'xor', 8),
        ('xorw', 'xor', 16),
        ('xorl', 'xor', 32),
        ('movb', 'mov', 8),
        ('movl', 'mov', 32),
        ('leal', 'lea', 32),
        ('xchgb', 'xchg', 8),
        ('xchgw', 'xchg', 16),
        ('xchgl', 'xchg', 32),
        ('testl', 'test', 32),
    ],
    RegImmInstruction: [
        ('addb', 'add', 8),
        ('addl', 'add', 32),
        ('andl', 'and', 32),
        ('adcl', 'adc', 32),
        ('cmpb', 'cmp', 8),
        ('cmpl', 'cmp', 32),
        ('rcll', 'rcl', 32),
        ('roll', 'rol', 32),
        ('rorl', 'ror', 32),
        ('sarl', 'sar', 32),
        ('sall', 'sal', 32),
        ('sbbl', 'sbb', 32),
        ('shll', 'shl', 32),
        ('shrl', 'shr', 32),
        ('subl', 'sub', 32),
        ('xorl', 'xor', 32),
        ('movl', 'mov', 32),
        ('testb', 'test', 8),
        ('testl', 'test', 32),
        ('subl_imm32', 'sub', 32),
    ],
    CondRegMemInstruction: [
        ('cmovl', 'cmov', 32, key) for key in cond_to_suffix.keys()
    ],
    CondRegInstruction: [
        ('set', 'set', 8, key) for key in cond_to_suffix.keys()
    ],
    RegInstruction: [
        ('divl', 'div', 32),
        ('idivl', 'idiv', 32),
        ('imull', 'imul', 32),
        ('mull', 'mul', 32),
        ('negl', 'neg', 32),
        ('notl', 'not', 32),
        ('roll', 'rol', 32),
        ('rorl', 'ror', 32),
        ('sarl', 'sar', 32),
        ('sall', 'sal', 32),
        ('shll', 'shl', 32),
        ('shrl', 'shr', 32),
        ('incrementl', 'inc', 32),
        ('decrementl', 'dec', 32),
    ],
    MemInstruction: [
        ('mull', 'mul', 32),
        ('negl', 'neg', 32),
        ('sarl', 'sar', 32),
        ('sall', 'sal', 32),
        ('shrl', 'shr', 32),
        ('incrementl', 'inc', 32),
        ('decrementl', 'dec', 32),
    ],
    RegMemImmInstruction: [
        ('imull', 'imul', 32),
    ],
    RegRegImmInstruction: [
        ('imull', 'imul', 32),
        ('shldl', 'shld', 32),
        ('shrdl', 'shrd', 32),
    ],
    MoveRegMemInstruction: [
        ('movzbl', 'movzx', 32, 8),
        ('movzwl', 'movzx', 32, 16),
        ('movsbl', 'movsx', 32, 8),
        ('movswl', 'movsx', 32, 16),
    ],
    MoveRegRegInstruction: [
        ('movzbl', 'movzx', 32, 8),
        ('movzwl', 'movzx', 32, 16),
        ('movsbl', 'movsx', 32, 8),
        ('movswl', 'movsx', 32, 16),
    ],
    CmpxchgInstruction: [
        ('cmpxchgb', 'cmpxchg', 8),
        ('cmpxchgw', 'cmpxchg', 16),
        ('cmpxchgl', 'cmpxchg', 32),
    ],
    # --- NDD instructions ---
    RegNddInstruction: [
        ('eidivl', 'idiv', 32, False),
        ('eidivl', 'idiv', 32, True),
        ('edivl', 'div', 32, False),
        ('edivl', 'div', 32, True),
        ('eimull', 'imul', 32, False),
        ('eimull', 'imul', 32, True),
        ('emull', 'mul', 32, False),
        ('emull', 'mul', 32, True),
    ],
    MemNddInstruction: [
        ('emull', 'mul', 32, False),
        ('emull', 'mul', 32, True),
    ],
    RegRegNddInstruction: [
        ('elzcntl', 'lzcnt', 32, False),
        ('elzcntl', 'lzcnt', 32, True),
        ('enegl', 'neg', 32, False),
        ('enegl', 'neg', 32, True),
        ('epopcntl', 'popcnt', 32, False),
        ('epopcntl', 'popcnt', 32, True),
        ('enotl', 'not', 32, None),
        ('eroll', 'rol', 32, False),
        ('eroll', 'rol', 32, True),
        ('erorl', 'ror', 32, False),
        ('erorl', 'ror', 32, True),
        ('esall', 'sal', 32, False),
        ('esall', 'sal', 32, True),
        ('esarl', 'sar', 32, False),
        ('esarl', 'sar', 32, True),
        ('edecl', 'dec', 32, False),
        ('edecl', 'dec', 32, True),
        ('eincl', 'inc', 32, False),
        ('eincl', 'inc', 32, True),
        ('eshll', 'shl', 32, False),
        ('eshll', 'shl', 32, True),
        ('eshrl', 'shr', 32, False),
        ('eshrl', 'shr', 32, True),
        ('etzcntl', 'tzcnt', 32, False),
        ('etzcntl', 'tzcnt', 32, True),
    ],
    RegMemNddInstruction: [
        ('elzcntl', 'lzcnt', 32, False),
        ('elzcntl', 'lzcnt', 32, True),
        ('enegl', 'neg', 32, False),
        ('enegl', 'neg', 32, True),
        ('epopcntl', 'popcnt', 32, False),
        ('epopcntl', 'popcnt', 32, True),
        ('esall', 'sal', 32, False),
        ('esall', 'sal', 32, True),
        ('esarl', 'sar', 32, False),
        ('esarl', 'sar', 32, True),
        ('edecl', 'dec', 32, False),
        ('edecl', 'dec', 32, True),
        ('eincl', 'inc', 32, False),
        ('eincl', 'inc', 32, True),
        ('eshrl', 'shr', 32, False),
        ('eshrl', 'shr', 32, True),
        ('etzcntl', 'tzcnt', 32, False),
        ('etzcntl', 'tzcnt', 32, True),
    ],
    RegMemImmNddInstruction: [
        ('eaddl', 'add', 32, False),
        ('eaddl', 'add', 32, True),
        ('eandl', 'and', 32, False),
        ('eandl', 'and', 32, True),
        ('eimull', 'imul', 32, False),
        ('eimull', 'imul', 32, True),
        ('eorl', 'or', 32, False),
        ('eorl', 'or', 32, True),
        ('eorb', 'or', 8, False),
        ('eorb', 'or', 8, True),
        ('esall', 'sal', 32, False),
        ('esall', 'sal', 32, True),
        ('esarl', 'sar', 32, False),
        ('esarl', 'sar', 32, True),
        ('eshrl', 'shr', 32, False),
        ('eshrl', 'shr', 32, True),
        ('esubl', 'sub', 32, False),
        ('esubl', 'sub', 32, True),
        ('exorl', 'xor', 32, False),
        ('exorl', 'xor', 32, True),
    ],
    RegMemRegNddInstruction: [
        ('eaddl', 'add', 32, False),
        ('eaddl', 'add', 32, True),
        ('eorl', 'or', 32, False),
        ('eorl', 'or', 32, True),
        ('eorb', 'or', 8, False),
        ('eorb', 'or', 8, True),
        ('esubl', 'sub', 32, False),
        ('esubl', 'sub', 32, True),
        ('exorl', 'xor', 32, False),
        ('exorl', 'xor', 32, True),
        ('exorb', 'xor', 8, False),
        ('exorb', 'xor', 8, True),
    ],
    RegRegImmNddInstruction: [
        ('eaddl', 'add', 32, False),
        ('eaddl', 'add', 32, True),
        ('eandl', 'and', 32, False),
        ('eandl', 'and', 32, True),
        ('eimull', 'imul', 32, False),
        ('eimull', 'imul', 32, True),
        ('eorl', 'or', 32, False),
        ('eorl', 'or', 32, True),
        ('ercll', 'rcl', 32, None),
        ('eroll', 'rol', 32, False),
        ('eroll', 'rol', 32, True),
        ('erorl', 'ror', 32, False),
        ('erorl', 'ror', 32, True),
        ('esall', 'sal', 32, False),
        ('esall', 'sal', 32, True),
        ('esarl', 'sar', 32, False),
        ('esarl', 'sar', 32, True),
        ('eshll', 'shl', 32, False),
        ('eshll', 'shl', 32, True),
        ('eshrl', 'shr', 32, False),
        ('eshrl', 'shr', 32, True),
        ('esubl', 'sub', 32, False),
        ('esubl', 'sub', 32, True),
        ('exorl', 'xor', 32, False),
        ('exorl', 'xor', 32, True),
        ('esubl_imm32', 'sub', 32, False),
        ('esubl_imm32', 'sub', 32, True),
    ],
    RegRegMemNddInstruction: [
        ('eaddl', 'add', 32, False),
        ('eaddl', 'add', 32, True),
        ('eandl', 'and', 32, False),
        ('eandl', 'and', 32, True),
        ('eimull', 'imul', 32, False),
        ('eimull', 'imul', 32, True),
        ('eorl', 'or', 32, False),
        ('eorl', 'or', 32, True),
        ('esubl', 'sub', 32, False),
        ('esubl', 'sub', 32, True),
        ('exorl', 'xor', 32, False),
        ('exorl', 'xor', 32, True),
        ('exorb', 'xor', 8, False),
        ('exorb', 'xor', 8, True),
        ('exorw', 'xor', 16, False),
        ('exorw', 'xor', 16, True),
    ],
    RegRegRegNddInstruction: [
        ('eaddl', 'add', 32, False),
        ('eaddl', 'add', 32, True),
        ('eandl', 'and', 32, False),
        ('eandl', 'and', 32, True),
        ('eimull', 'imul', 32, False),
        ('eimull', 'imul', 32, True),
        ('eorw', 'or', 16, False),
        ('eorw', 'or', 16, True),
        ('eorl', 'or', 32, False),
        ('eorl', 'or', 32, True),
        ('eshldl', 'shld', 32, False),
        ('eshldl', 'shld', 32, True),
        ('eshrdl', 'shrd', 32, False),
        ('eshrdl', 'shrd', 32, True),
        ('esubl', 'sub', 32, False),
        ('esubl', 'sub', 32, True),
        ('exorl', 'xor', 32, False),
        ('exorl', 'xor', 32, True),
    ],
    RegRegRegImmNddInstruction: [
        ('eshldl', 'shld', 32, False),
        ('eshldl', 'shld', 32, True),
        ('eshrdl', 'shrd', 32, False),
        ('eshrdl', 'shrd', 32, True),
    ],
    CondRegRegRegInstruction: [
        ('ecmovl', 'cmov', 32, key) for key in cond_to_suffix.keys()
    ],
    CondRegRegMemInstruction: [
        ('ecmovl', 'cmov', 32, key) for key in cond_to_suffix.keys()
    ],
}

instruction_set64 = {
    TwoRegInstruction: [
        ('adcq', 'adc', 64),
        ('cmpq', 'cmp', 64),
        ('imulq', 'imul', 64),
        ('popcntq', 'popcnt', 64),
        ('sbbq', 'sbb', 64),
        ('subq', 'sub', 64),
        ('tzcntq', 'tzcnt', 64),
        ('lzcntq', 'lzcnt', 64),
        ('addq', 'add', 64),
        ('andq', 'and', 64),
        ('orq', 'or', 64),
        ('xorq', 'xor', 64),
        ('movq', 'mov', 64),
        ('bsfq', 'bsf', 64),
        ('bsrq', 'bsr', 64),
        ('btq', 'bt', 64),
        ('xchgq', 'xchg', 64),
        ('testq', 'test', 64),
    ],
    MemRegInstruction: [
        ('addq', 'add', 64),
        ('andq', 'and', 64),
        ('cmpq', 'cmp', 64),
        ('orq', 'or', 64),
        ('xorq', 'xor', 64),
        ('subq', 'sub', 64),
        ('movq', 'mov', 64),
        ('xaddq', 'xadd', 64),
    ],
    MemImmInstruction: [
        ('andq', 'and', 64),
        ('addq', 'add', 64),
        ('cmpq', 'cmp', 64),
        ('sarq', 'sar', 64),
        ('salq', 'sal', 64),
        ('sbbq', 'sbb', 64),
        ('shrq', 'shr', 64),
        ('subq', 'sub', 64),
        ('xorq', 'xor', 64),
        ('orq', 'or', 64),
        ('movq', 'mov', 64),
        ('testq', 'test', 64),
    ],
    RegMemInstruction: [
        ('addq', 'add', 64),
        ('andq', 'and', 64),
        ('cmpq', 'cmp', 64),
        ('lzcntq', 'lzcnt', 64),
        ('orq', 'or', 64),
        ('adcq', 'adc', 64),
        ('imulq', 'imul', 64),
        ('popcntq', 'popcnt', 64),
        ('sbbq', 'sbb', 64),
        ('subq', 'sub', 64),
        ('tzcntq', 'tzcnt', 64),
        ('xorq', 'xor', 64),
        ('movq', 'mov', 64),
        ('leaq', 'lea', 64),
        ('cvttsd2siq', 'cvttsd2si', 64),
        ('xchgq', 'xchg', 64),
        ('testq', 'test', 64),
    ],
    RegImmInstruction: [
        ('addq', 'add', 64),
        ('andq', 'and', 64),
        ('adcq', 'adc', 64),
        ('cmpq', 'cmp', 64),
        ('rclq', 'rcl', 64),
        ('rcrq', 'rcr', 64),
        ('rolq', 'rol', 64),
        ('rorq', 'ror', 64),
        ('sarq', 'sar', 64),
        ('salq', 'sal', 64),
        ('sbbq', 'sbb', 64),
        ('shlq', 'shl', 64),
        ('shrq', 'shr', 64),
        ('subq', 'sub', 64),
        ('xorq', 'xor', 64),
        ('movq', 'mov', 64),
        ('mov64', 'mov', 64),
        ('btq', 'bt', 64),
        ('testq', 'test', 64),
        ('orq_imm32', 'or', 64),
        ('subq_imm32', 'sub', 64)
    ],
    CondRegMemInstruction: [
        ('cmovq', 'cmov', 64, key) for key in cond_to_suffix.keys()
    ],
    RegInstruction: [
        ('call', 'call', 64),
        ('divq', 'div', 64),
        ('idivq', 'idiv', 64),
        ('imulq', 'imul', 64),
        ('mulq', 'mul', 64),
        ('negq', 'neg', 64),
        ('notq', 'not', 64),
        ('rolq', 'rol', 64),
        ('rorq', 'ror', 64),
        ('sarq', 'sar', 64),
        ('salq', 'sal', 64),
        ('shlq', 'shl', 64),
        ('shrq', 'shr', 64),
        ('incrementq', 'inc', 64),
        ('decrementq', 'dec', 64),
        ('pushp', 'pushp', 64),
        ('popp', 'popp', 64)
    ],
    MemInstruction: [
        ('call', 'call', 64),
        ('mulq', 'mul', 64),
        ('negq', 'neg', 64),
        ('sarq', 'sar', 64),
        ('salq', 'sal', 64),
        ('shrq', 'shr', 64),
        ('incrementq', 'inc', 64),
        ('decrementq', 'dec', 64)
    ],
    RegMemImmInstruction: [
        ('imulq', 'imul', 64)
    ],
    RegRegImmInstruction: [
        ('imulq', 'imul', 64),
        ('shldq', 'shld', 64),
        ('shrdq', 'shrd', 64)
    ],
    Pop2Instruction: [
        ('pop2', 'pop2', 64),
        ('pop2p', 'pop2p', 64)
    ],
    Push2Instruction: [
        ('push2', 'push2', 64),
        ('push2p', 'push2p', 64)
    ],
    MoveRegMemInstruction: [
        ('movzbq', 'movzx', 64, 8),
        ('movzwq', 'movzx', 64, 16),
        ('movsbq', 'movsx', 64, 8),
        ('movswq', 'movsx', 64, 16),
    ],
    MoveRegRegInstruction: [
        ('movzbq', 'movzx', 64, 8),
        ('movzwq', 'movzx', 64, 16),
        ('movsbq', 'movsx', 64, 8),
        ('movswq', 'movsx', 64, 16),
    ],
    CmpxchgInstruction: [
        ('cmpxchgq', 'cmpxchg', 64),
    ],
    # --- NDD instructions ---
    RegNddInstruction: [
        ('eidivq', 'idiv', 64, False),
        ('eidivq', 'idiv', 64, True),
        ('edivq', 'div', 64, False),
        ('edivq', 'div', 64, True),
        ('eimulq', 'imul', 64, False),
        ('eimulq', 'imul', 64, True),
        ('emulq', 'mul', 64, False),
        ('emulq', 'mul', 64, True),
    ],
    MemNddInstruction: [
        ('emulq', 'mul', 64, False),
        ('emulq', 'mul', 64, True),
    ],
    RegRegNddInstruction: [
        ('eimulq', 'imul', 64, False),
        ('eimulq', 'imul', 64, True),
        ('elzcntq', 'lzcnt', 64, False),
        ('elzcntq', 'lzcnt', 64, True),
        ('enegq', 'neg', 64, False),
        ('enegq', 'neg', 64, True),
        ('enotq', 'not', 64, None),
        ('epopcntq', 'popcnt', 64, False),
        ('epopcntq', 'popcnt', 64, True),
        ('erolq', 'rol', 64, False),
        ('erolq', 'rol', 64, True),
        ('erorq', 'ror', 64, False),
        ('erorq', 'ror', 64, True),
        ('esalq', 'sal', 64, False),
        ('esalq', 'sal', 64, True),
        ('esarq', 'sar', 64, False),
        ('esarq', 'sar', 64, True),
        ('edecq', 'dec', 64, False),
        ('edecq', 'dec', 64, True),
        ('eincq', 'inc', 64, False),
        ('eincq', 'inc', 64, True),
        ('eshlq', 'shl', 64, False),
        ('eshlq', 'shl', 64, True),
        ('eshrq', 'shr', 64, False),
        ('eshrq', 'shr', 64, True),
        ('etzcntq', 'tzcnt', 64, False),
        ('etzcntq', 'tzcnt', 64, True),
    ],
    RegMemNddInstruction: [
        ('eimulq', 'imul', 64, False),
        ('eimulq', 'imul', 64, True),
        ('elzcntq', 'lzcnt', 64, False),
        ('elzcntq', 'lzcnt', 64, True),
        ('enegq', 'neg', 64, False),
        ('enegq', 'neg', 64, True),
        ('epopcntq', 'popcnt', 64, False),
        ('epopcntq', 'popcnt', 64, True),
        ('esalq', 'sal', 64, False),
        ('esalq', 'sal', 64, True),
        ('esarq', 'sar', 64, False),
        ('esarq', 'sar', 64, True),
        ('edecq', 'dec', 64, False),
        ('edecq', 'dec', 64, True),
        ('eincq', 'inc', 64, False),
        ('eincq', 'inc', 64, True),
        ('eshrq', 'shr', 64, False),
        ('eshrq', 'shr', 64, True),
        ('etzcntq', 'tzcnt', 64, False),
        ('etzcntq', 'tzcnt', 64, True),
    ],
    RegMemRegNddInstruction: [
        ('eaddq', 'add', 64, False),
        ('eaddq', 'add', 64, True),
        ('eandq', 'and', 64, False),
        ('eandq', 'and', 64, True),
        ('eorq', 'or', 64, False),
        ('eorq', 'or', 64, True),
        ('esubq', 'sub', 64, False),
        ('esubq', 'sub', 64, True),
        ('exorq', 'xor', 64, False),
        ('exorq', 'xor', 64, True),
    ],
    RegMemImmNddInstruction: [
        ('eaddq', 'add', 64, False),
        ('eaddq', 'add', 64, True),
        ('eandq', 'and', 64, False),
        ('eandq', 'and', 64, True),
        ('eimulq', 'imul', 64, False),
        ('eimulq', 'imul', 64, True),
        ('eorq', 'or', 64, False),
        ('eorq', 'or', 64, True),
        ('esalq', 'sal', 64, False),
        ('esalq', 'sal', 64, True),
        ('esarq', 'sar', 64, False),
        ('esarq', 'sar', 64, True),
        ('eshrq', 'shr', 64, False),
        ('eshrq', 'shr', 64, True),
        ('esubq', 'sub', 64, False),
        ('esubq', 'sub', 64, True),
        ('exorq', 'xor', 64, False),
        ('exorq', 'xor', 64, True),
    ],
    RegRegImmNddInstruction: [
        ('eaddq', 'add', 64, False),
        ('eaddq', 'add', 64, True),
        ('eandq', 'and', 64, False),
        ('eandq', 'and', 64, True),
        ('eimulq', 'imul', 64, False),
        ('eimulq', 'imul', 64, True),
        ('eorq', 'or', 64, False),
        ('eorq', 'or', 64, True),
        ('erclq', 'rcl', 64, None),
        ('erolq', 'rol', 64, False),
        ('erolq', 'rol', 64, True),
        ('erorq', 'ror', 64, False),
        ('erorq', 'ror', 64, True),
        ('esalq', 'sal', 64, False),
        ('esalq', 'sal', 64, True),
        ('esarq', 'sar', 64, False),
        ('esarq', 'sar', 64, True),
        ('eshlq', 'shl', 64, False),
        ('eshlq', 'shl', 64, True),
        ('eshrq', 'shr', 64, False),
        ('eshrq', 'shr', 64, True),
        ('esubq', 'sub', 64, False),
        ('esubq', 'sub', 64, True),
        ('exorq', 'xor', 64, False),
        ('exorq', 'xor', 64, True),
        ('eorq_imm32', 'or', 64, False),
        ('eorq_imm32', 'or', 64, False),
        ('esubq_imm32', 'sub', 64, False),
        ('esubq_imm32', 'sub', 64, True),
    ],
    RegRegMemNddInstruction: [
        ('eaddq', 'add', 64, False),
        ('eaddq', 'add', 64, True),
        ('eandq', 'and', 64, False),
        ('eandq', 'and', 64, True),
        ('eorq', 'or', 64, False),
        ('eorq', 'or', 64, True),
        ('eimulq', 'imul', 64, False),
        ('eimulq', 'imul', 64, True),
        ('esubq', 'sub', 64, False),
        ('esubq', 'sub', 64, True),
        ('exorq', 'xor', 64, False),
        ('exorq', 'xor', 64, True),
    ],
    RegRegRegNddInstruction: [
        ('eaddq', 'add', 64, False),
        ('eaddq', 'add', 64, True),
        ('eadcxq', 'adcx', 64, None),
        ('eadoxq', 'adox', 64, None),
        ('eandq', 'and', 64, False),
        ('eandq', 'and', 64, True),
        ('eimulq', 'imul', 64, False),
        ('eimulq', 'imul', 64, True),
        ('eorq', 'or', 64, False),
        ('eorq', 'or', 64, True),
        ('esubq', 'sub', 64, False),
        ('esubq', 'sub', 64, True),
        ('exorq', 'xor', 64, False),
        ('exorq', 'xor', 64, True),
    ],
    RegRegRegImmNddInstruction: [
        ('eshldq', 'shld', 64, False),
        ('eshldq', 'shld', 64, True),
        ('eshrdq', 'shrd', 64, False),
        ('eshrdq', 'shrd', 64, True),
    ],
    CondRegRegRegInstruction: [
        ('ecmovq', 'cmov', 64, key) for key in cond_to_suffix.keys()
    ],
    CondRegRegMemInstruction: [
        ('ecmovq', 'cmov', 64, key) for key in cond_to_suffix.keys()
    ],
}

if __name__ == "__main__":
    if platform.system() != "Linux":
        print("This script only works on Linux")
        exit(1)

    full_set = '--full' in sys.argv

    ifdef_flags = []
    insns_strs = []

    print("// BEGIN  Generated code -- do not edit")
    print("// Generated by x86-asmtest.py")

    outfile = open("x86ops.s", "w")
    outfile.write(".intel_syntax noprefix\n")

    for RegOp, ops in instruction_set.items():
        generate(RegOp, ops, True, full_set)

    if lp64_flag:
        lp64_flag = False
        print("#endif // _LP64")

    print("#ifdef _LP64")
    for RegOp, ops in instruction_set64.items():
        generate(RegOp, ops, False, full_set)
    print("#endif // _LP64")

    outfile.close()

    subprocess.check_call([X86_AS, "x86ops.s", "-o", "x86ops.o",])
    subprocess.check_call([X86_OBJCOPY, "-O", "binary", "-j", ".text", "x86ops.o", "x86ops.bin"])

    infile = open("x86ops.bin", "rb")
    bytes = bytearray(infile.read())
    infile.close()

    disassembly_text = subprocess.check_output([OBJDUMP, "-M", "intel", "-d", "x86ops.o", "--insn-width=16"], text=True)
    lines = disassembly_text.split("\n")
    instruction_regex = re.compile(r'^\s*([0-9a-f]+):\s*([0-9a-f\s]+?)(?:\s{2,})')
    instructions = []

    for i, line in enumerate(lines):
        match = instruction_regex.match(line)
        if match:
            offset = int(match.group(1), 16)
            insns = match.group(2).split()
            binary_code = ", ".join([f"0x{insn}" for insn in insns])
            length = len(insns)
            instructions.append((length, binary_code))

    print()
    print("  static const uint8_t insns[] =")
    print("  {")
    print_with_ifdef(ifdef_flags, instructions, lambda x: f"{x[1]}", 80)
    print("  };")
    print()
    print("  static const unsigned int insns_lens[] =")
    print("  {")
    print_with_ifdef(ifdef_flags, instructions, lambda x: f"{x[0]}", 5)
    print("  };")
    print()
    print("  static const char* insns_strs[] =")
    print("  {")
    print_with_ifdef(ifdef_flags, insns_strs, lambda x: f"\"{x}\"", 85)
    print("  };")

    print("// END  Generated code -- do not edit")

    for f in ["x86ops.s", "x86ops.o", "x86ops.bin"]:
        os.remove(f)
