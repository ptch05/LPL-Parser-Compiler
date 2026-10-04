package compile;

import ast.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

public class SymbolTable {

    private static class MethodInfo {
        final String methodName;
        final Type returnType;
        final List<FormalParam> parameters;
        final List<VarDecl> locals;
        final Map<String, Integer> paramOffsets;
        final Map<String, Integer> localOffsets;
        final Map<String, Type> paramTypes;
        final Map<String, Type> localTypes;

        MethodInfo(String name, Type retType, List<FormalParam> params, List<VarDecl> localDecls) {
            this.methodName = name;
            this.returnType = retType;
            this.parameters = Collections.unmodifiableList(params);
            this.locals = Collections.unmodifiableList(localDecls);
            this.paramOffsets = new HashMap<>();
            this.localOffsets = new HashMap<>();
            this.paramTypes = new HashMap<>();
            this.localTypes = new HashMap<>();

            int paramOffsetCounter = 1;
            for (FormalParam p : params) {
                if (paramOffsets.containsKey(p.name) || localOffsets.containsKey(p.name)) {
                    throw new StaticAnalysisException("Duplicate parameter name or shadowing: " + p.name + " in method " + name);
                }
                paramOffsets.put(p.name, paramOffsetCounter++);
                paramTypes.put(p.name, p.type);
            }

            int localOffsetCounter = 1;
            for (VarDecl l : localDecls) {
                if (paramOffsets.containsKey(l.name) || localOffsets.containsKey(l.name)) {
                    throw new StaticAnalysisException("Duplicate local variable name or shadowing: " + l.name + " in method " + name);
                }
                localOffsets.put(l.name, localOffsetCounter++);
                localTypes.put(l.name, l.type);
            }
        }

        Type getParamType(String name) { return paramTypes.get(name); }
        Type getLocalType(String name) { return localTypes.get(name); }
        Integer getParamOffset(String name) { return paramOffsets.get(name); }
        Integer getLocalOffset(String name) { return localOffsets.get(name); }
        int getParamCount() { return parameters.size(); }
        int getLocalCount() { return locals.size(); }
    }

    private static class GlobalInfo {
        final Type type;
        final int address;
        GlobalInfo(Type t, int addr) { this.type = t; this.address = addr; }
    }


    private final Map<String, GlobalInfo> globals;
    private final Map<String, MethodInfo> methodTable;
    private String currentMethodName;
    private int freshNameCounter;

    /**
     * Initialise a new symbol table.
     * @param program the program
     */
    public SymbolTable(Program program) {
        this.freshNameCounter = 0;
        this.globals = new HashMap<>();
        this.methodTable = new HashMap<>();
        this.currentMethodName = null;
        int nextGlobalAddress = 0;

        for (VarDecl decl: program.varDecls) {
            GlobalInfo duplicate = this.globals.get(decl.name);
            if (duplicate != null) {
                throw new StaticAnalysisException("Duplicate global variable: " + decl.name);
            }
            GlobalInfo info = new GlobalInfo(decl.type, nextGlobalAddress);
            this.globals.put(decl.name, info);
            nextGlobalAddress += 4;
        }

        for (MethodDef methodDef : program.methods) {
            String name = methodDef.getMethodName();
            if (methodTable.containsKey(name)) {
                throw new StaticAnalysisException("Duplicate method name: " + name);
            }
            Type returnType = null;
            if (methodDef instanceof FunDef) {
                returnType = ((FunDef) methodDef).returnType;
            }
            MethodInfo info = new MethodInfo(name, returnType, methodDef.formals, methodDef.locals);
            methodTable.put(name, info);
        }
    }

    public ScopeInfo getVarScopeInfo(String varName) {
        if (currentMethodName != null) {
            MethodInfo currentMethod = methodTable.get(currentMethodName);
            if (currentMethod != null) {
                Integer localOffset = currentMethod.getLocalOffset(varName);
                if (localOffset != null) {
                    return new ScopeInfo(Scope.LOCAL, localOffset, currentMethod.getLocalType(varName));
                }

                Integer paramOffset = currentMethod.getParamOffset(varName);
                if (paramOffset != null) {
                    return new ScopeInfo(Scope.PARAMETER, paramOffset, currentMethod.getParamType(varName));
                }
            }
        }

        GlobalInfo globalInfo = globals.get(varName);
        if (globalInfo != null) {
            return new ScopeInfo(Scope.GLOBAL, globalInfo.address, globalInfo.type);
        }

        throw new StaticAnalysisException("Undeclared variable: " + varName);
    }

    public String getMethodLabel(String methodName) {
        if (!methodTable.containsKey(methodName)) {
            throw new StaticAnalysisException("Call to undefined method: " + methodName);
        }
        return "$_" + methodName;
    }

    public void enterMethodScope(String methodName) {
        if (!methodTable.containsKey(methodName)) {
            throw new StaticAnalysisException("Internal Compiler Error: Entering scope for unknown method: " + methodName);
        }
        this.currentMethodName = methodName;
    }

    public void exitMethodScope() {
        this.currentMethodName = null;
    }

    public int getParamCountForCurrentMethod() {
        if (currentMethodName == null) {
            throw new IllegalStateException("Cannot get parameter count outside of a method scope.");
        }
        MethodInfo currentMethod = methodTable.get(currentMethodName);
        return (currentMethod != null) ? currentMethod.getParamCount() : 0;
    }

    public int getLocalCountForCurrentMethod() {
        if (currentMethodName == null) {
            throw new IllegalStateException("Cannot get local variable count outside of a method scope.");
        }
        MethodInfo currentMethod = methodTable.get(currentMethodName);
        return (currentMethod != null) ? currentMethod.getLocalCount() : 0;
    }
    public Type getMethodReturnType(String methodName) {
        MethodInfo info = methodTable.get(methodName);
        if (info == null) {
            throw new StaticAnalysisException("Call to undefined method: " + methodName);
        }
        return info.returnType;
    }

    public boolean isInMethodScope() {
        return this.currentMethodName != null;
    }


    /**
     * Each call to this method will return a fresh name which is
     * guaranteed not to clash with any name returned by makeVarLabel(x),
     * where x is any LPL identifier.
     * @param prefix a string to include as part of the generated name.
     * @return a fresh name which is prefixed with "$$_".
     */
    public String freshLabel(String prefix) {
        return "$$_" + prefix + "_" + (freshNameCounter++);
    }

    public static class StaticAnalysisException extends RuntimeException {
        public StaticAnalysisException(String message) {
            super(message);
        }
    }
}
