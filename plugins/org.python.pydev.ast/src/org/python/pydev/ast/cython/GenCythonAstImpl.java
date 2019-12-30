package org.python.pydev.ast.cython;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jface.text.IDocumentExtension4;
import org.python.pydev.ast.codecompletion.shell.AbstractShell;
import org.python.pydev.ast.codecompletion.shell.CythonShell;
import org.python.pydev.ast.interpreter_managers.InterpreterManagersAPI;
import org.python.pydev.core.log.Log;
import org.python.pydev.core.proposals.CompletionProposalFactory;
import org.python.pydev.json.eclipsesource.JsonArray;
import org.python.pydev.json.eclipsesource.JsonObject;
import org.python.pydev.json.eclipsesource.JsonValue;
import org.python.pydev.parser.PyParser.ParserInfo;
import org.python.pydev.parser.grammarcommon.CtxVisitor;
import org.python.pydev.parser.jython.ParseException;
import org.python.pydev.parser.jython.SimpleNode;
import org.python.pydev.parser.jython.ast.Assert;
import org.python.pydev.parser.jython.ast.Assign;
import org.python.pydev.parser.jython.ast.AugAssign;
import org.python.pydev.parser.jython.ast.Await;
import org.python.pydev.parser.jython.ast.BinOp;
import org.python.pydev.parser.jython.ast.BoolOp;
import org.python.pydev.parser.jython.ast.Call;
import org.python.pydev.parser.jython.ast.ClassDef;
import org.python.pydev.parser.jython.ast.Compare;
import org.python.pydev.parser.jython.ast.Dict;
import org.python.pydev.parser.jython.ast.For;
import org.python.pydev.parser.jython.ast.FunctionDef;
import org.python.pydev.parser.jython.ast.If;
import org.python.pydev.parser.jython.ast.IfExp;
import org.python.pydev.parser.jython.ast.Name;
import org.python.pydev.parser.jython.ast.NameTok;
import org.python.pydev.parser.jython.ast.NameTokType;
import org.python.pydev.parser.jython.ast.Num;
import org.python.pydev.parser.jython.ast.Pass;
import org.python.pydev.parser.jython.ast.Return;
import org.python.pydev.parser.jython.ast.Set;
import org.python.pydev.parser.jython.ast.Str;
import org.python.pydev.parser.jython.ast.Suite;
import org.python.pydev.parser.jython.ast.While;
import org.python.pydev.parser.jython.ast.Yield;
import org.python.pydev.parser.jython.ast.argumentsType;
import org.python.pydev.parser.jython.ast.decoratorsType;
import org.python.pydev.parser.jython.ast.exprType;
import org.python.pydev.parser.jython.ast.keywordType;
import org.python.pydev.parser.jython.ast.stmtType;
import org.python.pydev.parser.jython.ast.suiteType;
import org.python.pydev.parser.jython.ast.factory.AdapterPrefs;
import org.python.pydev.parser.jython.ast.factory.PyAstFactory;
import org.python.pydev.plugin.nature.SystemPythonNature;
import org.python.pydev.shared_core.model.ISimpleNode;
import org.python.pydev.shared_core.parsing.BaseParser.ParseOutput;
import org.python.pydev.shared_core.string.StringUtils;

public class GenCythonAstImpl {

    public static boolean IN_TESTS = false;
    private final ParserInfo parserInfo;

    public GenCythonAstImpl(ParserInfo parserInfo) {
        this.parserInfo = parserInfo;
    }

    private static class JsonToNodesBuilder {

        final List<stmtType> stmts = new ArrayList<stmtType>();
        final PyAstFactory astFactory;
        final CtxVisitor ctx = new CtxVisitor(null);

        public JsonToNodesBuilder(ParserInfo p) {
            astFactory = new PyAstFactory(new AdapterPrefs("\n", p));
        }

        public void addStatement(JsonValue jsonStmt) throws Exception {
            ISimpleNode node = createNode(jsonStmt);
            addToStmtsList(node, stmts);
        }

        private ISimpleNode createNode(JsonValue jsonValue) {
            if (jsonValue == null || (jsonValue.isString() && "None".equals(jsonValue.asString()))) {
                return null;
            }
            JsonObject asObject = jsonValue.asObject();
            JsonValue jsonNode = asObject.get("__node__");
            ISimpleNode node = null;
            if (jsonNode != null) {
                try {
                    switch (jsonNode.asString()) {
                        case "Int":
                            node = createInt(asObject);
                            break;

                        case "Name":
                            node = createName(asObject);
                            break;

                        case "Bool":
                            node = createBool(asObject);
                            break;

                        case "Null":
                        case "None":
                            node = createNone(asObject);
                            break;

                        case "IfStat":
                            node = createIf(asObject);
                            break;

                        case "JoinedStr":
                            node = createFString(asObject);
                            break;

                        case "String":
                            node = createString(asObject);
                            break;

                        case "Tuple":
                            node = createTuple(asObject);
                            break;

                        case "Dict":
                            node = createDict(asObject);
                            break;

                        case "List":
                            node = createList(asObject);
                            break;

                        case "Set":
                            node = createSet(asObject);
                            break;

                        case "Unicode":
                            node = createUnicode(asObject);
                            break;

                        case "Bytes":
                            node = createBytes(asObject);
                            break;

                        case "PassStat":
                            node = createPass(asObject);
                            break;

                        case "ExprStat":
                            node = createExpr(asObject);
                            break;

                        case "SimpleCall":
                            node = createSimpleCall(asObject);
                            break;

                        case "GeneralCall":
                            node = createGeneralCall(asObject);
                            break;

                        case "YieldExpr":
                            node = createYieldExpr(asObject);
                            break;

                        case "Def":
                            node = createFunctionDef(asObject);
                            break;

                        case "CFuncDef":
                            node = createCFunctionDef(asObject);
                            break;

                        case "SingleAssignment":
                            node = createSingleAssignment(asObject);
                            break;

                        case "ForInStat":
                        case "AsyncForStat":
                            node = createFor(asObject);
                            break;

                        case "AwaitExpr":
                            node = createAwait(asObject);
                            break;

                        case "CascadedCmp":
                            node = createCascade(asObject);
                            break;

                        case "PyClassDef":
                            node = createClassDef(asObject);
                            break;

                        case "CClassDef":
                            node = createCClassDef(asObject);
                            break;

                        case "PrimaryCmp":
                            node = createCompare(asObject);
                            break;

                        case "BoolBinop":
                            node = createBoolOp(asObject);
                            break;

                        case "CVarDef":
                            node = createCVarDef(asObject);
                            break;

                        case "CondExpr":
                            node = createCondExpr(asObject);
                            break;

                        case "WhileStat":
                            node = createWhile(asObject);
                            break;

                        case "AssertStat":
                            node = createAssert(asObject);
                            break;

                        case "ReturnStat":
                            node = createReturn(asObject);
                            break;

                        case "CFuncDeclarator":
                            node = createCFuncDeclarator(asObject);
                            break;

                        case "CDefExtern":
                            node = createCDefExtern(asObject);
                            break;

                        case "CStructOrUnionDef":
                            node = createCStructOrUnionDef(asObject);
                            break;

                        case "CTypeDef":
                            node = createCtypeDef(asObject);
                            break;

                        case "SizeofType":
                            node = createSizeofType(asObject);
                            break;

                        case "SizeofVar":
                            node = createSizeofVar(asObject);
                            break;

                        case "CEnumDef":
                            node = createCEnumDef(asObject);
                            break;

                        case "InPlaceAssignment":
                            node = createAugAssign(asObject);
                            break;

                        case "Add":
                        case "Sub":
                        case "Mul":
                        case "Div":
                        case "MatMult":
                        case "IntBinop":
                            node = createBinOp(asObject);
                            break;

                        default:
                            String msg = "Don't know how to create statement from cython json: "
                                    + asObject.toPrettyString();
                            log(msg);
                            break;

                    }
                } catch (Exception e) {
                    log(e);
                }
            }
            return node;
        }

