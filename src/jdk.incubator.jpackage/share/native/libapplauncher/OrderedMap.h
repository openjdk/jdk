/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef ORDEREDMAP_H
#define ORDEREDMAP_H

#include <map>
#include <vector>
#include <assert.h>
#include <stdexcept>

#include <iostream>

template <typename _T1, typename _T2>
struct JPPair
{
    typedef _T1 first_type;
    typedef _T2 second_type;

    first_type first;
    second_type second;

    JPPair(first_type Value1, second_type Value2) {
        first = Value1;
        second = Value2;
    }
};


template <typename TKey, typename TValue>
class OrderedMap {
public:
    typedef TKey key_type;
    typedef TValue mapped_type;
    typedef JPPair<key_type, mapped_type> container_type;
    typedef typename std::vector<container_type*>::iterator iterator;
    typedef typename std::vector<container_type*>::const_iterator const_iterator;

private:
    typedef std::map<key_type, container_type*> map_type;
    typedef std::vector<container_type*> list_type;

    map_type FMap;
    list_type FList;
    bool FAllowDuplicates;

    typename list_type::iterator FindListItem(const key_type Key) {
        typename list_type::iterator result = FList.end();

        for (typename list_type::iterator iterator =
                FList.begin(); iterator != FList.end(); iterator++) {
            container_type *item = *iterator;

            if (item->first == Key) {
                result = iterator;
                break;
            }
        }

        return result;
    }

public:
    OrderedMap() {
        FAllowDuplicates = false;
    }

    OrderedMap(const OrderedMap<key_type, mapped_type> &Value) {
        Append(Value);
        FAllowDuplicates = Value.GetAllowDuplicates();
    }

    ~OrderedMap() {
        Clear();
    }

    void SetAllowDuplicates(bool Value) {
        FAllowDuplicates = Value;
    }

    bool GetAllowDuplicates() const {
        return FAllowDuplicates;
    }

    iterator begin() {
        return FList.begin();
    }

    const_iterator begin() const {
        return FList.begin();
    }

    iterator end() {
        return FList.end();
    }

    const_iterator end() const {
        return FList.end();
    }

    void Clear() {
        for (typename list_type::iterator iterator =
                FList.begin(); iterator != FList.end(); iterator++) {
            container_type *item = *iterator;

            if (item != NULL) {
                delete item;
                item = NULL;
            }
        }

        FMap.clear();
        FList.clear();
    }

    bool ContainsKey(key_type Key) {
        bool result = false;

        if (FMap.find(Key) != FMap.end()) {
            result = true;
        }

        return result;
    }

    std::vector<key_type> GetKeys() {
        std::vector<key_type> result;

        for (typename list_type::const_iterator iterator = FList.begin();
             iterator != FList.end(); iterator++) {
            container_type *item = *iterator;
            result.push_back(item->first);
        }

        return result;
    }

    void Assign(const OrderedMap<key_type, mapped_type> &Value) {
        Clear();
        Append(Value);
    }

    void Append(const OrderedMap<key_type, mapped_type> &Value) {
        for (size_t index = 0; index < Value.FList.size(); index++) {
            container_type *item = Value.FList[index];
            Append(item->first, item->second);
        }
    }

    void Append(key_type Key, mapped_type Value) {
        container_type *item = new container_type(Key, Value);
        FMap.insert(std::pair<key_type, container_type*>(Key, item));
        FList.push_back(item);
    }

    bool RemoveByKey(key_type Key) {
        bool result = false;
        typename list_type::iterator iterator = FindListItem(Key);

        if (iterator != FList.end()) {
            FMap.erase(Key);
            FList.erase(iterator);
            result = true;
        }

        return result;
    }

    bool GetValue(key_type Key, mapped_type &Value) {
        bool result = false;
        container_type* item = FMap[Key];

        if (item != NULL) {
            Value = item->second;
            result = true;
        }

        return result;
    }

    bool SetValue(key_type Key, mapped_type &Value) {
        bool result = false;

        if ((FAllowDuplicates == false) && (ContainsKey(Key) == true)) {
            container_type *item = FMap[Key];

            if (item != NULL) {
                item->second = Value;
                result = true;
            }
        }
        else {
            Append(Key, Value);
            result = true;
        }

        return result;
    }

    bool GetKey(int index, key_type &Value) {
        if (index < 0 || index >= (int)FList.size()) {
            return false;
        }
        container_type *item = FList.at(index);
        if (item != NULL) {
            Value = item->first;
            return true;
        }

        return false;
    }

    bool GetValue(int index, mapped_type &Value) {
        if (index < 0 || index >= (int)FList.size()) {
            return false;
        }
        container_type *item = FList.at(index);
        if (item != NULL) {
            Value = item->second;
            return true;
        }

        return false;
    }

    mapped_type &operator[](key_type Key) {
        container_type* item = FMap[Key];
        assert(item != NULL);

        if (item != NULL) {
            return item->second;
        }

        throw std::invalid_argument("Key not found");
    }

    OrderedMap& operator= (OrderedMap &Value) {
        Clear();
        FAllowDuplicates = Value.GetAllowDuplicates();
        Append(Value);
        return *this;
    }

    size_t Count() {
        return FList.size();
    }
};

#endif // ORDEREDMAP_H
