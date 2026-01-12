package jvmram.suppliers;

import jvmram.suppliers.data.HardwareData;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;

abstract class AbstractFileReaderSupplier<T extends HardwareData> extends AbstractDataSupplier<T> {
    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final Path filePath;

    AbstractFileReaderSupplier(int pid, Path filePath) {
        super(pid);
        this.filePath = filePath;
    }

    @Override
    void setInitialized() {
        if (!Files.exists(filePath)) {
            LOG.info("No path {} exists. The process {} is probably already closed.", filePath, pid);
        } else {
            super.setInitialized();
        }
    }

    @Override
    @Nullable T doGetData() {
        startFileParse();
        try (var fr = new FileReader(filePath.toFile()); var br = new BufferedReader(fr)) {
            while (true) {
                var line = br.readLine();
                if (line == null) {
                    // EOF
                    break;
                }
                var proceed = parseLine(line);
                if ( !proceed) {
                    break;
                }
            }
        } catch (IOException e) {
            LOG.info("Exception reading RAM data from {}. The process {} is probably already closed.", filePath, pid);
            return null;
        }
        var parsed = parsedData();
        if (parsed == null) {
            LOG.info("Unable to extract complete RAM data from {}. The process {} was probably closed on the way.", filePath, pid);
        }
        return parsed;
    }

    /**
     * Метод для инициализации внутренней структуры Поставщика.
     */
    abstract void startFileParse();

    /**
     * Извлекает данные из текстовой строки и пополняет знание о возвращаемом значении.
     *
     * @param input -- строка данных для исследования
     * @return надо ли продолжать читать файл.
     */
    abstract boolean parseLine(String input);

    /**
     * Отдаёт распознанное значение.
     *
     * @return распознанное значение или null, если значение не распознано.
     */
    abstract @Nullable T parsedData();

    /**
     * Метод для использования в потомках.
     * Возвращает строковое значение в строке после префикса, ограниченное пробелом.
     *
     * @param line - входная строка
     * @param prefix - префикс или первое значение, которое нужно выкинуть.
     * @return - второе после префикса значение
     */
    String secondItem(String line, String prefix) {
        line = line.substring(prefix.length() + 1);
        line = line.trim();
        int next_space = line.indexOf(" ");
        return line.substring(0, next_space);
    }

    /**
     * Метод для использования в потомках.
     * Конвертирует килобайты в байты.
     *
     * @param inputInKilobytes - килобайты на входе.
     * @return - байты на выходе
     */
    long kilobytesToBytes(String inputInKilobytes) {
        return Long.parseLong(inputInKilobytes) * 1024;
    }
}
