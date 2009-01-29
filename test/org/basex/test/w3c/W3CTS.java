package org.basex.test.w3c;

import static org.basex.Text.*;
import static org.basex.util.Token.*;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.regex.Pattern;
import org.basex.BaseX;
import org.basex.core.Context;
import org.basex.core.Prop;
import org.basex.core.proc.Check;
import org.basex.data.Data;
import org.basex.data.Nodes;
import org.basex.data.XMLSerializer;
import org.basex.io.CachedOutput;
import org.basex.io.IO;
import org.basex.query.QueryContext;
import org.basex.query.QueryException;
import org.basex.query.QueryProcessor;
import org.basex.query.QueryTokens;
import org.basex.query.expr.Expr;
import org.basex.query.func.FNIndex;
import org.basex.query.func.Fun;
import org.basex.query.item.Item;
import org.basex.query.item.Nod;
import org.basex.query.item.QNm;
import org.basex.query.item.Str;
import org.basex.query.item.Type;
import org.basex.query.item.Uri;
import org.basex.query.iter.NodIter;
import org.basex.query.util.Var;
import org.basex.util.Performance;
import org.basex.util.TokenBuilder;
import org.basex.util.TokenList;

/**
 * XQuery Test Suite Wrapper.
 *
 * @author Workgroup DBIS, University of Konstanz 2005-08, ISC License
 * @author Christian Gruen
 */
public abstract class W3CTS {
  // Try "ulimit -n 65536" if Linux tells you "Too many open files."

  /** History Path. */
  private final String pathhis;
  /** Log File. */
  private final String pathlog;
  /** Test Suite input. */
  private final String input;
  /** Test Suite Identifier. */
  private final String testid;
  /** Path to the XQuery Test Suite. */
  private String path;

  /** Query Path. */
  private String queries;
  /** Expected Results. */
  private String expected;
  /** Reported Results. */
  private String results;
  /** Reports. */
  private String report;
  /** Test Sources. */
  private String sources;
  /** Inspect flag. */
  private static final byte[] INSPECT = token("Inspect");

  /** Maximum length of result output. */
  private static int maxout = 500;

  /** Delimiter. */
  private static final String DELIM = "#~%~#";
  /** Replacement pattern. */
  private static final Pattern CHOP = Pattern.compile(DELIM, Pattern.LITERAL);
  /** Replacement pattern. */
  private static final Pattern SLASH = Pattern.compile("/", Pattern.LITERAL);

  /** Query filter string. */
  private String single;
  /** Flag for printing current time functions into log file. */
  private boolean currTime;
  /** Flag for creating report files. */
  private boolean reporting;
  /** Verbose flag. */
  private boolean verbose;

  /** Cached source files. */
  private final HashMap<String, String> srcs = new HashMap<String, String>();
  /** Cached module files. */
  private final HashMap<String, String> mods = new HashMap<String, String>();
  /** Cached collections. */
  private final HashMap<String, byte[][]> colls =
    new HashMap<String, byte[][]>();
  /** Cached stop word files. */
  private final HashMap<String, String> stop = new HashMap<String, String>();

  /** OK log. */
  private final StringBuilder logOK = new StringBuilder();
  /** OK log. */
  private final StringBuilder logOK2 = new StringBuilder();
  /** Error log. */
  private final StringBuilder logErr = new StringBuilder();
  /** Error log. */
  private final StringBuilder logErr2 = new StringBuilder();
  /** File log. */
  private final StringBuilder logFile = new StringBuilder();

  /** Error counter. */
  private int err;
  /** Error2 counter. */
  private int err2;
  /** OK counter. */
  private int ok;
  /** OK2 counter. */
  private int ok2;

  /** Data reference. */
  private Data data;

  /**
   * Constructor.
   * @param nm name of test
   * @param p path to the text files
   */
  public W3CTS(final String nm, final String p) {
    input = nm + "Catalog.xml";
    testid = nm.substring(0, 4);
    pathhis = testid.toLowerCase() + ".hist";
    pathlog = testid.toLowerCase() + ".log";
    path = p;
  }

