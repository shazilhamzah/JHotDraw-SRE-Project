import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.InstanceOfTree;
import com.sun.source.tree.LineMap;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.Modifier;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * AST-based Chidamber-Kemerer and complexity metrics calculator for Java source files.
 * Uses JDK 17's built-in javac compiler tree API so it requires no external dependencies.
 */
public class MetricsCalculator {

  private static final Set<String> JAVA_LANG_SIMPLE_NAMES = Set.of(
      "Boolean", "Byte", "Character", "Class", "Comparable", "Double", "Enum", "Error",
      "Exception", "Float", "Integer", "Iterable", "Long", "Math", "Number", "Object",
      "Override", "Runnable", "RuntimeException", "SecurityException", "Short", "String",
      "StringBuilder", "StringBuffer", "SuppressWarnings", "System", "Thread", "Throwable",
      "Void", "OutOfMemoryError", "IllegalArgumentException", "IllegalStateException",
      "NullPointerException", "UnsupportedOperationException");

  private static final Set<String> PRIMITIVE_NAMES = Set.of(
      "boolean", "byte", "char", "short", "int", "long", "float", "double", "void");

  public static void main(String[] args) throws Exception {
    if (args.length < 3) {
      System.err.println(
          "Usage: MetricsCalculator <jsonOut> <textOut> <javaFile1> [<javaFile2> ...]");
      System.exit(2);
    }
    Path jsonOut = Path.of(args[0]);
    Path textOut = Path.of(args[1]);
    List<Path> inputFiles = new ArrayList<>();
    for (int i = 2; i < args.length; i++) {
      inputFiles.add(Path.of(args[i]).toAbsolutePath().normalize());
    }

    List<ClassMetrics> allMetrics = analyzeFiles(inputFiles);
    Files.createDirectories(jsonOut.getParent());
    Files.createDirectories(textOut.getParent());
    Files.writeString(jsonOut, toJson(allMetrics), StandardCharsets.UTF_8);
    Files.writeString(textOut, toTextReport(allMetrics), StandardCharsets.UTF_8);
    System.out.println("Analyzed " + allMetrics.size() + " class(es).");
  }

