import os
import random
import subprocess
import sys

AARCH64_AS = "as"
AARCH64_OBJDUMP = "objdump"
AARCH64_OBJCOPY = "objcopy"

# These tables are legal immediate logical operands
immediates8 \
     = [0x1, 0x0c, 0x3e, 0x60, 0x7c, 0x80, 0x83,
        0xe1, 0xbf, 0xef, 0xf3, 0xfe]

immediates16 \
     = [0x1, 0x38, 0x7e, 0xff, 0x1fc, 0x1ff, 0x3f0,
        0x7e0, 0xfc0, 0x1f80, 0x3ff0, 0x7e00, 0x7e00,
        0x8000, 0x81ff, 0xc1ff, 0xc003, 0xc7ff, 0xdfff,
        0xe03f, 0xe10f, 0xe1ff, 0xf801, 0xfc00, 0xfc07,
        0xff03, 0xfffe]

immediates32 \
     = [0x1, 0x3f, 0x1f0, 0x7e0,
        0x1c00, 0x3ff0, 0x8000, 0x1e000,
        0x3e000, 0x78000, 0xe0000, 0x100000,
        0x1fffe0, 0x3fe000, 0x780000, 0x7ffff8,
        0xff8000, 0x1800180, 0x1fffc00, 0x3c003c0,
        0x3ffff00, 0x7c00000, 0x7fffe00, 0xf000f00,
        0xfffe000, 0x18181818, 0x1ffc0000, 0x1ffffffe,
        0x3f003f00, 0x3fffe000, 0x60006000, 0x7f807f80,
        0x7ffffc00, 0x800001ff, 0x803fffff, 0x9f9f9f9f,
        0xc0000fff, 0xc0c0c0c0, 0xe0000000, 0xe003e003,
        0xe3ffffff, 0xf0000fff, 0xf0f0f0f0, 0xf80000ff,
        0xf83ff83f, 0xfc00007f, 0xfc1fffff, 0xfe0001ff,
        0xfe3fffff, 0xff003fff, 0xff800003, 0xff87ff87,
        0xffc00fff, 0xffe0000f, 0xffefffef, 0xfff1fff1,
        0xfff83fff, 0xfffc0fff, 0xfffe0fff, 0xffff3fff,
        0xffffc007, 0xffffe1ff, 0xfffff80f, 0xfffffe07,
        0xffffffbf, 0xfffffffd]

immediates64 \
     = [0x1, 0x1f80, 0x3fff0, 0x3ffffc,
        0x3fe0000, 0x1ffc0000, 0xf8000000, 0x3ffffc000,
        0xffffffe00, 0x3ffffff800, 0xffffc00000, 0x3f000000000,
        0x7fffffff800, 0x1fe000001fe0, 0x3ffffff80000, 0xc00000000000,
        0x1ffc000000000, 0x3ffff0003ffff, 0x7ffffffe00000, 0xfffffffffc000,
        0x1ffffffffffc00, 0x3fffffffffff00, 0x7ffffffffffc00, 0xffffffffff8000,
        0x1ffffffff800000, 0x3fffffc03fffffc, 0x7fffc0000000000, 0xff80ff80ff80ff8,
        0x1c00000000000000, 0x1fffffffffff0000, 0x3fffff803fffff80, 0x7fc000007fc00000,
        0x8000000000000000, 0x803fffff803fffff, 0xc000007fc000007f, 0xe00000000000ffff,
        0xe3ffffffffffffff, 0xf007f007f007f007, 0xf80003ffffffffff, 0xfc000003fc000003,
        0xfe000000007fffff, 0xff00000000007fff, 0xff800000000003ff, 0xffc00000000000ff,
        0xffe00000000003ff, 0xfff0000000003fff, 0xfff80000001fffff, 0xfffc0000fffc0000,
        0xfffe003fffffffff, 0xffff3fffffffffff, 0xffffc0000007ffff, 0xffffe01fffffe01f,
        0xfffff800000007ff, 0xfffffc0fffffffff, 0xffffff00003fffff, 0xffffffc0000007ff,
        0xfffffff0000001ff, 0xfffffffc00003fff, 0xffffffff07ffffff, 0xffffffffe003ffff,
        0xfffffffffc01ffff, 0xffffffffffc00003, 0xfffffffffffc000f, 0xffffffffffffe07f]

class Operand(object):

     def generate(self):
        return self

class Register(Operand):

    def generate(self):
        self.number = random.randint(0, 30)
        if self.number == 18:
            self.number = 17
        return self

    def astr(self, prefix):
        return prefix + str(self.number)

class FloatRegister(Register):

    def __str__(self):
        return self.astr("v")

    def nextReg(self):
        next = FloatRegister()
        next.number = (self.number + 1) % 32
        return next

class GeneralRegister(Register):

    def __str__(self):
        return self.astr("r")

class GeneralRegisterOrZr(Register):

    def generate(self):
        self.number = random.randint(0, 31)
        if self.number == 18:
            self.number = 16
        return self

    def astr(self, prefix = ""):
        if (self.number == 31):
            return prefix + "zr"
        else:
            return prefix + str(self.number)

    def __str__(self):
        if (self.number == 31):
            return self.astr()
        else:
            return self.astr("r")

class GeneralRegisterOrSp(Register):
    def generate(self):
        self.number = random.randint(0, 31)
        if self.number == 18:
            self.number = 15
        return self

    def astr(self, prefix = ""):
        if (self.number == 31):
            return "sp"
        else:
            return prefix + str(self.number)

    def __str__(self):
        if (self.number == 31):
            return self.astr()
        else:
            return self.astr("r")

class SVEVectorRegister(FloatRegister):
    def __str__(self):
        return self.astr("z")

class SVEPRegister(Register):
    def __str__(self):
        return self.astr("p")

    def generate(self):
        self.number = random.randint(0, 15)
        return self

class SVEGoverningPRegister(Register):
    def __str__(self):
        return self.astr("p")
    def generate(self):
        self.number = random.randint(0, 7)
        return self

class RegVariant(object):
    def __init__(self, low, high):
        self.number = random.randint(low, high)

    def astr(self):
        nameMap = {
             0: ".b",
             1: ".h",
             2: ".s",
             3: ".d",
             4: ".q"
        }
        return nameMap.get(self.number)

    def cstr(self):
        nameMap = {
             0: "__ B",
             1: "__ H",
             2: "__ S",
             3: "__ D",
             4: "__ Q"
        }
        return nameMap.get(self.number)

class FloatZero(Operand):

    def __str__(self):
        return "0.0"

    def astr(self, ignored):
        return "#0.0"

class OperandFactory:

    _modes = {'x' : GeneralRegister,
              'w' : GeneralRegister,
              'b' : FloatRegister,
              'h' : FloatRegister,
              's' : FloatRegister,
              'd' : FloatRegister,
              'z' : FloatZero,
              'p' : SVEPRegister,
              'P' : SVEGoverningPRegister,
              'Z' : SVEVectorRegister}

    @classmethod
    def create(cls, mode):
        return OperandFactory._modes[mode]()

class ShiftKind:

    def generate(self):
        self.kind = ["LSL", "LSR", "ASR"][random.randint(0,2)]
        return self

    def cstr(self):
        return self.kind

class Instruction(object):

    def __init__(self, name):
        self._name = name
        self.isWord = name.endswith("w") | name.endswith("wi")
        self.asmRegPrefix = ["x", "w"][self.isWord]

    def aname(self):
        if (self._name.endswith("wi")):
            return self._name[:len(self._name)-2]
        else:
            if (self._name.endswith("i") | self._name.endswith("w")):
                return self._name[:len(self._name)-1]
            else:
                return self._name

    def emit(self) :
        pass

    def compare(self) :
        pass

    def generate(self) :
        return self

    def cstr(self):
        return '__ %s(' % self.name()

    def astr(self):
        return '%s\t' % self.aname()

    def name(self):
        name = self._name
        if name == "and":
            name = "andr" # Special case: the name "and" can't be used
                          # in HotSpot, even for a member.
        return name

    def multipleForms(self):
         return 0

class InstructionWithModes(Instruction):

    def __init__(self, name, mode):
        Instruction.__init__(self, name)
        self.mode = mode
        self.isFloat = (mode == 'd') | (mode == 's')
        if self.isFloat:
            self.isWord = mode != 'd'
            self.asmRegPrefix = ["d", "s"][self.isWord]
        else:
            self.isWord = mode != 'x'
            self.asmRegPrefix = ["x", "w"][self.isWord]

    def name(self):
        return self._name + (self.mode if self.mode != 'x' else '')

    def aname(self):
        return (self._name+mode if (mode == 'b' or mode == 'h')
            else self._name)

class ThreeRegInstruction(Instruction):

    def generate(self):
        self.reg = [GeneralRegister().generate(), GeneralRegister().generate(),
                    GeneralRegister().generate()]
        return self


    def cstr(self):
        return (super(ThreeRegInstruction, self).cstr()
                + ('%s, %s, %s'
                   % (self.reg[0],
                      self.reg[1], self.reg[2])))

    def astr(self):
        prefix = self.asmRegPrefix
        return (super(ThreeRegInstruction, self).astr()
                + ('%s, %s, %s'
                   % (self.reg[0].astr(prefix),
                      self.reg[1].astr(prefix), self.reg[2].astr(prefix))))

class FourRegInstruction(ThreeRegInstruction):

    def generate(self):
        self.reg = ThreeRegInstruction.generate(self).reg + [GeneralRegister().generate()]
        return self


    def cstr(self):
        return (super(FourRegInstruction, self).cstr()
                + (', %s' % self.reg[3]))

    def astr(self):
        prefix = self.asmRegPrefix
        return (super(FourRegInstruction, self).astr()
                + (', %s' % self.reg[3].astr(prefix)))

class TwoRegInstruction(Instruction):

    def generate(self):
        self.reg = [GeneralRegister().generate(), GeneralRegister().generate()]
        return self

    def cstr(self):
        return (super(TwoRegInstruction, self).cstr()
                + '%s, %s' % (self.reg[0],
                              self.reg[1]))

    def astr(self):
        prefix = self.asmRegPrefix
        return (super(TwoRegInstruction, self).astr()
                + ('%s, %s'
                   % (self.reg[0].astr(prefix),
                      self.reg[1].astr(prefix))))

class TwoRegImmedInstruction(TwoRegInstruction):

    def generate(self):
        super(TwoRegImmedInstruction, self).generate()
        self.immed = random.randint(0, 1<<11 -1)
        return self

    def cstr(self):
        return (super(TwoRegImmedInstruction, self).cstr()
                + ', %su' % self.immed)

    def astr(self):
        return (super(TwoRegImmedInstruction, self).astr()
                + ', #%s' % self.immed)

class OneRegOp(Instruction):

    def generate(self):
        self.reg = GeneralRegister().generate()
        return self

    def cstr(self):
        return (super(OneRegOp, self).cstr()
                + '%s);' % self.reg)

    def astr(self):
        return (super(OneRegOp, self).astr()
                + '%s' % self.reg.astr(self.asmRegPrefix))

class ArithOp(ThreeRegInstruction):

    def generate(self):
        super(ArithOp, self).generate()
        self.kind = ShiftKind().generate()
        self.distance = random.randint(0, (1<<5)-1 if self.isWord else (1<<6)-1)
        return self

    def cstr(self):
        return ('%s, Assembler::%s, %s);'
                % (ThreeRegInstruction.cstr(self),
                   self.kind.cstr(), self.distance))

    def astr(self):
        return ('%s, %s #%s'
                % (ThreeRegInstruction.astr(self),
                   self.kind.cstr(),
                   self.distance))

