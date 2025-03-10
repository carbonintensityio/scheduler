package io.carbonintensity.scheduler.test.helper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

public class MutableClock extends Clock {
    private final Clock baseClock;
    private Duration offset = Duration.ZERO;

    public MutableClock(Clock baseClock) {
        this.baseClock = baseClock;
    }

    public void shift(Duration duration) {
        this.offset = this.offset.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return baseClock.getZone();
    }

    @Override
    public Clock withZone(ZoneId zone) {
        MutableClock mutableClock = new MutableClock(baseClock.withZone(zone));
        mutableClock.shift(this.offset);
        return mutableClock;
    }

    @Override
    public Instant instant() {
        return baseClock.instant().plus(offset);
    }
}
