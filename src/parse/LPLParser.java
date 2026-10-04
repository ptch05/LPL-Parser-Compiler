package parse;

import ast.*;
import sbnf.ParseException;
import sbnf.lex.Lexer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Parse an LPL program and build its AST.  */
public class LPLParser {

    public static final String LPL_GRAMMAR_FILE = "data/LPL-C.sbnf";

    private final Lexer lex;

    /**
     * Initialise a new LPL parser.
     */
    public LPLParser() {
        lex = new Lexer(LPL_GRAMMAR_FILE);
    }

    /** Parse an LPL source file and return its AST.
     *
     * @param sourcePath a path to an LPL source file
     * @return the Program AST for the parsed LPL program
     * @throws ParseException if the source file contains syntax errors
     * @throws IOException
     */
    public Program parse(String sourcePath) throws IOException {
        lex.readFile(sourcePath);
        lex.next();
        Program prog = program();
        if (!lex.tok().isType("EOF")) {
            throw new ParseException(lex.tok(), "EOF");
        }
        return prog;
    }

    private Program program() {
        List<VarDecl> globals = new ArrayList<>();
        List<Stm> body = new ArrayList<>();
        List<MethodDef> methods = new ArrayList<>();

        lex.eat("BEGIN");
        while (isStartOfType()) {
            globals.add(nonFormalVarDecl());
        }
        while (!lex.tok().isType("END") && !lex.tok().isType("EOF")) {
            body.add(stm());
        }
        lex.eat("END");
        while (lex.tok().isType("FUN") || lex.tok().isType("PROC")) {
            methods.add(funOrProcDef());
        }
        return new Program(globals, body, methods);
    }

    private boolean isStartOfType() {
        return lex.tok().isType("INT_TYPE");
    }

    private Type type() {
        lex.eat("INT_TYPE");
        while (lex.tok().isType("LSQBR")) {
            arraySpec();
        }
        return new TypeInt();
    }

    private void arraySpec() {
        lex.eat("LSQBR");
        lex.eat("RSQBR");
    }

    private VarDecl nonFormalVarDecl() {
        Type t = type();
        String id = lex.eat("ID");
        lex.eat("SEMIC");
        return new VarDecl(t, id);
    }

    private MethodDef funOrProcDef() {
        Type returnType = null;
        boolean isFunction;
        if (lex.tok().isType("FUN")) {
            lex.next();
            returnType = type();
            isFunction = true;
        } else if (lex.tok().isType("PROC")) {
            lex.next();
            isFunction = false;
        } else {
            throw new ParseException(lex.tok(), "FUN", "PROC");
        }
        return methodDef(returnType, isFunction);
    }

    private MethodDef methodDef(Type returnType, boolean isFunction) {
        String id = lex.eat("ID");
        lex.eat("LBR");
        List<FormalParam> formals = formals();
        lex.eat("RBR");
        lex.eat("LCBR");
        List<VarDecl> locals = new ArrayList<>();
        while (isStartOfType()) {
            locals.add(nonFormalVarDecl());
        }
        List<Stm> body = new ArrayList<>();
        while (!lex.tok().isType("RCBR") && !lex.tok().isType("EOF")) {
            body.add(stm());
        }
        lex.eat("RCBR");

        if (isFunction) {
            return new FunDef(returnType, id, formals, locals, body);
        } else {
            return new ProcDef(id, formals, locals, body);
        }
    }

    private List<FormalParam> formals() {
        List<FormalParam> params = new ArrayList<>();
        if (isStartOfType()) {
            Type t = type();
            String id = lex.eat("ID");
            params.add(new FormalParam(t, id));

            while (lex.tok().isType("COMMA")) {
                lex.next();
                Type nextT = type();
                String nextId = lex.eat("ID");
                params.add(new FormalParam(nextT, nextId));
            }
        }
        return params;
    }

    private Stm stm() {
        switch (lex.tok().type) {
            case "ID": {
                String id = lex.eat("ID");
                return stmIdFactor(id);
            }
            case "IF": {
                lex.next();
                lex.eat("LBR");
                Exp e = exp();
                lex.eat("RBR");
                Stm trueBranch = stm();
                lex.eat("ELSE");
                Stm falseBranch = stm();
                return new StmIf(e, trueBranch, falseBranch);
            }
            case "WHILE": {
                lex.next();
                lex.eat("LBR");
                Exp e = exp();
                lex.eat("RBR");
                Stm body = stm();
                return new StmWhile(e, body);
            }
            case "PRINT": {
                lex.next();
                Exp e = exp();
                lex.eat("SEMIC");
                return new StmPrint(e);
            }
            case "PRINTLN": {
                lex.next();
                Exp e = exp();
                lex.eat("SEMIC");
                return new StmPrintln(e);
            }
            case "PRINTCH": {
                lex.next();
                Exp e = exp();
                lex.eat("SEMIC");
                return new StmPrintChar(e);
            }
            case "NEWLINE": {
                lex.next();
                lex.eat("SEMIC");
                return new StmNewline();
            }
            case "RETURN": {
                lex.next();
                return stmReturnFactor();
            }
            case "LCBR": {
                lex.next();
                List<Stm> stms = new ArrayList<>();
                while (!lex.tok().isType("RCBR") && !lex.tok().isType("EOF")) {
                    stms.add(stm());
                }
                lex.eat("RCBR");
                return new StmBlock(stms);
            }
            case "SWITCH": {
                lex.next();
                lex.eat("LBR");
                Exp caseExp = exp();
                lex.eat("RBR");
                lex.eat("LCBR");
                List<StmSwitch.Case> cases = new ArrayList<>();
                while (lex.tok().isType("CASE")) {
                    cases.add(switchCase());
                }
                lex.eat("DEFAULT");
                lex.eat("COLON");
                Stm defaultCase = stm();
                lex.eat("RCBR");
                return new StmSwitch(caseExp, defaultCase, cases);
            }
            default:
                throw new ParseException(lex.tok(), "ID", "IF", "WHILE", "PRINT", "PRINTLN", "PRINTCH", "NEWLINE", "RETURN", "LCBR", "SWITCH");
        }
    }

