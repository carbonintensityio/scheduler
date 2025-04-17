package io.carbonintensity.scheduler.quarkus.common.runtime.util;

import static io.smallrye.common.expression.Expression.Flag.LENIENT_SYNTAX;
import static io.smallrye.common.expression.Expression.Flag.NO_TRIM;

import java.time.Duration;
import java.time.ZoneId;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.BiConsumer;

import io.carbonintensity.scheduler.GreenScheduled;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import io.quarkus.arc.Arc;
import io.quarkus.runtime.configuration.DurationConverter;
import io.smallrye.common.expression.Expression;
import io.smallrye.common.expression.ResolveContext;

/**
 * Utilities class for scheduler extensions.
 */
public final class SchedulerUtils {

    private SchedulerUtils() {
    }

    /**
     * Parse the `@Scheduled(executionMaxDelay = "")` value into milliseconds.
     *
     * @param scheduled annotation
     * @return returns the duration in milliseconds or {@link OptionalLong#empty()} if the expression evaluates to "off" or
     *         "disabled".
     */
    public static OptionalLong parseExecutionMaxDelayAsMillis(GreenScheduled scheduled) {
        //String value = lookUpPropertyValue(scheduled.executionMaxDelay());
        //if (value.isBlank()) {
            return OptionalLong.empty();
        //}
        //return OptionalLong.of(parseDurationAsMillis(scheduled, value, "executionMaxDelay"));
    }

    /**
     * Parse the `@Scheduled(overdueGracePeriod = "")` field into milliseconds.
     *
     * @param scheduled annotation
     * @return returns the duration.
     */
    public static Duration parseOverdueGracePeriod(GreenScheduled scheduled, Duration defaultDuration) {
        //String value = lookUpPropertyValue(scheduled.overdueGracePeriod());
        //if (value.isEmpty()) {
            return defaultDuration;
        //}
        //return parseDuration(scheduled, value, "overdueGracePeriod");
    }

    public static boolean isOff(String value) {
        return value != null && (value.equalsIgnoreCase("off") || value.equalsIgnoreCase("disabled"));
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

    public static ZoneId parseCronTimeZone(GreenScheduled scheduled) {
        String timeZone = lookUpPropertyValue(scheduled.timeZone());
        return timeZone.equals("Scheduled.DEFAULT_TIMEZONE") ? null : ZoneId.of(timeZone);
    }

    public static <T> T instantiateBeanOrClass(Class<T> type) {
        Instance<T> instance = Arc.container().select(type, Any.Literal.INSTANCE);
        if (instance.isAmbiguous()) {
            throw new IllegalArgumentException("Multiple beans match the type: " + type);
        } else if (instance.isUnsatisfied()) {
            try {
                return type.getConstructor().newInstance();
            } catch (Exception e) {
                throw new IllegalStateException("Unable to instantiate the class: " + type);
            }
        } else {
            return instance.get();
        }
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
        final String expanded = expression.evaluate(new BiConsumer<ResolveContext<RuntimeException>, StringBuilder>() {
            @Override
            public void accept(ResolveContext<RuntimeException> resolveContext, StringBuilder stringBuilder) {
                final Optional<String> resolve = config.getOptionalValue(resolveContext.getKey(), String.class);
                if (resolve.isPresent()) {
                    stringBuilder.append(resolve.get());
                } else if (resolveContext.hasDefault()) {
                    resolveContext.expandDefault();
                } else {
                    throw new NoSuchElementException(String.format("Could not expand value %s in property %s",
                            resolveContext.getKey(), expr));
                }
            }
        });
        return expanded;
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
