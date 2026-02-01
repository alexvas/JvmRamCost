--
-- метаданные организации хранимых данных приложением jvm-ram-cost
CREATE TABLE IF NOT EXISTS jvm_ram_cost_metadata (
    id INT AUTO_INCREMENT PRIMARY KEY,
    version INT NOT NULL
);
-- insert into jvm_ram_cost_metadata
INSERT INTO jvm_ram_cost_metadata (id, version)
VALUES (1, 1);
--
-- сессия запуска ОС (от загрузки до завершения работы ОС)
CREATE TABLE IF NOT EXISTS jvm_ram_cost_boot_session (
    id INT AUTO_INCREMENT PRIMARY KEY,
    os_type VARCHAR(20) NOT NULL,
    hostname TEXT NOT NULL,
    alias TEXT,
    machine_id UUID NOT NULL,
    boot_id TEXT NOT NULL,
    CONSTRAINT chk_os_type CHECK (os_type IN ('linux', 'windows'))
);
CREATE UNIQUE INDEX IF NOT EXISTS unique_jvm_ram_cost_boot_session_machine_id_boot_id ON jvm_ram_cost_boot_session (machine_id, boot_id);
--
-- информация о процессе
CREATE TABLE IF NOT EXISTS jvm_ram_cost_process_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    boot_session_id INT NOT NULL REFERENCES jvm_ram_cost_boot_session(id) ON DELETE CASCADE,
    pid INT NOT NULL,
    process_name TEXT NOT NULL,
    comment TEXT,
    process_state VARCHAR(20) NOT NULL,
    process_start_time TIMESTAMP NOT NULL,
    process_home_directory TEXT NOT NULL,
    jvm_major_version INT NOT NULL,
    jvm_version TEXT NOT NULL,
    gc_type VARCHAR(30),
    container_id TEXT,
    max_direct_memory_kib BIGINT,
    metaspace_max_kib BIGINT,
    xmx_kib BIGINT NOT NULL,
    xms_kib BIGINT NOT NULL,
    CONSTRAINT chk_state CHECK (
        process_state IN ('running', 'stopped', 'zombie')
    )
);
CREATE INDEX IF NOT EXISTS idx_jvm_ram_cost_process_info_boot_session_id ON jvm_ram_cost_process_info (boot_session_id);
CREATE INDEX IF NOT EXISTS idx_jvm_ram_cost_process_info_pid ON jvm_ram_cost_process_info (pid);
--
-- аргументы запуска процесса (много аргументов к одному процессу)
CREATE TABLE IF NOT EXISTS jvm_ram_cost_process_arguments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    process_info_id BIGINT NOT NULL REFERENCES jvm_ram_cost_process_info(id) ON DELETE CASCADE,
    key_arg TEXT NOT NULL,
    value_arg TEXT
);
CREATE INDEX IF NOT EXISTS fk_jvm_ram_cost_process_arguments_process_info_id ON jvm_ram_cost_process_arguments (process_info_id);
--
-- системные свойства процесса (много свойств к одному процессу)
CREATE TABLE IF NOT EXISTS jvm_ram_cost_process_system_properties (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    process_info_id BIGINT NOT NULL REFERENCES jvm_ram_cost_process_info(id) ON DELETE CASCADE,
    key_system_property TEXT NOT NULL,
    value_system_property TEXT
);
CREATE INDEX IF NOT EXISTS fk_jvm_ram_cost_process_system_properties_process_info_id ON jvm_ram_cost_process_system_properties (process_info_id);
--
-- переменные окружения процесса (много переменных к одному процессу)
CREATE TABLE IF NOT EXISTS jvm_ram_cost_process_environment_variables (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    process_info_id BIGINT NOT NULL REFERENCES jvm_ram_cost_process_info(id) ON DELETE CASCADE,
    key_environment_variable TEXT NOT NULL,
    value_environment_variable TEXT
);
CREATE INDEX IF NOT EXISTS fk_jvm_ram_cost_process_environment_variables_process_info_id ON jvm_ram_cost_process_environment_variables (process_info_id);
--
-- метрики процесса, какие может измерить приложение jvm-ram-cost
CREATE TABLE IF NOT EXISTS jvm_ram_cost_process_metrics (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name TEXT NOT NULL,
    unit VARCHAR(20) DEFAULT 'kib',
    -- единица измерения
    source VARCHAR(20) DEFAULT 'system',
    os_type VARCHAR(20) NOT NULL,
    CONSTRAINT chk_metrics_os_type CHECK (os_type IN ('linux', 'windows', 'all'))
);
INSERT INTO jvm_ram_cost_process_metrics (id, name, unit, source, os_type)
VALUES (1, 'RSS', 'kib', 'system', 'linux');
INSERT INTO jvm_ram_cost_process_metrics (id, name, unit, source, os_type)
VALUES (2, 'PSS', 'kib', 'system', 'linux');
INSERT INTO jvm_ram_cost_process_metrics (id, name, unit, source, os_type)
VALUES (3, 'USS', 'kib', 'system', 'linux');
INSERT INTO jvm_ram_cost_process_metrics (id, name, unit, source, os_type)
VALUES (4, 'WS', 'kib', 'system', 'windows');
INSERT INTO jvm_ram_cost_process_metrics (id, name, unit, source, os_type)
VALUES (5, 'PB', 'kib', 'system', 'windows');
INSERT INTO jvm_ram_cost_process_metrics (id, name, unit, source, os_type)
VALUES (6, 'HEAP_USED', 'kib', 'jvm', 'all');
INSERT INTO jvm_ram_cost_process_metrics (id, name, unit, source, os_type)
VALUES (7, 'HEAP_COMMITTED', 'kib', 'jvm', 'all');
INSERT INTO jvm_ram_cost_process_metrics (id, name, unit, source, os_type)
VALUES (8, 'OLD_GEN_MAX', 'kib', 'jvm', 'all');
INSERT INTO jvm_ram_cost_process_metrics (id, name, unit, source, os_type)
VALUES (9, 'OLD_GEN_COMMITTED', 'kib', 'jvm', 'all');
INSERT INTO jvm_ram_cost_process_metrics (id, name, unit, source, os_type)
VALUES (10, 'OLD_GEN_USED', 'kib', 'jvm', 'all');
INSERT INTO jvm_ram_cost_process_metrics (id, name, unit, source, os_type)
VALUES (11, 'NMT_USED', 'kib', 'jvm', 'all');
INSERT INTO jvm_ram_cost_process_metrics (id, name, unit, source, os_type)
VALUES (12, 'NMT_COMMITTED', 'kib', 'jvm', 'all');
INSERT INTO jvm_ram_cost_process_metrics (id, name, unit, source, os_type)
VALUES (13, 'BUFFER_TOTAL', 'kib', 'jvm', 'all');
--
-- собственно значения метрик, которые измеряет приложение jvm-ram-cost
CREATE TABLE IF NOT EXISTS jvm_ram_cost_process_metric_values (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    process_info_id BIGINT NOT NULL REFERENCES jvm_ram_cost_process_info(id) ON DELETE CASCADE,
    metric_id INT NOT NULL REFERENCES jvm_ram_cost_process_metrics(id) ON DELETE CASCADE,
    measured_at TIMESTAMP(3) NOT NULL,
    kibibytes BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_jvm_ram_cost_process_metric_values_pid_metric_type ON jvm_ram_cost_process_metric_values (process_info_id, metric_id);