class AddSubCarryOp(ThreeRegInstruction):

    def cstr(self):
        return ('%s);'
                % (ThreeRegInstruction.cstr(self)))

class AddSubExtendedOp(ThreeRegInstruction):

    uxtb, uxth, uxtw, uxtx, sxtb, sxth, sxtw, sxtx = range(8)
    optNames = ["uxtb", "uxth", "uxtw", "uxtx", "sxtb", "sxth", "sxtw", "sxtx"]

    def generate(self):
        super(AddSubExtendedOp, self).generate()
        self.amount = random.randint(1, 4)
        self.option = random.randint(0, 7)
        return self

    def cstr(self):
        return (super(AddSubExtendedOp, self).cstr()
                + (", ext::" + AddSubExtendedOp.optNames[self.option]
                   + ", " + str(self.amount) + ");"))

    def astr(self):
        return (super(AddSubExtendedOp, self).astr()
                + (", " + AddSubExtendedOp.optNames[self.option]
                   + " #" + str(self.amount)))

class AddSubImmOp(TwoRegImmedInstruction):

    def cstr(self):
         return super(AddSubImmOp, self).cstr() + ");"

class LogicalImmOp(AddSubImmOp):
     def generate(self):
          AddSubImmOp.generate(self)
          self.immed = \
              immediates32[random.randint(0, len(immediates32)-1)] \
              if self.isWord else \
              immediates64[random.randint(0, len(immediates64)-1)]

          return self

     def astr(self):
          return (super(TwoRegImmedInstruction, self).astr()
                  + ', #0x%x' % self.immed)

     def cstr(self):
          return super(AddSubImmOp, self).cstr() + "ll);"

class SVEBinaryImmOp(Instruction):
    def __init__(self, name):
        reg = SVEVectorRegister().generate()
        self.reg = [reg, reg]
        self.numRegs = len(self.reg)
        self._width = RegVariant(0, 3)
        self._isLogical = False
        if name in ["and", "eor", "orr"]:
            self._isLogical = True
        Instruction.__init__(self, name)

    def generate(self):
        Instruction.generate(self)
        self.immed = random.randint(0, (1<<8)-1)
        if self._isLogical:
            vectype = self._width.cstr()
            if vectype == "__ B":
                self.immed = immediates8[random.randint(0, len(immediates8)-1)]
            elif vectype == "__ H":
                self.immed = immediates16[random.randint(0, len(immediates16)-1)]
            elif vectype == "__ S":
                self.immed = immediates32[random.randint(0, len(immediates32)-1)]
            elif vectype == "__ D":
                self.immed = immediates64[random.randint(0, len(immediates64)-1)]
        return self

    def cstr(self):
        formatStr = "%s%s, %s, %su);"
        return (formatStr
                % tuple(["__ sve_" + self._name + "("] +
                        [str(self.reg[0]), self._width.cstr(), self.immed]))

    def astr(self):
        formatStr = "%s%s, %s, #0x%x"
        Regs = [str(self.reg[i]) + self._width.astr() for i in range(0, self.numRegs)]
        return (formatStr
                % tuple([Instruction.astr(self)] + Regs + [self.immed]))

class MultiOp():

    def multipleForms(self):
         return 3

    def forms(self):
         return ["__ pc()", "back", "forth"]

    def aforms(self):
         return [".", "back", "forth"]

class AbsOp(MultiOp, Instruction):

    def cstr(self):
        return super(AbsOp, self).cstr() + "%s);"

    def astr(self):
        return Instruction.astr(self) + "%s"

class RegAndAbsOp(MultiOp, Instruction):

    def multipleForms(self):
        if self.name() == "adrp":
            # We can only test one form of adrp because anything other
            # than "adrp ." requires relocs in the assembler output
            return 1
        return 3

    def generate(self):
        Instruction.generate(self)
        self.reg = GeneralRegister().generate()
        return self

    def cstr(self):
        if self.name() == "adrp":
            return "__ _adrp(" + "%s, %s);" % (self.reg, "%s")
        return (super(RegAndAbsOp, self).cstr()
                + "%s, %s);" % (self.reg, "%s"))

    def astr(self):
        return (super(RegAndAbsOp, self).astr()
                + self.reg.astr(self.asmRegPrefix) + ", %s")

class RegImmAbsOp(RegAndAbsOp):

    def cstr(self):
        return (Instruction.cstr(self)
                + "%s, %s, %s);" % (self.reg, self.immed, "%s"))

    def astr(self):
        return (Instruction.astr(self)
                + ("%s, #%s, %s"
                   % (self.reg.astr(self.asmRegPrefix), self.immed, "%s")))

    def generate(self):
        super(RegImmAbsOp, self).generate()
        self.immed = random.randint(0, 1<<5 -1)
        return self

class MoveWideImmOp(RegImmAbsOp):

    def multipleForms(self):
         return 0

    def cstr(self):
        return (Instruction.cstr(self)
                + "%s, %s, %s);" % (self.reg, self.immed, self.shift))

    def astr(self):
        return (Instruction.astr(self)
                + ("%s, #%s, lsl %s"
                   % (self.reg.astr(self.asmRegPrefix),
                      self.immed, self.shift)))

    def generate(self):
        super(RegImmAbsOp, self).generate()
        self.immed = random.randint(0, 1<<16 -1)
        if self.isWord:
            self.shift = random.randint(0, 1) * 16
        else:
            self.shift = random.randint(0, 3) * 16
        return self

class BitfieldOp(TwoRegInstruction):

    def cstr(self):
        return (Instruction.cstr(self)
                + ("%s, %s, %s, %s);"
                   % (self.reg[0], self.reg[1], self.immr, self.imms)))

    def astr(self):
        return (TwoRegInstruction.astr(self)
                + (", #%s, #%s"
                   % (self.immr, self.imms)))

    def generate(self):
        TwoRegInstruction.generate(self)
        self.immr = random.randint(0, 31)
        self.imms = random.randint(0, 31)
        return self

class ExtractOp(ThreeRegInstruction):

    def generate(self):
        super(ExtractOp, self).generate()
        self.lsb = random.randint(0, (1<<5)-1 if self.isWord else (1<<6)-1)
        return self

    def cstr(self):
        return (ThreeRegInstruction.cstr(self)
                + (", %s);" % self.lsb))

    def astr(self):
        return (ThreeRegInstruction.astr(self)
                + (", #%s" % self.lsb))

class CondBranchOp(MultiOp, Instruction):

    def cstr(self):
        return "__ br(Assembler::" + self.name() + ", %s);"

    def astr(self):
        return "b." + self.name() + "\t%s"

class ImmOp(Instruction):

    def cstr(self):
        return "%s%s);" % (Instruction.cstr(self), self.immed)

    def astr(self):
        return Instruction.astr(self) + "#" + str(self.immed)

    def generate(self):
        self.immed = random.randint(0, 1<<16 -1)
        return self

class Op(Instruction):

    def cstr(self):
        return Instruction.cstr(self) + ");"
    def astr(self):
        return self.aname();

class SystemOp(Instruction):

     def __init__(self, op):
          Instruction.__init__(self, op[0])
          self.barriers = op[1]

     def generate(self):
          Instruction.generate(self)
          self.barrier \
              = self.barriers[random.randint(0, len(self.barriers)-1)]
          return self

     def cstr(self):
          return Instruction.cstr(self) + "Assembler::" + self.barrier + ");"

     def astr(self):
          return Instruction.astr(self) + self.barrier

conditionCodes = ["EQ", "NE", "HS", "CS", "LO", "CC", "MI", "PL", "VS", \
                       "VC", "HI", "LS", "GE", "LT", "GT", "LE", "AL", "NV"]

class ConditionalCompareOp(TwoRegImmedInstruction):

    def generate(self):
        TwoRegImmedInstruction.generate(self)
        self.cond = random.randint(0, 15)
        self.immed = random.randint(0, 15)
        return self

    def cstr(self):
        return (super(ConditionalCompareOp, self).cstr() + ", "
                + "Assembler::" + conditionCodes[self.cond] + ");")

    def astr(self):
        return (super(ConditionalCompareOp, self).astr() +
                 ", " + conditionCodes[self.cond])

class ConditionalCompareImmedOp(Instruction):

    def generate(self):
        self.reg = GeneralRegister().generate()
        self.cond = random.randint(0, 15)
        self.immed2 = random.randint(0, 15)
        self.immed = random.randint(0, 31)
        return self

    def cstr(self):
        return (Instruction.cstr(self) + str(self.reg) + ", "
                + str(self.immed) + ", "
                + str(self.immed2) + ", "
                + "Assembler::" + conditionCodes[self.cond] + ");")

    def astr(self):
        return (Instruction.astr(self)
                + self.reg.astr(self.asmRegPrefix)
                + ", #" + str(self.immed)
                + ", #" + str(self.immed2)
                + ", " + conditionCodes[self.cond])

class TwoRegOp(TwoRegInstruction):

    def cstr(self):
        return TwoRegInstruction.cstr(self) + ");"

class ThreeRegOp(ThreeRegInstruction):

    def cstr(self):
        return ThreeRegInstruction.cstr(self) + ");"

class FourRegMulOp(FourRegInstruction):

    def cstr(self):
        return FourRegInstruction.cstr(self) + ");"

    def astr(self):
        isMaddsub = self.name().startswith("madd") | self.name().startswith("msub")
        midPrefix = self.asmRegPrefix if isMaddsub else "w"
        return (Instruction.astr(self)
                + self.reg[0].astr(self.asmRegPrefix)
                + ", " + self.reg[1].astr(midPrefix)
                + ", " + self.reg[2].astr(midPrefix)
                + ", " + self.reg[3].astr(self.asmRegPrefix))

class ConditionalSelectOp(ThreeRegInstruction):

    def generate(self):
        ThreeRegInstruction.generate(self)
        self.cond = random.randint(0, 15)
        return self

    def cstr(self):
        return (ThreeRegInstruction.cstr(self) + ", "
                + "Assembler::" + conditionCodes[self.cond] + ");")

    def astr(self):
        return (ThreeRegInstruction.astr(self)
                + ", " + conditionCodes[self.cond])

class LoadStoreExclusiveOp(InstructionWithModes):

    def __init__(self, op): # op is a tuple of ["name", "mode", registers]
        InstructionWithModes.__init__(self, op[0], op[1])
        self.num_registers = op[2]

    def astr(self):
        result = self.aname() + '\t'
        regs = list(self.regs)
        index = regs.pop() # The last reg is the index register
        prefix = ('x' if (self.mode == 'x')
                  & ((self.name().startswith("ld"))
                     | (self.name().startswith("stlr"))) # Ewww :-(
                  else 'w')
        result = result + regs.pop(0).astr(prefix) + ", "
        for s in regs:
            result = result + s.astr(self.asmRegPrefix) + ", "
        result = result + "[" + index.astr("x") + "]"
        return result

    def cstr(self):
        result = InstructionWithModes.cstr(self)
        regs = list(self.regs)
        index = regs.pop() # The last reg is the index register
        for s in regs:
            result = result + str(s) + ", "
        result = result + str(index) + ");"
        return result

    def appendUniqueReg(self):
        result = 0
        while result == 0:
            newReg = GeneralRegister().generate()
            result = 1
            for i in self.regs:
                result = result and (i.number != newReg.number)
        self.regs.append(newReg)

    def generate(self):
        self.regs = []
        for i in range(self.num_registers):
            self.appendUniqueReg()
        return self

    def name(self):
        if self.mode == 'x':
            return self._name
        else:
            return self._name + self.mode

    def aname(self):
        if (self.mode == 'b') | (self.mode == 'h'):
            return self._name + self.mode
        else:
            return self._name

