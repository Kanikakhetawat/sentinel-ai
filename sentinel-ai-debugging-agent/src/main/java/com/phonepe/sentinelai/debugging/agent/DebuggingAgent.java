package com.phonepe.sentinelai.debugging.agent;

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentExtension;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.debugging.agent.data.UserInput;
import lombok.NonNull;

import java.util.List;
import java.util.Map;

public class DebuggingAgent extends Agent<UserInput, String, DebuggingAgent> {

    protected DebuggingAgent(@NonNull AgentSetup setup,
                             List<AgentExtension> extensions,
                             Map<String, ExecutableTool> knownTools) {
        super(String.class, """
                """, setup, extensions, knownTools);
    }

    @Override
    public String name() {
        return "debugging-agent";
    }


}