    private Stm stmIdFactor(String id) {
        if (lex.tok().isType("LBR")) {
            lex.next();
            List<Exp> actuals = actuals();
            lex.eat("RBR");
            lex.eat("SEMIC");
            return new StmMethodCall(id, actuals);
        }

        while (lex.tok().isType("LSQBR")) {
            indexer();
        }
        lex.eat("ASSIGN");
        Exp valueExpression = exp();
        lex.eat("SEMIC");
        return new StmAssign(id, valueExpression);
    }

    private void indexer() {
        lex.eat("LSQBR");
        Exp indexExp = exp();
        lex.eat("RSQBR");
    }

    private Stm stmReturnFactor() {
        if (lex.tok().isType("SEMIC")) {
            lex.next();
            return new StmReturn(null);
        }

        Exp returnExp = exp();
        lex.eat("SEMIC");
        return new StmReturn(returnExp);
    }

    private StmSwitch.Case switchCase() {
        lex.eat("CASE");
        int caseNumber = signedInt();
        lex.eat("COLON");
        Stm stm = stm();
        return new StmSwitch.Case(caseNumber, stm);
    }

    private int optionalSign() {
        if (lex.tok().isType("MINUS")) {
            lex.next();
            return -1;
        }
        return 1;
    }

    private int signedInt() {
        return optionalSign() * Integer.parseInt(lex.eat("INTLIT"));
    }

    private Exp exp() {
        Exp e1 = simpleExp();
        return operatorClause(e1);
    }

    private Exp operatorClause(Exp leftExp) {
        return switch (lex.tok().type) {
            case "MUL" -> {
                lex.next();
                yield new ExpTimes(leftExp, simpleExp());
            }
            case "DIV" -> {
                lex.next();
                yield new ExpDiv(leftExp, simpleExp());
            }
            case "MINUS" -> {
                lex.next();
                yield new ExpMinus(leftExp, simpleExp());
            }
            case "ADD" -> {
                lex.next();
                yield new ExpPlus(leftExp, simpleExp());
            }
            case "LT" -> {
                lex.next();
                yield new ExpLessThan(leftExp, simpleExp());
            }
            case "LE" -> {
                lex.next();
                yield new ExpLessThanEqual(leftExp, simpleExp());
            }
            case "EQ" -> {
                lex.next();
                yield new ExpEqual(leftExp, simpleExp());
            }
            case "AND" -> {
                lex.next();
                yield new ExpAnd(leftExp, simpleExp());
            }
            case "OR" -> {
                lex.next();
                yield new ExpOr(leftExp, simpleExp());
            }
            default -> leftExp;
        };
    }

    private Exp simpleExp() {
        switch (lex.tok().type) {
            case "ID": {
                String id = lex.eat("ID");
                return simpleIdFactor(id);
            }
            case "MINUS":
            case "INTLIT": {
                return new ExpInt(signedInt());
            }
            case "NEW": {
                return newArrayExp();
            }
            case "NOT": {
                lex.next();
                return new ExpNot(simpleExp());
            }
            case "NULL": {
                lex.next();
                return new ExpInt(0);
            }
            case "LBR": {
                lex.next();
                Exp e = exp();
                lex.eat("RBR");
                return e;
            }
            default:
                throw new ParseException(lex.tok(), "ID", "MINUS", "INTLIT", "NEW", "NOT", "NULL", "LBR");
        }
    }

    private Exp newArrayExp() {
        lex.eat("NEW");
        lex.eat("INT_TYPE");
        lex.eat("LSQBR");
        Exp sizeExp = exp();
        lex.eat("RSQBR");
        while (lex.tok().isType("LSQBR")) {
            arraySpec();
        }
        return new ExpInt(0);
    }

    private Exp simpleIdFactor(String id) {
        if (lex.tok().isType("LBR")) {
            lex.next();
            List<Exp> actuals = actuals();
            lex.eat("RBR");
            return new ExpFunCall(id, actuals);
        }

        while (lex.tok().isType("LSQBR")) {
            indexer();
        }
        return simpleIdLexprFactor(id);
    }

    private Exp simpleIdLexprFactor(String id) {
        if (lex.tok().isType("DOT")) {
            lex.next();
            lex.eat("LENGTH");
            return new ExpInt(0);
        }

        return new ExpVar(id);
    }

    private List<Exp> actuals() {
        List<Exp> args = new ArrayList<>();
        if (!lex.tok().isType("RBR")) {
            args.add(exp());
            while (lex.tok().isType("COMMA")) {
                lex.next();
                args.add(exp());
            }
        }
        return args;
    }

    /**
     * Parse and pretty-print an LPL source file specified
     * by a command line argument.
     * @param args command-line arguments
     * @throws ParseException if the source file contains syntax errors
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: parse.LPLParser <source-file>");
            System.exit(1);
        }
        LPLParser parser = new LPLParser();
        Program program = parser.parse(args[0]);
        System.out.println(program);
    }
}