class Address(object):

    base_plus_unscaled_offset, pre, post, base_plus_reg, \
        base_plus_scaled_offset, pcrel, post_reg, base_only = range(8)
    kinds = ["base_plus_unscaled_offset", "pre", "post", "base_plus_reg",
             "base_plus_scaled_offset", "pcrel", "post_reg", "base_only"]
    extend_kinds = ["uxtw", "lsl", "sxtw", "sxtx"]

    @classmethod
    def kindToStr(cls, i):
         return cls.kinds[i]

    def generate(self, kind, shift_distance):
        self.kind = kind
        self.base = GeneralRegister().generate()
        self.index = GeneralRegister().generate()
        self.offset = {
            Address.base_plus_unscaled_offset: random.randint(-1<<8, 1<<8-1) | 1,
            Address.pre: random.randint(-1<<8, 1<<8-1),
            Address.post: random.randint(-1<<8, 1<<8-1),
            Address.pcrel: random.randint(0, 2),
            Address.base_plus_reg: 0,
            Address.base_plus_scaled_offset: (random.randint(0, 1<<11-1) | (3 << 9))*8,
            Address.post_reg: 0,
            Address.base_only: 0} [kind]
        self.offset >>= (3 - shift_distance)
        self.extend_kind = Address.extend_kinds[random.randint(0, 3)]
        self.shift_distance = random.randint(0, 1) * shift_distance
        return self

    def __str__(self):
        result = {
            Address.base_plus_unscaled_offset: "Address(%s, %s)" \
                % (str(self.base), self.offset),
            Address.pre: "Address(__ pre(%s, %s))" % (str(self.base), self.offset),
            Address.post: "Address(__ post(%s, %s))" % (str(self.base), self.offset),
            Address.post_reg: "Address(__ post(%s, %s))" % (str(self.base), self.index),
            Address.base_only: "Address(%s)" % (str(self.base)),
            Address.pcrel: "",
            Address.base_plus_reg: "Address(%s, %s, Address::%s(%s))" \
                % (self.base, self.index, self.extend_kind, self.shift_distance),
            Address.base_plus_scaled_offset:
            "Address(%s, %s)" % (self.base, self.offset) } [self.kind]
        if (self.kind == Address.pcrel):
            result = ["__ pc()", "back", "forth"][self.offset]
        return result

    def astr(self, prefix):
        extend_prefix = prefix
        if self.kind == Address.base_plus_reg:
            if self.extend_kind.endswith("w"):
                extend_prefix = "w"
        result = {
            Address.base_plus_unscaled_offset: "[%s, %s]" \
                 % (self.base.astr(prefix), self.offset),
            Address.pre: "[%s, %s]!" % (self.base.astr(prefix), self.offset),
            Address.post: "[%s], %s" % (self.base.astr(prefix), self.offset),
            Address.post_reg: "[%s], %s" % (self.base.astr(prefix), self.index.astr(prefix)),
            Address.base_only: "[%s]" %  (self.base.astr(prefix)),
            Address.pcrel: "",
            Address.base_plus_reg: "[%s, %s, %s #%s]" \
                % (self.base.astr(prefix), self.index.astr(extend_prefix),
                   self.extend_kind, self.shift_distance),
            Address.base_plus_scaled_offset: \
                "[%s, %s]" \
                % (self.base.astr(prefix), self.offset)
            } [self.kind]
        if (self.kind == Address.pcrel):
            result = [".", "back", "forth"][self.offset]
        return result

class LoadStoreOp(InstructionWithModes):

    def __init__(self, args):
        name, self.asmname, self.kind, mode = args
        InstructionWithModes.__init__(self, name, mode)

    def generate(self):

        # This is something of a kludge, but the offset needs to be
        # scaled by the memory datamode somehow.
        shift = 3
        if (self.mode == 'b') | (self.asmname.endswith("b")):
            shift = 0
        elif (self.mode == 'h') | (self.asmname.endswith("h")):
            shift = 1
        elif (self.mode == 'w') | (self.asmname.endswith("w")) \
                | (self.mode == 's') :
            shift = 2

        self.adr = Address().generate(self.kind, shift)

        isFloat = (self.mode == 'd') | (self.mode == 's')

        regMode = FloatRegister if isFloat else GeneralRegister
        self.reg = regMode().generate()
        kindStr = Address.kindToStr(self.kind);
        if (not isFloat) and (kindStr is "pre" or kindStr is "post"):
            (self.reg.number, self.adr.base.number) = random.sample(list(set(range(31)) - set([18])), 2)
        return self

    def cstr(self):
        if not(self._name.startswith("prfm")):
            return "%s%s, %s);" % (Instruction.cstr(self), str(self.reg), str(self.adr))
        else: # No target register for a prefetch
            return "%s%s);" % (Instruction.cstr(self), str(self.adr))

    def astr(self):
        if not(self._name.startswith("prfm")):
            return "%s\t%s, %s" % (self.aname(), self.reg.astr(self.asmRegPrefix),
                                     self.adr.astr("x"))
        else: # No target register for a prefetch
            return "%s %s" % (self.aname(),
                                     self.adr.astr("x"))

    def aname(self):
         result = self.asmname
         # if self.kind == Address.base_plus_unscaled_offset:
         #      result = result.replace("ld", "ldu", 1)
         #      result = result.replace("st", "stu", 1)
         return result

class LoadStorePairOp(InstructionWithModes):

     numRegs = 2

     def __init__(self, args):
          name, self.asmname, self.kind, mode = args
          InstructionWithModes.__init__(self, name, mode)
          self.offset = random.randint(-1<<4, 1<<4-1) << 4

     def generate(self):
          self.reg = [OperandFactory.create(self.mode).generate()
                      for i in range(self.numRegs)]
          self.base = OperandFactory.create('x').generate()
          kindStr = Address.kindToStr(self.kind);
          if kindStr is "pre" or kindStr is "post":
              if self._name.startswith("ld"):
                  (self.reg[0].number, self.reg[1].number, self.base.number) = random.sample(list(set(range(31)) - set([18])), 3)
              if self._name.startswith("st"):
                  self.base.number = random.choice(list(set(range(31)) - set([self.reg[0].number, self.reg[1].number, 18])))
          elif self._name.startswith("ld"):
              (self.reg[0].number, self.reg[1].number) = random.sample(list(set(range(31)) - set([18])), 2)
          return self

     def astr(self):
          address = ["[%s, #%s]", "[%s, #%s]!", "[%s], #%s"][self.kind]
          address = address % (self.base.astr('x'), self.offset)
          result = "%s\t%s, %s, %s" \
              % (self.asmname,
                 self.reg[0].astr(self.asmRegPrefix),
                 self.reg[1].astr(self.asmRegPrefix), address)
          return result

     def cstr(self):
          address = {
               Address.base_plus_unscaled_offset: "Address(%s, %s)" \
                    % (str(self.base), self.offset),
               Address.pre: "Address(__ pre(%s, %s))" % (str(self.base), self.offset),
               Address.post: "Address(__ post(%s, %s))" % (str(self.base), self.offset),
               } [self.kind]
          result = "__ %s(%s, %s, %s);" \
              % (self.name(), self.reg[0], self.reg[1], address)
          return result

class FloatInstruction(Instruction):

    def aname(self):
        if (self._name.endswith("s") | self._name.endswith("d")):
            return self._name[:len(self._name)-1]
        else:
            return self._name

    def __init__(self, args):
        name, self.modes = args
        Instruction.__init__(self, name)

    def generate(self):
        self.reg = [OperandFactory.create(self.modes[i]).generate()
                    for i in range(self.numRegs)]
        return self

    def cstr(self):
        formatStr = "%s%s" + ''.join([", %s" for i in range(1, self.numRegs)] + [");"])
        return (formatStr
                % tuple([Instruction.cstr(self)] +
                        [str(self.reg[i]) for i in range(self.numRegs)])) # Yowza

    def astr(self):
        formatStr = "%s%s" + ''.join([", %s" for i in range(1, self.numRegs)])
        return (formatStr
                % tuple([Instruction.astr(self)] +
                        [(self.reg[i].astr(self.modes[i])) for i in range(self.numRegs)]))

class SVEVectorOp(Instruction):
    def __init__(self, args):
        name = args[0]
        regTypes = args[1]
        regs = []
        for c in regTypes:
            regs.append(OperandFactory.create(c).generate())
        self.reg = regs
        self.numRegs = len(regs)
        if regTypes[0] != "p" and regTypes[1] == 'P':
           self._isPredicated = True
           assert len(args) > 2, "Must specify predicate type"
           for arg in args[2:]:
              if arg == 'm':
                 self._merge = "/m"
              elif arg == 'z':
                 self._merge = "/z"
              else:
                 assert arg == "dn", "Unknown predicate type"
        else:
           self._isPredicated = False
           self._merge = ""

        self._bitwiseop = False
        if name[0] == 'f':
            self._width = RegVariant(2, 3)
        elif not self._isPredicated and (name in ["and", "eor", "orr", "bic"]):
            self._width = RegVariant(3, 3)
            self._bitwiseop = True
        else:
            self._width = RegVariant(0, 3)

        self._dnm = None
        if len(args) > 2:
           for arg in args[2:]:
             if arg == "dn":
               self._dnm = arg

        Instruction.__init__(self, name)

    def cstr(self):
        formatStr = "%s%s" + ''.join([", %s" for i in range(0, self.numRegs)] + [");"])
        if self._bitwiseop:
            width = []
            formatStr = "%s%s" + ''.join([", %s" for i in range(1, self.numRegs)] + [");"])
        else:
            width = [self._width.cstr()]
        return (formatStr
                % tuple(["__ sve_" + self._name + "("] +
                        [str(self.reg[0])] +
                        width +
                        [str(self.reg[i]) for i in range(1, self.numRegs)]))
    def astr(self):
        formatStr = "%s%s" + ''.join([", %s" for i in range(1, self.numRegs)])
        if self._dnm == 'dn':
            formatStr += ", %s"
            dnReg = [str(self.reg[0]) + self._width.astr()]
        else:
            dnReg = []

        if self._isPredicated:
            restRegs = [str(self.reg[1]) + self._merge] + dnReg + [str(self.reg[i]) + self._width.astr() for i in range(2, self.numRegs)]
        else:
            restRegs = dnReg + [str(self.reg[i]) + self._width.astr() for i in range(1, self.numRegs)]
        return (formatStr
                % tuple([Instruction.astr(self)] +
                        [str(self.reg[0]) + self._width.astr()] +
                        restRegs))
    def generate(self):
        return self

class SVEReductionOp(Instruction):
    def __init__(self, args):
        name = args[0]
        lowRegType = args[1]
        self.reg = []
        Instruction.__init__(self, name)
        self.reg.append(OperandFactory.create('s').generate())
        self.reg.append(OperandFactory.create('P').generate())
        self.reg.append(OperandFactory.create('Z').generate())
        self._width = RegVariant(lowRegType, 3)
    def cstr(self):
        return "__ sve_%s(%s, %s, %s, %s);" % (self.name(),
                                              str(self.reg[0]),
                                              self._width.cstr(),
                                              str(self.reg[1]),
                                              str(self.reg[2]))
    def astr(self):
        if self.name() == "uaddv":
            dstRegName = "d" + str(self.reg[0].number)
        else:
            dstRegName = self._width.astr()[1] + str(self.reg[0].number)
        formatStr = "%s %s, %s, %s"
        if self.name() == "fadda":
            formatStr += ", %s"
            moreReg = [dstRegName]
        else:
            moreReg = []
        return formatStr % tuple([self.name()] +
                                 [dstRegName] +
                                 [str(self.reg[1])] +
                                 moreReg +
                                 [str(self.reg[2]) + self._width.astr()])

