package io.carbonintensity.scheduler.quarkus.common.runtime.util;

import static io.smallrye.common.expression.Expression.Flag.LENIENT_SYNTAX;
import static io.smallrye.common.expression.Expression.Flag.NO_TRIM;

import java.util.NoSuchElementException;
import java.util.Optional;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import io.smallrye.common.expression.Expression;

/**
 * Utilities class for scheduler extensions.
 */
public final class SchedulerUtils {

    private SchedulerUtils() {
    }

    /**
     * Looks up the property value by checking whether the value is a configuration key and resolves it if so.
     *
     * @param propertyValue property value to look up.
     * @return the resolved property value.
     */
    public static String lookUpPropertyValue(String propertyValue) {
        String value = propertyValue.stripLeading();
        if (!value.isEmpty() && isConfigValue(value)) {
            value = resolvePropertyExpression(adjustExpressionSyntax(value));
        }
        return value;
    }

    public static boolean isConfigValue(String val) {
        return isSimpleConfigValue(val) || isConfigExpression(val);
    }

    private static boolean isSimpleConfigValue(String val) {
        val = val.trim();
        return val.startsWith("{") && val.endsWith("}");
    }

    /**
     * Converts "{property}" to "${property}" for backwards compatibility
     */
    private static String adjustExpressionSyntax(String val) {
        if (isSimpleConfigValue(val)) {
            return '$' + val;
        }
        return val;
    }

    /**
     * Adapted from {@link io.smallrye.config.ExpressionConfigSourceInterceptor}
     */
    private static String resolvePropertyExpression(String expr) {
        final Config config = ConfigProvider.getConfig();
        final Expression expression = Expression.compile(expr, LENIENT_SYNTAX, NO_TRIM);
        return expression.evaluate((resolveContext, stringBuilder) -> {
            final Optional<String> resolve = config.getOptionalValue(resolveContext.getKey(), String.class);
            if (resolve.isPresent()) {
                stringBuilder.append(resolve.get());
            } else if (resolveContext.hasDefault()) {
                resolveContext.expandDefault();
            } else {
                throw new NoSuchElementException(String.format("Could not expand value %s in property %s",
                        resolveContext.getKey(), expr));
            }
        });
    }

    private static boolean isConfigExpression(String val) {
        if (val == null) {
            return false;
        }
        int exprStart = val.indexOf("${");
        int exprEnd = -1;
        if (exprStart >= 0) {
            exprEnd = val.indexOf('}', exprStart + 2);
        }
        return exprEnd > 0;
    }

}