        private org.python.pydev.parser.jython.ast.Tuple createTuple(JsonObject asObject) throws Exception {
            boolean endsWithComma = false;
            List<exprType> extract = extractExprs(asObject, "args");
            org.python.pydev.parser.jython.ast.Tuple tup = new org.python.pydev.parser.jython.ast.Tuple(
                    astFactory.createExprArray(extract.toArray()),
                    org.python.pydev.parser.jython.ast.Tuple.Load,
                    endsWithComma);
            setLine(tup, asObject);
            return tup;
        }

        private org.python.pydev.parser.jython.ast.List createList(JsonObject asObject) throws Exception {
            List<exprType> extract = extractExprs(asObject, "args");
            org.python.pydev.parser.jython.ast.List tup = new org.python.pydev.parser.jython.ast.List(
                    astFactory.createExprArray(extract.toArray()),
                    org.python.pydev.parser.jython.ast.List.Load);
            setLine(tup, asObject);
            return tup;
        }

        private Set createSet(JsonObject asObject) throws Exception {
            List<exprType> extract = extractExprs(asObject, "args");
            Set tup = new Set(astFactory.createExprArray(extract.toArray()));
            setLine(tup, asObject);
            return tup;
        }

        private Dict createDict(JsonObject asObject) throws Exception {
            List<JsonValue> bodyAsList = getBodyAsList(asObject.get("key_value_pairs"));
            List<exprType> keys = new ArrayList<>();
            List<exprType> values = new ArrayList<>();
            for (JsonValue jsonValue : bodyAsList) {
                JsonObject itemAsObj = jsonValue.asObject();
                ISimpleNode key = createNode(itemAsObj.get("key"));
                ISimpleNode val = createNode(itemAsObj.get("value"));
                keys.add(astFactory.asExpr(key));
                values.add(astFactory.asExpr(val));
            }
            Dict tup = new Dict(keys.toArray(new exprType[0]), values.toArray(new exprType[0]));
            setLine(tup, asObject);
            return tup;
        }

        private SimpleNode createString(JsonObject asObject) {
            boolean raw = false;
            boolean unicode = false;
            boolean binary = false;
            boolean fstring = false;
            int type = Str.SingleSingle;
            String s = "";

            JsonValue value = asObject.get("unicode_value");
            if (value != null && value.isString()) {
                s = value.asString();
            }

            Str str = new Str(s, type, unicode, raw, binary, fstring, null);
            setLine(str, asObject);
            return str;
        }

        private SimpleNode createFString(JsonObject asObject) throws Exception {
            boolean raw = false;
            boolean unicode = true;
            boolean binary = false;
            boolean fstring = false;
            int type = Str.SingleSingle;
            String s = "";

            List<ISimpleNode> stmts = new ArrayList<>();

            JsonValue values = asObject.get("values");
            if (values != null && values.isArray()) {
                for (JsonValue v : values.asArray()) {
                    if (v.isObject()) {
                        JsonObject o = v.asObject();
                        JsonValue n = o.get("__node__");
                        if (n != null && n.isString() && n.asString().equals("FormattedValue")) {
                            ISimpleNode node = createNode(o.get("value"));
                            addToNodesList(node, stmts);
                        } else {
                            ISimpleNode node = createNode(o);
                            if (node != null) {
                                stmts.add(node);
                            }
                        }
                    }
                }
            }

            Str str = new Str(s, type, unicode, raw, binary, fstring,
                    stmts.size() == 0 ? null : astFactory.createStmtArray(stmts.toArray()));
            setLine(str, asObject);
            return str;
        }

        private void addToStmtsList(ISimpleNode node, List<stmtType> stmts) {
            if (node != null) {
                if (node instanceof NodeList) {
                    NodeList nodeList = (NodeList) node;
                    for (ISimpleNode n : nodeList.nodes) {
                        addToStmtsList(n, stmts);
                    }
                } else {
                    stmts.add(astFactory.asStmt(node));
                }
            }
        }

        private void addToExprsList(ISimpleNode node, List<exprType> exprs) {
            if (node != null) {
                if (node instanceof NodeList) {
                    NodeList nodeList = (NodeList) node;
                    for (ISimpleNode n : nodeList.nodes) {
                        addToExprsList(n, exprs);
                    }
                } else {
                    exprs.add(astFactory.asExpr(node));
                }
            }
        }

