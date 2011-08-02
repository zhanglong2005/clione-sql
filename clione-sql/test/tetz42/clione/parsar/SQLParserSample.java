package tetz42.clione.parsar;

import static tetz42.clione.lang.ContextUtil.*;
import static tetz42.clione.parsar.ParsarUtil.*;
import static tetz42.clione.util.ClioneUtil.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tetz42.clione.exception.ClioneFormatException;
import tetz42.clione.exception.WrapException;
import tetz42.clione.io.IOUtil;
import tetz42.clione.io.LineReader;
import tetz42.clione.node.LineNode;
import tetz42.clione.node.PlaceHolder;
import tetz42.clione.node.SQLNode;
import tetz42.clione.parsar.ParsarUtil.NodeHolder;
import tetz42.clione.setting.Config;
import tetz42.clione.util.SBHolder;
import tetz42.util.ObjDumper4j;

public class SQLParserSample {

	public static void main(String args[]) {
		System.out.println("---------------------------");
		System.out.println(ObjDumper4j.dumper(new SQLParserSample("0000")
				.parse("tako\r\nika\r\nnamako\r\numiushi")));
		System.out.println(ObjDumper4j.dumper(new SQLParserSample("1111")
				.parse("tako'\r\nika\r\n'namako\r\numiushi")));
		System.out
				.println(ObjDumper4j
						.dumper(new SQLParserSample("3333")
								.parse("tako -- $octopus\r\n,ika -- &squid\t\n,namako -- $seacucumber")));
	}

	private static final String COMMENT = "COMMNET";
	private static final String LINEEND = "LINEEND";
	private static final String S_QUOT = "Single Quotation";
	private static final String D_QUOT = "Double Quotation";

	private static final Pattern delimPtn = Pattern
			.compile("/\\*|\\*/|--|'|\\(|\\)|(\r\n|\r|\n)|\\z");
	private static final Pattern lineEndPtn = Pattern
			.compile("(.*)(\r\n|\r|\n|\\z)");
	private static final Pattern commentPtn = Pattern
			.compile("/\\*|\\*/|(\r\n|\r|\n)");
	private static final Pattern singleStrPtn = Pattern
			.compile("(([^']|'')*)'");
	private static final Pattern doubleStrPtn = Pattern
			.compile("(([^\"]|\"\")*)\"");
	private static final Pattern indentPtn = Pattern.compile("\\A(\\s+)");
	private static final Pattern closePtn = Pattern.compile("\\A\\s*\\)");

	private static class LineInfo {
		private LineNode lineNode;
		private StringBuilder sb;
		private int lineNo;

		LineInfo(int lineNo) {
			this.lineNode = new LineNode(lineNo);
			this.sb = new StringBuilder();
			this.lineNo = lineNo;
		}

		void addLineNo() {
			this.lineNo++;
			this.lineNode.curLineNo(lineNo);
		}

		void clear() {
			this.lineNode = new LineNode(lineNo);
			this.sb.setLength(0);
		}
	}

	private String resourceInfo = null;

	private List<LineNode> parseFunction2(String src) {
		List<LineNode> flatList = new ArrayList<LineNode>();
		MatcherHolder mh = new MatcherHolder(src, delimPtn).bind(COMMENT,
				commentPtn).bind(LINEEND, lineEndPtn)
				.bind(S_QUOT, singleStrPtn).bind(D_QUOT, doubleStrPtn)
				.remember();
		LineInfo info = new LineInfo(1);
		parseFunc(flatList, mh, info);
		if (!mh.isEnd())
			throw new ClioneFormatException(joinByCrlf(
					"SQL Format Error: too much ')'", getResourceInfo()));
		return flatList;
	}

	private void parseFunc(final List<LineNode> flatList, MatcherHolder mh,
			LineInfo info) {
		while (mh.find()) {
			info.sb.append(mh.getRememberedToStart());
			String div = mh.get().group();
			if (div.equals("*/")) {
				throw new ClioneFormatException(joinByCrlf(
						"SQL Format Error: too much '*/'", getResourceInfo()));
			} else if (div.equals(")")) {
				break;
			} else if (div.equals("--")) {
				doLineComment(mh, info);
			} else if (div.equals("/*")) {
				doMultiComment(mh, info);
			} else if (div.equals("(")) {
				doParenthesis(mh, info);
			} else if (div.equals("'")) {
				doString(mh, info, S_QUOT);
			} else if (div.equals("\"")) {
				doString(mh, info, D_QUOT);
			} else {
				// in case line end or end of source string
				info.lineNode.sql = info.sb.toString();
				flatList.add(info.lineNode);
				info.lineNo++;
				info.clear();
			}
		}
	}

