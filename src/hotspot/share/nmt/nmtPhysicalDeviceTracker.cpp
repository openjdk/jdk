#include "precompiled.hpp"

#include "memory/allocation.hpp"
#include "nmt/nmtNativeCallStackStorage.hpp"
#include "nmt/nmtCommon.hpp"
#include "nmt/vmatree.hpp"
#include "nmt/nmtPhysicalDeviceTracker.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/nativeCallStack.hpp"
#include "utilities/ostream.hpp"

PhysicalDeviceTracker* PhysicalDeviceTracker::Instance::_tracker = nullptr;

PhysicalDeviceTracker::PhysicalDeviceTracker(bool is_detailed_mode)
: _stack_storage(is_detailed_mode), _devices() {
}

void PhysicalDeviceTracker::allocate_memory(PhysicalDevice* device, size_t offset,
                                            size_t size, MEMFLAGS flag,
                                            const NativeCallStack& stack) {
  NativeCallStackStorage::StackIndex sidx = _stack_storage.push(stack);
  DeviceSpace::Metadata metadata(sidx, flag);
  DeviceSpace::SummaryDiff diff = device->_tree.reserve_mapping(offset, size, metadata);
  for (int i = 0; i < mt_number_of_types; i++) {
    const VMATree::SingleDiff& rescom = diff.flag[i];
    VirtualMemory* summary = device->_summary.by_type(NMTUtil::index_to_flag(i));
    summary->reserve_memory(rescom.reserve);
  }
}

void PhysicalDeviceTracker::free_memory(PhysicalDevice* device, size_t offset, size_t size) {
  DeviceSpace::SummaryDiff diff = device->_tree.release_mapping(offset, size);
  for (int i = 0; i < mt_number_of_types; i++) {
    const VMATree::SingleDiff& rescom = diff.flag[i];
    VirtualMemory* summary = device->_summary.by_type(NMTUtil::index_to_flag(i));
    summary->reserve_memory(rescom.reserve);
  }
}

void PhysicalDeviceTracker::print_report_on(const PhysicalDevice* device, outputStream* stream, size_t scale) {
  stream->print_cr("Memory map of %s", device->_descriptive_name);
  const VMATree::VTreap* prev = nullptr;
  device->_tree.in_order_traversal([&](const VMATree::VTreap* current) {
    if (prev == nullptr) {
      // Must be first node.
      prev = current;
      return;
    }
    const auto& pval = prev->val();
    const auto& cval = current->val();
    assert(pval.out == cval.in, "must be");
    if (pval.out == VMATree::InOut::Reserved) {
      const auto& start_addr = prev->key();
      const auto& end_addr = current->key();
      stream->print_cr("[" PTR_FORMAT " - " PTR_FORMAT "] allocated " SIZE_FORMAT "%s" " bytes for %s", start_addr, end_addr,
                       NMTUtil::amount_in_scale(end_addr - start_addr, scale),
                       NMTUtil::scale_name(scale),
                       NMTUtil::flag_to_name(pval.metadata.flag));
      pval.metadata.stack_idx.stack().print_on(stream, 4);
    }
    prev = current;
  });
}

PhysicalDeviceTracker::PhysicalDevice* PhysicalDeviceTracker::make_device(const char* descriptive_name) {
  PhysicalDevice* device_place = new PhysicalDevice{descriptive_name};
  _devices.push(device_place);
  return device_place;
}

void PhysicalDeviceTracker::free_device(PhysicalDevice* device) {
  _devices.remove(device);
  delete device;
}

const GrowableArrayCHeap<PhysicalDeviceTracker::PhysicalDevice*, mtNMT>& PhysicalDeviceTracker::devices() {
  return _devices;
}
const VirtualMemorySnapshot& PhysicalDeviceTracker::summary_for(const PhysicalDevice* device) {
  return device->_summary;
}


bool PhysicalDeviceTracker::Instance::initialize(NMT_TrackingLevel tracking_level) {
  if (tracking_level == NMT_TrackingLevel::NMT_off) return true;
  _tracker = static_cast<PhysicalDeviceTracker*>(os::malloc(sizeof(PhysicalDeviceTracker), mtNMT));
  if (_tracker == nullptr) return false;
  new (_tracker) PhysicalDeviceTracker(tracking_level == NMT_TrackingLevel::NMT_detail);
  return true;
}
void PhysicalDeviceTracker::Instance::allocate_memory(PhysicalDevice* device, size_t offset,
                                                      size_t size, MEMFLAGS flag,
                                                      const NativeCallStack& stack) {
  _tracker->allocate_memory(device, offset, size, flag, stack);
}

void PhysicalDeviceTracker::Instance::free_memory(PhysicalDevice* device, size_t offset,
                                                  size_t size) {
  _tracker->free_memory(device, offset, size);
}

PhysicalDeviceTracker::PhysicalDevice*
PhysicalDeviceTracker::Instance::make_device(const char* descriptive_name) {
  return _tracker->make_device(descriptive_name);
}

void PhysicalDeviceTracker::Instance::print_report_on(const PhysicalDevice* device,
                                                      outputStream* stream, size_t scale) {
  _tracker->print_report_on(device, stream, scale);
}

const GrowableArrayCHeap<PhysicalDeviceTracker::PhysicalDevice*, mtNMT>& PhysicalDeviceTracker::Instance::devices() {
  return _tracker->devices();
};
void PhysicalDeviceTracker::summary_snapshot(VirtualMemorySnapshot* snapshot) const {
  for (int d = 0; d < _devices.length(); d++) {
    auto& device = _devices.at(d);
    for (int i = 0; i < mt_number_of_types; i++) {
      auto snap = snapshot->by_type(NMTUtil::index_to_flag(i));
      auto current = device->_summary.by_type(NMTUtil::index_to_flag(i));
      // PDT stores the memory as reserved but it's accounted as committed.
      snap->commit_memory(current->reserved());
    }
  }
}
void PhysicalDeviceTracker::Instance::summary_snapshot(VirtualMemorySnapshot* snapshot) {
  _tracker->summary_snapshot(snapshot);
}