        private void addToNodesList(ISimpleNode node, List<ISimpleNode> lst) {
            if (node != null) {
                if (node instanceof NodeList) {
                    NodeList nodeList = (NodeList) node;
                    for (ISimpleNode n : nodeList.nodes) {
                        addToNodesList(n, lst);
                    }
                } else {
                    lst.add(node);
                }
            }
        }

        private SimpleNode createUnicode(JsonObject asObject) {
            boolean raw = false;
            boolean unicode = true;
            boolean binary = false;
            boolean fstring = false;
            int type = Str.SingleSingle;
            String s = "";

            JsonValue value = asObject.get("value");
            if (value != null && value.isString()) {
                s = value.asString();
            }

            Str str = new Str(s, type, unicode, raw, binary, fstring, null);
            setLine(str, asObject);
            return str;
        }

        private SimpleNode createBytes(JsonObject asObject) {
            boolean raw = false;
            boolean unicode = false;
            boolean binary = true;
            boolean fstring = false;
            int type = Str.SingleSingle;
            String s = "";

            JsonValue value = asObject.get("value");
            if (value != null && value.isString()) {
                s = value.asString();
            }

            Str str = new Str(s, type, unicode, raw, binary, fstring, null);
            setLine(str, asObject);
            return str;
        }

        private SimpleNode createGeneralCall(final JsonObject asObject) throws Exception {
            final JsonValue funcJsonValue = asObject.get("function");
            if (funcJsonValue != null && funcJsonValue.isObject()) {
                final JsonObject funcAsObject = funcJsonValue.asObject();
                final Name name = createName(funcAsObject);
                if (name != null) {
                    final JsonValue jsonValueArgs = asObject.get("positional_args");
                    List<exprType> params = new ArrayList<>();
                    keywordType[] keywords = new keywordType[0];
                    exprType starargs = null;
                    exprType kwargs = null;

                    if (jsonValueArgs != null && jsonValueArgs.isArray()) {
                        for (JsonValue v : jsonValueArgs.asArray()) {
                            ISimpleNode n = createNode(v);
                            if (n != null) {
                                params.add((exprType) n);
                            }
                        }
                    }

                    Call call = astFactory.createCall(name, params, keywords, starargs, kwargs);
                    setLine(call, asObject);
                    return call;
                }
            }
            return null;
        }

        private SimpleNode createSimpleCall(final JsonObject asObject) throws Exception {
            final JsonValue funcJsonValue = asObject.get("function");
            if (funcJsonValue != null && funcJsonValue.isObject()) {
                final JsonObject funcAsObject = funcJsonValue.asObject();
                final Name name = createName(funcAsObject);
                if (name != null) {

                    final JsonValue jsonValueArgs = asObject.get("args");
                    List<exprType> params = new ArrayList<>();
                    keywordType[] keywords = new keywordType[0];
                    exprType starargs = null;
                    exprType kwargs = null;

                    if (jsonValueArgs != null && jsonValueArgs.isArray()) {
                        for (JsonValue v : jsonValueArgs.asArray()) {
                            ISimpleNode n = createNode(v);
                            if (n != null) {
                                params.add((exprType) n);
                            }
                        }
                    }

                    Call call = astFactory.createCall(name, params, keywords, starargs, kwargs);
                    setLine(call, asObject);
                    return call;
                }
            }
            return null;
        }

        public ISimpleNode createAugAssign(JsonObject asObject) {
            AugAssign node = null;
            JsonValue op1 = asObject.get("lhs");
            JsonValue op2 = asObject.get("rhs");
            JsonValue operator = asObject.get("operator");
            if (op1 != null && op1.isObject() && op2 != null && op2.isObject() && operator != null
                    && operator.isString()) {
                ISimpleNode left = createNode(op1);
                ISimpleNode right = createNode(op2);
                int op = 0;
                switch (operator.asString()) {
                    case "+":
                        op = AugAssign.Add;
                        break;
                    case "-":
                        op = AugAssign.Sub;
                        break;
                    case "*":
                        op = AugAssign.Mult;
                        break;
                    case "/":
                        op = AugAssign.Div;
                        break;
                    case "@":
                        op = AugAssign.Dot;
                        break;
                    case "&":
                        op = AugAssign.BitAnd;
                        break;
                    case "|":
                        op = AugAssign.BitOr;
                        break;
                    case "^":
                        op = AugAssign.BitXor;
                        break;

                }
                exprType leftAsExpr = astFactory.asExpr(left);
                try {
                    ctx.setAugStore(leftAsExpr);
                } catch (Exception e) {
                    Log.log(e);
                }
                node = new AugAssign(leftAsExpr, op, astFactory.asExpr(right));
                setLine(node, asObject);
            }
            return node;
        }

        public ISimpleNode createBinOp(JsonObject asObject) {
            BinOp node = null;
            JsonValue op1 = asObject.get("operand1");
            JsonValue op2 = asObject.get("operand2");
            JsonValue operator = asObject.get("operator");
            if (op1 != null && op1.isObject() && op2 != null && op2.isObject() && operator != null
                    && operator.isString()) {
                ISimpleNode left = createNode(op1);
                ISimpleNode right = createNode(op2);
                int op = 0;
                switch (operator.asString()) {
                    case "+":
                        op = BinOp.Add;
                        break;
                    case "-":
                        op = BinOp.Sub;
                        break;
                    case "*":
                        op = BinOp.Mult;
                        break;
                    case "/":
                        op = BinOp.Div;
                        break;
                    case "@":
                        op = BinOp.Dot;
                        break;
                    case "&":
                        op = BinOp.BitAnd;
                        break;
                    case "|":
                        op = BinOp.BitOr;
                        break;
                    case "^":
                        op = BinOp.BitXor;
                        break;
                    default:
                        break;

                }
                node = new BinOp(astFactory.asExpr(left), op, astFactory.asExpr(right));
                setLine(node, asObject);
            }
            return node;
        }

        private BoolOp createBoolOp(JsonObject asObject) {

            BoolOp node = null;
            JsonValue op1 = asObject.get("operand1");
            JsonValue op2 = asObject.get("operand2");
            JsonValue operator = asObject.get("operator");
            if (op1 != null && op1.isObject() && op2 != null && op2.isObject() && operator != null
                    && operator.isString()) {
                ISimpleNode left = createNode(op1);
                ISimpleNode right = createNode(op2);
                int op = 0;
                switch (operator.asString()) {
                    case "or":
                        op = BoolOp.Or;
                        break;
                    case "and":
                        op = BoolOp.And;
                        break;

                }
                node = new BoolOp(op, new exprType[] { astFactory.asExpr(left), astFactory.asExpr(right) });
                setLine(node, asObject);
            }
            return node;
        }

