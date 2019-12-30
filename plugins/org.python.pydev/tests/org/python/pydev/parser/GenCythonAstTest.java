package org.python.pydev.parser;

import java.io.File;
import java.nio.file.Path;

import org.eclipse.jface.text.Document;
import org.python.pydev.ast.codecompletion.revisited.CodeCompletionTestsBase;
import org.python.pydev.ast.codecompletion.revisited.modules.CompiledModule;
import org.python.pydev.ast.cython.GenCythonAstImpl;
import org.python.pydev.core.IGrammarVersionProvider;
import org.python.pydev.core.IPythonNature;
import org.python.pydev.core.MisconfigurationException;
import org.python.pydev.json.eclipsesource.JsonValue;
import org.python.pydev.parser.PyParser.ParserInfo;
import org.python.pydev.parser.jython.SimpleNode;
import org.python.pydev.parser.jython.ast.ClassDef;
import org.python.pydev.parser.jython.ast.Module;
import org.python.pydev.parser.jython.ast.Suite;
import org.python.pydev.parser.visitors.comparator.DifferException;
import org.python.pydev.parser.visitors.comparator.SimpleNodeComparator;
import org.python.pydev.parser.visitors.comparator.SimpleNodeComparator.LineColComparator;
import org.python.pydev.parser.visitors.comparator.SimpleNodeComparator.RegularLineComparator;
import org.python.pydev.shared_core.io.FileUtils;
import org.python.pydev.shared_core.model.ISimpleNode;
import org.python.pydev.shared_core.parsing.BaseParser.ParseOutput;

public class GenCythonAstTest extends CodeCompletionTestsBase {

    IGrammarVersionProvider grammarVersionProvider = new IGrammarVersionProvider() {

        @Override
        public int getGrammarVersion() throws MisconfigurationException {
            // Note: this is used in reparseDocument but not when generating the cython ast as we call the internal implementation.
            return IPythonNature.GRAMMAR_PYTHON_VERSION_3_8;
        }

        @Override
        public AdditionalGrammarVersionsToCheck getAdditionalGrammarVersions() throws MisconfigurationException {
            return null;
        }

    };

    @Override
    protected boolean isPython3Test() {
        return true;
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        GenCythonAstImpl.IN_TESTS = true;
        CompiledModule.COMPILED_MODULES_ENABLED = false;
        this.restorePythonPath(false);

        //        CorePlugin.setBundleInfo(new BundleInfoStub());
        //
        //        final InterpreterInfo info = new InterpreterInfo("3.7", TestDependent.PYTHON_30_EXE, new ArrayList<String>());
        //
        //        IEclipsePreferences preferences = new InMemoryEclipsePreferences();
        //        final PythonInterpreterManager manager = new PythonInterpreterManager(preferences);
        //        InterpreterManagersAPI.setPythonInterpreterManager(manager);
        //        manager.setInfos(new IInterpreterInfo[] { info }, null, null);

    }

    public void compareNodes(ISimpleNode parserNode, ISimpleNode cythonNode, LineColComparator lineColComparator)
            throws DifferException, Exception {
        SimpleNodeComparator simpleNodeComparator = new SimpleNodeComparator(lineColComparator);
        System.out.println("Internal:");
        System.out.println(parserNode);
        System.out.println("Cython:");
        System.out.println(cythonNode);
        simpleNodeComparator.compare((SimpleNode) parserNode, (SimpleNode) cythonNode);

        assertEquals(cythonNode.toString(), parserNode.toString());
    }

