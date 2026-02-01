package jvmram.backend.impl;

import io.grpc.BindableService;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Сервер, который управляет стартом и остановом сервера BackendImpl
 */
public class JvmRamBackendManager {

    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final int WORKER_THREADS_COUNT = 2;

    private volatile Server server;

    public void start(@SuppressWarnings("SameParameterValue") int port, BindableService service) {

        /*
         * По умолчанию gRPC использует глобальный разделяемый Executor.newCachedThreadPool(),
         * чтобы gRPC производить вызовы приложения. Это удобно, но может привести к созданию
         * большого числа потоков в случае большого количества вызовов (RPC). Зачастую лучше ограничить
         * число потоков, которое ваше приложение использует для обработки вызовов и позволить
         * RPC копиться в очереди, когда ЦПУ занят. Подходящее число потоков сильно меняется
         * от приложения к приложению. Приложения с асинхронной обработкой вызовов обычно не
         * нуждаются в числе потоков, большем числа ядер ЦПУ.
         */
        var executor = Executors.newFixedThreadPool(WORKER_THREADS_COUNT);
        server = Grpc.newServerBuilderForPort(port, InsecureServerCredentials.create())
                .executor(executor)
                .addService(service)
                .build();

        try {
            server.start();
        } catch (IOException e) {
            LOG.error("Failed to start server using port {}", port, e);
            return;
        }

        LOG.info("Server started, listening on {}", port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(executor)));
    }

    private void shutdown(ExecutorService executor) {
        // Здесь используем stderr, поскольку logger уже может успеть
        // выключиться собственным JVM shutdown hook.
        System.err.println("*** shutting down gRPC server since JVM is shutting down");
        try {
            if (server != null) {
                server.shutdown().awaitTermination(3, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            if (server != null) {
                server.shutdownNow();
            }
            // Аналогично: печатаем в STDERR
            e.printStackTrace(System.err);
        } finally {
            executor.shutdown();
        }
        // Аналогично: печатаем в STDERR
        System.err.println("*** server shut down");
    }

    /**
     * Ждём окончания работы в главном (main) потоке, поскольку библиотека grpc
     * использует потоки-демоны.
     */
    public void blockUntilShutdown() {
        if (server != null) {
            try {
                server.awaitTermination();
            } catch (InterruptedException e) {
                // Ничего не делаем. JVM и так останавливается. Просто выходим отсюда.
            }
        }
    }


}