        private Compare createCompare(JsonObject asObject) {

            Compare node = null;
            JsonValue op1 = asObject.get("operand1");
            JsonValue op2 = asObject.get("operand2");
            JsonValue operator = asObject.get("operator");
            if (op1 != null && op1.isObject() && op2 != null && op2.isObject() && operator != null
                    && operator.isString()) {
                ISimpleNode left = createNode(op1);

                List<exprType> right = new ArrayList<>();
                right.add(astFactory.asExpr(createNode(op2)));

                List<Integer> operators = new ArrayList<Integer>();
                operators.add(opToCompareOp(operator.asString()));

                JsonValue cascadeValue = asObject.get("cascade");
                if (cascadeValue != null && cascadeValue.isObject()) {
                    CascadeNode cascadeNode = (CascadeNode) createNode(cascadeValue);
                    while (cascadeNode != null) {
                        operators.add(opToCompareOp(cascadeNode.operator));
                        right.add(astFactory.asExpr(cascadeNode.operand));
                        cascadeNode = cascadeNode.cascade;
                    }
                }
                node = new Compare(astFactory.asExpr(left), operators.stream().mapToInt(i -> i).toArray(),
                        right.toArray(new exprType[0]));
                setLine(node, asObject);
            }
            return node;
        }

        private int opToCompareOp(String opStr) {
            int op = 0;
            switch (opStr) {
                case ">":
                    op = Compare.Gt;
                    break;
                case "==":
                    op = Compare.Eq;
                    break;
                case "!=":
                    op = Compare.NotEq;
                    break;
                case "<":
                    op = Compare.Lt;
                    break;
                case ">=":
                    op = Compare.GtE;
                    break;
                case "<=":
                    op = Compare.LtE;
                    break;
                case "in":
                    op = Compare.In;
                    break;
                case "not_in":
                    op = Compare.NotIn;
                    break;
                case "is":
                    op = Compare.Is;
                    break;
                case "is_not":
                    op = Compare.IsNot;
                    break;

            }
            return op;
        }

        private static class CascadeNode implements ISimpleNode {

            public final String operator;
            public final ISimpleNode operand;
            public final CascadeNode cascade;

            public CascadeNode(String operator, ISimpleNode operand, CascadeNode cascade) {
                this.operator = operator;
                this.operand = operand;
                this.cascade = cascade;
            }

        }

        private ISimpleNode createCascade(JsonObject asObject) {
            JsonValue operator = asObject.get("operator");
            JsonValue operand = asObject.get("operand2");
            JsonValue cascade = asObject.get("cascade");
            return new CascadeNode(operator.asString(), createNode(operand), (CascadeNode) createNode(cascade));
        }

        private exprType createExpr(JsonObject asObject) throws Exception {
            try {
                ISimpleNode node = createNode(asObject.get("expr"));
                exprType expr = astFactory.asExpr(node);
                setLine(expr, asObject);
                return expr;
            } catch (Exception e) {
                log(e);
                return null;
            }
        }

        private SimpleNode createAwait(JsonObject asObject) {
            try {
                exprType node = astFactory.asExpr(createNode(asObject.get("arg")));
                Await await = new Await(node);
                setLine(await, asObject);
                return await;
            } catch (Exception e) {
                log(e);
                return null;
            }
        }

        private Pass createPass(JsonObject asObject) {
            Pass pass = astFactory.createPass();
            setLine(pass, asObject);
            return pass;
        }

        private void setBases(JsonObject asObject, ClassDef classDef) {
            List<exprType> bases = extractExprs(asObject, "bases");
            if (bases.size() == 1 && bases.get(0) instanceof org.python.pydev.parser.jython.ast.Tuple) {
                org.python.pydev.parser.jython.ast.Tuple tuple = (org.python.pydev.parser.jython.ast.Tuple) bases
                        .get(0);
                astFactory.setBases(classDef, (Object[]) tuple.elts);

            } else {
                astFactory.setBases(classDef, bases.toArray());
            }
        }

        private ClassDef createCEnumDef(JsonObject asObject) throws Exception {
            final JsonValue value = asObject.get("name");
            if (value != null && value.isString()) {
                ClassDef classDef = astFactory.createClassDef(value.asString());
                setLine(classDef, asObject);
                classDef.name.beginLine = classDef.beginLine;
                classDef.name.beginColumn = classDef.beginColumn + 7;

                List<Assign> nodes = new ArrayList<>();
                JsonValue items = asObject.get("items");
                if (items != null && items.isArray()) {
                    JsonArray asArray = items.asArray();
                    for (JsonValue item : asArray) {
                        if (item.isObject()) {
                            Name name = createName(item.asObject());
                            if (name != null) {
                                ctx.setStore(name);
                                Num num = new Num(new Integer(0), Num.Int, "0");
                                Assign assign = astFactory.createAssign(name, num);
                                setLine(assign, item.asObject());
                                setLine(num, item.asObject());
                                nodes.add(assign);
                            }
                        }
                    }
                }
                astFactory.setBody(classDef, nodes.toArray());

                classDef.decs = createDecorators(asObject);
                return classDef;
            }
            return null;
        }

        private ClassDef createClassDef(JsonObject asObject) throws Exception {
            final JsonValue value = asObject.get("name");
            if (value != null && value.isString()) {
                ClassDef classDef = astFactory.createClassDef(value.asString());
                setLine(classDef, asObject);
                classDef.name.beginLine = classDef.beginLine;
                classDef.name.beginColumn = classDef.beginColumn + 7;

                setBases(asObject, classDef);

                astFactory.setBody(classDef, extractStmts(asObject, "body").toArray());

                classDef.decs = createDecorators(asObject);
                return classDef;
            }
            return null;
        }

        private ISimpleNode createSizeofVar(JsonObject asObject) throws Exception {
            Call call = astFactory.createCall("sizeof");
            setLine(call.func, asObject);
            setLine(call, asObject);
            return call;
        }

