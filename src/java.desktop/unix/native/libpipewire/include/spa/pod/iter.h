/* Simple Plugin API */
/* SPDX-FileCopyrightText: Copyright © 2018 Wim Taymans */
/* SPDX-License-Identifier: MIT */

#ifndef SPA_POD_ITER_H
#define SPA_POD_ITER_H

#include <errno.h>
#include <sys/types.h>

#include <spa/pod/pod.h>
#include <spa/pod/body.h>

#ifdef __cplusplus
extern "C" {
#endif

#ifndef SPA_API_POD_ITER
 #ifdef SPA_API_IMPL
  #define SPA_API_POD_ITER SPA_API_IMPL
 #else
  #define SPA_API_POD_ITER static inline
 #endif
#endif

/**
 * \addtogroup spa_pod
 * \{
 */

SPA_API_POD_ITER bool spa_pod_is_inside(const void *pod, uint32_t size, const void *iter)
{
	size_t remaining;

	return spa_ptr_type_inside(pod, size, iter, struct spa_pod, &remaining) &&
		remaining >= SPA_POD_BODY_SIZE(iter);
}

SPA_API_POD_ITER void *spa_pod_next(const void *iter)
{
	return SPA_PTROFF(iter, SPA_ROUND_UP_N(SPA_POD_SIZE(iter), SPA_POD_ALIGN), void);
}

SPA_API_POD_ITER struct spa_pod_prop *spa_pod_prop_first(const struct spa_pod_object_body *body)
{
	return SPA_PTROFF(body, sizeof(struct spa_pod_object_body), struct spa_pod_prop);
}

SPA_API_POD_ITER bool spa_pod_prop_is_inside(const struct spa_pod_object_body *body,
		uint32_t size, const struct spa_pod_prop *iter)
{
	size_t remaining;

	return spa_ptr_type_inside(body, size, iter, struct spa_pod_prop, &remaining) &&
		remaining >= iter->value.size;
}

SPA_API_POD_ITER struct spa_pod_prop *spa_pod_prop_next(const struct spa_pod_prop *iter)
{
	return SPA_PTROFF(iter, SPA_ROUND_UP_N(SPA_POD_PROP_SIZE(iter), SPA_POD_ALIGN), struct spa_pod_prop);
}

SPA_API_POD_ITER struct spa_pod_control *spa_pod_control_first(const struct spa_pod_sequence_body *body)
{
	return SPA_PTROFF(body, sizeof(struct spa_pod_sequence_body), struct spa_pod_control);
}

SPA_API_POD_ITER bool spa_pod_control_is_inside(const struct spa_pod_sequence_body *body,
		uint32_t size, const struct spa_pod_control *iter)
{
	size_t remaining;

	return spa_ptr_type_inside(body, size, iter, struct spa_pod_control, &remaining) &&
		remaining >= iter->value.size;
}

SPA_API_POD_ITER struct spa_pod_control *spa_pod_control_next(const struct spa_pod_control *iter)
{
	return SPA_PTROFF(iter, SPA_ROUND_UP_N(SPA_POD_CONTROL_SIZE(iter), SPA_POD_ALIGN), struct spa_pod_control);
}

#define SPA_POD_ARRAY_BODY_FOREACH(body, _size, iter)							\
	for ((iter) = (__typeof__(iter))SPA_PTROFF((body), sizeof(struct spa_pod_array_body), void);	\
	     (body)->child.size > 0 && spa_ptrinside(body, _size, iter, (body)->child.size, NULL);	\
	     (iter) = (__typeof__(iter))SPA_PTROFF((iter), (body)->child.size, void))

#define SPA_POD_ARRAY_FOREACH(obj, iter)							\
	SPA_POD_ARRAY_BODY_FOREACH(&(obj)->body, SPA_POD_BODY_SIZE(obj), iter)

#define SPA_POD_CHOICE_BODY_FOREACH(body, _size, iter)							\
	for ((iter) = (__typeof__(iter))SPA_PTROFF((body), sizeof(struct spa_pod_choice_body), void);	\
	     (body)->child.size > 0 && spa_ptrinside(body, _size, iter, (body)->child.size, NULL);	\
	     (iter) = (__typeof__(iter))SPA_PTROFF((iter), (body)->child.size, void))

#define SPA_POD_CHOICE_FOREACH(obj, iter)							\
	SPA_POD_CHOICE_BODY_FOREACH(&(obj)->body, SPA_POD_BODY_SIZE(obj), iter)

#define SPA_POD_FOREACH(pod, size, iter)					\
	for ((iter) = (pod);							\
	     spa_pod_is_inside(pod, size, iter);				\
	     (iter) = (__typeof__(iter))spa_pod_next(iter))

#define SPA_POD_STRUCT_FOREACH(obj, iter)							\
	SPA_POD_FOREACH(SPA_POD_STRUCT_BODY(obj), SPA_POD_BODY_SIZE(obj), iter)

#define SPA_POD_OBJECT_BODY_FOREACH(body, size, iter)						\
	for ((iter) = spa_pod_prop_first(body);				\
	     spa_pod_prop_is_inside(body, size, iter);			\
	     (iter) = spa_pod_prop_next(iter))

#define SPA_POD_OBJECT_FOREACH(obj, iter)							\
	SPA_POD_OBJECT_BODY_FOREACH(&(obj)->body, SPA_POD_BODY_SIZE(obj), iter)

#define SPA_POD_SEQUENCE_BODY_FOREACH(body, size, iter)						\
	for ((iter) = spa_pod_control_first(body);						\
	     spa_pod_control_is_inside(body, size, iter);						\
	     (iter) = spa_pod_control_next(iter))

#define SPA_POD_SEQUENCE_FOREACH(seq, iter)							\
	SPA_POD_SEQUENCE_BODY_FOREACH(&(seq)->body, SPA_POD_BODY_SIZE(seq), iter)

SPA_API_POD_ITER void *spa_pod_from_data(void *data, size_t maxsize, off_t offset, size_t size)
{
	struct spa_pod pod;
	const void *body;
	if (spa_pod_body_from_data(data, maxsize, offset, size, &pod, &body) < 0)
		return NULL;
	return SPA_PTROFF(data, offset, void);
}

SPA_API_POD_ITER int spa_pod_get_bool(const struct spa_pod *pod, bool *value)
{
	return spa_pod_body_get_bool(pod, SPA_POD_BODY_CONST(pod), value);
}

SPA_API_POD_ITER int spa_pod_get_id(const struct spa_pod *pod, uint32_t *value)
{
	return spa_pod_body_get_id(pod, SPA_POD_BODY_CONST(pod), value);
}

SPA_API_POD_ITER int spa_pod_get_int(const struct spa_pod *pod, int32_t *value)
{
	return spa_pod_body_get_int(pod, SPA_POD_BODY_CONST(pod), value);
}

SPA_API_POD_ITER int spa_pod_get_long(const struct spa_pod *pod, int64_t *value)
{
	return spa_pod_body_get_long(pod, SPA_POD_BODY_CONST(pod), value);
}

SPA_API_POD_ITER int spa_pod_get_float(const struct spa_pod *pod, float *value)
{
	return spa_pod_body_get_float(pod, SPA_POD_BODY_CONST(pod), value);
}

SPA_API_POD_ITER int spa_pod_get_double(const struct spa_pod *pod, double *value)
{
	return spa_pod_body_get_double(pod, SPA_POD_BODY_CONST(pod), value);
}

SPA_API_POD_ITER int spa_pod_get_string(const struct spa_pod *pod, const char **value)
{
	return spa_pod_body_get_string(pod, SPA_POD_BODY_CONST(pod), value);
}

SPA_API_POD_ITER int spa_pod_copy_string(const struct spa_pod *pod, size_t maxlen, char *dest)
{
	return spa_pod_body_copy_string(pod, SPA_POD_BODY_CONST(pod), dest, maxlen);
}

SPA_API_POD_ITER int spa_pod_get_bytes(const struct spa_pod *pod, const void **value, uint32_t *len)
{
	return spa_pod_body_get_bytes(pod, SPA_POD_BODY_CONST(pod), value, len);
}

SPA_API_POD_ITER int spa_pod_get_pointer(const struct spa_pod *pod, uint32_t *type, const void **value)
{
	return spa_pod_body_get_pointer(pod, SPA_POD_BODY_CONST(pod), type, value);
}

SPA_API_POD_ITER int spa_pod_get_fd(const struct spa_pod *pod, int64_t *value)
{
	return spa_pod_body_get_fd(pod, SPA_POD_BODY_CONST(pod), value);
}

SPA_API_POD_ITER int spa_pod_get_rectangle(const struct spa_pod *pod, struct spa_rectangle *value)
{
	return spa_pod_body_get_rectangle(pod, SPA_POD_BODY_CONST(pod), value);
}

SPA_API_POD_ITER int spa_pod_get_fraction(const struct spa_pod *pod, struct spa_fraction *value)
{
	return spa_pod_body_get_fraction(pod, SPA_POD_BODY_CONST(pod), value);
}

SPA_API_POD_ITER void *spa_pod_get_array_full(const struct spa_pod *pod, uint32_t *n_values,
		uint32_t *val_size, uint32_t *val_type)
{
	return (void*)spa_pod_body_get_array_values(pod, SPA_POD_BODY(pod), n_values, val_size, val_type);
}
SPA_API_POD_ITER void *spa_pod_get_array(const struct spa_pod *pod, uint32_t *n_values)
{
	uint32_t size, type;
	return spa_pod_get_array_full(pod, n_values, &size, &type);
}

SPA_API_POD_ITER uint32_t spa_pod_copy_array_full(const struct spa_pod *pod, uint32_t type,
		uint32_t size, void *values, uint32_t max_values)
{
	uint32_t n_values, val_size, val_type;
	const void *v = spa_pod_get_array_full(pod, &n_values, &val_size, &val_type);
	if (v == NULL || max_values == 0 || val_type != type || val_size != size)
		return 0;
	n_values = SPA_MIN(n_values, max_values);
	memcpy(values, v, val_size * n_values);
	return n_values;
}

#define spa_pod_copy_array(pod,type,values,max_values)	\
	spa_pod_copy_array_full(pod,type,sizeof(values[0]),values,max_values)

SPA_API_POD_ITER struct spa_pod *spa_pod_get_values(const struct spa_pod *pod,
		uint32_t *n_vals, uint32_t *choice)
{
	if (spa_pod_is_choice(pod)) {
		const struct spa_pod_choice *p = (const struct spa_pod_choice*)pod;
		uint32_t type, size;
		spa_pod_choice_body_get_values(p, SPA_POD_BODY_CONST(p), n_vals, choice, &size, &type);
		return (struct spa_pod*)&p->body.child;
	} else {
		*n_vals = pod->size < spa_pod_type_size(pod->type) ? 0 : 1;
		*choice = SPA_CHOICE_None;
		return (struct spa_pod*)pod;
	}
}

SPA_API_POD_ITER bool spa_pod_is_object_type(const struct spa_pod *pod, uint32_t type)
{
	return (pod && spa_pod_is_object(pod) && SPA_POD_OBJECT_TYPE(pod) == type);
}

SPA_API_POD_ITER bool spa_pod_is_object_id(const struct spa_pod *pod, uint32_t id)
{
	return (pod && spa_pod_is_object(pod) && SPA_POD_OBJECT_ID(pod) == id);
}

SPA_API_POD_ITER const struct spa_pod_prop *spa_pod_object_find_prop(const struct spa_pod_object *pod,
		const struct spa_pod_prop *start, uint32_t key)
{
	const struct spa_pod_prop *first, *res;

	first = spa_pod_prop_first(&pod->body);
	start = start ? spa_pod_prop_next(start) : first;

	for (res = start; spa_pod_prop_is_inside(&pod->body, pod->pod.size, res);
	     res = spa_pod_prop_next(res)) {
		if (res->key == key)
			return res;
	}
	for (res = first; res != start; res = spa_pod_prop_next(res)) {
		if (res->key == key)
			return res;
	}
	return NULL;
}

SPA_API_POD_ITER const struct spa_pod_prop *spa_pod_find_prop(const struct spa_pod *pod,
		const struct spa_pod_prop *start, uint32_t key)
{
	if (!spa_pod_is_object(pod))
		return NULL;
	return spa_pod_object_find_prop((const struct spa_pod_object *)pod, start, key);
}

SPA_API_POD_ITER int spa_pod_object_has_props(const struct spa_pod_object *pod)
{
	struct spa_pod_prop *res;
	SPA_POD_OBJECT_FOREACH(pod, res)
		return 1;
	return 0;
}

SPA_API_POD_ITER int spa_pod_object_fixate(struct spa_pod_object *pod)
{
	struct spa_pod_prop *res;
	SPA_POD_OBJECT_FOREACH(pod, res) {
		if (spa_pod_is_choice(&res->value) &&
		    !SPA_FLAG_IS_SET(res->flags, SPA_POD_PROP_FLAG_DONT_FIXATE))
			((struct spa_pod_choice*)&res->value)->body.type = SPA_CHOICE_None;
	}
	return 0;
}
SPA_API_POD_ITER int spa_pod_object_is_fixated(const struct spa_pod_object *pod)
{
	struct spa_pod_prop *res;
	SPA_POD_OBJECT_FOREACH(pod, res) {
		if (spa_pod_is_choice(&res->value) &&
		   ((struct spa_pod_choice*)&res->value)->body.type != SPA_CHOICE_None)
			return 0;
	}
	return 1;
}

SPA_API_POD_ITER int spa_pod_fixate(struct spa_pod *pod)
{
	if (!spa_pod_is_object(pod))
		return -EINVAL;
	return spa_pod_object_fixate((struct spa_pod_object *)pod);
}

SPA_API_POD_ITER int spa_pod_is_fixated(const struct spa_pod *pod)
{
	if (!spa_pod_is_object(pod))
		return -EINVAL;
	return spa_pod_object_is_fixated((const struct spa_pod_object *)pod);
}

/**
 * \}
 */

#ifdef __cplusplus
}  /* extern "C" */
#endif

#endif /* SPA_POD_ITER_H */
