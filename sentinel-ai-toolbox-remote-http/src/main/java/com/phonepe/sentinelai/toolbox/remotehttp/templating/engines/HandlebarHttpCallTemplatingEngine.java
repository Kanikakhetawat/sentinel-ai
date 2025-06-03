package com.phonepe.sentinelai.toolbox.remotehttp.templating.engines;

import com.github.jknack.handlebars.Handlebars;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.HttpCallTemplate;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.HttpCallTemplatingEngine;
import lombok.SneakyThrows;
import lombok.val;

import java.util.Map;

public class HandlebarHttpCallTemplatingEngine implements HttpCallTemplatingEngine {

    private static final Handlebars handlebars;

    static {
        handlebars = new Handlebars();
    }

    @Override
    @SneakyThrows
    public String convert(HttpCallTemplate.Template template, Map<String, Object> context) {
        val handlebarTemplate = handlebars.compile(template.getContent());
        return handlebarTemplate.apply(context);
    }
}