        private ISimpleNode createSizeofType(JsonObject asObject) throws Exception {
            Call call = astFactory.createCall("sizeof");
            setLine(call.func, asObject);
            setLine(call, asObject);
            return call;
        }

        private ISimpleNode createCtypeDef(JsonObject asObject) throws Exception {
            final JsonValue declarator = getDeclarator(asObject);
            if (declarator.isObject()) {
                Name name = createName(declarator.asObject());
                if (name != null) {
                    ctx.setStore(name);
                    Name nameNode = createNameFromBaseType(asObject);
                    if (nameNode == null) {
                        nameNode = astFactory.createNone();
                    }
                    Assign assign = astFactory.createAssign(name, nameNode);
                    setLine(assign, asObject);
                    return assign;
                }
            }
            return null;
        }

        private ISimpleNode createCStructOrUnionDef(JsonObject asObject) throws Exception {
            final JsonValue value = asObject.get("name");
            if (value != null && value.isString()) {
                ClassDef classDef = astFactory.createClassDef(value.asString());
                setLine(classDef, asObject);
                classDef.name.beginLine = classDef.beginLine;
                classDef.name.beginColumn = classDef.beginColumn + 6;

                astFactory.setBody(classDef, extractStmts(asObject, "attributes").toArray());
                return classDef;
            }
            return null;
        }

        private ClassDef createCClassDef(JsonObject asObject) throws Exception {
            final JsonValue value = asObject.get("class_name");
            if (value != null && value.isString()) {
                ClassDef classDef = astFactory.createClassDef(value.asString());
                setLine(classDef, asObject);
                classDef.name.beginLine = classDef.beginLine;
                classDef.name.beginColumn = classDef.beginColumn + 6; // cdef class X

                setBases(asObject, classDef);

                astFactory.setBody(classDef, extractStmts(asObject, "body").toArray());

                classDef.decs = createDecorators(asObject);
                return classDef;
            }
            return null;
        }

        private FunctionDef createFunctionDef(JsonObject asObject) throws Exception {
            final JsonValue value = asObject.get("name");
            if (value != null && value.isString()) {
                FunctionDef funcDef = astFactory.createFunctionDef(value.asString());
                setLine(funcDef, asObject);
                funcDef.name.beginLine = funcDef.beginLine;
                funcDef.name.beginColumn = funcDef.beginColumn + 4;

                funcDef.args = createArgs(asObject);
                funcDef.decs = createDecorators(asObject);

                JsonValue isAsyncDef = asObject.get("is_async_def");
                if (isAsyncDef != null && isAsyncDef.asString().equals("True")) {
                    funcDef.async = true;
                }
                astFactory.setBody(funcDef, extractStmts(asObject, "body").toArray());
                return funcDef;
            }
            return null;
        }

        private FunctionDef createCFuncDeclarator(JsonObject declarator) throws Exception {
            FunctionDef funcDef = null;
            JsonValue baseDeclarator = declarator.asObject().get("base");
            if (baseDeclarator != null) {
                JsonValue name = baseDeclarator.asObject().get("name");
                if (name != null) {
                    NameTokType nameTok = createNameTok(baseDeclarator.asObject(), NameTok.FunctionName);
                    funcDef = astFactory.createFunctionDef(nameTok);
                    setLine(funcDef, declarator);
                    funcDef.args = createArgs(declarator.asObject());
                }
            }
            return funcDef;
        }

        private FunctionDef createCFunctionDef(JsonObject asObject) throws Exception {
            final JsonValue declarator = getDeclarator(asObject);
            if (declarator != null && declarator.isObject()) {
                FunctionDef funcDef = createCFuncDeclarator(declarator.asObject());
                if (funcDef != null) {
                    funcDef.decs = createDecorators(asObject);
                    //                funcDef.name.beginLine = funcDef.beginLine;
                    //                funcDef.name.beginColumn = funcDef.beginColumn + 4;
                    //
                    //                funcDef.args = createArgs(asObject);
                    //                funcDef.decs = createDecorators(asObject);
                    //
                    //                JsonValue isAsyncDef = asObject.get("is_async_def");
                    //                if (isAsyncDef != null && isAsyncDef.asString().equals("True")) {
                    //                    funcDef.async = true;
                    //                }
                    astFactory.setBody(funcDef, extractStmts(asObject, "body").toArray());
                }
                return funcDef;
            }
            return null;
        }

        public List<stmtType> extractStmts(JsonObject asObject, String field) {
            JsonValue jsonValue = asObject.get(field);
            ArrayList<JsonValue> bodyAsList = getBodyAsList(jsonValue);
            List<stmtType> lst = new ArrayList<>();
            for (JsonValue v : bodyAsList) {
                ISimpleNode bodyNode = createNode(v);
                addToStmtsList(bodyNode, lst);
            }
            return lst;
        }

        public List<exprType> extractExprs(JsonObject asObject, String field) {
            JsonValue jsonValue = asObject.get(field);
            ArrayList<JsonValue> bodyAsList = getBodyAsList(jsonValue);
            List<exprType> lst = new ArrayList<>();
            for (JsonValue v : bodyAsList) {
                ISimpleNode bodyNode = createNode(v);
                addToExprsList(bodyNode, lst);
            }
            return lst;
        }

        private decoratorsType[] createDecorators(JsonObject asObject) throws Exception {
            List<decoratorsType> decs = new ArrayList<decoratorsType>();
            JsonValue jsonValue = asObject.get("decorators");
            if (jsonValue != null && jsonValue.isArray()) {
                for (JsonValue v : jsonValue.asArray()) {
                    decoratorsType dec = createDecorator(v);
                    if (dec != null) {
                        decs.add(dec);
                    }
                }
            }
            if (decs.size() == 0) {
                return null;
            }
            return decs.toArray(new decoratorsType[0]);
        }

