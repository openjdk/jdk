import os
import re
import subprocess

OBJDUMP = "objdump"
X86_AS = "as"
X86_OBJCOPY = "objcopy"

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

registers_mapping = {
    # skip rax, rsi, rdi, rsp, rbp as they have special encodings
    # 'rax': {64: 'rax', 32: 'eax', 16: 'ax', 8: 'al'},
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
        return self

    def cstr(self):
        return f"Address({self.base.cstr()}, {self.index.cstr()})"

    def astr(self):
        ptr_str = self.width_to_ptr.get(self._width, "qword ptr")
        return f"{ptr_str} [{self.base.cstr()} + {self.index.cstr()}]"

class Instruction(object):
    def __init__(self, name, aname):
        self._name = name
        self._aname = aname

    def generate_operands(self, *operands):
        self.operands = [operand for operand in operands]

    def cstr(self):
        return f'__ {self._name}(' + ', '.join([op.cstr() for op in self.operands]) + ');'

    def astr(self):
        return f'{self._aname} ' + ', '.join([op.astr() for op in self.operands])

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
        return f'__ {self._name} (' + ', '.join([reg.cstr() for reg in reversed(self.operands)]) + ');'

class Push2Instruction(TwoRegInstruction):
    def __init__(self, name, aname, width, reg1, reg2):
        super().__init__(name, aname, width, reg1, reg2)

    def cstr(self):
        # reverse to match the order in OpenJDK
        return f'__ {self._name} (' + ', '.join([reg.cstr() for reg in reversed(self.operands)]) + ');'

class CondRegMemInstruction(RegMemInstruction):
    def __init__(self, name, aname, width, cond, reg, mem_base, mem_idx):
        super().__init__(name, aname, width, reg, mem_base, mem_idx)
        self.cond = cond

    def cstr(self):
        return f'__ {self._name} (' + 'Assembler::Condition::' + self.cond + ', ' + ', '.join([self.reg.cstr(), self.mem.cstr()]) + ');'

    def astr(self):
        return f'{self._aname}' + cond_to_suffix[self.cond] + ' ' + ', '.join([self.reg.astr(), self.mem.astr()])

class CondRegInstruction(RegInstruction):
    def __init__(self, name, aname, width, cond, reg):
        super().__init__(name, aname, width, reg)
        self.cond = cond

    def cstr(self):
        return f'__ {self._name}b (' + 'Assembler::Condition::' + self.cond + ', ' + self.reg.cstr() + ');'

    def astr(self):
        return f'{self._aname}' + cond_to_suffix[self.cond] + ' ' + self.reg.astr()

class RegImm32Instruction(RegImmInstruction):
    def __init__(self, name, aname, width, reg, imm):
        super().__init__(name, aname, width, reg, imm)

    def cstr(self):
        return f'__ {self._name} (' + ', '.join([self.reg.cstr(), self.imm.cstr()]) + ');'

instrs = []
test_regs = list(registers_mapping.keys())

immediates32 = [2 ** i for i in range(0, 32, 4)]
immediates16 = [2 ** i for i in range(0, 16, 2)]
immediates8 = [2 ** i for i in range(0, 8, 2)]
immediates5 = [2 ** i for i in range(0, 5, 1)]
immediate_values_8_to_16_bit = [2 ** i for i in range(8, 16, 2)]
immediate_values_16_to_32_bit = [2 ** i for i in range(16, 32, 2)]

immediate_map = {
    8: immediates8,
    16: immediates16,
    32: immediates32,
    64: immediates32
}

def generate(RegOp, ops):
    # special cases
    shift_ops = {'sarl', 'sarq', 'shll', 'shlq', 'shrl', 'shrq', 'shrdl', 'shrdq', 'shldl', 'shldq', 'rcrq', 'rorl', 'rorq', 'roll', 'rolq', 'rcll', 'rclq'}
    addw_ops = {'addw'}
    for op in ops:
        op_name = op[0]
        width = op[2]
        if RegOp == RegInstruction or RegOp == CondRegInstruction:
            for reg in test_regs:
                instr = RegOp(*op, reg=reg)
                cstr = instr.cstr()
                astr = instr.astr()
                print("    %-50s //\t%s" % (cstr, astr))
                instrs.append(cstr)
                outfile.write(f"\t{astr}\n")
        elif RegOp == TwoRegInstruction:
            for i in range(len(test_regs)):
                instr = RegOp(*op, reg1=test_regs[i], reg2=test_regs[(i+1)%len(test_regs)])
                cstr = instr.cstr()
                astr = instr.astr()
                print("    %-50s //\t%s" % (cstr, astr))
                instrs.append(cstr)
                outfile.write(f"\t{astr}\n")
        elif RegOp == MemRegInstruction or RegOp == RegMemInstruction or RegOp == CondRegMemInstruction:
            for i in range(len(test_regs)):
                if test_regs[(i+2)%len(test_regs)] == 'rsp':
                    continue
                instr = RegOp(*op, reg=test_regs[i], mem_base=test_regs[(i+1)%len(test_regs)], mem_idx=test_regs[(i+2)%len(test_regs)])
                cstr = instr.cstr()
                astr = instr.astr()
                print("    %-50s //\t%s" % (cstr, astr))
                instrs.append(cstr)
                outfile.write(f"\t{astr}\n")
        elif RegOp == RegImmInstruction:
            for reg in test_regs:
                if op_name in shift_ops:
                    imm_list = immediates5
                else:
                    imm_list = immediate_map[width]
                for imm in imm_list:
                    instr = RegOp(*op, reg=reg, imm=imm)
                    cstr = instr.cstr()
                    astr = instr.astr()
                    print("    %-50s //\t%s" % (cstr, astr))
                    instrs.append(cstr)
                    outfile.write(f"\t{astr}\n")
        elif RegOp == MemImmInstruction:
            if op_name in addw_ops:
                imm_list = immediate_values_8_to_16_bit
            elif op_name in shift_ops:
                imm_list = immediates5
            else:
                imm_list = immediate_map[width]
            for imm in imm_list:
                for i in range(len(test_regs)):
                    instr = RegOp(*op, imm=imm, mem_base=test_regs[i], mem_idx=test_regs[(i+1)%len(test_regs)])
                    if test_regs[(i+1)%len(test_regs)] == 'rsp':
                        continue
                    cstr = instr.cstr()
                    astr = instr.astr()
                    print("    %-50s //\t%s" % (cstr, astr))
                    instrs.append(cstr)
                    outfile.write(f"\t{astr}\n")
        elif RegOp == MemInstruction:
            for i in range(len(test_regs)):
                instr = RegOp(*op, mem_base=test_regs[i], mem_idx=test_regs[(i+1)%len(test_regs)])
                if test_regs[(i+1)%len(test_regs)] == 'rsp':
                    continue
                cstr = instr.cstr()
                astr = instr.astr()
                print("    %-50s //\t%s" % (cstr, astr))
                instrs.append(cstr)
                outfile.write(f"\t{astr}\n")
        elif RegOp == RegRegImmInstruction:
            for i in range(len(test_regs)):
                if op_name in shift_ops:
                    imm_list = immediates5
                else:
                    imm_list = immediate_map[width]
                for imm in imm_list:
                    instr = RegOp(*op, reg1=test_regs[i], reg2=test_regs[(i+1)%len(test_regs)], imm=imm)
                    cstr = instr.cstr()
                    astr = instr.astr()
                    print("    %-50s //\t%s" % (cstr, astr))
                    instrs.append(cstr)
                    outfile.write(f"\t{astr}\n")
        elif RegOp == RegMemImmInstruction:
            for i in range(len(test_regs)):
                imm_list = immediate_map[width]
                for imm in imm_list:
                    instr = RegOp(*op, reg=test_regs[i], mem_base=test_regs[(i+1)%len(test_regs)], mem_idx=test_regs[(i+2)%len(test_regs)], imm=imm)
                    if test_regs[(i+2)%len(test_regs)] == 'rsp':
                        continue
                    cstr = instr.cstr()
                    astr = instr.astr()
                    print("    %-50s //\t%s" % (cstr, astr))
                    instrs.append(cstr)
                    outfile.write(f"\t{astr}\n")
        elif RegOp == Push2Instruction or RegOp == Pop2Instruction:
            for i in range(len(test_regs)):
                instr = RegOp(*op, reg1=test_regs[i], reg2=test_regs[(i+1)%len(test_regs)])
                # rsp can't be used in push2 and pop2
                if test_regs[(i+1)%len(test_regs)] == 'rsp' or test_regs[i] == 'rsp':
                    continue
                cstr = instr.cstr()
                astr = instr.astr()
                print("    %-50s //\t%s" % (cstr, astr))
                instrs.append(cstr)
                outfile.write(f"\t{astr}\n")
        elif RegOp ==  RegImm32Instruction:
            for i in range(len(test_regs)):
                for imm in immediate_values_16_to_32_bit:
                    instr = RegOp(*op, reg=test_regs[i], imm=imm)
                    cstr = instr.cstr()
                    astr = instr.astr()
                    print("    %-50s //\t%s" % (cstr, astr))
                    instrs.append(cstr)
                    outfile.write(f"\t{astr}\n")

print("// BEGIN  Generated code -- do not edit")
print("// Generated by x86-asmtest.py")

outfile = open("x86ops.s", "w")
outfile.write(".intel_syntax noprefix\n")

instruction_set = {
    TwoRegInstruction: [
        ('shldl', 'shld', 32),
        ('shrdl', 'shrd', 32),
        ('adcl', 'adc', 32),
        ('adcq', 'adc', 64),
        ('imull', 'imul', 32),
        ('imulq', 'imul', 64),
        ('popcntl', 'popcnt', 32),
        ('popcntq', 'popcnt', 64),
        ('sbbl', 'sbb', 32),
        ('sbbq', 'sbb', 64),
        ('subl', 'sub', 32),
        ('subq', 'sub', 64),
        ('tzcntl', 'tzcnt', 32),
        ('tzcntq', 'tzcnt', 64),
        ('lzcntl', 'lzcnt', 32),
        ('lzcntq', 'lzcnt', 64),
        ('addl', 'add', 32),
        ('addq', 'add', 64),
        ('andl', 'and', 32),
        ('andq', 'and', 64),
        ('orl', 'or', 32),
        ('orq', 'or', 64),
        ('xorl', 'xor', 32),
        ('xorq', 'xor', 64)
    ],
    MemRegInstruction: [
        ('addb', 'add', 8),
        ('addw', 'add', 16),
        ('addl', 'add', 32),
        ('addq', 'add', 64),
        ('adcl', 'adc', 32),
        ('andb', 'and', 8),
        ('andl', 'and', 32),
        ('andq', 'and', 64),
        ('orb', 'or', 8),
        ('orl', 'or', 32),
        ('orq', 'or', 64),
        ('xorb', 'xor', 8),
        ('xorl', 'xor', 32),
        ('xorq', 'xor', 64),
        ('subl', 'sub', 32),
        ('subq', 'sub', 64)
    ],
    MemImmInstruction: [
        ('adcl', 'adc', 32),
        ('andl', 'and', 32),
        ('andq', 'and', 64),
        ('addb', 'add', 8),
        ('addw', 'add', 16),
        ('addl', 'add', 32),
        ('addq', 'add', 64),
        ('sarl', 'sar', 32),
        ('sarq', 'sar', 64),
        ('sbbl', 'sbb', 32),
        ('sbbq', 'sbb', 64),
        ('shrl', 'shr', 32),
        ('shrq', 'shr', 64),
        ('subl', 'sub', 32),
        ('subq', 'sub', 64),
        ('xorl', 'xor', 32),
        ('xorq', 'xor', 64),
        ('orb', 'or', 8),
        ('orl', 'or', 32),
        ('orq', 'or', 64)
    ],
    RegMemInstruction: [
        ('addl', 'add', 32),
        ('addq', 'add', 64),
        ('andl', 'and', 32),
        ('andq', 'and', 64),
        ('lzcntl', 'lzcnt', 32),
        ('lzcntq', 'lzcnt', 64),
        ('orl', 'or', 32),
        ('orq', 'or', 64),
        ('adcl', 'adc', 32),
        ('adcq', 'adc', 64),
        ('imull', 'imul', 32),
        ('imulq', 'imul', 64),
        ('popcntl', 'popcnt', 32),
        ('popcntq', 'popcnt', 64),
        ('sbbl', 'sbb', 32),
        ('sbbq', 'sbb', 64),
        ('subl', 'sub', 32),
        ('subq', 'sub', 64),
        ('tzcntl', 'tzcnt', 32),
        ('tzcntq', 'tzcnt', 64),
        ('xorb', 'xor', 8),
        ('xorw', 'xor', 16),
        ('xorl', 'xor', 32),
        ('xorq', 'xor', 64)
    ],
    RegImmInstruction: [
        ('addb', 'add', 8),
        ('addl', 'add', 32),
        ('addq', 'add', 64),
        ('andl', 'and', 32),
        ('andq', 'and', 64),
        ('adcl', 'adc', 32),
        ('adcq', 'adc', 64),
        ('rcll', 'rcl', 32),
        ('rclq', 'rcl', 64),
        ('rcrq', 'rcr', 64),
        ('roll', 'rol', 32),
        ('rolq', 'rol', 64),
        ('rorl', 'ror', 32),
        ('rorq', 'ror', 64),
        ('sarl', 'sar', 32),
        ('sarq', 'sar', 64),
        ('sbbl', 'sbb', 32),
        ('sbbq', 'sbb', 64),
        ('shll', 'shl', 32),
        ('shlq', 'shl', 64),
        ('shrl', 'shr', 32),
        ('shrq', 'shr', 64),
        ('subl', 'sub', 32),
        ('subq', 'sub', 64),
        ('xorl', 'xor', 32),
        ('xorq', 'xor', 64),
    ],
    CondRegMemInstruction: [
        ('cmovl', 'cmov', 32, key) for key in cond_to_suffix.keys()
    ] + 
    [
        ('cmovq', 'cmov', 64, key) for key in cond_to_suffix.keys()
    ],
    CondRegInstruction: [
        ('set', 'set', 8, key) for key in cond_to_suffix.keys()
    ],
    RegInstruction: [
        ('divl', 'div', 32),
        ('divq', 'div', 64),
        ('idivl', 'idiv', 32),
        ('idivq', 'idiv', 64),
        ('imull', 'imul', 32),
        ('imulq', 'imul', 64),
        ('mull', 'mul', 32),
        ('mulq', 'mul', 64),
        ('negl', 'neg', 32),
        ('negq', 'neg', 64),
        ('notl', 'not', 32),
        ('notq', 'not', 64),
        ('roll', 'rol', 32),
        ('rolq', 'rol', 64),
        ('rorl', 'ror', 32),
        ('rorq', 'ror', 64),
        ('sarl', 'sar', 32),
        ('sarq', 'sar', 64),
        ('shll', 'shl', 32),
        ('shlq', 'shl', 64),
        ('shrl', 'shr', 32),
        ('shrq', 'shr', 64),
        ('incrementl', 'inc', 32),
        ('incrementq', 'inc', 64),
        ('decrementl', 'dec', 32),
        ('decrementq', 'dec', 64)
    ],
    MemInstruction: [
        ('mull', 'mul', 32),
        ('mulq', 'mul', 64),
        ('negl', 'neg', 32),
        ('negq', 'neg', 64),
        ('sarl', 'sar', 32),
        ('sarq', 'sar', 64),
        ('shrl', 'shr', 32),
        ('shrq', 'shr', 64),
        ('incrementl', 'inc', 32),
        ('incrementq', 'inc', 64),
        ('decrementl', 'dec', 32),
        ('decrementq', 'dec', 64)
    ],
    RegMemImmInstruction: [
        ('imull', 'imul', 32),
        ('imulq', 'imul', 64)
    ],
    RegRegImmInstruction: [
        ('imull', 'imul', 32),
        ('imulq', 'imul', 64),
        ('shldl', 'shld', 32),
        ('shldq', 'shld', 64),
        ('shrdl', 'shrd', 32),
        ('shrdq', 'shrd', 64)
    ],
    RegImm32Instruction: [
        ('orq_imm32', 'or', 64),
        ('subl_imm32', 'sub', 32),
        ('subq_imm32', 'sub', 64)
    ],
    Pop2Instruction: [
        ('pop2', 'pop2', 64),
        ('pop2p', 'pop2p', 64)
    ],
    Push2Instruction: [
        ('push2', 'push2', 64),
        ('push2p', 'push2p', 64)
    ],
}

for RegOp, ops in instruction_set.items():
    generate(RegOp, ops)

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

for line in lines:
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

for _, binary_code in instructions:
    print(f"    {binary_code}", end=",\n")

print("  };")
print("  static const unsigned int insns_lens[] =")
print("  {")
for i, (offset, _) in enumerate(instructions):
    if i % 8 == 0:
        print("    ", end="")
    print(f"{offset}", end=", ")
    if i % 8 == 7:
        print()
print()
print("  };")
print()
print("  static const char* insns_strs[] =")
print("  {")

for instr in instrs:
    print(f"    \"{instr}\",")
print("  };")

print("// END  Generated code -- do not edit")

for f in ["x86ops.s", "x86ops.o", "x86ops.bin"]:
    os.remove(f)