  /**
   * Initializes the code.
   * @param args command-line arguments
   * @throws Exception exception
   */
  void init(final String[] args) throws Exception {
    // modifying internal query arguments...
    for(final String arg : args) {
      if(arg.equals("-r")) {
        reporting = true;
        currTime = true;
      } else if(arg.startsWith("-p")) {
        path = arg.substring(2);
      } else if(arg.equals("-t")) {
        currTime = true;
      } else if(arg.equals("-v")) {
        verbose = true;
      } else if(!arg.startsWith("-")) {
        single = arg;
        maxout *= 10;
      } else {
        BaseX.outln("\nBaseX vs. XQuery Test Suite\n" +
            " [pat] perform only tests with the specified pattern\n" +
            " -h show this help\n" +
            " -p change path\n" +
            " -r create report\n" +
            " -v verbose output");
        return;
      }
    }

    queries = path + "Queries/XQuery/";
    expected = path + "ExpectedTestResults/";
    results = path + "ReportingResults/Results/";
    report = path + "ReportingResults/";
    sources = path + "TestSources/";

    final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    final String dat = sdf.format(Calendar.getInstance().getTime());

    final Performance perf = new Performance();
    final Context context = new Context();
    
    new Check(path + input).execute(context, null);
    data = context.data();

    final Nodes root = new Nodes(0, data);
    BaseX.outln("\nBaseX vs. XQuery Test Suite " +
        text("/*:test-suite/@version", root));

    BaseX.outln("\nCaching Sources...");
    for(final int s : nodes("//*:source", root).nodes) {
      final Nodes srcRoot = new Nodes(s, data);
      final String val = (path + text("@FileName", srcRoot)).replace('\\', '/');
      srcs.put(text("@ID", srcRoot), val);
    }

    BaseX.outln("Caching Modules...");
    for(final int s : nodes("//*:module", root).nodes) {
      final Nodes srcRoot = new Nodes(s, data);
      final String val = (path + text("@FileName", srcRoot)).replace('\\', '/');
      mods.put(text("@ID", srcRoot), val);
    }

    BaseX.outln("Caching Collections...");
    for(final int c : nodes("//*:collection", root).nodes) {
      final Nodes nodes = new Nodes(c, data);
      final String cname = text("@ID", nodes);

      final TokenList dl = new TokenList();
      final Nodes doc = nodes("*:input-document", nodes);
      for(int d = 0; d < doc.size; d++) {
        dl.add(token(sources + string(data.atom(doc.nodes[d])) + IO.XMLSUFFIX));
      }
      colls.put(cname, dl.finish());
    }

    BaseX.outln("Caching Stopwords...");
    for(final int s : nodes("//*:stopwords", root).nodes) {
      final Nodes srcRoot = new Nodes(s, data);
      final String val = (path + text("@FileName", srcRoot)).replace('\\', '/');
      stop.put(text("@uri", srcRoot), val);
    }

    if(reporting) {
      BaseX.outln("Delete old results...");
      delete(new File[] { new File(results) });
    }

    BaseX.out("Parsing Queries");
    final Nodes nodes = nodes("//*:test-case", root);
    for(int t = 0; t < nodes.size; t++) {
      if(!parse(new Nodes(nodes.nodes[t], data))) break;
      if(t % 1000 == 0) BaseX.out(".");
    }
    BaseX.outln();

    final String time = perf.getTimer();

    final int total = ok + ok2 + err + err2;

    BaseX.outln("Writing log file...\n");
    BufferedWriter bw = new BufferedWriter(
        new OutputStreamWriter(new FileOutputStream(pathlog), UTF8));
    bw.write("TEST RESULTS ==================================================");
    bw.write(Prop.NL + Prop.NL + "Total #Queries: " + total + Prop.NL);
    bw.write("Correct / Empty Results: " + ok + " / " + ok2 + Prop.NL);
    bw.write("Conformance (w/Empty Results): ");
    bw.write(pc(ok, total) + " / " + pc(ok + ok2, total) + Prop.NL);
    bw.write("Wrong Results / Errors: " + err + " / " + err2 + Prop.NL);
    //bw.write("Total Time: " + time + Prop.NL + Prop.NL);
    bw.write("WRONG =========================================================");
    bw.write(Prop.NL + Prop.NL + logErr + Prop.NL);
    bw.write("WRONG (ERRORS) ================================================");
    bw.write(Prop.NL + Prop.NL + logErr2 + Prop.NL);
    bw.write("CORRECT? (EMPTY) ==============================================");
    bw.write(Prop.NL + Prop.NL + logOK2 + Prop.NL);
    bw.write("CORRECT =======================================================");
    bw.write(Prop.NL + Prop.NL + logOK + Prop.NL);
    bw.write("===============================================================");
    bw.close();

    bw = new BufferedWriter(new FileWriter(pathhis, true));
    bw.write(dat + "\t" + ok + "\t" + ok2 + "\t" + err + "\t" + err2 + Prop.NL);
    bw.close();

    if(reporting) {
      bw = new BufferedWriter(new OutputStreamWriter(
          new FileOutputStream(report + NAME + IO.XMLSUFFIX), UTF8));
      write(bw, report + NAME + "Pre" + IO.XMLSUFFIX);
      bw.write(logFile.toString());
      write(bw, report + NAME + "Pos" + IO.XMLSUFFIX);
      bw.close();
    }

    BaseX.outln("Total #Queries: " + total);
    BaseX.outln("Correct / Empty results: " + ok + " / " + ok2);
    BaseX.out("Conformance (w/empty results): ");
    BaseX.outln(pc(ok, total) + " / " + pc(ok + ok2, total));
    BaseX.outln("Total Time: " + time);
  }