	// find end parenthesis and try to parse as SQLNode.
	private void doParenthesis(MatcherHolder mh, LineInfo info) {
		List<LineNode> flatList = new ArrayList<LineNode>();
		// int remembered = mh.getRememberd(); // hum...
		parseFunc(flatList, mh, info);
		if (mh.isEnd())
			throw new ClioneFormatException(joinByCrlf(
					"SQL Format Error: too much '('", getResourceInfo()));
		// mh.setRememberd(remembered); // hum...
		SQLNode sqlNode = parseIndent(flatList);

		// TODO add sqlNode to info
	}

	/**
	 * find line end. create place holder if it is parameter, join with next
	 * line if it is the sign of join, otherwise don't add to SQL because it's
	 * just a comment.
	 */
	private void doLineComment(MatcherHolder mh, LineInfo info) {
		mh.find(LINEEND);
		String comment = mh.get(LINEEND).group(1);
		// System.out.println("LINEEND1[" + comment + "]");
		if (isEmpty(comment) || isAllSpace(comment)) {
			info.addLineNo(); // because find the line end.
		} else if (comment.startsWith(" ")
				&& "$@&?#%'\":|".contains(comment.substring(1, 2))) {
			info.lineNode.holders.add(new PlaceHolder(comment, null, info.sb
					.length()));
			// System.out.println("LINEEND2[" + mh.get(LINEEND).group(2) + "]");
			mh.back(mh.get(LINEEND).group(2).length()); // ready for next
			mh.remember();
		}
	}

	// find end comment and try to parse as function.
	private void doMultiComment(MatcherHolder mh, LineInfo info) {
		findCommentEnd(mh, info);
		String comment = mh.getRememberedToStartWithoutRemember();
		if (isEmpty(comment) || comment.startsWith("*")) {
			mh.remember();
			return;
		}
		if ("!+".contains(comment.substring(0, 1))) {
			info.sb.append(mh.getRememberedToEnd(2));
			return;
		}
		mh.remember();

		// TODO get valueInBack and create PlaceHolder
	}

	private void findCommentEnd(MatcherHolder mh, LineInfo info) {
		while (mh.find(COMMENT)) {
			if (mh.get(COMMENT).group().equals("*/"))
				return; // normal end
			else if (mh.get(COMMENT).group().equals("/*"))
				// in case nested '/*' has detected
				findCommentEnd(mh, info);
			else
				// in case CRLF is detected
				info.addLineNo();
		}
		throw new ClioneFormatException(joinByCrlf(
				"SQL Format Error: too much '/*'", getResourceInfo()));
	}

	// find end string literal.
	private void doString(MatcherHolder mh, LineInfo info, final String type) {
		if (!mh.find(type))
			throw new ClioneFormatException(joinByCrlf("SQL Format Error: "
					+ type + " unmatched!", getResourceInfo()));
		// TODO implementation. Consider about additional lineNo when CRLF is
		// detected.
	}

	public SQLParserSample(String resourceInfo) {
		this.resourceInfo = resourceInfo;
	}

	public SQLNode parse(String src) {
		try {
			pushResouceInfo(resourceInfo);
			return parseRoot(src);
		} finally {
			popResourceInfo();
		}
	}

	public SQLNode parse(InputStream in) {
		try {
			byte[] bs = IOUtil.loadFromStream(in);
			return parse(new String(bs, Config.get().SQLFILE_ENCODING));
		} catch (UnsupportedEncodingException e) {
			throw new WrapException(joinByCrlf(e.getMessage(),
					"The setting of 'clione.properties' might be wrong. ",
					"The key name = 'SQLFILE_ENCODING'"), e);
		}
	}

	private SQLNode parseRoot(String src) {
		List<LineNode> flatList = parseFunction2(src);
		return parseIndent(flatList);
	}

