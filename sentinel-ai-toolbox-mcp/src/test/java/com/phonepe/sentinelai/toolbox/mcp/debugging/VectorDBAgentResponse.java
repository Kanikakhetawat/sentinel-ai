package com.phonepe.sentinelai.toolbox.mcp.debugging;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public class VectorDBAgentResponse {
    private String issue;
    private List<String> identifiedReasons;
    private List<String> relevantLinks;
}