class LdStNEONOp(Instruction):
    def __init__(self, args):
        self._name, self.regnum, self.arrangement, self.addresskind = args

    def generate(self):
        self.address = Address().generate(self.addresskind, 0)
        self._firstSIMDreg = FloatRegister().generate()
        if (self.addresskind  == Address.post):
            if (self._name in ["ld1r", "ld2r", "ld3r", "ld4r"]):
                elem_size = {"8B" : 1, "16B" : 1, "4H" : 2, "8H" : 2, "2S" : 4, "4S" : 4, "1D" : 8, "2D" : 8} [self.arrangement]
                self.address.offset = self.regnum * elem_size
            else:
                if (self.arrangement in ["8B", "4H", "2S", "1D"]):
                    self.address.offset = self.regnum * 8
                else:
                    self.address.offset = self.regnum * 16
        return self

    def cstr(self):
        buf = super(LdStNEONOp, self).cstr() + str(self._firstSIMDreg)
        current = self._firstSIMDreg
        for cnt in range(1, self.regnum):
            buf = '%s, %s' % (buf, current.nextReg())
            current = current.nextReg()
        return '%s, __ T%s, %s);' % (buf, self.arrangement, str(self.address))

    def astr(self):
        buf = '%s\t{%s.%s' % (self._name, self._firstSIMDreg, self.arrangement)
        current = self._firstSIMDreg
        for cnt in range(1, self.regnum):
            buf = '%s, %s.%s' % (buf, current.nextReg(), self.arrangement)
            current = current.nextReg()
        return  '%s}, %s' % (buf, self.address.astr("x"))

    def aname(self):
         return self._name

class NEONReduceInstruction(Instruction):
    def __init__(self, args):
        self._name, self.insname, self.arrangement = args

    def generate(self):
        current = FloatRegister().generate()
        self.dstSIMDreg = current
        self.srcSIMDreg = current.nextReg()
        return self

    def cstr(self):
        buf = Instruction.cstr(self) + str(self.dstSIMDreg)
        if self._name == "fmaxp" or self._name == "fminp":
            buf = '%s, %s, __ %s);' % (buf, self.srcSIMDreg, self.arrangement[1:])
        else:
            buf = '%s, __ T%s, %s);' % (buf, self.arrangement, self.srcSIMDreg)
        return buf

    def astr(self):
        buf = '%s\t%s' % (self.insname, self.dstSIMDreg.astr(self.arrangement[-1].lower()))
        buf = '%s, %s.%s' % (buf, self.srcSIMDreg, self.arrangement)
        return buf

    def aname(self):
        return self._name

class CommonNEONInstruction(Instruction):
    def __init__(self, args):
        self._name, self.insname, self.arrangement = args

    def generate(self):
        self._firstSIMDreg = FloatRegister().generate()
        return self

    def cstr(self):
        buf = Instruction.cstr(self) + str(self._firstSIMDreg)
        buf = '%s, __ T%s' % (buf, self.arrangement)
        current = self._firstSIMDreg
        for cnt in range(1, self.numRegs):
            buf = '%s, %s' % (buf, current.nextReg())
            current = current.nextReg()
        return '%s);' % (buf)

    def astr(self):
        buf = '%s\t%s.%s' % (self.insname, self._firstSIMDreg, self.arrangement)
        current = self._firstSIMDreg
        for cnt in range(1, self.numRegs):
            buf = '%s, %s.%s' % (buf, current.nextReg(), self.arrangement)
            current = current.nextReg()
        return buf

    def aname(self):
        return self._name

class SHA512SIMDOp(Instruction):

    def generate(self):
        if (self._name == 'sha512su0'):
            self.reg = [FloatRegister().generate(), FloatRegister().generate()]
        else:
            self.reg = [FloatRegister().generate(), FloatRegister().generate(),
                        FloatRegister().generate()]
        return self

    def cstr(self):
        if (self._name == 'sha512su0'):
            return (super(SHA512SIMDOp, self).cstr()
                    + ('%s, __ T2D, %s);' % (self.reg[0], self.reg[1])))
        else:
            return (super(SHA512SIMDOp, self).cstr()
                    + ('%s, __ T2D, %s, %s);' % (self.reg[0], self.reg[1], self.reg[2])))

    def astr(self):
        if (self._name == 'sha512su0'):
            return (super(SHA512SIMDOp, self).astr()
                    + ('\t%s.2D, %s.2D' % (self.reg[0].astr("v"), self.reg[1].astr("v"))))
        elif (self._name == 'sha512su1'):
            return (super(SHA512SIMDOp, self).astr()
                    + ('\t%s.2D, %s.2D, %s.2D' % (self.reg[0].astr("v"),
                       self.reg[1].astr("v"), self.reg[2].astr("v"))))
        else:
            return (super(SHA512SIMDOp, self).astr()
                    + ('\t%s, %s, %s.2D' % (self.reg[0].astr("q"),
                       self.reg[1].astr("q"), self.reg[2].astr("v"))))

class SHA3SIMDOp(Instruction):

    def generate(self):
        if ((self._name == 'eor3') or (self._name == 'bcax')):
            self.reg = [FloatRegister().generate(), FloatRegister().generate(),
                        FloatRegister().generate(), FloatRegister().generate()]
        else:
            self.reg = [FloatRegister().generate(), FloatRegister().generate(),
                        FloatRegister().generate()]
            if (self._name == 'xar'):
                self.imm6 = random.randint(0, 63)
        return self

    def cstr(self):
        if ((self._name == 'eor3') or (self._name == 'bcax')):
            return (super(SHA3SIMDOp, self).cstr()
                    + ('%s, __ T16B, %s, %s, %s);' % (self.reg[0], self.reg[1], self.reg[2], self.reg[3])))
        elif (self._name == 'rax1'):
            return (super(SHA3SIMDOp, self).cstr()
                    + ('%s, __ T2D, %s, %s);' % (self.reg[0], self.reg[1], self.reg[2])))
        else:
            return (super(SHA3SIMDOp, self).cstr()
                    + ('%s, __ T2D, %s, %s, %s);' % (self.reg[0], self.reg[1], self.reg[2], self.imm6)))

    def astr(self):
        if ((self._name == 'eor3') or (self._name == 'bcax')):
            return (super(SHA3SIMDOp, self).astr()
                    + ('\t%s.16B, %s.16B, %s.16B, %s.16B' % (self.reg[0].astr("v"), self.reg[1].astr("v"),
                        self.reg[2].astr("v"), self.reg[3].astr("v"))))
        elif (self._name == 'rax1'):
            return (super(SHA3SIMDOp, self).astr()
                    + ('\t%s.2D, %s.2D, %s.2D') % (self.reg[0].astr("v"), self.reg[1].astr("v"),
                        self.reg[2].astr("v")))
        else:
            return (super(SHA3SIMDOp, self).astr()
                    + ('\t%s.2D, %s.2D, %s.2D, #%s') % (self.reg[0].astr("v"), self.reg[1].astr("v"),
                        self.reg[2].astr("v"), self.imm6))

class LSEOp(Instruction):
    def __init__(self, args):
        self._name, self.asmname, self.size, self.suffix = args

    def generate(self):
        self._name = "%s%s" % (self._name, self.suffix)
        self.asmname = "%s%s" % (self.asmname, self.suffix)
        self.srcReg = GeneralRegisterOrZr().generate()
        self.tgtReg = GeneralRegisterOrZr().generate()
        self.adrReg = GeneralRegisterOrSp().generate()

        return self

    def cstr(self):
        sizeSpec = {"x" : "Assembler::xword", "w" : "Assembler::word"} [self.size]
        return super(LSEOp, self).cstr() + "%s, %s, %s, %s);" % (sizeSpec, self.srcReg, self.tgtReg, self.adrReg)

    def astr(self):
        return "%s\t%s, %s, [%s]" % (self.asmname, self.srcReg.astr(self.size), self.tgtReg.astr(self.size), self.adrReg.astr("x"))

    def aname(self):
         return self.asmname

class TwoRegFloatOp(FloatInstruction):
    numRegs = 2

class ThreeRegFloatOp(TwoRegFloatOp):
    numRegs = 3

class FourRegFloatOp(TwoRegFloatOp):
    numRegs = 4

class FloatConvertOp(TwoRegFloatOp):

    def __init__(self, args):
        self._cname, self._aname, modes = args
        TwoRegFloatOp.__init__(self, [self._cname, modes])

    def aname(self):
        return self._aname

    def cname(self):
        return self._cname

class TwoRegNEONOp(CommonNEONInstruction):
    numRegs = 2

class ThreeRegNEONOp(TwoRegNEONOp):
    numRegs = 3

class SpecialCases(Instruction):
    def __init__(self, data):
        self._name = data[0]
        self._cstr = data[1]
        self._astr = data[2]

    def cstr(self):
        return self._cstr

    def astr(self):
        return self._astr

def generate(kind, names):
    outfile.write("# " + kind.__name__ + "\n");
    print "\n// " + kind.__name__
    for name in names:
        for i in range(1):
             op = kind(name).generate()
             if op.multipleForms():
                  forms = op.forms()
                  aforms = op.aforms()
                  for i in range(op.multipleForms()):
                       cstr = op.cstr() % forms[i]
                       astr = op.astr() % aforms[i]
                       print "    %-50s //\t%s" % (cstr, astr)
                       outfile.write("\t" + astr + "\n")
             else:
                  print "    %-50s //\t%s" % (op.cstr(), op.astr())
                  outfile.write("\t" + op.astr() + "\n")

outfile = open("aarch64ops.s", "w")

# To minimize the changes of assembler test code
random.seed(0)

print "// BEGIN  Generated code -- do not edit"
print "// Generated by aarch64-asmtest.py"

print "    Label back, forth;"
print "    __ bind(back);"

outfile.write("back:\n")

generate (ArithOp,
          [ "add", "sub", "adds", "subs",
            "addw", "subw", "addsw", "subsw",
            "and", "orr", "eor", "ands",
            "andw", "orrw", "eorw", "andsw",
            "bic", "orn", "eon", "bics",
            "bicw", "ornw", "eonw", "bicsw" ])

generate (AddSubImmOp,
          [ "addw", "addsw", "subw", "subsw",
            "add", "adds", "sub", "subs"])
generate (LogicalImmOp,
          [ "andw", "orrw", "eorw", "andsw",
            "and", "orr", "eor", "ands"])

generate (AbsOp, [ "b", "bl" ])

generate (RegAndAbsOp, ["cbzw", "cbnzw", "cbz", "cbnz", "adr", "adrp"])

generate (RegImmAbsOp, ["tbz", "tbnz"])

generate (MoveWideImmOp, ["movnw", "movzw", "movkw", "movn", "movz", "movk"])

generate (BitfieldOp, ["sbfm", "bfmw", "ubfmw", "sbfm", "bfm", "ubfm"])

generate (ExtractOp, ["extrw", "extr"])

generate (CondBranchOp, ["EQ", "NE", "HS", "CS", "LO", "CC", "MI", "PL", "VS", "VC",
                        "HI", "LS", "GE", "LT", "GT", "LE", "AL", "NV" ])

