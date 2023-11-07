template <typename vtype, typename mm_t>
X86_SIMD_SORT_INLINE void COEX(mm_t &a, mm_t &b);

template <typename vtype, typename type_t>
X86_SIMD_SORT_INLINE type_t get_pivot(type_t *arr,
                                      const arrsize_t left,
                                      const arrsize_t right)
{
    using reg_t = typename vtype::reg_t;
    type_t samples[vtype::numlanes];
    arrsize_t delta = (right - left) / vtype::numlanes;
    for (int i = 0; i < vtype::numlanes; i++) {
        samples[i] = arr[left + i * delta];
    }
    reg_t rand_vec = vtype::loadu(samples);
    reg_t sort = vtype::sort_vec(rand_vec);

    return ((type_t *)&sort)[vtype::numlanes / 2];
}

template <typename vtype, typename type_t>
X86_SIMD_SORT_INLINE type_t get_pivot_blocks(type_t *arr,
                                             const arrsize_t left,
                                             const arrsize_t right)
{

    if (right - left <= 1024) { return get_pivot<vtype>(arr, left, right); }

    using reg_t = typename vtype::reg_t;
    constexpr int numVecs = 5;

    arrsize_t width = (right - vtype::numlanes) - left;
    arrsize_t delta = width / numVecs;

    reg_t vecs[numVecs];
    // Load data
    for (int i = 0; i < numVecs; i++) {
        vecs[i] = vtype::loadu(arr + left + delta * i);
    }

    // Implement sorting network (from https://bertdobbelaere.github.io/sorting_networks.html)
    COEX<vtype>(vecs[0], vecs[3]);
    COEX<vtype>(vecs[1], vecs[4]);

    COEX<vtype>(vecs[0], vecs[2]);
    COEX<vtype>(vecs[1], vecs[3]);

    COEX<vtype>(vecs[0], vecs[1]);
    COEX<vtype>(vecs[2], vecs[4]);

    COEX<vtype>(vecs[1], vecs[2]);
    COEX<vtype>(vecs[3], vecs[4]);

    COEX<vtype>(vecs[2], vecs[3]);

    // Calculate median of the middle vector
    reg_t &vec = vecs[numVecs / 2];
    vec = vtype::sort_vec(vec);

    type_t data[vtype::numlanes];
    vtype::storeu(data, vec);
    return data[vtype::numlanes / 2];
}
