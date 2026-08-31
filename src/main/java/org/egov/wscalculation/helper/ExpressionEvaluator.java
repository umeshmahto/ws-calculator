package org.egov.wscalculation.helper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.egov.wscalculation.constants.WSCalculationConstant;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ExpressionEvaluator {

    public BigDecimal evaluateExpression(String expression, Map<String, BigDecimal> context) {
        if (StringUtils.isBlank(expression)) {
            return BigDecimal.ZERO;
        }

        String expr = expression.trim();

        if (expr.startsWith("(") && expr.endsWith(")") && isMatchingParenthesis(expr)) {
            return evaluateExpression(expr.substring(1, expr.length() - 1), context);
        }

        if (expr.toUpperCase(Locale.ROOT).startsWith("MAX(") && expr.endsWith(")")) {
            String inner = expr.substring(4, expr.length() - 1);
            List<String> args = splitArguments(inner);
            BigDecimal maxVal = null;
            for (String arg : args) {
                BigDecimal val = evaluateExpression(arg, context);
                if (maxVal == null || val.compareTo(maxVal) > 0) {
                    maxVal = val;
                }
            }
            return maxVal != null ? maxVal : BigDecimal.ZERO;
        }

        if (expr.toUpperCase(Locale.ROOT).startsWith("MIN(") && expr.endsWith(")")) {
            String inner = expr.substring(4, expr.length() - 1);
            List<String> args = splitArguments(inner);
            BigDecimal minVal = null;
            for (String arg : args) {
                BigDecimal val = evaluateExpression(arg, context);
                if (minVal == null || val.compareTo(minVal) < 0) {
                    minVal = val;
                }
            }
            return minVal != null ? minVal : BigDecimal.ZERO;
        }

        if (expr.toUpperCase(Locale.ROOT).startsWith("IF(") && expr.endsWith(")")) {
            String inner = expr.substring(3, expr.length() - 1);
            List<String> args = splitArguments(inner);
            if (args.size() == 3) {
                boolean conditionResult = evaluateCondition(args.get(0), context);
                return conditionResult ? evaluateExpression(args.get(1), context) : evaluateExpression(args.get(2), context);
            }
        }

        int addSubIdx = findTopLevelOperator(expr, Arrays.asList("+", "-"));
        if (addSubIdx > 0) {
            String op = String.valueOf(expr.charAt(addSubIdx));
            BigDecimal left = evaluateExpression(expr.substring(0, addSubIdx), context);
            BigDecimal right = evaluateExpression(expr.substring(addSubIdx + 1), context);
            return "+".equals(op) ? left.add(right) : left.subtract(right);
        }

        int mulDivIdx = findTopLevelOperator(expr, Arrays.asList("*", "/"));
        if (mulDivIdx > 0) {
            String op = String.valueOf(expr.charAt(mulDivIdx));
            BigDecimal left = evaluateExpression(expr.substring(0, mulDivIdx), context);
            BigDecimal right = evaluateExpression(expr.substring(mulDivIdx + 1), context);
            if ("/".equals(op)) {
                if (right.compareTo(BigDecimal.ZERO) == 0) {
                    log.warn("Division by zero in expression: {}. Returning ZERO.", expr);
                    return BigDecimal.ZERO;
                }
                return left.divide(right, WSCalculationConstant.DIVISION_SCALE, RoundingMode.HALF_UP);
            } else {
                return left.multiply(right);
            }
        }

        return resolveToken(expr, context);
    }

    private boolean evaluateCondition(String condition, Map<String, BigDecimal> context) {
        if (condition.contains(">=")) {
            String[] parts = condition.split(">=");
            return evaluateExpression(parts[0], context).compareTo(evaluateExpression(parts[1], context)) >= 0;
        } else if (condition.contains("<=")) {
            String[] parts = condition.split("<=");
            return evaluateExpression(parts[0], context).compareTo(evaluateExpression(parts[1], context)) <= 0;
        } else if (condition.contains(">")) {
            String[] parts = condition.split(">");
            return evaluateExpression(parts[0], context).compareTo(evaluateExpression(parts[1], context)) > 0;
        } else if (condition.contains("<")) {
            String[] parts = condition.split("<");
            return evaluateExpression(parts[0], context).compareTo(evaluateExpression(parts[1], context)) < 0;
        } else if (condition.contains("==")) {
            String[] parts = condition.split("==");
            return evaluateExpression(parts[0], context).compareTo(evaluateExpression(parts[1], context)) == 0;
        }
        return false;
    }

    private int findTopLevelOperator(String expr, List<String> operators) {
        int depth = 0;
        for (int i = expr.length() - 1; i >= 0; i--) {
            char c = expr.charAt(i);
            if (c == ')') depth++;
            else if (c == '(') depth--;
            else if (depth == 0) {
                String s = String.valueOf(c);
                if (operators.contains(s) && i > 0 && i < expr.length() - 1) {
                    return i;
                }
            }
        }
        return -1;
    }

    private List<String> splitArguments(String innerText) {
        List<String> args = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < innerText.length(); i++) {
            char c = innerText.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;

            if (c == ',' && depth == 0) {
                args.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            args.add(current.toString().trim());
        }
        return args;
    }

    private boolean isMatchingParenthesis(String expr) {
        int depth = 0;
        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            if (depth == 0 && i < expr.length() - 1) {
                return false;
            }
        }
        return depth == 0;
    }

    private BigDecimal resolveToken(String token, Map<String, BigDecimal> context) {
        String key = token.trim().toLowerCase(Locale.ROOT);
        if (context.containsKey(key)) {
            return context.get(key);
        }
        try {
            return new BigDecimal(token.trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}