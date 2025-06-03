package com.phonepe.sentinelai.toolbox.remotehttp.templating.engines;

import com.phonepe.sentinelai.toolbox.remotehttp.templating.HttpCallTemplate;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.HttpCallTemplatingEngine;

import java.util.Map;

public class FunctionCallSubstitutorHttpCallTemplatingEngine implements HttpCallTemplatingEngine {

    @Override
    public String convert(HttpCallTemplate.Template template, Map<String, Object> context) {
        return template.getSupplier().apply(context);
    }
}