        private decoratorsType createDecorator(JsonValue v) throws Exception {
            if (v != null && v.isObject()) {
                JsonObject decAsObject = v.asObject();
                JsonValue decJsonValue = decAsObject.get("decorator");
                if (decJsonValue != null && decJsonValue.isObject()) {
                    JsonObject asObject = decJsonValue.asObject();
                    ISimpleNode func = createNode(asObject);
                    decoratorsType decorator = astFactory.createEmptyDecoratorsType();
                    if (func instanceof Name) {
                        decorator.func = (Name) func;
                    } else if (func instanceof Call) {
                        Call call = (Call) func;
                        decorator.func = call.func;
                        decorator.args = call.args;
                        decorator.keywords = call.keywords;
                        decorator.starargs = call.starargs;
                        decorator.kwargs = call.kwargs;
                        decorator.isCall = true;
                    } else {
                        if (func != null) {
                            log("Don't know how to create decorator from: " + func);
                        }
                    }
                    return decorator;
                }
            }
            return null;
        }

        private argumentsType createArgs(JsonObject funcAsObject) {
            argumentsType arguments = astFactory.createEmptyArgumentsType();
            JsonValue args = funcAsObject.get("args");
            if (args != null) {
                try {
                    List<exprType> argsList = new ArrayList<exprType>();
                    List<exprType> defaultsList = new ArrayList<exprType>();
                    List<exprType> annotationsList = new ArrayList<exprType>();

                    List<exprType> kwOnlyArgsList = new ArrayList<exprType>();
                    List<exprType> kwOnlyArgsDefaultsList = new ArrayList<exprType>();
                    List<exprType> kwOnlyArgsAnnotationsList = new ArrayList<exprType>();

                    for (JsonValue a : args.asArray()) {
                        JsonObject asObject = a.asObject();
                        JsonValue declaratorValue = getDeclarator(asObject);
                        if (declaratorValue != null) {
                            Name nameNode = null;
                            JsonObject declaratorAsObj = declaratorValue.asObject();
                            JsonValue nameValue = declaratorAsObj.get("name");
                            if (nameValue != null && !nameValue.asString().isEmpty()) {
                                nameNode = createName(declaratorAsObj);
                            }
                            if (nameNode == null) {
                                nameNode = createNameFromBaseType(asObject);

                            }
                            if (nameNode == null) {
                                log("Unable to get arg name in: " + asObject.toPrettyString());
                                continue;
                            }

                            boolean isKwOnly = false;
                            JsonValue kwOnlyValue = asObject.get("kw_only");
                            if (kwOnlyValue != null && kwOnlyValue.isString()) {
                                if ("1".equals(kwOnlyValue.asString())) {
                                    isKwOnly = true;
                                }
                            }

                            if (isKwOnly) {
                                ctx.setKwOnlyParam(nameNode);
                                kwOnlyArgsList.add(nameNode);
                                kwOnlyArgsDefaultsList.add((exprType) createNode(asObject.get("default")));
                                kwOnlyArgsAnnotationsList.add((exprType) createNode(asObject.get("annotation")));
                            } else {
                                ctx.setParam(nameNode);
                                argsList.add(nameNode);
                                defaultsList.add((exprType) createNode(asObject.get("default")));
                                annotationsList.add((exprType) createNode(asObject.get("annotation")));
                            }

                        }
                    }
                    arguments.kwonlyargs = kwOnlyArgsList.toArray(new exprType[0]);
                    arguments.kwonlyargannotation = kwOnlyArgsAnnotationsList.toArray(new exprType[0]);
                    arguments.kw_defaults = kwOnlyArgsDefaultsList.toArray(new exprType[0]);

                    arguments.args = argsList.toArray(new exprType[0]);
                    arguments.defaults = defaultsList.toArray(new exprType[0]);
                    arguments.annotation = annotationsList.toArray(new exprType[0]);
                } catch (Exception e) {
                    log(e);
                }
            }

            JsonValue starArgValue = funcAsObject.get("star_arg");
            if (starArgValue != null && starArgValue.isObject()) {
                arguments.vararg = createNameTok(starArgValue.asObject(), NameTok.VarArg);
            }

            JsonValue kwArgValue = funcAsObject.get("starstar_arg");
            if (kwArgValue != null && kwArgValue.isObject()) {
                arguments.kwarg = createNameTok(kwArgValue.asObject(), NameTok.KwArg);
            }
            return arguments;
        }

        private Name createNameFromBaseType(JsonObject asObject) {
            Name nameNode = null;
            JsonValue baseType = asObject.get("base_type");
            if (baseType != null && baseType.isObject()) {
                nameNode = createName(baseType.asObject());
                if (nameNode == null) {
                    JsonValue baseTypeNode = baseType.asObject().get("base_type_node");
                    if (baseTypeNode != null && baseTypeNode.isObject()) {
                        nameNode = createName(baseTypeNode.asObject());
                    }
                }
            }
            return nameNode;
        }

        private ArrayList<JsonValue> getBodyAsList(JsonValue jsonValue) {
            ArrayList<JsonValue> lst = new ArrayList<JsonValue>();
            if (jsonValue.isArray()) {
                for (JsonValue v : jsonValue.asArray()) {
                    lst.add(v);
                }
                return lst;
            }

            if (jsonValue.isString() && jsonValue.asString().equals("None")) {
                return lst;
            }
            JsonObject asObject = jsonValue.asObject();
            final JsonValue nodeType = asObject.get("__node__");
            if (nodeType != null) {
                if ("StatList".equals(nodeType.asString())) {
                    JsonValue stats = asObject.get("stats");
                    JsonArray asArray = stats.asArray();
                    for (JsonValue v : asArray) {
                        lst.add(v);
                    }
                } else {
                    // If it has a single element it may not be in a list.
                    lst.add(jsonValue);
                }
            }
            return lst;
        }

        private SimpleNode createFor(JsonObject asObject) throws Exception {
            exprType target = null;
            exprType iter = null;
            stmtType[] body = null;
            suiteType orelse = null;
            JsonValue isAsyncNode = asObject.get("is_async");
            boolean async = isAsyncNode != null && "True".equals(isAsyncNode.asString());

            JsonValue jsonTarget = asObject.get("target");
            if (jsonTarget != null) {
                target = astFactory.asExpr(createNode(jsonTarget));
                ctx.setStore(target);
            }
            JsonValue jsonIter = asObject.get("iterator");
            if (jsonIter != null) {
                JsonValue jsonValue = jsonIter.asObject().get("sequence");
                iter = astFactory.asExpr(createNode(jsonValue));
            }
            JsonValue jsonElse = asObject.get("else_clause");
            if (jsonElse != null && jsonElse.isObject()) {
                orelse = createSuite(jsonElse.asObject());
            }

            body = extractStmts(asObject, "body").toArray(new stmtType[0]);

            For node = new For(target, iter, body, orelse, async);
            setLine(node, asObject);
            return node;
        }

