import random

AARCH64_AS = "<PATH-TO-AS>"
AARCH64_OBJDUMP = "<PATH-TO-OBJDUMP>"
AARCH64_OBJCOPY = "<PATH-TO-OBJCOPY>"

class Operand(object):

     def generate(self):
        return self

class Register(Operand):

    def generate(self):
        self.number = random.randint(0, 30)
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

class FloatZero(Operand):

    def __str__(self):
        return "0.0"

    def astr(self, ignored):
        return "#0.0"

class OperandFactory:

    _modes = {'x' : GeneralRegister,
              'w' : GeneralRegister,
              's' : FloatRegister,
              'd' : FloatRegister,
              'z' : FloatZero}

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

     # These tables are legal immediate logical operands
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

     immediates \
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

     def generate(self):
          AddSubImmOp.generate(self)
          self.immed = \
              self.immediates32[random.randint(0, len(self.immediates32)-1)] \
              	if self.isWord \
              else \
              	self.immediates[random.randint(0, len(self.immediates)-1)]
              
          return self
                  
     def astr(self):
          return (super(TwoRegImmedInstruction, self).astr()
                  + ', #0x%x' % self.immed)

     def cstr(self):
          return super(AddSubImmOp, self).cstr() + "l);"
    
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

class LdStSIMDOp(Instruction):
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
        buf = super(LdStSIMDOp, self).cstr() + str(self._firstSIMDreg)
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

generate (ImmOp, ["svc", "hvc", "smc", "brk", "hlt", # "dpcs1",  "dpcs2",  "dpcs3"
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
    print "\n// " + Address.kindToStr(kind),
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
         [["fmuls", "sss"], ["fdivs", "sss"], ["fadds", "sss"], ["fsubs", "sss"], 
          ["fmuls", "sss"],
          ["fmuld", "ddd"], ["fdivd", "ddd"], ["faddd", "ddd"], ["fsubd", "ddd"], 
          ["fmuld", "ddd"]])

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

generate(LdStSIMDOp, [["ld1",  1, "8B",  Address.base_only],
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
                        ["mov",    "__ mov(v1, __ T1D, 0, zr);",                         "mov\tv1.d[0], xzr"],
                        ["mov",    "__ mov(v1, __ T2S, 1, zr);",                         "mov\tv1.s[1], wzr"],
                        ["mov",    "__ mov(v1, __ T4H, 2, zr);",                         "mov\tv1.h[2], wzr"],
                        ["mov",    "__ mov(v1, __ T8B, 3, zr);",                         "mov\tv1.b[3], wzr"],
                        ["ld1",    "__ ld1(v31, v0, __ T2D, Address(__ post(r1, r0)));", "ld1\t{v31.2d, v0.2d}, [x1], x0"]])

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

print "\n    __ bind(forth);"
outfile.write("forth:\n")

outfile.close()

import subprocess
import sys

# compile for 8.1 because of lse atomics
subprocess.check_call([AARCH64_AS, "-march=armv8.1-a", "aarch64ops.s", "-o", "aarch64ops.o"])

print
print "/*",
sys.stdout.flush()
subprocess.check_call([AARCH64_OBJDUMP, "-d", "aarch64ops.o"])
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
print "\n  };"
print "// END  Generated code -- do not edit"