    public void testGenCythonFromCythonTests() throws Exception {
        File cythonTestCompileDir = new File("X:\\cython\\tests\\compile");
        assertTrue(cythonTestCompileDir.isDirectory());
        FileUtils.visitDirectory(cythonTestCompileDir, true, (Path path) -> {
            String p = path.toString();
            if (p.endsWith(".py") || p.endsWith(".pyx") || p.endsWith(".pxd")) {
                System.out.println("Visiting: " + p);
                String s = FileUtils.getFileContents(path.toFile());
                try {
                    ParserInfo parserInfoCython = new ParserInfo(new Document(s), grammarVersionProvider);
                    ParseOutput cythonParseOutput = new GenCythonAstImpl(parserInfoCython).genCythonAst();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            return true;
        });
    }

    public void testGenCythonAstCases() throws Exception {
        String[] cases = new String[] {
                "1 | 2 == 0",
                "1 & 2 == 0",
                "1 ^ 2 == 0",
                "def method(a:int): return 1",
                "assert 1, 'ra'",
                "a = a + b",
                "a = a - b",
                "a = a * b",
                "a = a / b",
                "a = a @ b",
                "a |= b",
                "a ^= b",
                "a &= b",
                "a += b",
                "a -= b",
                "a *= b",
                "a /= b",
                "a @= b",
                "while True:\n  a=10\n  b=20",
                "while True:\n  pass\nelse:\n  a=10\n  b=20",
                "1 if a else b",
                "if b:\n  a=1\nelif c:\n  a=2\nelse:\n  a=3",
                "if a: pass",
                "a = 10",
                "def method():pass",
                "def method(a):pass",
                "def method(a, b):pass",
                "def method(a=1):pass",
                "def method(a=1,b=2):pass",
                "def method():\n    a=10\n    b=20",
                "def method(a=None):pass",
                "def method(a, *b, **c):pass",
                "def method(a, *, b):pass",
                "def method(a=1, *, b=2):pass",
                "def method(a=1, *, b:int=2):pass",
                "call()",
                "call(1, 2, b(True, False))",
                "call(u'nth')",
                "call(b'nth')",
                "call('nth')",
                "@dec\ndef method():pass",
                "@dec()\ndef method():pass",
                "class A:pass",
                "class A(set, object):pass",
                "class A((set, object)):pass",
                "@dec\nclass A((set, object)):pass",
                "async def foo(): pass",
                "def foo():yield 1",
                "a > b",
                "a or b",
                "a or b and c",
                "a > b < c != d >= 3 <= (d == 4, a == 3 or (1 in () and 2 not in (), a is 1 and b is not 2))",
                "[]",
                "[1, 3]",
                "{}",
                "{1:2, 3:4}",
                "{1, 2}",
                "for a in b:pass",
                "for a in b:\n    a=1\nelse:    a=2",

        };
        for (String s : cases) {
            compareCase(s);
        }
    }

    public ParseOutput compareCase(String expected) throws DifferException, Exception {
        return this.compareCase(expected, expected);
    }

    public ParseOutput compareCase(String expected, String cython) throws DifferException, Exception {
        // Suite types usually have a different start line number comparing our own with cython.
        return this.compareCase(expected, cython, new RegularLineComparator() {
            @Override
            public void compareLineCol(SimpleNode node, SimpleNode node2) throws DifferException {
                if (!(node instanceof Suite)) {
                    super.compareLineCol(node, node2);
                }
            }
        });
    }

    public ParseOutput compareCase(String expected, String cython, LineColComparator lineColComparator)
            throws DifferException, Exception {
        ParserInfo parserInfoCython = new ParserInfo(new Document(cython), grammarVersionProvider);
        ParseOutput cythonParseOutput = new GenCythonAstImpl(parserInfoCython).genCythonAst();

        ParserInfo parserInfoInternal = new ParserInfo(new Document(expected), grammarVersionProvider);
        ParseOutput parseOutput = PyParser.reparseDocument(parserInfoInternal);
        if (cythonParseOutput.ast == null) {
            if (cythonParseOutput.error != null) {
                throw new RuntimeException(cythonParseOutput.error);
            }
            throw new RuntimeException("Error parsing: " + cython);
        }

        compareNodes(parseOutput.ast, cythonParseOutput.ast, lineColComparator);
        return cythonParseOutput;
    }

    public void testError() throws Exception {
        String cython = "a b c";
        ParserInfo parserInfoCython = new ParserInfo(new Document(cython), grammarVersionProvider);
        ParseOutput cythonParseOutput = new GenCythonAstImpl(parserInfoCython).genCythonAst();
        assertNotNull(cythonParseOutput.error);
        assertNull(cythonParseOutput.ast);
    }

    public void testAsync() throws Exception {
        String s = ""
                + "async def foo():\n"
                + "    async for a in []:\n"
                + "        a=10\n"
                + "        b=20\n"
                + "        await bar()\n"
                + "    else:\n"
                + "        c=30\n"
                + "        d=40\n"
                + "";
        compareCase(s);
    }

    public void testGenCythonAstCornerCase1() throws Exception {
        ParserInfo parserInfo = new ParserInfo(new Document("(f'{a}{{}}nth')"), grammarVersionProvider);
        ParseOutput cythonParseOutput = new GenCythonAstImpl(parserInfo).genCythonAst();
        assertEquals(cythonParseOutput.ast.toString(),
                "Module[body=[Expr[value=Str[s=, type=SingleSingle, unicode=true, raw=false, binary=false, fstring=false, fstring_nodes=[Expr[value=Name[id=a, ctx=Load, reserved=false]], Expr[value=Str[s={}nth, type=SingleSingle, unicode=true, raw=false, binary=false, fstring=false, fstring_nodes=null]]]]]]]");
    }

    public void testGenCythonAstCornerCase2() throws Exception {
        compareCase("a = None", "a = NULL");

        compareCase("class E:\n  z = 0\n", "cdef enum E:\n  z\n");

        compareCase("a = sizeof()\n", "a = sizeof(OtherStruct[4])\n");
        compareCase("a = sizeof()\n", "a = sizeof(int[23][34])\n");

        compareCase("MyStructP = int\n", "ctypedef unsigned int MyStructP\n");

        compareCase("MyStructP = MyStruct\n", "ctypedef MyStruct* MyStructP\n");

        compareCase("\nclass MyStruct:\n  a = 10\n", "cdef extern from *:\n  struct MyStruct:\n    int a = 10\n");

        compareCase("\ndef foo(int): pass", "cdef extern from *:\n" +
                "\n    cdef void foo(int[]): pass\n\n");

        compareCase("def foo(a): pass", "cdef void foo(int[] a): pass\n");
        compareCase("def foo(int): pass", "cdef void foo(int[]): pass\n"); // i.e.: we just have the type.

        compareCase("spam_counter = None", "cdef extern int spam_counter");
        compareCase("def foo(a): pass", "cdef int **foo(int* a): pass");

        compareCase("('ab')", "(r'ab',)"); // there's no indication that it's a raw string...
        compareCase("'ab'", "('a' 'b')"); // "call('a' 'b')", // cython converts to 'ab' internally during parsing.
    }

    public void testGenCythonAstCornerCase3() throws Exception {
        String s = ""
                + "for a in b:\n"
                + "    a=1\n"
                + "else:\n" // internal starts suite here
                + "    a=2\n" // cython starts suite here
                + "    a=4";
        compareCase(s);
    }

    public void testGenCythonAstCdef() throws Exception {
        String s = "def bar(): pass\r\n";
        String cython = "cdef bar(): pass\r\n";
        compareCase(s, cython);
    }

    public void testGenCythonAstClassCDef() throws Exception {
        String s;
        String cython;
        ISimpleNode cythonAst;
        Module m;

        s = "class bar:\n    pass\r\n";
        cython = "cdef class bar:\n    pass\r\n";
        compareCase(s, cython);
        cythonAst = compareCase(s, cython).ast;
        m = (Module) cythonAst;
        ClassDef def = (ClassDef) m.body[0];
        assertEquals(12, def.name.beginColumn);

        s = "class bar(object):\n"
                + "    def method(self):\n"
                + "        pass";

        cython = "cdef class bar(object):\n"
                + "    cpdef def method(self):\n"
                + "        pass";
        compareCase(s, cython);

        s = "class bar(object):\n"
                + "    def method(self, a, b):\n"
                + "        pass";

        cython = "cdef class bar(object):\n"
                + "    cpdef def method(self, double a, int b) except *:\n"
                + "        pass";
        compareCase(s, cython);

        s = "class bar(object):\n"
                + "    x = 0; y = None\n"
                + "    def method(self, a, b):\n"
                + "        pass";

        cython = "cdef class bar(object):\n"
                + "    cdef int x = 0, y\n"
                + "    cpdef def method(self, double a, int b) except *:\n"
                + "        pass";
        compareCase(s, cython);
    }

    public void testGenCythonAstBasic() throws Exception {
        ParserInfo parserInfo = new ParserInfo(new Document("a = 10"), grammarVersionProvider);
        String output = new GenCythonAstImpl(parserInfo).genCythonJson();
        JsonValue value = JsonValue.readFrom(output);

        JsonValue body = value.asObject().get("stats");
        assertEquals(body, JsonValue.readFrom(
                "[\n" +
                        "        {\n" +
                        "            \"__node__\": \"SingleAssignment\",\n" +
                        "            \"line\": 1,\n" +
                        "            \"col\": 4,\n" +
                        "            \"lhs\": {\n" +
                        "                \"__node__\": \"Name\",\n" +
                        "                \"line\": 1,\n" +
                        "                \"col\": 0,\n" +
                        "                \"name\": \"a\"\n" +
                        "            },\n" +
                        "            \"rhs\": {\n" +
                        "                \"__node__\": \"Int\",\n" +
                        "                \"line\": 1,\n" +
                        "                \"col\": 4,\n" +
                        "                \"is_c_literal\": \"None\",\n" +
                        "                \"value\": \"10\",\n" +
                        "                \"unsigned\": \"\",\n" +
                        "                \"longness\": \"\",\n" +
                        "                \"constant_result\": \"10\",\n" +
                        "                \"type\": \"long\"\n" +
                        "            }\n" +
                        "        }\n" +
                        "    ]\n" +
                        "\n"));

        assertEquals(
                "Module[body=[Assign[targets=[Name[id=a, ctx=Store, reserved=false]], value=Num[n=10, type=Int, num=10], type=null]]]",
                new GenCythonAstImpl(parserInfo).genCythonAst().ast.toString());

    }
}
