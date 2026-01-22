/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 *
 */

#include "ci/ciArray.hpp"
#include "ci/ciConstant.hpp"
#include "ci/ciField.hpp"
#include "ci/ciFlatArray.hpp"
#include "ci/ciInlineKlass.hpp"
#include "ci/ciUtilities.inline.hpp"
#include "oops/oop.inline.hpp"

ciConstant ciFlatArray::null_marker_of_element_by_offset_impl(arrayOop ary, int index) {
  if (ary == nullptr) {
    return ciConstant();
  }
  assert(ary->is_array(), "");
  if (index < 0 || index >= ary->length()) {
    return ciConstant();
  }
    assert(ary->is_objArray(), "");
    flatArrayOop objary = (flatArrayOop) ary;
    jboolean elem = objary->null_marker_of_obj_at(index);
    return ciConstant(T_BOOLEAN, elem);
}

ciConstant ciFlatArray::check_constant_null_marker_cache(int off) {
  if (_constant_null_markers != nullptr) {
    for (int i = 0; i < _constant_null_markers->length(); ++i) {
      ConstantValue cached_val = _constant_null_markers->at(i);
      if (cached_val.off() == off) {
        return cached_val.value();
      }
    }
  }
  return ciConstant();
}

void ciFlatArray::add_to_constant_null_marker_cache(int off, ciConstant val) {
  assert(val.is_valid(), "value must be valid");
  assert(!check_constant_null_marker_cache(off).is_valid(), "duplicate");
  if (_constant_null_markers == nullptr) {
    Arena* arena = CURRENT_ENV->arena();
    _constant_null_markers = new (arena) GrowableArray<ConstantValue>(arena, 1, 0, ConstantValue());
  }
  _constant_null_markers->append(ConstantValue(off, val));
}

// Current value of an element.
// Returns T_ILLEGAL if there is no element at the given index.
ciConstant ciFlatArray::null_marker_of_element_by_index(int index) {
  ciConstant value = check_constant_null_marker_cache(index);
  if (value.is_valid()) {
    return value;
  }
  GUARDED_VM_ENTRY(
      value = null_marker_of_element_by_offset_impl(get_arrayOop(), index);)
  add_to_constant_null_marker_cache(index, value);
  return value;
}

ciConstant ciFlatArray::null_marker_of_element_by_offset(intptr_t element_offset) {
  FlatArrayKlass* faklass;
  GUARDED_VM_ENTRY(faklass = FlatArrayKlass::cast(get_arrayOop()->klass());)
  int lh = faklass->layout_helper();
  int shift = Klass::layout_helper_log2_element_size(lh);
  intptr_t header = arrayOopDesc::base_offset_in_bytes(T_FLAT_ELEMENT);
  intptr_t index = (element_offset - header) >> shift;
  intptr_t offset = header + (index << shift);
  if (offset != element_offset || index != (jint) index || index < 0 || index >= length()) {
    return ciConstant();
  }
  return null_marker_of_element_by_index((jint) index);
}

ciConstant ciFlatArray::element_value_by_offset(intptr_t element_offset) {
  FlatArrayKlass* faklass;
  GUARDED_VM_ENTRY(faklass = FlatArrayKlass::cast(get_arrayOop()->klass());)
  int lh = faklass->layout_helper();
  int shift = Klass::layout_helper_log2_element_size(lh);
  intptr_t header = arrayOopDesc::base_offset_in_bytes(T_FLAT_ELEMENT);
  intptr_t index = (element_offset - header) >> shift;
  intptr_t offset = header + (index << shift);
  if (offset != element_offset || index != (jint) index || index < 0 || index >= length()) {
    return ciConstant();
  }
  return element_value((jint) index);
}

ciConstant ciFlatArray::field_value_by_offset(intptr_t field_offset) {
  ciInlineKlass* elt_type = element_type()->as_inline_klass();
  FlatArrayKlass* faklass;
  GUARDED_VM_ENTRY(faklass = FlatArrayKlass::cast(get_arrayOop()->klass());)
  int lh = faklass->layout_helper();
  int shift = Klass::layout_helper_log2_element_size(lh);
  intptr_t header = arrayOopDesc::base_offset_in_bytes(T_FLAT_ELEMENT);
  intptr_t index = (field_offset - header) >> shift;
  intptr_t element_offset = header + (index << shift);
  int field_offset_in_element = (int)(field_offset - element_offset);
  ciField* field = elt_type->get_field_by_offset(elt_type->payload_offset() + field_offset_in_element, false);
  if (field == nullptr) {
    if (field_offset_in_element != elt_type->null_marker_offset_in_payload()) {
      return ciConstant();
    }
  }

  if (index != (jint) index || index < 0 || index >= length()) {
    return ciConstant();
  }
  ciConstant elt = field_value((jint) index, field);

  return elt;
}

ciConstant ciFlatArray::field_value_impl(int index, ciField* field) {
  flatArrayOop ary = (flatArrayOop)get_arrayOop();
  if (ary == nullptr) {
    return ciConstant();
  }

  assert(ary->is_flatArray(), "");

  if (index < 0 || index >= ary->length()) {
    return ciConstant();
  }

  if (field == nullptr) {
    return ciConstant(T_BOOLEAN, ary->null_marker_of_obj_at(index));
  }

  jint value;
  BasicType field_bt = field->type()->basic_type();
  ciInlineKlass* elt_type = element_type()->as_inline_klass();
  int field_off = field->offset_in_bytes() - elt_type->payload_offset();
  switch (field_bt) {
  case T_ARRAY:
  case T_OBJECT: {
    assert(!field->is_flat(), "that won't work for flat fields");
      oop f = ary->oop_field_at(index, field_off);
      return ciConstant(field_bt, CURRENT_ENV->get_object(f));
  }
  case T_LONG:   return ciConstant(ary->field_at<jlong  >(index, field_off));
  case T_FLOAT:  return ciConstant(ary->field_at<jfloat >(index, field_off));
  case T_DOUBLE: return ciConstant(ary->field_at<jdouble>(index, field_off));
  default:       return ciConstant();
  case T_BYTE:    value = ary->field_at<jbyte >(index, field_off);     break;
  case T_BOOLEAN: value = ary->field_at<jbyte >(index, field_off) & 1; break;
  case T_SHORT:   value = ary->field_at<jshort>(index, field_off);     break;
  case T_CHAR:    value = ary->field_at<jchar >(index, field_off);     break;
  case T_INT:     value = ary->field_at<jint  >(index, field_off);     break;
  }
  return ciConstant(field_bt, value);
}

ciConstant ciFlatArray::field_value(int index, ciField* field) {
  ciConstant value;
  GUARDED_VM_ENTRY(
    value = field_value_impl(index, field);
  )
  return value;
}
