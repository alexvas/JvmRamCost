package jvmram.model.di;

import jvmram.model.graph.GraphPointQueuesWritable;
import jvmram.model.graph.impl.GraphPointQueuesImpl;
import ru.dimension.di.DimensionDI;

public class ModelDiConfig {
    private ModelDiConfig() {
    }

    public static DimensionDI.Builder config(DimensionDI.Builder builder) {
        return builder
                .bind(GraphPointQueuesWritable.class, GraphPointQueuesImpl.class);
    }
}