        private suiteType createSuite(JsonObject asObject) {
            JsonValue jsonNode = asObject.get("__node__");
            Suite suite = null;
            if (jsonNode != null && jsonNode.asString().equals("StatList")) {
                List<stmtType> extract = extractStmts(asObject, "stats");
                suite = new Suite(astFactory.createStmtArray(extract.toArray()));
                setLine(suite, asObject);

            } else {
                ISimpleNode node = createNode(asObject);
                if (node != null) {
                    ArrayList<stmtType> lst = new ArrayList<>();
                    addToStmtsList(node, lst);
                    suite = new Suite(lst.toArray(new stmtType[0]));

                }
            }
            return suite;
        }

        private SimpleNode createSingleAssignment(JsonObject asObject) throws Exception {
            SimpleNode node = null;
            JsonValue lhs = asObject.get("lhs");
            JsonValue rhs = asObject.get("rhs");
            if (lhs != null && lhs.isObject() && rhs != null && rhs.isObject()) {
                ISimpleNode left = createNode(lhs);
                ctx.setStore((SimpleNode) left);

                ISimpleNode right = createNode(rhs);
                node = astFactory.createAssign((exprType) left, (exprType) right);
                setLine(node, asObject);
            }
            return node;
        }

        private static class NodeList implements ISimpleNode {
            public List<ISimpleNode> nodes = new ArrayList<ISimpleNode>();
        }

        private JsonValue getDeclarator(JsonObject asObject) {
            JsonValue jsonValue = asObject.get("declarator");
            if (jsonValue.isObject()) {
                return getRemovingPtrDeclarator(jsonValue.asObject());
            }
            return jsonValue;
        }

        private JsonValue getRemovingPtrDeclarator(JsonObject asObject) {
            JsonValue jsonValue2 = asObject.get("__node__");
            if (jsonValue2.isString()) {
                if ("CPtrDeclarator".equals(jsonValue2.asString())) {
                    JsonValue base = asObject.get("base");
                    if (base != null && base.isObject()) {
                        return getRemovingPtrDeclarator(base.asObject());
                    }
                }
            }
            return asObject;
        }

        private ISimpleNode createCDefExtern(JsonObject asObject) throws Exception {
            NodeList nodeList = new NodeList();
            List<stmtType> extractStmts = extractStmts(asObject, "body");
            for (stmtType stmtType : extractStmts) {
                nodeList.nodes.add(stmtType);
            }
            return nodeList;
        }

        private ISimpleNode createCVarDef(JsonObject asObject) throws Exception {
            NodeList nodeList = new NodeList();
            JsonValue declarators = asObject.get("declarators");
            if (declarators != null && declarators.isArray()) {
                for (JsonValue d : declarators.asArray()) {
                    JsonValue declaratorWithoutPtr = getRemovingPtrDeclarator(d.asObject());
                    if (declaratorWithoutPtr.isObject()) {
                        JsonObject declaratorAsObject = declaratorWithoutPtr.asObject();
                        JsonValue nodeValue = declaratorAsObject.get("__node__");
                        if (nodeValue != null && nodeValue.isString()
                                && nodeValue.asString().equals("CFuncDeclarator")) {
                            nodeList.nodes.add(createNode(declaratorAsObject));

                        } else {
                            Name left = createName(declaratorAsObject.asObject());
                            exprType right = null;
                            if (left != null) {
                                JsonValue defaultJsonValue = declaratorAsObject.get("default");
                                if (defaultJsonValue == null
                                        || (defaultJsonValue.isString()
                                                && defaultJsonValue.asString().equals("None"))) {
                                    right = astFactory.createNone();
                                    setLine(right, declaratorAsObject);
                                } else {
                                    right = astFactory.asExpr(createNode(defaultJsonValue));
                                }
                                ctx.setStore(left);
                                if (right == null) {
                                    right = astFactory.createNone();
                                    setLine(right, declaratorAsObject);
                                }
                                Assign node = astFactory.createAssign(left, right);
                                setLine(node, declaratorAsObject);
                                nodeList.nodes.add(node);
                            } else {
                                log("Could not create name from: " + d.toPrettyString());
                            }
                        }

                    }
                }
            }
            return nodeList;
        }

        private While createWhile(JsonObject asObject) {
            exprType test = astFactory.asExpr(createNode(asObject.get("condition")));
            stmtType[] body = extractStmts(asObject, "body").toArray(new stmtType[0]);
            suiteType orelse = null;

            List<stmtType> extractStmts = extractStmts(asObject, "else_clause");
            if (extractStmts.size() > 0) {
                orelse = new Suite(extractStmts.toArray(new stmtType[0]));
            }

            While whileStmt = new While(test, body, orelse);
            setLine(whileStmt, asObject);
            return whileStmt;
        }

        private Assert createAssert(JsonObject asObject) {
            exprType cond = astFactory.asExpr(createNode(asObject.get("cond")));
            exprType value = astFactory.asExpr(createNode(asObject.get("value")));

            Assert assertStmt = new Assert(cond, value);
            setLine(assertStmt, asObject);
            return assertStmt;
        }

        private Return createReturn(JsonObject asObject) {
            exprType value = astFactory.asExpr(createNode(asObject.get("value")));

            Return returnStmt = new Return(value);
            setLine(returnStmt, asObject);
            return returnStmt;
        }

        private IfExp createCondExpr(JsonObject asObject) {
            exprType test = astFactory.asExpr(createNode(asObject.get("test")));
            exprType body = astFactory.asExpr(createNode(asObject.get("true_val")));
            exprType orelse = astFactory.asExpr(createNode(asObject.get("false_val")));

            IfExp ifNode = new IfExp(test, body, orelse);
            setLine(ifNode, asObject);
            return ifNode;
        }

