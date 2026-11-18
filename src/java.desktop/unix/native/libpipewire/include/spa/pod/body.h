/* Simple Plugin API */
/* SPDX-FileCopyrightText: Copyright © 2018 Wim Taymans */
/* SPDX-License-Identifier: MIT */

#ifndef SPA_POD_BODY_H
#define SPA_POD_BODY_H

#include <errno.h>
#include <sys/types.h>

#include <spa/pod/pod.h>
#include <spa/utils/atomic.h>

#ifdef __cplusplus
extern "C" {
#endif

#ifndef SPA_API_POD_BODY
 #ifdef SPA_API_IMPL
  #define SPA_API_POD_BODY SPA_API_IMPL
 #else
  #define SPA_API_POD_BODY static inline
 #endif
#endif

/**
 * \addtogroup spa_pod
 * \{
 */

struct spa_pod_frame {
	struct spa_pod pod;
	struct spa_pod_frame *parent;
	uint32_t offset;
	uint32_t flags;
};

SPA_API_POD_BODY uint32_t spa_pod_type_size(uint32_t type)
{
	switch (type) {
	case SPA_TYPE_None:
	case SPA_TYPE_Bytes:
	case SPA_TYPE_Struct:
	case SPA_TYPE_Pod:
		return 0;
	case SPA_TYPE_String:
		return 1;
	case SPA_TYPE_Bool:
	case SPA_TYPE_Int:
		return sizeof(int32_t);
	case SPA_TYPE_Id:
		return sizeof(uint32_t);
	case SPA_TYPE_Long:
		return sizeof(int64_t);
	case SPA_TYPE_Float:
		return sizeof(float);
	case SPA_TYPE_Double:
		return sizeof(double);
	case SPA_TYPE_Rectangle:
		return sizeof(struct spa_rectangle);
	case SPA_TYPE_Fraction:
		return sizeof(struct spa_fraction);
	case SPA_TYPE_Bitmap:
		return sizeof(uint8_t);
	case SPA_TYPE_Array:
		return sizeof(struct spa_pod_array_body);
	case SPA_TYPE_Object:
		return sizeof(struct spa_pod_object_body);
	case SPA_TYPE_Sequence:
		return sizeof(struct spa_pod_sequence_body);
	case SPA_TYPE_Pointer:
		return sizeof(struct spa_pod_pointer_body);
	case SPA_TYPE_Fd:
		return sizeof(int64_t);
	case SPA_TYPE_Choice:
		return sizeof(struct spa_pod_choice_body);
	}
	return 0;
}

SPA_API_POD_BODY int spa_pod_choice_n_values(uint32_t choice_type, uint32_t *min, uint32_t *max)
{
	switch (choice_type) {
	case SPA_CHOICE_Enum:
		*min = 2;
		*max = UINT32_MAX;
		break;
	case SPA_CHOICE_Range:
		*min = *max = 3;
		break;
	case SPA_CHOICE_Step:
		*min = *max = 4;
		break;
	case SPA_CHOICE_None:
	case SPA_CHOICE_Flags:
		*min = *max = 1;
		break;
	default:
		/*
		 * This must always return at least 1, because callers
		 * assume that n_vals >= spa_pod_choice_n_values()
		 * mean that n_vals is at least 1.
		 */
		*min = 1;
		*max = UINT32_MAX;
		return 0;
	}
	return 1;
}

SPA_API_POD_BODY int spa_pod_body_from_data(void *data, size_t maxsize, off_t offset, size_t size,
		struct spa_pod *pod, const void **body)
{
	if (offset < 0 || offset > (int64_t)UINT32_MAX)
		return -EINVAL;
	if (size < sizeof(struct spa_pod) ||
	    size > maxsize ||
	    maxsize - size < (uint32_t)offset)
		return -EINVAL;
	memcpy(pod, SPA_PTROFF(data, offset, void), sizeof(struct spa_pod));
	if (!SPA_POD_IS_VALID(pod))
		return -EINVAL;
	if (pod->size > size - sizeof(struct spa_pod))
		return -EINVAL;
	*body = SPA_PTROFF(data, offset + sizeof(struct spa_pod), void);
	return 0;
}

SPA_API_POD_BODY int spa_pod_is_none(const struct spa_pod *pod)
{
	return SPA_POD_CHECK_TYPE(pod, SPA_TYPE_None);
}

SPA_API_POD_BODY int spa_pod_is_bool(const struct spa_pod *pod)
{
	return SPA_POD_CHECK(pod, SPA_TYPE_Bool, sizeof(int32_t));
}

#define SPA_POD_BODY_LOAD_ONCE(a, b) (*(a) = SPA_LOAD_ONCE(*(__typeof__(a))(b)))
#define SPA_POD_BODY_LOAD_FIELD_ONCE(a, b, field) ((a)->field = SPA_LOAD_ONCE(((__typeof__(a))(b))->field))

SPA_API_POD_BODY int spa_pod_body_get_bool(const struct spa_pod *pod, const void *body, bool *value)
{
	if (!spa_pod_is_bool(pod))
		return -EINVAL;
	*value = !!__atomic_load_n((const int32_t *)body, __ATOMIC_RELAXED);
	return 0;
}

SPA_API_POD_BODY int spa_pod_is_id(const struct spa_pod *pod)
{
	return SPA_POD_CHECK(pod, SPA_TYPE_Id, sizeof(uint32_t));
}

SPA_API_POD_BODY int spa_pod_body_get_id(const struct spa_pod *pod, const void *body, uint32_t *value)
{
	if (!spa_pod_is_id(pod))
		return -EINVAL;
	SPA_POD_BODY_LOAD_ONCE(value, body);
	return 0;
}

SPA_API_POD_BODY int spa_pod_is_int(const struct spa_pod *pod)
{
	return SPA_POD_CHECK(pod, SPA_TYPE_Int, sizeof(int32_t));
}

SPA_API_POD_BODY int spa_pod_body_get_int(const struct spa_pod *pod, const void *body, int32_t *value)
{
	if (!spa_pod_is_int(pod))
		return -EINVAL;
	SPA_POD_BODY_LOAD_ONCE(value, body);
	return 0;
}

SPA_API_POD_BODY int spa_pod_is_long(const struct spa_pod *pod)
{
	return SPA_POD_CHECK(pod, SPA_TYPE_Long, sizeof(int64_t));
}

SPA_API_POD_BODY int spa_pod_body_get_long(const struct spa_pod *pod, const void *body, int64_t *value)
{
	if (!spa_pod_is_long(pod))
		return -EINVAL;
	/* TODO this is wrong per C standard, but if it breaks so does the Linux kernel. */
	SPA_BARRIER;
	memcpy(value, body, sizeof *value);
	SPA_BARRIER;
	return 0;
}

SPA_API_POD_BODY int spa_pod_is_float(const struct spa_pod *pod)
{
	return SPA_POD_CHECK(pod, SPA_TYPE_Float, sizeof(float));
}

SPA_API_POD_BODY int spa_pod_body_get_float(const struct spa_pod *pod, const void *body, float *value)
{
	if (!spa_pod_is_float(pod))
		return -EINVAL;
	SPA_BARRIER;
	memcpy(value, body, sizeof *value);
	SPA_BARRIER;
	return 0;
}

SPA_API_POD_BODY int spa_pod_is_double(const struct spa_pod *pod)
{
	return SPA_POD_CHECK(pod, SPA_TYPE_Double, sizeof(double));
}

SPA_API_POD_BODY int spa_pod_body_get_double(const struct spa_pod *pod, const void *body, double *value)
{
	if (!spa_pod_is_double(pod))
		return -EINVAL;
	SPA_BARRIER;
	memcpy(value, body, sizeof *value);
	SPA_BARRIER;
	return 0;
}

SPA_API_POD_BODY int spa_pod_is_string(const struct spa_pod *pod)
{
	return SPA_POD_CHECK(pod, SPA_TYPE_String, 1);
}

SPA_API_POD_BODY int spa_pod_body_get_string(const struct spa_pod *pod,
		const void *body, const char **value)
{
	const char *s;
	if (!spa_pod_is_string(pod))
		return -EINVAL;
	s = (const char *)body;
	if (((const volatile char *)s)[pod->size-1] != '\0')
		return -EINVAL;
	*value = s;
	return 0;
}

SPA_API_POD_BODY int spa_pod_body_copy_string(const struct spa_pod *pod, const void *body,
		char *dest, size_t maxlen)
{
	const char *s;
	if (spa_pod_body_get_string(pod, body, &s) < 0 || maxlen < 1)
		return -EINVAL;
	/* pod->size includes the null terminator; a string that does not fit
	 * is reported as -ENOSPC instead of being silently truncated. */
	if (pod->size > maxlen) {
		dest[0] = '\0';
		return -ENOSPC;
	}
	SPA_BARRIER;
	strncpy(dest, s, maxlen-1);
	SPA_BARRIER;
	dest[maxlen-1]= '\0';
	return 0;
}

SPA_API_POD_BODY int spa_pod_is_bytes(const struct spa_pod *pod)
{
	return SPA_POD_CHECK_TYPE(pod, SPA_TYPE_Bytes);
}

SPA_API_POD_BODY int spa_pod_body_get_bytes(const struct spa_pod *pod, const void *body,
		const void **value, uint32_t *len)
{
	if (!spa_pod_is_bytes(pod))
		return -EINVAL;
	*value = (const void *)body;
	*len = pod->size;
	return 0;
}

SPA_API_POD_BODY int spa_pod_is_pointer(const struct spa_pod *pod)
{
	return SPA_POD_CHECK(pod, SPA_TYPE_Pointer, sizeof(struct spa_pod_pointer_body));
}

SPA_API_POD_BODY int spa_pod_body_get_pointer(const struct spa_pod *pod, const void *body,
		uint32_t *type, const void **value)
{
	struct spa_pod_pointer_body b;
	if (!spa_pod_is_pointer(pod))
		return -EINVAL;
	SPA_POD_BODY_LOAD_FIELD_ONCE(&b, body, type);
	SPA_POD_BODY_LOAD_FIELD_ONCE(&b, body, value);
	*type = b.type;
	*value = b.value;
	return 0;
}

SPA_API_POD_BODY int spa_pod_is_fd(const struct spa_pod *pod)
{
	return SPA_POD_CHECK(pod, SPA_TYPE_Fd, sizeof(int64_t));
}

SPA_API_POD_BODY int spa_pod_body_get_fd(const struct spa_pod *pod, const void *body,
		int64_t *value)
{
	if (!spa_pod_is_fd(pod))
		return -EINVAL;
	SPA_BARRIER;
	memcpy(value, body, sizeof *value);
	SPA_BARRIER;
	return 0;
}

SPA_API_POD_BODY int spa_pod_is_rectangle(const struct spa_pod *pod)
{
	return SPA_POD_CHECK(pod, SPA_TYPE_Rectangle, sizeof(struct spa_rectangle));
}

SPA_API_POD_BODY int spa_pod_body_get_rectangle(const struct spa_pod *pod, const void *body,
		struct spa_rectangle *value)
{
	if (!spa_pod_is_rectangle(pod))
		return -EINVAL;
	SPA_POD_BODY_LOAD_FIELD_ONCE(value, body, width);
	SPA_POD_BODY_LOAD_FIELD_ONCE(value, body, height);
	return 0;
}

SPA_API_POD_BODY int spa_pod_is_fraction(const struct spa_pod *pod)
{
	return SPA_POD_CHECK(pod, SPA_TYPE_Fraction, sizeof(struct spa_fraction));
}
SPA_API_POD_BODY int spa_pod_body_get_fraction(const struct spa_pod *pod, const void *body,
		struct spa_fraction *value)
{
	if (!spa_pod_is_fraction(pod))
		return -EINVAL;
	SPA_POD_BODY_LOAD_FIELD_ONCE(value, body, num);
	SPA_POD_BODY_LOAD_FIELD_ONCE(value, body, denom);
	return 0;
}

SPA_API_POD_BODY int spa_pod_is_bitmap(const struct spa_pod *pod)
{
	return SPA_POD_CHECK(pod, SPA_TYPE_Bitmap, sizeof(uint8_t));
}
SPA_API_POD_BODY int spa_pod_body_get_bitmap(const struct spa_pod *pod, const void *body,
		const uint8_t **value)
{
	if (!spa_pod_is_bitmap(pod))
		return -EINVAL;
	*value = (const uint8_t *)body;
	return 0;
}

SPA_API_POD_BODY int spa_pod_is_array(const struct spa_pod *pod)
{
	return SPA_POD_CHECK(pod, SPA_TYPE_Array, sizeof(struct spa_pod_array_body));
}
SPA_API_POD_BODY int spa_pod_body_get_array(const struct spa_pod *pod, const void *body,
		struct spa_pod_array *arr, const void **arr_body)
{
	if (!spa_pod_is_array(pod))
		return -EINVAL;
	arr->pod = *pod;
	SPA_POD_BODY_LOAD_FIELD_ONCE(&arr->body.child, body, type);
	SPA_POD_BODY_LOAD_FIELD_ONCE(&arr->body.child, body, size);
	*arr_body = SPA_PTROFF(body, sizeof(struct spa_pod_array_body), void);
	return 0;
}
SPA_API_POD_BODY const void *spa_pod_array_body_get_values(const struct spa_pod_array *arr,
		const void *body, uint32_t *n_values, uint32_t *val_size, uint32_t *val_type)
{
	uint32_t child_size = arr->body.child.size;
	*n_values = child_size ? (arr->pod.size - sizeof(arr->body)) / child_size : 0;
	*val_size = child_size;
	*val_type = arr->body.child.type;
	if (*val_size < spa_pod_type_size(*val_type))
		*n_values = 0;
	return body;
}

SPA_API_POD_BODY const void *spa_pod_body_get_array_values(const struct spa_pod *pod,
		const void *body, uint32_t *n_values, uint32_t *val_size, uint32_t *val_type)
{
	struct spa_pod_array arr;
	if (spa_pod_body_get_array(pod, body, &arr, &body) < 0)
		return NULL;
	return spa_pod_array_body_get_values(&arr, body, n_values, val_size, val_type);
}

SPA_API_POD_BODY int spa_pod_is_choice(const struct spa_pod *pod)
{
	return SPA_POD_CHECK(pod, SPA_TYPE_Choice, sizeof(struct spa_pod_choice_body));
}
SPA_API_POD_BODY int spa_pod_body_get_choice(const struct spa_pod *pod, const void *body,
		struct spa_pod_choice *choice, const void **choice_body)
{
	if (!spa_pod_is_choice(pod))
		return -EINVAL;
	choice->pod = *pod;
	SPA_POD_BODY_LOAD_FIELD_ONCE(&choice->body, body, type);
	SPA_POD_BODY_LOAD_FIELD_ONCE(&choice->body, body, flags);
	SPA_POD_BODY_LOAD_FIELD_ONCE(&choice->body, body, child.size);
	SPA_POD_BODY_LOAD_FIELD_ONCE(&choice->body, body, child.type);
	*choice_body = SPA_PTROFF(body, sizeof(struct spa_pod_choice_body), void);
	return 0;
}
SPA_API_POD_BODY const void *spa_pod_choice_body_get_values(const struct spa_pod_choice *pod,
		const void *body, uint32_t *n_values, uint32_t *choice,
		uint32_t *val_size, uint32_t *val_type)
{
	uint32_t child_size = pod->body.child.size, min, max;
	*val_size = child_size;
	*val_type = pod->body.child.type;
	*n_values = child_size ? (pod->pod.size - sizeof(pod->body)) / child_size : 0;
	*choice = pod->body.type;
	spa_pod_choice_n_values(*choice, &min, &max);
	if (*n_values < min || *val_size < spa_pod_type_size(*val_type))
		*n_values = 0;
	else if (*n_values > max)
		*n_values = max;
	return body;
}

SPA_API_POD_BODY int spa_pod_is_struct(const struct spa_pod *pod)
{
	return SPA_POD_CHECK_TYPE(pod, SPA_TYPE_Struct);
}

SPA_API_POD_BODY int spa_pod_is_object(const struct spa_pod *pod)
{
	return SPA_POD_CHECK(pod, SPA_TYPE_Object, sizeof(struct spa_pod_object_body));
}
SPA_API_POD_BODY int spa_pod_body_get_object(const struct spa_pod *pod, const void *body,
		struct spa_pod_object *object, const void **object_body)
{
	if (!spa_pod_is_object(pod))
		return -EINVAL;
	object->pod = *pod;
	SPA_POD_BODY_LOAD_FIELD_ONCE(&object->body, body, type);
	SPA_POD_BODY_LOAD_FIELD_ONCE(&object->body, body, id);
	*object_body = SPA_PTROFF(body, sizeof(struct spa_pod_object_body), void);
	return 0;
}

SPA_API_POD_BODY int spa_pod_is_sequence(const struct spa_pod *pod)
{
	return SPA_POD_CHECK(pod, SPA_TYPE_Sequence, sizeof(struct spa_pod_sequence_body));
}
SPA_API_POD_BODY int spa_pod_body_get_sequence(const struct spa_pod *pod, const void *body,
		struct spa_pod_sequence *seq, const void **seq_body)
{
	if (!spa_pod_is_sequence(pod))
		return -EINVAL;
	seq->pod = *pod;
	SPA_POD_BODY_LOAD_FIELD_ONCE(&seq->body, body, unit);
	SPA_POD_BODY_LOAD_FIELD_ONCE(&seq->body, body, pad);
	*seq_body = SPA_PTROFF(body, sizeof(struct spa_pod_sequence_body), void);
	return 0;
}

/**
 * \}
 */

#ifdef __cplusplus
}  /* extern "C" */
#endif

#endif /* SPA_POD_BODY_H */
