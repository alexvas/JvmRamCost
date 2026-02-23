package jvmram.model.graph;

import java.time.Instant;

public record GraphPoint(Instant moment, long bytes) {

    /**
     * Нет данных в связи со сбоем в работе или логике.
     */
    public static final GraphPoint NO_DATA = new GraphPoint(Instant.now(), -1);

    /**
     * Данные прежние. Следует использовать данные с предыдущего опроса,
     * поскольку таймаут опроса ещё не истёк.
     */
    public static final GraphPoint SAME_DATA = new GraphPoint(Instant.now(), -2);

    public boolean isRedundant() {
        return bytes == -1 || bytes == -2;
    }
}
