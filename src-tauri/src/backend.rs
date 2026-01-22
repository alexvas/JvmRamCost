use std::path::PathBuf;
use std::process::Command;

pub fn start_backend(resource_dir: &PathBuf) -> Result<std::process::Child, String> {

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
        // Linux: backend/bin/jvm-ram-cost
        let standalone_linux_path = resource_dir
            .join("backend")
            .join("bin")
            .join("jvm-ram-cost");
        if standalone_linux_path.exists() {
            return Ok(BackendKind::StandaloneLinux(standalone_linux_path));
        }

        // Windows: backend/jvm-ram-cost.exe (jpackage на Windows кладёт exe в корень, не в bin/)
        let standalone_windows_path = resource_dir.join("backend").join("jvm-ram-cost.exe");
        if standalone_windows_path.exists() {
            return Ok(BackendKind::StandaloneWindows(standalone_windows_path));
        }

        eprintln!(
            "Standalone backend not found at {:?} or {:?}",
            standalone_linux_path, standalone_windows_path
        );
        let java = BackendKind::find_java()?;
        Ok(BackendKind::Jdk25(java))
    }

    const MIN_JDK_VERSION: i32 = 25;

    fn find_java() -> Result<PathBuf, String> {
        // Пробуем java в PATH
        if let Ok(output) = Command::new("java")
            .arg("-XshowSettings:properties")
            .arg("-version")
            .output()
        {
            if output.status.success() {
                let stdout = String::from_utf8(output.stdout).unwrap();

                for line in stdout.lines() {
                    let line = line.trim();
                    if line.starts_with("java.specification.version =") {
                        let version = line.split('=').nth(1).unwrap().trim();
                        if version.parse::<i32>().unwrap() >= Self::MIN_JDK_VERSION {
                            return Ok("java".into());
                        }
                        break;
                    }
                }
            }
        }

        // Пробуем $JAVA_HOME/bin/java
        if let Ok(java_home) = std::env::var("JAVA_HOME") {
            let java_home_path = PathBuf::from(java_home);
            if java_home_path.exists() {
                let java_release_path = java_home_path.join("release");
                if java_release_path.exists() {
                    let java_release_file = std::fs::read_to_string(java_release_path).unwrap();
                    for line in java_release_file.lines() {
                        let line = line.trim();
                        if line.starts_with("JAVA_VERSION") {
                            let sem_ver = line.split('=').nth(1).unwrap().trim();
                            let sem_ver = sem_ver.trim_matches('"');
                            let version_major =
                                sem_ver.split('.').nth(0).unwrap().parse::<i32>().unwrap();
                            if version_major >= Self::MIN_JDK_VERSION {
                                let java_path = java_home_path.join("bin").join("java");
                                if java_path.exists() {
                                    return Ok(java_path);
                                }
                            }
                            break;
                        }
                    }
                }
            }
        }

        Err("Java не найдена. Установите Java (JDK-25 или выше) или задайте JAVA_HOME".into())
    }
}
