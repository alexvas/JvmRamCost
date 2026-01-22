use std::path::PathBuf;

pub fn get_resource_dir(resource_dir: &PathBuf) -> Result<PathBuf, String> {
    // Сначала пробуем стандартный resource_dir (работает в dev mode)
    if resource_dir.exists() {
        return Ok(resource_dir.clone());
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