  private static List<ClassMetrics> analyzeFiles(List<Path> files) throws IOException {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new IllegalStateException("System JavaCompiler is unavailable (JDK required).");
    }
    List<ClassMetrics> results = new ArrayList<>();
    try (StandardJavaFileManager fileManager =
        compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
      List<File> ioFiles = files.stream().map(Path::toFile).toList();
      Iterable<? extends JavaFileObject> compilationUnits =
          fileManager.getJavaFileObjectsFromFiles(ioFiles);
      JavacTask task = (JavacTask)
          compiler.getTask(null, fileManager, null, List.of("-proc:none"), null, compilationUnits);
      Trees trees = Trees.instance(task);
      SourcePositions positions = trees.getSourcePositions();
      for (var cu : task.parse()) {
        Path sourcePath = Path.of(cu.getSourceFile().toUri()).toAbsolutePath().normalize();
        String sourceText = Files.readString(sourcePath, StandardCharsets.UTF_8);
        List<String> lines = sourceText.lines().toList();
        LineMap lineMap = cu.getLineMap();
        List<ClassTree> classTrees = new ArrayList<>();
        new TreePathScanner<Void, Void>() {
          @Override
          public Void visitClass(ClassTree node, Void unused) {
            if (node.getSimpleName() != null && !node.getSimpleName().toString().isEmpty()) {
              classTrees.add(node);
            }
            return super.visitClass(node, null);
          }
        }.scan(cu, null);

        for (ClassTree classTree : classTrees) {
          results.add(computeClassMetrics(sourcePath, lines, cu, classTree, positions, lineMap));
        }
      }
    }
    return results;
  }

  private static ClassMetrics computeClassMetrics(
      Path sourcePath,
      List<String> fileLines,
      com.sun.source.tree.CompilationUnitTree cu,
      ClassTree classTree,
      SourcePositions positions,
      LineMap lineMap) {
    String className = classTree.getSimpleName().toString();
    long startOffset = positions.getStartPosition(cu, classTree);
    long endOffset = positions.getEndPosition(cu, classTree);
    int startLine = (int) lineMap.getLineNumber(startOffset);
    int endLine = (int) lineMap.getLineNumber(endOffset);
    int loc = endLine - startLine + 1;
    int sloc = countSloc(fileLines, startLine, endLine);

    Set<String> instanceFields = new LinkedHashSet<>();
    int totalFields = 0;
    List<MethodTree> directMethods = new ArrayList<>();
    Set<String> coupledTypes = new LinkedHashSet<>();

    if (classTree.getExtendsClause() != null) {
      collectTypeNames(classTree.getExtendsClause(), coupledTypes);
    }
    for (Tree impl : classTree.getImplementsClause()) {
      collectTypeNames(impl, coupledTypes);
    }

    for (Tree member : classTree.getMembers()) {
      if (member instanceof VariableTree vt) {
        totalFields++;
        if (!vt.getModifiers().getFlags().contains(Modifier.STATIC)) {
          instanceFields.add(vt.getName().toString());
        }
        collectTypeNames(vt.getType(), coupledTypes);
        if (vt.getInitializer() != null) {
          scanCoupledTypesInTree(cu, vt.getInitializer(), coupledTypes);
        }
      } else if (member instanceof MethodTree mt) {
        directMethods.add(mt);
      } else if (member instanceof BlockTree bt) {
        scanCoupledTypesInTree(cu, bt, coupledTypes);
      }
    }

    List<MethodMetrics> methodMetrics = new ArrayList<>();
    Map<String, Set<String>> methodFieldAccess = new LinkedHashMap<>();

    for (MethodTree mt : directMethods) {
      int mStart = (int) lineMap.getLineNumber(positions.getStartPosition(cu, mt));
      int mEnd = (int) lineMap.getLineNumber(positions.getEndPosition(cu, mt));
      int mLoc = mEnd - mStart + 1;
      int cc = computeCyclomaticComplexity(cu, mt);
      String mName = formatMethodSignature(mt, className);
      methodMetrics.add(new MethodMetrics(mName, mStart, mEnd, mLoc, cc));

      collectTypeNames(mt.getReturnType(), coupledTypes);
      for (VariableTree param : mt.getParameters()) {
        collectTypeNames(param.getType(), coupledTypes);
      }
      for (Tree thrown : mt.getThrows()) {
        collectTypeNames(thrown, coupledTypes);
      }
      if (mt.getBody() != null) {
        scanCoupledTypesInTree(cu, mt.getBody(), coupledTypes);
      }

      boolean isInstanceMethod =
          !mt.getModifiers().getFlags().contains(Modifier.STATIC) && mt.getBody() != null;
      if (isInstanceMethod) {
        Set<String> localVarNames = new HashSet<>();
        for (VariableTree param : mt.getParameters()) {
          localVarNames.add(param.getName().toString());
        }
        Set<String> accessed =
            findAccessedInstanceFields(cu, mt.getBody(), instanceFields, localVarNames);
        methodFieldAccess.put(mName + "@" + mStart, accessed);
      }
    }

    coupledTypes.remove(className);
    coupledTypes.removeIf(t -> PRIMITIVE_NAMES.contains(t) || JAVA_LANG_SIMPLE_NAMES.contains(t));

    int wmc = methodMetrics.stream().mapToInt(m -> m.cc).sum();
    int maxCc = methodMetrics.stream().mapToInt(m -> m.cc).max().orElse(0);
    double avgCc = methodMetrics.isEmpty() ? 0.0 : (double) wmc / methodMetrics.size();

    LcomResult lcom = computeLcom(instanceFields, methodFieldAccess);

    return new ClassMetrics(
        sourcePath.getFileName().toString(),
        className,
        startLine,
        endLine,
        loc,
        sloc,
        directMethods.size(),
        totalFields,
        wmc,
        maxCc,
        avgCc,
        coupledTypes.size(),
        lcom.lcom1,
        lcom.lcomStar,
        coupledTypes,
        methodMetrics);
  }

  private static String formatMethodSignature(MethodTree mt, String className) {
    String name = mt.getName().toString();
    if ("<init>".equals(name)) {
      name = className;
    }
    List<String> params = new ArrayList<>();
    for (VariableTree p : mt.getParameters()) {
      params.add(p.getType().toString());
    }
    return name + "(" + String.join(", ", params) + ")";
  }

  private static int computeCyclomaticComplexity(
      com.sun.source.tree.CompilationUnitTree cu, MethodTree mt) {
    if (mt.getBody() == null) {
      return 1;
    }
    final int[] cc = {1};
    new com.sun.source.util.TreeScanner<Void, Void>() {
      @Override
      public Void visitIf(IfTree node, Void unused) {
        cc[0]++;
        return super.visitIf(node, null);
      }

      @Override
      public Void visitForLoop(ForLoopTree node, Void unused) {
        cc[0]++;
        return super.visitForLoop(node, null);
      }

      @Override
      public Void visitEnhancedForLoop(EnhancedForLoopTree node, Void unused) {
        cc[0]++;
        return super.visitEnhancedForLoop(node, null);
      }

      @Override
      public Void visitWhileLoop(WhileLoopTree node, Void unused) {
        cc[0]++;
        return super.visitWhileLoop(node, null);
      }

      @Override
      public Void visitDoWhileLoop(DoWhileLoopTree node, Void unused) {
        cc[0]++;
        return super.visitDoWhileLoop(node, null);
      }

      @Override
      public Void visitCase(CaseTree node, Void unused) {
        if (!node.getExpressions().isEmpty()) {
          cc[0]++;
        }
        return super.visitCase(node, null);
      }

      @Override
      public Void visitCatch(CatchTree node, Void unused) {
        cc[0]++;
        return super.visitCatch(node, null);
      }

      @Override
      public Void visitConditionalExpression(ConditionalExpressionTree node, Void unused) {
        cc[0]++;
        return super.visitConditionalExpression(node, null);
      }

      @Override
      public Void visitBinary(BinaryTree node, Void unused) {
        if (node.getKind() == Tree.Kind.CONDITIONAL_AND
            || node.getKind() == Tree.Kind.CONDITIONAL_OR) {
          cc[0]++;
        }
        return super.visitBinary(node, null);
      }
    }.scan(mt.getBody(), null);
    return cc[0];
  }

  private static Set<String> findAccessedInstanceFields(
      com.sun.source.tree.CompilationUnitTree cu,
      BlockTree body,
      Set<String> instanceFields,
      Set<String> paramNames) {
    Set<String> accessed = new HashSet<>();
    Set<String> shadowed = new HashSet<>(paramNames);
    new com.sun.source.util.TreeScanner<Void, Void>() {
      @Override
      public Void visitClass(ClassTree node, Void unused) {
        return super.visitClass(node, null);
      }

      @Override
      public Void visitVariable(VariableTree node, Void unused) {
        shadowed.add(node.getName().toString());
        return super.visitVariable(node, null);
      }

      @Override
      public Void visitMemberSelect(MemberSelectTree node, Void unused) {
        if ("this".equals(node.getExpression().toString())) {
          String id = node.getIdentifier().toString();
          if (instanceFields.contains(id)) {
            accessed.add(id);
          }
        }
        return super.visitMemberSelect(node, null);
      }

      @Override
      public Void visitIdentifier(IdentifierTree node, Void unused) {
        String name = node.getName().toString();
        if (instanceFields.contains(name) && !shadowed.contains(name)) {
          accessed.add(name);
        }
        return super.visitIdentifier(node, null);
      }
    }.scan(body, null);
    return accessed;
  }

  private static void scanCoupledTypesInTree(
      com.sun.source.tree.CompilationUnitTree cu, Tree tree, Set<String> coupledTypes) {
    new com.sun.source.util.TreeScanner<Void, Void>() {
      @Override
      public Void visitVariable(VariableTree node, Void unused) {
        collectTypeNames(node.getType(), coupledTypes);
        return super.visitVariable(node, null);
      }

      @Override
      public Void visitNewClass(NewClassTree node, Void unused) {
        collectTypeNames(node.getIdentifier(), coupledTypes);
        return super.visitNewClass(node, null);
      }

      @Override
      public Void visitTypeCast(TypeCastTree node, Void unused) {
        collectTypeNames(node.getType(), coupledTypes);
        return super.visitTypeCast(node, null);
      }

      @Override
      public Void visitInstanceOf(InstanceOfTree node, Void unused) {
        collectTypeNames(node.getType(), coupledTypes);
        return super.visitInstanceOf(node, null);
      }

      @Override
      public Void visitCatch(CatchTree node, Void unused) {
        if (node.getParameter() != null) {
          collectTypeNames(node.getParameter().getType(), coupledTypes);
        }
        return super.visitCatch(node, null);
      }

      @Override
      public Void visitMemberSelect(MemberSelectTree node, Void unused) {
        if (node.getExpression() instanceof IdentifierTree id) {
          String name = id.getName().toString();
          if (!name.isEmpty() && Character.isUpperCase(name.charAt(0))) {
            coupledTypes.add(name);
          }
        }
        return super.visitMemberSelect(node, null);
      }
    }.scan(tree, null);
  }

  private static void collectTypeNames(Tree typeTree, Set<String> out) {
    if (typeTree == null) {
      return;
    }
    if (typeTree instanceof ParameterizedTypeTree ptt) {
      collectTypeNames(ptt.getType(), out);
      for (Tree arg : ptt.getTypeArguments()) {
        collectTypeNames(arg, out);
      }
      return;
    }
    String raw = typeTree.toString().replace("[]", "").trim();
    if (raw.isEmpty() || raw.equals("?") || raw.equals("var")) {
      return;
    }
    if (raw.startsWith("? extends ")) {
      raw = raw.substring("? extends ".length()).trim();
    } else if (raw.startsWith("? super ")) {
      raw = raw.substring("? super ".length()).trim();
    }
    int lt = raw.indexOf('<');
    if (lt >= 0) {
      raw = raw.substring(0, lt).trim();
    }
    int lastDot = raw.lastIndexOf('.');
    String simple = lastDot >= 0 ? raw.substring(lastDot + 1) : raw;
    if (!simple.isEmpty() && Character.isUpperCase(simple.charAt(0))) {
      out.add(simple);
    }
  }

  private static LcomResult computeLcom(
      Set<String> instanceFields, Map<String, Set<String>> methodFieldAccess) {
    List<Set<String>> methodSets = new ArrayList<>(methodFieldAccess.values());
    int m = methodSets.size();
    int p = 0;
    int q = 0;
    for (int i = 0; i < m; i++) {
      for (int j = i + 1; j < m; j++) {
        boolean sharesField = !Collections.disjoint(methodSets.get(i), methodSets.get(j));
        if (sharesField) {
          q++;
        } else {
          p++;
        }
      }
    }
    int lcom1 = Math.max(0, p - q);
    double lcomStar = 0.0;
    int a = instanceFields.size();
    if (m > 1 && a > 0) {
      Map<String, Integer> mu = new HashMap<>();
      for (String f : instanceFields) {
        mu.put(f, 0);
      }
      for (Set<String> accessed : methodSets) {
        for (String f : accessed) {
          mu.computeIfPresent(f, (k, v) -> v + 1);
        }
      }
      double sumMu = mu.values().stream().mapToInt(Integer::intValue).sum();
      lcomStar = (m - (sumMu / a)) / (m - 1);
      lcomStar = Math.max(0.0, Math.min(1.0, lcomStar));
    }
    return new LcomResult(lcom1, lcomStar);
  }

  private static int countSloc(List<String> lines, int startLine, int endLine) {
    int count = 0;
    boolean inBlockComment = false;
    int from = Math.max(1, startLine);
    int to = Math.min(lines.size(), endLine);
    for (int i = from; i <= to; i++) {
      String line = lines.get(i - 1).trim();
      if (line.isEmpty()) {
        continue;
      }
      if (inBlockComment) {
        int endIdx = line.indexOf("*/");
        if (endIdx >= 0) {
          inBlockComment = false;
          String rest = line.substring(endIdx + 2).trim();
          if (!rest.isEmpty() && !rest.startsWith("//")) {
            count++;
          }
        }
        continue;
      }
      if (line.startsWith("//")) {
        continue;
      }
      if (line.startsWith("/*")) {
        int endIdx = line.indexOf("*/", 2);
        if (endIdx < 0) {
          inBlockComment = true;
          continue;
        }
        String rest = line.substring(endIdx + 2).trim();
        if (rest.isEmpty() || rest.startsWith("//")) {
          continue;
        }
      }
      count++;
    }
    return count;
  }

  private static String toJson(List<ClassMetrics> classes) {
    StringBuilder sb = new StringBuilder();
    sb.append("{\n  \"classes\": [\n");
    for (int i = 0; i < classes.size(); i++) {
      ClassMetrics c = classes.get(i);
      sb.append("    {\n");
      sb.append("      \"file\": ").append(jsonStr(c.fileName)).append(",\n");
      sb.append("      \"className\": ").append(jsonStr(c.className)).append(",\n");
      sb.append("      \"startLine\": ").append(c.startLine).append(",\n");
      sb.append("      \"endLine\": ").append(c.endLine).append(",\n");
      sb.append("      \"loc\": ").append(c.loc).append(",\n");
      sb.append("      \"sloc\": ").append(c.sloc).append(",\n");
      sb.append("      \"nom\": ").append(c.nom).append(",\n");
      sb.append("      \"nof\": ").append(c.nof).append(",\n");
      sb.append("      \"wmc\": ").append(c.wmc).append(",\n");
      sb.append("      \"maxCc\": ").append(c.maxCc).append(",\n");
      sb.append(String.format(Locale.ROOT, "      \"avgCc\": %.2f,\n", c.avgCc));
      sb.append("      \"cbo\": ").append(c.cbo).append(",\n");
      sb.append("      \"lcom1\": ").append(c.lcom1).append(",\n");
      sb.append(String.format(Locale.ROOT, "      \"lcomStar\": %.4f,\n", c.lcomStar));
      sb.append("      \"coupledTypes\": [");
      List<String> sortedTypes = new ArrayList<>(c.coupledTypes);
      Collections.sort(sortedTypes);
      for (int t = 0; t < sortedTypes.size(); t++) {
        if (t > 0) sb.append(", ");
        sb.append(jsonStr(sortedTypes.get(t)));
      }
      sb.append("],\n");
      sb.append("      \"methods\": [\n");
      for (int m = 0; m < c.methods.size(); m++) {
        MethodMetrics mm = c.methods.get(m);
        sb.append("        {")
            .append("\"signature\": ").append(jsonStr(mm.signature)).append(", ")
            .append("\"startLine\": ").append(mm.startLine).append(", ")
            .append("\"endLine\": ").append(mm.endLine).append(", ")
            .append("\"loc\": ").append(mm.loc).append(", ")
            .append("\"cc\": ").append(mm.cc)
            .append("}");
        if (m + 1 < c.methods.size()) sb.append(",");
        sb.append("\n");
      }
      sb.append("      ]\n");
      sb.append("    }");
      if (i + 1 < classes.size()) sb.append(",");
      sb.append("\n");
    }
    sb.append("  ]\n}\n");
    return sb.toString();
  }

  private static String toTextReport(List<ClassMetrics> classes) {
    StringBuilder sb = new StringBuilder();
    sb.append("=== AST CK & Complexity Metrics ===\n\n");
    sb.append(String.format(
        Locale.ROOT,
        "%-30s %6s %6s %5s %5s %5s %6s %7s %5s %7s %8s\n",
        "Class", "LOC", "SLOC", "NOM", "NOF", "WMC", "MaxCC", "AvgCC", "CBO", "LCOM1", "LCOM*"));
    sb.append("-".repeat(102)).append("\n");
    for (ClassMetrics c : classes) {
      sb.append(String.format(
          Locale.ROOT,
          "%-30s %6d %6d %5d %5d %5d %6d %7.2f %5d %7d %8.4f\n",
          c.className,
          c.loc,
          c.sloc,
          c.nom,
          c.nof,
          c.wmc,
          c.maxCc,
          c.avgCc,
          c.cbo,
          c.lcom1,
          c.lcomStar));
    }
    sb.append("\n");
    for (ClassMetrics c : classes) {
      sb.append("Class ").append(c.className).append(" (").append(c.fileName)
          .append(":").append(c.startLine).append("-").append(c.endLine).append(")\n");
      sb.append("  Coupled types (").append(c.cbo).append("): ");
      List<String> sortedTypes = new ArrayList<>(c.coupledTypes);
      Collections.sort(sortedTypes);
      sb.append(String.join(", ", sortedTypes)).append("\n");
      sb.append("  Methods sorted by CC desc:\n");
      List<MethodMetrics> sorted = new ArrayList<>(c.methods);
      sorted.sort(Comparator.comparingInt((MethodMetrics m) -> m.cc).reversed()
          .thenComparingInt(m -> m.loc).reversed());
      for (MethodMetrics mm : sorted) {
        sb.append(String.format(
            Locale.ROOT,
            "    CC=%2d LOC=%3d L%4d-%-4d %s\n",
            mm.cc, mm.loc, mm.startLine, mm.endLine, mm.signature));
      }
      sb.append("\n");
    }
    return sb.toString();
  }

  private static String jsonStr(String s) {
    return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }

  private record LcomResult(int lcom1, double lcomStar) {}

  private record MethodMetrics(String signature, int startLine, int endLine, int loc, int cc) {}

  private record ClassMetrics(
      String fileName,
      String className,
      int startLine,
      int endLine,
      int loc,
      int sloc,
      int nom,
      int nof,
      int wmc,
      int maxCc,
      double avgCc,
      int cbo,
      int lcom1,
      double lcomStar,
      Set<String> coupledTypes,
      List<MethodMetrics> methods) {}
}
