package com.phonepe.sentinelai.toolbox.mcp.debugging;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Builder
@Data
public class SlackContent {
    private String channelName;
    private String user;
    private String text;
    private String permalink;
    private String date;
    private LocalDateTime dateTs;
}