  /**
   * Calculate percentage of correct queries.
   * @param v value
   * @param t total value
   * @return percentage
   */
  private String pc(final int v, final int t) {
    return (t == 0 ? 100 : (v * 10000 / t) / 100d) + "%";
  }

  /**
   * Parses the specified test case.
   * @param root root node
   * @throws Exception exception
   * @return true if the query, specified by {@link #single}, was evaluated.
   */
  private boolean parse(final Nodes root) throws Exception {
    final String pth = text("@FilePath", root);
    final String outname = text("@name", root);
    String inname = text("*:query/@name", root);
    if(inname == null) inname = outname;
    if(verbose) BaseX.outln(inname);

    if(single != null && !outname.startsWith(single)) return true;

    final IO file = IO.get(queries + pth + inname + ".xq");
    if(!file.exists()) {
      BaseX.errln("Not found: " + file);
      return true;
    }

    final String in = read(file);

    String output = "";
    String error = null;
    Item item = null;

    final TokenBuilder files = new TokenBuilder();
    try {
      final Context context = new Context();
      final CachedOutput out = new CachedOutput();
      final QueryProcessor xq = new QueryProcessor(in, file);
      
      Nodes cont = nodes("*:contextItem", root);
      if(cont.size != 0) new Check(sources + string(data.atom(cont.nodes[0])) +
          IO.XMLSUFFIX).execute(context, out);

      final QueryContext ctx = xq.ctx;
      ctx.stop = stop;
      
      files.add(file(nodes("*:input-file", root),
          nodes("*:input-file/@variable", root), ctx));
      files.add(file(nodes("*:input-URI", root),
          nodes("*:input-URI/@variable", root), ctx));
      files.add(file(nodes("*:defaultCollection", root), null, ctx));

      var(nodes("*:input-query/@name", root),
          nodes("*:input-query/@variable", root), pth, ctx);

      for(final int p : nodes("*:module", root).nodes) {
        final String ns = text("@namespace", new Nodes(p, data));
        final String f = mods.get(string(data.atom(p))) + ".xq";
        xq.module(ns, f);
      }

      Prop.info = true;
      Prop.allInfo = true;
      
      // evaluate and serialize query
      item = xq.eval(context.current());
      item.serialize(new XMLSerializer(out));
      output = norm(out.finish());
      
      System.out.println(xq.ctx.info());

    } catch(final QueryException ex) {
      error = ex.getMessage();
      if(error.startsWith("Stopped at")) {
        error = error.substring(error.indexOf('\n') + 1);
      }

      if(error.startsWith("[")) {
        final int i = error.indexOf("]");
        error = error.substring(1).substring(0, i - 1) +
          error.substring(i + 1);
      }
    } catch(final Exception ex) {
      final ByteArrayOutputStream bw = new ByteArrayOutputStream();
      ex.printStackTrace(new PrintStream(bw));
      error = bw.toString();
    } catch(final Error ex) {
      final ByteArrayOutputStream bw = new ByteArrayOutputStream();
      ex.printStackTrace(new PrintStream(bw));
      error = bw.toString();
    }

    final Nodes outFiles = nodes("*:output-file/text()", root);
    final Nodes cmpFiles = nodes("*:output-file/@compare", root);
    final StringBuilder tb = new StringBuilder();
    for(int o = 0; o < outFiles.size; o++) {
      if(o != 0) tb.append(DELIM);
      final String resFile = string(data.atom(outFiles.nodes[o]));
      final IO exp = IO.get(expected + pth + resFile);
      tb.append(exp.exists() ? read(exp) : ("Not Found: " + exp));
    }
    String result = tb.toString();
    String expError = text("*:expected-error/text()", root);

    final StringBuilder log = new StringBuilder(pth + inname + ".xq");
    if(files.size != 0) {
      log.append(" [");
      log.append(files);
      log.append("]");
    }
    log.append(Prop.NL);

    /** Remove comments. */
    log.append(compact(in));
    log.append(Prop.NL);
    final String logStr = log.toString();
    final boolean print = currTime || !logStr.contains("current-") &&
        !logStr.contains("implicit-timezone");

    if(reporting) {
      logFile.append("    <test-case name=\"");
      logFile.append(outname);
      logFile.append("\" result='");
    }

    boolean rightCode = false;
    if(error != null && (outFiles.size == 0 || expError.length() != 0)) {
      expError = error(pth + outname, expError);
      final String code = error.substring(0, Math.min(8, error.length()));
      for(final String er : SLASH.split(expError)) {
        if(code.equals(er)) {
          rightCode = true;
          break;
        }
      }
    }

    if(rightCode) {
      if(print) {
        logOK.append(logStr);
        logOK.append("[Right] ");
        logOK.append(error);
        logOK.append(Prop.NL);
        logOK.append(Prop.NL);
        addLog(pth, outname + ".log", error);
      }
      if(reporting) logFile.append("pass");
      ok++;
    } else if(error == null) {
      boolean inspect = false;
      final String[] split = CHOP.split(result, 0);
      int s = -1;
      while(++s < split.length) {
        inspect |= s < cmpFiles.nodes.length && eq(data.atom(cmpFiles.nodes[s]),
            INSPECT);
        if(split[s].equals(output)) break;
      }

      if(s == split.length && !inspect) {
        if(print) {
          if(outFiles.size == 0) result = error(pth + outname, expError);
          logErr.append(logStr);
          logErr.append("[" + testid + " ] ");
          logErr.append(chop(result));
          logErr.append(Prop.NL);
          logErr.append("[Wrong] ");
          logErr.append(chop(output));
          logErr.append(Prop.NL);
          logErr.append(Prop.NL);
          final boolean nodes = item instanceof Nod && item.type != Type.TXT;
          addLog(pth, outname + (nodes ? IO.XMLSUFFIX : IO.TXTSUFFIX), output);
        }
        if(reporting) logFile.append("fail");
        err++;
      } else {
        if(print) {
          logOK.append(logStr);
          logOK.append("[Right] ");
          logOK.append(chop(output));
          logOK.append(Prop.NL);
          logOK.append(Prop.NL);
          final boolean nodes = item instanceof Nod && item.type != Type.TXT;
          addLog(pth, outname + (nodes ? IO.XMLSUFFIX : IO.TXTSUFFIX), output);
        }
        if(reporting) {
          logFile.append("pass");
          if(inspect) logFile.append("' todo='inspect");
        }
        ok++;
      }
    } else {
      if(outFiles.size == 0 || expError.length() != 0) {
        if(print) {
          logOK2.append(logStr);
          logOK2.append("[" + testid + " ] ");
          logOK2.append(expError);
          logOK2.append(Prop.NL);
          logOK2.append("[Rght?] ");
          logOK2.append(error);
          logOK2.append(Prop.NL);
          logOK2.append(Prop.NL);
          addLog(pth, outname + ".log", error);
        }
        if(reporting) logFile.append("pass");
        ok2++;
      } else {
        if(print) {
          logErr2.append(logStr);
          logErr2.append("[" + testid + " ] ");
          logErr2.append(chop(result));
          logErr2.append(Prop.NL);
          logErr2.append("[Wrong] ");
          logErr2.append(error);
          logErr2.append(Prop.NL);
          logErr2.append(Prop.NL);
          addLog(pth, outname + ".log", error);
        }
        if(reporting) logFile.append("fail");
        err2++;
      }
    }
    if(reporting) {
      logFile.append("'/>");
      logFile.append(Prop.NL);
    }

    return single == null || !outname.equals(single);
  }

