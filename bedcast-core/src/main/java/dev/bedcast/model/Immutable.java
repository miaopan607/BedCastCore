package dev.bedcast.model;

import java.util.*;

/** 只接受 JSON 值，发布前冻结整棵扩展数据树。 */
public final class Immutable {
    private Immutable() { }
    public static <K,V> Map<K,V> map(Map<K,V> source) {
        if (source instanceof FrozenMap) return source;
        return source.isEmpty() ? Collections.emptyMap() : new FrozenMap<>(new LinkedHashMap<>(source), false);
    }
    private static final class FrozenMap<K,V> extends AbstractMap<K,V> {
        private final Map<K,V> entries;
        private final boolean json;
        FrozenMap(Map<K,V> owned, boolean json) { entries = Collections.unmodifiableMap(owned); this.json = json; }
        @Override public Set<Entry<K,V>> entrySet() { return entries.entrySet(); }
        @Override public V get(Object key) { return entries.get(key); }
        @Override public boolean containsKey(Object key) { return entries.containsKey(key); }
        @Override public int size() { return entries.size(); }
    }
    @SuppressWarnings("unchecked")
    public static <T> List<T> list(Collection<T> source) {
        if (source instanceof FrozenList) return (List<T>)source;
        return source.isEmpty() ? Collections.emptyList() : new FrozenList<>(source);
    }
    private static final class FrozenList<T> extends AbstractList<T> implements RandomAccess {
        private final List<T> entries;
        FrozenList(Collection<T> entries) { this.entries = new ArrayList<>(entries); }
        @Override public T get(int index) { return entries.get(index); }
        @Override public int size() { return entries.size(); }
    }
    @SuppressWarnings("unchecked")
    public static Map<String,Object> json(Map<String,?> source) {
        if (source instanceof FrozenMap && ((FrozenMap<?,?>)source).json) return (Map<String,Object>)source;
        if (source.isEmpty()) return Collections.emptyMap();
        Map<String,Object> result = new LinkedHashMap<>();
        source.forEach((key,value) -> result.put(Objects.requireNonNull(key), freeze(value)));
        return new FrozenMap<>(result, true);
    }
    private static Object freeze(Object value) {
        if (value instanceof FrozenMap && ((FrozenMap<?,?>)value).json) return value;
        if (value == null || value instanceof String || value instanceof Boolean || value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) return value;
        if (value instanceof Double || value instanceof Float) {
            if (!Double.isFinite(((Number)value).doubleValue())) throw new IllegalArgumentException("非有限 JSON 数字");
            return value;
        }
        if (value instanceof Map) {
            Map<String,Object> copy = new LinkedHashMap<>();
            ((Map<?,?>)value).forEach((k,v) -> { if (!(k instanceof String)) throw new IllegalArgumentException("JSON 键必须为字符串"); copy.put((String)k, freeze(v)); });
            return new FrozenMap<>(copy, true);
        }
        if (value instanceof Collection) {
            List<Object> copy = new ArrayList<>();
            for (Object entry : (Collection<?>)value) copy.add(freeze(entry));
            return Collections.unmodifiableList(copy);
        }
        throw new IllegalArgumentException("不是 JSON 值: " + value.getClass().getName());
    }
    public static Map<String,Object> data(Object... pairs) {
        Map<String,Object> result = new LinkedHashMap<>();
        for (int i=0;i<pairs.length;i+=2) result.put((String)pairs[i],pairs[i+1]);
        return result;
    }
}
