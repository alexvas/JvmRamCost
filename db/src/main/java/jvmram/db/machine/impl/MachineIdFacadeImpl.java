package jvmram.db.machine.impl;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jvmram.db.boot.Os;
import jvmram.db.machine.MachineIdFacade;

import java.lang.foreign.*;
import java.nio.file.Path;
import java.util.UUID;

import static jvmram.db.utils.Utils.invokeExact;
import static jvmram.db.utils.Utils.readContent;

@Singleton
public class MachineIdFacadeImpl implements MachineIdFacade {

    @Inject
    MachineIdFacadeImpl() {
    }

    @Override
    public UUID getMachineId(Os os) {
        return switch (os) {
            case LINUX -> getLinuxMachineId();
            case WINDOWS -> getWindowsMachineId();
        };
    }

    private UUID getLinuxMachineId() {
        var machineIdPath = Path.of("/etc/machine-id");
        var id = readContent(machineIdPath);
        if (id == null) {
            throw new IllegalStateException("No %s".formatted(machineIdPath));
        }
        return UUID.fromString(id);
    }

    private static final long HKEY_LOCAL_MACHINE = 0x80000002L;
    private static final int KEY_READ = 0x20019;
    private static final int ERROR_SUCCESS = 0;

    private UUID getWindowsMachineId() {
        try (var arena = Arena.ofConfined()) {
            var linker = Linker.nativeLinker();
            var advapi32 = SymbolLookup.libraryLookup("advapi32", Arena.global());

            // RegOpenKeyExW
            var regOpenKeyExW = linker.downcallHandle(
                    advapi32.find("RegOpenKeyExW").orElseThrow(),
                    FunctionDescriptor.of(
                            ValueLayout.JAVA_INT,      // return LONG (error code)
                            ValueLayout.JAVA_LONG,     // hKey (HKEY)
                            ValueLayout.ADDRESS,       // lpSubKey (LPCWSTR)
                            ValueLayout.JAVA_INT,      // ulOptions
                            ValueLayout.JAVA_INT,      // samDesired
                            ValueLayout.ADDRESS        // phkResult (PHKEY)
                    )
            );

            // RegQueryValueExW
            var regQueryValueExW = linker.downcallHandle(
                    advapi32.find("RegQueryValueExW").orElseThrow(),
                    FunctionDescriptor.of(
                            ValueLayout.JAVA_INT,      // return LONG
                            ValueLayout.JAVA_LONG,     // hKey
                            ValueLayout.ADDRESS,       // lpValueName
                            ValueLayout.ADDRESS,       // lpReserved
                            ValueLayout.ADDRESS,       // lpType
                            ValueLayout.ADDRESS,       // lpData
                            ValueLayout.ADDRESS        // lpcbData
                    )
            );

            // RegCloseKey
            var regCloseKey = linker.downcallHandle(
                    advapi32.find("RegCloseKey").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG)
            );

            // Subkey path: SOFTWARE\Microsoft\Cryptography
            var subKey = "SOFTWARE\\Microsoft\\Cryptography";
            var subKeySegment = arena.allocateFrom(subKey, java.nio.charset.StandardCharsets.UTF_16LE);
            // Null-terminate for wide string
            var subKeyWide = arena.allocate((subKey.length() + 1) * 2L);
            subKeyWide.copyFrom(subKeySegment);

            // Pointer to receive opened key handle
            var phkResult = arena.allocate(ValueLayout.JAVA_LONG);

            int result = (int) invokeExact(
                    regOpenKeyExW,
                    HKEY_LOCAL_MACHINE,
                    subKeyWide,
                    0,
                    KEY_READ,
                    phkResult
            );

            if (result != ERROR_SUCCESS) {
                throw new IllegalStateException("RegOpenKeyExW failed with error code: " + result);
            }

            long hKey = phkResult.get(ValueLayout.JAVA_LONG, 0);

            try {
                // Value name: MachineGuid
                var valueName = "MachineGuid";
                var valueNameWide = arena.allocate((valueName.length() + 1) * 2L);
                valueNameWide.copyFrom(arena.allocateFrom(valueName, java.nio.charset.StandardCharsets.UTF_16LE));

                // Buffer for the GUID (max 39 chars including null terminator, in UTF-16 = 78 bytes)
                int bufferSize = 128;
                var dataBuffer = arena.allocate(bufferSize);
                var dataSize = arena.allocate(ValueLayout.JAVA_INT);
                dataSize.set(ValueLayout.JAVA_INT, 0, bufferSize);

                result = (int) invokeExact(
                        regQueryValueExW,
                        hKey,
                        valueNameWide,
                        MemorySegment.NULL,
                        MemorySegment.NULL,
                        dataBuffer,
                        dataSize
                );

                if (result != ERROR_SUCCESS) {
                    throw new IllegalStateException("RegQueryValueExW failed with error code: " + result);
                }

                int actualSize = dataSize.get(ValueLayout.JAVA_INT, 0);
                // Convert UTF-16LE bytes to String (excluding null terminator)
                byte[] bytes = dataBuffer.asSlice(0, actualSize - 2).toArray(ValueLayout.JAVA_BYTE);
                var guid = new String(bytes, java.nio.charset.StandardCharsets.UTF_16LE);

                return UUID.fromString(guid);
            } finally {
                invokeExact(regCloseKey, hKey);
            }
        }
    }
}
