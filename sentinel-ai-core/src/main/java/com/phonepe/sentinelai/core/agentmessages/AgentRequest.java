package com.phonepe.sentinelai.core.agentmessages;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Base class for all requests sent by the agent to the LLM
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public abstract class AgentRequest extends AgentMessage {

    protected AgentRequest(AgentMessageType requestType) {
        super(requestType);
    }

    @Override
    public <T> T accept(AgentMessageVisitor<T> visitor) {
        return visitor.visit(this);
    }

    public abstract <T> T accept(AgentRequestVisitor<T> visitor);
}
