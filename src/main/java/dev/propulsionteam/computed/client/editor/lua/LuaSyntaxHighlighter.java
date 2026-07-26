package dev.propulsionteam.computed.client.editor.lua;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class LuaSyntaxHighlighter {
    public static final int DEFAULT = 0xFFC5C5C5;
    public static final int ATTRIBUTE = 0xFF7983AB;
    public static final int SELF = 0xFFD1BE5F;
    public static final int CONSTANT = 0xFF75A1C9;
    public static final int STRING = 0xFF729369;
    public static final int FUNCTION = 0xFF9073B6;
    public static final int COMMENT = 0xFF5F5F5F;
    public static final int LOCAL = 0xFFAC7070;
    public static final int CONTROL = 0xFFCD844F;

    private static final Set<String> CONTROL_WORDS = Set.of(
            "and",
            "break",
            "do",
            "else",
            "elseif",
            "end",
            "for",
            "function",
            "goto",
            "if",
            "in",
            "not",
            "or",
            "repeat",
            "return",
            "then",
            "until",
            "while");
    private static final Set<String> CONSTANT_WORDS = Set.of("false", "nil", "true");

    private LuaSyntaxHighlighter() {}

    public static List<List<Span>> highlight(String source) {
        String[] lines = (source == null ? "" : source).split("\n", -1);
        List<List<Span>> result = new ArrayList<>(lines.length);
        State state = new State();
        for (String line : lines) {
            result.add(highlightLine(line, state));
        }
        return List.copyOf(result);
    }

    private static List<Span> highlightLine(String line, State state) {
        List<Span> spans = new ArrayList<>();
        int index = 0;
        while (index < line.length()) {
            if (state.longKind != LongKind.NONE) {
                String closing = "]" + "=".repeat(state.longEquals) + "]";
                int end = line.indexOf(closing, index);
                int color = state.longKind == LongKind.COMMENT ? COMMENT : STRING;
                if (end < 0) {
                    add(spans, line.substring(index), color);
                    index = line.length();
                } else {
                    end += closing.length();
                    add(spans, line.substring(index, end), color);
                    index = end;
                    state.longKind = LongKind.NONE;
                }
                continue;
            }

            char character = line.charAt(index);
            if (Character.isWhitespace(character)) {
                int end = index + 1;
                while (end < line.length() && Character.isWhitespace(line.charAt(end))) {
                    end++;
                }
                add(spans, line.substring(index, end), DEFAULT);
                index = end;
                continue;
            }

            if (character == '-' && index + 1 < line.length() && line.charAt(index + 1) == '-') {
                int opener = longBracketEquals(line, index + 2);
                if (opener >= 0) {
                    String closing = "]" + "=".repeat(opener) + "]";
                    int content = index + 4 + opener;
                    int end = line.indexOf(closing, content);
                    if (end < 0) {
                        add(spans, line.substring(index), COMMENT);
                        state.longKind = LongKind.COMMENT;
                        state.longEquals = opener;
                        break;
                    }
                    end += closing.length();
                    add(spans, line.substring(index, end), COMMENT);
                    index = end;
                    continue;
                }
                add(spans, line.substring(index), COMMENT);
                break;
            }

            if (character == '"' || character == '\'') {
                int end = quotedStringEnd(line, index, character);
                add(spans, line.substring(index, end), STRING);
                index = end;
                continue;
            }

            int longString = longBracketEquals(line, index);
            if (longString >= 0) {
                String closing = "]" + "=".repeat(longString) + "]";
                int content = index + 2 + longString;
                int end = line.indexOf(closing, content);
                if (end < 0) {
                    add(spans, line.substring(index), STRING);
                    state.longKind = LongKind.STRING;
                    state.longEquals = longString;
                    break;
                }
                end += closing.length();
                add(spans, line.substring(index, end), STRING);
                index = end;
                continue;
            }

            if (isNumberStart(line, index)) {
                int end = numberEnd(line, index);
                add(spans, line.substring(index, end), CONSTANT);
                index = end;
                continue;
            }

            if (isIdentifierStart(character)) {
                int end = index + 1;
                while (end < line.length() && isIdentifierPart(line.charAt(end))) {
                    end++;
                }
                String word = line.substring(index, end);
                int next = nextNonWhitespace(line, end);
                int color = word.equals("local")
                        ? LOCAL
                        : CONTROL_WORDS.contains(word)
                                ? CONTROL
                                : CONSTANT_WORDS.contains(word)
                                        ? CONSTANT
                                        : word.equals("self")
                                                ? SELF
                                                : next < line.length() && line.charAt(next) == '('
                                                        ? FUNCTION
                                                        : state.tableDepth > 0
                                                                        && next < line.length()
                                                                        && line.charAt(next) == '='
                                                                ? ATTRIBUTE
                                                                : DEFAULT;
                add(spans, word, color);
                index = end;
                continue;
            }

            if (character == '{') {
                state.tableDepth++;
            } else if (character == '}') {
                state.tableDepth = Math.max(0, state.tableDepth - 1);
            }
            add(spans, Character.toString(character), DEFAULT);
            index++;
        }
        if (line.isEmpty()) {
            return List.of();
        }
        return List.copyOf(spans);
    }

    private static void add(List<Span> spans, String text, int color) {
        if (text.isEmpty()) {
            return;
        }
        if (!spans.isEmpty() && spans.getLast().color() == color) {
            Span previous = spans.removeLast();
            spans.add(new Span(previous.text() + text, color));
        } else {
            spans.add(new Span(text, color));
        }
    }

    private static int quotedStringEnd(String line, int start, char quote) {
        boolean escaped = false;
        for (int index = start + 1; index < line.length(); index++) {
            char character = line.charAt(index);
            if (escaped) {
                escaped = false;
            } else if (character == '\\') {
                escaped = true;
            } else if (character == quote) {
                return index + 1;
            }
        }
        return line.length();
    }

    private static int longBracketEquals(String line, int start) {
        if (start >= line.length() || line.charAt(start) != '[') {
            return -1;
        }
        int index = start + 1;
        while (index < line.length() && line.charAt(index) == '=') {
            index++;
        }
        return index < line.length() && line.charAt(index) == ']' ? index - start - 1 : -1;
    }

    private static boolean isNumberStart(String line, int index) {
        char character = line.charAt(index);
        if (Character.isDigit(character)) {
            return true;
        }
        return character == '.'
                && index + 1 < line.length()
                && Character.isDigit(line.charAt(index + 1));
    }

    private static int numberEnd(String line, int start) {
        int index = start;
        boolean hexadecimal = index + 1 < line.length()
                && line.charAt(index) == '0'
                && (line.charAt(index + 1) == 'x' || line.charAt(index + 1) == 'X');
        if (hexadecimal) {
            index += 2;
            while (index < line.length()) {
                char character = line.charAt(index);
                if (Character.digit(character, 16) < 0
                        && character != '_'
                        && character != '.') {
                    break;
                }
                index++;
            }
            if (index < line.length()
                    && (line.charAt(index) == 'p' || line.charAt(index) == 'P')) {
                index = exponentEnd(line, index + 1);
            }
            return index;
        }
        while (index < line.length()
                && (Character.isDigit(line.charAt(index))
                        || line.charAt(index) == '_')) {
            index++;
        }
        if (index < line.length() && line.charAt(index) == '.') {
            index++;
            while (index < line.length()
                    && (Character.isDigit(line.charAt(index))
                            || line.charAt(index) == '_')) {
                index++;
            }
        }
        if (index < line.length()
                && (line.charAt(index) == 'e' || line.charAt(index) == 'E')) {
            index = exponentEnd(line, index + 1);
        }
        return index;
    }

    private static int exponentEnd(String line, int start) {
        int index = start;
        if (index < line.length()
                && (line.charAt(index) == '+' || line.charAt(index) == '-')) {
            index++;
        }
        while (index < line.length()
                && (Character.isDigit(line.charAt(index))
                        || line.charAt(index) == '_')) {
            index++;
        }
        return index;
    }

    private static int nextNonWhitespace(String line, int start) {
        int index = start;
        while (index < line.length() && Character.isWhitespace(line.charAt(index))) {
            index++;
        }
        return index;
    }

    private static boolean isIdentifierStart(char character) {
        return character == '_' || Character.isLetter(character);
    }

    private static boolean isIdentifierPart(char character) {
        return character == '_' || Character.isLetterOrDigit(character);
    }

    public record Span(String text, int color) {}

    private enum LongKind {
        NONE,
        COMMENT,
        STRING
    }

    private static final class State {
        private LongKind longKind = LongKind.NONE;
        private int longEquals;
        private int tableDepth;
    }
}
