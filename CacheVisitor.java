import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.LinkedHashMap;

public class CacheVisitor extends Visitor {

    public CacheVisitor(EntityCache cache, String targetLanguage) {
        super();
        this.cache = cache;
        this.targetLanguage = targetLanguage;
    }

    @Override public String visitPattern_initializer(SwiftParser.Pattern_initializerContext ctx) {
        String varName = ctx.pattern().identifier_pattern().getText();
        AbstractType varType =
                ctx.pattern().type_annotation() != null && ctx.pattern().type_annotation().type() != null ? Type.fromDefinition(ctx.pattern().type_annotation().type())
                : Type.infer(ctx.initializer().expression(), this);
        cache(varName, varType, ctx);
        return null;
    }

    @Override public String visitProperty_declaration(SwiftParser.Property_declarationContext ctx) {
        String varName = ctx.variable_name().getText();
        AbstractType varType = Type.fromDefinition(ctx.type_annotation().type());
        cache(varName, varType, ctx);
        visit(ctx.property_declaration_body());
        return null;
    }

    private void cache(String varName, AbstractType varType, ParseTree ctx) {
        if(varType instanceof FunctionType) varName += FunctionUtil.nameAugment((FunctionType)varType);
        cache.cacheOne(varName, varType, ctx);
    }

    @Override public String visitFunction_declaration(SwiftParser.Function_declarationContext ctx) {
        FunctionType functionType = new FunctionType(ctx, this);
        cache.cacheOne(FunctionUtil.functionName(ctx, functionType), functionType, ctx);

        ArrayList<String> parameterLocalNames = FunctionUtil.parameterLocalNames(FunctionUtil.parameters(ctx));
        for(int i = 0; i < parameterLocalNames.size(); i++) {
            cache.cacheOne(parameterLocalNames.get(i), functionType.parameterTypes.get(i), ctx.function_body().code_block());
        }

        visit(ctx.function_body());

        return null;
    }

    @Override public String visitSetter_clause(SwiftParser.Setter_clauseContext ctx) {
        this.visitPropertyClause(ctx);
        return null;
    }
    @Override public String visitWillSet_clause(SwiftParser.WillSet_clauseContext ctx) {
        this.visitPropertyClause(ctx);
        return null;
    }
    @Override public String visitDidSet_clause(SwiftParser.DidSet_clauseContext ctx) {
        this.visitPropertyClause(ctx);
        return null;
    }
    private void visitPropertyClause(ParserRuleContext ctx) {
        SwiftParser.Property_declarationContext propertyDeclaration = (SwiftParser.Property_declarationContext) ctx.parent.parent.parent;
        AbstractType propertyType = cache.findLoose(propertyDeclaration.variable_name().getText(), ctx).object.type.copy();
        propertyType.isGetterSetter = false;
        SwiftParser.Code_blockContext blockContext =
            ctx instanceof SwiftParser.Setter_clauseContext ? ((SwiftParser.Setter_clauseContext)ctx).code_block() :
            ctx instanceof SwiftParser.WillSet_clauseContext ? ((SwiftParser.WillSet_clauseContext)ctx).code_block() :
            ((SwiftParser.DidSet_clauseContext)ctx).code_block();
        String argumentName =
            ctx instanceof SwiftParser.Setter_clauseContext ? AssignmentUtil.setterArgumentName((SwiftParser.Setter_clauseContext) ctx) :
            ctx instanceof SwiftParser.WillSet_clauseContext ? AssignmentUtil.willSetArgumentName((SwiftParser.WillSet_clauseContext)ctx) :
            AssignmentUtil.didSetArgumentName((SwiftParser.DidSet_clauseContext)ctx);

        cache.cacheOne(argumentName, propertyType, blockContext);

        visit(blockContext);
    }

    @Override public String visitClosure_expression(SwiftParser.Closure_expressionContext ctx) {
        /*SwiftParser.Parameter_listContext parameterList = ctx.closure_signature().parameter_clause().parameter_list();
        List<SwiftParser.ParameterContext> parameters = parameterList != null ? parameterList.parameter() : null;
        ArrayList<AbstractType> parameterTypes = FunctionUtil.parameterTypes(parameters, this);
        for(int i = 0; parameterTypes != null && i < parameterTypes.size(); i++) {
            cache.cacheOne(FunctionUtil.parameterLocalName(parameters.get(i)), parameterTypes.get(i), ctx);
        }*/

        return null;
    }

    @Override public String visitClass_declaration(SwiftParser.Class_declarationContext ctx) {

        String className = ctx.class_name().getText();
        cache.cacheOne(className, new NestedByIndexType(new LinkedHashMap<String, AbstractType>(), "class", className, false), ctx);

        visit(ctx.class_body());

        return null;
    }

    @Override public String visitStruct_declaration(SwiftParser.Struct_declarationContext ctx) {

        String className = ctx.struct_name().getText();
        cache.cacheOne(className, new NestedByIndexType(new LinkedHashMap<String, AbstractType>(), "struct", className, false), ctx);

        visit(ctx.struct_body());

        return null;
    }

    @Override public String visitFor_in_statement(SwiftParser.For_in_statementContext ctx) {

        if(ctx.expression() != null && ctx.expression().binary_expressions() != null) {
            String varName = ctx.pattern().getText().equals("_") ? "$" : ctx.pattern().getText();
            cache.cacheOne(varName, new BasicType("Int"), ctx.code_block());
        }
        else {
            Expression iteratedObject = new Expression(ctx.expression(), null, this);
            AbstractType iteratedType = iteratedObject.type;
            String indexVar = "$", valueVar;
            if(ctx.pattern().tuple_pattern() != null) {
                indexVar = ctx.pattern().tuple_pattern().tuple_pattern_element_list().tuple_pattern_element(0).getText();
                valueVar = ctx.pattern().tuple_pattern().tuple_pattern_element_list().tuple_pattern_element(1).getText();
            }
            else {
                valueVar = ctx.pattern().identifier_pattern().getText();
            }
            cache.cacheOne(indexVar, iteratedType.swiftType().equals("String") ? new BasicType("Int"): ((NestedType)iteratedType).keyType, ctx.code_block());
            cache.cacheOne(valueVar, iteratedType.swiftType().equals("String") ? new BasicType("String"): ((NestedType)iteratedType).valueType, ctx.code_block());
        }

        visit(ctx.code_block());

        return null;
    }

    private void cacheIfLet(ParserRuleContext ctx, SwiftParser.Code_blockContext codeBlock) {
        IfLet ifLet = new IfLet(ctx, this);
        for(int i = 0; i < ifLet.varNames.size(); i++) {
            cache.cacheOne(ifLet.varNames.get(i), ifLet.varTypes.get(i), codeBlock);
        }
        visit(codeBlock);
    }

    @Override public String visitIf_statement(SwiftParser.If_statementContext ctx) {
        cacheIfLet(ctx, ctx.code_block());
        if(ctx.else_clause() != null) visit(ctx.else_clause());
        return null;
    }

    @Override public String visitGuard_statement(SwiftParser.Guard_statementContext ctx) {
        cacheIfLet(ctx, ctx.code_block());
        return null;
    }
}
