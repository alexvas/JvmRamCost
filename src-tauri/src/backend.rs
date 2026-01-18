use std::path::PathBuf;
use std::process::Command;

use tauri::{App, Manager};

pub fn start_backend(app: &App) -> Result<std::process::Child, String> {
    let backend_kind = BackendKind::new()?;

    let mut cmd: Command = match backend_kind.clone() {
        BackendKind::StandaloneLinux(path) => {
            // JVM-аргументы уже вшиты в launcher через jpackage --java-options
            Command::new(path)
        }
        BackendKind::Jdk25(path) => {
            let mut cmd = Command::new(path);
            cmd.arg("-Xms10m")
                .arg("-Xmx100m")
                .arg("-XX:MaxDirectMemorySize=50m")
                .arg("--enable-native-access=ALL-UNNAMED");

            let resource_path = app
                .path()
                .resource_dir()
                .map_err(|e| format!("Не удалось получить путь к ресурсам: {}", e))?
                .join("jvm-ram-cost.jar");
            if !resource_path.exists() {
                return Err(format!("JAR файл не найден: {:?}", resource_path));
            }
            cmd.arg("-jar").arg(&resource_path);

            cmd
        }
    };

    #[cfg(unix)]
    {
        use std::os::unix::process::CommandExt;
        unsafe {
            cmd.pre_exec(|| {
                libc::setpgid(0, 0);
                Ok(())
            });
        }
    }

    cmd.spawn()
        .map_err(|e| format!("Не удалось запустить бэкенд: {}", e))
}

use std::process::Child;
use std::time::Duration;

pub fn kill(child: &mut Child) {
    #[cfg(unix)]
    {
        // На Unix убиваем всю process group
        unsafe {
            let pgid = libc::getpgid(child.id() as i32);
            if pgid > 0 {
                let _ = libc::killpg(pgid, libc::SIGTERM);
                // Даём время на graceful shutdown
                std::thread::sleep(Duration::from_millis(500));
                let _ = libc::killpg(pgid, libc::SIGKILL);
            } else {
                // Если не удалось получить pgid, просто убиваем процесс
                let _ = child.kill();
            }
        }
    }
    #[cfg(not(unix))]
    {
        let _ = child.kill();
    }
    let _ = child.wait();
}

#[derive(Clone)]
enum BackendKind {
    StandaloneLinux(PathBuf),
    Jdk25(PathBuf),
}

impl BackendKind {
    fn new() -> Result<Self, String> {
        let standalone_path =
            PathBuf::from("usr/lib/jvm-ram-cost-standalone/backend/bin/jvm-ram-cost");
        let standalone_exists = standalone_path.exists();
        if standalone_exists {
            Ok(BackendKind::StandaloneLinux(standalone_path))
        } else {
            let java = BackendKind::find_java()?;
            Ok(BackendKind::Jdk25(java))
        }
    }

    fn find_java() -> Result<PathBuf, String> {
        // Пробуем java в PATH
        if let Ok(output) = std::process::Command::new("java").arg("-version").output() {
            if output.status.success() {
                return Ok("java".into());
            }
        }

        // Пробуем $JAVA_HOME/bin/java
        if let Ok(java_home) = std::env::var("JAVA_HOME") {
            let java_path = PathBuf::from(java_home).join("bin/java");
            if java_path.exists() {
                return Ok(java_path);
            }
        }

        Err("Java не найдена. Установите Java или задайте JAVA_HOME".into())
    }
}
