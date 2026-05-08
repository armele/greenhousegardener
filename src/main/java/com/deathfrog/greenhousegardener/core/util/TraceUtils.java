package com.deathfrog.greenhousegardener.core.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.deathfrog.greenhousegardener.GreenhouseGardenerMod;

public final class TraceUtils
{
    public static final String TRACE_NONE = "none";

    private static final Map<String, Boolean> TRACE_MAP = new ConcurrentHashMap<>();

    private TraceUtils()
    {
    }

    public static void dynamicTrace(final String traceKey, final Runnable loggingStatement)
    {
        if (!Boolean.TRUE.equals(TRACE_MAP.get(traceKey)))
        {
            return;
        }

        try
        {
            loggingStatement.run();
        }
        catch (final Throwable t)
        {
            GreenhouseGardenerMod.LOGGER.warn("Trace '{}' threw while logging; swallowing.", traceKey, t);
        }
    }

    public static boolean isTracing(final String traceKey)
    {
        return Boolean.TRUE.equals(TRACE_MAP.get(traceKey));
    }

    public static void setTrace(final String traceKey, final boolean traceSetting)
    {
        TRACE_MAP.put(traceKey, traceSetting);
    }
}
