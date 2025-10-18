#include "asm/macroAssembler.hpp"
#include "compiler/disassembler.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/globals_extension.hpp"
#include "unittest.hpp"

#if defined(AARCH64) && !defined(ZERO)
#include <chrono>

static void dump_code(address start, address end) {
  ResourceMark resMark;
  stringStream sstream;
  Disassembler::decode(start, end, &sstream);
  printf("%s\n", sstream.as_string());
}

class MacroAssemblerZeroWordsTest : public ::testing::Test {
public:
  MacroAssemblerZeroWordsTest() { }
  ~MacroAssemblerZeroWordsTest() { }

  // Measure wall time of MacroAssembler::zero_words for different sizes.
  // Sizes are count of words for clearing and could be:
  // 4 for 32B ( 32B / 8B-per-word = 4 words)
  // 16 for 128B (128B / 8B-per-word = 16 words)
  // 64 for 512B (512B / 8B-per-word = 64 words)
  static void benchmark_zero_words(uint clear_words_count) {
    BufferBlob* blob = BufferBlob::create("zero_words_test", 200000);
    CodeBuffer code(blob);
    MacroAssembler _masm(&code);

    const uint call_count = 1000;
    const uint word_count = clear_words_count;
    uint64_t* buffer = new uint64_t[word_count];
    Register base = r10;
    uint cnt = word_count;
    // Set up base register to point to buffer
    _masm.mov(base, (uintptr_t)buffer);

    _masm.zero_words(base, cnt);
    dump_code(code.insts()->start(), code.insts()->end());

    auto start = std::chrono::steady_clock::now();
    for (uint i = 0; i < call_count; ++i) {
        _masm.zero_words(base, cnt);
    }
    auto end = std::chrono::steady_clock::now();
    auto wall_time_ns = std::chrono::duration_cast<std::chrono::nanoseconds>(end - start).count();
    printf("Clear %u words with lower limit %lu, zero_words wall time (ns): %lu\n",
           word_count,
           static_cast<unsigned long>(BlockZeroingLowLimit),
           static_cast<unsigned long>(wall_time_ns / call_count));

    delete[] buffer;
    BufferBlob::free(blob);
  }
};

// If necessary, UseBlockZeroing should be configured during JVM initialization.
// However, here it is not required to specify it explicitly via TEST_VM_OPTS
// because MacroAssembler::zero_words does not check the UseBlockZeroing flag.
// In contrast, the stub functions for AArch64, such as generate_zero_blocks, do perform this check.

TEST_VM_F(MacroAssemblerZeroWordsTest, UseBZ_clear_32B_with_lowlimit_8B) {
  FLAG_SET_CMDLINE(BlockZeroingLowLimit, 8);
  benchmark_zero_words(4); // 32B
}
// JDK-8365991 updates the default value of BlockZeroingLowLimit to 256 bytes
// when UseBlockZeroing is set to false. As a result, if a smaller low limit (e.g., 8 bytes)
// was previously configured, the generated code for clearing 32 bytes would no longer use zero_blocks_stub.
// Instead, a simpler sequence of instructions can be produced by applying the 256-byte low limit.
//
// Measuring the wall-clock time difference for invocations of MacroAssembler::zero_words
// can help evaluate the performance impact of proactively adjusting the BlockZeroingLowLimit.
TEST_VM_F(MacroAssemblerZeroWordsTest, UseBZ_clear_32B_with_lowlimit_256B) {
  FLAG_SET_CMDLINE(BlockZeroingLowLimit, 256);
  benchmark_zero_words(4); // 32B
}

TEST_VM_F(MacroAssemblerZeroWordsTest, UseBZ_clear_128B_with_lowlimit_64B) {
  FLAG_SET_CMDLINE(BlockZeroingLowLimit, 64);
  benchmark_zero_words(16); // 128B
}

// JDK-8365991 updates BlockZeroingLowLimit from 64B to 256B when UseBlockZeroing is false,
// which also improves the efficiency of generating the code for clearing 128-byte memory blocks.
TEST_VM_F(MacroAssemblerZeroWordsTest, UseBZ_clear_128B_with_lowlimit_256B) {
  FLAG_SET_CMDLINE(BlockZeroingLowLimit, 256);
  benchmark_zero_words(16); // 128B
}

#endif  // AARCH64 && !ZERO