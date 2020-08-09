package com.github.dreamhead.bot.junit5.junit5;

import com.github.dreamhead.bot.AnyField;
import com.github.dreamhead.bot.Bot;
import com.github.dreamhead.bot.BotInitializer;
import com.github.dreamhead.bot.BotWith;
import com.github.dreamhead.bot.DoubleField;
import com.github.dreamhead.bot.FieldFactory;
import com.github.dreamhead.bot.IntField;
import com.github.dreamhead.bot.LongField;
import com.github.dreamhead.bot.ObjectBot;
import com.github.dreamhead.bot.StringField;
import com.github.dreamhead.bot.util.FieldEntry;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.github.dreamhead.bot.reflection.ReflectionSupport.findAnnotatedFields;
import static com.github.dreamhead.bot.reflection.ReflectionSupport.findAnnotation;
import static com.github.dreamhead.bot.reflection.ReflectionSupport.newInstance;
import static com.github.dreamhead.bot.reflection.ReflectionSupport.setFieldValue;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.extension.ExtensionContext.Namespace.create;

public class BotExtension implements BeforeAllCallback, AfterAllCallback, TestInstancePostProcessor {
    private static final ExtensionContext.Namespace BOT = create("com.github.dreamhead.bot");

    @Override
    public void postProcessTestInstance(final Object testInstance, final ExtensionContext context) {
        Class<?> testInstanceClass = testInstance.getClass();
        ObjectBot bot = context.getStore(BOT).get(testInstanceClass, ObjectBot.class);

        List<Field> fields = findAnnotatedFields(testInstanceClass, Bot.class);
        for (Field field : fields) {
            Bot annotation = field.getAnnotation(Bot.class);
            List<FieldEntry<Object>> pairs = getModifiers(field);
            Object value = bot.of(annotation.value(), field.getType(), pairs.toArray(new FieldEntry[0]));
            setFieldValue(testInstance, field, value);
        }
    }

    private List<FieldEntry<Object>> getModifiers(final Field field) {
        List<FieldEntry<Object>> stringModifiers = getModifiers(field, StringField.class,
                annotation -> FieldEntry.of(annotation.name(), annotation.value()));
        List<FieldEntry<Object>> longModifiers = getModifiers(field, LongField.class,
                annotation -> FieldEntry.of(annotation.name(), annotation.value()));
        List<FieldEntry<Object>> intModifiers = getModifiers(field, IntField.class,
                annotation -> FieldEntry.of(annotation.name(), annotation.value()));
        List<FieldEntry<Object>> doubleModifiers = getModifiers(field, DoubleField.class,
                annotation -> FieldEntry.of(annotation.name(), annotation.value()));
        List<FieldEntry<Object>> anyModifiers = getModifiers(field, AnyField.class,
                this::getAnyValue);

        return Stream.of(stringModifiers, longModifiers, intModifiers, anyModifiers, doubleModifiers)
                .flatMap(Collection::stream)
                .collect(toList());
    }

    private FieldEntry<Object> getAnyValue(final AnyField annotation) {
        Class<? extends FieldFactory<?>> factory = annotation.factory();
        return FieldEntry.of(annotation.name(), newInstance(factory).getValue());
    }

    @SuppressWarnings("unchecked")
    private <T extends Annotation>
    List<FieldEntry<Object>> getModifiers(final Field field,
                                            final Class<T> annotationClass,
                                            final Function<T, FieldEntry<Object>> function) {
        Annotation[] annotations = field.getAnnotationsByType(annotationClass);
        if (annotations.length == 0) {
            return new ArrayList<>();
        }

        return Arrays.stream(annotations)
                .map(annotation -> function.apply((T) annotation))
                .collect(toList());
    }

    private ObjectBot createBot(final BotWith botWith) {
        Class<? extends BotInitializer>[] factories = botWith.value();
        BotInitializer initializer = newInstance(factories[0]);
        ObjectBot bot = new ObjectBot();
        initializer.initializer(bot);
        return bot;
    }

    @Override
    public void beforeAll(final ExtensionContext context) {
        Class<?> testClass = context.getRequiredTestClass();

        Optional<BotWith> annotation = findBotAnnotation(context);
        annotation.ifPresent(botWith -> {
            ObjectBot bot = createBot(botWith);
            context.getStore(BOT).put(testClass, bot);
        });
    }

    private Optional<BotWith> findBotAnnotation(final ExtensionContext context) {
        Optional<AnnotatedElement> element = context.getElement();
        return element.flatMap(annotatedElement ->
                findAnnotation(annotatedElement, BotWith.class));
    }

    @Override
    public void afterAll(final ExtensionContext context) {
        Class<?> testClass = context.getRequiredTestClass();
        context.getStore(BOT).remove(testClass);
    }
}
