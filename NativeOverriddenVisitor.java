import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.Arrays;
import java.util.List;

public class NativeOverriddenVisitor extends SwiftBaseVisitor<String> {

    @Override protected String aggregateResult(String aggregate, String nextResult) {
        return aggregate + nextResult;
    }

    @Override protected String defaultResult() {
        return "";
    }

    @Override public String visitChildren(RuleNode node) { return visitChildren(node, null); }

    public String visitChildren(RuleNode node, List<Integer> withoutNodes) {
        String result = this.defaultResult();
        int n = node.getChildCount();

        for(int i = 0; i < n && this.shouldVisitNextChild(node, result); ++i) {
            if(withoutNodes != null && withoutNodes.contains(i)) continue;
            ParseTree c = node.getChild(i);
            String childResult = c instanceof TerminalNode ? printTerminalNode((TerminalNode) c) : c.accept(this);
            result = this.aggregateResult(result, childResult);
        }

        return result;
    }

    protected String printTerminalNode(TerminalNode c) {
        String text = c.getText();
        if(text.equals("<EOF>")) {
            return "";
        }
        else if(text.equals("let")) {
            return "const ";
        }
        else if(text.equals("print")) {
            return "console.log ";
        }
        else if(text.equals("func")) {
            return "function ";
        }
        else if(text.equals(".")) {
            return ".";
        }
        return text + " ";
    }
};