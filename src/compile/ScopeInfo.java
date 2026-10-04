package compile;

import ast.Type;

public class ScopeInfo {
    private final Scope scope;
    private final int offset;
    private final Type type;

    public ScopeInfo(Scope scope, int offset, Type type) {
        this.scope = scope;
        this.offset = offset;
        this.type = type;
    }

    public Scope getScope() { return scope; }
    public int getOffset() { return offset; }
    public Type getType() { return type; }
}