generate (ImmOp, ["svc", "hvc", "smc", "brk", "hlt", # "dcps1",  "dcps2",  "dcps3"
               ])

generate (Op, ["nop", "eret", "drps", "isb"])

barriers = ["OSHLD", "OSHST", "OSH", "NSHLD", "NSHST", "NSH",
            "ISHLD", "ISHST", "ISH", "LD", "ST", "SY"]

generate (SystemOp, [["dsb", barriers], ["dmb", barriers]])

generate (OneRegOp, ["br", "blr"])

for mode in 'xwhb':
    generate (LoadStoreExclusiveOp, [["stxr", mode, 3], ["stlxr", mode, 3],
                                     ["ldxr", mode, 2], ["ldaxr", mode, 2],
                                     ["stlr", mode, 2], ["ldar", mode, 2]])

for mode in 'xw':
    generate (LoadStoreExclusiveOp, [["ldxp", mode, 3], ["ldaxp", mode, 3],
                                     ["stxp", mode, 4], ["stlxp", mode, 4]])

for kind in range(6):
    sys.stdout.write("\n// " + Address.kindToStr(kind))
    if kind != Address.pcrel:
        generate (LoadStoreOp,
                  [["str", "str", kind, "x"], ["str", "str", kind, "w"],
                   ["str", "strb", kind, "b"], ["str", "strh", kind, "h"],
                   ["ldr", "ldr", kind, "x"], ["ldr", "ldr", kind, "w"],
                   ["ldr", "ldrb", kind, "b"], ["ldr", "ldrh", kind, "h"],
                   ["ldrsb", "ldrsb", kind, "x"], ["ldrsh", "ldrsh", kind, "x"],
                   ["ldrsh", "ldrsh", kind, "w"], ["ldrsw", "ldrsw", kind, "x"],
                   ["ldr", "ldr", kind, "d"], ["ldr", "ldr", kind, "s"],
                   ["str", "str", kind, "d"], ["str", "str", kind, "s"],
                   ])
    else:
        generate (LoadStoreOp,
                  [["ldr", "ldr", kind, "x"], ["ldr", "ldr", kind, "w"]])


for kind in (Address.base_plus_unscaled_offset, Address.pcrel, Address.base_plus_reg, \
                 Address.base_plus_scaled_offset):
    generate (LoadStoreOp,
              [["prfm", "prfm\tPLDL1KEEP,", kind, "x"]])

generate(AddSubCarryOp, ["adcw", "adcsw", "sbcw", "sbcsw", "adc", "adcs", "sbc", "sbcs"])

generate(AddSubExtendedOp, ["addw", "addsw", "sub", "subsw", "add", "adds", "sub", "subs"])

generate(ConditionalCompareOp, ["ccmnw", "ccmpw", "ccmn", "ccmp"])
generate(ConditionalCompareImmedOp, ["ccmnw", "ccmpw", "ccmn", "ccmp"])
generate(ConditionalSelectOp,
         ["cselw", "csincw", "csinvw", "csnegw", "csel", "csinc", "csinv", "csneg"])

generate(TwoRegOp,
         ["rbitw", "rev16w", "revw", "clzw", "clsw", "rbit",
          "rev16", "rev32", "rev", "clz", "cls"])
generate(ThreeRegOp,
         ["udivw", "sdivw", "lslvw", "lsrvw", "asrvw", "rorvw", "udiv", "sdiv",
          "lslv", "lsrv", "asrv", "rorv", "umulh", "smulh"])
generate(FourRegMulOp,
         ["maddw", "msubw", "madd", "msub", "smaddl", "smsubl", "umaddl", "umsubl"])

generate(ThreeRegFloatOp,
         [["fabds", "sss"], ["fmuls", "sss"], ["fdivs", "sss"], ["fadds", "sss"], ["fsubs", "sss"],
          ["fabdd", "ddd"], ["fmuld", "ddd"], ["fdivd", "ddd"], ["faddd", "ddd"], ["fsubd", "ddd"],
          ])

generate(FourRegFloatOp,
         [["fmadds", "ssss"], ["fmsubs", "ssss"], ["fnmadds", "ssss"], ["fnmadds", "ssss"],
          ["fmaddd", "dddd"], ["fmsubd", "dddd"], ["fnmaddd", "dddd"], ["fnmaddd", "dddd"],])

generate(TwoRegFloatOp,
         [["fmovs", "ss"], ["fabss", "ss"], ["fnegs", "ss"], ["fsqrts", "ss"],
          ["fcvts", "ds"],
          ["fmovd", "dd"], ["fabsd", "dd"], ["fnegd", "dd"], ["fsqrtd", "dd"],
          ["fcvtd", "sd"],
          ])

generate(FloatConvertOp, [["fcvtzsw", "fcvtzs", "ws"], ["fcvtzs", "fcvtzs", "xs"],
                          ["fcvtzdw", "fcvtzs", "wd"], ["fcvtzd", "fcvtzs", "xd"],
                          ["scvtfws", "scvtf", "sw"], ["scvtfs", "scvtf", "sx"],
                          ["scvtfwd", "scvtf", "dw"], ["scvtfd", "scvtf", "dx"],
                          ["fmovs", "fmov", "ws"], ["fmovd", "fmov", "xd"],
                          ["fmovs", "fmov", "sw"], ["fmovd", "fmov", "dx"]])

generate(TwoRegFloatOp, [["fcmps", "ss"], ["fcmpd", "dd"],
                         ["fcmps", "sz"], ["fcmpd", "dz"]])

for kind in range(3):
     generate(LoadStorePairOp, [["stp", "stp", kind, "w"], ["ldp", "ldp", kind, "w"],
                                ["ldpsw", "ldpsw", kind, "x"],
                                ["stp", "stp", kind, "x"], ["ldp", "ldp", kind, "x"]
                                ])
generate(LoadStorePairOp, [["stnp", "stnp", 0, "w"], ["ldnp", "ldnp", 0, "w"],
                           ["stnp", "stnp", 0, "x"], ["ldnp", "ldnp", 0, "x"]])

generate(LdStNEONOp, [["ld1",  1, "8B",  Address.base_only],
                      ["ld1",  2, "16B", Address.post],
                      ["ld1",  3, "1D",  Address.post_reg],
                      ["ld1",  4, "8H",  Address.post],
                      ["ld1r", 1, "8B",  Address.base_only],
                      ["ld1r", 1, "4S",  Address.post],
                      ["ld1r", 1, "1D",  Address.post_reg],
                      ["ld2",  2, "2D",  Address.base_only],
                      ["ld2",  2, "4H",  Address.post],
                      ["ld2r", 2, "16B", Address.base_only],
                      ["ld2r", 2, "2S",  Address.post],
                      ["ld2r", 2, "2D",  Address.post_reg],
                      ["ld3",  3, "4S",  Address.post_reg],
                      ["ld3",  3, "2S",  Address.base_only],
                      ["ld3r", 3, "8H",  Address.base_only],
                      ["ld3r", 3, "4S",  Address.post],
                      ["ld3r", 3, "1D",  Address.post_reg],
                      ["ld4",  4, "8H",  Address.post],
                      ["ld4",  4, "8B",  Address.post_reg],
                      ["ld4r", 4, "8B",  Address.base_only],
                      ["ld4r", 4, "4H",  Address.post],
                      ["ld4r", 4, "2S",  Address.post_reg],
])

generate(NEONReduceInstruction,
         [["addv", "addv", "8B"], ["addv", "addv", "16B"],
          ["addv", "addv", "4H"], ["addv", "addv", "8H"],
          ["addv", "addv", "4S"],
          ["smaxv", "smaxv", "8B"], ["smaxv", "smaxv", "16B"],
          ["smaxv", "smaxv", "4H"], ["smaxv", "smaxv", "8H"],
          ["smaxv", "smaxv", "4S"], ["fmaxv", "fmaxv", "4S"],
          ["sminv", "sminv", "8B"], ["uminv", "uminv", "8B"],
          ["sminv", "sminv", "16B"],["uminv", "uminv", "16B"],
          ["sminv", "sminv", "4H"], ["uminv", "uminv", "4H"],
          ["sminv", "sminv", "8H"], ["uminv", "uminv", "8H"],
          ["sminv", "sminv", "4S"], ["uminv", "uminv", "4S"],
          ["fminv", "fminv", "4S"],
          ["fmaxp", "fmaxp", "2S"], ["fmaxp", "fmaxp", "2D"],
          ["fminp", "fminp", "2S"], ["fminp", "fminp", "2D"],
          ])

generate(TwoRegNEONOp,
         [["absr", "abs", "8B"], ["absr", "abs", "16B"],
          ["absr", "abs", "4H"], ["absr", "abs", "8H"],
          ["absr", "abs", "2S"], ["absr", "abs", "4S"],
          ["absr", "abs", "2D"],
          ["fabs", "fabs", "2S"], ["fabs", "fabs", "4S"],
          ["fabs", "fabs", "2D"],
          ["fneg", "fneg", "2S"], ["fneg", "fneg", "4S"],
          ["fneg", "fneg", "2D"],
          ["fsqrt", "fsqrt", "2S"], ["fsqrt", "fsqrt", "4S"],
          ["fsqrt", "fsqrt", "2D"],
          ["notr", "not", "8B"], ["notr", "not", "16B"],
          ])

