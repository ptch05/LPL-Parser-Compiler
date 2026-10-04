package ast;

import compile.SymbolTable;
import java.util.List;
import java.util.Collections;

public abstract class MethodDef extends AST {
    public final String id;
    public final List<FormalParam> formals;
    public final List<VarDecl> locals;
    public final List<Stm> body;

    public MethodDef(String id, List<FormalParam> formals, List<VarDecl> locals, List<Stm> body) {
        this.id = id;
        this.formals = Collections.unmodifiableList(formals);
        this.locals = Collections.unmodifiableList(locals);
        this.body = Collections.unmodifiableList(body);
    }

    public String getMethodName() {
        return id;
    }

    public void compileBody(SymbolTable st) {
        for (Stm s : body) {
            s.compile(st);
        }

       if (this instanceof ProcDef) {
            emit("push 0");
            int numParams = formals.size();
            emit("push " + numParams);
            emit("ret");
       }
    }
}
