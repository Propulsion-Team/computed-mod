package dev.propulsionteam.computed.customnodes.expr;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Parser {
    private final List<Lexer.Token> tokens;
    private final EvalContext ctx;
    private int index = 0;

    public Parser(List<Lexer.Token> tokens, EvalContext ctx) {
        this.tokens = tokens;
        this.ctx = ctx;
    }

    /** Parse and evaluate a program: one or more `;`-separated statements.
     *  The value of the last statement is returned. */
    public Value parseProgram() {
        Value result = Value.ZERO;
        while (!check(Lexer.TokenType.EOF)) {
            result = parseStatement();
            if (!check(Lexer.TokenType.EOF)) {
                expect(Lexer.TokenType.SEMICOLON);
            }
        }
        return result;
    }

    /** A statement is either an assignment (`name = expr`) or a bare expression. */
    private Value parseStatement() {
        // Lookahead: IDENT followed by `=` (but not `==`) → assignment
        if (check(Lexer.TokenType.IDENT) && peekAhead(1) == Lexer.TokenType.EQ) {
            String name = tokens.get(index).text().toLowerCase(Locale.ROOT);
            index += 2; // consume ident + `=`
            Value rhs = parseExpression();
            ctx.setVar(name, rhs);
            return rhs;
        }
        return parseExpression();
    }

    private Value parseExpression() { return parseOr(); }

    private Value parseOr() {
        Value left = parseAnd();
        while (match(Lexer.TokenType.OR_OR)) {
            Value right = parseAnd();
            left = Value.ofBool(left.asBool() || right.asBool());
        }
        return left;
    }

    private Value parseAnd() {
        Value left = parseEquality();
        while (match(Lexer.TokenType.AND_AND)) {
            Value right = parseEquality();
            left = Value.ofBool(left.asBool() && right.asBool());
        }
        return left;
    }

    private Value parseEquality() {
        Value left = parseComparison();
        while (true) {
            if (match(Lexer.TokenType.EQ_EQ)) {
                left = Value.ofBool(left.equals(parseComparison()));
            } else if (match(Lexer.TokenType.BANG_EQ)) {
                left = Value.ofBool(!left.equals(parseComparison()));
            } else return left;
        }
    }

    private Value parseComparison() {
        Value left = parseTerm();
        while (true) {
            if (match(Lexer.TokenType.LT))    { left = Value.ofBool(left.asNumber() < parseTerm().asNumber()); }
            else if (match(Lexer.TokenType.LT_EQ)) { left = Value.ofBool(left.asNumber() <= parseTerm().asNumber()); }
            else if (match(Lexer.TokenType.GT))    { left = Value.ofBool(left.asNumber() > parseTerm().asNumber()); }
            else if (match(Lexer.TokenType.GT_EQ)) { left = Value.ofBool(left.asNumber() >= parseTerm().asNumber()); }
            else return left;
        }
    }

    private Value parseTerm() {
        Value left = parseFactor();
        while (true) {
            if (match(Lexer.TokenType.PLUS))  { left = left.add(parseFactor()); }
            else if (match(Lexer.TokenType.MINUS)) { left = Value.of(left.asNumber() - parseFactor().asNumber()); }
            else return left;
        }
    }

    private Value parseFactor() {
        Value left = parseUnary();
        while (true) {
            if (match(Lexer.TokenType.STAR)) {
                left = Value.of(left.asNumber() * parseUnary().asNumber());
            } else if (match(Lexer.TokenType.SLASH)) {
                double r = parseUnary().asNumber();
                left = Value.of(r == 0.0 ? 0.0 : left.asNumber() / r);
            } else if (match(Lexer.TokenType.PERCENT)) {
                double r = parseUnary().asNumber();
                left = Value.of(r == 0.0 ? 0.0 : left.asNumber() % r);
            } else return left;
        }
    }

    private Value parseUnary() {
        if (match(Lexer.TokenType.MINUS)) return Value.of(-parseUnary().asNumber());
        if (match(Lexer.TokenType.PLUS))  return parseUnary();
        if (match(Lexer.TokenType.BANG))  return Value.ofBool(!parseUnary().asBool());
        return parsePrimary();
    }

    private Value parsePrimary() {
        Lexer.Token tok = peek();

        if (match(Lexer.TokenType.NUMBER)) {
            return Value.of(tok.number());
        }

        if (match(Lexer.TokenType.STRING_LIT)) {
            return Value.of(tok.string());
        }

        if (match(Lexer.TokenType.IDENT)) {
            String name = tok.text().toLowerCase(Locale.ROOT);
            if (match(Lexer.TokenType.LPAREN)) {
                List<Value> args = new ArrayList<>();
                if (!check(Lexer.TokenType.RPAREN)) {
                    do { args.add(parseExpression()); } while (match(Lexer.TokenType.COMMA));
                }
                expect(Lexer.TokenType.RPAREN);
                return ctx.callFunction(name, args);
            }
            // Variable lookup
            if (ctx.hasVar(name)) return ctx.getVar(name);
            throw new IllegalArgumentException("Unknown variable: " + tok.text());
        }

        if (match(Lexer.TokenType.LPAREN)) {
            Value v = parseExpression();
            expect(Lexer.TokenType.RPAREN);
            return v;
        }

        throw new IllegalArgumentException("Unexpected token: " + tok.text());
    }

    // --- Helpers ---

    private boolean match(Lexer.TokenType type) {
        if (check(type)) { index++; return true; }
        return false;
    }

    private boolean check(Lexer.TokenType type) {
        return peek().type() == type;
    }

    private Lexer.Token peek() { return tokens.get(index); }

    private Lexer.TokenType peekAhead(int offset) {
        int i = index + offset;
        return i < tokens.size() ? tokens.get(i).type() : Lexer.TokenType.EOF;
    }

    private void expect(Lexer.TokenType type) {
        if (!match(type))
            throw new IllegalArgumentException("Expected " + type + " but got " + peek().type());
    }
}