generate(ThreeRegNEONOp,
         [["andr", "and", "8B"], ["andr", "and", "16B"],
          ["orr", "orr", "8B"], ["orr", "orr", "16B"],
          ["eor", "eor", "8B"], ["eor", "eor", "16B"],
          ["addv", "add", "8B"], ["addv", "add", "16B"],
          ["addv", "add", "4H"], ["addv", "add", "8H"],
          ["addv", "add", "2S"], ["addv", "add", "4S"],
          ["addv", "add", "2D"],
          ["fadd", "fadd", "2S"], ["fadd", "fadd", "4S"],
          ["fadd", "fadd", "2D"],
          ["subv", "sub", "8B"], ["subv", "sub", "16B"],
          ["subv", "sub", "4H"], ["subv", "sub", "8H"],
          ["subv", "sub", "2S"], ["subv", "sub", "4S"],
          ["subv", "sub", "2D"],
          ["fsub", "fsub", "2S"], ["fsub", "fsub", "4S"],
          ["fsub", "fsub", "2D"],
          ["mulv", "mul", "8B"], ["mulv", "mul", "16B"],
          ["mulv", "mul", "4H"], ["mulv", "mul", "8H"],
          ["mulv", "mul", "2S"], ["mulv", "mul", "4S"],
          ["fabd", "fabd", "2S"], ["fabd", "fabd", "4S"],
          ["fabd", "fabd", "2D"],
          ["fmul", "fmul", "2S"], ["fmul", "fmul", "4S"],
          ["fmul", "fmul", "2D"],
          ["mlav", "mla", "4H"], ["mlav", "mla", "8H"],
          ["mlav", "mla", "2S"], ["mlav", "mla", "4S"],
          ["fmla", "fmla", "2S"], ["fmla", "fmla", "4S"],
          ["fmla", "fmla", "2D"],
          ["mlsv", "mls", "4H"], ["mlsv", "mls", "8H"],
          ["mlsv", "mls", "2S"], ["mlsv", "mls", "4S"],
          ["fmls", "fmls", "2S"], ["fmls", "fmls", "4S"],
          ["fmls", "fmls", "2D"],
          ["fdiv", "fdiv", "2S"], ["fdiv", "fdiv", "4S"],
          ["fdiv", "fdiv", "2D"],
          ["maxv", "smax", "8B"], ["maxv", "smax", "16B"],
          ["maxv", "smax", "4H"], ["maxv", "smax", "8H"],
          ["maxv", "smax", "2S"], ["maxv", "smax", "4S"],
          ["smaxp", "smaxp", "8B"], ["smaxp", "smaxp", "16B"],
          ["smaxp", "smaxp", "4H"], ["smaxp", "smaxp", "8H"],
          ["smaxp", "smaxp", "2S"], ["smaxp", "smaxp", "4S"],
          ["fmax", "fmax", "2S"], ["fmax", "fmax", "4S"],
          ["fmax", "fmax", "2D"],
          ["minv", "smin", "8B"], ["minv", "smin", "16B"],
          ["minv", "smin", "4H"], ["minv", "smin", "8H"],
          ["minv", "smin", "2S"], ["minv", "smin", "4S"],
          ["sminp", "sminp", "8B"], ["sminp", "sminp", "16B"],
          ["sminp", "sminp", "4H"], ["sminp", "sminp", "8H"],
          ["sminp", "sminp", "2S"], ["sminp", "sminp", "4S"],
          ["fmin", "fmin", "2S"], ["fmin", "fmin", "4S"],
          ["fmin", "fmin", "2D"],
          ["cmeq", "cmeq", "8B"], ["cmeq", "cmeq", "16B"],
          ["cmeq", "cmeq", "4H"], ["cmeq", "cmeq", "8H"],
          ["cmeq", "cmeq", "2S"], ["cmeq", "cmeq", "4S"],
          ["cmeq", "cmeq", "2D"],
          ["fcmeq", "fcmeq", "2S"], ["fcmeq", "fcmeq", "4S"],
          ["fcmeq", "fcmeq", "2D"],
          ["cmgt", "cmgt", "8B"], ["cmgt", "cmgt", "16B"],
          ["cmgt", "cmgt", "4H"], ["cmgt", "cmgt", "8H"],
          ["cmgt", "cmgt", "2S"], ["cmgt", "cmgt", "4S"],
          ["cmgt", "cmgt", "2D"],
          ["cmhi", "cmhi", "8B"], ["cmhi", "cmhi", "16B"],
          ["cmhi", "cmhi", "4H"], ["cmhi", "cmhi", "8H"],
          ["cmhi", "cmhi", "2S"], ["cmhi", "cmhi", "4S"],
          ["cmhi", "cmhi", "2D"],
          ["cmhs", "cmhs", "8B"], ["cmhs", "cmhs", "16B"],
          ["cmhs", "cmhs", "4H"], ["cmhs", "cmhs", "8H"],
          ["cmhs", "cmhs", "2S"], ["cmhs", "cmhs", "4S"],
          ["cmhs", "cmhs", "2D"],
          ["fcmgt", "fcmgt", "2S"], ["fcmgt", "fcmgt", "4S"],
          ["fcmgt", "fcmgt", "2D"],
          ["cmge", "cmge", "8B"], ["cmge", "cmge", "16B"],
          ["cmge", "cmge", "4H"], ["cmge", "cmge", "8H"],
          ["cmge", "cmge", "2S"], ["cmge", "cmge", "4S"],
          ["cmge", "cmge", "2D"],
          ["fcmge", "fcmge", "2S"], ["fcmge", "fcmge", "4S"],
          ["fcmge", "fcmge", "2D"],
          ])

