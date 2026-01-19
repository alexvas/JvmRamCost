use std::path::PathBuf;
use std::process::Command;

use tauri::{App, Manager};

fn get_resource_dir(app: &App) -> Result<PathBuf, String> {
    // Сначала пробуем стандартный resource_dir (работает в dev mode)
    if let Ok(resource_dir) = app.path().resource_dir() {
        if resource_dir.exists() {
            return Ok(resource_dir);
        }
    }

    // Для installed приложений определяем путь относительно executable
    let exe_path = std::env::current_exe()
        .map_err(|e| format!("Не удалось получить путь к executable: {}", e))?;

    #[cfg(target_os = "linux")]
    {
        // Linux: /usr/bin/app-name -> /usr/lib/app-name/
        let exe_name = exe_path
            .file_name()
            .and_then(|n| n.to_str())
            .ok_or("Не удалось получить имя executable")?;
        let resource_dir = PathBuf::from(format!("/usr/lib/{}", exe_name));
        if resource_dir.exists() {
            return Ok(resource_dir);
        }
    }

    #[cfg(target_os = "windows")]
    {
        // Windows: C:\Program Files\app-name\app-name.exe -> C:\Program Files\app-name\
        if let Some(parent) = exe_path.parent() {
            if parent.exists() {
                return Ok(parent.to_path_buf());
            }
        }
    }

    Err(format!(
        "Не удалось определить путь к ресурсам. Executable: {:?}",
        exe_path
    ))
}

pub fn start_backend(app: &App) -> Result<std::process::Child, String> {
    let resource_dir = get_resource_dir(app)?;

    let backend_kind = BackendKind::new(resource_dir.clone())?;

    let mut cmd: Command = match backend_kind.clone() {
        BackendKind::StandaloneLinux(path) => {
            // JVM-аргументы уже вшиты в launcher через jpackage --java-options
            Command::new(path)
        }
        BackendKind::StandaloneWindows(path) => {
            // JVM-аргументы уже вшиты в launcher через jpackage --java-options
            Command::new(path)
        }
        BackendKind::Jdk25(path) => {
            let mut cmd = Command::new(path);
            cmd.arg("-Xms10m")
                .arg("-Xmx100m")
                .arg("-XX:MaxDirectMemorySize=50m")
                .arg("--enable-native-access=ALL-UNNAMED");

            let jar_path = resource_dir.join("jvm-ram-cost.jar");
            if !jar_path.exists() {
                return Err(format!("JAR файл не найден: {:?}", jar_path));
            }
            cmd.arg("-jar").arg(&jar_path);

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
    StandaloneWindows(PathBuf),
    Jdk25(PathBuf),
}

impl BackendKind {
    fn new(resource_dir: PathBuf) -> Result<Self, String> {
        let standalone_linux_path = resource_dir
            .join("backend")
            .join("bin")
            .join("jvm-ram-cost");
        let standalone_exists = standalone_linux_path.exists();
        if standalone_exists {
            return Ok(BackendKind::StandaloneLinux(standalone_linux_path));
        }

        let standalone_windows_path = resource_dir
            .join("backend")
            .join("bin")
            .join("jvm-ram-cost.exe");
        if standalone_windows_path.exists() {
            return Ok(BackendKind::StandaloneWindows(standalone_windows_path));
        }

        eprintln!(
            "Standalone backend not found at {:?} or {:?}",
            standalone_linux_path,
            standalone_windows_path
        );
        let java = BackendKind::find_java()?;
        Ok(BackendKind::Jdk25(java))
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