        private If createIf(JsonObject asObject) {
            JsonValue ifClauses = asObject.get("if_clauses");
            If ifNode = null;
            If lastIfNode = null;
            if (ifClauses != null && ifClauses.isArray()) {
                for (JsonValue v : ifClauses.asArray()) {
                    JsonObject ifValueAsObject = v.asObject();
                    JsonValue conditionNodeValue = ifValueAsObject.get("condition");
                    if (conditionNodeValue != null) {
                        ISimpleNode conditionNode = createNode(conditionNodeValue);
                        suiteType orelse = null;
                        stmtType[] body = null;
                        If ifNodeTemp = new If(astFactory.asExpr(conditionNode), body, orelse);
                        astFactory.setBody(ifNodeTemp, extractStmts(ifValueAsObject, "body").toArray());
                        setLine(ifNodeTemp, ifValueAsObject);

                        if (ifNode == null) {
                            ifNode = ifNodeTemp;
                        } else if (lastIfNode != null) {
                            lastIfNode.orelse = new Suite(new stmtType[] { ifNodeTemp });
                        }
                        lastIfNode = ifNodeTemp;
                    }
                }

                if (lastIfNode != null) {
                    JsonValue jsonValue = asObject.get("else_clause");
                    if (jsonValue != null && jsonValue.isObject()) {
                        ISimpleNode elseClause = createNode(jsonValue);
                        List<stmtType> elseStmts = new ArrayList<>();
                        addToStmtsList(elseClause, elseStmts);
                        lastIfNode.orelse = new Suite(elseStmts.toArray(new stmtType[0]));
                    }
                }
            }
            return ifNode;
        }

        private SimpleNode createNone(JsonObject asObject) {
            Name node = astFactory.createNone();
            setLine(node, asObject);
            return node;
        }

        private SimpleNode createBool(JsonObject asObject) {
            JsonValue value = asObject.get("value");
            if (value != null && value.isString()) {
                Name node = astFactory.createName(value.asString());
                node.reserved = true;
                setLine(node, asObject);
                return node;
            }
            log("Unable to create bool with info: " + asObject);
            return null;
        }

        private NameTokType createNameTok(JsonObject asObject, int ctx) {
            NameTok node = null;
            JsonValue value;
            value = asObject.get("name");
            if (value != null) {
                node = new NameTok(value.asString(), ctx);
                setLine(node, asObject);
            }
            return node;
        }

        private Name createName(JsonObject asObject) {
            Name node = null;
            JsonValue value;
            value = asObject.get("name");
            if (value != null) {
                node = astFactory.createName(value.asString());
                setLine(node, asObject);
            }
            return node;
        }

        private Yield createYieldExpr(JsonObject asObject) throws Exception {
            Yield node = null;
            JsonValue value = asObject.get("arg");
            if (value != null) {
                node = new Yield(astFactory.asExpr(createNode(value)), false);
                setLine(node, asObject);
            }
            return node;
        }

        private SimpleNode createInt(JsonObject asObject) {
            JsonValue value = asObject.get("value");
            if (value != null) {
                Num node = new Num(new java.math.BigInteger(value.asString()), Num.Int, value.asString());
                setLine(node, asObject);
                return node;
            }
            return null;
        }

        private void setLine(SimpleNode node, JsonObject asObject) {
            if (node != null) {
                JsonValue line = asObject.get("line");
                if (line != null) {
                    node.beginLine = line.asInt();
                }
                JsonValue col = asObject.get("col");
                if (col != null) {
                    node.beginColumn = col.asInt() + 1;
                }
            }
        }

        public ISimpleNode createModule() {
            return astFactory.createModule(stmts);
        }

    }

    private ParseOutput jsonToParseOutput(ParserInfo p, String cythonJson, long modifiedTime) {
        JsonValue json = JsonValue.readFrom(cythonJson);
        JsonObject asObject = json.asObject();
        JsonValue jsonValue = asObject.get("__node__");
        if (jsonValue == null) {
            log("Unable to deal with: " + asObject.toPrettyString());

        } else if (jsonValue.isString() && !"StatList".equals(jsonValue.asString())) {
            if (jsonValue.asString().equals("CompileError")) {
                JsonValue lineValue = asObject.get("line");
                JsonValue colValue = asObject.get("col");
                JsonValue messageValue = asObject.get("message_only");
                ParseException exc = new ParseException(messageValue.asString(), lineValue.asInt(), colValue.asInt());
                return new ParseOutput(null, exc, modifiedTime);
            }
            log("Expected cython ast to have StatList as root. Found json: " + cythonJson);
            return null;
        } else {
            JsonValue body = asObject.get("stats");
            if (body != null && body.isArray()) {
                // System.out.println(body.toPrettyString());
                JsonToNodesBuilder builder = new JsonToNodesBuilder(p);
                JsonArray asArray = body.asArray();
                Iterator<JsonValue> iterator = asArray.iterator();
                while (iterator.hasNext()) {
                    JsonValue node = iterator.next();
                    try {
                        builder.addStatement(node);
                    } catch (Exception e) {
                        log("Error converting cython json to ast: " + node, e);
                    }
                }
                ISimpleNode ast = builder.createModule();
                return new ParseOutput(ast, null, modifiedTime);
            }
        }
        return null;
    }

    private static void log(String s) {
        Log.log(s);
        if (IN_TESTS) {
            throw new RuntimeException(s);
        }
    }

    private static void log(Exception e) {
        Log.log(e);
        if (IN_TESTS) {
            throw new RuntimeException(e);
        }
    }

    private static void log(String s, Exception e) {
        Log.log(s, e);
        if (IN_TESTS) {
            throw new RuntimeException(s, e);
        }
    }

    public ParseOutput genCythonAst() {
        long modifiedTime = ((IDocumentExtension4) parserInfo.document).getModificationStamp();
        String cythonJson = genCythonJson();
        return jsonToParseOutput(parserInfo, cythonJson, modifiedTime);
    }

    public String genCythonJson() {
        try {
            SystemPythonNature nature = new SystemPythonNature(InterpreterManagersAPI.getPythonInterpreterManager());
            CythonShell serverShell = (CythonShell) AbstractShell.getServerShell(nature,
                    CompletionProposalFactory.get().getCythonShellId());
            String contents = parserInfo.document.get();
            return serverShell.convertToJsonAst(StringUtils.replaceNewLines(contents, "\n"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