generate(SpecialCases, [["ccmn",   "__ ccmn(zr, zr, 3u, Assembler::LE);",                "ccmn\txzr, xzr, #3, LE"],
                        ["ccmnw",  "__ ccmnw(zr, zr, 5u, Assembler::EQ);",               "ccmn\twzr, wzr, #5, EQ"],
                        ["ccmp",   "__ ccmp(zr, 1, 4u, Assembler::NE);",                 "ccmp\txzr, 1, #4, NE"],
                        ["ccmpw",  "__ ccmpw(zr, 2, 2, Assembler::GT);",                 "ccmp\twzr, 2, #2, GT"],
                        ["extr",   "__ extr(zr, zr, zr, 0);",                            "extr\txzr, xzr, xzr, 0"],
                        ["stlxp",  "__ stlxp(r0, zr, zr, sp);",                          "stlxp\tw0, xzr, xzr, [sp]"],
                        ["stlxpw", "__ stlxpw(r2, zr, zr, r3);",                         "stlxp\tw2, wzr, wzr, [x3]"],
                        ["stxp",   "__ stxp(r4, zr, zr, r5);",                           "stxp\tw4, xzr, xzr, [x5]"],
                        ["stxpw",  "__ stxpw(r6, zr, zr, sp);",                          "stxp\tw6, wzr, wzr, [sp]"],
                        ["dup",    "__ dup(v0, __ T16B, zr);",                           "dup\tv0.16b, wzr"],
                        ["dup",    "__ dup(v0, __ S, v1);",                              "dup\ts0, v1.s[0]"],
                        ["mov",    "__ mov(v1, __ T1D, 0, zr);",                         "mov\tv1.d[0], xzr"],
                        ["mov",    "__ mov(v1, __ T2S, 1, zr);",                         "mov\tv1.s[1], wzr"],
                        ["mov",    "__ mov(v1, __ T4H, 2, zr);",                         "mov\tv1.h[2], wzr"],
                        ["mov",    "__ mov(v1, __ T8B, 3, zr);",                         "mov\tv1.b[3], wzr"],
                        ["smov",   "__ smov(r0, v1, __ S, 0);",                          "smov\tx0, v1.s[0]"],
                        ["smov",   "__ smov(r0, v1, __ H, 1);",                          "smov\tx0, v1.h[1]"],
                        ["smov",   "__ smov(r0, v1, __ B, 2);",                          "smov\tx0, v1.b[2]"],
                        ["umov",   "__ umov(r0, v1, __ D, 0);",                          "umov\tx0, v1.d[0]"],
                        ["umov",   "__ umov(r0, v1, __ S, 1);",                          "umov\tw0, v1.s[1]"],
                        ["umov",   "__ umov(r0, v1, __ H, 2);",                          "umov\tw0, v1.h[2]"],
                        ["umov",   "__ umov(r0, v1, __ B, 3);",                          "umov\tw0, v1.b[3]"],
                        ["fmov",   "__ fmovhid(r0, v1);",                                "fmov\tx0, v1.d[1]"],
                        ["ld1",    "__ ld1(v31, v0, __ T2D, Address(__ post(r1, r0)));", "ld1\t{v31.2d, v0.2d}, [x1], x0"],
                        ["fcvtzs", "__ fcvtzs(v0, __ T4S, v1);",                         "fcvtzs\tv0.4s, v1.4s"],
                        # SVE instructions
                        ["cpy",     "__ sve_cpy(z0, __ S, p0, v1);",                      "mov\tz0.s, p0/m, s1"],
                        ["cpy",     "__ sve_cpy(z0, __ B, p0, 127, true);",               "mov\tz0.b, p0/m, 127"],
                        ["cpy",     "__ sve_cpy(z1, __ H, p0, -128, true);",              "mov\tz1.h, p0/m, -128"],
                        ["cpy",     "__ sve_cpy(z2, __ S, p0, 32512, true);",             "mov\tz2.s, p0/m, 32512"],
                        ["cpy",     "__ sve_cpy(z5, __ D, p0, -32768, false);",           "mov\tz5.d, p0/z, -32768"],
                        ["cpy",     "__ sve_cpy(z10, __ B, p0, -1, false);",              "mov\tz10.b, p0/z, -1"],
                        ["cpy",     "__ sve_cpy(z11, __ S, p0, -1, false);",              "mov\tz11.s, p0/z, -1"],
                        ["inc",     "__ sve_inc(r0, __ S);",                              "incw\tx0"],
                        ["dec",     "__ sve_dec(r1, __ H);",                              "dech\tx1"],
                        ["lsl",     "__ sve_lsl(z0, __ B, z1, 7);",                       "lsl\tz0.b, z1.b, #7"],
                        ["lsl",     "__ sve_lsl(z21, __ H, z1, 15);",                     "lsl\tz21.h, z1.h, #15"],
                        ["lsl",     "__ sve_lsl(z0, __ S, z1, 31);",                      "lsl\tz0.s, z1.s, #31"],
                        ["lsl",     "__ sve_lsl(z0, __ D, z1, 63);",                      "lsl\tz0.d, z1.d, #63"],
                        ["lsr",     "__ sve_lsr(z0, __ B, z1, 7);",                       "lsr\tz0.b, z1.b, #7"],
                        ["asr",     "__ sve_asr(z0, __ H, z11, 15);",                     "asr\tz0.h, z11.h, #15"],
                        ["lsr",     "__ sve_lsr(z30, __ S, z1, 31);",                     "lsr\tz30.s, z1.s, #31"],
                        ["asr",     "__ sve_asr(z0, __ D, z1, 63);",                      "asr\tz0.d, z1.d, #63"],
                        ["lsl",     "__ sve_lsl(z0, __ B, p0, 0);",                       "lsl\tz0.b, p0/m, z0.b, #0"],
                        ["lsl",     "__ sve_lsl(z0, __ B, p0, 5);",                       "lsl\tz0.b, p0/m, z0.b, #5"],
                        ["lsl",     "__ sve_lsl(z1, __ H, p1, 15);",                      "lsl\tz1.h, p1/m, z1.h, #15"],
                        ["lsl",     "__ sve_lsl(z2, __ S, p2, 31);",                      "lsl\tz2.s, p2/m, z2.s, #31"],
                        ["lsl",     "__ sve_lsl(z3, __ D, p3, 63);",                      "lsl\tz3.d, p3/m, z3.d, #63"],
                        ["lsr",     "__ sve_lsr(z0, __ B, p0, 1);",                       "lsr\tz0.b, p0/m, z0.b, #1"],
                        ["lsr",     "__ sve_lsr(z0, __ B, p0, 8);",                       "lsr\tz0.b, p0/m, z0.b, #8"],
                        ["lsr",     "__ sve_lsr(z1, __ H, p1, 15);",                      "lsr\tz1.h, p1/m, z1.h, #15"],
                        ["lsr",     "__ sve_lsr(z2, __ S, p2, 7);",                       "lsr\tz2.s, p2/m, z2.s, #7"],
                        ["lsr",     "__ sve_lsr(z2, __ S, p2, 31);",                      "lsr\tz2.s, p2/m, z2.s, #31"],
                        ["lsr",     "__ sve_lsr(z3, __ D, p3, 63);",                      "lsr\tz3.d, p3/m, z3.d, #63"],
                        ["asr",     "__ sve_asr(z0, __ B, p0, 1);",                       "asr\tz0.b, p0/m, z0.b, #1"],
                        ["asr",     "__ sve_asr(z0, __ B, p0, 7);",                       "asr\tz0.b, p0/m, z0.b, #7"],
                        ["asr",     "__ sve_asr(z1, __ H, p1, 5);",                       "asr\tz1.h, p1/m, z1.h, #5"],
                        ["asr",     "__ sve_asr(z1, __ H, p1, 15);",                      "asr\tz1.h, p1/m, z1.h, #15"],
                        ["asr",     "__ sve_asr(z2, __ S, p2, 31);",                      "asr\tz2.s, p2/m, z2.s, #31"],
                        ["asr",     "__ sve_asr(z3, __ D, p3, 63);",                      "asr\tz3.d, p3/m, z3.d, #63"],
                        ["addvl",   "__ sve_addvl(sp, r0, 31);",                          "addvl\tsp, x0, #31"],
                        ["addpl",   "__ sve_addpl(r1, sp, -32);",                         "addpl\tx1, sp, -32"],
                        ["cntp",    "__ sve_cntp(r8, __ B, p0, p1);",                     "cntp\tx8, p0, p1.b"],
                        ["dup",     "__ sve_dup(z0, __ B, 127);",                         "dup\tz0.b, 127"],
                        ["dup",     "__ sve_dup(z1, __ H, -128);",                        "dup\tz1.h, -128"],
                        ["dup",     "__ sve_dup(z2, __ S, 32512);",                       "dup\tz2.s, 32512"],
                        ["dup",     "__ sve_dup(z7, __ D, -32768);",                      "dup\tz7.d, -32768"],
                        ["dup",     "__ sve_dup(z10, __ B, -1);",                         "dup\tz10.b, -1"],
                        ["dup",     "__ sve_dup(z11, __ S, -1);",                         "dup\tz11.s, -1"],
                        ["ld1b",    "__ sve_ld1b(z0, __ B, p0, Address(sp));",            "ld1b\t{z0.b}, p0/z, [sp]"],
                        ["ld1b",    "__ sve_ld1b(z0, __ H, p1, Address(sp));",            "ld1b\t{z0.h}, p1/z, [sp]"],
                        ["ld1b",    "__ sve_ld1b(z0, __ S, p2, Address(sp, r8));",        "ld1b\t{z0.s}, p2/z, [sp, x8]"],
                        ["ld1b",    "__ sve_ld1b(z0, __ D, p3, Address(sp, 7));",         "ld1b\t{z0.d}, p3/z, [sp, #7, MUL VL]"],
                        ["ld1h",    "__ sve_ld1h(z10, __ H, p1, Address(sp, -8));",       "ld1h\t{z10.h}, p1/z, [sp, #-8, MUL VL]"],
                        ["ld1w",    "__ sve_ld1w(z20, __ S, p2, Address(r0, 7));",        "ld1w\t{z20.s}, p2/z, [x0, #7, MUL VL]"],
                        ["ld1b",    "__ sve_ld1b(z30, __ B, p3, Address(sp, r8));",       "ld1b\t{z30.b}, p3/z, [sp, x8]"],
                        ["ld1w",    "__ sve_ld1w(z0, __ S, p4, Address(sp, r28));",       "ld1w\t{z0.s}, p4/z, [sp, x28, LSL #2]"],
                        ["ld1d",    "__ sve_ld1d(z11, __ D, p5, Address(r0, r1));",       "ld1d\t{z11.d}, p5/z, [x0, x1, LSL #3]"],
                        ["st1b",    "__ sve_st1b(z22, __ B, p6, Address(sp));",           "st1b\t{z22.b}, p6, [sp]"],
                        ["st1b",    "__ sve_st1b(z31, __ B, p7, Address(sp, -8));",       "st1b\t{z31.b}, p7, [sp, #-8, MUL VL]"],
                        ["st1b",    "__ sve_st1b(z0, __ H, p1, Address(sp));",            "st1b\t{z0.h}, p1, [sp]"],
                        ["st1b",    "__ sve_st1b(z0, __ S, p2, Address(sp, r8));",        "st1b\t{z0.s}, p2, [sp, x8]"],
                        ["st1b",    "__ sve_st1b(z0, __ D, p3, Address(sp));",            "st1b\t{z0.d}, p3, [sp]"],
                        ["st1w",    "__ sve_st1w(z0, __ S, p1, Address(r0, 7));",         "st1w\t{z0.s}, p1, [x0, #7, MUL VL]"],
                        ["st1b",    "__ sve_st1b(z0, __ B, p2, Address(sp, r1));",        "st1b\t{z0.b}, p2, [sp, x1]"],
                        ["st1h",    "__ sve_st1h(z0, __ H, p3, Address(sp, r8));",        "st1h\t{z0.h}, p3, [sp, x8, LSL #1]"],
                        ["st1d",    "__ sve_st1d(z0, __ D, p4, Address(r0, r17));",       "st1d\t{z0.d}, p4, [x0, x17, LSL #3]"],
                        ["ldr",     "__ sve_ldr(z0, Address(sp));",                       "ldr\tz0, [sp]"],
                        ["ldr",     "__ sve_ldr(z31, Address(sp, -256));",                "ldr\tz31, [sp, #-256, MUL VL]"],
                        ["str",     "__ sve_str(z8, Address(r8, 255));",                  "str\tz8, [x8, #255, MUL VL]"],
                        ["cntb",    "__ sve_cntb(r9);",                                   "cntb\tx9"],
                        ["cnth",    "__ sve_cnth(r10);",                                  "cnth\tx10"],
                        ["cntw",    "__ sve_cntw(r11);",                                  "cntw\tx11"],
                        ["cntd",    "__ sve_cntd(r12);",                                  "cntd\tx12"],
                        ["brka",    "__ sve_brka(p2, p0, p2, false);",                    "brka\tp2.b, p0/z, p2.b"],
                        ["brka",    "__ sve_brka(p1, p2, p3, true);",                     "brka\tp1.b, p2/m, p3.b"],
                        ["brkb",    "__ sve_brkb(p1, p2, p3, false);",                    "brkb\tp1.b, p2/z, p3.b"],
                        ["brkb",    "__ sve_brkb(p2, p3, p4, true);",                     "brkb\tp2.b, p3/m, p4.b"],
                        ["rev",     "__ sve_rev(p0, __ B, p1);",                          "rev\tp0.b, p1.b"],
                        ["rev",     "__ sve_rev(p1, __ H, p2);",                          "rev\tp1.h, p2.h"],
                        ["rev",     "__ sve_rev(p2, __ S, p3);",                          "rev\tp2.s, p3.s"],
                        ["rev",     "__ sve_rev(p3, __ D, p4);",                          "rev\tp3.d, p4.d"],
                        ["incp",    "__ sve_incp(r0, __ B, p2);",                         "incp\tx0, p2.b"],
                        ["whilelt", "__ sve_whilelt(p0, __ B, r1, r28);",                 "whilelt\tp0.b, x1, x28"],
                        ["whilele", "__ sve_whilele(p2, __ H, r11, r8);",                 "whilele\tp2.h, x11, x8"],
                        ["whilelo", "__ sve_whilelo(p3, __ S, r7, r2);",                  "whilelo\tp3.s, x7, x2"],
                        ["whilels", "__ sve_whilels(p4, __ D, r17, r10);",                "whilels\tp4.d, x17, x10"],
                        ["sel",     "__ sve_sel(z0, __ B, p0, z1, z2);",                  "sel\tz0.b, p0, z1.b, z2.b"],
                        ["sel",     "__ sve_sel(z4, __ D, p0, z5, z6);",                  "sel\tz4.d, p0, z5.d, z6.d"],
                        ["cmpeq",   "__ sve_cmp(Assembler::EQ, p1, __ B, p0, z0, z1);",   "cmpeq\tp1.b, p0/z, z0.b, z1.b"],
                        ["cmpne",   "__ sve_cmp(Assembler::NE, p1, __ H, p0, z2, z3);",   "cmpne\tp1.h, p0/z, z2.h, z3.h"],
                        ["cmpge",   "__ sve_cmp(Assembler::GE, p1, __ S, p2, z4, z5);",   "cmpge\tp1.s, p2/z, z4.s, z5.s"],
                        ["cmpgt",   "__ sve_cmp(Assembler::GT, p1, __ D, p3, z6, z7);",   "cmpgt\tp1.d, p3/z, z6.d, z7.d"],
                        ["cmphi",   "__ sve_cmp(Assembler::HI, p1, __ S, p2, z4, z5);",   "cmphi\tp1.s, p2/z, z4.s, z5.s"],
                        ["cmphs",   "__ sve_cmp(Assembler::HS, p1, __ D, p3, z6, z7);",   "cmphs\tp1.d, p3/z, z6.d, z7.d"],
                        ["cmpeq",   "__ sve_cmp(Assembler::EQ, p1, __ B, p4, z0, 15);",   "cmpeq\tp1.b, p4/z, z0.b, #15"],
                        ["cmpne",   "__ sve_cmp(Assembler::NE, p1, __ H, p0, z2, -16);",  "cmpne\tp1.h, p0/z, z2.h, #-16"],
                        ["cmple",   "__ sve_cmp(Assembler::LE, p1, __ S, p1, z4, 0);",    "cmple\tp1.s, p1/z, z4.s, #0"],
                        ["cmplt",   "__ sve_cmp(Assembler::LT, p1, __ D, p2, z6, -1);",   "cmplt\tp1.d, p2/z, z6.d, #-1"],
                        ["cmpge",   "__ sve_cmp(Assembler::GE, p1, __ S, p3, z4, 5);",    "cmpge\tp1.s, p3/z, z4.s, #5"],
                        ["cmpgt",   "__ sve_cmp(Assembler::GT, p1, __ B, p4, z6, -2);",   "cmpgt\tp1.b, p4/z, z6.b, #-2"],
                        ["fcmeq",   "__ sve_fcm(Assembler::EQ, p1, __ S, p0, z0, z1);",   "fcmeq\tp1.s, p0/z, z0.s, z1.s"],
                        ["fcmne",   "__ sve_fcm(Assembler::NE, p1, __ D, p0, z2, z3);",   "fcmne\tp1.d, p0/z, z2.d, z3.d"],
                        ["fcmgt",   "__ sve_fcm(Assembler::GT, p1, __ S, p2, z4, z5);",   "fcmgt\tp1.s, p2/z, z4.s, z5.s"],
                        ["fcmge",   "__ sve_fcm(Assembler::GE, p1, __ D, p3, z6, z7);",   "fcmge\tp1.d, p3/z, z6.d, z7.d"],
                        ["uunpkhi", "__ sve_uunpkhi(z0, __ H, z1);",                      "uunpkhi\tz0.h, z1.b"],
                        ["uunpklo", "__ sve_uunpklo(z4, __ S, z5);",                      "uunpklo\tz4.s, z5.h"],
                        ["sunpkhi", "__ sve_sunpkhi(z6, __ D, z7);",                      "sunpkhi\tz6.d, z7.s"],
                        ["sunpklo", "__ sve_sunpklo(z10, __ H, z11);",                    "sunpklo\tz10.h, z11.b"],
                        ["scvtf",   "__ sve_scvtf(z1, __ D, p0, z0, __ S);",              "scvtf\tz1.d, p0/m, z0.s"],
                        ["scvtf",   "__ sve_scvtf(z3, __ D, p1, z2, __ D);",              "scvtf\tz3.d, p1/m, z2.d"],
                        ["scvtf",   "__ sve_scvtf(z6, __ S, p2, z1, __ D);",              "scvtf\tz6.s, p2/m, z1.d"],
                        ["scvtf",   "__ sve_scvtf(z6, __ S, p3, z1, __ S);",              "scvtf\tz6.s, p3/m, z1.s"],
                        ["scvtf",   "__ sve_scvtf(z6, __ H, p3, z1, __ S);",              "scvtf\tz6.h, p3/m, z1.s"],
                        ["scvtf",   "__ sve_scvtf(z6, __ H, p3, z1, __ D);",              "scvtf\tz6.h, p3/m, z1.d"],
                        ["scvtf",   "__ sve_scvtf(z6, __ H, p3, z1, __ H);",              "scvtf\tz6.h, p3/m, z1.h"],
                        ["fcvt",    "__ sve_fcvt(z5, __ D, p3, z4, __ S);",               "fcvt\tz5.d, p3/m, z4.s"],
                        ["fcvt",    "__ sve_fcvt(z1, __ S, p3, z0, __ D);",               "fcvt\tz1.s, p3/m, z0.d"],
                        ["fcvtzs",  "__ sve_fcvtzs(z19, __ D, p2, z1, __ D);",            "fcvtzs\tz19.d, p2/m, z1.d"],
                        ["fcvtzs",  "__ sve_fcvtzs(z9, __ S, p1, z8, __ S);",             "fcvtzs\tz9.s, p1/m, z8.s"],
                        ["fcvtzs",  "__ sve_fcvtzs(z1, __ S, p2, z0, __ D);",             "fcvtzs\tz1.s, p2/m, z0.d"],
                        ["fcvtzs",  "__ sve_fcvtzs(z1, __ D, p3, z0, __ S);",             "fcvtzs\tz1.d, p3/m, z0.s"],
                        ["fcvtzs",  "__ sve_fcvtzs(z1, __ S, p4, z18, __ H);",            "fcvtzs\tz1.s, p4/m, z18.h"],
                        ["lasta",   "__ sve_lasta(r0, __ B, p0, z15);",                   "lasta\tw0, p0, z15.b"],
                        ["lastb",   "__ sve_lastb(r1, __ B, p1, z16);",                   "lastb\tw1, p1, z16.b"],
                        ["lasta",   "__ sve_lasta(v0, __ B, p0, z15);",                   "lasta\tb0, p0, z15.b"],
                        ["lastb",   "__ sve_lastb(v1, __ B, p1, z16);",                   "lastb\tb1, p1, z16.b"],
                        ["index",   "__ sve_index(z6, __ S, 1, 1);",                      "index\tz6.s, #1, #1"],
                        ["cpy",     "__ sve_cpy(z7, __ H, p3, r5);",                      "cpy\tz7.h, p3/m, w5"],
                        ["tbl",     "__ sve_tbl(z16, __ S, z17, z18);",                   "tbl\tz16.s, {z17.s}, z18.s"],
                        ["ld1w",    "__ sve_ld1w_gather(z15, p0, r5, z16);",              "ld1w\t{z15.s}, p0/z, [x5, z16.s, uxtw #2]"],
                        ["ld1d",    "__ sve_ld1d_gather(z15, p0, r5, z16);",              "ld1d\t{z15.d}, p0/z, [x5, z16.d, uxtw #3]"],
                        ["st1w",    "__ sve_st1w_scatter(z15, p0, r5, z16);",             "st1w\t{z15.s}, p0, [x5, z16.s, uxtw #2]"],
                        ["st1d",    "__ sve_st1d_scatter(z15, p0, r5, z16);",             "st1d\t{z15.d}, p0, [x5, z16.d, uxtw #3]"],
                        ["and",     "__ sve_and(p0, p1, p2, p3);",                        "and\tp0.b, p1/z, p2.b, p3.b"],
                        ["ands",    "__ sve_ands(p4, p5, p6, p0);",                       "ands\tp4.b, p5/z, p6.b, p0.b"],
                        ["eor",     "__ sve_eor(p0, p1, p2, p3);",                        "eor\tp0.b, p1/z, p2.b, p3.b"],
                        ["eors",    "__ sve_eors(p5, p6, p0, p1);",                       "eors\tp5.b, p6/z, p0.b, p1.b"],
                        ["orr",     "__ sve_orr(p0, p1, p2, p3);",                        "orr\tp0.b, p1/z, p2.b, p3.b"],
                        ["orrs",    "__ sve_orrs(p9, p1, p4, p5);",                       "orrs\tp9.b, p1/z, p4.b, p5.b"],
                        ["bic",     "__ sve_bic(p10, p7, p9, p11);",                      "bic\tp10.b, p7/z, p9.b, p11.b"],
                        ["ptest",   "__ sve_ptest(p7, p1);",                              "ptest\tp7, p1.b"],
                        ["ptrue",   "__ sve_ptrue(p1, __ B);",                            "ptrue\tp1.b"],
                        ["ptrue",   "__ sve_ptrue(p2, __ H);",                            "ptrue\tp2.h"],
                        ["ptrue",   "__ sve_ptrue(p3, __ S);",                            "ptrue\tp3.s"],
                        ["ptrue",   "__ sve_ptrue(p4, __ D);",                            "ptrue\tp4.d"],
                        ["pfalse",  "__ sve_pfalse(p7);",                                 "pfalse\tp7.b"],
                        ["uzp1",    "__ sve_uzp1(p0, __ B, p0, p1);",                     "uzp1\tp0.b, p0.b, p1.b"],
                        ["uzp1",    "__ sve_uzp1(p0, __ H, p0, p1);",                     "uzp1\tp0.h, p0.h, p1.h"],
                        ["uzp1",    "__ sve_uzp1(p0, __ S, p0, p1);",                     "uzp1\tp0.s, p0.s, p1.s"],
                        ["uzp1",    "__ sve_uzp1(p0, __ D, p0, p1);",                     "uzp1\tp0.d, p0.d, p1.d"],
                        ["uzp2",    "__ sve_uzp2(p0, __ B, p0, p1);",                     "uzp2\tp0.b, p0.b, p1.b"],
                        ["uzp2",    "__ sve_uzp2(p0, __ H, p0, p1);",                     "uzp2\tp0.h, p0.h, p1.h"],
                        ["uzp2",    "__ sve_uzp2(p0, __ S, p0, p1);",                     "uzp2\tp0.s, p0.s, p1.s"],
                        ["uzp2",    "__ sve_uzp2(p0, __ D, p0, p1);",                     "uzp2\tp0.d, p0.d, p1.d"],
                        ["punpklo", "__ sve_punpklo(p1, p0);",                            "punpklo\tp1.h, p0.b"],
                        ["punpkhi", "__ sve_punpkhi(p1, p0);",                            "punpkhi\tp1.h, p0.b"],
                        ["compact", "__ sve_compact(z16, __ S, z16, p1);",                "compact\tz16.s, p1, z16.s"],
                        ["compact", "__ sve_compact(z16, __ D, z16, p1);",                "compact\tz16.d, p1, z16.d"],
])

