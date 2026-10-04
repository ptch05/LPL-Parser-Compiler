package ast;

import compile.SymbolTable;
import compile.ScopeInfo;

public class ExpVar extends Exp {

    public final String varName;

    public ExpVar(String varName) {
        this.varName = varName;
    }

    @Override
    public void compile(SymbolTable st) {
        ScopeInfo info = st.getVarScopeInfo(varName);

        switch (info.getScope()) {
            case GLOBAL:
                emit("get_dp");
                emit("push " + info.getOffset());
                emit("add");
                emit("load");
                break;

            case LOCAL:
                emit("get_fp");
                emit("push " + (-4 * info.getOffset()));
                emit("add");
                emit("load");
                break;

            case PARAMETER:
                emit("get_fp");
                emit("push " + (4 * info.getOffset()));
                emit("add");
                emit("load");
                break;

            default:
                throw new Error("Undefined variable or scope error during compile: " + varName);
        }
    }

    @Override
    public <T> T accept(ast.util.Visitor<T> visitor) {
        return visitor.visit(this);
    }
}