  /**
   * Removes comments and double string.
   * @param in input string
   * @return result
   */
  private String compact(final String in) {
    final StringBuilder sb = new StringBuilder();
    int m = 0;
    boolean s = false;
    final int cl = in.length();
    for(int c = 0; c < cl; c++) {
      final char ch = in.charAt(c);
      if(ch == '(' && c + 1 < cl && in.charAt(c + 1) == ':') {
        if(m == 0 && !s) {
          sb.append(' ');
          s = true;
        }
        m++;
        c++;
      } else if(m != 0 && ch == ':' && c + 1 < cl && in.charAt(c + 1) == ')') {
        m--;
        c++;
      } else if(m == 0) {
        if(!s || ch > ' ') sb.append(ch);
        s = ch <= ' ';
      }
    }
    return sb.toString().trim();
  }

  /**
   * Initializes the input files, specified by the context nodes.
   * @param nod variables
   * @param var documents
   * @param ctx xquery context
   * @return string with input files
   * @throws QueryException query exception
   */
  private byte[] file(final Nodes nod, final Nodes var,
      final QueryContext ctx) throws QueryException {

    final TokenBuilder tb = new TokenBuilder();
    for(int c = 0; c < nod.size; c++) {
      final byte[] nm = data.atom(nod.nodes[c]);
      final String src = srcs.get(string(nm));
      if(tb.size != 0) tb.add(", ");
      tb.add(nm);

      if(src == null) {
        // assign collection
        final NodIter col = new NodIter();
        for(final byte[] cl : colls.get(string(nm))) col.add(ctx.doc(cl, true));
        ctx.addColl(col, nm);

        if(var != null) {
          final Var v = new Var(new QNm(data.atom(var.nodes[c])));
          ctx.vars.addGlobal(v.bind(Uri.uri(nm), ctx));
        }
      } else {
        // assign document
        final Fun fun = FNIndex.get().get(token("doc"), QueryTokens.FNURI,
            new Expr[] { Str.get(src) });
        final Var v = new Var(new QNm(data.atom(var.nodes[c])));
        ctx.vars.addGlobal(v.bind(fun, ctx));
      }
    }
    return tb.finish();
  }

