package com.saivandan.crm;

import java.lang.reflect.RecordComponent;
import java.util.Collection;
import java.util.Map;

/**
 * Minimal JSON writer. No third-party dependency.
 *
 * Supports: null, String, Boolean, Number, Map, Collection and Java records.
 * Records are read through their record components, so every model record in
 * the {@code model} package is serialised without any per-model mapping code.
 */
public final class Json {

    private Json() {
    }

    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        append(out, value);
        return out.toString();
    }

    private static void append(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String s) {
            appendString(out, s);
        } else if (value instanceof Boolean || value instanceof Number) {
            out.append(value);
        } else if (value instanceof Map<?, ?> map) {
            appendMap(out, map);
        } else if (value instanceof Collection<?> list) {
            appendList(out, list);
        } else if (value.getClass().isRecord()) {
            appendRecord(out, value);
        } else {
            appendString(out, String.valueOf(value));
        }
    }

    private static void appendMap(StringBuilder out, Map<?, ?> map) {
        out.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            appendString(out, String.valueOf(entry.getKey()));
            out.append(':');
            append(out, entry.getValue());
        }
        out.append('}');
    }

    private static void appendList(StringBuilder out, Collection<?> list) {
        out.append('[');
        boolean first = true;
        for (Object item : list) {
            if (!first) {
                out.append(',');
            }
            first = false;
            append(out, item);
        }
        out.append(']');
    }

    private static void appendRecord(StringBuilder out, Object record) {
        out.append('{');
        RecordComponent[] components = record.getClass().getRecordComponents();
        for (int i = 0; i < components.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            RecordComponent component = components[i];
            appendString(out, component.getName());
            out.append(':');
            try {
                append(out, component.getAccessor().invoke(record));
            } catch (ReflectiveOperationException ex) {
                append(out, null);
            }
        }
        out.append('}');
    }

    private static void appendString(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}