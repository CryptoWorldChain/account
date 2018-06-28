package org.brewchain.account.util;

import java.util.*;
import java.util.Map.Entry;

public class ByteArrayMap<V> implements Map<byte[], byte[]> {
	private final Map<ByteArrayWrapper, byte[]> delegate;

	public ByteArrayMap() {
		this(new HashMap<ByteArrayWrapper, byte[]>());
	}

	public ByteArrayMap(Map<ByteArrayWrapper, byte[]> delegate) {
		this.delegate = delegate;
	}

	@Override
	public int size() {
		return delegate.size();
	}

	@Override
	public boolean isEmpty() {
		return delegate.isEmpty();
	}

	@Override
	public boolean containsKey(Object key) {
		return delegate.containsKey(new ByteArrayWrapper((byte[]) key));
	}

	@Override
	public boolean containsValue(Object value) {
		return delegate.containsValue(value);
	}

	@Override
	public byte[] get(Object key) {
		return delegate.get(new ByteArrayWrapper((byte[]) key));
	}

	@Override
	public byte[] put(byte[] key, byte[] value) {
		return delegate.put(new ByteArrayWrapper(key), value);
	}

	@Override
	public byte[] remove(Object key) {
		return delegate.remove(new ByteArrayWrapper((byte[]) key));
	}

	@Override
	public void putAll(Map<? extends byte[], ? extends byte[]> m) {
		for (Entry<? extends byte[], ? extends byte[]> entry : m.entrySet()) {
            delegate.put(new ByteArrayWrapper(entry.getKey()), entry.getValue());
        }
	}

	@Override
    public void clear() {
        delegate.clear();
    }

    @Override
    public Set<byte[]> keySet() {
        return new ByteArraySet(new SetAdapter<>(delegate));
    }

    @Override
    public Collection<byte[]> values() {
        return delegate.values();
    }

    @Override
    public Set<Entry<byte[], byte[]>> entrySet() {
        return new MapEntrySet(delegate.entrySet());
    }

    @Override
    public boolean equals(Object o) {
        return delegate.equals(o);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    private class MapEntrySet implements Set<Map.Entry<byte[], byte[]>> {
        private final Set<Map.Entry<ByteArrayWrapper, byte[]>> delegate;

        private MapEntrySet(Set<Entry<ByteArrayWrapper, byte[]>> delegate) {
            this.delegate = delegate;
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            throw new RuntimeException("Not implemented");
        }

        @Override
        public Iterator<Entry<byte[], byte[]>> iterator() {
            final Iterator<Entry<ByteArrayWrapper, byte[]>> it = delegate.iterator();
            return new Iterator<Entry<byte[], byte[]>>() {

                @Override
                public boolean hasNext() {
                    return it.hasNext();
                }

                @Override
                public Entry<byte[], byte[]> next() {
                    Entry<ByteArrayWrapper, byte[]> next = it.next();
                    return new AbstractMap.SimpleImmutableEntry(next.getKey().getData(), next.getValue());
                }

                @Override
                public void remove() {
                    it.remove();
                }
            };
        }

        @Override
        public Object[] toArray() {
            throw new RuntimeException("Not implemented");
        }

        @Override
        public <T> T[] toArray(T[] a) {
            throw new RuntimeException("Not implemented");
        }

        @Override
        public boolean add(Entry<byte[], byte[]> vEntry) {
            throw new RuntimeException("Not implemented");
        }

        @Override
        public boolean remove(Object o) {
            throw new RuntimeException("Not implemented");
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            throw new RuntimeException("Not implemented");
        }

        @Override
        public boolean addAll(Collection<? extends Entry<byte[], byte[]>> c) {
            throw new RuntimeException("Not implemented");
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            throw new RuntimeException("Not implemented");
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            throw new RuntimeException("Not implemented");
        }

        @Override
        public void clear() {
            throw new RuntimeException("Not implemented");

        }
    }
}
