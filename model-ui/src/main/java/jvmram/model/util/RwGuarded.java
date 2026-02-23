package jvmram.model.util;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

@SuppressWarnings("ClassCanBeRecord")
public class RwGuarded {
    private final ReadWriteLock lock;

    private RwGuarded(ReadWriteLock lock) {
        this.lock = lock;
    }

    public void read(Runnable input) {
        var read = lock.readLock();
        read.lock();
        try {
            input.run();
        } finally {
            read.unlock();
        }
    }

    public <T> T read(Supplier<T> input) {
        var read = lock.readLock();
        read.lock();
        try {
            return input.get();
        } finally {
            read.unlock();
        }
    }

    public void write(Runnable input) {
        var write = lock.writeLock();
        write.lock();
        try {
            input.run();
        } finally {
            write.unlock();
        }
    }

    public <T> T write(Supplier<T> input) {
        var write = lock.writeLock();
        write.lock();
        try {
            return input.get();
        } finally {
            write.unlock();
        }
    }

    public static RwGuarded create() {
        var wrapped = new ReentrantReadWriteLock();
        return new RwGuarded(wrapped);
    }
}
