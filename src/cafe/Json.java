package cafe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON tối giản, không cần thư viện ngoài.
 * Giá trị được hỗ trợ: Map (khóa String), List, String, Long, Double, Boolean và null.
 */
public final class Json {
    private static final int MAX_DEPTH = 32;

    private Json() {}

    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        write(out, value);
        return out.toString();
    }

    public static Object parse(String text) {
        if (text == null) throw new IllegalArgumentException("JSON rỗng.");
        Parser parser = new Parser(text);
        parser.skip();
        Object value = parser.value(0);
        parser.skip();
        if (!parser.done()) throw parser.error("có dữ liệu thừa sau JSON");
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object value = parse(text);
        if (value instanceof Map<?, ?>) return (Map<String, Object>) value;
        throw new IllegalArgumentException("JSON phải là một đối tượng.");
    }

    private static void write(StringBuilder out, Object v) {
        if (v == null) {
            out.append("null");
        } else if (v instanceof String s) {
            quote(out, s);
        } else if (v instanceof Boolean || v instanceof Integer || v instanceof Long
                || v instanceof Short || v instanceof Byte) {
            out.append(v);
        } else if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d))
                throw new IllegalArgumentException("Số không hợp lệ trong JSON.");
            out.append(d);
        } else if (v instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key))
                    throw new IllegalArgumentException("Khóa JSON phải là chuỗi.");
                if (!first) out.append(',');
                first = false;
                quote(out, key);
                out.append(':');
                write(out, entry.getValue());
            }
            out.append('}');
        } else if (v instanceof Collection<?> items) {
            out.append('[');
            boolean first = true;
            for (Object item : items) {
                if (!first) out.append(',');
                first = false;
                write(out, item);
            }
            out.append(']');
        } else {
            throw new IllegalArgumentException("Kiểu dữ liệu không hỗ trợ: " + v.getClass().getSimpleName());
        }
    }

    private static void quote(StringBuilder out, String s) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        out.append('"');
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
        }

        boolean done() {
            return i >= s.length();
        }

        boolean peek(char c) {
            return i < s.length() && s.charAt(i) == c;
        }

        void expect(char c) {
            if (!peek(c)) throw error("cần ký tự '" + c + "'");
            i++;
        }

        void skip() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') i++;
                else break;
            }
        }

        IllegalArgumentException error(String message) {
            return new IllegalArgumentException("JSON không hợp lệ tại vị trí " + i + ": " + message + ".");
        }

        Object value(int depth) {
            if (depth > MAX_DEPTH) throw error("lồng quá sâu");
            if (done()) throw error("kết thúc đột ngột");
            switch (s.charAt(i)) {
                case '{': return object(depth);
                case '[': return array(depth);
                case '"': return string();
                case 't': literal("true"); return Boolean.TRUE;
                case 'f': literal("false"); return Boolean.FALSE;
                case 'n': literal("null"); return null;
                default: return number();
            }
        }

        private void literal(String word) {
            if (!s.startsWith(word, i)) throw error("giá trị không hợp lệ");
            i += word.length();
        }

        private Map<String, Object> object(int depth) {
            Map<String, Object> map = new LinkedHashMap<>();
            i++;
            skip();
            if (peek('}')) {
                i++;
                return map;
            }
            while (true) {
                skip();
                if (!peek('"')) throw error("cần khóa dạng chuỗi");
                String key = string();
                skip();
                expect(':');
                skip();
                map.put(key, value(depth + 1));
                skip();
                if (peek(',')) {
                    i++;
                    continue;
                }
                expect('}');
                return map;
            }
        }

        private List<Object> array(int depth) {
            List<Object> list = new ArrayList<>();
            i++;
            skip();
            if (peek(']')) {
                i++;
                return list;
            }
            while (true) {
                skip();
                list.add(value(depth + 1));
                skip();
                if (peek(',')) {
                    i++;
                    continue;
                }
                expect(']');
                return list;
            }
        }

        private String string() {
            i++; // dấu " mở đầu
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (done()) throw error("chuỗi chưa được đóng");
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c < 0x20) throw error("ký tự điều khiển trong chuỗi");
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                if (done()) throw error("escape dở dang");
                char e = s.charAt(i++);
                switch (e) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u': {
                        if (i + 4 > s.length()) throw error("mã \\u dở dang");
                        int code = 0;
                        for (int k = 0; k < 4; k++) {
                            int digit = Character.digit(s.charAt(i + k), 16);
                            if (digit < 0) throw error("mã \\u không hợp lệ");
                            code = code * 16 + digit;
                        }
                        i += 4;
                        sb.append((char) code);
                        break;
                    }
                    default: throw error("escape không hợp lệ");
                }
            }
        }

        private int digits() {
            int start = i;
            while (i < s.length() && s.charAt(i) >= '0' && s.charAt(i) <= '9') i++;
            return i - start;
        }

        private Object number() {
            int start = i;
            if (peek('-')) i++;
            if (digits() == 0) throw error("giá trị không hợp lệ");
            boolean integral = true;
            if (peek('.')) {
                integral = false;
                i++;
                if (digits() == 0) throw error("số không hợp lệ");
            }
            if (peek('e') || peek('E')) {
                integral = false;
                i++;
                if (peek('+') || peek('-')) i++;
                if (digits() == 0) throw error("số không hợp lệ");
            }
            String text = s.substring(start, i);
            try {
                if (integral) return Long.valueOf(text);
                return Double.valueOf(text);
            } catch (NumberFormatException ex) {
                throw error("số quá lớn");
            }
        }
    }
}
