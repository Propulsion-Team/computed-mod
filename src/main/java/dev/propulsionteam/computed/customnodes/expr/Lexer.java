package dev.propulsionteam.computed.customnodes.expr;

import java.util.ArrayList;
import java.util.List;

public final class Lexer {
    public enum TokenType {
        NUMBER, STRING_LIT, IDENT,
        LPAREN, RPAREN, COMMA, SEMICOLON,
        PLUS, MINUS, STAR, SLASH, PERCENT,
        BANG, AND_AND, OR_OR,
        EQ, EQ_EQ, BANG_EQ, LT, LT_EQ, GT, GT_EQ,
        EOF
    }

    public record Token(TokenType type, String text, double number, String string) {
        static Token num(double v, String text) { return new Token(TokenType.NUMBER, text, v, null); }
        static Token str(String v)              { return new Token(TokenType.STRING_LIT, "\"" + v + "\"", 0, v); }
        static Token ident(String t)            { return new Token(TokenType.IDENT, t, 0, null); }
        static Token sym(TokenType t, String text) { return new Token(t, text, 0, null); }
    }

    public static List<Token> tokenize(String source) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        int len = source.length();
        while (i < len) {
            char c = source.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }

            // Numbers
            if (Character.isDigit(c) || (c == '.' && i + 1 < len && Character.isDigit(source.charAt(i + 1)))) {
                int start = i++;
                while (i < len && (Character.isDigit(source.charAt(i)) || source.charAt(i) == '.')) i++;
                String text = source.substring(start, i);
                tokens.add(Token.num(Double.parseDouble(text), text));
                continue;
            }

            // Identifiers
            if (Character.isLetter(c) || c == '_') {
                int start = i++;
                while (i < len && (Character.isLetterOrDigit(source.charAt(i)) || source.charAt(i) == '_')) i++;
                tokens.add(Token.ident(source.substring(start, i)));
                continue;
            }

            // String literals: "..." or '...'
            if (c == '"' || c == '\'') {
                char quote = c;
                i++;
                StringBuilder sb = new StringBuilder();
                while (i < len && source.charAt(i) != quote) {
                    char ch = source.charAt(i);
                    if (ch == '\\' && i + 1 < len) {
                        i++;
                        char esc = source.charAt(i);
                        sb.append(switch (esc) {
                            case 'n'  -> '\n';
                            case 't'  -> '\t';
                            case 'r'  -> '\r';
                            default   -> esc;
                        });
                    } else {
                        sb.append(ch);
                    }
                    i++;
                }
                if (i >= len) throw new IllegalArgumentException("Unterminated string literal");
                i++; // consume closing quote
                tokens.add(Token.str(sb.toString()));
                continue;
            }

            // Two-char operators
            if (i + 1 < len) {
                String two = source.substring(i, i + 2);
                TokenType tt = switch (two) {
                    case "&&" -> TokenType.AND_AND;
                    case "||" -> TokenType.OR_OR;
                    case "==" -> TokenType.EQ_EQ;
                    case "!=" -> TokenType.BANG_EQ;
                    case "<=" -> TokenType.LT_EQ;
                    case ">=" -> TokenType.GT_EQ;
                    default   -> null;
                };
                if (tt != null) { tokens.add(Token.sym(tt, two)); i += 2; continue; }
            }

            // Single-char
            TokenType tt = switch (c) {
                case '(' -> TokenType.LPAREN;
                case ')' -> TokenType.RPAREN;
                case ',' -> TokenType.COMMA;
                case ';' -> TokenType.SEMICOLON;
                case '+' -> TokenType.PLUS;
                case '-' -> TokenType.MINUS;
                case '*' -> TokenType.STAR;
                case '/' -> TokenType.SLASH;
                case '%' -> TokenType.PERCENT;
                case '!' -> TokenType.BANG;
                case '<' -> TokenType.LT;
                case '>' -> TokenType.GT;
                case '=' -> TokenType.EQ;
                default  -> throw new IllegalArgumentException("Unsupported character: " + c);
            };
            tokens.add(Token.sym(tt, String.valueOf(c)));
            i++;
        }
        tokens.add(Token.sym(TokenType.EOF, ""));
        return tokens;
    }
}
