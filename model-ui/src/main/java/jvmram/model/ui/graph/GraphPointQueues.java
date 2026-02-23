package jvmram.model.ui.graph;

import java.time.Instant;
import java.util.Collection;

public interface GraphPointQueues {

    /**
     * Какие очереди (графики) по типу памяти и номеру процесса содержит модель.
     *
     * @return ключи-указатели на графики
     */
    Collection<GraphKey> keys();

    /**
     * Отдаёт очередь (график) данных для определённого типа памяти и нужного PID.
     *
     * @param key ключ-указатель на график
     * @return очередь
     */
    Collection<GraphPoint> getPoints(GraphKey key);

    /**
     * Показывает момент, когда стартовало приложение.
     * @return момент старта приложения
     */
    Instant getApplicationStart();
}