  /**
   * Evaluates the the input files and assigns the result to the specified
   * variables.
   * @param nod variables
   * @param var documents
   * @param pth file path
   * @param ctx xquery context
   * @throws Exception exception
   */
  private void var(final Nodes nod, final Nodes var, final String pth,
      final QueryContext ctx) throws Exception {

    for(int c = 0; c < nod.size; c++) {
      final String file = pth + string(data.atom(nod.nodes[c])) + ".xq";
      final String in = read(IO.get(queries + file));
      final QueryProcessor xq = new QueryProcessor(in);
      final Item item = xq.eval(null);
      final Var v = new Var(new QNm(data.atom(var.nodes[c])));
      ctx.vars.addGlobal(v.bind(item, ctx));
    }
  }

  /**
   * Adds a log file.
   * @param pth file path
   * @param nm file name
   * @param msg message
   * @throws Exception exception
   */
  private void addLog(final String pth, final String nm, final String msg)
      throws Exception {

    if(reporting) {
      final File file = new File(results + pth);
      if(!file.exists()) file.mkdirs();
      final BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(
          new FileOutputStream(results + pth + nm), UTF8));
      bw.write(msg);
      bw.close();
    }
  }

  /**
   * Returns an error message.
   * @param nm test name
   * @param error XQTS error
   * @return error message
   * @throws Exception exception
   */
  private String error(final String nm, final String error) throws Exception {
    final String error2 = expected + nm + ".log";
    final IO file  = IO.get(error2);
    return file.exists() ? error + "/" + read(file) : error;
  }

  /**
   * Returns the resulting query text (text node or attribute value).
   * @param qu query
   * @param root root node
   * @return attribute value
   * @throws Exception exception
   */
  private String text(final String qu, final Nodes root) throws Exception {
    final Nodes n = nodes(qu, root);
    final TokenBuilder sb = new TokenBuilder();
    for(int i = 0; i < n.size; i++) {
      if(i != 0) sb.add('/');
      sb.add(data.atom(n.nodes[i]));
    }
    return sb.toString();
  }

  /**
   * Returns the resulting query nodes.
   * @param qu query
   * @param root root node
   * @return attribute value
   * @throws Exception exception
   */
  private Nodes nodes(final String qu, final Nodes root) throws Exception {
    return new QueryProcessor(qu).queryNodes(root);
  }

  /**
   * Recursively deletes a directory.
   * @param pth deletion path
   */
  private void delete(final File[] pth) {
    for(final File f : pth) {
      if(f.isDirectory()) delete(f.listFiles());
      f.delete();
    }
  }

  /**
   * Adds the specified file to the writer.
   * @param bw writer
   * @param f file path
   * @throws Exception exception
   */
  private void write(final BufferedWriter bw, final String f) throws Exception {
    final BufferedReader br = new BufferedReader(new
        InputStreamReader(new FileInputStream(f), UTF8));
    String line;
    while((line = br.readLine()) != null) {
      bw.write(line);
      bw.write(Prop.NL);
    }
    br.close();
  }

  /**
   * Chops the specified string to a maximum of 100 characters.
   * @param string string
   * @return chopped string
   */
  private String chop(final String string) {
    if(string == null) return "";
    final String str = CHOP.matcher(string).replaceAll(" / ");
    final int sl = str.length();
    return sl < maxout ? str :
      new StringBuilder(str.substring(0, maxout)).append("...").toString();
  }

  /**
   * Returns the contents of the specified file.
   * @param f file to be read
   * @return content
   * @throws IOException I/O exception
   */
  String read(final IO f) throws IOException {
    final StringBuilder sb = new StringBuilder();
    final BufferedReader br = new BufferedReader(new
        InputStreamReader(new FileInputStream(f.path()), UTF8));
    String l;
    while((l = br.readLine()) != null) {
      l = l.trim();
      if(l.length() == 0) continue;
      sb.append(l.indexOf(" />") != -1 ? l.replaceAll(" />", "/>") : l);
      sb.append(' ');
    }
    br.close();
    return sb.toString().trim();
  }

  /**
   * Normalizes the specified string.
   * @param string string
   * @return normalized string
   */
  String norm(final byte[] string) {
    final String str = string(string);
    final StringBuilder sb = new StringBuilder();
    boolean nl = true;
    for(int l = 0; l < str.length(); l++) {
      final char c = str.charAt(l);
      if(nl) {
        nl = c >= 0 && c <= ' ';
      } else {
        nl = c == '\r' || c == '\n';
        if(nl) {
          // delete trailing whitespaces
          while(sb.charAt(sb.length() - 1) <= ' ')
            sb.deleteCharAt(sb.length() - 1);
          sb.append(' ');
        }
      }
      if(!nl) sb.append(c);
    }
    return sb.toString().trim();
  }
}
