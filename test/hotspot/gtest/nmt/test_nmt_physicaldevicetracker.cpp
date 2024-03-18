#include "precompiled.hpp"
#include "memory/allocation.hpp"
#include "nmt/vmatree.hpp"
#include "nmt/memTracker.hpp"
#include "runtime/os.hpp"
#include "unittest.hpp"

class PhysicalDeviceTrackerTest : public testing::Test {
public:
  size_t sz(int x) { return (size_t) x; }
  void basics() {
    PhysicalDeviceTracker tracker(false);
    PhysicalDeviceTracker::MemoryFile* dev = tracker.make_device("test");
    tracker.allocate_memory(dev, 0, 100, mtTest, CALLER_PC);
    EXPECT_EQ(dev->_summary.by_type(mtTest)->reserved(), sz(100));
    tracker.allocate_memory(dev, 100, 100, mtTest, CALLER_PC);
    EXPECT_EQ(dev->_summary.by_type(mtTest)->reserved(), sz(200));
    tracker.allocate_memory(dev, 200, 100, mtTest, CALLER_PC);
    EXPECT_EQ(dev->_summary.by_type(mtTest)->reserved(), sz(300));
    tracker.free_memory(dev, 0, 300);
    EXPECT_EQ(dev->_summary.by_type(mtTest)->reserved(), sz(0));
    tracker.allocate_memory(dev, 0, 100, mtTest, CALLER_PC);
    EXPECT_EQ(dev->_summary.by_type(mtTest)->reserved(), sz(100));
    tracker.free_memory(dev, 50, 10);
    EXPECT_EQ(dev->_summary.by_type(mtTest)->reserved(), sz(90));
  };
};

TEST_VM_F(PhysicalDeviceTrackerTest, Basics) {
  this->basics();
}