	private List<LineNode> parseFunction(String src) throws IOException {
		List<LineNode> flatList = new ArrayList<LineNode>();
		MatcherHolder mh = new MatcherHolder(src, delimPtn);
		LineReader br = null;
		SBHolder sbh = new SBHolder();
		LineNode lineNode = null;
		String line;
		while (null != (line = br.readLine())) {
			if (lineNode == null)
				lineNode = new LineNode(br.getStartLineNo(), br.getEndLineNo());
			else
				lineNode.curLineNo(br.getCurLineNo());
			sbh.append(line);
			Matcher m = delimPtn.matcher(line);
			while (m.find()) {
				if (m.group().equals("*/"))
					throw new ClioneFormatException(
							"SQL Format Error: too much '*/'" + CRLF + line
									+ CRLF + getResourceInfo());
				if (m.group().equals("--")) {
					if (CRLF.equals(nextStr(line, m.end(), CRLF.length())))
						continue; // '--' join is responsibility of LineReader.
					String s = nextStr(line, m.end(), 2);
					if (s != null && s.startsWith(" ")
							&& "$@&?#%'\":|".contains(s.substring(1))) {
						// create place holder
						lineNode.holders.add(new PlaceHolder(line.substring(m
								.end()), null, m.start()));
						sbh.delete(m.start(), sbh.sb.length());
					}
					break;
				}
				int start = sbh.getPreLength() + m.start();
				m = findCommentEnd(br, sbh, m, lineNode);
				int end = sbh.getPreLength() + m.end();
				String commentFlg = sbh.sb.substring(start + 2, start + 3);
				if ("*".equals(commentFlg))
					sbh.delete(start, end);
				else if (!"+!".contains(commentFlg)) {
					// create place holder
					String valueInBack = getValueInBack(sbh.sb.substring(end),
							br, sbh);
					lineNode.holders.add(new PlaceHolder(sbh.sb.substring(
							start + 2, end - 2), valueInBack, start));
					if (valueInBack == null) {
						sbh.delete(start, end);
					} else {
						int length = valueInBack.length();
						sbh.delete(start, end + length);
						// skip searching on valueInBack;
						int lastLineLength = sbh.sb.length()
								- sbh.getPreLength();
						m.region(m.end() + length, lastLineLength);
					}
				}
			}
			lineNode.sql = sbh.sb.toString();
			flatList.add(lineNode);
			lineNode = null;
			sbh.clear();
		}

		return flatList;
	}

	private Matcher findCommentEnd(LineReader br, SBHolder sbh, Matcher m,
			LineNode lineNode) throws IOException {
		while (m.find()) {
			if (m.group().equals("*/"))
				return m;
			if (m.group().equals("--"))
				continue;
			// in case nested '/*' has detected
			m = findCommentEnd(br, sbh, m, lineNode);
		}
		String line = br.readLine();
		if (line == null)
			throw new ClioneFormatException("SQL Format Error: too match '/*'"
					+ CRLF + getResourceInfo());
		lineNode.curLineNo(br.getCurLineNo());
		sbh.append(CRLF).append(line);
		return findCommentEnd(br, sbh, delimPtn.matcher(line), lineNode);
	}

	private SQLNode parseIndent(List<LineNode> wholeNodeList) {
		NodeHolder holder = new NodeHolder(wholeNodeList);
		List<LineNode> resultList = new ArrayList<LineNode>();
		List<LineNode> list;
		do {
			resultList.addAll(list = buildNodes(holder, ""));
		} while (list.size() != 0);
		SQLNode sqlNode = new SQLNode();
		sqlNode.nodes = resultList;
		sqlNode.resourceInfo = resourceInfo;
		return sqlNode;
	}

	private List<LineNode> buildNodes(NodeHolder holder, String indent) {
		ArrayList<LineNode> list = new ArrayList<LineNode>();

		LineNode parentNode = null;
		LineNode node;
		while (null != (node = holder.next())) {
			Matcher m = indentPtn.matcher(node.sql);
			String curIndent = m.find() ? m.group(1) : "";

			if (calcIndent(indent) < calcIndent(curIndent)) {
				holder.back();
				if (parentNode == null) {
					// performed only 1st loop time.
					indent = curIndent;
					continue;
				}
				parentNode.childBlocks.addAll(buildNodes(holder, curIndent));
				continue;
			} else if (calcIndent(indent) > calcIndent(curIndent)
					&& !closePtn.matcher(node.sql).find()) {
				holder.back();
				return list;
			}

			list.add(parentNode = node);
		}
		return list;
	}
}