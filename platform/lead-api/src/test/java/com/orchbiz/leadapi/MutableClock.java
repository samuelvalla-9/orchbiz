package com.orchbiz.leadapi;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Test clock that can be moved forward to exercise the dedupe window. */
public class MutableClock extends Clock {

    public static final Instant START = Instant.parse("2026-11-02T10:00:00Z");
    private Instant now = START;

    public void reset() { now = START; }
    public void advance(Duration d) { now = now.plus(d); }

    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return now; }
}
