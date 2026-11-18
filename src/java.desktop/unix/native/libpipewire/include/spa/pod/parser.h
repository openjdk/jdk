/* Spa */
/* SPDX-FileCopyrightText: Copyright © 2018 Wim Taymans */
/* SPDX-License-Identifier: MIT */

#ifndef SPA_POD_PARSER_H
#define SPA_POD_PARSER_H

#include <errno.h>
#include <stdarg.h>

#include <spa/pod/body.h>
#include <spa/pod/vararg.h>

#ifdef __cplusplus
extern "C" {
#endif

#ifndef SPA_API_POD_PARSER
 #ifdef SPA_API_IMPL
  #define SPA_API_POD_PARSER SPA_API_IMPL
 #else
  #define SPA_API_POD_PARSER static inline
 #endif
#endif

/**
 * \addtogroup spa_pod
 * \{
 */

struct spa_pod_parser_state {
	uint32_t offset;
	uint32_t flags;
	struct spa_pod_frame *frame;
};

struct spa_pod_parser {
	const void *data;
	uint32_t size;
	uint32_t _padding;
	struct spa_pod_parser_state state;
};

#define SPA_POD_PARSER_INIT(buffer,size)  ((struct spa_pod_parser){ (buffer), (size), 0, {0,0,NULL}})

SPA_API_POD_PARSER void spa_pod_parser_init(struct spa_pod_parser *parser,
				       const void *data, uint32_t size)
{
	*parser = SPA_POD_PARSER_INIT(data, size);
}

SPA_API_POD_PARSER void spa_pod_parser_pod(struct spa_pod_parser *parser,
				      const struct spa_pod *pod)
{
	spa_pod_parser_init(parser, pod, SPA_POD_SIZE(pod));
}

SPA_API_POD_PARSER void spa_pod_parser_init_pod_body(struct spa_pod_parser *parser,
	      const struct spa_pod *pod, const void *body)
{
	spa_pod_parser_init(parser,
			SPA_PTROFF(body, -sizeof(struct spa_pod), const struct spa_pod),
			pod->size + sizeof(struct spa_pod));
}
SPA_API_POD_PARSER void spa_pod_parser_init_from_data(struct spa_pod_parser *parser,
		const void *data, uint32_t maxsize, uint32_t offset, uint32_t size)
{
	size_t offs, sz;
	offs = SPA_MIN(offset, maxsize);
	sz = SPA_MIN(maxsize - offs, size);
	spa_pod_parser_init(parser, SPA_PTROFF(data, offs, void), sz);
}

SPA_API_POD_PARSER void
spa_pod_parser_get_state(struct spa_pod_parser *parser, struct spa_pod_parser_state *state)
{
	*state = parser->state;
}

SPA_API_POD_PARSER void
spa_pod_parser_reset(struct spa_pod_parser *parser, struct spa_pod_parser_state *state)
{
	parser->state = *state;
}

SPA_API_POD_PARSER int
spa_pod_parser_read_header(struct spa_pod_parser *parser, uint32_t offset, uint32_t size,
		void *header, uint32_t header_size, uint32_t pod_offset, const void **body)
{
	/* Cast to uint64_t to avoid wraparound. */
	const uint64_t long_offset = (uint64_t)offset + header_size;
	if (long_offset <= size && (offset & 7) == 0) {
		struct spa_pod *pod;
		/* a barrier around the memcpy to make sure it is not moved around or
		 * duplicated after the size check below. We need to work on shared
		 * memory and so there could be updates happening while we read. */
		SPA_BARRIER;
		memcpy(header, SPA_PTROFF(parser->data, offset, void), header_size);
		SPA_BARRIER;
		pod = SPA_PTROFF(header, pod_offset, struct spa_pod);
		/* Check that the size (rounded to the next multiple of 8) is in bounds. */
		if (long_offset + SPA_ROUND_UP_N((uint64_t)pod->size, SPA_POD_ALIGN) <= size) {
			*body = SPA_PTROFF(parser->data, long_offset, void);
			return 0;
		}
	}
	return -EPIPE;
}

SPA_API_POD_PARSER struct spa_pod *
spa_pod_parser_deref(struct spa_pod_parser *parser, uint32_t offset, uint32_t size)
{
	struct spa_pod pod;
	const void *body;
	if (spa_pod_parser_read_header(parser, offset, size, &pod, sizeof(pod), 0, &body) < 0)
		return NULL;
	return SPA_PTROFF(body, -sizeof(pod), struct spa_pod);
}

SPA_API_POD_PARSER struct spa_pod *spa_pod_parser_frame(struct spa_pod_parser *parser, struct spa_pod_frame *frame)
{
	return SPA_PTROFF(parser->data, frame->offset, struct spa_pod);
}

SPA_API_POD_PARSER void spa_pod_parser_push(struct spa_pod_parser *parser,
		      struct spa_pod_frame *frame, const struct spa_pod *pod, uint32_t offset)
{
	frame->pod = *pod;
	frame->offset = offset;
	frame->parent = parser->state.frame;
	frame->flags = parser->state.flags;
	parser->state.frame = frame;
}

SPA_API_POD_PARSER int spa_pod_parser_get_header(struct spa_pod_parser *parser,
		void *header, uint32_t header_size, uint32_t pod_offset, const void **body)
{
	struct spa_pod_frame *f = parser->state.frame;
	uint32_t size = f ? f->offset + SPA_POD_SIZE(&f->pod) : parser->size;
	return spa_pod_parser_read_header(parser, parser->state.offset, size,
			header, header_size, pod_offset, body);
}

SPA_API_POD_PARSER int spa_pod_parser_current_body(struct spa_pod_parser *parser,
		struct spa_pod *pod, const void **body)
{
	return spa_pod_parser_get_header(parser, pod, sizeof(struct spa_pod), 0, body);
}

SPA_API_POD_PARSER struct spa_pod *spa_pod_parser_current(struct spa_pod_parser *parser)
{
	struct spa_pod pod;
	const void *body;
	if (spa_pod_parser_current_body(parser, &pod, &body) < 0)
		return NULL;
	return SPA_PTROFF(body, -sizeof(struct spa_pod), struct spa_pod);
}

SPA_API_POD_PARSER void spa_pod_parser_advance(struct spa_pod_parser *parser, const struct spa_pod *pod)
{
	parser->state.offset += SPA_ROUND_UP_N(SPA_POD_SIZE(pod), SPA_POD_ALIGN);
}

SPA_API_POD_PARSER int spa_pod_parser_next_body(struct spa_pod_parser *parser,
		struct spa_pod *pod, const void **body)
{
	if (spa_pod_parser_current_body(parser, pod, body) < 0)
		return -EINVAL;
	spa_pod_parser_advance(parser, pod);
	return 0;
}

SPA_API_POD_PARSER struct spa_pod *spa_pod_parser_next(struct spa_pod_parser *parser)
{
	struct spa_pod pod;
	const void *body;
	if (spa_pod_parser_current_body(parser, &pod, &body) < 0)
		return NULL;
	spa_pod_parser_advance(parser, &pod);
	return SPA_PTROFF(body, -sizeof(struct spa_pod), struct spa_pod);
}

SPA_API_POD_PARSER void spa_pod_parser_restart(struct spa_pod_parser *parser,
		      struct spa_pod_frame *frame)
{
	parser->state.offset = frame->offset;
}

SPA_API_POD_PARSER void spa_pod_parser_unpush(struct spa_pod_parser *parser,
		      struct spa_pod_frame *frame)
{
	spa_pod_parser_restart(parser, frame);
	parser->state.frame = frame->parent;
}

SPA_API_POD_PARSER int spa_pod_parser_pop(struct spa_pod_parser *parser,
		      struct spa_pod_frame *frame)
{
	spa_pod_parser_unpush(parser, frame);
	spa_pod_parser_advance(parser, &frame->pod);
	return 0;
}

SPA_API_POD_PARSER int spa_pod_parser_get_bool(struct spa_pod_parser *parser, bool *value)
{
	int res;
	struct spa_pod pod;
	const void *body;
	if ((res = spa_pod_parser_current_body(parser, &pod, &body)) < 0)
		return res;
	if ((res = spa_pod_body_get_bool(&pod, body, value)) >= 0)
		spa_pod_parser_advance(parser, &pod);
	return res;
}

SPA_API_POD_PARSER int spa_pod_parser_get_id(struct spa_pod_parser *parser, uint32_t *value)
{
	int res;
	struct spa_pod pod;
	const void *body;
	if ((res = spa_pod_parser_current_body(parser, &pod, &body)) < 0)
		return res;
	if ((res = spa_pod_body_get_id(&pod, body, value)) >= 0)
		spa_pod_parser_advance(parser, &pod);
	return res;
}

SPA_API_POD_PARSER int spa_pod_parser_get_int(struct spa_pod_parser *parser, int32_t *value)
{
	int res;
	struct spa_pod pod;
	const void *body;
	if ((res = spa_pod_parser_current_body(parser, &pod, &body)) < 0)
		return res;
	if ((res = spa_pod_body_get_int(&pod, body, value)) >= 0)
		spa_pod_parser_advance(parser, &pod);
	return res;
}

SPA_API_POD_PARSER int spa_pod_parser_get_long(struct spa_pod_parser *parser, int64_t *value)
{
	int res;
	struct spa_pod pod;
	const void *body;
	if ((res = spa_pod_parser_current_body(parser, &pod, &body)) < 0)
		return res;
	if ((res = spa_pod_body_get_long(&pod, body, value)) >= 0)
		spa_pod_parser_advance(parser, &pod);
	return res;
}

SPA_API_POD_PARSER int spa_pod_parser_get_float(struct spa_pod_parser *parser, float *value)
{
	int res;
	struct spa_pod pod;
	const void *body;
	if ((res = spa_pod_parser_current_body(parser, &pod, &body)) < 0)
		return res;
	if ((res = spa_pod_body_get_float(&pod, body, value)) >= 0)
		spa_pod_parser_advance(parser, &pod);
	return res;
}

SPA_API_POD_PARSER int spa_pod_parser_get_double(struct spa_pod_parser *parser, double *value)
{
	int res;
	struct spa_pod pod;
	const void *body;
	if ((res = spa_pod_parser_current_body(parser, &pod, &body)) < 0)
		return res;
	if ((res = spa_pod_body_get_double(&pod, body, value)) >= 0)
		spa_pod_parser_advance(parser, &pod);
	return res;
}

SPA_API_POD_PARSER int spa_pod_parser_get_string(struct spa_pod_parser *parser, const char **value)
{
	int res;
	struct spa_pod pod;
	const void *body;
	if ((res = spa_pod_parser_current_body(parser, &pod, &body)) < 0)
		return res;
	if ((res = spa_pod_body_get_string(&pod, body, value)) >= 0)
		spa_pod_parser_advance(parser, &pod);
	return res;
}

SPA_API_POD_PARSER int spa_pod_parser_get_bytes(struct spa_pod_parser *parser, const void **value, uint32_t *len)
{
	int res;
	struct spa_pod pod;
	const void *body;
	if ((res = spa_pod_parser_current_body(parser, &pod, &body)) < 0)
		return res;
	if ((res = spa_pod_body_get_bytes(&pod, body, value, len)) >= 0)
		spa_pod_parser_advance(parser, &pod);
	return res;
}

SPA_API_POD_PARSER int spa_pod_parser_get_pointer(struct spa_pod_parser *parser, uint32_t *type, const void **value)
{
	int res;
	struct spa_pod pod;
	const void *body;
	if ((res = spa_pod_parser_current_body(parser, &pod, &body)) < 0)
		return res;
	if ((res = spa_pod_body_get_pointer(&pod, body, type, value)) >= 0)
		spa_pod_parser_advance(parser, &pod);
	return res;
}

SPA_API_POD_PARSER int spa_pod_parser_get_fd(struct spa_pod_parser *parser, int64_t *value)
{
	int res;
	struct spa_pod pod;
	const void *body;
	if ((res = spa_pod_parser_current_body(parser, &pod, &body)) < 0)
		return res;
	if ((res = spa_pod_body_get_fd(&pod, body, value)) >= 0)
		spa_pod_parser_advance(parser, &pod);
	return res;
}

SPA_API_POD_PARSER int spa_pod_parser_get_rectangle(struct spa_pod_parser *parser, struct spa_rectangle *value)
{
	int res;
	struct spa_pod pod;
	const void *body;
	if ((res = spa_pod_parser_current_body(parser, &pod, &body)) < 0)
		return res;
	if ((res = spa_pod_body_get_rectangle(&pod, body, value)) >= 0)
		spa_pod_parser_advance(parser, &pod);
	return res;
}

SPA_API_POD_PARSER int spa_pod_parser_get_fraction(struct spa_pod_parser *parser, struct spa_fraction *value)
{
	int res;
	struct spa_pod pod;
	const void *body;
	if ((res = spa_pod_parser_current_body(parser, &pod, &body)) < 0)
		return res;
	if ((res = spa_pod_body_get_fraction(&pod, body, value)) >= 0)
		spa_pod_parser_advance(parser, &pod);
	return res;
}

SPA_API_POD_PARSER int spa_pod_parser_get_pod_body(struct spa_pod_parser *parser,
		struct spa_pod *value, const void **body)
{
	int res;
	if ((res = spa_pod_parser_current_body(parser, value, body)) < 0)
		return res;
	spa_pod_parser_advance(parser, value);
	return 0;
}

SPA_API_POD_PARSER int spa_pod_parser_get_pod(struct spa_pod_parser *parser, struct spa_pod **value)
{
	int res;
	struct spa_pod pod;
	const void *body;
	if ((res = spa_pod_parser_get_pod_body(parser, &pod, &body)) < 0)
		return res;
	*value = SPA_PTROFF(body, -sizeof(struct spa_pod), struct spa_pod);
	return 0;
}

SPA_API_POD_PARSER int spa_pod_parser_init_struct_body(struct spa_pod_parser *parser,
		struct spa_pod_frame *frame, const struct spa_pod *pod, const void *body)
{
	if (!spa_pod_is_struct(pod))
		return -EINVAL;
	spa_pod_parser_init_pod_body(parser, pod, body);
	spa_pod_parser_push(parser, frame, pod, parser->state.offset);
	parser->state.offset += sizeof(struct spa_pod_struct);
	return 0;
}

SPA_API_POD_PARSER int spa_pod_parser_push_struct_body(struct spa_pod_parser *parser,
		struct spa_pod_frame *frame, struct spa_pod *str, const void **str_body)
{
	int res;
	if ((res = spa_pod_parser_current_body(parser, str, str_body)) < 0)
		return res;
	if (!spa_pod_is_struct(str))
		return -EINVAL;
	spa_pod_parser_push(parser, frame, str, parser->state.offset);
	parser->state.offset += sizeof(struct spa_pod_struct);
	return 0;
}
SPA_API_POD_PARSER int spa_pod_parser_push_struct(struct spa_pod_parser *parser,
		struct spa_pod_frame *frame)
{
	struct spa_pod pod;
	const void *body;
	return spa_pod_parser_push_struct_body(parser, frame, &pod, &body);
}

SPA_API_POD_PARSER int spa_pod_parser_init_object_body(struct spa_pod_parser *parser,
		struct spa_pod_frame *frame, const struct spa_pod *pod, const void *body,
		struct spa_pod_object *object, const void **object_body)
{
	int res;
	if (!spa_pod_is_object(pod))
		return -EINVAL;
	spa_pod_parser_init_pod_body(parser, pod, body);
	if ((res = spa_pod_body_get_object(pod, body, object, object_body)) < 0)
		return res;
	spa_pod_parser_push(parser, frame, pod, parser->state.offset);
	parser->state.offset += sizeof(struct spa_pod_object);
	return 0;
}

SPA_API_POD_PARSER int spa_pod_parser_push_object_body(struct spa_pod_parser *parser,
		struct spa_pod_frame *frame, struct spa_pod_object *object, const void **object_body)
{
	int res;
	struct spa_pod pod;
	const void *body;
	if ((res = spa_pod_parser_current_body(parser, &pod, &body)) < 0)
		return res;
	if ((res = spa_pod_body_get_object(&pod, body, object, object_body)) < 0)
		return res;
	spa_pod_parser_push(parser, frame, &pod, parser->state.offset);
	parser->state.offset += sizeof(struct spa_pod_object);
	return 0;
}
SPA_API_POD_PARSER int spa_pod_parser_push_object(struct spa_pod_parser *parser,
		struct spa_pod_frame *frame, uint32_t type, uint32_t *id)
{
	int res;
	struct spa_pod_object obj;
	const void *obj_body;
	if ((res = spa_pod_parser_push_object_body(parser, frame, &obj, &obj_body)) < 0)
		return res;
	if (type != obj.body.type) {
		spa_pod_parser_unpush(parser, frame);
		return -EPROTO;
	}
	if (id != NULL)
		*id = obj.body.id;
	return 0;
}
SPA_API_POD_PARSER int spa_pod_parser_get_prop_body(struct spa_pod_parser *parser,
		struct spa_pod_prop *prop, const void **body)
{
	int res;
	if ((res = spa_pod_parser_get_header(parser, prop,
			sizeof(struct spa_pod_prop),
			offsetof(struct spa_pod_prop, value), body)) >= 0)
		parser->state.offset += SPA_ROUND_UP_N(SPA_POD_PROP_SIZE(prop), SPA_POD_ALIGN);
	return res;
}

SPA_API_POD_PARSER int spa_pod_parser_push_sequence_body(struct spa_pod_parser *parser,
		struct spa_pod_frame *frame, struct spa_pod_sequence *seq, const void **seq_body)
{
	int res;
	struct spa_pod pod;
	const void *body;
	if ((res = spa_pod_parser_current_body(parser, &pod, &body)) < 0)
		return res;
	if ((res = spa_pod_body_get_sequence(&pod, body, seq, seq_body)) < 0)
		return res;
	spa_pod_parser_push(parser, frame, &pod, parser->state.offset);
	parser->state.offset += sizeof(struct spa_pod_sequence);
	return 0;
}

SPA_API_POD_PARSER int spa_pod_parser_get_control_body(struct spa_pod_parser *parser,
		struct spa_pod_control *control, const void **body)
{
	int res;
	if ((res = spa_pod_parser_get_header(parser, control,
			sizeof(struct spa_pod_control),
			offsetof(struct spa_pod_control, value), body)) >= 0)
		parser->state.offset += SPA_ROUND_UP_N(SPA_POD_CONTROL_SIZE(control), SPA_POD_ALIGN);
	return res;
}

SPA_API_POD_PARSER int spa_pod_parser_object_find_prop(struct spa_pod_parser *parser,
		uint32_t key, struct spa_pod_prop *prop, const void **body)
{
	uint32_t start_offset;
	struct spa_pod_frame *f = parser->state.frame;

	if (f == NULL || f->pod.type != SPA_TYPE_Object)
		return -EINVAL;

	start_offset = f->offset;
	while (spa_pod_parser_get_prop_body(parser, prop, body) >= 0) {
		if (prop->key == key)
			return 0;
	}
	spa_pod_parser_restart(parser, f);
	parser->state.offset += sizeof(struct spa_pod_object);
	while (parser->state.offset != start_offset &&
	       spa_pod_parser_get_prop_body(parser, prop, body) >= 0) {
		if (prop->key == key)
			return 0;
	}
	*body = NULL;
	return -ENOENT;
}

SPA_API_POD_PARSER bool spa_pod_parser_body_can_collect(const struct spa_pod *pod, const void *body, char type)
{
	struct spa_pod_choice choice;

	if (pod == NULL)
		return false;

	if (pod->type == SPA_TYPE_Choice) {
		if (!spa_pod_is_choice(pod))
			return false;
		if (type == 'V' || type == 'W')
			return true;
		if (spa_pod_body_get_choice(pod, body, &choice, &body) < 0)
			return false;
		if (choice.body.type != SPA_CHOICE_None)
			return false;
		pod = &choice.body.child;
	}

	switch (type) {
	case 'P':
	case 'Q':
		return true;
	case 'b':
		return spa_pod_is_bool(pod);
	case 'I':
		return spa_pod_is_id(pod);
	case 'i':
		return spa_pod_is_int(pod);
	case 'l':
		return spa_pod_is_long(pod);
	case 'f':
		return spa_pod_is_float(pod);
	case 'd':
		return spa_pod_is_double(pod);
	case 's':
		return spa_pod_is_string(pod) || spa_pod_is_none(pod);
	case 'S':
		return spa_pod_is_string(pod);
	case 'y':
		return spa_pod_is_bytes(pod);
	case 'R':
		return spa_pod_is_rectangle(pod);
	case 'F':
		return spa_pod_is_fraction(pod);
	case 'B':
		return spa_pod_is_bitmap(pod);
	case 'a':
		return spa_pod_is_array(pod);
	case 'p':
		return spa_pod_is_pointer(pod);
	case 'h':
		return spa_pod_is_fd(pod);
	case 'T':
	case 'U':
		return spa_pod_is_struct(pod) || spa_pod_is_none(pod);
	case 'N':
	case 'O':
		return spa_pod_is_object(pod) || spa_pod_is_none(pod);
	case 'V':
	case 'W':
	default:
		return false;
	}
}
SPA_API_POD_PARSER bool spa_pod_parser_can_collect(const struct spa_pod *pod, char type)
{
	return spa_pod_parser_body_can_collect(pod, SPA_POD_BODY_CONST(pod), type);
}

#define SPA_POD_PARSER_COLLECT_BODY(_pod,_body,_type,args)				\
({											\
	int res = 0;									\
	struct spa_pod_choice choice;							\
	const struct spa_pod *_p = _pod;						\
	const void *_b = _body;								\
	if (_p->type == SPA_TYPE_Choice && _type != 'V' && _type != 'W') {		\
		if (spa_pod_body_get_choice(_p, _b, &choice, &_b) >= 0 &&		\
		    choice.body.type == SPA_CHOICE_None)				\
			_p = &choice.body.child;					\
	}										\
	switch (_type) {								\
	case 'b':									\
	{										\
		bool *val = va_arg(args, bool*);					\
		res = spa_pod_body_get_bool(_p, _b, val);				\
		break;									\
	}										\
	case 'I':									\
	{										\
		uint32_t *val = va_arg(args, uint32_t*);				\
		res = spa_pod_body_get_id(_p, _b, val);					\
		break;									\
	}										\
	case 'i':									\
	{										\
		int32_t *val = va_arg(args, int32_t*);					\
		res = spa_pod_body_get_int(_p, _b, val);				\
		break;									\
	}										\
	case 'l':									\
	{										\
		int64_t *val = va_arg(args, int64_t*);					\
		res = spa_pod_body_get_long(_p, _b, val);				\
		break;									\
	}										\
	case 'f':									\
	{										\
		float *val = va_arg(args, float*);					\
		res = spa_pod_body_get_float(_p, _b, val);				\
		break;									\
	}										\
	case 'd':									\
	{										\
		double *val = va_arg(args, double*);					\
		res = spa_pod_body_get_double(_p, _b, val);				\
		break;									\
	}										\
	case 's':									\
	{										\
		const char **dest = va_arg(args, const char**);				\
		if (_p->type == SPA_TYPE_None)					\
			*dest = NULL;							\
		else									\
			res = spa_pod_body_get_string(_p, _b, dest);			\
		break;									\
	}										\
	case 'S':									\
	{										\
		char *dest = va_arg(args, char*);					\
		uint32_t maxlen = va_arg(args, uint32_t);				\
		res = spa_pod_body_copy_string(_p, _b, dest, maxlen);		\
		break;									\
	}										\
	case 'y':									\
	{										\
		const void **value = va_arg(args, const void**);			\
		uint32_t *len = va_arg(args, uint32_t*);				\
		res = spa_pod_body_get_bytes(_p, _b, value, len);			\
		break;									\
	}										\
	case 'R':									\
	{										\
		struct spa_rectangle *val = va_arg(args, struct spa_rectangle*);	\
		res = spa_pod_body_get_rectangle(_p, _b, val);			\
		break;									\
	}										\
	case 'F':									\
	{										\
		struct spa_fraction *val = va_arg(args, struct spa_fraction*);		\
		res = spa_pod_body_get_fraction(_p, _b, val);			\
		break;									\
	}										\
	case 'B':									\
	{										\
		const uint8_t **val = va_arg(args, const uint8_t**);			\
		res = spa_pod_body_get_bitmap(_p, _b, val);				\
		break;									\
	}										\
	case 'a':									\
	{										\
		uint32_t *val_size = va_arg(args, uint32_t*);				\
		uint32_t *val_type = va_arg(args, uint32_t*);				\
		uint32_t *n_values = va_arg(args, uint32_t*);				\
		const void **arr_body = va_arg(args, const void**);			\
		*arr_body = spa_pod_body_get_array_values(_p, _b,			\
				n_values, val_size, val_type);				\
		if (*arr_body == NULL)							\
			res = -EINVAL;							\
		break;									\
	}										\
	case 'p':									\
	{										\
		uint32_t *type = va_arg(args, uint32_t*);				\
		const void **value = va_arg(args, const void**);			\
		res = spa_pod_body_get_pointer(_p, _b, type, value);			\
		break;									\
	}										\
	case 'h':									\
	{										\
		int64_t *val = va_arg(args, int64_t*);					\
		res = spa_pod_body_get_fd(_p, _b, val);					\
		break;									\
	}										\
	default:									\
	{										\
		bool valid = false, do_body = false;					\
		switch (_type) {							\
		case 'Q':								\
			do_body = true;							\
			SPA_FALLTHROUGH;						\
		case 'P':								\
			valid = true;							\
			break;								\
		case 'U':								\
			do_body = true;							\
			SPA_FALLTHROUGH;						\
		case 'T':								\
			valid = spa_pod_is_struct(_p) || spa_pod_is_none(_p);		\
			break;								\
		case 'N':								\
			do_body = true;							\
			SPA_FALLTHROUGH;						\
		case 'O':								\
			valid = spa_pod_is_object(_p) || spa_pod_is_none(_p);		\
			break;								\
		case 'W':								\
			do_body = true;							\
			SPA_FALLTHROUGH;						\
		case 'V':								\
			valid = spa_pod_is_choice(_p) || spa_pod_is_none(_p);		\
			break;								\
		default:								\
			res = -EINVAL;							\
			break;								\
		}									\
		if (res >= 0 && do_body) {						\
			struct spa_pod *p = va_arg(args, struct spa_pod*);		\
			const void **v = va_arg(args, const void **);			\
			if (valid && p && v) {						\
				*p = *_p;						\
				*v = _b;						\
			}								\
		} else if (res >= 0) {							\
			const struct spa_pod **d = va_arg(args, const struct spa_pod**);\
			if (valid && d)							\
				*d = (_p->type == SPA_TYPE_None) ?			\
					NULL :						\
					SPA_PTROFF((_b), -sizeof(struct spa_pod),	\
						const struct spa_pod);			\
		}									\
		if (!valid)								\
			res = -EINVAL;							\
		break;									\
	}										\
	}										\
	res;										\
})

#define SPA_POD_PARSER_COLLECT(pod,_type,args)						\
	SPA_POD_PARSER_COLLECT_BODY(pod, SPA_POD_BODY_CONST(pod),_type,args)

#define SPA_POD_PARSER_SKIP(_type,args)							\
do {											\
	switch (_type) {								\
	case 'S':									\
		va_arg(args, char*);							\
		va_arg(args, uint32_t);							\
		break;									\
	case 'a':									\
		va_arg(args, void*);							\
		va_arg(args, void*);							\
		SPA_FALLTHROUGH 							\
	case 'p':									\
	case 'y':									\
		va_arg(args, void*);							\
		SPA_FALLTHROUGH 							\
	case 'b':									\
	case 'I':									\
	case 'i':									\
	case 'l':									\
	case 'f':									\
	case 'd':									\
	case 's':									\
	case 'R':									\
	case 'F':									\
	case 'B':									\
	case 'h':									\
	case 'V':									\
	case 'P':									\
	case 'T':									\
	case 'O':									\
	case 'W':									\
	case 'Q':									\
	case 'U':									\
	case 'N':									\
		va_arg(args, void*);							\
		break;									\
	}										\
} while(false)

SPA_API_POD_PARSER int spa_pod_parser_getv(struct spa_pod_parser *parser, va_list args)
{
	struct spa_pod_frame *f = parser->state.frame;
	int count = 0;

	if (f == NULL)
		return -EINVAL;

	do {
		bool optional;
		struct spa_pod pod = (struct spa_pod) { 0, SPA_TYPE_None };
		const void *body = NULL;
		const char *format;
		struct spa_pod_prop prop;

		if (f->pod.type == SPA_TYPE_Object) {
			uint32_t key = va_arg(args, uint32_t), *flags = NULL;

			if (key == 0)
				break;
			if (key == SPA_ID_INVALID) {
				key = va_arg(args, uint32_t);
				flags = va_arg(args, uint32_t*);
			}
			if (spa_pod_parser_object_find_prop(parser, key, &prop, &body) >= 0) {
				pod = prop.value;
				if (flags)
					*flags = prop.flags;
			}
		}

		if ((format = va_arg(args, char *)) == NULL)
			break;

		if (f->pod.type == SPA_TYPE_Struct)
			spa_pod_parser_next_body(parser, &pod, &body);

		if ((optional = (*format == '?')))
			format++;

		if (SPA_POD_PARSER_COLLECT_BODY(&pod, body, *format, args) >= 0) {
			count++;
		} else if (!optional) {
			if (body == NULL)
				return -ESRCH;
			else
				return -EPROTO;
		}
	} while (true);

	return count;
}

SPA_API_POD_PARSER int spa_pod_parser_get(struct spa_pod_parser *parser, ...)
{
	int res;
	va_list args;

	va_start(args, parser);
	res = spa_pod_parser_getv(parser, args);
	va_end(args);

	return res;
}

#define SPA_POD_OPT_Bool(val)				"?" SPA_POD_Bool(val)
#define SPA_POD_OPT_Id(val)				"?" SPA_POD_Id(val)
#define SPA_POD_OPT_Int(val)				"?" SPA_POD_Int(val)
#define SPA_POD_OPT_Long(val)				"?" SPA_POD_Long(val)
#define SPA_POD_OPT_Float(val)				"?" SPA_POD_Float(val)
#define SPA_POD_OPT_Double(val)				"?" SPA_POD_Double(val)
#define SPA_POD_OPT_String(val)				"?" SPA_POD_String(val)
#define SPA_POD_OPT_Stringn(val,len)			"?" SPA_POD_Stringn(val,len)
#define SPA_POD_OPT_Bytes(val,len)			"?" SPA_POD_Bytes(val,len)
#define SPA_POD_OPT_Rectangle(val)			"?" SPA_POD_Rectangle(val)
#define SPA_POD_OPT_Fraction(val)			"?" SPA_POD_Fraction(val)
#define SPA_POD_OPT_Array(csize,ctype,n_vals,vals)	"?" SPA_POD_Array(csize,ctype,n_vals,vals)
#define SPA_POD_OPT_Pointer(type,val)			"?" SPA_POD_Pointer(type,val)
#define SPA_POD_OPT_Fd(val)				"?" SPA_POD_Fd(val)
#define SPA_POD_OPT_Pod(val)				"?" SPA_POD_Pod(val)
#define SPA_POD_OPT_PodObject(val)			"?" SPA_POD_PodObject(val)
#define SPA_POD_OPT_PodStruct(val)			"?" SPA_POD_PodStruct(val)
#define SPA_POD_OPT_PodChoice(val)			"?" SPA_POD_PodChoice(val)
#define SPA_POD_OPT_PodBody(val,body)			"?" SPA_POD_PodBody(val,body)
#define SPA_POD_OPT_PodBodyObject(val,body)		"?" SPA_POD_PodBodyObject(val,body)
#define SPA_POD_OPT_PodBodyStruct(val,body)		"?" SPA_POD_PodBodyStruct(val,body)
#define SPA_POD_OPT_PodBodyChoice(val,body)		"?" SPA_POD_PodBodyChoice(val,body)

#define spa_pod_parser_get_object(p,type,id,...)				\
({										\
	struct spa_pod_frame _f;						\
	int _res;								\
	if ((_res = spa_pod_parser_push_object(p, &_f, type, id)) == 0) {	\
		_res = spa_pod_parser_get(p,##__VA_ARGS__, 0);			\
		spa_pod_parser_pop(p, &_f);					\
	}									\
	_res;									\
})

#define spa_pod_parser_get_struct(p,...)				\
({									\
	struct spa_pod_frame _f;					\
	int _res;							\
	if ((_res = spa_pod_parser_push_struct(p, &_f)) == 0) {		\
		_res = spa_pod_parser_get(p,##__VA_ARGS__, NULL);	\
		spa_pod_parser_pop(p, &_f);				\
	}								\
	_res;							\
})

#define spa_pod_body_parse_object(pod,body,type,id,...)		\
({								\
	struct spa_pod_parser _p;				\
	spa_pod_parser_init_pod_body(&_p, pod, body);		\
	spa_pod_parser_get_object(&_p,type,id,##__VA_ARGS__);	\
})

#define spa_pod_parse_object(pod,type,id,...)		\
	spa_pod_body_parse_object(pod,SPA_POD_BODY_CONST(pod),type,id,##__VA_ARGS__)

#define spa_pod_body_parse_struct(pod,body,...)			\
({								\
	struct spa_pod_parser _p;				\
	spa_pod_parser_init_pod_body(&_p, pod, body);		\
	spa_pod_parser_get_struct(&_p,##__VA_ARGS__);		\
})

#define spa_pod_parse_struct(pod,...)		\
	spa_pod_body_parse_struct(pod,SPA_POD_BODY_CONST(pod),##__VA_ARGS__)
/**
 * \}
 */

#ifdef __cplusplus
}  /* extern "C" */
#endif

#endif /* SPA_POD_PARSER_H */
