/* Simple Plugin API */
/* SPDX-FileCopyrightText: Copyright © 2018 Wim Taymans */
/* SPDX-License-Identifier: MIT */

#ifndef SPA_POD_BUILDER_H
#define SPA_POD_BUILDER_H

#include <stdarg.h>

#include <spa/utils/hook.h>
#include <spa/pod/body.h>
#include <spa/pod/vararg.h>

#ifdef __cplusplus
extern "C" {
#endif

/** \defgroup spa_pod POD
 * Binary data serialization format
 */

/**
 * \addtogroup spa_pod
 * \{
 */

#ifndef SPA_API_POD_BUILDER
 #ifdef SPA_API_IMPL
  #define SPA_API_POD_BUILDER SPA_API_IMPL
 #else
  #define SPA_API_POD_BUILDER static inline
 #endif
#endif

struct spa_pod_builder_state {
	uint32_t offset;
#define SPA_POD_BUILDER_FLAG_BODY	(1<<0)
#define SPA_POD_BUILDER_FLAG_FIRST	(1<<1)
	uint32_t flags;
	struct spa_pod_frame *frame;
};

struct spa_pod_builder;

struct spa_pod_builder_callbacks {
#define SPA_VERSION_POD_BUILDER_CALLBACKS 0
	uint32_t version;

	int (*overflow) (void *data, uint32_t size);
};

struct spa_pod_builder {
	void *data;
	uint32_t size;
	uint32_t _padding;
	struct spa_pod_builder_state state;
	struct spa_callbacks callbacks;
};

#define SPA_POD_BUILDER_INIT(buffer,size)  ((struct spa_pod_builder){ (buffer), (size), 0, {0,0,NULL},{NULL,NULL}})

SPA_API_POD_BUILDER void
spa_pod_builder_get_state(struct spa_pod_builder *builder, struct spa_pod_builder_state *state)
{
	*state = builder->state;
}

SPA_API_POD_BUILDER bool spa_pod_builder_corrupted(const struct spa_pod_builder *builder)
{
	return builder->state.offset > builder->size;
}

SPA_API_POD_BUILDER void
spa_pod_builder_set_callbacks(struct spa_pod_builder *builder,
		const struct spa_pod_builder_callbacks *callbacks, void *data)
{
	builder->callbacks = SPA_CALLBACKS_INIT(callbacks, data);
}

SPA_API_POD_BUILDER void
spa_pod_builder_reset(struct spa_pod_builder *builder, struct spa_pod_builder_state *state)
{
	struct spa_pod_frame *f;
	uint32_t size = builder->state.offset - state->offset;
	builder->state = *state;
	for (f = builder->state.frame; f ; f = f->parent)
		f->pod.size -= SPA_MIN(size, f->pod.size);
}

SPA_API_POD_BUILDER void spa_pod_builder_init(struct spa_pod_builder *builder, void *data, uint32_t size)
{
	*builder = SPA_POD_BUILDER_INIT(data, size);
}

SPA_API_POD_BUILDER struct spa_pod *
spa_pod_builder_deref_fallback(struct spa_pod_builder *builder, uint32_t offset, struct spa_pod *fallback)
{
	uint32_t size = builder->size;
	if (offset + UINT64_C(8) <= size) {
		struct spa_pod *pod = SPA_PTROFF(builder->data, offset, struct spa_pod);
		if (offset + (uint64_t)SPA_POD_SIZE(pod) <= size &&
		    SPA_POD_IS_VALID(pod))
			return pod;
	}
	return fallback;
}

SPA_API_POD_BUILDER struct spa_pod *
spa_pod_builder_deref(struct spa_pod_builder *builder, uint32_t offset)
{
	return (struct spa_pod*)spa_pod_builder_deref_fallback(builder, offset, NULL);
}

SPA_API_POD_BUILDER struct spa_pod *
spa_pod_builder_frame(struct spa_pod_builder *builder, struct spa_pod_frame *frame)
{
	if (frame->offset + (uint64_t)SPA_POD_SIZE(&frame->pod) <= builder->size)
		return SPA_PTROFF(builder->data, frame->offset, struct spa_pod);
	return NULL;
}

SPA_API_POD_BUILDER void
spa_pod_builder_push(struct spa_pod_builder *builder,
		     struct spa_pod_frame *frame,
		     const struct spa_pod *pod,
		     uint32_t offset)
{
	frame->pod = *pod;
	frame->offset = offset;
	frame->parent = builder->state.frame;
	frame->flags = builder->state.flags;
	builder->state.frame = frame;

	if (frame->pod.type == SPA_TYPE_Array || frame->pod.type == SPA_TYPE_Choice)
		builder->state.flags = SPA_POD_BUILDER_FLAG_FIRST | SPA_POD_BUILDER_FLAG_BODY;
}

SPA_API_POD_BUILDER int spa_pod_builder_raw(struct spa_pod_builder *builder, const void *data, uint32_t size)
{
	int res = 0;
	struct spa_pod_frame *f;
	uint32_t offset = builder->state.offset;
	size_t data_offset = -1;
	uint64_t total_size = offset + (uint64_t) size;

	if (total_size > builder->size) {
		if (total_size > UINT32_MAX)
			return -ENOSPC;

		/* data could be inside the data we will realloc */
		if (spa_ptrinside(builder->data, builder->size, data, size, NULL))
			data_offset = SPA_PTRDIFF(data, builder->data);

		res = -ENOSPC;
		if (offset <= builder->size)
			spa_callbacks_call_res(&builder->callbacks,
					struct spa_pod_builder_callbacks, res,
					overflow, 0, total_size);
	}
	if (res == 0 && data) {
		if (data_offset != (size_t) -1)
			data = SPA_PTROFF(builder->data, data_offset, const void);

		memcpy(SPA_PTROFF(builder->data, offset, void), data, size);
	}

	builder->state.offset = total_size;

	for (f = builder->state.frame; f ; f = f->parent)
		f->pod.size += size;

	return res;
}

SPA_API_POD_BUILDER void spa_pod_builder_remove(struct spa_pod_builder *builder, uint32_t size)
{
	struct spa_pod_frame *f;
	builder->state.offset -= SPA_MIN(size, builder->state.offset);
	for (f = builder->state.frame; f ; f = f->parent)
		f->pod.size -= SPA_MIN(size, f->pod.size);
}

SPA_API_POD_BUILDER int spa_pod_builder_pad(struct spa_pod_builder *builder, uint32_t size)
{
	uint64_t zeroes = 0;
	if (builder->state.flags == SPA_POD_BUILDER_FLAG_BODY)
		return 0;
	size = SPA_ROUND_UP_N(size, SPA_POD_ALIGN) - size;
	return size ? spa_pod_builder_raw(builder, &zeroes, size) : 0;
}

SPA_API_POD_BUILDER int
spa_pod_builder_raw_padded(struct spa_pod_builder *builder, const void *data, uint32_t size)
{
	int r, res = spa_pod_builder_raw(builder, data, size);
	if ((r = spa_pod_builder_pad(builder, size)) < 0)
		res = r;
	return res;
}

SPA_API_POD_BUILDER void *spa_pod_builder_pop(struct spa_pod_builder *builder, struct spa_pod_frame *frame)
{
	struct spa_pod *pod;

	if (SPA_FLAG_IS_SET(builder->state.flags, SPA_POD_BUILDER_FLAG_FIRST)) {
		const struct spa_pod p = { 0, SPA_TYPE_None };
		spa_pod_builder_raw(builder, &p, sizeof(p));
	}
	if ((pod = (struct spa_pod*)spa_pod_builder_frame(builder, frame)) != NULL)
		*pod = frame->pod;

	builder->state.frame = frame->parent;
	builder->state.flags = frame->flags;
	spa_pod_builder_pad(builder, builder->state.offset);
	return pod;
}

SPA_API_POD_BUILDER int
spa_pod_builder_primitive_body(struct spa_pod_builder *builder, const struct spa_pod *p,
		const void *body, uint32_t body_size, const char *suffix, uint32_t suffix_size)
{
	int res = 0, r;
	uint32_t size = SPA_POD_SIZE(p) - body_size - suffix_size;
	if (builder->state.flags != SPA_POD_BUILDER_FLAG_BODY) {
		SPA_FLAG_CLEAR(builder->state.flags, SPA_POD_BUILDER_FLAG_FIRST);
		res = spa_pod_builder_raw(builder, p, size);
	}
	if (body_size > 0 && (r = spa_pod_builder_raw(builder, body, body_size)) < 0)
		res = r;
	if (suffix_size > 0 && (r = spa_pod_builder_raw(builder, suffix, suffix_size)) < 0)
		res = r;
	if ((r = spa_pod_builder_pad(builder, builder->state.offset)) < 0)
		res = r;
	return res;
}

SPA_API_POD_BUILDER int
spa_pod_builder_primitive(struct spa_pod_builder *builder, const struct spa_pod *p)
{
	return spa_pod_builder_primitive_body(builder, p,
			SPA_POD_BODY_CONST(p), p->size, NULL, 0);
}

#define SPA_POD_INIT(size,type) ((struct spa_pod) { (size), (type) })

#define SPA_POD_INIT_None() SPA_POD_INIT(0, SPA_TYPE_None)

SPA_API_POD_BUILDER int spa_pod_builder_none(struct spa_pod_builder *builder)
{
	const struct spa_pod p = SPA_POD_INIT_None();
	return spa_pod_builder_primitive(builder, &p);
}

SPA_API_POD_BUILDER int spa_pod_builder_child(struct spa_pod_builder *builder, uint32_t size, uint32_t type)
{
	const struct spa_pod p = SPA_POD_INIT(size,type);
	SPA_FLAG_CLEAR(builder->state.flags, SPA_POD_BUILDER_FLAG_FIRST);
	return spa_pod_builder_raw(builder, &p, sizeof(p));
}

#define SPA_POD_INIT_Bool(val) ((struct spa_pod_bool){ { sizeof(uint32_t), SPA_TYPE_Bool }, (val) ? 1 : 0, 0 })

SPA_API_POD_BUILDER int spa_pod_builder_bool(struct spa_pod_builder *builder, bool val)
{
	const struct spa_pod_bool p = SPA_POD_INIT_Bool(val);
	return spa_pod_builder_primitive(builder, &p.pod);
}

#define SPA_POD_INIT_Id(val) ((struct spa_pod_id){ { sizeof(uint32_t), SPA_TYPE_Id }, (val), 0 })

SPA_API_POD_BUILDER int spa_pod_builder_id(struct spa_pod_builder *builder, uint32_t val)
{
	const struct spa_pod_id p = SPA_POD_INIT_Id(val);
	return spa_pod_builder_primitive(builder, &p.pod);
}

#define SPA_POD_INIT_Int(val) ((struct spa_pod_int){ { sizeof(int32_t), SPA_TYPE_Int }, (val), 0 })

SPA_API_POD_BUILDER int spa_pod_builder_int(struct spa_pod_builder *builder, int32_t val)
{
	const struct spa_pod_int p = SPA_POD_INIT_Int(val);
	return spa_pod_builder_primitive(builder, &p.pod);
}

#define SPA_POD_INIT_Long(val) ((struct spa_pod_long){ { sizeof(int64_t), SPA_TYPE_Long }, (val) })

SPA_API_POD_BUILDER int spa_pod_builder_long(struct spa_pod_builder *builder, int64_t val)
{
	const struct spa_pod_long p = SPA_POD_INIT_Long(val);
	return spa_pod_builder_primitive(builder, &p.pod);
}

#define SPA_POD_INIT_Float(val) ((struct spa_pod_float){ { sizeof(float), SPA_TYPE_Float }, (val), 0 })

SPA_API_POD_BUILDER int spa_pod_builder_float(struct spa_pod_builder *builder, float val)
{
	const struct spa_pod_float p = SPA_POD_INIT_Float(val);
	return spa_pod_builder_primitive(builder, &p.pod);
}

#define SPA_POD_INIT_Double(val) ((struct spa_pod_double){ { sizeof(double), SPA_TYPE_Double }, (val) })

SPA_API_POD_BUILDER int spa_pod_builder_double(struct spa_pod_builder *builder, double val)
{
	const struct spa_pod_double p = SPA_POD_INIT_Double(val);
	return spa_pod_builder_primitive(builder, &p.pod);
}

#define SPA_POD_INIT_String(len) ((struct spa_pod_string){ { (len), SPA_TYPE_String } })

SPA_API_POD_BUILDER int
spa_pod_builder_write_string(struct spa_pod_builder *builder, const char *str, uint32_t len)
{
	int r, res;
	res = spa_pod_builder_raw(builder, str, len);
	if ((r = spa_pod_builder_raw(builder, "", 1)) < 0)
		res = r;
	if ((r = spa_pod_builder_pad(builder, builder->state.offset)) < 0)
		res = r;
	return res;
}

SPA_API_POD_BUILDER int
spa_pod_builder_string_len(struct spa_pod_builder *builder, const char *str, uint32_t len)
{
	const struct spa_pod_string p = SPA_POD_INIT_String(len+1);
	return spa_pod_builder_primitive_body(builder, &p.pod, str, len, "", 1);
}

SPA_API_POD_BUILDER int spa_pod_builder_string(struct spa_pod_builder *builder, const char *str)
{
	uint32_t len = str ? strlen(str) : 0;
	return spa_pod_builder_string_len(builder, str ? str : "", len);
}

#define SPA_POD_INIT_Bytes(len) ((struct spa_pod_bytes){ { (len), SPA_TYPE_Bytes } })

SPA_API_POD_BUILDER int
spa_pod_builder_bytes(struct spa_pod_builder *builder, const void *bytes, uint32_t len)
{
	const struct spa_pod_bytes p = SPA_POD_INIT_Bytes(len);
	return spa_pod_builder_primitive_body(builder, &p.pod, bytes, len, NULL, 0);
}
SPA_API_POD_BUILDER void *
spa_pod_builder_reserve_bytes(struct spa_pod_builder *builder, uint32_t len)
{
	uint32_t offset = builder->state.offset;
	if (spa_pod_builder_bytes(builder, NULL, len) < 0)
		return NULL;
	return SPA_PTROFF(builder->data, offset + sizeof(struct spa_pod), void);
}

#define SPA_POD_INIT_Pointer(type,value) ((struct spa_pod_pointer){ { sizeof(struct spa_pod_pointer_body), SPA_TYPE_Pointer }, { (type), 0, (value) } })

SPA_API_POD_BUILDER int
spa_pod_builder_pointer(struct spa_pod_builder *builder, uint32_t type, const void *val)
{
	const struct spa_pod_pointer p = SPA_POD_INIT_Pointer(type, val);
	return spa_pod_builder_primitive(builder, &p.pod);
}

#define SPA_POD_INIT_Fd(fd) ((struct spa_pod_fd){ { sizeof(int64_t), SPA_TYPE_Fd }, (fd) })

SPA_API_POD_BUILDER int spa_pod_builder_fd(struct spa_pod_builder *builder, int64_t fd)
{
	const struct spa_pod_fd p = SPA_POD_INIT_Fd(fd);
	return spa_pod_builder_primitive(builder, &p.pod);
}

#define SPA_POD_INIT_Rectangle(val) ((struct spa_pod_rectangle){ { sizeof(struct spa_rectangle), SPA_TYPE_Rectangle }, (val) })

SPA_API_POD_BUILDER int
spa_pod_builder_rectangle(struct spa_pod_builder *builder, uint32_t width, uint32_t height)
{
	const struct spa_pod_rectangle p = SPA_POD_INIT_Rectangle(SPA_RECTANGLE(width, height));
	return spa_pod_builder_primitive(builder, &p.pod);
}

#define SPA_POD_INIT_Fraction(val) ((struct spa_pod_fraction){ { sizeof(struct spa_fraction), SPA_TYPE_Fraction }, (val) })

SPA_API_POD_BUILDER int
spa_pod_builder_fraction(struct spa_pod_builder *builder, uint32_t num, uint32_t denom)
{
	const struct spa_pod_fraction p = SPA_POD_INIT_Fraction(SPA_FRACTION(num, denom));
	return spa_pod_builder_primitive(builder, &p.pod);
}

SPA_API_POD_BUILDER int
spa_pod_builder_push_array(struct spa_pod_builder *builder, struct spa_pod_frame *frame)
{
	const struct spa_pod_array p =
	    { {sizeof(struct spa_pod_array_body) - sizeof(struct spa_pod), SPA_TYPE_Array},
	    {{0, 0}} };
	uint32_t offset = builder->state.offset;
	int res = spa_pod_builder_raw(builder, &p, sizeof(p) - sizeof(struct spa_pod));
	spa_pod_builder_push(builder, frame, &p.pod, offset);
	return res;
}

SPA_API_POD_BUILDER int
spa_pod_builder_array(struct spa_pod_builder *builder,
		      uint32_t child_size, uint32_t child_type, uint32_t n_elems, const void *elems)
{
	const struct spa_pod_array p = {
		{(uint32_t)(sizeof(struct spa_pod_array_body) + n_elems * child_size), SPA_TYPE_Array},
		{{child_size, child_type}}
	};
	return spa_pod_builder_primitive_body(builder, &p.pod, elems, n_elems * child_size, NULL, 0);
}

#define SPA_POD_INIT_CHOICE_BODY(type, flags, child_size, child_type)				\
	((struct spa_pod_choice_body) { (type), (flags), { (child_size), (child_type) }})

#define SPA_POD_INIT_Choice(type, ctype, child_type, n_vals, ...)				\
	((struct { struct spa_pod_choice choice; ctype vals[(n_vals)];})			\
	{ { { (n_vals) * sizeof(ctype) + sizeof(struct spa_pod_choice_body), SPA_TYPE_Choice },	\
		{ (type), 0, { sizeof(ctype), (child_type) } } }, { __VA_ARGS__ } })

SPA_API_POD_BUILDER int
spa_pod_builder_push_choice(struct spa_pod_builder *builder, struct spa_pod_frame *frame,
		uint32_t type, uint32_t flags)
{
	const struct spa_pod_choice p =
	    { {sizeof(struct spa_pod_choice_body) - sizeof(struct spa_pod), SPA_TYPE_Choice},
	    { type, flags, {0, 0}} };
	uint32_t offset = builder->state.offset;
	int res = spa_pod_builder_raw(builder, &p, sizeof(p) - sizeof(struct spa_pod));
	spa_pod_builder_push(builder, frame, &p.pod, offset);
	return res;
}

#define SPA_POD_INIT_Struct(size) ((struct spa_pod_struct){ { (size), SPA_TYPE_Struct } })

SPA_API_POD_BUILDER int
spa_pod_builder_push_struct(struct spa_pod_builder *builder, struct spa_pod_frame *frame)
{
	const struct spa_pod_struct p = SPA_POD_INIT_Struct(0);
	uint32_t offset = builder->state.offset;
	int res = spa_pod_builder_raw(builder, &p, sizeof(p));
	spa_pod_builder_push(builder, frame, &p.pod, offset);
	return res;
}

#define SPA_POD_INIT_Object(size,type,id,...)	((struct spa_pod_object){ { (size), SPA_TYPE_Object }, { (type), (id) }, ##__VA_ARGS__ })

SPA_API_POD_BUILDER int
spa_pod_builder_push_object(struct spa_pod_builder *builder, struct spa_pod_frame *frame,
		uint32_t type, uint32_t id)
{
	const struct spa_pod_object p =
	    SPA_POD_INIT_Object(sizeof(struct spa_pod_object_body), type, id);
	uint32_t offset = builder->state.offset;
	int res = spa_pod_builder_raw(builder, &p, sizeof(p));
	spa_pod_builder_push(builder, frame, &p.pod, offset);
	return res;
}

#define SPA_POD_INIT_Prop(key,flags,size,type)	\
	((struct spa_pod_prop){ (key), (flags), { (size), (type) } })

SPA_API_POD_BUILDER int
spa_pod_builder_prop(struct spa_pod_builder *builder, uint32_t key, uint32_t flags)
{
	const struct { uint32_t key; uint32_t flags; } p = { key, flags };
	return spa_pod_builder_raw(builder, &p, sizeof(p));
}

#define SPA_POD_INIT_Sequence(size,unit)	\
	((struct spa_pod_sequence){ { (size), SPA_TYPE_Sequence}, {(unit), 0 } })

SPA_API_POD_BUILDER int
spa_pod_builder_push_sequence(struct spa_pod_builder *builder, struct spa_pod_frame *frame, uint32_t unit)
{
	const struct spa_pod_sequence p =
	    SPA_POD_INIT_Sequence(sizeof(struct spa_pod_sequence_body), unit);
	uint32_t offset = builder->state.offset;
	int res = spa_pod_builder_raw(builder, &p, sizeof(p));
	spa_pod_builder_push(builder, frame, &p.pod, offset);
	return res;
}

SPA_API_POD_BUILDER int
spa_pod_builder_control(struct spa_pod_builder *builder, uint32_t offset, uint32_t type)
{
	const struct { uint32_t offset; uint32_t type; } p = { offset, type };
	return spa_pod_builder_raw(builder, &p, sizeof(p));
}

SPA_API_POD_BUILDER uint32_t spa_choice_from_id_flags(char id, uint32_t *flags)
{
	switch (id) {
	case 'r':
		return SPA_CHOICE_Range;
	case 's':
		return SPA_CHOICE_Step;
	case 'e':
		return SPA_CHOICE_Enum;
	case 'F':
		*flags |= SPA_POD_PROP_FLAG_DROP;
		SPA_FALLTHROUGH;
	case 'f':
		return SPA_CHOICE_Flags;
	case 'n':
	default:
		return SPA_CHOICE_None;
	}
}
SPA_API_POD_BUILDER uint32_t spa_choice_from_id(char id)
{
	uint32_t flags = 0;
	return spa_choice_from_id_flags(id, &flags);
}

#define SPA_POD_BUILDER_COLLECT(builder,type,args)				\
do {										\
	switch (type) {								\
	case 'b':								\
		spa_pod_builder_bool(builder, !!va_arg(args, int));		\
		break;								\
	case 'I':								\
		spa_pod_builder_id(builder, va_arg(args, uint32_t));		\
		break;								\
	case 'i':								\
		spa_pod_builder_int(builder, va_arg(args, int));		\
		break;								\
	case 'l':								\
		spa_pod_builder_long(builder, va_arg(args, int64_t));		\
		break;								\
	case 'f':								\
		spa_pod_builder_float(builder, (float)va_arg(args, double));	\
		break;								\
	case 'd':								\
		spa_pod_builder_double(builder, va_arg(args, double));		\
		break;								\
	case 's':								\
	{									\
		char *strval = va_arg(args, char *);				\
		if (strval != NULL) {						\
			size_t len = strlen(strval);				\
			spa_pod_builder_string_len(builder, strval, len);	\
		}								\
		else								\
			spa_pod_builder_none(builder);				\
		break;								\
	}									\
	case 'S':								\
	{									\
		char *strval = va_arg(args, char *);				\
		size_t len = va_arg(args, int);					\
		spa_pod_builder_string_len(builder, strval, len);		\
		break;								\
	}									\
	case 'y':								\
	{									\
		void *ptr  = va_arg(args, void *);				\
		int len = va_arg(args, int);					\
		spa_pod_builder_bytes(builder, ptr, len);			\
		break;								\
	}									\
	case 'R':								\
	{									\
		struct spa_rectangle *rectval =					\
			va_arg(args, struct spa_rectangle *);			\
		spa_pod_builder_rectangle(builder,				\
				rectval->width, rectval->height);		\
		break;								\
	}									\
	case 'F':								\
	{									\
		struct spa_fraction *fracval =					\
			va_arg(args, struct spa_fraction *);			\
		spa_pod_builder_fraction(builder, fracval->num, fracval->denom);\
		break;								\
	}									\
	case 'a':								\
	{									\
		int child_size = va_arg(args, int);				\
		int child_type = va_arg(args, int);				\
		int n_elems = va_arg(args, int);				\
		void *elems = va_arg(args, void *);				\
		spa_pod_builder_array(builder, child_size,			\
				child_type, n_elems, elems);			\
		break;								\
	}									\
	case 'p':								\
	{									\
		int t = va_arg(args, uint32_t);					\
		spa_pod_builder_pointer(builder, t, va_arg(args, void *));	\
		break;								\
	}									\
	case 'h':								\
		spa_pod_builder_fd(builder, va_arg(args, int));			\
		break;								\
	case 'P':								\
	case 'O':								\
	case 'T':								\
	case 'V':								\
	{									\
		struct spa_pod *pod = va_arg(args, struct spa_pod *);		\
		if (pod == NULL)						\
			spa_pod_builder_none(builder);				\
		else								\
			spa_pod_builder_primitive(builder, pod);		\
		break;								\
	}									\
	case 'Q':								\
	case 'N':								\
	case 'U':								\
	case 'W':								\
	{									\
		struct spa_pod *pod = va_arg(args, struct spa_pod *);		\
		const void *body = va_arg(args, const void *);			\
		spa_pod_builder_primitive_body(builder, pod,			\
					body, pod->size, NULL, 0);		\
		break;								\
	}									\
	}									\
} while(false)

SPA_API_POD_BUILDER int
spa_pod_builder_addv(struct spa_pod_builder *builder, va_list args)
{
	int res = 0;
	struct spa_pod_frame *frame = builder->state.frame;
	uint32_t ftype = frame ? frame->pod.type : (uint32_t)SPA_TYPE_None;

	do {
		const char *format;
		int n_values = 1;
		struct spa_pod_frame f;
		bool choice;
		uint32_t key = 0, flags = 0, offset = 0, type = 0, ctype = 0;

		switch (ftype) {
		case SPA_TYPE_Object:
			key = va_arg(args, uint32_t);
			if (key == 0)
				goto exit;
			if (key == SPA_ID_INVALID) {
				key = va_arg(args, uint32_t);
				flags = va_arg(args, uint32_t);
			}
			break;
		case SPA_TYPE_Sequence:
			offset = va_arg(args, uint32_t);
			type = va_arg(args, uint32_t);
			if (type == 0)
				goto exit;
			break;
		}


		if ((format = va_arg(args, const char *)) == NULL)
			break;

		choice = *format == '?';
		if (choice) {
			ctype = spa_choice_from_id_flags(*++format, &flags);
			if (*format != '\0')
				format++;
		}
		switch (ftype) {
		case SPA_TYPE_Object:
			spa_pod_builder_prop(builder, key, flags);
			break;
		case SPA_TYPE_Sequence:
			spa_pod_builder_control(builder, offset, type);
			break;
		}
		if (choice) {
			spa_pod_builder_push_choice(builder, &f, ctype, 0);
			n_values = va_arg(args, int);
		}
		while (n_values-- > 0)
			SPA_POD_BUILDER_COLLECT(builder, *format, args);

		if (choice)
			spa_pod_builder_pop(builder, &f);
	} while (true);

      exit:
	return res;
}

SPA_API_POD_BUILDER int spa_pod_builder_add(struct spa_pod_builder *builder, ...)
{
	int res;
	va_list args;

	va_start(args, builder);
	res = spa_pod_builder_addv(builder, args);
	va_end(args);

	return res;
}

#define spa_pod_builder_add_object(b,type,id,...)				\
({										\
	struct spa_pod_builder *_b = (b);					\
	struct spa_pod_frame _f;						\
	spa_pod_builder_push_object(_b, &_f, type, id);				\
	spa_pod_builder_add(_b, ##__VA_ARGS__, 0);				\
	spa_pod_builder_pop(_b, &_f);						\
})

#define spa_pod_builder_add_struct(b,...)					\
({										\
	struct spa_pod_builder *_b = (b);					\
	struct spa_pod_frame _f;						\
	spa_pod_builder_push_struct(_b, &_f);					\
	spa_pod_builder_add(_b, ##__VA_ARGS__, NULL);				\
	spa_pod_builder_pop(_b, &_f);						\
})

#define spa_pod_builder_add_sequence(b,unit,...)				\
({										\
	struct spa_pod_builder *_b = (b);					\
	struct spa_pod_frame _f;						\
	spa_pod_builder_push_sequence(_b, &_f, unit);				\
	spa_pod_builder_add(_b, ##__VA_ARGS__, 0, 0);				\
	spa_pod_builder_pop(_b, &_f);						\
})

/** Copy a pod structure */
SPA_API_POD_BUILDER struct spa_pod *
spa_pod_copy(const struct spa_pod *pod)
{
	size_t size;
	struct spa_pod *c;

	size = SPA_POD_SIZE(pod);
	if ((c = (struct spa_pod *) malloc(size)) == NULL)
		return NULL;
	return (struct spa_pod *) memcpy(c, pod, size);
}

/**
 * \}
 */

#ifdef __cplusplus
}  /* extern "C" */
#endif

#endif /* SPA_POD_BUILDER_H */
