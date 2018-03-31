package de.dmeiners.mapping.impl.jexl;

import de.dmeiners.mapping.api.PostProcessor;
import de.dmeiners.mapping.api.ResultTypeException;
import de.dmeiners.mapping.api.ScriptExecutionException;
import de.dmeiners.mapping.api.ScriptNameResolver;
import de.dmeiners.mapping.api.ScriptParseException;
import de.dmeiners.mapping.impl.ClasspathScriptNameResolver;
import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.JexlException;
import org.apache.commons.jexl3.JexlScript;
import org.apache.commons.jexl3.MapContext;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class JexlPostProcessor implements PostProcessor {

    private final JexlEngine engine;
    private final ScriptNameResolver scriptNameResolver;

    JexlPostProcessor() {

        this(new ClasspathScriptNameResolver());
    }

    JexlPostProcessor(ScriptNameResolver scriptNameResolver) {
        this.engine = new JexlBuilder()
            .cache(512)
            .strict(true)
            .silent(false)
            .create();

        this.scriptNameResolver = scriptNameResolver;
    }

    @Override
    public <T> T process(T target, String scriptName, Map<String, Object> context) {

        String scriptText = this.scriptNameResolver.resolve(scriptName);
        return processInline(target, scriptText, context);
    }

    @Override
    public <T> T processInline(T target, String scriptText, Map<String, Object> context) {

        JexlScript script = parse(scriptText);
        Object result = execute(target, context, script);

        if (!result.getClass().isInstance(target)) {
            throw new ResultTypeException(String.format("Script did not return an object of type '%s'.",
                target.getClass().getName()));
        }

        // The above check should let this "cast" never fail. At runtime we are dealing with objects anyway.
        return (T) result;
    }

    @Override
    public <T> List<T> process(Collection<T> targets, String scriptName, Map<String, Object> context) {

        String scriptText = this.scriptNameResolver.resolve(scriptName);
        return processInline(targets, scriptText, context);
    }

    @Override
    public <T> List<T> processInline(Collection<T> targets, String scriptText, Map<String, Object> context) {

        return targets.stream()
            .map(it -> processInline(it, scriptText, context))
            .collect(Collectors.toList());
    }

    private <T> Object execute(T target, Map<String, Object> context, JexlScript script) {

        Object result;

        try {
            result = script.execute(new MapContext(context), target);
        } catch (JexlException e) {
            throw new ScriptExecutionException(String.format("Error executing parsed script: '%s'",
                script.getParsedText()), e);
        }
        return result;
    }

    private JexlScript parse(String scriptText) {

        String preparedScriptText = ensureLastExpressionIsTarget(scriptText);
        JexlScript script;

        try {

            script = engine.createScript(preparedScriptText, "target");
        } catch (JexlException e) {
            throw new ScriptParseException(String.format("Error parsing script text: '%s'", preparedScriptText), e);
        }
        return script;
    }

    private String ensureLastExpressionIsTarget(String scriptText) {
        return String.format("%s; target;", scriptText);
    }
}