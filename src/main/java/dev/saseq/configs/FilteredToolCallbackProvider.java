package dev.saseq.configs;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.Arrays;
import java.util.Set;

public class FilteredToolCallbackProvider implements ToolCallbackProvider {

    private final ToolCallbackProvider delegate;
    private final Set<String> allowedTools;

    public FilteredToolCallbackProvider(ToolCallbackProvider delegate, Set<String> allowedTools) {
        this.delegate = delegate;
        this.allowedTools = allowedTools;
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        ToolCallback[] all = delegate.getToolCallbacks();
        if (allowedTools.isEmpty()) {
            return all;
        }
        return Arrays.stream(all)
                .filter(cb -> allowedTools.contains(cb.getToolDefinition().name()))
                .toArray(ToolCallback[]::new);
    }
}