print "\n// FloatImmediateOp"
for float in ("2.0", "2.125", "4.0", "4.25", "8.0", "8.5", "16.0", "17.0", "0.125",
              "0.1328125", "0.25", "0.265625", "0.5", "0.53125", "1.0", "1.0625",
              "-2.0", "-2.125", "-4.0", "-4.25", "-8.0", "-8.5", "-16.0", "-17.0",
              "-0.125", "-0.1328125", "-0.25", "-0.265625", "-0.5", "-0.53125", "-1.0", "-1.0625"):
    astr = "fmov d0, #" + float
    cstr = "__ fmovd(v0, " + float + ");"
    print "    %-50s //\t%s" % (cstr, astr)
    outfile.write("\t" + astr + "\n")

# ARMv8.1A
for size in ("x", "w"):
    for suffix in ("", "a", "al", "l"):
        generate(LSEOp, [["swp", "swp", size, suffix],
                         ["ldadd", "ldadd", size, suffix],
                         ["ldbic", "ldclr", size, suffix],
                         ["ldeor", "ldeor", size, suffix],
                         ["ldorr", "ldset", size, suffix],
                         ["ldsmin", "ldsmin", size, suffix],
                         ["ldsmax", "ldsmax", size, suffix],
                         ["ldumin", "ldumin", size, suffix],
                         ["ldumax", "ldumax", size, suffix]]);

# ARMv8.2A
generate(SHA3SIMDOp, ["bcax", "eor3", "rax1", "xar"])

generate(SHA512SIMDOp, ["sha512h", "sha512h2", "sha512su0", "sha512su1"])

for i in range(6):
    generate(SVEBinaryImmOp, ["add", "sub", "and", "eor", "orr"])

generate(SVEVectorOp, [["add", "ZZZ"],
                       ["sub", "ZZZ"],
                       ["fadd", "ZZZ"],
                       ["fmul", "ZZZ"],
                       ["fsub", "ZZZ"],
                       ["abs", "ZPZ", "m"],
                       ["add", "ZPZ", "m", "dn"],
                       ["and", "ZPZ", "m", "dn"],
                       ["asr", "ZPZ", "m", "dn"],
                       ["cnt", "ZPZ", "m"],
                       ["eor", "ZPZ", "m", "dn"],
                       ["lsl", "ZPZ", "m", "dn"],
                       ["lsr", "ZPZ", "m", "dn"],
                       ["mul", "ZPZ", "m", "dn"],
                       ["neg", "ZPZ", "m"],
                       ["not", "ZPZ", "m"],
                       ["orr", "ZPZ", "m", "dn"],
                       ["smax", "ZPZ", "m", "dn"],
                       ["smin", "ZPZ", "m", "dn"],
                       ["sub", "ZPZ", "m", "dn"],
                       ["fabs", "ZPZ", "m"],
                       ["fadd", "ZPZ", "m", "dn"],
                       ["fdiv", "ZPZ", "m", "dn"],
                       ["fmax", "ZPZ", "m", "dn"],
                       ["fmin", "ZPZ", "m", "dn"],
                       ["fmul", "ZPZ", "m", "dn"],
                       ["fneg", "ZPZ", "m"],
                       ["frintm", "ZPZ", "m"],
                       ["frintn", "ZPZ", "m"],
                       ["frintp", "ZPZ", "m"],
                       ["fsqrt", "ZPZ", "m"],
                       ["fsub", "ZPZ", "m", "dn"],
                       ["fmad", "ZPZZ", "m"],
                       ["fmla", "ZPZZ", "m"],
                       ["fmls", "ZPZZ", "m"],
                       ["fnmla", "ZPZZ", "m"],
                       ["fnmls", "ZPZZ", "m"],
                       ["mla", "ZPZZ", "m"],
                       ["mls", "ZPZZ", "m"],
                       ["and", "ZZZ"],
                       ["eor", "ZZZ"],
                       ["orr", "ZZZ"],
                       ["bic", "ZZZ"],
                       ["uzp1", "ZZZ"],
                       ["uzp2", "ZZZ"],
                      ])

generate(SVEReductionOp, [["andv", 0], ["orv", 0], ["eorv", 0], ["smaxv", 0], ["sminv", 0],
                          ["fminv", 2], ["fmaxv", 2], ["fadda", 2], ["uaddv", 0]])

print "\n    __ bind(forth);"
outfile.write("forth:\n")

outfile.close()

# compile for sve with 8.2 and sha3 because of SHA3 crypto extension.
subprocess.check_call([AARCH64_AS, "-march=armv8.2-a+sha3+sve", "aarch64ops.s", "-o", "aarch64ops.o"])

print
print "/*"
print "*/"

subprocess.check_call([AARCH64_OBJCOPY, "-O", "binary", "-j", ".text", "aarch64ops.o", "aarch64ops.bin"])

infile = open("aarch64ops.bin", "r")
bytes = bytearray(infile.read())

print
print "  static const unsigned int insns[] ="
print "  {"

i = 0
while i < len(bytes):
     print "    0x%02x%02x%02x%02x," % (bytes[i+3], bytes[i+2], bytes[i+1], bytes[i]),
     i += 4
     if i%16 == 0:
          print
print
print "  };"
print "// END  Generated code -- do not edit"

infile.close()

for f in ["aarch64ops.s", "aarch64ops.o", "aarch64ops.bin"]:
    os.remove(f)
